# PipePipe All Features

This release combines the eleven isolated v5.4 feature branches below. Version `5.4.0-unofficial.9` also fixes the queue-selection regression in `.8`: internal timeline changes and media-source removal cannot undo an explicit selection made by "Play now and keep queue." Automatic transitions and seeks still update the selection normally.

## Included behavior changes

| Feature branch | Changed behavior |
| --- | --- |
| [fix/header-enqueue-consistency](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/fix/header-enqueue-consistency) | Makes playlist header enqueue and enqueue-next actions consistently extend the current queue without changing the active player mode. |
| [feat/2730-nonblocking-feed-refresh](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/feat/2730-nonblocking-feed-refresh) | Keeps the subscription feed usable while refresh work runs in the background. |
| [fix/play-queue-drag-scroll-velocity](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/fix/play-queue-drag-scroll-velocity) | Stabilizes drag-to-reorder scrolling in the play queue, including continuous drags near its boundaries. |
| [fix/play-queue-top-item-velocity](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/fix/play-queue-top-item-velocity) | Keeps the queue viewport anchored when reordering its first items, including asynchronous move updates. |
| [feat/subscription-group-content-rules](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/feat/subscription-group-content-rules) | Adds subscription-group content rules so group feeds can include or exclude different stream types. |
| [fix/main-player-browsing-preserves-queue](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/fix/main-player-browsing-preserves-queue) | Lets you browse other video details while the current player and queue continue playing. |
| [feat/main-player-queue-play-actions](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/feat/main-player-queue-play-actions) | Adds explicit actions to play a browsed item now while keeping the queue, append it, or replace the queue. |
| [fix/main-player-queue-review](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/fix/main-player-queue-review) | Keeps the mini-player overlay synchronized with the playing queue while browsing other details, with queue-action follow-up fixes. |
| [fix/main-player-fullscreen-readiness](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/fix/main-player-fullscreen-readiness) | Defers fullscreen requests until the player and its listeners are ready. |
| [feat/player-mode-switching](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/feat/player-mode-switching) | Switches the active session between main, background and popup playback while retaining the queue, position and playing or paused state. |
| [feat/sponsorblock-editor](https://github.com/darvilp/PipePipeClient/tree/migrate/v5.4/feat/sponsorblock-editor) | Adds a precise SponsorBlock editor with segment drafts and playback controls, and holds the current queue item while editing. |

Each branch also contains the shared queue-selection correction and its regression tests. Feature branch READMEs describe their individual scope; this list describes the combined release.

## Build and release

Use `python3 tools/release.py --check` to verify signing, toolchain and source pins, then `python3 tools/release.py` to build and export a signed local candidate. See [the release runbook](release/README.md) for device validation and publication, and [release notes](release/RELEASE_NOTES.md) for the current version.

---

## Upstream project

The client of [PipePipe](https://codeberg.org/NullPointerException/PipePipe).
