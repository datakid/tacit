# Tacit 1.1 execution plan for the coding agent

You are implementing the next Tacit release with write access to `github.com/nickburser122/tacit`. This file is the complete brief. Read all of it before your first commit, and re-read section 1 before every milestone. If anything here conflicts with `docs/HANDOFF.md` "Owner's rules", the owner's rules win. If this plan is ambiguous, stop and ask in the PR under "Needs owner". Do not guess.

The goals, in priority order:

1. No regressions.
2. Fix the real bugs listed in section 4.
3. Make file search fast and trustworthy.
4. Speed up everything else.
5. Build a stronger shortcut maker.
6. Make Compact and Comfortable both work well everywhere.

The app drawer stays as it is today: opt-in and off by default. Section 5, M13, explains why.

---

## 0. Before you write any code

1. Commit this file as `docs/PLAN-1.1.md`. It is the first commit of the M8 branch.
2. Read these files completely before changing anything:
   * `README.md`, `docs/HANDOFF.md`, `docs/decisions.md` (all 64 decisions), `docs/limits.md`, `docs/SIGNING.md`
   * `app/build.gradle.kts`, `.github/workflows/android.yml`, `.github/scripts/*.sh`
   * `app/src/test/java/app/tacit/RepoRulesTest.kt`, `app/src/test/java/app/tacit/core/KeystrokeBudgetTest.kt`, and everything in `app/src/sharedTest`
3. Each milestone also lists the files you must read before touching that area. Read them first, every time.
4. The newest decision today is 64. Your new decisions start at 65, are numbered in order, and are never renumbered.
5. Running things:
   * If you can run Gradle locally (JDK 17, Android SDK), run `./gradlew assembleDebug testDebugUnitTest lintDebug` before every push.
   * If you cannot, say so in the PR and rely on CI.
   * Never write that something passed unless you saw the output. Paste the relevant lines.
   * Label emulator results as "emulator".

---

## 1. Rules

### 1.1 Owner rules (from HANDOFF, enforced by `RepoRulesTest`)

* No comments in Kotlin. Explanations go in `docs/decisions.md`.
* No INTERNET permission and no runtime dependencies (`implementation(...)` count stays 0).
* No Compose, AppCompat, RecyclerView, coroutines or DI frameworks.
* No new work in `Application.onCreate`. No allocation per bind in adapters.
* `KeystrokeBudgetTest` must not regress.
* Signing keys never go into the repo or the workflow, and the workflow never reads `secrets.`.
* Every new string goes in `values/strings.xml` and `values-ar/strings.xml`, with a real Arabic translation:
  * Modern Standard Arabic, with the same placeholders (`%1$s`)
  * Arabic punctuation (، ؟) where natural
  * RTL must work
* Release notes are extra concise and atomic. One PR per milestone. Merge only when every CI job is green, and only the owner merges.

### 1.2 Agent rules (new for this release)

1. **Never** push to `main` or `beta`, merge a PR, delete a branch you didn't create, force-push a branch someone else pushed to, create tags, or publish or edit releases.
2. **Never** delete or rename a test, and never weaken an assertion. Also never add `@Ignore`, `Assume.*`/`assumeTrue`/`assumeFalse`, retries, or `Thread.sleep` to make a test pass. If a test is wrong, explain it under "Needs owner" and stop.
3. **Never** raise a budget constant or edit a golden file to make CI green. If a change intentionally alters ranking, file results or a budget, put the before/after diff in the PR under "Guard changes" and stop for owner approval.
4. **Stop and ask** (PR section "Needs owner") before doing any of these:
   * adding a permission or a `<queries>` entry not named in this plan
   * adding any dependency, including test dependencies
   * changing an on-disk format without an automatic migration and a migration test
   * changing ranking outside a task that explicitly allows it
   * touching signing, `publish-release.sh`, or release logic
5. Only start a milestone after the previous milestone's PR is merged into `main`. If it isn't merged, stop and report. Don't stack branches.
6. Keep each commit focused and buildable. Use commit messages like `M9: coalesce refresh requests (B06)`.
7. Write any code in files under `app/src/main/java/app/tacit/<area>/`, following the existing package layout. Pure logic goes in `core/` so it can be unit-tested.
8. Read before you write. If the code differs from what this plan describes (it may have changed), trust the code, adapt, and note the difference in the PR.

### 1.3 Versions (decision 65, write it in M8)

The existing formula is `versionCode = major*10000 + minor*100 + patch`. If every milestone took the next minor with a `-beta` suffix, the stable release couldn't get a higher versionCode than the last beta. So use patch betas and a clean stable release:

| Milestone | versionName | versionCode |
|---|---|---|
| M8 | 1.0.1-beta | 10001 |
| M9 | 1.0.2-beta | 10002 |
| M10 | 1.0.3-beta | 10003 |
| M11 | 1.0.4-beta | 10004 |
| M12 | 1.0.5-beta | 10005 |
| M13 | 1.1.0 | 10100 |

Every milestone adds these files, as `RepoRulesTest` requires:

