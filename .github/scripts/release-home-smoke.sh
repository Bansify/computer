#!/usr/bin/env bash
# Runs only inside a disposable GitHub emulator; not for an owner's device.
set -euo pipefail
./gradlew :app:assembleRelease -PlocalSign -PversionCode=21 -PversionName=1.0.21 --no-daemon --no-configuration-cache
apk=$(find app/build/outputs/apk/release -name '*.apk' -type f -print -quit)
test -n "$apk"
mkdir -p old-release
# Test the exact published APK as evidence, separately from the new candidate.
old_available=0
if gh release download v1.0.21 --pattern 'GothwadComputer-v1.0.21-Release.apk' --dir old-release; then
  old_available=1
else
  echo '::warning::Could not download the published v1.0.21 APK for comparison; candidate testing is still required.'
fi

inspect_home() {
  local package=$1 label=$2 level=$3 ready=0 attempt
  adb install "$package" || { echo "::${level}::${label}: APK installation failed"; return 1; }
  adb shell am force-stop com.gothwad.computer || { echo "::${level}::${label}: force-stop failed"; return 1; }
  adb logcat -c
  adb shell input keyevent KEYCODE_WAKEUP || true
  adb shell wm dismiss-keyguard || true
  adb shell am start -W -n com.gothwad.computer/.MainActivity
  capture_screen() {
    adb shell rm -f /data/local/tmp/gothwad-window.xml
    timeout 20s adb shell uiautomator dump /data/local/tmp/gothwad-window.xml > "release-dump-${label}.log" 2>&1 || true
    adb exec-out cat /data/local/tmp/gothwad-window.xml > "release-ui-${label}.xml" 2>/dev/null || true
    # Android 10 uiautomator may never become idle; dumpsys can inspect a live view tree.
    if ! grep -q '<hierarchy' "release-ui-${label}.xml"; then
      adb shell dumpsys activity top > "release-top-${label}.log" 2>&1 || true
      cat "release-top-${label}.log" > "release-ui-${label}.xml"
    fi
  }
  for attempt in $(seq 1 8); do
    capture_screen
    if grep -q 'id/safe_retry' "release-ui-${label}.xml"; then
      echo "::${level}::${label}: Recovery appeared instead of the normal desktop."
      break
    fi
    if grep -q 'id/btn_start' "release-ui-${label}.xml"; then
      ready=1
      break
    fi
    sleep 1
  done
  if [ "$ready" -eq 1 ]; then
    # A frame appearing once is not proof that startup remains healthy.
    sleep 8
    capture_screen
    if ! grep -q 'id/btn_start' "release-ui-${label}.xml" || grep -q 'id/safe_retry' "release-ui-${label}.xml"; then
      ready=0
    fi
  fi
  adb logcat -d -v brief > "release-logcat-${label}.log"
  adb uninstall com.gothwad.computer >/dev/null || { echo "::error::Could not clean up emulator installation"; exit 1; }
  if [ "$ready" -ne 1 ]; then
    LEVEL="$level" LABEL="$label" python3 - <<'PY'
import os
from pathlib import Path
label, level = os.environ['LABEL'], os.environ['LEVEL']
text = Path(f'release-logcat-{label}.log').read_text(errors='replace').splitlines()
selected = [line for line in text if any(x in line for x in ('FATAL EXCEPTION', 'Desktop startup failed', 'MainActivity', 'GothwadApplication', 'Caused by:', 'com.gothwad.computer'))]
message = '\n'.join(selected[-60:])[:10000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print(f'::{level}::{label} did not reach a stable desktop. {message}')
ui = Path(f'release-ui-{label}.xml').read_text(errors='replace')
interesting = '\n'.join(line for line in ui.splitlines() if any(item in line for item in ('View Hierarchy', 'btn_start', 'safe_retry', 'MainActivity')))[:4000] or ui[:4000]
print(f'::{level}::UI hierarchy: ' + interesting.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
print(f'::{level}::UI dump command: ' + Path(f'release-dump-{label}.log').read_text(errors='replace')[:2000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A'))
PY
    return 1
  fi
  echo "$label: desktop visible after startup."
}

# A known old failure is reported as a warning but must not conceal a new failure.
if [ "$old_available" -eq 1 ]; then
  if ! inspect_home old-release/GothwadComputer-v1.0.21-Release.apk published warning; then
    echo 'Published v1.0.21 did not pass this emulator smoke test.'
  fi
fi
inspect_home "$apk" candidate error
