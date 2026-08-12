import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const monitorDashboard = readFileSync(resolve('src/views/dashboard/monitor/index.vue'), 'utf8')

for (const selector of [
  '[class*="layout-header"]',
  '[class*="layout-multiple-header"]',
  '[class*="multiple-tabs"]',
  '[class*="layout-sider"]',
]) {
  assert.match(
    monitorDashboard,
    new RegExp(selector.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')),
    `The dashboard overlay style should hide ${selector} so no default white frame appears above the home page.`,
  )
}
