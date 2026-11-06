# PipePipe All Features 5.5.0-unofficial.17

This dogfooding build synchronizes the fork with upstream client dev at `118ffc86b` and extractor main at `28a1a5ee8`.

- Port queue-preserving browsing, explicit playback choices, timestamp routing and mode switching to upstream's Kotlin detail screen and player event architecture.
- Port the precise SponsorBlock editor, retained drafts and queue-advance hold to the new detail components.
- Retain nonblocking feed refresh, subscription-group content rules, header enqueue behavior and queue drag improvements.
- Adopt upstream's SABR token-renewal fix at `b336bb654`. The extractor follows current upstream.

The app is PipePipe All Features (Unofficial), package `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures`, signed with the established All Features certificate. Release APKs are minified and non-debuggable. The arm64 version code is **112404**, supporting an in-place update from `.16`.

Upstream now requires Android 7.0 or later, API 24. The existing content-rule database schema is retained. Updates are manual through the fork's release page.

Use `SHA256SUMS` and the generated source manifest to identify the exact APKs and source revisions. Runtime validation and signed-upgrade results are recorded separately with the local dogfooding handoff.
