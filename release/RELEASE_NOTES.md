# PipePipe All Features 5.3.1-unofficial.4

Public test-drive prerelease from the darvilp fork. This build combines the all-features queue, browsing, playlist, fullscreen, and player-switching work with upstream PipePipe 5.3.1.

## Upstream 5.3.1 sync

- Includes upstream fullscreen gesture and rotation controls, title-row interaction, search-history fixes, comment formatting, NicoNico login updates, and channel toolbar changes.
- Uses PipePipeExtractor 5.3.1 for the matching extraction fixes.

## Restored player switching

- The separate queue's overflow menu offers the two alternative playback modes. Popup and background selections keep the queue screen open; main playback returns to the active item.
- Switching retains the player, queue, selected item, position, play/pause intent, and current playback settings. A paused or buffering session does not resume merely because its mode changed.
- Expanding the popup returns to fullscreen main playback without stopping the service. Popup windows and the close overlay are removed before the player view changes owner. Fullscreen return restores the visible video after rotation from the mini-player.
- Returning to main uses the active queue rather than a different video or replacement playlist being browsed. Newer requests supersede delayed navigation.
- Compatible sources are reused. A necessary audio/video source change restores the captured position and video controls, including when background playback began with an audio-only source.
- Paused quality controls update when a new quality source is prepared, so the displayed selection matches playback before and after switching.

## Continuity and acceptance

A brief buffering gap is accepted when changing the required source. Gapless audio across every source type is not promised. The validation record separates deterministic emulator tests, online source checks, and physical acceptance of the exact signed artifact. Any unverified case remains unverified; this candidate is not publication approval.

## Installation and updates

The release keeps the all-features application ID but starts a new public signing lineage. Its version name is `5.3.1-unofficial.4`, with arm64 version code `111104`; the minified release variant is not debuggable. Updates remain manual through the fork's releases page, with official automatic updates disabled. Choose `arm64-v8a` for almost every current Android phone or tablet.

Native Android PiP, a new playback engine, and broader hidden-playback or Android Auto handoff fixes are outside this change.
