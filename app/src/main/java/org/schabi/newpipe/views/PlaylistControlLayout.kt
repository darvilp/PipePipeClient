package org.schabi.newpipe.views

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.preference.PreferenceManager
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.PlaylistControlBinding
import org.schabi.newpipe.player.playqueue.PlayQueue
import org.schabi.newpipe.util.NavigationHelper

/**
 * Control bar (background / play all / popup). Call [configure] with the page it is shown on
 * so that it can be hidden according to the appearance setting.
 */
class PlaylistControlLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var disabled = false

    /** @param pageKey one of the values in R.array.list_control_pages_value_list */
    fun configure(pageKey: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val enabled = prefs.getStringSet(
            context.getString(R.string.list_control_pages_key),
            resources.getStringArray(R.array.list_control_pages_value_list).toSet()
        )
        disabled = enabled?.contains(pageKey) != true
        if (disabled) super.setVisibility(GONE)
    }

    /** All playback buttons append to the same queue on hold. */
    fun setEnqueueOnLongPress(queue: (() -> PlayQueue)?) {
        val binding = PlaylistControlBinding.bind(this)
        val listener = queue?.let { createQueue ->
            OnLongClickListener {
                val items = createQueue()
                if (!items.isEmpty) {
                    NavigationHelper.enqueueOnPlayer(context, items)
                }
                true
            }
        }
        listOf(binding.playlistCtrlPlayAllButton, binding.playlistCtrlPlayBgButton,
            binding.playlistCtrlPlayPopupButton).forEach { button ->
            button.setOnLongClickListener(listener)
            button.isLongClickable = listener != null
        }
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(if (disabled) GONE else visibility)
    }
}
