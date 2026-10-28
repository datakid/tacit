# Handoff

Tacit is at 1.0.1-beta (1.0.0 plus the safety net for 1.1). CI builds it, runs the unit tests, runs lint and the R8 release build, and runs the device tests on Android 14, 15 and 16 emulators. Nothing has been measured on a real phone. This file says what is left and what to check there.

## Owner's rules
- No comments in Kotlin (strict reading). Explanations go in `docs/decisions.md`; `RepoRulesTest` enforces it.
- No INTERNET permission. No runtime dependencies unless there's a written reason in README and `RepoRulesTest` is updated.
- Report real test output only. Never claim a test passed without running it, and say when a result comes from an emulator.
- Speed is requirement number one. Don't add Compose, AppCompat, coroutines or a dependency-injection framework. No new work in `Application.onCreate`, no allocation per bind, and `KeystrokeBudgetTest` must not regress.
- Signing keys stay with the owner and never go on GitHub (`docs/SIGNING.md`; `RepoRulesTest` guards it).
- Every new string goes in `values/strings.xml` and `values-ar/strings.xml` with a real Arabic translation. RTL must work.
- Release notes are extra concise and atomic. One pull request per milestone; merge only when every CI job is green.

## Release 1.0.0, step by step
1. Merge the M7 pull request. CI on `main` creates a **draft** release `v1.0.0` with `tacit-v1.0.0-unsigned.apk` and `SHA256SUMS`.
2. Sign it on your computer and upload the signed APK: `docs/SIGNING.md`.
3. Install the signed APK on a phone (uninstall any beta first, the key differs) and go through the checklist below.
4. Publish the draft.
5. F-Droid: `docs/fdroid/app.tacit.yml` is an unvalidated draft recipe; steps are in `docs/SIGNING.md`.

## Release 1.1.0, step by step
1. Merge the M13 pull request. CI on `main` creates a **draft** release `v1.1.0` with `tacit-v1.1.0-unsigned.apk` and `SHA256SUMS` (the notes file `docs/release-notes/v1.1.0.md` has no suffix).
2. Sign it on your computer and upload the signed APK: `docs/SIGNING.md`.
3. Install it over 1.0.0 if that is on your phone (same key). A phone with a 1.0.x beta has to uninstall it first; export the settings before that.
4. Go through the checklist below on a flagship and a budget phone, then publish the draft.

