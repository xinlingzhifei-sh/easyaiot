import argparse
import secrets
import subprocess
import sys
from pathlib import Path
from typing import List


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.services.coturn_config_service import build_coturn_config, build_turn_urls  # noqa: E402


DEFAULT_IMAGES = [
    "docker.m.daocloud.io/instrumentisto/coturn:latest",
    "docker.m.daocloud.io/coturn/coturn:4.6.2",
    "dockerproxy.net/coturn/coturn:4.6.2",
    "coturn/coturn:4.6.2",
]


def _run(cmd: List[str], *, timeout: int):
    return subprocess.run(cmd, check=False, capture_output=True, text=True, timeout=timeout)


def _write(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def _render_files(args, credential: str):
    work_dir = Path(args.work_dir)
    config_path = work_dir / "turnserver.conf"
    env_path = work_dir / "turn.env"
    realm = args.realm or args.public_host
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
        f"WEBRTC_TURN_URLS={','.join(build_turn_urls(args.public_host, listen_port=args.listen_port))}",
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
    _write(config_path, config)
    _write(env_path, env)
    return config_path, env_path


def _docker_run_command(args, image: str, config_path: Path) -> List[str]:
    return [
        "docker",
        "run",
        "-d",
        "--name",
        args.container_name,
        "--restart",
        "unless-stopped",
        "--network",
        "host",
        "-v",
        f"{config_path}:/etc/coturn/turnserver.conf:ro",
        image,
        "-c",
        "/etc/coturn/turnserver.conf",
    ]


def _install_container(args, config_path: Path) -> str:
    _run(["docker", "rm", "-f", args.container_name], timeout=20)
    errors = []
    for image in args.image:
        try:
            pull = _run(["docker", "pull", image], timeout=args.pull_timeout)
        except subprocess.TimeoutExpired:
            errors.append(f"{image}: pull timed out")
            continue
        if pull.returncode != 0:
            errors.append(f"{image}: {pull.stderr.strip() or pull.stdout.strip()}")
            continue
        run = _run(_docker_run_command(args, image, config_path), timeout=30)
        if run.returncode == 0:
            return image
        errors.append(f"{image}: {run.stderr.strip() or run.stdout.strip()}")
    raise RuntimeError("Unable to start coturn container: " + "; ".join(errors))


def main() -> int:
    parser = argparse.ArgumentParser(description="Install yFeiEye coturn container and render WebRTC TURN env.")
    parser.add_argument("--public-ip", required=True)
    parser.add_argument("--public-host", required=True)
    parser.add_argument("--realm", default="")
    parser.add_argument("--username", default="yfeieye")
    parser.add_argument("--credential", default="")
    parser.add_argument("--listen-port", type=int, default=3478)
    parser.add_argument("--relay-min-port", type=int, default=49160)
    parser.add_argument("--relay-max-port", type=int, default=49200)
    parser.add_argument("--work-dir", default="/opt/yfeieye-turn")
    parser.add_argument("--container-name", default="yfeieye-coturn")
    parser.add_argument("--image", action="append", default=[])
    parser.add_argument("--pull-timeout", type=int, default=90)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if not args.image:
        args.image = DEFAULT_IMAGES

    credential = args.credential or secrets.token_urlsafe(24)
    config_path, env_path = _render_files(args, credential)
    print(f"Rendered coturn config: {config_path}")
    print(f"Rendered WebRTC TURN env: {env_path}")

    run_cmd = _docker_run_command(args, args.image[0], config_path)
    if args.dry_run:
        print("DRY_RUN: " + " ".join(run_cmd))
        return 0

    try:
        image = _install_container(args, config_path)
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 1
    print(f"Started coturn container {args.container_name} with image {image}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
