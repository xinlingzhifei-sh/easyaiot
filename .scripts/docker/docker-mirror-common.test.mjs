import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const dockerDir = dirname(fileURLToPath(import.meta.url));
const commonScript = join(dockerDir, 'docker_mirror_common.sh').replaceAll('\\', '/');
const bash = process.platform === 'win32' && existsSync('C:/Git/bin/bash.exe')
  ? 'C:/Git/bin/bash.exe'
  : 'bash';

function runBash(source) {
  const result = spawnSync(bash, ['-c', source], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return result.stdout.trim();
}

// 用 shell 函数替身宿主机信号，只验证决策
function dnsDecision({ auto, force = '', loopback }) {
  return runBash(`
source '${commonScript}'
_host_resolv_uses_loopback_dns() { return ${loopback ? 0 : 1}; }
EASYAIOT_DOCKER_DNS_AUTO='${auto}' EASYAIOT_FORCE_DOCKER_DNS='${force}' _should_configure_docker_dns && echo yes || echo no
`);
}

test('DNS auto-config stays on by default for existing callers', () => {
  assert.equal(dnsDecision({ auto: '', loopback: true }), 'yes');
  assert.equal(dnsDecision({ auto: '1', loopback: true }), 'yes');
});

test('EASYAIOT_DOCKER_DNS_AUTO=0 keeps daemon.json DNS untouched (install_linux.sh default)', () => {
  assert.equal(dnsDecision({ auto: '0', loopback: true }), 'no');
});

test('explicit EASYAIOT_FORCE_DOCKER_DNS=1 still wins over the auto switch', () => {
  assert.equal(dnsDecision({ auto: '0', force: '1', loopback: false }), 'yes');
});

function pullFallback(image) {
  return runBash(`
source '${commonScript}'
print_info() { :; }
print_success() { echo "ok: $1"; }
docker() {
  case "$1" in
    image) return 1 ;;
    pull) echo "pull \${@: -1}"; [[ "\${@: -1}" == docker.m.daocloud.io/* ]] ;;
    tag) echo "tag $2 $3"; return 0 ;;
  esac
}
DOCKER_MIRROR=https://docker.m.daocloud.io DOCKER_MIRROR_FALLBACKS=docker.m.daocloud.io
docker_pull_with_mirror_fallback '${image}'
echo "rc=$?"
`).split('\n');
}

test('mirror fallback re-tags tag references to the original name', () => {
  assert.deepEqual(pullFallback('nodered/node-red:5.0.9'), [
    'pull nodered/node-red:5.0.9',
    'pull docker.m.daocloud.io/nodered/node-red:5.0.9',
    'tag docker.m.daocloud.io/nodered/node-red:5.0.9 nodered/node-red:5.0.9',
    'ok: 已拉取并标记为 nodered/node-red:5.0.9',
    'rc=0',
  ]);
});

test('mirror fallback accepts digest references without trying to tag them', () => {
  const digest = 'zlmediakit/zlmediakit@sha256:ddc5127d4fab7be622b34148ac8869ca6310298acd33440f1236e771a3c5ceee';
  assert.deepEqual(pullFallback(digest), [
    `pull ${digest}`,
    `pull docker.m.daocloud.io/${digest}`,
    `ok: 已按 digest 拉取 docker.m.daocloud.io/${digest}`,
    'rc=0',
  ]);
});
