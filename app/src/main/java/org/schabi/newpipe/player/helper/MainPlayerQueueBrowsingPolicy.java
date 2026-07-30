package org.schabi.newpipe.player.helper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.player.PlayerService.PlayerType;

/**
 * Classifies a detail-page target relative to an active main-player queue.
 */
public final class MainPlayerQueueBrowsingPolicy {
    private MainPlayerQueueBrowsingPolicy() {
    }

    public enum Relation {
        NO_ACTIVE_MAIN_QUEUE,
        ACTIVE_ITEM,
        OTHER_ITEM
    }

    public static boolean shouldPreserveQueueForBrowsing(@NonNull final Relation relation,
                                                         final boolean playbackRequested) {
        return relation != Relation.NO_ACTIVE_MAIN_QUEUE && !playbackRequested;
    }

    @NonNull
    public static Relation classify(@Nullable final PlayerType playerType,
                                    final boolean switchingPlayers,
                                    @Nullable final Integer activeServiceId,
                                    @Nullable final String activeUrl,
                                    final int targetServiceId,
                                    @Nullable final String targetUrl) {
        if (switchingPlayers
                || playerType != PlayerType.VIDEO
                || activeServiceId == null
                || activeUrl == null
                || targetUrl == null) {
            return Relation.NO_ACTIVE_MAIN_QUEUE;
        }

        if (activeServiceId == targetServiceId && activeUrl.equals(targetUrl)) {
            return Relation.ACTIVE_ITEM;
        }

        return Relation.OTHER_ITEM;
    }
}
