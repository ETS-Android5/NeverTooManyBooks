/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;

/**
 * Dialog to edit an existing publisher.
 * <p>
 * Calling point is a List.
 */
public class EditPublisherDialogFragment
        extends DialogFragment {

    /** Log tag. */
    public static final String TAG = "EditPublisherDialogFrag";

    /** Database Access. */
    private DAO mDb;

    @Nullable
    private WeakReference<BookChangedListener> mListener;

    private AutoCompleteTextView mNameView;

    /** The Publisher we're editing. */
    private Publisher mPublisher;
    /** Current edit. */
    private String mName;

    /**
     * Constructor.
     *
     * @param publisher to edit.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@NonNull final Publisher publisher) {
        final DialogFragment frag = new EditPublisherDialogFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(DBDefinitions.KEY_PUBLISHER, publisher);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        mPublisher = requireArguments().getParcelable(DBDefinitions.KEY_PUBLISHER);
        Objects.requireNonNull(mPublisher, ErrorMsg.ARGS_MISSING_PUBLISHER);

        if (savedInstanceState == null) {
            mName = mPublisher.getName();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_PUBLISHER);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        final View root = inflater.inflate(R.layout.dialog_edit_publisher, null);

        //noinspection ConstantConditions
        DiacriticArrayAdapter<String> mAdapter = new DiacriticArrayAdapter<>(
                getContext(), R.layout.dropdown_menu_popup_item, mDb.getPublisherNames());

        mNameView = root.findViewById(R.id.name);
        mNameView.setText(mName);
        mNameView.setAdapter(mAdapter);

        return new MaterialAlertDialogBuilder(getContext())
                .setIcon(R.drawable.ic_edit)
                .setView(root)
                .setTitle(R.string.lbl_publisher)
                .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    mName = mNameView.getText().toString().trim();
                    if (mName.isEmpty()) {
                        Snackbar.make(mNameView, R.string.warning_missing_name,
                                      Snackbar.LENGTH_LONG).show();
                        return;
                    }

                    if (mPublisher.getName().equals(mName)) {
                        return;
                    }
                    mDb.updatePublisher(mPublisher.getName(), mName);

                    // and spread the news of the changes.
                    //  Bundle data = new Bundle();
                    //  data.putString(DBDefinitions.KEY_PUBLISHER, mPublisher.getName());
                    if (mListener != null && mListener.get() != null) {
                        mListener.get().onBookChanged(0, BookChangedListener.PUBLISHER, null);
                    } else {
                        if (BuildConfig.DEBUG /* always */) {
                            Log.w(TAG, "onBookChanged|" +
                                       (mListener == null ? ErrorMsg.LISTENER_WAS_NULL
                                                          : ErrorMsg.LISTENER_WAS_DEAD));
                        }
                    }
                })
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_PUBLISHER, mName);
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final BookChangedListener listener) {
        mListener = new WeakReference<>(listener);
    }

    @Override
    public void onPause() {
        mName = mNameView.getText().toString().trim();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }
}
