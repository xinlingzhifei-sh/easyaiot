import { readFile, stat } from 'node:fs/promises';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const MAX_TEXT_FILE_BYTES = 25 * 1024 * 1024;
const SCAN_BATCH_SIZE = 32;

const SECRET_PATTERNS = [
  {
    kind: 'aws-access-key',
    pattern: /\b(?:AKIA|ASIA)[A-Z0-9]{16}\b/g,
  },
  {
    kind: 'github-token',
    pattern: /\b(?:github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,})\b/g,
  },
  {
    kind: 'slack-token',
    pattern: /\bxox[baprs]-[A-Za-z0-9-]{10,}\b/g,
  },
  {
    kind: 'google-api-key',
    pattern: /\bAIza[0-9A-Za-z_-]{30,}\b/g,
  },
  {
    kind: 'openai-api-key',
    pattern: /\b(?:sk-proj-[A-Za-z0-9_-]{40,}|sk-[A-Za-z0-9]{32,})\b/g,
  },
  {
    kind: 'jwt',
    pattern: /\beyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}\b/g,
  },
  {
    kind: 'webhook-secret',
    pattern: /(?:qyapi\.weixin\.qq\.com\/cgi-bin\/webhook\/send\?key=|oapi\.dingtalk\.com\/robot\/send\?access_token=|open\.feishu\.cn\/open-apis\/bot\/v2\/hook\/|www\.feishu\.cn\/flow\/api\/trigger-webhook\/)(?!CHANGE_ME\b)[A-Za-z0-9_-]{8,}/g,
  },
  {
    kind: 'private-key-block',
    pattern: /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[\s\S]{64,}?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/g,
  },
];

const FORBIDDEN_TRACKED_PATHS = [
  /^deploy-packages\//i,
  /^\.artifacts\//i,
  /^output\//i,
  /^tmp\//i,
  /^\.pnpm-store\//i,
  /^\.scripts\/docker\/fuxa_data\//i,
  /^\.scripts\/docker\/vscode_data\//i,
  /^VIDEO\/data\/face_db\//i,
  /^NODE\/agent\.env$/i,
  /^APP\/env\/\.env$/i,
  /^WEB\/\.env\.development\.bak$/i,
  /^TASK\/config\/test[^/]*\.ini$/i,
  /^\.scripts\/docker\/\.env\.docker$/i,
];

function isRuntimeEnvironmentPath(file) {
  return /^(?:AI|VIDEO)\/\.env(?:\.[^/]+)?$/i.test(file)
    && !/\.example$/i.test(file);
}

function lineNumberAt(text, index) {
  let line = 1;
  for (let position = 0; position < index; position += 1) {
    if (text.charCodeAt(position) === 10) {
      line += 1;
    }
  }
  return line;
}

function finding(file, line, kind) {
  return { file, line, kind };
}

function normalizeEnvValue(rawValue) {
  return String(rawValue)
    .trim()
    .replace(/^(['"])(.*)\1$/, '$2')
    .trim();
}

function isSafeEnvPlaceholder(value) {
  if (!value || /^\$\{[A-Z_][A-Z0-9_]*(?::[^}]*)?\}$/.test(value)) {
    return true;
  }
  return /^(?:CHANGE_ME|CHANGEME|PLACEHOLDER|EXAMPLE|YOUR_[A-Z0-9_]+|X{4,}|<[^>]+>)$/i
    .test(value);
}

export function isForbiddenTrackedPath(file) {
  const normalized = String(file).replaceAll('\\', '/');
  return isRuntimeEnvironmentPath(normalized)
    || FORBIDDEN_TRACKED_PATHS.some(pattern => pattern.test(normalized));
}

export function scanText(text, file = '<memory>') {
  const source = String(text);
  const findings = [];

  for (const { kind, pattern } of SECRET_PATTERNS) {
    pattern.lastIndex = 0;
    for (const match of source.matchAll(pattern)) {
      findings.push(finding(file, lineNumberAt(source, match.index), kind));
    }
  }

  if (/(?:^|\/)\.env(?:\.|$)/i.test(file.replaceAll('\\', '/'))) {
    source.split(/\r?\n/).forEach((line, index) => {
      const match = line.match(
        /^\s*([A-Z0-9_]*(?:PASSWORD|PASSWD|SECRET|TOKEN|KEY)[A-Z0-9_]*)\s*=\s*(.*?)\s*$/,
      );
      if (!match) {
        return;
      }
      const value = normalizeEnvValue(match[2]);
      if (!isSafeEnvPlaceholder(value)) {
        findings.push(finding(file, index + 1, 'tracked-env-secret-literal'));
      }
    });
  }

  return findings;
}

export function formatFinding(result) {
  return `${result.file}:${result.line} (${result.kind})`;
}

function listTrackedFiles(repositoryRoot) {
  const result = spawnSync(
    'git',
    ['-c', 'core.quotepath=false', 'ls-files', '-z'],
    {
      cwd: repositoryRoot,
      encoding: 'buffer',
      windowsHide: true,
    },
  );
  if (result.status !== 0) {
    throw new Error(`git ls-files failed with exit code ${result.status}`);
  }
  return result.stdout
    .toString('utf8')
    .split('\0')
    .filter(Boolean);
}

async function main() {
  const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
  const trackedFiles = listTrackedFiles(repositoryRoot);
  const findings = [];
  let scannedTextFiles = 0;

  for (let offset = 0; offset < trackedFiles.length; offset += SCAN_BATCH_SIZE) {
    const batch = trackedFiles.slice(offset, offset + SCAN_BATCH_SIZE);
    const results = await Promise.all(batch.map(async (file) => {
      if (isForbiddenTrackedPath(file)) {
        return {
          scanned: false,
          findings: [finding(file, 1, 'runtime-file-tracked')],
        };
      }

      const absolutePath = path.join(repositoryRoot, file);
      let metadata;
      try {
        metadata = await stat(absolutePath);
      } catch {
        return { scanned: false, findings: [] };
      }
      if (!metadata.isFile() || metadata.size > MAX_TEXT_FILE_BYTES) {
        return { scanned: false, findings: [] };
      }

      const buffer = await readFile(absolutePath);
      if (buffer.includes(0)) {
        return { scanned: false, findings: [] };
      }
      return {
        scanned: true,
        findings: scanText(buffer.toString('utf8'), file),
      };
    }));

    for (const result of results) {
      if (result.scanned) {
        scannedTextFiles += 1;
      }
      findings.push(...result.findings);
    }
  }

  if (findings.length > 0) {
    console.error(`REPOSITORY_SECRET_HYGIENE_FINDINGS=${findings.length}`);
    findings.forEach(result => console.error(formatFinding(result)));
    process.exitCode = 1;
    return;
  }

  console.log(`REPOSITORY_SECRET_HYGIENE_OK files=${scannedTextFiles}`);
}

const invokedPath = process.argv[1] ? path.resolve(process.argv[1]).toLowerCase() : '';
const modulePath = fileURLToPath(import.meta.url).toLowerCase();
if (invokedPath === modulePath) {
  await main();
}
