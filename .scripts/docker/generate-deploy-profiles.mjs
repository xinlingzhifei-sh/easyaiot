#!/usr/bin/env node

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const dockerDir = dirname(fileURLToPath(import.meta.url));
const rootDir = resolve(dockerDir, '..', '..');
const manifestPath = join(dockerDir, 'deploy-profiles.json');
const shellPath = join(dockerDir, 'deploy-profiles.generated.sh');
const webPath = join(rootDir, 'WEB', 'src', 'generated', 'deploymentProfileManifest.ts');
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));

function fail(message) {
  throw new Error(`deploy-profiles.json: ${message}`);
}

function assertUnique(values, label) {
  if (new Set(values).size !== values.length) fail(`${label} contains duplicate values`);
}

function validate() {
  if (manifest.schemaVersion !== 1) fail('schemaVersion must be 1');
  assertUnique(manifest.profileOrder, 'profileOrder');
  assertUnique(manifest.moduleOrder, 'moduleOrder');
  assertUnique(manifest.featureOrder, 'featureOrder');
  assertUnique(manifest.middlewareServiceOrder, 'middlewareServiceOrder');
  assertUnique(manifest.deviceServiceOrder, 'deviceServiceOrder');
  if (JSON.stringify(Object.keys(manifest.moduleRuntime)) !== JSON.stringify(manifest.moduleOrder)) {
    fail('moduleRuntime must follow moduleOrder exactly');
  }
  for (const [moduleName, runtime] of Object.entries(manifest.moduleRuntime)) {
    if (!runtime.label || !Number.isInteger(runtime.port) || !String(runtime.healthPath ?? '').startsWith('/')) {
      fail(`moduleRuntime.${moduleName} needs label, integer port and healthPath starting with /`);
    }
  }
  if (JSON.stringify(Object.keys(manifest.profiles)) !== JSON.stringify(manifest.profileOrder)) {
    fail('profiles must follow profileOrder exactly');
  }

  for (const [moduleName, source] of Object.entries(manifest.externalModules ?? {})) {
    if (!/^[A-Z][A-Z0-9_]*$/.test(moduleName)) fail(`externalModules key ${moduleName} must be an upper-case module directory`);
    if (typeof source?.repository !== 'string' || !source.repository) fail(`externalModules.${moduleName}.repository is required`);
    if (typeof source?.ref !== 'string' || !source.ref) fail(`externalModules.${moduleName}.ref is required`);
  }
  for (const profileId of manifest.profileOrder) {
    for (const moduleName of manifest.profiles[profileId].modules) {
      if (manifest.externalModules?.[moduleName]) fail(`${profileId} must not enable external module ${moduleName} by default`);
    }
  }

  const aliases = new Set();
  for (const profileId of manifest.profileOrder) {
    const profile = manifest.profiles[profileId];
    for (const alias of [profileId, ...profile.aliases]) {
      const normalized = String(alias).toLowerCase();
      if (aliases.has(normalized)) fail(`duplicate profile alias: ${normalized}`);
      aliases.add(normalized);
    }
    for (const moduleName of profile.modules) {
      if (!manifest.moduleOrder.includes(moduleName)) fail(`${profileId} references unknown module ${moduleName}`);
    }
    for (const service of profile.middlewareServices) {
      if (!manifest.middlewareServiceOrder.includes(service)) fail(`${profileId} references unknown middleware ${service}`);
    }
    for (const service of profile.deviceServices) {
      if (!manifest.deviceServiceOrder.includes(service)) fail(`${profileId} references unknown DEVICE service ${service}`);
    }
    for (const feature of profile.features) {
      if (!manifest.featureOrder.includes(feature)) fail(`${profileId} references unknown feature ${feature}`);
    }
    assertUnique(profile.modules, `${profileId}.modules`);
    assertUnique(profile.middlewareServices, `${profileId}.middlewareServices`);
    assertUnique(profile.deviceServices, `${profileId}.deviceServices`);
    assertUnique(profile.features, `${profileId}.features`);
    assertUnique(profile.capabilities, `${profileId}.capabilities`);
  }
}

