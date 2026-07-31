package org.schabi.newpipe.player.helper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.player.helper.MainPlayerQueueBrowsingPolicy.Relation;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Defines the available explicit-play actions when browsing away from an active main-player item.
 */
public final class MainPlayerQueueActionPolicy {
    private MainPlayerQueueActionPolicy() {
    }

    public enum Action {
        PLAY_NOW_KEEP_QUEUE,
        ADD_TO_END,
        REPLACE_QUEUE
    }

    public static boolean shouldShowDialog(@NonNull final Relation relation,
                                           final boolean playerStopped,
                                           final int queueSize) {
        return relation == Relation.OTHER_ITEM && !playerStopped && queueSize > 0;
    }

    @NonNull
    public static Action actionAt(final int index) {
        switch (index) {
            case 0:
                return Action.PLAY_NOW_KEEP_QUEUE;
            case 1:
                return Action.ADD_TO_END;
            case 2:
                return Action.REPLACE_QUEUE;
            default:
                throw new IllegalArgumentException("Unknown queue action index: " + index);
        }
    }

    @Nullable
    public static QueueSnapshot snapshotOf(@Nullable final PlayQueue playQueue) {
        if (playQueue == null || playQueue.isEmpty() || playQueue.getItem() == null) {
            return null;
        }
        return new QueueSnapshot(playQueue);
    }

    public static final class QueueSnapshot {
        @NonNull
        private final PlayQueue playQueue;
        @NonNull
        private final PlayQueueItem activeItem;
        private final int activeIndex;
        @NonNull
        private final List<PlayQueueItem> items;

        private QueueSnapshot(@NonNull final PlayQueue playQueue) {
            this.playQueue = playQueue;
            this.activeItem = Objects.requireNonNull(playQueue.getItem());
            this.activeIndex = playQueue.getIndex();
            this.items = new ArrayList<>(playQueue.getStreams());
        }

        public boolean matches(@Nullable final PlayQueue currentQueue) {
            if (currentQueue != playQueue
                    || currentQueue.getIndex() != activeIndex
                    || currentQueue.getItem() != activeItem
                    || currentQueue.size() != items.size()) {
                return false;
            }

            for (int index = 0; index < items.size(); index++) {
                if (currentQueue.getItem(index) != items.get(index)) {
                    return false;
                }
            }
            return true;
        }
    }
}
