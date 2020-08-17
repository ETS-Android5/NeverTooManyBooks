/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.BaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Bookshelf}.
 */
public class EditBookshelfDialogFragment
        extends BaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditBookshelfDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;


    /** Database Access. */
    private DAO mDb;
    /** View Binding. */
    private DialogEditBookshelfBinding mVb;

    /** The Bookshelf we're editing. */
    private Bookshelf mBookshelf;

    /** Current edit. */
    private String mName;

    /**
     * No-arg constructor for OS use.
     */
    public EditBookshelfDialogFragment() {
        super(R.layout.dialog_edit_bookshelf);
    }

    /**
     * Constructor.
     *
     * @param requestKey for use with the FragmentResultListener
     * @param bookshelf  to edit.
     *
     * @return instance
     */
    public static DialogFragment newInstance(@SuppressWarnings("SameParameterValue")
                                             @NonNull final String requestKey,
                                             @NonNull final Bookshelf bookshelf) {
        final DialogFragment frag = new EditBookshelfDialogFragment();
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putParcelable(DBDefinitions.KEY_FK_BOOKSHELF, bookshelf);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDb = new DAO(TAG);

        final Bundle args = requireArguments();
        mRequestKey = args.getString(BKEY_REQUEST_KEY);
        mBookshelf = args.getParcelable(DBDefinitions.KEY_FK_BOOKSHELF);
        Objects.requireNonNull(mBookshelf, ErrorMsg.NULL_BOOKSHELF);

        if (savedInstanceState == null) {
            mName = mBookshelf.getName();
        } else {
            mName = savedInstanceState.getString(DBDefinitions.KEY_BOOKSHELF_NAME);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookshelfBinding.bind(view);

        mVb.toolbar.setNavigationOnClickListener(v -> dismiss());
        mVb.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.MENU_SAVE) {
                if (saveChanges()) {
                    dismiss();
                }
                return true;
            }
            return false;
        });

        mVb.bookshelf.setText(mName);
    }

    private boolean saveChanges() {
        viewToModel();
        if (mName.isEmpty()) {
            showError(mVb.lblBookshelf, R.string.vldt_non_blank_required);
            return false;
        }

        // anything actually changed ?
        if (mBookshelf.getName().equals(mName)) {
            return true;
        }

        // check if a shelf with this name already exists (will be null if not)
        final Bookshelf existingShelfWithSameName = mDb.getBookshelfByName(mName);

        // are we adding a new Bookshelf but trying to use an existing name?
        if ((mBookshelf.getId() == 0) && (existingShelfWithSameName != null)) {
            final Context context = getContext();

            //noinspection ConstantConditions
            final String msg = context.getString(R.string.warning_x_already_exists,
                                                 context.getString(R.string.lbl_bookshelf));
            showError(mVb.lblBookshelf, msg);
            return false;
        }

        if (existingShelfWithSameName == null) {
            // It's a simple rename, store changes
            mBookshelf.setName(mName);

            final boolean success;
            if (mBookshelf.getId() == 0) {
                //noinspection ConstantConditions
                success = mDb.insert(getContext(), mBookshelf) > 0;
            } else {
                //noinspection ConstantConditions
                success = mDb.update(getContext(), mBookshelf);
            }
            if (success) {
                OnResultListener.sendResult(this, mRequestKey, mBookshelf.getId(), 0);
                return true;
            }
        } else {
            // Merge the 2 shelves
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(mName)
                    .setMessage(R.string.confirm_merge_bookshelves)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_merge, (d, w) -> {
                        // move all books from the shelf being edited to the existing shelf
                        final long toShelfId = existingShelfWithSameName.getId();
                        final int booksMoved = mDb.mergeBookshelves(mBookshelf.getId(), toShelfId);

                        OnResultListener.sendResult(this, mRequestKey, toShelfId, booksMoved);
                        dismiss();
                    })
                    .create()
                    .show();
        }

        return false;
    }

    private void viewToModel() {
        mName = mVb.bookshelf.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBDefinitions.KEY_BOOKSHELF_NAME, mName);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    public interface OnResultListener
            extends FragmentResultListener {

        /* private. */ String BOOKSHELF_ID = "bookshelfId";
        /* private. */ String BOOKS_MOVED = "booksMoved";

        static void sendResult(@NonNull final Fragment fragment,
                               @NonNull final String requestKey,
                               final long bookshelfId,
                               final int booksMoved) {
            final Bundle result = new Bundle();
            result.putLong(BOOKSHELF_ID, bookshelfId);
            result.putInt(BOOKS_MOVED, booksMoved);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        @Override
        default void onFragmentResult(@NonNull final String requestKey,
                                      @NonNull final Bundle result) {
            onResult(result.getLong(BOOKSHELF_ID), result.getInt(BOOKS_MOVED));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param bookshelfId the id of the updated shelf, or of the newly inserted shelf.
         * @param booksMoved  if a merge took place, the amount of books moved (otherwise 0).
         */
        void onResult(long bookshelfId,
                      int booksMoved);
    }
}
