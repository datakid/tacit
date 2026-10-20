#!/usr/bin/env bash
set -euo pipefail

if [ "${GITHUB_REF:-}" = "refs/heads/main" ]; then
  echo "guard-check: on main, nothing to compare"
  exit 0
fi

git rev-parse --verify --quiet refs/remotes/origin/main > /dev/null || git fetch --no-tags origin main:refs/remotes/origin/main
base="$(git merge-base origin/main HEAD)"
echo "guard-check: base ${base}, head $(git rev-parse HEAD)"

count_tests() {
  { git grep -c '@Test' "$1" -- app/src/test app/src/androidTest app/src/sharedTest || true; } | awk -F: '{ total += $NF } END { print total + 0 }'
}

failed=0

base_tests="$(count_tests "$base")"
head_tests="$(count_tests HEAD)"
echo "guard-check: @Test count ${base_tests} at base, ${head_tests} at head"
if [ "$head_tests" -lt "$base_tests" ]; then
  echo "::error title=Guard failed::@Test count dropped from ${base_tests} to ${head_tests}. Tests must not be deleted or renamed."
  failed=1
fi

weakened="$({ git diff "$base" HEAD -U0 -- '*.kt' || true; } | { grep -E '^\+' || true; } | { grep -v '^+++' || true; } | { grep -E '@([A-Za-z0-9_]+\.)*Ignore|Assume\.|assumeTrue|assumeFalse|assumeThat|assumeNoException' || true; })"
if [ -n "$weakened" ]; then
  echo "$weakened"
  echo "::error title=Guard failed::A change adds @Ignore, Assume, assumeTrue or assumeFalse to Kotlin sources."
  failed=1
fi

protected='^(\.github/workflows/|\.github/scripts/|app/src/test/java/app/tacit/RepoRulesTest\.kt$|app/src/test/java/app/tacit/core/KeystrokeBudgetTest\.kt$|app/src/sharedTest/.*/Budgets\.kt$|app/src/test/resources/golden/|app/build\.gradle\.kts$|app/proguard-rules\.pro$|app/src/main/AndroidManifest\.xml$|app/lint-baseline\.xml$)'
touched="$({ git diff --name-only "$base" HEAD || true; } | { grep -E "$protected" || true; } | paste -sd ',' - | sed 's/,/, /g')"
if [ -n "$touched" ]; then
  echo "::warning title=Protected files changed::${touched}"
fi

exit "$failed"
