import unittest

from app.utils.algo_mqtt_bus import _normalize_alert_payload


class TestAlgoMqttTenant(unittest.TestCase):

    def test_flat_payload_preserves_tenant(self):
        payload = _normalize_alert_payload({
            'tenant_id': 7,
            'device_id': 'camera-01',
            'object': 'person',
            'event': 'detection',
        })

        self.assertEqual(7, payload['tenant_id'])


if __name__ == '__main__':
    unittest.main()
