package org.schabi.newpipe.fragments.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import androidx.appcompat.app.AlertDialog;
import android.widget.EditText;
import org.schabi.newpipe.R;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.IOException;
import org.schabi.newpipe.extractor.downloader.Response;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.SingleSubject;
import io.reactivex.rxjava3.disposables.Disposable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.fragments.list.sponsorblock.SponsorBlockFragment;

import java.lang.reflect.Field;
import java.util.function.Consumer;

/** Exercises completion separately from transport. No submission requests are made. */
@RunWith(AndroidJUnit4.class)
public class SponsorBlockSubmissionTest {
    @Test
    public void delayedCompletionDoesNotModifyTheVideoNavigatedTo() {
        withFixture(f -> {
            final StreamInfo next = info("next");
            next.addSponsorBlockSegment(segment("TEMP"));
            final SponsorBlockFragment nextEditor = editor(next, draft(next, 8));
            f.show(next, nextEditor);

            assertFalse(f.complete());
            assertTrue(has(f.original, "submitted"));
            assertFalse(has(f.original, "TEMP"));
            assertTrue(has(next, "TEMP"));
            assertFalse(has(next, "submitted"));
            assertEquals(8, nextEditor.getDraftVersion());
        });
    }

    @Test
    public void sameUrlFromAnotherServiceIsNotTheSubmittedVideo() {
        withFixture(f -> {
            final StreamInfo otherService = new StreamInfo(1, "original",
                    f.original.getUrl(), "Other service");
            otherService.addSponsorBlockSegment(segment("TEMP"));
            final SponsorBlockFragment otherEditor = editor(otherService, draft(otherService, 7));
            f.show(otherService, otherEditor);

            assertFalse(f.complete());
            assertTrue(otherEditor.saveDraft().containsKey("start"));
            assertTrue(has(otherService, "TEMP"));
            assertFalse(has(otherService, "submitted"));
        });
    }

    @Test
    public void completionPreservesAnEditedDraft() {
        withFixture(f -> {
            f.submittedEditor.restoreDraft(draft(f.original, 8));
            assertFalse(f.complete());
            assertTrue(has(f.original, "TEMP"));
            assertTrue(has(f.original, "submitted"));
            assertEquals(8, f.submittedEditor.getDraftVersion());
        });
    }

    @Test
    public void completionClearsAnUnchangedReplacementEditor() {
        withFixture(f -> {
            final StreamInfo replacementInfo = info("original");
            replacementInfo.addSponsorBlockSegment(segment("TEMP"));
            final SponsorBlockFragment replacement = editor(replacementInfo,
                    draft(replacementInfo, 7));
            f.show(replacementInfo, replacement);

            assertTrue(f.complete());
            assertFalse(replacement.saveDraft().containsKey("start"));
            assertFalse(replacement.saveDraft().containsKey("end"));
            assertFalse(has(f.original, "TEMP"));
            assertFalse(has(replacementInfo, "TEMP"));
            assertTrue(has(replacementInfo, "submitted"));
        });
    }

    @Test
    public void completionPreservesAnEditedReplacementEditor() {
        withFixture(f -> {
            final StreamInfo replacementInfo = info("original");
            replacementInfo.addSponsorBlockSegment(segment("TEMP"));
            final SponsorBlockFragment replacement = editor(replacementInfo,
                    draft(replacementInfo, 8));
            f.show(replacementInfo, replacement);

            assertFalse(f.complete());
            assertEquals(1234, replacement.saveDraft().getInt("start"));
            assertEquals(8, replacement.getDraftVersion());
            assertTrue(has(replacementInfo, "TEMP"));
            assertTrue(has(replacementInfo, "submitted"));
        });
    }

    @Test
    public void completionClearsUnchangedSavedDraftBeforeItsTabIsCreated() {
        withFixture(f -> {
            f.tabs.clearAllItems();
            set(f.parent, "sponsorBlockDraft", draft(f.original, 7));
            assertFalse(f.complete()); // No visible editor needs a success dialog.
            assertNull(get(f.parent, "sponsorBlockDraft"));
            assertFalse(has(f.original, "TEMP"));
            assertTrue(has(f.original, "submitted"));
        });
    }

