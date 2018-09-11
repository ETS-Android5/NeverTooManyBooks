/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.widgets;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;

/**
 * TODO: RecyclerView
 * If you are considering using array adapter with a ListView, consider using
 * {@link android.support.v7.widget.RecyclerView} instead.
 * RecyclerView offers similar features with better performance and more flexibility than
 * ListView provides.
 * See the
 * <a href="https://developer.android.com/guide/topics/ui/layout/recyclerview.html">
 * Recycler View</a> guide.</p>
 *
 *
 *
 * ArrayAdapter to manage rows of an arbitrary type with row movement via clicking
 * on predefined sub-views, if present.
 *
 * The layout can optionally contain these "@+id/" :
 *
 * - row_details         onRowClick
 * - if no 'id/row_details' found, then 'id/row' is tried instead
 * - ROW_UP              onRowUp
 * - ROW_DOWN            onRowDown
 * - ROW_DELETE          onRowDelete
 *
 * ids.xml has these predefined:
 * <pre>
 * 		<item name="row_details" type="id"/>
 * 		<item name="row" type="id"/>
 *     	<item name="ROW_UP" type="id"/>
 * 		<item name="ROW_DOWN" type="id"/>
 * 		<item name="ROW_DELETE" type="id"/>
 *     	<item name="ROW_POSITION" type="id" />
 *     	<item name="TAG_POSITION" type="id" />
 * 	</pre>
 *
 * @author Philip Warner
 */
public abstract class SimpleListAdapter<T> extends ArrayAdapter<T> {
    private final int mRowViewId;
    private final ArrayList<T> mItems;

