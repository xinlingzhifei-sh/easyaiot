export type DatasetAuditValue = 1 | 2;

export interface DatasetAuditRecord {
  id: number | string;
  name: string;
  version?: string;
  coverPath?: string;
  description?: string;
  datasetType: number;
}

export interface DatasetAuditForm {
  audit: DatasetAuditValue;
  reason: string;
}

export function validateDatasetAuditForm(form: DatasetAuditForm): string {
  if (form.audit === 2 && !form.reason.trim()) {
    return '请输入驳回原因';
  }
  return '';
}

export function buildDatasetAuditPayload(record: DatasetAuditRecord, form: DatasetAuditForm) {
  return {
    id: record.id,
    name: record.name,
    version: record.version || 'v1.0.0',
    coverPath: record.coverPath || '',
    description: record.description || '',
    datasetType: record.datasetType,
    audit: form.audit,
    reason: form.audit === 2 ? form.reason.trim() : '',
  };
}
