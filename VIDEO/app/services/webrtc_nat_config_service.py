"""
WebRTC NAT playback configuration.

This service intentionally only exposes deploy-time configuration to callers.
The actual ICE negotiation remains inside the browser and media server.
"""
import os
from typing import Any, Dict, List


def _split_csv(value: str) -> List[str]:
    return [part.strip() for part in (value or "").split(",") if part.strip()]


def _env_bool(name: str, default: bool = False) -> bool:
    value = (os.getenv(name) or "").strip().lower()
    if not value:
        return default
    return value in {"1", "true", "yes", "on"}


def build_webrtc_nat_config() -> Dict[str, Any]:
    stun_urls = _split_csv(os.getenv("WEBRTC_STUN_URLS", ""))
    turn_urls = _split_csv(os.getenv("WEBRTC_TURN_URLS", ""))
    turn_username = (os.getenv("WEBRTC_TURN_USERNAME") or "").strip()
    turn_credential = (os.getenv("WEBRTC_TURN_CREDENTIAL") or "").strip()
    turn_ready = bool(turn_urls and turn_username and turn_credential)
    ice_servers: List[Dict[str, Any]] = []

    if stun_urls:
        ice_servers.append({"urls": stun_urls})

    if turn_ready:
        turn_server: Dict[str, Any] = {"urls": turn_urls}
        turn_server["username"] = turn_username
        turn_server["credential"] = turn_credential
        ice_servers.append(turn_server)

    require_secure_context = _env_bool("WEBRTC_FORCE_SECURE")
    turn_status = "ready" if turn_ready else ("turn_incomplete" if turn_urls else "not_configured")

    return {
        "iceServers": ice_servers,
        "candidate_ip": (os.getenv("WEBRTC_PUBLIC_IP") or os.getenv("ZLM_RTC_EXTERN_IP") or "").strip() or None,
        "public_host": (os.getenv("WEBRTC_PUBLIC_HOST") or "").strip() or None,
        "require_secure_context": require_secure_context,
        "prefer_wss": require_secure_context or _env_bool("WEBRTC_PREFER_WSS"),
        "turn_ready": turn_ready,
        "turn_status": turn_status,
    }