    private final View.OnLongClickListener mRowLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            try {
                int pos = getViewRow(v);
                T item = getItem(pos);
                return onRowLongClick(v, item, pos);
            } catch (Exception e) {
                Logger.logError(e);
            }
            return false;
        }
    };
    private final View.OnClickListener mRowClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                int pos = getViewRow(v);
                T item = getItem(pos);
                onRowClick(v, item, pos);
            } catch (Exception e) {
                Logger.logError(e);
            }
        }
    };

    private final View.OnClickListener mRowDeleteListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            try {
                int pos = getViewRow(v);
                T old = getItem(pos);
                if (onRowDelete(v, old, pos)) {
                    remove(old);
                    notifyDataSetChanged();
                    onListChanged();
                }
            } catch (Exception e) {
                // TODO: Allow a specific exception to cancel the action
                Logger.logError(e);
            }
        }
    };
    private final View.OnClickListener mRowDownListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int pos = getViewRow(v);
            if (pos == (getCount() - 1))
                return;
            T old = getItem(pos);
            try {
                onRowDown(v, old, pos);

                mItems.set(pos, getItem(pos + 1));
                mItems.set(pos + 1, old);
                notifyDataSetChanged();
                onListChanged();
            } catch (Exception e) {
                // TODO: Allow a specific exception to cancel the action
                Logger.logError(e);
            }
        }
    };
    private final View.OnClickListener mRowUpListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int pos = getViewRow(v);
            if (pos == 0)
                return;
            T old = getItem(pos - 1);
            try {
                onRowUp(v, old, pos);

                mItems.set(pos - 1, getItem(pos));
                mItems.set(pos, old);
                notifyDataSetChanged();
                onListChanged();
            } catch (Exception e) {
                // TODO: Allow a specific exception to cancel the action
                Logger.logError(e);
            }

        }
    };

    // Flag fields to (slightly) optimize lookups and prevent looking for fields that are not there.
    private boolean mCheckedFields = false;
    private boolean mHasPosition = false;
    private boolean mHasUp = false;
    private boolean mHasDown = false;
    private boolean mHasDelete = false;

    protected SimpleListAdapter(Context context, int rowViewId, ArrayList<T> items) {
        super(context, rowViewId, items);
        mRowViewId = rowViewId;
        mItems = items;
    }

    protected void onListChanged() {
    }

    /**
     * Called when an otherwise inactive part of the row is clicked.
     *
     * @param target The view clicked
     * @param object The object associated with this row
     */
    protected void onRowClick(View target, T object, int position) {
    }

    /**
     * Called when an otherwise inactive part of the row is long clicked.
     *
     * @param target The view clicked
     * @param object The object associated with this row
     *
     * @return true if handled
     */
    protected boolean onRowLongClick(View target, T object, int position) {
        return true;
    }

    /**
     * @return true if delete is allowed to happen
     */
    protected boolean onRowDelete(View target, T object, int position) {
        return true;
    }

    protected void onRowDown(View target, T object, int position) {
    }

    protected void onRowUp(View target, T object, int position) {
    }

    /**
     * Call to set up the row view. This is called by the original {@link #getView}
     *  @param convertView The target row view object
     * @param object The object (or type T) from which to draw values.
     */
    abstract protected void onSetupView(int position, View convertView, T object);

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        final T object = this.getItem(position);

        // Get the view; if not defined, load it.
        if (convertView == null) {
            LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            // If possible, ask the object for the view ID
            if (object != null && object instanceof ViewProvider) {
                //noinspection ConstantConditions
                convertView = vi.inflate(((ViewProvider) object).getViewId(), null);
            } else {
                //noinspection ConstantConditions
                convertView = vi.inflate(mRowViewId, null);
            }
        }

        // Save this views position
        ViewTagger.setTag(convertView, R.id.TAG_POSITION, position);

        // If we use a TouchListView, then don't enable the whole row, so grabber/del btns keep working
        View row = convertView.findViewById(R.id.row_details);
        if (row == null) {
            if (BuildConfig.DEBUG) {
                System.out.println("R.id.row_details NOT found in " + this);
            }
            // but if we did not define a details row, try row anyhow
            row = convertView.findViewById(R.id.row);
            if (BuildConfig.DEBUG) {
                System.out.println("Using R.id.row instead");
            }
        }

        if (row != null) {
            row.setOnLongClickListener(mRowLongClickListener);
            row.setOnClickListener(mRowClickListener);
            row.setFocusable(false);
        }

        // If the object is not null, do some processing
        if (object != null) {
            // Try to set position value
            if (mHasPosition || !mCheckedFields) {
                TextView pt = convertView.findViewById(R.id.ROW_POSITION);
                if (pt != null) {
                    mHasPosition = true;
                    String text = Integer.toString(position + 1);
                    pt.setText(text);
                }
            }

            // Try to set the UP handler
            if (mHasUp || !mCheckedFields) {
                ImageView up = convertView.findViewById(R.id.ROW_UP);
                if (up != null) {
                    up.setOnClickListener(mRowUpListener);
                    mHasUp = true;
                }
            }

            // Try to set the DOWN handler
            if (mHasDown || !mCheckedFields) {
                ImageView dn = convertView.findViewById(R.id.ROW_DOWN);
                if (dn != null) {
                    dn.setOnClickListener(mRowDownListener);
                    mHasDown = true;
                }
            }

            // Try to set the DELETE handler
            if (mHasDelete || !mCheckedFields) {
                ImageView del = convertView.findViewById(R.id.ROW_DELETE);
                if (del != null) {
                    del.setOnClickListener(mRowDeleteListener);
                    mHasDelete = true;
                }
            }

            // Ask the subclass to set other fields.
            try {
                onSetupView(position, convertView, object);
            } catch (Exception e) {
                Logger.logError(e);
            }
            convertView.setBackgroundResource(android.R.drawable.list_selector_background);

            mCheckedFields = true;
        }
        return convertView;
    }

    /**
     * Find the first ancestor that has the ID R.id.row. This
     * will be the complete row View. Use the TAG on that to get
     * the physical row number.
     *
     * @param v View to search from
     *
     * @return The row view.
     */
    public Integer getViewRow(@NonNull View v) {
        while (v.getId() != R.id.row) {
            ViewParent p = v.getParent();
            if (!(p instanceof View)) {
                throw new RuntimeException("Could not find row view in view ancestors");
            }
            v = (View) p;
        }
        Object o = ViewTagger.getTag(v, R.id.TAG_POSITION);
        if (o == null)
            throw new RuntimeException("A view with the tag R.id.row was found, but it is not the view for the row");
        return (Integer) o;
    }

    /**
     * Interface to allow underlying objects to determine their view ID.
     */
    public interface ViewProvider {
        @SuppressWarnings("SameReturnValue")
        int getViewId();
    }

}