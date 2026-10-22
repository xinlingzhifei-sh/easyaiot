import assert from 'node:assert/strict';
import { chmodSync, cpSync, existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { after, before, test } from 'node:test';
import { fileURLToPath } from 'node:url';

// EL 系入口（CentOS / CentOS ARM / openEuler）共用 install_el_common.sh。
// 在临时副本里把转交目标 install_linux*.sh 换成打印参数的替身，PATH 里放 docker / systemctl /
// firewall-cmd / uname 替身，只验证入口契约，不安装任何东西。
const dockerDir = dirname(fileURLToPath(import.meta.url));
const bash = process.platform === 'win32' && existsSync('C:/Git/bin/bash.exe')
  ? 'C:/Git/bin/bash.exe'
  : 'bash';
let work;
let copyDocker;
let fakeBin;

function writeExec(path, body) {
  writeFileSync(path, `#!/bin/bash\n${body}\n`, 'utf8');
  chmodSync(path, 0o755);
}

before(() => {
  work = mkdtempSync(join(tmpdir(), 'install-el-common-'));
  copyDocker = join(work, '.scripts', 'docker');
  cpSync(dockerDir, copyDocker, { recursive: true, filter: src => basename(src) !== 'logs' });
  for (const target of ['install_linux.sh', 'install_linux_arm.sh']) {
    writeExec(join(copyDocker, target), `echo "delegate=${target} args=$* script=$EASYAIOT_INSTALL_SCRIPT label=$EASYAIOT_INSTALL_LABEL dns=\${DOCKER_DNS:-} agent=\${EASYAIOT_PLATFORM_AGENT_SCRIPT:-}"`);
  }
  fakeBin = join(work, 'bin');
  mkdirSync(fakeBin);
  writeExec(join(fakeBin, 'docker'), 'case "$1" in version) printf "Server:\\n Engine:\\n  Version: ${FAKE_DOCKER_VERSION:-26.1.4}\\n";; -v) echo "Docker version ${FAKE_DOCKER_VERSION:-26.1.4}, build x";; esac; exit 0');
  writeExec(join(fakeBin, 'systemctl'), 'exit 3');
  writeExec(join(fakeBin, 'uname'), 'if [ "$1" = "-m" ]; then echo "${FAKE_ARCH:-x86_64}"; else /usr/bin/uname "$@"; fi');
});

after(() => {
  if (work) rmSync(work, { recursive: true, force: true });
});

function runEntry(entry, args, env = {}) {
  const result = spawnSync(bash, [join(copyDocker, entry).replaceAll('\\', '/'), ...args], {
    cwd: copyDocker,
    encoding: 'utf8',
    env: { ...process.env, PATH: `${fakeBin}${process.platform === 'win32' ? ';' : ':'}${process.env.PATH}`, ...env },
  });
  return { status: result.status, out: (result.stdout + result.stderr).replace(/\x1b\[[0-9;]*m/g, '') };
}

const quiet = ['--force', '--no-upgrade-docker', '--skip-mirror', '--no-firewall'];

test('CentOS entry consumes its own options and forwards the rest to install_linux.sh', () => {
  const { status, out } = runEntry('install_linux_centos.sh', [...quiet, 'logs', 'WEB']);
  assert.equal(status, 0, out);
  assert.match(out, /delegate=install_linux\.sh args=logs WEB script=\.scripts\/docker\/install_linux_centos\.sh label=yFeiEye 统一安装脚本 \(CentOS \/ RHEL\)/);
});

test('"--" stops option parsing so later dashes reach install_linux.sh untouched', () => {
  const { status, out } = runEntry('install_linux_centos.sh', [...quiet, '--', 'clean-build-runtime', '--module', 'WEB']);
  assert.equal(status, 0, out);
  assert.match(out, /args=clean-build-runtime --module WEB/);
});

test('profile skips environment preparation entirely', () => {
  const { status, out } = runEntry('install_linux_centos.sh', ['profile']);
  assert.equal(status, 0, out);
  assert.doesNotMatch(out, /系统环境检查|当前 Docker 版本/);
  assert.match(out, /delegate=install_linux\.sh args=profile/);
});

test('CentOS ARM entry delegates to install_linux_arm.sh', () => {
  const { status, out } = runEntry('install_linux_centos_arm.sh', [...quiet, 'status']);
  assert.equal(status, 0, out);
  assert.match(out, /delegate=install_linux_arm\.sh args=status script=\.scripts\/docker\/install_linux_centos_arm\.sh label=yFeiEye 统一安装脚本 \(CentOS \/ RHEL · ARM\)/);
});

test('CentOS ARM entry stops on a non-EL / x86_64 host unless --force is given', () => {
  const { status, out } = runEntry('install_linux_centos_arm.sh', ['--no-upgrade-docker', 'status'], { FAKE_ARCH: 'x86_64' });
  assert.notEqual(status, 0);
  assert.doesNotMatch(out, /delegate=/);
});

test('openEuler entry accepts --el-release in both spellings and exports DOCKER_DNS', () => {
  for (const opt of [['--el-release', '7'], ['--el-release=8']]) {
    const { status, out } = runEntry('install_linux_openeuler.sh', [...opt, ...quiet, 'start']);
    assert.equal(status, 0, out);
    assert.match(out, /delegate=install_linux\.sh args=start script=\.scripts\/docker\/install_linux_openeuler\.sh label=yFeiEye 统一安装脚本 \(openEuler\) dns=223\.5\.5\.5,119\.29\.29\.29/);
  }
});

test('openEuler entry rejects unsupported --el-release values', () => {
  const { status, out } = runEntry('install_linux_openeuler.sh', ['--el-release', '6', 'start']);
  assert.notEqual(status, 0);
  assert.match(out, /不支持的 --el-release: 6/);
});

test('help prints the entry help followed by the delegate help', () => {
  const { status, out } = runEntry('install_linux_centos_arm.sh', ['help']);
  assert.equal(status, 0, out);
  assert.match(out, /CentOS ARM 专用选项/);
  assert.match(out, /以下为 install_linux_arm\.sh 完整帮助/);
  assert.match(out, /delegate=install_linux_arm\.sh args=help/);
});

test('--upgrade-docker-only stops after Docker preparation', () => {
  const { status, out } = runEntry('install_linux_centos_arm.sh', ['--force', '--skip-mirror', '--upgrade-docker-only', 'install']);
  assert.equal(status, 0, out);
  assert.match(out, /Docker 版本满足要求/);
  assert.match(out, /Docker CE 准备完成 \(CentOS ARM\)/);
  assert.doesNotMatch(out, /delegate=/);
});

// 直接 source 公共库，用 shell 函数替身验证决策逻辑
function runCommon(body) {
  const common = join(dockerDir, 'install_el_common.sh').replaceAll('\\', '/');
  const result = spawnSync(bash, ['-c', `SCRIPT_DIR=/nonexistent\nsource '${common}'\n${body}`], { encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return result.stdout.replace(/\x1b\[[0-9;]*m/g, '').trim().split(/\r?\n/);
}

test('is_docker_too_old treats 1.13, 18.09 and empty versions as too old', () => {
  assert.deepEqual(runCommon(`
for v in "" 1.13.1 18.09.0 19.03.15 20.10.24 26.1.4; do
  is_docker_too_old "$v" && echo "$v:old" || echo "$v:ok"
done`), [':old', '1.13.1:old', '18.09.0:old', '19.03.15:old', '20.10.24:ok', '26.1.4:ok']);
});

test('get_docker_server_version falls back to docker -v when the Server section has no column-0 Version', () => {
  // 现有 awk 只认行首的 "Version:"，新版 docker version 输出是缩进的，因此实际总是走 docker -v
  assert.deepEqual(runCommon(`
docker() { case "$1" in version) printf 'Server:\n Engine:\n  Version: 20.10.7\n';; -v) echo "Docker version 20.10.7, build f0df350";; esac; }
get_docker_server_version
docker() { case "$1" in version) return 1;; -v) echo "Docker version 1.13.1, build 7d71120/1.13.1";; esac; }
get_docker_server_version`), ['20.10.7', '1.13.1']);
});

test('ensure_modern_docker upgrades old Docker via the configured installer and honours the vendor hook', () => {
  assert.deepEqual(runCommon(`
_el_is_root() { return 0; }
command() { [ "$1" = "-v" ] && return 0; builtin command "$@"; }
docker() { case "$1" in info) return 0;; esac; }
get_docker_server_version() { echo "$FAKE_VER"; }
fake_install() { echo "installed"; }
EL_INSTALL_DOCKER_FN=fake_install
EL_OLD_DOCKER_HINT="hint"
FAKE_VER=1.13.1; ensure_modern_docker | grep -E '^installed$'
FAKE_VER=26.1.4; ensure_modern_docker | grep -c installed || true
el_replace_vendor_docker() { echo "vendor"; return 0; }
FAKE_VER=1.13.1; ensure_modern_docker | grep -E '^(vendor|installed)$'
`), ['installed', '0', 'vendor']);
});

test('configure_firewalld_ports opens every port then reloads once', () => {
  const lines = runCommon(`
_el_is_root() { return 0; }
systemctl() { return 0; }
calls=0
firewall-cmd() { echo "fw $*" >> "$FW_LOG"; return 0; }
FW_LOG=$(mktemp)
configure_firewalld_ports >/dev/null
grep -c -- '--add-port=.*/tcp' "$FW_LOG"
grep -c -- '--add-port=.*/udp' "$FW_LOG"
grep -c -- '--reload' "$FW_LOG"
grep -q -- '--add-port=13338-13437/tcp' "$FW_LOG" && echo idea-range
rm -f "$FW_LOG"
`);
  assert.deepEqual(lines, ['26', '3', '1', 'idea-range']);
});

test('configure_firewalld_ports respects --no-firewall and inactive firewalld', () => {
  assert.deepEqual(runCommon(`
_el_is_root() { return 0; }
firewall-cmd() { echo called; }
SKIP_FIREWALL=true; configure_firewalld_ports | grep -c called || true
SKIP_FIREWALL=false; systemctl() { return 3; }; configure_firewalld_ports | grep -c called || true
`), ['0', '0']);
});