    @Test
    public void completionPreservesEditedSavedDraftBeforeItsTabIsCreated() {
        withFixture(f -> {
            f.tabs.clearAllItems();
            set(f.parent, "sponsorBlockDraft", draft(f.original, 8));
            assertFalse(f.complete());
            final Bundle saved = (Bundle) get(f.parent, "sponsorBlockDraft");
            assertEquals(8, saved.getLong("version"));
            assertEquals("1:02.", saved.getString("editorText"));
            assertTrue(has(f.original, "TEMP"));
        });
    }

    @Test
    public void completionDoesNotClearNewDraftWithSameRevisionAfterReturningToVideo() {
        withFixture(f -> {
            final Bundle newDraft = draft(f.original, 7);
            newDraft.putString("draftId", "new-draft");
            final SponsorBlockFragment replacement = editor(f.original, newDraft);
            f.show(f.original, replacement);

            assertFalse(f.complete());
            assertEquals(7, replacement.getDraftVersion());
            assertEquals("new-draft", replacement.getDraftId());
            assertTrue(replacement.saveDraft().containsKey("start"));
            assertTrue(has(f.original, "TEMP"));
        });
    }

    @Test
    public void completionRemovesTemporaryMarkerFromReplacementInfoWithoutActiveTab() {
        withFixture(f -> {
            final StreamInfo replacementInfo = info("original");
            replacementInfo.addSponsorBlockSegment(segment("TEMP"));
            set(f.parent, "currentInfo", replacementInfo);
            f.tabs.clearAllItems();
            set(f.parent, "sponsorBlockDraft", draft(replacementInfo, 7));

            assertFalse(f.complete());
            assertFalse(has(replacementInfo, "TEMP"));
            assertTrue(has(replacementInfo, "submitted"));
        });
    }

    @Test
    public void parentStateHooksPreserveDraftOfTabWhoseViewWasNeverCreated() {
        withFixture(f -> {
            final Bundle saved = new Bundle();
            f.parent.onSaveInstanceState(saved);
            final VideoDetailFragment restoredParent = new VideoDetailFragment();
            restoredParent.onRestoreInstanceState(saved);
            final Bundle secondSave = new Bundle();
            restoredParent.onSaveInstanceState(secondSave);
            final Bundle restoredDraft = secondSave.getBundle("sponsorBlockDraft");
            assertEquals(f.original.getUrl(), restoredDraft.getString("videoUrl"));
            assertEquals(0, restoredDraft.getInt("serviceId"));
            assertEquals(1234, restoredDraft.getInt("start"));
            assertEquals(5678, restoredDraft.getInt("end"));
            assertEquals(2, restoredDraft.getInt("category"));
            assertEquals(7, restoredDraft.getLong("version"));
            assertEquals("original-draft", restoredDraft.getString("draftId"));
            assertEquals("1:02.", restoredDraft.getString("editorText"));
            assertTrue(restoredDraft.getBoolean("editorStart"));
        });
    }

