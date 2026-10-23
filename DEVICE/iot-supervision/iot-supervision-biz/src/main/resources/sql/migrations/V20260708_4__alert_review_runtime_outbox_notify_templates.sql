-- Seed alert-review runtime outbox notification templates.
-- The publisher remains disabled until yfeieye.review.runtime-outbox.notify.enabled=true
-- and admin recipients are configured for the release tenant.
WITH seed_templates(id, name, code, type, nickname, content, params, remark) AS (
  VALUES
    (
      2026070841,
      'Alert Review Runtime Alert',
      'YFEIEYE_REVIEW_RUNTIME_ALERT',
      1,
      'yFeiEye',
      'Alert-review runtime alert: {alertKey}; action: {action}; run: {runId}; time: {createdAt}',
      '["alertKey","action","runId","createdAt"]',
      'FR-24 runtime patrol/outbox alert template'
    ),
    (
      2026070842,
      'Alert Review Operations Report',
      'YFEIEYE_REVIEW_OPERATIONS_REPORT',
      1,
      'yFeiEye',
      'Alert-review operations report: {reportType}; key: {reportKey}; generated: {generatedAt}; gaps: {evidenceGaps}',
      '["reportType","reportKey","generatedAt","evidenceGaps"]',
      'FR-35 shift/daily operations report template'
    )
)
INSERT INTO system_notify_template(
  id, name, code, type, nickname, content, params, status, remark,
  creator, create_time, updater, update_time, deleted
)
SELECT
  id, name, code, type, nickname, content, params, 0, remark,
  'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 0
FROM seed_templates seed
WHERE NOT EXISTS (
  SELECT 1
  FROM system_notify_template existing
  WHERE existing.code = seed.code
    AND existing.deleted = 0
);
