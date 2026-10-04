# PipePipe All Features 5.4.0-unofficial.9

Fixes the .8 regression where "Play now and keep queue" could insert the picked video but continue playing the previous one. ExoPlayer's internal timeline and source-removal callbacks can no longer overwrite an explicit queue selection while the new source loads. Automatic transitions, seeks and the blocked-playback guard retain their normal behavior.

The fix is present on all eleven isolated v5.4 feature branches. Each branch README now briefly explains its changed behavior; the release README consolidates all eleven descriptions. The combined feature set and established All Features app identity are retained. Arm64 version code is 111604, allowing an update from .8 with the same signing key.

The regression was reproduced before the fix: two callback tests reset selection from index 1 to 0. Each updated feature branch builds independently and passes all four callback regression tests, covering playing, paused, normal-navigation and blocked states. JVM policy tests run as part of the release gate. Emulator callback tests do not substitute for physical-device playback acceptance.

Use the release assets' SHA256SUMS and source-manifest.json to identify the exact APK and source revisions. This follow-up release is published at the user's request after reporting the .8 regression. The .8 release and its artifacts are retained.
