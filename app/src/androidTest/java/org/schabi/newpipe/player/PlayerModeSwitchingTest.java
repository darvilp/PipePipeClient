package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.view.Menu;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.PlayerService.PlayerType;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Production service and queue activity, with cached metadata and synthetic local media. */
@RunWith(AndroidJUnit4.class)
public class PlayerModeSwitchingTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Activity main;
    private PlayQueueActivity activity;
    private GatedMediaServer mediaServer;
    private final Map<String, Object> savedPreferences = new HashMap<>();

    @Before
    public void isolateOptionalNetworkFeatures() {
        instrumentation.runOnMainSync(() -> {
            final android.content.Context context = instrumentation.getTargetContext();
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
            final String sponsor = context.getString(R.string.sponsor_block_enable_key);
            final String tabs = context.getString(R.string.video_tabs_key);
            final String selectedTab = context.getString(R.string.stream_info_selected_tab_key);
            for (final String key : Arrays.asList(sponsor, tabs, selectedTab,
                    context.getString(R.string.always_start_from_beginning_key),
                    context.getString(R.string.start_main_player_fullscreen_key),
                    context.getString(R.string.rotate_fullscreen_to_video_orientation_key))) {
                savedPreferences.put(key, preferences.getAll().get(key));
            }
            preferences.edit().putBoolean(sponsor, false)
                    .putStringSet(tabs, Collections.singleton("description"))
                    .putString(selectedTab, "DESCRIPTION")
                    .putBoolean(context.getString(R.string.start_main_player_fullscreen_key), false)
                    .commit();
        });
    }

    @Test
    public void mainQueueOffersBothAlternativeModes() throws Exception {
        start(PlayerType.VIDEO);
        instrumentation.runOnMainSync(() -> {
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onPrepareOptionsMenu(menu);
            assertTrue("Main mode must offer popup", menu.findItem(R.id.action_switch_popup)
                    .isVisible());
            assertTrue("Main mode must offer background",
                    menu.findItem(R.id.action_switch_background).isVisible());
            assertFalse(menu.findItem(R.id.action_switch_main).isVisible());
        });
    }

    @Test
    public void audioToPopupKeepsPausedIntentAndQueue() throws Exception {
        start(PlayerType.AUDIO);
        final Player original = activity.player;
        final PlayQueue queue = original.getPlayQueue();
        final Object engine = original.simpleExoPlayer;
        final Object adapter = original.getPlayQueueAdapter();
        instrumentation.runOnMainSync(() -> {
            assertTrue("Grant SYSTEM_ALERT_WINDOW to target package before this test",
                    PermissionHelper.isPopupEnabled(activity));
            assertFalse(original.getPlayWhenReady());
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_popup));
        });
        await(() -> original.popupPlayerSelected(), "Popup mode");
        instrumentation.runOnMainSync(() -> {
            assertSame(original, activity.player);
            assertSame(queue, original.getPlayQueue());
            assertSame(engine, original.simpleExoPlayer);
            assertSame(adapter, original.getPlayQueueAdapter());
            assertEquals(2, queue.size());
            assertEquals(0, queue.getIndex());
            assertFalse("Switching must not resume paused playback", original.getPlayWhenReady());
        });
    }

    @Test
    public void compatibleVideoModesPreserveSourceManager() throws Exception {
        start(PlayerType.VIDEO);
        final Player original = activity.player;
        final Object manager = field(original, "playQueueManager");
        instrumentation.runOnMainSync(() -> {
            assertTrue("Grant SYSTEM_ALERT_WINDOW before this test",
                    PermissionHelper.isPopupEnabled(activity));
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_popup));
        });
        await(original::popupPlayerSelected, "Popup mode");
        instrumentation.runOnMainSync(() -> assertSame(
                "Moving the same video into popup must not reload its source",
                manager, field(original, "playQueueManager")));
    }

    @Test
    public void selectedQualityIsCurrentBeforeAndAfterPopupSwitch() throws Exception {
        start(PlayerType.VIDEO, false, false, false, true);
        final Player original = activity.player;
        final VideoStream[] requested = new VideoStream[1];
        instrumentation.runOnMainSync(() -> {
            final androidx.appcompat.widget.PopupMenu popup =
                    (androidx.appcompat.widget.PopupMenu) field(original, "qualityPopupMenu");
            final org.schabi.newpipe.player.mediaitem.MediaItemTag.Quality quality =
                    org.schabi.newpipe.player.mediaitem.MediaItemTag
                            .from(original.simpleExoPlayer.getCurrentMediaItem()).get()
                            .getMaybeQuality().get();
            assertEquals(2, quality.getSortedVideoStreams().size());
            final int next = quality.getSelectedVideoStreamIndex() == 0 ? 1 : 0;
            requested[0] = quality.getSortedVideoStreams().get(next);
            assertTrue(popup.getMenu().performIdentifierAction(next, 0));
        });
        await(() -> org.schabi.newpipe.player.mediaitem.MediaItemTag
                .from(original.simpleExoPlayer.getCurrentMediaItem())
                .flatMap(org.schabi.newpipe.player.mediaitem.MediaItemTag::getMaybeQuality)
                .map(quality -> quality.getSelectedVideoStream() == requested[0]).orElse(false)
                && original.simpleExoPlayer.getPlaybackState()
                == com.google.android.exoplayer2.Player.STATE_READY, "Selected source prepared");
        final Object manager = field(original, "playQueueManager");
        instrumentation.runOnMainSync(() -> assertSame(
                "Quality controls reflect the selected source before switching",
                requested[0], original.getSelectedVideoStream()));
        selectMode(PlayerType.POPUP);
        await(original::popupPlayerSelected, "Popup mode");
        instrumentation.runOnMainSync(() -> {
            assertSame(requested[0], original.getSelectedVideoStream());
            assertSame(manager, field(original, "playQueueManager"));
        });
    }

    @Test
    public void leavingPopupReleasesCloseOverlayForNextPopup() throws Exception {
        start(PlayerType.POPUP);
        final Player original = activity.player;
        instrumentation.runOnMainSync(() -> {
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_background));
        });
        await(original::audioPlayerSelected, "Background mode");
        instrumentation.runOnMainSync(() -> assertEquals(
                "Detached popup close overlay must be released before another popup",
                null, field(original, "closeOverlayBinding")));
    }

    @Test
    public void popupCloseAnimationCannotStopALaterBackgroundSelection() throws Exception {
        start(PlayerType.POPUP);
        final Player original = activity.player;
        instrumentation.runOnMainSync(() -> {
            original.closePopup();
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_background));
        });
        SystemClock.sleep(600);
        instrumentation.runOnMainSync(() -> {
            assertFalse("Old close animation must not stop a later mode",
                    original.getPlayQueue().isDisposed());
            assertEquals(PlayerType.AUDIO, original.getPlayerType());
        });
    }

    @Test
    public void mainReturnUsesActiveQueueInsteadOfBrowsedDetails() throws Exception {
        checkMainReturn(false);
    }

    @Test
    public void mainReturnIgnoresPendingReplacementPlaylist() throws Exception {
        checkMainReturn(true);
    }

    private void checkMainReturn(final boolean pendingReplacement) throws Exception {
        start(PlayerType.AUDIO);
        final Player original = activity.player;
        final PlayQueue queue = original.getPlayQueue();
        final String activeUrl = queue.getItem().getUrl();
        launchMain();
        instrumentation.runOnMainSync(() -> {
            final AppCompatActivity host = (AppCompatActivity) main;
            final String displayedUrl = pendingReplacement ? activeUrl
                    : queue.getItem(1).getUrl();
            final PlayQueue replacement = pendingReplacement ? new SinglePlayQueue(
                    new StreamInfoItem(0, activeUrl, "Replacement", StreamType.VIDEO_STREAM))
                    : null;
            NavigationHelper.openVideoDetailFragment(host, host.getSupportFragmentManager(),
                    0, displayedUrl, "Browsed details", replacement, false);
        });
        await(() -> ((AppCompatActivity) main).getSupportFragmentManager()
                .findFragmentById(R.id.fragment_player_holder) instanceof VideoDetailFragment,
                "Browsed details fragment");
        instrumentation.waitForIdleSync();
        reopenQueueIfNeeded();
        instrumentation.runOnMainSync(() -> {
            assertSame("Main return dispatches from the live queue activity",
                    original, activity.player);
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_main));
        });
        await(original::videoPlayerSelected, "Main mode");
        instrumentation.runOnMainSync(() -> {
            assertSame("Returning must retain the active queue", queue, original.getPlayQueue());
            assertEquals(activeUrl, original.getPlayQueue().getItem().getUrl());
            assertFalse("Returning must preserve pause", original.getPlayWhenReady());
            assertNotNull(original.getParentActivity());
        });
    }

    @Test
    public void laterBackgroundSelectionCancelsPendingMainNavigation() throws Exception {
        start(PlayerType.AUDIO);
        final Player original = activity.player;
        instrumentation.runOnMainSync(() -> {
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_main));
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_background));
        });
        instrumentation.waitForIdleSync();
        SystemClock.sleep(500);
        instrumentation.runOnMainSync(() -> {
            assertEquals(PlayerType.AUDIO, original.getPlayerType());
            assertFalse(original.getPlayWhenReady());
            assertEquals(null, original.getRootView().getParent());
        });
    }

    @Test
    public void mainReturnFollowsQueueAdvancementDuringNavigation() throws Exception {
        start(PlayerType.AUDIO);
        final Player original = activity.player;
        final PlayQueue queue = original.getPlayQueue();
        instrumentation.runOnMainSync(() -> {
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_main));
            queue.setIndex(1);
        });
        await(original::videoPlayerSelected, "Main mode after queue advancement");
        await(() -> original.getParentActivity() != null
                && "Second fixture".contentEquals(((android.widget.TextView) original
                .getParentActivity().findViewById(R.id.detail_video_title_view)).getText()),
                "Main details must show the newly active item");
        instrumentation.runOnMainSync(() -> {
            assertSame(queue, original.getPlayQueue());
            assertEquals(1, queue.getIndex());
            assertFalse(original.getPlayWhenReady());
        });
    }

    @Test
    public void newPlaybackCancelsPendingMainNavigation() throws Exception {
        start(PlayerType.AUDIO);
        final Player original = activity.player;
        final String replacementUrl = original.getPlayQueue().getItem(1).getUrl();
        instrumentation.runOnMainSync(() -> {
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_main));
            original.handleIntent(NavigationHelper.getPlayerIntent(main,
                    DeviceUtils.getPlayerServiceClass(), new SinglePlayQueue(new StreamInfoItem(
                            0, replacementUrl, "Replacement", StreamType.VIDEO_STREAM)), false, false)
                    .putExtra(Player.PLAYER_TYPE, PlayerType.AUDIO.ordinal()));
        });
        instrumentation.waitForIdleSync();
        SystemClock.sleep(500);
        instrumentation.runOnMainSync(() -> {
            assertEquals(PlayerType.AUDIO, original.getPlayerType());
            assertEquals(1, original.getPlayQueue().size());
            assertEquals(replacementUrl, original.getPlayQueue().getItem().getUrl());
            assertFalse(original.getPlayWhenReady());
        });
    }

    @Test
    public void shutdownCancelsPendingMainNavigation() throws Exception {
        start(PlayerType.AUDIO);
        final Player original = activity.player;
        instrumentation.runOnMainSync(() -> {
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_main));
            original.service.stopService();
        });
        instrumentation.waitForIdleSync();
        SystemClock.sleep(500);
        instrumentation.runOnMainSync(() -> {
            assertTrue(original.getPlayQueue().isDisposed());
            assertFalse(original.isModeSwitchReady());
            assertFalse(PlayerHolder.getInstance().isPlayerOpen());
        });
    }

    @Test
    public void popupExpansionKeepsPausedServiceAndEntersFullscreen() throws Exception {
        start(PlayerType.POPUP);
        final Player original = activity.player;
        final Object engine = original.simpleExoPlayer;
        final PlayQueue queue = original.getPlayQueue();
        final Object manager = field(original, "playQueueManager");
        instrumentation.runOnMainSync(() -> original.getBinding().fullScreenButton.performClick());
        await(() -> original.videoPlayerSelected() && original.isFullscreen(), "Fullscreen main");
        instrumentation.runOnMainSync(() -> {
            assertSame(original, PlayerHolder.getInstance().getPlayer());
            assertSame(engine, original.simpleExoPlayer);
            assertSame(queue, original.getPlayQueue());
            assertSame(manager, field(original, "playQueueManager"));
            assertFalse(original.getPlayWhenReady());
        });
    }

    @Test
    public void popupExpansionShowsVideoAfterCollapsedMiniPlayerCycleAndRotation() throws Exception {
        start(PlayerType.AUDIO);
        final Player original = activity.player;
        instrumentation.runOnMainSync(() -> PreferenceManager.getDefaultSharedPreferences(main)
                .edit().putBoolean(main.getString(
                        R.string.rotate_fullscreen_to_video_orientation_key), true).commit());
        selectMode(PlayerType.VIDEO);
        await(() -> original.videoPlayerSelected() && original.getParentActivity() != null,
                "Main attached before scrolling");
        instrumentation.runOnMainSync(() -> original.getParentActivity().setRequestedOrientation(
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        await(() -> original.getParentActivity() != null
                && original.getParentActivity().getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT, "Portrait main");
        final Activity beforeExpansion = original.getParentActivity();
        final Object engine = original.simpleExoPlayer;
        final PlayQueue queue = original.getPlayQueue();
        collapseMainAndOpenQueue(original);
        for (final PlayerType target : new PlayerType[]{PlayerType.POPUP, PlayerType.AUDIO,
                PlayerType.VIDEO, PlayerType.AUDIO, PlayerType.POPUP}) {
            selectMode(target);
            await(() -> original.getPlayerType() == target
                    && original.simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_READY, "Prepared " + target);
            if (target == PlayerType.VIDEO) {
                await(() -> original.getParentActivity() != null, "Main returned");
                collapseMainAndOpenQueue(original);
            }
        }
        instrumentation.runOnMainSync(() -> original.getBinding().fullScreenButton.performClick());
        await(() -> original.videoPlayerSelected() && original.isFullscreen()
                && original.getParentActivity() != null
                && original.getParentActivity() != beforeExpansion
                && original.getParentActivity().getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && original.getSurfaceView().getHeight() > 0
                && "First fixture".contentEquals(((android.widget.TextView)
                        original.getParentActivity().findViewById(R.id.detail_video_title_view))
                        .getText()), "Fullscreen main recreated with details in landscape");
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            final Activity host = original.getParentActivity();
            final android.graphics.Rect visible = new android.graphics.Rect();
            final boolean videoVisible = original.getSurfaceView().getGlobalVisibleRect(visible);
            final int windowHeight = host.getWindow().getDecorView().getHeight();
            assertTrue("Fullscreen video must occupy the visible window after recreation: rect="
                    + visible + ", windowHeight=" + windowHeight + ", appBarTop="
                    + host.findViewById(R.id.app_bar_layout).getTop(),
                    videoVisible && visible.height() >= windowHeight * 0.9);
            assertSame(host.findViewById(R.id.player_placeholder),
                    original.getRootView().getParent());
            assertSame(engine, original.simpleExoPlayer);
            assertSame(queue, original.getPlayQueue());
            assertFalse(original.getPlayWhenReady());
        });
    }

    private void collapseMainAndOpenQueue(final Player original) {
        final Activity host = original.getParentActivity();
        final com.google.android.material.bottomsheet.BottomSheetBehavior<android.view.View> sheet =
                com.google.android.material.bottomsheet.BottomSheetBehavior.from(
                        host.findViewById(R.id.fragment_player_holder));
        instrumentation.runOnMainSync(() -> sheet.setState(
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED));
        await(() -> sheet.getState()
                == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED
                && host.findViewById(R.id.app_bar_layout).getTop() < 0,
                "Mini-player collapsed with its app-bar offset");
        instrumentation.runOnMainSync(() -> assertTrue(host.findViewById(
                R.id.overlay_play_queue_button).performClick()));
        await(() -> {
            for (final Activity candidate : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (candidate instanceof PlayQueueActivity) {
                    activity = (PlayQueueActivity) candidate;
                    return activity.player == original;
                }
            }
            return false;
        }, "Mini-player queue resumed and bound");
    }

    @Test
    public void recreatedMainKeepsPausedQueueAndEngine() throws Exception {
        start(PlayerType.AUDIO);
        final Player original = activity.player;
        final PlayQueue queue = original.getPlayQueue();
        final Object engine = original.simpleExoPlayer;
        selectMode(PlayerType.VIDEO);
        await(() -> original.videoPlayerSelected() && original.getParentActivity() != null,
                "Main attached");
        final Activity previous = original.getParentActivity();
        instrumentation.runOnMainSync(previous::recreate);
        await(() -> original.getParentActivity() != null
                && original.getParentActivity() != previous, "Recreated main attached");
        instrumentation.runOnMainSync(() -> {
            assertSame(queue, original.getPlayQueue());
            assertSame(engine, original.simpleExoPlayer);
            assertFalse(original.getPlayWhenReady());
        });
    }

    @Test
    public void recoveryRetainsPositionBeforeDurationIsKnown() throws Exception {
        start(PlayerType.AUDIO);
        instrumentation.runOnMainSync(() -> {
            final Player player = activity.player;
            player.simpleExoPlayer.setMediaItem(
                    com.google.android.exoplayer2.MediaItem.fromUri("file:///not-loaded-yet"),
                    7000);
            assertEquals(com.google.android.exoplayer2.C.TIME_UNSET,
                    player.simpleExoPlayer.getDuration());
            player.setRecovery();
            assertEquals("Unknown duration must not erase recovery",
                    7000, player.getPlayQueue().getItem().getRecoveryPosition());
        });
    }

    @Test
    public void audioOnlySourceReloadsOnceAndRecoversDespiteNewPlaybackPreference() throws Exception {
        start(PlayerType.AUDIO, true, false);
        final Player original = activity.player;
        final Object oldManager = field(original, "playQueueManager");
        final Object engine = original.simpleExoPlayer;
        final PlayQueue queue = original.getPlayQueue();
        final java.util.List<Long> recoverySeeks = new java.util.ArrayList<>();
        final Object[] immediateManager = new Object[1];
        instrumentation.runOnMainSync(() -> {
            PreferenceManager.getDefaultSharedPreferences(main).edit()
                    .putBoolean(main.getString(R.string.always_start_from_beginning_key), true)
                    .commit();
            original.simpleExoPlayer.seekTo(7000);
        });
        await(() -> original.simpleExoPlayer.getPlaybackState()
                == com.google.android.exoplayer2.Player.STATE_READY, "Seek settled");
        instrumentation.runOnMainSync(() -> {
            original.simpleExoPlayer.addListener(new com.google.android.exoplayer2.Player.Listener() {
                @Override
                public void onPositionDiscontinuity(
                        final com.google.android.exoplayer2.Player.PositionInfo previous,
                        final com.google.android.exoplayer2.Player.PositionInfo next,
                        final int reason) {
                    if (reason == com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK) {
                        recoverySeeks.add(next.positionMs);
                    }
                }
            });
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            activity.onOptionsItemSelected(menu.findItem(R.id.action_switch_popup));
            immediateManager[0] = field(original, "playQueueManager");
        });
        await(original::popupPlayerSelected, "Popup with video");
        await(() -> original.simpleExoPlayer.getPlaybackState()
                == com.google.android.exoplayer2.Player.STATE_READY, "Reload prepared");
        final Object newManager = field(original, "playQueueManager");
        instrumentation.runOnMainSync(() -> {
            assertTrue("An audio-only source needs a video reload", oldManager != newManager);
            assertSame("The immediately installed manager survives preparation",
                    immediateManager[0], newManager);
            assertEquals("One recovery seek must target the captured position",
                    Collections.singletonList(7000L), recoverySeeks);
            assertEquals(7000, original.getCurrentPosition(), 1000);
            assertSame(engine, original.simpleExoPlayer);
            assertSame(queue, original.getPlayQueue());
            assertFalse(original.getPlayWhenReady());
        });
        SystemClock.sleep(300);
        instrumentation.runOnMainSync(() -> assertSame("Only one source-manager reload",
                newManager, field(original, "playQueueManager")));
    }

    @Test
    public void rapidModeRequestsRetainPendingNonzeroRecovery() throws Exception {
        start(PlayerType.AUDIO, true, false);
        final Player original = activity.player;
        instrumentation.runOnMainSync(() -> original.simpleExoPlayer.seekTo(7000));
        await(() -> original.simpleExoPlayer.getPlaybackState()
                == com.google.android.exoplayer2.Player.STATE_READY, "Seek prepared");
        instrumentation.runOnMainSync(() -> {
            assertTrue(original.switchPlaybackMode(PlayerType.POPUP));
            assertTrue(original.switchPlaybackMode(PlayerType.AUDIO));
            assertTrue(original.switchPlaybackMode(PlayerType.POPUP));
        });
        await(() -> original.simpleExoPlayer.getPlaybackState()
                == com.google.android.exoplayer2.Player.STATE_READY, "Latest source prepared");
        instrumentation.runOnMainSync(() -> {
            assertTrue(original.popupPlayerSelected());
            assertFalse(original.getPlayWhenReady());
            assertEquals("Superseding requests retain the original recovery position",
                    7000, original.getCurrentPosition(), 1000);
        });
    }

    @Test
    public void allSixTransitionsRetainPlayIntentWhileMediaIsBuffering() throws Exception {
        checkBufferedTransitions(true);
    }

    @Test
    public void allSixTransitionsRetainPauseWhileMediaIsBuffering() throws Exception {
        checkBufferedTransitions(false);
    }

    private void checkBufferedTransitions(final boolean playWhenReady) throws Exception {
        start(PlayerType.VIDEO, false, true);
        final Player original = activity.player;
        final Object engine = original.simpleExoPlayer;
        final PlayQueue queue = original.getPlayQueue();
        final Object adapter = original.getPlayQueueAdapter();
        instrumentation.runOnMainSync(() -> original.simpleExoPlayer
                .setPlayWhenReady(playWhenReady));
        for (final PlayerType target : new PlayerType[]{PlayerType.POPUP, PlayerType.AUDIO,
                PlayerType.VIDEO, PlayerType.AUDIO, PlayerType.POPUP, PlayerType.VIDEO}) {
            reopenQueueIfNeeded();
            selectMode(target);
            await(() -> original.getPlayerType() == target, "Buffered mode " + target);
            await(() -> original.simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_BUFFERING,
                    "Reload buffers in " + target);
            instrumentation.runOnMainSync(() -> {
                assertEquals(com.google.android.exoplayer2.Player.STATE_BUFFERING,
                        original.simpleExoPlayer.getPlaybackState());
                assertEquals(playWhenReady, original.getPlayWhenReady());
                assertSame(engine, original.simpleExoPlayer);
                assertSame(queue, original.getPlayQueue());
                assertSame(adapter, original.getPlayQueueAdapter());
            });
        }
        mediaServer.release();
        await(() -> original.simpleExoPlayer.getPlaybackState()
                == com.google.android.exoplayer2.Player.STATE_READY, "Gate released");
        instrumentation.runOnMainSync(() -> assertEquals(playWhenReady, original.getPlayWhenReady()));
    }

    private void reopenQueueIfNeeded() {
        if (activity.isDestroyed() || activity.isFinishing()) {
            activity = (PlayQueueActivity) instrumentation.startActivitySync(
                    NavigationHelper.getPlayQueueActivityIntent(main)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> activity.player != null, "Queue reconnected");
        }
    }

    private void selectMode(final PlayerType target) {
        instrumentation.runOnMainSync(() -> {
            final Menu menu = new MenuBuilder(activity);
            activity.onCreateOptionsMenu(menu);
            final int action = target == PlayerType.VIDEO ? R.id.action_switch_main
                    : target == PlayerType.POPUP ? R.id.action_switch_popup
                    : R.id.action_switch_background;
            activity.onOptionsItemSelected(menu.findItem(action));
        });
    }

    @Test
    public void audioOriginWithSabrAlternativeRestoresVideoSourceAndQuality() throws Exception {
        start(PlayerType.AUDIO, true, false, true);
        final Player original = activity.player;
        final Object manager = field(original, "playQueueManager");
        instrumentation.runOnMainSync(() -> assertEquals(null, original.getSelectedVideoStream()));
        selectMode(PlayerType.POPUP);
        await(() -> original.popupPlayerSelected()
                && original.getSelectedVideoStream() != null
                && original.simpleExoPlayer.getPlaybackState()
                == com.google.android.exoplayer2.Player.STATE_READY, "Video mode prepared");
        instrumentation.runOnMainSync(() -> {
            assertTrue("An audio-origin tag must reload even with a SABR alternative",
                    manager != field(original, "playQueueManager"));
            assertNotNull("Video quality controls require video source metadata",
                    original.getSelectedVideoStream());
            assertEquals("144p", original.getSelectedVideoStream().getResolution());
            assertFalse(original.getPlayWhenReady());
        });
    }

    @Test
    public void deniedPopupRequiresAnotherSelectionAfterGrant() throws Exception {
        start(PlayerType.AUDIO);
        final Player original = activity.player;
        final PlayQueue queue = original.getPlayQueue();
        final Object engine = original.simpleExoPlayer;
        final String packageName = instrumentation.getTargetContext().getPackageName();
        assertTrue("Fixture starts with granted overlay permission",
                Settings.canDrawOverlays(instrumentation.getTargetContext()));
        shell("appops set " + packageName + " SYSTEM_ALERT_WINDOW ignore");
        try {
            assertFalse(Settings.canDrawOverlays(instrumentation.getTargetContext()));
            selectMode(PlayerType.POPUP);
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                assertEquals(PlayerType.AUDIO, original.getPlayerType());
                assertSame(queue, original.getPlayQueue());
                assertSame(engine, original.simpleExoPlayer);
                assertFalse(original.getPlayWhenReady());
                assertEquals(null, original.getRootView().getParent());
            });
            shell("appops set " + packageName + " SYSTEM_ALERT_WINDOW allow");
            instrumentation.waitForIdleSync();
            SystemClock.sleep(250);
            instrumentation.runOnMainSync(() -> assertEquals(
                    "Grant alone must not replay a mode request", PlayerType.AUDIO,
                    original.getPlayerType()));
            selectMode(PlayerType.POPUP);
            await(original::popupPlayerSelected, "Explicit selection after grant");
        } finally {
            shell("appops set " + packageName + " SYSTEM_ALERT_WINDOW allow");
        }
    }

    private void shell(final String command) throws Exception {
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation()
                .executeShellCommand(command);
             InputStream output = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            final byte[] buffer = new byte[1024];
            while (output.read(buffer) != -1) {
                // Drain to wait for command completion; no device identifiers are logged.
            }
        }
    }

    @Test
    public void selectingCurrentMainModeIsIdempotent() throws Exception {
        checkCurrentMode(PlayerType.VIDEO);
    }

    @Test
    public void selectingCurrentAudioModeIsIdempotent() throws Exception {
        checkCurrentMode(PlayerType.AUDIO);
    }

    @Test
    public void selectingCurrentPopupModeIsIdempotent() throws Exception {
        checkCurrentMode(PlayerType.POPUP);
    }

    private void checkCurrentMode(final PlayerType type) throws Exception {
        start(type);
        final Player original = activity.player;
        if (type == PlayerType.VIDEO) {
            selectMode(PlayerType.VIDEO);
            await(() -> original.getParentActivity() != null
                    && original.getRootView().getParent()
                    == original.getParentActivity().findViewById(R.id.player_placeholder),
                    "Visible main fixture attached");
        }
        instrumentation.runOnMainSync(() -> {
            final Object manager = field(original, "playQueueManager");
            final Object engine = original.simpleExoPlayer;
            final Object parent = original.getRootView().getParent();
            final Object adapter = original.getPlayQueueAdapter();
            final PlayQueue queue = original.getPlayQueue();
            assertTrue(original.switchPlaybackMode(type));
            assertTrue(original.switchPlaybackMode(type));
            assertSame(manager, field(original, "playQueueManager"));
            assertSame(engine, original.simpleExoPlayer);
            assertSame(adapter, original.getPlayQueueAdapter());
            assertSame(parent, original.getRootView().getParent());
            assertSame(queue, original.getPlayQueue());
            assertFalse(original.getPlayWhenReady());
        });
    }

    @Test
    public void allSixTransitionsRetainShuffledOrderAndSelection() throws Exception {
        start(PlayerType.VIDEO);
        final Player original = activity.player;
        final PlayQueue queue = original.getPlayQueue();
        instrumentation.runOnMainSync(() -> {
            queue.append(new SinglePlayQueue(new StreamInfoItem(
                    0, queue.getItem().getUrl(), "Duplicate", StreamType.VIDEO_STREAM)).getItem());
            original.onShuffleClicked();
        });
        await(queue::isShuffled, "Shuffle applied");
        final java.util.List<org.schabi.newpipe.player.playqueue.PlayQueueItem> order =
                new java.util.ArrayList<>(queue.getStreams());
        final Object selected = queue.getItem();
        for (final PlayerType target : new PlayerType[]{PlayerType.POPUP, PlayerType.AUDIO,
                PlayerType.VIDEO, PlayerType.AUDIO, PlayerType.POPUP, PlayerType.VIDEO}) {
            reopenQueueIfNeeded();
            selectMode(target);
            await(() -> original.getPlayerType() == target, "Shuffled mode " + target);
            instrumentation.runOnMainSync(() -> {
                assertSame(queue, original.getPlayQueue());
                assertTrue(queue.isShuffled());
                assertTrue(original.simpleExoPlayer.getShuffleModeEnabled());
                assertSame(selected, queue.getItem());
                assertEquals(order.size(), queue.size());
                for (int i = 0; i < order.size(); i++) {
                    assertSame(order.get(i), queue.getItem(i));
                }
            });
        }
    }

    private static final class PlaybackClock implements com.google.android.exoplayer2.Player.Listener {
        private long played;
        private long started;
        private boolean playing;

        @Override
        public void onIsPlayingChanged(final boolean isPlaying) {
            final long now = SystemClock.elapsedRealtime();
            if (playing) {
                played += now - started;
            }
            playing = isPlaying;
            started = now;
        }

        long playedMillis() {
            return played + (playing ? SystemClock.elapsedRealtime() - started : 0);
        }
    }

    @Test
    public void allSixTransitionsPreservePausedStateAndSettings() throws Exception {
        start(PlayerType.VIDEO);
        checkAllTransitions(false);
    }

    @Test
    public void allSixTransitionsPreservePlayingStateAndSettings() throws Exception {
        start(PlayerType.VIDEO);
        checkAllTransitions(true);
    }

    private void checkAllTransitions(final boolean playWhenReady) {
        final Player original = activity.player;
        final PlayQueue queue = original.getPlayQueue();
        final Object engine = original.simpleExoPlayer;
        final Object adapter = original.getPlayQueueAdapter();
        instrumentation.runOnMainSync(() -> {
            assertTrue("Grant SYSTEM_ALERT_WINDOW before this test",
                    PermissionHelper.isPopupEnabled(activity));
            original.simpleExoPlayer.setPlayWhenReady(false);
            original.simpleExoPlayer.setPlaybackParameters(
                    new com.google.android.exoplayer2.PlaybackParameters(1.25f, 0.9f));
            original.simpleExoPlayer.setRepeatMode(
                    com.google.android.exoplayer2.Player.REPEAT_MODE_ALL);
            original.simpleExoPlayer.setSkipSilenceEnabled(true);
            original.simpleExoPlayer.setVolume(0f);
            original.simpleExoPlayer.seekTo(5000);
        });
        await(() -> original.simpleExoPlayer.getPlaybackState()
                == com.google.android.exoplayer2.Player.STATE_READY, "Initial seek settled");
        final PlaybackClock playbackClock = new PlaybackClock();
        instrumentation.runOnMainSync(() -> {
            original.simpleExoPlayer.addListener(playbackClock);
            original.simpleExoPlayer.setPlayWhenReady(playWhenReady);
        });
        if (playWhenReady) {
            await(() -> original.simpleExoPlayer.isPlaying()
                    && original.getCurrentPosition() >= 6000, "Playback clock advances");
        }
        for (final PlayerType target : new PlayerType[]{PlayerType.POPUP, PlayerType.AUDIO,
                PlayerType.VIDEO, PlayerType.AUDIO, PlayerType.POPUP, PlayerType.VIDEO}) {
            if (activity.isDestroyed() || activity.isFinishing()) {
                activity = (PlayQueueActivity) instrumentation.startActivitySync(
                        NavigationHelper.getPlayQueueActivityIntent(main)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                await(() -> activity.player != null, "Queue reconnected");
            }
            final long[] before = new long[2];
            instrumentation.runOnMainSync(() -> {
                before[0] = original.getCurrentPosition();
                before[1] = playbackClock.playedMillis();
                final Menu menu = new MenuBuilder(activity);
                activity.onCreateOptionsMenu(menu);
                final int action = target == PlayerType.VIDEO ? R.id.action_switch_main
                        : target == PlayerType.POPUP ? R.id.action_switch_popup
                        : R.id.action_switch_background;
                activity.onOptionsItemSelected(menu.findItem(action));
            });
            await(() -> original.getPlayerType() == target, "Mode " + target);
            await(() -> original.simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_READY, "Prepared " + target);
            instrumentation.runOnMainSync(() -> {
                if (target != PlayerType.VIDEO) {
                    assertSame(original, activity.player);
                } else {
                    assertSame(original, PlayerHolder.getInstance().getPlayer());
                }
                assertSame(queue, original.getPlayQueue());
                assertSame(engine, original.simpleExoPlayer);
                assertSame(adapter, original.getPlayQueueAdapter());
                assertEquals(playWhenReady, original.getPlayWhenReady());
                assertEquals(1.25f, original.getPlaybackParameters().speed, 0f);
                assertEquals(0.9f, original.getPlaybackParameters().pitch, 0f);
                assertTrue(original.simpleExoPlayer.getSkipSilenceEnabled());
                assertEquals(0f, original.simpleExoPlayer.getVolume(), 0f);
                assertEquals(com.google.android.exoplayer2.Player.REPEAT_MODE_ALL,
                        original.getRepeatMode());
                assertEquals(2, queue.size());
                assertEquals(0, queue.getIndex());
                final double expectedPosition = before[0]
                        + (playbackClock.playedMillis() - before[1]) * 1.25;
                assertEquals("Position drift after " + target, expectedPosition,
                        original.simpleExoPlayer.getCurrentPosition(), 1000);
                if (target == PlayerType.VIDEO) {
                    assertNotNull("Main surface must be attached", original.getParentActivity());
                    assertSame(original.getParentActivity().findViewById(R.id.player_placeholder),
                            original.getRootView().getParent());
                } else if (target == PlayerType.POPUP) {
                    assertNotNull("Popup root must be attached", original.getRootView().getParent());
                } else {
                    assertEquals(null, original.getRootView().getParent());
                }
            });
        }
    }

    private static Object field(final Player player, final String name) {
        try {
            final Field field = Player.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(player);
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private void start(final PlayerType type) throws Exception {
        start(type, false, false);
    }

    private void start(final PlayerType type, final boolean separateAudio,
                       final boolean bufferMedia) throws Exception {
        start(type, separateAudio, bufferMedia, false);
    }

    private void start(final PlayerType type, final boolean separateAudio,
                       final boolean bufferMedia, final boolean sabrAlternative) throws Exception {
        start(type, separateAudio, bufferMedia, sabrAlternative, false);
    }

    private void start(final PlayerType type, final boolean separateAudio,
                       final boolean bufferMedia, final boolean sabrAlternative,
                       final boolean qualityAlternative) throws Exception {
        final File media = copyAsset("player-mode-fixture.mp4");
        final File audio = separateAudio ? copyAsset("player-mode-fixture.m4a") : null;
        if (bufferMedia) {
            mediaServer = new GatedMediaServer(java.nio.file.Files.readAllBytes(media.toPath()));
        }
        final String mediaUrl = bufferMedia ? mediaServer.url() : Uri.fromFile(media).toString();
        final String streamId = "fixture-" + System.nanoTime();
        launchMain();
        instrumentation.runOnMainSync(() -> {
            final String first = "https://www.youtube.com/watch?v=aaaaaaaaaaa";
            final String second = "https://www.youtube.com/watch?v=bbbbbbbbbbb";
            for (final String url : Arrays.asList(first, second)) {
                final StreamInfo info = new StreamInfo(0, url, url,
                        url.equals(first) ? "First fixture" : "Second fixture");
                info.setStreamType(StreamType.VIDEO_STREAM);
                info.setDuration(30);
                info.setDescription(Description.EMPTY_DESCRIPTION);
                info.setUploaderName("Synthetic fixture");
                info.setVideoStreams(Collections.singletonList(new VideoStream.Builder()
                        .setId(streamId).setContent(mediaUrl, true)
                        .setMediaFormat(MediaFormat.MPEG_4).setResolution("144p")
                        .setIsVideoOnly(false).build()));
                if (qualityAlternative) {
                    final java.util.List<VideoStream> videos = new java.util.ArrayList<>(
                            info.getVideoStreams());
                    videos.add(new VideoStream.Builder().setId(streamId + "-alternate")
                            .setContent(mediaUrl, true).setMediaFormat(MediaFormat.MPEG_4)
                            .setResolution("180p").setIsVideoOnly(false).build());
                    info.setVideoStreams(videos);
                }
                if (sabrAlternative) {
                    final java.util.List<VideoStream> videos = new java.util.ArrayList<>(
                            info.getVideoStreams());
                    videos.add(new VideoStream.Builder().setId("unselected-sabr")
                            .setContent("sabr://unselected", true)
                            .setDeliveryMethod(DeliveryMethod.SABR).setMediaFormat(MediaFormat.WEBM)
                            .setResolution("16p").setIsVideoOnly(false).build());
                    info.setVideoStreams(videos);
                }
                if (audio != null) {
                    info.setAudioStreams(Collections.singletonList(new AudioStream.Builder()
                            .setId(streamId + "-audio")
                            .setContent(Uri.fromFile(audio).toString(), true)
                            .setMediaFormat(MediaFormat.M4A).setAverageBitrate(96).build()));
                }
                InfoCache.getInstance().putInfo(0, url, info, InfoItem.InfoType.STREAM);
            }
            final PlayQueue queue = new SinglePlayQueue(Arrays.asList(
                    new StreamInfoItem(0, first, "First", StreamType.VIDEO_STREAM),
                    new StreamInfoItem(0, second, "Second", StreamType.VIDEO_STREAM)), 0);
            ContextCompat.startForegroundService(main, NavigationHelper.getPlayerIntent(main,
                    DeviceUtils.getPlayerServiceClass(), queue, false, false)
                    .putExtra(Player.PLAYER_TYPE, type.ordinal()));
        });
        SystemClock.sleep(1000);
        activity = (PlayQueueActivity) instrumentation.startActivitySync(
                NavigationHelper.getPlayQueueActivityIntent(main)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> activity.player != null && activity.player.getPlayQueue() != null
                && activity.player.getPlayQueueAdapter() != null, "Service binding");
        if (bufferMedia) {
            assertTrue("Real player must request the gated media", mediaServer.awaitRequest());
            await(() -> activity.player.simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_BUFFERING, "Media gate buffers");
        } else {
            await(() -> activity.player.simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_READY, "Synthetic media prepared");
            instrumentation.runOnMainSync(() -> assertNotNull(activity.player.getCurrentStreamInfo()
                    .orElse(null)));
        }
    }

    private void launchMain() {
        instrumentation.runOnMainSync(() -> instrumentation.getTargetContext().startActivity(
                new Intent(instrumentation.getTargetContext(), MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        await(() -> {
            for (final Activity candidate : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (candidate instanceof MainActivity) {
                    main = candidate;
                    return true;
                }
            }
            return false;
        }, "Main activity resumed");
    }

    private File copyAsset(final String name) throws Exception {
        final File media = new File(instrumentation.getTargetContext().getCacheDir(), name);
        try (InputStream source = instrumentation.getContext().getAssets().open(name);
             FileOutputStream target = new FileOutputStream(media)) {
            final byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) {
                target.write(buffer, 0, count);
            }
        }
        return media;
    }

    private void await(final BooleanSupplier condition, final String message) {
        final long deadline = SystemClock.elapsedRealtime() + 30000;
        final boolean[] result = {false};
        do {
            instrumentation.runOnMainSync(() -> result[0] = condition.getAsBoolean());
            if (result[0]) {
                return;
            }
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError(message);
    }

    @After
    public void cleanUp() throws Exception {
        instrumentation.runOnMainSync(() -> {
            final Player active = PlayerHolder.getInstance().getPlayer();
            if (active != null && active.getParentActivity() != null) {
                main = active.getParentActivity();
            }
            restorePreferences();
            if (activity != null) {
                activity.finish();
            }
            PlayerHolder.getInstance().stopService();
            if (main != null) {
                main.stopService(new Intent(main, DeviceUtils.getPlayerServiceClass()));
                main.finish();
            }
        });
        instrumentation.waitForIdleSync();
        if (mediaServer != null) {
            mediaServer.close();
        }
    }

    private void restorePreferences() {
        final SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(
                instrumentation.getTargetContext()).edit();
        for (final Map.Entry<String, Object> entry : savedPreferences.entrySet()) {
            final Object value = entry.getValue();
            if (value == null) {
                editor.remove(entry.getKey());
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else {
                @SuppressWarnings("unchecked") final Set<String> tabs = (Set<String>) value;
                editor.putStringSet(entry.getKey(), tabs);
            }
        }
        editor.commit();
    }
}
