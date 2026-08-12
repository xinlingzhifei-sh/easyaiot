"""
车牌算法独立训练/推理/版本管理蓝图
"""
import json
import logging
import os
import shutil
import tempfile
import threading
import time
import traceback
import uuid
from datetime import datetime
from urllib.parse import parse_qs, urlparse

import torch
import yaml
from flask import Blueprint, current_app, jsonify, request
from ultralytics import YOLO

from app.services.minio_service import ModelService
from app.utils.model_upload_security import require_official_pretrained_model
from db_models import (
    db,
    PlateAlgorithmVersion,
    PlateDataset,
    PlateTrainTask,
    PlateInferenceTask,
)

plate_bp = Blueprint('plate', __name__)
logger = logging.getLogger(__name__)

# 训练运行时状态（内存）
_plate_train_runtime = {}

# 推理模型缓存
_plate_model_cache = {}

# 车牌模型相关 MinIO 存储桶（统一 plate 前缀）
PLATE_MODEL_BUCKET = 'plate-models'
PLATE_TRAIN_RESULTS_BUCKET = 'plate-train-results'
PLATE_TRAIN_LOGS_BUCKET = 'plate-train-logs'
PLATE_INFERENCE_RESULTS_BUCKET = 'plate-inference-results'
# 兼容历史命名：旧版训练日志桶
PLATE_TRAIN_LOGS_LEGACY_BUCKET = 'plate-logs'


def _parse_minio_url(url: str):
    """解析MinIO下载URL，返回(bucket, object_key)"""
    try:
        parsed = urlparse(url)
        path_parts = parsed.path.split('/')
        if len(path_parts) >= 5 and path_parts[3] == 'buckets':
            bucket_name = path_parts[4]
        else:
            return None, None
        query_params = parse_qs(parsed.query)
        object_key = query_params.get('prefix', [None])[0]
        return bucket_name, object_key
    except Exception:
        return None, None


def _build_minio_download_url(bucket_name: str, object_key: str):
    return f"/api/v1/buckets/{bucket_name}/objects/download?prefix={object_key}"


def _append_train_log(task: PlateTrainTask, message: str, progress: int = None):
    log_line = f"[{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] {message}"
    task.train_log = (task.train_log or '') + log_line + '\n'
    if progress is not None:
        task.progress = max(0, min(100, int(progress)))
    db.session.commit()
    logger.info(log_line)


def _upload_train_log_with_fallback(log_file: str, version_tag: str, task_id: int):
    """
    训练日志优先上传到新桶 plate-train-logs。
    若新桶不可用，回退到历史桶 plate-logs。
    """
    train_log_object_key = f"plate/train-logs/{version_tag}/task_{task_id}.log"

    upload_ok, upload_err = ModelService.upload_to_minio(
        PLATE_TRAIN_LOGS_BUCKET,
        train_log_object_key,
        log_file
    )
    if upload_ok:
        return PLATE_TRAIN_LOGS_BUCKET, train_log_object_key

    legacy_ok, legacy_err = ModelService.upload_to_minio(
        PLATE_TRAIN_LOGS_LEGACY_BUCKET,
        train_log_object_key,
        log_file
    )
    if legacy_ok:
        logger.warning(
            "训练日志上传到新桶失败，已回退历史桶: %s, 错误: %s",
            PLATE_TRAIN_LOGS_BUCKET,
            upload_err
        )
        return PLATE_TRAIN_LOGS_LEGACY_BUCKET, train_log_object_key

    logger.warning(
        "训练日志上传失败，新/旧桶均不可用: %s, %s, errors: %s | %s",
        PLATE_TRAIN_LOGS_BUCKET,
        PLATE_TRAIN_LOGS_LEGACY_BUCKET,
        upload_err,
        legacy_err
    )
    return None, None


def _resolve_split_path(raw_path: str, split_name: str, yaml_dir: str, dataset_root: str):
    if not raw_path:
        return None

    normalized_raw = str(raw_path).replace('\\', '/')
    candidate_bases = [
        yaml_dir,
        dataset_root,
        os.path.dirname(dataset_root),
    ]

    for base in candidate_bases:
        candidate = os.path.normpath(os.path.join(base, normalized_raw))
        if os.path.exists(candidate):
            return os.path.abspath(candidate)

    split_alias = {
        'train': ['train'],
        'val': ['val', 'valid', 'validation'],
        'test': ['test'],
    }
    for alias in split_alias.get(split_name, [split_name]):
        for base in candidate_bases:
            img_candidate = os.path.join(base, alias, 'images')
            if os.path.exists(img_candidate):
                return os.path.abspath(img_candidate)

    return None


def _find_first_data_yaml(root_dir: str):
    for root, _, files in os.walk(root_dir):
        for file_name in files:
            if file_name.lower() == 'data.yaml':
                return os.path.join(root, file_name)
    return None


