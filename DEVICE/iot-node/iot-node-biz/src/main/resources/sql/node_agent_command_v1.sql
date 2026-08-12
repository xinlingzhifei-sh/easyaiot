CREATE TABLE IF NOT EXISTS node_agent_command (
    id BIGSERIAL PRIMARY KEY,
    node_id BIGINT NOT NULL,
    command_type VARCHAR(64) NOT NULL,
    command_key VARCHAR(160) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    attempt_count INT NOT NULL DEFAULT 0,
    lease_until TIMESTAMP,
    last_error TEXT,
    result_json TEXT,
    acked_at TIMESTAMP,
    finished_at TIMESTAMP,
    creator VARCHAR(64),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater VARCHAR(64),
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_node_agent_command_active_key
    ON node_agent_command(node_id, command_key)
    WHERE deleted = FALSE AND status IN ('pending', 'leased', 'running');

CREATE INDEX IF NOT EXISTS idx_node_agent_command_poll
    ON node_agent_command(node_id, status, lease_until, create_time)
    WHERE deleted = FALSE;
