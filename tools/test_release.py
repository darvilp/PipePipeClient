"""Signing/configuration regression checks; no real credentials or builds used."""
import contextlib
import io
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import release


class ReleaseTests(unittest.TestCase):
    def test_private_file_and_environment_precedence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            key = root / 'key with spaces.p12'
            key.touch()
            config = root / 'release.env'
            config.write_text(f"export KEYSTORE_FILE='{key}'\n"
                              "KEYSTORE_PASSWORD='file password # literal'\n"
                              "KEY_ALIAS=file-alias\nKEY_PASSWORD=file-key-password\n")
            env, _ = release.load_environment({
                'PIPEPIPE_RELEASE_ENV': str(config),
                'KEYSTORE_PASSWORD': 'process legacy password',
                'KEY_ALIAS': 'process-alias', 'PATH': os.environ['PATH'],
            })
            self.assertEqual(env['KEY_PATH'], str(key))
            self.assertEqual(env['KEY_STORE_PASSWORD'], 'process legacy password')
            self.assertEqual(env['KEY_ALIAS'], 'process-alias')
            self.assertEqual(env['KEY_PASSWORD'], 'file-key-password')
            self.assertEqual(release.read_config(config)['KEY_STORE_PASSWORD'],
                             'file password # literal')

    def test_canonical_names_win_within_each_source(self):
        values = release.normalize({'KEY_PATH': 'canonical', 'KEYSTORE_FILE': 'legacy'})
        self.assertEqual(values['KEY_PATH'], 'canonical')

    def test_config_does_not_execute_shell(self):
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / 'must-not-exist'
            config = Path(directory) / 'config'
            config.write_text(f"KEY_PASSWORD='$(touch {marker})'\n")
            self.assertIn('$(touch ', release.read_config(config)['KEY_PASSWORD'])
            self.assertFalse(marker.exists())
            config.write_text("KEY_PASSWORD='private unterminated\n")
            with self.assertRaises(release.ReleaseError) as result:
                release.read_config(config)
            self.assertNotIn('private unterminated', str(result.exception))

    def test_ci_uses_process_inputs_without_loading_default_config(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'pipepipe').mkdir()
            (root / 'pipepipe/release.env').write_text('invalid shell command')
            key = root / 'key.p12'
            key.touch()
            env, _ = release.load_environment({
                'CI': 'true', 'XDG_CONFIG_HOME': str(root),
                'KEY_PATH': str(key), 'KEY_STORE_PASSWORD': 'password',
                'KEY_ALIAS': 'alias', 'KEY_PASSWORD': 'password',
            })
            self.assertEqual(env['KEY_PATH'], str(key))

    def test_explicit_missing_config_fails_even_with_process_inputs(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(release.ReleaseError, 'Cannot read private'):
                release.load_environment({'PIPEPIPE_RELEASE_ENV': str(Path(directory) / 'absent')})

    def test_wrong_certificate_or_public_only_entry_is_rejected(self):
        env = {'JAVA_HOME': '/unused', 'KEY_PATH': 'private-key-path', 'KEY_ALIAS': 'private-alias'}
        for output in ('PrivateKeyEntry\nSHA256: 00:11', 'trustedCertEntry\nSHA256: AA:BB'):
            with patch('release.subprocess.run', return_value=subprocess.CompletedProcess(
                    [], 0, stdout=output, stderr='private diagnostic')):
                with self.assertRaises(release.ReleaseError):
                    release.verify_signer(env, {'signer_sha256': 'aabb'})
        with patch('release.subprocess.run', return_value=subprocess.CompletedProcess(
                [], 0, stdout='PrivateKeyEntry\nSHA256: AA:BB', stderr='')):
            release.verify_signer(env, {'signer_sha256': 'aabb'})

    def test_stream_masks_private_values_and_preserves_failure(self):
        values = ['unique-private-password', '/private/keystore.p12', 'unique-private-alias']
        command = [sys.executable, '-c',
                   'import sys; print(sys.argv[1]); print(sys.argv[2], file=sys.stderr); '
                   'print(sys.argv[3]); sys.exit(7)', *values]
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            status = release.stream_command(command, os.environ.copy(), values)
        self.assertEqual(status, 7)
        for value in values:
            self.assertNotIn(value, output.getvalue())
        self.assertEqual(output.getvalue().count('[private signing input]'), 3)


if __name__ == '__main__':
    unittest.main()