def _normalize_dataset_yaml(dataset_root: str, output_dir: str = None):
    """
    适配YOLO数据集结构并生成标准化data.yaml（绝对路径）
    兼容Roboflow导出的 train/valid/test 结构。
    """
    data_yaml = _find_first_data_yaml(dataset_root)
    if not data_yaml:
        raise ValueError('未找到 data.yaml，无法开始训练')

    with open(data_yaml, 'r', encoding='utf-8') as f:
        cfg = yaml.safe_load(f) or {}

    yaml_dir = os.path.dirname(data_yaml)
    train_path = _resolve_split_path(cfg.get('train'), 'train', yaml_dir, dataset_root)
    val_path = _resolve_split_path(cfg.get('val'), 'val', yaml_dir, dataset_root)
    test_path = _resolve_split_path(cfg.get('test'), 'test', yaml_dir, dataset_root) if cfg.get('test') else None

    if not train_path:
        raise ValueError('数据集 train 路径无效，请检查 data.yaml 或目录结构')
    if not val_path:
        raise ValueError('数据集 val/valid 路径无效，请检查 data.yaml 或目录结构')

    normalized_cfg = {
        'train': train_path,
        'val': val_path,
        'nc': cfg.get('nc', 1),
        'names': cfg.get('names', ['License_Plate']),
    }
    if test_path:
        normalized_cfg['test'] = test_path

    target_dir = output_dir or dataset_root
    os.makedirs(target_dir, exist_ok=True)
    normalized_yaml_path = os.path.join(target_dir, 'data.normalized.yaml')
    with open(normalized_yaml_path, 'w', encoding='utf-8') as f:
        yaml.safe_dump(normalized_cfg, f, allow_unicode=True, sort_keys=False)

    return normalized_yaml_path


def _plate_data_root():
    return os.path.join(
        os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')),
        'data',
        'plate'
    )


def _prepare_dataset_source_to_dir(dataset_source: str, target_dir: str):
    """
    支持三种输入：
    1) 本地目录（包含data.yaml）
    2) 本地zip
    3) MinIO下载URL（zip或目录prefix）
    """
    os.makedirs(target_dir, exist_ok=True)

    if os.path.exists(dataset_source):
        source_abs = os.path.abspath(dataset_source)
        if os.path.isdir(source_abs):
            return source_abs
        if source_abs.lower().endswith('.zip'):
            if not ModelService.extract_zip(source_abs, target_dir):
                raise ValueError(f'本地数据集zip解压失败: {source_abs}')
            return target_dir
        raise ValueError('本地数据集路径仅支持目录或zip文件')

    bucket_name, object_key = _parse_minio_url(dataset_source)
    if not bucket_name or not object_key:
        raise ValueError('dataset_source 不是有效路径，也不是可解析的MinIO下载URL')

    local_zip_path = os.path.join(target_dir, 'dataset.zip')
    if object_key.lower().endswith('.zip'):
        success, error_msg = ModelService.download_from_minio(
            bucket_name=bucket_name,
            object_name=object_key,
            destination_path=local_zip_path
        )
        if not success:
            raise RuntimeError(f'从MinIO下载数据集zip失败: {error_msg or ""}')
    else:
        success, error_msg = ModelService.download_directory_from_minio(
            bucket_name=bucket_name,
            object_prefix=object_key,
            destination_zip_path=local_zip_path
        )
        if not success:
            raise RuntimeError(f'从MinIO下载数据集目录失败: {error_msg or ""}')

    if not ModelService.extract_zip(local_zip_path, target_dir):
        raise RuntimeError('下载后的数据集zip解压失败')
    if os.path.exists(local_zip_path):
        os.remove(local_zip_path)
    return target_dir


def _prepare_dataset(task: PlateTrainTask, dataset_source: str, workspace_root: str):
    dataset_dir = os.path.join(workspace_root, 'dataset')
    extracted_root = _prepare_dataset_source_to_dir(dataset_source, dataset_dir)
    normalized_yaml_path = _normalize_dataset_yaml(extracted_root, output_dir=workspace_root)
    task.dataset_local_path = extracted_root
    task.normalized_data_yaml = normalized_yaml_path
    db.session.commit()
    return normalized_yaml_path, extracted_root


def _load_normalized_dataset_cfg(dataset_source: str, temp_dir: str):
    extracted_root = _prepare_dataset_source_to_dir(dataset_source, temp_dir)
    normalized_yaml = _normalize_dataset_yaml(extracted_root, output_dir=temp_dir)
    with open(normalized_yaml, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f) or {}


def _copy_split_with_labels(src_img_dir: str, dst_img_dir: str, dst_label_dir: str, source_tag: str):
    if not src_img_dir or not os.path.exists(src_img_dir):
        return 0

    src_label_dir = os.path.join(os.path.dirname(src_img_dir), 'labels')
    copied = 0
    for file_name in os.listdir(src_img_dir):
        src_img_path = os.path.join(src_img_dir, file_name)
        if not os.path.isfile(src_img_path):
            continue

        ext = os.path.splitext(file_name)[1]
        target_name = f"{source_tag}_{copied:06d}{ext}"
        shutil.copy2(src_img_path, os.path.join(dst_img_dir, target_name))

        label_stem = os.path.splitext(file_name)[0]
        src_label_path = os.path.join(src_label_dir, f'{label_stem}.txt')
        if os.path.exists(src_label_path):
            shutil.copy2(src_label_path, os.path.join(dst_label_dir, f"{source_tag}_{copied:06d}.txt"))

        copied += 1
    return copied


