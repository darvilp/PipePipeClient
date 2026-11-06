package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player.PositionInfo;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/** Exercises the real callback while ExoPlayer still reports the previous queue index. */
@RunWith(AndroidJUnit4.class)
public class PlayerDiscontinuityRegressionTest {
    private Player player;
    private SinglePlayQueue queue;

    @Before
    public void setUp() {
        onMain(() -> {
            final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            final Service service = new LocalService(context);
            final PlayerServiceInterface serviceInterface = (PlayerServiceInterface)
                    Proxy.newProxyInstance(PlayerServiceInterface.class.getClassLoader(),
                            new Class<?>[]{PlayerServiceInterface.class}, (proxy, method, args) -> {
                                if ("getInstance".equals(method.getName())) {
                                    return service;
                                }
                                if ("isLandscape".equals(method.getName())) {
                                    return false;
                                }
                                throw new AssertionError("Unexpected service call: " + method);
                            });
            player = new Player(serviceInterface);
            player.simpleExoPlayer = new ExoPlayer.Builder(context).build();
            setField("exoPlayerEventAdapter", new ExoPlayerEventAdapter(player));
            queue = new SinglePlayQueue(Arrays.asList(item("first"), item("picked"),
                    item("last")), 0);
            queue.init();
            setField("playQueue", queue);
        });
    }

    @After
    public void tearDown() {
        onMain(() -> {
            if (player != null) {
                player.destroy();
                ((DefaultTrackSelector) getField("trackSelector")).release();
            }
        });
    }

    @Test
    public void internalTimelineChangeCannotUndoPlayingSelection() {
        assertSelectionSurvives(com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL,
                PlayerPlaybackState.PLAYING);
    }

    @Test
    public void sourceRemovalCannotUndoPausedSelection() {
        assertSelectionSurvives(com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_REMOVE,
                PlayerPlaybackState.PAUSED);
    }

    @Test
    public void navigationStillAdvancesTheSelectedItem() {
        onMain(() -> {
            setField("currentState", PlayerPlaybackState.PLAYING);
            for (final int reason : new int[]{
                    com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
                    com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK,
                    com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT}) {
                queue.setIndex(0);
                player.onPositionDiscontinuity(position(0), position(1), reason);
                assertEquals("Navigation must update selection for reason " + reason,
                        1, queue.getIndex());
            }
        });
    }

    @Test
    public void blockedPlaybackDoesNotAdoptAnUnreadyIndex() {
        onMain(() -> {
            queue.setIndex(1);
            setField("currentState", PlayerPlaybackState.BLOCKED);
            player.onPositionDiscontinuity(position(0), position(0),
                    com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK);
            assertEquals(1, queue.getIndex());
        });
    }

    private void assertSelectionSurvives(final int reason, final PlayerPlaybackState state) {
        onMain(() -> {
            // Explicit play has selected the inserted item; source replacement is still pending.
            queue.setIndex(1);
            final Object selected = queue.getItem();
            setField("currentState", state);
            player.onPositionDiscontinuity(position(0), position(0), reason);
            assertEquals("Timeline maintenance must not reset the explicit selection", 1,
                    queue.getIndex());
            assertSame(selected, queue.getItem());
        });
    }

    private static StreamInfoItem item(final String name) {
        final StreamInfoItem item = new StreamInfoItem(0, "https://example.invalid/" + name,
                name, StreamType.AUDIO_STREAM);
        item.setUploaderName("Test uploader");
        return item;
    }

    private static PositionInfo position(final int index) {
        return new PositionInfo(null, index, null, null, index, 0, 0, -1, -1);
    }

    private Object getField(final String name) {
        try {
            final Field field = Player.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(player);
        } catch (final ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private void setField(final String name, final Object value) {
        try {
            final Field field = Player.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(player, value);
        } catch (final ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void onMain(final Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }

    private static final class LocalService extends Service {
        LocalService(final Context context) {
            attachBaseContext(context);
        }

        @Override
        public IBinder onBind(final Intent intent) {
            return null;
        }
    }
}
