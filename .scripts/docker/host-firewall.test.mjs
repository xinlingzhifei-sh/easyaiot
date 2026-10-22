import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const dockerDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(dockerDir, '..', '..');
const firewallScript = join(dockerDir, 'host_firewall_common.sh').replaceAll('\\', '/');
const bash = process.platform === 'win32' && existsSync('C:/Git/bin/bash.exe')
  ? 'C:/Git/bin/bash.exe'
  : 'bash';

// 用 shell 函数替身 ufw / docker / id；被测函数会丢弃 ufw 的输出，替身经 fd 3 记录 allow 调用
function run({ uid = 0, ufwActive = true, subnets = '172.24.0.0/16 ', skip = '', hasUfw = true, ports = '' }) {
  const source = `
id() { echo ${uid}; }
${hasUfw ? `ufw() { case "$1" in status) ${ufwActive ? "echo 'Status: active'" : "echo 'Status: inactive'"};; allow) echo "UFW $*" >&3;; esac; }` : 'command() { [ "$2" = ufw ] && return 1; builtin command "$@"; }'}
docker() { printf '%s' '${subnets}'; }
source '${firewallScript}'
EASYAIOT_SKIP_FIREWALL='${skip}' allow_docker_network_to_host_ports yfeieye-network ${ports} 3>&1 >/dev/null 2>&1 | grep '^UFW' || true
`;
  const result = spawnSync(bash, ['-c', source], { cwd: repoRoot, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return result.stdout.trim();
}

test('active ufw: allows only the Docker network subnet to the host service ports', () => {
  assert.equal(
    run({}),
    'UFW allow proto tcp from 172.24.0.0/16 to any port 48080,48099,6000,5000,1985,8080,1984 comment yfeieye: yfeieye-network to host services',
  );
});

test('explicit port list overrides the default', () => {
  assert.match(run({ ports: '6000' }), /from 172\.24\.0\.0\/16 to any port 6000 /);
});

test('IPv6 subnets are skipped, IPv4 kept', () => {
  const out = run({ subnets: '172.24.0.0/16 fd00:dead::/64 ' });
  assert.equal(out.split('\n').length, 1);
  assert.match(out, /172\.24\.0\.0\/16/);
});

test('inactive ufw, missing ufw, non-root, opt-out or unknown subnet: no rule added', () => {
  assert.equal(run({ ufwActive: false }), '');
  assert.equal(run({ hasUfw: false }), '');
  assert.equal(run({ uid: 1000 }), '');
  assert.equal(run({ skip: '1' }), '');
  assert.equal(run({ subnets: '' }), '');
});

test('WEB installer opens the firewall after bringing the container up', () => {
  const text = readFileSync(join(repoRoot, 'WEB/install_linux.sh'), 'utf8');
  assert.match(text, /source "\$\{YFEIEYE_ROOT\}\/\.scripts\/docker\/host_firewall_common\.sh"/);
  assert.equal((text.match(/allow_docker_network_to_host_ports yfeieye-network/g) || []).length, 2);
});

test('default port list covers every host-gateway upstream in the WEB nginx config', () => {
  const conf = readFileSync(join(repoRoot, 'WEB/conf/nginx.conf'), 'utf8');
  const script = readFileSync(join(dockerDir, 'host_firewall_common.sh'), 'utf8');
  const allowed = new Set(script.match(/YFEIEYE_HOST_SERVICE_PORTS="([^"]+)"/)[1].split(','));
  // zlm-host 走 docker-proxy 发布端口（DNAT 走 FORWARD 链），不受 INPUT 规则影响
  const hostNetworkUpstreams = /\b(?:gateway|system-host|video-host|ai-host|srs-host|rtc-host):(\d+)/g;
  for (const [, port] of conf.matchAll(hostNetworkUpstreams)) {
    assert.ok(allowed.has(port), `nginx 上游端口 ${port} 未在 YFEIEYE_HOST_SERVICE_PORTS 中`);
  }
});
