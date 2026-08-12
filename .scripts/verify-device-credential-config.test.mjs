import assert from 'node:assert/strict';
import test from 'node:test';

import {
  collectComposeServiceEnvironmentNames,
  collectYamlCredentialReferences,
  findServicesMissingEnvironment,
  formatFinding,
  scanComposeText,
  scanYamlText,
} from './verify-device-credential-config.mjs';

test('YAML 凭据必须引用环境变量', () => {
  const findings = scanYamlText(`
spring:
  datasource:
    username: repository-user
    password: repository-secret
    url: jdbc:TAOS-RS://db:6041/iot?user=root&password=url-secret
  redis:
    password: \${REDIS_PASSWORD}
  cloud:
    nacos:
      password: "\${NACOS_PASSWORD}"
  kafka:
    ssl:
      trust-store-password: repository-secret
sip:
  register-password-auth: true
`, 'application-prod.yaml');

  assert.deepEqual(
    findings.map(finding => finding.path),
    [
      'spring.datasource.username',
      'spring.datasource.password',
      'spring.datasource.url.password',
      'spring.kafka.ssl.trust-store-password',
    ],
  );
});

test('Compose 凭据赋值和 URL 内密码不得使用字面量', () => {
  const findings = scanComposeText(`
services:
  app:
    environment:
      - SAFE_PASSWORD=\${SAFE_PASSWORD:?SAFE_PASSWORD is required}
      - LEAKED_TOKEN=token-value
      - JDBC_URL=jdbc:TAOS-RS://db:6041/iot?user=root&password=url-secret
      - PRIVATE_URL=https://service-user:service-secret@example.invalid/api
`, 'docker-compose.yml');

  assert.deepEqual(
    findings.map(finding => finding.path),
    ['LEAKED_TOKEN', 'JDBC_URL.password', 'PRIVATE_URL.userinfo'],
  );
});

test('诊断信息不得回显凭据值', () => {
  const [finding] = scanYamlText(
    'service:\n  secret: must-never-appear-in-output\n',
    'application.yaml',
  );
  const output = formatFinding(finding);

  assert.match(output, /application\.yaml:2 service\.secret/);
  assert.doesNotMatch(output, /must-never-appear-in-output/);
});

test('可提取模块凭据引用及容器环境变量传递关系', () => {
  const references = collectYamlCredentialReferences(`
spring:
  datasource:
    username: \${POSTGRES_USER:postgres}
    password: \${POSTGRES_PASSWORD}
`, 'application-local.yaml');
  assert.deepEqual(
    references.map(reference => reference.name),
    ['POSTGRES_USER', 'POSTGRES_PASSWORD'],
  );

  const services = collectComposeServiceEnvironmentNames(`
services:
  iot-demo:
    environment:
      - POSTGRES_USER=\${POSTGRES_USER:-postgres}
      - POSTGRES_PASSWORD=\${POSTGRES_PASSWORD:?required}
`);
  assert.deepEqual(
    [...services.get('iot-demo')],
    ['POSTGRES_USER', 'POSTGRES_PASSWORD'],
  );
});

test('RPC token must reach every DEVICE caller and server', () => {
  const services = collectComposeServiceEnvironmentNames(`
services:
  iot-gateway:
    environment:
      - IOT_RPC_INTERNAL_TOKEN=\${IOT_RPC_INTERNAL_TOKEN:?required}
  iot-system:
    environment:
      - POSTGRES_PASSWORD=\${POSTGRES_PASSWORD:?required}
`);

  assert.deepEqual(
    findServicesMissingEnvironment(
      services,
      ['iot-gateway', 'iot-system'],
      'IOT_RPC_INTERNAL_TOKEN',
    ),
    ['iot-system'],
  );
});
