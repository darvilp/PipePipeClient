# SponsorBlock editor validation

Validated on 2026-09-23 for `feat/sponsorblock-editor`, based on
`08b277619ac05a5b227ca53a7fe4cb1958663c4d`. This branch remains separate from the
all-features release.

## Scope and review

Issues [1](https://github.com/darvilp/PipePipeClient/issues/1),
[2](https://github.com/darvilp/PipePipeClient/issues/2),
[3](https://github.com/darvilp/PipePipeClient/issues/3),
[4](https://github.com/darvilp/PipePipeClient/issues/4),
[5](https://github.com/darvilp/PipePipeClient/issues/5),
[6](https://github.com/darvilp/PipePipeClient/issues/6), and
[7](https://github.com/darvilp/PipePipeClient/issues/7) cover the implementation.

The editor has explicit Start/End boundaries, exact millisecond text editing,
current-position marking, separate seeking, Video start/end, 100 ms adjustments,
visible category selection, duration, and single-position Highlights. Boundary
previews and draggable handles are excluded.

Review covered service/video ownership, draft UUID and revision checks, upload
completion against replacement/offscreen editors, preservation of unsaved text,
recycled voting rows, and queue-hold ownership. Actionable findings were fixed,
including a device-reproduced dialog callback crash after view destruction. The
save listener is now installed synchronously after showing the dialog.

The hold starts when a matching, visible, resumed editor has Start set and stays
active after End is set. ExoPlayer pauses at item end; PipePipe's completion path
also guards queue advancement. Old editors cannot release a newer owner's hold.
Clearing, successful unchanged-draft submission, leaving the tab/foreground, and
intentional navigation release it. Release does not resume a paused player.
Saved autoplay/repeat preferences are not changed.

## Automated checks

The final source passed these Gradle tasks:

```text
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleDebugAndroidTest
```

Four JVM tests passed, covering parsing, millisecond round trips, overflow,
durations beyond 24 hours, and category-specific validation.

All 32 focused instrumentation tests passed on the S10 in 33.017 seconds.
The suite covers:

- 11 editor tests: fresh marking after paused seeks, explicit zero, missing-boundary
  seeking, exact Video end, invalid input, cancel/reopen, Highlight, unknown
  duration, offscreen snapshots, and hold visibility.
- 13 submission/restoration tests: actual parent activity recreation with an open
  editor, saved/offscreen drafts, replacement editors, video/service isolation,
  UUID/revision isolation, temporary markers, delayed completion, duplicate
  suppression, HTTP failures, and transport failure/retry.
- 4 voting tests: recycled visibility, segment identity across holders, duplicate
  requests, pending rows, and HTTP/transport retry.
- 4 queue tests using real ExoPlayer and local silence sources: pause at the first
  item, explicit Next, release without resuming, stale owners, unrelated videos,
  and ordinary automatic advancement without a draft.

Submission and voting responses are controlled in tests. No fabricated segments
or votes were sent to SponsorBlock.

Lint completed under the repository's existing non-aborting configuration. It
reported 539 warnings, 103 errors, 1,134 fatal findings, and one hint. Review of
locations against added lines found no new diagnostics. Existing translation,
layout, permission/receiver, and LiveData findings remain; this is not a clean
repository-wide lint result.

## Physical-device observations

Tests used the dedicated S10 running Android 12 / API 31. The isolated app was
installed without clearing existing app data.

- Portrait timestamp input, keyboard, shortcut buttons, and confirmation controls
  were visually usable. Video start saved explicit zero. Video end saved the
  player's exact `18.933` second duration.
- Unsaved `0:02.345` text and the open dialog survived rotation and saved correctly.
- Highlight hid End and preserved the selected position.
- A paused seek to `8.123` seconds, followed by clearing boundaries and marking
  Start, produced `0:00:08.123`; the media session was paused at position `8123`.
- With a successor queued, foreground playback paused on item 0 at the current
  video's end. Player logs recorded `playWhenReady=false`, reason 5. Explicit
  Next selected item 1.
- With no active draft, playback advanced automatically from item 0 to item 1.
  Leaving the foreground also released the hold.

## Reproducing the isolated build

The device's existing generic debug database used schema 902, while this branch
uses 901. An initial install could not open that database. Its data was preserved,
and a compatible prior debug APK was restored after verifying its compiled Room
identity matched the existing database. Validation then used the separate package
`InfinityLoop1309.NewPipeEnhanced.debug.sponsorblock`.

Package isolation used an external Gradle init script, without changing the
repository's build settings:

```groovy
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.androidComponents.finalizeDsl { android ->
            android.buildTypes.debug.applicationIdSuffix = '.debug.sponsorblock'
            android.buildTypes.debug.resValue 'string', 'app_name', 'PipePipe SponsorBlock'
        }
    }
}
```

Run the tasks above with `-I <init-script>`. This environment also required
`-Pkotlin.compiler.execution.strategy=in-process`, `--no-daemon`, and
`--max-workers=2` to avoid a local Kotlin daemon connection problem. The sibling
extractor was at `c0cd0d61863f430af86475aaac968fbef245f507`.

Raw logs and screenshots are retained locally under the ignored
`app/build/sponsorblock-validation/` directory. Device/network/account details
are excluded from this document and public issue comments. The delivered APK is
an isolated debug build, not an all-features integration or release artifact.
