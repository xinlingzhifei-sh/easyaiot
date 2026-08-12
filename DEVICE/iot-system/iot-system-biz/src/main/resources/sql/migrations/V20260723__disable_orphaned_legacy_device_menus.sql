WITH retired_parent AS (
  SELECT id
  FROM system_menu
  WHERE deleted = 0
    AND status <> 0
    AND path IN ('/device', 'producthidden')
)
UPDATE system_menu child
SET
  status = 1,
  visible = FALSE,
  updater = 'system',
  update_time = CURRENT_TIMESTAMP
FROM retired_parent parent
WHERE child.parent_id = parent.id
  AND child.deleted = 0
  AND child.component IN (
    'device/device_group/index',
    'device/device_log/index',
    'device/device_topic/index',
    'device/product/index',
    'device/product_template/index',
    'device/product_type/index',
    'device/protocol/index'
  );
