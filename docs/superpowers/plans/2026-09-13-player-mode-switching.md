# Restore Main, Background, and Popup Player Switching

## Summary and release criteria

Restore reliable three-way switching in the fork using the existing player service, queue, and `PlayerType` values. Expose the two alternative modes in the separate queue’s overflow menu, and route the popup’s existing expand button through the same switching behavior.

**Accepted continuity requirement:** preserve the playback session and avoid unnecessary reloads. A brief buffering gap is acceptable when changing media sources requires it. Gapless playback is not a release requirement.

Success means switching preserves the active queue, selected item, playback position, play/pause intent, and playback settings, with the correct surface and controls afterward. Native Android PiP, a new player engine, and unrelated playback fixes are excluded.

## Implementation changes

### 1. Establish one operation for switching the current session

- Add a main-thread `Player.switchPlaybackMode(PlayerType target)` operation within the existing player implementation. It changes presentation and any required media selection while retaining the running service, ExoPlayer instance, queue, and queue adapter.
- Add a shared navigation entry point for explicit mode changes. Popup/background requests use the bound player directly; main-player requests open the active item’s detail screen and complete the mode switch when its player surface is ready.
- Keep explicit switching separate from starting a video, replacing a queue, and enqueueing. Those existing actions retain their current behavior.
- Expose `getPlayWhenReady()` through `PlayerHolder` where navigation needs it. Do not use `isPlaying()` to infer playback intent while buffering.
- Serialize switches on the main thread. Repeated requests for the same mode are harmless. Use a small request revision counter to reject delayed main-player navigation after a newer mode request, new playback request, or service shutdown. Read the active queue item again when navigation completes.

### 2. Preserve playback state and clean up surfaces

- Preserve queue identity, order, selected item, shuffle/repeat, speed, pitch, skip-silence, mute, selected audio track, and applicable video-quality/subtitle choices. Keep video preferences available while background mode disables video and subtitles.
- Capture recovery position before changing source selection. Associate it with the current queue item; do not assign an old item’s position to a newly selected item. Unknown duration must not clamp a valid position to an invalid timestamp.
- Preserve `playWhenReady` for playing, paused, and buffering states. Returning to main must not call an unconditional `play()`.
- Remove the popup expand button’s service-stop behavior. Detach the popup window and its close overlay explicitly, then attach the existing player view to main playback. Ensure popup/background transitions perform the corresponding cleanup without leaving invisible overlay windows.
- Reuse media sources for main↔popup transitions when the selected streams remain unchanged. For audio/video transitions, reuse the existing source only when it already supports the required tracks, using the existing source-compatibility logic and SABR handling. Otherwise perform one recovery-backed reload.
- Do not add retry loops or rebuild the player to mask a failed transition. Surface genuine playback errors through the existing error handling.

### 3. Restore consistent navigation and menu behavior

- In `PlayQueueActivity`, show “Switch to main,” “Switch to popup,” and “Switch to background” according to the active mode, hiding only the current mode. Disable these actions until the service has a valid active queue.
- Refresh the menu after service binding and mode changes, including changes made outside the queue screen.
- Keep the queue screen open after switching to popup or background. Switching to main opens the active item’s main-player screen. Preserve the existing behavior of entering fullscreen when expanding from popup; otherwise honor the main-player fullscreen preference.
- Treat main playback with its video surface hidden as main mode. Selecting background makes it explicit background mode; selecting popup opens the overlay. This does not change the existing minimize-on-exit preference.
- Check popup permission before changing playback or removing the existing surface. Use the existing permission guidance when permission is missing; granting permission requires the user to select popup again.
- On return to main, explicitly synchronize the detail screen with the service’s active item and queue. A previously browsed video or pending replacement playlist must not start or replace that queue.
- Route the existing popup expand action and automatic main-to-popup minimization through the shared switching behavior. Detail-screen actions that intentionally start or enqueue another video remain playback actions.
- Continue using the existing service-selection mechanism for normal and Android Auto services. This feature must not start a second service merely to change player mode.

