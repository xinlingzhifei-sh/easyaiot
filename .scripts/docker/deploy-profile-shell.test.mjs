import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const dockerDir = dirname(fileURLToPath(import.meta.url));
const profileScript = join(dockerDir, 'deploy_profile.sh').replaceAll('\\', '/');
const bash = process.platform === 'win32' && existsSync('C:/Git/bin/bash.exe')
  ? 'C:/Git/bin/bash.exe'
  : 'bash';

function runBash(source) {
  const result = spawnSync(bash, ['-c', source], {
    cwd: join(dockerDir, '..', '..'),
    encoding: 'utf8',
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
  return result.stdout.trim();
}

test('Shell compatibility helpers expose the manifest module and service allowlists', () => {
  const output = runBash(`
source '${profileScript}'
for profile in edge mini standard full; do
  EASYAIOT_DEPLOY_PROFILE="$profile"
  modules=""
  while IFS= read -r module; do
    if module_enabled_for_deploy_profile "$module"; then
      modules="\${modules}\${modules:+,}\${module}"
    fi
  done < <(deploy_profile_manifest_module_order)
  printf '%s|%s|%s|%s\\n' "$profile" "$modules" "$(device_enabled_services)" "$(middleware_skipped_services)"
done
`);

  const rows = Object.fromEntries(output.split(/\r?\n/).map(line => {
    const [profile, modules, device, middlewareSkips] = line.split('|');
    return [profile, { modules: modules.split(','), device: device ? device.split(' ') : [], middlewareSkips }];
  }));
  assert.deepEqual(rows.edge.modules, ['.scripts/docker', 'VIDEO', 'WEB']);
  assert.deepEqual(rows.mini.modules, ['.scripts/docker', 'DEVICE', 'AI', 'VIDEO', 'WEB']);
  assert.deepEqual(rows.standard.modules, ['.scripts/docker', 'DEVICE', 'AI', 'RTC', 'POST', 'VIDEO', 'WEB']);
  assert.deepEqual(rows.full.modules, ['.scripts/docker', 'DEVICE', 'AI', 'RTC', 'POST', 'VIDEO', 'WEB', 'APP', 'VISUALIZE', 'TRANSFORM']);
  assert.deepEqual(rows.edge.device, []);
  assert.ok(rows.edge.middlewareSkips.includes('GPUStack'));
  assert.ok(rows.full.middlewareSkips.includes('GPUStack'));
});

test('developer services stay off unless their declared override is explicit', () => {
  const output = runBash(`
source '${profileScript}'
EASYAIOT_DEPLOY_PROFILE=full
module_enabled_for_deploy_profile HARNESS && echo unexpected-default || echo default-off
EASYAIOT_ENABLE_HARNESS=1
module_enabled_for_deploy_profile HARNESS && echo explicit-on || echo unexpected-off
deploy_feature_enabled_for_profile industrial-demo && echo unexpected-demo || echo demo-off
EASYAIOT_ENABLE_INDUSTRIAL_DEMO=1
deploy_feature_enabled_for_profile industrial-demo && echo demo-opt-in || echo unexpected-demo-off
EASYAIOT_ENABLE_GPUSTACK=1
middleware_skipped_services
`);
  const [defaultState, explicitState, demoDefault, demoOptIn, skippedServices = ''] = output.split(/\r?\n/);
  assert.equal(defaultState, 'default-off');
  assert.equal(explicitState, 'explicit-on');
  assert.equal(demoDefault, 'demo-off');
  assert.equal(demoOptIn, 'demo-opt-in');
  assert.ok(!skippedServices.split(' ').includes('GPUStack'));
});

test('unknown deployment profiles fail instead of expanding to full', () => {
  const result = spawnSync(bash, ['-c', `
source '${profileScript}'
deploy_profile_manifest_normalize standrad
`], {
    cwd: join(dockerDir, '..', '..'),
    encoding: 'utf8',
  });
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /未知部署规格: standrad/);
});

test('module display names, ports and health paths come from the manifest', () => {
  const output = runBash(`
source '${profileScript}'
deploy_profile_load_module_tables
for module in .scripts/docker DEVICE POST WEB PANEL; do
  printf '%s|%s|%s|%s\n' "$module" "\${MODULE_NAMES[$module]}" "\${MODULE_PORTS[$module]}" "\${MODULE_HEALTH_ENDPOINTS[$module]}"
done
deploy_profile_manifest_module_label UNKNOWN
`);
  assert.deepEqual(output.split(/\r?\n/), [
    '.scripts/docker|基础服务|8848|/nacos/actuator/health',
    'DEVICE|Device服务|48080|/actuator/health',
    'POST|POST服务|8089|/readyz',
    'WEB|Web前端服务|8888|/health',
    'PANEL|运维控制台|9200|/health',
    'UNKNOWN',
  ]);
});

test('installers load module tables instead of hardcoding them', () => {
  for (const file of ['install_linux.sh', 'install_business_linux.sh']) {
    const source = readFileSync(join(dockerDir, file), 'utf8');
    assert.match(source, /^deploy_profile_load_module_tables$/m, file);
    assert.doesNotMatch(source, /MODULE_PORTS\[\s*"?DEVICE"?\s*\]\s*=/, file);
    assert.doesNotMatch(source, /declare -A MODULE_(NAMES|PORTS|HEALTH_ENDPOINTS)/, file);
  }
});
