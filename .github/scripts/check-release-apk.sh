#!/usr/bin/env bash
# Verify the published baseline, and optionally compare a new signed APK to it.
set -euo pipefail
baseline=$1
candidate=${2:-}
expected_code=${3:-}
aapt=$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -n 1)
apksigner=$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -n 1)
test -n "$aapt" && test -n "$apksigner" || { echo "Android build tools missing" >&2; exit 1; }
test -s "$baseline" || { echo "Baseline APK is missing" >&2; exit 1; }
metadata=$("$aapt" dump badging "$baseline" | sed -n '1p')
if [[ "$metadata" != *"name='com.gothwad.computer'"* || "$metadata" != *"versionCode='20'"* ]]; then
  echo "Unexpected package or version code in v1.0.20; investigate upgrade history." >&2
  exit 1
fi
baseline_certs=$("$apksigner" verify --print-certs "$baseline" | grep -E '^Signer #[0-9]+ certificate SHA-256 digest:' | sort)
test -n "$baseline_certs" || { echo "Could not read baseline signing certificate" >&2; exit 1; }
if [[ -n "$candidate" ]]; then
  test -s "$candidate"
  metadata=$("$aapt" dump badging "$candidate" | sed -n '1p')
  [[ "$expected_code" =~ ^[1-9][0-9]*$ ]] || { echo "Missing expected version code" >&2; exit 1; }
  if [[ "$metadata" != *"name='com.gothwad.computer'"* || "$metadata" != *"versionCode='$expected_code'"* ]]; then
    echo "Candidate package or version code is incorrect" >&2
    exit 1
  fi
  candidate_certs=$("$apksigner" verify --print-certs "$candidate" | grep -E '^Signer #[0-9]+ certificate SHA-256 digest:' | sort)
  if [[ -z "$candidate_certs" || "$candidate_certs" != "$baseline_certs" ]]; then
    echo "Release APK signing certificate differs from v1.0.20; refusing to publish." >&2
    exit 1
  fi
fi
echo "Release APK baseline verification passed."
