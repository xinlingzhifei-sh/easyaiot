import os
import subprocess
from typing import Any, Dict


class StreamForwardExecutor:
    def __init__(self):
        self._processes: Dict[str, subprocess.Popen] = {}

    def deploy(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        device_id = str(payload.get("deviceId") or "").strip()
        rtsp_url = str(payload.get("rtspUrl") or "").strip()
        rtmp_push_url = str(payload.get("rtmpPushUrl") or "").strip()
        transport = str(payload.get("transport") or "tcp").strip().lower()
        log_dir = str(payload.get("logDir") or os.path.join(os.getcwd(), "logs", "edge", device_id))

        if not device_id:
            raise ValueError("deviceId is required")
        if not rtsp_url.startswith("rtsp://"):
            raise ValueError("rtspUrl must start with rtsp://")
        if not rtmp_push_url.startswith("rtmp://"):
            raise ValueError("rtmpPushUrl must start with rtmp://")
        if transport not in ("tcp", "udp"):
            transport = "tcp"

        existing = self._processes.get(device_id)
        if existing and existing.poll() is None:
            return {
                "pid": existing.pid,
                "deviceId": device_id,
                "alreadyRunning": True,
                "rtmpPushUrl": rtmp_push_url,
            }

        os.makedirs(log_dir, exist_ok=True)
        log_path = os.path.join(log_dir, "stream-forward.log")
        cmd = [
            "ffmpeg",
            "-hide_banner",
            "-loglevel",
            "warning",
            "-rtsp_transport",
            transport,
            "-i",
            rtsp_url,
            "-an",
            "-c:v",
            "copy",
            "-f",
            "flv",
            "-flvflags",
            "no_duration_filesize",
            rtmp_push_url,
        ]
        with open(log_path, "a", encoding="utf-8") as log_file:
            process = subprocess.Popen(
                cmd,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                stdin=subprocess.DEVNULL,
            )
        self._processes[device_id] = process
        return {
            "pid": process.pid,
            "deviceId": device_id,
            "logPath": log_path,
            "rtmpPushUrl": rtmp_push_url,
        }

    def stop(self, payload: Dict[str, Any]) -> Dict[str, Any]:
        device_id = str(payload.get("deviceId") or "").strip()
        process = self._processes.get(device_id)
        if not process:
            return {"deviceId": device_id, "stopped": False}
        if process.poll() is None:
            process.terminate()
        self._processes.pop(device_id, None)
        return {"deviceId": device_id, "stopped": True}