def _merge_plate_datasets(dataset_entities):
    if not dataset_entities:
        raise ValueError('缺少待合并数据集')

    merged_root = os.path.join(_plate_data_root(), 'datasets', f"merged_{datetime.now().strftime('%Y%m%d%H%M%S')}")
    merged_splits = {
        'train': {
            'images': os.path.join(merged_root, 'train', 'images'),
            'labels': os.path.join(merged_root, 'train', 'labels')
        },
        'val': {
            'images': os.path.join(merged_root, 'val', 'images'),
            'labels': os.path.join(merged_root, 'val', 'labels')
        },
        'test': {
            'images': os.path.join(merged_root, 'test', 'images'),
            'labels': os.path.join(merged_root, 'test', 'labels')
        }
    }
    for split_dirs in merged_splits.values():
        os.makedirs(split_dirs['images'], exist_ok=True)
        os.makedirs(split_dirs['labels'], exist_ok=True)

    expected_nc = None
    expected_names = None
    copied_counter = {'train': 0, 'val': 0, 'test': 0}

    for idx, dataset in enumerate(dataset_entities, start=1):
        temp_extract_dir = tempfile.mkdtemp(prefix=f"plate_ds_merge_{dataset.id}_")
        try:
            cfg = _load_normalized_dataset_cfg(dataset.dataset_source, temp_extract_dir)
            nc = int(cfg.get('nc', 1))
            names = cfg.get('names', ['License_Plate'])
            if expected_nc is None:
                expected_nc = nc
                expected_names = names
            elif expected_nc != nc or expected_names != names:
                raise ValueError(f'数据集标签定义不一致，无法合并: {dataset.name}(ID:{dataset.id})')

            source_tag = f"ds{dataset.id}_{idx}"
            copied_counter['train'] += _copy_split_with_labels(
                cfg.get('train'),
                merged_splits['train']['images'],
                merged_splits['train']['labels'],
                source_tag
            )
            copied_counter['val'] += _copy_split_with_labels(
                cfg.get('val'),
                merged_splits['val']['images'],
                merged_splits['val']['labels'],
                source_tag
            )
            copied_counter['test'] += _copy_split_with_labels(
                cfg.get('test'),
                merged_splits['test']['images'],
                merged_splits['test']['labels'],
                source_tag
            )
        finally:
            shutil.rmtree(temp_extract_dir, ignore_errors=True)

    if copied_counter['train'] <= 0 or copied_counter['val'] <= 0:
        raise ValueError('合并后数据集缺少train/val样本，无法用于训练')

    merged_yaml = {
        'train': merged_splits['train']['images'],
        'val': merged_splits['val']['images'],
        'nc': expected_nc if expected_nc is not None else 1,
        'names': expected_names if expected_names is not None else ['License_Plate'],
    }
    if copied_counter['test'] > 0:
        merged_yaml['test'] = merged_splits['test']['images']

    os.makedirs(merged_root, exist_ok=True)
    with open(os.path.join(merged_root, 'data.yaml'), 'w', encoding='utf-8') as f:
        yaml.safe_dump(merged_yaml, f, allow_unicode=True, sort_keys=False)

    return merged_root, copied_counter


def _get_device(use_gpu: bool):
    if use_gpu and torch.cuda.is_available():
        return 0
    return 'cpu'


def _upload_training_artifacts(version: PlateAlgorithmVersion, task: PlateTrainTask, train_output_dir: str):
    weights_path = os.path.join(train_output_dir, 'weights', 'best.pt')
    if not os.path.exists(weights_path):
        raise FileNotFoundError(f'训练完成但未找到 best.pt: {weights_path}')

    version_tag = version.version.replace('/', '_')

    model_obj = f"plate/models/{version_tag}/task_{task.id}/best.pt"
    success, error_msg = ModelService.upload_to_minio(PLATE_MODEL_BUCKET, model_obj, weights_path)
    if not success:
        raise RuntimeError(f'上传best.pt到MinIO失败: {error_msg or ""}')
    model_url = _build_minio_download_url(PLATE_MODEL_BUCKET, model_obj)

    csv_path = os.path.join(train_output_dir, 'results.csv')
    csv_url = None
    if os.path.exists(csv_path):
        csv_obj = f"plate/train-results/{version_tag}/task_{task.id}/results.csv"
        csv_ok, csv_err = ModelService.upload_to_minio(PLATE_TRAIN_RESULTS_BUCKET, csv_obj, csv_path)
        if csv_ok:
            csv_url = _build_minio_download_url(PLATE_TRAIN_RESULTS_BUCKET, csv_obj)
        else:
            logger.warning("上传results.csv失败: %s", csv_err)

    png_path = os.path.join(train_output_dir, 'results.png')
    png_url = None
    if os.path.exists(png_path):
        png_obj = f"plate/train-results/{version_tag}/task_{task.id}/results.png"
        png_ok, png_err = ModelService.upload_to_minio(PLATE_TRAIN_RESULTS_BUCKET, png_obj, png_path)
        if png_ok:
            png_url = _build_minio_download_url(PLATE_TRAIN_RESULTS_BUCKET, png_obj)
        else:
            logger.warning("上传results.png失败: %s", png_err)

    task.minio_model_path = model_url
    task.metrics_path = csv_url
    task.train_results_path = png_url
    version.model_path = model_url
    version.metrics_path = csv_url
    version.train_results_path = png_url
    version.status = 'draft'
    db.session.commit()


