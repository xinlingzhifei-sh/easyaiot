import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const root = resolve(scriptDirectory, '../..');
const middlewareCompose = readFileSync(resolve(scriptDirectory, 'docker-compose.yml'), 'utf8');
const deviceCompose = readFileSync(resolve(root, 'DEVICE/docker-compose.yml'), 'utf8');
const videoCompose = readFileSync(resolve(root, 'VIDEO/docker-compose.yaml'), 'utf8');
const aiCompose = readFileSync(resolve(root, 'AI/docker-compose.yaml'), 'utf8');
const alertVerifier = readFileSync(resolve(scriptDirectory, 'verify_alert_mqtt_chain.sh'), 'utf8');
const dvrVerifier = readFileSync(resolve(scriptDirectory, 'verify_dvr_nfs_chain.sh'), 'utf8');

assert.match(
  middlewareCompose,
  /\$\{EASYAIOT_MEDIA_ROOT[^}]*\}:\/data:rw/,
  'SRS must write the configured media root',
);
assert.match(
  videoCompose,
  /\$\{EASYAIOT_MEDIA_ROOT[^}]*\}:\/mnt\/easyaiot-media:rw/,
  'VIDEO must mount the canonical media root',
);
assert.match(
  videoCompose,
  /\$\{EASYAIOT_MEDIA_ROOT[^}]*\}\/yfeieye-record-exports:\/data\/yfeieye-record-exports:rw/,
  'VIDEO record exports must stay under the canonical media root',
);
assert.match(
  deviceCompose,
  /\$\{EASYAIOT_MEDIA_ROOT[^}]*\}:\/mnt\/easyaiot-media:rw/,
  'iot-sink must mount the same canonical media root',
);
assert.match(deviceCompose, /YFEIEYE_SRS_HOOK_TOKEN=\$\{YFEIEYE_SRS_HOOK_TOKEN:\?/);
assert.match(dvrVerifier, /X-YFeiEye-Hook-Token/);
assert.match(dvrVerifier, /YFEIEYE_SRS_HOOK_TOKEN/);
assert.match(alertVerifier, /EASYAIOT_MEDIA_ROOT[^\n]*alert_images/);
assert.match(
  aiCompose,
  /SAM_MODEL_PATH=\$\{SAM_MODEL_PATH:-\/app\/model\/sam3\/sam3\.1_multiplex\.pt\}/,
  'SAM3 model weights must default to the persistent /app/model mount',
);

console.log('Media chain contract passed');
