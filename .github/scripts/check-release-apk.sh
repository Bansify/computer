#!/usr/bin/env bash
# Check package, version and the owner-selected public signing certificate.
set -euo pipefail
candidate=$1
expected_code=$2
certificate_file=$3
[[ "$expected_code" =~ ^[1-9][0-9]*$ ]] || { echo "Invalid expected version code" >&2; exit 1; }
test -s "$candidate" && test -s "$certificate_file" || { echo "Missing APK or certificate pin" >&2; exit 1; }
expected=$(tr -d '\r\n' < "$certificate_file")
[[ "$expected" =~ ^[0-9a-f]{64}$ ]] || { echo "Invalid signing certificate pin" >&2; exit 1; }
aapt=$(find "$ANDROID_HOME/build-tools" -name aapt -type f | sort -V | tail -n 1)
apksigner=$(find "$ANDROID_HOME/build-tools" -name apksigner -type f | sort -V | tail -n 1)
test -n "$aapt" && test -n "$apksigner" || { echo "Android build tools missing" >&2; exit 1; }
metadata=$("$aapt" dump badging "$candidate" | sed -n '1p')
if [[ "$metadata" != *"name='com.gothwad.computer'"* || "$metadata" != *"versionCode='$expected_code'"* ]]; then
  echo "Candidate package or version code is incorrect" >&2
  exit 1
fi
if ! output=$("$apksigner" verify --print-certs "$candidate" 2>&1); then
  echo "APK signature verification failed: $output" >&2
  exit 1
fi
certs=$(printf '%s\n' "$output" | sed -nE 's/^(Signer #[0-9]+|V[0-9.]+ Signer:?) certificate SHA-256 digest: ([0-9a-fA-F]+)$/\2/p' | tr '[:upper:]' '[:lower:]' | sort -u)
if [[ -z "$certs" || "$certs" != "$expected" ]]; then
  echo "Signed APK does not match the owner-selected release certificate; refusing to publish." >&2
  exit 1
fi
echo "APK package, version and release signing certificate verified."
