# PipePipe All Features — unofficial release channel

This branch prepares the integrated all-features build for the darvilp fork. APKs are GitHub Release assets, not files committed to this branch. Creating this local branch does not publish it. Push the exact client and extractor revisions and verify their availability before publishing a candidate.

The release application ID is `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures`, matching the existing all-features installation. The release variant is **not debuggable**. Its label is **PipePipe All Features (Unofficial)**. Updates are manual through the fork's releases page; this build cancels old official update jobs and does not run the official update checker.

## Recreate the source

Download the candidate's `source-manifest.json` alongside its APK. That generated manifest includes the exact `client_commit`; the tracked manifest records the integration basis instead, avoiding a self-referencing commit hash.

From the directory containing the downloaded manifest:

```bash
git clone https://github.com/darvilp/PipePipeClient.git PipePipeClient
git clone https://github.com/darvilp/PipePipeExtractor.git PipePipeExtractor
client_revision=$(python3 -c 'import json; print(json.load(open("source-manifest.json"))["client_commit"])')
extractor_revision=$(python3 -c 'import json; print(json.load(open("source-manifest.json"))["extractor_commit"])')
git -C PipePipeClient checkout --detach "$client_revision"
git -C PipePipeExtractor checkout --detach "$extractor_revision"
```

Both checkouts must be clean. The sibling extractor layout is required by `settings.gradle`. The build script checks the extractor revision, the client integration ancestry and the tracked `ffmpeg/ffmpeg-kit.aar` SHA-256. Retain the project licenses and dependency notices when distributing corresponding source and binaries.

## One entry point for local and CI releases

From the client checkout, run:

```bash
python3 tools/release.py --check  # preflight only; no build or export
python3 tools/release.py          # signed local test-drive candidate
```

GitHub Actions calls this same entry point with `--output`. `tools/build-unofficial-release.sh` is its internal build backend; do not create per-release wrappers or invoke Gradle directly to produce a distributable APK. The entry point never pushes, tags, creates a release or publishes anything.

Use JDK 25, Python 3.11 or later, and the Android SDK versions in `source-manifest.json`. The script honors `JAVA_HOME` and `ANDROID_HOME`/`ANDROID_SDK_ROOT`. On this WSL host it can discover the installed JDK 25 and `~/Android/Sdk`; CI provisions the manifest toolchain explicitly. The backend uses two Gradle workers by default, Kotlin compilation in-process, a 4 GiB JVM heap and no configuration cache. Set `UNOFFICIAL_MAX_WORKERS` only when needed.

### Private signing configuration

The standard local configuration is `${XDG_CONFIG_HOME:-$HOME/.config}/pipepipe/release.env`. This host has a private link there to the existing signing setup. `PIPEPIPE_RELEASE_ENV` may select a different private file. No chat-history search or temporary credential reconstruction should be needed again.

The file contains literal `NAME=value` assignments, optionally with `export`, shell quoting and comments. It is parsed as data, never sourced as shell code. Use absolute paths or `~`; shell substitutions and variable expansion are not performed. Keep the file private, outside Git. The accepted settings are:

- `KEY_PATH`, `KEY_STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Existing `KEYSTORE_FILE` and `KEYSTORE_PASSWORD` are accepted as aliases for `KEY_PATH` and `KEY_STORE_PASSWORD`.
- Optional `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `UNOFFICIAL_MAX_WORKERS`, `PIPEPIPE_RELEASE_OUTPUT`.

Nonempty process environment values override the file, including legacy aliases. Canonical names take precedence over legacy names within each source. CI skips automatic local-file loading and uses its four GitHub secrets; an explicitly selected file is still honored. Never print or commit a keystore, alias, password, base64 payload or actual private setup path. Never generate a substitute key. Preflight checks the selected private-key certificate against `signer_sha256` before Gradle runs. Build output masks the loaded signing values.

The GitHub workflow maps `PIPEPIPE_ALL_FEATURES_KEYSTORE_BASE64`, `PIPEPIPE_ALL_FEATURES_KEYSTORE_PASSWORD`, `PIPEPIPE_ALL_FEATURES_KEY_ALIAS`, and `PIPEPIPE_ALL_FEATURES_KEY_PASSWORD` to the same signing inputs. Check their names with `gh secret list --repo darvilp/PipePipeClient` if diagnosing CI setup; their presence does not replace the local private key file.

### What each run verifies and exports

Both modes require clean client and sibling extractor checkouts and check source pins, integration ancestry, FFmpeg hash, Gradle/version metadata, toolchain and signer. Build mode additionally runs JVM tests, release assembly and the unchanged lint baseline, then verifies every ABI APK's package, version, non-debuggable manifest, SDK range, native ABI, 16 KB ZIP alignment and certificate.

