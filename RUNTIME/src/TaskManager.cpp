#include "TaskManager.h"
#include "SimpleJson.h"

#include <httplib.h>

#include <algorithm>
#include <cctype>
#include <csignal>
#include <ctime>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <sstream>
#include <thread>
#include <utility>
#include <vector>

#ifndef _WIN32
#include <sys/wait.h>
#include <unistd.h>
#endif

namespace {

std::string writeJson(const JsonValue& value) {
    return value.stringify();
}

JsonValue responseBody(int code, const std::string& message) {
    JsonValue root = JsonValue::object();
    root["code"] = code;
    root["msg"] = message;
    return root;
}

std::string jsonResponse(int code, const std::string& message) {
    return writeJson(responseBody(code, message));
}

bool parseJson(const std::string& body, JsonValue& root, std::string& error) {
    return SimpleJson::parse(body, root, error);
}

int asInt(const JsonValue& value, const std::string& key, int fallback = 0) {
    if (!value.isObject() || !value.has(key)) {
        return fallback;
    }
    return value[key].asInt(fallback);
}

double asDouble(const JsonValue& value, const std::string& key, double fallback = 0.0) {
    if (!value.isObject() || !value.has(key)) {
        return fallback;
    }
    return value[key].asDouble(fallback);
}

bool asBool(const JsonValue& value, const std::string& key, bool fallback = false) {
    if (!value.isObject() || !value.has(key)) {
        return fallback;
    }
    return value[key].asBool(fallback);
}

std::string asString(const JsonValue& value, const std::string& key, const std::string& fallback = "") {
    if (!value.isObject() || !value.has(key)) {
        return fallback;
    }
    return value[key].asString(fallback);
}

int readTaskId(const JsonValue& root) {
    int taskId = asInt(root, "task_id", 0);
    if (taskId <= 0) {
        taskId = asInt(root, "taskId", 0);
    }
    return taskId;
}

std::string sanitizeRegionName(std::string name) {
    if (name.empty()) {
        return "default";
    }
    for (char& ch : name) {
        const bool allowed = std::isalnum(static_cast<unsigned char>(ch)) || ch == '_' || ch == '-';
        if (!allowed) {
            ch = '_';
        }
    }
    return name;
}

std::string polygonToIni(const JsonValue& polygon) {
    std::ostringstream out;
    out << "[";
    for (size_t i = 0; i < polygon.size(); ++i) {
        if (i > 0) {
            out << ",";
        }
        const JsonValue& point = polygon[i];
        out << "[" << point[0].asInt() << "," << point[1].asInt() << "]";
    }
    out << "]";
    return out.str();
}

std::string quoteForCommand(const std::string& text) {
    std::string result = "\"";
    for (char ch : text) {
        if (ch == '"') {
            result += "\\\"";
        } else {
            result += ch;
        }
    }
    result += "\"";
    return result;
}

bool hasExtension(const std::string& path, const std::string& extension) {
    if (path.size() < extension.size()) {
        return false;
    }
    std::string suffix = path.substr(path.size() - extension.size());
    for (char& ch : suffix) {
        ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    }
    return suffix == extension;
}

bool constantTimeEqual(const std::string& left, const std::string& right) {
    const size_t length = std::max(left.size(), right.size());
    size_t difference = left.size() ^ right.size();
    for (size_t i = 0; i < length; ++i) {
        const unsigned char leftByte =
            i < left.size() ? static_cast<unsigned char>(left[i]) : 0;
        const unsigned char rightByte =
            i < right.size() ? static_cast<unsigned char>(right[i]) : 0;
        difference |= static_cast<size_t>(leftByte ^ rightByte);
    }
    return difference == 0;
}

std::string requestToken(const httplib::Request& req) {
    const std::string bearer = req.get_header_value("Authorization");
    const std::string prefix = "Bearer ";
    if (bearer.rfind(prefix, 0) == 0) {
        return bearer.substr(prefix.size());
    }
    return req.get_header_value("X-Task-Token");
}

bool isSafeIniValue(const std::string& value, size_t maxLength = 4096) {
    if (value.size() > maxLength) {
        return false;
    }
    for (unsigned char ch : value) {
        if (ch < 0x20 || ch == 0x7f) {
            return false;
        }
    }
    return true;
}

std::filesystem::path expectedConfigPath(const TaskManagerOptions& options, int taskId) {
    return std::filesystem::absolute(
        std::filesystem::path(options.configDir) /
        ("task" + std::to_string(taskId) + ".ini")
    ).lexically_normal();
}

std::string formatTime(std::chrono::system_clock::time_point value) {
    if (value.time_since_epoch().count() == 0) {
        return "";
    }
    std::time_t time = std::chrono::system_clock::to_time_t(value);
    std::tm tmValue{};
#ifdef _WIN32
    localtime_s(&tmValue, &time);
#else
    localtime_r(&time, &tmValue);
#endif
    char buffer[32];
    std::strftime(buffer, sizeof(buffer), "%Y-%m-%d %H:%M:%S", &tmValue);
    return buffer;
}

JsonValue taskToJson(const TaskProcess& task) {
    JsonValue item = JsonValue::object();
    item["task_id"] = task.taskId;
    item["status"] = task.status;
    item["config_path"] = task.configPath;
    item["pid"] = static_cast<double>(task.pid);
    item["exit_code"] = task.exitCode;
    item["started_at"] = formatTime(task.startedAt);
    return item;
}

} // namespace

