import * as assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { isProtectedAlertImageUrl } from '../src/utils/alertMinioImage';

const page = 'https://qa.example/yfeieye/alert';
const endpoint = '/yfeieye/dev-api/video/alert/image';
assert.equal(isProtectedAlertImageUrl(`${endpoint}?path=test.jpg`, endpoint, page), true);
assert.equal(isProtectedAlertImageUrl('/dev-api/video/alert/image?path=test.jpg', '/dev-api/video/alert/image', page), true);
for (const source of [
  `https://media.example${endpoint}`,
  `//media.example${endpoint}`,
  `https://user@qa.example${endpoint}`,
  `${endpoint}/other`,
  `${endpoint}-export`,
  `/dev-api/video/alert/image`,
  '/logo.png',
]) {
  assert.equal(isProtectedAlertImageUrl(source, endpoint, page), false, source);
}
assert.equal(isProtectedAlertImageUrl(`https://media.example${endpoint}`, `https://media.example${endpoint}`, page), false);

const component = readFileSync(new URL('../src/views/alert/components/AlertImage.vue', import.meta.url), 'utf8');
assert.ok(component.includes("redirect: 'error'"));
assert.ok(component.includes('URL.revokeObjectURL(objectUrl)'));
assert.ok(component.includes('if (controller.signal.aborted) return;'));
for (const location of ['AlertCards', 'ImageModal']) {
  const view = readFileSync(new URL(`../src/views/alert/components/${location}/index.vue`, import.meta.url), 'utf8');
  assert.ok(view.includes('<AlertImage'), location);
}
