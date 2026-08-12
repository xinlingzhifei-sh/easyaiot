import argparse
import secrets
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.services.coturn_config_service import build_coturn_config, build_turn_urls  # noqa: E402


def _write(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Render coturn config and yFeiEye WebRTC TURN env.")
    parser.add_argument("--public-ip", required=True)
    parser.add_argument("--public-host", required=True)
    parser.add_argument("--realm", default="")
    parser.add_argument("--username", default="yfeieye")
    parser.add_argument("--credential", default="")
    parser.add_argument("--listen-port", type=int, default=3478)
    parser.add_argument("--relay-min-port", type=int, default=49160)
    parser.add_argument("--relay-max-port", type=int, default=49200)
    parser.add_argument("--config-out", required=True)
    parser.add_argument("--env-out", required=True)
    args = parser.parse_args()

    credential = args.credential or secrets.token_urlsafe(24)
    realm = args.realm or args.public_host
    turn_urls = build_turn_urls(args.public_host, listen_port=args.listen_port)
    config = build_coturn_config(
        public_ip=args.public_ip,
        realm=realm,
        username=args.username,
        credential=credential,
        listen_port=args.listen_port,
        relay_min_port=args.relay_min_port,
        relay_max_port=args.relay_max_port,
    )
    env = "\n".join([
        f"WEBRTC_TURN_URLS={','.join(turn_urls)}",
        f"WEBRTC_TURN_USERNAME={args.username}",
        f"WEBRTC_TURN_CREDENTIAL={credential}",
        f"TURN_PUBLIC_IP={args.public_ip}",
        f"TURN_PUBLIC_HOST={args.public_host}",
        f"TURN_REALM={realm}",
        f"TURN_LISTEN_PORT={args.listen_port}",
        f"TURN_RELAY_MIN_PORT={args.relay_min_port}",
        f"TURN_RELAY_MAX_PORT={args.relay_max_port}",
        "",
    ])
    _write(Path(args.config_out), config)
    _write(Path(args.env_out), env)
    print(f"Rendered coturn config: {args.config_out}")
    print(f"Rendered WebRTC TURN env: {args.env_out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
