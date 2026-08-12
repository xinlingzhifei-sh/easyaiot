from dataclasses import dataclass


def resolve_registration_ip(bind_host, pod_ip, local_ip_factory):
    """Return an address reachable wherever the service is actually bound."""
    explicit_ip = (pod_ip or '').strip()
    if explicit_ip:
        return explicit_ip

    concrete_bind_host = (bind_host or '').strip()
    if concrete_bind_host not in ('', '0.0.0.0', '::', '[::]'):
        return concrete_bind_host

    return local_ip_factory()


@dataclass(frozen=True)
class NacosRegistrationConfig:
    server_addresses: str
    namespace: str
    username: str
    password: str
    service_name: str
    ip: str
    port: int


class NacosRegistrationLoop:
    def __init__(self, config, client_factory, on_registered=None, logger=print):
        self.config = config
        self.client_factory = client_factory
        self.on_registered = on_registered
        self.logger = logger
        self.client = None
        self.registered = False

    def tick(self):
        if not self.registered:
            return self._register()
        return self._heartbeat()

    def run(self, stop_event, interval=5):
        while not stop_event.is_set():
            self.tick()
            stop_event.wait(interval)

    def _register(self):
        try:
            client = self.client_factory(
                server_addresses=self.config.server_addresses,
                namespace=self.config.namespace,
                username=self.config.username,
                password=self.config.password,
            )
            client.add_naming_instance(
                service_name=self.config.service_name,
                ip=self.config.ip,
                port=self.config.port,
                cluster_name="DEFAULT",
                healthy=True,
                ephemeral=True,
            )
            self.client = client
            self.registered = True
            if self.on_registered:
                self.on_registered(client)
            self.logger(
                f"Nacos service registered: "
                f"{self.config.service_name}@{self.config.ip}:{self.config.port}"
            )
            return True
        except Exception as exc:
            self.client = None
            self.registered = False
            self.logger(f"Nacos registration failed, will retry: {exc}")
            return False

    def _heartbeat(self):
        try:
            self.client.send_heartbeat(
                service_name=self.config.service_name,
                ip=self.config.ip,
                port=self.config.port,
            )
            return True
        except Exception as exc:
            self.registered = False
            self.logger(f"Nacos heartbeat failed, will re-register: {exc}")
            return False