TaskManager::TaskManager(TaskManagerOptions options) : options_(std::move(options)) {
}

TaskManager::~TaskManager() {
    stopAllTasks();
}

bool TaskManager::run() {
    httplib::Server server;

    server.set_pre_routing_handler(
        [this](const httplib::Request& req, httplib::Response& res) {
            if (req.path == "/health") {
                return httplib::Server::HandlerResponse::Unhandled;
            }
            if (!constantTimeEqual(requestToken(req), options_.authToken)) {
                res.status = 401;
                res.set_header("WWW-Authenticate", "Bearer");
                res.set_content(jsonResponse(401, "Unauthorized"), "application/json");
                return httplib::Server::HandlerResponse::Handled;
            }
            return httplib::Server::HandlerResponse::Unhandled;
        }
    );

    server.Get("/health", [this](const httplib::Request&, httplib::Response& res) {
        res.set_content(handleHealth(), "application/json");
    });

    server.Get("/metrics", [this](const httplib::Request&, httplib::Response& res) {
        res.set_content(handleMetrics(), "application/json");
    });

    server.Get("/task/list", [this](const httplib::Request&, httplib::Response& res) {
        res.set_content(handleTaskList(), "application/json");
    });

    server.Get("/task/status", [this](const httplib::Request& req, httplib::Response& res) {
        int httpStatus = 200;
        std::string taskIdText = req.has_param("task_id") ? req.get_param_value("task_id") : "";
        res.set_content(handleTaskStatus(taskIdText, httpStatus), "application/json");
        res.status = httpStatus;
    });

    server.Post("/task/start", [this](const httplib::Request& req, httplib::Response& res) {
        int httpStatus = 200;
        res.set_content(handleStartTask(req.body, httpStatus), "application/json");
        res.status = httpStatus;
    });

    server.Post("/task/stop", [this](const httplib::Request& req, httplib::Response& res) {
        int httpStatus = 200;
        res.set_content(handleStopTask(req.body, httpStatus), "application/json");
        res.status = httpStatus;
    });

    server.Post("/config/generate", [this](const httplib::Request& req, httplib::Response& res) {
        int httpStatus = 200;
        res.set_content(handleGenerateConfig(req.body, httpStatus), "application/json");
        res.status = httpStatus;
    });

    server.set_read_timeout(5, 0);
    server.set_write_timeout(5, 0);
    server.set_payload_max_length(1024 * 1024);

    std::cout << "TaskManager listening on " << options_.host << ":" << options_.port << std::endl;
    return server.listen(options_.host.c_str(), options_.port);
}

