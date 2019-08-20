/*
 * @Copyright 2019 HardBackNutter
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
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;

/**
 * Dialog to add a new TOCEntry, or edit an existing one.
 * <p>
 * Show with the {@link Fragment#getChildFragmentManager()}
 * <p>
 * Uses {@link Fragment#getParentFragment()} for sending results back.
 */
public class EditTocEntryDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    public static final String TAG = "EditTocEntryDialogFragment";

    private static final String BKEY_HAS_MULTIPLE_AUTHORS = TAG + ":hasMultipleAuthors";
    private static final String BKEY_TOC_ENTRY = TAG + ":tocEntry";

    private AutoCompleteTextView mAuthorTextView;
    private EditText mTitleTextView;
    private EditText mPubDateTextView;

    private boolean mHasMultipleAuthors;
    private TocEntry mTocEntry;

    /** Database Access. */
    private DAO mDb;

    private WeakReference<EditTocEntryResults> mListener;

    /**
     * Constructor.
     *
     * @param tocEntry           to edit.
     * @param hasMultipleAuthors Flag that will enable/disable the author edit field
     *
     * @return the instance
     */
    public static EditTocEntryDialogFragment newInstance(@NonNull final TocEntry tocEntry,
                                                         final boolean hasMultipleAuthors) {
        EditTocEntryDialogFragment frag = new EditTocEntryDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, hasMultipleAuthors);
        args.putParcelable(BKEY_TOC_ENTRY, tocEntry);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Call this from {@link #onAttachFragment} in the parent.
     *
     * @param listener the object to send the result to.
     */
    public void setListener(@NonNull final EditTocEntryResults listener) {
        mListener = new WeakReference<>(listener);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO();

        Bundle currentArgs = savedInstanceState != null ? savedInstanceState : requireArguments();
        mTocEntry = currentArgs.getParcelable(BKEY_TOC_ENTRY);
        mHasMultipleAuthors = currentArgs.getBoolean(BKEY_HAS_MULTIPLE_AUTHORS, false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        // Reminder: *always* use the activity inflater here.
        //noinspection ConstantConditions
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        final View root = layoutInflater.inflate(R.layout.dialog_edit_book_toc, null);

        mAuthorTextView = root.findViewById(R.id.author);
        mTitleTextView = root.findViewById(R.id.title);
        mPubDateTextView = root.findViewById(R.id.first_publication);

        if (mHasMultipleAuthors) {
            @SuppressWarnings("ConstantConditions")
            ArrayAdapter<String> authorAdapter =
                    new ArrayAdapter<>(getContext(),
                                       android.R.layout.simple_dropdown_item_1line,
                                       mDb.getAuthorNames(DBDefinitions.KEY_AUTHOR_FORMATTED));
            mAuthorTextView.setAdapter(authorAdapter);

            mAuthorTextView.setText(mTocEntry.getAuthor().getLabel());
            mAuthorTextView.setVisibility(View.VISIBLE);
        } else {
            mAuthorTextView.setVisibility(View.GONE);
        }

        mTitleTextView.setText(mTocEntry.getTitle());
        mPubDateTextView.setText(mTocEntry.getFirstPublication());

        //noinspection ConstantConditions
        return new AlertDialog.Builder(getContext())
                       .setIconAttribute(android.R.attr.alertDialogIcon)
                       .setView(root)
                       .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                       .setPositiveButton(android.R.string.ok, this::onConfirm)
                       .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BKEY_TOC_ENTRY, mTocEntry);
        outState.putBoolean(BKEY_HAS_MULTIPLE_AUTHORS, mHasMultipleAuthors);
    }

    @Override
    public void onPause() {
        getFields();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    private void getFields() {
        mTocEntry.setTitle(mTitleTextView.getText().toString().trim());
        mTocEntry.setFirstPublication(mPubDateTextView.getText().toString().trim());
        if (mHasMultipleAuthors) {
            mTocEntry.setAuthor(Author.fromString(mAuthorTextView.getText().toString().trim()));
        }
    }

    private void onConfirm(@SuppressWarnings("unused") @NonNull final DialogInterface d,
                           @SuppressWarnings("unused") final int which) {
        getFields();
        if (mListener.get() != null) {
            mListener.get().addOrUpdateEntry(mTocEntry);
        } else {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                Logger.debug(this, "onConfirm",
                             Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
            }
        }
    }

    public interface EditTocEntryResults {

        void addOrUpdateEntry(@NonNull TocEntry tocEntry);
    }
}
