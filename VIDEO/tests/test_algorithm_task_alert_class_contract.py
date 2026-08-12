from pathlib import Path

from app.utils.alert_class_filter import filter_detections_for_alert


def test_empty_alert_classes_allow_any_detection():
    detections = [{"class_name": "person"}, {"class_name": "safehat"}]

    assert filter_detections_for_alert(detections, []) == detections


def test_non_empty_alert_classes_keep_filtering_enabled():
    detections = [{"class_name": "person"}, {"class_name": "safehat"}]

    assert filter_detections_for_alert(detections, ["safehat"]) == [detections[1]]


def test_task_service_does_not_reject_empty_alert_classes():
    source = Path("app/services/algorithm_task_service.py").read_text(encoding="utf-8")

    assert "if alert_event_enabled and not parse_alert_class_names(alert_class_names):" not in source
    assert "启用告警事件时必须指定至少一个告警触发标签" not in source
