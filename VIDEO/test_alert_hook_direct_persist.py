"""alert_hook mini 直连落库逻辑单元测试。"""
import importlib
import os
import sys
import types
import unittest
from unittest.mock import MagicMock, patch

import app.services as app_services


class TestAlertHookDirectPersist(unittest.TestCase):
    def test_notification_channel_key_is_stable_and_channel_specific(self):
        email = {'method': 'email', 'template_id': 11}
        reordered_email = {'template_id': 11, 'method': 'email'}
        webhook = {'method': 'http', 'template_id': 11}

        self.assertEqual(
            self.hook_mod._notification_channel_key(email),
            self.hook_mod._notification_channel_key(reordered_email),
        )
        self.assertNotEqual(
            self.hook_mod._notification_channel_key(email),
            self.hook_mod._notification_channel_key(webhook),
        )

    def setUp(self):
        self._env = os.environ.copy()
        self.addCleanup(self._restore_environment)
        self._module_name = 'app.services.alert_hook_service'
        self._missing = object()
        self._previous_hook_attribute = getattr(
            app_services, 'alert_hook_service', self._missing)
        self._previous_hook_module = sys.modules.pop(self._module_name, None)
        self.addCleanup(self._restore_hook_module)
        kafka_module = types.ModuleType('kafka')
        kafka_errors_module = types.ModuleType('kafka.errors')
        kafka_module.KafkaProducer = MagicMock
        kafka_errors_module.KafkaError = type('KafkaError', (Exception,), {})
        kafka_module.errors = kafka_errors_module
        with patch.dict(sys.modules, {
            'kafka': kafka_module,
            'kafka.errors': kafka_errors_module,
        }):
            self.hook_mod = importlib.import_module(self._module_name)

    def _restore_environment(self):
        os.environ.clear()
        os.environ.update(self._env)

    def _restore_hook_module(self):
        sys.modules.pop(self._module_name, None)
        if self._previous_hook_module is not None:
            sys.modules[self._module_name] = self._previous_hook_module
        if self._previous_hook_attribute is self._missing:
            try:
                delattr(app_services, 'alert_hook_service')
            except AttributeError:
                pass
        else:
            app_services.alert_hook_service = self._previous_hook_attribute

    def test_should_use_direct_persist_in_mini(self):
        with patch.dict(os.environ, {'EASYAIOT_DEPLOY_PROFILE': 'mini'}, clear=True):
            self.assertTrue(self.hook_mod._should_use_direct_alert_persist())

    def test_kafka_suppression_isolated_by_event_identity(self):
        with patch.object(self.hook_mod.time, 'time', return_value=100.0):
            first_person = self.hook_mod._should_suppress_alert_event_kafka(
                9, 'dev-1', 'realtime', 5, '1:person'
            )
            first_detection = self.hook_mod._should_suppress_alert_event_kafka(
                9, 'dev-1', 'realtime', 5, '32:detection'
            )
            repeated_person = self.hook_mod._should_suppress_alert_event_kafka(
                9, 'dev-1', 'realtime', 5, '1:person'
            )

        self.assertFalse(first_person)
        self.assertFalse(first_detection)
        self.assertTrue(repeated_person)

    def test_kafka_suppression_isolated_by_event_identity(self):
        with patch.object(hook_mod.time, 'time', return_value=100.0):
            first_person = hook_mod._should_suppress_alert_event_kafka(
                9, 'dev-1', 'realtime', 5, '1:person'
            )
            first_detection = hook_mod._should_suppress_alert_event_kafka(
                9, 'dev-1', 'realtime', 5, '32:detection'
            )
            repeated_person = hook_mod._should_suppress_alert_event_kafka(
                9, 'dev-1', 'realtime', 5, '1:person'
            )

        self.assertFalse(first_person)
        self.assertFalse(first_detection)
        self.assertTrue(repeated_person)

    def test_process_alert_hook_uses_direct_persist_in_mini(self):
        alert_data = {
            'object': 'chair',
            'event': '办公室设备',
            'device_id': 'dev-1',
            'device_name': 'CH1',
            'task_type': 'realtime',
            'time': '2026-06-20 12:00:00',
        }
        task = {
            'task_id': 1,
            'task_name': '办公室设备',
            'task_type': 'realtime',
            'face_detection_enabled': False,
            'plate_detection_enabled': False,
            'alert_event_suppress_time': 5,
        }
        with patch.dict(os.environ, {'EASYAIOT_DEPLOY_PROFILE': 'mini'}, clear=True):
            with patch.object(self.hook_mod, '_query_alert_event_task', return_value=task), \
                    patch.object(self.hook_mod, '_query_alert_notification_config', return_value=None):
                with patch.object(self.hook_mod, '_persist_alert_directly', return_value={'status': 'success', 'alert_id': 99, 'mode': 'direct_persist'}) as persist_mock:
                    result = self.hook_mod.process_alert_hook(alert_data)
        self.assertEqual(result['status'], 'success')
        self.assertEqual(result['alert_id'], 99)
        persist_mock.assert_called_once()

    def test_minimal_kafka_message_preserves_tenant(self):
        message = self.hook_mod._build_minimal_alert_kafka_message(
            {
                'tenant_id': 7,
                'object': 'person',
                'event': 'detection',
                'device_id': 'camera-01',
            },
            {},
        )

        self.assertEqual(7, message['tenant_id'])


if __name__ == '__main__':
    unittest.main()
