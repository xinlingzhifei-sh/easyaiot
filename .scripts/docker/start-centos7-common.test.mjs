import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

// CentOS 7 单服务启动器共用 start_centos7_common.sh；用 shell 函数替身 docker / systemctl，
// 不调用真实命令。
const dockerDir = dirname(fileURLToPath(import.meta.url));
const bash = process.platform === 'win32' && existsSync('C:/Git/bin/bash.exe')
  ? 'C:/Git/bin/bash.exe'
  : 'bash';
const starters = ['minio', 'rustfs', 'nodered', 'fuxa', 'postgresql'].map(name => `start_${name}_centos7.sh`);

function runCommon(body) {
  const result = spawnSync(bash, ['-c', `
SCRIPT_DIR=.
MIN_DOCKER_MAJOR=20
source ./start_centos7_common.sh
${body}`], { cwd: dockerDir, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return result.stdout.replace(/\x1b\[[0-9;]*m/g, '').trim().split(/\r?\n/);
}

test('network name comes from deploy-profiles.json, not per-script constants', () => {
  assert.deepEqual(runCommon('echo "$NETWORK_NAME"'), ['yfeieye-network']);
  for (const file of starters) {
    const source = readFileSync(join(dockerDir, file), 'utf8');
    assert.doesNotMatch(source, /^NETWORK_NAME=/m, file);
    assert.doesNotMatch(source, /easyaiot-network/, file);
    assert.match(source, /^source "\$\{SCRIPT_DIR\}\/start_centos7_common\.sh"$/m, file);
    assert.doesNotMatch(source, /^(print_info|check_docker|upgrade_docker_ce_centos7|ensure_network)\(\)/m, file);
  }
});

test('every starter still prints its help through the shared library', () => {
  for (const file of starters) {
    const result = spawnSync(bash, [file, '--help'], { cwd: dockerDir, encoding: 'utf8' });
    assert.equal(result.status, 0, `${file}: ${result.stderr}`);
    assert.match(result.stdout, /用法/, file);
  }
});

test('is_docker_too_old flags CentOS 7 docker 1.13 and anything below 20', () => {
  assert.deepEqual(runCommon(`
for v in 1.13.1 19.03.15 20.10.7; do is_docker_too_old "$v" && echo "$v:old" || echo "$v:ok"; done`),
  ['1.13.1:old', '19.03.15:old', '20.10.7:ok']);
});

test('_pull_from_registry tags every configured local name after a mirror pull', () => {
  assert.deepEqual(runCommon(`
docker() { echo "docker $*"; return 0; }
NODERED_IMAGE=nodered/node-red:5.0.9
NODERED_IMAGE_ALIAS=nodered/node-red:alias
C7_PULL_TAG_VARS="NODERED_IMAGE NODERED_IMAGE_ALIAS"
_pull_from_registry docker.m.daocloud.io/nodered/node-red:5.0.9 | grep -E '^docker (tag|pull)|已拉取'`), [
    'docker pull docker.m.daocloud.io/nodered/node-red:5.0.9',
    'docker tag docker.m.daocloud.io/nodered/node-red:5.0.9 nodered/node-red:5.0.9',
    'docker tag docker.m.daocloud.io/nodered/node-red:5.0.9 nodered/node-red:alias',
    '[SUCCESS] 已拉取并标记为 nodered/node-red:5.0.9',
  ]);
});

test('ensure_modern_docker upgrades only when needed and refuses with --no-upgrade-docker', () => {
  const lines = runCommon(`
C7_SERVICE_LABEL=MinIO
upgrade_docker_ce_centos7() { echo upgraded; }
_c7_is_root() { return 0; }
get_docker_server_version() { echo 26.1.4; }
ensure_modern_docker | grep -E 'upgraded|可拉取'
get_docker_server_version() { echo 1.13.1; }
ensure_modern_docker | grep -E '^upgraded$'
( SKIP_DOCKER_UPGRADE=true; ensure_modern_docker >/dev/null ); echo "skip_rc=$?"
FORCE_DOCKER_UPGRADE=true; get_docker_server_version() { echo 26.1.4; }; ensure_modern_docker | grep -E '^upgraded$'
`);
  assert.deepEqual(lines, ['[SUCCESS] Docker 版本可拉取 MinIO 镜像', 'upgraded', 'skip_rc=1', 'upgraded']);
});

test('ensure_network creates the manifest network only when missing', () => {
  assert.deepEqual(runCommon(`
docker() { case "$1 $2" in "network ls") printf '%s\\n' bridge $EXISTING;; "network create") echo "create $3" >> "$LOG";; esac; }
LOG=$(mktemp)
EXISTING=yfeieye-network ensure_network >/dev/null; grep -c create "$LOG" || true
EXISTING= ensure_network >/dev/null; cat "$LOG"; rm -f "$LOG"`), ['0', 'create yfeieye-network']);
});

test('check_centos7 builds the firewalld hint from the configured ports', () => {
  const lines = runCommon(`
FORCE_OS_CHECK=false
systemctl() { return 0; }
C7_FIREWALL_PORTS="9000 9001"
check_centos7 | grep 'firewall-cmd'`);
  assert.deepEqual(lines, ['[INFO]   sudo firewall-cmd --permanent --add-port=9000/tcp --add-port=9001/tcp && sudo firewall-cmd --reload']);
});
