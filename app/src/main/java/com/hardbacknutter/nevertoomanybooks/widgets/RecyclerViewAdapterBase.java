/*
 * @Copyright 2018-2021 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * NeverTooManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverTooManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverTooManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.ItemTouchHelperAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * Base class for implementing a RecyclerView with Drag&Drop support for re-arranging rows.
 * Supports an optional 'delete' button as well.
 * <p>
 * See {@link ItemTouchHelperViewHolderBase} for the matching base class for the ViewHolder.
 *
 * @param <Item> list item type
 * @param <VHT>  ViewHolder type
 */
public abstract class RecyclerViewAdapterBase<Item, VHT extends ItemTouchHelperViewHolderBase>
        extends RecyclerView.Adapter<VHT>
        implements ItemTouchHelperAdapter {

    @NonNull
    private final List<Item> mItems;
    /** Optional. */
    @Nullable
    private final StartDragListener mDragStartListener;
    /** Cached inflater. */
    @NonNull
    private final LayoutInflater mInflater;
    @NonNull
    private final Context mContext;

    /**
     * Constructor.
     *
     * @param context           Current context
     * @param items             List of items
     * @param dragStartListener Listener to handle the user moving rows up and down
     */
    protected RecyclerViewAdapterBase(@NonNull final Context context,
                                      @NonNull final List<Item> items,
                                      @Nullable final StartDragListener dragStartListener) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mDragStartListener = dragStartListener;
        mItems = items;
    }

    @NonNull
    protected LayoutInflater getLayoutInflater() {
        return mInflater;
    }

    @NonNull
    protected Context getContext() {
        return mContext;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    @CallSuper
    public void onBindViewHolder(@NonNull final VHT holder,
                                 final int position) {

        if (holder.mDeleteButton != null) {
            holder.mDeleteButton.setOnClickListener(v -> {
                final int adapterPosition = holder.getBindingAdapterPosition();
                // 2019-09-25: yes, we CAN (and did) get a NO_POSITION value here. So check it!
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    // don't touch the item list, but update the screen.
                    notifyDataSetChanged();
                } else {
                    // situation normal.
                    onDelete(adapterPosition, getItem(adapterPosition));
                }
            });
        }

        // If we support drag drop re-ordering,
        if (mDragStartListener != null && holder.mDragHandleView != null) {
            // Start a drag whenever the handle view is touched
            holder.mDragHandleView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mDragStartListener.onStartDrag(holder);
                }
                return false;
            });
        }
    }

    protected void onDelete(final int adapterPosition,
                            @NonNull final Item item) {
        mItems.remove(item);
        notifyItemRemoved(adapterPosition);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    public Item getItem(final int position) {
        return mItems.get(position);
    }

    /**
     * Note that we're changing the position of the item every time the
     * view is shifted to a new index, and not at the end of a “drop” event.
     *
     * @param fromPosition The start position of the moved item.
     * @param toPosition   The resolved position of the moved item.
     *
     * @return {@code true} if a move was done, {@code false} if not.
     */
    @Override
    @CallSuper
    public boolean onItemMove(final int fromPosition,
                              final int toPosition) {
        Collections.swap(mItems, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    /**
     * Swiping a row will by default call {@link #onDelete(int, Object)}.
     *
     * @param position The position of the item swiped.
     */
    @Override
    @CallSuper
    public void onItemSwiped(final int position) {
        onDelete(position, mItems.get(position));
    }
}
