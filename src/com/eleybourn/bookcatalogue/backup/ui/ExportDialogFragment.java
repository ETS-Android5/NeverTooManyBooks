package com.eleybourn.bookcatalogue.backup.ui;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.EditText;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.debug.MustImplementException;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

public class ExportDialogFragment
        extends DialogFragment {

    /** Fragment manager tag. */
    private static final String TAG = ExportDialogFragment.class.getSimpleName();

    private ExportSettings mExportSettings;

    private OnExportTypeSelectionDialogResultsListener mActivity;

    /**
     * (syntax sugar for newInstance)
     */
    public static void show(@NonNull final FragmentManager fm,
                            @NonNull final ExportSettings settings) {
        if (fm.findFragmentByTag(TAG) == null) {
            newInstance(settings).show(fm, TAG);
        }
    }

    /**
     * Constructor.
     *
     * @return Created fragment
     */
    @NonNull
    public static ExportDialogFragment newInstance(@NonNull final ExportSettings settings) {
        ExportDialogFragment frag = new ExportDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_SETTINGS, settings);
        frag.setArguments(args);
        return frag;
    }

    /**
     * Ensure activity supports interface.
     */
    @Override
    @CallSuper
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        if (!(context instanceof OnExportTypeSelectionDialogResultsListener)) {
            throw new MustImplementException(context,
                                             OnExportTypeSelectionDialogResultsListener.class);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_export_options, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        mActivity = (OnExportTypeSelectionDialogResultsListener) requireActivity();
        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mExportSettings = args.getParcelable(UniqueId.BKEY_IMPORT_EXPORT_SETTINGS);

        view.findViewById(R.id.cancel).setOnClickListener(v -> dismiss());

        view.findViewById(R.id.confirm).setOnClickListener(v -> {
            updateOptions();
            mActivity.onExportTypeSelectionDialogResult(mExportSettings);
            dismiss();
        });
    }

    private void updateOptions() {
        Dialog dialog = getDialog();
        // what to export. All checked == ExportSettings.ALL
        //noinspection ConstantConditions
        if (((Checkable) dialog.findViewById(R.id.cbx_xml_tables)).isChecked()) {
            mExportSettings.what |= ExportSettings.XML_TABLES;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_books_csv)).isChecked()) {
            mExportSettings.what |= ExportSettings.BOOK_CSV;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_covers)).isChecked()) {
            mExportSettings.what |= ExportSettings.COVERS;
        }
        if (((Checkable) dialog.findViewById(R.id.cbx_preferences)).isChecked()) {
            mExportSettings.what |= ExportSettings.PREFERENCES | ExportSettings.BOOK_LIST_STYLES;
        }

        Checkable radioSinceLastBackup = dialog.findViewById(R.id.radioSinceLastBackup);
        Checkable radioSinceDate = dialog.findViewById(R.id.radioSinceDate);

        if (radioSinceLastBackup.isChecked()) {
            mExportSettings.what |= ExportSettings.EXPORT_SINCE;
            // it's up to the Exporter to determine/set the last backup date.
            mExportSettings.dateFrom = null;

        } else if (radioSinceDate.isChecked()) {
            EditText dateSinceView = dialog.findViewById(R.id.txtDate);
            try {
                mExportSettings.what |= ExportSettings.EXPORT_SINCE;
                mExportSettings.dateFrom =
                        DateUtils.parseDate(dateSinceView.getText().toString().trim());
            } catch (RuntimeException e) {
                UserMessage.showUserMessage(dateSinceView, R.string.warning_date_not_set);
                mExportSettings.what = ExportSettings.NOTHING;
            }
        }
    }

    @Override
    public void onPause() {
        updateOptions();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(UniqueId.BKEY_IMPORT_EXPORT_SETTINGS, mExportSettings);
    }

    /**
     * Listener interface to receive notifications when dialog is confirmed.
     */
    public interface OnExportTypeSelectionDialogResultsListener {

        void onExportTypeSelectionDialogResult(@NonNull ExportSettings settings);
    }
}
