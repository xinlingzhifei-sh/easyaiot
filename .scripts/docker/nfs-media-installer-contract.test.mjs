import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const installerSource = readFileSync(resolve(scriptDirectory, 'install_middleware_linux.sh'), 'utf8');
const nfsStackSource = readFileSync(
  resolve(scriptDirectory, '../media-cluster/nfs/ensure_nfs_media_stack.sh'),
  'utf8',
);
const nfsHealthSource = readFileSync(
  resolve(scriptDirectory, '../media-cluster/nfs/check_nfs_health.sh'),
  'utf8',
);

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

const localDirectGuard = nfsStackSource.match(
  /if \[ "\$install_server" -eq 1 \] && \[ "\$NFS_EXPORT" = "\$MOUNT_ROOT" \]; then([\s\S]*?)\n    fi/,
);

assert.ok(localDirectGuard, 'local NFS export must guard against mounting a directory onto itself');
assert.match(localDirectGuard[1], /NFS_LOCAL_DIRECT/);
assert.match(localDirectGuard[1], /_ensure_local_media_permissions/);
assert.match(localDirectGuard[1], /echo ENSURE_NFS_MEDIA_STACK_OK/);
assert.match(localDirectGuard[1], /return 0/);
assert.ok(
  nfsStackSource.indexOf(localDirectGuard[0]) < nfsStackSource.indexOf('install_nfs_client.sh'),
  'local same-path guard must run before the NFS client mount',
);
assert.doesNotMatch(
  nfsHealthSource,
  /ss -ltn 2>\/dev\/null \| grep -q ':2049'/,
  'NFS port detection must not use grep -q under pipefail because ss can exit on SIGPIPE',
);
assert.match(nfsHealthSource, /ss -ltn 2>\/dev\/null \| grep ':2049' >\/dev\/null/);

console.log('NFS media installer contract passed');
