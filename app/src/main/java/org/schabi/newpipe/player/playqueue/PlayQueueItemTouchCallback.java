package org.schabi.newpipe.player.playqueue;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.views.ItemDragTouchHelperCallback;

public abstract class PlayQueueItemTouchCallback extends ItemDragTouchHelperCallback {
    private int pendingAnchorPosition = RecyclerView.NO_POSITION;
    private int pendingAnchorOffset;

    public PlayQueueItemTouchCallback() {
        super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.RIGHT);
    }

    public abstract void onMove(int sourceIndex, int targetIndex);

    public abstract void onSwiped(int index);

    @Override
    public boolean onMove(final RecyclerView recyclerView, final RecyclerView.ViewHolder source,
                          final RecyclerView.ViewHolder target) {
        if (source.getItemViewType() != target.getItemViewType()) {
            return false;
        }

        final int sourceIndex = source.getBindingAdapterPosition();
        final int targetIndex = target.getBindingAdapterPosition();
        if (sourceIndex == RecyclerView.NO_POSITION || targetIndex == RecyclerView.NO_POSITION
                || sourceIndex == targetIndex) {
            return false;
        }

        pendingAnchorPosition = RecyclerView.NO_POSITION;
        final RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager && targetIndex > sourceIndex) {
            final LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
            final int firstVisiblePosition =
                    linearLayoutManager.findFirstVisibleItemPosition();
            final View firstVisibleView =
                    linearLayoutManager.findViewByPosition(firstVisiblePosition);
            if (firstVisibleView != null) {
                // Preserve the current viewport slot, not the identity of the dragged holder.
                // Capture it anew for each swap so edge scrolling remains free to advance it.
                pendingAnchorPosition = firstVisiblePosition;
                pendingAnchorOffset = linearLayoutManager.getDecoratedTop(firstVisibleView)
                        - recyclerView.getPaddingTop();
            }
        }

        onMove(sourceIndex, targetIndex);
        return true;
    }

    @Override
    public void onSelectedChanged(final RecyclerView.ViewHolder viewHolder,
                                  final int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        pendingAnchorPosition = RecyclerView.NO_POSITION;
    }

    @Override
    public void onMoved(@NonNull final RecyclerView recyclerView,
                        @NonNull final RecyclerView.ViewHolder viewHolder,
                        final int fromPos,
                        @NonNull final RecyclerView.ViewHolder target,
                        final int toPos,
                        final int x,
                        final int y) {
        final RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager
                && pendingAnchorPosition != RecyclerView.NO_POSITION) {
            // RecyclerView otherwise follows the dragged holder when it is the first visible row,
            // making each move expose another target without any further finger motion. Apply the
            // anchor captured before the adapter move changes RecyclerView's position mapping.
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(
                    pendingAnchorPosition, pendingAnchorOffset);
            pendingAnchorPosition = RecyclerView.NO_POSITION;
            return;
        }

        pendingAnchorPosition = RecyclerView.NO_POSITION;
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return false;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return true;
    }

    @Override
    public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int swipeDir) {
        onSwiped(viewHolder.getBindingAdapterPosition());
    }
}
