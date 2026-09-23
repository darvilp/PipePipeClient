package org.schabi.newpipe.fragments.list.sponsorblock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.plugins.RxAndroidPlugins;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.SingleSubject;

@RunWith(AndroidJUnit4.class)
public class SponsorBlockSegmentListAdapterTest {
    @Test
    public void recycledPendingRowRestoresBothVoteButtons() {
        onMain(() -> {
            final Fixture fixture = new Fixture();
            fixture.bind("TEMP", SponsorBlockCategory.PENDING);
            assertEquals(View.INVISIBLE, fixture.up().getVisibility());
            assertEquals(View.INVISIBLE, fixture.down().getVisibility());
            fixture.bind("normal", SponsorBlockCategory.SPONSOR);
            assertEquals(View.VISIBLE, fixture.up().getVisibility());
            assertEquals(View.VISIBLE, fixture.down().getVisibility());
        });
    }

    @Test
    public void votesRemainWithSegmentAcrossRebindingAndOtherHolders() {
        onMain(() -> {
            final Fixture fixture = new Fixture();
            fixture.bind("first", SponsorBlockCategory.SPONSOR);
            final SingleSubject<Response> first = click(fixture.up());
            assertTrue(first.hasObservers());
            assertFalse(click(fixture.up()).hasObservers());

            fixture.bind("second", SponsorBlockCategory.SPONSOR);
            first.onSuccess(response(200));
            final SingleSubject<Response> second = click(fixture.up());
            assertTrue("First segment's completion must not block second", second.hasObservers());
            second.onSuccess(response(200));

            fixture.bind("first", SponsorBlockCategory.SPONSOR);
            fixture.holder = fixture.adapter.onCreateViewHolder(fixture.parent, 0);
            fixture.adapter.onBindViewHolder(fixture.holder, 0);
            assertFalse("Successful vote survives holder replacement",
                    click(fixture.up()).hasObservers());
            assertTrue("Changing the vote remains possible", click(fixture.down()).hasObservers());
        });
    }

    @Test
    public void transportAndHttpFailuresAllowRetry() {
        onMain(() -> {
            final Fixture fixture = new Fixture();
            fixture.bind("retry", SponsorBlockCategory.SPONSOR);
            // Existing NPE handling avoids opening an error Snackbar in this unattached row.
            click(fixture.up()).onError(new NullPointerException("test failure"));
            final SingleSubject<Response> retry = click(fixture.up());
            assertTrue(retry.hasObservers());
            retry.onSuccess(response(500));
            assertTrue(click(fixture.up()).hasObservers());
        });
    }

    @Test
    public void pendingRowsNeverSubmitEvenWithProgrammaticClick() {
        onMain(() -> {
            final Fixture fixture = new Fixture();
            fixture.bind("TEMP", SponsorBlockCategory.PENDING);
            assertFalse(click(fixture.up()).hasObservers());
        });
    }

    private static Response response(final int status) {
        return new Response(status, "test response", null, "", null, "");
    }

    private static SingleSubject<Response> click(final View button) {
        final SingleSubject<Response> response = SingleSubject.create();
        final AtomicBoolean intercepted = new AtomicBoolean();
        // Replace only the request source; preserve the adapter's scheduling and callbacks.
        RxJavaPlugins.setOnSingleAssembly(source ->
                intercepted.compareAndSet(false, true) ? response : source);
        try {
            button.performClick();
        } finally {
            RxJavaPlugins.setOnSingleAssembly(null);
        }
        return response;
    }

    private static void onMain(final Runnable test) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            RxJavaPlugins.setIoSchedulerHandler(scheduler -> Schedulers.trampoline());
            RxAndroidPlugins.setMainThreadSchedulerHandler(scheduler -> Schedulers.trampoline());
            try {
                test.run();
            } finally {
                RxJavaPlugins.reset();
                RxAndroidPlugins.reset();
            }
        });
    }

    private static final class Fixture {
        private final FrameLayout parent;
        private final SponsorBlockSegmentListAdapter adapter;
        private SponsorBlockSegmentListAdapter.SponsorBlockSegmentItemViewHolder holder;

        private Fixture() {
            final Context context = new androidx.appcompat.view.ContextThemeWrapper(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(), R.style.LightTheme);
            parent = new FrameLayout(context);
            adapter = new SponsorBlockSegmentListAdapter(context, null);
            holder = adapter.onCreateViewHolder(parent, 0);
        }

        private void bind(final String uuid, final SponsorBlockCategory category) {
            adapter.setItems(new SponsorBlockSegment[]{new SponsorBlockSegment(
                    uuid, 0, 1000, category, SponsorBlockAction.SKIP, 0)});
            adapter.onBindViewHolder(holder, 0);
        }

        private View up() {
            return holder.itemView.findViewById(R.id.item_segment_vote_up_imageview);
        }

        private View down() {
            return holder.itemView.findViewById(R.id.item_segment_vote_down_imageview);
        }
    }
}
