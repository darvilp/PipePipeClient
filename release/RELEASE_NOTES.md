# PipePipe All Features 5.5.0-unofficial.13

Four corrections based on reproducible emulator failures in the previous All Features build:

- Normal subscription refresh now uses stream metadata for generic channel tabs. PeerTube live streams remain visible in Live-only groups and stay out of Videos-only groups. Explicit Shorts and Livestreams tabs retain their classifications. Refresh affected subscriptions normally to update cached classifications.
- Collapsing a marked SponsorBlock editor into the mini-player releases its end-of-item queue hold. Progress updates cannot reacquire the hold while the editor is hidden; reopening the visible editor can hold again.
- Changing playback mode while a live stream is paused keeps its source released. Selecting another live queue item and switching modes no longer recreates a source before Play. Resuming prepares the selected item.
- Changing list, card or grid preferences during a feed refresh updates row layouts immediately while preserving the current content snapshot.

All four failures were reproduced on a disposable API 36.1 emulator before product edits, then passed the same focused checks after the fixes. The checks use the production refresh/extractor/database path with controlled PeerTube HTTP responses, the real detail-sheet/editor lifecycle and queue-hold flag, real service playback with local HLS media, and feed-fragment pause/resume with changed preferences.

The release also passes JVM tests, the existing lint baseline and binary/signing checks for all four ABI APKs. A controlled signed `.12` to `.13` emulator upgrade retains subscription, playlist, speed, pitch and theme records, followed by launch and playback smoke checks. These checks do not establish every source, user configuration or physical-device behavior.

The app remains **PipePipe All Features (Unofficial)**, package `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures`, with the established All Features signing certificate. The APK is minified and non-debuggable. Arm64 version code is **112004**, allowing an in-place update from `.12`. Updates remain manual through this fork's release page.

The upstream 5.5.0 base, extractor and FFmpeg pins are unchanged. This adds no database migration and retains the existing All Features functionality. The scheduled What's New population investigation remains separate from these refresh fixes.

Use `SHA256SUMS` and `source-manifest.json` to identify the exact APKs and source revisions. Previous releases remain available.
