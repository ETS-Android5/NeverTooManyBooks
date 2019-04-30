package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.FormattedMessageException;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.csv.CsvExporter;
import com.eleybourn.bookcatalogue.backup.csv.ExportCSVTask;
import com.eleybourn.bookcatalogue.backup.csv.ImportCSVTask;
import com.eleybourn.bookcatalogue.backup.ui.BackupActivity;
import com.eleybourn.bookcatalogue.backup.ui.RestoreActivity;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.FilePicker;
import com.eleybourn.bookcatalogue.dialogs.HintManager;
import com.eleybourn.bookcatalogue.dialogs.ValuePicker;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsUtils;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.TaskQueueListActivity;
import com.eleybourn.bookcatalogue.tasks.OnTaskFinishedListener;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.GenericFileProvider;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

public class AdminFragment
        extends Fragment
        implements OnTaskFinishedListener {

    /** Fragment manager tag. */
    public static final String TAG = AdminFragment.class.getSimpleName();

    /** requestCode for making a backup to archive. */
    private static final int REQ_ARCHIVE_BACKUP = 0;
    /** requestCode for doing a restore/import from archive. */
    private static final int REQ_ARCHIVE_RESTORE = 1;

    /**
     * collected results from all started activities, which we'll pass on up in our own setResult.
     */
    private final Intent mResultData = new Intent();

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        View root = getView();
        View v;

        // Export (backup) to Archive
        //noinspection ConstantConditions
        v = root.findViewById(R.id.lbl_backup);
        v.setOnClickListener(v1 -> {
            Intent intent = new Intent(getContext(), BackupActivity.class);
            startActivityForResult(intent, REQ_ARCHIVE_BACKUP);
        });


        // Import from Archive - Start the restore activity
        v = root.findViewById(R.id.lbl_import_from_archive);
        v.setOnClickListener(v1 -> {
            Intent intent = new Intent(getContext(), RestoreActivity.class);
            startActivityForResult(intent, REQ_ARCHIVE_RESTORE);
        });


        // Export to CSV
        v = root.findViewById(R.id.lbl_export);
        v.setOnClickListener(v1 -> exportToCSV());


        // Import From CSV
        v = root.findViewById(R.id.lbl_import);
        v.setOnClickListener(v1 -> {
            // Verify - this can be a dangerous operation
            confirmToImportFromCSV();
        });


        /* Automatically Update Fields from internet*/
        v = root.findViewById(R.id.lbl_update_internet);
        v.setOnClickListener(v1 -> {
            Intent intent = new Intent(getContext(), UpdateFieldsFromInternetActivity.class);
            startActivity(intent);
        });


        // Goodreads Synchronize
        v = root.findViewById(R.id.lbl_sync_with_goodreads);
        v.setOnClickListener(v1 -> GoodreadsUtils.importAll(this, true));


        // Goodreads Import
        v = root.findViewById(R.id.lbl_import_all_from_goodreads);
        v.setOnClickListener(v1 -> GoodreadsUtils.importAll(this, false));


        // Goodreads Export (send to)
        v = root.findViewById(R.id.lbl_send_books_to_goodreads);
        v.setOnClickListener(v1 -> GoodreadsUtils.sendBooks(this));

        /* Start the activity that shows the basic details of GoodReads tasks. */
        v = root.findViewById(R.id.lbl_background_tasks);
        v.setOnClickListener(v1 -> {
            Intent intent = new Intent(getContext(), TaskQueueListActivity.class);
            startActivity(intent);
        });


        /* Reset Hints */
        v = root.findViewById(R.id.lbl_reset_hints);
        v.setOnClickListener(v1 -> {
            HintManager.resetHints();
            UserMessage.showUserMessage(v1, R.string.hints_have_been_reset);
        });


        /* Erase cover cache */
        v = root.findViewById(R.id.lbl_erase_cover_cache);
        v.setOnClickListener(v1 -> {
            try (CoversDBA coversDBAdapter = CoversDBA.getInstance()) {
                coversDBAdapter.deleteAll();
            }
        });


        /* Copy database for tech support */
        v = root.findViewById(R.id.lbl_copy_database);
        v.setOnClickListener(v1 -> {
            StorageUtils.exportDatabaseFiles();
            UserMessage.showUserMessage(v1, R.string.progress_end_backup_success);
        });
    }

    /**
     * Export all data to a CSV file.
     */
    private void exportToCSV() {
        File file = StorageUtils.getFile(CsvExporter.EXPORT_FILE_NAME);
        ExportSettings settings = new ExportSettings(file);
        settings.what = ExportSettings.BOOK_CSV;

        //noinspection ConstantConditions
        @NonNull
        FragmentManager fm = getFragmentManager();
        if (fm.findFragmentByTag(TAG) == null) {
            ProgressDialogFragment<Void> progressDialog =
                    ProgressDialogFragment.newInstance(R.string.progress_msg_backing_up,
                                                       false, 0);
            ExportCSVTask task = new ExportCSVTask(settings, progressDialog);
            progressDialog.setTask(R.id.TASK_ID_CSV_EXPORT, task);
            progressDialog.show(fm, TAG);
            task.execute();
        }
    }

    /**
     * Ask before importing.
     */
    private void confirmToImportFromCSV() {
        //noinspection ConstantConditions
        new AlertDialog.Builder(getContext())
                .setMessage(R.string.warning_import_be_cautious)
                .setTitle(R.string.title_import_book_data)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> importFromCSV())
                .create()
                .show();
    }

    /**
     * Import all data from somewhere on Shared Storage; ask user to disambiguate if necessary.
     */
    private void importFromCSV() {
        List<File> files = StorageUtils.findCsvFiles();
        // If none, exit with message
        if (files.isEmpty()) {
            //noinspection ConstantConditions
            UserMessage.showUserMessage(getView(), R.string.import_error_csv_file_not_found);
        } else {
            if (files.size() == 1) {
                // If only 1, just use it
                importFromCSV(files.get(0));
            } else {
                // If more than one, ask user which file
                // ENHANCE: Consider asking about importing cover images.
                //noinspection ConstantConditions
                ValuePicker picker =
                        new FilePicker(getContext(),
                                       getString(R.string.lbl_import_from_csv),
                                       getString(R.string.import_warning_select_csv_file),
                                       files,
                                       this::importFromCSV);
                picker.show();
            }
        }
    }

    /**
     * Import data.
     *
     * @param file the CSV file to read
     */
    private void importFromCSV(@NonNull final File file) {
        ImportSettings settings = new ImportSettings(file);
        settings.what = ImportSettings.BOOK_CSV;

        //noinspection ConstantConditions
        @NonNull
        FragmentManager fm = getFragmentManager();
        if (fm.findFragmentByTag(TAG) == null) {
            ProgressDialogFragment<Void> progressDialog =
                    ProgressDialogFragment.newInstance(R.string.progress_msg_importing,
                                                       false, 0);

            ImportCSVTask task = new ImportCSVTask(settings, progressDialog);
            progressDialog.setTask(R.id.TASK_ID_CSV_IMPORT, task);
            progressDialog.show(fm, TAG);
            task.execute();
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        // collect all data
        if (data != null) {
            mResultData.putExtras(data);
        }

        switch (requestCode) {
            case REQ_ARCHIVE_BACKUP:
            case REQ_ARCHIVE_RESTORE:
                if (resultCode == Activity.RESULT_OK) {
                    // no local action needed, pass results up
                    //noinspection ConstantConditions
                    getActivity().setResult(resultCode, mResultData);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        Tracker.exitOnActivityResult(this);
    }

    /**
     * The result of the task is not used here.
     * <p>
     * <p>{@inheritDoc}
     */
    @Override
    public void onTaskFinished(final int taskId,
                               final boolean success,
                               @Nullable final Object result,
                               @Nullable final Exception e) {
        switch (taskId) {
            case R.id.TASK_ID_CSV_EXPORT:
                if (success) {
                    onExportFinished();
                } else {
                    //noinspection ConstantConditions
                    UserMessage.showUserMessage(getView(), e.getLocalizedMessage());
                }
                break;

            case R.id.TASK_ID_CSV_IMPORT:
                if (!success) {
                    String msg;
                    if (e instanceof FormattedMessageException) {
                        msg = ((FormattedMessageException) e).getFormattedMessage(getResources());
                    } else if (e != null) {
                        msg = e.getLocalizedMessage();
                    } else {
                        msg = getString(R.string.error_import_failed);
                    }
                    //noinspection ConstantConditions
                    UserMessage.showUserMessage(getView(), msg);
                }
                break;
        }
    }

    /**
     * Callback for the CSV export task.
     */
    private void onExportFinished() {
        //noinspection ConstantConditions
        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.export_csv_email)
                .setIcon(R.drawable.ic_send)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setPositiveButton(android.R.string.ok, (d, which) -> emailCSVFile())
                .create();

        try {
            //noinspection ConstantConditions
            if (!getActivity().isFinishing()) {
                // Catch errors resulting from 'back' being pressed multiple times so that
                // the activity is destroyed before the dialog can be shown.
                // See http://code.google.com/p/android/issues/detail?id=3953
                dialog.show();
            }
        } catch (RuntimeException e) {
            Logger.error(this, e);
        }
    }

    /**
     * Create and send an email with the CSV export file.
     */
    private void emailCSVFile() {
        String subject = '[' + getString(R.string.app_name) + "] "
                + getString(R.string.lbl_export_to_csv);

        final Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("plain/text")
                .putExtra(Intent.EXTRA_SUBJECT, subject);

        ArrayList<Uri> uris = new ArrayList<>();
        try {
            File csvExportFile = StorageUtils.getFile(CsvExporter.EXPORT_FILE_NAME);
            //noinspection ConstantConditions
            Uri coverURI = FileProvider.getUriForFile(getContext(),
                                                      GenericFileProvider.AUTHORITY,
                                                      csvExportFile);

            uris.add(coverURI);
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            startActivity(Intent.createChooser(intent, getString(R.string.title_send_mail)));
        } catch (NullPointerException e) {
            Logger.error(this, e);
            //noinspection ConstantConditions
            UserMessage.showUserMessage(getView(), R.string.error_email_failed);
        }
    }
}