std::string TaskManager::handleHealth() {
    JsonValue root = responseBody(0, "ok");
    root["service"] = "TaskManager";
    root["status"] = "ok";
    root["port"] = options_.port;
    root["task_binary"] = options_.taskBinary;
    return writeJson(root);
}

std::string TaskManager::handleMetrics() {
    std::lock_guard<std::mutex> lock(tasksMutex_);
    int running = 0;
    for (auto& pair : tasks_) {
        refreshTaskLocked(pair.second);
        if (pair.second.status == "running") {
            running++;
        }
    }

    JsonValue root = responseBody(0, "ok");
    root["configured_tasks"] = static_cast<int>(tasks_.size());
    root["running_tasks"] = running;
    root["max_tasks"] = options_.maxTasks;
    return writeJson(root);
}

std::string TaskManager::handleGenerateConfig(const std::string& body, int& httpStatus) {
    JsonValue root;
    std::string error;
    if (!parseJson(body, root, error)) {
        httpStatus = 400;
        return jsonResponse(400, "Invalid JSON: " + error);
    }

    int taskId = readTaskId(root);
    if (taskId <= 0) {
        httpStatus = 400;
        return jsonResponse(400, "task_id must be a positive integer");
    }

    const JsonValue video = root["video"];
    const JsonValue model = root.has("model") ? root["model"] : root["ai"];
    const JsonValue rtmp = root["rtmp"];
    const JsonValue alarm = root["alarm"];

    std::string rtspUrl = asString(video, "source", asString(video, "rtsp_url"));
    if (rtspUrl.empty()) {
        httpStatus = 400;
        return jsonResponse(400, "video.source or video.rtsp_url is required");
    }

    std::string taskName = asString(root, "task_name", "task_" + std::to_string(taskId));
    std::string modelPath = asString(model, "model_path");
    std::string classesPath = asString(model, "classes_path");
    std::string rtmpUrl = asString(rtmp, "rtmp_url");
    std::string hookUrl = asString(alarm, "hook_url");
    bool rtmpEnabled = asBool(rtmp, "enable", !rtmpUrl.empty());
    bool alarmEnabled = asBool(alarm, "enable", false);
    bool drawEnabled = asBool(rtmp, "enable_draw", true);
    int defaultControlPort = taskId <= 57535 ? 8000 + taskId : 0;
    int controlPort = asInt(root, "control_port", defaultControlPort);

    if (
        !isSafeIniValue(taskName, 128) ||
        !isSafeIniValue(rtspUrl) ||
        !isSafeIniValue(modelPath) ||
        !isSafeIniValue(classesPath) ||
        !isSafeIniValue(rtmpUrl) ||
        !isSafeIniValue(hookUrl)
    ) {
        httpStatus = 400;
        return jsonResponse(400, "Config contains control characters or exceeds length limits");
    }
    if (controlPort <= 0 || controlPort > 65535) {
        httpStatus = 400;
        return jsonResponse(400, "control_port must be between 1 and 65535");
    }

    std::filesystem::path configDir(options_.configDir);
    std::error_code fsError;
    std::filesystem::create_directories(configDir, fsError);
    if (fsError) {
        httpStatus = 500;
        return jsonResponse(500, "Failed to create config directory: " + fsError.message());
    }

    std::filesystem::path configPath = expectedConfigPath(options_, taskId);
    const std::filesystem::file_status configStatus =
        std::filesystem::symlink_status(configPath, fsError);
    if (fsError == std::errc::no_such_file_or_directory) {
        fsError.clear();
    }
    if (fsError) {
        httpStatus = 500;
        return jsonResponse(500, "Failed to inspect config path: " + fsError.message());
    }
    if (std::filesystem::is_symlink(configStatus)) {
        httpStatus = 400;
        return jsonResponse(400, "Refusing to overwrite a symlink config path");
    }
    std::ofstream file(configPath);
    if (!file.is_open()) {
        httpStatus = 500;
        return jsonResponse(500, "Failed to open config file for writing");
    }

    file << "[task]\n";
    file << "id=" << taskId << "\n";
    file << "name=" << taskName << "\n";
    file << "control_port=" << controlPort << "\n\n";

    file << "[video]\n";
    file << "rtsp_url=" << rtspUrl << "\n";
    if (!rtmpUrl.empty()) {
        file << "rtmp_url=" << rtmpUrl << "\n";
    }
    file << "width=" << asInt(video, "width", 1920) << "\n";
    file << "height=" << asInt(video, "height", 1080) << "\n";
    file << "fps=" << asInt(video, "fps", asInt(rtmp, "fps", 25)) << "\n\n";

    file << "[ai]\n";
    file << "enable=" << (!modelPath.empty() ? "true" : "false") << "\n";
    if (!modelPath.empty()) {
        file << "model_path=" << modelPath << "\n";
    }
    if (!classesPath.empty()) {
        file << "classes_path=" << classesPath << "\n";
    }
    file << "confidence_threshold=" << asDouble(model, "confidence_threshold", 0.5) << "\n";
    file << "threads=" << asInt(model, "threads", 3) << "\n\n";

    file << "[alarm]\n";
    file << "enable=" << (alarmEnabled ? "true" : "false") << "\n";
    if (!hookUrl.empty()) {
        file << "hook_url=" << hookUrl << "\n";
    }
    file << "confidence_threshold=" << asDouble(alarm, "confidence_threshold", 0.6) << "\n";
    file << "cooldown_time=" << asInt(alarm, "cooldown_time", 30) << "\n\n";

    file << "[features]\n";
    file << "enable_rtmp=" << (rtmpEnabled ? "true" : "false") << "\n";
    file << "enable_draw=" << (drawEnabled ? "true" : "false") << "\n";
    file << "enable_alarm=" << (alarmEnabled ? "true" : "false") << "\n";
    file << "headless=true\n\n";

    if (root.has("regions") && root["regions"].isArray() && root["regions"].size() > 0) {
        file << "[regions]\n";
        for (const auto& region : root["regions"].arrayItems()) {
            std::string name = sanitizeRegionName(asString(region, "region_id", "default"));
            const JsonValue polygon = region["polygon"];
            if (!polygon.isArray() || polygon.size() < 3) {
                continue;
            }
            file << name << "=" << polygonToIni(polygon) << "\n";
        }
    }

    file.close();

    JsonValue response = responseBody(0, "Config generated successfully");
    response["config_path"] = std::filesystem::absolute(configPath).string();
    response["task_id"] = taskId;
    return writeJson(response);
}

