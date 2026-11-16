#!/usr/bin/env bash
# GitHub disposable emulator only. This never runs against a user's device.
set -euo pipefail
./gradlew :app:assembleRelease -PlocalSign --no-daemon --no-configuration-cache
apk=$(find app/build/outputs/apk/release -name '*.apk' -type f -print -quit)
test -n "$apk"
adb install -r "$apk"
adb shell am force-stop com.gothwad.computer
adb logcat -c
adb shell am start -W -n com.gothwad.computer/.MainActivity
ready=0
for attempt in $(seq 1 30); do
  adb shell uiautomator dump /sdcard/gothwad-window.xml >/dev/null 2>&1 || true
  adb exec-out cat /sdcard/gothwad-window.xml > release-ui.xml 2>/dev/null || true
  if grep -q 'com.gothwad.computer:id/safe_retry' release-ui.xml; then
    echo '::error::Minified release started in recovery instead of the desktop.'
    break
  fi
  if grep -q 'com.gothwad.computer:id/btn_start' release-ui.xml; then
    ready=1
    break
  fi
  sleep 1
done
adb logcat -d -v brief > release-logcat.log
if [ "$ready" -ne 1 ]; then
  echo '::error::The release desktop was not visible on the emulator.'
  python3 - <<'PY'
from pathlib import Path
text = Path('release-logcat.log').read_text(errors='replace').splitlines()
selected = [line for line in text if any(x in line for x in ('FATAL EXCEPTION', 'Desktop startup failed', 'MainActivity', 'GothwadApplication', 'Caused by:', 'com.gothwad.computer'))]
message = '\n'.join(selected[-70:])[:12000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print('::error::' + message)
print('::error::UI hierarchy: ' + Path('release-ui.xml').read_text(errors='replace')[:4000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
PY
  exit 1
fi
echo 'Minified release desktop appeared successfully.'
