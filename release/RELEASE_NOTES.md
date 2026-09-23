# PipePipe All Features 5.3.1-unofficial.5

Public test-drive prerelease from the darvilp fork. This update adds the SponsorBlock editor to the all-features build based on upstream PipePipe 5.3.1.

## Upstream 5.3.1 sync

- Includes upstream fullscreen gesture and rotation controls, title-row interaction, search-history fixes, comment formatting, NicoNico login updates, and channel toolbar changes.
- Uses PipePipeExtractor 5.3.1 for the matching extraction fixes.

## SponsorBlock editor

- Set exact Start and End times with millisecond input, the current player position, video boundaries, or 100 ms adjustments. Highlights use one position.
- See the segment category and duration, jump to either boundary, and keep unsaved draft times while navigating or rotating.
- While a visible draft has a Start time, playback pauses at that video's end instead of advancing the queue. Explicit Next still works; leaving the editor releases the hold.
- Submission and voting handle retries and stale responses without applying them to a replacement editor or recycled row.
- The editor passed focused JVM and device instrumentation tests on an isolated debug package. The signed all-features release has not been exercised on hardware yet.

## Restored player switching

- The separate queue's overflow menu offers the two alternative playback modes. Popup and background selections keep the queue screen open; main playback returns to the active item.
- Switching retains the player, queue, selected item, position, play/pause intent, and current playback settings. A paused or buffering session does not resume merely because its mode changed.
- Expanding the popup returns to fullscreen main playback without stopping the service. Popup windows and the close overlay are removed before the player view changes owner. Fullscreen return restores the visible video after rotation from the mini-player.
- Returning to main uses the active queue rather than a different video or replacement playlist being browsed. Newer requests supersede delayed navigation.
- Compatible sources are reused. A necessary audio/video source change restores the captured position and video controls, including when background playback began with an audio-only source.
- Paused quality controls update when a new quality source is prepared, so the displayed selection matches playback before and after switching.

## Continuity and acceptance

A brief buffering gap is accepted when changing the required source. Gapless audio across every source type is not promised. The validation record separates isolated device tests from acceptance of this exact signed release; the latter remains unverified.

## Installation and updates

The release keeps the all-features application ID and the signing key used for `.4`. Its version name is `5.3.1-unofficial.5`, with arm64 version code `111204`; the minified release variant is not debuggable. Updates remain manual through the fork's releases page, with official automatic updates disabled. Choose `arm64-v8a` for almost every current Android phone or tablet.

Native Android PiP, a new playback engine, and broader hidden-playback or Android Auto handoff fixes are outside this change.
