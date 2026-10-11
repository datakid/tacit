#!/usr/bin/env bash
adb logcat -c
status=0
: > device-tests.log
: > tests-ran.txt
for cls in ${1//,/ }; do
  ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$cls" --stacktrace >> device-tests.log 2>&1 || status=1
  find app/build/outputs/androidTest-results -name 'TEST-*.xml' 2>/dev/null | while read -r f; do
    grep -oE '<testsuite [^>]*>' "$f" | head -1
    grep -oE '<testcase [^>]*>' "$f"
    grep -A6 '<failure' "$f" | cut -c1-300
  done >> tests-ran.txt
done
adb logcat -d -v threadtime > logcat.txt
{
  echo "exit status: $status"
  echo "--- crash in logcat"
  grep -n -A45 "FATAL EXCEPTION" logcat.txt | head -150
  echo "--- instrumentation output"
  grep -nE "Process crashed|FAILED|AssertionError|Exception|Tests on|tests completed|> Task :app:connected" device-tests.log | head -60
  echo "--- clipboard service lines"
  grep -iE "clipboard" logcat.txt | grep -iE "deny|denied|not focused|access" | head -20
  echo "--- tests that ran"
  cat tests-ran.txt
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
