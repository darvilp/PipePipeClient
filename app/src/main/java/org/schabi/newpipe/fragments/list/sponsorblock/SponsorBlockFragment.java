package org.schabi.newpipe.fragments.list.sponsorblock;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentSponsorBlockBinding;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory;
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.fragments.StateSaverFragment;
import org.schabi.newpipe.util.SponsorBlockHelper;
import org.schabi.newpipe.util.SponsorBlockMode;
import org.schabi.newpipe.util.SponsorBlockTime;
import org.schabi.newpipe.util.StreamTypeUtil;

import java.util.Queue;
import java.util.UUID;

public class SponsorBlockFragment extends StateSaverFragment
        implements CompoundButton.OnCheckedChangeListener, SponsorBlockSegmentListAdapterListener {
    private static final SponsorBlockCategory[] CATEGORIES = {
        SponsorBlockCategory.SPONSOR, SponsorBlockCategory.INTRO, SponsorBlockCategory.OUTRO,
        SponsorBlockCategory.INTERACTION, SponsorBlockCategory.HIGHLIGHT,
        SponsorBlockCategory.SELF_PROMO, SponsorBlockCategory.NON_MUSIC,
        SponsorBlockCategory.PREVIEW, SponsorBlockCategory.FILLER
    };
    private StreamInfo streamInfo;
    private FragmentSponsorBlockBinding binding;
    private Integer markedStartTime;
    private Integer markedEndTime;
    private int categoryIndex;
    private long draftVersion;
    private String draftId = UUID.randomUUID().toString();
    private SponsorBlockSegmentListAdapter segmentListAdapter;
    private SponsorBlockFragmentListener listener;
    private SponsorBlockMode currentMode;
    private AlertDialog timeDialog;
    private EditText timeInput;
    private boolean editingStart;
    private boolean submitting;
    private Bundle initialDraft;
    private String startInput;
    private String endInput;

    public SponsorBlockFragment() { }

    public SponsorBlockFragment(@NonNull final StreamInfo info) {
        streamInfo = info;
    }

    @Override
    public String generateSuffix() {
        return "." + System.nanoTime() + ".sponsorblock";
    }

    @Override
    public void writeTo(final Queue<Object> objects) {
        objects.add(streamInfo);
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> objects) {
        streamInfo = (StreamInfo) objects.poll();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        binding = FragmentSponsorBlockBinding.inflate(inflater, container, false);
        segmentListAdapter = new SponsorBlockSegmentListAdapter(requireContext(), this);
        binding.segmentList.setAdapter(segmentListAdapter);
        binding.sponsorBlockControlsMarkSegmentStart.setOnClickListener(v -> mark(true));
        binding.sponsorBlockControlsMarkSegmentEnd.setOnClickListener(v -> mark(false));
        binding.sponsorBlockControlsSegmentStart.setOnClickListener(v -> edit(true, null));
        binding.sponsorBlockControlsSegmentEnd.setOnClickListener(v -> edit(false, null));
        binding.jumpStart.setOnClickListener(v -> seek(markedStartTime));
        binding.jumpEnd.setOnClickListener(v -> seek(markedEndTime));
        binding.jumpStart.setContentDescription(getString(R.string.sponsor_block_jump_start_description));
        binding.jumpEnd.setContentDescription(getString(R.string.sponsor_block_jump_end_description));
        binding.sponsorBlockControlsClearSegment.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setMessage(R.string.sponsor_block_clear_marked_segment_prompt)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes, (d, w) -> clearPendingSegment()).show());
        binding.sponsorBlockControlsSubmitSegment.setOnClickListener(v -> submit());
        final String[] names = new String[CATEGORIES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = SponsorBlockHelper.convertCategoryToFriendlyName(requireContext(), CATEGORIES[i]);
        }
        final ArrayAdapter<String> categories = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        categories.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.segmentCategory.setAdapter(categories);
        setSponsorBlockMode(currentMode);
        return binding.getRoot();
    }

    @Override
    public void onViewStateRestored(@Nullable final Bundle savedState) {
        super.onViewStateRestored(savedState);
        final Bundle state = initialDraft != null ? initialDraft : savedState;
        initialDraft = null;
        if (state != null) {
            markedStartTime = state.containsKey("start") ? state.getInt("start") : null;
            markedEndTime = state.containsKey("end") ? state.getInt("end") : null;
            categoryIndex = state.getInt("category", 0);
            draftVersion = state.getLong("version", 0);
            draftId = state.getString("draftId", draftId);
            startInput = state.getString("startInput");
            endInput = state.getString("endInput");
        }
        binding.segmentCategory.setSelection(categoryIndex);
        binding.segmentCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view,
                                       final int position, final long id) {
                if (categoryIndex != position) {
                    categoryIndex = position;
                    changed();
                }
            }
            @Override
            public void onNothingSelected(final AdapterView<?> parent) { }
        });
        refreshSponsorBlockSegments();
        render();
        if (state != null && state.containsKey("editorText")) {
            final String text = state.getString("editorText");
            final boolean start = state.getBoolean("editorStart");
            binding.getRoot().post(() -> {
                if (binding != null && isAdded()) {
                    edit(start, text);
                }
            });
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle state) {
        super.onSaveInstanceState(state);
        state.putAll(saveDraft());
    }

    public Bundle saveDraft() {
        if (initialDraft != null) {
            return new Bundle(initialDraft);
        }
        final Bundle state = new Bundle();
        if (streamInfo != null) {
            state.putString("videoUrl", streamInfo.getUrl());
            state.putInt("serviceId", streamInfo.getServiceId());
        }
        if (markedStartTime != null) {
            state.putInt("start", markedStartTime);
        }
        if (markedEndTime != null) {
            state.putInt("end", markedEndTime);
        }
        state.putInt("category", categoryIndex);
        state.putLong("version", draftVersion);
        state.putString("draftId", draftId);
        state.putString("startInput", startInput);
        state.putString("endInput", endInput);
        if (timeDialog != null && timeDialog.isShowing()) {
            state.putString("editorText", timeInput.getText().toString());
            state.putBoolean("editorStart", editingStart);
        }
        return state;
    }

    public void restoreDraft(@Nullable final Bundle state) {
        if (state != null && streamInfo != null
                && streamInfo.getUrl().equals(state.getString("videoUrl"))
                && streamInfo.getServiceId() == state.getInt("serviceId")) {
            initialDraft = new Bundle(state);
        }
    }

    public void setListener(final SponsorBlockFragmentListener value) {
        listener = value;
    }

    public void setSponsorBlockMode(@Nullable final SponsorBlockMode mode) {
        currentMode = mode;
        if (binding != null) {
            binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(null);
            binding.skippingIsEnabledSwitch.setChecked(mode == SponsorBlockMode.ENABLED);
            binding.skippingIsEnabledSwitch.setOnCheckedChangeListener(this);
        }
    }

    @Override
    public void onCheckedChanged(final CompoundButton button, final boolean checked) {
        if (listener != null) {
            listener.onSkippingEnabledChanged(checked);
        }
    }

    private boolean isHighlight() {
        return CATEGORIES[categoryIndex] == SponsorBlockCategory.HIGHLIGHT;
    }

    @Nullable
    private Long duration() {
        if (streamInfo == null || StreamTypeUtil.isLiveStream(streamInfo.getStreamType())) {
            return null;
        }
        final Long playerDuration = listener == null ? null
                : listener.getSponsorBlockDuration(streamInfo.getUrl());
        if (playerDuration != null && playerDuration > 0 && playerDuration <= Integer.MAX_VALUE) {
            return playerDuration;
        }
        final long seconds = streamInfo.getDuration();
        return seconds > 0 && seconds <= Integer.MAX_VALUE / 1000
                ? seconds * 1000 : null;
    }

    private void mark(final boolean start) {
        final Long position = listener == null || streamInfo == null ? null
                : listener.getSponsorBlockPosition(streamInfo.getUrl());
        if (position == null || position < 0 || position > Integer.MAX_VALUE) {
            binding.segmentError.setText(R.string.sponsor_block_position_unavailable);
            return;
        }
        if (!setBoundary(start, position.intValue())) {
            binding.segmentError.setText(R.string.sponsor_block_time_invalid);
        }
    }

    private boolean setBoundary(final boolean start, final int value) {
        final Long limit = duration();
        if (value < 0 || limit != null && value > limit
                || !isHighlight() && (start && markedEndTime != null && value >= markedEndTime
                || !start && markedStartTime != null && value <= markedStartTime)) {
            return false;
        }
        if (start) {
            markedStartTime = value;
            startInput = null;
        } else {
            markedEndTime = value;
            endInput = null;
        }
        changed();
        return true;
    }

    private void changed() {
        draftVersion++;
        if (listener != null) {
            if (SponsorBlockTime.valid(markedStartTime, markedEndTime, isHighlight(), duration())) {
                listener.onRequestNewPendingSegment(streamInfo.getServiceId(), streamInfo.getUrl(), markedStartTime,
                        isHighlight() ? markedStartTime : markedEndTime);
            } else {
                listener.onRequestClearPendingSegment(streamInfo.getServiceId(), streamInfo.getUrl());
            }
        }
        render();
    }

    private void edit(final boolean start, @Nullable final String restoredText) {
        if (timeDialog != null && timeDialog.isShowing()) {
            return;
        }
        editingStart = start;
        final View view = getLayoutInflater().inflate(R.layout.dialog_sponsor_block_time, null);
        timeInput = view.findViewById(R.id.time_input);
        final Integer value = start ? markedStartTime : markedEndTime;
        final String previousInput = start ? startInput : endInput;
        timeInput.setText(restoredText != null ? restoredText
                : previousInput != null ? previousInput : value == null ? "" : SponsorBlockTime.format(value));
        timeInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(final CharSequence text, final int offset,
                                                    final int count, final int after) { }
            @Override public void onTextChanged(final CharSequence text, final int offset,
                                                final int before, final int count) { }
            @Override public void afterTextChanged(final Editable text) {
                // Unsaved edits must not be erased by an older upload completion.
                draftVersion++;
                if (start) {
                    startInput = text.toString();
                } else {
                    endInput = text.toString();
                }
            }
        });
        timeInput.setContentDescription(getString(start ? R.string.start : R.string.end));
        view.findViewById(R.id.time_minus).setOnClickListener(v -> nudge(-100));
        view.findViewById(R.id.time_plus).setOnClickListener(v -> nudge(100));
        view.findViewById(R.id.video_start).setOnClickListener(v -> timeInput.setText(SponsorBlockTime.format(0)));
        final Long limit = duration();
        view.findViewById(R.id.video_end).setEnabled(limit != null);
        view.findViewById(R.id.video_end).setOnClickListener(v -> timeInput.setText(SponsorBlockTime.format(limit)));
        timeDialog = new AlertDialog.Builder(requireContext())
                .setTitle(start ? R.string.start : R.string.end).setView(view)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.ok, null).create();
        timeDialog.show();
        timeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                if (setBoundary(start, SponsorBlockTime.parse(timeInput.getText().toString()))) {
                    timeDialog.dismiss();
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // Keep the user's text and dialog open for correction.
            }
            timeInput.setError(getString(R.string.sponsor_block_time_invalid));
        });
    }

    private void nudge(final int amount) {
        try {
            final int next = Math.addExact(SponsorBlockTime.parse(timeInput.getText().toString()), amount);
            final Long limit = duration();
            if (next < 0 || limit != null && next > limit) {
                throw new IllegalArgumentException();
            }
            timeInput.setText(SponsorBlockTime.format(next));
            timeInput.setError(null);
        } catch (IllegalArgumentException | ArithmeticException ignored) {
            timeInput.setError(getString(R.string.sponsor_block_time_invalid));
        }
    }

    private void seek(@Nullable final Integer time) {
        if (time != null) {
            onSkipToTimestampRequested(time);
        }
    }

    @Override
    public void onSkipToTimestampRequested(final long positionMillis) {
        if (listener != null && streamInfo != null
                && listener.getSponsorBlockPosition(streamInfo.getUrl()) != null) {
            listener.onSeekToRequested(positionMillis);
        }
    }

    private void submit() {
        if (submitting || listener == null
                || !SponsorBlockTime.valid(markedStartTime, markedEndTime, isHighlight(), duration())) {
            return;
        }
        listener.onRequestSubmitPendingSegment(streamInfo.getServiceId(), streamInfo.getUrl(), new SponsorBlockSegment("", markedStartTime,
                isHighlight() ? markedStartTime : markedEndTime, CATEGORIES[categoryIndex],
                isHighlight() ? SponsorBlockAction.POI : SponsorBlockAction.SKIP,
                streamInfo.getServiceId()));
    }

    public boolean isForVideo(final int serviceId, final String url) {
        return streamInfo != null && streamInfo.getServiceId() == serviceId
                && streamInfo.getUrl().equals(url);
    }

    public String getDraftId() {
        return initialDraft != null ? initialDraft.getString("draftId", draftId) : draftId;
    }

    public long getDraftVersion() {
        return initialDraft != null ? initialDraft.getLong("version", 0) : draftVersion;
    }

    public void setSubmitting(final boolean value) {
        submitting = value;
        render();
    }

    public void clearPendingSegment() {
        draftVersion = getDraftVersion();
        initialDraft = null;
        draftId = UUID.randomUUID().toString();
        markedStartTime = null;
        markedEndTime = null;
        startInput = null;
        endInput = null;
        if (timeDialog != null) {
            timeDialog.dismiss();
        }
        changed();
        if (binding == null) {
            // A completed upload may clear a restored offscreen tab before its view exists.
            initialDraft = saveDraft();
        }
    }

    public void refreshSponsorBlockSegments() {
        if (segmentListAdapter != null && streamInfo != null) {
            segmentListAdapter.setItems(streamInfo.getSponsorBlockSegments());
        }
    }

    /** Called after metadata/player changes as well as after editing. */
    public void refreshEditor() {
        render();
    }

    private void render() {
        if (binding == null) {
            return;
        }
        final String start = markedStartTime == null ? getString(R.string.sponsor_block_time_unset)
                : SponsorBlockTime.format(markedStartTime);
        final String end = markedEndTime == null ? getString(R.string.sponsor_block_time_unset)
                : SponsorBlockTime.format(markedEndTime);
        binding.sponsorBlockControlsSegmentStart.setText(markedStartTime == null ? start
                : getString(R.string.sponsor_block_edit_time, start));
        binding.sponsorBlockControlsSegmentEnd.setText(markedEndTime == null ? end
                : getString(R.string.sponsor_block_edit_time, end));
        binding.sponsorBlockControlsSegmentStart.setContentDescription(getString(R.string.sponsor_block_edit_start_description, start));
        binding.sponsorBlockControlsSegmentEnd.setContentDescription(getString(R.string.sponsor_block_edit_end_description, end));
        binding.endRow.setVisibility(isHighlight() ? View.GONE : View.VISIBLE);
        binding.jumpStart.setEnabled(markedStartTime != null);
        binding.jumpEnd.setEnabled(markedEndTime != null);
        final boolean valid = SponsorBlockTime.valid(markedStartTime, markedEndTime, isHighlight(), duration());
        binding.segmentDuration.setText(valid && !isHighlight()
                ? getString(R.string.sponsor_block_duration, SponsorBlockTime.format(markedEndTime - markedStartTime)) : "");
        binding.segmentError.setText(!valid && markedStartTime != null
                && (markedEndTime != null || isHighlight()) ? getString(R.string.sponsor_block_time_invalid) : "");
        binding.sponsorBlockControlsSubmitSegment.setEnabled(valid && !submitting);
        binding.sponsorBlockControlsSubmitSegment.setText(submitting ? R.string.sponsor_block_saving : R.string.submit);
        updateHold();
    }

    private void updateHold() {
        final boolean hold = binding != null && isResumed() && getUserVisibleHint()
                && markedStartTime != null && streamInfo != null && listener != null
                && listener.getSponsorBlockPosition(streamInfo.getUrl()) != null;
        if (listener != null && streamInfo != null) {
            listener.onSponsorBlockEditingChanged(this, streamInfo.getUrl(), hold);
        }
        if (binding != null) {
            binding.queueHoldStatus.setVisibility(hold ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void setUserVisibleHint(final boolean visible) {
        super.setUserVisibleHint(visible);
        updateHold();
    }

    @Override
    public void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onPause() {
        if (listener != null && streamInfo != null) {
            listener.onSponsorBlockEditingChanged(this, streamInfo.getUrl(), false);
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (listener != null && streamInfo != null) {
            listener.onSponsorBlockEditingChanged(this, streamInfo.getUrl(), false);
        }
        if (timeDialog != null) {
            timeDialog.dismiss();
            timeDialog = null;
            timeInput = null;
        }
        if (binding != null) {
            binding.segmentList.setAdapter(null);
        }
        binding = null;
        segmentListAdapter = null;
        super.onDestroyView();
    }
}
