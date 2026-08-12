"""
SAM + YOLO 智能标注策略：定义各阶段参与时机与标注模式决策。

核心链路：SAM 冷启动 →（可选自动训练 YOLO）→ YOLO 量产 → SAM 补充 → 迭代 YOLO
"""
from __future__ import annotations

import json
from copy import deepcopy
from typing import Any

# 流水线阶段
PHASE_COLLECTING = 'collecting'
PHASE_BOOTSTRAP_SAM = 'bootstrap_sam'
PHASE_TRAINING = 'training'
PHASE_YOLO_LABEL = 'yolo_label'
PHASE_ITERATE = 'iterate'
PHASE_PACKAGING = 'packaging'
PHASE_DONE = 'done'
PHASE_PAUSED = 'paused'
PHASE_CANCELLED = 'cancelled'

# 内置标准预训练（工程根目录下权重文件）
OFFICIAL_MODEL_ARCHS = (
    '@AI/yolo26n.pt',
    '@AI/yolo11n.pt',
    '@AI/yolov8n.pt',
)
DEFAULT_MODEL_ARCH = OFFICIAL_MODEL_ARCHS[0]

_LEGACY_ARCH_ALIASES = {
    'yolo26n.pt': '@AI/yolo26n.pt',
    'yolo11n.pt': '@AI/yolo11n.pt',
    'yolov8n.pt': '@AI/yolov8n.pt',
}

DEFAULT_STRATEGY: dict[str, Any] = {
    # 阶段一：SAM 冷启动（0 样本首批标注）
    'bootstrap_sam_limit': 200,
    # True=跳过 SAM 冷启动（与 initial_model_id 独立）
    'skip_sam_cold_start': False,
    # 每标注多少张后触发 YOLO 迭代训练（0=不自动迭代，仅首轮训练）
    'yolo_iterate_every': 500,
    # 是否首轮 SAM 完成后自动训练 YOLO
    'auto_train_yolo': True,
    # 量产推理用模型（模型管理 model_id）；可与 SAM 冷启动并存
    'initial_model_id': None,
    # 自动训练微调基座（模型管理 model_id）；不填则首轮用 model_arch
    'pretrain_model_id': None,
    # 无 pretrain_model_id 时使用的标准预训练（默认 YOLO26n）
    'model_arch': DEFAULT_MODEL_ARCH,
    'sam_supplement_enabled': True,
    # 前 N 张量产阶段：YOLO 无结果时用 SAM 补充
    'sam_supplement_until_labeled': 500,
    # 达到该 mAP 后停止 SAM 补充（0=不按 mAP 停止）
    'sam_supplement_stop_map': 0.0,
    # 单张图 YOLO 检出数低于此值时尝试 SAM 补充
    'sam_supplement_min_detections': 1,
    'yolo_confidence': 0.5,
    'sam_confidence': 0.45,
    # SAM 冷启动最低识别率（有检出图片占比），低于此值建议改用手动/YOLO 自动标注
    'sam_bootstrap_min_hit_rate': 0.3,
    # 自动标注模型更新历史保留条数（None=读环境变量 AUTO_LABEL_MODEL_HISTORY_MAX）
    'model_history_max': None,
    'train_epochs': 50,
    'train_batch_size': 16,
    'train_imgsz': 640,
    'use_gpu': True,
}


def merge_strategy(raw: dict | None) -> dict[str, Any]:
    cfg = deepcopy(DEFAULT_STRATEGY)
    if raw:
        for k, v in raw.items():
            if v is None:
                continue
            cfg[k] = v
    if cfg.get('model_arch'):
        cfg['model_arch'] = normalize_model_arch(cfg['model_arch'])
    return cfg


def normalize_model_arch(model_arch: str | None) -> str:
    """限定为标准三款内置权重，非法值回退默认。"""
    arch = (model_arch or DEFAULT_MODEL_ARCH).strip()
    if arch in _LEGACY_ARCH_ALIASES:
        arch = _LEGACY_ARCH_ALIASES[arch]
    if arch in OFFICIAL_MODEL_ARCHS:
        return arch
    return DEFAULT_MODEL_ARCH


def ai_project_root() -> str:
    import os
    return os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))


