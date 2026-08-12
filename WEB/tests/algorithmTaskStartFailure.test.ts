import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const algorithmTaskService = readFileSync(
  fileURLToPath(new URL('../../VIDEO/app/services/algorithm_task_service.py', import.meta.url)),
  'utf8',
)

assert.match(
  algorithmTaskService,
  /success,\s*msg,\s*is_running\s*=\s*start_task_services\(task_id,\s*task\)/,
  'Starting an algorithm task should use the launcher service result as the source of truth.',
)

assert.match(
  algorithmTaskService,
  /else:\s*\n\s*service_message\s*=\s*msg[\s\S]*task\.is_enabled\s*=\s*False[\s\S]*task\.run_status\s*=\s*'stopped'[\s\S]*db\.session\.commit\(\)[\s\S]*raise RuntimeError\(service_message\)/,
  'If the launcher fails, the API must not leave the task marked enabled and successful.',
)

assert.match(
  algorithmTaskService,
  /except Exception as e:\s*\n\s*logger\.warning\(f"启动任务 \{task_id\} 的服务时出错:[\s\S]*task\.is_enabled\s*=\s*False[\s\S]*task\.run_status\s*=\s*'stopped'[\s\S]*db\.session\.commit\(\)[\s\S]*raise RuntimeError\(service_message\)/,
  'Launcher exceptions must also clear the enabled/running state and fail the start request.',
)
