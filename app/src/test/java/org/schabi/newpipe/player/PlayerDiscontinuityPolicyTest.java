package org.schabi.newpipe.player;

import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_AUTO_TRANSITION;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_REMOVE;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PlayerDiscontinuityPolicyTest {
    @Test
    public void timelineMaintenanceCannotOverrideExplicitQueueSelection() {
        assertFalse(Player.shouldAdoptPlayerIndex(DISCONTINUITY_REASON_INTERNAL));
        assertFalse(Player.shouldAdoptPlayerIndex(DISCONTINUITY_REASON_REMOVE));
    }

    @Test
    public void playbackNavigationStillUpdatesSelection() {
        assertTrue(Player.shouldAdoptPlayerIndex(DISCONTINUITY_REASON_AUTO_TRANSITION));
        assertTrue(Player.shouldAdoptPlayerIndex(DISCONTINUITY_REASON_SEEK));
        assertTrue(Player.shouldAdoptPlayerIndex(DISCONTINUITY_REASON_SEEK_ADJUSTMENT));
    }
}
