package org.schabi.newpipe.fragments.list.sponsorblock;

import static org.junit.Assert.*;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import java.util.function.Consumer;
import java.lang.reflect.Field;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction;

@RunWith(AndroidJUnit4.class)
public class SponsorBlockEditorTest {
    @Test public void markingReadsPositionAtTapAndZeroIsExplicit() {
        withEditor(f -> {
            assertFalse(f.view(R.id.jump_start).isEnabled());
            f.position = 0L;
            f.view(R.id.sponsor_block_controls_mark_segment_start).performClick();
            assertTrue(f.text(R.id.sponsor_block_controls_segment_start).contains("0:00:00.000"));
            f.position = 5432L; // Represents a seek while paused; no progress callback.
            f.view(R.id.sponsor_block_controls_mark_segment_end).performClick();
            assertEquals(5432, f.end);
            assertTrue(f.view(R.id.sponsor_block_controls_submit_segment).isEnabled());
        });
    }
    @Test public void missingStartNeverSeeksToEnd() {
        withEditor(f -> {
            f.position = 5000L;
            f.view(R.id.sponsor_block_controls_mark_segment_end).performClick();
            f.view(R.id.jump_start).performClick();
            assertEquals(-1, f.seek);
            f.view(R.id.jump_end).performClick();
            assertEquals(5000, f.seek);
        });
    }
    @Test public void equalBoundariesAreRejectedAndClearReleasesHold() {
        withEditor(f -> {
            f.position = 5000L;
            f.view(R.id.sponsor_block_controls_mark_segment_start).performClick();
            assertTrue(f.held);
            f.view(R.id.sponsor_block_controls_mark_segment_end).performClick();
            assertFalse(f.view(R.id.sponsor_block_controls_submit_segment).isEnabled());
            f.fragment.clearPendingSegment();
            assertFalse(f.held);
            assertFalse(f.view(R.id.jump_start).isEnabled());
        });
    }
    @Test public void hiddenTabReleasesHoldAndCompletedDraftStillHolds() {
        withEditor(f -> {
            f.position = 0L;
            f.view(R.id.sponsor_block_controls_mark_segment_start).performClick();
            f.position = 5000L;
            f.view(R.id.sponsor_block_controls_mark_segment_end).performClick();
            assertTrue(f.held);
            f.fragment.setUserVisibleHint(false);
            assertFalse(f.held);
            f.fragment.setUserVisibleHint(true);
            assertTrue(f.held);
        });
    }
    @Test public void videoStartAndEndShortcutsUseExactDuration() {
        withEditor(f -> {
            f.open(true);
            f.dialog().findViewById(R.id.video_start).performClick();
            f.save();
            assertEquals(0, f.fragment.saveDraft().getInt("start"));
            f.duration = 60123L;
            f.open(false);
            f.dialog().findViewById(R.id.video_end).performClick();
            assertEquals("0:01:00.123", f.input().getText().toString());
            f.save();
            assertEquals(60123, f.end);
        });
    }

    @Test public void unchangedEditorRoundTripsMilliseconds() {
        withEditor(f -> {
            f.position = 5432L;
            f.view(R.id.sponsor_block_controls_mark_segment_start).performClick();
            f.open(true);
            assertEquals("0:00:05.432", f.input().getText().toString());
            f.save();
            assertEquals(5432, f.fragment.saveDraft().getInt("start"));
        });
    }

    @Test public void invalidInputKeepsDialogAndOriginalText() {
        withEditor(f -> {
            f.open(true);
            f.input().setText("1:99.123");
            f.save();
            assertTrue(f.dialog().isShowing());
            assertEquals("1:99.123", f.input().getText().toString());
            assertNotNull(f.input().getError());
            assertFalse(f.fragment.saveDraft().containsKey("start"));
            f.dialog().cancel();
        });
    }

    @Test public void canceledInputReopensWithoutChangingBoundary() {
        withEditor(f -> {
            f.position = 5432L;
            f.view(R.id.sponsor_block_controls_mark_segment_start).performClick();
            f.open(true);
            f.input().setText("9.876");
            f.dialog().cancel();
            assertEquals(5432, f.fragment.saveDraft().getInt("start"));
            f.open(true);
            assertEquals("9.876", f.input().getText().toString());
            assertEquals(5432, f.fragment.saveDraft().getInt("start"));
            f.save();
            assertEquals(9876, f.fragment.saveDraft().getInt("start"));
        });
    }

