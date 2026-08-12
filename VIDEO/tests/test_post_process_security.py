import unittest

from app.utils.post_process_runner import get_task_script_path


class PostProcessSecurityTest(unittest.TestCase):

    def test_script_name_must_be_a_safe_python_basename(self):
        for unsafe_name in (
            '../outside.py',
            '..\\outside.py',
            '/tmp/outside.py',
            'nested/process.py',
            'process.txt',
        ):
            with self.subTest(unsafe_name=unsafe_name):
                with self.assertRaisesRegex(ValueError, '脚本名'):
                    get_task_script_path(7, unsafe_name)

    def test_safe_script_name_stays_in_task_workspace(self):
        path = get_task_script_path(7, 'post_process_v2.py')

        self.assertEqual('post_process_v2.py', path.name)
        self.assertEqual('task_7', path.parent.name)


if __name__ == '__main__':
    unittest.main()
