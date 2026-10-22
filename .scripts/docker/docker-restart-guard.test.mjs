import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const dockerDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(dockerDir, '..', '..');
const guardScript = join(dockerDir, 'docker_restart_guard.sh').replaceAll('\\', '/');
const bash = process.platform === 'win32' && existsSync('C:/Git/bin/bash.exe')
  ? 'C:/Git/bin/bash.exe'
  : 'bash';

// 用 shell 函数替身 systemctl / docker，记录调用而不触碰真实守护进程
function runGuard({ running = '', liveRestore = 'false', psFails = false, reloadFails = false, active = true, allow = '' }) {
  const source = `
id() { echo 0; }
calls=()
systemctl() { calls+=("systemctl $*"); case "$1" in is-active) return ${active ? 0 : 3};; reload) return ${reloadFails ? 1 : 0};; esac; return 0; }
docker() {
  case "$1" in
    ps) ${psFails ? 'return 1' : `printf '%s' '${running}'`};;
    info) echo '${liveRestore}';;
  esac
}
source '${guardScript}'
EASYAIOT_ALLOW_DOCKER_RESTART='${allow}' apply_docker_daemon_config test-change >/dev/null 2>&1
rc=$?
printf '%s\\n' "\${calls[@]}" | grep -E '^systemctl (restart|reload) docker$' | tr '\\n' ','
echo "rc=$rc"
`;
  const result = spawnSync(bash, ['-c', source], { cwd: repoRoot, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return result.stdout.trim();
}

test('no running containers: restart is safe', () => {
  assert.equal(runGuard({ running: '' }), 'systemctl restart docker,rc=0');
});

test('running containers without live-restore: only reload, never restart', () => {
  assert.equal(runGuard({ running: 'abc\ndef\n' }), 'systemctl reload docker,rc=0');
});

test('live-restore enabled: restart keeps containers, so restart is allowed', () => {
  assert.equal(runGuard({ running: 'abc\n', liveRestore: 'true' }), 'systemctl restart docker,rc=0');
});

test('explicit EASYAIOT_ALLOW_DOCKER_RESTART=1 restarts even with running containers', () => {
  assert.equal(runGuard({ running: 'abc\n', allow: '1' }), 'systemctl restart docker,rc=0');
});

test('docker ps failure is treated as unknown, not as zero containers', () => {
  assert.equal(runGuard({ psFails: true }), 'systemctl reload docker,rc=0');
});

test('reload failure with running containers reports failure instead of restarting', () => {
  assert.equal(runGuard({ running: 'abc\n', reloadFails: true }), 'systemctl reload docker,rc=1');
});

test('inactive Docker: nothing to do', () => {
  assert.equal(runGuard({ running: 'abc\n', active: false }), 'rc=0');
});

test('installers do not call systemctl restart docker directly', () => {
  const files = [
    '.scripts/docker/configure_huawei_mirror.sh',
    '.scripts/docker/install_linux.sh',
    '.scripts/docker/install_linux_arm.sh',
    '.scripts/docker/install_linux_centos.sh',
    '.scripts/docker/install_linux_centos_arm.sh',
    '.scripts/docker/install_linux_openeuler.sh',
    '.scripts/docker/install_el_common.sh',
    '.scripts/docker/install_middleware_linux.sh',
    '.scripts/docker/start_fuxa_centos7.sh',
    '.scripts/docker/start_minio_centos7.sh',
    '.scripts/docker/start_nodered_centos7.sh',
    '.scripts/docker/start_postgresql_centos7.sh',
    '.scripts/docker/start_rustfs_centos7.sh',
    'AI/install_linux.sh',
    'AI/install_linux_kylin.sh',
  ];
  for (const file of files) {
    const offending = readFileSync(join(repoRoot, file), 'utf8')
      .split('\n')
      .map((line, i) => [i + 1, line.trim()])
      .filter(([, line]) => /^(sudo\s+)?(if\s+!?\s*)?systemctl\s+restart\s+docker\b/.test(line));
    assert.deepEqual(offending, [], `${file} 直接重启了 Docker，应改用 apply_docker_daemon_config`);
  }
});

test('shared restart_docker_if_active delegates to the guard', () => {
  // docker_mirror_common.sh 里另有一段打印给人看的手工排障命令（heredoc），不在上面的逐行检查里
  const output = spawnSync(bash, ['-c', `
id() { echo 0; }
systemctl() { echo "systemctl $*"; case "$1" in is-active) return 0;; esac; return 0; }
docker() { case "$1" in ps) echo abc;; info) echo false;; esac; }
source '.scripts/docker/docker_mirror_common.sh'
restart_docker_if_active 2>&1 | grep -E '^systemctl (restart|reload) docker$'
`], { cwd: repoRoot, encoding: 'utf8' });
  assert.equal(output.stdout.trim(), 'systemctl reload docker', output.stderr);
});

test('middleware installer no longer adds an empty runtimes key on every run', () => {
  const text = readFileSync(join(repoRoot, '.scripts/docker/install_middleware_linux.sh'), 'utf8');
  assert.doesNotMatch(text, /changes\.append\("添加 runtimes 配置"\)/);
});
