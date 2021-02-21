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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.dao.FormatDao;

/**
 * Dialog to edit an <strong>in-line in Books table</strong> Format.
 */
public class EditFormatDialogFragment
        extends EditStringBaseDialogFragment {

    /** Fragment/Log tag. */
    public static final String TAG = "EditFormatDialogFrag";

    /**
     * No-arg constructor for OS use.
     */
    public EditFormatDialogFragment() {
        super(R.string.lbl_format, R.string.lbl_format, BooksOnBookshelf.RowChangeListener.FORMAT);
    }

    /**
     * Launch the dialog.
     *
     * @param text to edit.
     */
    public static void launch(@NonNull final FragmentActivity activity,
                              @NonNull final String text) {

        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, BooksOnBookshelf.RowChangeListener.REQUEST_KEY);
        args.putString(BKEY_TEXT, text);

        final DialogFragment frag = new EditFormatDialogFragment();
        frag.setArguments(args);
        frag.show(activity.getSupportFragmentManager(), TAG);
    }

    @NonNull
    @Override
    protected List<String> getList() {
        return FormatDao.getInstance().getList();
    }

    @Override
    void onSave(@NonNull final String originalText,
                @NonNull final String currentText) {
        FormatDao.getInstance().update(originalText, currentText);
    }
}