* `docs/release-notes/v<versionName>.md`
* `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
* `fastlane/metadata/android/ar/changelogs/<versionCode>.txt`

Each changelog is 500 bytes or less, in both languages.

---

## 2. Workflow for every milestone

1. Branch from the latest `main`: `m8-safety-net`, `m9-bug-sweep`, `m10-file-search`, `m11-density-a11y`, `m12-shortcut-maker`, `m13-speed-rc`.
2. Read the milestone's must-read files.
3. Do the tasks in the order listed. Every bug fix comes with a test that fails before the fix and passes after, unless the plan says the fix can't be tested on JVM or emulator. In that case, say so.
4. Push and open the PR with the template below. Fix CI until every job is green.
5. Record the "Perf and size" annotation numbers from CI before and after: `TACIT_KEYSTROKE_JVM`, APK sizes, and any new `TACIT_*` lines.
6. Update `docs/decisions.md`, `README.md` (features and limits), `docs/limits.md`, and the HANDOFF on-device checklist wherever behaviour changed.

PR body template (use it exactly):

```
## What changed
## Bugs fixed (IDs from PLAN-1.1 section 4)
## Tests run (pasted output; say local, CI, or emulator)
## Perf before → after (same machine or same CI job)
## Guard changes (protected files touched, with reason)
## Risks
## Decisions added
## Needs owner
```

---

## 3. Guardrails you build in M8 and must keep

1. **Protected files.** Any PR touching these must list them under "Guard changes":
   * `.github/workflows/**`, `.github/scripts/**`
   * `app/src/test/java/app/tacit/RepoRulesTest.kt`
   * `app/src/test/java/app/tacit/core/KeystrokeBudgetTest.kt`
   * `app/src/sharedTest/**/Budgets.kt`
   * `app/src/test/resources/golden/**`
   * `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
   * `app/lint-baseline.xml`
2. **CI guard script** `.github/scripts/guard-check.sh`, run as the first step of the `build` job. Checkout uses `fetch-depth: 0`. It does four things:
   * Finds `base=$(git merge-base origin/main HEAD)`. On `main` itself, it skips.
   * Counts `@Test` with `git grep -c '@Test' <rev> -- app/src/test app/src/androidTest app/src/sharedTest` for both `base` and `HEAD`, and fails if HEAD's count is lower.
   * Fails if `git diff base HEAD -- '*.kt'` adds a line matching `@Ignore`, `Assume\.`, `assumeTrue` or `assumeFalse`.
   * Prints a `::warning title=Protected files changed::` listing any protected paths in `git diff --name-only base HEAD`.
3. **Golden ranking test** `RankingGoldenTest`, created in M8 from the unchanged 1.0.0 code. It covers about 80 queries over `Fixtures.all()`, using `SearchEngine.search(q, 8)`, plus about 20 composer-level queries through `ResultComposer` (calculator, typed actions, phone, URL, email, web row).
   * Expected output lives in `app/src/test/resources/golden/ranking.tsv`. Each line is `query<TAB>key1@score1|key2@score2|...`.
   * The test compares exact keys, order and scores. On mismatch, the failure message prints the full actual TSV.
   * To generate the file: commit the test with an empty file, let CI fail, and copy the actual TSV out of the failure message. This must happen before any `app/src/main` change in M8.
   * Queries must include: `y, yt, you, gos, ag, asm gou, asmaa, wfi, wi fi, blutooth, bluetoth, set, sett, cal, cam, zoe, zoë, mul, أسماء, اسما, جودة`, every entry of `Fixtures.typingScript`, two-word reversed orders, a 1-letter query, a 12-letter query, and an alias-exact query (add an alias to one fixture item inside the test).
4. **Golden file-search test** `FileGoldenTest`, also created in M8 from unchanged code. It runs `FileMatcher.search` on a deterministic fixture table of 50,000 entries over 100 queries, expecting the top 10 as `path@score`, stored in `golden/files.tsv`.
   * Covers: plain terms, two terms, `ext:pdf`, `*.jpg`, an implicit extension (`invoice pdf`), `in:download`, an Arabic term, a folder name, and a query with no hits.
   * Freeze time: make the recency calculation injectable (`now: Long` parameter with default `System.currentTimeMillis()`) and pass a fixed `now` in tests. This is the only `app/src/main` change allowed in M8, and it must not change behaviour.
5. **Fixture generator** `app/src/sharedTest/java/app/tacit/fixtures/FileFixtures.kt`. It uses `java.util.Random(42)`, word lists (English plus 20 Arabic words), about 30 extensions, depth 1–6 folders under `/storage/emulated/0`, about 8% folders, sizes 0–50 MB, and modified times spread over 3 years before a fixed `NOW`. It builds a `FileTable` directly.
6. **Budgets file** `app/src/sharedTest/java/app/tacit/fixtures/Budgets.kt` holds every performance limit. `KEYSTROKE_JVM_P95_MS = 25.0` moves here from `KeystrokeBudgetTest`, with the same value, and the test reads it. New budgets are added here only with owner approval.
7. **Backup compatibility** `BackupCompatTest`, a device test. The fixture `app/src/androidTest/assets/backup-1.0.0.json` is hand-written to match the 1.0.0 `Backup.export()` format: `tacit_backup: 1`, prefs, 3 aliases (one shared), 2 snippets, and one quicklink of each 1.0.0 type. It imports into a clean state and asserts every alias, snippet, quicklink and pref matches. Every later milestone that touches the backup keeps this passing.
8. **Lint gate.** Set `lint { abortOnError = true; warningsAsErrors = false; checkReleaseBuilds = false; baseline = file("lint-baseline.xml") }`.
   * Generate the baseline from unchanged code. If you can't run Gradle locally, add a temporary `workflow_dispatch`-only job that runs `./gradlew updateLintBaseline` and uploads `app/lint-baseline.xml` as an artifact. Commit the file, then remove the job in the same PR.
   * After that, new lint errors fail CI.
9. **StrictMode trend.** In `TacitTestRunner`, override `callApplicationOnCreate(app)`. Before calling super, set a `StrictMode.ThreadPolicy` (detectDiskReads, detectDiskWrites, detectNetwork, penaltyLog) and a `VmPolicy` (detectLeakedClosableObjects, detectActivityLeaks, penaltyLog).
   * `device-tests.sh` counts `StrictMode policy violation` in `logcat.txt` and prints the count plus the 10 most frequent `at app.tacit.` frames under them in the summary notice.
   * Record the M8 counts per API level in `docs/strictmode-baseline.txt`. Later milestones must not raise them. This is reported, not a hard failure.

---

## 4. Bug register

Severity: High, Med or Low. Confidence: "confirmed" means read in the code, "verify" means check it before fixing. Every fix needs a test where it's feasible.

**B01 High, confirmed: the file index never refreshes on its own.** `rescan()` only runs from Settings actions. When the user turns on Files, chooses "All files access" and grants it, Tacit only rebuilds the Settings view on return, so no scan starts and file search stays empty. Files downloaded later never appear until the user taps Rescan. Fix in M10 (auto-refresh policy). [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/app/src/main/java/app/tacit/ui/SettingsActivity.kt)

**B02 High, confirmed: file search is blocked while a scan runs.** `search()` and `rescan()` post to the same `tacit-files` HandlerThread, so every query waits for the whole scan to finish. Users read this as "not found". Fix in M10. [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/app/src/main/java/app/tacit/files/FileIndex.kt)

**B03 Med, confirmed: Settings shows "0 files" after a cold start.** `count()` returns `table?.count ?: 0`, and `table` is only loaded by the first search. Fix in M10 by storing the count in the file header and reading the header off the main thread.

**B04 High (perf), confirmed: a Regex is compiled on every call.** `Normalizer.splitCamel` calls `raw.replace(Regex(...))` each time. It runs for every name of every `SearchItem` on every rebuild and on every `IndexCache.read` at cold start. Fix in M9 by hoisting it to a `private val`. [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/app/src/main/java/app/tacit/core/Normalizer.kt)

**B05 Med, confirmed: cached contacts and quicklinks lose their extra data.** `IndexCache` doesn't persist `SearchItem.extra`, so contacts restored at cold start have no `ContactInfo`.
* The adapter still shows Call, Message and WhatsApp (`info == null` passes the visibility check), but `onContactAction` returns early, so the buttons do nothing until the background refresh finishes.
* Cached quicklink rows lose `extra = link.type`, which affects their icon.
* Fix in M9 with IndexCache v4 (spec in M9). [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/app/src/main/java/app/tacit/IndexCache.kt)

**B06 Med, confirmed: refresh storms.** `Graph.requestRefresh()` isn't coalesced, unlike `requestRebuild()`. Two things call it constantly:
* The contacts `ContentObserver`, registered with descendants enabled, which fires repeatedly during sync.
* Every `LauncherApps.Callback` event (a Play Store batch update fires dozens).

Each call queues a full `refreshAll()`: apps, contacts, settings, snippets, quicklinks, rebuild, and a full `index.bin` write. Fix in M9. [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/app/src/main/java/app/tacit/Graph.kt)

**B07 Med, confirmed: icons go stale and load slowly.** The icon cache is only cleared on settings changes, so an app update keeps its old icon until the process dies. Icons also render on `Graph.worker`, behind refresh work, so drawer icons load slowly after a package event. Fix in M9 (eviction) and M13 (dedicated thread). [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/app/src/main/java/app/tacit/ui/IconCache.kt)

**B08 Med, confirmed: WhatsApp buttons go stale.** `refreshMessengers()` only runs in `buildViews()`. Installing or uninstalling WhatsApp or WhatsApp Business later leaves the contact buttons out of date. Fix in M9. [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/app/src/main/java/app/tacit/ui/SearchActivity.kt)

**B09 Med, confirmed: disk I/O on the main thread per keystroke.** `aliasedFileRows()` calls `File(...).exists()` for each matching file alias on every keystroke. Fix in M9.

**B10 Low (perf), confirmed: two list updates per keystroke.** In `publish()`, `adapter.selected = ...` triggers `notifyDataSetChanged()`, and `submit()` triggers it again. `Graph.prefs.barAtBottom` is also read three times per publish. Fix in M9.

**B11 Med, confirmed path, effect unverified: folder results are broken in "Choose folders" mode.** `TreeScanner` stores folders as `"$name/"` with no document URI. `FileTable.pathOf` then builds `tree:content://…/Name`, which is neither a file path nor a content URI. Open, Open folder, Copy path and Create quicklink all receive this garbage, and `Actions.openFile` passes it to `TacitFileProvider.uriFor(File(...))`. Fix in M10 (index v2 stores a document URI for folders). Test on JVM with `FileTable`. [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/app/src/main/java/app/tacit/files/TreeScanner.kt)

**B12 Low, confirmed: scan errors leave the index stuck.** `rescan()` has no try/finally. An exception leaves `indexing = true`, never calls `done`, and kills the process from the files thread. Fix in M10.

**B13 Low (perf), confirmed: avoidable allocations in the file matcher.** `FileMatcher.search` builds `".$extension"` per entry when filtering by extension, and `scoreName` calls `substringBeforeLast` per match. Fix in M10. [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/app/src/main/java/app/tacit/files/FileMatcher.kt)

**B14 Med, confirmed: clipboard capture waits behind other work.** Capture runs on `Graph.worker`, behind `IndexCache.read` and `refreshAll` at cold start and behind every refresh. Android 10+ only allows clipboard reads while Tacit is focused, so a late read can miss the clip. Fix in M9.

**B15 Med, verify: pinned home-screen shortcuts aren't updated when a quicklink changes.** Grep `QuicklinksActivity.kt`, `QuicklinkStore.kt` and `PinShortcut.kt` for `updateShortcuts` and `disableShortcuts`. If neither exists, renaming a quicklink leaves the old label on the home screen, and deleting one leaves a dead shortcut. Fix in M9.

**B16 Low, confirmed: backup import has three problems.**
* It reads the file, parses JSON and writes all stores on the main thread.
* It replaces all snippets and quicklinks without asking.
* It doesn't apply theme or icon changes until restart.

Fix in M9.

**B17 Low, confirmed: the hidden-items dialog shows package names instead of app labels.** Fix in M9.

**B18 Low, confirmed: `ResultAdapter` declares stable IDs from `key.hashCode()`.** Hash collisions between file paths are possible. Fix in M9: return `false` from `hasStableIds()` after checking nothing reads `getItemId`.

**B19 Med, confirmed (HANDOFF): narrow phones truncate contact names.** Four 48 dp contact buttons leave too little room. Fix in M11.

**B20 Med, confirmed: CI problems.**
* `ubuntu-latest` moves to Ubuntu 26 on 2026-10-19.
* Node 20 actions are deprecated.
* `push` and `pull_request` both trigger on every PR branch, so PRs run twice.
* There's no `concurrency` group, so two pushes to `main` can race `publish-release.sh`.

Fix in M8. [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/.github/workflows/android.yml)

**B21 Low, confirmed: lint can never fail CI** (`abortOnError = false`). Fix in M8 (section 3.8). [Source](https://raw.githubusercontent.com/nickburser122/tacit/main/app/build.gradle.kts)

**B22 Unknown, open: `SettingsApplyTest` flakes.** It fails on a different test each time and passes on re-run. Investigate in M8.

**B23 Low, confirmed: rescans aren't coalesced.** Toggling "hidden files" twice, or choosing a folder right after enabling Files, queues several full scans. Fix in M10.

**B24 Low (perf), verify: possible allocations in the `Scorer` hot loop.** `for ((index, word) in name.words.withIndex())` may allocate an iterator per item per keystroke. Check the bytecode with `javap -c` on `Scorer`. If `IndexedValue` or `IndexingIterable` appears, replace it with an index loop. If the compiler already turns it into an index loop, leave it and say so. Fix in M13.

**B25 Low, verify: long snippets are cut in the cache.** `IndexCache` cuts `payload` to 16,000 characters. If `SnippetSource` puts the snippet body in `payload`, a long snippet copied before the first refresh finishes is truncated. Fix with IndexCache v4 in M9 (length-prefixed UTF-8, no truncation).

**B26 Low, confirmed: the 500,000-file limit is silent.** Users get no message when it's hit. Fix in M10.

**B27 Low, confirmed: `isDefaultHome()` makes a PackageManager call on every back press.** Fix in M9 by caching the result per `onResume`.

---

## 5. Milestones

### M8: Safety net and CI (1.0.1-beta). No behaviour changes.

Must read first: the files in section 0, plus `TacitTestRunner.kt`, `ScreenshotTest.kt` and `SettingsApplyTest.kt`.

Do these in order:

1. **Pin the runner first, as its own commit, pushed immediately.** Set `runs-on: ubuntu-24.04` in both jobs.
2. **Update the GitHub Actions.**
   * For each action (`actions/checkout`, `actions/setup-java`, `gradle/actions/setup-gradle`, `actions/upload-artifact`, `reactivecircus/android-emulator-runner`), open the latest major version's `action.yml` and check `runs.using`.
   * Move to the newest major that uses node24, or that doesn't use node20 for composite actions. Pin major tags, never `@main`.
   * Read each action's changelog for breaking changes, such as upload-artifact naming rules, and adapt.
3. **Fix the triggers.**
   * Change `on.push` to `branches: [main, beta]`, and keep `pull_request` and `workflow_dispatch`.
   * Add `concurrency: { group: ${{ github.workflow }}-${{ github.ref }}, cancel-in-progress: ${{ github.ref != 'refs/heads/main' }} }`.
4. **Add the guard script and `fetch-depth: 0`** (section 3.2).
5. **Add the test guardrails:** `FileFixtures`, `Budgets.kt`, `RankingGoldenTest` with `golden/ranking.tsv`, `FileGoldenTest` with `golden/files.tsv`, the injectable `now` in `FileMatcher`, and `BackupCompatTest`. Add `BackupCompatTest` to the CI device-test list.
6. **Lint baseline and gate** (section 3.8).
7. **StrictMode** in the test runner, the reporting in `device-tests.sh`, and `docs/strictmode-baseline.txt`.
8. **Put the remaining device tests in CI.** Add `IntentContractTest`, `PlatformContractTest` and `EntryPointsUiTest` to the CI list. If any fail on emulators for reasons outside Tacit (missing apps on the `google_apis` image), leave that class out and list the exact failure under "Needs owner".
9. **B22, the `SettingsApplyTest` flake:**
   1. Add a temporary `workflow_dispatch` job that runs only `SettingsApplyTest` 10 times on API 35 and uploads logcat.
   2. Add an androidTest helper `Idle.await()`. It posts a `CountDownLatch` to `Graph.worker`, then to `Graph.main`, and waits up to 5 s for both. Call it wherever a test changes a pref and then asserts UI or engine state.
   3. Check whether failing tests read prefs written with `apply()` before the write finished, assert before `refreshLook()` runs in `onResume`, or race `recreate()`. Fix the cause in the test (idle waits), not with sleeps or retries.
   4. Write what you found as a decision. Remove the temporary job.
10. **Write decisions:** 65 (versions), 66 (guard script and protected files), 67 (golden tests and how to regenerate them only with owner approval), 68 (lint baseline), 69 (StrictMode trend).
11. **Done when:** CI is green on all jobs, the golden files are committed, `app/src/main` changes are limited to the injectable `now`, and the PR shows the M8 baseline numbers.

### M9: Bug sweep (1.0.2-beta)

Must read first: `Graph.kt`, `IndexCache.kt`, `SearchItem.kt`, `Normalizer.kt`, `SearchActivity.kt`, `ResultAdapter.kt`, `IconCache.kt`, `AppSource.kt`, `ContactSource.kt`, `ClipboardStore.kt`, `Backup.kt`, `SettingsActivity.kt`, `PinShortcut.kt`, `QuicklinksActivity.kt`, `QuicklinkStore.kt`, `SnippetSource.kt`.

1. **B04.** Hoist the camel-case Regex into a `private val`.
   * Add an ASCII fast path to `Normalizer.normalize`: if every char is below `0x80`, return `input.lowercase()`.
   * Add `NormalizerEquivalenceTest`. It keeps a copy of the 1.0.0 algorithm in test sources and compares outputs for every fixture name plus 10,000 random ASCII and mixed strings from `Random(7)`. Outputs must be identical.
2. **B06, refresh coalescing.**
   * Replace `requestRefresh()` with `requestRefresh(sources: Int)`, where `sources` is a bit mask: APPS=1, CONTACTS=2, SETTINGS=4, SNIPPETS=8, QUICKLINKS=16, ALL=31.
   * Pending bits accumulate in an `AtomicInteger`. The first request schedules `worker.postDelayed(400 ms)`. That runnable takes the bits with `getAndSet(0)`, reloads only those sources, and then calls `rebuild()` once.
   * Any APPS change also reloads SETTINGS, because settings depend on apps.
   * The contacts observer requests CONTACTS. LauncherApps callbacks request APPS. Existing callers with no argument mean ALL.
   * Add a unit-testable `RefreshCoalescer` in `core/` with an injected scheduler. Test: 50 requests in a burst produce 1 run, and the union of bits is preserved.
3. **B05 and B25, IndexCache v4.**
   * Bump `VERSION` to 4. A v3 file returns null, which is today's fallback.
   * Store `payload` as length-prefixed UTF-8 (`writeInt` then bytes), with no truncation.
   * For CONTACT items, also store: lookupKey, contactId, number count and numbers, primary (empty string means null), whatsappDataId and whatsappBusinessDataId (−1 means null), starred.
   * For quicklink ACTION items whose `extra` is a `QuicklinkType`, store the type name. For any other `extra`, write one marker byte: 0 none, 1 contact, 2 quicklink type.
   * Write a JVM round-trip test using a temp dir and a fake `Context`-free API. Refactor `IndexCache` so the stream read/write functions take `DataInput`/`DataOutput` and can be tested without Android.
4. **B07, icon eviction.** In `AppSource`'s callback, for added, changed, removed, available and unavailable events, post to main a call to `Graph.icons.evictPackage(pkg)`. It goes through `memory.snapshot().keys` and removes keys whose payload package matches. To do that, store the package with the key: keep a `HashMap<String, String>` from key to package, filled when an icon is cached.
5. **B08, messenger state.** Add `@Volatile var packagesVersion` to `Graph`, incremented in the `AppSource` callback. `SearchActivity` remembers the last value it saw, and `onResume` calls `refreshMessengers()` only when it changed.
6. **B09, aliased files.** Remove `exists()` from the keystroke path.
   * Keep a `HashMap<String, Boolean>` of key to "exists", rebuilt on `Graph.worker` when `onResume` runs or the alias version changes. Unknown keys count as present.
   * Missing entries are hidden on the next query.
7. **B10, one update per keystroke.** Add `ResultAdapter.submit(items, selected)`, which assigns both and calls `notifyDataSetChanged()` once. Use `look.barBottom` in `publish()`.
8. **B14, clipboard thread.** Create a `HandlerThread("tacit-clip")` lazily inside `ClipboardStore` on first capture, never in `onCreate`. Post the capture and its 600 ms retry there instead of `Graph.worker`.
9. **B15, pinned shortcut sync** (if verification confirms the bug). When a quicklink is saved and its name or type changed, call `ShortcutManager.updateShortcuts(listOf(PinShortcut.info(ctx, link)))`. When one is deleted, call `disableShortcuts(listOf("ql-$id"), getString(R.string.ql_pin_removed))`. New string: en "This quicklink was deleted", ar "حُذف هذا الرابط السريع". Wrap both calls in `runCatching`.
10. **B16, backup import.**
    * Before importing, show a confirm dialog: "Replace your settings, %1$d aliases, %2$d snippets and %3$d quicklinks with the backup?" Arabic: "استبدال إعداداتك و%1$d اسمًا مستعارًا و%2$d مقتطفًا و%3$d رابطًا سريعًا بالنسخة الاحتياطية؟"
    * Read and import on `Graph.worker`, then on main: `Graph.applyPrefs()`, `Graph.icons.clear()`, `requestRefresh(ALL)`, and `recreate()` if the theme changed, otherwise `build()`.
11. **B17, labels in the hidden dialog.** Use `Graph.apps.appItems().firstOrNull { it.key == key }?.label ?: key`, and the same for settings and contacts through their sources.
12. **B18.** Make `hasStableIds()` return `false`.
13. **B27.** Cache `isDefaultHome()` in a field refreshed in `onResume`.
14. **Fix main-thread I/O.** Fix every `app.tacit` main-thread disk violation that StrictMode reported in M8 and that falls in files this milestone touches. List the rest under Risks.
15. **Decisions:** 70 (refresh coalescing and source masks), 71 (IndexCache v4), 72 (clip thread).
16. **Done when:** both golden tests are unchanged, `KeystrokeBudgetTest` is the same or better, StrictMode counts are the same or lower, and every fix has a test or a written reason it can't have one.

### M10: Fast, trustworthy file search (1.0.3-beta)

Must read first: `files/*.kt`, `SearchActivity.kt` (`runQuery`, `publish`, `fileItem`, `aliasedFileRows`), `SettingsActivity.kt` (Files section), `Actions.kt` (`openFile`, `openFolder`, `shareFile`), `TacitFileProvider.kt`, `QuicklinkMaker.kt`, and decision 58 plus every decision about files.

1. **Threads (B02, B12).**
   * `FileIndex` owns two lazily started HandlerThreads: `tacit-files-scan` at `THREAD_PRIORITY_BACKGROUND` and `tacit-files-search` at `THREAD_PRIORITY_DEFAULT`.
   * `@Volatile table` is swapped atomically. Search never waits for a scan.
   * Scan bodies are wrapped in try/finally. `finally` resets state and invokes callbacks. Exceptions are caught, written to the crash log file the way `TacitApp` does it, and leave the old table in place.
2. **Scan generations (B23).**
   * An `AtomicInteger scanGen` is incremented by `clear()` and by every accepted scan request. The scanner checks it every 1,024 entries and aborts if it changed. A finished scan publishes only if its generation is still current.
   * `clear()` sets the table to EMPTY right away and deletes the file on the scan thread.
   * Coalescing: while a scan is queued, merge new requests into it. While one is running, a request because the scope changed (hidden toggle, folders added or removed) sets `rerun = true` and one more scan runs afterwards. An automatic refresh request while scanning is dropped.
3. **Scan state.** `sealed class ScanState { Idle; Scanning(found: Int, startedAt: Long); Done(count: Int, at: Long, truncated: Boolean) }` is exposed as a volatile field, with main-thread listeners (`addStateListener` / `removeStateListener`). While scanning, `found` updates at most every 250 ms.
4. **Partial results.** During a scan where no table is loaded and `files.bin` is missing, publish a snapshot table every 25,000 entries or 250 ms, whichever comes first. If a previous table exists, keep serving it until the new one is complete. When a partial table is published and `SearchActivity` has an active file query, re-run that query, throttled to once per 500 ms.
5. **Scanning row in search.**
   * When files are wanted for the current query and the state is `Scanning`, add one row: `ItemKind.ACTION`, key `files:status`, label from `files_indexing` with the found count formatted using `NumberFormat.getIntegerInstance()`, and payload `tacit|app.tacit.ui.SettingsActivity`.
   * In the FILES scope, show it whenever scanning. In the ALL scope, show it only while there are no file hits.
   * When files are enabled but the state is Idle with no table and no access, show `files_not_indexed` instead.
   * Strings:
     * `files_indexing`: en "Indexing files… %1$s found", ar "جارٍ فهرسة الملفات… عُثر على %1$s"
     * `files_not_indexed`: en "Files aren't indexed yet. Tap to open Settings", ar "لم تُفهرس الملفات بعد. انقر لفتح الإعدادات"
6. **Auto-refresh (B01).**
   * `FileIndex.maybeRefresh()` is called from `SearchActivity.onResume` and `SettingsActivity.onResume`, never from any `onCreate`. It posts to the scan thread and does nothing if files are off or access is missing.
   * No table on disk means a full scan.
   * With All-files access on API 30+, for each volume in `MediaStore.getExternalVolumeNames(ctx)`, compare `MediaStore.getVersion(ctx, vol)` and `MediaStore.getGeneration(ctx, vol)` with the stored header values. If either differs and the last scan finished more than 120 s ago, refresh.
   * In folder (SAF) mode, refresh if the last scan is older than 6 h.
   * A refresh is a full background scan that keeps the old table until it's done. That keeps deletions correct.
7. **Scanner strategies.** Implement these behind `interface ScanStrategy { fun scan(sink: EntrySink, gen: () -> Boolean) }`:
   * (a) the current `File` walker, kept unchanged as the baseline
   * (b) an NIO walker: `Files.newDirectoryStream(dir)` plus `Files.readAttributes(p, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)`, one stat per entry, skipping symlinks, same hidden and `Android/data`/`Android/obb` rules
   * (c) MediaStore, for All-files access on API 30+: query `MediaStore.Files.getContentUri(vol)` for `RELATIVE_PATH`, `DISPLAY_NAME`, `SIZE`, `DATE_MODIFIED` (seconds × 1000). Build absolute paths from the volume directory (`StorageManager.getStorageVolume(MediaStore.Files.getContentUri(vol))?.directory`). Create folder entries from the distinct parent directories. Apply the hidden rule to every path segment.
   * (d) hybrid: publish (c) immediately, then run (b) and replace the table when it finishes.
   * SAF mode always uses `TreeScanner`. Add an `ACTION_OPEN_DOCUMENT_TREE`-mode note to `limits.md` saying it's slower because each folder is a separate query.
8. **Benchmark and coverage on the emulator** (`FileScanBenchmarkTest`, a device test added to the CI list for API 35 only).
   * In `@Before`, run `appops set app.tacit MANAGE_EXTERNAL_STORAGE allow` through `UiAutomation.executeShellCommand`.
   * Seed `/sdcard/TacitBench/` with 30,000 files and 3,000 folders using `FileFixtures`-style names. Include a `.nomedia` folder with 200 files, a hidden `.cache` folder with 100 files, and 50 files created in the last second.
   * Run strategies a, b and c three times each. Print `TACIT_FILESCAN strategy=<x> ms=<n> entries=<n>`.
   * Compute the set differences against (b) for the bench tree only, and print them. Assert only that (b) finds exactly what (a) finds. Speed and the MediaStore coverage are reported, not asserted.
   * Delete the tree in `@After`.
   * **Decision rule (write it as decision 74):** if (c) covers everything (b) finds in the bench tree, including the `.nomedia` files and the fresh files, the default with All-files access is (c), and (d) runs at most once per 24 h. Otherwise the default is (d). Without All-files access, use (b), or the tree scanner in SAF mode.
9. **Index format v2 (B03, B11, B26).**
   * The header is written first: magic int `0x54464932`, version 2, entryCount, createdAt, strategy byte, truncated boolean, volume count, then per volume: name UTF, mediaStoreVersion UTF, generation long.
   * Then the directories, then entries. Each entry: name UTF, a flags byte (bit0 = normalized stored, bit1 = directory, bit2 = has document URI), normalized UTF only when it differs from `display.lowercase()`, document URI UTF when bit2 is set, parent int, size long, modified long.
   * Tree folders store their document URI, so `pathOf` returns `content://…` for both tree files and tree folders (B11). `Actions.openFile` must route a `content://` URI whose MIME type is `DocumentsContract.Document.MIME_TYPE_DIR` to the folder opener.
   * `FileIndex.summary()` reads only the header on the scan thread, and Settings shows it: count, `files_indexed_at`, and `files_limit_reached` when truncated.
   * Strings:
     * `files_indexed_at`: en "Last indexed %1$s", ar "آخر فهرسة %1$s". Format the time with `DateUtils.getRelativeTimeSpanString`.
     * `files_limit_reached`: en "Limit reached: only the first %1$s files are indexed", ar "بلغت الفهرسة الحد الأقصى: فُهرس أول %1$s ملف فقط"
   * Reading a v1 file still works through the old reader and schedules a refresh. Add `FileIndexMigrationTest` (JVM, stream-based like IndexCache): v1 bytes become a correct table, and v2 round-trips with tree entries and Arabic names.
10. **Matcher speed (B13).** The output must stay identical, and `FileGoldenTest` is unchanged.
    * Precompute a `LongArray masks` for every entry when building or loading the table. For each char of the normalized display name, set bit `c - 'a'` for a–z, `26 + (c - '0')` for 0–9, and `36 + (c.code % 28)` for anything else. Build the query mask from all term chars except spaces, and skip the entry if `(mask and q) != q`. This is exact, because every term must be a substring of the name.
    * Precompute `".$extension"` once.
    * Replace `substringBeforeLast` with a `lastIndexOf('.')` and `regionMatches` comparison that gives the same result.
    * Compute `now` once per search.
    * Add `FileMatcherEquivalenceTest`: copy the 1.0.0 matcher into test sources as `LegacyFileMatcher`, run 300 queries on a 300,000-entry fixture, and require identical results.
    * Add `FileSearchSpeedTest`: run legacy and new matchers interleaved, 5 rounds each, and assert new median ≤ 0.6 × legacy median.
    * Print `TACIT_FILESEARCH_JVM p50 p95 entries`. The owner adds an absolute budget to `Budgets.kt` from the first CI numbers.
11. **Preload.** When `SearchActivity.onResume` runs with files enabled and the table isn't loaded, post a load of `files.bin` to the search thread, so the first `/query` doesn't pay for it.
12. **Settings.** Rescan shows the live state. It reads "Indexing… N" while scanning and is disabled while a scan is queued.
13. **Decisions:** 73 (threads, generations, coalescing), 74 (strategy choice with the measured numbers), 75 (auto-refresh policy, and why there's no FileObserver: it needs one watch per folder, hits inotify limits and costs memory), 76 (index v2), 77 (mask prefilter).
14. **Done when:** both golden tests are unchanged, the equivalence and speed tests are green, the migration test is green, emulator scan numbers are in the PR, and the HANDOFF checklist has "first search while indexing shows the indexing row" and "new download appears without Rescan".

### M11: Density, narrow screens, accessibility (1.0.4-beta)

Must read first: `RowDensity.kt`, `ResultAdapter.kt`, `SearchActivity.kt` (`buildViews`, `makeWelcome`), `LetterStrip.kt`, `SettingsActivity.kt` (`rowView`), `AliasesActivity.kt`, `SnippetsActivity.kt`, `QuicklinksActivity.kt`, `ScreenshotTest.kt`, `ResultAdapterTest.kt`, `ScreenPaddingTest.kt`.

1. **Redefine Compact (decision 78).** Comfortable stays exactly as it is. Compact becomes a Raycast-style dense row:

   | Field | Comfortable (unchanged) | Compact (new) |
   |---|---|---|
   | rowDp | 64 | 48 |
   | slotDp | 48 | 32 |
   | iconDp | 40 | 28 |
   | kindPadDp | 10 | 6 |
   | initialsSp | 15 | 12 |
   | titleSp | 17 | 15 |
   | subtitleSp | 14 | 12 |
   | subtitle position | below title | inline, after title, end-aligned |

   * Add `val inlineSubtitle: Boolean` to `RowDensity`.
   * With an inline subtitle, the holder puts title (width 0, weight 1, one line, ellipsize end) and subtitle (wrap content, `maxWidth` = 40% of `screenWidthDp`, computed once in the adapter, one line, ellipsize end, muted) in one horizontal row.
   * Holders are built once per density, so binding allocates nothing. Icons still render at `iconDp`.
   * `IconCache` already clears on density change, so keep that.
2. **Density everywhere.**
   * Search field vertical padding: 14 dp Comfortable, 10 dp Compact.
   * Settings `rowView` minimum height: 56 dp Comfortable, 48 dp Compact.
   * Apply the same min-height rule to list rows in the Aliases, Snippets and Quicklinks screens.
   * Letter strip text: 11 sp Comfortable, 10 sp Compact.
   * Touch targets never go below 48 dp.
3. **Density choice on the first-run card.** Add a row below the body: label `welcome_rows` (en "Rows", ar "الصفوف") with two chips, Comfortable and Compact, reusing the existing strings. Tapping a chip saves the pref and rebuilds views, without ending the welcome.
4. **Narrow screens (B19).** In the adapter, compute once per adapter:

   ```
   buttonsFit = floor((screenWidthDp - 16 - 12 - slotDp - 12 - 140) / 48)
   ```

   Here 140 dp is the minimum width kept for the name. If the contact's available actions (Call, Message, WhatsApp if installed, WhatsApp Business if installed) are more than `buttonsFit`, show the first `buttonsFit - 1` and then a More button.
   * More opens an `AlertDialog` listing the rest.
   * More's icon is a new `ic_action_more` vector from Lucide "ellipsis". Add its ISC/MIT notice to `NOTICE`, following the existing pattern.
   * String `action_more`: en "More actions", ar "إجراءات أخرى".
   * Test on an emulator with `wm size 720x1280` and `wm density 360` (320 dp wide): the title width must be at least 140 dp with both WhatsApps installed. If a test can't install WhatsApp, use the adapter's `installedMessengers` setter.
5. **Accessibility.**
   * Content descriptions on: the letter strip (`drawer_index`, en "Alphabet index", ar "فهرس الحروف"), each drawer tab, the clear and apps buttons (check they already exist), the status row, and the More button.
   * Mark the drawer tabs as selected through `isSelected`, which already exists.
   * Add `AccessibilityLabelsTest`: walk the view tree of the search screen with the drawer open and closed, and assert every clickable, visible `ImageView` and every `TextView` without text has a non-empty `contentDescription`.
6. **Screenshot matrix.** Extend `ScreenshotTest` to cover density (2) × bar position (2) × LTR/RTL (Arabic locale through the existing mechanism) × font scale 1.0/1.3. Set `settings put system font_scale` through a shell command and reset it in `@After`.
   * Screenshots are artifacts only; there's no image diffing.
   * Add assertions: with font scale 1.3 and Compact, row height ≥ 48 dp and the title isn't empty-ellipsized (`layout.getEllipsisCount(0) < text.length`).
7. **Decisions:** 78 (new Compact), 79 (contact button overflow rule).
8. **Done when:** golden tests are unchanged, `ResultAdapterTest` and the screenshot tests are green, Arabic strings are complete, and the HANDOFF checklist is updated.

### M12: Shortcut maker 2 (1.0.5-beta)

Must read first: `QuicklinkRules.kt`, `QuicklinkStore.kt`, `QuicklinkSource.kt`, `QuicklinkTargets.kt`, `QuicklinkLauncher.kt`, `QuicklinkMaker.kt`, `QuicklinksActivity.kt`, `PinShortcut.kt`, `AppSource.kt`, `SecondaryMenu.kt`, `Backup.kt`, `QuicklinksTest.kt`, `QuicklinksV2Test.kt`, `QuicklinkRulesTest.kt`, and decisions about quicklinks (including 34, the URL scheme rules).

Allowed manifest changes in this milestone: one new activity (below), one new `<queries>` entry for `android.intent.action.CREATE_SHORTCUT`, and one new activity for the explorer if you build it as an activity. Nothing else.

1. **Accept pinned shortcuts from other apps** (works only while Tacit is the default home app).
   * Add `actions/PinRequestActivity`, `exported="true"`, theme `@android:style/Theme.Translucent.NoTitleBar`, `excludeFromRecents`, `taskAffinity=""`. Intent filter: action `android.content.pm.action.CONFIRM_PIN_SHORTCUT`, category DEFAULT.
   * In `onCreate`, call `getSystemService(LauncherApps)!!.getPinItemRequest(intent)`. If it's null, invalid, or not `REQUEST_TYPE_SHORTCUT`, call `finish()`. A null return also rejects spoofed intents.
   * Show an `AlertDialog`:
     * title: the shortcut's `shortLabel`
     * message: the app label from `Graph.apps.appItems()`, falling back to the package name
     * buttons: Add (`pin_add`, en "Add to Tacit", ar "إضافة إلى Tacit") and Cancel
   * Add calls `request.accept()`, then `Graph.requestRefresh(APPS)`, then `finish()`. Cancel and dismiss just `finish()`.
   * Accepted shortcuts become pinned shortcuts that `AppSource.loadShortcuts()` already lists, so they're searchable rows that can take aliases. Don't create a quicklink for them.
   * In `AppSource`, keep a `@Volatile pinnedKeys: Set<String>` of keys whose `ShortcutInfo.isPinned && !isDynamic && !isDeclaredInManifest`. In `SecondaryMenu`, show Unpin (`menu_unpin_shortcut`, en "Remove from Tacit", ar "إزالة من Tacit") for those keys. It calls `launcherApps.pinShortcuts(pkg, currentPinnedIdsForPkg - id, user)` and then `requestRefresh(APPS)`.
   * When Tacit is the default home, hide the existing "Add to home screen" menu entry, because Tacit has no home screen to pin to. Add this to `limits.md`.
   * Device test: create a test shortcut from the test APK with `ShortcutManager.requestPinShortcut`. On the emulator, Tacit must first be set as home via `cmd package set-home-activity app.tacit/.ui.SearchActivity`. Then confirm through UiAutomator and assert the row appears. If the emulator image refuses `set-home-activity`, report under Needs owner.
2. **App screen explorer** (new quicklink type `ACTIVITY`, target `pkg/cls`).
   * Flow: pick an app, then pick a screen.
   * The app list comes from `Graph.apps.appItems()` (main profile only), with "Android Settings" (`com.android.settings`) pinned on top, and a filter field using `Normalizer`.
   * The screen list comes from `getPackageInfo(pkg, PackageInfoFlags.of(GET_ACTIVITIES))` on API 33+ or the int flag otherwise. Run it on `Graph.worker` and show the result on main. Keep activities where `exported && enabled && permission == null`.
   * Each row shows `loadLabel` (falling back to the simple class name) with the simple class name as subtitle. Sort labelled screens first, then alphabetically.
   * Each row has a Try action that launches without saving.
   * Launch with `Intent().setComponent(...).addFlags(FLAG_ACTIVITY_NEW_TASK)`. Catch `SecurityException` and `ActivityNotFoundException`, then show `ql_cannot_open` (en "This screen can't be opened directly", ar "لا يمكن فتح هذه الشاشة مباشرة") and open the editor.
   * `QuicklinkTargets.state`: MISSING when `getActivityInfo` throws `NameNotFoundException`.
   * Add to `limits.md`: some screens need extras or permissions and will refuse to open; work-profile apps are not listed.
3. **Legacy app shortcuts** (`ACTION_CREATE_SHORTCUT`).
   * Add the `<queries>` intent entry. List the targets with `queryIntentActivities(Intent(ACTION_CREATE_SHORTCUT), 0)` and launch the chosen one with `startActivityForResult`.
   * On the result, first try `LauncherApps.getPinItemRequest(data)`. If it's valid and Tacit holds the shortcut host permission, call `accept()`, which makes it a pinned shortcut.
   * Otherwise read the legacy `Intent.EXTRA_SHORTCUT_INTENT` and `EXTRA_SHORTCUT_NAME`, and save an `INTENT` quicklink through the sanitizer (next item).
   * If neither is present, show `ql_cannot_open`.
4. **Custom intent builder** (new type `INTENT`).
   * The target is `intent.toUri(Intent.URI_INTENT_SCHEME)`.
   * Fields in an "Advanced" section of the quicklink editor: action, data URI, MIME type, categories (comma-separated), component (`pkg/cls`), and up to 20 extras with typed values (string, int, long, boolean).
   * Validation lives in a pure `core/IntentSpec` (data class plus `validate(): List<Problem>`) with JVM tests. Conversion to `Intent` lives in `actions/IntentSanitizer`.
   * Sanitizer rules, applied at save and again at every launch:
     * reject if the component or package is `app.tacit`
     * reject data with `file:` scheme, or `content:` with authority `app.tacit.files`
     * clear the selector
     * strip all `FLAG_GRANT_*` flags and add `FLAG_ACTIVITY_NEW_TASK`
     * keys up to 100 chars, string values up to 1,000 chars
     * require a non-empty action or a component
   * A Try button launches without saving. Save is enabled only if the intent resolves with `resolveActivity`.
5. **Quicklink icons.** Add a sixth CSV column `icon`. A missing column means `glyph`, which keeps old files readable. Values: `glyph` (current behaviour), `app` (target app icon for APP, APP_SHORTCUT and ACTIVITY), or `letter:XY` (one or two characters).
   * `PinShortcut.icon` renders these:
     * `app`: the app icon drawn on the black background at the same inset, desaturated when icon mode is mono
     * `letter`: white text, bold, 45% of the size, centered
   * The editor gets an Icon choice row.
6. **Unified New flow.** Quicklinks screen → New shows one chooser: Website (URL), App, App screen, App shortcut, Settings screen (the existing SETTING type), Settings explorer (the app screen explorer preselected to `com.android.settings`), File, Folder, Contact action, Legacy shortcut, Custom intent. Add strings for each in both languages.
7. **Backup.** Export new types and the icon field. `QuicklinkRules.exportTarget` and `importTarget` handle ACTIVITY (export as is) and INTENT (export as is; import re-runs the sanitizer and drops invalid entries). Bump `tacit_backup` to 2. The importer accepts 1 and 2. `BackupCompatTest` (the 1.0.0 fixture) must stay green. Add a v2 round-trip test.
8. **Tests:** `IntentSpecTest` (JVM, about 25 cases including every reject rule), `QuicklinkStoreCsvTest` (5-column file loads, 6-column file round-trips), plus device tests for the explorer (open Settings, pick the Wi-Fi screen if present, Try launches) and for an INTENT quicklink that targets Tacit being rejected.
9. **Decisions:** 80 (pin requests and Unpin), 81 (activity explorer limits), 82 (intent sanitizer rules), 83 (icon column and backup v2).
10. **Done when:** all quicklink tests are green, the backup compat tests are green, golden tests are unchanged, and `limits.md` and README are updated.

### M13: Speed pass, drawer verdict, release candidate (1.1.0)

Must read first: `SearchEngine.kt`, `Scorer.kt`, `SearchItem.kt`, `IconCache.kt`, `SearchActivity.kt` (drawer parts), `DrawerList.kt`, the `benchmark/` module, and decision 58.

1. **Safe prefilter in `SearchEngine`.** Give every `SearchItem` a `charMask: Long` computed at build time over all `names[].full` and `aliases`, with the same bit scheme as M10.
   * Per query, compute `q` over the query chars without spaces, and `allowed` as the sum of `typoAllowed(word)` over the query words (one word means `typoAllowed(query)`).
   * Skip the item if `popcount(q and charMask.inv()) > allowed`. That's exact: each missing distinct character costs at least one edit, and every non-typo tier needs every query char present.
   * Add `SearchEngine(prefilter: Boolean = true)`. `SearchPrefilterEquivalenceTest` runs every golden query plus 500 random 1–12 character queries from `Random(11)` with the prefilter on and off, and requires identical results.
   * `SearchSpeedTest` asserts median(on) ≤ 0.8 × median(off), interleaved over 5 rounds.
   * Don't add incremental filtering of previous results. The typo budget grows with query length, so results aren't monotonic. Record this as decision 84.
2. **B24.** Check the bytecode and fix if needed (section 4).
3. **Icons (B07, second part).**
   * Move rendering to a lazily created `HandlerThread("tacit-icons", THREAD_PRIORITY_DEFAULT)` owned by `IconCache`.
   * Cache size: `min(24 MB, Runtime.getRuntime().maxMemory() / 8)`, with 12 MB as the floor.
   * Experiment: convert rendered bitmaps with `copy(Bitmap.Config.HARDWARE, false)` on the icon thread. Keep it only if the benchmark module's scroll or frame metrics on the emulator don't get worse. Record the numbers either way.
4. **Drawer verdict (decision 85): keep it opt-in, off by default, no Files tab in this release.** Why:
   * When off, it costs nothing: no views, no list, no icons. Decisions 1 and 58 already designed it that way.
   * When on, the only real costs are icon rendering and memory, which items 3 and 5 reduce.
   * Turning it on by default would add icon memory for every user, which goes against the keyboard-first design.
   * A Files tab would need the whole index in memory and its own list UI. Revisit after real-phone numbers from M10.
5. **Drawer prewarm.** On `ACTION_DOWN` on the Apps button (an `OnTouchListener` that returns false), build `DrawerList` if it isn't cached and request icons for the first `ceil(screenHeight / rowHeight)` rows.
6. **Cold-start trace sections:** `tacit.cache.read` (worker), `tacit.buildViews`, `tacit.firstQuery`. Run the benchmark module on the CI emulator for API 35 as a non-blocking step (`continue-on-error: true`). Its numbers are a trend annotation only, because emulator benchmarks aren't reliable. Mention that in the notice.
7. **Release candidate.** No new features from here; fix only what CI or the owner reports.
   * Update README features, `limits.md`, the HANDOFF on-device checklist (pin requests, app screen explorer, legacy shortcuts, custom intents, indexing row, auto-refresh, new Compact, narrow-screen More button) and the HANDOFF "Later" list.
   * Write stable release notes `v1.1.0.md` and both changelogs.
8. **Done when:** every CI job is green, `KeystrokeBudgetTest` p95 is the same or better than M8, the file speed tests pass, golden tests are unchanged since M8 (or every change is owner-approved), and StrictMode counts are the same or lower than M8.

---

## 6. Owner-only actions (never do these yourself)

* Set branch protection on `main`: changes only through PRs, all checks required, only the owner merges. Add `CODEOWNERS` with `* @nickburser122` if the owner asks.
* Merge PRs, approve guard changes, set absolute budgets in `Budgets.kt`, and approve golden-file changes.
* Sign releases on their own machine (`docs/SIGNING.md`) and publish draft releases.
* Run the on-device checklist on a flagship and a budget phone after M10 and M13, including scan time with All-files access versus folders, MediaStore coverage, and pin requests from Chrome "Add to Home screen" with Tacit as home.
* Review the Arabic strings.

---

## 7. Stop conditions

Stop work, push what you have, and write "Needs owner" if any of these happen:

* CI is red for a reason you can't trace to your own change after two attempts.
* A golden or equivalence test would need changing.
* A permission, dependency or format change is needed beyond what this plan allows.
* The code contradicts this plan in a way that changes the design.
* An emulator limitation blocks a required test.

Partial progress, honestly reported, is always better than a green build reached by weakening a guard.