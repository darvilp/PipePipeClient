package org.schabi.newpipe.player.playqueue;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.views.ItemDragTouchHelperCallback;

public abstract class PlayQueueItemTouchCallback extends ItemDragTouchHelperCallback {

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

        onMove(sourceIndex, targetIndex);
        return true;
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
