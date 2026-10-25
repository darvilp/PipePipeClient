package org.schabi.newpipe.player;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.fragments.detail.TabAdapter;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.fragments.list.sponsorblock.SponsorBlockFragment;
import org.schabi.newpipe.fragments.list.sponsorblock.SponsorBlockFragmentListener;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.player.mediasession.PlayerServiceInterface;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

/** Real detail sheet and editor lifecycle; synthetic metadata and a real ExoPlayer queue guard. */
@RunWith(AndroidJUnit4.class)
public class SponsorBlockSheetVisibilityTest {
    @Test
    public void collapsedHostReleasesHoldAndProgressCannotReacquireIt() {
        checkHiddenHost(false);
    }

    @Test
    public void fullscreenHostReleasesHoldAndProgressCannotReacquireIt() {
        checkHiddenHost(true);
    }

    private void checkHiddenHost(final boolean fullscreen) {
        final AtomicReference<Player> playing = new AtomicReference<>();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            final AtomicReference<VideoDetailFragment> host = new AtomicReference<>();
            final AtomicReference<FragmentVideoDetailBinding> hostViews = new AtomicReference<>();
            final AtomicReference<SponsorBlockFragment> editor = new AtomicReference<>();
            final AtomicReference<BottomSheetBehavior<?>> sheet = new AtomicReference<>();
            scenario.onActivity(activity -> {
                final VideoDetailFragment parent = VideoDetailFragment.getInstanceInCollapsedState();
                set(VideoDetailFragment.class, parent, "bottomSheetState", BottomSheetBehavior.STATE_COLLAPSED);
                activity.getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_player_holder, parent, "sheet-hold-test").commitNow();
                host.set(parent);
                final PlayerBinding playerBinding = PlayerBinding.inflate(activity.getLayoutInflater());
                final LocalService service = new LocalService(activity);
                final PlayerServiceInterface serviceInterface = (PlayerServiceInterface) Proxy.newProxyInstance(
                        PlayerServiceInterface.class.getClassLoader(), new Class<?>[]{PlayerServiceInterface.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "getInstance": return service;
                                case "getView": return playerBinding.getRoot();
                                case "removeViewFromParent":
                                    final ViewGroup container = (ViewGroup) playerBinding.getRoot().getParent();
                                    if (container != null) container.removeView(playerBinding.getRoot());
                                    return null;
                                default: return method.getReturnType() == boolean.class ? false : null;
                            }
                        });
                final Player player = new Player(serviceInterface);
                set(Player.class, player, "binding", playerBinding);
                player.simpleExoPlayer = new ExoPlayer.Builder(activity).build();
                set(Player.class, player, "exoPlayerEventAdapter", new ExoPlayerEventAdapter(player));
                playing.set(player);
                final String url = "https://example.invalid/sheet-video";
                final StreamInfoItem item = new StreamInfoItem(
                        0, url, "Sheet fixture", StreamType.VIDEO_STREAM);
                item.setUploaderName("Synthetic fixture");
                final SinglePlayQueue queue = new SinglePlayQueue(item);
                queue.init();
                set(Player.class, player, "playQueue", queue);
                final Object metadata = get(Player.class, player, "metadataController");
                set(metadata.getClass(), metadata, "currentItem", queue.getItem());
                set(metadata.getClass(), metadata, "currentMetadata", queue.getItem());
                set(VideoDetailFragment.class, parent, "player", player);
                set(VideoDetailFragment.class, parent, "playerService", serviceInterface);
                set(VideoDetailFragment.class, parent, "serviceId", 0);
                set(VideoDetailFragment.class, parent, "url", url);
                final StreamInfo info = new StreamInfo(0, url, url, "Sheet fixture");
                info.setStreamType(StreamType.VIDEO_STREAM);
                info.setDuration(60);
                set(VideoDetailFragment.class, parent, "currentInfo", info);
                final SponsorBlockFragment child = new SponsorBlockFragment(info);
                child.setListener(new SponsorBlockFragmentListener() {
                    @Override public Long getSponsorBlockPosition(String video) { return 1000L; }
                    @Override public Long getSponsorBlockDuration(String video) { return 60000L; }
                    @Override public void onSponsorBlockEditingChanged(Object owner, String video,
                                                                       boolean held) {
                        player.setSponsorBlockEditing(owner, video, held);
                    }
                    @Override public void onSkippingEnabledChanged(boolean enabled) { }
                    @Override public void onRequestNewPendingSegment(int service, String video,
                                                                    int start, int end) { }
                    @Override public void onRequestClearPendingSegment(int service, String video) { }
                    @Override public void onRequestSubmitPendingSegment(int service, String video,
                                                                        SponsorBlockSegment segment) { }
                    @Override public void onSeekToRequested(long position) { }
                });
                final TabAdapter tabs = new TabAdapter(parent.getChildFragmentManager());
                tabs.addFragment(child, "SPONSOR_BLOCK TAB");
                set(VideoDetailFragment.class, parent, "pageAdapter", tabs);
                final FragmentVideoDetailBinding binding = (FragmentVideoDetailBinding)
                        get(VideoDetailFragment.class, parent, "binding");
                hostViews.set(binding);
                binding.playerPlaceholder.addView(playerBinding.getRoot());
                binding.viewPager.setAdapter(tabs);
                binding.viewPager.setVisibility(View.VISIBLE);
                editor.set(child);
                sheet.set(BottomSheetBehavior.from(activity.findViewById(R.id.fragment_player_holder)));
                sheet.get().setState(BottomSheetBehavior.STATE_EXPANDED);
            });
            awaitSheet(scenario, sheet.get(), BottomSheetBehavior.STATE_EXPANDED);
            scenario.onActivity(activity -> {
                assertTrue(editor.get().isResumed());
                assertTrue(editor.get().getUserVisibleHint());
                editor.get().requireView().findViewById(R.id.sponsor_block_controls_mark_segment_start)
                        .performClick();
                assertTrue(playing.get().simpleExoPlayer.getPauseAtEndOfMediaItems());
                if (fullscreen) {
                    setFullscreenHost(host.get(), playing.get(), true);
                    assertSame(hostViews.get().fullscreenPlayerContainer,
                            playing.get().getRootView().getParent());
                    assertEquals(View.VISIBLE, hostViews.get().fullscreenPlayerContainer.getVisibility());
                } else {
                    sheet.get().setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            });
            if (!fullscreen) awaitSheet(scenario, sheet.get(), BottomSheetBehavior.STATE_COLLAPSED);
            scenario.onActivity(activity -> {
                assertTrue("Hiding the host does not pause the child", editor.get().isResumed());
                assertTrue(editor.get().getUserVisibleHint());
                assertFalse("Hidden editor must release the queue hold",
                        playing.get().simpleExoPlayer.getPauseAtEndOfMediaItems());
                editor.get().refreshEditor();
                assertFalse("Progress renders must not reacquire an invisible editor's hold",
                        playing.get().simpleExoPlayer.getPauseAtEndOfMediaItems());
                if (fullscreen) {
                    setFullscreenHost(host.get(), playing.get(), false);
                    assertSame(hostViews.get().playerPlaceholder,
                            playing.get().getRootView().getParent());
                    assertEquals(View.GONE, hostViews.get().fullscreenPlayerContainer.getVisibility());
                } else {
                    sheet.get().setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            });
            awaitSheet(scenario, sheet.get(), BottomSheetBehavior.STATE_EXPANDED);
            scenario.onActivity(activity -> assertTrue("The visible draft may hold again",
                    playing.get().simpleExoPlayer.getPauseAtEndOfMediaItems()));
        } finally {
            if (playing.get() != null) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    playing.get().destroy();
                    ((DefaultTrackSelector) get(Player.class, playing.get(), "trackSelector")).release();
                });
            }
        }
    }

    private static void setFullscreenHost(final VideoDetailFragment host, final Player player,
                                          final boolean fullscreen) {
        // Control orientation state while exercising the real host callback and player reparenting.
        set(PlayerUiModeController.class, player.getUiModeController(), "isFullscreen", fullscreen);
        host.onFullscreenStateChanged(fullscreen);
    }

    private static void awaitSheet(final ActivityScenario<MainActivity> scenario,
                                   final BottomSheetBehavior<?> sheet, final int expected) {
        final long deadline = SystemClock.uptimeMillis() + 5000;
        final boolean[] ready = {false};
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> ready[0] = sheet.getState() == expected);
            if (ready[0]) return;
            SystemClock.sleep(20);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Sheet did not settle at " + expected);
    }

    private static Object get(final Class<?> type, final Object target, final String name) {
        try {
            final Field field = type.getDeclaredField(name); field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void set(final Class<?> type, final Object target, final String name, final Object value) {
        try {
            final Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static final class LocalService extends Service {
        LocalService(final Context context) { attachBaseContext(context); }
        @Override public IBinder onBind(final Intent intent) { return null; }
    }
}
