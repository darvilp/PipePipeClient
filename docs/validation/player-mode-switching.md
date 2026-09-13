# Player mode switching validation

## Source and scope

Feature branch: `feat/player-mode-switching`, based on client `639262d8c2c5e71ef44445ca2bdd5d9451872695`. Extractor: `4210348141bed6f9544da8eef8175d4ba91adf9f`. Existing worktrees were preserved. The approved plan is in `docs/superpowers/plans/2026-09-13-player-mode-switching.md`.

Switching keeps the existing service, ExoPlayer, queue and adapter. Necessary source-change buffering is accepted. Native PiP, unrelated playback changes and publication are excluded. The release branch's removal of the earlier unproven SABR diagnostic is retained.

## Deterministic results

Debug app and instrumentation APK assembly passed with JDK 25 and the existing Gradle wrapper. On a disposable API 36.1 x86_64 emulator, all **88 regression tests passed** (146.708 seconds): 27 switching tests and 61 existing browsing, queue-action, pending-replacement, enqueue, viewport, continuous-gesture, metadata, fullscreen, insert-next and drag-scroll tests. All **17 applicable JVM tests passed**, with no failures, errors or skips.

The switching tests exercise all six directions while playing, paused and genuinely buffering. Cached metadata, generated media and a gated localhost HTTP server make these cases independent of external streams. Assertions cover service/player/queue/adapter identity, selected item and ordering, intent, settings, source reuse or replacement, recovery positions and final surface ownership. Position drift allows at most one second after accounting for playback elapsed. Paused recovery retains 7000 ms even with the always-start-at-beginning preference enabled.

Additional cases cover browsed details, pending replacement playlists, queue advancement, navigation cancellation by newer playback or shutdown, rapid requests, permission denial, recreation, popup expansion and close-overlay teardown. Production review specifically checked service lifetime, stale main navigation, queue replacement, pause/buffering intent and source selection.

The unchanged baseline reproduced hidden popup menu actions, unnecessary main-to-popup media-manager replacement, invalid unknown-duration recovery and unintended resume from paused audio to popup. Its debug APK SHA-256 was `a3f7a825e7073f659905c9e0e94080b027f03b1c112f82a90a91fabf92337d2f`. The baseline also passed 29 pre-existing offline regressions. Emulator startup and announcement-dialog failures were isolated from the playback assertions.

A paused quality-selection regression was separately reproduced before the bounded metadata-view refresh and then passed with that fix. Deferred popup removal was confirmed to detach the reparented main surface; synchronous window removal resolved the tested ownership failure. These findings are covered by the final passing suite.

Debug lint completed with 1,237 existing error/fatal findings and **zero new error/fatal findings**, compared by issue, message, path and count against the unchanged release baseline. This is not a clean lint result.

## Online source results

The opt-in production-service probe passed for actual **SABR** and **PROGRESSIVE_HTTP** delivery, using the extractor's mweb and visionos clients respectively. Each run exercised all six directions both playing and paused, an audio-origin return to video/popup, an alternative video quality, available external subtitle sources, and screen-off background playback followed by return. The SABR run selected 144p and retained the available TTML subtitle source. This does not establish subtitle rendering or alternate-language audio coverage when the sample has no such selectable track.

Run with the debug app and ordinary instrumentation APK on a disposable device:

```sh
adb -s <device> shell am instrument -w \
  -e class org.schabi.newpipe.player.PlayerModeOnlineSwitchingTest \
  -e playerModeOnlineProbe true -e url '<public stream URL>' \
  -e expectedDelivery SABR -e client mweb -e audioOrigin true -e screenCycle true \
  InfinityLoop1309.NewPipeEnhanced.debug.allfeatures.test/androidx.test.runner.AndroidJUnitRunner
```

For the non-SABR probe use `expectedDelivery PROGRESSIVE_HTTP` and `client visionos`. The probe verifies actual delivery instead of inferring it from the client. A live-window attempt stopped during extraction with “This live stream recording is not available,” before playback or switching; live-edge and time-shift behavior remain unverified. Live-window coverage is separately opt-in with `expectedDelivery LIVE_STREAM` and `live true`.

The online fixture foregrounds MainActivity before extraction because Android can restrict networking for background UIDs. Debug-only network configuration permits HTTP solely to 127.0.0.1 for deterministic gated media; the release network policy is unchanged.

## Acceptance limits

Signed candidate checks and physical-device acceptance are recorded with the generated release artifact, including its exact hash. Emulator state and player-event evidence do not prove physical audible continuity. No phone was connected during the deterministic and online runs. Alternate audio-language selection and live-edge/time-shift preservation require explicit results before being called verified. Activity recreation is covered; a physical orientation/audio-output session remains necessary.

No publication is authorized by this record. Retain the existing release lint baseline and reject new error/fatal findings rather than replacing the baseline.

## Reproducible fixture

The test asset is generated, contains no third-party media, and uses a test pattern plus a 440 Hz tone. Regenerate with:

```sh
ffmpeg -hide_banner -loglevel error -f lavfi -i testsrc2=size=320x180:rate=15 -f lavfi -i sine=frequency=440:sample_rate=44100 -t 30 -c:v libx264 -preset ultrafast -crf 32 -pix_fmt yuv420p -c:a aac -b:a 48k -movflags +faststart app/src/androidTest/assets/player-mode-fixture.mp4
```

Audio-only fixture: `ffmpeg -hide_banner -loglevel error -i app/src/androidTest/assets/player-mode-fixture.mp4 -vn -c:a copy app/src/androidTest/assets/player-mode-fixture.m4a`.
