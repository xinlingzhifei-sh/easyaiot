import types
import unittest
from datetime import datetime, timedelta, timezone
from urllib.parse import quote
from unittest.mock import patch


class AlertMediaSerializationTest(unittest.TestCase):

    def test_raw_minio_media_urls_are_rewritten_to_authorized_video_routes(self):
        from app.services.alert_service import _alert_to_dict

        image_url = (
            '/api/v1/buckets/alert-images/objects/download?'
            'prefix=tenants%2F7%2Fcameras%2Fcamera-01%2Falert.jpg')
        record_path = (
            '/api/v1/buckets/record-space/objects/download?'
            'prefix=tenants%2F7%2Fcamera-01%2Fclip.flv')
        alert = types.SimpleNamespace(
            id=1,
            object='person',
            event='intrusion',
            region='zone-a',
            device_id='camera-01',
            device_name='camera',
            image_path='/data/alerts/alert.jpg',
            image_url=image_url,
            record_path=record_path,
            information=None,
            task_type='realtime',
            time=None,
            notify_users=None,
            channels=None,
        )

        serialized = _alert_to_dict(alert)

        self.assertEqual(
            '/video/alert/image?alert_id=1',
            serialized['image_url'],
        )
        self.assertEqual(
            '/video/alert/record?alert_id=1',
            serialized['record_path'],
        )
        self.assertNotIn('image_path', serialized)
        self.assertNotIn('/api/v1/buckets/', str(serialized))
        self.assertNotIn('/data/alerts/', str(serialized))

    def test_alert_record_resolver_never_returns_raw_minio_download_url(self):
        from app.services.alert_service import _record_path_playback_payload

        record_path = (
            '/api/v1/buckets/record-space/objects/download?'
            'prefix=tenants%2F7%2Fcamera-01%2Fclip.flv')

        payload = _record_path_playback_payload(record_path, 'camera-01')

        self.assertNotIn('file_path', payload)
        self.assertEqual(
            f'/video/alert/record?path={quote(record_path, safe="")}',
            payload['video_url'],
        )

    def test_alert_record_path_fast_path_returns_the_matching_record_start(self):
        from app.services import alert_service

        record_path = (
            '/api/v1/buckets/record-space/objects/download?'
            'prefix=tenants%2F7%2Fcamera-01%2Fclip.flv')
        alert_time = datetime(2026, 7, 13, 10, 0, 20)
        record_start = datetime(2026, 7, 13, 10, 0, 0)
        alert = types.SimpleNamespace(
            id=11,
            tenant_id=7,
            device_id='camera-01',
            time=alert_time,
            record_path=record_path,
        )
        query = types.SimpleNamespace(
            filter=lambda *args: query,
            first=lambda: alert,
        )
        playback = types.SimpleNamespace(
            file_path=record_path,
            event_time=record_start,
        )

        with patch.object(alert_service, 'Alert', types.SimpleNamespace(
                id=11, tenant_id=7, query=query)), patch.object(
                alert_service, 'find_playback_for_alert', return_value=playback):
            payload = alert_service.resolve_alert_record_video(
                'camera-01',
                alert_time,
                alert_id=11,
                tenant_id=7,
            )

        self.assertEqual(record_start.isoformat(), payload['record_start_time'])
        self.assertNotEqual(alert_time.isoformat(), payload['record_start_time'])

    def test_epoch_record_filename_wins_over_legacy_metadata_end_time(self):
        from app.services import alert_service

        epoch_ms = 1783778337610
        record_start = datetime.fromtimestamp(
            epoch_ms / 1000,
            tz=timezone(timedelta(hours=8)),
        )
        metadata_time = record_start + timedelta(seconds=31)
        record_path = (
            '/api/v1/buckets/record-space/objects/download?'
            'prefix=tenants%2F7%2Fcamera-01%2F1783778337610.flv')
        alert = types.SimpleNamespace(
            id=13,
            tenant_id=7,
            device_id='camera-01',
            time=metadata_time,
            record_path=record_path,
        )
        query = types.SimpleNamespace(
            filter=lambda *args: query,
            first=lambda: alert,
        )
        playback = types.SimpleNamespace(
            file_path=record_path,
            event_time=metadata_time,
        )

        with patch.object(alert_service, 'Alert', types.SimpleNamespace(
                id=13, tenant_id=7, query=query)), patch.object(
                alert_service, 'find_playback_for_alert', return_value=playback):
            payload = alert_service.resolve_alert_record_video(
                'camera-01',
                metadata_time,
                alert_id=13,
                tenant_id=7,
            )

        self.assertEqual(record_start.isoformat(), payload['record_start_time'])
        self.assertNotEqual(metadata_time.isoformat(), payload['record_start_time'])

    def test_alert_record_path_fast_path_keeps_unknown_start_null(self):
        from app.services import alert_service

        record_path = (
            '/api/v1/buckets/record-space/objects/download?'
            'prefix=tenants%2F7%2Fcamera-01%2Funknown.flv')
        alert_time = datetime(2026, 7, 13, 10, 0, 20)
        alert = types.SimpleNamespace(
            id=12,
            tenant_id=7,
            device_id='camera-01',
            time=alert_time,
            record_path=record_path,
        )
        query = types.SimpleNamespace(
            filter=lambda *args: query,
            first=lambda: alert,
        )

        with patch.object(alert_service, 'Alert', types.SimpleNamespace(
                id=12, tenant_id=7, query=query)), patch.object(
                alert_service, 'find_playback_for_alert', return_value=None), patch.object(
                alert_service, 'find_record_file_for_alert', return_value=None):
            payload = alert_service.resolve_alert_record_video(
                'camera-01',
                alert_time,
                alert_id=12,
                tenant_id=7,
            )

        self.assertIn('record_start_time', payload)
        self.assertIsNone(payload['record_start_time'])


if __name__ == '__main__':
    unittest.main()
