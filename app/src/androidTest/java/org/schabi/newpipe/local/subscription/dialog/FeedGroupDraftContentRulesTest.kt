package org.schabi.newpipe.local.subscription.dialog

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.CheckBox
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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
    fun queuedGroupSaveUsesSubmittedMembershipSnapshot() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val groupId = 424243L
        for (id in listOf(42L, 43L)) {
            database.subscriptionDAO().insert(SubscriptionEntity().apply {
                uid = id
                serviceId = 0
                url = "https://example.invalid/channel/$id"
                name = "Channel $id"
            })
        }
        database.feedGroupDAO().insert(FeedGroupEntity(groupId, "Before save", FeedGroupIcon.ALL))
        database.feedGroupDAO().insertSubscriptionsToGroup(listOf(
            FeedGroupSubscriptionEntity(groupId, 42L)
        ))

        val transactionStarted = CountDownLatch(1)
        val releaseTransaction = CountDownLatch(1)
        val saveCompleted = CountDownLatch(1)
        val transactionError = AtomicReference<Throwable>()
        val store = ViewModelStore()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var model: FeedGroupDialogViewModel? = null
        val observer = Observer<FeedGroupDialogViewModel.DialogEvent> {
            if (it is FeedGroupDialogViewModel.DialogEvent.SuccessEvent) saveCompleted.countDown()
        }
        val transactionHolder = Thread({
            try {
                database.runInTransaction {
                    transactionStarted.countDown()
                    check(releaseTransaction.await(10, TimeUnit.SECONDS))
                }
            } catch (error: Throwable) {
                transactionError.set(error)
            }
        }, "feed-group-save-gate").apply {
            isDaemon = true
            start()
        }
        try {
            assertTrue("The competing Room transaction must hold the save",
                transactionStarted.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                model = ViewModelProvider(store, FeedGroupDialogViewModel.Factory(context, groupId))
                    .get(FeedGroupDialogViewModel::class.java)
                FeedGroupDialogViewModel::class.java.getDeclaredField("feedDatabaseManager")
                    .apply { isAccessible = true }.set(model, FeedDatabaseManager(database))
                model!!.dialogEventLiveData.observeForever(observer)
                val submittedMembership = hashSetOf(42L)
                model!!.updateGroup("Submitted membership", FeedGroupIcon.ALL, submittedMembership,
                    0, FeedContentSelection.ALL, emptyMap())
                // Mutate the caller's set while the real Room transaction keeps its save queued.
                submittedMembership.remove(42L)
                submittedMembership.add(43L)
            }
            releaseTransaction.countDown()
            assertTrue("The queued save must complete", saveCompleted.await(5, TimeUnit.SECONDS))
            assertEquals("Queued saves must preserve submitted membership", setOf(42L), database.feedGroupDAO().getSubscriptionsForGroup(groupId)
                .firstOrError().blockingGet().map { it.subscriptionId }.toSet())
        } finally {
            releaseTransaction.countDown()
            transactionHolder.join(5000)
            instrumentation.runOnMainSync {
                model?.dialogEventLiveData?.removeObserver(observer)
                store.clear()
            }
            database.close()
        }
        assertFalse("The transaction gate must not leave a worker running", transactionHolder.isAlive)
        transactionError.get()?.let { throw AssertionError("The transaction gate failed", it) }
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