Each successful build writes four APKs, `SHA256SUMS`, per-ABI manifests, the exact source manifest and release notes into a new timestamped directory. Local WSL exports default to `E:\apks\pipepipe-all-features` when `/mnt/e/apks` exists; otherwise they use `build/unofficial`. Override with `--output <directory>` or `PIPEPIPE_RELEASE_OUTPUT`. The script refuses an existing candidate directory. Report the exact arm64 filename, SHA-256 and signer to the tester.

Before every newly distributed app build, increment the fork counter and base version code in `app/build.gradle` and match `source-manifest.json`. Do not automatically modify versions or commit source during a release run. Do not overwrite old APKs or reuse a public tag. Documentation/tooling changes alone do not replace an already tested APK; retain its original source commit and hash.

Run `python3 tools/test_release.py` when changing the entry point; CI runs the same checks before loading signing secrets.

## Validation limits and lint baseline

The original integration source `76cd32aa3` reports 1,237 lint error/fatal findings, including 1,132 `ExtraTranslation` findings. Current counts are reported by the release gate. Passing the baseline is **not a clean lint result**. `lint-errors-baseline.json` records the original findings by issue, message, relative path and count. The release script rejects additional error/fatal findings and reports the remaining existing findings. Do not regenerate that baseline from a failing candidate to make the check pass.

Android callback, database and continuous-drag regressions accompany the fixes. These tests do not replace testing the signed, minified candidate on a device. Before publishing, record the candidate source commit, hash and device acceptance and verify an in-place upgrade, preserved subscriptions/playlists/settings, queue gestures in both queue screens, explicit playlist replacement and Next, browsing-overlay advancement, fullscreen/orientation, speed/pitch, and hidden playback. The earlier SABR diagnostic was unproven and is removed on this release branch; its removal is not a claim that the reported hidden-playback failure is solved.

## Publication

After candidate acceptance and publication approval:

1. Make both exact source revisions available on the fork repositories using normal pushes.
2. Tag the accepted client commit as `all-features/v<version_name>` from its manifest, refusing an existing tag.
3. Let the tag workflow create a draft prerelease in `darvilp/PipePipeClient` with the four verified ABI APKs, checksums, generated manifests and release notes.
4. Verify the draft target and downloaded asset hashes before publishing it as a prerelease.

Keep previous releases and local APK copies. A safe rollback may require rebuilding corrected source with a higher version code; do not assume an older APK can downgrade newer database state. Runtime acceptance remains device-specific even though every ABI split receives the same structural verification.

## Repeat the controlled upgrade probe

`UnofficialReleaseUpgradeProbe` is separate from ordinary test runs. It uses Android framework instrumentation so test-library references do not conflict with the minified app. Use it only on a disposable acceptance device: it adds one clearly named subscription and local playlist and sets speed, pitch and theme preferences.

1. Install and launch the older all-features APK with the established signer.
2. Build the probe APK with `./gradlew :app:assembleDebugAndroidTest -PunofficialUpgradeProbe=true` and sign a separate copy with that same signer. Ordinary test builds omit that property and use AndroidJUnitRunner. Install the test APK on the acceptance device.
3. Run `adb -s <device> shell am instrument -w -e releaseUpgradePhase seed InfinityLoop1309.NewPipeEnhanced.debug.allfeatures.test/org.schabi.newpipe.release.UnofficialReleaseUpgradeProbe`. Require `Release upgrade seed: PASS` in the result.
4. Install the verified release APK with `adb install -r`, without uninstalling or clearing the target app. Launch the upgraded app.
5. Run the same instrument with `-e releaseUpgradePhase verify` and require `Release upgrade verify: PASS`. It checks the non-debuggable release version, retained subscription, playlist, speed, pitch and theme, and the legacy worker's public constructor through the release class loader.

The test checks controlled persisted records; it does not establish playback quality, queue touch behavior on hardware, or preservation of every possible user configuration. Always target the intended device explicitly with `adb -s`.

## Repeat the live queue regression

The ordinary queue tests cover deferred adapter notifications, viewport offsets, gesture cancellation and continuous dragging. `PlayQueueActivityGestureTest` adds an opt-in online test using the real player service, separate queue activity and Android touch injection. It starts a synthetic queue of public sample videos and stops its service and activities afterward. Run only on a disposable test device with the debug app and test APK installed:

```bash
adb -s <device> shell am instrument -w -e queueActivityProbe true \
  -e class org.schabi.newpipe.player.PlayQueueActivityGestureTest \
  InfinityLoop1309.NewPipeEnhanced.debug.allfeatures.test/androidx.test.runner.AndroidJUnitRunner
```

Require both cases to pass: the dragged first row is playing, and another row is playing. Each one-row drag must reorder once and leave the viewport at position 0. This debug regression does not replace acceptance of the signed release APK on the target phone.
