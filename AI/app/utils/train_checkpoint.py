import numbers
import os
import shutil
import uuid
from dataclasses import dataclass
from functools import lru_cache
from typing import Callable


CHECKPOINT_ACTION_RESUME = 'resume'
CHECKPOINT_ACTION_FINALIZE = 'finalize'
CHECKPOINT_ACTION_REJECT = 'reject'


@dataclass(frozen=True)
class YoloCheckpointState:
    epoch: int | None
    optimizer_present: bool
    configured_epochs: int | None
    resumable: bool
    weights_only: bool


@dataclass(frozen=True)
class PreparedYoloCheckpoint:
    path: str
    action: str
    state: YoloCheckpointState


def _default_checkpoint_loader(checkpoint_path: str):
    import torch

    return torch.load(
        checkpoint_path,
        map_location='cpu',
        weights_only=True,
    )


def _normalize_positive_int(value) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, numbers.Integral):
        normalized = int(value)
        return normalized if normalized > 0 else None
    try:
        normalized = int(str(value).strip())
    except (TypeError, ValueError):
        return None
    return normalized if normalized > 0 else None


def inspect_yolo_checkpoint(
    checkpoint_path: str,
    loader: Callable | None = None,
) -> YoloCheckpointState:
    checkpoint_loader = loader or _default_checkpoint_loader
    checkpoint = checkpoint_loader(checkpoint_path)
    if not isinstance(checkpoint, dict):
        return YoloCheckpointState(
            epoch=None,
            optimizer_present=False,
            configured_epochs=None,
            resumable=False,
            weights_only=False,
        )

    raw_epoch = checkpoint.get('epoch')
    epoch = int(raw_epoch) if isinstance(raw_epoch, numbers.Integral) else None
    optimizer_present = checkpoint.get('optimizer') is not None
    train_args = checkpoint.get('train_args') or checkpoint.get('args') or {}
    if not isinstance(train_args, dict):
        train_args = vars(train_args) if hasattr(train_args, '__dict__') else {}
    configured_epochs = _normalize_positive_int(train_args.get('epochs'))
    has_model_weights = checkpoint.get('model') is not None or checkpoint.get('ema') is not None
    resumable = epoch is not None and epoch >= 0 and optimizer_present
    weights_only = has_model_weights and not resumable and not optimizer_present
    return YoloCheckpointState(
        epoch=epoch,
        optimizer_present=optimizer_present,
        configured_epochs=configured_epochs,
        resumable=resumable,
        weights_only=weights_only,
    )


@lru_cache(maxsize=64)
def _inspect_yolo_checkpoint_cached(
    checkpoint_path: str,
    modified_ns: int,
    file_size: int,
) -> YoloCheckpointState:
    del modified_ns, file_size
    return inspect_yolo_checkpoint(checkpoint_path)


def is_yolo_checkpoint_resumable(checkpoint_path: str) -> bool:
    try:
        file_stat = os.stat(checkpoint_path)
        state = _inspect_yolo_checkpoint_cached(
            os.path.abspath(checkpoint_path),
            file_stat.st_mtime_ns,
            file_stat.st_size,
        )
        return state.resumable
    except Exception:
        return False


def find_yolo_checkpoint(model_dir: str) -> str | None:
    if not model_dir or not os.path.isdir(model_dir):
        return None
    candidates = []
    for name in os.listdir(model_dir):
        if not name.startswith('train_results'):
            continue
        result_dir = os.path.join(model_dir, name)
        checkpoint_path = os.path.join(result_dir, 'weights', 'last.pt')
        if os.path.isfile(checkpoint_path):
            candidates.append(checkpoint_path)
    if not candidates:
        return None
    newest = max(candidates, key=lambda path: (os.path.getmtime(path), path))
    return os.path.abspath(newest)


def resolve_yolo_checkpoint_action(
    state: YoloCheckpointState,
    completed_epochs: int,
    target_epochs: int,
) -> str:
    if state.resumable:
        return CHECKPOINT_ACTION_RESUME

    completed = max(0, int(completed_epochs or 0))
    target = max(0, int(target_epochs or 0))
    progress_is_final = target >= 2 and completed >= target - 1
    checkpoint_matches_run = state.configured_epochs == target
    is_final_weights = state.weights_only and state.epoch == -1
    if is_final_weights and progress_is_final and checkpoint_matches_run:
        return CHECKPOINT_ACTION_FINALIZE
    return CHECKPOINT_ACTION_REJECT


def stage_yolo_checkpoint(
    source_path: str,
    staging_root: str,
    task_id: int,
) -> str:
    source = os.path.abspath(source_path)
    task_dir = os.path.abspath(os.path.join(staging_root, f'train_{int(task_id)}'))
    destination = os.path.join(task_dir, 'last.pt')
    os.makedirs(task_dir, exist_ok=True)

    if os.path.exists(destination):
        try:
            if os.path.samefile(source, destination):
                return destination
        except OSError:
            pass

    temp_destination = f'{destination}.tmp-{uuid.uuid4().hex}'
    try:
        shutil.copy2(source, temp_destination)
        os.replace(temp_destination, destination)
    finally:
        try:
            os.remove(temp_destination)
        except OSError:
            pass
    return destination


def prepare_yolo_resume_checkpoint(
    source_path: str,
    staging_root: str,
    task_id: int,
    completed_epochs: int,
    target_epochs: int,
    loader: Callable | None = None,
) -> PreparedYoloCheckpoint:
    staged_path = stage_yolo_checkpoint(source_path, staging_root, task_id)
    state = inspect_yolo_checkpoint(staged_path, loader=loader)
    return PreparedYoloCheckpoint(
        path=staged_path,
        action=resolve_yolo_checkpoint_action(
            state,
            completed_epochs=completed_epochs,
            target_epochs=target_epochs,
        ),
        state=state,
    )


def cleanup_staged_checkpoint(checkpoint_path: str, staging_root: str) -> None:
    if not checkpoint_path:
        return
    root = os.path.abspath(staging_root)
    path = os.path.abspath(checkpoint_path)
    try:
        if os.path.commonpath((root, path)) != root:
            return
    except ValueError:
        return

    try:
        os.remove(path)
    except OSError:
        return
    try:
        os.rmdir(os.path.dirname(path))
    except OSError:
        pass
