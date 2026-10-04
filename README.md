## Branch behavior

Switches the active session between main, background and popup playback while retaining the queue, position and playing or paused state.

This v5.4 branch also preserves an explicitly selected queue item while ExoPlayer replaces media sources. Internal timeline and source-removal events cannot reset the selection to the previous item; normal playback transitions and seeks still update it.

---

## README

The client of [PipePipe](https://codeberg.org/NullPointerException/PipePipe).
