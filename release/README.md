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

## Build a candidate

Use JDK 25, Python 3.11 or later, and an Android SDK with compile SDK 37 and build-tools 37.0.0. Set `JAVA_HOME` and `ANDROID_HOME` for that installation. The repository supplies Gradle 9.5.1.

Provide `KEY_PATH`, `KEY_STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` through the private build environment. Use the durable key matching `signer_sha256` in the manifest. Do not commit a keystore, passwords or local signing paths. A different key cannot update the existing installation.

```bash
cd PipePipeClient
tools/build-unofficial-release.sh
```

The script refuses missing signing inputs and dirty or mismatched source. It runs JVM tests, release assembly and release lint, then verifies every ABI APK's package, version, non-debuggable manifest, SDK range, native ABI, 16 KB zip alignment and signing certificate. It emits a timestamped directory under `build/unofficial`, including four APKs, checksums, manifests and release notes. It refuses an existing candidate directory. An optional first argument selects the parent output directory.

The current counter is base version code `1112`, producing arm64 code `111204`, with version name `5.3.1-unofficial.5`. Increment the fork counter for every subsequent distributed build, independently of the upstream version name. Never overwrite an old APK or reuse a public release tag.

## Validation limits and lint baseline

The original integration source `76cd32aa3` reports 1,237 lint error/fatal findings, including 1,132 `ExtraTranslation` findings. The reviewed corrections produce the identical error set. This is **not a clean lint result**. `lint-errors-baseline.json` records the original findings by issue, message, relative path and count. The release script rejects additional error/fatal findings and reports the remaining existing findings. Do not regenerate that baseline from a failing candidate to make the check pass.

Android callback, database and continuous-drag regressions accompany the fixes. These tests do not replace testing the signed, minified candidate on a device. Before publishing, record the candidate hash and verify an in-place upgrade, preserved subscriptions/playlists/settings, queue gestures in both queue screens, explicit playlist replacement and Next, browsing-overlay advancement, fullscreen/orientation, speed/pitch, and hidden playback. The earlier SABR diagnostic was unproven and is removed on this release branch; its removal is not a claim that the reported hidden-playback failure is solved.

## Publication

After candidate acceptance and publication approval:

1. Make both exact source revisions available on the fork repositories using normal pushes.
2. Tag the tested client commit, for example `all-features/v5.3.1-unofficial.5`, refusing an existing tag.
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
