# Home Command Center UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the dashboard home screen as a calm, video-first command center that is intentional at 1920x1080, remains usable on standard desktop browsers, and becomes a task-ordered mobile experience at 390px.

**Architecture:** Keep every existing data source and interaction inside the current Vue components. Move summary metrics into a continuous header rail, leave the device tree as the left navigation rail, keep video as the flexible center workspace, and present alarms as a compact right queue. Responsive behavior is CSS-driven at 1180px and 767px so video/device/alarm business logic is not duplicated.

**Tech Stack:** Vue 3 SFC, TypeScript, Less, Ant Design Vue, Node.js built-in test runner, Vite.

---

## File map

- Modify `WEB/src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs`: encode desktop, standard-browser, and mobile layout contracts.
- Modify `WEB/src/views/dashboard/monitor/index.vue`: provide the page grid, design tokens, breakpoint behavior, and metric data wiring.
- Modify `WEB/src/views/dashboard/monitor/components/Header.vue`: render the compact global bar and continuous KPI rail.
- Modify `WEB/src/views/dashboard/monitor/components/Sidebar.vue`: remove duplicate overview cards and turn the component into a focused device navigator.
- Modify `WEB/src/views/dashboard/monitor/components/VideoMonitor.vue`: sharpen the video workspace hierarchy, controls, selected state, and active-event strip.
- Modify `WEB/src/views/dashboard/monitor/components/AlarmPanel.vue`: convert the alarm area into a compact priority queue that stacks cleanly on mobile.

### Task 1: Lock the responsive contract

**Files:**
- Modify: `WEB/src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs`

- [ ] **Step 1: Replace the old fixed-layout assertion with the new command-center contract**

```js
test('dashboard uses a video-first command-center grid with desktop and mobile breakpoints', () => {
  const dashboard = read('../index.vue')
  const sidebar = read('Sidebar.vue')
  const alarmPanel = read('AlarmPanel.vue')
  const topHeader = read('Header.vue')

  assert.match(dashboard, /grid-template-columns:\s*284px\s+minmax\(0,\s*1fr\)\s+328px;/)
  assert.match(dashboard, /@media\s*\(max-width:\s*1180px\)/)
  assert.match(dashboard, /@media\s*\(max-width:\s*767px\)/)
  assert.match(dashboard, /grid-template-areas:[\s\S]*"center"[\s\S]*"alarms"[\s\S]*"devices"/)
  assert.match(sidebar, /width:\s*100%;/)
  assert.match(alarmPanel, /width:\s*100%;/)
  assert.match(topHeader, /class="kpi-rail"/)
})
```

- [ ] **Step 2: Run the contract and confirm it fails before implementation**

Run: `cd WEB && node --test src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs`

Expected: FAIL because the fixed 350px/320px flex layout and old header are still present.

### Task 2: Build the global bar and KPI rail

**Files:**
- Modify: `WEB/src/views/dashboard/monitor/index.vue`
- Modify: `WEB/src/views/dashboard/monitor/components/Header.vue`

- [ ] **Step 1: Wire existing statistics into the header**

```vue
<MonitorHeader
  :active-videos="activeVideos"
  :statistics="statistics"
  :today-alarm-count="todayAlarmCount"
  :dashboard-health="dashboardHealth"
  :last-updated-text="lastUpdatedText"
  @admin-entry="releaseDashboardOverlay"
/>
```

- [ ] **Step 2: Render one compact global row followed by a continuous KPI rail**

```vue
<header class="monitor-header">
  <div class="global-bar">...</div>
  <div class="kpi-rail" aria-label="实时运行指标">
    <div v-for="metric in kpiMetrics" :key="metric.label" class="kpi-item">
      <span class="kpi-label">{{ metric.label }}</span>
      <strong class="kpi-value">{{ metric.value }}</strong>
      <span class="kpi-unit">{{ metric.unit }}</span>
    </div>
  </div>
</header>
```

- [ ] **Step 3: Add typed metric computation without new API calls**

```ts
const props = withDefaults(defineProps<{
  activeVideos?: any[]
  statistics?: DashboardStatistics
  todayAlarmCount?: number
  dashboardHealth?: DashboardHealth
  lastUpdatedText?: string
}>(), {
  activeVideos: () => [],
  todayAlarmCount: 0,
})

const kpiMetrics = computed(() => [
  { label: '在线设备', value: props.statistics?.cameraCount ?? 0, unit: '台' },
  { label: '实时画面', value: props.activeVideos.length, unit: '路' },
  { label: '启用算法', value: props.statistics?.algorithmCount ?? 0, unit: '项' },
  { label: '今日告警', value: props.todayAlarmCount, unit: '次', tone: 'attention' },
  { label: '模型资源', value: props.statistics?.modelCount ?? 0, unit: '个' },
])
```

- [ ] **Step 4: Style the two-row header at 72px + 64px desktop and compact it on smaller screens**

Use the spec tokens `#061017`, `#0A1720`, `#0E202B`, `#1D3541`, `#26D5E4`, `#3DDC97`, `#F5B942`, and `#FF5F6D`. Keep borders at 1px, radii at 0-4px, and avoid decorative gradients.

