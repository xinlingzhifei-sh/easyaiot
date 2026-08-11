import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const installerSource = readFileSync(resolve(scriptDirectory, 'install_middleware_linux.sh'), 'utf8');

const nfsHelper = installerSource.match(
  /ensure_nfs_media_before_srs\(\) \{([\s\S]*?)\n\}/,
)?.[1];

assert.ok(nfsHelper, 'install_middleware_linux.sh must define ensure_nfs_media_before_srs');
assert.match(nfsHelper, /local nfs_script=/);
assert.match(nfsHelper, /local resolve_helper=/);
assert.match(nfsHelper, /local mount_root/);
assert.match(nfsHelper, /bash "\$nfs_script"/);
assert.match(
  installerSource,
  /ensure_host_data_directory_before_srs\(\) \{\s*ensure_nfs_media_before_srs\s*\}/,
);

console.log('NFS media installer contract passed');
