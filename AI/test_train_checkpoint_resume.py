import os
import shutil
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

try:
    import torch
except ModuleNotFoundError:
    torch = None

from app.utils.train_checkpoint import (
    CHECKPOINT_ACTION_FINALIZE,
    CHECKPOINT_ACTION_REJECT,
    CHECKPOINT_ACTION_RESUME,
    _default_checkpoint_loader,
    cleanup_staged_checkpoint,
    find_yolo_checkpoint,
    prepare_yolo_resume_checkpoint,
)


class TrainCheckpointResumeTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.mkdtemp(prefix='easyaiot-checkpoint-test-')
        self.output_dir = os.path.join(self.temp_dir, 'train_25', 'train_results')
        self.source_path = os.path.join(self.output_dir, 'weights', 'last.pt')
        self.staging_root = os.path.join(self.temp_dir, 'resume-checkpoints')
        os.makedirs(os.path.dirname(self.source_path), exist_ok=True)
        with open(self.source_path, 'wb') as checkpoint_file:
            checkpoint_file.write(b'checkpoint')

    def tearDown(self):
        shutil.rmtree(self.temp_dir, ignore_errors=True)

    def _prepare(self, checkpoint, completed_epochs=39, target_epochs=100):
        return prepare_yolo_resume_checkpoint(
            self.source_path,
            self.staging_root,
            task_id=25,
            completed_epochs=completed_epochs,
            target_epochs=target_epochs,
            loader=lambda _: checkpoint,
        )

    def test_default_loader_never_enables_pickle_object_loading(self):
        calls = []
        fake_torch = types.SimpleNamespace(
            load=lambda *args, **kwargs: calls.append((args, kwargs)) or {},
        )

        with patch.dict(sys.modules, {'torch': fake_torch}):
            _default_checkpoint_loader(self.source_path)

        self.assertTrue(calls)
        self.assertIs(calls[0][1]['weights_only'], True)

    def test_resumable_checkpoint_survives_train_results_cleanup(self):
        prepared = self._prepare({
            'epoch': 38,
            'optimizer': {'state': {}},
            'train_args': {'epochs': 100},
        })

        self.assertEqual(CHECKPOINT_ACTION_RESUME, prepared.action)
        self.assertNotEqual(os.path.abspath(self.source_path), prepared.path)
        shutil.rmtree(self.output_dir)
        self.assertTrue(os.path.isfile(prepared.path))

    def test_final_weights_with_one_epoch_lag_are_completed(self):
        prepared = self._prepare(
            {
                'epoch': -1,
                'optimizer': None,
                'model': object(),
                'train_args': {'epochs': 100},
            },
            completed_epochs=99,
        )

        self.assertEqual(CHECKPOINT_ACTION_FINALIZE, prepared.action)

    def test_early_weights_only_checkpoint_is_rejected(self):
        prepared = self._prepare({
            'epoch': -1,
            'optimizer': None,
            'model': object(),
            'train_args': {'epochs': 100},
        })

        self.assertEqual(CHECKPOINT_ACTION_REJECT, prepared.action)

    def test_intermediate_weights_only_checkpoint_is_not_finalized(self):
        prepared = self._prepare(
            {
                'epoch': 37,
                'optimizer': None,
                'model': object(),
                'train_args': {'epochs': 100},
            },
            completed_epochs=99,
        )

        self.assertEqual(CHECKPOINT_ACTION_REJECT, prepared.action)

    def test_cleanup_removes_only_staged_checkpoint(self):
        prepared = self._prepare({
            'epoch': 38,
            'optimizer': {'state': {}},
            'train_args': {'epochs': 100},
        })

        cleanup_staged_checkpoint(prepared.path, self.staging_root)

        self.assertFalse(os.path.exists(prepared.path))
        self.assertTrue(os.path.exists(self.source_path))

    def test_checkpoint_lookup_prefers_newest_result_directory(self):
        lookup_root = os.path.join(self.temp_dir, 'lookup')
        older_path = os.path.join(lookup_root, 'train_results9', 'weights', 'last.pt')
        newer_path = os.path.join(lookup_root, 'train_results10', 'weights', 'last.pt')
        os.makedirs(os.path.dirname(older_path), exist_ok=True)
        os.makedirs(os.path.dirname(newer_path), exist_ok=True)
        with open(older_path, 'wb') as checkpoint_file:
            checkpoint_file.write(b'older')
        with open(newer_path, 'wb') as checkpoint_file:
            checkpoint_file.write(b'newer')
        os.utime(older_path, (100, 100))
        os.utime(newer_path, (200, 200))

        checkpoint = find_yolo_checkpoint(lookup_root)

        self.assertEqual(os.path.abspath(newer_path), checkpoint)

    @unittest.skipIf(torch is None, 'PyTorch is only available in ai-service')
    def test_real_pytorch_checkpoint_metadata_is_loaded(self):
        torch.save(
            {
                'epoch': 38,
                'optimizer': {'state': {}, 'param_groups': []},
                'model': {'weight': torch.ones(1, 1), 'bias': torch.zeros(1)},
                'train_args': {'epochs': 100},
            },
            self.source_path,
        )

        prepared = prepare_yolo_resume_checkpoint(
            self.source_path,
            self.staging_root,
            task_id=25,
            completed_epochs=39,
            target_epochs=100,
        )

        self.assertEqual(CHECKPOINT_ACTION_RESUME, prepared.action)
        self.assertEqual(38, prepared.state.epoch)


if __name__ == '__main__':
    unittest.main()
