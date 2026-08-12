# TaskManager MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deployable TaskManager service for TASK that can generate per-camera ini files and manage single-camera TASK child processes through HTTP.

**Architecture:** TaskManager is a lightweight C++ HTTP service on port 7000. It does not embed `Detech`; it starts and stops independent `TASK` processes so long-running video loops are isolated. The existing TASK runtime gains a `headless=true` config flag so server/container execution does not require an OpenCV display window.

**Tech Stack:** C++17, cpp-httplib, JsonCpp, CMake, Docker, Python unittest integration test.

---

### Task 1: API Contract Test

**Files:**
- Create: `TASK/tests/test_taskmanager_api.py`

- [x] Write an integration test that launches `TaskManager` with a fake TASK binary.
- [x] Verify the test fails before implementation because no `TaskManager` executable exists.

### Task 2: TaskManager C++ Service

**Files:**
- Create: `TASK/src/TaskManager.h`
- Create: `TASK/src/TaskManager.cpp`
- Create: `TASK/src/TaskManagerMain.cpp`

- [ ] Implement `/health`.
- [ ] Implement `/config/generate`.
- [ ] Implement `/task/start`.
- [ ] Implement `/task/stop`.
- [ ] Implement `/task/status`.
- [ ] Implement `/task/list`.
- [ ] Use child processes instead of in-memory `Detech` instances.

### Task 3: Headless TASK Runtime

**Files:**
- Modify: `TASK/src/Config.h`
- Modify: `TASK/src/ConfigParser.cpp`
- Modify: `TASK/src/Detech.cpp`

- [ ] Add config defaults.
- [ ] Parse `headless=true`.
- [ ] Skip OpenCV window creation, display, key polling, and destroy calls in headless mode.

### Task 4: Build And Container Files

**Files:**
- Modify: `TASK/CMakeLists.txt`
- Create: `TASK/Dockerfile.taskmanager`
- Create: `TASK/docker-compose.yml`

- [ ] Add a `TaskManager` executable target.
- [ ] Allow building TaskManager without the full AI/video runtime dependency set.
- [ ] Add a Linux container for TaskManager on port 7000.

### Task 5: Verification

**Files:**
- Test: `TASK/tests/test_taskmanager_api.py`

- [ ] Build the TaskManager binary.
- [ ] Run the Python integration test with `TASK_MANAGER_BIN` pointing to the built binary.
- [ ] Report any build/deployment blockers separately from code completion.
