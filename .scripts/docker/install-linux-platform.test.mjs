import assert from 'node:assert/strict';
import { cpSync, existsSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { basename, dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { after, before, test } from 'node:test';
import { fileURLToPath } from 'node:url';

// install_linux_arm.sh / install_linux_kylin.sh 是 install_linux.sh 的薄封装：
// 在临时目录副本里加载各入口（日志写进副本，不污染工作区），用 shell 函数替身 uname / docker，
// 只校验平台契约，不触碰真实 Docker。
const dockerDir = dirname(fileURLToPath(import.meta.url));
const scriptsDir = join(dockerDir, '..');
const bash = process.platform === 'win32' && existsSync('C:/Git/bin/bash.exe')
  ? 'C:/Git/bin/bash.exe'
  : 'bash';
let work;
let copyDocker;

before(() => {
  work = mkdtempSync(join(tmpdir(), 'install-linux-platform-'));
  copyDocker = join(work, '.scripts', 'docker');
  cpSync(dockerDir, copyDocker, { recursive: true, filter: src => basename(src) !== 'logs' });
  cpSync(join(scriptsDir, 'node'), join(work, '.scripts', 'node'), { recursive: true });
});

after(() => {
  if (work) rmSync(work, { recursive: true, force: true });
});

function runBash(source, env = {}) {
  const result = spawnSync(bash, ['-c', source], {
    cwd: copyDocker,
    encoding: 'utf8',
    env: { ...process.env, EASYAIOT_DEPLOY_PROFILE: 'full', ...env },
  });
  return result;
}

// source 入口并执行 help（help 不碰 Docker），之后在同一 shell 里检查函数与变量
function probe(entry, checks, { arch = 'aarch64', env = {} } = {}) {
  const result = runBash(`
uname() { if [ "\${1:-}" = "-m" ]; then echo ${arch}; else command uname "$@"; fi; }
docker() { echo "docker $*" >> "$PWD/.docker-calls"; return 1; }
source ./${entry} help > /dev/null 2>&1
echo "---"
${checks}
`, env);
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return result.stdout.split('---\n')[1].trim().split(/\r?\n/);
}

const contractChecks = `
echo "entry=$INSTALL_ENTRY_NAME"
echo "tag=$INSTALL_PLATFORM_TAG"
echo "ai=$(module_install_script AI) video=$(module_install_script VIDEO) device=$(module_install_script DEVICE) mw=$(module_install_script .scripts/docker)"
echo "dns=$EASYAIOT_DOCKER_DNS_AUTO"
echo "log=$(basename "$LOG_FILE" | sed 's/_[0-9]*_[0-9]*\\.log$//')"
echo "label=$EASYAIOT_INSTALL_LABEL"
`;

test('ARM wrapper delegates to install_linux.sh with ARM platform values', () => {
  assert.deepEqual(probe('install_linux_arm.sh', contractChecks), [
    'entry=install_linux_arm.sh',
    'tag= (ARM架构)',
    'ai=install_linux_arm.sh video=install_linux_arm.sh device=install_linux.sh mw=install_middleware_linux.sh',
    'dns=0',
    'log=install_linux_arm',
    'label=yFeiEye 统一安装脚本 (ARM / aarch64)',
  ]);
});

test('Kylin wrapper enables DNS auto-config and Kylin module scripts', () => {
  assert.deepEqual(probe('install_linux_kylin.sh', contractChecks), [
    'entry=install_linux_kylin.sh',
    'tag= (麒麟系统)',
    'ai=install_linux_kylin.sh video=install_linux_kylin.sh device=install_linux.sh mw=install_middleware_linux.sh',
    'dns=1',
    'log=install_linux_kylin',
    'label=yFeiEye 统一安装脚本 (麒麟 ARM)',
  ]);
});

test('install_linux.sh run directly ignores leaked platform variables', () => {
  const result = runBash('bash ./install_linux.sh help', {
    INSTALL_ENTRY_NAME: 'install_linux_arm.sh',
    INSTALL_PLATFORM_TAG: ' (ARM架构)',
  });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /^yFeiEye 统一安装脚本\n/);
  assert.match(result.stdout, /\.\/install_linux\.sh menu/);
  assert.doesNotMatch(result.stdout, /install_linux_arm|ARM架构/);
});

test('wrapper help keeps platform notes and full install_linux.sh command set', () => {
  const result = runBash('bash ./install_linux_arm.sh help');
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /\.\/install_linux_arm\.sh menu/);
  assert.match(result.stdout, /本脚本专用于 ARM 架构/);
  assert.match(result.stdout, /verify-alert/);
  assert.match(result.stdout, /SITE 子命令（\.\/install_linux_arm\.sh site/);
});

test('ARM preflight rejects x86_64 before any Docker check', () => {
  const result = runBash(`
uname() { if [ "\${1:-}" = "-m" ]; then echo x86_64; else command uname "$@"; fi; }
export -f uname
docker() { echo "docker-called"; return 1; }
export -f docker
bash ./install_linux_arm.sh build
`);
  assert.equal(result.status, 1);
  assert.match(result.stdout, /本脚本专用于 ARM 架构部署/);
  assert.doesNotMatch(result.stdout, /docker-called/);
});

test('ARM preflight accepts aarch64 and normalizes ARCH', () => {
  assert.deepEqual(probe('install_linux_arm.sh', 'install_platform_preflight >/dev/null; echo "arch=$ARCH"'), ['arch=aarch64']);
});

test('Kylin prepare_docker fixes host DNS, then mirror, then buildx', () => {
  const lines = probe('install_linux_kylin.sh', `
ensure_host_dns_for_docker() { echo "dns $1"; }
configure_docker_mirror() { echo mirror; }
ensure_docker_buildx_plugin() { echo buildx; return 1; }
install_platform_prepare_docker 2>&1 | sed 's/\\x1b\\[[0-9;]*m//g'
`);
  assert.deepEqual(lines, ['dns docker.cnb.cool', 'mirror', 'buildx', '[WARNING] buildx 未就绪，后续本地 docker build 可能失败']);
});

test('default prepare_docker only configures the mirror', () => {
  const lines = probe('install_linux_arm.sh', `
ensure_host_dns_for_docker() { echo dns; }
configure_docker_mirror() { echo mirror; }
install_platform_prepare_docker
`);
  assert.deepEqual(lines, ['mirror']);
});

test('install_linux.sh calls the preflight hook before Docker checks in every command', () => {
  const lines = probe('install_linux_arm.sh', `
declare -f install_linux start_all restart_all build_all pull_runtime_images build_runtime_images update_all \\
  | awk '/^[a-z_]+ \\(\\)/ { fn=$1; seen=0 } /install_platform_preflight/ { seen=1 } /check_docker "\\$@"/ { print fn ":" seen }'
`);
  assert.deepEqual(lines, [
    'install_linux:1', 'start_all:1', 'restart_all:1', 'build_all:1',
    'pull_runtime_images:1', 'build_runtime_images:1', 'update_all:1',
  ]);
});
