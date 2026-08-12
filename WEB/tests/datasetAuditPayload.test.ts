import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import {
  buildDatasetAuditPayload,
  validateDatasetAuditForm,
} from '../src/views/dataset/components/DatasetAuditModal/auditPayload'

const baseRecord = {
  id: 1001,
  name: '巡检图片集',
  version: 'v1.0.0',
  coverPath: '/upload/dataset-cover.png',
  description: '用于巡检缺陷训练',
  datasetType: 0,
  audit: 0,
  reason: '旧原因',
}

assert.equal(
  validateDatasetAuditForm({ audit: 1, reason: '' }),
  '',
  'Approving a dataset should not require a reason.',
)

assert.equal(
  validateDatasetAuditForm({ audit: 2, reason: '  图片不清晰  ' }),
  '',
  'Rejecting a dataset should pass when a reason is provided.',
)

assert.equal(
  validateDatasetAuditForm({ audit: 2, reason: '   ' }),
  '请输入驳回原因',
  'Rejecting a dataset should require a non-empty reason.',
)

assert.deepEqual(
  buildDatasetAuditPayload(baseRecord, { audit: 1, reason: '无需保留' }),
  {
    id: 1001,
    name: '巡检图片集',
    version: 'v1.0.0',
    coverPath: '/upload/dataset-cover.png',
    description: '用于巡检缺陷训练',
    datasetType: 0,
    audit: 1,
    reason: '',
  },
  'Approving should clear any rejection reason.',
)

assert.deepEqual(
  buildDatasetAuditPayload(baseRecord, { audit: 2, reason: '  图片不清晰  ' }),
  {
    id: 1001,
    name: '巡检图片集',
    version: 'v1.0.0',
    coverPath: '/upload/dataset-cover.png',
    description: '用于巡检缺陷训练',
    datasetType: 0,
    audit: 2,
    reason: '图片不清晰',
  },
  'Rejecting should trim and submit the rejection reason.',
)

const listPath = fileURLToPath(
  new URL('../src/views/dataset/components/DatasetList/index.vue', import.meta.url),
)
const cardPath = fileURLToPath(
  new URL('../src/views/dataset/components/DatasetCardList/index.vue', import.meta.url),
)
const listSource = readFileSync(listPath, 'utf8')
const cardSource = readFileSync(cardPath, 'utf8')

assert.match(
  listSource,
  /DatasetAuditModal/,
  'The dataset list should mount the audit modal.',
)

assert.match(
  listSource,
  /title:\s*'审核'/,
  'The dataset table action column should expose an audit action.',
)

assert.match(
  listSource,
  /@verif="handleAudit"/,
  'The dataset card-list audit event should open the same audit modal.',
)

assert.match(
  cardSource,
  /ant-design:audit-outlined/,
  'The dataset card should expose a visible audit icon action.',
)

assert.match(
  cardSource,
  /defineEmits\(\[[^\]]*'verif'[^\]]*\]\)/,
  'The dataset card list should declare the audit event it emits.',
)
