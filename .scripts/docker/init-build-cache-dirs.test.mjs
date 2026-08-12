import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));

test('build cache scripts do not call undefined easyaiot compatibility symbols', () => {
  const sources = [
    'init-build-cache-dirs.sh',
    'cache_python_resources.sh',
  ].map((file) => readFileSync(join(scriptDir, file), 'utf8'));

  for (const source of sources) {
    assert.doesNotMatch(
      source,
      /\beasyaiot_(?:build_cache_base|chown_build_cache|build_cache_dirs)\b/,
    );
    assert.doesNotMatch(source, /\binit_easyaiot_build_cache_dirs\b/);
    assert.doesNotMatch(source, /\bEASYAIOT_PYTHON_CACHE_MODULES\b/);
  }
});
