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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveInfo;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.backup.base.OptionsDialogBase;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogImportOptionsBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;

public class ImportHelperDialogFragment
        extends OptionsDialogBase<ImportManager> {

    /** Log tag. */
    public static final String TAG = "ImportHelperDialogFragment";
    private static final String BKEY_OPTIONS = TAG + ":options";

    private ImportHelperViewModel mModel;
    /** View Binding. */
    private DialogImportOptionsBinding mVb;
    /** Indicates if we're importing from a CSV file. */
    private boolean mIsCsvBooks;

    /**
     * Constructor.
     *
     * @param requestKey for use with the FragmentResultListener
     * @param manager    import configuration; must have a valid Uri set.
     *
     * @return instance
     */
    @NonNull
    public static DialogFragment newInstance(@SuppressWarnings("SameParameterValue")
                                             @NonNull final String requestKey,
                                             @NonNull final ImportManager manager) {
        final DialogFragment frag = new ImportHelperDialogFragment();
        final Bundle args = new Bundle(2);
        args.putString(BKEY_REQUEST_KEY, requestKey);
        args.putParcelable(BKEY_OPTIONS, manager);
        frag.setArguments(args);
        return frag;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {

        mModel = new ViewModelProvider(this).get(ImportHelperViewModel.class);
        //noinspection ConstantConditions
        mModel.init(getContext(), requireArguments());

        mVb = DialogImportOptionsBinding.inflate(getLayoutInflater());

        mIsCsvBooks = mModel.isCsvContainer(getContext());
        setupOptions();

        return new MaterialAlertDialogBuilder(getContext())
                .setView(mVb.getRoot())
                .setTitle(R.string.lbl_import_options)
                .setNegativeButton(android.R.string.cancel, (d, w) -> onCancelled())
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        onOptionsSet(mModel.getHelper()))
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        // do NOT adjust the dialog if we're doing CSV... it's big enough
        if (!mIsCsvBooks) {
            fixDialogWidth(R.dimen.import_dialog_landscape_width);
        }
    }

    /**
     * Set the checkboxes/radio-buttons from the options.
     */
    private void setupOptions() {
        final ImportManager helper = mModel.getHelper();

        if (mIsCsvBooks) {
            // CSV files don't have options other then the books.
            mVb.cbxGroup.setVisibility(View.GONE);

        } else {
            // Populate the options.
            mVb.cbxBooks.setChecked(helper.isSet(Options.BOOKS));
            mVb.cbxBooks.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        helper.setOption(Options.BOOKS, isChecked);
                        mVb.rbBooksGroup.setEnabled(isChecked);
                    });

            mVb.cbxCovers.setChecked(helper.isSet(Options.COVERS));
            mVb.cbxCovers.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> helper.setOption(Options.COVERS, isChecked));

            mVb.cbxPrefsAndStyles.setChecked(helper.isSet(Options.PREFS | Options.STYLES));
            mVb.cbxPrefsAndStyles.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        helper.setOption(Options.PREFS, isChecked);
                        helper.setOption(Options.STYLES, isChecked);
                    });
        }

        // enable or disable the sync option
        //noinspection ConstantConditions
        if (mIsCsvBooks || mModel.getArchiveCreationDate(getContext()) != null) {
            final boolean allBooks = !helper.isSet(ImportManager.IMPORT_ONLY_NEW_OR_UPDATED);
            mVb.rbBooksAll.setChecked(allBooks);
            mVb.infoBtnRbBooksAll.setOnClickListener(StandardDialogs::infoPopup);
            mVb.rbBooksSync.setChecked(!allBooks);
            mVb.infoBtnRbBooksSync.setOnClickListener(StandardDialogs::infoPopup);

            mVb.rbBooksGroup.setOnCheckedChangeListener(
                    // We only have two buttons and one option, so just check the pertinent one.
                    (group, checkedId) -> helper.setOption(ImportManager.IMPORT_ONLY_NEW_OR_UPDATED,
                                                           checkedId == mVb.rbBooksSync.getId()));
        } else {
            // If the archive does not have a valid creation-date field, then we can't use sync
            // TODO Maybe change string to "... archive is missing a creation date field.
            mVb.rbBooksGroup.setEnabled(false);
            mVb.rbBooksAll.setChecked(true);
            mVb.rbBooksSync.setChecked(false);
            helper.setOption(ImportManager.IMPORT_ONLY_NEW_OR_UPDATED, false);
            mVb.infoBtnRbBooksSync.setContentDescription(
                    getString(R.string.warning_import_old_archive));
        }
    }

    public static class ImportHelperViewModel
            extends ViewModel {

        /** import configuration. */
        private ImportManager mHelper;

        @Nullable
        private ArchiveInfo mInfo;

        public void init(@NonNull final Context context,
                         @NonNull final Bundle args) {
            mHelper = args.getParcelable(BKEY_OPTIONS);
            Objects.requireNonNull(mHelper);
            try {
                mInfo = mHelper.getInfo(context);
            } catch (@NonNull final IOException | InvalidArchiveException e) {
                // We should never get here, as the archive being valid should have been
                // checked before creating the dialog.
                throw new IllegalStateException(e);
            }
        }

        @NonNull
        ImportManager getHelper() {
            return mHelper;
        }

        @Nullable
        ArchiveInfo getInfo() {
            return mInfo;
        }

        /**
         * Get the archive creation date.
         *
         * @param context Current context
         *
         * @return the date, or {@code null} if none present
         */
        @Nullable
        LocalDateTime getArchiveCreationDate(@NonNull final Context context) {
            if (mInfo == null) {
                return null;
            } else {
                return mInfo.getCreationDate(context);
            }
        }

        boolean isCsvContainer(@NonNull final Context context) {
            return ArchiveContainer.CsvBooks.equals(mHelper.getContainer(context));
        }
    }
}
