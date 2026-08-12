import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync, writeFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const script = resolve('.scripts/configure-nginx-stream-secret.mjs');
const root = mkdtempSync(join(tmpdir(), 'yfeieye-nginx-secret-'));

try {
  const envFile = join(root, '.env.docker');
  const outputFile = join(root, 'yfeieye-stream-secret.runtime.conf');
  const secret = 'unit-test-stream-ticket-secret-32-bytes-plus';
  writeFileSync(envFile, `STREAM_TICKET_SECRET=${secret}\n`, 'utf8');

  const success = spawnSync(process.execPath, [
    script,
    `--env-file=${envFile}`,
    `--output-file=${outputFile}`,
    '--skip-nginx-check',
  ], { encoding: 'utf8' });
  assert.equal(success.status, 0, success.stderr);
  assert.equal(
    readFileSync(outputFile, 'utf8'),
    `set $stream_secret "${secret}";\n`,
  );
  assert.equal(success.stdout.includes(secret), false);

  const invalidEnv = join(root, 'invalid.env');
  const invalidOutput = join(root, 'invalid.conf');
  writeFileSync(invalidEnv, 'STREAM_TICKET_SECRET=short\n', 'utf8');
  const invalid = spawnSync(process.execPath, [
    script,
    `--env-file=${invalidEnv}`,
    `--output-file=${invalidOutput}`,
    '--skip-nginx-check',
  ], { encoding: 'utf8' });
  assert.notEqual(invalid.status, 0);
  assert.equal(existsSync(invalidOutput), false);

  console.log('nginx stream secret configurator tests OK');
} finally {
  rmSync(root, { recursive: true, force: true });
}
