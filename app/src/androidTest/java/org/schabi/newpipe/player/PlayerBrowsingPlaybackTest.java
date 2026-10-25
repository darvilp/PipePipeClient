package org.schabi.newpipe.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.support.v4.media.session.MediaSessionCompat;
import android.widget.TextView;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.exoplayer2.ExoPlayer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.holder.CommentsMiniInfoItemHolder;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.mediaitem.ExoMediaItems;
import org.schabi.newpipe.player.mediaitem.PlayerMediaItem;
import org.schabi.newpipe.player.mediaitem.StreamInfoResolver;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver.SourceType;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsJsonHelper;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.CommentTextOnTouchListener;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.external_communication.InternalUrlsHandler;
import org.schabi.newpipe.util.external_communication.TextLinkifier;

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

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.SingleSubject;

/** Real main-player navigation with cached metadata and generated local media. */
@RunWith(AndroidJUnit4.class)
public class PlayerBrowsingPlaybackTest {
    private static final String CURRENT_URL = "https://www.youtube.com/watch?v=brwtest0001";
    private static final String OTHER_URL = "https://www.youtube.com/watch?v=brwtest0002";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Map<String, Object> savedPreferences = new HashMap<>();
    private final Map<String, StreamInfo> fixtureMetadata = new HashMap<>();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private MainActivity main;
    private Player player;
    private boolean startedPlayback;

    @Before
    public void isolatePlaybackPreferences() {
        instrumentation.runOnMainSync(() -> {
            assertNull("Requires no pre-existing player service", PlayerHolder.getInstance()
                    .getPlayer());
            for (final Stage stage : Stage.values()) {
                for (final Activity activity : ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(stage)) {
                    assertFalse("Requires no pre-existing MainActivity",
                            activity instanceof MainActivity && stage != Stage.DESTROYED);
                }
            }
            final Context context = instrumentation.getTargetContext();
            final SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(context);
            for (final int key : new int[]{R.string.sponsor_block_enable_key,
                    R.string.dearrow_enable_key, R.string.auto_queue_key,
                    R.string.enable_watch_history_key, R.string.enable_playback_resume_key,
                    R.string.video_tabs_key, R.string.stream_info_selected_tab_key,
                    R.string.always_start_from_beginning_key, R.string.autoplay_key,
                    R.string.start_main_player_fullscreen_key, R.string.saved_tabs_key}) {
                savedPreferences.put(context.getString(key), preferences.getAll()
                        .get(context.getString(key)));
            }
            assertTrue(preferences.edit()
                    .putBoolean(context.getString(R.string.sponsor_block_enable_key), false)
                    .putBoolean(context.getString(R.string.dearrow_enable_key), false)
                    .putBoolean(context.getString(R.string.auto_queue_key), false)
                    .putBoolean(context.getString(R.string.enable_watch_history_key), false)
                    .putBoolean(context.getString(R.string.enable_playback_resume_key), false)
                    .putStringSet(context.getString(R.string.video_tabs_key),
                            Collections.singleton("description"))
                    .putString(context.getString(R.string.stream_info_selected_tab_key),
                            "DESCRIPTION")
                    .putString(context.getString(R.string.autoplay_key),
                            context.getString(R.string.autoplay_always_key))
                    // Offline kiosk failures clear the shared metadata cache.
                    .putString(context.getString(R.string.saved_tabs_key),
                            TabsJsonHelper.getJsonToSave(Collections.singletonList(
                                    Tab.Type.BLANK.getTab())))
                    .putBoolean(context.getString(R.string.start_main_player_fullscreen_key), false)
                    .commit());
        });
    }

    @Test
    public void sameVideoUrlTimestampSeeksPreparedSingleItem() throws Exception {
        checkUrlTimestamp(5);
    }

    @Test
    public void sameVideoZeroUrlTimestampSeeksPreparedSingleItem() throws Exception {
        checkUrlTimestamp(0);
    }

