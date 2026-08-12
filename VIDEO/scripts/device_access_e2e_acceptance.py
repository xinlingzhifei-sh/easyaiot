import argparse
import json
import sys
from pathlib import Path


VIDEO_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(VIDEO_ROOT))

from app.services.device_access_acceptance_service import run_device_access_acceptance


def _load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def _requests_client():
    import requests

    return requests


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Run yFeiEye device-access E2E acceptance probes.")
    parser.add_argument("--config", required=True, help="Path to acceptance JSON config.")
    parser.add_argument("--output", required=True, help="Path to write the acceptance report JSON.")
    parser.add_argument(
        "--plan-only",
        action="store_true",
        help="Do not execute HTTP probes; generate a blocked readiness report from the configured matrix.",
    )
    args = parser.parse_args(argv)

    config = _load_json(Path(args.config))
    report = run_device_access_acceptance(
        config,
        http_client=None if args.plan_only else _requests_client(),
    )
    _write_json(Path(args.output), report)

    if report["status"] == "passed":
        return 0
    if report["status"] == "blocked":
        return 2
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
