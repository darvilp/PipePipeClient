package org.schabi.newpipe.fragments.list.sponsorblock;

import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment;

public interface SponsorBlockFragmentListener {
    Long getSponsorBlockPosition(String videoUrl);
    Long getSponsorBlockDuration(String videoUrl);
    void onSponsorBlockEditingChanged(Object owner, String videoUrl, boolean editing);
    void onSkippingEnabledChanged(boolean newValue);
    void onRequestNewPendingSegment(int serviceId, String videoUrl, int startTime, int endTime);
    void onRequestClearPendingSegment(int serviceId, String videoUrl);
    void onRequestSubmitPendingSegment(int serviceId, String videoUrl, SponsorBlockSegment newSegment);
    void onSeekToRequested(long positionMillis);
}