## Versions and CI
- Versions: one minor per milestone with `-beta` until 1.0.0 (decision 31). `versionCode = major * 10000 + minor * 100 + patch`. The release tag is the newest file name in `docs/release-notes`; a name without a suffix is a stable release.
- `build` job: debug build, unit tests (including `KeystrokeBudgetTest` and `RepoRulesTest`), lint, device-test compile, release build, a repeat release build with a byte comparison, benchmark module compile, signing-script check with a throwaway key, then publishing on `main`. The "Perf and size" and "Repeatable build" annotations hold the numbers.
- `benchmark` job (since 1.1.0): the benchmark module on an API 35 emulator, `TypingBenchmark` and `ColdStartSectionsBenchmark` each in its own Gradle run with a ten-minute limit (`.github/scripts/benchmark.sh`), as a step with `continue-on-error: true`. It prints the "Benchmark on emulator, API 35 (trend only)" notice: frame times for the typing script, `tacit.query` time, and cold start with the `tacit.cache.read`, `tacit.buildViews` and `tacit.firstQuery` sections. Emulator numbers are a trend, not a measurement, so nothing compares them with a budget. It uses `CompilationMode.None` (instrumentation argument `tacit.compilation=none`) because `Partial` needs `androidx.profileinstaller` in the app, and the benchmark build type gets a `profileable` overlay (`app/src/benchmark/AndroidManifest.xml`) that the release and debug builds do not have. A first version without the time limits hung until the job's 40 minutes ran out.
- `device-tests` job: API 34, 35 and 36 emulators, one Gradle run per test class (`.github/scripts/device-tests.sh`). `BackupCompatTest`, `IntentContractTest` and `PlatformContractTest` are in the list. `EntryPointsUiTest` and `IconCacheTest` are in the list too; `EntryPointsUiTest` was out until 1.0.2-beta because `ResultAdapter.getItemId` crashed on an empty list (B18, decision 72).
- Device tests run with `TacitTestRunner`, which creates the first-run marker so only `FirstRunTest` sees the welcome card.
- `SettingsApplyTest` has failed once on a different test in each of three milestones and passed on re-run. Ten isolated runs on API 35 all passed, so its cause is still not found. A sibling flake (`ActionsTest`, API 34) was traced to the worker's startup and refresh tasks replacing the items a test had injected, and `Idle.await()` now waits for the worker and the main thread around item injection and preference changes (decision 69).
- Window-focus failures on API 35 (1.0.2-beta): of three attempts at the same commit, two failed with `RootViewWithoutFocusException` (`has-window-focus=false` on a Tacit window): one in `QuicklinksV2Test`, one in the first classes and all later ones (`ClipboardService: Denying clipboard access ... not in focus`); an earlier run lost `AliasesTest` the same way. API 34 and 36 passed every attempt, and the third attempt on API 35 passed with all 178 tests. Nothing in the kept output said what holds the focus, so this was recorded, not explained. Since 1.0.5-beta `device-tests.sh` adds a "focus around failures" section to the summary, the input focus and ANR events from the 14 seconds before each failing test; read it first on the next failure.
- Window-focus failures on API 35 (1.0.5-beta): three full runs of the first M12 code failed on API 35 only, each with `RootViewWithoutFocusException` in tests that open the quicklink editor from `EXTRA_EDIT` (`QuicklinksV2Test`, `ShortcutMakerTest`, `AliasesTest`); four runs of unchanged main on API 35 all passed. The editor then built the five-field intent form for every link; building it only when the Custom intent type is shown was followed by six full runs of the same app code on API 35: three passed every test, one lost a `PinRequestTest` test to a different race (since fixed), and two failed UI tests (`AliasesTest` lost window focus; a `PinRequestTest` and a `ShortcutMakerTest` test did not find their dialog, the second with `has-window-focus=false`) on the two slowest emulators seen (file-scan seed 98 and 116 s against 69 to 97 s in the passing runs of main and M12; the benchmark class also timed out at 295 s once). That fits a heavier UI making missing focus more likely on a slow emulator, but it is not proof, and the 1.0.2-beta note above shows the flake exists on main too.
- Failures on API 35 in M13 (1.1.0): of the completed runs of the M13 branch on API 35, two passed every test and two failed. One lost `ShortcutMakerTest.theAppIconIsOfferedForAppLinks` to `RootViewWithoutFocusException`. The other lost four tests of `SettingsApplyTest`, the first class after the emulator boots (`'Theme' not on screen`, `settings button missing`, `light theme is not white`, `battery not shown`); the 14 seconds of input focus events before them were empty, so that run does not show a window losing focus. All four go through `UiAutomation` (a screenshot or an accessibility query) while the tests of the class that use `ActivityScenario` passed, which points at the emulator's accessibility connection rather than at app code, but it is not proof. API 34 and 36 passed every run of the final code, nothing in M13 touches those screens, and no retry, sleep or ignore was added. The cause is still not found; the next failure's events and the test order are the places to look.
- `guard-check.sh` runs first in the `build` job and fails on a lower `@Test` count or an added `Ignore`/`Assume` (decision 66). `RankingGoldenTest` and `FileGoldenTest` compare against `app/src/test/resources/golden/*.tsv`; change those files only with the owner's approval (decision 67). Lint fails on new errors (decision 68). The device-test notice starts with StrictMode counts, compared with `docs/strictmode-baseline.txt` (decision 69).

