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
    ('复核录像播放', 'system:supervision-alert-review:media:playback', 10),
    ('复核抓拍查看', 'system:supervision-alert-review:media:snapshot', 20),
    ('复核证据导出', 'system:supervision-alert-review:media:export', 30),
    ('复核导出下载', 'system:supervision-alert-review:media:download', 40),
    ('复核清单校验', 'system:supervision-alert-review:media:manifest', 50)
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