def _train_worker(app, task_id: int):
    with app.app_context():
        task = PlateTrainTask.query.get(task_id)
        if not task:
            return
        version = PlateAlgorithmVersion.query.get(task.version_id) if task.version_id else None
        if not version:
            task.status = 'failed'
            task.error_message = '训练任务缺少版本信息'
            task.end_time = datetime.utcnow()
            db.session.commit()
            return

        runtime_state = _plate_train_runtime.get(task_id, {'stop_requested': False})
        try:
            task.status = 'running'
            task.progress = 1
            db.session.commit()
            _append_train_log(task, f'开始训练车牌算法版本: {version.version}', 1)

            params = json.loads(task.hyperparameters or '{}')
            model_arch = require_official_pretrained_model(
                params.get('model_arch', version.base_model or 'yolo11n.pt')
            )
            epochs = int(params.get('epochs', 100))
            imgsz = int(params.get('imgsz', 640))
            batch = int(params.get('batch_size', 16))
            workers = int(params.get('workers', 8))
            use_gpu = bool(params.get('use_gpu', True))

            workspace_root = os.path.join(
                os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')),
                'data',
                'plate',
                f'train_task_{task.id}'
            )
            os.makedirs(workspace_root, exist_ok=True)

            _append_train_log(task, '准备数据集...', 5)
            normalized_yaml, _ = _prepare_dataset(task, task.dataset_source, workspace_root)
            _append_train_log(task, f'数据集准备完成，配置文件: {normalized_yaml}', 15)

            if runtime_state.get('stop_requested'):
                raise RuntimeError('训练已被用户停止')

            _append_train_log(task, f'加载模型: {model_arch}', 20)
            yolo_model = YOLO(model_arch)
            device = _get_device(use_gpu=use_gpu)

            def on_epoch_end(trainer):
                if runtime_state.get('stop_requested'):
                    raise RuntimeError('训练已被用户停止')
                total_epochs = max(1, int(getattr(trainer, 'epochs', epochs)))
                now_epoch = int(getattr(trainer, 'epoch', 0)) + 1
                progress = min(95, 20 + int(now_epoch * 70 / total_epochs))
                task.progress = progress
                db.session.commit()

            yolo_model.add_callback('on_train_epoch_end', on_epoch_end)

            _append_train_log(task, f'开始YOLO11训练: epochs={epochs}, imgsz={imgsz}, batch={batch}', 25)
            yolo_model.train(
                data=normalized_yaml,
                epochs=epochs,
                imgsz=imgsz,
                batch=batch,
                workers=workers,
                device=device,
                project=workspace_root,
                name='train_results',
                exist_ok=True
            )

            if runtime_state.get('stop_requested'):
                raise RuntimeError('训练已被用户停止')

            train_output_dir = os.path.join(workspace_root, 'train_results')
            _append_train_log(task, '训练完成，开始上传产物到MinIO...', 96)
            _upload_training_artifacts(version, task, train_output_dir)

            # 上传训练日志
            log_file = os.path.join(workspace_root, 'train.log')
            with open(log_file, 'w', encoding='utf-8') as f:
                f.write(task.train_log or '')
            version_tag = version.version.replace('/', '_')
            log_bucket, log_obj = _upload_train_log_with_fallback(log_file, version_tag, task.id)
            if log_bucket and log_obj:
                _append_train_log(task, f'训练日志已上传: {_build_minio_download_url(log_bucket, log_obj)}', 98)

            task.status = 'completed'
            task.progress = 100
            task.end_time = datetime.utcnow()
            db.session.commit()
            _append_train_log(task, '训练任务完成', 100)
        except Exception as e:
            err_msg = str(e)
            stop_requested = runtime_state.get('stop_requested', False)
            task.status = 'stopped' if stop_requested else 'failed'
            task.error_message = err_msg
            task.end_time = datetime.utcnow()
            db.session.commit()
            _append_train_log(task, f'训练任务结束: {err_msg}')
            logger.error("车牌训练任务失败: %s\n%s", err_msg, traceback.format_exc())
        finally:
            _plate_train_runtime.pop(task_id, None)


def _get_active_or_latest_version(version_id: int = None):
    if version_id:
        return PlateAlgorithmVersion.query.get(version_id)
    active = PlateAlgorithmVersion.query.filter_by(is_active=True).order_by(
        PlateAlgorithmVersion.updated_at.desc()
    ).first()
    if active:
        return active
    return PlateAlgorithmVersion.query.order_by(PlateAlgorithmVersion.created_at.desc()).first()


def _get_yolo_model_for_version(version: PlateAlgorithmVersion):
    if not version or not version.model_path:
        raise ValueError('当前版本尚未绑定可用模型')

    cache_key = f"{version.id}:{version.model_path}"
    if cache_key in _plate_model_cache:
        return _plate_model_cache[cache_key]

    model_dir = os.path.join(
        os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')),
        'data',
        'plate',
        'inference_models',
        f'version_{version.id}'
    )
    os.makedirs(model_dir, exist_ok=True)
    local_model_path = os.path.join(model_dir, 'best.pt')

    # 优先使用已存在的本地模型，避免每次推理都重复下载。
    if os.path.exists(local_model_path):
        model = YOLO(local_model_path)
        _plate_model_cache[cache_key] = model
        return model

    model_path = (version.model_path or '').strip()
    if not model_path:
        raise ValueError('版本模型路径不可用，请检查 model_path')

    if os.path.exists(model_path):
        local_model_path = os.path.abspath(model_path)
    else:
        bucket_name, object_key = _parse_minio_url(model_path)

        # 兼容历史路径格式：
        # 1) bucket/object_key
        # 2) 仅 object_key（默认从 plate-models 桶下载）
        if not (bucket_name and object_key):
            normalized_path = model_path.lstrip('/')
            if '/' in normalized_path:
                prefix, suffix = normalized_path.split('/', 1)
                if prefix and suffix and prefix in {
                    PLATE_MODEL_BUCKET,
                    PLATE_TRAIN_RESULTS_BUCKET,
                    PLATE_INFERENCE_RESULTS_BUCKET,
                    PLATE_TRAIN_LOGS_BUCKET,
                    PLATE_TRAIN_LOGS_LEGACY_BUCKET,
                    'models',
                    'model-train',
                    'inference-results',
                }:
                    bucket_name, object_key = prefix, suffix
                elif suffix:
                    bucket_name, object_key = PLATE_MODEL_BUCKET, normalized_path
            elif normalized_path:
                bucket_name, object_key = PLATE_MODEL_BUCKET, normalized_path

        if bucket_name and object_key:
            ok, err = ModelService.download_from_minio(bucket_name, object_key, local_model_path)
            if not ok:
                raise RuntimeError(f'从MinIO下载模型失败: {err or ""}')
        else:
            raise ValueError('版本模型路径不可用，请检查 model_path')

    model = YOLO(local_model_path)
    _plate_model_cache[cache_key] = model
    return model


