from pathlib import Path


def test_docker_model_service_uses_middleware_minio_endpoint_by_default():
    ai_root = Path(__file__).resolve().parents[1]
    compose = (ai_root / 'docker-compose.yaml').read_text(encoding='utf-8')

    assert 'MINIO_ENDPOINT=${AI_DOCKER_MINIO_ENDPOINT:-localhost:9000}' in compose
    assert 'MINIO_ENDPOINT=${MINIO_ENDPOINT:-' not in compose

    # .env.docker 含凭据，不入库；由安装脚本从 env.example 生成并改写 MINIO_ENDPOINT。
    # run.py 以 override=True 加载它，所以生成值会覆盖 compose 的 environment。
    minio_endpoint_writes = (
        'MINIO_ENDPOINT=localhost:9000',
        '_set_env_docker_kv .env.docker MINIO_ENDPOINT "localhost:9000"',
    )
    for script_name in ('install_linux.sh', 'install_linux_arm.sh', 'install_linux_kylin.sh'):
        script = (ai_root / script_name).read_text(encoding='utf-8')
        assert 'cp env.example .env.docker' in script
        assert any(write in script for write in minio_endpoint_writes), script_name
        assert 'MINIO_ENDPOINT=10.0.0.87:9000' not in script


def test_main_docker_compose_keeps_minio_data_outside_release_path():
    repo_root = Path(__file__).resolve().parents[2]
    compose = (repo_root / '.scripts' / 'docker' / 'docker-compose.yml').read_text(encoding='utf-8')

    assert '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/minio_data/data:/data:rw' in compose
    assert '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/minio_data/config:/root/.minio:rw' in compose
    assert './minio_data/data:/data:rw' not in compose
    assert './minio_data/config:/root/.minio:rw' not in compose
