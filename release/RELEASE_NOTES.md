# PipePipe All Features 5.4.0-unofficial.8

All Features release based on PipePipeClient v5.4.0 and the matching v5.4.0 extractor. The local arm64 candidate passed the user test drive and was approved for publication.

All eleven maintained feature branches were migrated and built separately before integration. This candidate includes consistent header enqueue actions, nonblocking feed refresh, both queue scrolling fixes, subscription-group content rules, browsing without queue replacement, explicit queue play actions and playlist replacement fixes, overlay metadata updates, deferred fullscreen, player-mode switching, and the precise SponsorBlock editor.

The player features use the v5.4 controller and media-item APIs. Existing v5.4 fullscreen readiness and rotation behavior is retained. No experimental SABR diagnostic is included.

The application ID remains `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures`, with visible name `PipePipe All Features (Unofficial)`. The release variant is minified and non-debuggable. Version code is 111504 on arm64, higher than the preceding .7 dogfood build. Updates remain manual through the fork releases page.

Changed features to exercise on your device: update and retained settings/subscriptions/playlists; playback and browsing; queue gestures, Play next and playlist replacement; main/background/popup switching while playing and paused; fullscreen and rotation; feed content rules and refresh; SponsorBlock draft editing, submission and queue hold. Build and automated-check results are recorded separately from this device acceptance.

Validation: all four signed ABI builds passed identity, signer and alignment checks; 21 JVM tests and 54 selected Android regression tests passed. Android regressions ran on an API 36 TV emulator against the debug candidate. Release lint has zero new errors against the unchanged baseline, with 1,235 existing error/fatal findings.

The user-accepted local arm64 APK was built from `a82656a55662fe2df83c48acb0bdfd24beb13b30`, with SHA-256 `11b608bf3d5dee6273204f58f529022c05409ddada64b0f346db8dbadc4a34d5`. Subsequent release commits update instructions, tooling, notes and release history without changing application source. GitHub assets are rebuilt by the shared release entry point; their exact revisions and hashes are provided in `source-manifest.json` and `SHA256SUMS`.
