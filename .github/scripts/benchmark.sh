#!/usr/bin/env bash
status=0
rm -rf benchmark-out benchmark-pulled
mkdir -p benchmark-out
: > benchmark-gradle.log
: > benchmark-failures.txt
: > benchmark-ran.txt
: > benchmark-exit.txt
for cls in app.tacit.benchmark.TypingBenchmark app.tacit.benchmark.ColdStartSectionsBenchmark; do
  short=${cls##*.}
  rm -rf benchmark/build/outputs/connected_android_test_additional_output benchmark/build/outputs/androidTest-results
  start=$SECONDS
  timeout -k 20 600 ./gradlew :benchmark:connectedBenchmarkAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR,LOW-BATTERY,NOT-PROFILEABLE,DEBUGGABLE,UNLOCKED \
    -Pandroid.testInstrumentationRunnerArguments.tacit.compilation=none \
    -Pandroid.testInstrumentationRunnerArguments.class="$cls" \
    --stacktrace >> benchmark-gradle.log 2>&1
  code=$?
  echo "$short exit $code after $((SECONDS - start)) s" >> benchmark-exit.txt
  if [ "$code" -ne 0 ]; then
    status=1
    adb shell am force-stop app.tacit > /dev/null 2>&1
    adb shell am force-stop app.tacit.benchmark > /dev/null 2>&1
    ./gradlew --stop > /dev/null 2>&1
  fi
  find benchmark/build -name '*benchmarkData.json' 2> /dev/null | while read -r f; do
    cp "$f" "benchmark-out/$short-$(basename "$f")"
  done
  find benchmark/build/outputs -name 'TEST-*.xml' 2> /dev/null | while read -r f; do
    grep -ho '<testcase [^>]*name="[^"]*"' "$f" | sed -E 's/.*name="([^"]*)"/ran \1/' >> benchmark-ran.txt
    awk '/<testcase /{match($0, /name="[^"]*"/); name=substr($0, RSTART, RLENGTH)} /<failure/{print "FAILED " name; n=7} n>0{print; n--}' "$f" | cut -c1-320 >> benchmark-failures.txt
  done
done
if ! ls benchmark-out/*.json > /dev/null 2>&1; then
  adb pull /sdcard/Android/media/app.tacit.benchmark benchmark-pulled > /dev/null 2>&1 || true
  find benchmark-pulled -name '*benchmarkData.json' -exec cp {} benchmark-out/ \; 2>/dev/null
fi
find benchmark/build benchmark-pulled -name '*.json' 2> /dev/null | head -8 > benchmark-found.txt
{
  echo "exit status: $status"
  echo "Emulator numbers are a trend, not a measurement; this step never blocks."
  cat benchmark-exit.txt
  cat benchmark-ran.txt
  python3 .github/scripts/benchmark-summary.py benchmark-out
  if ! ls benchmark-out/*.json > /dev/null 2>&1; then
    echo "--- json files seen"
    cat benchmark-found.txt
  fi
  if [ "$status" -ne 0 ]; then
    echo "--- failures"
    head -c 1500 benchmark-failures.txt
    echo "--- gradle"
    grep -nE "FAILED|Error|error:|Exception|ERRORS|What went wrong" benchmark-gradle.log | head -16 | cut -c1-240
  fi
} > benchmark-summary.txt
msg=$(head -c 3800 benchmark-summary.txt)
msg="${msg//'%'/'%25'}"
msg="${msg//$'\r'/'%0D'}"
msg="${msg//$'\n'/'%0A'}"
echo "::notice title=Benchmark on emulator, API 35 (trend only)::$msg"
exit "$status"