    @Test
    public void playNowAndKeepQueuePreservesRequestedTimestamp() throws Exception {
        start(false);
        final PlayQueue queue = player.getPlayQueue();
        final ExoPlayer engine = player.simpleExoPlayer;
        instrumentation.runOnMainSync(() -> {
            final StreamInfo target = fixtureMetadata.get(OTHER_URL);
            target.setStartPosition(5);
            cacheFixtureMetadata();
            final PlayQueue request = new SinglePlayQueue(target);
            assertEquals(5000, request.getRecoveryPosition(request.getItem()));
            player.handleIntent(NavigationHelper.getPlayerEnqueueNextAndPlayIntent(
                    main, DeviceUtils.getPlayerServiceClass(), request));
        });
        await(() -> engine.getPlaybackState() == ExoPlayer.STATE_READY
                && player.getCurrentStreamInfo().map(info -> OTHER_URL.equals(info.getUrl()))
                        .orElse(false), "Selected queued video must prepare");
        instrumentation.runOnMainSync(() -> {
            player.pause();
            assertSame(queue, player.getPlayQueue());
            assertSame(engine, player.simpleExoPlayer);
            assertEquals(2, queue.size());
            assertEquals(1, queue.getIndex());
            assertEquals("Play now must preserve the requested timestamp", 5000,
                    player.getCurrentPosition(), 1000);
        });
    }

    @Test
    public void descriptionTimestampTargetsBrowsedVideo() throws Exception {
        browseTimestampedVideo();
        await(() -> descriptionTimestampView() != null
                && descriptionTimestampView().getText() instanceof Spanned
                && ((Spanned) descriptionTimestampView().getText()).getSpans(0,
                        descriptionTimestampView().length(), ClickableSpan.class).length > 0,
                "Browsed description must contain its real timestamp link");
        instrumentation.runOnMainSync(() -> {
            final TextView text = descriptionTimestampView();
            final Spanned spans = (Spanned) text.getText();
            spans.getSpans(0, spans.length(), ClickableSpan.class)[0].onClick(text);
        });
        assertTimestampTargetsBrowsedVideo("Description timestamps must target displayed video");
    }

    @Test
    public void currentVideoDescriptionTimestampKeepsExistingQueue() throws Exception {
        start(false);
        final PlayQueue queue = player.getPlayQueue();
        final TextView[] text = new TextView[1];
        instrumentation.runOnMainSync(() -> {
            queue.append(new SinglePlayQueue(fixtureMetadata.get(OTHER_URL)).getItem());
            text[0] = new TextView(main);
            TextLinkifier.createLinksFromPlainText(text[0], "0:12 jump",
                    fixtureMetadata.get(CURRENT_URL), disposables);
        });
        await(() -> text[0].getText() instanceof Spanned && ((Spanned) text[0].getText())
                .getSpans(0, text[0].length(), ClickableSpan.class).length > 0,
                "Current-video timestamp must become clickable");
        instrumentation.runOnMainSync(() -> {
            final Spanned spans = (Spanned) text[0].getText();
            spans.getSpans(0, spans.length(), ClickableSpan.class)[0].onClick(text[0]);
        });
        await(() -> Math.abs(player.getCurrentPosition() - 12000) < 1000,
                "Current-video timestamp must seek without replacing its queue");
        instrumentation.runOnMainSync(() -> {
            player.pause();
            assertSame(queue, player.getPlayQueue());
            assertEquals(2, queue.size());
            assertEquals(CURRENT_URL, queue.getItem().getUrl());
        });
    }

