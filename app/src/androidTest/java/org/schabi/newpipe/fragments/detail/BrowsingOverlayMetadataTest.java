package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.view.LayoutInflater;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentHostCallback;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.PlayerService.PlayerType;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/** Exercises the metadata callback and real overlay views without fetching stream data. */
@RunWith(AndroidJUnit4.class)
public class BrowsingOverlayMetadataTest {
    @Test
    public void mainPlayerMetadataAdvancesOverlayWhileDetailsRemainUnrelated() {
        checkMetadata(PlayerType.VIDEO, "B");
    }

    @Test
    public void unrelatedPopupMetadataKeepsExistingOverlayBehavior() {
        checkMetadata(PlayerType.POPUP, "A");
    }

    private static void checkMetadata(final PlayerType type, final String expectedTitle) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            final Context context = new ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.LightTheme);
            final android.content.SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(context);
            final String sponsorKey = context.getString(R.string.sponsor_block_enable_key);
            final boolean hadSponsorSetting = prefs.contains(sponsorKey);
            final boolean sponsorSetting = prefs.getBoolean(sponsorKey, true);
            prefs.edit().putBoolean(sponsorKey, false).commit();
            Player player = null;
            try {
                final ContextService service = new ContextService(context);
                final PlayerServiceInterface serviceInterface = (PlayerServiceInterface)
                        Proxy.newProxyInstance(PlayerServiceInterface.class.getClassLoader(),
                                new Class<?>[]{PlayerServiceInterface.class},
                                (proxy, method, args) -> method.getName().equals("getInstance")
                                        ? service : method.getReturnType() == boolean.class ? false : null);
                player = new Player(serviceInterface);
                setField(Player.class, player, "playerType", type);
                final VideoDetailFragment fragment = VideoDetailFragment.getInstance(
                        0, "X", "Details X", new SinglePlayQueue(item("X")));
                final FragmentHostCallback<Object> host =
                        new FragmentHostCallback<Object>(context, new Handler(), 0) {
                            @Override
                            public Object onGetHost() {
                                return this;
                            }
                        };
                setField(Fragment.class, fragment, "mHost", host);
                final FragmentVideoDetailBinding binding =
                        FragmentVideoDetailBinding.inflate(LayoutInflater.from(context));
                setField(VideoDetailFragment.class, fragment, "binding", binding);
                setField(VideoDetailFragment.class, fragment, "player", player);
                final StreamInfo details = new StreamInfo(0, "X", "X", "Details X");
                setField(VideoDetailFragment.class, fragment, "currentInfo", details);
                binding.overlayTitleTextView.setText("A");
                final PlayQueue active = new SinglePlayQueue(Arrays.asList(item("A"), item("B")), 1);
                final StreamInfo metadata = new StreamInfo(0, "B", "B", "B");
                metadata.setUploaderName("Uploader B");
                fragment.onMetadataUpdate(metadata, active);
                assertEquals(expectedTitle, binding.overlayTitleTextView.getText().toString());
                assertEquals("X", fragment.url);
                assertSame(details, getField(VideoDetailFragment.class, fragment, "currentInfo"));
                if (type == PlayerType.VIDEO) {
                    assertEquals("Uploader B", binding.overlayChannelTextView.getText().toString());
                }
            } catch (final ReflectiveOperationException error) {
                throw new AssertionError(error);
            } finally {
                if (player != null) {
                    try {
                        prefs.unregisterOnSharedPreferenceChangeListener(
                                (android.content.SharedPreferences.OnSharedPreferenceChangeListener)
                                        getField(Player.class, player, "preferenceChangeListener"));
                    } catch (final ReflectiveOperationException error) {
                        throw new AssertionError(error);
                    }
                }
                if (hadSponsorSetting) {
                    prefs.edit().putBoolean(sponsorKey, sponsorSetting).commit();
                } else {
                    prefs.edit().remove(sponsorKey).commit();
                }
            }
        });
    }

    private static StreamInfoItem item(final String url) {
        return new StreamInfoItem(0, url, url, StreamType.VIDEO_STREAM);
    }

    static void setField(final Class<?> type, final Object object,
                                 final String name, final Object value)
            throws ReflectiveOperationException {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    static Object getField(final Class<?> type, final Object object, final String name)
            throws ReflectiveOperationException {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    static final class ContextService extends Service {
        ContextService(final Context context) {
            attachBaseContext(context);
        }

        @Override
        public IBinder onBind(final Intent intent) {
            return null;
        }
    }
}
