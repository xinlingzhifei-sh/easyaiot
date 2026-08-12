"""Alert service tenant/camera fail-closed regression tests."""
import inspect
import unittest
from unittest.mock import patch


class _Column:
    def __init__(self, name):
        self.name = name

    def __eq__(self, value):
        return ('eq', self.name, value)

    def in_(self, values):
        return ('in', self.name, tuple(values))

    def isnot(self, value):
        return ('isnot', self.name, value)

    def desc(self):
        return ('desc', self.name)


class _Query:
    def __init__(self):
        self.criteria = []

    def filter(self, *criteria):
        self.criteria.extend(criteria)
        return self

    def order_by(self, *criteria):
        return self

    def all(self):
        return []

    def count(self):
        return 0


class AlertTenantScopeTest(unittest.TestCase):
    def test_alert_list_applies_required_tenant_and_camera_predicates(self):
        from app.services import alert_service

        self.assertIn(
            'tenant_id', inspect.signature(alert_service.get_alert_list).parameters)

        query = _Query()
        alert_model = self._alert_model(query)
        fake_db = type('Db', (), {
            'func': type('Func', (), {
                'trim': staticmethod(lambda value: value),
            })(),
        })()

        with patch.object(alert_service, 'Alert', alert_model), patch.object(
                alert_service, 'db', fake_db), patch.object(
                alert_service, 'backfill_alert_records_for_list', lambda rows: None):
            result = alert_service.get_alert_list(
                {}, tenant_id=7, camera_ids=['camera-01'])

        self.assertEqual({'alert_list': [], 'total': 0}, result)
        self.assertIn(('eq', 'tenant_id', 7), query.criteria)
        self.assertIn(('in', 'device_id', ('camera-01',)), query.criteria)

    def test_alert_list_rejects_missing_tenant_or_camera_scope(self):
        from app.services import alert_service

        self.assertIn(
            'tenant_id', inspect.signature(alert_service.get_alert_list).parameters)

        for tenant_id, camera_ids in ((None, ['camera-01']), (7, []), (0, ['camera-01'])):
            with self.subTest(tenant_id=tenant_id, camera_ids=camera_ids):
                with self.assertRaisesRegex(ValueError, 'tenant.*camera|camera.*tenant'):
                    alert_service.get_alert_list(
                        {}, tenant_id=tenant_id, camera_ids=camera_ids)

    @staticmethod
    def _alert_model(query):
        return type('Alert', (), {
            'query': query,
            'tenant_id': _Column('tenant_id'),
            'device_id': _Column('device_id'),
            'image_url': _Column('image_url'),
            'time': _Column('time'),
            'object': _Column('object'),
            'event': _Column('event'),
            'correlation_id': _Column('correlation_id'),
            'task_type': _Column('task_type'),
            'task_id': _Column('task_id'),
            'task_name': _Column('task_name'),
            'business_tags': _Column('business_tags'),
        })


if __name__ == '__main__':
    unittest.main()
