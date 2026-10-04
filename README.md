## Branch behavior

Stabilizes drag-to-reorder scrolling in the play queue, including continuous drags near its boundaries.

This v5.4 branch also preserves an explicitly selected queue item while ExoPlayer replaces media sources. Internal timeline and source-removal events cannot reset the selection to the previous item; normal playback transitions and seeks still update it.

---

## README

The client of [PipePipe](https://codeberg.org/NullPointerException/PipePipe).
