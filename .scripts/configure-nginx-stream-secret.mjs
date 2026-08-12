#!/usr/bin/env node
import {
  chmodSync,
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  rmSync,
  statSync,
  writeFileSync,
} from 'node:fs';
import { dirname, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

function parseArgs(argv) {
  const options = {
    envFile: resolve('VIDEO/.env.docker'),
    outputFile: '/etc/nginx/yfeieye-secrets/yfeieye-stream-secret.runtime.conf',
    nginxBin: 'nginx',
    skipNginxCheck: false,
    reload: false,
  };
  for (const arg of argv) {
    if (arg.startsWith('--env-file=')) options.envFile = resolve(arg.slice(11));
    else if (arg.startsWith('--output-file=')) options.outputFile = resolve(arg.slice(14));
    else if (arg.startsWith('--nginx-bin=')) options.nginxBin = arg.slice(12);
    else if (arg === '--skip-nginx-check') options.skipNginxCheck = true;
    else if (arg === '--reload') options.reload = true;
    else throw new Error(`Unknown argument: ${arg}`);
  }
  if (options.reload && options.skipNginxCheck) {
    throw new Error('--reload requires the nginx configuration check');
  }
  return options;
}

function readEnvValue(path, key) {
  const source = readFileSync(path, 'utf8');
  for (const line of source.split(/\r?\n/)) {
    const match = /^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/.exec(line);
    if (!match || match[1] !== key) continue;
    let value = match[2];
    if ((value.startsWith('"') && value.endsWith('"'))
        || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    return value;
  }
  return '';
}

function restoreOutput(outputFile, previous) {
  if (!previous) {
    rmSync(outputFile, { force: true });
    return;
  }
  const restoreFile = `${outputFile}.${process.pid}.restore`;
  writeFileSync(restoreFile, previous.content, { mode: previous.mode });
  renameSync(restoreFile, outputFile);
  chmodSync(outputFile, previous.mode);
}

function runNginx(nginxBin, args) {
  const result = spawnSync(nginxBin, args, { encoding: 'utf8' });
  return result.status === 0 && !result.error;
}

const options = parseArgs(process.argv.slice(2));
const secret = readEnvValue(options.envFile, 'STREAM_TICKET_SECRET');
if (Buffer.byteLength(secret, 'utf8') < 32) {
  throw new Error('STREAM_TICKET_SECRET must contain at least 32 bytes');
}
if (!/^[A-Za-z0-9._~+/=-]+$/.test(secret)) {
  throw new Error('STREAM_TICKET_SECRET contains unsupported characters');
}

const outputFile = options.outputFile;
mkdirSync(dirname(outputFile), { recursive: true });
const previous = existsSync(outputFile)
  ? { content: readFileSync(outputFile), mode: statSync(outputFile).mode & 0o777 }
  : null;
const temporaryFile = `${outputFile}.${process.pid}.tmp`;

try {
  writeFileSync(temporaryFile, `set $stream_secret "${secret}";\n`, { mode: 0o600 });
  renameSync(temporaryFile, outputFile);
  chmodSync(outputFile, 0o600);
  if (!options.skipNginxCheck && !runNginx(options.nginxBin, ['-t'])) {
    throw new Error('nginx configuration check failed');
  }
  if (options.reload && !runNginx(options.nginxBin, ['-s', 'reload'])) {
    throw new Error('nginx reload failed');
  }
} catch (error) {
  rmSync(temporaryFile, { force: true });
  restoreOutput(outputFile, previous);
  throw error;
}

console.log(JSON.stringify({
  configured: true,
  nginxChecked: !options.skipNginxCheck,
  nginxReloaded: options.reload,
  outputFile,
}));
