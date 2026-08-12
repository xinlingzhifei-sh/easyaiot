import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const composeSource = readFileSync(resolve(scriptDirectory, 'docker-compose.yml'), 'utf8');
const installerSource = readFileSync(resolve(scriptDirectory, 'install_middleware_linux.sh'), 'utf8');

assert.match(composeSource, /0\.0\.0\.0:8001:8001\/tcp/);
assert.match(composeSource, /0\.0\.0\.0:8001:8001\/udp/);

const rtcSection = installerSource.match(/\[rtc\]([\s\S]*?)\n\[/)?.[1];
assert.ok(rtcSection, 'install_middleware_linux.sh must contain an [rtc] section');
assert.match(rtcSection, /^externIP=$/m);
assert.match(rtcSection, /^port=8001$/m);
assert.match(rtcSection, /^tcpPort=8001$/m);
assert.equal(
  installerSource.match(/normalize_zlmediakit_rtc_config "\$zlm_config_file" "\$zlm_rtc_extern_ip"/g)?.length,
  2,
);

console.log('ZLMediaKit WebRTC port contract passed');
