import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import {
  buildMigrationPlan,
  buildPsqlInvocation,
  buildPsqlScript,
  parseArgs,
} from './apply-alert-review-migrations.mjs';

const root = mkdtempSync(join(tmpdir(), 'alert-review-migrations-'));
const migrations = join(root, 'DEVICE', 'iot-system', 'iot-system-biz', 'src', 'main', 'resources', 'sql', 'migrations');
mkdirSync(migrations, { recursive: true });
writeFileSync(join(migrations, 'V20260701__baseline.sql'), 'CREATE TABLE sample_one(id BIGINT);\n');
writeFileSync(join(migrations, 'V20260702__hardening.sql'), 'ALTER TABLE sample_one ADD COLUMN name TEXT;\n');

const options = parseArgs([
  '--database-url=postgresql://review-user:secret-value@db.internal:5432/yfeieye',
  `--repo-root=${root}`,
]);
assert.equal(options.databaseUrl, 'postgresql://review-user:secret-value@db.internal:5432/yfeieye');
assert.equal(options.repoRoot, root);
assert.equal(options.verifyOnly, false);

const invocation = buildPsqlInvocation(options);
assert.equal(invocation.command, 'psql');
assert.deepEqual(invocation.args, ['-v', 'ON_ERROR_STOP=1']);
assert.equal(invocation.env.PGHOST, 'db.internal');
assert.equal(invocation.env.PGPORT, '5432');
assert.equal(invocation.env.PGDATABASE, 'yfeieye');
assert.equal(invocation.env.PGUSER, 'review-user');
assert.equal(invocation.env.PGPASSWORD, 'secret-value');
assert.equal(invocation.args.join(' ').includes('secret-value'), false);

const plan = buildMigrationPlan(root, [
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260701__baseline.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260702__hardening.sql',
]);
assert.equal(plan.length, 2);
assert.equal(plan[0].version, 'V20260701__baseline.sql');
assert.match(plan[0].checksum, /^[a-f0-9]{64}$/);
assert.notEqual(plan[0].checksum, plan[1].checksum);

const sql = buildPsqlScript(plan, { verifyOnly: false });
assert.match(sql, /pg_advisory_lock/);
assert.match(sql, /system_alert_review_schema_history/);
assert.match(sql, /checksum mismatch/);
assert.match(sql, /\\if :already_applied/);
assert.match(sql, /BEGIN;[\s\S]*CREATE TABLE sample_one/);
assert.match(sql, /INSERT INTO system_alert_review_schema_history/);
assert.match(sql, /COMMIT;/);
assert.match(sql, /pg_advisory_unlock/);

const verifySql = buildPsqlScript(plan, { verifyOnly: true });
assert.doesNotMatch(verifySql, /CREATE TABLE sample_one/);
assert.match(verifySql, /migration history missing/);

assert.throws(
  () => parseArgs(['--database-url=postgresql://localhost/yfeieye', '--container=postgres']),
  /exactly one/,
);

console.log('alert review production migration runner tests OK');
