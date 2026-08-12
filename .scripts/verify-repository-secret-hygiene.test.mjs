import assert from 'node:assert/strict';
import test from 'node:test';

import {
  formatFinding,
  isForbiddenTrackedPath,
  scanText,
} from './verify-repository-secret-hygiene.mjs';

test('识别常见服务令牌且诊断不回显值', () => {
  const secret = ['eyJ', 'a'.repeat(24), '.', 'b'.repeat(24), '.', 'c'.repeat(24)].join('');
  const [result] = scanText(`AUTH_TOKEN=${secret}`, 'config.txt');

  assert.equal(result.kind, 'jwt');
  assert.doesNotMatch(formatFinding(result), new RegExp(secret));
});

test('跟踪中的环境文件不得包含敏感字面量', () => {
  const findings = scanText(`
PUBLIC_URL=https://example.invalid
SERVICE_PASSWORD=repository-secret-value
EMPTY_TOKEN=
SAFE_SECRET=CHANGE_ME
RUNTIME_KEY=\${RUNTIME_KEY}
`, 'WEB/.env.production');

  assert.deepEqual(
    findings.map(result => result.kind),
    ['tracked-env-secret-literal'],
  );
});

test('运行时与部署产物路径不得继续受 Git 跟踪', () => {
  assert.equal(isForbiddenTrackedPath('deploy-packages/release/app.js'), true);
  assert.equal(isForbiddenTrackedPath('.scripts/docker/fuxa_data/appdata/settings.js'), true);
  assert.equal(isForbiddenTrackedPath('VIDEO/data/face_db/milvus_lite.db'), true);
  assert.equal(isForbiddenTrackedPath('VIDEO/.env.prod'), true);
  assert.equal(isForbiddenTrackedPath('VIDEO/.env.prod.example'), false);
  assert.equal(isForbiddenTrackedPath('WEB/src/main.ts'), false);
});