No database migration, new library, package identity change, or public external API is required. The interface additions are internal to player switching and navigation.

## Test plan

### Deterministic regression coverage

Add instrumentation coverage around the production player service and activities, following the existing queue-activity test harness. Use cached stream metadata and controlled media responses for reproducible state and buffering tests; keep live YouTube checks separately opt-in.

- Exercise **all six directed transitions**, each while playing, paused, and buffering.
- Assert queue and player identity, order, index, playback intent, settings, and the final surface owner. Verify transitions do not reset to zero, jump to another item, create duplicate playback, or leave popup overlays attached.
- With controlled on-demand media, require the recovery seek target to match the captured position. After playback settles, allow at most one second of unexplained position drift, accounting for actual playback elapsed during the transition.
- Verify main↔popup with unchanged streams does not recreate the media-source manager; verify a required source change performs a single reload.
- Cover a different detail page being browsed, an explicit pending replacement playlist, queue advancement during main navigation, rapid repeated switching, denied popup permission, and service disconnection.
- Cover popup expansion and return after orientation/activity recreation. For live streams, preserve live-edge versus time-shifted intent rather than comparing absolute on-demand timestamps.

### Integration and artifact validation

- Build the debug app and test APK with `:app:assembleDebug` and `:app:assembleDebugAndroidTest`; run the new switch tests and the existing browsing-policy, pending-queue, enqueue, and queue-gesture regressions on the emulator.
- Run the project’s applicable unit tests and lint checks, inspect their results, and distinguish pre-existing lint findings from new findings.
- Run live SABR and non-SABR transitions, including alternate audio tracks, video quality, screen-off/background playback, and popup return.
- Validate the signed, minified release candidate on an explicitly targeted physical phone. Record the installed artifact hash, state preservation, audible gaps, and any playback errors. Emulator results alone do not establish physical audio continuity.
- Compare failures or apparent improvements with the baseline artifact before attributing them to the change. A skipped live case is recorded as unverified.

Release is blocked by state loss, wrong-item playback, unintended resume/pause, crashes, stalled playback, or broken surface ownership. A measured buffering gap during a necessary source change is acceptable.

## Branching, review, and release preparation

1. During implementation, save this plan under `docs/superpowers/plans/2026-09-13-player-mode-switching.md`.
2. Record client/extractor revisions and worktree status. Create an isolated `feat/player-mode-switching` branch from the reviewed `personal/all-completed-features` baseline, currently `639262d8c`. Preserve existing worktrees and do not import unrelated upstream changes.
3. Implement and test the shared switching operation, then the controls/navigation integration. Review the complete diff specifically for service lifetime, stale navigation, queue replacement, pause/buffering behavior, and source reloads.
4. Merge the reviewed feature into the integration branch, then into `unofficial/all-features`, currently `49a918586`. Preserve that branch’s release configuration and previous corrections.
5. Build the next unofficial candidate through the existing release workflow. Retain the established application ID and signer, increment the unofficial version/code above previously distributed artifacts, and update the source manifest and validation record.
6. Copy the validated APK to `E:\apks` with a unique version/commit/ABI filename and verify its checksum. Prepare release notes describing restored switching and the accepted buffering limitation. Publishing remains a separate release action governed by the existing workflow.

## Assumptions and defaults

- The first release uses the existing separate queue overflow menu; it adds no new picker, settings page, or notification action.
- Current-session switching preserves user-selected playback settings. Starting a different video retains its existing autoplay and queue-replacement semantics.
- Brief necessary buffering is acceptable; seamless audio across every source type is deferred.
- Existing Android version support, custom popup behavior, extractor integration, and unofficial update identity remain in place.
- Implementation may repair transition-specific defects needed to meet these criteria, but broader SABR, Android Auto handoff, and player-engine work require a separate scope.
