package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;

import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class PendingQueueReplacementTest {
    @Test
    public void pendingPlaylistSurvivesFragmentStateRestoration() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final PlayQueue replacement = queue("A", "C", "D");
            final VideoDetailFragment original = fragment(replacement);
            final Bundle state = new Bundle();
            original.onSaveInstanceState(state);
            final VideoDetailFragment restored = fragment(null);
            restored.onRestoreInstanceState(state);
            restored.onQueueUpdate(queue("A", "B"));
            assertEquals(replacement, queueForIntent(restored));
        });
    }

    @Test
    public void serviceQueueUpdateCannotReplaceExplicitPendingPlaylist() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final PlayQueue active = queue("A", "B");
            final PlayQueue replacement = queue("A", "C", "D");
            final VideoDetailFragment fragment = fragment(replacement);
            fragment.onQueueUpdate(active);
            assertSame(replacement, model(fragment).getPlayQueue());
            assertSame(replacement, queueForIntent(fragment));
        });
    }

    @Test
    public void passiveDetailsAcceptExistingServiceQueue() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final PlayQueue active = queue("A", "B");
            final VideoDetailFragment fragment = fragment(null);
            fragment.onQueueUpdate(active);
            assertSame(active, queueForIntent(fragment));
        });
    }

    private static VideoDetailFragment fragment(final PlayQueue queue) {
        final VideoDetailFragment fragment = VideoDetailFragment.getInstance(0, "A", "A", queue);
        final VideoDetailViewModel model = new VideoDetailViewModel();
        model.setInitialData(0, "A", "A", queue);
        try {
            final Field field = VideoDetailFragment.class.getDeclaredField("vm");
            field.setAccessible(true);
            field.set(fragment, model);
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
        return fragment;
    }

    private static VideoDetailViewModel model(final VideoDetailFragment fragment) {
        try {
            final Field field = VideoDetailFragment.class.getDeclaredField("vm");
            field.setAccessible(true);
            return (VideoDetailViewModel) field.get(fragment);
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static PlayQueue queue(final String... urls) {
        return new SinglePlayQueue(Arrays.stream(urls)
                .map(url -> new StreamInfoItem(0, url, url, StreamType.VIDEO_STREAM))
                .collect(java.util.stream.Collectors.toList()), 0);
    }

    private static PlayQueue queueForIntent(final VideoDetailFragment fragment) {
        try {
            final Method method = VideoDetailFragment.class.getDeclaredMethod(
                    "playQueueForIntent", boolean.class);
            method.setAccessible(true);
            return (PlayQueue) method.invoke(fragment, false);
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }
}
