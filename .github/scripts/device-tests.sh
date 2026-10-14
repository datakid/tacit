#!/usr/bin/env bash
adb logcat -c
status=0
: > device-tests.log
: > tests-ran.txt
rm -f timeout-diagnostics.txt
for cls in ${1//,/ }; do
  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$cls" --stacktrace >> device-tests.log 2>&1 &
  gpid=$!
  waited=0
  while kill -0 "$gpid" 2>/dev/null && [ "$waited" -lt 300 ]; do
    sleep 5
    waited=$((waited + 5))
  done
  if kill -0 "$gpid" 2>/dev/null; then
    status=1
    echo "TIMEOUT in $cls" >> tests-ran.txt
    {
      adb root
      sleep 3
      adb wait-for-device
      pid=$(adb shell pidof app.tacit | tr -d '\r')
      echo "app pid: $pid"
      adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity|ResumedActivity"
      adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp|mFocusedWindow"
      adb shell kill -3 "$pid"
      sleep 4
      latest=$(adb shell 'ls -t /data/anr 2>/dev/null | head -1' | tr -d '\r')
      echo "anr file: $latest"
      adb shell "cat /data/anr/$latest" > anr-full.txt
      awk '/^"/{keep=($0 ~ /Instr|Test|main|Espresso/)} keep && (/^"/ || /^  at /)' anr-full.txt | head -120
    } > timeout-diagnostics.txt 2>&1
    kill "$gpid" 2>/dev/null
    wait "$gpid" 2>/dev/null
  else
    wait "$gpid" || status=1
  fi
  find app/build/outputs/androidTest-results -name 'TEST-*.xml' 2>/dev/null | while read -r f; do
    grep -oE '<testsuite [^>]*>' "$f" | head -1 | sed -E 's/.*name="app\.tacit\.([^"]*)".*tests="([0-9]+)".*failures="([0-9]+)".*errors="([0-9]+)".*skipped="([0-9]+)".*time="([^"]*)".*/\1 tests=\2 failures=\3 errors=\4 skipped=\5 time=\6s/'
    grep -oE '<testcase [^>]*>' "$f" | sed -E 's/<testcase name="([^"]*)".*time="([0-9.]+)".*/\1 \2/' | awk '$2 >= 5 {print "  slow: " $0}'
    awk '/<testcase /{match($0,/name="[^"]*"/); name=substr($0,RSTART,RLENGTH)} /<failure/{print "FAILED " name; n=5} n>0{print; n--}' "$f" | cut -c1-260
  done >> tests-ran.txt
done
adb logcat -d -v threadtime > logcat.txt
rm -rf tacit-shots
adb pull /sdcard/tacit-shots tacit-shots >/dev/null 2>&1 || true
{
  echo "exit status: $status"
  echo "--- timeout diagnostics"
  head -c 1200 timeout-diagnostics.txt 2>/dev/null
  echo "--- tests that ran"
  head -c 2600 tests-ran.txt
  echo "--- crash in logcat"
  grep -n -A45 "FATAL EXCEPTION" logcat.txt | head -100
  echo "--- clipboard service lines"
  grep -iE "clipboard" logcat.txt | grep -iE "deny|denied|not focused|access" | head -6
  echo "--- instrumentation output"
  grep -nE "Process crashed|FAILED|AssertionError|Tests on|tests completed" device-tests.log | head -20
} > device-summary.txt
msg=$(head -c 30000 device-summary.txt)
msg="${msg//'%'/'%25'}"
msg="${msg//$'\r'/'%0D'}"
msg="${msg//$'\n'/'%0A'}"
if [ "$status" -eq 0 ]; then
  echo "::notice title=Device tests passed::$msg"
else
  echo "::error title=Device tests failed::$msg"
fi
exit "$status"
