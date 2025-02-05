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
package com.hardbacknutter.nevertoomanybooks.dialogs.entities;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogEditBookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

/**
 * Dialog to edit an <strong>EXISTING or NEW</strong> {@link Bookshelf}.
 */
public class EditBookshelfDialogFragment
        extends FFBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditBookshelfDialogFrag";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String mRequestKey;

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

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        mRequestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        mBookshelf = Objects.requireNonNull(args.getParcelable(DBKey.FK_BOOKSHELF),
                                            DBKey.FK_BOOKSHELF);

        if (savedInstanceState == null) {
            mName = mBookshelf.getName();
        } else {
            //noinspection ConstantConditions
            mName = savedInstanceState.getString(DBKey.BOOKSHELF_NAME);
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVb = DialogEditBookshelfBinding.bind(view);

        mVb.bookshelf.setText(mName);
        mVb.bookshelf.requestFocus();
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (saveChanges()) {
                dismiss();
            }
            return true;
        }
        return false;
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

        // store changes
        mBookshelf.setName(mName);

        final BookshelfDao bookshelfDao = ServiceLocator.getInstance().getBookshelfDao();

        // check if it already exists (will be 0 if not)
        final long existingId = bookshelfDao.find(mBookshelf);

        // are we adding a new one but trying to use an existing name?
        if ((mBookshelf.getId() == 0) && (existingId != 0)) {
            final Context context = getContext();

            //noinspection ConstantConditions
            final String msg = context.getString(R.string.warning_x_already_exists,
                                                 context.getString(R.string.lbl_bookshelf));
            showError(mVb.lblBookshelf, msg);
            return false;
        }

        if (existingId == 0) {
            final boolean success;
            if (mBookshelf.getId() == 0) {
                //noinspection ConstantConditions
                success = bookshelfDao.insert(getContext(), mBookshelf) > 0;
            } else {
                //noinspection ConstantConditions
                success = bookshelfDao.update(getContext(), mBookshelf);
            }
            if (success) {
                Launcher.setResult(this, mRequestKey, mBookshelf.getId());
                return true;
            }
        } else {
            // Merge the 2
            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(mBookshelf.getLabel(getContext()))
                    .setMessage(R.string.confirm_merge_bookshelves)
                    .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                    .setPositiveButton(R.string.action_merge, (d, w) -> {
                        // move all books from the one being edited to the existing one
                        bookshelfDao.merge(mBookshelf, existingId);

                        Launcher.setResult(this, mRequestKey, existingId);
                        dismiss();
                    })
                    .create()
                    .show();
        }

        return false;
    }

    private void viewToModel() {
        //noinspection ConstantConditions
        mName = mVb.bookshelf.getText().toString().trim();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(DBKey.BOOKSHELF_NAME, mName);
    }

    @Override
    public void onPause() {
        viewToModel();
        super.onPause();
    }

    public abstract static class Launcher
            implements FragmentResultListener {

        private String mRequestKey;
        private FragmentManager mFragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @IntRange(from = 1) final long bookshelfId) {
            final Bundle result = new Bundle(1);
            result.putLong(DBKey.FK_BOOKSHELF, bookshelfId);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            mFragmentManager = fragmentManager;
            mRequestKey = requestKey;
            mFragmentManager.setFragmentResultListener(mRequestKey, lifecycleOwner, this);
        }

        /**
         * Launch the dialog.
         *
         * @param bookshelf to edit.
         */
        public void launch(@NonNull final Bookshelf bookshelf) {

            final Bundle args = new Bundle(2);
            args.putString(BKEY_REQUEST_KEY, mRequestKey);
            args.putParcelable(DBKey.FK_BOOKSHELF, bookshelf);

            final DialogFragment frag = new EditBookshelfDialogFragment();
            frag.setArguments(args);
            frag.show(mFragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(SanityCheck.requirePositiveValue(result.getLong(DBKey.FK_BOOKSHELF),
                                                      DBKey.FK_BOOKSHELF));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param bookshelfId the id of the updated shelf, or of the newly inserted shelf.
         */
        public abstract void onResult(long bookshelfId);
    }
}
