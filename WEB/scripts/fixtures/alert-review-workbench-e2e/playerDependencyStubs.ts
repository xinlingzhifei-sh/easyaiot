export function copyText() {}
export function controlPTZ() {}
export function callOnvifPreset() {}
export function deleteOnvifPreset() {}
export async function queryOnvifPresets() {
  return []
}
export function setOnvifPreset() {}
export function addGbPreset() {}
export function callGbPreset() {}
export function controlGbFocus() {}
export function controlGbIris() {}
export function controlGbPtz() {}
export function deleteGbPreset() {}
export async function queryGbPreset() {
  return []
}
export async function startGbAudioBroadcast() {
  return {}
}
export async function stopGbAudioBroadcast() {
  return {}
}
export async function queryAlertRecord() {
  return null
}
export async function playByDeviceAndChannel() {
  return { data: { data: {} } }
}
export function getGb28181PlayIds() {
  return null
}
export function shouldPlayViaGb28181() {
  return false
}
export function formatCameraDeviceLabel() {
  return 'E2E camera'
}
export function pickWvpPlaySources() {
  return []
}
export const AI_PLAY_FALLBACK_MS = 2500
export const AI_STREAM_LOAD_TIMEOUT_SEC = 3
export const AI_STREAM_HEART_TIMEOUT_SEC = 8
export function isAiStreamPlayUrl() {
  return false
}
export function normalizeJessibucaPlayUrl(url: string) {
  return url
}
export async function pickDirectPlayUrls(record: Record<string, any>) {
  return { url: record?.http_stream || null }
}
export async function resolveGbChannelPlayUrls() {
  return { url: null }
}
export function schedulePendingAiStreamUpgrade() {}
export function rewriteStreamHostToPageHost(url: string) {
  return url
}
export function isProtectedStreamUrl() {
  return false
}
export async function signStreamUrl(url: string) {
  return url
}
export function clearTicketForUrl() {}
export function detectVideoCodecFromUrl() {
  return 'unknown'
}
export function normalizeVideoCodec(value?: string) {
  return value || 'unknown'
}
export function shouldUseWasmLivePlayer() {
  return false
}
