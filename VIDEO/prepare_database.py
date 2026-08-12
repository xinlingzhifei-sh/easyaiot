"""Prepare the VIDEO baseline and immutable migrations under one bounded lock."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

from apply_migrations import apply_migrations, build_migration_plan
from bootstrap_schema import bootstrap_schema
from schema_lock import production_schema_lock


def prepare_database(database_url: str, repo_root=None) -> list[dict]:
    root = Path(repo_root or Path(__file__).resolve().parent)
    plan = build_migration_plan(root)
    with production_schema_lock(database_url):
        bootstrap_schema(database_url)
        return apply_migrations(database_url, plan, acquire_lock=False)


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description='Prepare VIDEO schema under one bounded advisory lock')
    parser.add_argument('--database-url', default=os.environ.get('DATABASE_URL', ''))
    parser.add_argument('--repo-root', default=str(Path(__file__).resolve().parent))
    return parser.parse_args(argv)


def main(argv=None) -> int:
    options = parse_args(argv)
    results = prepare_database(options.database_url, options.repo_root)
    print(json.dumps({
        'status': 'schema_ready',
        'migrationCount': len(results),
        'results': results,
    }, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
