import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const dockerDir = dirname(fileURLToPath(import.meta.url));
const rootDir = join(dockerDir, '..', '..');
const profileScript = join(dockerDir, 'deploy_profile.sh').replaceAll('\\', '/');
const bash = process.platform === 'win32' && existsSync('C:/Git/bin/bash.exe')
  ? 'C:/Git/bin/bash.exe'
  : 'bash';
const externalModules = ['HARNESS', 'IDEA', 'TERMINAL', 'TWIN', 'SITE'];

function posix(path) {
  return path.replaceAll('\\', '/');
}

function run(source, env = {}) {
  const result = spawnSync(bash, ['-c', source], {
    cwd: rootDir,
    encoding: 'utf8',
    env: {
      ...process.env,
      GIT_AUTHOR_NAME: 'test',
      GIT_AUTHOR_EMAIL: 'test@example.invalid',
      GIT_COMMITTER_NAME: 'test',
      GIT_COMMITTER_EMAIL: 'test@example.invalid',
      ...env,
    },
  });
  return { status: result.status, stdout: result.stdout.trim(), stderr: result.stderr };
}

function withSandbox(fn) {
  const sandbox = mkdtempSync(join(tmpdir(), 'external-modules-'));
  try {
    const platformRoot = join(sandbox, 'platform');
    const remote = join(sandbox, 'twin.git');
    const seed = join(sandbox, 'seed');
    const setup = run(`
set -e
mkdir -p '${posix(platformRoot)}'
git init -q --bare -b main '${posix(remote)}'
git init -q -b main '${posix(seed)}'
printf '#!/bin/bash\\necho twin-v1\\n' > '${posix(seed)}/install_linux.sh'
git -C '${posix(seed)}' add -A
git -C '${posix(seed)}' commit -q -m v1
git -C '${posix(seed)}' push -q '${posix(remote)}' main
`);
    assert.equal(setup.status, 0, setup.stderr);
    fn({ sandbox, platformRoot: posix(platformRoot), remote: posix(remote), seed: posix(seed) });
  } finally {
    rmSync(sandbox, { recursive: true, force: true });
  }
}

test('manifest lists the split modules as external, opt-in only', () => {
  const manifest = JSON.parse(readFileSync(join(dockerDir, 'deploy-profiles.json'), 'utf8'));
  assert.deepEqual(Object.keys(manifest.externalModules).sort(), [...externalModules].sort());
  for (const [moduleName, source] of Object.entries(manifest.externalModules)) {
    assert.match(source.repository, /^https:\/\/github\.com\/xinlingzhifei-sh\/yfeieye-[a-z]+\.git$/, moduleName);
    assert.ok(source.ref, `${moduleName} needs a ref`);
    for (const [profile, config] of Object.entries(manifest.profiles)) {
      assert.ok(!config.modules.includes(moduleName), `${profile} must not enable external ${moduleName}`);
    }
  }
  const result = run(`
source '${profileScript}'
deploy_profile_manifest_external_modules | tr '\\n' ' '
echo
external_module_is_external SITE && echo site-external
external_module_is_external DEVICE || echo device-internal
YFEIEYE_TWIN_REPO=/srv/twin.git external_module_repository TWIN
`);
  assert.equal(result.status, 0, result.stderr);
  const [list, site, device, override] = result.stdout.split(/\r?\n/);
  assert.deepEqual(list.trim().split(' ').sort(), [...externalModules].sort());
  assert.equal(site, 'site-external');
  assert.equal(device, 'device-internal');
  assert.equal(override, '/srv/twin.git');
});

test('non-external modules and read-only commands never fetch', () => {
  withSandbox(({ platformRoot, remote }) => {
    const result = run(`
source '${profileScript}'
ensure_external_module_checkout DEVICE install '${platformRoot}' && echo device-ok
ensure_external_module_checkout TWIN status '${platformRoot}' && echo status-ok
ensure_external_module_checkout TWIN logs '${platformRoot}' && echo logs-ok
[ -e '${platformRoot}/TWIN' ] && echo unexpected-checkout || echo no-checkout
`, { YFEIEYE_TWIN_REPO: remote });
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(result.stdout.split(/\r?\n/), ['device-ok', 'status-ok', 'logs-ok', 'no-checkout']);
  });
});

