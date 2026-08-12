export interface DeviceDetectionRegion {
  id: number
  device_id: string
  region_name: string
  region_type: 'polygon' | 'line' | 'rectangle'
  points: Array<{ x: number; y: number }>
  image_id?: number
  image_path?: string
  color: string
  opacity: number
  is_enabled: boolean
  sort_order: number
  model_ids?: number[]
  minStaySeconds?: number
  inertiaFrames?: number
  loiteringSeconds?: number
}

declare global {
  interface Window {
    __alertReviewE2EApiCalls?: Array<{ name: string; payload?: unknown }>
  }
}

const imageUrl = '/scripts/fixtures/alert-review-workbench-e2e/region-snapshot.svg'
const protectedImageUrl = '/video/camera/cam-east-gate/snapshot-image/tenants/1/cameras/cam-east-gate/region-snapshot.jpg'

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

export async function getDeviceRegions(deviceId: string) {
  record('getDeviceRegions', deviceId)
  return {
    code: 0,
    msg: 'ok',
    data: regions.map(cloneRegion),
  }
}

export async function updateDeviceRegion(regionId: number, data: Partial<DeviceDetectionRegion>) {
  record('updateDeviceRegion', { regionId, data })
  regions = regions.map(region =>
    region.id === regionId
      ? {
          ...region,
          ...data,
          id: region.id,
          device_id: region.device_id,
          image_id: region.image_id,
          image_path: region.image_path,
          points: data.points || region.points,
          model_ids: data.model_ids || region.model_ids || [],
        }
      : region,
  )
  const updated = regions.find(region => region.id === regionId) || regions[0]
  return {
    code: 0,
    msg: 'ok',
    data: cloneRegion(updated),
  }
}

export async function createDeviceRegion(deviceId: string, data: Partial<DeviceDetectionRegion>) {
  record('createDeviceRegion', { deviceId, data })
  const created: DeviceDetectionRegion = {
    ...regions[0],
    ...data,
    id: 802,
    device_id: deviceId,
    region_name: data.region_name || 'gate-zone',
    region_type: data.region_type || 'polygon',
    points: data.points || regions[0].points,
    color: data.color || '#2f80ed',
    opacity: data.opacity ?? 0.35,
    is_enabled: data.is_enabled ?? true,
    sort_order: data.sort_order ?? 1,
    model_ids: data.model_ids || [],
  }
  regions = [...regions, created]
  return {
    code: 0,
    msg: 'ok',
    data: cloneRegion(created),
  }
}

export async function deleteDeviceRegion(regionId: number) {
  record('deleteDeviceRegion', regionId)
  regions = regions.filter(region => region.id !== regionId)
  return {
    code: 0,
    msg: 'ok',
  }
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
        width: 1,
        height: 1,
      },
    },
  }
}

export async function loadProtectedSnapshotObjectUrl(url: string) {
  record('loadProtectedSnapshotObjectUrl', url)
  return imageUrl
}
