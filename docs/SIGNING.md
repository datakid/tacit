# Signing and publishing a stable release

Your signing key never goes on GitHub. CI builds an unsigned APK; you sign it on your own computer.

## What CI does
- A push to `main` whose newest `docs/release-notes/` file has no suffix (for example `v1.0.0.md`) creates a **draft** release with `tacit-v1.0.0-unsigned.apk` and `SHA256SUMS`.
- Beta notes (for example `v0.6.0-beta.md`) still create a normal prerelease with the debug APK.
- If the release already exists, CI only refreshes its notes. It never touches the files you uploaded.
- Every CI run signs the release APK once with a throwaway key made on the runner, to prove `tools/sign-release.sh` works. That key and its output are discarded. `RepoRulesTest` fails if a key file or a repository secret appears in the repo or the workflow.

## One-time: make your key
```
keytool -genkeypair -v -keystore tacit-release.jks -storetype PKCS12 -alias tacit -keyalg RSA -keysize 4096 -validity 10000
```
Keep the `.jks` file and its password somewhere safe and offline, with a backup. If you lose it, nobody can update Tacit without uninstalling it first. `.gitignore` already blocks `*.jks`.

## Every release
1. Open the draft release and download `tacit-v1.0.0-unsigned.apk` and `SHA256SUMS`.
2. Check the download: `sha256sum -c SHA256SUMS`.
3. Sign it. You need Android SDK build-tools (`zipalign`, `apksigner`).
   - Script (Linux, macOS, WSL, Git Bash): `tools/sign-release.sh tacit-v1.0.0-unsigned.apk tacit-release.jks tacit`. It asks for the password, or reads `TACIT_KS_PASS`. It writes `tacit-v1.0.0.apk` and `tacit-v1.0.0.apk.sha256`.
   - By hand (any shell; on Windows use `apksigner.bat` from `%ANDROID_HOME%\build-tools\<version>\`):
     ```
     zipalign -f -p 4 tacit-v1.0.0-unsigned.apk aligned.apk
     apksigner sign --ks tacit-release.jks --ks-key-alias tacit --v4-signing-enabled false --out tacit-v1.0.0.apk aligned.apk
     apksigner verify --verbose --print-certs tacit-v1.0.0.apk
     ```
4. Read the `certificate SHA-256 digest` line and confirm it is your key.
5. On the draft release, upload `tacit-v1.0.0.apk` and its `.sha256`, and delete the unsigned APK and `SHA256SUMS`.
6. Publish the draft. This creates the tag `v1.0.0` on the commit CI recorded.

Phones running a beta build (debug-signed) must uninstall before installing the signed one. Android only updates over the same key.

## F-Droid
`docs/fdroid/app.tacit.yml` is a draft recipe for the `fdroiddata` repository. It has not been validated against F-Droid's tools.
1. Put your certificate fingerprint in `AllowedAPKSigningKeys` (lowercase SHA-256, no colons).
2. Copy the file to `metadata/app.tacit.yml` in a fork of `fdroiddata`, run `fdroid lint app.tacit` and `fdroid build -l app.tacit`, then open a merge request.
3. The `Binaries` line lets F-Droid compare its own build with your signed APK and publish yours when they match byte for byte. If they differ, F-Droid signs its own build instead and users can't switch between the two without reinstalling.
4. The `Repeatable build` annotation in CI says whether two builds on the same runner match, with a fingerprint per APK part. Between two runners the only difference seen was the commit hash AGP writes to `META-INF/version-control-info.textproto`, so build from the same commit as the tag. That is a first check only; F-Droid builds on a different machine and this has not been tried there.
5. The store text lives in `fastlane/metadata/android/` (English and Arabic). Each release needs a `changelogs/<versionCode>.txt` in both folders. `RepoRulesTest` checks this.
