package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.source.SilenceMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.mediaitem.ExceptionTag;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Offline integration checks: real ExoPlayer audio, synthetic queue, no service or extractor. */
@RunWith(AndroidJUnit4.class)
public class SponsorBlockQueueGuardTest {
    private static final String VIDEO_URL = "https://example.invalid/first";
    private Player player;
    private ExoPlayer exoPlayer;
    private SinglePlayQueue queue;
    private SharedPreferences preferences;
    private String modeKey;
    private String previousMode;

    @Before
    public void setUp() {
        onMain(() -> {
            final Context context = InstrumentationRegistry.getInstrumentation()
                    .getTargetContext();
            preferences = PreferenceManager.getDefaultSharedPreferences(context);
            modeKey = context.getString(R.string.pref_sponsorblock_mode_key);
            previousMode = preferences.getString(modeKey, null);
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
            exoPlayer = new ExoPlayer.Builder(context).build();
            exoPlayer.setVolume(0);
            player.simpleExoPlayer = exoPlayer;
            queue = new SinglePlayQueue(Arrays.asList(
                    new StreamInfoItem(0, VIDEO_URL, "First", StreamType.AUDIO_STREAM),
                    new StreamInfoItem(0, "https://example.invalid/second", "Second",
                            StreamType.AUDIO_STREAM)), 0);
            queue.init();
            // Keep UI and network resolution outside this fixture. Production guard and manual
            // queue navigation still execute on a real Player and a real ExoPlayer.
            setField("playQueue", queue);
            setField("currentItem", queue.getItem());
            setField("currentMetadata", ExceptionTag.of(queue.getItem(), Collections.emptyList()));
        });
    }

    @After
    public void tearDown() {
        onMain(() -> {
            if (player != null) {
                preferences.unregisterOnSharedPreferenceChangeListener(
                        (SharedPreferences.OnSharedPreferenceChangeListener)
                                getField("preferenceChangeListener"));
                player.destroy();
                ((DefaultTrackSelector) getField("trackSelector")).release();
            } else if (exoPlayer != null) {
                exoPlayer.release();
            }
            if (preferences != null) {
                if (previousMode == null) {
                    preferences.edit().remove(modeKey).commit();
                } else {
                    preferences.edit().putString(modeKey, previousMode).commit();
                }
            }
        });
    }

    @Test
    public void draftPausesAtFirstItemAndExplicitNextReleasesGuard() throws Exception {
        final CountDownLatch pausedAtEnd = new CountDownLatch(1);
        final AtomicReference<PlaybackException> error = new AtomicReference<>();
        onMain(() -> {
            exoPlayer.addListener(new com.google.android.exoplayer2.Player.Listener() {
                @Override
                public void onPlayWhenReadyChanged(final boolean playWhenReady, final int reason) {
                    if (!playWhenReady && reason == com.google.android.exoplayer2.Player
                            .PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                        pausedAtEnd.countDown();
                    }
                }

                @Override
                public void onPlayerError(final PlaybackException playbackError) {
                    error.set(playbackError);
                    pausedAtEnd.countDown();
                }
            });
            exoPlayer.setMediaSources(Arrays.asList(
                    new SilenceMediaSource.Factory().setDurationUs(400_000).createMediaSource(),
                    new SilenceMediaSource.Factory().setDurationUs(400_000).createMediaSource()));
            player.setSponsorBlockEditing(this, VIDEO_URL, true);
            exoPlayer.prepare();
            exoPlayer.play();
        });
        assertTrue("Player did not pause at the end of the first item",
                pausedAtEnd.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Offline silence playback failed", error.get());
        }
        onMain(() -> {
            assertEquals("Automatic playback must stay on the edited item", 0,
                    exoPlayer.getCurrentMediaItemIndex());
            assertFalse(exoPlayer.getPlayWhenReady());
            assertTrue(exoPlayer.getCurrentPosition() >= 350);
            assertEquals(0, queue.getIndex());
            player.setSponsorBlockEditing(this, VIDEO_URL, false);
            assertFalse("Releasing an ended hold must not resume playback",
                    exoPlayer.getPlayWhenReady());
            player.setSponsorBlockEditing(this, VIDEO_URL, true);
            player.playNext();
            assertEquals("Explicit next must still select the successor", 1, queue.getIndex());
            assertFalse(exoPlayer.getPauseAtEndOfMediaItems());
        });
    }

    @Test
    public void noDraftAllowsAutomaticAdvancement() throws Exception {
        final CountDownLatch advanced = new CountDownLatch(1);
        onMain(() -> {
            exoPlayer.addListener(new com.google.android.exoplayer2.Player.Listener() {
                @Override
                public void onMediaItemTransition(final com.google.android.exoplayer2.MediaItem item,
                                                  final int reason) {
                    if (exoPlayer.getCurrentMediaItemIndex() == 1) {
                        advanced.countDown();
                    }
                }
            });
            exoPlayer.setMediaSources(Arrays.asList(
                    new SilenceMediaSource.Factory().setDurationUs(400_000).createMediaSource(),
                    new SilenceMediaSource.Factory().setDurationUs(400_000).createMediaSource()));
            exoPlayer.prepare();
            exoPlayer.play();
        });
        assertTrue("Playback without a draft must reach the successor",
                advanced.await(15, TimeUnit.SECONDS));
    }

    @Test
    public void oldOwnerCannotReleaseNewOwnersGuard() {
        onMain(() -> {
            final Object oldOwner = new Object();
            final Object newOwner = new Object();
            player.setSponsorBlockEditing(oldOwner, VIDEO_URL, true);
            player.setSponsorBlockEditing(newOwner, VIDEO_URL, true);
            player.setSponsorBlockEditing(oldOwner, VIDEO_URL, false);
            assertTrue(exoPlayer.getPauseAtEndOfMediaItems());
            player.setSponsorBlockEditing(newOwner, VIDEO_URL, false);
            assertFalse(exoPlayer.getPauseAtEndOfMediaItems());
        });
    }

    @Test
    public void unrelatedVideoCannotAcquireGuard() {
        onMain(() -> {
            player.setSponsorBlockEditing(this, "https://example.invalid/other", true);
            assertFalse(exoPlayer.getPauseAtEndOfMediaItems());
            player.setSponsorBlockEditing(this, VIDEO_URL, true);
            assertTrue(exoPlayer.getPauseAtEndOfMediaItems());
            player.setSponsorBlockEditing(this, VIDEO_URL, false);
            assertFalse(exoPlayer.getPauseAtEndOfMediaItems());
        });
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

    private Object getField(final String name) {
        try {
            final Field field = Player.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(player);
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
