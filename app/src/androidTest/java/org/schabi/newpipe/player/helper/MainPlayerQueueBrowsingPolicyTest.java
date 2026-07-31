package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.player.helper.MainPlayerQueueBrowsingPolicy.Relation.ACTIVE_ITEM;
import static org.schabi.newpipe.player.helper.MainPlayerQueueBrowsingPolicy.Relation.NO_ACTIVE_MAIN_QUEUE;
import static org.schabi.newpipe.player.helper.MainPlayerQueueBrowsingPolicy.Relation.OTHER_ITEM;

import org.junit.Test;
import org.schabi.newpipe.player.PlayerService.PlayerType;

public final class MainPlayerQueueBrowsingPolicyTest {
    @Test
    public void sameMainQueueItemIsActiveItem() {
        assertEquals(ACTIVE_ITEM, MainPlayerQueueBrowsingPolicy.classify(
                PlayerType.VIDEO, false, 0, "https://example.com/active",
                0, "https://example.com/active"));
    }

    @Test
    public void differentUrlOrServiceIsAnotherItem() {
        assertEquals(OTHER_ITEM, MainPlayerQueueBrowsingPolicy.classify(
                PlayerType.VIDEO, false, 0, "https://example.com/active",
                0, "https://example.com/other"));
        assertEquals(OTHER_ITEM, MainPlayerQueueBrowsingPolicy.classify(
                PlayerType.VIDEO, false, 0, "https://example.com/active",
                1, "https://example.com/active"));
    }

    @Test
    public void switchingPlayersDoesNotBecomeBrowsing() {
        assertEquals(NO_ACTIVE_MAIN_QUEUE, MainPlayerQueueBrowsingPolicy.classify(
                PlayerType.VIDEO, true, 0, "https://example.com/active",
                0, "https://example.com/other"));
    }

    @Test
    public void passiveNavigationPreservesButExplicitPlaybackReplaces() {
        assertTrue(MainPlayerQueueBrowsingPolicy.shouldPreserveQueueForBrowsing(
                OTHER_ITEM, false));
        assertFalse(MainPlayerQueueBrowsingPolicy.shouldPreserveQueueForBrowsing(
                OTHER_ITEM, true));
        assertFalse(MainPlayerQueueBrowsingPolicy.shouldPreserveQueueForBrowsing(
                NO_ACTIVE_MAIN_QUEUE, false));
    }

    @Test
    public void onlyBrowsingAnotherMainQueueItemContinuesAudioOnly() {
        assertTrue(MainPlayerQueueBrowsingPolicy.shouldContinueAudioOnlyForBrowsing(
                OTHER_ITEM));
        assertFalse(MainPlayerQueueBrowsingPolicy.shouldContinueAudioOnlyForBrowsing(
                ACTIVE_ITEM));
        assertFalse(MainPlayerQueueBrowsingPolicy.shouldContinueAudioOnlyForBrowsing(
                NO_ACTIVE_MAIN_QUEUE));
    }

    @Test
    public void popupAndBackgroundPlayersAreUnchanged() {
        assertEquals(NO_ACTIVE_MAIN_QUEUE, MainPlayerQueueBrowsingPolicy.classify(
                PlayerType.POPUP, false, 0, "https://example.com/active",
                0, "https://example.com/other"));
        assertEquals(NO_ACTIVE_MAIN_QUEUE, MainPlayerQueueBrowsingPolicy.classify(
                PlayerType.AUDIO, false, 0, "https://example.com/active",
                0, "https://example.com/other"));
    }

    @Test
    public void incompleteQueueOrTargetIsNotPreserved() {
        assertEquals(NO_ACTIVE_MAIN_QUEUE, MainPlayerQueueBrowsingPolicy.classify(
                PlayerType.VIDEO, false, null, null,
                0, "https://example.com/other"));
        assertEquals(NO_ACTIVE_MAIN_QUEUE, MainPlayerQueueBrowsingPolicy.classify(
                PlayerType.VIDEO, false, 0, "https://example.com/active",
                0, null));
    }
}
