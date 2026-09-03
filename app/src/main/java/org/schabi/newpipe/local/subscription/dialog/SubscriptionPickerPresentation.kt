package org.schabi.newpipe.local.subscription.dialog

import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog.ScreenState
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog.ScreenState.SubscriptionsPickerScreen
import org.schabi.newpipe.local.subscription.item.PickerSubscriptionItem

internal fun subscriptionPickerPresentationItems(
    subscriptions: List<PickerSubscriptionItem>,
    selectedIds: Set<Long>
): List<PickerSubscriptionItem> = subscriptions.map { subscription ->
    subscription.copy(isSelected = subscription.subscriptionEntity.uid in selectedIds)
}

internal fun effectiveShowOnlyUngrouped(
    screen: ScreenState,
    storedPreference: Boolean
): Boolean = screen is SubscriptionsPickerScreen && storedPreference
