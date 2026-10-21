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
  {
    // 历史上硬编码在 compose / yaml / 安装脚本 / 源码中的平台默认口令；
    // 拆开拼接，避免本文件自身命中
    kind: 'known-default-credential',
    pattern: new RegExp(
      `(?:${['iot', '45722414822'].join('')}|${['basiclab', '@iot'].join('')}[A-Za-z0-9]{6,})`,
      'g',
    ),
  },
];

// 配置类文件：compose / Spring yaml / shell / env 模板等，额外做逐行的口令字面量检查
const CONFIG_FILE_PATTERN = /(?:\.ya?ml|\.sh|\.properties|\.ps1|\.ini|\.conf|\.toml)$|(?:^|\/)Dockerfile[^/]*$|(?:^|\/)(?:[^/]*\.)?env\.example$|(?:^|\/)\.env(?:\.[^/]*)?$/i;
const CONFIG_FILE_EXCLUDES = [
  /(?:^|\/)pnpm-lock\.yaml$/i,
  /(?:^|\/)\.github\/workflows\//i,
  /(?:^|\/)3rdparty\//i,
];
// 变量名以 password / passwd / secret / token（可带 _key）结尾才视为凭据，排除 *_SECRET_DIR 之类
const SECRET_NAME = '[A-Za-z0-9_.-]*(?:password|passwd|secret|token)(?:[_-]key)?';
// ${SECRET:-literal}（shell / compose）与 ${secret:literal}（Spring）：给凭据写了字面量默认值
const SECRET_LITERAL_FALLBACK = new RegExp(
  `\\$\\{(${SECRET_NAME}):-?(?![-?=+}$\\\\])([^}]*)\\}`,
  'gi',
);
// postgresql://user:pass@ 这类连接串中内嵌的口令
const CONNECTION_STRING_CREDENTIAL = /\b(?:postgres(?:ql)?|mysql|redis|rediss|amqp|mongodb)(?:\+\w+)?:\/\/[^\s:@/'"]*:([^\s@/'"]+)@/gi;
// YAML（含注释掉的行与 compose 列表项）中 password: literal / - PASSWORD=literal
const YAML_SECRET_ENTRY = new RegExp(
  `^\\s*(?:#\\s*)?(?:-\\s+)?(${SECRET_NAME})\\s*[:=]\\s*(.*)$`,
  'i',
);
// 同一行或上一行带此标记时跳过逐行检查（仅用于上游出厂口令、演示种子数据等已评审的例外）
const ALLOW_MARKER = 'secret-hygiene: allow';

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
  // 各模块安装脚本生成的 .env.docker 含真实凭据，只允许跟踪 *.example 模板
  /(?:^|\/)\.env\.docker$/i,
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
    .test(value)
    || /^your[-_]/i.test(value)
    || /change[-_]?me/i.test(value);
}

// 连接串 / yaml 中的口令占位：变量引用、尖括号、CHANGE_ME、纯说明文字（如「密码」）等
function isSafeCredentialPlaceholder(value) {
  const normalized = normalizeEnvValue(value).replace(/\s+#.*$/, '').trim();
  if (!normalized || normalized.startsWith('#') || /^\\?\$/.test(normalized)) {
    return true;
  }
  if (!/[A-Za-z0-9]/.test(normalized)) {
    return true;
  }
  return isSafeEnvPlaceholder(normalized)
    || /^(?:password|passwd|pass|secret|token|%s|\*+|\{[^}]*\}|true|false|null|~|[|>][-+]?)$/i.test(normalized);
}

function isConfigFile(file) {
  const normalized = String(file).replaceAll('\\', '/');
  return CONFIG_FILE_PATTERN.test(normalized)
    && !CONFIG_FILE_EXCLUDES.some(pattern => pattern.test(normalized));
}

function isEnvTemplateOrRuntimeFile(file) {
  return /(?:^|\/)\.env(?:\.|$)|(?:^|\/)(?:[^/]*\.)?env\.example$/i.test(file);
}

function scanConfigLines(lines, file, findings) {
  const yaml = /\.ya?ml$/i.test(file);
  lines.forEach((line, index) => {
    if (line.includes(ALLOW_MARKER) || (index > 0 && lines[index - 1].includes(ALLOW_MARKER))) {
      return;
    }
    SECRET_LITERAL_FALLBACK.lastIndex = 0;
    for (const match of line.matchAll(SECRET_LITERAL_FALLBACK)) {
      if (!isSafeCredentialPlaceholder(match[2])) {
        findings.push(finding(file, index + 1, 'secret-literal-fallback'));
      }
    }
    CONNECTION_STRING_CREDENTIAL.lastIndex = 0;
    for (const match of line.matchAll(CONNECTION_STRING_CREDENTIAL)) {
      if (!isSafeCredentialPlaceholder(match[1])) {
        findings.push(finding(file, index + 1, 'connection-string-credential'));
      }
    }
    // MinIO 出厂账号（用户名与口令相同），配置与脚本里不得作为默认值
    if (/\bminioadmin\b/i.test(line)) {
      findings.push(finding(file, index + 1, 'minio-default-credential'));
    }
    if (yaml) {
      const match = line.match(YAML_SECRET_ENTRY);
      if (match && !isSafeCredentialPlaceholder(match[2])) {
        findings.push(finding(file, index + 1, 'yaml-secret-literal'));
      }
    }
  });
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

  const normalizedFile = file.replaceAll('\\', '/');
  const lines = source.split(/\r?\n/);
  if (isEnvTemplateOrRuntimeFile(normalizedFile)) {
    lines.forEach((line, index) => {
      if (line.includes(ALLOW_MARKER) || (index > 0 && lines[index - 1].includes(ALLOW_MARKER))) {
        return;
      }
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

  if (isConfigFile(normalizedFile)) {
    scanConfigLines(lines, normalizedFile, findings);
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
