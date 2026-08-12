import * as assert from 'node:assert/strict'

import {
  type DashboardGuardTaskApi,
  startDashboardGuardTask,
} from '../src/views/dashboard/monitor/dashboardGuardTask'

const deviceId = 'gb28181_44010200493432381460_34020000001320000001'
const calls = {
  create: 0,
  start: 0,
  stop: 0,
}

const api: DashboardGuardTaskApi = {
  listAlgorithmTasks: async () => ({
    code: 0,
    data: [
      {
        id: 1,
        task_name: `[Dashboard Guard] test (monitor-ai:${deviceId})`,
        task_type: 'realtime',
        device_ids: [deviceId],
        model_ids: [1],
        alert_event_enabled: true,
        is_enabled: true,
      },
    ],
    total: 1,
  }),
  createAlgorithmTask: async () => {
    calls.create += 1
    return { id: 2 }
  },
  startAlgorithmTask: async () => {
    calls.start += 1
  },
  stopAlgorithmTask: async () => {
    calls.stop += 1
  },
}

async function main() {
  const result = await startDashboardGuardTask({
    scope: {
      key: `split-screen-ai:${deviceId}`,
      label: '[GB28181] test',
      deviceIds: [deviceId],
    },
    api,
  })

  assert.deepEqual(result, { taskId: 1, reusedExistingTask: true })
  assert.deepEqual(calls, { create: 0, start: 0, stop: 0 })
}

void main()
