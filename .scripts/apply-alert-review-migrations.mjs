import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync, readFileSync } from 'node:fs';
import { basename, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { MIGRATION_FILES } from './alert-review-postgres-migration-smoke.mjs';

const HISTORY_TABLE = 'system_alert_review_schema_history';
const ADVISORY_LOCK_NAME = 'yfeieye:alert-review:production-migrations:v1';

export function parseArgs(args, cwd = process.cwd()) {
  const options = {
    container: null,
    databaseUrl: null,
    database: null,
    user: 'postgres',
    repoRoot: cwd,
    verifyOnly: false,
    dryRun: false,
    help: false,
  };
  for (const arg of args) {
    if (arg === '--help' || arg === '-h') options.help = true;
    else if (arg === '--verify-only') options.verifyOnly = true;
    else if (arg === '--dry-run') options.dryRun = true;
    else if (arg.startsWith('--container=')) options.container = arg.slice('--container='.length);
    else if (arg.startsWith('--database-url=')) options.databaseUrl = arg.slice('--database-url='.length);
    else if (arg.startsWith('--database=')) options.database = arg.slice('--database='.length);
    else if (arg.startsWith('--user=')) options.user = arg.slice('--user='.length);
    else if (arg.startsWith('--repo-root=')) options.repoRoot = resolve(arg.slice('--repo-root='.length));
    else throw new Error(`Unknown option: ${arg}`);
  }
  if (!options.help) {
    const targetCount = Number(Boolean(options.container)) + Number(Boolean(options.databaseUrl));
    if (targetCount !== 1) {
      throw new Error('Specify exactly one of --container=NAME or --database-url=URL');
    }
    if (options.container && !options.database) {
      throw new Error('--database=NAME is required with --container');
    }
  }
  return options;
}

export function buildMigrationPlan(repoRoot, migrationFiles = MIGRATION_FILES) {
  const versions = new Set();
  return migrationFiles.map((relativePath) => {
    const path = resolve(repoRoot, relativePath);
    if (!existsSync(path)) throw new Error(`Migration file not found: ${path}`);
    const version = basename(path);
    if (!/^V[0-9][A-Za-z0-9_]*__.+\.sql$/.test(version)) {
      throw new Error(`Invalid production migration filename: ${version}`);
    }
    if (versions.has(version)) throw new Error(`Duplicate migration version: ${version}`);
    versions.add(version);
    const content = readFileSync(path, 'utf8');
    return {
      version,
      relativePath: relativePath.replaceAll('\\', '/'),
      path,
      content,
      checksum: createHash('sha256').update(content, 'utf8').digest('hex'),
    };
  });
}

export function buildPsqlInvocation(options) {
  if (options.container) {
    return {
      command: 'docker',
      args: [
        'exec', '-i', options.container,
        'psql', '-U', options.user || 'postgres', '-d', options.database,
        '-v', 'ON_ERROR_STOP=1',
      ],
      env: process.env,
      label: `${options.container}/${options.database}`,
    };
  }
  const url = new URL(options.databaseUrl);
  const database = decodeURIComponent(url.pathname.replace(/^\/+/, '')) || 'postgres';
  const env = { ...process.env, PGDATABASE: database };
  if (url.hostname) env.PGHOST = url.hostname;
  if (url.port) env.PGPORT = url.port;
  if (url.username) env.PGUSER = decodeURIComponent(url.username);
  if (url.password) env.PGPASSWORD = decodeURIComponent(url.password);
  if (url.searchParams.has('sslmode')) env.PGSSLMODE = url.searchParams.get('sslmode');
  return {
    command: 'psql',
    args: ['-v', 'ON_ERROR_STOP=1'],
    env,
    label: `psql/${database}`,
  };
}

export function buildPsqlScript(plan, { verifyOnly = false } = {}) {
  const lines = [
    '\\set ON_ERROR_STOP on',
    `SELECT pg_advisory_lock(hashtextextended('${sqlLiteral(ADVISORY_LOCK_NAME)}', 0));`,
  ];
  if (verifyOnly) {
    lines.push(
      `DO $verify_history$`,
      'BEGIN',
      `  IF to_regclass('${HISTORY_TABLE}') IS NULL THEN`,
      `    RAISE EXCEPTION 'migration history missing: ${HISTORY_TABLE}';`,
      '  END IF;',
      'END',
      '$verify_history$;',
    );
  } else {
    lines.push(
      'BEGIN;',
      `CREATE TABLE IF NOT EXISTS ${HISTORY_TABLE} (`,
      '  installed_rank BIGSERIAL PRIMARY KEY,',
      '  version VARCHAR(255) NOT NULL UNIQUE,',
      '  checksum CHAR(64) NOT NULL,',
      '  script_path TEXT NOT NULL,',
      '  installed_by VARCHAR(128) NOT NULL DEFAULT CURRENT_USER,',
      '  installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP',
      ');',
      'COMMIT;',
    );
  }

  for (const migration of plan) {
    const version = sqlLiteral(migration.version);
    const checksum = sqlLiteral(migration.checksum);
    lines.push(
      `DO $checksum_${safeTag(migration.version)}$`,
      'DECLARE stored_checksum TEXT;',
      'BEGIN',
      `  SELECT checksum INTO stored_checksum FROM ${HISTORY_TABLE} WHERE version = '${version}';`,
      `  IF stored_checksum IS NOT NULL AND stored_checksum <> '${checksum}' THEN`,
      `    RAISE EXCEPTION 'checksum mismatch for migration ${version}: expected ${checksum}, history has %', stored_checksum;`,
      '  END IF;',
      'END',
      `$checksum_${safeTag(migration.version)}$;`,
    );
    if (verifyOnly) {
      lines.push(
        `DO $installed_${safeTag(migration.version)}$`,
        'BEGIN',
        `  IF NOT EXISTS (SELECT 1 FROM ${HISTORY_TABLE} WHERE version = '${version}') THEN`,
        `    RAISE EXCEPTION 'migration history missing: ${version}';`,
        '  END IF;',
        'END',
        `$installed_${safeTag(migration.version)}$;`,
      );
      continue;
    }
    lines.push(
      `SELECT EXISTS (SELECT 1 FROM ${HISTORY_TABLE} WHERE version = '${version}') AS already_applied \\gset`,
      '\\if :already_applied',
      `  \\echo already applied ${migration.version}`,
      '\\else',
      'BEGIN;',
      migration.content.trimEnd(),
      `INSERT INTO ${HISTORY_TABLE}(version, checksum, script_path)`,
      `VALUES ('${version}', '${checksum}', '${sqlLiteral(migration.relativePath)}');`,
      'COMMIT;',
      `  \\echo applied ${migration.version}`,
      '\\endif',
    );
  }
  lines.push(
    `SELECT pg_advisory_unlock(hashtextextended('${sqlLiteral(ADVISORY_LOCK_NAME)}', 0));`,
    '',
  );
  return lines.join('\n');
}

function sqlLiteral(value) {
  return String(value ?? '').replaceAll("'", "''");
}

function safeTag(value) {
  return String(value).replace(/[^A-Za-z0-9_]/g, '_');
}

function printHelp() {
  console.log(`Usage: node .scripts/apply-alert-review-migrations.mjs \\
  (--database-url=URL | --container=NAME --database=NAME [--user=USER]) \\
  [--repo-root=PATH] [--verify-only] [--dry-run]

Applies the ordered alert-review production migrations with a PostgreSQL
transaction advisory lock and SHA-256 history checks. Applied migration files
are immutable: a checksum mismatch aborts before any later migration runs.
Use --verify-only after deployment. Rollback is snapshot/restore or a reviewed
forward repair migration; this runner never performs destructive automatic down migrations.`);
}

function runCli() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  const plan = buildMigrationPlan(options.repoRoot);
  const sql = buildPsqlScript(plan, { verifyOnly: options.verifyOnly });
  if (options.dryRun) {
    console.log(JSON.stringify({
      mode: options.verifyOnly ? 'verify' : 'apply',
      migrations: plan.map(({ version, relativePath, checksum }) => ({ version, relativePath, checksum })),
    }, null, 2));
    return;
  }
  const invocation = buildPsqlInvocation(options);
  const result = spawnSync(invocation.command, invocation.args, {
    input: sql,
    encoding: 'utf8',
    env: invocation.env,
    windowsHide: true,
    maxBuffer: 32 * 1024 * 1024,
  });
  if (result.status !== 0) {
    throw new Error([
      `alert review migration ${options.verifyOnly ? 'verification' : 'apply'} failed in ${invocation.label}`,
      result.stdout,
      result.stderr,
    ].filter(Boolean).join('\n'));
  }
  if (result.stdout) process.stdout.write(result.stdout);
  console.log(`alert review production migrations ${options.verifyOnly ? 'verified' : 'applied'}: ${plan.length}`);
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)) === resolve(process.argv[1])) {
  try {
    runCli();
  } catch (error) {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  }
}
