package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.content.ContextCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Tracks;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.PlayerService.PlayerType;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.NavigationHelper;

import java.io.FileInputStream;
import java.util.function.BooleanSupplier;

/** Opt-in production-service probe. Supply playerModeOnlineProbe=true, url, and expectedDelivery. */
@RunWith(AndroidJUnit4.class)
public class PlayerModeOnlineSwitchingTest {
    private static final String TAG = "PlayerModeOnlineProbe";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private Activity main;
    private PlayQueueActivity activity;
    private Player player;
    private boolean enabled;
    private String previousClient;
    private long gapStart;
    private long maximumGap;

    @Test
    public void realSourceSurvivesAllModes() throws Exception {
        final Bundle arguments = InstrumentationRegistry.getArguments();
        assumeTrue("Online playback probe is explicitly opt-in",
                "true".equals(arguments.getString("playerModeOnlineProbe")));
        enabled = true;
        final String url = arguments.getString("url");
        assertNotNull("Supply a playable public stream URL", url);
        final String expectedDelivery = arguments.getString("expectedDelivery");
        assertNotNull("Declare the delivery method being validated", expectedDelivery);
        final int serviceId = Integer.parseInt(arguments.getString("serviceId", "0"));
        previousClient = NewPipe.getYoutubePlayerClient();
        NewPipe.setYoutubePlayerClient(arguments.getString("client", "mweb"));
        final boolean audioOrigin = "true".equals(arguments.getString("audioOrigin"));
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
        // Extract only after the app is foreground: background UIDs may have no network access.
        final StreamInfo info = StreamInfo.getInfo(NewPipe.getService(serviceId), url);
        InfoCache.getInstance().putInfo(serviceId, url, info, InfoItem.InfoType.STREAM);
        instrumentation.runOnMainSync(() -> ContextCompat.startForegroundService(main,
                NavigationHelper.getPlayerIntent(main, DeviceUtils.getPlayerServiceClass(),
                        new SinglePlayQueue(info), false, false)
                        .putExtra(Player.PLAYER_TYPE, (audioOrigin
                                ? PlayerType.AUDIO : PlayerType.VIDEO).ordinal())));
        openQueue();
        await(() -> activity.player != null && activity.player.simpleExoPlayer != null,
                "Production service connected");
        player = activity.player;
        awaitReady();
        if (audioOrigin) {
            final Object originalEngine = player.simpleExoPlayer;
            final Object originalService = player.service;
            final PlayQueue originalQueue = player.getPlayQueue();
            for (final PlayerType target : new PlayerType[]{PlayerType.POPUP, PlayerType.VIDEO}) {
                instrumentation.runOnMainSync(() ->
                        NavigationHelper.switchPlayerMode(main, player, target));
                await(() -> player.getPlayerType() == target
                        && player.getSelectedVideoStream() != null
                        && player.simpleExoPlayer.getPlaybackState()
                        == com.google.android.exoplayer2.Player.STATE_READY,
                        "Audio-origin video source prepared");
                instrumentation.runOnMainSync(() -> {
                    assertSame(originalEngine, player.simpleExoPlayer);
                    assertSame(originalService, player.service);
                    assertSame(originalQueue, player.getPlayQueue());
                    assertEquals(false, player.getPlayWhenReady());
                    final VideoStream selected = player.getSelectedVideoStream();
                    assertNotNull("Audio-origin return restores selected video metadata", selected);
                    assertEquals(expectedDelivery, selected.getDeliveryMethod().name());
                    if (!info.getSubtitles().isEmpty()) {
                        assertTrue("Available external subtitles are attached after audio origin",
                                !"[]".equals(subtitleFormats()));
                    }
                    Log.i(TAG, "audioOriginRestored mode=" + target + " delivery="
                            + selected.getDeliveryMethod() + " quality=" + selected.getResolution()
                            + " subtitleFormats=" + subtitleFormats());
                });
            }
        }
        final boolean live = "true".equals(arguments.getString("live"));
        if (!live) {
            selectAlternativeAndAwait("qualityPopupMenu");
            selectAlternativeAndAwait("audioTrackPopupMenu");
        }
        final PlayQueue queue = player.getPlayQueue();
        final Object engine = player.simpleExoPlayer;
        final Object adapter = player.getPlayQueueAdapter();
        final Object service = player.service;
        final String[] quality = new String[1];
        final String[] audio = new String[1];
        final String[] audioTrack = new String[1];
        final String[] text = new String[1];
        instrumentation.runOnMainSync(() -> {
            final VideoStream selected = player.getSelectedVideoStream();
            if (live) {
                assertTrue("Supplied live stream must be a live window",
                        player.simpleExoPlayer.isCurrentMediaItemLive());
                assertEquals("LIVE_STREAM", expectedDelivery);
            } else {
                assertNotNull("Video source must expose selected quality", selected);
                assertEquals(expectedDelivery, selected.getDeliveryMethod().name());
                quality[0] = selected.getResolution();
            }
            audio[0] = selectedAudioLanguage();
            audioTrack[0] = selectedAudioTrack();
            text[0] = subtitleFormats();
            player.simpleExoPlayer.setPlaybackParameters(
                    new com.google.android.exoplayer2.PlaybackParameters(1.25f, 0.9f));
            player.simpleExoPlayer.setSkipSilenceEnabled(true);
            player.simpleExoPlayer.setRepeatMode(com.google.android.exoplayer2.Player.REPEAT_MODE_ALL);
            player.simpleExoPlayer.addListener(new com.google.android.exoplayer2.Player.Listener() {
                @Override
                public void onIsPlayingChanged(final boolean playing) {
                    if (!playing && player.getPlayWhenReady()) {
                        gapStart = SystemClock.elapsedRealtime();
                    } else if (playing && gapStart != 0) {
                        maximumGap = Math.max(maximumGap, SystemClock.elapsedRealtime() - gapStart);
                        gapStart = 0;
                    }
                }
            });
            Log.i(TAG, "delivery=" + expectedDelivery + " quality=" + quality[0]
                    + " audioLanguage=" + audio[0] + " subtitleFormats=" + text[0]);
        });
        for (final boolean playing : new boolean[]{true, false}) {
            instrumentation.runOnMainSync(() -> player.simpleExoPlayer.setPlayWhenReady(playing));
            for (final PlayerType target : new PlayerType[]{PlayerType.POPUP, PlayerType.AUDIO,
                    PlayerType.VIDEO, PlayerType.AUDIO, PlayerType.POPUP, PlayerType.VIDEO}) {
                openQueue();
                instrumentation.runOnMainSync(() -> {
                    final MenuBuilder menu = new MenuBuilder(activity);
                    activity.onCreateOptionsMenu(menu);
                    activity.onPrepareOptionsMenu(menu);
                    final int id = target == PlayerType.VIDEO ? R.id.action_switch_main
                            : target == PlayerType.POPUP ? R.id.action_switch_popup
                            : R.id.action_switch_background;
                    assertTrue(activity.onOptionsItemSelected(menu.findItem(id)));
                });
                await(() -> player.getPlayerType() == target, "Requested mode reached");
                awaitReady();
                SystemClock.sleep(1500);
                instrumentation.runOnMainSync(() -> {
                    assertSame(service, player.service);
                    assertSame(engine, player.simpleExoPlayer);
                    assertSame(queue, player.getPlayQueue());
                    assertSame(adapter, player.getPlayQueueAdapter());
                    assertEquals(playing, player.getPlayWhenReady());
                    assertEquals(1.25f, player.getPlaybackParameters().speed, 0f);
                    assertEquals(0.9f, player.getPlaybackParameters().pitch, 0f);
                    assertTrue(player.simpleExoPlayer.getSkipSilenceEnabled());
                    assertEquals(com.google.android.exoplayer2.Player.REPEAT_MODE_ALL,
                            player.simpleExoPlayer.getRepeatMode());
                    assertEquals("Selected audio language survives", audio[0], selectedAudioLanguage());
                    assertEquals("Explicit audio selection survives", audioTrack[0], selectedAudioTrack());
                    if (target != PlayerType.AUDIO && !live) {
                        assertNotNull(player.getSelectedVideoStream());
                        assertEquals(quality[0], player.getSelectedVideoStream().getResolution());
                        assertEquals("External text formats survive", text[0], subtitleFormats());
                    }
                    if (target == PlayerType.VIDEO) {
                        assertNotNull(player.getParentActivity());
                        assertSame(player.getParentActivity().findViewById(R.id.player_placeholder),
                                player.getRootView().getParent());
                    }
                    assertNull("Playback error", player.simpleExoPlayer.getPlayerError());
                    Log.i(TAG, "mode=" + target + " playWhenReady=" + playing
                            + " positionMs=" + player.getCurrentPosition()
                            + " maximumNotPlayingGapMs=" + maximumGap);
                });
            }
        }
        if (live) {
            for (final boolean atEdge : new boolean[]{true, false}) {
                instrumentation.runOnMainSync(() -> {
                    if (atEdge) {
                        player.simpleExoPlayer.seekToDefaultPosition();
                        player.simpleExoPlayer.play();
                    } else {
                        assertTrue("Live stream must offer a time-shift window",
                                player.getDuration() > 60000);
                        player.simpleExoPlayer.pause();
                        player.simpleExoPlayer.seekTo(player.getDuration() - 30000);
                    }
                });
                awaitReady();
                final long[] offset = {0};
                final long capturedAt = SystemClock.elapsedRealtime();
                instrumentation.runOnMainSync(() -> {
                    offset[0] = player.simpleExoPlayer.getCurrentLiveOffset();
                    assertTrue("Live offset must be observable for time-shift verification",
                            offset[0] != C.TIME_UNSET);
                });
                for (final PlayerType target : new PlayerType[]{PlayerType.POPUP, PlayerType.AUDIO,
                        PlayerType.VIDEO, PlayerType.AUDIO, PlayerType.POPUP, PlayerType.VIDEO}) {
                    instrumentation.runOnMainSync(() ->
                            NavigationHelper.switchPlayerMode(main, player, target));
                    await(() -> player.getPlayerType() == target, "Live mode reached");
                    awaitReady();
                    instrumentation.runOnMainSync(() -> {
                        assertEquals(atEdge, player.getPlayWhenReady());
                        if (atEdge) {
                            assertTrue("Live edge retained", player.isLiveEdge());
                        } else {
                            final long expectedOffset = offset[0]
                                    + SystemClock.elapsedRealtime() - capturedAt;
                            assertEquals("Paused live offset advances with wall time",
                                    expectedOffset, player.simpleExoPlayer.getCurrentLiveOffset(),
                                    3000);
                        }
                    });
                }
            }
        }
        if ("true".equals(arguments.getString("screenCycle"))) {
            instrumentation.runOnMainSync(() -> {
                NavigationHelper.switchPlayerMode(main, player, PlayerType.AUDIO);
                player.simpleExoPlayer.play();
            });
            shell("input keyevent KEYCODE_SLEEP");
            SystemClock.sleep(3000);
            shell("input keyevent KEYCODE_WAKEUP");
            awaitReady();
            instrumentation.runOnMainSync(() -> {
                assertTrue(player.getPlayWhenReady());
                assertNull(player.simpleExoPlayer.getPlayerError());
                Log.i(TAG, "screenCycleCompleted maximumNotPlayingGapMs=" + maximumGap);
            });
        }
    }

