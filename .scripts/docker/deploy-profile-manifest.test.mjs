import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const dockerDir = dirname(fileURLToPath(import.meta.url));
const rootDir = join(dockerDir, '..', '..');
const manifestPath = join(dockerDir, 'deploy-profiles.json');
const generatorPath = join(dockerDir, 'generate-deploy-profiles.mjs');
const generatedShellPath = join(dockerDir, 'deploy-profiles.generated.sh');
const generatedWebPath = join(rootDir, 'WEB', 'src', 'generated', 'deploymentProfileManifest.ts');

const expectedProfiles = ['edge', 'mini', 'standard', 'full'];
const unsafeDefaultModules = ['HARNESS', 'IDEA', 'TERMINAL', 'TWIN', 'PANEL'];

function loadManifest() {
  assert.ok(existsSync(manifestPath), 'deploy-profiles.json must be the canonical profile source');
  return JSON.parse(readFileSync(manifestPath, 'utf8'));
}

function composeServices(path) {
  const source = readFileSync(path, 'utf8');
  const services = [];
  let inServices = false;
  for (const line of source.split(/\r?\n/)) {
    if (line === 'services:') {
      inServices = true;
      continue;
    }
    if (inServices && /^\S/.test(line)) break;
    const match = inServices ? /^  ([A-Za-z0-9_-]+):\s*$/.exec(line) : null;
    if (match) services.push(match[1]);
  }
  return services;
}

test('canonical manifest defines the four deployment profiles and increasing budgets', () => {
  const manifest = loadManifest();
  assert.equal(manifest.schemaVersion, 1);
  assert.deepEqual(manifest.profileOrder, expectedProfiles);
  assert.deepEqual(Object.keys(manifest.profiles), expectedProfiles);
  assert.deepEqual(
    expectedProfiles.map(profile => manifest.profiles[profile].containerMemoryBudgetMiB),
    [2048, 4096, 16384, 20480],
  );
  assert.equal(manifest.networkName, 'yfeieye-network');
});

test('production profiles fail closed for developer, demo, and host-control modules', () => {
  const manifest = loadManifest();
  assert.deepEqual(manifest.featureOrder, ['mqtt-demo', 'industrial-demo', 'flow-demo']);
  for (const [profile, config] of Object.entries(manifest.profiles)) {
    for (const module of unsafeDefaultModules) {
      assert.ok(!config.modules.includes(module), `${profile} must not enable ${module} by default`);
    }
    assert.ok(!config.middlewareServices.includes('GPUStack'), `${profile} must not enable GPUStack`);
    assert.deepEqual(config.features, [], `${profile} must not enable demo features by default`);
  }
  assert.deepEqual(manifest.profiles.edge.modules, ['.scripts/docker', 'VIDEO', 'WEB']);
  assert.deepEqual(manifest.profiles.mini.modules, ['.scripts/docker', 'DEVICE', 'AI', 'VIDEO', 'WEB']);
  assert.deepEqual(
    manifest.profiles.standard.modules,
    ['.scripts/docker', 'DEVICE', 'AI', 'RTC', 'POST', 'VIDEO', 'WEB'],
  );
  assert.deepEqual(
    manifest.profiles.full.modules,
    ['.scripts/docker', 'DEVICE', 'AI', 'RTC', 'POST', 'VIDEO', 'WEB', 'APP', 'VISUALIZE', 'TRANSFORM'],
  );
});

test('manifest allowlists every compose service and leaves no implicit auto-start path', () => {
  const manifest = loadManifest();
  const middlewareServices = composeServices(join(dockerDir, 'docker-compose.yml'));
  const deviceServices = composeServices(join(rootDir, 'DEVICE', 'docker-compose.yml'));
  assert.deepEqual([...manifest.middlewareServiceOrder].sort(), [...middlewareServices].sort());
  assert.deepEqual([...manifest.deviceServiceOrder].sort(), [...deviceServices].sort());
  assert.deepEqual(manifest.profiles.edge.deviceServices, []);
  assert.deepEqual(
    manifest.profiles.mini.deviceServices,
    ['iot-gateway', 'iot-system', 'iot-infra', 'iot-sink'],
  );
  assert.ok(manifest.deviceServiceOrder.includes('iot-flow'), 'edge cleanup must account for iot-flow');
  const appCompose = readFileSync(join(rootDir, 'APP', 'docker-compose.yaml'), 'utf8');
  assert.match(appCompose, /name: \$\{EASYAIOT_NETWORK_NAME:-yfeieye-network\}/);
  assert.doesNotMatch(appCompose, /name: easyaiot-network/);
  for (const composePath of [
    'AI/docker-compose.desktop.yaml',
    'DEVICE/docker-compose.desktop.yaml',
    'VIDEO/docker-compose.desktop.yaml',
    'POST/docker-compose.desktop.yaml',
    'RTC/docker-compose.desktop.yaml',
  ]) {
    const compose = readFileSync(join(rootDir, composePath), 'utf8');
    assert.match(compose, /name: \$\{EASYAIOT_NETWORK_NAME:-yfeieye-network\}/, composePath);
    assert.doesNotMatch(compose, /name: easyaiot-network/, composePath);
  }
});

