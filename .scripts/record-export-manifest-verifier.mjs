#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '..');
const verifierPath = join(repoRoot, 'VIDEO', 'app', 'services', 'record_export_manifest_verifier.py');
const python = process.env.PYTHON || (process.platform === 'win32' ? 'python' : 'python3');
const ALLOWED_SENSITIVE_ENV_KEYS = new Set([
  'YFEIEYE_RECORD_EXPORT_HMAC_SECRET',
  'YFEIEYE_RECORD_EXPORT_HMAC_KEYS',
  'YFEIEYE_RECORD_EXPORT_KEY_ID',
]);
const UNRELATED_SENSITIVE_ENV_KEYS = new Set([
  'YFEIEYE_DEVICE_AUTH_TOKEN',
  'YFEIEYE_VIDEO_SMOKE_TOKEN',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_URL',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES',
  'YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI',
  'YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI',
]);

if (!existsSync(verifierPath)) {
  console.error(`record export manifest verifier not found: ${verifierPath}`);
  process.exit(1);
}

const result = spawnSync(python, [verifierPath, ...process.argv.slice(2)], {
  env: buildManifestVerifierEnvironment(),
  stdio: 'inherit',
});

if (result.error) {
  console.error(`failed to run ${python}: ${result.error.message}`);
  process.exit(1);
}

process.exit(result.status ?? 1);

function buildManifestVerifierEnvironment(parentEnv = process.env) {
  const env = {};
  for (const [key, value] of Object.entries(parentEnv || {})) {
    const normalizedKey = key.toUpperCase();
    if (ALLOWED_SENSITIVE_ENV_KEYS.has(normalizedKey)) {
      env[key] = value;
      continue;
    }
    if (UNRELATED_SENSITIVE_ENV_KEYS.has(normalizedKey)
        || /(?:^|_)(?:ACCESS_TOKEN|AUTH_TOKEN|SESSION_TOKEN|TOKEN|ACCESS_KEY(?:_ID)?|COOKIE|COOKIES|LOCAL_STORAGE|PASSWORD|PASSWD|SECRET|SIGNATURE|API_KEY|PRIVATE_KEY)(?:_|$)/i.test(normalizedKey)) {
      continue;
    }
    env[key] = value;
  }
  return env;
}
