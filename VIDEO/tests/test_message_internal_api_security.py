import os
import unittest
from unittest.mock import patch

from app.services.algorithm_task_service import _message_internal_api_headers


class MessageInternalApiSecurityTest(unittest.TestCase):

    def test_missing_internal_token_fails_closed(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, 'IOT_MESSAGE_INTERNAL_TOKEN'):
                _message_internal_api_headers()

    def test_internal_token_is_sent_in_dedicated_header(self):
        token = 'message-internal-test-token-32-bytes'
        with patch.dict(
            os.environ,
            {
                'IOT_MESSAGE_INTERNAL_TOKEN': token,
                'TENANT_ID': '7',
            },
            clear=True,
        ):
            headers = _message_internal_api_headers()

        self.assertEqual(token, headers['X-Iot-Message-Token'])
        self.assertEqual('7', headers['tenant-id'])


if __name__ == '__main__':
    unittest.main()