std::string TaskManager::handleStartTask(const std::string& body, int& httpStatus) {
    JsonValue root;
    std::string error;
    if (!parseJson(body, root, error)) {
        httpStatus = 400;
        return jsonResponse(400, "Invalid JSON: " + error);
    }

    int taskId = readTaskId(root);
    std::string configPath = asString(root, "config_path");
    if (taskId <= 0 || configPath.empty()) {
        httpStatus = 400;
        return jsonResponse(400, "task_id and config_path are required");
    }

    const std::filesystem::path expectedPath = expectedConfigPath(options_, taskId);
    const std::filesystem::path suppliedPath =
        std::filesystem::absolute(configPath).lexically_normal();
    if (suppliedPath != expectedPath) {
        httpStatus = 400;
        return jsonResponse(400, "config_path must match the generated path for task_id");
    }
    std::error_code fsError;
    if (std::filesystem::is_symlink(expectedPath, fsError)) {
        httpStatus = 400;
        return jsonResponse(400, "Symlink config paths are not allowed");
    }
    if (fsError) {
        httpStatus = 500;
        return jsonResponse(500, "Failed to inspect config_path: " + fsError.message());
    }
    if (!std::filesystem::is_regular_file(expectedPath, fsError)) {
        httpStatus = 404;
        return jsonResponse(404, "config_path does not exist");
    }
    if (fsError) {
        httpStatus = 500;
        return jsonResponse(500, "Failed to inspect config_path: " + fsError.message());
    }

    std::lock_guard<std::mutex> lock(tasksMutex_);
    auto existing = tasks_.find(taskId);
    const bool newTask = existing == tasks_.end();
    if (newTask && static_cast<int>(tasks_.size()) >= options_.maxTasks) {
        httpStatus = 429;
        return jsonResponse(429, "Task limit exceeded");
    }
    TaskProcess& task = tasks_[taskId];
    task.taskId = taskId;
    refreshTaskLocked(task);
    if (task.status == "running") {
        JsonValue response = responseBody(0, "Task already running");
        response["task_id"] = taskId;
        response["pid"] = static_cast<double>(task.pid);
        return writeJson(response);
    }
    task.configPath = expectedPath.string();
    task.exitCode = 0;
    if (!startChildProcess(task)) {
        if (newTask) {
            tasks_.erase(taskId);
        }
        httpStatus = 500;
        return jsonResponse(500, "Failed to start TASK process");
    }

    JsonValue response = responseBody(0, "Task started successfully");
    response["task_id"] = taskId;
    response["pid"] = static_cast<double>(task.pid);
    return writeJson(response);
}

