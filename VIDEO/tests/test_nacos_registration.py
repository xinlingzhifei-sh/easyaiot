import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.utils.nacos_registration import (
    NacosRegistrationConfig,
    NacosRegistrationLoop,
    resolve_registration_ip,
)


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
        service_name="video-server",
        ip="10.0.0.8",
        port=6000,
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
            "service_name": "video-server",
            "ip": "10.0.0.8",
            "port": 6000,
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
        service_name="video-server",
        ip="10.0.0.8",
        port=6000,
    )
    loop = NacosRegistrationLoop(config, factory)

    loop.tick()
    loop.tick()
    factory.clients[1].fail_heartbeat = True

    assert loop.tick() is False
    assert loop.registered is False


def test_registration_uses_concrete_flask_bind_address():
    assert resolve_registration_ip(
        bind_host="172.17.0.1",
        pod_ip="",
        local_ip_factory=lambda: "192.168.0.88",
    ) == "172.17.0.1"


def test_registration_uses_detected_ip_for_wildcard_bind_address():
    assert resolve_registration_ip(
        bind_host="0.0.0.0",
        pod_ip="",
        local_ip_factory=lambda: "192.168.0.88",
    ) == "192.168.0.88"


def test_registration_keeps_explicit_pod_ip_override():
    assert resolve_registration_ip(
        bind_host="172.17.0.1",
        pod_ip="10.7.7.23",
        local_ip_factory=lambda: "192.168.0.88",
    ) == "10.7.7.23"
