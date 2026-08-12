"""Real ffmpeg regression tests for browser-seekable review playback."""
import json
import os
import shutil
import subprocess
import tempfile
import threading
import time
import unittest
from unittest import mock


class TestSeekablePlayback(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.cache_dir = os.path.join(self.temp_dir.name, 'cache')
        self.previous_cache = os.environ.get('YFEIEYE_SEEKABLE_PLAYBACK_CACHE_DIR')
        self.previous_ttl = os.environ.get('YFEIEYE_SEEKABLE_PLAYBACK_CACHE_TTL_SECONDS')
        self.previous_max_bytes = os.environ.get('YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES')
        os.environ['YFEIEYE_SEEKABLE_PLAYBACK_CACHE_DIR'] = self.cache_dir

    def tearDown(self):
        if self.previous_cache is None:
            os.environ.pop('YFEIEYE_SEEKABLE_PLAYBACK_CACHE_DIR', None)
        else:
            os.environ['YFEIEYE_SEEKABLE_PLAYBACK_CACHE_DIR'] = self.previous_cache
        self._restore_env('YFEIEYE_SEEKABLE_PLAYBACK_CACHE_TTL_SECONDS', self.previous_ttl)
        self._restore_env('YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES', self.previous_max_bytes)
        self.temp_dir.cleanup()

    @staticmethod
    def _restore_env(name, value):
        if value is None:
            os.environ.pop(name, None)
        else:
            os.environ[name] = value

    def test_real_flv_and_extensionless_record_become_verified_seekable_mp4(self):
        from app.services.seekable_playback_service import prepare_seekable_mp4_path

        source_flv = os.path.join(self.temp_dir.name, 'source.flv')
        self._generate_flv(source_flv)
        extensionless = os.path.join(self.temp_dir.name, 'camera-recording')
        shutil.copyfile(source_flv, extensionless)

        first = prepare_seekable_mp4_path(source_flv)
        second = prepare_seekable_mp4_path(extensionless)

        for result in (first, second):
            self.assertTrue(os.path.isfile(result['path']), result)
            self.assertGreater(os.path.getsize(result['path']), 1024)
            self.assertEqual('video/mp4', result['content_type'])
            self.assertRegex(result['source_sha256'], r'^sha256:[0-9a-f]{64}$')
            self.assertRegex(result['output_sha256'], r'^sha256:[0-9a-f]{64}$')
            probe = self._probe(result['path'])
            self.assertEqual('h264', probe['streams'][0]['codec_name'])
            self.assertGreater(float(probe['format']['duration']), 1.0)
            self.assertIn('mov,mp4', probe['format']['format_name'])

    def test_verified_cache_is_reused_without_running_ffmpeg_again(self):
        from app.services.seekable_playback_service import prepare_seekable_mp4_path

        source = os.path.join(self.temp_dir.name, 'cache-source.flv')
        self._generate_flv(source)
        first = prepare_seekable_mp4_path(source)
        first_mtime = os.path.getmtime(first['path'])
        time.sleep(0.05)
        second = prepare_seekable_mp4_path(source)

        self.assertEqual(first['path'], second['path'])
        self.assertEqual(first['output_sha256'], second['output_sha256'])
        self.assertEqual(first_mtime, os.path.getmtime(second['path']))
        self.assertTrue(second['cache_hit'])

    def test_corrupt_record_fails_without_publishable_mp4(self):
        from app.services.seekable_playback_service import prepare_seekable_mp4_path

        source = os.path.join(self.temp_dir.name, 'corrupt-record')
        with open(source, 'wb') as handle:
            handle.write(b'not-a-video')

        with self.assertRaises(RuntimeError):
            prepare_seekable_mp4_path(source)
        published = []
        if os.path.isdir(self.cache_dir):
            published = [name for name in os.listdir(self.cache_dir) if name.endswith('.mp4')]
        self.assertEqual([], published)

    def test_seekable_ffmpeg_command_disables_stdin_and_bounds_threads(self):
        from app.services import seekable_playback_service as service

        probe = {
            'streams': [{'codec_type': 'video', 'codec_name': 'h264'}],
            'format': {'duration': '12.5'},
        }
        with mock.patch.object(service, '_probe', return_value=probe):
            command = service._build_ffmpeg_command('source.flv', 'output.mp4')

        self.assertIn('-nostdin', command)
        self.assertEqual('2', command[command.index('-threads') + 1])
        self.assertEqual('1', command[command.index('-filter_threads') + 1])
        thread_positions = [
            index for index, value in enumerate(command) if value == '-threads'
        ]
        self.assertGreater(max(thread_positions), command.index('-c:v'))

    def test_seekable_execute_passes_duration_and_output_limit_to_shared_guard(self):
        from app.services import seekable_playback_service as service

        completed = subprocess.CompletedProcess(['ffmpeg'], 0, b'', b'')
        output_path = os.path.join(self.cache_dir, 'output.mp4')
        with mock.patch.dict(os.environ, {
            'YFEIEYE_SEEKABLE_PLAYBACK_MAX_OUTPUT_BYTES': '4096',
            'YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES': '8192',
        }, clear=False), mock.patch.object(
            service, 'run_ffmpeg_guarded', return_value=completed
        ) as guarded:
            actual = service._execute_ffmpeg(
                ['ffmpeg', '-i', 'source.flv', output_path],
                output_path=output_path,
                expected_duration=12.5,
            )

        self.assertIs(completed, actual)
        guarded.assert_called_once_with(
            ['ffmpeg', '-i', 'source.flv', output_path],
            output_path=output_path,
            expected_duration=12.5,
            max_output_bytes=4096,
            quota_path=self.cache_dir,
            max_total_bytes=8192,
        )

    def test_seekable_oversized_output_is_rejected_before_verification_and_cleaned(self):
        from app.services import seekable_playback_service as service

        source = os.path.join(self.temp_dir.name, 'oversized.flv')
        with open(source, 'wb') as handle:
            handle.write(b'input-video')

        def oversized(_command, output_path=None, expected_duration=None):
            del expected_duration
            with open(output_path, 'wb') as handle:
                handle.write(b'x' * 64)
            raise RuntimeError('ffmpeg output size limit exceeded')

        with mock.patch.dict(os.environ, {
            'YFEIEYE_SEEKABLE_PLAYBACK_MAX_OUTPUT_BYTES': '32',
        }, clear=False), mock.patch.object(
            service, '_build_ffmpeg_command', return_value=['ffmpeg']
        ), mock.patch.object(
            service, '_probe', return_value={
                'streams': [{'codec_type': 'video', 'codec_name': 'h264'}],
                'format': {'duration': '1.0'},
            }
        ), mock.patch.object(
            service, '_execute_ffmpeg', side_effect=oversized
        ), self.assertRaisesRegex(RuntimeError, 'output size limit'):
            service.prepare_seekable_mp4_path(source)

        if os.path.isdir(self.cache_dir):
            self.assertFalse(any(name.startswith('.output-')
                                 for name in os.listdir(self.cache_dir)))

    def test_source_is_not_copied_when_cache_quota_cannot_fit_snapshot(self):
        from app.services import seekable_playback_service as service

        source = os.path.join(self.temp_dir.name, 'quota-source.flv')
        with open(source, 'wb') as handle:
            handle.write(b'x' * 64)
        previous = os.environ.get('YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES')
        os.environ['YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES'] = '32'
        try:
            with mock.patch.object(service, '_copy_stable_source') as copy_source:
                with self.assertRaisesRegex(RuntimeError, 'quota'):
                    service.prepare_seekable_mp4_path(source)
            copy_source.assert_not_called()
        finally:
            if previous is None:
                os.environ.pop('YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES', None)
            else:
                os.environ['YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES'] = previous

    def test_ffmpeg_concurrency_env_is_resolved_on_first_slot_not_import(self):
        import importlib
        from app.services import media_resource_guard as resource_guard

        previous = os.environ.get('YFEIEYE_FFMPEG_MAX_CONCURRENT')
        try:
            os.environ['YFEIEYE_FFMPEG_MAX_CONCURRENT'] = '2'
            resource_guard = importlib.reload(resource_guard)
            os.environ['YFEIEYE_FFMPEG_MAX_CONCURRENT'] = '1'
            with resource_guard.ffmpeg_slot(wait_seconds=0.01):
                with self.assertRaisesRegex(RuntimeError, 'capacity'):
                    with resource_guard.ffmpeg_slot(wait_seconds=0.01):
                        self.fail('a second slot must not be available')
        finally:
            if previous is None:
                os.environ.pop('YFEIEYE_FFMPEG_MAX_CONCURRENT', None)
            else:
                os.environ['YFEIEYE_FFMPEG_MAX_CONCURRENT'] = previous
            importlib.reload(resource_guard)

    def test_guard_terminates_child_when_resource_probe_raises_unexpectedly(self):
        from app.services import media_resource_guard as resource_guard

        class FakeProcess:
            returncode = None

            def __init__(self):
                self.terminated = False

            def poll(self):
                return 0 if self.terminated else None

            def terminate(self):
                self.terminated = True
                self.returncode = -15

            def kill(self):
                self.terminated = True
                self.returncode = -9

            def communicate(self, timeout=None):
                del timeout
                return b'', b''

        process = FakeProcess()
        with mock.patch.object(
            resource_guard.subprocess, 'Popen', return_value=process
        ), mock.patch.object(
            resource_guard, '_enforce_disk_limits', side_effect=OSError('disk probe failed')
        ), self.assertRaisesRegex(OSError, 'disk probe failed'):
            resource_guard.run_ffmpeg_guarded(
                ['ffmpeg'], output_path='output.mp4', max_output_bytes=1024)

        self.assertTrue(process.terminated)

    def test_cache_cleanup_removes_expired_pairs_and_preserves_active_lock(self):
        from app.services.seekable_playback_service import cleanup_seekable_playback_cache

        os.makedirs(self.cache_dir, exist_ok=True)
        os.environ['YFEIEYE_SEEKABLE_PLAYBACK_CACHE_TTL_SECONDS'] = '60'
        os.environ['YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES'] = '1048576'
        now = time.time()
        self._cache_pair('expired', 128, now - 120)
        self._cache_pair('locked', 128, now - 120)
        identity_alias = os.path.join(self.cache_dir, '.identity-expired.json')
        with open(identity_alias, 'w', encoding='utf-8') as handle:
            json.dump({'source_sha256': f'sha256:{"0" * 64}'}, handle)
        open(os.path.join(self.cache_dir, 'locked.lock'), 'wb').close()

        result = cleanup_seekable_playback_cache(now=now)

        self.assertEqual(1, result['expired_pairs_removed'])
        self.assertFalse(os.path.exists(os.path.join(self.cache_dir, 'expired.mp4')))
        self.assertFalse(os.path.exists(identity_alias))
        self.assertTrue(os.path.exists(os.path.join(self.cache_dir, 'locked.mp4')))

    def test_cache_cleanup_enforces_capacity_oldest_first(self):
        from app.services.seekable_playback_service import cleanup_seekable_playback_cache

        os.makedirs(self.cache_dir, exist_ok=True)
        os.environ['YFEIEYE_SEEKABLE_PLAYBACK_CACHE_TTL_SECONDS'] = '86400'
        os.environ['YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES'] = '300'
        now = time.time()
        self._cache_pair('older', 220, now - 20)
        self._cache_pair('newer', 220, now - 10)

        result = cleanup_seekable_playback_cache(now=now)

        self.assertEqual(1, result['capacity_pairs_removed'])
        self.assertFalse(os.path.exists(os.path.join(self.cache_dir, 'older.mp4')))
        self.assertTrue(os.path.exists(os.path.join(self.cache_dir, 'newer.mp4')))
        self.assertLessEqual(result['remaining_bytes'], 300)

    def test_stale_owner_cannot_delete_replacement_lock(self):
        from app.services.seekable_playback_service import (
            _claim_lock,
            _read_lock_token,
            _release_owned_lock,
        )

        os.makedirs(self.cache_dir, exist_ok=True)
        lock_path = os.path.join(self.cache_dir, 'camera.lock')
        old_owner = _claim_lock(lock_path)
        self.assertTrue(old_owner)
        stale_at = time.time() - 120
        os.utime(lock_path, (stale_at, stale_at))

        replacement_owner = _claim_lock(
            lock_path,
            remove_stale=True,
            stale_seconds=30,
        )

        self.assertTrue(replacement_owner)
        self.assertNotEqual(old_owner, replacement_owner)
        _release_owned_lock(lock_path, old_owner)
        self.assertTrue(os.path.exists(lock_path))
        self.assertEqual(replacement_owner, _read_lock_token(lock_path))
        _release_owned_lock(lock_path, replacement_owner)

    def test_lock_heartbeat_prevents_stale_takeover(self):
        from app.services.seekable_playback_service import (
            _claim_lock,
            _lock_heartbeat,
            _release_owned_lock,
        )

        os.makedirs(self.cache_dir, exist_ok=True)
        lock_path = os.path.join(self.cache_dir, 'heartbeat.lock')
        owner = _claim_lock(lock_path)
        stale_at = time.time() - 120
        os.utime(lock_path, (stale_at, stale_at))

        with _lock_heartbeat(lock_path, owner, interval_seconds=0.01):
            time.sleep(0.05)
            contender = _claim_lock(
                lock_path,
                remove_stale=True,
                stale_seconds=0.2,
            )

        self.assertIsNone(contender)
        _release_owned_lock(lock_path, owner)

    def test_cache_cleanup_preserves_active_read_lease(self):
        from app.services.seekable_playback_service import (
            acquire_seekable_playback_lease,
            cleanup_seekable_playback_cache,
            release_seekable_playback_lease,
        )

        os.makedirs(self.cache_dir, exist_ok=True)
        os.environ['YFEIEYE_SEEKABLE_PLAYBACK_CACHE_TTL_SECONDS'] = '1'
        now = time.time()
        self._cache_pair('leased', 128, now - 120)
        media_path = os.path.join(self.cache_dir, 'leased.mp4')
        lease = acquire_seekable_playback_lease(media_path)

        while_leased = cleanup_seekable_playback_cache(now=now)

        self.assertEqual(0, while_leased['expired_pairs_removed'])
        self.assertTrue(os.path.exists(media_path))
        release_seekable_playback_lease(lease)

        after_release = cleanup_seekable_playback_cache(now=now)
        self.assertEqual(1, after_release['expired_pairs_removed'])
        self.assertFalse(os.path.exists(media_path))

    def test_concurrent_request_waits_for_owner_then_hits_cache(self):
        from app.services import seekable_playback_service as service

        source = os.path.join(self.temp_dir.name, 'concurrent.flv')
        self._generate_flv(source)
        original_execute = service._execute_ffmpeg
        execution_count = 0
        execution_lock = threading.Lock()

        def slow_execute(command, **kwargs):
            nonlocal execution_count
            with execution_lock:
                execution_count += 1
            time.sleep(0.15)
            return original_execute(command, **kwargs)

        results = []
        errors = []

        def prepare():
            try:
                results.append(service.prepare_seekable_mp4_path(source))
            except Exception as exception:  # pragma: no cover - asserted below
                errors.append(exception)

        with mock.patch.object(service, '_execute_ffmpeg', side_effect=slow_execute):
            first = threading.Thread(target=prepare)
            second = threading.Thread(target=prepare)
            first.start()
            time.sleep(0.03)
            second.start()
            first.join(timeout=30)
            second.join(timeout=30)

        self.assertFalse(first.is_alive())
        self.assertFalse(second.is_alive())
        self.assertEqual([], errors)
        self.assertEqual(2, len(results))
        self.assertEqual(1, execution_count)
        self.assertEqual(results[0]['path'], results[1]['path'])
        self.assertTrue(any(result['cache_hit'] for result in results))

    def test_concurrent_same_identity_materializes_source_once(self):
        from app.services import seekable_playback_service as service

        source = os.path.join(self.temp_dir.name, 'remote-source.flv')
        self._generate_flv(source)
        materialization_count = 0
        materialization_lock = threading.Lock()
        results = []
        errors = []

        def materialize(destination):
            nonlocal materialization_count
            with materialization_lock:
                materialization_count += 1
            time.sleep(0.15)
            shutil.copyfile(source, destination)

        def prepare():
            try:
                results.append(service.prepare_seekable_mp4_path(
                    source_identity='tenant:1:space:2:object:camera.flv',
                    source_size_bytes=os.path.getsize(source),
                    materialize_source=materialize,
                ))
            except Exception as exception:  # pragma: no cover - asserted below
                errors.append(exception)

        first = threading.Thread(target=prepare)
        second = threading.Thread(target=prepare)
        first.start()
        time.sleep(0.03)
        second.start()
        first.join(timeout=30)
        second.join(timeout=30)

        self.assertFalse(first.is_alive())
        self.assertFalse(second.is_alive())
        self.assertEqual([], errors)
        self.assertEqual(2, len(results))
        self.assertEqual(1, materialization_count)
        self.assertEqual(results[0]['path'], results[1]['path'])
        self.assertTrue(any(result['cache_hit'] for result in results))

    def test_different_source_materializations_share_ffmpeg_capacity_slot(self):
        from app.services import media_resource_guard as guard
        from app.services import seekable_playback_service as service

        source = os.path.join(self.temp_dir.name, 'shared-capacity.flv')
        self._generate_flv(source)
        previous_limit = os.environ.get('YFEIEYE_FFMPEG_MAX_CONCURRENT')
        active_materializations = 0
        max_active_materializations = 0
        activity_lock = threading.Lock()
        both_materializing = threading.Event()
        results = []
        errors = []

        def materialize(destination):
            nonlocal active_materializations, max_active_materializations
            with activity_lock:
                active_materializations += 1
                max_active_materializations = max(
                    max_active_materializations, active_materializations)
                if active_materializations == 2:
                    both_materializing.set()
            try:
                both_materializing.wait(0.3)
                shutil.copyfile(source, destination)
            finally:
                with activity_lock:
                    active_materializations -= 1

        def prepare(identity):
            try:
                results.append(service.prepare_seekable_mp4_path(
                    source_identity=identity,
                    source_size_bytes=os.path.getsize(source),
                    materialize_source=materialize,
                ))
            except Exception as exception:  # pragma: no cover - asserted below
                errors.append(exception)

        try:
            os.environ['YFEIEYE_FFMPEG_MAX_CONCURRENT'] = '1'
            guard._FFMPEG_LIMIT = None
            guard._FFMPEG_SEMAPHORE = None
            first = threading.Thread(target=prepare, args=('remote:first',))
            second = threading.Thread(target=prepare, args=('remote:second',))
            first.start()
            second.start()
            first.join(timeout=30)
            second.join(timeout=30)
        finally:
            self._restore_env('YFEIEYE_FFMPEG_MAX_CONCURRENT', previous_limit)
            guard._FFMPEG_LIMIT = None
            guard._FFMPEG_SEMAPHORE = None

        self.assertFalse(first.is_alive())
        self.assertFalse(second.is_alive())
        self.assertEqual([], errors)
        self.assertEqual(2, len(results))
        self.assertEqual(1, max_active_materializations)

    def test_media_storage_reservations_are_atomic_across_slots(self):
        from app.services import media_resource_guard as guard

        quota_dir = os.path.join(self.temp_dir.name, 'reservation-quota')
        os.makedirs(quota_dir)
        previous_limit = os.environ.get('YFEIEYE_FFMPEG_MAX_CONCURRENT')
        previous_reserve = os.environ.get('YFEIEYE_MEDIA_DISK_MIN_FREE_BYTES')
        first_entered = threading.Event()
        release_first = threading.Event()
        entered = []
        errors = []

        def reserve(name):
            try:
                with guard.media_storage_slot(
                        quota_dir, incoming_bytes=700, max_total_bytes=1000):
                    entered.append(name)
                    if name == 'first':
                        first_entered.set()
                        release_first.wait(2)
            except Exception as exception:  # pragma: no cover - asserted below
                errors.append(exception)

        try:
            os.environ['YFEIEYE_FFMPEG_MAX_CONCURRENT'] = '2'
            os.environ['YFEIEYE_MEDIA_DISK_MIN_FREE_BYTES'] = '0'
            guard._FFMPEG_LIMIT = None
            guard._FFMPEG_SEMAPHORE = None
            first = threading.Thread(target=reserve, args=('first',))
            second = threading.Thread(target=reserve, args=('second',))
            first.start()
            self.assertTrue(first_entered.wait(2))
            second.start()
            second.join(timeout=2)
            release_first.set()
            first.join(timeout=2)
        finally:
            release_first.set()
            self._restore_env('YFEIEYE_FFMPEG_MAX_CONCURRENT', previous_limit)
            self._restore_env('YFEIEYE_MEDIA_DISK_MIN_FREE_BYTES', previous_reserve)
            guard._FFMPEG_LIMIT = None
            guard._FFMPEG_SEMAPHORE = None

        self.assertFalse(first.is_alive())
        self.assertFalse(second.is_alive())
        self.assertEqual(['first'], entered)
        self.assertEqual(1, len(errors))
        self.assertIn('quota', str(errors[0]))

    def test_prepare_can_publish_and_acquire_read_lease_before_unlock(self):
        from app.services import seekable_playback_service as service

        source = os.path.join(self.temp_dir.name, 'atomic-lease.flv')
        self._generate_flv(source)
        original_release = service._release_owned_lock
        producer_release_observations = []

        def observe_release(lock_path, token):
            name = os.path.basename(lock_path)
            if name.endswith('.lock') and not name.startswith('.'):
                key = name[:-5]
                producer_release_observations.append(any(
                    entry.startswith(f'{key}.lease.')
                    for entry in os.listdir(self.cache_dir)
                ))
            return original_release(lock_path, token)

        with mock.patch.object(service, '_release_owned_lock', side_effect=observe_release):
            prepared = service.prepare_seekable_mp4_path(source, acquire_lease=True)

        self.assertEqual([True], producer_release_observations)
        self.assertTrue(os.path.isfile(prepared['lease']['path']))
        self.assertTrue(service.release_seekable_playback_lease(prepared['lease']))

    def test_producer_heartbeat_covers_verify_hash_and_publish_phase(self):
        from app.services import seekable_playback_service as service

        source = os.path.join(self.temp_dir.name, 'heartbeat-publish.flv')
        self._generate_flv(source)
        original_verify = service._verify_mp4
        original_touch = service._touch_owned_lock
        state = {'verifying': False}
        touches_during_verify = []

        def slow_verify(path):
            state['verifying'] = True
            try:
                time.sleep(0.08)
                return original_verify(path)
            finally:
                state['verifying'] = False

        def observe_touch(lock_path, token):
            if state['verifying']:
                touches_during_verify.append(lock_path)
            return original_touch(lock_path, token)

        with mock.patch.dict(os.environ, {
            'YFEIEYE_SEEKABLE_PLAYBACK_LOCK_HEARTBEAT_SECONDS': '0.01',
        }, clear=False), mock.patch.object(
            service, '_verify_mp4', side_effect=slow_verify
        ), mock.patch.object(
            service, '_touch_owned_lock', side_effect=observe_touch
        ):
            service.prepare_seekable_mp4_path(source)

        self.assertTrue(touches_during_verify)

    def _cache_pair(self, key, size, modified_at):
        output = os.path.join(self.cache_dir, f'{key}.mp4')
        metadata = os.path.join(self.cache_dir, f'{key}.json')
        with open(output, 'wb') as handle:
            handle.write(b'x' * size)
        with open(metadata, 'w', encoding='utf-8') as handle:
            json.dump({'source_sha256': f'sha256:{"0" * 64}'}, handle)
        os.utime(output, (modified_at, modified_at))
        os.utime(metadata, (modified_at, modified_at))

    @staticmethod
    def _generate_flv(path):
        command = [
            'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
            '-f', 'lavfi', '-i', 'testsrc=size=160x120:rate=10',
            '-t', '2', '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
            '-an', '-f', 'flv', path,
        ]
        result = subprocess.run(command, capture_output=True, timeout=30)
        if result.returncode != 0:
            raise AssertionError(result.stderr.decode('utf-8', errors='replace'))

    @staticmethod
    def _probe(path):
        result = subprocess.run([
            'ffprobe', '-v', 'error', '-show_streams', '-show_format',
            '-of', 'json', path,
        ], capture_output=True, text=True, timeout=15)
        if result.returncode != 0:
            raise AssertionError(result.stderr)
        return json.loads(result.stdout)


if __name__ == '__main__':
    unittest.main()