def _prepare_inference_input(temp_dir: str, input_source: str = None):
    if 'file' in request.files and request.files['file'].filename:
        upload = request.files['file']
        ext = os.path.splitext(upload.filename)[1] or '.jpg'
        local_path = os.path.join(temp_dir, f'input{ext}')
        upload.save(local_path)
        return local_path, upload.filename

    if not input_source:
        raise ValueError('请上传 file 或提供 input_source')

    if os.path.exists(input_source):
        return os.path.abspath(input_source), input_source

    bucket_name, object_key = _parse_minio_url(input_source)
    if not bucket_name or not object_key:
        raise ValueError('input_source 不是本地路径，也不是有效的MinIO下载URL')

    ext = os.path.splitext(object_key)[1] or '.jpg'
    local_path = os.path.join(temp_dir, f'input{ext}')
    ok, err = ModelService.download_from_minio(bucket_name, object_key, local_path)
    if not ok:
        raise RuntimeError(f'下载推理输入失败: {err or ""}')
    return local_path, input_source


@plate_bp.route('/dataset/list', methods=['GET'])
def list_plate_datasets():
    page_no = int(request.args.get('pageNo', 1))
    page_size = int(request.args.get('pageSize', 10))
    keyword = (request.args.get('keyword') or '').strip()

    query = PlateDataset.query
    if keyword:
        query = query.filter(PlateDataset.name.like(f'%{keyword}%'))
    query = query.order_by(PlateDataset.created_at.desc())

    pagination = query.paginate(page=page_no, per_page=page_size, error_out=False)
    return jsonify({
        'code': 0,
        'msg': 'success',
        'data': [d.to_dict() for d in pagination.items],
        'total': pagination.total
    })


@plate_bp.route('/dataset/<int:dataset_id>', methods=['GET'])
def get_plate_dataset(dataset_id):
    dataset = PlateDataset.query.get_or_404(dataset_id)
    return jsonify({'code': 0, 'msg': 'success', 'data': dataset.to_dict()})


@plate_bp.route('/dataset/create', methods=['POST'])
def create_plate_dataset():
    data = request.get_json() or {}
    name = (data.get('name') or '').strip()
    dataset_source = (data.get('dataset_source') or '').strip()
    if not name:
        return jsonify({'code': 400, 'msg': 'name不能为空'}), 400
    if not dataset_source:
        return jsonify({'code': 400, 'msg': 'dataset_source不能为空'}), 400
    if PlateDataset.query.filter_by(name=name).first():
        return jsonify({'code': 400, 'msg': f'数据集{name}已存在'}), 400

    entity = PlateDataset(
        name=name,
        description=data.get('description'),
        dataset_source=dataset_source,
        source_type='custom',
        status='ready'
    )
    db.session.add(entity)
    db.session.commit()
    return jsonify({'code': 0, 'msg': '车牌数据集创建成功', 'data': entity.to_dict()})


@plate_bp.route('/dataset/<int:dataset_id>/update', methods=['PUT'])
def update_plate_dataset(dataset_id):
    dataset = PlateDataset.query.get_or_404(dataset_id)
    data = request.get_json() or {}

    if 'name' in data:
        name = (data.get('name') or '').strip()
        if not name:
            return jsonify({'code': 400, 'msg': 'name不能为空'}), 400
        exists = PlateDataset.query.filter(
            PlateDataset.name == name,
            PlateDataset.id != dataset_id
        ).first()
        if exists:
            return jsonify({'code': 400, 'msg': f'数据集{name}已存在'}), 400
        dataset.name = name

    if 'description' in data:
        dataset.description = data.get('description')

    if 'dataset_source' in data:
        dataset_source = (data.get('dataset_source') or '').strip()
        if not dataset_source:
            return jsonify({'code': 400, 'msg': 'dataset_source不能为空'}), 400
        running_tasks = PlateTrainTask.query.filter(
            PlateTrainTask.dataset_source == dataset.dataset_source,
            PlateTrainTask.status.in_(['preparing', 'running', 'stopping'])
        ).count()
        if running_tasks > 0:
            return jsonify({'code': 400, 'msg': '该数据集存在运行中的训练任务，暂不允许修改dataset_source'}), 400
        dataset.dataset_source = dataset_source

    if 'status' in data and data.get('status') in {'ready', 'archived'}:
        dataset.status = data.get('status')

    db.session.commit()
    return jsonify({'code': 0, 'msg': '数据集更新成功', 'data': dataset.to_dict()})


