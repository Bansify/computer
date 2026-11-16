import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'check-release-apk.sh'
PIN = '73ab85c428aae3e50152ed2837d6f090a3f1ed97a227e257b73b720e16693ae1'


class ReleaseApkTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        tools = root / 'sdk' / 'build-tools' / '35.0.0'
        tools.mkdir(parents=True)
        for name, body in {
            'aapt': 'printf "%s\\n" "$FAKE_BADGING"',
            'apksigner': 'printf "%s\\n" "$FAKE_CERT"; exit "${FAKE_VERIFY_EXIT:-0}"',
        }.items():
            tool = tools / name
            tool.write_text('#!/usr/bin/env bash\n' + body + '\n')
            tool.chmod(0o755)
        self.apk = root / 'release.apk'
        self.apk.write_text('fake')
        self.pin = root / 'pin.txt'
        self.pin.write_text(PIN + '\n')
        self.env = dict(os.environ, ANDROID_HOME=str(root / 'sdk'),
                        FAKE_BADGING="package: name='com.gothwad.computer' versionCode='21' versionName='1.0.21'",
                        FAKE_CERT=f'V3.0 Signer: certificate SHA-256 digest: {PIN}')

    def run_check(self, **updates):
        return subprocess.run(['bash', str(SCRIPT), str(self.apk), '21', str(self.pin)],
                              env={**self.env, **updates}, capture_output=True, text=True)

    def test_selected_certificate_and_package(self):
        self.assertEqual(self.run_check().returncode, 0)
        self.assertEqual(self.run_check(FAKE_CERT=f'Signer #1 certificate SHA-256 digest: {PIN.upper()}').returncode, 0)

    def test_rejects_other_or_missing_certificates(self):
        for output in ['V3.0 Signer: certificate SHA-256 digest: ' + 'a' * 64, '',
                       f'V3.0 Signer: certificate SHA-256 digest: {PIN}\nV2.0 Signer: certificate SHA-256 digest: ' + 'b' * 64]:
            with self.subTest(output=output):
                self.assertNotEqual(self.run_check(FAKE_CERT=output).returncode, 0)
        self.assertNotEqual(self.run_check(FAKE_VERIFY_EXIT='1').returncode, 0)

    def test_rejects_wrong_package_version_and_pin(self):
        self.assertNotEqual(self.run_check(FAKE_BADGING="package: name='other' versionCode='21'").returncode, 0)
        self.assertNotEqual(self.run_check(FAKE_BADGING="package: name='com.gothwad.computer' versionCode='20'").returncode, 0)
        self.pin.write_text('invalid\n')
        self.assertNotEqual(self.run_check().returncode, 0)


if __name__ == '__main__':
    unittest.main()
