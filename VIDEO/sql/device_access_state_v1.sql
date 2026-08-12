CREATE TABLE IF NOT EXISTS device_access_state_event (
    id BIGSERIAL PRIMARY KEY,
    device_id VARCHAR(100) NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    reason_code VARCHAR(100),
    reason_message TEXT,
    source_event VARCHAR(100),
    event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    stream_id VARCHAR(200),
    node_id BIGINT,
    tenant_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_device_access_state_event_device_time
    ON device_access_state_event(device_id, event_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_access_state_event_tenant_protocol
    ON device_access_state_event(tenant_id, protocol, event_time DESC);

CREATE TABLE IF NOT EXISTS device_access_state_current (
    device_id VARCHAR(100) NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    reason_code VARCHAR(100),
    reason_message TEXT,
    source_event VARCHAR(100),
    last_transition_time TIMESTAMP NOT NULL,
    stream_id VARCHAR(200),
    node_id BIGINT,
    tenant_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (device_id, protocol)
);

CREATE INDEX IF NOT EXISTS idx_device_access_state_current_state
    ON device_access_state_current(state, last_transition_time DESC);

CREATE INDEX IF NOT EXISTS idx_device_access_state_current_tenant
    ON device_access_state_current(tenant_id, state, last_transition_time DESC);
