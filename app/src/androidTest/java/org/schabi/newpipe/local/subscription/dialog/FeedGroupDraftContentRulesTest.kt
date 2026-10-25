package org.schabi.newpipe.local.subscription.dialog

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.CheckBox
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.database.AppDatabase
import org.schabi.newpipe.database.feed.model.FeedContentSelection
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.database.feed.model.FeedGroupSubscriptionEntity
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.local.subscription.item.PickerSubscriptionItem

@RunWith(AndroidJUnit4::class)
class FeedGroupDraftContentRulesTest {
    @Test
    fun defaultEditBeforeInitialMembershipDeliveryPreservesPersistedException() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val groupId = 424242L
            val subscription = SubscriptionEntity().apply {
                uid = 42L
                serviceId = 0
                url = "https://example.invalid/channel/42"
                name = "Late channel"
            }
            database.subscriptionDAO().insert(subscription)
            database.feedGroupDAO().insert(FeedGroupEntity(
                groupId, "Late membership", FeedGroupIcon.ALL,
                contentSelection = FeedContentSelection.VIDEOS
            ))
            database.feedGroupDAO().insertSubscriptionsToGroup(listOf(
                FeedGroupSubscriptionEntity(groupId, subscription.uid, FeedContentSelection.LIVE)
            ))
            val group = database.feedGroupDAO().getGroup(groupId).blockingGet()
            val memberships = database.feedGroupDAO().getSubscriptionsForGroup(groupId)
                .firstOrError().blockingGet()

            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val dialog = FeedGroupDialog.newInstance(groupId)
                    dialog.showNow(activity.supportFragmentManager, "late-content-rules")
                    val model = FeedGroupDialog::class.java.getDeclaredField("viewModel").apply {
                        isAccessible = true
                    }.get(dialog) as FeedGroupDialogViewModel
                    // Deliver the two initial callbacks in the order a slow membership query allows.
                    model.groupLiveData.removeObservers(dialog.viewLifecycleOwner)
                    model.subscriptionsLiveData.removeObservers(dialog.viewLifecycleOwner)
                    FeedGroupDialogViewModel::class.java.getDeclaredField("feedDatabaseManager")
                        .apply { isAccessible = true }.set(model, FeedDatabaseManager(database))
                    FeedGroupDialog::class.java.getDeclaredMethod(
                        "handleGroup", FeedGroupEntity::class.java
                    ).apply { isAccessible = true }.invoke(dialog, group)

                    val editor = dialog.requireView().findViewById<View>(R.id.content_rules_button)
                    val canEditBeforeMemberships = editor.isEnabled
                    fun addShorts() {
                        editor.performClick()
                        dialog.requireView().findViewById<CheckBox>(R.id.default_content_shorts)
                            .performClick()
                    }
                    if (canEditBeforeMemberships) addShorts()
                    FeedGroupDialog::class.java.getDeclaredMethod(
                        "setupSubscriptionPicker", List::class.java, List::class.java, List::class.java
                    ).apply { isAccessible = true }.invoke(
                        dialog, listOf(PickerSubscriptionItem(subscription)),
                        listOf(PickerSubscriptionItem(subscription)), memberships
                    )
                    // Gating the editor until hydration is also a valid way to preserve the rule.
                    if (!canEditBeforeMemberships) {
                        assertTrue(editor.isEnabled)
                        addShorts()
                    }
                    val confirm = dialog.requireView().findViewById<View>(R.id.confirm_button)
                    confirm.performClick()
                    confirm.performClick()
                }

                val deadline = SystemClock.uptimeMillis() + 5000
                var savedMask: Int?
                do {
                    savedMask = database.feedGroupDAO().getGroup(groupId).blockingGet()
                        ?.contentSelection?.mask
                    if (savedMask == 3) break
                    SystemClock.sleep(20)
                } while (SystemClock.uptimeMillis() < deadline)
                assertEquals("The default edit must be saved before checking the exception", 3, savedMask)
                assertEquals(
                    "A default edit before initial membership delivery must preserve saved exceptions",
                    FeedContentSelection.LIVE,
                    database.feedGroupDAO().getSubscriptionsForGroup(groupId)
                        .firstOrError().blockingGet().single().contentSelectionOverride
                )
            }
        } finally {
            database.close()
        }
    }

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
