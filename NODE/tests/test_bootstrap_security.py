import unittest
from unittest.mock import Mock, patch

import run_agent


class BootstrapSecurityTest(unittest.TestCase):

    def test_remote_agent_never_calls_platform_bootstrap(self):
        with (
            patch.object(run_agent, 'PLATFORM_AGENT', False),
            patch.object(run_agent.requests, 'get') as get,
        ):
            self.assertFalse(run_agent.try_refresh_credentials())

        get.assert_not_called()

    def test_platform_agent_sends_dedicated_bootstrap_token(self):
        response = Mock(status_code=503)
        with (
            patch.object(run_agent, 'PLATFORM_AGENT', True),
            patch.object(run_agent, 'PLATFORM_AGENT_BOOTSTRAP_TOKEN', 'bootstrap-secret'),
            patch.object(run_agent.requests, 'get', return_value=response) as get,
        ):
            self.assertFalse(run_agent.try_refresh_credentials())

        self.assertEqual(
            'bootstrap-secret',
            get.call_args.kwargs['headers']['X-Bootstrap-Token'],
        )


if __name__ == '__main__':
    unittest.main()
