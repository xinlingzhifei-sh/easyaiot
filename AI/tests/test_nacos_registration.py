import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.utils.nacos_registration import NacosRegistrationConfig, NacosRegistrationLoop


class FakeClient:
    def __init__(self, fail_register=False):
        self.fail_register = fail_register
        self.fail_heartbeat = False
        self.register_calls = []
        self.heartbeat_calls = []

    def add_naming_instance(self, **kwargs):
        self.register_calls.append(kwargs)
        if self.fail_register:
            raise ConnectionError("nacos unavailable")

    def send_heartbeat(self, **kwargs):
        self.heartbeat_calls.append(kwargs)
        if self.fail_heartbeat:
            raise ConnectionError("heartbeat failed")


class FakeClientFactory:
    def __init__(self):
        self.clients = []

    def __call__(self, **kwargs):
        client = FakeClient(fail_register=len(self.clients) == 0)
        self.clients.append(client)
        return client


def test_retries_nacos_registration_after_initial_failure():
    factory = FakeClientFactory()
    config = NacosRegistrationConfig(
        server_addresses="localhost:8848",
        namespace="",
        username="nacos",
        password="pw",
        service_name="model-server",
        ip="10.0.0.9",
        port=5000,
    )
    registered = []
    loop = NacosRegistrationLoop(config, factory, on_registered=registered.append)

    assert loop.tick() is False
    assert loop.registered is False

    assert loop.tick() is True

    assert loop.registered is True
    assert len(factory.clients) == 2
    assert registered == [factory.clients[1]]
    assert factory.clients[1].register_calls == [
        {
            "service_name": "model-server",
            "ip": "10.0.0.9",
            "port": 5000,
            "cluster_name": "DEFAULT",
            "healthy": True,
            "ephemeral": True,
        }
    ]


def test_heartbeat_failure_returns_loop_to_registration_mode():
    factory = FakeClientFactory()
    config = NacosRegistrationConfig(
        server_addresses="localhost:8848",
        namespace="",
        username="nacos",
        password="pw",
        service_name="model-server",
        ip="10.0.0.9",
        port=5000,
    )
    loop = NacosRegistrationLoop(config, factory)

    loop.tick()
    loop.tick()
    factory.clients[1].fail_heartbeat = True

    assert loop.tick() is False
    assert loop.registered is False
