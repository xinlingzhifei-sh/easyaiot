import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const defaultLayout = readFileSync(resolve('src/layouts/default/index.vue'), 'utf8')

assert.match(
  defaultLayout,
  /import\s+\{\s*useFullContent\s*\}\s+from\s+['"]@\/hooks\/web\/useFullContent['"]/,
  'The default layout should read the full-content state.',
)

assert.match(
  defaultLayout,
  /const\s+\{\s*getFullContent\s*\}\s*=\s*useFullContent\(\)/,
  'The default layout should expose getFullContent to the template and layout class logic.',
)

assert.match(
  defaultLayout,
  /if\s*\(\s*!unref\(getFullContent\)\s*&&\s*\(unref\(getIsMixSidebar\)\s*\|\|\s*unref\(getShowMenu\)\)\s*\)/,
  'Full-content pages should not reserve sider layout width.',
)

assert.match(
  defaultLayout,
  /if\s*\(\s*!unref\(getFullContent\)\s*&&\s*!unref\(getShowMenu\)\s*&&\s*unref\(getAutoCollapse\)\s*\)/,
  'Full-content pages should not add collapsed-tab layout chrome.',
)

assert.match(
  defaultLayout,
  /<LayoutHeader\s+v-if="!getFullContent && getShowFullHeaderRef"\s+fixed\s*\/>/,
  'Full-content pages should not render the top layout header.',
)

assert.match(
  defaultLayout,
  /<LayoutSideBar\s+v-if="!getFullContent && \(getShowSidebar \|\| getIsMobile\)"\s*\/>/,
  'Full-content pages should not render the sidebar.',
)
