#ifndef TASK_MANAGER_H
#define TASK_MANAGER_H

#include <chrono>
#include <map>
#include <mutex>
#include <string>

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#else
#include <sys/types.h>
#endif

struct TaskManagerOptions {
    std::string host{"0.0.0.0"};
    int port{7000};
    std::string taskBinary{"./TASK"};
    std::string configDir{"config/generated"};
    std::string authToken;
    int maxTasks{50};
};

struct TaskProcess {
    int taskId{0};
    std::string configPath;
    std::string status{"stopped"};
    int exitCode{0};
    std::chrono::system_clock::time_point startedAt{};

#ifdef _WIN32
    HANDLE processHandle{nullptr};
    DWORD pid{0};
#else
    pid_t pid{-1};
#endif
};

class TaskManager {
public:
    explicit TaskManager(TaskManagerOptions options);
    ~TaskManager();

    bool run();
    void stopAllTasks();

private:
    TaskManagerOptions options_;
    std::mutex tasksMutex_;
    std::map<int, TaskProcess> tasks_;

    std::string handleHealth();
    std::string handleGenerateConfig(const std::string& body, int& httpStatus);
    std::string handleStartTask(const std::string& body, int& httpStatus);
    std::string handleStopTask(const std::string& body, int& httpStatus);
    std::string handleTaskStatus(const std::string& taskIdText, int& httpStatus);
    std::string handleTaskList();
    std::string handleMetrics();

    bool startChildProcess(TaskProcess& task);
    bool stopChildProcess(TaskProcess& task);
    bool isProcessRunning(TaskProcess& task);
    void refreshTaskLocked(TaskProcess& task);
};

#endif // TASK_MANAGER_H
