package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Tracks;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.youtube.sabr.YoutubeSabrSession;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.datasource.SabrSessionHelper;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsJsonHelper;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.youtube.LocalDomPoTokenProvider;

import java.io.FileInputStream;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Opt-in live probe: sabrFocusLossProbe=true, scenario=foreground|home|settings|browse. */
@RunWith(AndroidJUnit4.class)
public class SabrFocusLossPlaybackTest {
    private static final String TAG = "SabrFocusLossProbe";
    private static final String REPORTED_URL = "https://www.youtube.com/watch?v=Hq2c7pww1Ds";
    private static final Pattern SAFE_DIAGNOSTIC_FIELDS = Pattern.compile(
            "\\b(?:n|playerMs|audioThrough|videoThrough|selectedTracks|http|segments|count|"
                    + "protection|status|backoffMs|attempt|elapsedMs|retry)=[0-9/.-]+"
                    + "|\\bselectedTracks=(?:true|false)");
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> savedPreferences = new HashMap<>();
    private final CopyOnWriteArrayList<YoutubeSabrSession> sessions = new CopyOnWriteArrayList<>();
    private final AtomicReference<PlaybackException> playbackError = new AtomicReference<>();
    private MainActivity main;
    private Player player;
    private ExoPlayer engine;
    private PlayQueue originalQueue;
    private PlayerMediaItem originalItem;
    private boolean enabled;
    private boolean startedPlayback;
    private String previousClient;
    private String url;

