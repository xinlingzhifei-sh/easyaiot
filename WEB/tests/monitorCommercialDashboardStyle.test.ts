import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const read = (path: string) => readFileSync(resolve(path), 'utf8')

const dashboard = read('src/views/dashboard/monitor/index.vue')
const header = read('src/views/dashboard/monitor/components/Header.vue')
const sidebar = read('src/views/dashboard/monitor/components/Sidebar.vue')
const videoMonitor = read('src/views/dashboard/monitor/components/VideoMonitor.vue')
const alarmPanel = read('src/views/dashboard/monitor/components/AlarmPanel.vue')
const brandingStorage = read('src/utils/platformBrandingStorage.ts')

assert.match(dashboard, /<MonitorSidebar/)
assert.match(dashboard, /<VideoMonitor/)
assert.match(dashboard, /<AlarmPanel/)

assert.match(dashboard, /:today-alarm-count="todayAlarmCount"/)
assert.match(header, /dashboardTitle/)
assert.match(header, /实时画面/)
assert.match(header, /今日告警/)
assert.match(header, /props\.activeVideos\.length/)
assert.match(brandingStorage, /DEFAULT_DASHBOARD_TITLE = '逸飞AI智眼监控平台'/)
assert.match(brandingStorage, /resolveDashboardTitle\(data\.dashboardTitle, defaults\.dashboardTitle\)/)
assert.match(brandingStorage, /value\.trim\(\) === LEGACY_DEFAULT_DASHBOARD_TITLE/)
assert.doesNotMatch(brandingStorage, new RegExp('云边端一体' + '算法预警监控平台'))

assert.match(dashboard, /--dashboard-bg:/)
assert.match(dashboard, /--dashboard-panel:/)
assert.match(dashboard, /--dashboard-border:/)
assert.match(dashboard, /--dashboard-accent:/)
assert.doesNotMatch(dashboard, /linear-gradient\(25deg,\s*#0f2249/)

for (const source of [sidebar, videoMonitor, alarmPanel]) {
  assert.match(source, /var\(--dashboard-panel\)/)
  assert.match(source, /var\(--dashboard-border\)/)
}

for (const source of [dashboard, header, sidebar, videoMonitor, alarmPanel]) {
  assert.doesNotMatch(source, /司法|一级戒备|在押/)
}
