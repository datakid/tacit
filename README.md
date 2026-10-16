# Tacit

A keyboard-first, offline, black-and-white Android launcher. You type, press Enter, and it's done. Inspired by KISS Launcher, built for F-Droid. "Raycast for Android."

> **Status: written blind, never compiled.** The code was written in an environment with no JDK, Android SDK or emulator. Expect compile errors on the first CI run. Start with `docs/HANDOFF.md`.

## Features in the code
- **Search screen**: a single search field with the keyboard already up and autocorrect suppressed (`TYPE_TEXT_VARIATION_FILTER`, or the stricter `textVisiblePassword` option). Results sit next to the bar, which is at the bottom by default. Clock and date are optional.
- **Fuzzy search tiers**: exact alias > alias prefix > exact name > name prefix > word prefix > initials > letters in order > typo on a word prefix.
  - Typo budget: no typos for 1–2 letters, 1 edit for 3–5, 2 edits for 6+. The first letter must match unless two letters are swapped.
  - Multi-word queries match each word in any order.
  - Examples: `gos` → Asmaa Gouda, `ag`, `asm gou`, `wfi` → Wi-Fi, `blutooth`, `yt` → YouTube.
- **Arabic**: letter folding (أ/إ/آ→ا, ة→ه, ى→ي, harakat, tatweel, Arabic-Indic and Persian digits). Arabic names also get Latin sound-alike variants (g/j, q/k, vowel forms), so `asmaa` finds أسماء جودة. This can be turned off.
- **Apps**: all profiles (work profile, Android 15 Private Space label). Searches app shortcuts when Tacit is the default home app. Long-press gives aliases, pin, app info, notifications, uninstall and hide.
- **Contacts** (opt-in): Call (dialer, or a direct call if enabled), Message (SMS app), WhatsApp and WhatsApp Business.
  - WhatsApp opens the chat directly through WhatsApp's own contact entry; otherwise it uses `wa.me` with the default country code.
  - A contact with several numbers uses the marked primary number, otherwise you pick one.
  - Typing a raw phone number gives Call, Message and WhatsApp rows.
- **Settings search** from six sources:
  - a curated list with synonyms (Arabic included)
  - every `Settings.ACTION_*` the device resolves
  - the Settings app's exported screens
  - Settings shortcut targets
  - Android 10+ panels
  - per-app info, notification and in-app settings pages

  Also: a flashlight toggle, and a "Search system settings" row where the device supports it.
- **Files** (opt-in): two ways to grant access. **All files access** walks every shared volume (skipping Android/data and Android/obb). **Choose folders** is for people who decline that: grant individual folders through Android's picker, and Tacit remembers them across reboots (persisted tree grants walked with DocumentsContract). Android won't let you pick the storage root or the Download root this way. Granted folders can be added or removed in Settings.
  - The index is a compact binary file; search runs on a background thread and cancels on each keystroke.
  - Filters: `ext:pdf`, `in:download`, `*.jpg`, `report pdf`.
  - Opens files through Tacit's own read-only provider. Long-press gives share, open folder, copy path.
- **Clipboard history** (opt-in):
  - Saved when Tacit's window gets focus, and only when the clip's timestamp is new, which avoids repeated "pasted" toasts.
  - Detects card numbers (Luhn), IBANs (mod-97), one-time codes, tokens, private keys, and Android's own "sensitive" flag. Policy per setting: never store, delete after 1 minute, or store with the preview hidden.
  - Encrypted with AES-256-GCM (key in Android Keystore). Retention in days and/or items, where 0 means forever.
  - FLAG_SECURE while the clipboard is visible (`cb ` prefix).
  - Optional **lock**: fingerprint, face or screen lock (platform `BiometricPrompt`, no library) before the clipboard is shown. It locks again whenever Tacit leaves the screen.
- **Aliases**:
  - Several per item, comma-separated (Arabic comma works too). One table per category: Apps, Contacts, Settings, Snippets, Shortcuts.
  - One alias can sit on several items; all of them rank at the top, and shared aliases are listed above the table.
  - Bulk "Fill initials", filter, CSV export and import (matched by key or by name). Also editable from any result's long-press menu.
- **Snippets**: trigger, title and body, with `{date}`, `{time}`, `{datetime}`, `{clipboard}` and optional formats like `{date:dd/MM/yyyy}`. Selecting one copies it. CSV import and export.
- **Calculator** (BigDecimal, no AI):
  - Arithmetic with `^ ! mod sqrt sin cos tan ln log abs round floor ceil π e`.
  - Natural phrasing: `15% of 340`, `340 + 15%`, `50 is what % of 200`, `half of 90`, `twice 12`, `5 plus 3 times 2`.
  - Units: length, mass, volume, area (incl. feddan), speed, data, time, temperature.
  - Number bases (`255 in hex`), time zones (`5pm dubai in tokyo`, `time in cairo`), dates (`days until dec 25`, `next friday + 3 weeks`, `today - 2024-01-01`).
  - Enter copies the result.
- **Web search**: DuckDuckGo (default), Google, Wikipedia, YouTube, OpenStreetMap. Engines are editable in Settings. The query opens in your browser.
- **Row size and app drawer**: rows are Comfortable (64 dp, large icons) by default or Compact (56 dp), in Settings → Look. An optional app drawer, off by default, adds an Apps button beside Settings: an A–Z list of installed apps with a letter strip. Long-press works as in search.
- **Ways to open it**: default Home, Assistant (minimal voice service stub, no voice features), Quick Settings tile, Tacit's own search-bar widget for other launchers, launcher app shortcuts, hardware keys (↑ ↓ Enter Tab Esc).
- Export and import of everything as one JSON file. Offline crash log with "Share last crash". Backup is disabled.
- **Arabic** translation of every screen; RTL layout.

