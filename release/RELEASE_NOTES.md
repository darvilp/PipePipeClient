# PipePipe All Features 5.5.0-unofficial.16

This update improves recovery when YouTube rejects a SABR proof-of-origin token.

- Renew the rejected token provider before retrying, instead of repeatedly submitting a cached session token. Recovery remains bounded to three retries and resets its budget after receiving media.
- Keep replacement tokens paired with their visitor identity and client version. When those change, fetch the matching player response, streaming URL and configuration together, and discard the previous identity's playback cookie.
- Let an in-flight token request retry against the replacement provider if another request retires its minting session. Return the replacement token and metadata together, preserve cancellation, and bound repeated retirement.

Seventeen controlled Android emulator regressions passed, covering token renewal, retry limits, identity consistency in the next SABR request, failed refresh, concurrent mint retirement, cancellation, browsing and source preservation. All 25 JVM tests passed. The existing lint baseline passed with 1,235 existing error/fatal findings and no new findings. A signed .15-to-.16 upgrade retained the seeded subscription, playlist and playback preferences.

The reported video was also tested on an Android API 36.1 emulator. The unchanged .15 baseline played beyond the reported cutoff both in fullscreen and after switching to Home with audio-only playback. The original Android 17 focus-loss failure was not reproduced on this emulator, so these recovery fixes do not establish the cause of the initial token rejection. Physical-device behavior remains untested.

The app remains PipePipe All Features (Unofficial), package `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures`, with the established signing certificate. Release APKs are minified and non-debuggable. The arm64 version code is **112304**, supporting an in-place update from `.15`. Updates remain manual through this fork's release page.

The extractor revision is pinned in `source-manifest.json`. The FFmpeg pin is unchanged, and there is no database migration. Use `SHA256SUMS` and the generated source manifest to identify the exact APKs and source revisions. Previous releases remain available.