std::string TaskManager::handleStopTask(const std::string& body, int& httpStatus) {
    JsonValue root;
    std::string error;
    if (!parseJson(body, root, error)) {
        httpStatus = 400;
        return jsonResponse(400, "Invalid JSON: " + error);
    }

    int taskId = readTaskId(root);
    if (taskId <= 0) {
        httpStatus = 400;
        return jsonResponse(400, "task_id is required");
    }

    std::lock_guard<std::mutex> lock(tasksMutex_);
    auto it = tasks_.find(taskId);
    if (it == tasks_.end()) {
        httpStatus = 404;
        return jsonResponse(404, "Task not found");
    }

    refreshTaskLocked(it->second);
    if (it->second.status == "running" && !stopChildProcess(it->second)) {
        httpStatus = 500;
        return jsonResponse(500, "Failed to stop TASK process");
    }
    it->second.status = "stopped";

    JsonValue response = responseBody(0, "Task stopped successfully");
    response["task_id"] = taskId;
    return writeJson(response);
}

std::string TaskManager::handleTaskStatus(const std::string& taskIdText, int& httpStatus) {
    int taskId = 0;
    try {
        taskId = std::stoi(taskIdText);
    } catch (...) {
        taskId = 0;
    }
    if (taskId <= 0) {
        httpStatus = 400;
        return jsonResponse(400, "task_id is required");
    }

    std::lock_guard<std::mutex> lock(tasksMutex_);
    auto it = tasks_.find(taskId);
    if (it == tasks_.end()) {
        httpStatus = 404;
        return jsonResponse(404, "Task not found");
    }
    refreshTaskLocked(it->second);

    JsonValue response = responseBody(0, "ok");
    response["data"] = taskToJson(it->second);
    return writeJson(response);
}

std::string TaskManager::handleTaskList() {
    std::lock_guard<std::mutex> lock(tasksMutex_);
    JsonValue response = responseBody(0, "ok");
    JsonValue data = JsonValue::array();
    for (auto& pair : tasks_) {
        refreshTaskLocked(pair.second);
        data.append(taskToJson(pair.second));
    }
    response["data"] = data;
    return writeJson(response);
}