    @Test public void highlightHidesEndAndSubmitsSinglePoint() {
        withEditor(f -> {
            final Spinner categories = (Spinner) f.view(R.id.segment_category);
            categories.setSelection(4);
            categories.getOnItemSelectedListener().onItemSelected(categories, null, 4, 4);
            assertEquals(View.GONE, f.view(R.id.end_row).getVisibility());
            f.position = 1234L;
            f.view(R.id.sponsor_block_controls_mark_segment_start).performClick();
            assertTrue(f.view(R.id.sponsor_block_controls_submit_segment).isEnabled());
            f.view(R.id.sponsor_block_controls_submit_segment).performClick();
            assertNotNull(f.submitted);
            assertEquals(1234.0, f.submitted.startTime, 0.0);
            // The shared model gives Highlights one second of seekbar display width.
            // POI serialization sends the start as both API boundaries.
            assertEquals(2234.0, f.submitted.endTime, 0.0);
            assertEquals(SponsorBlockAction.POI, f.submitted.action);
        });
    }

    @Test public void unknownDurationDisablesVideoEnd() {
        withEditor(f -> {
            f.duration = null;
            f.info.setDuration(-1);
            f.open(false);
            assertFalse(f.dialog().findViewById(R.id.video_end).isEnabled());
            assertTrue(f.dialog().findViewById(R.id.video_start).isEnabled());
        });
    }

    @Test public void draftSurvivesReplacementBeforeViewCreation() {
        withEditor(f -> {
            f.position = 1234L;
            f.view(R.id.sponsor_block_controls_mark_segment_start).performClick();
            f.position = 5678L;
            f.view(R.id.sponsor_block_controls_mark_segment_end).performClick();
            f.open(true);
            f.input().setText("2.345");
            final Bundle saved = f.fragment.saveDraft();
            final SponsorBlockFragment replacement = new SponsorBlockFragment(f.info);
            replacement.restoreDraft(saved);
            final Bundle offscreen = replacement.saveDraft();
            assertEquals(1234, offscreen.getInt("start"));
            assertEquals(5678, offscreen.getInt("end"));
            assertEquals("2.345", offscreen.getString("editorText"));
            assertEquals(f.fragment.getDraftVersion(), replacement.getDraftVersion());
        });
    }

    private void withEditor(final Consumer<Fixture> test) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                final Fixture f = new Fixture();
                final StreamInfo info = new StreamInfo(0, "test", "https://example.invalid/video", "Test");
                f.info = info;
                info.setDuration(60);
                info.setStreamType(StreamType.VIDEO_STREAM);
                f.fragment = new SponsorBlockFragment(info);
                f.fragment.setListener(f);
                activity.getSupportFragmentManager().beginTransaction().add(f.fragment, "sponsor-test").commitNow();
                f.fragment.setUserVisibleHint(true);
                test.accept(f);
                activity.getSupportFragmentManager().beginTransaction().remove(f.fragment).commitNow();
            });
        }
    }
    private static final class Fixture implements SponsorBlockFragmentListener {
        SponsorBlockFragment fragment;
        StreamInfo info;
        Long duration = 60000L;
        SponsorBlockSegment submitted;
        Long position = 0L;
        long seek = -1;
        int end = -1;
        boolean held;
        void open(boolean start) {
            view(start ? R.id.sponsor_block_controls_segment_start
                    : R.id.sponsor_block_controls_segment_end).performClick();
        }
        AlertDialog dialog() { return (AlertDialog) field("timeDialog"); }
        EditText input() { return (EditText) field("timeInput"); }
        void save() { dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); }
        Object field(String name) {
            try {
                final Field field = SponsorBlockFragment.class.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(fragment);
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        }
        View view(int id) { return fragment.requireView().findViewById(id); }
        String text(int id) { return ((TextView) view(id)).getText().toString(); }
        @Override public Long getSponsorBlockPosition(String url) { return position; }
        @Override public Long getSponsorBlockDuration(String url) { return duration; }
        @Override public void onSponsorBlockEditingChanged(Object owner, String url, boolean editing) { held = editing; }
        @Override public void onSkippingEnabledChanged(boolean enabled) { }
        @Override public void onRequestNewPendingSegment(int serviceId, String url, int start, int endTime) { end = endTime; }
        @Override public void onRequestClearPendingSegment(int serviceId, String url) { }
        @Override public void onRequestSubmitPendingSegment(int serviceId, String url, SponsorBlockSegment segment) { submitted = segment; }
        @Override public void onSeekToRequested(long time) { seek = time; }
    }
}
