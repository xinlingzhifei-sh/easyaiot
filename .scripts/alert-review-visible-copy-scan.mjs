import { existsSync, readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const VISIBLE_COPY_TARGETS = [
  'WEB/src/views/alert/components/AlertReviewWorkbench.vue',
  'WEB/src/views/alert/index.vue',
  'WEB/src/views/camera/components/DeviceRegionDrawer/index.vue',
  'WEB/src/components/VideoPlayer/DialogPlayer.vue',
  'WEB/src/components/Player/module/jessibuca.vue',
  'WEB/src/api/device/patrol.ts',
  'WEB/src/utils/alertRecord.ts',
  'WEB/src/utils/alertRecordPlayback.ts',
  'WEB/scripts/fixtures/alert-review-workbench-e2e/main.ts',
  'VIDEO/app/blueprints/record.py',
  'VIDEO/app/blueprints/alert.py',
  'VIDEO/app/services/record_export_service.py',
  'VIDEO/app/services/record_video_service.py',
];

export const VISIBLE_COPY_MOJIBAKE_PATTERNS = [
  { pattern: '\u93bd\u52eb\u511a', reason: 'encoding_mojibake' },
  { pattern: '\u5bb8\u2103', reason: 'encoding_mojibake' },
  { pattern: '\uFFFD', reason: 'encoding_replacement_character' },
  { pattern: '\u935b\u5a45', reason: 'encoding_mojibake' },
  { pattern: '\u7efe\u8de8', reason: 'encoding_mojibake' },
  { pattern: '\u8930\u66de', reason: 'encoding_mojibake' },
  { pattern: '\u93c3\u72bb', reason: 'encoding_mojibake' },
  { pattern: '\u5bf0\u546d', reason: 'encoding_mojibake' },
  { pattern: '\u675e\ue102', reason: 'encoding_mojibake' },
  { pattern: '\u7459\u55db', reason: 'encoding_mojibake' },
  { pattern: '\u93be\ue15f', reason: 'encoding_mojibake' },
  { pattern: '\u7035\u677f', reason: 'encoding_mojibake' },
  { pattern: '\u745c\u7248', reason: 'encoding_mojibake' },
  { pattern: '\u9227', reason: 'encoding_mojibake' },
  { pattern: '\u95bb', reason: 'encoding_mojibake' },
  { pattern: '\u9420', reason: 'encoding_mojibake' },
  { pattern: '\u93c9\u2542', reason: 'encoding_mojibake' },
  { pattern: '\u95c1\u63d2', reason: 'encoding_mojibake' },
  { pattern: '\u95ba\u581d', reason: 'encoding_mojibake' },
  { pattern: '\u7f02\u54c4\u7d8d', reason: 'encoding_mojibake' },
  { pattern: '\u5bf0\u546e\u589c', reason: 'encoding_mojibake' },
  { pattern: '\u93c8\ue048\u53a4', reason: 'encoding_mojibake' },
  { pattern: '\u7f03\u7521', reason: 'encoding_mojibake' },
];

function normalizePath(path) {
  return String(path || '').replace(/\\/g, '/').replace(/^\.\//, '');
}

function lineNumberAt(content, index) {
  return content.slice(0, index).split(/\r?\n/).length;
}

export function scanVisibleCopyFiles(files) {
  const blockers = [];
  for (const file of files) {
    const path = normalizePath(file.path);
    const content = String(file.content ?? '');
    for (const { pattern, reason } of VISIBLE_COPY_MOJIBAKE_PATTERNS) {
      const index = content.indexOf(pattern);
      if (index === -1) {
        continue;
      }
      blockers.push({
        path,
        reason,
        pattern,
        line: lineNumberAt(content, index),
      });
      break;
    }
  }
  return {
    ok: blockers.length === 0,
    blockers,
  };
}

function readTargets(targets) {
  const files = [];
  for (const target of targets) {
    const path = normalizePath(target);
    if (!existsSync(path) || !statSync(path).isFile()) {
      throw new Error(`visible-copy target missing: ${path}`);
    }
    files.push({ path, content: readFileSync(path, 'utf8') });
  }
  return files;
}

function parseTargets(args) {
  const explicitTargets = args
    .filter(arg => arg.startsWith('--target='))
    .map(arg => normalizePath(arg.slice('--target='.length)))
    .filter(Boolean);
  return explicitTargets.length ? explicitTargets : VISIBLE_COPY_TARGETS;
}

function printHelp() {
  console.log(`Usage: node .scripts/alert-review-visible-copy-scan.mjs [--target=PATH ...]

Scans FR alert-review visible-copy files for UTF-8 replacement characters and common mojibake fragments.`);
}

function runCli() {
  const args = process.argv.slice(2);
  if (args.includes('--help') || args.includes('-h')) {
    printHelp();
    return;
  }

  let result;
  try {
    result = scanVisibleCopyFiles(readTargets(parseTargets(args)));
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
    return;
  }

  if (!result.ok) {
    console.error('Alert review visible copy scan failed:');
    for (const blocker of result.blockers) {
      console.error(`- ${blocker.reason}: ${blocker.path}:${blocker.line}`);
    }
    process.exitCode = 1;
    return;
  }

  console.log(`Alert review visible copy scan OK: ${parseTargets(args).length} file(s) checked.`);
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)) === resolve(process.argv[1])) {
  runCli();
}
