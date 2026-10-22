/**
 * 页面品牌展示层：监听整个文档，把文本节点与提示类属性里的运行时品牌标识替换为 yFeiEye 写法。
 *
 * 只改 DOM 展示，不改数据：组件状态、接口请求、复制按钮与表单值仍是真实值。
 * 可编辑区域、输入框内容、脚本样式和代码编辑器保留原文——编辑器会把 DOM 变化当作文档修改回写。
 * 需要从 DOM 读回原文时（例如 v-html 渲染的代码块复制）请用 getRawTextContent。
 */
import { hasRuntimeBrand, toBrandDisplayText } from '@/utils/brandDisplay'

/** 只做展示替换的属性；value 等表单值属于真实数据，不在此列 */
const DISPLAY_ATTRIBUTES = ['placeholder', 'title', 'alt', 'aria-label']
/** 需要局部关闭替换时给容器加 data-brand-raw（文本与属性都保留原文） */
const RAW_SELECTOR = '[data-brand-raw]'
/** 文本保留原文的元素；其自身的 placeholder 等属性仍会替换 */
const RAW_TEXT_SELECTOR = 'script, style, noscript, textarea, .CodeMirror'

const maskedTexts = new WeakMap<Text, { raw: string; masked: string }>()
let observer: MutationObserver | null = null

function isUntouchable(el: Element): boolean {
  return (el as HTMLElement).isContentEditable || !!el.closest(RAW_SELECTOR)
}

function maskText(node: Text) {
  const value = node.nodeValue ?? ''
  const entry = maskedTexts.get(node)
  if (entry && entry.masked === value)
    return
  const parent = node.parentElement
  if (!hasRuntimeBrand(value) || !parent || isUntouchable(parent) || parent.closest(RAW_TEXT_SELECTOR)) {
    maskedTexts.delete(node)
    return
  }
  const masked = toBrandDisplayText(value)
  maskedTexts.set(node, { raw: value, masked })
  node.nodeValue = masked
}

function maskAttributes(el: Element) {
  for (const name of DISPLAY_ATTRIBUTES) {
    const value = el.getAttribute(name)
    if (value && hasRuntimeBrand(value))
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
  const rootEl = root as Element
  if (isUntouchable(rootEl))
    return
  maskAttributes(rootEl)
  if (rootEl.closest(RAW_TEXT_SELECTOR))
    return
  const walker = document.createTreeWalker(rootEl, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      if (node.nodeType === Node.TEXT_NODE)
        return NodeFilter.FILTER_ACCEPT
      const el = node as Element
      if ((el as HTMLElement).isContentEditable || el.matches(RAW_SELECTOR))
        return NodeFilter.FILTER_REJECT
      maskAttributes(el)
      return el.matches(RAW_TEXT_SELECTOR) ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_SKIP
    },
  })
  for (let node = walker.nextNode(); node; node = walker.nextNode())
    maskText(node as Text)
}

function onMutations(mutations: MutationRecord[]) {
  for (const mutation of mutations) {
    if (mutation.type === 'characterData') {
      maskText(mutation.target as Text)
    }
    else if (mutation.type === 'attributes') {
      const el = mutation.target as Element
      if (!isUntouchable(el))
        maskAttributes(el)
    }
    else {
      mutation.addedNodes.forEach(maskTree)
    }
  }
}

/** 读取节点的原始文本（未经品牌展示替换），用法同 textContent */
export function getRawTextContent(node: Node | null | undefined): string {
  if (!node)
    return ''
  const rawOf = (text: Text) => {
    const entry = maskedTexts.get(text)
    return entry && entry.masked === text.nodeValue ? entry.raw : text.nodeValue ?? ''
  }
  if (node.nodeType === Node.TEXT_NODE)
    return rawOf(node as Text)
  let text = ''
  const walker = document.createTreeWalker(node, NodeFilter.SHOW_TEXT)
  for (let current = walker.nextNode(); current; current = walker.nextNode())
    text += rawOf(current as Text)
  return text
}

export function setupBrandDisplay() {
  if (observer || typeof MutationObserver === 'undefined')
    return
  maskTree(document.documentElement)
  observer = new MutationObserver(onMutations)
  observer.observe(document.documentElement, {
    subtree: true,
    childList: true,
    characterData: true,
    attributes: true,
    attributeFilter: DISPLAY_ATTRIBUTES,
  })
}
