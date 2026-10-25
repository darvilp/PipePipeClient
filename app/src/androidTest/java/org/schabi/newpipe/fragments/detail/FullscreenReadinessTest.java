package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.view.LayoutInflater;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.exoplayer2.ExoPlayer;
import org.schabi.newpipe.player.PlayerPlaybackParameters;
import org.schabi.newpipe.player.PlayerPlaybackState;
import org.schabi.newpipe.player.RepeatMode;
import org.schabi.newpipe.player.SponsorBlockController;
import org.schabi.newpipe.player.PlayerControlsVisibilityController;
import androidx.test.core.app.ActivityScenario;
import org.schabi.newpipe.MainActivity;
import android.widget.FrameLayout;
import java.lang.reflect.Field;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Real Player readiness guard and listener callbacks, without media/network playback. */
@RunWith(AndroidJUnit4.class)
public class FullscreenReadinessTest {
    @Test
    public void coldRequestRetriesAfterListenerAttachmentAndIsConsumedOnce() {
        runCase(false, true, false);
    }

    @Test
    public void warmRequestEntersFullscreenImmediately() {
        runCase(true, true, false);
    }

    @Test
    public void disabledPreferenceDoesNotEnterFullscreen() {
        runCase(false, false, false);
    }

    @Test
    public void newerInlineRequestCancelsPendingFullscreen() {
        runCase(false, true, true);
    }

    private static void runCase(final boolean warm, final boolean requested,
                                final boolean cancel) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
        scenario.onActivity(activity -> {
            final Context context = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.LightTheme);
            final ContextService service = new ContextService(context);
            final PlayerServiceInterface serviceInterface = (PlayerServiceInterface)
                    Proxy.newProxyInstance(PlayerServiceInterface.class.getClassLoader(),
                            new Class<?>[]{PlayerServiceInterface.class},
                            (proxy, method, args) -> method.getName().equals("getInstance")
                                    ? service : method.getReturnType() == boolean.class ? false : null);
            final Player player = new Player(serviceInterface);
            final ExoPlayer exo = new ExoPlayer.Builder(context).build();
            try {
                setField(Player.class, player, "simpleExoPlayer", exo);
                setField(Player.class, player, "binding",
                        PlayerBinding.inflate(LayoutInflater.from(context)));
                setField(Player.class, player, "playQueue", new SinglePlayQueue(
                        new StreamInfoItem(0, "A", "A", StreamType.VIDEO_STREAM)));
                final FrameLayout parent = new FrameLayout(activity);
                parent.addView(player.getRootView());
                setField(Player.class, player, "isPrepared", true);
                final VideoDetailFragment fragment = VideoDetailFragment.getInstance(
                        0, "A", "A", null);
                setField(VideoDetailFragment.class, fragment, "binding",
                        FragmentVideoDetailBinding.inflate(LayoutInflater.from(context)));
                final PlayerServiceEventListener listener = (PlayerServiceEventListener)
                        Proxy.newProxyInstance(PlayerServiceEventListener.class.getClassLoader(),
                                new Class<?>[]{PlayerServiceEventListener.class}, (proxy, method, args) -> {
                                    if (method.getName().equals("onPlaybackUpdate")) {
                                        fragment.onPlaybackUpdate((PlayerPlaybackState) args[0], (RepeatMode) args[1],
                                                (boolean) args[2], (PlayerPlaybackParameters) args[3]);
                                    }
                                    return null;
                                });
                if (warm) {
                    setField(VideoDetailFragment.class, fragment, "player", player);
                    player.setFragmentListener(listener);
                }
                prepare(fragment, requested);
                if (!warm) {
                    assertFalse(player.isFullscreen());
                    setField(VideoDetailFragment.class, fragment, "player", player);
                    // The connection callback precedes the real Player listener attachment.
                    prepare(fragment, cancel ? false : requested);
                    assertFalse(player.isFullscreen());
                    player.setFragmentListener(listener);
                }
                if (requested && !cancel) {
                    assertTrue(player.isFullscreen());
                    player.changeFullscreen(false);
                    fragment.onPlaybackUpdate(PlayerPlaybackState.PREFLIGHT, RepeatMode.OFF, false,
                            new PlayerPlaybackParameters(1f, 1f));
                    assertFalse("Consumed requests must not re-enter fullscreen", player.isFullscreen());
                } else {
                    assertFalse(player.isFullscreen());
                }
            } catch (final ReflectiveOperationException error) {
                throw new AssertionError(error);
            } finally {
                try {
                    ((Handler) getField(PlayerControlsVisibilityController.class,
                            getField(Player.class, player, "controlsVisibilityController"),
                            "controlsVisibilityHandler"))
                            .removeCallbacksAndMessages(null);
                    ((SponsorBlockController) getField(Player.class, player,
                            "sponsorBlockController")).destroy();
                } catch (final ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
                exo.release();
            }
        });
        }
    }

    private static void prepare(final VideoDetailFragment fragment, final boolean fullscreen)
            throws ReflectiveOperationException {
        final Method method = VideoDetailFragment.class.getDeclaredMethod(
                "prepareMainPlayerUi", boolean.class);
        method.setAccessible(true);
        method.invoke(fragment, fullscreen);
    }

    private static void setField(final Class<?> type, final Object object,
                                 final String name, final Object value)
            throws ReflectiveOperationException {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static Object getField(final Class<?> type, final Object object, final String name)
            throws ReflectiveOperationException {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static final class ContextService extends Service {
        ContextService(final Context context) { attachBaseContext(context); }
        @Override public IBinder onBind(final Intent intent) { return null; }
    }
}
