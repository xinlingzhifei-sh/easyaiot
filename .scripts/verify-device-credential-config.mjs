import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SENSITIVE_KEYS = new Set([
  'password',
  'passwd',
  'secret',
  'token',
  'access-token',
  'access-key',
  'secret-key',
  'private-key',
  'api-key',
  'client-secret',
  'signing-key',
  'encryption-key',
  'key-store-password',
  'mqtt-password',
  'username',
  'user-name',
  'mqtt-username',
]);

const RPC_TOKEN_ENVIRONMENT = 'IOT_RPC_INTERNAL_TOKEN';
const RPC_TOKEN_SERVICES = [
  'iot-gateway',
  'iot-system',
  'iot-infra',
  'iot-device',
  'iot-dataset',
  'iot-node',
  'iot-visualize',
  'iot-tdengine',
  'iot-file',
  'iot-message',
  'iot-sink',
  'iot-gb28181',
];

function normalizeKey(key) {
  return String(key)
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replaceAll('_', '-')
    .toLowerCase();
}

function isSensitiveKey(key) {
  const normalized = normalizeKey(key);
  return (
    SENSITIVE_KEYS.has(normalized)
    || normalized.endsWith('-password')
    || normalized.endsWith('-passwd')
    || normalized.endsWith('-secret')
    || normalized.endsWith('-token')
    || normalized.endsWith('-username')
    || normalized.endsWith('-access-key')
    || normalized.endsWith('-secret-key')
    || normalized.endsWith('-private-key')
    || normalized.endsWith('-api-key')
  );
}

function isSensitiveEnvironmentName(name) {
  const normalized = normalizeKey(name);
  return [...SENSITIVE_KEYS].some(key => (
    normalized === key || normalized.endsWith(`-${key}`)
  ));
}

function stripInlineComment(value) {
  let quote = '';
  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    if (quote) {
      if (char === quote && value[index - 1] !== '\\') {
        quote = '';
      }
      continue;
    }
    if (char === '"' || char === "'") {
      quote = char;
      continue;
    }
    if (char === '#' && (index === 0 || /\s/.test(value[index - 1]))) {
      return value.slice(0, index).trim();
    }
  }
  return value.trim();
}

function stripOuterQuotes(value) {
  if (value.length >= 2) {
    const first = value[0];
    const last = value[value.length - 1];
    if ((first === '"' && last === '"') || (first === "'" && last === "'")) {
      return value.slice(1, -1).trim();
    }
  }
  return value;
}

function isEnvironmentReference(value) {
  const normalized = stripOuterQuotes(stripInlineComment(String(value)));
  return /^\$\{[A-Z_][A-Z0-9_]*(?::[^}\r\n]*)?\}$/.test(normalized);
}

function extractEnvironmentReference(value) {
  const normalized = stripOuterQuotes(stripInlineComment(String(value)));
  return normalized.match(/^\$\{([A-Z_][A-Z0-9_]*)(?::[^}\r\n]*)?\}$/)?.[1] || '';
}

function finding(file, line, credentialPath, kind) {
  return { file, line, path: credentialPath, kind };
}

