import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const ensureScript = readFileSync(
  fileURLToPath(new URL('../../.scripts/node/ensure_platform_agent.sh', import.meta.url)),
  'utf8',
);
const installScript = readFileSync(
  fileURLToPath(new URL('../../NODE/install.sh', import.meta.url)),
  'utf8',
);

assert.match(
  ensureScript,
  /install_agent_runtime_if_needed\(\)/,
  'Platform agent ensure script should install the agent runtime before starting run_agent.py directly.',
);
assert.match(
  ensureScript,
  /bash "\$\{SOURCE_DIR\}\/install\.sh" install/,
  'Platform agent ensure script should use NODE/install.sh when /opt/easyaiot/node-agent is missing.',
);
assert.match(
  ensureScript,
  /bash "\$\{SOURCE_DIR\}\/install\.sh" install\s+>&2/,
  'Platform agent ensure script should keep installer logs out of the stdout path returned by command substitution.',
);
assert.match(
  ensureScript,
  /work_dir="\$INSTALL_DIR"/,
  'After installing the platform agent runtime, ensure script should switch to /opt/easyaiot/node-agent.',
);

assert.match(
  installScript,
  /setup_agent_online_site_packages\(\)/,
  'Node agent installer should have an online pip fallback when pip-wheels are absent.',
);
assert.match(
  installScript,
  /sync_agent_sources\(\)[\s\S]*sudo mkdir -p "\$INSTALL_DIR"[\s\S]*readlink -f "\$INSTALL_DIR"/,
  'Node agent installer should create the install directory before resolving it with readlink -f.',
);
assert.match(
  installScript,
  /EASYAIOT_AGENT_ALLOW_ONLINE_PIP/,
  'Online pip fallback should be controlled by an explicit environment variable.',
);
assert.match(
  installScript,
  /--target="\$SITE_PKG"[\s\S]*-r requirements\.txt/,
  'Online pip fallback should install dependencies into the agent site-packages directory.',
);
