# PipePipe All Features 5.5.0-unofficial.11

Local test-drive candidate based on the exact upstream PipePipe v5.5.0 release and its pinned extractor. Publication is pending device acceptance and a separate user request.

Includes upstream list multi-selection, fullscreen touch locking, revised pinch zoom, live chat, comment and channel-ID filtering, preferred audio quality, DeArrow, and YouTube/SoundCloud/PeerTube/NicoNico fixes. Upstream fullscreen minimization and notification seeking fixes are included.

Retains all eleven maintained fork features and the `.9` queue-selection correction. Each stable feature branch was independently built with its instrumentation APK and JVM tests before integration. History header long-press enqueue actions coexist with upstream's new sort menu. Feed multi-selection works alongside nonblocking refresh. SponsorBlock keeps the precise editor, drafts and queue holds while adopting upstream's separate global-enable and skipping preferences. Pending queue, mode-switch and fullscreen state remain preserved across recreation.

The package remains `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures`, with label `PipePipe All Features (Unofficial)` and the established signing certificate. The signed release variant is minified and non-debuggable. Arm64 version code is 111804, allowing an in-place update from `.10` with the same signer. Updates remain manual through the fork release page.

Includes a focused fix for a pre-existing media-session crash when a queue item has no thumbnail. Missing and empty artwork is omitted from queue metadata; valid artwork is retained. Emulator mode-switch validation exposed the crash.

Also makes the new upstream chat-panel cleanup safe before player views are created. Headless queue regressions exposed the missing view guard; observer cleanup and normal panel hiding are preserved.

Test queue actions while playing and paused, main/background/popup switching, hidden YouTube playback, live chat, list selection during refresh, fullscreen/rotation and touch locking, SponsorBlock toggles and editing holds, DeArrow, and subscription content rules. The previously reported scheduled What's New refresh behavior remains an unresolved investigation; this sync does not claim to fix it.

Use the exported `SHA256SUMS` and `source-manifest.json` to identify the exact APK and client/extractor revisions. Build, emulator and signed-device acceptance are separate validation steps. Previous releases, original feature refs and the older unfinished merge are preserved.
