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

test('识别历史硬编码的平台默认口令且诊断不回显值', () => {
  // 拆开拼接，避免测试文件自身被仓库扫描命中
  const legacy = ['iot', '45722414822'].join('');
  const findings = scanText(`url = "postgresql://postgres:${legacy}@db/x"`, 'tool.py');

  assert.deepEqual(findings.map(result => result.kind), ['known-default-credential']);
  assert.doesNotMatch(formatFinding(findings[0]), new RegExp(legacy));
});

test('配置与脚本中的凭据不得写字面量默认值', () => {
  const findings = scanText([
    '      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD:-literal-pass}',
    '      - NACOS_PASSWORD=${NACOS_PASSWORD:?NACOS_PASSWORD is required}',
    '      - REDIS_PASSWORD=${REDIS_PASSWORD:-}',
    '      - TOKEN_DIR=${YFEIEYE_NGINX_SECRET_DIR:-/etc/nginx/secrets}',
    '      - SECRET=${MINIO_SECRET_KEY:-$(read_env_var MINIO_SECRET_KEY)}',
  ].join('\n'), 'MODULE/docker-compose.yml');
  assert.deepEqual(
    findings.map(result => [result.line, result.kind]),
    [[1, 'secret-literal-fallback']],
  );

  const spring = scanText([
    'spring:',
    '  password: ${NACOS_PASSWORD:literal-pass}',
    '  password: ${POSTGRES_PASSWORD}',
  ].join('\n'), 'MODULE/src/main/resources/bootstrap-local.yaml');
  assert.deepEqual(spring.map(result => result.line), [2]);

  const shell = scanText('PG_PASS="${POSTGRES_PASSWORD:-literal-pass}"\n', 'MODULE/run.sh');
  assert.deepEqual(shell.map(result => result.kind), ['secret-literal-fallback']);
});

test('连接串内嵌口令只接受占位符', () => {
  const findings = scanText([
    'DATABASE_URL=postgresql://postgres:literal-pass@localhost:15432/iot-video20',
    'DATABASE_URL=postgresql://postgres:CHANGE_ME@localhost:5432/iot-video20?sslmode=disable',
    'DATABASE_URL=postgresql://postgres:${POSTGRES_PASSWORD}@PostgresSQL:5432/iot-ai20',
    '# DATABASE_URL=postgresql://用户名:密码@主机:端口/数据库名',
    'VIDEO_DB_URL=postgresql://postgres:<POSTGRES_PASSWORD>@127.0.0.1:5432/iot-video20',
  ].join('\n'), 'MODULE/env.example');

  assert.deepEqual(
    findings.map(result => [result.line, result.kind]),
    [[1, 'connection-string-credential']],
  );
});

test('YAML 中包括注释行在内的口令字面量都会被发现', () => {
  const findings = scanText([
    'spring:',
    '  redis:',
    '    password: literal-pass',
    '#    password: commented-pass',
    '  secret: ${ZLM_SECRET}',
    '    key-store-password: # 密钥库密码',
    '  js-tokens: 4.0.0',
  ].join('\n'), 'MODULE/src/main/resources/application-local.yaml');

  assert.deepEqual(
    findings.map(result => [result.line, result.kind]),
    [[3, 'yaml-secret-literal'], [4, 'yaml-secret-literal']],
  );
});

test('env.example 模板只允许占位符，审阅过的例外可显式标记', () => {
  const findings = scanText([
    'NACOS_USERNAME=CHANGE_ME',
    'NACOS_PASSWORD=literal-pass',
    'SECRET_KEY=your-rtc-secret-key-change-me',
    '# 演示种子数据；secret-hygiene: allow',
    'PASSWORD=32423',
  ].join('\n'), 'POST/env.example');

  assert.deepEqual(
    findings.map(result => [result.line, result.kind]),
    [[2, 'tracked-env-secret-literal']],
  );
});

test('配置与脚本中不得以 MinIO 出厂账号作默认值', () => {
  const findings = scanText('ACCESS_KEY="${RUSTFS_ACCESS_KEY:-minioadmin}"\n', '.scripts/docker/sync.sh');
  assert.deepEqual(findings.map(result => result.kind), ['minio-default-credential']);
  assert.deepEqual(scanText('Username: minioadmin\n', 'README.md'), []);
});

test('运行时与部署产物路径不得继续受 Git 跟踪', () => {
  assert.equal(isForbiddenTrackedPath('deploy-packages/release/app.js'), true);
  assert.equal(isForbiddenTrackedPath('.scripts/docker/fuxa_data/appdata/settings.js'), true);
  assert.equal(isForbiddenTrackedPath('VIDEO/data/face_db/milvus_lite.db'), true);
  assert.equal(isForbiddenTrackedPath('VIDEO/.env.prod'), true);
  assert.equal(isForbiddenTrackedPath('VIDEO/.env.prod.example'), false);
  assert.equal(isForbiddenTrackedPath('POST/.env.docker'), true);
  assert.equal(isForbiddenTrackedPath('POST/.env.docker.example'), false);
  assert.equal(isForbiddenTrackedPath('WEB/src/main.ts'), false);
});