@plate_bp.route('/dataset/<int:dataset_id>/delete', methods=['POST'])
def delete_plate_dataset(dataset_id):
    dataset = PlateDataset.query.get_or_404(dataset_id)
    running_task = PlateTrainTask.query.filter(
        PlateTrainTask.dataset_source == dataset.dataset_source,
        PlateTrainTask.status.in_(['preparing', 'running', 'stopping'])
    ).first()
    if running_task:
        return jsonify({'code': 400, 'msg': '该数据集存在运行中的训练任务，无法删除'}), 400

    if dataset.source_type == 'merged' and os.path.isdir(dataset.dataset_source):
        try:
            shutil.rmtree(dataset.dataset_source, ignore_errors=True)
        except Exception:
            logger.warning('删除合并数据集目录失败: %s', dataset.dataset_source)

    db.session.delete(dataset)
    db.session.commit()
    return jsonify({'code': 0, 'msg': '数据集删除成功'})


@plate_bp.route('/dataset/merge', methods=['POST'])
def merge_plate_datasets():
    data = request.get_json() or {}
    name = (data.get('name') or '').strip()
    dataset_ids = data.get('dataset_ids') or []
    if not name:
        return jsonify({'code': 400, 'msg': 'name不能为空'}), 400
    if not isinstance(dataset_ids, list) or len(dataset_ids) < 2:
        return jsonify({'code': 400, 'msg': 'dataset_ids至少需要2个数据集ID'}), 400
    if PlateDataset.query.filter_by(name=name).first():
        return jsonify({'code': 400, 'msg': f'数据集{name}已存在'}), 400

    try:
        dataset_ids = [int(i) for i in dataset_ids]
    except Exception:
        return jsonify({'code': 400, 'msg': 'dataset_ids必须为整数列表'}), 400

    unique_ids = list(dict.fromkeys(dataset_ids))
    datasets = PlateDataset.query.filter(PlateDataset.id.in_(unique_ids)).all()
    if len(datasets) != len(unique_ids):
        return jsonify({'code': 400, 'msg': '部分dataset_id不存在'}), 400

    id_to_dataset = {d.id: d for d in datasets}
    ordered_datasets = [id_to_dataset[i] for i in unique_ids]

    try:
        merged_source, merge_stats = _merge_plate_datasets(ordered_datasets)
    except Exception as e:
        return jsonify({'code': 400, 'msg': f'合并数据集失败: {str(e)}'}), 400

    entity = PlateDataset(
        name=name,
        description=data.get('description'),
        dataset_source=merged_source,
        source_type='merged',
        status='ready',
        merged_from=json.dumps(unique_ids, ensure_ascii=False)
    )
    db.session.add(entity)
    db.session.commit()

    result = entity.to_dict()
    result['merge_stats'] = merge_stats
    return jsonify({'code': 0, 'msg': '数据集合并成功', 'data': result})


@plate_bp.route('/version/list', methods=['GET'])
def list_plate_versions():
    page_no = int(request.args.get('pageNo', 1))
    page_size = int(request.args.get('pageSize', 10))
    query = PlateAlgorithmVersion.query.order_by(PlateAlgorithmVersion.created_at.desc())
    pagination = query.paginate(page=page_no, per_page=page_size, error_out=False)
    return jsonify({
        'code': 0,
        'msg': 'success',
        'data': [v.to_dict() for v in pagination.items],
        'total': pagination.total
    })


@plate_bp.route('/version/<int:version_id>', methods=['GET'])
def get_plate_version(version_id):
    version = PlateAlgorithmVersion.query.get_or_404(version_id)
    return jsonify({'code': 0, 'msg': 'success', 'data': version.to_dict()})


@plate_bp.route('/version/create', methods=['POST'])
def create_plate_version():
    data = request.get_json() or {}
    version = (data.get('version') or '').strip()
    if not version:
        return jsonify({'code': 400, 'msg': 'version不能为空'}), 400
    try:
        base_model = require_official_pretrained_model(
            data.get('base_model', 'yolo11n.pt')
        )
    except ValueError as model_error:
        return jsonify({'code': 400, 'msg': str(model_error)}), 400

    exists = PlateAlgorithmVersion.query.filter_by(version=version).first()
    if exists:
        return jsonify({'code': 400, 'msg': f'版本{version}已存在'}), 400

    entity = PlateAlgorithmVersion(
        version=version,
        description=data.get('description'),
        base_model=base_model,
        status='draft',
        is_active=False
    )
    db.session.add(entity)
    db.session.commit()
    return jsonify({'code': 0, 'msg': '车牌算法版本创建成功', 'data': entity.to_dict()})


@plate_bp.route('/version/<int:version_id>/update', methods=['PUT'])
def update_plate_version(version_id):
    version = PlateAlgorithmVersion.query.get_or_404(version_id)
    data = request.get_json() or {}
    if 'description' in data:
        version.description = data.get('description')
    if 'base_model' in data and data.get('base_model'):
        try:
            version.base_model = require_official_pretrained_model(
                data.get('base_model')
            )
        except ValueError as model_error:
            return jsonify({'code': 400, 'msg': str(model_error)}), 400
    if 'status' in data and data.get('status') in {'draft', 'active', 'archived'}:
        version.status = data.get('status')
    db.session.commit()
    return jsonify({'code': 0, 'msg': '版本更新成功', 'data': version.to_dict()})


