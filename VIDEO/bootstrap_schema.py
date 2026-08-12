"""Create the current VIDEO baseline schema before versioned migrations.

SQLAlchemy create_all is intentionally used only as a fresh-install baseline:
it creates absent tables but never alters existing production tables. All
evolution of an existing table is handled by apply_migrations.py.
"""
from __future__ import annotations

import argparse
import json
import os

from flask import Flask
from schema_lock import sqlalchemy_schema_connect_options


def bootstrap_schema(database_url: str) -> None:
    normalized_url = str(database_url or '').strip()
    if not normalized_url:
        raise ValueError('DATABASE_URL is required for VIDEO schema bootstrap')
    normalized_url = normalized_url.replace('postgres://', 'postgresql://', 1)

    from models import db

    app = Flask('yfeieye-video-schema-bootstrap')
    app.config['SQLALCHEMY_DATABASE_URI'] = normalized_url
    app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
    app.config['SQLALCHEMY_ENGINE_OPTIONS'] = {
        'pool_pre_ping': True,
        'connect_args': {
            'connect_timeout': 10,
            'options': sqlalchemy_schema_connect_options(),
        },
    }
    db.init_app(app)
    with app.app_context():
        # Importing models registers the complete metadata before create_all.
        import models  # noqa: F401
        db.create_all()
        db.session.remove()


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description='Create absent VIDEO baseline tables')
    parser.add_argument('--database-url', default=os.environ.get('DATABASE_URL', ''))
    return parser.parse_args(argv)


def main(argv=None) -> int:
    options = parse_args(argv)
    bootstrap_schema(options.database_url)
    print(json.dumps({'status': 'baseline_ready'}, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
