# PipePipe All Features 5.5.0-beta1-unofficial.10

Local test-drive candidate based on upstream PipePipe v5.5.0-beta1 and its matching extractor. This is a beta candidate; publication is pending device acceptance and a separate user request.

Includes upstream YouTube playback and livestream recovery fixes, SABR response reloading, the switch to MWEB/SABR as the only YouTube endpoint, DeArrow titles and thumbnails, PeerTube updates, and optional Bluetooth seeking.

Retains all eleven maintained fork features and the `.9` queue-selection correction. Each feature was independently built with its instrumentation APK and JVM tests before integration. The SponsorBlock merge retains draft editing and queue holds while adopting upstream's preference-derived enabled state and keeping the tab visible when disabled. Pending queue, mode-switch and fullscreen state remain preserved across recreation.

The package remains `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures`, with label `PipePipe All Features (Unofficial)` and the established signing certificate. The signed release variant is minified and non-debuggable. Arm64 version code is 111704, allowing an in-place update from `.9` with the same signer. Updates remain manual through the fork release page.

Test queue actions while playing and paused, main/background/popup switching, hidden YouTube playback, long videos and live streams, fullscreen/rotation, SponsorBlock toggles and editing holds, DeArrow titles/thumbnails, and subscription content rules. The previously reported scheduled What's New refresh behavior remains an unresolved investigation; this sync does not claim to fix it.

Use the exported `SHA256SUMS` and `source-manifest.json` to identify the exact APK and client/extractor revisions. Build, emulator and signed-device acceptance are separate validation steps. Previous releases, original feature refs and the older unfinished merge are preserved.
