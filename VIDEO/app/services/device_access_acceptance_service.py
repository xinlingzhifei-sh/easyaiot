from datetime import datetime
from typing import Any, Dict, List, Optional


ACCEPTANCE_SCENARIOS = [
    {
        "id": "gb28181_public",
        "title": "GB28181 public registration, playback, and PTZ",
        "required_probes": ["registration", "play", "ptz", "access_state"],
        "required_evidence": ["device_id", "public_network", "ptz_command"],
    },
    {
        "id": "rtsp_same_lan",
        "title": "Same-network RTSP pull and playback",
        "required_probes": ["source_validation", "play", "access_state"],
        "required_evidence": ["device_id", "rtsp_source", "lan_network"],
    },
    {
        "id": "rtsp_edge_outbound",
        "title": "Private RTSP through Edge Agent outbound push",
        "required_probes": ["edge_command", "publish_hook", "play", "access_state"],
        "required_evidence": ["device_id", "edge_node_id", "command_id"],
    },
    {
        "id": "rtmp_public_push",
        "title": "Public RTMP device push with signed ingest",
        "required_probes": ["signed_url_issue", "publish_hook", "access_state"],
        "required_evidence": ["device_id", "tenant_id", "token_version"],
    },
    {
        "id": "http_flv_public_playback",
        "title": "HTTP-FLV public playback",
        "required_probes": ["public_flv_playback", "access_state"],
        "required_evidence": ["device_id", "public_network", "play_url"],
    },
    {
        "id": "webrtc_public_playback",
        "title": "WebRTC public playback across NAT",
        "required_probes": ["https_page", "wss_signaling", "access_state"],
        "required_evidence": [
            "device_id",
            "network_name",
            "https_origin_verified",
            "wss_signaling_verified",
            "ice_connection_state",
            "selected_candidate_pair",
            "public_candidate_observed",
            "turn_relay_verified",
            "cross_carrier_verified",
            "mobile_hotspot_verified",
            "weak_network_profile",
            "weak_network_result",
        ],
    },
]


def _configured_scenario(config: Dict[str, Any], scenario_id: str) -> Dict[str, Any]:
    scenarios = config.get("scenarios") if isinstance(config, dict) else None
    if isinstance(scenarios, dict):
        value = scenarios.get(scenario_id)
        return value if isinstance(value, dict) else {}
    return {}


def _missing_keys(source: Dict[str, Any], keys: List[str]) -> List[str]:
    return [key for key in keys if source.get(key) in (None, "")]


def _missing_probes(probes: Dict[str, Any], keys: List[str]) -> List[str]:
    missing = []
    for key in keys:
        probe = probes.get(key)
        if not isinstance(probe, dict):
            missing.append(key)
        elif not str(probe.get("url") or "").strip():
            missing.append(f"{key}.url")
    return missing


def _run_probe(name: str, probe: Dict[str, Any], http_client: Any) -> Dict[str, Any]:
    method = str(probe.get("method") or "GET").upper()
    url = str(probe.get("url") or "").strip()
    try:
        response = http_client.request(
            method,
            url,
            headers=probe.get("headers") if isinstance(probe.get("headers"), dict) else None,
            json=probe.get("json") if "json" in probe else None,
            timeout=float(probe.get("timeout_seconds") or 10),
        )
        http_status = int(getattr(response, "status_code", 0) or 0)
        passed = 200 <= http_status < 400
        return {
            "name": name,
            "status": "passed" if passed else "failed",
            "method": method,
            "url": url,
            "http_status": http_status,
        }
    except Exception as e:
        return {
            "name": name,
            "status": "failed",
            "method": method,
            "url": url,
            "error": str(e),
        }


def run_device_access_acceptance(
    config: Dict[str, Any],
    *,
    http_client: Optional[Any] = None,
) -> Dict[str, Any]:
    now = datetime.utcnow().isoformat()
    results = []
    for scenario in ACCEPTANCE_SCENARIOS:
        configured = _configured_scenario(config, scenario["id"])
        probes = configured.get("probes") if isinstance(configured.get("probes"), dict) else {}
        evidence = configured.get("evidence") if isinstance(configured.get("evidence"), dict) else {}
        missing_probes = _missing_probes(probes, scenario["required_probes"])
        missing_evidence = _missing_keys(evidence, scenario["required_evidence"])
        probe_results = []
        if missing_probes or missing_evidence:
            status = "blocked"
        elif http_client is None:
            status = "blocked"
            missing_probes = ["http_client"]
        else:
            probe_results = [
                _run_probe(name, probes[name], http_client)
                for name in scenario["required_probes"]
            ]
            status = "failed" if any(probe["status"] == "failed" for probe in probe_results) else "passed"
        results.append({
            "id": scenario["id"],
            "title": scenario["title"],
            "status": status,
            "missing_probes": missing_probes,
            "missing_evidence": missing_evidence,
            "probes": probe_results,
            "evidence": evidence,
        })

    status = (
        "failed" if any(item["status"] == "failed" for item in results)
        else "blocked" if any(item["status"] == "blocked" for item in results)
        else "passed"
    )
    return {
        "status": status,
        "generated_at": now,
        "environment": config.get("environment") if isinstance(config, dict) else None,
        "scenarios": results,
    }
