package org.schabi.newpipe.player.playqueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PlayQueueInsertNextTest {
    @Test
    public void insertsAfterCurrentAndSelectsNewItem() {
        final PlayQueue queue = queueAt(1, item("A"), item("B"), item("C"));
        final PlayQueueItem previousItem = queue.getItem();
        final PlayQueueItem insertedItem = queueItem("X");

        queue.insertNextAndSelect(insertedItem);

        assertEquals(Arrays.asList("A", "B", "X", "C"), titlesOf(queue));
        assertEquals(2, queue.getIndex());
        assertSame(insertedItem, queue.getItem());
        assertTrue(queue.previous());
        assertSame(previousItem, queue.getItem());
    }

    @Test
    public void insertsAfterFirstAndLastItems() {
        final PlayQueue afterFirst = queueAt(0, item("A"), item("B"));
        afterFirst.insertNextAndSelect(queueItem("X"));
        assertEquals(Arrays.asList("A", "X", "B"), titlesOf(afterFirst));
        assertEquals(1, afterFirst.getIndex());

        final PlayQueue afterLast = queueAt(1, item("A"), item("B"));
        afterLast.insertNextAndSelect(queueItem("X"));
        assertEquals(Arrays.asList("A", "B", "X"), titlesOf(afterLast));
        assertEquals(2, afterLast.getIndex());
    }

    @Test
    public void preservesDuplicateItemsInsteadOfMovingThem() {
        final PlayQueue queue = queueAt(0, item("A"), item("X"), item("B"));
        final PlayQueueItem duplicate = queueItem("X");

        queue.insertNextAndSelect(duplicate);

        assertEquals(Arrays.asList("A", "X", "X", "B"), titlesOf(queue));
        assertSame(duplicate, queue.getItem());
    }

    @Test
    public void insertsNextInShuffledOrderAndAppendsInOriginalOrder() {
        final PlayQueue queue = queueAt(1, item("A"), item("B"), item("C"));
        final PlayQueueItem previousItem = queue.getItem();
        final PlayQueueItem insertedItem = queueItem("X");
        queue.shuffle();

        queue.insertNextAndSelect(insertedItem);

        assertTrue(queue.isShuffled());
        assertSame(insertedItem, queue.getItem(1));
        assertSame(insertedItem, queue.getItem());
        assertTrue(queue.previous());
        assertSame(previousItem, queue.getItem());

        queue.unshuffle();
        assertFalse(queue.isShuffled());
        assertEquals(Arrays.asList("A", "B", "C", "X"), titlesOf(queue));
        assertSame(previousItem, queue.getItem());
    }

    @Test
    public void replacesUnplayedAutoQueueTailButPreservesActiveAutoQueuedItem() {
        final PlayQueue unplayedTail = queueAt(0, item("A"), item("Auto"));
        unplayedTail.getItem(1).setAutoQueued(true);
        unplayedTail.insertNextAndSelect(queueItem("X"));
        assertEquals(Arrays.asList("A", "X"), titlesOf(unplayedTail));

        final PlayQueue activeTail = queueAt(1, item("A"), item("Auto"));
        final PlayQueueItem activeAutoQueuedItem = activeTail.getItem();
        activeAutoQueuedItem.setAutoQueued(true);
        activeTail.insertNextAndSelect(queueItem("X"));
        assertEquals(Arrays.asList("A", "Auto", "X"), titlesOf(activeTail));
        assertTrue(activeTail.previous());
        assertSame(activeAutoQueuedItem, activeTail.getItem());
        assertTrue(activeAutoQueuedItem.isAutoQueued());
    }

    private static PlayQueue queueAt(final int index, final StreamInfoItem... items) {
        return new SinglePlayQueue(Arrays.asList(items), index);
    }

    private static StreamInfoItem item(final String title) {
        return new StreamInfoItem(0, "https://example.com/" + title, title,
                StreamType.VIDEO_STREAM);
    }

    private static PlayQueueItem queueItem(final String title) {
        return new SinglePlayQueue(item(title)).getItem();
    }

    private static List<String> titlesOf(final PlayQueue queue) {
        final List<String> titles = new ArrayList<>();
        for (final PlayQueueItem item : queue.getStreams()) {
            titles.add(item.getTitle());
        }
        return titles;
    }
}
