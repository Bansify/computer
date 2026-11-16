#!/usr/bin/env bash
# Runs only inside a disposable GitHub emulator; not for an owner's device.
set -euo pipefail
./gradlew :app:assembleRelease -PlocalSign -PversionCode=21 -PversionName=1.0.21 --no-daemon --no-configuration-cache
apk=$(find app/build/outputs/apk/release -name '*.apk' -type f -print -quit)
test -n "$apk"
mkdir -p old-release
# Test the exact published APK as evidence, separately from the new candidate.
gh release download v1.0.21 --pattern 'GothwadComputer-v1.0.21-Release.apk' --dir old-release

inspect_home() {
  local package=$1 label=$2 level=$3 ready=0 attempt
  adb install "$package"
  adb shell am force-stop com.gothwad.computer
  adb logcat -c
  adb shell am start -W -n com.gothwad.computer/.MainActivity
  for attempt in $(seq 1 20); do
    adb shell rm -f /data/local/tmp/gothwad-window.xml
    adb shell uiautomator dump /data/local/tmp/gothwad-window.xml >/dev/null 2>&1 || true
    adb exec-out cat /data/local/tmp/gothwad-window.xml > "release-ui-${label}.xml" 2>/dev/null || true
    if grep -q 'com.gothwad.computer:id/safe_retry' "release-ui-${label}.xml"; then
      echo "::${level}::${label}: Recovery appeared instead of the normal desktop."
      break
    fi
    if grep -q 'com.gothwad.computer:id/btn_start' "release-ui-${label}.xml"; then
      ready=1
      break
    fi
    sleep 1
  done
  if [ "$ready" -eq 1 ]; then
    # A frame appearing once is not proof that startup remains healthy.
    sleep 8
    adb shell rm -f /data/local/tmp/gothwad-window.xml
    adb shell uiautomator dump /data/local/tmp/gothwad-window.xml >/dev/null 2>&1 || true
    adb exec-out cat /data/local/tmp/gothwad-window.xml > "release-ui-${label}.xml" 2>/dev/null || true
    if ! grep -q 'com.gothwad.computer:id/btn_start' "release-ui-${label}.xml" || grep -q 'com.gothwad.computer:id/safe_retry' "release-ui-${label}.xml"; then
      ready=0
    fi
  fi
  adb logcat -d -v brief > "release-logcat-${label}.log"
  adb uninstall com.gothwad.computer >/dev/null
  if [ "$ready" -ne 1 ]; then
    LEVEL="$level" LABEL="$label" python3 - <<'PY'
import os
from pathlib import Path
label, level = os.environ['LABEL'], os.environ['LEVEL']
text = Path(f'release-logcat-{label}.log').read_text(errors='replace').splitlines()
selected = [line for line in text if any(x in line for x in ('FATAL EXCEPTION', 'Desktop startup failed', 'MainActivity', 'GothwadApplication', 'Caused by:', 'com.gothwad.computer'))]
message = '\n'.join(selected[-60:])[:10000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print(f'::{level}::{label} did not reach a stable desktop. {message}')
print(f'::{level}::UI hierarchy: ' + Path(f'release-ui-{label}.xml').read_text(errors='replace')[:4000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
PY
    return 1
  fi
  echo "$label: desktop visible after startup."
}

# A known old failure is reported as a warning but must not conceal a new failure.
if ! inspect_home old-release/GothwadComputer-v1.0.21-Release.apk published warning; then
  echo 'Published v1.0.21 did not pass this emulator smoke test.'
fi
inspect_home "$apk" candidate error
