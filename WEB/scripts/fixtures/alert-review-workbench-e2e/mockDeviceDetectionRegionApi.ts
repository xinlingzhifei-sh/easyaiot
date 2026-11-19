import type { DeviceDetectionRegion, DeviceRegionSnapshot } from '@/api/device/device_detection_region'

declare global {
  interface Window {
    __alertReviewE2EApiCalls?: Array<{ name: string; payload?: unknown }>
  }
}

const imageUrl = '/scripts/fixtures/alert-review-workbench-e2e/region-snapshot.svg'
const protectedImageUrl = '/video/camera/cam-east-gate/snapshot-image/tenants/1/cameras/cam-east-gate/region-snapshot.jpg'

let revision = 8
let regions: DeviceDetectionRegion[] = [{
  id: 801,
  device_id: 'cam-east-gate',
  region_name: 'gate-zone',
  region_type: 'polygon',
  points: [
    { x: 0.1, y: 0.1 },
    { x: 0.9, y: 0.1 },
    { x: 0.9, y: 0.9 },
    { x: 0.1, y: 0.9 },
  ],
  image_id: 9001,
  image_path: imageUrl,
  color: '#2f80ed',
  opacity: 0.35,
  is_enabled: true,
  sort_order: 1,
  model_ids: [7],
  minStaySeconds: 15,
  inertiaFrames: 1,
  loiteringSeconds: 5,
}]

function record(name: string, payload?: unknown) {
  window.__alertReviewE2EApiCalls ||= []
  window.__alertReviewE2EApiCalls.push({ name, payload })
}

function cloneRegion(region: DeviceDetectionRegion) {
  return {
    ...region,
    points: region.points.map(point => ({ ...point })),
    model_ids: [...(region.model_ids || [])],
  }
}

export async function getDeviceRegionsSnapshot(deviceId: string, taskId: number): Promise<DeviceRegionSnapshot> {
  record('getDeviceRegionsSnapshot', { deviceId, taskId })
  return { device_id: deviceId, task_id: taskId, revision, regions: regions.map(cloneRegion) }
}

export async function replaceDeviceRegions(
  deviceId: string,
  taskId: number,
  expectedRevision: number,
  updated: DeviceDetectionRegion[],
) {
  record('replaceDeviceRegions', { deviceId, taskId, expectedRevision, regions: updated.map(cloneRegion) })
  if (expectedRevision !== revision)
    throw new Error('Unexpected region revision')
  regions = updated.map(cloneRegion)
  revision += 1
  return { data: { code: 0, revision, runtime_sync_status: 'applied', data: regions.map(cloneRegion) } }
}

export async function captureDeviceSnapshot(deviceId: string) {
  record('captureDeviceSnapshot', deviceId)
  return {
    data: {
      code: 0,
      msg: 'ok',
      data: {
        image_id: 9001,
        image_url: protectedImageUrl,
        width: 320,
        height: 180,
      },
    },
  }
}

export async function loadProtectedSnapshotObjectUrl(url: string) {
  record('loadProtectedSnapshotObjectUrl', url)
  return imageUrl
}
