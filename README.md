# PipePipe All Features

This fork combines the eight feature topics below with upstream development updates. Version `5.5.0-unofficial.17` uses the established All Features application identity and signing certificate for in-place updates.

The release channel is `unofficial/all-features`. APKs and exact source manifests are distributed through [GitHub Releases](https://github.com/darvilp/PipePipeClient/releases). The existing `submit/upstream-v5.5-eight/` branches contain the prepared upstream topics.

See [release/README.md](release/README.md) for build, signing, validation and publication instructions. The application is named **PipePipe All Features (Unofficial)**.

## Included behavior changes

| Submission branch | Changed behavior |
| --- | --- |
| [01-player-safety](https://github.com/darvilp/PipePipeClient/tree/submit/upstream-v5.5-eight/01-player-safety) | Player lifecycle, queue metadata and enqueue-mode guards |
| [02-feed-refresh](https://github.com/darvilp/PipePipeClient/tree/submit/upstream-v5.5-eight/02-feed-refresh) | Usable subscription feeds during refresh |
| [03-content-rules](https://github.com/darvilp/PipePipeClient/tree/submit/upstream-v5.5-eight/03-content-rules) | Subscription-group content rules |
| [04-header-enqueue](https://github.com/darvilp/PipePipeClient/tree/submit/upstream-v5.5-eight/04-header-enqueue) | Queue header actions that preserve the active player mode |
| [05-queue-preserving-playback](https://github.com/darvilp/PipePipeClient/tree/submit/upstream-v5.5-eight/05-queue-preserving-playback) | Queue-preserving browsing, explicit play choices and timestamp routing |
| [06-queue-drag](https://github.com/darvilp/PipePipeClient/tree/submit/upstream-v5.5-eight/06-queue-drag) | Stable edge scrolling and asynchronous viewport restoration |
| [07-sponsorblock-editor](https://github.com/darvilp/PipePipeClient/tree/submit/upstream-v5.5-eight/07-sponsorblock-editor) | Precise segment editing with retained drafts |
| [08-player-mode-switching](https://github.com/darvilp/PipePipeClient/tree/submit/upstream-v5.5-eight/08-player-mode-switching) | Main, background and popup transitions for the active session |

## Build and release

Use `python3 tools/release.py --check` to verify signing, toolchain and source pins, then `python3 tools/release.py` to build and export a signed local candidate. The [release runbook](release/README.md) records acceptance and publication steps.

## Upstream project

The client of [PipePipe](https://codeberg.org/NullPointerException/PipePipe).