    @Test
    public void reportedVideoSurvivesFocusTransition() throws Exception {
        final Bundle arguments = InstrumentationRegistry.getArguments();
        assumeTrue("Live SABR probe is explicitly opt-in",
                "true".equals(arguments.getString("sabrFocusLossProbe")));
        final String scenario = arguments.getString("scenario", "home");
        assertTrue("Unknown focus scenario", "foreground".equals(scenario)
                || "home".equals(scenario) || "settings".equals(scenario)
                || "browse".equals(scenario));
        url = arguments.getString("url", REPORTED_URL);
        final long observeMs = Long.parseLong(arguments.getString("observeSeconds", "110"))
                * 1000L;
        assertTrue("Observation period must be positive", observeMs > 0);
        previousClient = NewPipe.getYoutubePlayerClient();
        instrumentation.runOnMainSync(() -> {
            assertNull("Use a disposable emulator without an active player",
                    PlayerHolder.getInstance().getPlayer());
            for (final Stage stage : Stage.values()) {
                for (final Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(stage)) {
                    assertFalse("Use an emulator without a pre-existing main activity",
                            activity instanceof MainActivity && stage != Stage.DESTROYED);
                }
            }
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(
                    instrumentation.getTargetContext());
            assertFalse("The reported reproduction uses anonymous YouTube",
                    ServiceList.YouTube.hasTokens());
            final String cookies = preferences.getString(key(R.string.youtube_cookies_key), "");
            assertTrue("Use an emulator without a signed-in YouTube account",
                    cookies == null || cookies.trim().isEmpty());
            enabled = true;
            final int[] keys = {R.string.youtube_player_client_key,
                    R.string.minimize_on_exit_key, R.string.autoplay_key,
                    R.string.auto_queue_key, R.string.enable_watch_history_key,
                    R.string.enable_playback_resume_key, R.string.start_main_player_fullscreen_key,
                    R.string.sponsor_block_enable_key, R.string.dearrow_enable_key,
                    R.string.video_tabs_key, R.string.saved_tabs_key,
                    R.string.stream_info_selected_tab_key};
            for (final int resource : keys) {
                savedPreferences.put(key(resource), preferences.getAll().get(key(resource)));
            }
            assertTrue(preferences.edit()
                    .putString(key(R.string.youtube_player_client_key), "mweb")
                    .putString(key(R.string.minimize_on_exit_key),
                            key(R.string.minimize_on_exit_background_key))
                    .putString(key(R.string.autoplay_key), key(R.string.autoplay_always_key))
                    .putBoolean(key(R.string.auto_queue_key), false)
                    .putBoolean(key(R.string.enable_watch_history_key), false)
                    .putBoolean(key(R.string.enable_playback_resume_key), false)
                    .putBoolean(key(R.string.start_main_player_fullscreen_key), false)
                    .putBoolean(key(R.string.sponsor_block_enable_key), false)
                    .putBoolean(key(R.string.dearrow_enable_key), false)
                    .putStringSet(key(R.string.video_tabs_key),
                            Collections.singleton("description"))
                    .putString(key(R.string.stream_info_selected_tab_key), "DESCRIPTION")
                    .putString(key(R.string.saved_tabs_key), TabsJsonHelper.getJsonToSave(
                            Collections.singletonList(Tab.Type.BLANK.getTab())))
                    .commit());
            NewPipe.setYoutubePlayerClient("mweb");
            instrumentation.getTargetContext().startActivity(new Intent(
                    instrumentation.getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        await(() -> findResumedMain() != null, "Main activity resumed", 30000);
        awaitForegroundTokenProvider();
        SabrSessionHelper.setBenchmarkSessionObserver(sessions::add);
        // Extract after foregrounding so Android grants the app normal network access.
        final StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, url);
        assertTrue("Supplied video must be long enough for uninterrupted observation",
                info.getDuration() * 1000L > observeMs + 20000L);
        instrumentation.runOnMainSync(() -> {
            main = findResumedMain();
            InfoCache.getInstance().putInfo(0, url, info, InfoItem.InfoType.STREAM);
            startedPlayback = true;
            NavigationHelper.playOnMainPlayer(main, new SinglePlayQueue(info));
        });
        await(() -> {
            player = PlayerHolder.getInstance().getPlayer();
            return player != null && player.simpleExoPlayer != null;
        }, "Production player service created", 30000);
        instrumentation.runOnMainSync(() -> {
            engine = player.simpleExoPlayer;
            engine.addListener(new com.google.android.exoplayer2.Player.Listener() {
                @Override
                public void onPlayerError(final PlaybackException error) {
                    playbackError.compareAndSet(null, error);
                }
            });
        });
        await(() -> {
            assertHealthy();
            return engine.isPlaying() && player.getParentActivity() instanceof MainActivity
                    && player.getMenuController().getSelectedVideoStream() != null;
        }, "Reported video starts playing", 150000);
        instrumentation.runOnMainSync(() -> {
            assertEquals(DeliveryMethod.SABR,
                    player.getMenuController().getSelectedVideoStream().getDeliveryMethod());
            assertFalse(player.isAudioOnly());
            originalQueue = player.getPlayQueue();
            originalItem = originalQueue.getItem();
            assertEquals(info.getUrl(), originalItem.getUrl());
            player.changeFullscreen(true);
        });
        await(() -> player.isFullscreen() && engine.isPlaying()
                && hasSelectedTrack(C.TRACK_TYPE_VIDEO), "Fullscreen video playback", 30000);
        monitor(10000, "fullscreen");
        if ("home".equals(scenario)) {
            shell("input keyevent KEYCODE_HOME");
            await(() -> player.isAudioOnly() && !hasSelectedTrack(C.TRACK_TYPE_VIDEO),
                    "Home transition selects audio-only", 15000);
        } else if ("settings".equals(scenario)) {
            shell("am start -a android.settings.SETTINGS");
            await(() -> player.isAudioOnly() && !hasSelectedTrack(C.TRACK_TYPE_VIDEO),
                    "Other app selects audio-only", 15000);
        } else if ("browse".equals(scenario)) {
            final String other = arguments.getString("browseUrl",
                    "https://www.youtube.com/watch?v=G-eNlqqkn1w");
            assertFalse("Browse target must differ from the playing video", url.equals(other));
            instrumentation.runOnMainSync(() -> player.changeFullscreen(false));
            await(() -> !player.isFullscreen()
                    && player.getParentActivity() instanceof MainActivity
                    && player.getParentActivity() == findResumedMain()
                    && !player.getParentActivity().getSupportFragmentManager().isStateSaved(),
                    "Fullscreen exit settles before browsing", 30000);
            instrumentation.runOnMainSync(() -> {
                main = (MainActivity) player.getParentActivity();
                NavigationHelper.openVideoDetailFragment(main, main.getSupportFragmentManager(),
                        0, other, "SABR focus probe browsing target", null, false);
            });
            await(() -> player.isAudioOnly() && player.getRootView().getParent() == null,
                    "Other details detach video while retaining original playback", 30000);
        }
        monitor(observeMs, scenario);
        assertTrue("Production SABR session was observed", !sessions.isEmpty());
        Log.i(TAG, "PASS scenario=" + scenario + " observedAdvanceMs=" + observeMs);
    }

    private void monitor(final long advanceMs, final String phase) {
        final long[] position = {0};
        instrumentation.runOnMainSync(() -> position[0] = engine.getCurrentPosition());
        final long start = position[0];
        long lastPosition = start;
        long lastProgress = SystemClock.elapsedRealtime();
        final long deadline = lastProgress + advanceMs + 45000;
        while (SystemClock.elapsedRealtime() < deadline) {
            instrumentation.runOnMainSync(() -> {
                assertHealthy();
                assertSame("Focus transition retains the original queue",
                        originalQueue, player.getPlayQueue());
                assertSame("Focus transition retains the original playing item",
                        originalItem, player.getPlayQueue().getItem());
                assertTrue("Playback must remain requested", engine.getPlayWhenReady());
                position[0] = engine.getCurrentPosition();
                Log.i(TAG, "phase=" + phase + " positionMs=" + position[0]
                        + " state=" + engine.getPlaybackState() + " playing=" + engine.isPlaying()
                        + " fullscreen=" + player.isFullscreen() + " audioOnly="
                        + player.isAudioOnly() + " audioTrack=" + hasSelectedTrack(C.TRACK_TYPE_AUDIO)
                        + " videoTrack=" + hasSelectedTrack(C.TRACK_TYPE_VIDEO));
            });
            if (position[0] - start >= advanceMs) {
                return;
            }
            if (position[0] >= lastPosition + 200) {
                lastPosition = position[0];
                lastProgress = SystemClock.elapsedRealtime();
            }
            assertTrue("Playback stalled during " + phase + " at " + position[0] + "ms",
                    SystemClock.elapsedRealtime() - lastProgress < 35000);
            SystemClock.sleep(1000);
        }
        throw new AssertionError("Insufficient real playback advance during " + phase
                + ": start=" + start + ", end=" + position[0]);
    }

    private void assertHealthy() {
        assertNull("Recorded playback error", playbackError.get());
        assertNotNull("Player engine must remain alive", player.simpleExoPlayer);
        assertSame("Focus transition must retain the playback engine", engine,
                player.simpleExoPlayer);
        assertNull("Current playback error", engine.getPlayerError());
    }

    private void awaitForegroundTokenProvider() throws Exception {
        final String videoId = ServiceList.YouTube.getStreamLHFactory().getId(url);
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                LocalDomPoTokenProvider.INSTANCE.getPlayerPoToken(videoId);
                return;
            } catch (final Exception error) {
                boolean dnsFailure = false;
                for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                    if (cause instanceof UnknownHostException) {
                        dnsFailure = true;
                        break;
                    }
                }
                if (!dnsFailure || attempt == 3) {
                    throw error;
                }
                // App warmup can cache a DNS failure before its first activity has network access.
                Log.i(TAG, "startupRetry=" + attempt);
                SystemClock.sleep(1000);
            }
        }
    }

