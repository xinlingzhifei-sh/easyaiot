-- Seed alert-review scheduler jobs in a safe paused state.
-- Operators must enable them after release-specific VIDEO, notification, and tenant scope checks.
WITH seed_jobs(name, handler_name, handler_param, cron_expression, retry_count, retry_interval, monitor_timeout) AS (
  VALUES
    ('Alert Review Runtime Patrol', 'supervisionAlertReviewRuntimePatrolJob', '', '0 0/5 * * * ?', 3, 60000, 300000),
    ('Alert Review Runtime Outbox', 'supervisionAlertReviewRuntimeOutboxJob', '100', '0 0/1 * * * ?', 3, 60000, 180000),
    ('Alert Review Event Reconcile', 'supervisionAlertReviewEventReconcileJob', '', '0 0/5 * * * ?', 3, 60000, 180000),
    ('Alert Review Evidence Export', 'supervisionAlertReviewEvidenceExportWorkerJob', '20', '0 0/2 * * * ?', 3, 60000, 300000),
    ('Alert Review Semantic Index', 'supervisionAlertReviewSemanticIndexJob', '50', '0 0/10 * * * ?', 3, 60000, 300000),
    ('Alert Review Shift Report', 'supervisionAlertReviewOperationsReportJob', 'shift', '0 0 0/8 * * ?', 3, 60000, 300000),
    ('Alert Review Daily Report', 'supervisionAlertReviewOperationsReportJob', 'daily', '0 10 0 * * ?', 3, 60000, 300000)
),
numbered_seed_jobs AS (
  SELECT
    2026070800 + row_number() OVER (ORDER BY handler_name, handler_param) AS id,
    name,
    handler_name,
    handler_param,
    cron_expression,
    retry_count,
    retry_interval,
    monitor_timeout
  FROM seed_jobs
)
INSERT INTO infra_job(
  id, name, status, handler_name, handler_param, cron_expression,
  retry_count, retry_interval, monitor_timeout,
  creator, create_time, updater, update_time, deleted
)
SELECT
  id, name, 2, handler_name, handler_param, cron_expression,
  retry_count, retry_interval, monitor_timeout,
  'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 0
FROM numbered_seed_jobs seed
WHERE NOT EXISTS (
  SELECT 1
  FROM infra_job existing
  WHERE existing.handler_name = seed.handler_name
    AND existing.handler_param IS NOT DISTINCT FROM seed.handler_param
    AND existing.deleted = 0
);
