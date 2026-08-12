import { buildModelDownloadUrl as buildModelDownloadUrlById } from '@/api/device/modelDownloadUrl';

export interface ModelDownloadRecord {
  id: number | string;
  model_path?: string | null;
  onnx_model_path?: string | null;
}

export function buildModelDownloadUrl(record: ModelDownloadRecord, apiBase?: string) {
  return buildModelDownloadUrlById(record.id, apiBase);
}
