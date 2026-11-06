package org.schabi.newpipe.fragments.detail

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.ErrorUtil
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockAction
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockCategory
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockExtractorHelper
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.fragments.list.sponsorblock.SponsorBlockFragment
import org.schabi.newpipe.fragments.list.sponsorblock.SponsorBlockFragmentListener
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.util.SponsorBlockHelper
import org.schabi.newpipe.util.SponsorBlockMode
import java.util.function.Function

/** Connects the SponsorBlock editor to the displayed stream and the active player. */
class DetailSponsorBlock(
    private val fragment: Fragment,
    private val host: Host,
) : SponsorBlockFragmentListener {

    interface Host {
        val player: Player?
        val currentInfo: StreamInfo?
        val sponsorBlockFragment: SponsorBlockFragment?
        var sponsorBlockDraft: Bundle?
    }

    private var submitSubscriber: Disposable? = null
    private var submissionUrl: String? = null
    private var submissionServiceId = -1

    fun configureEditor(editor: SponsorBlockFragment) {
        editor.setListener(this)
        host.sponsorBlockDraft?.let {
            editor.restoreDraft(it)
            host.sponsorBlockDraft = null
        }
        val info = host.currentInfo
        editor.setSubmitting(info != null && info.url == submissionUrl
            && info.serviceId == submissionServiceId)
    }

    fun saveDraft() {
        host.sponsorBlockFragment?.let { host.sponsorBlockDraft = it.saveDraft() }
    }

    fun onProgress() {
        host.sponsorBlockFragment?.refreshEditor()
    }

    fun onMetadataUpdate() {
        host.sponsorBlockFragment?.setSponsorBlockMode(
            SponsorBlockHelper.getSkippingMode(fragment.requireContext()))
        host.sponsorBlockFragment?.refreshEditor()
    }

    fun dispose() {
        submitSubscriber?.dispose()
        submitSubscriber = null
        submissionUrl = null
        host.sponsorBlockFragment?.setSubmitting(false)
    }

    override fun onSkippingEnabledChanged(newValue: Boolean) {
        host.player?.setSponsorBlockMode(
            if (newValue) SponsorBlockMode.ENABLED else SponsorBlockMode.DISABLED)
    }

    override fun onRequestNewPendingSegment(serviceId: Int, videoUrl: String,
                                            startTime: Int, endTime: Int) {
        val info = host.currentInfo ?: return
        if (info.serviceId != serviceId || info.url != videoUrl) {
            return
        }
        info.removeSponsorBlockSegment(PENDING_UUID)
        info.addSponsorBlockSegment(SponsorBlockSegment(PENDING_UUID, startTime.toDouble(),
            endTime.toDouble(), SponsorBlockCategory.PENDING, SponsorBlockAction.SKIP, serviceId))
        if (getSponsorBlockPosition(videoUrl) != null) {
            host.player?.onMarkSeekbarRequested(info)
        }
        host.sponsorBlockFragment?.refreshSponsorBlockSegments()
    }

    override fun onRequestClearPendingSegment(serviceId: Int, videoUrl: String) {
        val info = host.currentInfo ?: return
        if (info.serviceId != serviceId || info.url != videoUrl) {
            return
        }
        info.removeSponsorBlockSegment(PENDING_UUID)
        if (getSponsorBlockPosition(videoUrl) != null) {
            host.player?.onMarkSeekbarRequested(info)
        }
        host.sponsorBlockFragment?.refreshSponsorBlockSegments()
    }

    override fun getSponsorBlockPosition(videoUrl: String): Long? {
        val player = host.player ?: return null
        if (player.isStopped || player.playQueue?.item?.url != videoUrl
            || player.currentStreamInfo.map { it.url == videoUrl }.orElse(false) != true) {
            return null
        }
        return player.currentPosition
    }

    override fun getSponsorBlockDuration(videoUrl: String): Long? {
        if (getSponsorBlockPosition(videoUrl) == null) {
            return null
        }
        return host.player?.duration?.takeIf { it > 0 }
    }

    override fun onSponsorBlockEditingChanged(owner: Any, videoUrl: String, editing: Boolean) {
        host.player?.setSponsorBlockEditing(owner, videoUrl, editing)
    }

    override fun onRequestSubmitPendingSegment(serviceId: Int, videoUrl: String,
                                               segment: SponsorBlockSegment) {
        submitSponsorBlockSegment(serviceId, videoUrl, segment) { info ->
            val userId = SponsorBlockHelper.getUserId(fragment.requireContext())
            Single.fromCallable {
                SponsorBlockExtractorHelper.submitSponsorBlockSegment(info, segment,
                    SponsorBlockExtractorHelper.getApiUrl(info), userId)
            }
        }
    }

    fun submitSponsorBlockSegment(serviceId: Int, videoUrl: String,
                                   segment: SponsorBlockSegment,
                                   submission: Function<StreamInfo, Single<Response>>) {
        val info = host.currentInfo ?: return
        if (info.serviceId != serviceId || info.url != videoUrl
            || submitSubscriber?.isDisposed == false) {
            return
        }
        val editor = host.sponsorBlockFragment ?: return
        val version = editor.draftVersion
        val draftId = editor.draftId
        val context = fragment.requireContext()
        submissionUrl = videoUrl
        submissionServiceId = serviceId
        editor.setSubmitting(true)
        submitSubscriber = submission.apply(info)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ response ->
                finishSubmission(info, editor)
                val code = response.responseCode()
                if (code != 200 && code != 409) {
                    Toast.makeText(context, response.responseMessage().ifEmpty { "Error $code" },
                        Toast.LENGTH_SHORT).show()
                    return@subscribe
                }
                val sameDraft = applySponsorBlockSubmission(info, segment, draftId, version, editor)
                if (sameDraft && fragment.isAdded && fragment.view != null) {
                    AlertDialog.Builder(context)
                        .setMessage(R.string.sponsor_block_upload_success_message)
                        .setPositiveButton(R.string.ok, null).show()
                }
            }, { error ->
                finishSubmission(info, editor)
                if (fragment.isAdded && fragment.view != null) {
                    ErrorUtil.showSnackbar(context, ErrorInfo(error, UserAction.USER_REPORT,
                        "Submit SponsorBlock segment"))
                }
            })
    }

    private fun finishSubmission(info: StreamInfo, submittedEditor: SponsorBlockFragment) {
        submissionUrl = null
        submittedEditor.setSubmitting(false)
        host.sponsorBlockFragment?.takeIf { it.isForVideo(info.serviceId, info.url) }
            ?.setSubmitting(false)
    }

    fun applySponsorBlockSubmission(info: StreamInfo, segment: SponsorBlockSegment,
                                    draftId: String, version: Long,
                                    submittedEditor: SponsorBlockFragment): Boolean {
        val videoUrl = info.url
        val activeEditor = host.sponsorBlockFragment
            ?.takeIf { it.isForVideo(info.serviceId, videoUrl) }
        val savedDraft = host.sponsorBlockDraft?.takeIf {
            it.getString("videoUrl") == videoUrl && it.getInt("serviceId") == info.serviceId
        }
        val latestVersion = activeEditor?.draftVersion ?: savedDraft?.getLong("version", 0)
            ?: submittedEditor.draftVersion
        val latestDraftId = activeEditor?.draftId ?: savedDraft?.getString("draftId")
            ?: submittedEditor.draftId
        val unchanged = latestDraftId == draftId && latestVersion == version
        info.addSponsorBlockSegment(segment)
        if (unchanged) {
            info.removeSponsorBlockSegment(PENDING_UUID)
            if (savedDraft != null) {
                host.sponsorBlockDraft = null
            }
        }
        val currentInfo = host.currentInfo?.takeIf {
            it.serviceId == info.serviceId && it.url == videoUrl
        }
        if (currentInfo != null && currentInfo !== info) {
            currentInfo.addSponsorBlockSegment(segment)
        }
        if (unchanged) {
            activeEditor?.clearPendingSegment()
            currentInfo?.removeSponsorBlockSegment(PENDING_UUID)
        }
        if (currentInfo != null) {
            if (getSponsorBlockPosition(videoUrl) != null) {
                host.player?.onMarkSeekbarRequested(currentInfo)
            }
            host.sponsorBlockFragment?.refreshSponsorBlockSegments()
        }
        return unchanged && activeEditor != null
    }

    override fun onSeekToRequested(positionMillis: Long) {
        host.player?.seekTo(positionMillis)
    }

    private companion object {
        const val PENDING_UUID = "TEMP"
    }
}