function shellCaseValue(entries, fallback = '') {
  return entries.map(([key, value]) => `        ${key}) echo ${JSON.stringify(String(value))} ;;`).join('\n')
    + `\n        *) echo ${JSON.stringify(String(fallback))} ;;`;
}

function shellMembershipFunction(name, catalogKey, profileKey) {
  const branches = manifest.profileOrder.map((profileId) => {
    const members = manifest.profiles[profileId][profileKey];
    const patterns = members.length ? members.map(member => `${profileId}:${member}`).join('|') : `${profileId}:__none__`;
    return `        ${patterns}) return 0 ;;`;
  });
  return `${name}() {
    local profile item
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    item="\${2:-}"
    case "\${profile}:\${item}" in
${branches.join('\n')}
        *) return 1 ;;
    esac
}`;
}

function enabledContainers(profileId) {
  const profile = manifest.profiles[profileId];
  const names = [];
  for (const service of profile.middlewareServices) {
    if (service.endsWith('-init')) continue;
    names.push(...(manifest.containerNames.middleware[service] ?? []));
  }
  for (const service of profile.deviceServices) names.push(service);
  for (const moduleName of profile.modules) names.push(...(manifest.containerNames.modules[moduleName] ?? []));
  return [...new Set(names)];
}

function renderShell() {
  const aliases = [];
  for (const profileId of manifest.profileOrder) {
    aliases.push([profileId, profileId]);
    for (const alias of manifest.profiles[profileId].aliases) aliases.push([String(alias).toLowerCase(), profileId]);
  }

  const normalizeCases = aliases
    .map(([alias, profileId]) => `        ${alias}) echo ${JSON.stringify(profileId)} ;;`)
    .join('\n');
  const profileListCases = (field, source = manifest.profiles) => manifest.profileOrder
    .map(profileId => `        ${profileId}) echo ${JSON.stringify(source[profileId][field].join(' '))} ;;`)
    .join('\n');
  const skipCases = (catalog, field) => manifest.profileOrder.map((profileId) => {
    const enabled = new Set(manifest.profiles[profileId][field]);
    return `        ${profileId}) echo ${JSON.stringify(catalog.filter(item => !enabled.has(item)).join(' '))} ;;`;
  }).join('\n');
  const containerCases = manifest.profileOrder
    .map(profileId => `        ${profileId}) echo ${JSON.stringify(enabledContainers(profileId).join(' '))} ;;`)
    .join('\n');
  const middlewareContainers = manifest.middlewareServiceOrder
    .flatMap(service => manifest.containerNames.middleware[service] ?? []);
  const applicationContainers = manifest.moduleOrder
    .flatMap(moduleName => manifest.containerNames.modules[moduleName] ?? []);
  const moduleOverrides = Object.entries(manifest.moduleEnvironmentOverrides)
    .map(([moduleName, envName]) => `        ${moduleName}) echo ${JSON.stringify(envName)} ;;`).join('\n');
  const middlewareOverrides = Object.entries(manifest.middlewareEnvironmentOverrides)
    .map(([service, envName]) => `        ${service}) echo ${JSON.stringify(envName)} ;;`).join('\n');
  const featureOverrides = Object.entries(manifest.featureEnvironmentOverrides)
    .map(([feature, envName]) => `        ${feature}) echo ${JSON.stringify(envName)} ;;`).join('\n');
  const middlewareContainerCases = manifest.middlewareServiceOrder
    .map(service => `        ${service}) echo ${JSON.stringify((manifest.containerNames.middleware[service] ?? []).join(' '))} ;;`)
    .join('\n');
  const moduleRuntimeCases = field => manifest.moduleOrder
    .map(moduleName => `        ${JSON.stringify(moduleName)}) echo ${JSON.stringify(String(manifest.moduleRuntime[moduleName][field]))} ;;`)
    .join('\n');
  const moduleContainerCases = manifest.moduleOrder
    .map(moduleName => `        ${moduleName}) echo ${JSON.stringify((manifest.containerNames.modules[moduleName] ?? []).join(' '))} ;;`)
    .join('\n');
  const externalModules = Object.entries(manifest.externalModules ?? {});
  const externalModuleCases = field => externalModules
    .map(([moduleName, source]) => `        ${moduleName}) echo ${JSON.stringify(source[field])} ;;`)
    .join('\n');

  return `#!/bin/bash
# Generated by generate-deploy-profiles.mjs from deploy-profiles.json. Do not edit.

deploy_profile_manifest_profile_ids() {
    printf '%s\\n' ${manifest.profileOrder.map(JSON.stringify).join(' ')}
}

deploy_profile_manifest_normalize() {
    local value
    value="$(printf '%s' "\${1:-\${EASYAIOT_DEPLOY_PROFILE:-${manifest.defaultProfile}}}" | tr '[:upper:]' '[:lower:]')"
    case "$value" in
${normalizeCases}
        *)
        printf '未知部署规格: %s\\n' "\${1:-\${EASYAIOT_DEPLOY_PROFILE:-}}" >&2
        return 1
        ;;
    esac
}

deploy_profile_manifest_network_name() {
    echo ${JSON.stringify(manifest.networkName)}
}

deploy_profile_manifest_middleware_containers() {
    echo ${JSON.stringify(middlewareContainers.join(' '))}
}

deploy_profile_manifest_middleware_service_order() {
    printf '%s\\n' ${manifest.middlewareServiceOrder.map(JSON.stringify).join(' ')}
}

deploy_profile_manifest_device_containers() {
    echo ${JSON.stringify(manifest.deviceServiceOrder.join(' '))}
}

deploy_profile_manifest_application_containers() {
    echo ${JSON.stringify(applicationContainers.join(' '))}
}

deploy_profile_manifest_middleware_service_containers() {
    case "\${1:-}" in
${middlewareContainerCases}
        *) echo "" ;;
    esac
}

deploy_profile_manifest_module_containers() {
    case "\${1:-}" in
${moduleContainerCases}
        *) echo "" ;;
    esac
}

deploy_profile_manifest_module_order() {
    printf '%s\\n' ${manifest.moduleOrder.map(JSON.stringify).join(' ')}
}

${shellMembershipFunction('deploy_profile_manifest_module_enabled', 'moduleOrder', 'modules')}

deploy_profile_manifest_module_label() {
    case "\${1:-}" in
${moduleRuntimeCases('label')}
        *) echo "\${1:-}" ;;
    esac
}

deploy_profile_manifest_module_port() {
    case "\${1:-}" in
${moduleRuntimeCases('port')}
        *) echo "" ;;
    esac
}

deploy_profile_manifest_module_health_path() {
    case "\${1:-}" in
${moduleRuntimeCases('healthPath')}
        *) echo "" ;;
    esac
}

deploy_profile_manifest_module_override_env() {
    case "\${1:-}" in
${moduleOverrides}
        *) echo "" ;;
    esac
}

deploy_profile_manifest_external_modules() {
    printf '%s\\n' ${externalModules.map(([moduleName]) => JSON.stringify(moduleName)).join(' ')}
}

deploy_profile_manifest_external_module_repository() {
    case "\${1:-}" in
${externalModuleCases('repository')}
        *) echo "" ;;
    esac
}

deploy_profile_manifest_external_module_ref() {
    case "\${1:-}" in
${externalModuleCases('ref')}
        *) echo "" ;;
    esac
}

${shellMembershipFunction('deploy_profile_manifest_feature_enabled', 'featureOrder', 'features')}

deploy_profile_manifest_feature_override_env() {
    case "\${1:-}" in
${featureOverrides}
        *) echo "" ;;
    esac
}

${shellMembershipFunction('deploy_profile_manifest_middleware_enabled', 'middlewareServiceOrder', 'middlewareServices')}

deploy_profile_manifest_middleware_override_env() {
    case "\${1:-}" in
${middlewareOverrides}
        *) echo "" ;;
    esac
}

deploy_profile_manifest_middleware_skipped_services() {
    local profile
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    case "$profile" in
${skipCases(manifest.middlewareServiceOrder, 'middlewareServices')}
    esac
}

deploy_profile_manifest_device_enabled_services() {
    local profile
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    case "$profile" in
${profileListCases('deviceServices')}
    esac
}

deploy_profile_manifest_device_skipped_services() {
    local profile
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    case "$profile" in
${skipCases(manifest.deviceServiceOrder, 'deviceServices')}
    esac
}

deploy_profile_manifest_frontend_profile() {
    local profile
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    case "$profile" in
${shellCaseValue(manifest.profileOrder.map(id => [id, manifest.profiles[id].frontendProfile]), manifest.profiles[manifest.defaultProfile].frontendProfile)}
    esac
}

deploy_profile_manifest_budget_mib() {
    local profile
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    case "$profile" in
${shellCaseValue(manifest.profileOrder.map(id => [id, manifest.profiles[id].containerMemoryBudgetMiB]), manifest.profiles[manifest.defaultProfile].containerMemoryBudgetMiB)}
    esac
}

deploy_profile_manifest_budget_label() {
    local profile
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    case "$profile" in
${shellCaseValue(manifest.profileOrder.map(id => [id, manifest.profiles[id].budgetLabel]), manifest.profiles[manifest.defaultProfile].budgetLabel)}
    esac
}

deploy_profile_manifest_label() {
    local profile
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    case "$profile" in
${shellCaseValue(manifest.profileOrder.map(id => [id, manifest.profiles[id].label]), manifest.profiles[manifest.defaultProfile].label)}
    esac
}

deploy_profile_manifest_local_storage() {
    local profile
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    case "$profile" in
${manifest.profileOrder.filter(id => manifest.profiles[id].localStorage).map(id => `        ${id}) return 0 ;;`).join('\n')}
        *) return 1 ;;
    esac
}

deploy_profile_manifest_compose_profile_flags() {
    local profile flags=""
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    deploy_profile_manifest_middleware_enabled "$profile" TDengine && flags="--profile tdengine"
    if deploy_profile_manifest_middleware_enabled "$profile" EMQX; then
        flags="\${flags:+$flags }--profile emqx"
    fi
    echo "$flags"
}

deploy_profile_manifest_expected_containers() {
    local profile
    profile="$(deploy_profile_manifest_normalize "\${1:-}")"
    case "$profile" in
${containerCases}
    esac
}
`;
}

