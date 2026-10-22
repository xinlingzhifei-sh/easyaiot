import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { hasRuntimeBrand, toBrandDisplayText } from '../src/utils/brandDisplay'

const read = (relative: string) => readFileSync(fileURLToPath(new URL(relative, import.meta.url)), 'utf8')

// 展示写法与节点存储标签的既有展示值（STORAGE_TAG_DISPLAY_DEFAULTS）保持一致
assert.equal(toBrandDisplayText('/opt/easyaiot/RUNTIME'), '/opt/yFeiEye/RUNTIME')
assert.equal(toBrandDisplayText('/mnt/easyaiot-media'), '/mnt/yFeiEye-media')
assert.equal(toBrandDisplayText('easyaiot-playbacks'), 'yFeiEye-playbacks')
assert.equal(toBrandDisplayText('easyaiot.edge.join-token'), 'yFeiEye.edge.join-token')
assert.equal(toBrandDisplayText('journalctl -u easyaiot-node-agent'), 'journalctl -u yFeiEye-node-agent')
assert.equal(toBrandDisplayText('请检查 INSTALL_SCRIPT / EASYAIOT_ROOT'), '请检查 INSTALL_SCRIPT / YFEIEYE_ROOT')
assert.equal(toBrandDisplayText('EasyAIoT 与 easyaiot'), 'yFeiEye 与 yFeiEye')
assert.equal(toBrandDisplayText('卸载 /opt/easyaiot/RUNTIME 与 /opt/easyaiot/VIDEO'), '卸载 /opt/yFeiEye/RUNTIME 与 /opt/yFeiEye/VIDEO')
assert.equal(toBrandDisplayText('无品牌文本'), '无品牌文本')
// 连续调用不受全局正则 lastIndex 影响
assert.equal(hasRuntimeBrand('easyaiot'), true)
assert.equal(hasRuntimeBrand('easyaiot'), true)
assert.equal(hasRuntimeBrand('yFeiEye'), false)
assert.equal(hasRuntimeBrand(null), false)

const logic = read('../src/logics/brandDisplay.ts')
assert.match(logic, /const DISPLAY_ATTRIBUTES = \['placeholder', 'title', 'alt', 'aria-label'\]/)
assert.doesNotMatch(logic, /DISPLAY_ATTRIBUTES = \[[^\]]*'value'/, 'Form values are real data and must never be rewritten.')
assert.match(logic, /isContentEditable/, 'Editable regions must keep their real text.')
assert.match(logic, /RAW_TEXT_SELECTOR = 'script, style, noscript, textarea, \.CodeMirror'/)
assert.match(logic, /export function getRawTextContent/)

const main = read('../src/main.ts')
assert.match(main, /import \{ setupBrandDisplay \} from '@\/logics\/brandDisplay'/)
assert.match(main, /setupBrandDisplay\(\)/)

// v-html 代码块的复制按钮从 DOM 取文本，必须读回原文，否则复制出的命令会带上展示写法
const assistant = read('../src/components/PlatformAssistant/index.vue')
assert.match(assistant, /getRawTextContent\(btn\.closest\('\.md-code'\)\?\.querySelector\('pre code'\)\)/)
assert.doesNotMatch(assistant, /querySelector\('pre code'\)\?\.textContent/)

const panelMain = read('../../PANEL/ui/src/main.ts')
assert.match(panelMain, /setupBrandDisplay\(\)/)
const panelLogic = read('../../PANEL/ui/src/brandDisplay.ts')
assert.match(panelLogic, /const DISPLAY_ATTRIBUTES = \['placeholder', 'title', 'alt', 'aria-label'\]/)
assert.match(panelLogic, /isContentEditable/)

console.log('brand display tests OK')
