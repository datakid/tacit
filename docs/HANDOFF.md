# Handoff for the next chat (with GitHub connected)

The code was written **without a compiler**. Your first job is to get CI green. After that it gets tested on a phone. Don't redesign anything; fix errors with the smallest change that works.

## Order of work
1. Push the repo. `gradle/wrapper/gradle-wrapper.jar`, `gradlew` and `gradlew.bat` were downloaded from Gradle's official `v9.7.1` tag. If wrapper validation fails, run `gradle wrapper --gradle-version 9.7.1` and commit the result.
2. Run CI and paste the **complete** error output. Most likely failures, in order:
   1. **Build script.** AGP 9 has Kotlin built in. If the `kotlin-gradle-plugin` classpath in the root `build.gradle.kts` conflicts or isn't needed, delete that `buildscript` block. If AGP rejects `compileSdk = 37` or `minSdk = 29`, use the block form it suggests (`minSdk { version = release(29) }`). If `platforms;android-37` is missing on the runner, add `sdkmanager "platforms;android-37"` to CI.
   2. **Manifest merge or resources.** Possible spots: `android:enableOnBackInvokedCallback`, `ACCESS_HIDDEN_PROFILES` (remove it if unknown), `tools:targetApi`, `voice_interaction.xml` attributes.
   3. **Kotlin type or API errors.** Watch these:
      - `AppSource.profileLabel` (`getLauncherUserInfo`, `USER_TYPE_PROFILE_PRIVATE`, API 35)
      - `IconCache` (`AdaptiveIconDrawable.monochrome`, API 33)
      - `SearchActivity` (`overrideActivityTransition` 34+, the deprecated `onBackPressed` override)
      - `ClipboardStore` (`ClipDescription.timestamp`, `extras` setter)
      - `SettingsCatalog` (any `Settings.ACTION_*` constant missing from SDK 37: replace it with its string value)
      - `Entry.kt` (`startActivityAndCollapse` overloads)
      - `SettingsActivity` (`Switch` deprecation is only a warning)
   4. **Unit tests.** Watch these:
      - `ScorerTest.gosFindsAsmaaGouda`, `latinFindsArabicContact`, `wfiFindsWifi`, `ytFindsYoutubeFirst`
      - `CalculatorTest.units` (10-decimal rounding)
      - `dates` (fixed clock: 2026-10-08 UTC)
      - `ParsingTest.sensitive`

      Fix the code when the code is wrong. Change a test only when the test itself is wrong, and say which one you changed.
   5. **RepoRulesTest.noCommentsInKotlinSources.** The checker removes string contents, then looks for `//` or `/*`. If it flags a URL inside a string, fix the checker, not the URL.
   6. **Benchmark module (`:benchmark`).** It uses `com.android.test` with `targetProjectPath = ":app"` and a `benchmark` build type in both modules. Possible failures:
      - the `experimentalProperties["android.experimental.self-instrumenting"]` line (delete it if AGP 9 rejects it)
      - `androidComponents.beforeVariants` (DSL changes)
      - `TraceSectionMetric` signature or the `ExperimentalMetricApi` opt-in
      - `BaselineProfileRule.collect(includeInStartupProfile = ...)` parameter name

      While fixing it, you may temporarily remove `:benchmark:assembleBenchmark` from CI so the app work isn't blocked. Restore it afterwards.
   7. **Device tests (`app/src/androidTest`).** They compile in the `build` job (`assembleDebugAndroidTest`) and run in the `device-tests` job on an API 35 emulator. If the emulator job is flaky, keep the compile step and run the tests on a phone with `./gradlew connectedDebugAndroidTest`. `EntryPointsUiTest.keyboardIsShownOnOpen` reads `dumpsys input_method`; the field names change between Android versions, so adjust the match if needed.
   8. **Shared fixtures.** `app/src/sharedTest/java` is added to both the `test` and `androidTest` source sets in `app/build.gradle.kts`. If AGP 9's new DSL rejects `java.srcDir`, use `kotlin.directories += "src/sharedTest/java"` or the DSL it suggests.
   9. **New features to check on a phone:**
      - **Choose folders:** Settings → Files → Choose folders → pick Documents → Rescan → search a file → it opens → Open folder.
      - **Clipboard lock:** Settings → Clipboard → Lock → type `cb ` → prompt → unlock → leave Tacit and come back → locked again.
      - **Arabic:** switch the phone language to Arabic and check every screen reads right-to-left.
3. Once CI is green: install `app-debug.apk` from the CI artifacts on a real phone and work through the checklist below.

## Owner's rules
- No comments in Kotlin (strict reading). Explanations go in `docs/decisions.md`.
- No INTERNET permission. No runtime dependencies unless there's a written reason in README and `RepoRulesTest` is updated.
- Report real test output only. Never claim a test passed without running it.
- Speed is requirement number one. Don't add Compose, AppCompat, coroutines or a dependency-injection framework.

## Architecture
- `core/`: pure Kotlin, no Android (normalizer, transliteration, scorer, search engine, query grammar, result composer, quick actions, contact file formats, drawer list, row densities). Unit-tested.
- `calc/`: calculator, units, city→zone table. Unit-tested.
- `clip/`: sensitive detectors (tested) and the encrypted clipboard store.
- `data/`: alias store, snippet store, CSV, JSON backup, contact export.
- `files/`: file index (scan, persist, background search) and matcher (tested).
- `sources/`: apps, contacts, Settings discovery, snippets → `SearchItem`s.
- `actions/`: every outgoing intent (launch, dial, SMS, WhatsApp, Settings, files, web, timer, alarm, calendar), the typed-action rows (`ActionRows`), the contact export screen and the read-only file provider.
- `ui/`: search screen (platform `ListView`, no RecyclerView), alias tables, snippets, settings, long-press menu, icon cache, app drawer letter strip.
- `system/`: tile, widget, assistant stubs.
- `Graph`: the shared singleton with a single worker `HandlerThread`. `IndexCache` writes the binary snapshot that's read at startup.

## On-device checklist
- [ ] Typing feels instant with 300+ apps and 2,000+ contacts; no dropped frames while typing
- [ ] Keyboard opens on every summon: Home, tile, widget, assistant gesture
- [ ] Gboard, Samsung Keyboard, HeliBoard, FlorisBoard: no autocorrect fighting, swipe typing works, Arabic works. If not, try "Strict no-autocorrect input".
- [ ] `gos` finds Asmaa Gouda; Arabic contacts found from Latin typing
- [ ] Call, Message, WhatsApp and WhatsApp Business open the right app and chat
- [ ] Settings coverage: write down which common screens are missing (Pixel, Samsung, Xiaomi)
- [ ] Files: All files access flow, rescan time, opening a PDF or an image, open folder
- [ ] Clipboard: copy in another app, open Tacit, check it's captured; the toast appears only for new clips; OTPs are not stored
- [ ] Alias table: batch edit, CSV round trip, a shared alias shows every item
- [ ] Work profile and Private Space apps launch
- [ ] TalkBack labels, large font, RTL (Arabic system language)
- [ ] Compare cold start with KISS: `adb shell am start -W -n app.tacit/.ui.SearchActivity` against `fr.neamar.kiss/.MainActivity`, 10 runs each after `adb shell am force-stop`

## Later (owner notes)
- Files: make the scan as fast as possible, and show a clear scanning state while the index is being built, so a search in the first minutes is not read as "not found" (2026-10-16). A files tab in the drawer waits for this (decision 58).