function renderWeb() {
  const profileIds = manifest.profileOrder.map(id => `  '${id}',`).join('\n');
  const capabilityIds = [...new Set([
    ...manifest.profileOrder.flatMap(id => manifest.profiles[id].capabilities),
    ...manifest.web.menuAliases.map(alias => alias.capabilityId),
  ])].sort();
  return `// Generated by .scripts/docker/generate-deploy-profiles.mjs. Do not edit.

export const deploymentProfileIds = [
${profileIds}
] as const

export const deploymentCapabilityIds = [
${capabilityIds.map(id => `  '${id}',`).join('\n')}
] as const

export const deploymentProfileManifest = ${JSON.stringify({
    defaultProfile: manifest.defaultProfile,
    profileOrder: manifest.profileOrder,
    profiles: Object.fromEntries(manifest.profileOrder.map(id => [id, {
      aliases: manifest.profiles[id].aliases,
      capabilities: manifest.profiles[id].capabilities,
    }])),
    web: manifest.web,
  }, null, 2)} as const

export type DeploymentProfileId = typeof deploymentProfileIds[number]
export type DeploymentCapabilityId = typeof deploymentCapabilityIds[number]
`;
}

validate();
const outputs = [[shellPath, renderShell()], [webPath, renderWeb()]];
const check = process.argv.includes('--check');
let stale = false;
for (const [path, content] of outputs) {
  if (check) {
    let actual = '';
    try { actual = readFileSync(path, 'utf8'); } catch {}
    if (actual !== content) {
      console.error(`stale generated file: ${path}`);
      stale = true;
    }
  } else {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, content, 'utf8');
    console.log(`generated ${path}`);
  }
}
if (stale) process.exitCode = 1;
