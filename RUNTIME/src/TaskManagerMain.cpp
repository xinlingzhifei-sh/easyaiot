#include "TaskManager.h"

#include <cstdlib>
#include <iostream>
#include <string>

namespace {

void printUsage(const char* program) {
    std::cout << "Usage: " << program
              << " [--host 0.0.0.0] [--port 7000] [--task-bin ./TASK]"
              << " [--config-dir config/generated] [--max-tasks 50]\n"
              << "Environment: TASK_MANAGER_TOKEN is required\n";
}

bool readOption(int& index, int argc, char* argv[], std::string& value) {
    if (index + 1 >= argc) {
        return false;
    }
    value = argv[++index];
    return true;
}

std::string readEnvironment(const char* name) {
#ifdef _WIN32
    char* value = nullptr;
    size_t length = 0;
    if (_dupenv_s(&value, &length, name) != 0 || value == nullptr) {
        return "";
    }
    std::string result(value);
    std::free(value);
    return result;
#else
    const char* value = std::getenv(name);
    return value != nullptr ? std::string(value) : std::string();
#endif
}

} // namespace

int main(int argc, char* argv[]) {
    TaskManagerOptions options;
    options.authToken = readEnvironment("TASK_MANAGER_TOKEN");

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        std::string value;
        if (arg == "--help" || arg == "-h") {
            printUsage(argv[0]);
            return 0;
        } else if (arg == "--host" && readOption(i, argc, argv, value)) {
            options.host = value;
        } else if (arg == "--port" && readOption(i, argc, argv, value)) {
            options.port = std::stoi(value);
        } else if (arg == "--task-bin" && readOption(i, argc, argv, value)) {
            options.taskBinary = value;
        } else if (arg == "--config-dir" && readOption(i, argc, argv, value)) {
            options.configDir = value;
        } else if (arg == "--max-tasks" && readOption(i, argc, argv, value)) {
            options.maxTasks = std::stoi(value);
        } else {
            std::cerr << "Unknown or incomplete option: " << arg << "\n";
            printUsage(argv[0]);
            return 1;
        }
    }

    if (options.port <= 0 || options.port > 65535) {
        std::cerr << "Invalid port: " << options.port << "\n";
        return 1;
    }
    if (options.maxTasks <= 0) {
        std::cerr << "Invalid max task count: " << options.maxTasks << "\n";
        return 1;
    }
    if (options.authToken.empty()) {
        std::cerr << "TASK_MANAGER_TOKEN is required\n";
        return 1;
    }

    TaskManager manager(options);
    return manager.run() ? 0 : 1;
}
