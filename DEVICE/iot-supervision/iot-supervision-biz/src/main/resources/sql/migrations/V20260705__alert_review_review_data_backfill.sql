DO $$
DECLARE
  item RECORD;
  current_data JSONB;
  current_segment JSONB;
  labels JSONB;
  zones JSONB;
  object_ids JSONB;
  source_alert_ids JSONB;
  source_alert_id TEXT;
  severity TEXT;
  segment_id TEXT;
  first_alert_time_text TEXT;
  last_alert_time_text TEXT;
BEGIN
  FOR item IN
    SELECT
      item_row.id,
      item_row.review_data,
      item_row.source_system,
      item_row.source_alert_type,
      item_row.source_alert_ids,
      item_row.object_label,
      item_row.first_alert_time,
      item_row.last_alert_time,
      item_row.camera_id,
      item_row.zone_code,
      item_row.rule_code
    FROM system_supervision_alert_review_item item_row
    WHERE item_row.deleted = 0
  LOOP
    BEGIN
      current_data := CASE
        WHEN item.review_data IS NULL OR btrim(item.review_data) = '' THEN '{}'::jsonb
        ELSE item.review_data::jsonb
      END;
    EXCEPTION WHEN others THEN
      current_data := '{}'::jsonb;
    END;

    current_segment := CASE
      WHEN jsonb_typeof(current_data -> 'reviewSegment') = 'object' THEN current_data -> 'reviewSegment'
      ELSE '{}'::jsonb
    END;
    labels := CASE
      WHEN jsonb_typeof(current_data -> 'labels') = 'array' THEN current_data -> 'labels'
      WHEN NULLIF(btrim(COALESCE(item.object_label, '')), '') IS NULL THEN '[]'::jsonb
      ELSE jsonb_build_array(btrim(item.object_label))
    END;
    zones := CASE
      WHEN jsonb_typeof(current_data -> 'zones') = 'array' THEN current_data -> 'zones'
      WHEN NULLIF(btrim(COALESCE(item.zone_code, '')), '') IS NULL THEN '[]'::jsonb
      ELSE jsonb_build_array(btrim(item.zone_code))
    END;
    object_ids := CASE
      WHEN jsonb_typeof(current_data -> 'objectIds') = 'array' THEN current_data -> 'objectIds'
      ELSE '[]'::jsonb
    END;

    SELECT COALESCE(jsonb_agg(alert_id ORDER BY first_ordinal), '[]'::jsonb)
    INTO source_alert_ids
    FROM (
      SELECT btrim(value) AS alert_id, min(ordinal) AS first_ordinal
      FROM regexp_split_to_table(COALESCE(item.source_alert_ids, ''), E'\n') WITH ORDINALITY AS split(value, ordinal)
      WHERE btrim(value) <> ''
      GROUP BY btrim(value)
    ) alerts;
    source_alert_id := COALESCE(NULLIF(source_alert_ids ->> 0, ''), item.source_system || ':review-item:' || item.id);
    severity := CASE
      WHEN lower(COALESCE(item.source_alert_type, '')) LIKE '%detect%'
        OR lower(COALESCE(item.source_alert_type, '')) LIKE '%motion%' THEN 'detection'
      ELSE 'alert'
    END;
    first_alert_time_text := CASE WHEN item.first_alert_time IS NULL THEN NULL ELSE item.first_alert_time::text END;
    last_alert_time_text := CASE WHEN item.last_alert_time IS NULL THEN first_alert_time_text ELSE item.last_alert_time::text END;
    segment_id := COALESCE(
      current_segment ->> 'segmentId',
      NULLIF(COALESCE(item.camera_id, '') || '-' || COALESCE(to_char(item.first_alert_time, 'YYYYMMDDHH24MISS'), ''), '-'),
      'review-item-' || item.id
    );

    current_data := jsonb_set(current_data, '{reviewDataVersion}', '1'::jsonb, true);
    current_data := jsonb_set(current_data, '{labels}', labels, true);
    current_data := jsonb_set(current_data, '{zones}', zones, true);
    current_data := jsonb_set(current_data, '{objectIds}', object_ids, true);

    IF jsonb_typeof(current_data -> 'objects') IS DISTINCT FROM 'array'
      OR jsonb_array_length(CASE WHEN jsonb_typeof(current_data -> 'objects') = 'array' THEN current_data -> 'objects' ELSE '[]'::jsonb END) = 0 THEN
      current_data := jsonb_set(
        current_data,
        '{objects}',
        CASE
          WHEN jsonb_array_length(labels) = 0 THEN '[]'::jsonb
          ELSE jsonb_build_array(jsonb_strip_nulls(jsonb_build_object(
            'id', object_ids ->> 0,
            'label', labels ->> 0,
            'confidence', current_data -> 'confidence',
            'bbox', current_data -> 'bbox'
          )))
        END,
        true
      );
    END IF;

    IF jsonb_typeof(current_data -> 'detections') IS DISTINCT FROM 'array'
      OR jsonb_array_length(CASE WHEN jsonb_typeof(current_data -> 'detections') = 'array' THEN current_data -> 'detections' ELSE '[]'::jsonb END) = 0 THEN
      current_data := jsonb_set(
        current_data,
        '{detections}',
        jsonb_build_array(jsonb_strip_nulls(jsonb_build_object(
          'sourceAlertId', source_alert_id,
          'alertTime', first_alert_time_text,
          'cameraId', item.camera_id,
          'zoneCode', item.zone_code,
          'ruleCode', item.rule_code,
          'objectLabel', item.object_label,
          'labels', labels,
          'zones', zones,
          'objectIds', object_ids,
          'confidence', current_data -> 'confidence',
          'bbox', current_data -> 'bbox',
          'correlationId', current_data ->> 'correlationId',
          'source', 'migration_backfill'
        ))),
        true
      );
    END IF;

    current_data := jsonb_set(
      current_data,
      '{reviewSegment}',
      current_segment || jsonb_strip_nulls(jsonb_build_object(
        'segmentId', segment_id,
        'cameraId', item.camera_id,
        'severity', severity,
        'status', 'active',
        'startTime', first_alert_time_text,
        'endTime', last_alert_time_text,
        'objectIds', object_ids,
        'zones', zones,
        'sourceAlertIds', source_alert_ids,
        'events', CASE
          WHEN jsonb_array_length(CASE WHEN jsonb_typeof(current_segment -> 'events') = 'array' THEN current_segment -> 'events' ELSE '[]'::jsonb END) > 0
            THEN current_segment -> 'events'
          ELSE jsonb_build_array(jsonb_strip_nulls(jsonb_build_object(
            'event', 'migration_backfill',
            'happenedAt', first_alert_time_text,
            'sourceAlertId', source_alert_id,
            'ruleCode', item.rule_code,
            'objectIds', object_ids,
            'labels', labels,
            'zones', zones
          )))
        END
      )),
      true
    );

    UPDATE system_supervision_alert_review_item
    SET review_data = current_data::text
    WHERE id = item.id
      AND review_data IS DISTINCT FROM current_data::text;
  END LOOP;
END $$;
