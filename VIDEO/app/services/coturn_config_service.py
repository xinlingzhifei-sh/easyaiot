from typing import List


def _require_text(name: str, value: str) -> str:
    clean = (value or "").strip()
    if not clean:
        raise ValueError(f"{name} is required")
    return clean


def build_turn_urls(public_host: str, *, listen_port: int = 3478) -> List[str]:
    host = _require_text("public_host", public_host)
    port = int(listen_port)
    return [
        f"turn:{host}:{port}?transport=udp",
        f"turn:{host}:{port}?transport=tcp",
    ]


def build_coturn_config(
    *,
    public_ip: str,
    realm: str,
    username: str,
    credential: str,
    listen_port: int = 3478,
    relay_min_port: int = 49160,
    relay_max_port: int = 49200,
) -> str:
    clean_public_ip = _require_text("public_ip", public_ip)
    clean_realm = _require_text("realm", realm)
    clean_username = _require_text("username", username)
    clean_credential = _require_text("credential", credential)
    return "\n".join([
        f"listening-port={int(listen_port)}",
        "listening-ip=0.0.0.0",
        f"external-ip={clean_public_ip}",
        f"realm={clean_realm}",
        f"server-name={clean_realm}",
        "fingerprint",
        "lt-cred-mech",
        f"user={clean_username}:{clean_credential}",
        f"min-port={int(relay_min_port)}",
        f"max-port={int(relay_max_port)}",
        "no-cli",
        "no-tlsv1",
        "no-tlsv1_1",
        "no-multicast-peers",
        "no-loopback-peers",
        "log-file=stdout",
        "simple-log",
        "",
    ])
