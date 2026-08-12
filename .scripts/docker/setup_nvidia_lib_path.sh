#!/usr/bin/env bash
# 非 Docker 部署：在启动 Python 前补全 onnxruntime-gpu 所需的 nvidia 库路径。
# 用法:
#   source /path/to/yfeieye/.scripts/docker/setup_nvidia_lib_path.sh
#   python VIDEO/run.py
#
# systemd 示例 (ExecStartPre):
#   ExecStartPre=/bin/bash -c 'source /opt/yfeieye/.scripts/docker/setup_nvidia_lib_path.sh'

if [ "${_ONNX_NVIDIA_LD_PATH_DONE:-}" = "1" ]; then
  return 0 2>/dev/null || exit 0
fi

PYTHON_BIN="${PYTHON_BIN:-python3}"
NVIDIA_ORT_LD="$("$PYTHON_BIN" -c "
import glob
import os
import site

paths = sorted({
    d
    for root in site.getsitepackages()
    for d in glob.glob(os.path.join(root, 'nvidia', '*', 'lib'))
    if os.path.isdir(d)
})
print(':'.join(paths))
" 2>/dev/null || true)"

if [ -n "$NVIDIA_ORT_LD" ]; then
  if [ -n "${LD_LIBRARY_PATH:-}" ]; then
    export LD_LIBRARY_PATH="${NVIDIA_ORT_LD}:${LD_LIBRARY_PATH}"
  else
    export LD_LIBRARY_PATH="${NVIDIA_ORT_LD}"
  fi
fi
export _ONNX_NVIDIA_LD_PATH_DONE=1
