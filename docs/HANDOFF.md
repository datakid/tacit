# Handoff

Tacit is at 1.0.0. CI builds it, runs the unit tests, runs lint and the R8 release build, and runs the device tests on Android 14, 15 and 16 emulators. Nothing has been measured on a real phone. This file says what is left and what to check there.

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

## Versions and CI
- Versions: one minor per milestone with `-beta` until 1.0.0 (decision 31). `versionCode = major * 10000 + minor * 100 + patch`. The release tag is the newest file name in `docs/release-notes`; a name without a suffix is a stable release.
- `build` job: debug build, unit tests (including `KeystrokeBudgetTest` and `RepoRulesTest`), lint, device-test compile, release build, a repeat release build with a byte comparison, benchmark module compile, signing-script check with a throwaway key, then publishing on `main`. The "Perf and size" and "Repeatable build" annotations hold the numbers.
- `device-tests` job: API 34, 35 and 36 emulators, one Gradle run per test class (`.github/scripts/device-tests.sh`). `IntentContractTest`, `PlatformContractTest` and `EntryPointsUiTest` are compiled but not in the CI list; run them on a phone with `./gradlew connectedDebugAndroidTest`.
- Device tests run with `TacitTestRunner`, which creates the first-run marker so only `FirstRunTest` sees the welcome card.
- `SettingsApplyTest` has failed once on a different test in each of three milestones and passed on re-run. The cause is not found; it looks like emulator timing.

## Architecture
- `core/`: pure Kotlin, no Android (normalizer, transliteration, scorer, search engine, query grammar, result composer, quick actions, quicklink rules, alias rules, contact file formats, drawer list, row densities). Unit-tested.
- `calc/`: calculator, units, city to zone table. Unit-tested.
- `clip/`: sensitive detectors (tested), the encrypted clipboard store and the clipboard lock.
- `data/`: alias store, snippet store, quicklink store, CSV/TSV, JSON backup, contact export.
- `files/`: file index (scan, persist, background search) and matcher (tested).
- `sources/`: apps, contacts, Settings discovery, snippets, quicklinks to `SearchItem`s.
- `actions/`: every outgoing intent, the typed-action rows (`ActionRows`), quicklink launching and pinned shortcuts, the contact export screen and the read-only file provider.
- `ui/`: search screen (platform `ListView`, no RecyclerView), settings, aliases, snippets, quicklinks, long-press menu, icon cache, app drawer letter strip.
- `system/`: tile, widget, assistant stubs, battery text.
- `Graph`: the shared singleton with a single worker `HandlerThread`. `IndexCache` writes the binary snapshot read at startup. `Welcome` holds the first-run marker.

## On-device checklist
- [ ] Typing feels instant with 300+ apps and 2,000+ contacts; no dropped frames while typing
- [ ] Keyboard opens on every summon: Home, tile, widget, assistant gesture
- [ ] Gboard, Samsung Keyboard, HeliBoard, FlorisBoard: no autocorrect fighting, swipe typing works, Arabic works. If not, try "Strict no-autocorrect input".
- [ ] First run: the card shows once, "Make it my home" opens the home-app screen, the card does not come back after Got it, typing or leaving
- [ ] `gos` finds Asmaa Gouda; Arabic contacts found from Latin typing
- [ ] Call, Message, WhatsApp and WhatsApp Business open the right app and chat
- [ ] Settings coverage: write down which common screens are missing (Pixel, Samsung, Xiaomi)
- [ ] Files: All files access flow, Choose folders flow (Settings → Files → Choose folders → Documents → Rescan), rescan time, opening a PDF or an image, open folder
- [ ] Clipboard: copy in another app, open Tacit, check it's captured; the toast appears only for new clips; OTPs are not stored; lock works
- [ ] Alias table: batch edit, CSV round trip, a shared alias shows every item
- [ ] Quicklinks: one of each type, pin to home screen, a deleted target opens its editor
- [ ] Quick actions: `timer 10 tea`, `alarm 7:30`, `event dentist tomorrow 3pm` open the clock and calendar apps with the right values
- [ ] Row size: Comfortable and Compact; contact rows with both WhatsApps on a narrow phone (four buttons can truncate the name)
- [ ] Drawer: letter strip on a real thumb, fling speed and icon loading on a budget phone, System settings tab
- [ ] Work profile and Private Space apps launch
- [ ] TalkBack labels (not run yet), large font, RTL (Arabic system language)
- [ ] Compare cold start with KISS: `adb shell am start -W -n app.tacit/.ui.SearchActivity` against `fr.neamar.kiss/.MainActivity`, 10 runs each after `adb shell am force-stop`
- [ ] Run the benchmark module on a flagship and a budget phone and record the numbers

## Later (owner notes)
- Files: make the scan as fast as possible, and show a clear scanning state while the index is being built, so a search in the first minutes is not read as "not found" (2026-10-16). A files tab in the drawer waits for this (decision 58).
- Find the cause of the `SettingsApplyTest` flake.
- Narrow phones: the four contact buttons can truncate the name.
- GitHub Actions: `ubuntu-latest` moves to Ubuntu 26 on 2026-10-19; the Node 20 action deprecation also needs a look.
- Add real phone screenshots to `fastlane/metadata/android/*/images/phoneScreenshots/`.
