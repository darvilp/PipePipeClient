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
    private int lastMoveDirection;
    private boolean returningFromTopEdge;
    private int topEdgeReturnAnchorPosition = RecyclerView.NO_POSITION;
    private int topEdgeReturnAnchorOffset;

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
        final int moveDirection = Integer.signum(targetIndex - sourceIndex);
        if (moveDirection < 0) {
            returningFromTopEdge = false;
            topEdgeReturnAnchorPosition = RecyclerView.NO_POSITION;
        } else if (lastMoveDirection < 0 && sourceIndex == 0) {
            returningFromTopEdge = true;
        }
        final RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager && targetIndex > sourceIndex) {
            final LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
            final int firstVisiblePosition =
                    linearLayoutManager.findFirstVisibleItemPosition();
            if (returningFromTopEdge && firstVisiblePosition != RecyclerView.NO_POSITION) {
                if (topEdgeReturnAnchorPosition == RecyclerView.NO_POSITION) {
                    final View firstVisibleView =
                            linearLayoutManager.findViewByPosition(firstVisiblePosition);
                    topEdgeReturnAnchorPosition = firstVisiblePosition;
                    topEdgeReturnAnchorOffset = firstVisibleView == null
                            ? 0 : firstVisibleView.getTop() - recyclerView.getPaddingTop();
                }
                // Keep one fixed viewport anchor while the selected row returns from the top.
                // Advancing the anchor with sourceIndex feeds each adapter move back into layout,
                // which immediately exposes another target and races through the queue.
                pendingAnchorPosition = topEdgeReturnAnchorPosition;
                pendingAnchorOffset = topEdgeReturnAnchorOffset;
            } else if (firstVisiblePosition == sourceIndex) {
                final View firstVisibleView =
                        linearLayoutManager.findViewByPosition(firstVisiblePosition);
                // Anchor the row that follows the dragged holder to prevent RecyclerView from
                // exposing new targets without further finger movement.
                pendingAnchorPosition = sourceIndex + 1;
                pendingAnchorOffset = firstVisibleView == null
                        ? 0 : firstVisibleView.getTop() - recyclerView.getPaddingTop();
            }
        }

        lastMoveDirection = moveDirection;
        onMove(sourceIndex, targetIndex);
        return true;
    }

    @Override
    public void onSelectedChanged(final RecyclerView.ViewHolder viewHolder,
                                  final int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            lastMoveDirection = 0;
            returningFromTopEdge = false;
            topEdgeReturnAnchorPosition = RecyclerView.NO_POSITION;
        }
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
