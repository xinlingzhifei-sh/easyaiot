import importlib
import sys
import types
import unittest
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


class FakeQuery:
    def __init__(self, model):
        self.model = model
        self.filters = {}

    def filter_by(self, **kwargs):
        query = FakeQuery(self.model)
        query.filters = kwargs
        return query

    def one_or_none(self):
        matches = []
        for row in self.model.rows:
            if all(getattr(row, key, None) == value for key, value in self.filters.items()):
                matches.append(row)
        return matches[0] if matches else None


class FakeSecret:
    rows = []
    query = None

    def __init__(self, **kwargs):
        self.id = None
        for key, value in kwargs.items():
            setattr(self, key, value)


class FakeAudit:
    rows = []

    def __init__(self, **kwargs):
        self.id = None
        for key, value in kwargs.items():
            setattr(self, key, value)


FakeSecret.query = FakeQuery(FakeSecret)


class FakeSession:
    def __init__(self):
        self.commits = 0

    def add(self, row):
        rows = getattr(type(row), "rows", None)
        if rows is not None and row not in rows:
            rows.append(row)

    def commit(self):
        self.commits += 1


db = types.SimpleNamespace(session=FakeSession())
sys.modules["models"] = types.SimpleNamespace(
    Device=object,
    DeviceAccessStateCurrent=object,
    DeviceAccessStateEvent=object,
    DeviceRtmpIngestSecret=FakeSecret,
    DeviceRtmpPublishAudit=FakeAudit,
    StreamForwardTask=object,
    db=db,
)
sys.modules["app.services.device_access_state_service"] = types.SimpleNamespace(
    record_media_stream_offline=Mock(),
    record_device_access_event=Mock(),
    record_srs_publish_online=Mock(),
)
sys.modules.pop("app.services.rtmp_ingest_auth_service", None)

rtmp_ingest_auth_service = importlib.import_module("app.services.rtmp_ingest_auth_service")


