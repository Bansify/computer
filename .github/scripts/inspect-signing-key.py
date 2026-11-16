#!/usr/bin/env python3
"""Compare configured keystores to a public certificate without exposing secrets."""
import base64
import os
import re
import subprocess
import tempfile
from pathlib import Path

# Public fingerprint of the published v1.0.20 release APK.
EXPECTED = 'b98ca2ee039eedea20f24530c76fcda301265a4bfc444744e437e3fd120d7690'
password = os.environ.get('KEYSTORE_PASS', '')
if not password:
    raise SystemExit('::error::Release keystore password is not configured.')


def inspect(label, encoded):
    if not encoded:
        print(f'::notice::{label}: not configured')
        return
    try:
        key_bytes = base64.b64decode(''.join(encoded.split()), validate=True)
    except ValueError:
        print(f'::warning::{label}: invalid base64')
        return
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / 'release.jks'
        path.write_bytes(key_bytes)
        path.chmod(0o600)
        result = subprocess.run(
            ['keytool', '-list', '-v', '-keystore', str(path), '-storepass:env', 'KEYSTORE_PASS'],
            capture_output=True, text=True, env={**os.environ, 'LC_ALL': 'C'},
        )
        if result.returncode:
            print(f'::warning::{label}: could not inspect with configured keystore password')
            return
        aliases = []
        for chunk in re.split(r'(?m)^Alias name:\s*', result.stdout)[1:]:
            alias = chunk.splitlines()[0].strip()
            if not re.search(r'(?m)^Entry type:\s*PrivateKeyEntry\s*$', chunk):
                continue
            fingerprint = re.search(r'(?m)^\s*SHA256:\s*([0-9A-Fa-f:]+)', chunk)
            if fingerprint:
                aliases.append((alias, fingerprint.group(1).replace(':', '').lower()))
        matches = [alias.lower() for alias, digest in aliases if digest == EXPECTED]
        configured = os.environ.get('KEY_ALIAS', '').strip()
        selected = configured if configured else (aliases[0][0] if aliases else '')
        print(f'::notice::{label}: private key entries inspected: {len(aliases)}')
        print(f'::notice::{label}: contains v1.0.20 certificate: {"yes" if matches else "no"}')
        print(f'::notice::{label}: selected alias matches v1.0.20: {"yes" if selected.lower() in matches else "no"}')


inspect('Primary release keystore', os.environ.get('PRIMARY_KEYSTORE', ''))
inspect('Fallback release keystore', os.environ.get('FALLBACK_KEYSTORE', ''))