test('install clones the external repository into <root>/<module> and update fast-forwards it', () => {
  withSandbox(({ platformRoot, remote, seed }) => {
    const install = run(`
source '${profileScript}'
ensure_external_module_checkout TWIN install '${platformRoot}' || exit 1
bash '${platformRoot}/TWIN/install_linux.sh'
git -C '${platformRoot}/TWIN' config --get yfeieye.externalModule
`, { YFEIEYE_TWIN_REPO: remote });
    assert.equal(install.status, 0, install.stderr);
    assert.deepEqual(install.stdout.split(/\r?\n/), ['twin-v1', 'TWIN']);
    assert.deepEqual(readdirSync(platformRoot), ['TWIN'], 'no temporary fetch directory may remain');

    const update = run(`
set -e
printf '#!/bin/bash\\necho twin-v2\\n' > '${seed}/install_linux.sh'
git -C '${seed}' commit -q -am v2
git -C '${seed}' push -q '${remote}' main
source '${profileScript}'
ensure_external_module_checkout TWIN start '${platformRoot}'
bash '${platformRoot}/TWIN/install_linux.sh'
ensure_external_module_checkout TWIN update '${platformRoot}'
bash '${platformRoot}/TWIN/install_linux.sh'
`, { YFEIEYE_TWIN_REPO: remote });
    assert.equal(update.status, 0, update.stderr);
    assert.deepEqual(update.stdout.split(/\r?\n/), ['twin-v1', 'twin-v2']);

    const dirty = run(`
echo local-edit >> '${platformRoot}/TWIN/install_linux.sh'
source '${profileScript}'
ensure_external_module_checkout TWIN update '${platformRoot}' && echo kept
`, { YFEIEYE_TWIN_REPO: remote });
    assert.equal(dirty.status, 0, dirty.stderr);
    assert.equal(dirty.stdout, 'kept');
    assert.match(dirty.stderr, /本地改动/);
  });
});

test('fetch failures and disabled fetching leave no half-made directory', () => {
  withSandbox(({ sandbox, platformRoot, remote }) => {
    const disabled = run(`
source '${profileScript}'
ensure_external_module_checkout TWIN install '${platformRoot}' && echo unexpected || echo refused
`, { YFEIEYE_TWIN_REPO: remote, YFEIEYE_EXTERNAL_MODULES_FETCH: '0' });
    assert.equal(disabled.stdout, 'refused');
    assert.match(disabled.stderr, /YFEIEYE_EXTERNAL_MODULES_FETCH=0/);

    const missing = run(`
source '${profileScript}'
ensure_external_module_checkout TWIN install '${platformRoot}' && echo unexpected || echo failed
`, { YFEIEYE_TWIN_REPO: posix(join(sandbox, 'missing.git')) });
    assert.equal(missing.stdout, 'failed');
    assert.deepEqual(readdirSync(platformRoot), []);
  });
});

test('a release archive with one top-level directory is unpacked in place of a clone', () => {
  withSandbox(({ sandbox, platformRoot, remote }) => {
    const archive = posix(join(sandbox, 'yfeieye-twin-1.0.0.tar.gz'));
    const result = run(`
set -e
git --git-dir='${remote}' archive --prefix=yfeieye-twin-1.0.0/ -o '${archive}' main
source '${profileScript}'
ensure_external_module_checkout TWIN install '${platformRoot}'
bash '${platformRoot}/TWIN/install_linux.sh'
`, { YFEIEYE_TWIN_ARCHIVE: archive, YFEIEYE_TWIN_REPO: '/nonexistent.git' });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.stdout, 'twin-v1');
    assert.deepEqual(readdirSync(platformRoot), ['TWIN']);
  });
});

test('every module entry point ensures external checkouts before looking for the module directory', () => {
  const sources = {
    'install_linux.sh': 2,
    'install_mac.sh': 1,
    'install_desktop_common.sh': 1,
    'install_business_linux.sh': 1,
    'runtime_image.sh': 1,
  };
  for (const [file, expected] of Object.entries(sources)) {
    const source = readFileSync(join(dockerDir, file), 'utf8');
    const calls = source.match(/ensure_external_module_checkout /g) ?? [];
    assert.equal(calls.length, expected, `${file} must call ensure_external_module_checkout ${expected} time(s)`);
  }
  // ARM / 麒麟入口是 install_linux.sh 的薄封装，经它的 execute_module_command / run_site_module 检出
  for (const file of ['install_linux_arm.sh', 'install_linux_kylin.sh']) {
    const source = readFileSync(join(dockerDir, file), 'utf8');
    assert.match(source, /^source "\$\{SCRIPT_DIR\}\/install_linux\.sh"$/m, `${file} must delegate to install_linux.sh`);
    assert.doesNotMatch(source, /^execute_module_command\(\)|^run_site_module\(\)/m, `${file} must not override the checkout-aware entry points`);
  }
});
