"""告警入站契约 v1：各通道（MQTT 总线、HTTP hook、Kafka、直连落库）共用同一幂等键。"""
import copy
import importlib
import json
import os
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

import app.services as app_services
from app.utils import algo_mqtt_bus
from app.utils.alert_ingress_contract import (
    ALERT_IDEMPOTENCY_KEY_MAX_LEN,
    apply_alert_identity_fields,
    ensure_alert_idempotency_key,
    resolve_alert_idempotency_key,
    validate_alert_ingress,
)

ROOT = Path(os.environ.get('EASYAIOT_CONTRACT_ROOT', Path(__file__).resolve().parents[2]))
FIXTURES = ROOT / 'testdata' / 'contracts'


def _load_alert_fixtures():
    fixtures = {}
    for path in sorted(FIXTURES.glob('alert_*.json')):
        data = json.loads(path.read_text(encoding='utf-8'))
        expect = data.pop('_expect')
        fixtures[path.name] = (data, expect)
    return fixtures


class AlertIdempotencyKeyContractTest(unittest.TestCase):
    def setUp(self):
        self.fixtures = _load_alert_fixtures()

    def test_shared_fixtures_are_present(self):
        self.assertEqual(
            ['alert_bus_nested.json', 'alert_event_id_only.json',
             'alert_flat_hook.json', 'alert_without_key.json'],
            sorted(self.fixtures),
        )

    def test_resolver_matches_fixture_expectation(self):
        for name, (data, expect) in self.fixtures.items():
            with self.subTest(fixture=name):
                self.assertEqual(expect['idempotency_key'], resolve_alert_idempotency_key(data))

    def test_ensure_generates_uuid_once_and_reuses_existing(self):
        data = dict(self.fixtures['alert_without_key.json'][0])
        generated = ensure_alert_idempotency_key(data)
        self.assertEqual(36, len(generated))
        self.assertEqual(generated, data['correlation_id'])
        self.assertEqual(generated, ensure_alert_idempotency_key(data))

        event_only = dict(self.fixtures['alert_event_id_only.json'][0])
        self.assertEqual('fixture-event-only', ensure_alert_idempotency_key(event_only))
        self.assertEqual('fixture-event-only', event_only['correlation_id'])

    def test_validation_only_rejects_inputs_that_cannot_be_persisted(self):
        for name, (data, _) in self.fixtures.items():
            with self.subTest(fixture=name):
                self.assertEqual([], validate_alert_ingress(data))
        self.assertEqual(['device_id_missing'], validate_alert_ingress({'device_id': ' '}))
        too_long = {'device_id': 'cam', 'correlation_id': 'x' * (ALERT_IDEMPOTENCY_KEY_MAX_LEN + 1)}
        self.assertEqual(['correlation_id_too_long'], validate_alert_ingress(too_long))
        self.assertEqual(['payload_not_object'], validate_alert_ingress(None))

    def test_kafka_identity_fields_follow_unified_key(self):
        for name, (data, expect) in self.fixtures.items():
            with self.subTest(fixture=name):
                message = apply_alert_identity_fields({}, data)
                self.assertEqual(expect['idempotency_key'], message.get('correlationId'))
                self.assertEqual(1, message['schemaVersion'])
        flat = self.fixtures['alert_flat_hook.json'][0]
        self.assertEqual('fixture-event-ignored', apply_alert_identity_fields({}, flat)['eventId'])

    def test_mqtt_bus_normalization_keeps_unified_key_and_event_id(self):
        for name, (data, expect) in self.fixtures.items():
            with self.subTest(fixture=name):
                payload = algo_mqtt_bus._normalize_alert_payload(
                    copy.deepcopy(data), snapshot=expect['snapshot'])
                self.assertEqual(expect['idempotency_key'], payload.get('correlation_id'))
                self.assertIsInstance(payload['alert'], dict)
        event_only = algo_mqtt_bus._normalize_alert_payload(
            copy.deepcopy(self.fixtures['alert_event_id_only.json'][0]))
        self.assertEqual('fixture-event-only', event_only['event_id'])

    def test_publish_alert_fills_missing_key_and_writes_it_back(self):
        data = copy.deepcopy(self.fixtures['alert_without_key.json'][0])
        published = []
        with patch.dict(os.environ, {'ALGO_BUS_TRANSPORT': 'mqtt'}), \
                patch.object(algo_mqtt_bus, '_publish',
                             side_effect=lambda topic, msg_type, payload: published.append(payload) or True):
            self.assertTrue(algo_mqtt_bus.publish_alert(data))
            self.assertTrue(algo_mqtt_bus.publish_alert(data))
        self.assertEqual(2, len(published))
        self.assertEqual(data['correlation_id'], published[0]['correlation_id'])
        self.assertEqual(published[0]['correlation_id'], published[1]['correlation_id'])


