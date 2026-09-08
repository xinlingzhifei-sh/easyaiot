import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parent


class HttpsEntrypointTest(unittest.TestCase):
    """上游入口变更合并后，保留本地 HTTPS 与 HTTP/2 部署约定。"""

    def test_docker_nginx_profiles_keep_https_and_http2(self):
        for name in ('nginx.conf', 'nginx.mini.conf', 'nginx.edge.conf'):
            with self.subTest(config=name):
                config = (ROOT / 'conf' / name).read_text(encoding='utf-8')
                self.assertIn('listen 443 ssl;', config)
                self.assertIn('http2 on;', config)
                self.assertIn('ssl_certificate     /etc/nginx/ssl/server.crt;', config)
                self.assertIn('ssl_certificate_key /etc/nginx/ssl/server.key;', config)
                self.assertIn('ssl_protocols       TLSv1.2 TLSv1.3;', config)
                self.assertNotIn('listen 80;', config)

    def test_compose_maps_web_port_to_https_and_probes_https(self):
        compose = (ROOT / 'docker-compose.yaml').read_text(encoding='utf-8')
        self.assertIn('"${WEB_PORT:-8888}:443"', compose)
        self.assertIn('./conf/ssl:/etc/nginx/ssl:ro', compose)
        self.assertIn('"--no-check-certificate", "https://127.0.0.1/health"', compose)
        self.assertNotIn('http://127.0.0.1/health', compose)

    def test_linux_lifecycle_prepares_tls_and_probes_https(self):
        installer = (ROOT / 'install_linux.sh').read_text(encoding='utf-8')
        for name in ('install_service', 'start_service', 'restart_service'):
            with self.subTest(lifecycle=name):
                body = installer.split(f'{name}() {{', 1)[1].split('\n}', 1)[0]
                self.assertIn('ensure_ssl_certs || exit 1', body)
        self.assertIn('wget -q --spider --no-check-certificate https://127.0.0.1/health', installer)


if __name__ == '__main__':
    unittest.main()