function scanInlineCredentials(value, file, line, credentialPath) {
  const findings = [];
  const normalized = stripOuterQuotes(stripInlineComment(String(value)));
  const urlPassword = normalized.match(/\bpassword=([^&\s'"]+)/i);
  if (urlPassword && !isEnvironmentReference(urlPassword[1])) {
    findings.push(finding(
      file,
      line,
      `${credentialPath}.password`,
      'url-literal',
    ));
  }

  const userInfo = normalized.match(/:\/\/[^:\s/@]+:([^@\s/]+)@/);
  if (userInfo && !isEnvironmentReference(userInfo[1])) {
    findings.push(finding(
      file,
      line,
      `${credentialPath}.userinfo`,
      'url-literal',
    ));
  }
  return findings;
}

export function scanYamlText(text, file = '<memory>') {
  const findings = [];
  let stack = [];

  String(text).split(/\r?\n/).forEach((line, index) => {
    const match = line.match(/^(\s*)([A-Za-z0-9_.-]+)\s*:\s*(.*?)\s*$/);
    if (!match) {
      return;
    }

    const indent = match[1].length;
    const key = match[2];
    const rawValue = stripInlineComment(match[3]);
    stack = stack.filter(entry => entry.indent < indent);
    const credentialPath = [...stack.map(entry => entry.key), key].join('.');

    if (isSensitiveKey(key) && rawValue && !isEnvironmentReference(rawValue)) {
      findings.push(finding(file, index + 1, credentialPath, 'yaml-literal'));
    }
    findings.push(...scanInlineCredentials(
      rawValue,
      file,
      index + 1,
      credentialPath,
    ));

    stack.push({ indent, key });
  });

  return findings;
}

export function collectYamlCredentialReferences(text, file = '<memory>') {
  const references = [];
  const seen = new Set();
  let stack = [];

  String(text).split(/\r?\n/).forEach((line, index) => {
    const match = line.match(/^(\s*)([A-Za-z0-9_.-]+)\s*:\s*(.*?)\s*$/);
    if (!match) {
      return;
    }

    const indent = match[1].length;
    const key = match[2];
    const rawValue = stripInlineComment(match[3]);
    stack = stack.filter(entry => entry.indent < indent);
    const credentialPath = [...stack.map(entry => entry.key), key].join('.');

    const names = [];
    if (isSensitiveKey(key)) {
      names.push(extractEnvironmentReference(rawValue));
    }
    if (/[?&](?:user|password)=/i.test(rawValue)) {
      for (const match of rawValue.matchAll(
        /[?&](?:user|password)=\$\{([A-Z_][A-Z0-9_]*)(?::[^}\r\n]*)?\}/gi,
      )) {
        names.push(match[1]);
      }
    }

    for (const name of names.filter(Boolean)) {
      const identity = `${credentialPath}\0${name}`;
      if (!seen.has(identity)) {
        seen.add(identity);
        references.push({
          file,
          line: index + 1,
          path: credentialPath,
          name,
        });
      }
    }

    stack.push({ indent, key });
  });

  return references;
}

export function scanComposeText(text, file = '<memory>') {
  const findings = [];

  String(text).split(/\r?\n/).forEach((line, index) => {
    const content = stripInlineComment(line);
    if (!content || content.trimStart().startsWith('#')) {
      return;
    }

    const listAssignment = content.match(/^\s*-\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$/);
    const mapAssignment = content.match(/^\s+([A-Z][A-Z0-9_]*)\s*:\s*(.*)$/);
    const assignment = listAssignment || mapAssignment;
    if (assignment) {
      const [, name, rawValue] = assignment;
      const value = rawValue.trim();
      if (isSensitiveEnvironmentName(name) && value && !isEnvironmentReference(value)) {
        findings.push(finding(file, index + 1, name, 'compose-literal'));
      }
    }

    const assignmentName = assignment?.[1] || 'URL';
    findings.push(...scanInlineCredentials(
      assignment?.[2] || content,
      file,
      index + 1,
      assignmentName,
    ));
  });

  return findings;
}

export function collectComposeServiceEnvironmentNames(text) {
  const services = new Map();
  let inServices = false;
  let currentService = '';
  let inEnvironment = false;

  String(text).split(/\r?\n/).forEach((line) => {
    if (/^services:\s*$/.test(line)) {
      inServices = true;
      return;
    }
    if (inServices && /^\S/.test(line) && line.trim()) {
      inServices = false;
      currentService = '';
      inEnvironment = false;
      return;
    }
    if (!inServices) {
      return;
    }

    const serviceMatch = line.match(/^ {2}([A-Za-z0-9_-]+):\s*$/);
    if (serviceMatch) {
      currentService = serviceMatch[1];
      services.set(currentService, new Set());
      inEnvironment = false;
      return;
    }
    if (!currentService) {
      return;
    }
    if (/^ {4}environment:\s*$/.test(line)) {
      inEnvironment = true;
      return;
    }
    if (/^ {4}\S/.test(line)) {
      inEnvironment = false;
    }
    if (!inEnvironment) {
      return;
    }

    const environmentMatch = line.match(
      /^ {6}-\s*([A-Z_][A-Z0-9_]*)(?:=|$)/,
    );
    if (environmentMatch) {
      services.get(currentService).add(environmentMatch[1]);
    }
  });

  return services;
}

export function findServicesMissingEnvironment(
  services,
  requiredServices,
  environmentName,
) {
  return requiredServices.filter(service => (
    !services.get(service)?.has(environmentName)
  ));
}

function collectEnvFileNames(text) {
  const names = new Set();
  String(text).split(/\r?\n/).forEach((line) => {
    const match = line.match(/^\s*([A-Z_][A-Z0-9_]*)=/);
    if (match) {
      names.add(match[1]);
    }
  });
  return names;
}

export function formatFinding(result) {
  return `${result.file}:${result.line} ${result.path} (${result.kind})`;
}

async function collectResourceYamlFiles(directory) {
  const files = [];
  const entries = await readdir(directory, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.name === 'target' || entry.name === '.git') {
      continue;
    }
    const absolutePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...await collectResourceYamlFiles(absolutePath));
      continue;
    }
    if (
      /[\\/]src[\\/]main[\\/]resources[\\/](?:application|bootstrap)[^\\/]*\.ya?ml$/i
        .test(absolutePath)
    ) {
      files.push(absolutePath);
    }
  }
  return files;
}

