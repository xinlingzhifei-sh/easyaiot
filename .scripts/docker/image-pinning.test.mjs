import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const dockerDir = dirname(fileURLToPath(import.meta.url));
const scriptsDir = join(dockerDir, '..');
const compose = readFileSync(join(dockerDir, 'docker-compose.yml'), 'utf8');
const zlmDigest = 'sha256:ddc5127d4fab7be622b34148ac8869ca6310298acd33440f1236e771a3c5ceee';

function serviceImage(name) {
  const match = compose.match(new RegExp(`^  ${name}:\\n    image: (.+)$`, 'm'));
  assert.ok(match, `docker-compose.yml 缺少 ${name} 的 image`);
  return match[1].trim();
}

test('middleware compose has no floating latest / branch tags', () => {
  const images = [...compose.matchAll(/^\s+image:\s*(.+)$/gm)].map(m => m[1].trim());
  assert.ok(images.length >= 10);
  for (const image of images) {
    assert.doesNotMatch(image, /:(latest|master)$/, `${image} 未锁定版本`);
  }
});

test('ZLMediaKit is pinned by digest and Node-RED by explicit version', () => {
  assert.equal(serviceImage('ZLMediaKit'), `zlmediakit/zlmediakit@${zlmDigest}`);
  assert.equal(serviceImage('NodeRED'), 'nodered/node-red:5.0.9');
});

test('CentOS 7 Node-RED starter defaults to the compose image', () => {
  const starter = readFileSync(join(dockerDir, 'start_nodered_centos7.sh'), 'utf8');
  assert.match(starter, /^NODERED_IMAGE="\$\{NODERED_IMAGE:-nodered\/node-red:5\.0\.9\}"$/m);
});

test('media-cluster export pulls ZLMediaKit by the same digest', () => {
  const exporter = readFileSync(join(scriptsDir, 'media-cluster', 'export_media_images.sh'), 'utf8');
  assert.match(exporter, new RegExp(`ZLM_DIGEST="\\$\\{ZLM_DIGEST:-${zlmDigest}\\}"`));
  assert.doesNotMatch(exporter, /zlmediakit\/zlmediakit:master"\s*$/m);
  assert.match(exporter, /docker\.m\.daocloud\.io\/zlmediakit\/zlmediakit@\$\{ZLM_DIGEST\}/);
});