void TaskManager::refreshTaskLocked(TaskProcess& task) {
    if (task.status == "running" && !isProcessRunning(task)) {
        task.status = "stopped";
    }
}

bool TaskManager::startChildProcess(TaskProcess& task) {
#ifdef _WIN32
    std::string command = quoteForCommand(options_.taskBinary) + " " + quoteForCommand(task.configPath);
    if (hasExtension(options_.taskBinary, ".cmd") || hasExtension(options_.taskBinary, ".bat")) {
        command = "cmd.exe /C call " + command;
    }
    STARTUPINFOA startupInfo{};
    PROCESS_INFORMATION processInfo{};
    startupInfo.cb = sizeof(startupInfo);

    std::vector<char> commandBuffer(command.begin(), command.end());
    commandBuffer.push_back('\0');

    BOOL ok = CreateProcessA(
        nullptr,
        commandBuffer.data(),
        nullptr,
        nullptr,
        FALSE,
        CREATE_NO_WINDOW,
        nullptr,
        nullptr,
        &startupInfo,
        &processInfo
    );
    if (!ok) {
        return false;
    }
    CloseHandle(processInfo.hThread);
    task.processHandle = processInfo.hProcess;
    task.pid = processInfo.dwProcessId;
#else
    pid_t pid = fork();
    if (pid < 0) {
        return false;
    }
    if (pid == 0) {
        execl(options_.taskBinary.c_str(), options_.taskBinary.c_str(), task.configPath.c_str(), static_cast<char*>(nullptr));
        _exit(127);
    }
    task.pid = pid;
#endif
    task.status = "running";
    task.startedAt = std::chrono::system_clock::now();
    return true;
}

bool TaskManager::stopChildProcess(TaskProcess& task) {
#ifdef _WIN32
    if (!task.processHandle) {
        task.status = "stopped";
        return true;
    }
    if (!TerminateProcess(task.processHandle, 0)) {
        return false;
    }
    WaitForSingleObject(task.processHandle, 5000);
    CloseHandle(task.processHandle);
    task.processHandle = nullptr;
    task.pid = 0;
#else
    if (task.pid <= 0) {
        task.status = "stopped";
        return true;
    }
    kill(task.pid, SIGTERM);
    for (int i = 0; i < 50; ++i) {
        int status = 0;
        pid_t result = waitpid(task.pid, &status, WNOHANG);
        if (result == task.pid) {
            task.exitCode = WIFEXITED(status) ? WEXITSTATUS(status) : 0;
            task.pid = -1;
            task.status = "stopped";
            return true;
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    kill(task.pid, SIGKILL);
    waitpid(task.pid, nullptr, 0);
    task.pid = -1;
#endif
    task.status = "stopped";
    return true;
}

bool TaskManager::isProcessRunning(TaskProcess& task) {
#ifdef _WIN32
    if (!task.processHandle) {
        return false;
    }
    DWORD exitCode = 0;
    if (!GetExitCodeProcess(task.processHandle, &exitCode)) {
        return false;
    }
    if (exitCode == STILL_ACTIVE) {
        return true;
    }
    task.exitCode = static_cast<int>(exitCode);
    CloseHandle(task.processHandle);
    task.processHandle = nullptr;
    task.pid = 0;
    return false;
#else
    if (task.pid <= 0) {
        return false;
    }
    int status = 0;
    pid_t result = waitpid(task.pid, &status, WNOHANG);
    if (result == 0) {
        return true;
    }
    if (result == task.pid) {
        task.exitCode = WIFEXITED(status) ? WEXITSTATUS(status) : 0;
        task.pid = -1;
        return false;
    }
    return false;
#endif
}

void TaskManager::stopAllTasks() {
    std::lock_guard<std::mutex> lock(tasksMutex_);
    for (auto& pair : tasks_) {
        refreshTaskLocked(pair.second);
        if (pair.second.status == "running") {
            stopChildProcess(pair.second);
        }
    }
}
