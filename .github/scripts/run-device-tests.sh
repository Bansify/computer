#!/usr/bin/env bash
set +e
./gradlew connectedDebugAndroidTest --no-daemon > device-tests.log 2>&1
result=$?
cat device-tests.log
if [ "$result" -ne 0 ]; then
  tail -n 80 device-tests.log | while IFS= read -r line; do
    echo "::error::$line"
  done
fi
exit "$result"
