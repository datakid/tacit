#!/usr/bin/env bash
runs="${1:-10}"
chmod +x gradlew
./gradlew installDebug installDebugAndroidTest --stacktrace > flake-install.log 2>&1 || { tail -60 flake-install.log; echo "::error title=Install failed::see log"; exit 1; }
adb logcat -c
: > flake-summary.txt
failed_runs=0
for run in $(seq 1 "$runs"); do
  adb shell am instrument -w -r -e class app.tacit.SettingsApplyTest app.tacit.test/app.tacit.TacitTestRunner > "flake-run-$run.txt" 2>&1
  if grep -q "INSTRUMENTATION_STATUS_CODE: -[12]" "flake-run-$run.txt"; then
    failed_runs=$((failed_runs + 1))
    echo "run $run FAILED" >> flake-summary.txt
    awk '/INSTRUMENTATION_STATUS: test=/{t=$0} /INSTRUMENTATION_STATUS_CODE: -[12]/{print "  " t}' "flake-run-$run.txt" | sort -u >> flake-summary.txt
    grep -E "INSTRUMENTATION_STATUS: stack=|AssertionError|Exception:" "flake-run-$run.txt" | head -4 | cut -c1-300 >> flake-summary.txt
  else
    echo "run $run ok" >> flake-summary.txt
  fi
  grep -E "^OK \(|^Tests run:" "flake-run-$run.txt" | head -1 >> flake-summary.txt
done
adb logcat -d -v threadtime > flake-logcat.txt
echo "failed runs: $failed_runs of $runs" >> flake-summary.txt
msg=$(head -c 3800 flake-summary.txt)
msg="${msg//'%'/'%25'}"
msg="${msg//$'\r'/'%0D'}"
msg="${msg//$'\n'/'%0A'}"
echo "::notice title=SettingsApplyTest x${runs}::$msg"
exit 0
