from pathlib import Path


VIDEO_ROOT = Path(__file__).resolve().parents[1]


def test_algorithm_task_motion_gate_columns_are_migrated_on_startup():
    models_source = (VIDEO_ROOT / "models.py").read_text(encoding="utf-8")
    run_source = (VIDEO_ROOT / "run.py").read_text(encoding="utf-8")

    assert "def ensure_algorithm_task_motion_gate_columns" in models_source
    assert "'motion_gate_enabled': 'BOOLEAN NOT NULL DEFAULT FALSE'" in models_source
    assert "'motion_gate_config': 'TEXT'" in models_source
    assert "ensure_algorithm_task_motion_gate_columns" in run_source
