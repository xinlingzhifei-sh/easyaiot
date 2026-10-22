/**
 * 页面品牌展示层（与 WEB/src/logics/brandDisplay.ts 同一规则，PANEL 独立部署故单独一份）。
 *
 * 容器名、镜像名、安装路径、环境变量等运行时标识仍沿用 easyaiot，只在显示到页面时换成
 * yFeiEye 写法（EASYAIOT_ROOT → YFEIEYE_ROOT）。只改 DOM 展示：接口数据、复制内容与表单值仍是真实值。
 */

const RUNTIME_BRAND_TEST = /easyaiot/i
const RUNTIME_BRAND_PATTERN = /easyaiot/gi
/** 只做展示替换的属性；value 等表单值属于真实数据，不在此列 */
const DISPLAY_ATTRIBUTES = ['placeholder', 'title', 'alt', 'aria-label']
/** 文本保留原文的元素；需要局部关闭替换时给容器加 data-brand-raw */
const RAW_SELECTOR = 'script, style, noscript, textarea, [data-brand-raw]'

let observer: MutationObserver | null = null

export function toBrandDisplayText(text: string): string {
  if (!RUNTIME_BRAND_TEST.test(text))
    return text
  return text.replace(RUNTIME_BRAND_PATTERN, match => (match === 'EASYAIOT' ? 'YFEIEYE' : 'yFeiEye'))
}

function maskText(node: Text) {
  const value = node.nodeValue ?? ''
  const parent = node.parentElement
  if (!RUNTIME_BRAND_TEST.test(value) || !parent || parent.isContentEditable || parent.closest(RAW_SELECTOR))
    return
  node.nodeValue = toBrandDisplayText(value)
}

function maskAttributes(el: Element) {
  if ((el as HTMLElement).isContentEditable || el.closest('[data-brand-raw]'))
    return
  for (const name of DISPLAY_ATTRIBUTES) {
    const value = el.getAttribute(name)
    if (value && RUNTIME_BRAND_TEST.test(value))
      el.setAttribute(name, toBrandDisplayText(value))
  }
}

function maskTree(root: Node) {
  if (root.nodeType === Node.TEXT_NODE) {
    maskText(root as Text)
    return
  }
  if (root.nodeType !== Node.ELEMENT_NODE)
    return
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT)
  maskAttributes(root as Element)
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    if (node.nodeType === Node.TEXT_NODE)
      maskText(node as Text)
    else
      maskAttributes(node as Element)
  }
}

export function setupBrandDisplay() {
  if (observer || typeof MutationObserver === 'undefined')
    return
  maskTree(document.documentElement)
  observer = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
      if (mutation.type === 'characterData')
        maskText(mutation.target as Text)
      else if (mutation.type === 'attributes')
        maskAttributes(mutation.target as Element)
      else
        mutation.addedNodes.forEach(maskTree)
    }
  })
  observer.observe(document.documentElement, {
    subtree: true,
    childList: true,
    characterData: true,
    attributes: true,
    attributeFilter: DISPLAY_ATTRIBUTES,
  })
}
