/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.booklist;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckedTextView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapterRowActionListener;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.booklist.BooklistStyleGroupsActivity.GroupWrapper;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Activity to edit the groups associated with a style (include/exclude and/or move up/down)
 *
 * @author Philip Warner
 */
public class BooklistStyleGroupsActivity extends EditObjectListActivity<GroupWrapper> {

    public static final int REQUEST_CODE = UniqueId.ACTIVITY_REQUEST_CODE_BOOKLIST_STYLE_GROUPS;

    private static final String TAG = "StyleEditor";
    /** Preferences setup */
    public static final String REQUEST_BKEY_STYLE = TAG + ".Style";
    public static final String REQUEST_BKEY_SAVE_TO_DATABASE = TAG + ".SaveToDb";

    /** Copy of the style we are editing */
    private BooklistStyle mStyle;
    /** Copy of flag passed by calling activity to indicate changes made here should be saved on exit */
    private boolean mSaveToDb = true;

    /**
     * Constructor
     */
    public BooklistStyleGroupsActivity() {
        super(R.layout.booklist_style_edit_group_list, R.layout.row_edit_booklist_style, null);
    }

    @Override
    @CallSuper
    protected void onCreate(final @Nullable Bundle savedInstanceState) {
        Tracker.enterOnCreate(this, savedInstanceState);
        // Get the intent and get the style and other settings
        Intent intent = this.getIntent();
        mStyle = intent.getParcelableExtra(REQUEST_BKEY_STYLE);

        if (intent.hasExtra(REQUEST_BKEY_SAVE_TO_DATABASE)) {
            mSaveToDb = intent.getBooleanExtra(REQUEST_BKEY_SAVE_TO_DATABASE, true);
        }

        /* Indicated this activity was called without an existing style */
        if (mStyle == null) {
            mStyle = new BooklistStyle("");
        }

        // Init the subclass now that we have the style
        super.onCreate(savedInstanceState);

        this.setTitle(getString(R.string.groupings) + ": " + mStyle.getDisplayName());

        if (savedInstanceState == null) {
            HintManager.displayHint(this.getLayoutInflater(), R.string.hint_booklist_style_groups, null);
        }
        Tracker.exitOnCreate(this);
    }

    /**
     * Required by parent class since we do not pass a key for the intent to get the list.
     */
    @Nullable
    @Override
    protected ArrayList<GroupWrapper> getList() {
        // Build an array list with the groups from the style
        ArrayList<GroupWrapper> groups = new ArrayList<>();
        for (BooklistGroup g : mStyle) {
            groups.add(new GroupWrapper(g, true));
        }

        // Get all other groups and add any missing ones to the list
        for (BooklistGroup g : BooklistGroup.getAllGroups()) {
            if (!mStyle.hasKind(g.kind)) {
                groups.add(new GroupWrapper(g, false));
            }
        }

        return groups;
    }

    /**
     * Save the style in the resulting Intent
     */
    @Override
    protected boolean onSave(final @NonNull Intent intent) {
        // Save the properties of this style
        Properties props = mStyle.getProperties();
        // Loop through ALL groups
        for (GroupWrapper wrapper : mList) {
            // Remove it from style
            mStyle.removeGroup(wrapper.group.kind);
            // Add it back, if required.
            // Add then move ensures order will also match
            if (wrapper.present) {
                mStyle.addGroup(wrapper.group);
            }
        }

        // Apply any saved properties.
        mStyle.setProperties(props);

        // Store in resulting Intent
        intent.putExtra(REQUEST_BKEY_STYLE, (Parcelable) mStyle); /* 06ed8d0e-7120-47aa-b47e-c0cd46361dcb */

        // Save to DB if necessary
        if (mSaveToDb) {
            mDb.insertOrUpdateBooklistStyle(mStyle);
        }

        return true;
    }

    protected SimpleListAdapter<GroupWrapper> createListAdapter(final @LayoutRes int rowViewId, final @NonNull ArrayList<GroupWrapper> list) {
        return new GroupWrapperListAdapter(this, rowViewId, list);
    }

    /**
     * We build a list of GroupWrappers which is passed to the underlying class for editing.
     * The wrapper includes extra details needed by this activity.
     *
     * @author Philip Warner
     */
    public static class GroupWrapper implements Serializable, Parcelable {
        private static final long serialVersionUID = 3108094089675884238L;
        /** The actual group */
        @NonNull
        final BooklistGroup group;
        /** Whether this groups is present in the style */
        boolean present;

        /** Constructor */
        GroupWrapper(final @NonNull BooklistGroup group, final boolean present) {
            this.group = group;
            this.present = present;
        }


        protected GroupWrapper(Parcel in) {
            group = in.readParcelable(BooklistGroup.class.getClassLoader());
            present = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeParcelable(group, flags);
            dest.writeByte((byte) (present ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<GroupWrapper> CREATOR = new Creator<GroupWrapper>() {
            @Override
            public GroupWrapper createFromParcel(Parcel in) {
                return new GroupWrapper(in);
            }

            @Override
            public GroupWrapper[] newArray(int size) {
                return new GroupWrapper[size];
            }
        };
    }

    protected class GroupWrapperListAdapter extends SimpleListAdapter<GroupWrapper> implements SimpleListAdapterRowActionListener<GroupWrapper> {
        GroupWrapperListAdapter(final @NonNull Context context, final @LayoutRes int rowViewId, final @NonNull ArrayList<GroupWrapper> items) {
            super(context, rowViewId, items);
        }

        @Override
        public void onGetView(final @NonNull View target, final @NonNull GroupWrapper wrapper) {
            Holder holder = ViewTagger.getTag(target, R.id.TAG_HOLDER);// value BooklistStyleGroupsActivity.Holder
            if (holder == null) {
                // New view, so build the Holder
                holder = new Holder();
                holder.name = target.findViewById(R.id.name);
                holder.present = target.findViewById(R.id.present);
                // Tag the parts that need it
                ViewTagger.setTag(target, R.id.TAG_HOLDER, holder);// value BooklistStyleGroupsActivity.Holder
                ViewTagger.setTag(holder.present, R.id.TAG_HOLDER, holder);// value BooklistStyleGroupsActivity.Holder

                // Handle a click on the CheckedTextView
                holder.present.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(@NonNull View v) {
                        Holder h = ViewTagger.getTagOrThrow(v, R.id.TAG_HOLDER);// value BooklistStyleGroupsActivity.Holder
                        boolean newStatus = !h.wrapper.present;
                        h.wrapper.present = newStatus;
                        h.present.setChecked(newStatus);
                    }
                });
            }
            // Setup the variant fields in the holder
            holder.wrapper = wrapper;
            holder.name.setText(wrapper.group.getName());
            holder.present.setChecked(holder.wrapper.present);
        }
    }

    /**
     * Holder pattern for each row.
     *
     * @author Philip Warner
     */
    private class Holder {
        GroupWrapper wrapper;
        TextView name;
        CheckedTextView present;
    }
}
