/**
 * 页面展示层品牌映射。
 *
 * 运行时真实的安装路径、配置键、服务名、镜像名仍沿用 easyaiot（改名会破坏已部署环境），
 * 只在展示到页面时替换为 yFeiEye 写法：/opt/easyaiot → /opt/yFeiEye，
 * easyaiot.edge.join-token → yFeiEye.edge.join-token，EASYAIOT_ROOT → YFEIEYE_ROOT。
 * 接口参数、复制内容、表单值与本地存储一律使用真实值，不要把这里的结果写回去。
 */

const RUNTIME_BRAND_TEST = /easyaiot/i
const RUNTIME_BRAND_PATTERN = /easyaiot/gi

export const DISPLAY_BRAND = 'yFeiEye'
export const DISPLAY_BRAND_UPPER = 'YFEIEYE'

export function hasRuntimeBrand(text: string | null | undefined): boolean {
  return !!text && RUNTIME_BRAND_TEST.test(text)
}

/** 把文本中的运行时品牌标识替换为展示写法；全大写（环境变量前缀）保持全大写 */
export function toBrandDisplayText(text: string): string {
  if (!hasRuntimeBrand(text))
    return text
  return text.replace(RUNTIME_BRAND_PATTERN, match => (match === 'EASYAIOT' ? DISPLAY_BRAND_UPPER : DISPLAY_BRAND))
}