def resolve_model_arch_path(model_arch: str | None) -> str:
    """解析标准 model_arch 为本地绝对路径。"""
    import os

    arch = normalize_model_arch(model_arch)
    if arch.startswith('@AI/'):
        rel = arch[4:].lstrip('/')
        return os.path.join(ai_project_root(), rel)
    return arch


def skip_sam_bootstrap(strategy: dict) -> bool:
    return bool(strategy.get('skip_sam_cold_start'))


def initial_pipeline_phase(strategy: dict) -> str:
    """根据策略决定流水线起始阶段。"""
    if skip_sam_bootstrap(strategy):
        if strategy.get('initial_model_id'):
            return PHASE_YOLO_LABEL
        if strategy.get('auto_train_yolo'):
            return PHASE_TRAINING
        return PHASE_YOLO_LABEL
    return PHASE_BOOTSTRAP_SAM


def should_skip_first_auto_train(strategy: dict, train_round: int) -> bool:
    """跳过 SAM 且已有量产模型时，不自动做首轮训练（仍可迭代训练）。"""
    return (
        train_round == 0
        and skip_sam_bootstrap(strategy)
        and bool(strategy.get('initial_model_id'))
        and not strategy.get('pretrain_model_id')
    )


def parse_pipeline_state(task) -> dict[str, Any]:
    """从 task.pipeline_config 解析流水线状态。"""
    if not task.pipeline_config:
        return {}
    try:
        cfg = json.loads(task.pipeline_config) if isinstance(task.pipeline_config, str) else task.pipeline_config
        return cfg if isinstance(cfg, dict) else {}
    except Exception:
        return {}


def get_strategy(task) -> dict[str, Any]:
    state = parse_pipeline_state(task)
    return merge_strategy(state.get('strategy'))


def get_pipeline_phase(task) -> str:
    state = parse_pipeline_state(task)
    return state.get('pipeline_phase') or PHASE_COLLECTING


def get_counters(task) -> dict[str, int]:
    state = parse_pipeline_state(task)
    return {
        'total_labeled': max(
            int(state.get('total_labeled') or 0),
            int(task.success_count or 0),
        ),
        'sam_labeled': int(state.get('sam_labeled') or 0),
        'yolo_labeled': int(state.get('yolo_labeled') or 0),
        'sam_supplemented': int(state.get('sam_supplemented') or 0),
        'captured_count': int(state.get('captured_count') or 0),
        'train_round': int(state.get('train_round') or 0),
    }


def _require_completed_train_artifact(model, source_label: str) -> tuple[str, str]:
    from db_models import TrainTask

    if not model or not model.model_path:
        raise ValueError(f'{source_label}没有训练权重')
    backing_task = TrainTask.query.filter(
        TrainTask.minio_model_path == model.model_path,
        TrainTask.status == 'completed',
    ).first()
    if not backing_task:
        raise ValueError(
            f'{source_label}不是平台已完成训练任务生成的权重，拒绝加载 .pt'
        )
    return model.model_path, source_label


def resolve_train_pretrained_path(strategy: dict, *, current_model_id: int | None, round_no: int) -> tuple[str | None, str]:
    """
    解析训练用的预训练权重。
    返回 (model_path_or_arch, source_label)。
    """
    from db_models import Model

    if round_no > 1 and current_model_id:
        m = Model.query.get(int(current_model_id))
        if m and m.model_path:
            return _require_completed_train_artifact(
                m,
                f'迭代模型 model_id={current_model_id}',
            )

    for key, label in (
        ('pretrain_model_id', '微调基座'),
    ):
        raw = strategy.get(key)
        if not raw:
            continue
        try:
            mid = int(raw)
        except (TypeError, ValueError):
            continue
        m = Model.query.get(mid)
        if m and m.model_path:
            return _require_completed_train_artifact(m, f'{label} model_id={mid}')

    arch = resolve_model_arch_path(strategy.get('model_arch'))
    display = strategy.get('model_arch') or DEFAULT_MODEL_ARCH
    friendly = display.replace('@AI/', '')
    return arch, f'标准预训练 {friendly}'


def get_current_model_id(task) -> int | None:
    state = parse_pipeline_state(task)
    mid = state.get('current_model_id') or task.model_id
    if mid:
        try:
            return int(mid)
        except (TypeError, ValueError):
            return None
    strategy = get_strategy(task)
    init_id = strategy.get('initial_model_id')
    if init_id:
        try:
            return int(init_id)
        except (TypeError, ValueError):
            return None
    return None


