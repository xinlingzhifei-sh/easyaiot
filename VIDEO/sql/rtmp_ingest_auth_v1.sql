CREATE TABLE IF NOT EXISTS device_rtmp_ingest_secret (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(100) NOT NULL,
    tenant_id VARCHAR(100) NOT NULL,
    token_version INT NOT NULL DEFAULT 1,
    secret VARCHAR(128) NOT NULL,
    rotated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_device_rtmp_ingest_secret_device_tenant
    ON device_rtmp_ingest_secret(device_id, tenant_id);

CREATE TABLE IF NOT EXISTS device_rtmp_publish_audit (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(100),
    tenant_id VARCHAR(100),
    node_id INT,
    app VARCHAR(64),
    stream VARCHAR(200),
    token_version INT,
    accepted BOOLEAN NOT NULL DEFAULT FALSE,
    reason_code VARCHAR(100) NOT NULL,
    reason_message TEXT,
    remote_ip VARCHAR(64),
    raw_params TEXT,
    event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_device_rtmp_publish_audit_device_time
    ON device_rtmp_publish_audit(device_id, event_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_rtmp_publish_audit_tenant_time
    ON device_rtmp_publish_audit(tenant_id, event_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_rtmp_publish_audit_node_time
    ON device_rtmp_publish_audit(node_id, event_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_rtmp_publish_audit_reason_time
    ON device_rtmp_publish_audit(reason_code, event_time DESC);
