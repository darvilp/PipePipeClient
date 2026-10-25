# PipePipe All Features 5.5.0-unofficial.12

Two corrections to the All Features build based on upstream PipePipe 5.5.0:

- Changing the played-items filter during a subscription refresh now updates the visible feed during progress and error states, including when the filter leaves no items. Repeated progress updates keep the displayed snapshot in place.
- Editing a subscription group's content defaults now preserves channel exceptions through intermediate checkbox choices. Exceptions are normalized when the group is saved, so changing the same defaults in a different order no longer discards an unsaved exception.

Includes the same integrated fork features as `5.5.0-unofficial.11`. The extractor and FFmpeg inputs are unchanged. This release adds no database migration beyond the content-rules schema already present in `.11`.

The app remains **PipePipe All Features (Unofficial)**, package `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures`, signed with the established All Features certificate. It is minified and non-debuggable. Arm64 version code is **111904**, allowing an in-place update from `.11`. Updates remain manual through this fork's release page.

Validation includes the focused feed and content-rule emulator regressions, JVM tests, the existing release lint baseline, structural and signing checks for all four ABI APKs, and a controlled signed `.11` to `.12` emulator upgrade retaining subscriptions, a local playlist, speed, pitch and theme. The emulator checks do not establish physical-device playback quality or every possible user configuration.

The previously reported scheduled What's New population behavior remains a separate investigation. The feed correction here concerns played-items filtering during an active refresh.

Use `SHA256SUMS` and `source-manifest.json` to identify the exact APKs and source revisions. All earlier releases remain available.