## Prefixes (editable)
Typed actions (no prefix): `timer 10 tea` · `alarm 7:30` · `event dentist tomorrow 3pm` · `uuid` · `pass 20` · `roll 2d6` · `flip` · `rand 1 10` · `upper`/`lower`/`title`/`reverse`/`slug`/`b64`/`unb64`/`urlenc`/`urldec` (a keyword alone transforms the clipboard) · `export` (contacts as vCard, CSV or numbers)

`=` calculator · `>` settings · `@` contacts · `/` files · `;` snippets · `cb␠` clipboard · `ddg␠ g␠ w␠ yt␠ map␠` web

Web prefixes need the trailing space, so `ytmusic` still finds the app.

## Entry points (manifest)
| Component | Purpose |
|---|---|
| `ui.SearchActivity` | LAUNCHER, HOME, ASSIST, SEARCH_LONG_PRESS |
| `ui.SettingsActivity`, `ui.AliasesActivity`, `ui.SnippetsActivity` | internal screens (Settings and Aliases are also launcher shortcuts) |
| `system.TacitTile` | Quick Settings tile |
| `system.SearchWidget` | search-bar widget for other launchers |
| `system.AssistService` + session + recognition stubs | lets Tacit be picked as the default assistant |
| `actions.TacitFileProvider` | read-only `content://app.tacit.files/<path>` for opening files |

## Data (all on the device)
| File | Contents |
|---|---|
| SharedPreferences `tacit` | settings, pins, hidden items |
| `files/aliases.csv` | `key,aliases` |
| `files/snippets.csv` | `id,trigger,title,body` |
| `files/clips.bin` | clipboard history, AES-GCM encrypted |
| `files/files.bin` | file index |
| `cache/index.bin` | search index snapshot so the first frame doesn't wait on content providers |
| SharedPreferences `granted_trees` | folders granted with "Choose folders" (not exported in the backup, since the grants can't move between devices) |

Item keys look like `app:<component>:<userSerial>`, `contact:<lookupKey>`, `setting:<action>`, `snippet:<id>`, `shortcut:<pkg>:<id>:<serial>`.

## Dependencies
- **Runtime: none** (platform APIs only).
- **JVM tests:** JUnit 4.13.2.
- **Device tests:** androidx.test runner/rules/core 1.7.0, ext-junit 1.3.0, Espresso core and intents 3.7.0, UI Automator 2.4.0.
- **Benchmark module:** androidx.benchmark macro-junit4 1.5.0.

All versions come from the verified data sheet.

`RepoRulesTest` fails if:
- a runtime `implementation(...)` is added to the app
- any `//` or `/* */` comment appears in any Kotlin source set or the benchmark module
- INTERNET appears in any manifest
- an English string is missing from the Arabic translation

## Tests
| Suite | Where | Command |
|---|---|---|
| JVM unit tests: scorer, calculator, parsing, detectors, CSV, files, folder-grant names, keystroke budget on 1,000 apps + 3,000 contacts | CI | `./gradlew testDebugUnitTest` |
| Device tests: search screen (Espresso), intent contracts (dial, SMS, WhatsApp, Business, Settings, URL, mail), platform contracts (no INTERNET, HOME, ASSIST, tile, widget, voice service, no init providers, backup off, Settings discovery, encrypted clipboard), entry points and keyboard (UI Automator) | CI emulator (API 35) or phone | `./gradlew connectedDebugAndroidTest` |
| Speed: cold, warm and hot startup; frames for a 30-keystroke script; `tacit.query` trace time; KISS comparison | **your phones** | `./gradlew :benchmark:connectedBenchmarkAndroidTest` |
| Speed profile: baseline profile generator | your phone | `./gradlew :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.tacit.benchmark.BaselineProfileGenerator`, then copy the generated `*-baseline-prof.txt` over `app/src/main/baseline-prof.txt` |

The fixtures (`app/src/sharedTest/.../Fixtures.kt`) are generated from a fixed seed, so benchmark numbers are comparable between devices and runs.

## Not done yet
- **Real numbers.** Benchmarks are written but need your phones (one flagship, one budget, plus KISS installed).
- **Applying the speed profile on F-Droid installs.** `app/src/main/baseline-prof.txt` is a hand-written starter. Play installs pick it up automatically; F-Droid installs need `androidx.profileinstaller`, which isn't in the verified version sheet. Add it once you confirm the version.
- detekt and ktlint (versions not in the sheet; `RepoRulesTest` covers the no-comment rule meanwhile)
- MediaStore seeding, FileObserver and scheduled rescans for files
- Signal and Telegram buttons (the code paths exist, but the buttons aren't shown)
- F-Droid fastlane metadata, reproducible-build check, LICENSE file
- Third-party widget hosting (deliberately skipped, see `docs/decisions.md`)

## Build
`./gradlew assembleDebug testDebugUnitTest lintDebug` (JDK 17). CI: `.github/workflows/android.yml` uploads the debug APK.

Toolchain (from `docs/kotlin-android-verified-data-2026-10-07.json`): AGP 9.4.0, Kotlin 2.4.20, Gradle 9.7.1, compileSdk 37, targetSdk 36, minSdk 29.

Licence: GPL-3.0 (proposed, not yet added).
