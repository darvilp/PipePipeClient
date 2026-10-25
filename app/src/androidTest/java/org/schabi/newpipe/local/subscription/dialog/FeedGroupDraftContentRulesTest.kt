package org.schabi.newpipe.local.subscription.dialog

import android.widget.CheckBox
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedContentSelection

@RunWith(AndroidJUnit4::class)
class FeedGroupDraftContentRulesTest {
    @Test
    fun equivalentCheckboxOrdersKeepTheSameChannelException() {
        val videosFirst = intArrayOf(R.id.default_content_videos, R.id.default_content_shorts)
        val shortsFirst = videosFirst.reversedArray()
        for (order in listOf(videosFirst, shortsFirst)) {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val dialog = FeedGroupDialog().apply {
                        selectedSubscriptions = hashSetOf(42L)
                        wasSubscriptionSelectionChanged = true
                        selectedContentSelectionMask = FeedContentSelection.ALL.mask
                        contentSelectionOverrides = hashMapOf(42L to 6)
                        wasContentRulesChanged = true
                        currentScreen = FeedGroupDialog.ScreenState.ContentRulesScreen
                    }
                    dialog.showNow(activity.supportFragmentManager, "draft-content-rules")
                    for (checkboxId in order) {
                        dialog.requireView().findViewById<CheckBox>(checkboxId).performClick()
                        assertEquals("Unsaved checkbox changes must preserve explicit intent",
                            mapOf(42L to 6), dialog.contentSelectionOverrides)
                    }
                    assertEquals(FeedContentSelection.LIVE.mask, dialog.selectedContentSelectionMask)
                    dialog.dismissNow()
                }
            }
        }
    }
}
