const assert = require('node:assert/strict')
const { readFileSync } = require('node:fs')
const { join } = require('node:path')
const test = require('node:test')

const monitorDir = __dirname
const read = (file) => readFileSync(join(monitorDir, file), 'utf8')

test('video monitor toolbar keeps labels readable while allowing controls to wrap', () => {
  const source = read('VideoMonitor.vue')

  assert.match(source, /\.monitor-header\s*\{[\s\S]*height:\s*auto;/)
  assert.match(source, /\.monitor-header\s*\{[\s\S]*flex-wrap:\s*wrap;/)
  assert.match(source, /\.header-title\s*\{[\s\S]*white-space:\s*nowrap;/)
  assert.match(source, /\.header-time\s*\{[\s\S]*white-space:\s*nowrap;/)
  assert.match(source, /\.header-location\s*\{[\s\S]*white-space:\s*nowrap;/)
  assert.match(source, /\.split-toolbar\s*\{[\s\S]*flex-wrap:\s*wrap;/)
})

test('dashboard uses a video-first command-center grid with desktop and mobile breakpoints', () => {
  const dashboard = read('../index.vue')
  const sidebar = read('Sidebar.vue')
  const alarmPanel = read('AlarmPanel.vue')
  const topHeader = read('Header.vue')

  assert.match(dashboard, /grid-template-columns:\s*284px\s+minmax\(0,\s*1fr\)\s+328px;/)
  assert.match(dashboard, /grid-template-areas:\s*'devices center alarms';/)
  assert.match(dashboard, /@media\s*\(max-width:\s*1180px\)/)
  assert.match(dashboard, /@media\s*\(max-width:\s*767px\)/)
  assert.match(dashboard, /grid-template-areas:\s*'center'\s*'alarms'\s*'devices';/)
  assert.match(sidebar, /\.monitor-sidebar\s*\{[\s\S]*width:\s*100%;/)
  assert.match(alarmPanel, /\.alarm-panel\s*\{[\s\S]*width:\s*100%;/)
  assert.match(topHeader, /class="global-bar"/)
  assert.match(topHeader, /class="kpi-rail"/)
  assert.match(topHeader, /v-for="metric in kpiMetrics"/)
})

test('mobile command center promotes one active video and keeps controls reachable', () => {
  const videoMonitor = read('VideoMonitor.vue')

  assert.match(videoMonitor, /@media\s*\(max-width:\s*767px\)/)
  assert.match(videoMonitor, /\.video-window\s*\{[\s\S]*display:\s*none;/)
  assert.match(videoMonitor, /\.video-window\.active\s*\{[\s\S]*display:\s*block(?:\s*!important)?;/)
  assert.match(videoMonitor, /\.split-toolbar\s*\{[\s\S]*overflow-x:\s*auto;/)
  assert.match(videoMonitor, />活跃事件</)
})

test('big-screen admin entry releases the overlay so normal navigation can be clicked', () => {
  const dashboard = read('../index.vue')
  const topHeader = read('Header.vue')

  assert.match(dashboard, /function\s+releaseDashboardOverlay\(\)/)
  assert.match(dashboard, /defineExpose\(\{\s*releaseDashboardOverlay\s*\}\)/)
  assert.match(dashboard, /\.monitor-dashboard--embedded/)
  assert.doesNotMatch(dashboard, /z-index:\s*9999;/)

  assert.match(topHeader, /<button[\s\S]*data-testid="monitor-admin-entry"/)
  assert.match(topHeader, /@click="handleGoToAdmin"/)
  assert.match(topHeader, /adminEntryLabel/)
  assert.match(topHeader, /emit\('admin-entry'\)/)
  assert.match(topHeader, /resolveAdminEntryTarget\(router\)/)
  assert.match(topHeader, /router\.push\(resolveAdminEntryTarget\(router\)\)/)
})

test('dashboard controls expose stable selectors and keep text rendering crisp', () => {
  const dashboard = read('../index.vue')
  const topHeader = read('Header.vue')
  const videoMonitor = read('VideoMonitor.vue')
  const sidebar = read('Sidebar.vue')

  assert.match(dashboard, /data-testid="monitor-dashboard"/)
  assert.match(topHeader, /data-testid="monitor-platform-title"/)
  assert.match(sidebar, /data-testid="monitor-sidebar"/)
  assert.match(videoMonitor, /data-testid="monitor-split-toolbar"/)
  assert.match(videoMonitor, /:data-testid="`monitor-split-\$\{layout\.value\}`"/)
  assert.match(videoMonitor, /data-testid="monitor-ai-toggle"/)

  assert.match(dashboard, /text-rendering:\s*geometricPrecision;/)
  assert.match(dashboard, /-webkit-font-smoothing:\s*antialiased;/)
  assert.doesNotMatch(dashboard, /filter:\s*blur\(/)
  assert.doesNotMatch(topHeader, /letter-spacing:\s*\.06rem;/)
})

test('default main nav items expose route-based test ids for smoke clicks', () => {
  const mixSider = read('../../../../layouts/default/sider/MixSider.vue')

  assert.match(mixSider, /function\s+normalizeMenuTestId\(path:\s*string\)/)
  assert.match(mixSider, /:data-testid="`main-nav-\$\{normalizeMenuTestId\(item\.path\)\}`"/)
  assert.match(mixSider, /:data-testid="`main-nav-label-\$\{normalizeMenuTestId\(item\.path\)\}`"/)
})
