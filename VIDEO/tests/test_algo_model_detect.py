import unittest

from app.utils.algo_model_detect import (
    allowed_classes_include_person,
    dedupe_detections_by_iou,
    filter_detections_by_allowed_classes,
    filter_person_like_detections,
    iter_tiled_regions,
    prefer_loaded_person_classes,
    resolve_model_allowed_class_names,
)


class AlgoModelDetectTest(unittest.TestCase):
    def test_person_model_name_limits_coco_outputs_to_person(self):
        allowed = resolve_model_allowed_class_names(
            {
                "name": "人模型",
                "classNames": [],
                "selectedClassNames": [],
            }
        )

        detections = [
            {"class_name": "person", "confidence": 0.8},
            {"class_name": "car", "confidence": 0.9},
            {"class_name": "train", "confidence": 0.7},
        ]

        filtered = filter_detections_by_allowed_classes(detections, allowed)

        self.assertEqual([det["class_name"] for det in filtered], ["person"])

    def test_explicit_model_classes_take_priority_over_name_fallback(self):
        allowed = resolve_model_allowed_class_names(
            {
                "name": "人模型",
                "selectedClassNames": ["helmet", "no_helmet"],
            }
        )

        detections = [
            {"class_name": "person", "confidence": 0.8},
            {"class_name": "helmet", "confidence": 0.9},
            {"class_name": "no helmet", "confidence": 0.7},
        ]

        filtered = filter_detections_by_allowed_classes(detections, allowed)

        self.assertEqual(
            [det["class_name"] for det in filtered],
            ["helmet", "no helmet"],
        )

    def test_loaded_person_only_model_does_not_override_non_person_task_classes(self):
        allowed = prefer_loaded_person_classes({"face"}, {0: "person"}.values())

        detections = [
            {"class_name": "person", "confidence": 0.8},
            {"class_name": "face", "confidence": 0.9},
        ]

        filtered = filter_detections_by_allowed_classes(detections, allowed)

        self.assertEqual([det["class_name"] for det in filtered], ["face"])

    def test_loaded_person_only_model_keeps_person_task_classes(self):
        allowed = prefer_loaded_person_classes({"person", "human"}, {0: "person"}.values())

        detections = [
            {"class_name": "person", "confidence": 0.8},
            {"class_name": "face", "confidence": 0.9},
        ]

        filtered = filter_detections_by_allowed_classes(detections, allowed)

        self.assertEqual([det["class_name"] for det in filtered], ["person"])

    def test_loaded_multiclass_model_keeps_configured_fallback(self):
        allowed = prefer_loaded_person_classes({"fall"}, {0: "stand", 1: "fall"}.values())

        detections = [
            {"class_name": "stand", "confidence": 0.8},
            {"class_name": "fall", "confidence": 0.9},
        ]

        filtered = filter_detections_by_allowed_classes(detections, allowed)

        self.assertEqual([det["class_name"] for det in filtered], ["fall"])

    def test_allowed_classes_include_person_aliases(self):
        self.assertTrue(allowed_classes_include_person({"person"}))
        self.assertTrue(allowed_classes_include_person({"行人"}))
        self.assertFalse(allowed_classes_include_person({"car", "train"}))

    def test_tiled_regions_cover_frame_with_overlap(self):
        regions = iter_tiled_regions((360, 640, 3), columns=3, rows=2, overlap_ratio=0.2)

        self.assertEqual(len(regions), 6)
        self.assertEqual(regions[0], (0, 0, 234, 198))
        self.assertEqual(regions[-1], (405, 162, 640, 360))

    def test_dedupe_keeps_highest_confidence_same_class_overlap(self):
        detections = [
            {"class_name": "person", "confidence": 0.4, "bbox": [10, 10, 50, 80]},
            {"class_name": "person", "confidence": 0.8, "bbox": [12, 12, 52, 82]},
            {"class_name": "helmet", "confidence": 0.7, "bbox": [12, 12, 52, 82]},
        ]

        filtered = dedupe_detections_by_iou(detections, iou_threshold=0.5)

        self.assertEqual(
            [(det["class_name"], det["confidence"]) for det in filtered],
            [("person", 0.8), ("helmet", 0.7)],
        )

    def test_person_like_filter_rejects_low_quality_false_person_boxes(self):
        detections = [
            {"class_name": "person", "confidence": 0.82, "bbox": [20, 20, 42, 90]},
            {"class_name": "person", "confidence": 0.72, "bbox": [100, 100, 190, 130]},
            {"class_name": "person", "confidence": 0.11, "bbox": [220, 90, 236, 128]},
            {"class_name": "person", "confidence": 0.8, "bbox": [300, 100, 306, 112]},
            {"class_name": "helmet", "confidence": 0.22, "bbox": [10, 10, 70, 40]},
        ]

        filtered = filter_person_like_detections(
            detections,
            frame_shape=(360, 640, 3),
            min_confidence=0.18,
            min_height_ratio=0.035,
        )

        self.assertEqual(
            [(det["class_name"], det["bbox"]) for det in filtered],
            [
                ("person", [20, 20, 42, 90]),
                ("helmet", [10, 10, 70, 40]),
            ],
        )

    def test_person_like_filter_rejects_tiny_tiled_false_person_boxes_by_default(self):
        detections = [
            {"class_name": "person", "confidence": 0.36, "bbox": [443, 203, 450, 214]},
            {"class_name": "person", "confidence": 0.27, "bbox": [386, 166, 392, 176]},
            {"class_name": "person", "confidence": 0.82, "bbox": [300, 200, 311, 218]},
        ]

        filtered = filter_person_like_detections(detections, frame_shape=(360, 640, 3))

        self.assertEqual(
            [det["bbox"] for det in filtered],
            [[300, 200, 311, 218]],
        )


if __name__ == "__main__":
    unittest.main()
