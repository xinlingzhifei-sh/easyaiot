import assert from 'node:assert/strict';
import { existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const dockerDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(dockerDir, '..', '..');
const bash = process.platform === 'win32' && existsSync('C:/Git/bin/bash.exe')
  ? 'C:/Git/bin/bash.exe'
  : 'bash';
const posix = p => p.replaceAll('\\', '/');
// 被测函数要求绝对路径以 / 开头；Windows 下 Git Bash 用 /c/... 形式
const bashAbs = p => posix(p).replace(/^([A-Za-z]):\//, (_, d) => `/${d.toLowerCase()}/`);

// 从安装脚本中只抽取被测函数，不执行安装流程
function runConsume(work) {
  const middleware = posix(join(dockerDir, 'install_middleware_linux.sh'));
  const profile = posix(join(dockerDir, 'deploy_profile.sh'));
  const source = `
print_info() { echo "[INFO] $1"; }
print_error() { echo "[ERR] $1"; }
eval "$(sed -n '/^_set_env_docker_kv() {/,/^}/p' '${profile}')"
eval "$(sed -n '/^read_middleware_env_value() {/,/^}/p;/^resolve_middleware_data_root() {/,/^}/p;/^consume_nacos_empty_init_switch() {/,/^}/p' '${middleware}')"
MIDDLEWARE_ENV_FILE='${posix(join(work, '.env.docker'))}'
consume_nacos_empty_init_switch
`;
  const result = spawnSync(bash, ['-c', source], { cwd: repoRoot, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return readFileSync(join(work, '.env.docker'), 'utf8');
}

function setup({ flag, nacosData }) {
  const work = mkdtempSync(join(tmpdir(), 'nacos-switch-'));
  const dataRoot = bashAbs(join(work, 'data'));
  mkdirSync(join(work, 'data', 'nacos_data', 'data'), { recursive: true });
  if (nacosData) writeFileSync(join(work, 'data', 'nacos_data', 'data', 'marker'), 'x');
  writeFileSync(join(work, '.env.docker'), `YFEIEYE_DOCKER_DATA_ROOT=${dataRoot}\nYFEIEYE_NACOS_ALLOW_EMPTY_DATA_INIT=${flag}\nOTHER=keep\n`);
  return work;
}

test('switch is reset to false once Nacos has written its data directory', t => {
  const work = setup({ flag: 'true', nacosData: true });
  t.after(() => rmSync(work, { recursive: true, force: true }));
  const env = runConsume(work);
  assert.match(env, /^YFEIEYE_NACOS_ALLOW_EMPTY_DATA_INIT=false$/m);
  assert.match(env, /^OTHER=keep$/m);
});

test('switch stays on while the data directory is still empty', t => {
  const work = setup({ flag: 'TRUE', nacosData: false });
  t.after(() => rmSync(work, { recursive: true, force: true }));
  assert.match(runConsume(work), /^YFEIEYE_NACOS_ALLOW_EMPTY_DATA_INIT=TRUE$/m);
});

test('a switch that is already false is left untouched', t => {
  const work = setup({ flag: 'false', nacosData: true });
  t.after(() => rmSync(work, { recursive: true, force: true }));
  assert.match(runConsume(work), /^YFEIEYE_NACOS_ALLOW_EMPTY_DATA_INIT=false$/m);
});

test('wait_for_nacos consumes the switch when Nacos becomes ready', () => {
  const text = readFileSync(join(dockerDir, 'install_middleware_linux.sh'), 'utf8');
  const body = text.slice(text.indexOf('wait_for_nacos() {'));
  assert.match(body.slice(0, body.indexOf('\n}\n')), /print_success "Nacos 服务已就绪"\n\s+consume_nacos_empty_init_switch\n\s+return 0/);
});