async function main() {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const deviceRoot = path.join(repositoryRoot, 'DEVICE');
  const yamlFiles = await collectResourceYamlFiles(deviceRoot);
  const findings = [];
  const referencesByModule = new Map();

  for (const absolutePath of yamlFiles.sort()) {
    const relativePath = path.relative(repositoryRoot, absolutePath);
    const yamlText = await readFile(absolutePath, 'utf8');
    findings.push(...scanYamlText(yamlText, relativePath));
    const moduleName = path.relative(deviceRoot, absolutePath).split(path.sep)[0];
    const moduleReferences = referencesByModule.get(moduleName) || [];
    moduleReferences.push(...collectYamlCredentialReferences(
      yamlText,
      relativePath,
    ));
    referencesByModule.set(moduleName, moduleReferences);
  }

  const composePath = path.join(deviceRoot, 'docker-compose.yml');
  const composeText = await readFile(composePath, 'utf8');
  const composeRelativePath = path.relative(repositoryRoot, composePath);
  findings.push(...scanComposeText(composeText, composeRelativePath));

  const serviceEnvironmentNames = collectComposeServiceEnvironmentNames(composeText);
  const envExamplePath = path.join(repositoryRoot, '.scripts', 'docker', 'env.example');
  const envExampleRelativePath = path.relative(repositoryRoot, envExamplePath);
  const envExampleNames = collectEnvFileNames(await readFile(envExamplePath, 'utf8'));
  const checkedPassThrough = new Set();
  const checkedDeclarations = new Set();

  for (const serviceName of findServicesMissingEnvironment(
    serviceEnvironmentNames,
    RPC_TOKEN_SERVICES,
    RPC_TOKEN_ENVIRONMENT,
  )) {
    findings.push(finding(
      composeRelativePath,
      1,
      `${serviceName}.${RPC_TOKEN_ENVIRONMENT}`,
      'missing-compose-pass-through',
    ));
  }
  if (!envExampleNames.has(RPC_TOKEN_ENVIRONMENT)) {
    findings.push(finding(
      envExampleRelativePath,
      1,
      RPC_TOKEN_ENVIRONMENT,
      'missing-env-example',
    ));
  }

  for (const [moduleName, references] of referencesByModule) {
    const serviceNames = serviceEnvironmentNames.get(moduleName) || new Set();
    for (const reference of references) {
      const passThroughIdentity = `${moduleName}\0${reference.name}`;
      if (
        !checkedPassThrough.has(passThroughIdentity)
        && !serviceNames.has(reference.name)
      ) {
        checkedPassThrough.add(passThroughIdentity);
        findings.push(finding(
          composeRelativePath,
          1,
          `${moduleName}.${reference.name}`,
          'missing-compose-pass-through',
        ));
      }
      if (
        !checkedDeclarations.has(reference.name)
        && !envExampleNames.has(reference.name)
      ) {
        checkedDeclarations.add(reference.name);
        findings.push(finding(
          envExampleRelativePath,
          1,
          reference.name,
          'missing-env-example',
        ));
      }
    }
  }

  if (findings.length > 0) {
    console.error(`DEVICE_CREDENTIAL_CONFIG_FINDINGS=${findings.length}`);
    findings.forEach(result => console.error(formatFinding(result)));
    process.exitCode = 1;
    return;
  }

  console.log(`DEVICE_CREDENTIAL_CONFIG_OK files=${yamlFiles.length + 1}`);
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]).toLowerCase() : '';
const modulePath = fileURLToPath(import.meta.url).toLowerCase();
if (invokedPath === modulePath) {
  await main();
}
