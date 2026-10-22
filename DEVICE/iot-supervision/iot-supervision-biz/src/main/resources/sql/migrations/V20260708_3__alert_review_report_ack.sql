CREATE TABLE IF NOT EXISTS system_supervision_alert_review_report_ack (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  report_key VARCHAR(128) NOT NULL,
  report_type VARCHAR(32) NOT NULL,
  period_start TIMESTAMP,
  period_end TIMESTAMP,
  review_item_ids TEXT NOT NULL,
  acknowledgement_status VARCHAR(32) NOT NULL,
  acknowledged_by BIGINT,
  acknowledged_at TIMESTAMP,
  acknowledgement_note TEXT,
  metadata TEXT,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_report_ack_key
ON system_supervision_alert_review_report_ack(tenant_id, report_key)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_report_ack_scope
ON system_supervision_alert_review_report_ack(tenant_id, report_type, period_start, period_end);
