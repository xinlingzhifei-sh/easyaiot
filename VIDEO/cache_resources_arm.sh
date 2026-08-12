#!/bin/bash
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YFEIEYE_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
exec "${YFEIEYE_ROOT}/.scripts/docker/cache_python_resources_arm.sh" VIDEO "$@"
