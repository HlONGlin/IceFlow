#!/usr/bin/env bash
set -euo pipefail

serial="${ANDROID_SERIAL:-emulator-5554}"
mkdir -p integration-results
trap 'adb -s "$serial" logcat -d > integration-results/logcat.txt 2>&1 || true' EXIT

adb -s "$serial" install -r integration-apks/dual/debug/app-dual-x86_64-debug.apk
adb -s "$serial" install -r integration-apks/androidTest/dual/debug/app-dual-debug-androidTest.apk
adb -s "$serial" shell am instrument -w -r -e networkToggle true \
  com.iceflow.test/androidx.test.runner.AndroidJUnitRunner \
  | tee integration-results/instrumentation.txt

# am instrument can exit zero even when tests fail or the test process crashes.
grep -Eq '^OK \([1-9][0-9]* tests?\)' integration-results/instrumentation.txt
