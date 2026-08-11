import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const composeSource = readFileSync(resolve(scriptDirectory, 'docker-compose.yml'), 'utf8');

const milvusService = composeSource.match(/\n  Milvus:\n([\s\S]*?)\n  [A-Za-z][^\n]*:\n/)?.[1];

assert.ok(milvusService, 'docker-compose.yml must define the Milvus service');
assert.match(milvusService, /image: \$\{MILVUS_IMAGE:-milvusdb\/milvus:v2\.6\.3\}/);
assert.doesNotMatch(milvusService, /milvusdb\/milvus:v2\.6\.0/);

console.log('Milvus Compose contract passed');
