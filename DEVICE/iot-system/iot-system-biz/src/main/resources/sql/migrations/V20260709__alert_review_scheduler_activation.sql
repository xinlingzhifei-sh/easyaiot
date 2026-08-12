-- Activate the alert-review runtime jobs after all fail-closed handlers exist.
-- This is a forward migration: the original scheduler seed checksum remains stable.
UPDATE infra_job
SET status = 1,
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
