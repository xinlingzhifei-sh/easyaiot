CREATE OR REPLACE FUNCTION fn_supervision_alert_review_segment_status_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  old_rank INTEGER;
  new_rank INTEGER;
BEGIN
  IF OLD.segment_status IS NOT DISTINCT FROM NEW.segment_status THEN
    RETURN NEW;
  END IF;

  old_rank := CASE OLD.segment_status
    WHEN 'active' THEN 1
    WHEN 'detection' THEN 2
    WHEN 'alert' THEN 3
    WHEN 'ended' THEN 4
    ELSE NULL
  END;
  new_rank := CASE NEW.segment_status
    WHEN 'active' THEN 1
    WHEN 'detection' THEN 2
    WHEN 'alert' THEN 3
    WHEN 'ended' THEN 4
    ELSE NULL
  END;

  IF old_rank IS NULL OR new_rank IS NULL THEN
    RETURN NEW;
  END IF;
  IF new_rank < old_rank THEN
    RAISE EXCEPTION 'invalid review segment status transition: % -> %', OLD.segment_status, NEW.segment_status
      USING ERRCODE = 'check_violation';
  END IF;

  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS tr_supervision_alert_review_segment_status_transition
ON system_supervision_alert_review_segment;

CREATE TRIGGER tr_supervision_alert_review_segment_status_transition
BEFORE UPDATE OF segment_status
ON system_supervision_alert_review_segment
FOR EACH ROW
WHEN (OLD.deleted = 0 AND NEW.deleted = 0)
EXECUTE FUNCTION fn_supervision_alert_review_segment_status_transition();