## Architecture
- `core/`: pure Kotlin, no Android (normalizer, transliteration, scorer, search engine, query grammar, result composer, quick actions, quicklink rules, alias rules, contact file formats, drawer list, row densities). Unit-tested.
- `calc/`: calculator, units, city to zone table. Unit-tested.
- `clip/`: sensitive detectors (tested), the encrypted clipboard store and the clipboard lock.
- `data/`: alias store, snippet store, quicklink store, CSV/TSV, JSON backup, contact export.
- `files/`: file index (two threads, scan generations, `ScanState`, index v2 in `IndexFormat`), the scanners (`FileWalkers`, `MediaStoreStrategy`, `TreeScanner`), `RefreshPolicy` and the matcher (tested).
- `sources/`: apps, contacts, Settings discovery, snippets, quicklinks to `SearchItem`s.
- `actions/`: every outgoing intent, the typed-action rows (`ActionRows`), quicklink launching and pinned shortcuts, the contact export screen and the read-only file provider.
- `ui/`: search screen (platform `ListView`, no RecyclerView), settings, aliases, snippets, quicklinks, long-press menu, icon cache, app drawer letter strip.
- `system/`: tile, widget, assistant stubs, battery text.
- `Graph`: the shared singleton with a single worker `HandlerThread`. `IndexCache` writes the binary snapshot read at startup (format in `core/IndexCodec`, version 4, decision 71). `Graph.requestRefresh(mask)` coalesces refreshes (decision 70). The clipboard captures on its own `tacit-clip` thread (decision 72). `Welcome` holds the first-run marker.

## On-device checklist
- [ ] Typing feels instant with 300+ apps and 2,000+ contacts; no dropped frames while typing
- [ ] Keyboard opens on every summon: Home, tile, widget, assistant gesture
- [ ] Gboard, Samsung Keyboard, HeliBoard, FlorisBoard: no autocorrect fighting, swipe typing works, Arabic works. If not, try "Strict no-autocorrect input".
- [ ] First run: the card shows once, "Make it my home" opens the home-app screen, the card does not come back after Got it, typing or leaving
- [ ] `gos` finds Asmaa Gouda; Arabic contacts found from Latin typing
- [ ] Call, Message, WhatsApp and WhatsApp Business open the right app and chat
- [ ] Settings coverage: write down which common screens are missing (Pixel, Samsung, Xiaomi)
- [ ] Files: first search while indexing shows the indexing row and early results; a new download appears without Rescan; All files access flow, Choose folders flow (Settings → Files → Choose folders → Documents → Rescan), rescan time, opening a PDF or an image, open folder
- [ ] Clipboard: copy in another app, open Tacit, check it's captured; the toast appears only for new clips; OTPs are not stored; lock works
- [ ] Install, update and uninstall an app while Tacit is open: the row and its icon update within a second or two; install or remove WhatsApp and the contact buttons follow when you return to Tacit
- [ ] Settings → Import: choose a backup, the counts dialog appears, Cancel changes nothing, Replace applies everything and the theme at once
- [ ] Rename a quicklink that is pinned to the home screen: the shortcut label changes; delete it and the shortcut says it was deleted
- [ ] Alias table: batch edit, CSV round trip, a shared alias shows every item
- [ ] Quicklinks: one of each type, pin to home screen, a deleted target opens its editor
- [ ] Pin from another app: while Tacit is home, use Add to home screen in an app that offers it (Chrome); Tacit asks "Add to Tacit", the new row appears, and its long-press menu has Remove from Tacit
- [ ] App screens: Add quicklink, App screen, pick two apps you use and a screen in each; Try opens it, the saved link opens it, and a screen that closes at once tells you in the editor; Settings explorer lists Wi-Fi and display screens on your phone
- [ ] Custom intent: one with an action and data (a `geo:` or `https:` address), one with a text extra, one pointing at an app that is not installed (Save refuses), one pointing at Tacit (refused); Try before Save for a handler the check cannot see
- [ ] Legacy shortcut: pick an app that still offers one (a browser, a file manager); the result is a quicklink, or a row when Tacit is home
- [ ] Icons: change a pinned quicklink to the app icon and to letters; the home screen icon follows; Arabic letters work
- [ ] Backup made on 1.0.5 imports back with the icons; a backup from 1.0.4 imports with standard icons
- [ ] Quick actions: `timer 10 tea`, `alarm 7:30`, `event dentist tomorrow 3pm` open the clock and calendar apps with the right values
- [ ] Row size: Comfortable and Compact (Compact is the new dense row with the subtitle inline); pick it on the first-run card and in Settings; contact rows with both WhatsApps on a narrow phone show More and the name stays readable; Compact next to Comfortable in Settings, Aliases, Snippets and Quicklinks; large font and Arabic in both
- [ ] Drawer: letter strip on a real thumb, fling speed and icon loading on a budget phone, System settings tab
- [ ] Work profile and Private Space apps launch
- [ ] TalkBack: the letter strip says "Alphabet index", the drawer tabs say which one is selected, the More button says "More actions"; it has not been run with TalkBack on a phone (the labels are checked by `AccessibilityLabelsTest`)
- [ ] Large font (1.3) and RTL (Arabic system language) on the search screen in both row sizes; the screenshot matrix in the CI artifacts shows all sixteen combinations
- [ ] Compare cold start with KISS: `adb shell am start -W -n app.tacit/.ui.SearchActivity` against `fr.neamar.kiss/.MainActivity`, 10 runs each after `adb shell am force-stop`
- [ ] Icon: the new launcher icon in your launcher's circle, squircle and themed (monochrome) shapes, on a dark and a light wallpaper; the Quick Settings tile, the two long-press shortcuts, the widget's search mark, and the splash screen on Android 12 and later
- [ ] Drawer, first press: with the drawer on and 300+ apps, the first screenful of icons is there as the drawer opens (the Apps button starts drawing them on touch), then scroll fast: icons catch up without stutter and none is wrong after an app update
- [ ] Run the benchmark module on a flagship and a budget phone and record the numbers, including `tacit.cache.read`, `tacit.buildViews` and `tacit.firstQuery` from `ColdStartSectionsBenchmark`; the emulator trend in CI is not a substitute

