package org.schabi.newpipe.fragments.list.sponsorblock;

import static org.schabi.newpipe.util.TimeUtils.millisecondsToString;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockExtractorHelper;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.util.SponsorBlockHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SponsorBlockSegmentListAdapter extends
        RecyclerView.Adapter<SponsorBlockSegmentListAdapter.SponsorBlockSegmentItemViewHolder> {
    private final Context context;
    private ArrayList<SponsorBlockSegment> sponsorBlockSegments = new ArrayList<>();
    private final SponsorBlockSegmentListAdapterListener listener;
    private final Map<String, VoteState> votes = new HashMap<>();
    private final CompositeDisposable voteRequests = new CompositeDisposable();

    public SponsorBlockSegmentListAdapter(final Context context,
                                          final SponsorBlockSegmentListAdapterListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(final SponsorBlockSegment[] items) {
        if (items == null) {
            sponsorBlockSegments.clear();
        } else {
            sponsorBlockSegments = new ArrayList<>(Arrays.asList(items));
        }

        // find the first "highlight" segment (if it exists) and move it to the top
        if (sponsorBlockSegments.size() > 0) {
            final Optional<SponsorBlockSegment> highlightSegment =
                    sponsorBlockSegments
                            .stream()
                            .filter(x -> x.category == SponsorBlockCategory.HIGHLIGHT)
                            .findFirst();

            if (highlightSegment.isPresent()) {
                sponsorBlockSegments.remove(highlightSegment.get());
                sponsorBlockSegments.add(0, highlightSegment.get());
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SponsorBlockSegmentListAdapter.SponsorBlockSegmentItemViewHolder onCreateViewHolder(
            @NonNull final ViewGroup parent, final int viewType) {
        final View itemView = LayoutInflater
                .from(context)
                .inflate(R.layout.list_segments_item, parent, false);
        return new SponsorBlockSegmentItemViewHolder(itemView, listener, votes, voteRequests);
    }

    @Override
    public void onBindViewHolder(
            @NonNull final SponsorBlockSegmentListAdapter.SponsorBlockSegmentItemViewHolder holder,
            final int position) {
        final SponsorBlockSegment sponsorBlockSegment = sponsorBlockSegments.get(position);
        holder.updateFrom(sponsorBlockSegment);
    }

    @Override
    public int getItemCount() {
        return sponsorBlockSegments.size();
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull final RecyclerView recyclerView) {
        voteRequests.clear();
        votes.values().forEach(state -> state.isVoting = false);
        super.onDetachedFromRecyclerView(recyclerView);
    }

    private static final class VoteState {
        private boolean isVoting;
        private Integer lastVote;
    }

    public static class SponsorBlockSegmentItemViewHolder extends RecyclerView.ViewHolder {
        private final View itemSegmentColorView;
        private final ImageView itemSegmentSkipToHighlight;
        private final TextView itemSegmentNameTextView;
        private final TextView itemSegmentStartTimeTextView;
        private final TextView itemSegmentEndTimeTextView;
        private final ImageView itemSegmentVoteUpImageView;
        private final ImageView itemSegmentVoteDownImageView;
        private final Map<String, VoteState> votes;
        private final CompositeDisposable voteRequests;
        private String segmentUuid;
        private int segmentServiceId;
        private SponsorBlockSegment currentSponsorBlockSegment;

        private SponsorBlockSegmentItemViewHolder(
                @NonNull final View itemView,
                final SponsorBlockSegmentListAdapterListener listener,
                final Map<String, VoteState> votes,
                final CompositeDisposable voteRequests) {
            super(itemView);
            this.votes = votes;
            this.voteRequests = voteRequests;

            itemSegmentColorView = itemView.findViewById(R.id.item_segment_color_view);
            itemSegmentSkipToHighlight = itemView.findViewById(R.id.item_segment_skip_to_highlight);
            itemSegmentSkipToHighlight.setOnClickListener(v -> {
                if (currentSponsorBlockSegment != null && listener != null) {
                    listener.onSkipToTimestampRequested(
                            (long) currentSponsorBlockSegment.startTime);
                }
            });
            itemSegmentNameTextView = itemView.findViewById(
                    R.id.item_segment_category_name_textview);
            itemSegmentStartTimeTextView = itemView.findViewById(
                    R.id.item_segment_start_time_textview);
            itemSegmentStartTimeTextView.setOnClickListener(v -> {
                if (currentSponsorBlockSegment != null && listener != null) {
                    listener.onSkipToTimestampRequested(
                            (long) currentSponsorBlockSegment.startTime);
                }
            });
            itemSegmentEndTimeTextView = itemView.findViewById(R.id.item_segment_end_time_textview);
            itemSegmentEndTimeTextView.setOnClickListener(v -> {
                if (currentSponsorBlockSegment != null && listener != null) {
                    listener.onSkipToTimestampRequested((long) currentSponsorBlockSegment.endTime);
                }
            });

            // voting:
            //   1 = up
            //   0 = down
            //   20 = reset
            itemSegmentVoteUpImageView =
                    itemView.findViewById(R.id.item_segment_vote_up_imageview);
            itemSegmentVoteUpImageView.setOnClickListener(v -> vote(1));
            itemSegmentVoteUpImageView.setOnLongClickListener(v -> {
                vote(20);
                return true;
            });
            itemSegmentVoteDownImageView =
                    itemView.findViewById(R.id.item_segment_vote_down_imageview);
            itemSegmentVoteDownImageView.setOnClickListener(v -> vote(0));
            itemSegmentVoteDownImageView.setOnLongClickListener(v -> {
                vote(20);
                return true;
            });
        }

        private void updateFrom(final SponsorBlockSegment sponsorBlockSegment) {
            currentSponsorBlockSegment = sponsorBlockSegment;

            final Context context = itemView.getContext();

            // uuid
            segmentUuid = sponsorBlockSegment.uuid;
            segmentServiceId = sponsorBlockSegment.serviceId;

            // category color
            final Integer segmentColor =
                    SponsorBlockHelper.convertCategoryToColor(
                            sponsorBlockSegment.category, context);
            if (segmentColor != null) {
                itemSegmentColorView.setBackgroundColor(segmentColor);
            }

            // skip to highlight
            if (sponsorBlockSegment.category == SponsorBlockCategory.HIGHLIGHT) {
                itemSegmentColorView.setVisibility(View.GONE);
                itemSegmentSkipToHighlight.setVisibility(View.VISIBLE);
            } else {
                itemSegmentColorView.setVisibility(View.VISIBLE);
                itemSegmentSkipToHighlight.setVisibility(View.GONE);
            }

            // category name
            final String friendlyCategoryName =
                    SponsorBlockHelper.convertCategoryToFriendlyName(
                            context, sponsorBlockSegment.category);
            itemSegmentNameTextView.setText(friendlyCategoryName);

            // from
            final String startText = millisecondsToString(sponsorBlockSegment.startTime);
            itemSegmentStartTimeTextView.setText(startText);

            // to
            final String endText = millisecondsToString(sponsorBlockSegment.endTime);
            itemSegmentEndTimeTextView.setText(endText);

            final int voteVisibility = canVote() ? View.VISIBLE : View.INVISIBLE;
            itemSegmentVoteUpImageView.setVisibility(voteVisibility);
            itemSegmentVoteDownImageView.setVisibility(voteVisibility);
        }

        private boolean canVote() {
            return currentSponsorBlockSegment != null
                    && currentSponsorBlockSegment.category != SponsorBlockCategory.PENDING
                    && segmentUuid != null && !segmentUuid.isEmpty()
                    && !"TEMP".equals(segmentUuid);
        }

        private void vote(final int value) {
            if (!canVote()) {
                return;
            }

            // State belongs to the segment, not the recycled row displaying it.
            final String uuid = segmentUuid;
            final int serviceId = segmentServiceId;
            final VoteState state = votes.computeIfAbsent(serviceId + ":" + uuid,
                    key -> new VoteState());
            if (state.isVoting || Integer.valueOf(value).equals(state.lastVote)) {
                return;
            }
            final Context context = itemView.getContext();
            final String userId = SponsorBlockHelper.getUserId(context);
            state.isVoting = true;

            // Let the captured segment request finish even if this holder is rebound.
            voteRequests.add(Single.fromCallable(() ->
                            SponsorBlockExtractorHelper.submitSponsorBlockSegmentVote(
                                    uuid, SponsorBlockExtractorHelper.getApiUrl(serviceId),
                                    value, userId))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(response -> {
                        state.isVoting = false;
                        String toastMessage;
                        if (response.responseCode() != 200) {
                            toastMessage = response.responseMessage();
                            if (toastMessage.equals("")) {
                                toastMessage = "Error " + response.responseCode();
                            }
                        } else if (value == 0) {
                            state.lastVote = value;
                            toastMessage = context.getString(
                                    R.string.sponsor_block_segment_voted_down_toast);
                        } else if (value == 1) {
                            state.lastVote = value;
                            toastMessage = context.getString(
                                    R.string.sponsor_block_segment_voted_up_toast);
                        } else if (value == 20) {
                            state.lastVote = value;
                            toastMessage = context.getString(
                                    R.string.sponsor_block_segment_reset_vote_toast);
                        } else {
                            return;
                        }
                        Toast.makeText(context,
                                toastMessage,
                                Toast.LENGTH_SHORT).show();
                    }, throwable -> {
                        state.isVoting = false;
                        if (throwable instanceof NullPointerException) {
                            return;
                        }
                        ErrorUtil.showSnackbar(context,
                                new ErrorInfo(throwable, UserAction.SUBSCRIPTION_UPDATE,
                                        "Submit vote for SponsorBlock segment"));
                    }));
        }
    }
}