class AlertHookContractTest(unittest.TestCase):
    """HTTP hook：Kafka 消息与 Kafka 失败后的直连兜底共用同一 correlation_id。"""

    TASK = {
        'task_id': 404,
        'task_name': '烟雾',
        'task_type': 'realtime',
        'face_detection_enabled': False,
        'plate_detection_enabled': False,
        'alert_event_suppress_time': 0,
        'model_ids': [],
    }

    def setUp(self):
        self._env = os.environ.copy()
        self.addCleanup(self._restore_environment)
        self._module_name = 'app.services.alert_hook_service'
        self._missing = object()
        self._previous_hook_attribute = getattr(app_services, 'alert_hook_service', self._missing)
        self._previous_hook_module = sys.modules.pop(self._module_name, None)
        self.addCleanup(self._restore_hook_module)
        kafka_module = types.ModuleType('kafka')
        kafka_errors_module = types.ModuleType('kafka.errors')
        kafka_module.KafkaProducer = MagicMock
        kafka_errors_module.KafkaError = type('KafkaError', (Exception,), {})
        kafka_module.errors = kafka_errors_module
        # 其它测试模块会在收集阶段把 sys.modules['models'] 换成简化桩，这里固定使用自己的桩
        models_stub = types.SimpleNamespace(
            db=MagicMock(),
            AlgorithmTask=MagicMock(),
            AlgorithmTaskNotificationState=MagicMock(),
            Device=MagicMock(),
        )
        with patch.dict(sys.modules, {
            'kafka': kafka_module,
            'kafka.errors': kafka_errors_module,
            'models': models_stub,
        }):
            self.hook_mod = importlib.import_module(self._module_name)
        self.fixtures = _load_alert_fixtures()

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

    def _run_hook_with_failing_kafka(self, alert_data):
        future = MagicMock()
        future.get.side_effect = TimeoutError('kafka ack timeout')
        producer = MagicMock()
        producer.send.return_value = future
        hook = self.hook_mod
        with patch.dict(os.environ, {'ALERT_USE_DIRECT_PERSIST': 'false'}, clear=True), \
                patch.object(hook, '_query_alert_event_task', return_value=dict(self.TASK)), \
                patch.object(hook, '_query_alert_notification_config', return_value=None), \
                patch.object(hook, '_resolve_alert_event_suppress_seconds', return_value=0), \
                patch.object(hook, 'get_kafka_producer', return_value=producer), \
                patch.object(hook, '_persist_alert_directly',
                             return_value={'status': 'success', 'mode': 'direct_persist'}) as persist:
            result = hook.process_alert_hook(alert_data)
        return result, producer, persist

    def test_kafka_fallback_reuses_generated_key(self):
        alert_data = copy.deepcopy(self.fixtures['alert_without_key.json'][0])
        result, producer, persist = self._run_hook_with_failing_kafka(alert_data)

        self.assertEqual('success', result['status'])
        kafka_message = producer.send.call_args.kwargs['value']
        persisted = persist.call_args.args[0]
        self.assertEqual(36, len(kafka_message['correlationId']))
        self.assertEqual(kafka_message['correlationId'], persisted['correlation_id'])
        self.assertEqual(kafka_message['correlationId'], resolve_alert_idempotency_key(persisted))

    def test_kafka_message_uses_event_id_when_correlation_id_missing(self):
        alert_data = copy.deepcopy(self.fixtures['alert_event_id_only.json'][0])
        _, producer, persist = self._run_hook_with_failing_kafka(alert_data)

        kafka_message = producer.send.call_args.kwargs['value']
        self.assertEqual('fixture-event-only', kafka_message['correlationId'])
        self.assertEqual('fixture-event-only', kafka_message['eventId'])
        self.assertEqual('fixture-event-only', persist.call_args.args[0]['correlation_id'])

    def test_notification_message_carries_unified_key(self):
        alert_data = copy.deepcopy(self.fixtures['alert_event_id_only.json'][0])
        config = {
            'task_id': 303,
            'task_name': '安全帽',
            'notify_methods': ['http'],
            'notify_users': [{'name': 'duty', 'phone': '10000000000'}],
        }
        message = self.hook_mod._build_notification_message_for_kafka(alert_data, config)
        self.assertIsNotNone(message)
        self.assertEqual('fixture-event-only', message['correlationId'])

    def test_hook_rejects_inputs_that_cannot_be_persisted(self):
        missing_device = copy.deepcopy(self.fixtures['alert_flat_hook.json'][0])
        missing_device.pop('device_id')
        too_long = copy.deepcopy(self.fixtures['alert_flat_hook.json'][0])
        too_long['correlation_id'] = 'k' * (ALERT_IDEMPOTENCY_KEY_MAX_LEN + 1)
        for payload, problem in ((missing_device, 'device_id_missing'), (too_long, 'correlation_id_too_long')):
            with self.subTest(problem=problem), \
                    patch.object(self.hook_mod, 'get_kafka_producer') as producer_factory:
                result = self.hook_mod.process_alert_hook(payload)
                self.assertEqual('rejected', result['status'])
                self.assertEqual('invalid_alert_contract', result['reason'])
                self.assertIn(problem, result['error'])
                producer_factory.assert_not_called()


if __name__ == '__main__':
    unittest.main()
