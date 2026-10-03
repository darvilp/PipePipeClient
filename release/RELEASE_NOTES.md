# PipePipe All Features 5.4.0-unofficial.8

Local test-drive candidate based on PipePipeClient v5.4.0 and the matching v5.4.0 extractor. Publication is pending user acceptance.

All eleven maintained feature branches were migrated and built separately before integration. This candidate includes consistent header enqueue actions, nonblocking feed refresh, both queue scrolling fixes, subscription-group content rules, browsing without queue replacement, explicit queue play actions and playlist replacement fixes, overlay metadata updates, deferred fullscreen, player-mode switching, and the precise SponsorBlock editor.

The player features use the v5.4 controller and media-item APIs. Existing v5.4 fullscreen readiness and rotation behavior is retained. No experimental SABR diagnostic is included.

The application ID remains `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures`, with visible name `PipePipe All Features (Unofficial)`. The release variant is minified and non-debuggable. Version code is 111504 on arm64, higher than the preceding .7 dogfood build. Updates remain manual through the fork releases page.

Before publication, test the signed APK on your device: update and retained settings/subscriptions/playlists; playback and browsing; queue gestures, Play next and playlist replacement; main/background/popup switching while playing and paused; fullscreen and rotation; feed content rules and refresh; SponsorBlock draft editing, submission and queue hold. Build and automated-check results are recorded separately from this device acceptance.