test('generated Shell and WEB artifacts are committed and current', () => {
  assert.ok(existsSync(generatorPath), 'profile generator must exist');
  assert.ok(existsSync(generatedShellPath), 'generated Shell policy must exist');
  assert.ok(existsSync(generatedWebPath), 'generated WEB policy must exist');
  const result = spawnSync(process.execPath, [generatorPath, '--check'], {
    cwd: rootDir,
    encoding: 'utf8',
  });
  assert.equal(result.status, 0, result.stderr || result.stdout);
});

test('deployment consumers query generated policy instead of keeping profile matrices', () => {
  const profileSource = readFileSync(join(dockerDir, 'deploy_profile.sh'), 'utf8');
  const linuxSource = readFileSync(join(dockerDir, 'install_linux.sh'), 'utf8');
  const armSource = readFileSync(join(dockerDir, 'install_linux_arm.sh'), 'utf8');
  const kylinSource = readFileSync(join(dockerDir, 'install_linux_kylin.sh'), 'utf8');
  const macSource = readFileSync(join(dockerDir, 'install_mac.sh'), 'utf8');
  const desktopSource = readFileSync(join(dockerDir, 'install_desktop_common.sh'), 'utf8');
  const businessSource = readFileSync(join(dockerDir, 'install_business_linux.sh'), 'utf8');
  const middlewareLinuxSource = readFileSync(join(dockerDir, 'install_middleware_linux.sh'), 'utf8');
  const middlewareDesktopSource = readFileSync(join(dockerDir, 'install_middleware_desktop.sh'), 'utf8');
  const middlewareMacSource = readFileSync(join(dockerDir, 'install_middleware_mac.sh'), 'utf8');
  const analyzerSource = readFileSync(join(dockerDir, 'analyze_deploy_memory.sh'), 'utf8');
  const windowsSource = readFileSync(join(dockerDir, 'install_windows.ps1'), 'utf8');

  assert.match(profileSource, /source "\$_DEPLOY_PROFILE_MANIFEST_SH"/);
  assert.match(profileSource, /deploy_profile_manifest_module_enabled/);
  assert.match(profileSource, /deploy_profile_manifest_middleware_skipped_services/);
  assert.match(profileSource, /deploy_profile_manifest_device_skipped_services/);
  assert.match(profileSource, /deploy_profile_manifest_feature_enabled/);
  assert.match(profileSource, /label=easyaiot\.idea\.managed=1/);
  assert.match(linuxSource, /deploy_profile_manifest_module_order/);
  assert.match(linuxSource, /deploy_feature_enabled_for_profile mqtt-demo/);
  for (const source of [linuxSource, armSource, kylinSource, desktopSource]) {
    assert.match(source, /deploy_feature_enabled_for_profile flow-demo/);
    assert.doesNotMatch(source, /EASYAIOT_ENABLE_FLOW_DEMO:-1/);
  }
  assert.match(businessSource, /deploy_feature_enabled_for_profile mqtt-demo/);
  assert.match(businessSource, /deploy_feature_enabled_for_profile industrial-demo/);
  assert.doesNotMatch(businessSource, /EASYAIOT_ENABLE_(?:MQTT|INDUSTRIAL)_DEMO:-1/);
  for (const source of [armSource, kylinSource, macSource]) {
    assert.doesNotMatch(source, /total_count=\$\{#MODULES\[@\]\}/);
    assert.match(source, /total_count=\$\(_count_enabled_modules\)/);
  }
  assert.match(macSource, /up -d "\$\{device_targets\[@\]\}"/);
  for (const source of [linuxSource, armSource, kylinSource, macSource, desktopSource, businessSource]) {
    assert.match(source, /deploy_profile_manifest_network_name/);
  }
  assert.match(middlewareLinuxSource, /compose_up_middleware --force-recreate "\$\{_skip_optional\[@\]\}"/);
  assert.doesNotMatch(middlewareLinuxSource, /mw_compose up -d --force-recreate/);
  for (const source of [middlewareLinuxSource, middlewareDesktopSource, middlewareMacSource]) {
    assert.match(source, /deploy_profile_manifest_middleware_service_order/);
  }
  assert.match(middlewareLinuxSource, /deploy_profile_manifest_middleware_service_containers "\$service"/);
  for (const source of [middlewareDesktopSource, middlewareMacSource]) {
    assert.match(source, /middleware_service_enabled "\$svc" \|\| continue/);
    assert.match(source, /NETWORK_NAME="\$\(deploy_profile_manifest_network_name\)"/);
  }
  assert.match(analyzerSource, /deploy_profile_manifest_network_name/);
  assert.doesNotMatch(analyzerSource, /\bapply_deploy_profile\b/);
  assert.doesNotMatch(analyzerSource, /iot-message\|iot-sink/);
  assert.equal((analyzerSource.match(/edge 典型 .*容器合计/g) ?? []).length, 1);
  assert.match(windowsSource, /deploy-profiles\.json/);
  assert.match(windowsSource, /throw "未知部署规格:/);
});