- [ ] **Step 5: Re-run the contract**

Run: `cd WEB && node --test src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs`

Expected: The KPI assertion passes; the page-grid assertions still fail until Task 3.

### Task 3: Establish the video-first page grid and side rails

**Files:**
- Modify: `WEB/src/views/dashboard/monitor/index.vue`
- Modify: `WEB/src/views/dashboard/monitor/components/Sidebar.vue`
- Modify: `WEB/src/views/dashboard/monitor/components/AlarmPanel.vue`

- [ ] **Step 1: Assign semantic grid areas in the page template**

```vue
<MonitorSidebar class="monitor-devices" ... />
<div class="monitor-center">...</div>
<AlarmPanel class="monitor-alarms" ... />
```

- [ ] **Step 2: Apply the three responsive layouts**

```less
.monitor-content {
  display: grid;
  grid-template-columns: 284px minmax(0, 1fr) 328px;
  grid-template-areas: 'devices center alarms';
}

@media (max-width: 1180px) {
  .monitor-content {
    grid-template-columns: minmax(220px, 26vw) minmax(0, 1fr);
    grid-template-areas: 'devices center' 'alarms center';
  }
}

@media (max-width: 767px) {
  .monitor-content {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: 'center' 'alarms' 'devices';
    overflow-y: auto;
  }
}
```

- [ ] **Step 3: Remove the duplicate 2x2 overview-card section from Sidebar**

Keep the existing `BasicTree`, loading, search, selection, GB28181 loading, and emit behavior unchanged. Replace the section decoration with one hairline container and a compact channel-count badge.

- [ ] **Step 4: Restyle AlarmPanel as a compact queue**

Keep image URL resolution and click-to-play behavior unchanged. Reduce per-row surface decoration, use amber for unconfirmed items, green/cyan for resolved/confirmed states, and let the list become a finite-height section on mobile.

- [ ] **Step 5: Run the responsive contract**

Run: `cd WEB && node --test src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs`

Expected: PASS.

### Task 4: Refine the central video workspace

**Files:**
- Modify: `WEB/src/views/dashboard/monitor/components/VideoMonitor.vue`

- [ ] **Step 1: Preserve all handlers and change only presentation copy and hierarchy**

Change the lower strip heading from `告警录像` to `活跃事件`, keep each item calling `handleRecordClick(record)`, and keep every stable `data-testid`.

- [ ] **Step 2: Replace decorative background/card effects with structural surfaces**

```less
.video-monitor {
  background: var(--dashboard-panel);
  border: 1px solid var(--dashboard-border);
  border-radius: 2px;
  box-shadow: none;
}

.monitor-content {
  gap: 6px;
  padding: 6px;
  background: #03090d;
}

.video-window.active {
  border-color: var(--dashboard-cyan);
  box-shadow: inset 0 0 0 1px var(--dashboard-cyan);
}
```

- [ ] **Step 3: Keep controls usable at standard desktop and mobile widths**

At 1180px allow the toolbar to wrap without truncating labels. At 767px hide secondary time/location text, show one video cell as the main task surface, keep the split controls horizontally scrollable, and place the active-event strip below it.

- [ ] **Step 4: Run the contract after the style rewrite**

Run: `cd WEB && node --test src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs`

Expected: PASS, including stable-selector and admin-entry assertions.

### Task 5: Verify production readiness and visual breakpoints

**Files:**
- Verify only: all files above

- [ ] **Step 1: Run formatting integrity**

Run: `git diff --check`

Expected: no whitespace errors.

- [ ] **Step 2: Run the focused contract**

Run: `cd WEB && node --test src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs`

Expected: 5 tests pass.

- [ ] **Step 3: Run TypeScript validation**

Run: `cd WEB && pnpm type:check`

Expected: no new errors in the five modified dashboard files. If repository baseline errors remain elsewhere, record them separately with exact paths.

- [ ] **Step 4: Build the production frontend**

Run: `cd WEB && pnpm build`

Expected: Vite build and post-build script complete successfully.

- [ ] **Step 5: Inspect the page at the three target viewports**

Use 1920x1080, 1440x900, and 390x844. Verify no horizontal overflow; the center video remains the largest desktop region; mobile order is video, alarms, devices; controls remain reachable; and the existing device/video/alarm interactions retain their selectors.

- [ ] **Step 6: Commit only the command-center implementation**

```bash
git add WEB/src/views/dashboard/monitor/index.vue \
  WEB/src/views/dashboard/monitor/components/Header.vue \
  WEB/src/views/dashboard/monitor/components/Sidebar.vue \
  WEB/src/views/dashboard/monitor/components/VideoMonitor.vue \
  WEB/src/views/dashboard/monitor/components/AlarmPanel.vue \
  WEB/src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs \
  docs/superpowers/plans/2026-07-19-home-command-center-ui.md
git commit -m "feat(dashboard): rebuild command center experience"
```

Expected: unrelated dirty-worktree files remain unstaged.
