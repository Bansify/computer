#!/usr/bin/env python3
"""Inspect certificate fingerprints without printing the keystore or credentials."""
import base64
import os
import re
import subprocess
import tempfile
from pathlib import Path

# Public fingerprint of the published v1.0.20 release APK.
EXPECTED = 'b98ca2ee039eedea20f24530c76fcda301265a4bfc444744e437e3fd120d7690'
encoded = os.environ.get('KEYSTORE_BASE64', '')
password = os.environ.get('KEYSTORE_PASS', '')
if not encoded or not password:
    raise SystemExit('::error::Release keystore or password is not configured.')
try:
    key_bytes = base64.b64decode(''.join(encoded.split()), validate=True)
except ValueError:
    raise SystemExit('::error::The configured release keystore is not valid base64.')
with tempfile.TemporaryDirectory() as tmp:
    path = Path(tmp) / 'release.jks'
    path.write_bytes(key_bytes)
    path.chmod(0o600)
    result = subprocess.run(
        ['keytool', '-list', '-v', '-keystore', str(path), '-storepass:env', 'KEYSTORE_PASS'],
        capture_output=True, text=True, env={**os.environ, 'LC_ALL': 'C'},
    )
    if result.returncode:
        raise SystemExit('::error::Could not inspect the configured keystore. Check the keystore password.')
    aliases = []
    for chunk in re.split(r'(?m)^Alias name:\s*', result.stdout)[1:]:
        alias = chunk.splitlines()[0].strip()
        if not re.search(r'(?m)^Entry type:\s*PrivateKeyEntry\s*$', chunk):
            continue
        fingerprint = re.search(r'(?m)^\s*SHA256:\s*([0-9A-Fa-f:]+)', chunk)
        if fingerprint:
            aliases.append((alias, fingerprint.group(1).replace(':', '').lower()))
    matches = [alias for alias, digest in aliases if digest == EXPECTED]
    configured = os.environ.get('KEY_ALIAS', '').strip()
    selected = configured if configured else (aliases[0][0] if aliases else '')
    print(f'::notice::Private-key entries inspected: {len(aliases)}')
    print(f'::notice::Original v1.0.20 signing certificate in keystore: {"yes" if matches else "no"}')
    print(f'::notice::Currently selected alias matches v1.0.20: {"yes" if selected.lower() in [a.lower() for a in matches] else "no"}')
    if matches and selected.lower() not in [a.lower() for a in matches]:
        print('::notice::The original certificate exists in this keystore but a different alias is being selected.')
    elif not matches:
        print('::notice::The original certificate was not found among this keystore’s private-key entries.')
