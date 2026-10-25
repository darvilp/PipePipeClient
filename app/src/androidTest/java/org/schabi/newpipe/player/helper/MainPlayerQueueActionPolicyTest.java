package org.schabi.newpipe.player.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.schabi.newpipe.player.helper.MainPlayerQueueActionPolicy.Action.ADD_TO_END;
import static org.schabi.newpipe.player.helper.MainPlayerQueueActionPolicy.Action.PLAY_NOW_KEEP_QUEUE;
import static org.schabi.newpipe.player.helper.MainPlayerQueueActionPolicy.Action.REPLACE_QUEUE;
import static org.schabi.newpipe.player.helper.MainPlayerQueueBrowsingPolicy.Relation.ACTIVE_ITEM;
import static org.schabi.newpipe.player.helper.MainPlayerQueueBrowsingPolicy.Relation.NO_ACTIVE_MAIN_QUEUE;
import static org.schabi.newpipe.player.helper.MainPlayerQueueBrowsingPolicy.Relation.OTHER_ITEM;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.util.Arrays;

public final class MainPlayerQueueActionPolicyTest {
    @Test
    public void promptsOnlyForAnotherItemInAnActiveNonEmptyMainQueue() {
        assertTrue(MainPlayerQueueActionPolicy.shouldShowDialog(OTHER_ITEM, false, 1));
        assertFalse(MainPlayerQueueActionPolicy.shouldShowDialog(ACTIVE_ITEM, false, 1));
        assertFalse(MainPlayerQueueActionPolicy.shouldShowDialog(NO_ACTIVE_MAIN_QUEUE, false, 1));
        assertFalse(MainPlayerQueueActionPolicy.shouldShowDialog(OTHER_ITEM, true, 1));
        assertFalse(MainPlayerQueueActionPolicy.shouldShowDialog(OTHER_ITEM, false, 0));
    }

    @Test
    public void dialogActionOrderIsStable() {
        assertEquals(PLAY_NOW_KEEP_QUEUE, MainPlayerQueueActionPolicy.actionAt(0));
        assertEquals(ADD_TO_END, MainPlayerQueueActionPolicy.actionAt(1));
        assertEquals(REPLACE_QUEUE, MainPlayerQueueActionPolicy.actionAt(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownActionIndex() {
        MainPlayerQueueActionPolicy.actionAt(3);
    }

    @Test
    public void snapshotMatchesOnlyTheUnchangedQueueAndActiveItem() {
        final PlayQueue queue = queueAt(0, item("A"), item("B"));
        final MainPlayerQueueActionPolicy.QueueSnapshot snapshot =
                MainPlayerQueueActionPolicy.snapshotOf(queue);

        assertTrue(snapshot.matches(queue));

        queue.append(new SinglePlayQueue(item("C")).getItem());
        assertFalse(snapshot.matches(queue));
    }

    @Test
    public void snapshotRejectsReorderingWithoutASelectionChange() {
        final PlayQueue queue = queueAt(0, item("A"), item("B"), item("C"));
        final MainPlayerQueueActionPolicy.QueueSnapshot snapshot =
                MainPlayerQueueActionPolicy.snapshotOf(queue);

        queue.move(1, 2);

        assertFalse(snapshot.matches(queue));
    }

    @Test
    public void snapshotRejectsSelectionAndQueueIdentityChanges() {
        final PlayQueue queue = queueAt(0, item("A"), item("B"));
        final MainPlayerQueueActionPolicy.QueueSnapshot snapshot =
                MainPlayerQueueActionPolicy.snapshotOf(queue);

        queue.setIndex(1);
        assertFalse(snapshot.matches(queue));
        assertFalse(snapshot.matches(queueAt(0, item("A"), item("B"))));
    }

    private static PlayQueue queueAt(final int index, final StreamInfoItem... items) {
        return new SinglePlayQueue(Arrays.asList(items), index);
    }

    private static StreamInfoItem item(final String title) {
        return new StreamInfoItem(0, "https://example.com/" + title, title,
                StreamType.VIDEO_STREAM);
    }
}