    @Test
    public void commentTimestampTargetsBrowsedVideo() throws Exception {
        browseTimestampedVideo();
        final TextView[] text = new TextView[1];
        instrumentation.runOnMainSync(() -> {
            final CommentsInfoItem comment = new CommentsInfoItem(0, OTHER_URL, "Fixture comment");
            comment.setUploaderName("Fixture author");
            comment.setCommentText(new Description("0:12 jump", Description.PLAIN_TEXT));
            final CommentsMiniInfoItemHolder holder = new CommentsMiniInfoItemHolder(
                    new InfoItemBuilder(main), new FrameLayout(main));
            holder.updateFromItem(comment, null);
            text[0] = holder.itemView.findViewById(R.id.itemCommentContentView);
        });
        await(() -> text[0].getUrls().length > 0, "Comment holder must generate its timestamp link");
        instrumentation.runOnMainSync(() -> {
            final TextView view = text[0];
            view.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.AT_MOST));
            view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
            final Spanned spans = (Spanned) view.getText();
            final URLSpan link = view.getUrls()[0];
            final int offset = spans.getSpanStart(link);
            final int line = view.getLayout().getLineForOffset(offset);
            final float x = view.getTotalPaddingLeft()
                    + view.getLayout().getPrimaryHorizontal(offset) + 2;
            final float y = view.getTotalPaddingTop() + view.getLayout().getLineTop(line) + 2;
            final long now = SystemClock.uptimeMillis();
            final MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
            final MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0);
            try {
                assertTrue(CommentTextOnTouchListener.INSTANCE.onTouch(view, down));
                assertTrue(CommentTextOnTouchListener.INSTANCE.onTouch(view, up));
            } finally {
                down.recycle();
                up.recycle();
            }
        });
        assertTimestampTargetsBrowsedVideo("Comment timestamps must target displayed video");
    }

    private void browseTimestampedVideo() throws Exception {
        start(false);
        instrumentation.runOnMainSync(() -> {
            assertTrue(PreferenceManager.getDefaultSharedPreferences(main).edit()
                    .putString(main.getString(R.string.autoplay_key),
                            main.getString(R.string.autoplay_never_key)).commit());
            fixtureMetadata.get(OTHER_URL).setDescription(
                    new Description("0:12 jump", Description.PLAIN_TEXT));
            cacheFixtureMetadata();
            NavigationHelper.openVideoDetailFragment(main, main.getSupportFragmentManager(),
                    0, OTHER_URL, "Other browsing fixture", null, false);
        });
        await(() -> detailsShow("Other browsing fixture") && player.isAudioOnly()
                && player.simpleExoPlayer.getPlaybackState() == ExoPlayer.STATE_READY,
                "Browse another video while the original keeps playing");
    }

    private TextView descriptionTimestampView() {
        return main.findViewById(R.id.detail_description_view);
    }

    private void assertTimestampTargetsBrowsedVideo(final String message) {
        await(() -> player.simpleExoPlayer.getPlaybackState() == ExoPlayer.STATE_READY
                && Math.abs(player.getCurrentPosition() - 12000) < 1000,
                "Timestamp click must prepare playback near twelve seconds");
        instrumentation.runOnMainSync(() -> {
            player.pause();
            assertEquals(message, OTHER_URL, player.getPlayQueue().getItem().getUrl());
            assertEquals(12000, player.getCurrentPosition(), 1000);
        });
    }

    private void checkUrlTimestamp(final int seconds) throws Exception {
        start(false);
        final PlayQueue queue = player.getPlayQueue();
        final ExoPlayer engine = player.simpleExoPlayer;
        instrumentation.runOnMainSync(() -> {
            assertEquals(1, queue.size());
            player.pause();
            engine.seekTo(18000);
        });
        await(() -> engine.getPlaybackState() == ExoPlayer.STATE_READY
                && Math.abs(player.getCurrentPosition() - 18000) < 500,
                "Prepared playback at a different position");
        instrumentation.runOnMainSync(() -> assertTrue(InternalUrlsHandler
                .handleUrlDescriptionTimestamp(disposables, main, "internal://timestamp/12")));
        await(() -> engine.getPlaybackState() == ExoPlayer.STATE_READY
                && Math.abs(player.getCurrentPosition() - 12000) < 500,
                "Internal timestamp control seeks the real player");

        final boolean[] timestampSeek = {false};
        instrumentation.runOnMainSync(() -> {
            player.pause();
            engine.addListener(new com.google.android.exoplayer2.Player.Listener() {
                @Override
                public void onPositionDiscontinuity(
                        final com.google.android.exoplayer2.Player.PositionInfo previous,
                        final com.google.android.exoplayer2.Player.PositionInfo next,
                        final int reason) {
                    if (reason == com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK
                            && next.positionMs == seconds * 1000L) {
                        timestampSeek[0] = true;
                    }
                }
            });
            assertTrue("Same-item queue equality alone cannot identify a timestamp request",
                    queue.equals(new SinglePlayQueue(player.getCurrentStreamInfo().get(),
                            seconds * 1000L)));
            cacheFixtureMetadata();
            assertTrue(InternalUrlsHandler.handleUrlDescriptionTimestamp(disposables,
                    main, CURRENT_URL + "&t=" + seconds));
        });
        await(() -> timestampSeek[0] && engine.getPlaybackState() == ExoPlayer.STATE_READY,
                "Explicit same-video URL timestamp must seek to " + seconds + " seconds");
        instrumentation.runOnMainSync(() -> {
            assertSame(queue, player.getPlayQueue());
            assertSame(engine, player.simpleExoPlayer);
            assertTrue(player.getPlayWhenReady());
            assertEquals(seconds * 1000L, player.getCurrentPosition(), 1000);
            assertSame(main.findViewById(R.id.player_placeholder),
                    player.getRootView().getParent());
        });
    }

    @Test
    public void browsingAndReturningKeepPositionDespiteAlwaysStartFromBeginning() throws Exception {
        start(true);
        final PlayQueue queue = player.getPlayQueue();
        final ExoPlayer engine = player.simpleExoPlayer;
        final Object session = player.getMediaSessionManager();
        final MediaSessionCompat.Token token = player.getMediaSessionManager().getSessionToken();
        final Object videoManager = sourceManager();
        instrumentation.runOnMainSync(() -> {
            assertTrue(PreferenceManager.getDefaultSharedPreferences(main).edit()
                    .putString(main.getString(R.string.autoplay_key),
                            main.getString(R.string.autoplay_never_key)).commit());
            final VideoStream selected = player.getMenuController().getSelectedVideoStream();
            assertNotNull(selected);
            assertFalse(selected.isVideoOnly());
            assertEquals(DeliveryMethod.PROGRESSIVE_HTTP, selected.getDeliveryMethod());
            assertEquals(MediaFormat.MPEG_4, selected.getFormat());
            assertEquals(SourceType.VIDEO_WITH_AUDIO_OR_AUDIO_ONLY,
                    player.getSourceResolver().getStreamSourceType().get());
            assertEquals(1, player.getCurrentStreamInfo().get().getAudioStreams().size());
            assertFalse(player.isAudioOnly());
            engine.seekTo(7000);
        });
        await(() -> engine.getPlaybackState() == ExoPlayer.STATE_READY
                && player.getCurrentPosition() >= 7000 && player.getCurrentPosition() < 8000,
                "Real video playback prepared near seven seconds");
        final long[] before = new long[2];
        instrumentation.runOnMainSync(() -> {
            before[0] = player.getCurrentPosition();
            before[1] = SystemClock.elapsedRealtime();
            cacheFixtureMetadata();
            NavigationHelper.openVideoDetailFragment(main, main.getSupportFragmentManager(),
                    0, OTHER_URL, "Other browsing fixture", null, false);
            assertNotSame("Embedded audio plus a separate M4A must reload for browsing",
                    videoManager, sourceManager());
        });
        await(() -> detailsShow("Other browsing fixture") && player.isAudioOnly()
                && sourceManager() != videoManager
                && engine.getPlaybackState() == ExoPlayer.STATE_READY,
                "Other details displayed with the real audio-only source prepared");
        instrumentation.runOnMainSync(() -> {
            assertPlaybackUnchanged(queue, engine, session, token, before);
            assertNull("Browsing details detaches the active video view",
                    player.getRootView().getParent());
        });

        final Object audioManager = sourceManager();
        instrumentation.runOnMainSync(() -> {
            before[0] = player.getCurrentPosition();
            before[1] = SystemClock.elapsedRealtime();
            cacheFixtureMetadata();
            NavigationHelper.openVideoDetailFragment(main, main.getSupportFragmentManager(),
                    0, CURRENT_URL, "Current browsing fixture", null, false);
        });
        await(() -> detailsShow("Current browsing fixture") && !player.isAudioOnly()
                && sourceManager() != audioManager
                && engine.getPlaybackState() == ExoPlayer.STATE_READY
                && player.getRootView().getParent() == main.findViewById(R.id.player_placeholder),
                "Returning to the current details prepares and reattaches video");
        instrumentation.runOnMainSync(() ->
                assertPlaybackUnchanged(queue, engine, session, token, before));
    }

    @Test
    public void timestampDuringReturnSourceReloadWinsOverCapturedRecovery() throws Exception {
        checkTimestampDuringReturnSourceReload(false);
    }

    @Test
    public void plainTimestampDuringReturnSourceReloadWinsOverCapturedRecovery() throws Exception {
        checkTimestampDuringReturnSourceReload(true);
    }

    private void checkTimestampDuringReturnSourceReload(final boolean plainTimestamp)
            throws Exception {
        start(false);
        final PlayQueue queue = player.getPlayQueue();
        final ExoPlayer engine = player.simpleExoPlayer;
        instrumentation.runOnMainSync(() -> {
            assertEquals(1, queue.size());
            assertTrue(player.shouldSeek());
            assertTrue(PreferenceManager.getDefaultSharedPreferences(main).edit()
                    .putString(main.getString(R.string.autoplay_key),
                            main.getString(R.string.autoplay_never_key)).commit());
            player.pause();
            engine.seekTo(7000);
        });
        await(() -> engine.getPlaybackState() == ExoPlayer.STATE_READY
                && Math.abs(engine.getCurrentPosition() - 7000) < 100,
                "Paused real video prepared at seven seconds");
        instrumentation.runOnMainSync(() -> {
            cacheFixtureMetadata();
            NavigationHelper.openVideoDetailFragment(main, main.getSupportFragmentManager(),
                    0, OTHER_URL, "Other browsing fixture", null, false);
        });
        await(() -> detailsShow("Other browsing fixture") && player.isAudioOnly()
                && engine.getPlaybackState() == ExoPlayer.STATE_READY,
                "Browsing prepares the active item's real audio-only source");

        final Object audioManager = sourceManager();
        final Object[] returnManager = new Object[1];
        final SingleSubject<StreamInfo> metadataGate = SingleSubject.create();
        final boolean[] timestampSeek = {false};
        final Bundle recoveryEvidence = new Bundle();
        instrumentation.runOnMainSync(() -> {
            assertSame(queue, player.getPlayQueue());
            assertEquals(7000, engine.getCurrentPosition(), 100);
            assertFalse(ExoMediaItems.fromMediaItem(engine.getCurrentMediaItem())
                    .flatMap(PlayerMediaItem::getMaybeQuality).isPresent());
            // Start the fragment's return-source action here to gate metadata before queued loads.
            player.setMainPlayerDetailsBrowsing(false);
            returnManager[0] = sourceManager();
            assertNotSame(audioManager, returnManager[0]);
            final long recovery = queue.getRecoveryPosition(queue.getItem());
            assertEquals(7000, recovery, 100);
            recoveryEvidence.putLong("capturedRecoveryMs", recovery);
            instrumentation.sendStatus(0, recoveryEvidence);
            try {
                final Field resolver = returnManager[0].getClass()
                        .getDeclaredField("streamInfoResolver");
                resolver.setAccessible(true);
                resolver.set(returnManager[0], (StreamInfoResolver) item -> {
                    assertEquals(CURRENT_URL, item.getUrl());
                    return metadataGate;
                });
            } catch (final ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
            engine.addListener(new com.google.android.exoplayer2.Player.Listener() {
                @Override
                public void onPositionDiscontinuity(
                        final com.google.android.exoplayer2.Player.PositionInfo previous,
                        final com.google.android.exoplayer2.Player.PositionInfo next,
                        final int reason) {
                    if (reason == com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK
                            && next.positionMs == 5000) {
                        timestampSeek[0] = true;
                    }
                }
            });
            assertTrue(PreferenceManager.getDefaultSharedPreferences(main).edit()
                    .putString(main.getString(R.string.autoplay_key),
                            main.getString(R.string.autoplay_always_key)).commit());
        });
        await(metadataGate::hasObservers, "Return source requests gated fixture metadata");
        if (plainTimestamp) {
            instrumentation.runOnMainSync(() -> {
                cacheFixtureMetadata();
                NavigationHelper.openVideoDetailFragment(main, main.getSupportFragmentManager(),
                        0, CURRENT_URL, "Current browsing fixture", null, false);
            });
            await(() -> detailsShow("Current browsing fixture"),
                    "Current details are available while the return source is pending");
        }
        instrumentation.runOnMainSync(() -> {
            cacheFixtureMetadata();
            if (plainTimestamp) {
                assertTrue(InternalUrlsHandler.handleUrlCommentsTimestamp(disposables,
                        main, CURRENT_URL + "#timestamp=5"));
            } else {
                assertTrue(InternalUrlsHandler.handleUrlDescriptionTimestamp(disposables,
                        main, CURRENT_URL + "&t=5"));
            }
        });
        await(() -> timestampSeek[0] && Math.abs(engine.getCurrentPosition() - 5000) < 500,
                "Timestamp must seek to five seconds while return metadata is held");
        instrumentation.runOnMainSync(() -> {
            assertFalse(metadataGate.hasValue());
            assertSame(returnManager[0], sourceManager());
            assertEquals("Plain timestamps preserve pause; URL playback starts playing",
                    !plainTimestamp, engine.getPlayWhenReady());
            recoveryEvidence.putLong("afterTimestampRecoveryMs",
                    queue.getRecoveryPosition(queue.getItem()));
            instrumentation.sendStatus(0, recoveryEvidence);
            metadataGate.onSuccess(fixtureMetadata.get(CURRENT_URL));
        });
        await(() -> engine.getPlaybackState() == ExoPlayer.STATE_READY
                && detailsShow("Current browsing fixture")
                && ExoMediaItems.fromMediaItem(engine.getCurrentMediaItem())
                        .flatMap(PlayerMediaItem::getMaybeQuality).isPresent(),
                "The gated return video source prepares through the real source manager");
        instrumentation.runOnMainSync(() -> {
            assertSame(queue, player.getPlayQueue());
            assertSame(engine, player.simpleExoPlayer);
            assertEquals("Timestamp route must retain its play intent after source preparation",
                    !plainTimestamp, engine.getPlayWhenReady());
            recoveryEvidence.putLong("preparedRecoveryMs",
                    queue.getRecoveryPosition(queue.getItem()));
            recoveryEvidence.putLong("preparedPositionMs", engine.getCurrentPosition());
            instrumentation.sendStatus(0, recoveryEvidence);
            assertEquals("Explicit timestamp must survive delayed return-source synchronization: "
                            + recoveryEvidence,
                    5000, engine.getCurrentPosition(), 750);
        });
    }

    private void assertPlaybackUnchanged(final PlayQueue queue, final ExoPlayer engine,
                                         final Object session, final MediaSessionCompat.Token token,
                                         final long[] before) {
        assertSame(player, PlayerHolder.getInstance().getPlayer());
        assertSame(queue, player.getPlayQueue());
        assertEquals(CURRENT_URL, queue.getItem().getUrl());
        assertEquals(0, queue.getIndex());
        assertSame(engine, player.simpleExoPlayer);
        assertSame(session, player.getMediaSessionManager());
        assertEquals(token, player.getMediaSessionManager().getSessionToken());
        assertEquals(PlayerService.PlayerType.VIDEO, player.getPlayerType());
        assertTrue("Browsing must preserve play intent", player.getPlayWhenReady());
        final long position = player.getCurrentPosition();
        assertTrue("Source reload must preserve nonzero progress: " + position,
                position >= before[0] - 1000);
        assertTrue("Source reload must not jump forward: " + position,
                position <= before[0] + (SystemClock.elapsedRealtime() - before[1])
                        * engine.getPlaybackParameters().speed + 1000);
    }

    private void start(final boolean alwaysStartFromBeginning) throws Exception {
        final String media = Uri.fromFile(copyAsset("player-mode-fixture.mp4")).toString();
        final String audio = Uri.fromFile(copyAsset("player-mode-fixture.m4a")).toString();
        instrumentation.runOnMainSync(() -> {
            final Context context = instrumentation.getTargetContext();
            assertTrue(PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putBoolean(context.getString(R.string.always_start_from_beginning_key),
                            alwaysStartFromBeginning).commit());
            for (final String url : Arrays.asList(CURRENT_URL, OTHER_URL)) {
                final StreamInfo info = new StreamInfo(0, url, url, url.equals(CURRENT_URL)
                        ? "Current browsing fixture" : "Other browsing fixture");
                info.setStreamType(StreamType.VIDEO_STREAM);
                info.setDuration(30);
                info.setDescription(Description.EMPTY_DESCRIPTION);
                info.setUploaderName("Synthetic fixture");
                info.setVideoStreams(Collections.singletonList(new VideoStream.Builder()
                        .setId("browsing-video").setContent(media, true)
                        .setMediaFormat(MediaFormat.MPEG_4).setResolution("144p")
                        .setIsVideoOnly(false).build()));
                info.setAudioStreams(Collections.singletonList(new AudioStream.Builder()
                        .setId("browsing-audio").setContent(audio, true)
                        .setMediaFormat(MediaFormat.M4A).setAverageBitrate(96).build()));
                fixtureMetadata.put(url, info);
            }
        });
        main = (MainActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            startedPlayback = true;
            cacheFixtureMetadata();
            NavigationHelper.playOnMainPlayer(main,
                    new SinglePlayQueue(fixtureMetadata.get(CURRENT_URL)));
        });
        await(() -> {
            player = PlayerHolder.getInstance().getPlayer();
            return player != null && player.getPlayQueue() != null
                    && player.simpleExoPlayer.getPlaybackState() == ExoPlayer.STATE_READY
                    && player.getParentActivity() == main
                    && detailsShow("Current browsing fixture");
        }, "Cached fixture prepared in the real main player");
        instrumentation.runOnMainSync(() -> {
            assertTrue(player.getPlayWhenReady());
            assertSame(main.findViewById(R.id.player_placeholder),
                    player.getRootView().getParent());
            player.simpleExoPlayer.setVolume(0f);
        });
    }

    private void cacheFixtureMetadata() {
        for (final Map.Entry<String, StreamInfo> entry : fixtureMetadata.entrySet()) {
            InfoCache.getInstance().putInfo(0, entry.getKey(), entry.getValue(),
                    InfoItem.InfoType.STREAM);
            assertSame("Fixture metadata must survive through navigation and source reload",
                    entry.getValue(), InfoCache.getInstance().getFromKey(0, entry.getKey(),
                            InfoItem.InfoType.STREAM));
        }
    }

    private boolean detailsShow(final String title) {
        final TextView displayedTitle = main.findViewById(R.id.detail_video_title_view);
        return main.getSupportFragmentManager().findFragmentById(R.id.fragment_player_holder)
                instanceof VideoDetailFragment && displayedTitle != null
                && title.contentEquals(displayedTitle.getText());
    }

    private Object sourceManager() {
        try {
            final Field manager = Player.class.getDeclaredField("playQueueManager");
            manager.setAccessible(true);
            return manager.get(player);
        } catch (final ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private File copyAsset(final String name) throws Exception {
        final File file = new File(instrumentation.getTargetContext().getCacheDir(), name);
        try (InputStream source = instrumentation.getContext().getAssets().open(name);
             FileOutputStream target = new FileOutputStream(file)) {
            final byte[] buffer = new byte[8192];
            int count;
            while ((count = source.read(buffer)) != -1) {
                target.write(buffer, 0, count);
            }
        }
        return file;
    }

    private void await(final BooleanSupplier condition, final String message) {
        final long deadline = SystemClock.elapsedRealtime() + 15000;
        final boolean[] result = {false};
        do {
            instrumentation.runOnMainSync(() -> result[0] = condition.getAsBoolean());
            if (result[0]) {
                return;
            }
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        final String[] state = new String[1];
        instrumentation.runOnMainSync(() -> {
            final TextView title = main == null ? null
                    : main.findViewById(R.id.detail_video_title_view);
            final PlayQueue queue = player == null ? null : player.getPlayQueue();
            final ExoPlayer engine = player == null ? null : player.simpleExoPlayer;
            state[0] = "title=" + (title == null ? null : title.getText())
                    + ", queueUrl=" + (queue == null || queue.getItem() == null
                            ? null : queue.getItem().getUrl())
                    + ", audioOnly=" + (player == null ? null : player.isAudioOnly())
                    + ", engineState=" + (engine == null ? null : engine.getPlaybackState())
                    + ", position=" + (engine == null ? null : engine.getCurrentPosition())
                    + ", playWhenReady=" + (engine == null ? null : engine.getPlayWhenReady())
                    + ", manager=" + (player == null ? null : sourceManager())
                    + ", cachedCurrent=" + (InfoCache.getInstance().getFromKey(0,
                            CURRENT_URL, InfoItem.InfoType.STREAM) != null)
                    + ", cachedOther=" + (InfoCache.getInstance().getFromKey(0,
                            OTHER_URL, InfoItem.InfoType.STREAM) != null);
        });
        throw new AssertionError(message + " [" + state[0] + "]");
    }

    @After
    public void cleanUp() {
        instrumentation.runOnMainSync(() -> {
            disposables.clear();
            if (startedPlayback) {
                final Player active = PlayerHolder.getInstance().getPlayer();
                if (active != null) {
                    active.service.stopService();
                }
                PlayerHolder.getInstance().stopService();
                main.stopService(new Intent(main, DeviceUtils.getPlayerServiceClass()));
            }
            if (main != null) {
                main.finish();
            }
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
            assertTrue(editor.commit());
            for (final String url : Arrays.asList(CURRENT_URL, OTHER_URL)) {
                InfoCache.getInstance().removeInfo(0, url, InfoItem.InfoType.STREAM);
            }
        });
        instrumentation.waitForIdleSync();
    }
}
