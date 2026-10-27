#!/usr/bin/env bash
status=0
rm -rf benchmark-out benchmark-pulled
mkdir -p benchmark-out
./gradlew :benchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR,LOW-BATTERY,NOT-PROFILEABLE,DEBUGGABLE,UNLOCKED \
  -Pandroid.testInstrumentationRunnerArguments.tacit.compilation=none \
  -Pandroid.testInstrumentationRunnerArguments.class=app.tacit.benchmark.TypingBenchmark,app.tacit.benchmark.ColdStartSectionsBenchmark \
  --stacktrace > benchmark-gradle.log 2>&1 || status=$?
find benchmark/build -name '*.json' -path '*additional_test_output*' -exec cp {} benchmark-out/ \; 2>/dev/null
if ! ls benchmark-out/*.json > /dev/null 2>&1; then
  adb pull /sdcard/Android/media/app.tacit.benchmark benchmark-pulled > /dev/null 2>&1 || true
  find benchmark-pulled -name '*.json' -exec cp {} benchmark-out/ \; 2>/dev/null
fi
find benchmark/build/outputs -name 'TEST-*.xml' 2> /dev/null | while read -r f; do
  awk '/<testcase /{match($0, /name="[^"]*"/); name=substr($0, RSTART, RLENGTH)} /<failure/{print "FAILED " name; n=7} n>0{print; n--}' "$f" | cut -c1-320
done > benchmark-failures.txt
{
  echo "exit status: $status"
  echo "Emulator numbers are a trend, not a measurement; this step never blocks."
  python3 .github/scripts/benchmark-summary.py benchmark-out
  if [ "$status" -ne 0 ]; then
    echo "--- failures"
    head -c 1800 benchmark-failures.txt
    echo "--- gradle"
    grep -nE "FAILED|Error|error:|Exception|ERRORS|What went wrong" benchmark-gradle.log | head -20 | cut -c1-240
  fi
} > benchmark-summary.txt
msg=$(head -c 3800 benchmark-summary.txt)
msg="${msg//'%'/'%25'}"
msg="${msg//$'\r'/'%0D'}"
msg="${msg//$'\n'/'%0A'}"
echo "::notice title=Benchmark on emulator, API 35 (trend only)::$msg"
exit "$status"
