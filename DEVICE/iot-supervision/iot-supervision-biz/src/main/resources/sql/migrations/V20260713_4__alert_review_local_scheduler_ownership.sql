-- The owning iot-system service runs these handlers only when the explicit local
-- scheduler flag is enabled. Keep the shared Quartz source paused so a scheduler
-- repair or sync cannot activate a second execution source.
LOCK TABLE infra_job,
           system_supervision_alert_review_runtime_outbox,
           system_supervision_alert_review_runtime_outbox_delivery
IN SHARE ROW EXCLUSIVE MODE;

UPDATE infra_job
SET status = 2,
    updater = 'system',
    update_time = CURRENT_TIMESTAMP
WHERE handler_name IN (
  'supervisionAlertReviewRuntimePatrolJob',
  'supervisionAlertReviewRuntimeOutboxJob',
  'supervisionAlertReviewEventReconcileJob',
  'supervisionAlertReviewEvidenceExportWorkerJob',
  'supervisionAlertReviewSemanticIndexJob',
  'supervisionAlertReviewOperationsReportJob'
)
  AND deleted = 0;

-- Preserve the earliest delivery attempt if a pre-migration race created more
-- than one row for the same report scope.
WITH ranked_reports AS (
  SELECT id,
         ROW_NUMBER() OVER (
           PARTITION BY tenant_id, event_type, alert_key
           ORDER BY created_at, id
         ) AS row_number
  FROM system_supervision_alert_review_runtime_outbox
  WHERE event_type = 'review_operations_report'
    AND deleted = 0
)
UPDATE system_supervision_alert_review_runtime_outbox_delivery delivery
SET deleted = 1,
    updater = 'system',
    update_time = CURRENT_TIMESTAMP
FROM ranked_reports duplicate
WHERE delivery.outbox_id = duplicate.id
  AND duplicate.row_number > 1
  AND delivery.deleted = 0;

WITH ranked_reports AS (
  SELECT id,
         ROW_NUMBER() OVER (
           PARTITION BY tenant_id, event_type, alert_key
           ORDER BY created_at, id
         ) AS row_number
  FROM system_supervision_alert_review_runtime_outbox
  WHERE event_type = 'review_operations_report'
    AND deleted = 0
)
UPDATE system_supervision_alert_review_runtime_outbox target
SET deleted = 1,
    updater = 'system',
    update_time = CURRENT_TIMESTAMP
FROM ranked_reports duplicate
WHERE target.id = duplicate.id
  AND duplicate.row_number > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_runtime_outbox_report
ON system_supervision_alert_review_runtime_outbox(tenant_id, event_type, alert_key)
WHERE deleted = 0
  AND event_type = 'review_operations_report';