    private void selectAlternativeAndAwait(final String fieldName) {
        final boolean[] dispatched = {false};
        final boolean[] tracksChanged = {false};
        final VideoStream[] requestedQuality = new VideoStream[1];
        final com.google.android.exoplayer2.Player.Listener listener =
                new com.google.android.exoplayer2.Player.Listener() {
                    @Override
                    public void onTracksChanged(final Tracks tracks) {
                        tracksChanged[0] = !tracks.getGroups().isEmpty();
                    }
                };
        instrumentation.runOnMainSync(() -> {
            try {
                final java.lang.reflect.Field field = Player.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                final androidx.appcompat.widget.PopupMenu popup =
                        (androidx.appcompat.widget.PopupMenu) field.get(player);
                final android.view.Menu menu = popup.getMenu();
                if (menu.size() > 1) {
                    int index = menu.size() - 1;
                    if ("qualityPopupMenu".equals(fieldName)) {
                        final java.lang.reflect.Field selected =
                                Player.class.getDeclaredField("selectedStreamIndex");
                        selected.setAccessible(true);
                        if (selected.getInt(player) == index) {
                            index = 0;
                        }
                        requestedQuality[0] =
                                org.schabi.newpipe.player.mediaitem.MediaItemTag
                                        .from(player.simpleExoPlayer.getCurrentMediaItem()).get()
                                        .getMaybeQuality().get().getSortedVideoStreams().get(index);
                    } else if (menu.getItem(index).getTitle().toString().equals(
                            player.getBinding().audioTrackTextView.getText().toString())) {
                        index = 0;
                    }
                    player.simpleExoPlayer.addListener(listener);
                    assertTrue("Alternative selection dispatches through its real menu",
                            menu.performIdentifierAction(menu.getItem(index).getItemId(), 0));
                    dispatched[0] = true;
                }
            } catch (final ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
        if (dispatched[0]) {
            await(() -> tracksChanged[0] && (requestedQuality[0] == null
                    || org.schabi.newpipe.player.mediaitem.MediaItemTag
                    .from(player.simpleExoPlayer.getCurrentMediaItem())
                    .flatMap(org.schabi.newpipe.player.mediaitem.MediaItemTag::getMaybeQuality)
                    .map(quality -> quality.getSelectedVideoStream() == requestedQuality[0])
                    .orElse(false)), "Requested alternative source/track selection applied");
            awaitReady();
            if (requestedQuality[0] != null) {
                instrumentation.runOnMainSync(() -> assertSame(
                        "Quality controls match the requested prepared source",
                        requestedQuality[0], player.getSelectedVideoStream()));
            }
            instrumentation.runOnMainSync(() -> player.simpleExoPlayer.removeListener(listener));
        }
    }

    private String selectedAudioTrack() {
        try {
            final java.lang.reflect.Field field = Player.class.getDeclaredField("videoResolver");
            field.setAccessible(true);
            return ((org.schabi.newpipe.player.resolver.VideoPlaybackResolver) field.get(player))
                    .getAudioTrack();
        } catch (final ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private String selectedAudioLanguage() {
        for (final Tracks.Group group : player.simpleExoPlayer.getCurrentTracks().getGroups()) {
            if (group.getType() == C.TRACK_TYPE_AUDIO) {
                for (int i = 0; i < group.length; i++) {
                    if (group.isTrackSelected(i)) {
                        return String.valueOf(group.getTrackFormat(i).language);
                    }
                }
            }
        }
        throw new AssertionError("Selected audio track must exist");
    }

    private String subtitleFormats() {
        final java.util.TreeSet<String> formats = new java.util.TreeSet<>();
        for (final Tracks.Group group : player.simpleExoPlayer.getCurrentTracks().getGroups()) {
            if (group.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < group.length; i++) {
                    final com.google.android.exoplayer2.Format format = group.getTrackFormat(i);
                    formats.add(format.language + ":" + format.sampleMimeType);
                }
            }
        }
        return formats.toString();
    }

    private void openQueue() {
        if (activity != null && !activity.isDestroyed() && !activity.isFinishing()) {
            return;
        }
        activity = (PlayQueueActivity) instrumentation.startActivitySync(
                NavigationHelper.getPlayQueueActivityIntent(instrumentation.getTargetContext())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> activity.player != null, "Queue service connected");
    }

    private void awaitReady() {
        await(() -> {
            assertNull("Playback error", player.simpleExoPlayer.getPlayerError());
            return player.simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_READY;
        }, "Online source prepared");
    }

    private void await(final BooleanSupplier condition, final String message) {
        final long deadline = SystemClock.elapsedRealtime() + 150000;
        final boolean[] result = {false};
        do {
            instrumentation.runOnMainSync(() -> result[0] = condition.getAsBoolean());
            if (result[0]) {
                return;
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError(message);
    }

    private void shell(final String command) throws Exception {
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation()
                .executeShellCommand(command);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            final byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) {
                // Drain the command to completion.
            }
        }
    }

    @After
    public void cleanUp() {
        if (!enabled) {
            return;
        }
        if (previousClient != null) {
            NewPipe.setYoutubePlayerClient(previousClient);
        }
        instrumentation.runOnMainSync(() -> {
            if (activity != null) {
                activity.finish();
            }
            PlayerHolder.getInstance().stopService();
            instrumentation.getTargetContext().stopService(new Intent(
                    instrumentation.getTargetContext(), DeviceUtils.getPlayerServiceClass()));
            if (player != null && player.getParentActivity() != null) {
                main = player.getParentActivity();
            }
            if (main != null) {
                main.finish();
            }
        });
    }
}
