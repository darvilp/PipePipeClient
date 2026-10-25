# PipePipe All Features 5.5.0-unofficial.15

This update fixes playback and subscription-group edge cases while retaining the upstream 5.5.0 base and existing All Features behavior.

- Honor explicit same-video URL timestamps, including zero, and preserve position when browsing requires an internal source reload.
- Keep the requested start position when using Play now and keep queue.
- Preserve a replacement video's requested timestamp while its metadata loads. Progress saving waits until playback owns the selected queue item.
- Store explicit same-video URL seeks in the active queue so a pending source reload cannot restore the previous position, including when seeking to zero.
- Apply description and comment timestamps to the displayed stream. A timestamp for another stream can start it from main, audio or popup playback; a timestamp for the current stream retains its queue and mode.
- Keep an explicit seek when a pending source reload finishes, including a seek back to zero.
- Resume paused live playback at the current live default, including after a mode change. Selecting a VOD while live playback is paused retains that VOD's saved position.
- Release the SponsorBlock editor's end-of-item queue hold when fullscreen covers the editor, and prevent hidden refreshes from reacquiring it.
- Wait for saved group defaults and channel memberships before editing or saving content rules.
- Snapshot group membership and rules at Save so later edits cannot change a queued transaction.
- Preserve channel exceptions across temporary membership changes and search-result deliveries within the same draft.
- Restore the content-rule screens correctly after saved-state serialization.

Timestamp regressions verify the first prepared position and an actual requested seek, so natural playback cannot produce a false pass. Regression checks use generated local media, cached metadata, controlled HTTP responses, the real player service and Room database, and actual description/comment click handlers. These checks do not establish every provider, user configuration or physical-device behavior. Signed upgrade acceptance is recorded separately from debug instrumentation.

The app remains **PipePipe All Features (Unofficial)**, package `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures`, with the established All Features signing certificate. Release APKs are minified and non-debuggable. The arm64 version code is **112204**, allowing an in-place update from `.13`. Updates remain manual through this fork's release page.

The extractor and FFmpeg pins are unchanged, and this release adds no database migration. Use `SHA256SUMS` and `source-manifest.json` to identify the exact APKs and source revisions. Previous releases remain available.