class RtmpIngestAuthServiceTest(unittest.TestCase):

    def setUp(self):
        FakeSecret.rows = []
        FakeAudit.rows = []
        db.session.commits = 0

    def test_issue_signed_url_and_accept_valid_publish_hook(self):
        issued = rtmp_ingest_auth_service.issue_rtmp_ingest_url(
            "cam-001",
            tenant_id="tenant-a",
            ttl_seconds=60,
            base_url="rtmp://media.example.com/live",
            now=1_700_000_000,
        )

        parsed = urlparse(issued["push_url"])
        query = parse_qs(parsed.query)
        self.assertEqual("rtmp", parsed.scheme)
        self.assertEqual("/live/cam-001", parsed.path)
        self.assertEqual(["tenant-a"], query["tenant"])
        self.assertEqual(["1700000060"], query["exp"])
        self.assertEqual(["1"], query["ver"])
        self.assertEqual(1, len(query["sig"]))
        self.assertEqual(64, len(query["sig"][0]))
        self.assertNotIn("secret", issued)

        hook_payload = {
            "app": "live",
            "stream": "cam-001",
            "node_id": 7,
            "param": f"?tenant={query['tenant'][0]}&exp={query['exp'][0]}&ver={query['ver'][0]}&sig={query['sig'][0]}",
        }

        with patch("app.services.rtmp_ingest_auth_service.record_device_access_event") as record_state:
            result = rtmp_ingest_auth_service.verify_rtmp_publish_hook(
                hook_payload,
                remote_ip="203.0.113.9",
                now=1_700_000_030,
            )

        self.assertTrue(result["accepted"])
        self.assertEqual("rtmp_publish_accepted", result["reason_code"])
        self.assertEqual(1, len(FakeAudit.rows))
        audit = FakeAudit.rows[0]
        self.assertTrue(audit.accepted)
        self.assertEqual("cam-001", audit.device_id)
        self.assertEqual("tenant-a", audit.tenant_id)
        self.assertEqual("203.0.113.9", audit.remote_ip)
        self.assertEqual(7, audit.node_id)
        record_state.assert_called_once_with(
            device_id="cam-001",
            protocol="rtmp",
            state="stream_online",
            reason_code="rtmp_publish_accepted",
            reason_message="Signed RTMP publish accepted",
            source_event="srs.on_publish",
            stream_id="live/cam-001",
            node_id=7,
            tenant_id="tenant-a",
            commit=False,
        )

    def test_publish_hook_audits_params_parsed_from_stream_url(self):
        issued = rtmp_ingest_auth_service.issue_rtmp_ingest_url(
            "cam-001",
            tenant_id="tenant-a",
            ttl_seconds=60,
            base_url="rtmp://media.example.com/live",
            now=1_700_000_000,
        )
        query = urlparse(issued["push_url"]).query

        result = rtmp_ingest_auth_service.verify_rtmp_publish_hook(
            {
                "app": "live",
                "stream": "cam-001",
                "stream_url": issued["push_url"],
            },
            remote_ip="203.0.113.9",
            now=1_700_000_030,
        )

        self.assertTrue(result["accepted"])
        self.assertEqual(query, FakeAudit.rows[0].raw_params)

    def test_publish_hook_uses_requested_source_event(self):
        issued = rtmp_ingest_auth_service.issue_rtmp_ingest_url(
            "cam-001",
            tenant_id="tenant-a",
            ttl_seconds=60,
            base_url="rtmp://media.example.com/live",
            now=1_700_000_000,
        )
        query = parse_qs(urlparse(issued["push_url"]).query)

        with patch("app.services.rtmp_ingest_auth_service.record_device_access_event") as record_state:
            result = rtmp_ingest_auth_service.verify_rtmp_publish_hook(
                {
                    "app": "live",
                    "stream": "cam-001",
                    "param": (
                        f"?tenant=tenant-a&exp={query['exp'][0]}"
                        f"&ver={query['ver'][0]}&sig={query['sig'][0]}"
                    ),
                },
                source_event="zlm.on_publish",
                now=1_700_000_030,
            )

        self.assertTrue(result["accepted"])
        self.assertEqual("zlm.on_publish", record_state.call_args.kwargs["source_event"])

    def test_publish_hook_allows_non_ingest_media_apps_without_signature(self):
        with patch("app.services.rtmp_ingest_auth_service.record_device_access_event") as record_state:
            result = rtmp_ingest_auth_service.verify_rtmp_publish_hook(
                {
                    "app": "rtp",
                    "stream": "44010200493432381460_34020000001320000001",
                    "node_id": 9,
                },
                remote_ip="192.0.2.30",
            )

        self.assertTrue(result["accepted"])
        self.assertEqual("rtmp_non_ingest_app_allowed", result["reason_code"])
        self.assertEqual("rtp/44010200493432381460_34020000001320000001", result["stream_id"])
        self.assertEqual(1, len(FakeAudit.rows))
        audit = FakeAudit.rows[0]
        self.assertTrue(audit.accepted)
        self.assertEqual("44010200493432381460_34020000001320000001", audit.device_id)
        self.assertIsNone(audit.tenant_id)
        self.assertEqual("rtp", audit.app)
        self.assertEqual(9, audit.node_id)
        record_state.assert_not_called()

    def test_rejects_missing_expired_and_wrong_tenant_publish_hooks(self):
        issued = rtmp_ingest_auth_service.issue_rtmp_ingest_url(
            "cam-001",
            tenant_id="tenant-a",
            ttl_seconds=60,
            base_url="rtmp://media.example.com/live",
            now=1_700_000_000,
        )
        parsed = urlparse(issued["push_url"])
        query = parse_qs(parsed.query)

        cases = [
            (
                "missing signature",
                "?tenant=tenant-a&exp=1700000060&ver=1",
                "rtmp_missing_sig",
            ),
            (
                "expired signature",
                f"?tenant=tenant-a&exp={query['exp'][0]}&ver=1&sig={query['sig'][0]}",
                "rtmp_expired",
            ),
            (
                "wrong tenant",
                f"?tenant=tenant-b&exp=1700000060&ver=1&sig={query['sig'][0]}",
                "rtmp_unknown_token",
            ),
        ]

        for _, param, reason_code in cases:
            with self.subTest(reason_code=reason_code):
                with patch("app.services.rtmp_ingest_auth_service.record_device_access_event") as record_state:
                    result = rtmp_ingest_auth_service.verify_rtmp_publish_hook(
                        {"app": "live", "stream": "cam-001", "param": param},
                        remote_ip="203.0.113.9",
                        now=1_700_000_120 if reason_code == "rtmp_expired" else 1_700_000_030,
                    )

                self.assertFalse(result["accepted"])
                self.assertEqual(reason_code, result["reason_code"])
                self.assertFalse(FakeAudit.rows[-1].accepted)
                self.assertEqual(reason_code, FakeAudit.rows[-1].reason_code)
                record_state.assert_called_once()
                self.assertEqual("error", record_state.call_args.kwargs["state"])
                self.assertEqual(reason_code, record_state.call_args.kwargs["reason_code"])

    def test_forced_rotation_rejects_old_token_version_and_issues_new_version(self):
        old = rtmp_ingest_auth_service.issue_rtmp_ingest_url(
            "cam-001",
            tenant_id="tenant-a",
            ttl_seconds=600,
            base_url="rtmp://media.example.com/live",
            now=1_700_000_000,
        )
        old_query = parse_qs(urlparse(old["push_url"]).query)

        rotated = rtmp_ingest_auth_service.rotate_rtmp_ingest_token(
            "cam-001",
            tenant_id="tenant-a",
            now=1_700_000_030,
        )
        self.assertEqual(2, rotated["token_version"])

        new = rtmp_ingest_auth_service.issue_rtmp_ingest_url(
            "cam-001",
            tenant_id="tenant-a",
            ttl_seconds=600,
            base_url="rtmp://media.example.com/live",
            now=1_700_000_040,
        )
        new_query = parse_qs(urlparse(new["push_url"]).query)
        self.assertEqual(["2"], new_query["ver"])
        self.assertNotEqual(old_query["sig"][0], new_query["sig"][0])

        with patch("app.services.rtmp_ingest_auth_service.record_device_access_event") as record_state:
            result = rtmp_ingest_auth_service.verify_rtmp_publish_hook(
                {
                    "app": "live",
                    "stream": "cam-001",
                    "param": (
                        f"?tenant=tenant-a&exp={old_query['exp'][0]}"
                        f"&ver={old_query['ver'][0]}&sig={old_query['sig'][0]}"
                    ),
                },
                remote_ip="203.0.113.9",
                now=1_700_000_050,
            )

        self.assertFalse(result["accepted"])
        self.assertEqual("rtmp_token_version_revoked", result["reason_code"])
        self.assertEqual("error", record_state.call_args.kwargs["state"])

    def test_forced_rotation_records_audit_and_registered_state_event(self):
        rtmp_ingest_auth_service.issue_rtmp_ingest_url(
            "cam-001",
            tenant_id="tenant-a",
            ttl_seconds=600,
            base_url="rtmp://media.example.com/live",
            now=1_700_000_000,
        )

        with patch("app.services.rtmp_ingest_auth_service.record_device_access_event") as record_state:
            rotated = rtmp_ingest_auth_service.rotate_rtmp_ingest_token(
                "cam-001",
                tenant_id="tenant-a",
                now=1_700_000_030,
            )

        self.assertEqual(2, rotated["token_version"])
        self.assertEqual(1, len(FakeAudit.rows))
        audit = FakeAudit.rows[0]
        self.assertTrue(audit.accepted)
        self.assertEqual("cam-001", audit.device_id)
        self.assertEqual("tenant-a", audit.tenant_id)
        self.assertEqual(2, audit.token_version)
        self.assertEqual("rtmp_token_rotated", audit.reason_code)
        self.assertEqual("RTMP ingest token rotated; previous push URLs are revoked", audit.reason_message)
        record_state.assert_called_once_with(
            device_id="cam-001",
            protocol="rtmp",
            state="registered",
            reason_code="rtmp_token_rotated",
            reason_message="RTMP ingest token rotated; previous push URLs are revoked",
            source_event="rtmp.token.rotate",
            stream_id="live/cam-001",
            node_id=None,
            tenant_id="tenant-a",
            commit=False,
        )


if __name__ == "__main__":
    unittest.main()
