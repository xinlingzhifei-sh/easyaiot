import assert from 'node:assert/strict';
import test from 'node:test';

import { buildModelDownloadUrl } from './downloadUrl';

test('model management downloads through the backend endpoint when a MinIO path exists', () => {
  const record = {
    id: 42,
    model_path: '/api/v1/buckets/models/objects/download?prefix=models/demo.pt',
  };

  assert.equal(buildModelDownloadUrl(record, '/dev-api'), '/dev-api/model/42/download');
});
