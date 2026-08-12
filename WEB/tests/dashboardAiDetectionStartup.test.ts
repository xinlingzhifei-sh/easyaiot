import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const videoMonitor = readFileSync(
  fileURLToPath(
    new URL('../src/views/dashboard/monitor/components/VideoMonitor.vue', import.meta.url),
  ),
  'utf8',
)
const splitScreenMonitor = readFileSync(
  fileURLToPath(
    new URL('../src/views/camera/components/SplitScreenMonitor/MonitorPanel.vue', import.meta.url),
  ),
  'utf8',
)

const algorithmTaskApi = readFileSync(
  fileURLToPath(new URL('../src/api/device/algorithm_task.ts', import.meta.url)),
  'utf8',
)

assert.match(
  videoMonitor,
  /data-testid="monitor-ai-toggle"|<a-checkbox|AI_TOGGLE_STORAGE_KEY|readPersistedEnableAi|persistEnableAi|const enableAi/,
  'The dashboard video monitor should expose one AI recognition toggle in the video header.',
)

assert.match(
  videoMonitor,
  /dashboardGuardTask|startDashboardGuardTask|stopDashboardGuardTask|listAlgorithmTasks|createAlgorithmTask/,
  'Enabling AI from the dashboard video monitor should start or stop backend recognition tasks.',
)

assert.match(
  videoMonitor,
  /async function ensureDashboardAiRecognitionForDevices[\s\S]*await startDashboardGuardTask\(\{ scope, api: dashboardGuardApi \}\)/,
  'The dashboard AI toggle should call the shared backend recognition task starter.',
)

assert.match(
  algorithmTaskApi,
  /listAlgorithmTasks[\s\S]*options:\s*AlgorithmTaskRequestOptions[\s\S]*errorMessageMode:\s*options\.errorMessageMode/,
  'Algorithm task list calls should allow dashboard auto-start to suppress the global generic error toast.',
)

assert.match(
  algorithmTaskApi,
  /startAlgorithmTask[\s\S]*options:\s*AlgorithmTaskRequestOptions[\s\S]*errorMessageMode:\s*options\.errorMessageMode[\s\S]*stopAlgorithmTask[\s\S]*options:\s*AlgorithmTaskRequestOptions[\s\S]*errorMessageMode:\s*options\.errorMessageMode/,
  'Algorithm task start/stop calls should allow dashboard auto-start to suppress the global generic error toast.',
)

assert.match(
  videoMonitor,
  /listAlgorithmTasks\(params[\s\S]*errorMessageMode:\s*'none'[\s\S]*startAlgorithmTask\(taskId,[\s\S]*errorMessageMode:\s*'none'[\s\S]*stopAlgorithmTask\(taskId,[\s\S]*errorMessageMode:\s*'none'/,
  'Dashboard AI auto-start should handle task API errors itself instead of also showing the global 系统异常 toast after refresh.',
)

assert.match(
  videoMonitor,
  /watch\(enableAi,[\s\S]*ensureDashboardAiRecognitionForVisibleDevices\(\)[\s\S]*reloadAllVideosForAiToggle\(\)/,
  'Toggling AI on should start recognition for visible videos and then reload them through the AI stream path.',
)

assert.match(
  videoMonitor,
  /return pickDirectPlayUrls\(dev,\s*enableAi\.value\)/,
  'Dashboard direct camera playback should prefer the AI stream only after the video header toggle is enabled.',
)

assert.match(
  videoMonitor,
  /resolveGbChannelPlayUrls\([\s\S]*\{\s*enableAi:\s*enableAi\.value,\s*synced:\s*playDevice\s*\}/,
  'Dashboard GB28181 channel playback should request the AI stream only after the video header toggle is enabled.',
)

assert.match(
  videoMonitor,
  /loadGbChannelSyncedDevice[\s\S]*if \(!id \|\| id\.startsWith\('gb_ch_'\)\) return ''/,
  'GB28181 AI recognition should resolve the synced camera device and never start tasks against gb_ch_* UI ids.',
)

assert.match(
  videoMonitor,
  /AI_PLAY_FALLBACK_MS|preferAi|fallbackUrl/,
  'Dashboard AI playback should keep the original stream fallback while the algorithm stream warms up.',
)

const pendingAiUpgradeStart = videoMonitor.indexOf('schedulePendingAiStreamUpgrade(')
assert.notEqual(
  pendingAiUpgradeStart,
  -1,
  'Dashboard playback should support upgrading a warm original stream to the AI stream.',
)
const pendingAiUpgradeEnd = videoMonitor.indexOf('if (!hasFallback)', pendingAiUpgradeStart)
const pendingAiUpgradeBlock = videoMonitor.slice(pendingAiUpgradeStart, pendingAiUpgradeEnd)
assert.match(
  pendingAiUpgradeBlock,
  /url:\s*pendingAi,[\s\S]*aiStatus:\s*'ai'/,
  'Upgrading the dashboard player to the pending AI stream should update the visible AI status.',
)

assert.match(
  splitScreenMonitor,
  /const enableAi = ref\(false\)/,
  'Split-screen monitoring should not enable AI streams by default; original live video must be the safe default.',
)

assert.match(
  splitScreenMonitor,
  /startDashboardGuardTask|stopDashboardGuardTask|listAlgorithmTasks|createAlgorithmTask/,
  'Enabling AI from split-screen monitoring should start or stop backend recognition tasks.',
)

assert.match(
  splitScreenMonitor,
  /async function ensureSplitScreenAiRecognitionForDevices[\s\S]*await startDashboardGuardTask\(\{ scope, api: splitScreenGuardApi \}\)/,
  'Split-screen AI toggle should call the shared backend recognition task starter.',
)

assert.match(
  splitScreenMonitor,
  /watch\(enableAi,[\s\S]*ensureSplitScreenAiRecognitionForVisibleDevices\(\)[\s\S]*reloadAllPlayCellsForAiToggle\(\)/,
  'Toggling AI on in split-screen should start recognition for visible cells and then reload them through the AI stream path.',
)

assert.match(
  splitScreenMonitor,
  /loadGbChannelSyncedDevice[\s\S]*if \(!id \|\| id\.startsWith\('gb_ch_'\)\) return ''/,
  'Split-screen GB28181 AI recognition should resolve the synced camera device and never start tasks against gb_ch_* UI ids.',
)
