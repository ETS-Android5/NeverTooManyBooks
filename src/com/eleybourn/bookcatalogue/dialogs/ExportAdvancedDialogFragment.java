package com.eleybourn.bookcatalogue.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.RadioButton;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.Exporter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.ExportSettings;
import com.eleybourn.bookcatalogue.dialogs.ExportTypeSelectionDialogFragment.OnExportTypeSelectionDialogResultListener;
import com.eleybourn.bookcatalogue.utils.DateUtils;

import java.io.File;
import java.util.Objects;

public class ExportAdvancedDialogFragment extends DialogFragment {
    private int mDialogId;
    private File mFile;

//	/**
//	 * Listener interface to receive notifications when dialog is closed by any means.
//	 * 
//	 * @author pjw
//	 */
//	public interface OnExportAdvancedDialogResultListener {
//		public void onExportAdvancedDialogResult(int dialogId, ExportAdvancedDialogFragment dialog, int rowId, File file);
//	}

    /**
     * Constructor
     *
     * @param dialogId ID passed by caller. Can be 0, will be passed back in event
     * @param file     the file
     *
     * @return Created fragment
     */
    public static ExportAdvancedDialogFragment newInstance(final int dialogId, @NonNull final File file) {
        final ExportAdvancedDialogFragment frag = new ExportAdvancedDialogFragment();
        final Bundle args = new Bundle();
        args.putInt(UniqueId.BKEY_DIALOG_ID, dialogId);
        args.putString(UniqueId.BKEY_FILE_SPEC, file.getAbsolutePath());
        frag.setArguments(args);
        return frag;
    }

    /**
     * Ensure activity supports event
     */
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof OnExportTypeSelectionDialogResultListener)) {
            throw new IllegalArgumentException("Activity " + context.getClass().getSimpleName() + " must implement OnExportTypeSelectionDialogResultListener");
        }

    }


    /**
     * Utility routine to set the OnClickListener for a given view to change a checkbox.
     *
     * @param cbId  checkbox view id
     * @param relId Related view id
     */
    private void setRelatedView(@NonNull final View root, @IdRes final int cbId, @IdRes final int relId) {
        final CheckBox cb = root.findViewById(cbId);
        final View rel = root.findViewById(relId);
        if (BuildConfig.DEBUG) {
            // catch layout issues before we click on them.
            if (cb == null || rel == null) {
                throw new IllegalArgumentException("Layout must have: " + cbId + " and " + relId);
            }
        }
        rel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cb.setChecked(!cb.isChecked());
            }
        });
    }

//	private OnClickListener mRowClickListener = new OnClickListener() {
//		@Override
//		public void onClick(View v) {
//			handleClick(v);
//		}};
//
//	/**
//	 * Utility routine to set the OnClickListener for a given view item.
//	 * 
//	 * @param id		Sub-View ID
//	 */
//	private void setOnClickListener(View root, @IdRes int id) {
//		View v = root.findViewById(id);
//		v.setOnClickListener(mRowClickListener);
//		v.setBackgroundResource(android.R.drawable.list_selector_background);
//	}

    /**
     * Create the underlying dialog
     */
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        mDialogId = getArguments().getInt(UniqueId.BKEY_DIALOG_ID);
        mFile = new File(Objects.requireNonNull(getArguments().getString(UniqueId.BKEY_FILE_SPEC)));

        View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_export_advanced_options, null);
        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setView(v)
                .setTitle(R.string.advanced_options)
                .setIcon(R.drawable.ic_help_outline)
                .create();

        dialog.setCanceledOnTouchOutside(false);

        v.findViewById(R.id.cancel).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        v.findViewById(R.id.confirm).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                handleClick(v);
            }
        });

        setRelatedView(v, R.id.books_check, R.id.all_books_row);
        setRelatedView(v, R.id.covers_check, R.id.covers_row);

        return dialog;
    }

    private void handleClick(@SuppressWarnings("unused") View view) {
        try {
            OnExportTypeSelectionDialogResultListener a = (OnExportTypeSelectionDialogResultListener) getActivity();
            if (a != null) {
                ExportSettings settings = createSettings();
                if (settings != null) {
                    a.onExportTypeSelectionDialogResult(mDialogId, this, settings);
                    dismiss();
                }
            } else {
                dismiss();
            }
        } catch (Exception e) {
            Logger.logError(e);
        }
    }

    @Nullable
    private ExportSettings createSettings() {
        final ExportSettings settings = new ExportSettings();

        settings.file = mFile;
        settings.options = 0;
        settings.dateFrom = null;

        Dialog dialog = this.getDialog();
        if (((CheckBox) dialog.findViewById(R.id.books_check)).isChecked()) {
            settings.options |= Exporter.EXPORT_DETAILS;
        }
        if (((CheckBox) dialog.findViewById(R.id.covers_check)).isChecked()) {
            settings.options |= Exporter.EXPORT_COVERS;
        }
        if (((CheckBox) dialog.findViewById(R.id.preferences_check)).isChecked()) {
            settings.options |= Exporter.EXPORT_PREFERENCES | Exporter.EXPORT_STYLES;
        }

        if (((RadioButton) dialog.findViewById(R.id.radioSinceLast)).isChecked()) {
            settings.options |= Exporter.EXPORT_SINCE;
            settings.dateFrom = null;
        } else if (((RadioButton) dialog.findViewById(R.id.radioSinceDate)).isChecked()) {
            View v = dialog.findViewById(R.id.txtDate);
            try {
                settings.options |= Exporter.EXPORT_SINCE;
                settings.dateFrom = DateUtils.parseDate(v.toString());
            } catch (Exception e) {
                //Snackbar.make(v, R.string.no_date, Snackbar.LENGTH_LONG).show();
                StandardDialogs.showQuickNotice(getActivity(), R.string.no_date);
                return null;
            }
        }

        return settings;
    }
}