@plate_bp.route('/version/<int:version_id>/delete', methods=['POST'])
def delete_plate_version(version_id):
    version = PlateAlgorithmVersion.query.get_or_404(version_id)
    running_task = PlateTrainTask.query.filter(
        PlateTrainTask.version_id == version_id,
        PlateTrainTask.status.in_(['preparing', 'running', 'stopping'])
    ).first()
    if running_task:
        return jsonify({'code': 400, 'msg': '该版本存在运行中的训练任务，无法删除'}), 400

    if version.is_active:
        return jsonify({'code': 400, 'msg': '激活中的版本不允许删除，请先切换激活版本'}), 400

    db.session.delete(version)
    db.session.commit()
    return jsonify({'code': 0, 'msg': '版本删除成功'})


@plate_bp.route('/version/<int:version_id>/activate', methods=['POST'])
def activate_plate_version(version_id):
    version = PlateAlgorithmVersion.query.get_or_404(version_id)
    if not version.model_path:
        return jsonify({'code': 400, 'msg': '该版本暂无可用模型，请先完成训练'}), 400

    PlateAlgorithmVersion.query.update({'is_active': False, 'status': 'archived'})
    version.is_active = True
    version.status = 'active'
    db.session.commit()
    return jsonify({'code': 0, 'msg': '版本激活成功', 'data': version.to_dict()})


@plate_bp.route('/train/start', methods=['POST'])
def start_plate_train():
    data = request.get_json() or {}
    try:
        model_arch = require_official_pretrained_model(
            data.get('model_arch', 'yolo11n.pt')
        )
    except ValueError as model_error:
        return jsonify({'code': 400, 'msg': str(model_error)}), 400
    dataset_id = data.get('dataset_id')
    if dataset_id is None or str(dataset_id).strip() == '':
        return jsonify({'code': 400, 'msg': 'dataset_id不能为空'}), 400
    try:
        dataset_id = int(dataset_id)
    except Exception:
        return jsonify({'code': 400, 'msg': 'dataset_id必须是整数'}), 400

    dataset = PlateDataset.query.get(dataset_id)
    if not dataset:
        return jsonify({'code': 400, 'msg': f'数据集ID={dataset_id}不存在'}), 400
    if dataset.status != 'ready':
        return jsonify({'code': 400, 'msg': f'数据集{dataset.name}(ID:{dataset.id})不可用'}), 400

    dataset_name = dataset.name
    dataset_source = dataset.dataset_source

    version_str = (data.get('version') or '').strip()
    if not version_str:
        version_str = f"V{datetime.now().strftime('%Y.%m.%d.%H%M%S')}"

    if PlateAlgorithmVersion.query.filter_by(version=version_str).first():
        return jsonify({'code': 400, 'msg': f'版本{version_str}已存在'}), 400

    version = PlateAlgorithmVersion(
        version=version_str,
        description=data.get('description'),
        base_model=model_arch,
        status='draft',
        is_active=False
    )
    db.session.add(version)
    db.session.flush()

    hyperparameters = {
        'model_arch': model_arch,
        'epochs': int(data.get('epochs', 100)),
        'imgsz': int(data.get('imgsz', 640)),
        'batch_size': int(data.get('batch_size', 16)),
        'workers': int(data.get('workers', 8)),
        'use_gpu': bool(data.get('use_gpu', True)),
        'dataset_id': dataset_id,
        'dataset_name': dataset_name,
    }
    task = PlateTrainTask(
        version_id=version.id,
        dataset_source=dataset_source,
        status='preparing',
        progress=0,
        hyperparameters=json.dumps(hyperparameters, ensure_ascii=False),
        train_log=''
    )
    db.session.add(task)
    db.session.commit()

    _plate_train_runtime[task.id] = {'stop_requested': False}
    app = current_app._get_current_object()
    thread = threading.Thread(target=_train_worker, args=(app, task.id), daemon=True)
    thread.start()

    return jsonify({
        'code': 0,
        'msg': '车牌算法训练已启动',
        'data': {
            'train_task_id': task.id,
            'version_id': version.id,
            'version': version.version,
            'dataset_id': dataset_id,
            'dataset_name': dataset_name
        }
    })


@plate_bp.route('/train/stop/<int:task_id>', methods=['POST'])
def stop_plate_train(task_id):
    task = PlateTrainTask.query.get_or_404(task_id)
    runtime = _plate_train_runtime.get(task_id)
    if not runtime:
        return jsonify({'code': 400, 'msg': '任务未在运行中'}), 400

    runtime['stop_requested'] = True
    task.status = 'stopping'
    db.session.commit()
    _append_train_log(task, '收到停止请求，等待当前epoch完成后停止')
    return jsonify({'code': 0, 'msg': '已发送停止请求'})


@plate_bp.route('/train/status/<int:task_id>', methods=['GET'])
def get_plate_train_status(task_id):
    task = PlateTrainTask.query.get_or_404(task_id)
    return jsonify({
        'code': 0,
        'msg': 'success',
        'data': {
            'id': task.id,
            'version_id': task.version_id,
            'status': task.status,
            'progress': task.progress,
            'error_message': task.error_message,
            'start_time': task.start_time.isoformat() if task.start_time else None,
            'end_time': task.end_time.isoformat() if task.end_time else None
        }
    })


@plate_bp.route('/train/logs/<int:task_id>', methods=['GET'])
def get_plate_train_logs(task_id):
    task = PlateTrainTask.query.get_or_404(task_id)
    return jsonify({'code': 0, 'msg': 'success', 'data': task.train_log or ''})


