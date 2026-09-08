# PipePipe All Features 5.3.1-beta-unofficial.1

Unofficial prerelease candidate from the darvilp fork. It retains the completed all-features integration: queue enqueue/play actions, browsing without replacing playback, nonblocking refresh, fullscreen controls and subscription-group content rules.

## Corrections

- Keep the queue viewport stable when dragging the first visible item down. Reversing after reaching the top no longer retains an anchor that undoes later edge scrolling. Row margins are included in the saved viewport offset.
- Preserve an explicitly requested playlist through service callbacks, including when its selected video is already playing. Passive browsing still retains the active queue.
- Update the main-player overlay as playback advances while unrelated details remain open.
- Retain a direct-fullscreen request until the player and listener are ready, and consume it after fullscreen is entered.
- Add Room regression coverage showing that normal refresh already repairs migrated Shorts classifications while preserving same-refresh classifications and group overrides. No database behavior or schema change was needed for that review item.

## Installation and updates

This candidate uses the existing all-features application ID and established signing certificate, with arm64 version code `110804`. Its release variant is not debuggable. The app label explicitly identifies the unofficial build. Download future updates from the darvilp release page in Settings; automatic official updates are disabled and old official update jobs are cancelled.

## Validation and known limits

Regression coverage includes actual Android queue layout/touch-helper behavior, fragment callbacks and Room migration/refresh. The original lint backlog remains; the release script rejects new error/fatal findings relative to the recorded integration baseline. See the candidate's validation record for tests performed against its exact APK hash, including device upgrade and playback acceptance.

The previous SABR hidden-player diagnostic is removed because it was not established as a fix. The reported hidden-playback issue is not claimed to be resolved. Physical-device and minified-release acceptance must be recorded separately from debug regression results.
