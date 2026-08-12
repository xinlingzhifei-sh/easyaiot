import assert from 'node:assert/strict';
import test from 'node:test';

import { buildModelDownloadUrl } from './modelDownloadUrl';

test('model download URL always uses the backend model endpoint', () => {
  assert.equal(buildModelDownloadUrl(42, '/dev-api'), '/dev-api/model/42/download');
});

test('model download URL normalizes a trailing API base slash', () => {
  assert.equal(buildModelDownloadUrl(42, '/dev-api/'), '/dev-api/model/42/download');
});