@plate_bp.route('/train/tasks', methods=['GET'])
def list_plate_train_tasks():
    page_no = int(request.args.get('pageNo', 1))
    page_size = int(request.args.get('pageSize', 10))
    query = PlateTrainTask.query.order_by(PlateTrainTask.created_at.desc())
    pagination = query.paginate(page=page_no, per_page=page_size, error_out=False)
    return jsonify({
        'code': 0,
        'msg': 'success',
        'data': [t.to_dict() for t in pagination.items],
        'total': pagination.total
    })


@plate_bp.route('/inference/run', methods=['POST'])
def plate_inference_run():
    started_at = time.time()
    data = request.get_json() if request.is_json else request.form.to_dict()
    version_id = data.get('version_id') if isinstance(data, dict) else None
    try:
        version_id = int(version_id) if version_id else None
    except Exception:
        return jsonify({'code': 400, 'msg': 'version_id必须是整数'}), 400

    version = _get_active_or_latest_version(version_id)
    if not version:
        return jsonify({'code': 400, 'msg': '未找到可用车牌算法版本'}), 400
    if not version.model_path:
        return jsonify({'code': 400, 'msg': '当前版本尚未训练产出模型'}), 400

    inference_task = PlateInferenceTask(
        version_id=version.id,
        status='processing',
        input_source=(data.get('input_source') if isinstance(data, dict) else None)
    )
    db.session.add(inference_task)
    db.session.commit()

    temp_dir = tempfile.mkdtemp(prefix=f"plate_infer_{inference_task.id}_")
    try:
        conf = float(data.get('conf', 0.25)) if isinstance(data, dict) else 0.25
        iou = float(data.get('iou', 0.45)) if isinstance(data, dict) else 0.45
        input_source = data.get('input_source') if isinstance(data, dict) else None
        input_path, source_display = _prepare_inference_input(temp_dir, input_source=input_source)

        yolo_model = _get_yolo_model_for_version(version)
        results = yolo_model(input_path, conf=conf, iou=iou, verbose=False)
        result = results[0]

        annotated_path = os.path.join(temp_dir, 'result.jpg')
        result.save(filename=annotated_path)

        detections = []
        for box in result.boxes:
            detections.append({
                'class': int(box.cls.item()),
                'class_name': result.names[int(box.cls.item())],
                'confidence': float(box.conf.item()),
                'bbox': box.xyxy.tolist()[0]
            })

        detections_json_path = os.path.join(temp_dir, 'detections.json')
        with open(detections_json_path, 'w', encoding='utf-8') as f:
            json.dump(detections, f, ensure_ascii=False, indent=2)

        date_str = datetime.now().strftime('%Y%m%d')
        image_obj = f"plate/inference/images/{date_str}/task_{inference_task.id}_{uuid.uuid4().hex[:8]}.jpg"
        json_obj = f"plate/inference/json/{date_str}/task_{inference_task.id}_{uuid.uuid4().hex[:8]}.json"

        img_ok, img_err = ModelService.upload_to_minio(PLATE_INFERENCE_RESULTS_BUCKET, image_obj, annotated_path)
        if not img_ok:
            raise RuntimeError(f'推理结果图片上传失败: {img_err or ""}')
        json_ok, json_err = ModelService.upload_to_minio(PLATE_INFERENCE_RESULTS_BUCKET, json_obj, detections_json_path)
        if not json_ok:
            raise RuntimeError(f'推理结果JSON上传失败: {json_err or ""}')

        inference_task.status = 'completed'
        inference_task.input_source = source_display
        inference_task.output_image_path = _build_minio_download_url(PLATE_INFERENCE_RESULTS_BUCKET, image_obj)
        inference_task.output_json_path = _build_minio_download_url(PLATE_INFERENCE_RESULTS_BUCKET, json_obj)
        inference_task.detection_count = len(detections)
        inference_task.result_preview = json.dumps(detections[:20], ensure_ascii=False)
        inference_task.processing_time = time.time() - started_at
        db.session.commit()

        return jsonify({
            'code': 0,
            'msg': '车牌推理成功',
            'data': {
                'task_id': inference_task.id,
                'version_id': version.id,
                'version': version.version,
                'output_image_path': inference_task.output_image_path,
                'output_json_path': inference_task.output_json_path,
                'detection_count': inference_task.detection_count,
                'detections': detections
            }
        })
    except Exception as e:
        inference_task.status = 'failed'
        inference_task.error_message = str(e)
        inference_task.processing_time = time.time() - started_at
        db.session.commit()
        logger.error("车牌推理失败: %s\n%s", str(e), traceback.format_exc())
        return jsonify({'code': 500, 'msg': f'车牌推理失败: {str(e)}'}), 500
    finally:
        if os.path.exists(temp_dir):
            shutil.rmtree(temp_dir, ignore_errors=True)


@plate_bp.route('/inference/tasks', methods=['GET'])
def list_plate_inference_tasks():
    page_no = int(request.args.get('pageNo', 1))
    page_size = int(request.args.get('pageSize', 10))
    query = PlateInferenceTask.query.order_by(PlateInferenceTask.created_at.desc())
    pagination = query.paginate(page=page_no, per_page=page_size, error_out=False)
    return jsonify({
        'code': 0,
        'msg': 'success',
        'data': [t.to_dict() for t in pagination.items],
        'total': pagination.total
    })


@plate_bp.route('/inference/task/<int:task_id>', methods=['GET'])
def get_plate_inference_task(task_id):
    task = PlateInferenceTask.query.get_or_404(task_id)
    return jsonify({'code': 0, 'msg': 'success', 'data': task.to_dict()})
