import * as assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const src = (relativePath: string) =>
  fileURLToPath(new URL(`../src/views/dashboard/monitor/${relativePath}`, import.meta.url))

const read = (relativePath: string) => readFileSync(src(relativePath), 'utf8')

const dashboardPath = src('index.vue')
const dataSourcePath = src('useDashboardData.ts')

const dashboard = readFileSync(dashboardPath, 'utf8')
const dataSource = existsSync(dataSourcePath) ? readFileSync(dataSourcePath, 'utf8') : ''
const header = read('components/Header.vue')
const sidebar = read('components/Sidebar.vue')
const videoMonitor = read('components/VideoMonitor.vue')
const alarmPanel = read('components/AlarmPanel.vue')

assert.ok(
  existsSync(dataSourcePath),
  'The monitor dashboard should centralize statistics and alert polling in useDashboardData.ts.',
)

assert.match(
  dataSource,
  /queryAlarmList[\s\S]*getDashboardStatistics|getDashboardStatistics[\s\S]*queryAlarmList/,
  'The shared dashboard data source should fetch both alarm list and dashboard statistics.',
)

assert.match(
  dataSource,
  /lastUpdatedAt[\s\S]*lastUpdatedText[\s\S]*dashboardHealth|dashboardHealth[\s\S]*lastUpdatedAt[\s\S]*lastUpdatedText/,
  'The shared dashboard data source should expose last update time and API-derived health.',
)

assert.match(dashboard, /useDashboardData/, 'The dashboard page should consume the shared data source.')
assert.doesNotMatch(
  dashboard,
  /import\s*\{[^}]*queryAlarmList|getDashboardStatistics[^}]*\}\s*from\s*['"]@\/api\/device\/calculate['"]/,
  'The page should not poll statistics or alerts directly once the shared data source owns refresh.',
)
assert.doesNotMatch(dashboard, /loadAlarmList|loadTodayAlarmCount/, 'The page should not keep local polling loaders.')
assert.doesNotMatch(sidebar, /getDashboardStatistics|loadStatistics|statisticsTimer/, 'Sidebar statistics should come from props.')
assert.doesNotMatch(videoMonitor, /queryAlarmList|loadAlertRecords|recordTimer/, 'Video alert records should come from props.')

assert.match(dashboard, /:dashboard-health="dashboardHealth"/, 'Header should receive real dashboard health.')
assert.match(dashboard, /:last-updated-text="lastUpdatedText"/, 'Header should receive the shared last update text.')
assert.match(header, /dashboardHealth/, 'Header should expose a dashboardHealth prop.')
assert.doesNotMatch(header, />\s*大屏模式\s*<[\s\S]*>\s*在线\s*</, 'Header should not present static online status.')
assert.match(header, /health-status--online[\s\S]*health-status--degraded[\s\S]*health-status--offline/, 'Header should style real health states.')
assert.match(
  header,
  /@media\s*\(max-width:\s*1440px\)[\s\S]*@media\s*\(max-width:\s*1180px\)[\s\S]*@media\s*\(max-width:\s*767px\)/,
  'Header should adapt across desktop, compact browser, and mobile breakpoints.',
)

assert.match(alarmPanel, /getDispositionStatus/, 'Alarm panel should derive a read-only disposition status.')
assert.match(alarmPanel, /getDispositionStatusText/, 'Alarm panel should render disposition text.')
assert.match(alarmPanel, /disposition-tag/, 'Alarm panel should display the disposition tag.')
for (const statusText of ['未确认', '已确认', '已处理', '误报']) {
  assert.match(alarmPanel, new RegExp(statusText), `Alarm panel should include ${statusText} disposition text.`)
}

assert.match(videoMonitor, /type DashboardAiStatus/, 'Video slots should track a lightweight AI stream status.')
assert.match(videoMonitor, /aiStatus\??:\s*DashboardAiStatus/, 'DashboardVideoSlot should include aiStatus.')
assert.match(videoMonitor, /getAiStatusText/, 'Video monitor should map AI stream status to visible labels.')
assert.match(videoMonitor, /ai-status-tag/, 'Video monitor should render an AI status tag in each populated slot.')
for (const statusText of ['原始', 'AI', '回退', '无 AI 流']) {
  assert.match(videoMonitor, new RegExp(statusText), `Video monitor should include ${statusText} AI status text.`)
}
assert.match(
  videoMonitor,
  /preferAi[\s\S]*aiStatus[\s\S]*fallback|fallback[\s\S]*aiStatus[\s\S]*preferAi/,
  'AI status should distinguish preferred AI streams from fallback playback.',
)