    @Test
    public void actualParentRecreationPreservesDraftAndOpenEditorText() {
        final AtomicReference<VideoDetailFragment> originalParent = new AtomicReference<>();
        final java.util.concurrent.atomic.AtomicLong savedRevision = new java.util.concurrent.atomic.AtomicLong();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                final VideoDetailFragment parent = VideoDetailFragment.getInstanceInCollapsedState();
                originalParent.set(parent);
                activity.getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_player_holder, parent, "draft-parent").commitNow();
                final StreamInfo info = info("recreated");
                final SponsorBlockFragment editor = editor(info, draft(info, 7));
                // Keep the actual parent URL empty to suppress extraction and playback startup.
                // Its real lifecycle, adapter lookup, child restoration and state hooks still run.
                final TabAdapter tabs = new TabAdapter(parent.getChildFragmentManager());
                tabs.addFragment(editor, "SPONSOR_BLOCK TAB");
                set(parent, "pageAdapter", tabs);
                parent.getChildFragmentManager().beginTransaction()
                        .add(R.id.view_pager, editor, "draft-editor").commitNow();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                final SponsorBlockFragment editor = (SponsorBlockFragment) originalParent.get()
                        .getChildFragmentManager().findFragmentByTag("draft-editor");
                final AlertDialog dialog = (AlertDialog) editorField(editor, "timeDialog");
                assertTrue(dialog.isShowing());
                ((EditText) dialog.findViewById(R.id.time_input)).setText("0:06.");
                savedRevision.set(editor.getDraftVersion());
            });
            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                final VideoDetailFragment parent = (VideoDetailFragment) activity
                        .getSupportFragmentManager().findFragmentByTag("draft-parent");
                assertNotSame(originalParent.get(), parent);
                final Bundle state = (Bundle) get(parent, "sponsorBlockDraft");
                assertEquals(1234, state.getInt("start"));
                assertEquals(5678, state.getInt("end"));
                assertEquals(2, state.getInt("category"));
                assertEquals(savedRevision.get(), state.getLong("version"));
                assertEquals("original-draft", state.getString("draftId"));
                assertEquals("0:06.", state.getString("editorText"));
                final SponsorBlockFragment editor = (SponsorBlockFragment) parent
                        .getChildFragmentManager().findFragmentByTag("draft-editor");
                final AlertDialog dialog = (AlertDialog) editorField(editor, "timeDialog");
                assertTrue(dialog.isShowing());
                assertEquals("0:06.", ((EditText) dialog.findViewById(R.id.time_input))
                        .getText().toString());
            });
        }
    }

    private static Object editorField(final SponsorBlockFragment editor, final String name) {
        try {
            final Field field = SponsorBlockFragment.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(editor);
        } catch (final ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    public void duplicateSubmissionIsBlockedAndHttpFailureAllowsRetry() {
        withTransport((scenario, parent) -> {
            scenario.onActivity(activity -> {
                parent.submit();
                parent.submit();
                assertEquals(1, parent.requests);
                assertTrue((Boolean) editorField(parent.editor, "submitting"));
                parent.response.onSuccess(new Response(500, "Controlled failure", null,
                        "", null, ""));
            });
            awaitCompletion(scenario, parent);
            scenario.onActivity(activity -> {
                assertFalse((Boolean) editorField(parent.editor, "submitting"));
                assertTrue(parent.editor.saveDraft().containsKey("start"));
                parent.response = SingleSubject.create();
                parent.submit();
                assertEquals(2, parent.requests);
                parent.response.onSuccess(new Response(200, "OK", null, "", null, ""));
            });
            awaitCompletion(scenario, parent);
            scenario.onActivity(activity -> {
                assertFalse(parent.editor.saveDraft().containsKey("start"));
                assertFalse((Boolean) editorField(parent.editor, "submitting"));
            });
        });
    }

    @Test
    public void transportFailureAllowsRetryAndStaleRequestsNeverStart() {
        withTransport((scenario, parent) -> {
            scenario.onActivity(activity -> {
                parent.onRequestSubmitPendingSegment(1, parent.info.getUrl(), segment("submitted"));
                parent.onRequestSubmitPendingSegment(0, "https://example.invalid/stale",
                        segment("submitted"));
                assertEquals(0, parent.requests);
                parent.submit();
                parent.response.onError(new IOException("Controlled offline failure"));
            });
            awaitCompletion(scenario, parent);
            scenario.onActivity(activity -> {
                assertFalse((Boolean) editorField(parent.editor, "submitting"));
                assertTrue(parent.editor.saveDraft().containsKey("start"));
                parent.response = SingleSubject.create();
                parent.submit();
                assertEquals(2, parent.requests);
                parent.response.onSuccess(new Response(409, "Already exists", null,
                        "", null, ""));
            });
            awaitCompletion(scenario, parent);
            scenario.onActivity(activity ->
                    assertFalse(parent.editor.saveDraft().containsKey("start")));
        });
    }

    private static void awaitCompletion(final ActivityScenario<MainActivity> scenario,
                                        final ControlledDetailFragment parent) {
        final AtomicBoolean complete = new AtomicBoolean();
        final long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        do {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                final Disposable subscription = (Disposable) get(parent.fragment, "submitSegmentSubscriber");
                complete.set(subscription != null && subscription.isDisposed());
            });
            if (complete.get()) {
                return;
            }
            android.os.SystemClock.sleep(20);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Controlled submission did not finish");
    }

    private static void withTransport(final java.util.function.BiConsumer<
            ActivityScenario<MainActivity>, ControlledDetailFragment> test) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            final AtomicReference<ControlledDetailFragment> result = new AtomicReference<>();
            scenario.onActivity(activity -> {
                final ControlledDetailFragment parent = new ControlledDetailFragment();
                activity.getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_player_holder, parent.fragment, "controlled-parent").commitNow();
                parent.info = info("transport");
                parent.editor = editor(parent.info, draft(parent.info, 7));
                final TabAdapter tabs = new TabAdapter(parent.fragment.getChildFragmentManager());
                tabs.addFragment(parent.editor, "SPONSOR_BLOCK TAB");
                set(parent.fragment, "pageAdapter", tabs);
                set(parent.fragment, "currentInfo", parent.info);
                result.set(parent);
            });
            test.accept(scenario, result.get());
        }
    }

    /** Only transport is replaced; production guards and callbacks run unchanged. */
    private static final class ControlledDetailFragment {
        private final VideoDetailFragment fragment = VideoDetailFragment.getInstanceInCollapsedState();
        private SingleSubject<Response> response = SingleSubject.create();
        private int requests;
        private StreamInfo info;
        private SponsorBlockFragment editor;

        private void onRequestSubmitPendingSegment(final int serviceId, final String url,
                                                    final SponsorBlockSegment segment) {
            fragment.submitSponsorBlockSegment(serviceId, url, segment, capturedInfo -> {
                requests++;
                return response;
            });
        }

        private void submit() {
            onRequestSubmitPendingSegment(info.getServiceId(), info.getUrl(), segment("submitted"));
        }
    }

    private static void withFixture(final Consumer<Fixture> test) {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> test.accept(new Fixture(activity)));
        }
    }

    private static StreamInfo info(final String id) {
        final StreamInfo result = new StreamInfo(0, id, "https://example.invalid/" + id, id);
        result.setDuration(60);
        return result;
    }

    private static Bundle draft(final StreamInfo info, final long version) {
        final Bundle result = new Bundle();
        result.putString("videoUrl", info.getUrl());
        result.putInt("serviceId", info.getServiceId());
        result.putInt("start", 1234);
        result.putInt("end", 5678);
        result.putInt("category", 2);
        result.putLong("version", version);
        result.putString("draftId", "original-draft");
        result.putString("editorText", "1:02.");
        result.putBoolean("editorStart", true);
        return result;
    }

    private static SponsorBlockFragment editor(final StreamInfo info, final Bundle state) {
        final SponsorBlockFragment result = new SponsorBlockFragment(info);
        result.restoreDraft(state);
        return result;
    }

    private static SponsorBlockSegment segment(final String uuid) {
        return new SponsorBlockSegment(uuid, 1234, 5678,
                SponsorBlockCategory.SPONSOR, SponsorBlockAction.SKIP, 0);
    }

    private static boolean has(final StreamInfo info, final String uuid) {
        for (final SponsorBlockSegment segment : info.getSponsorBlockSegments()) {
            if (uuid.equals(segment.uuid)) {
                return true;
            }
        }
        return false;
    }

    private static void set(final Object target, final String name, final Object value) {
        try {
            final Field field = VideoDetailFragment.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (final ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Object get(final Object target, final String name) {
        try {
            final Field field = VideoDetailFragment.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (final ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static final class Fixture {
        private final VideoDetailFragment parent = new VideoDetailFragment();
        private final StreamInfo original = info("original");
        private final SponsorBlockFragment submittedEditor = editor(original, draft(original, 7));
        private final TabAdapter tabs;

        private Fixture(final MainActivity activity) {
            tabs = new TabAdapter(activity.getSupportFragmentManager());
            original.addSponsorBlockSegment(segment("TEMP"));
            set(parent, "pageAdapter", tabs);
            show(original, submittedEditor);
        }

        private void show(final StreamInfo info, final SponsorBlockFragment editor) {
            set(parent, "currentInfo", info);
            tabs.clearAllItems();
            tabs.addFragment(editor, "SPONSOR_BLOCK TAB");
        }

        private boolean complete() {
            return parent.applySponsorBlockSubmission(original, segment("submitted"),
                    "original-draft", 7, submittedEditor);
        }
    }
}
