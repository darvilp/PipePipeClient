package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;

import android.app.Service;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.ContextThemeWrapper;

import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.util.ThemeHelper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.PlayerService.PlayerType;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.SerializedCache;

import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class HeaderEnqueueNavigationTest {
    @Test
    public void upstreamEnqueueEntryPointsPreserveEveryActivePlayerMode() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            for (final PlayerType activeType : PlayerType.values()) {
                checkEnqueue(context, activeType, false);
                checkEnqueue(context, activeType, true);
            }
        });
    }

    private static void checkEnqueue(final Context context, final PlayerType activeType,
                                     final boolean next) {
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
        final Player player = new Player(serviceInterface);
        prepareView(player, context);
        final PlayQueue queue = new SinglePlayQueue(
                Arrays.asList(item("first"), item("second"), item("third")), 0);
        queue.init();
        try {
            player.setPlayQueue(queue);
            player.setPlayerType(activeType);
            player.setAudioOnly(activeType == PlayerType.AUDIO);
            final CapturingContext capture = new CapturingContext(context);
            final PlayerHolder holder = PlayerHolder.getInstance();
            final Player previousPlayer = setHolderPlayer(holder, player);
            final Intent intent;
            try {
                if (next) {
                    NavigationHelper.enqueueNextOnPlayer(capture,
                            new SinglePlayQueue(item("queued")));
                } else {
                    NavigationHelper.enqueueOnPlayer(capture,
                            new SinglePlayQueue(item("queued")));
                }
                intent = capture.intent;
                assertNotNull(intent);
                assertEquals(activeType.ordinal(), intent.getIntExtra(
                        PlayerIntentConstants.PLAYER_TYPE, -1));
            } finally {
                setHolderPlayer(holder, previousPlayer);
            }

            new PlayerStartController(player).handleIntent(intent);

            assertSame(queue, player.getPlayQueue());
            assertEquals(activeType, player.getPlayerType());
            assertEquals(activeType == PlayerType.AUDIO, player.isAudioOnly());
            assertEquals(0, queue.getIndex());
            assertEquals(4, queue.size());
            assertEquals("https://example.invalid/queued",
                    queue.getItem(next ? 1 : 3).getUrl());
        } finally {
            try {
                player.destroy();
            } finally {
                queue.dispose();
                player.getTrackSelector().release();
            }
        }
    }

    @Test
    public void missingSessionRequestsBackgroundPlayback() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            final PlayerHolder holder = PlayerHolder.getInstance();
            final Player previousPlayer = setHolderPlayer(holder, null);
            try {
                for (final boolean next : new boolean[]{false, true}) {
                    final CapturingContext capture = new CapturingContext(context);
                    if (next) {
                        NavigationHelper.enqueueNextOnPlayer(capture,
                                new SinglePlayQueue(item("queued")));
                    } else {
                        NavigationHelper.enqueueOnPlayer(capture,
                                new SinglePlayQueue(item("queued")));
                    }
                    assertNotNull(capture.intent);
                    assertEquals(PlayerType.AUDIO.ordinal(), capture.intent.getIntExtra(
                            PlayerIntentConstants.PLAYER_TYPE, -1));
                    final String cacheKey = capture.intent.getStringExtra(
                            PlayerIntentConstants.PLAY_QUEUE_KEY);
                    final PlayQueue queued = SerializedCache.getInstance().take(
                            cacheKey, PlayQueue.class);
                    assertNotNull(queued);
                    queued.dispose();
                }
            } finally {
                setHolderPlayer(holder, previousPlayer);
            }
        });
    }

    private static void prepareView(final Player player, final Context context) {
        try {
            final Field binding = Player.class.getDeclaredField("binding");
            binding.setAccessible(true);
            binding.set(player, PlayerBinding.inflate(LayoutInflater.from(
                    new ContextThemeWrapper(context, ThemeHelper.getThemeForService(context, -1)))));
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Player setHolderPlayer(final PlayerHolder holder, final Player player) {
        try {
            final Field field = PlayerHolder.class.getDeclaredField("player");
            field.setAccessible(true);
            final Player previous = (Player) field.get(holder);
            field.set(holder, player);
            return previous;
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static final class CapturingContext extends ContextWrapper {
        private Intent intent;

        CapturingContext(final Context base) {
            super(base);
        }

        @Override
        public ComponentName startForegroundService(final Intent service) {
            intent = service;
            return service.getComponent();
        }
    }

    private static StreamInfoItem item(final String name) {
        final StreamInfoItem item = new StreamInfoItem(0,
                "https://example.invalid/" + name, name, StreamType.VIDEO_STREAM);
        item.setUploaderName("Uploader");
        return item;
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
