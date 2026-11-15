#!/usr/bin/env bash
set +e
./gradlew connectedDebugAndroidTest --no-daemon > device-tests.log 2>&1
result=$?
cat device-tests.log
if [ "$result" -ne 0 ]; then
  python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
import re

def annotate(text):
    text = text.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
    print(f'::error::{text[:5000]}')

count = 0
for path in Path('app/build/outputs/androidTest-results').rglob('*.xml'):
    try:
        root = ET.parse(path).getroot()
        for case in root.iter('testcase'):
            for failure in list(case.findall('failure')) + list(case.findall('error')):
                annotate(f"{case.get('classname', '')}.{case.get('name', '')}: {failure.get('message', '')} {(failure.text or '')[:2500]}")
                count += 1
                if count >= 8:
                    break
            if count >= 8:
                break
    except (OSError, ET.ParseError):
        pass
    if count >= 8:
        break
if not count:
    lines = Path('device-tests.log').read_text(errors='replace').splitlines()
    matches = [s for s in lines if re.search(r'FAILED|failure|exception|error|tests completed|What went wrong', s, re.I)]
    annotate('\n'.join(matches[-25:]) or '\n'.join(lines[-25:]))
PY
fi
exit "$result"