    private boolean hasSelectedTrack(final int type) {
        for (final Tracks.Group group : engine.getCurrentTracks().getGroups()) {
            if (group.getType() == type && group.isSelected()) {
                return true;
            }
        }
        return false;
    }

    private MainActivity findResumedMain() {
        for (final Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)) {
            if (activity instanceof MainActivity) {
                return (MainActivity) activity;
            }
        }
        return null;
    }

    private void await(final BooleanSupplier condition, final String message, final long timeout) {
        final long deadline = SystemClock.elapsedRealtime() + timeout;
        final boolean[] reached = {false};
        do {
            instrumentation.runOnMainSync(() -> reached[0] = condition.getAsBoolean());
            if (reached[0]) {
                return;
            }
            SystemClock.sleep(100);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError(message);
    }

    private String key(final int resource) {
        return instrumentation.getTargetContext().getString(resource);
    }

    private void shell(final String command) throws Exception {
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation()
                .executeShellCommand(command);
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor())) {
            final byte[] bytes = new byte[1024];
            while (input.read(bytes) != -1) {
                // Drain the shell command to completion.
            }
        }
    }

    @After
    public void cleanUp() {
        if (!enabled) {
            return;
        }
        SabrSessionHelper.setBenchmarkSessionObserver(null);
        instrumentation.runOnMainSync(() -> {
            if (startedPlayback) {
                PlayerHolder.getInstance().stopService();
                instrumentation.getTargetContext().stopService(new Intent(
                        instrumentation.getTargetContext(), DeviceUtils.getPlayerServiceClass()));
            }
            for (final Stage stage : Stage.values()) {
                for (final Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(stage)) {
                    if (activity instanceof MainActivity) {
                        activity.finish();
                    }
                }
            }
            final SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(
                    instrumentation.getTargetContext()).edit();
            for (final Map.Entry<String, Object> entry : savedPreferences.entrySet()) {
                if (entry.getValue() == null) {
                    editor.remove(entry.getKey());
                } else if (entry.getValue() instanceof Boolean) {
                    editor.putBoolean(entry.getKey(), (Boolean) entry.getValue());
                } else if (entry.getValue() instanceof String) {
                    editor.putString(entry.getKey(), (String) entry.getValue());
                } else {
                    @SuppressWarnings("unchecked") final Set<String> values =
                            (Set<String>) entry.getValue();
                    editor.putStringSet(entry.getKey(), values);
                }
            }
            assertTrue("Restore playback probe preferences", editor.commit());
            NewPipe.setYoutubePlayerClient(previousClient);
            if (url != null) {
                InfoCache.getInstance().removeInfo(0, url, InfoItem.InfoType.STREAM);
            }
        });
        // Keep only numeric protocol fields, never token, cookie, visitor or signed URL values.
        for (int index = 0; index < sessions.size(); index++) {
            for (final String event : sessions.get(index).getDiagnosticTrace().split(" \\| ")) {
                final Matcher matcher = SAFE_DIAGNOSTIC_FIELDS.matcher(event);
                final StringBuilder fields = new StringBuilder();
                while (matcher.find()) {
                    fields.append(' ').append(matcher.group());
                }
                if (fields.length() > 0) {
                    final String eventName = event.split(" ", 2)[0];
                    Log.i(TAG, "session=" + index + " event="
                            + eventName.replaceAll("[^a-z_]", "") + fields);
                }
            }
        }
    }
}