## Later (owner notes)
- Files tab in the drawer (decisions 58 and 85): not in 1.1. The scan speed and scanning state it waited for shipped in 1.0.3-beta, but a list would need the whole index in memory and a list UI of its own. Revisit after real-phone scan time and memory numbers.
- Incremental filtering of the previous results was rejected (decision 84): the typo allowance grows with the query length, so a longer query can match items the shorter one did not. The exact prefilter went in instead.
- Find the cause of the `SettingsApplyTest` flake (1.0.1-beta: not reproduced in ten isolated runs, idle waits added, decision 69).
- Per-app actions like KISS (2026-10-22): a browser's new tab and private tab, and the shortcuts other apps declare. Tacit already lists every dynamic, manifest and pinned shortcut of other apps as a row while it is the default home app (`AppSource.loadShortcuts`; Android hides them otherwise, see limits.md), but a shortcut row matches only its own label, so typing the app's name does not bring them up. Next: let an app's name match its shortcuts, show them in the app row's long-press menu, and check on a phone which apps expose any. Screens that an app does not declare as shortcuts need the activity explorer planned for M12.
- Main-thread disk work that is left (1.0.2-beta): `Prefs` loads its preferences inside `Graph.init`, `Welcome.pending` checks its marker file once per process, and the quicklink editor saves, deletes and rechecks targets on the main thread (the stores write their files synchronously, and several device tests read them straight after the click). Moving the editor to the worker needs an asynchronous store API first.
- GitHub Actions: `ubuntu-latest` moves to Ubuntu 26 on 2026-10-19; the Node 20 action deprecation also needs a look (1.0.1-beta: runners pinned to `ubuntu-24.04`, actions on node24 majors, `setup-gradle` with `cache-provider: basic`).
- Add real phone screenshots to `fastlane/metadata/android/*/images/phoneScreenshots/`.
