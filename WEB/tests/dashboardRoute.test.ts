import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const dashboardRoute = readFileSync(resolve('src/router/routes/modules/dashboard.ts'), 'utf8')

assert.match(
  dashboardRoute,
  /name:\s*['"]DashboardPage['"][\s\S]*fullContent:\s*true/,
  'The dashboard home page should render in full-content mode so the default menu, header, and top frame are hidden.',
)
