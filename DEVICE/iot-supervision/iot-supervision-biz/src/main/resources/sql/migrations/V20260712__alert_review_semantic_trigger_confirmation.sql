CREATE UNIQUE INDEX IF NOT EXISTS uk_alert_review_semantic_trigger_evaluation
ON system_supervision_alert_review_case_audit(
  tenant_id,
  (substring(metadata FROM '"evaluationId":"(sem-[0-9a-f-]{36})"'))
)
WHERE deleted = 0
  AND action_type = 'semantic_trigger_evaluated'
  AND position('"schemaVersion":"semantic-trigger-evaluation-v1"' IN metadata) > 0
  AND substring(metadata FROM '"evaluationId":"(sem-[0-9a-f-]{36})"') IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_alert_review_semantic_trigger_terminal
ON system_supervision_alert_review_case_audit(
  tenant_id,
  (substring(metadata FROM '"evaluationId":"(sem-[0-9a-f-]{36})"'))
)
WHERE deleted = 0
  AND (
    (
      action_type = 'semantic_trigger_confirmed'
      AND position('"humanConfirmationStatus":"confirmed"' IN metadata) > 0
    )
    OR (
      action_type = 'semantic_trigger_rejected'
      AND position('"humanConfirmationStatus":"rejected"' IN metadata) > 0
    )
  )
  AND position('"schemaVersion":"semantic-trigger-evaluation-v1"' IN metadata) > 0
  AND substring(metadata FROM '"evaluationId":"(sem-[0-9a-f-]{36})"') IS NOT NULL;

CREATE SEQUENCE IF NOT EXISTS system_menu_seq;

SELECT setval(
  'system_menu_seq',
  GREATEST(
    COALESCE((SELECT MAX(id) FROM system_menu), 0),
    (SELECT last_value FROM system_menu_seq)
  ),
  TRUE
);

WITH seed(name, permission, sort) AS (
  VALUES
    ('语义触发评估', 'system:supervision-alert-review:semantic-trigger:evaluate', 70),
    ('语义触发确认', 'system:supervision-alert-review:semantic-trigger:confirm', 80)
),
updated AS (
  UPDATE system_menu menu
  SET
    name = seed.name,
    type = 3,
    sort = seed.sort,
    status = 0,
    visible = TRUE,
    keep_alive = TRUE,
    always_show = TRUE,
    updater = 'system',
    update_time = CURRENT_TIMESTAMP,
    deleted = 0
  FROM seed
  WHERE menu.permission = seed.permission
  RETURNING menu.permission
)
INSERT INTO system_menu(
  id,
  name,
  permission,
  type,
  sort,
  parent_id,
  path,
  icon,
  component,
  component_name,
  status,
  visible,
  keep_alive,
  always_show,
  creator,
  create_time,
  updater,
  update_time,
  deleted
)
SELECT
  nextval('system_menu_seq'),
  seed.name,
  seed.permission,
  3,
  seed.sort,
  0,
  '',
  '#',
  NULL,
  NULL,
  0,
  TRUE,
  TRUE,
  TRUE,
  'system',
  CURRENT_TIMESTAMP,
  'system',
  CURRENT_TIMESTAMP,
  0
FROM seed
WHERE NOT EXISTS (
  SELECT 1 FROM updated WHERE updated.permission = seed.permission
)
  AND NOT EXISTS (
    SELECT 1 FROM system_menu menu WHERE menu.permission = seed.permission
  );
