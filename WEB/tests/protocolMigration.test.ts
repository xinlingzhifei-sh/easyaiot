import * as assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const protocolSql = readFileSync(
  resolve('../DEVICE/iot-device/iot-device-biz/src/main/resources/sql/protocol.sql'),
  'utf8',
)

assert.match(
  protocolSql,
  /CREATE TABLE IF NOT EXISTS public\.protocol/i,
  'The device service should ship an idempotent migration for the protocol table.',
)

for (const column of [
  'id',
  'app_id',
  'product_identification',
  'protocol_name',
  'protocol_identification',
  'protocol_version',
  'protocol_type',
  'protocol_voice',
  'class_name',
  'file_path',
  'content',
  'status',
  'create_by',
  'create_time',
  'update_by',
  'update_time',
  'remark',
  'tenant_id',
]) {
  assert.match(protocolSql, new RegExp(`\\b${column}\\b`, 'i'), `protocol.sql should define ${column}.`)
}

assert.match(
  protocolSql,
  /CREATE INDEX IF NOT EXISTS idx_protocol_tenant_id/i,
  'The protocol table should have a tenant_id index for tenant-filtered menu queries.',
)