def decide_label_mode(
    task,
    *,
    yolo_detection_count: int = 0,
    force_phase: str | None = None,
) -> str:
    """
    决定当前图片的标注模式。
    返回: 'sam' | 'yolo' | 'yolo_sam_supplement' | 'skip'
    """
    state = parse_pipeline_state(task)
    if state.get('pipeline_phase') in (PHASE_PAUSED, PHASE_CANCELLED):
        return 'skip'

    strategy = get_strategy(task)
    counters = get_counters(task)
    total = counters['total_labeled']
    phase = force_phase or get_pipeline_phase(task)
    model_id = get_current_model_id(task)
    bootstrap = int(strategy['bootstrap_sam_limit'])

    # 阶段一：SAM 冷启动（可与量产模型并存：前 N 张仍 SAM）
    if phase in (PHASE_COLLECTING, PHASE_BOOTSTRAP_SAM):
        if skip_sam_bootstrap(strategy):
            return 'yolo' if model_id else 'skip'
        if total < bootstrap:
            return 'sam'
        return 'yolo' if model_id else 'skip'

    # 训练阶段不标注
    if phase == PHASE_TRAINING:
        return 'skip'

    # 量产 / 迭代阶段
    if phase in (PHASE_YOLO_LABEL, PHASE_ITERATE, PHASE_COLLECTING):
        if not model_id:
            if not skip_sam_bootstrap(strategy) and total < bootstrap:
                return 'sam'
            return 'skip'

        if not strategy.get('sam_supplement_enabled'):
            return 'yolo'

        supplement_until = int(strategy.get('sam_supplement_until_labeled') or 0)
        min_dets = int(strategy.get('sam_supplement_min_detections') or 1)

        # 超过补充窗口且（可选）mAP 达标 → 仅 YOLO
        if supplement_until > 0 and total >= supplement_until:
            stop_map = float(strategy.get('sam_supplement_stop_map') or 0)
            current_map = float(state.get('current_map') or 0)
            if stop_map > 0 and current_map >= stop_map:
                return 'yolo'
            if supplement_until > 0 and total >= supplement_until * 2:
                return 'yolo'

        if yolo_detection_count < min_dets:
            return 'yolo_sam_supplement'
        return 'yolo'

    return 'skip'


def should_trigger_training(task) -> bool:
    """是否应在当前进度触发 YOLO 训练。"""
    state = parse_pipeline_state(task)
    strategy = get_strategy(task)
    counters = get_counters(task)
    total = counters['total_labeled']
    phase = get_pipeline_phase(task)
    bootstrap = int(strategy['bootstrap_sam_limit'])

    if not strategy.get('auto_train_yolo'):
        return False
    if should_skip_first_auto_train(strategy, counters['train_round']):
        return False
    if phase == PHASE_TRAINING:
        return False

    if not skip_sam_bootstrap(strategy) and phase in (PHASE_COLLECTING, PHASE_BOOTSTRAP_SAM) and total >= bootstrap:
        return True

    iterate_every = int(strategy.get('yolo_iterate_every') or 0)
    if iterate_every > 0 and phase in (PHASE_YOLO_LABEL, PHASE_ITERATE):
        if total > 0 and total % iterate_every == 0:
            last_trigger = int(state.get('last_iterate_at_labeled') or 0)
            if last_trigger != total:
                return True
    return False


def advance_phase_after_bootstrap(task) -> str:
    """SAM 冷启动完成后进入的下一阶段。"""
    strategy = get_strategy(task)
    if strategy.get('auto_train_yolo'):
        return PHASE_TRAINING
    return PHASE_YOLO_LABEL


def phase_display_name(phase: str) -> str:
    names = {
        PHASE_COLLECTING: '采集中',
        PHASE_BOOTSTRAP_SAM: 'SAM 冷启动',
        PHASE_TRAINING: 'YOLO 训练中',
        PHASE_YOLO_LABEL: 'YOLO 量产标注',
        PHASE_ITERATE: '迭代优化',
        PHASE_PACKAGING: '打包导出',
        PHASE_DONE: '已完成',
        PHASE_PAUSED: '已暂停',
        PHASE_CANCELLED: '已取消',
    }
    return names.get(phase, phase)
