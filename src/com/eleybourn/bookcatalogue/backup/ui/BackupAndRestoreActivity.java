/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.backup.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Objects;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.backup.BackupManager;
import com.eleybourn.bookcatalogue.backup.BackupTask;
import com.eleybourn.bookcatalogue.backup.ExportSettings;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.RestoreTask;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.filechooser.FileChooserBaseActivity;
import com.eleybourn.bookcatalogue.filechooser.FileChooserFragment;
import com.eleybourn.bookcatalogue.tasks.ProgressDialogFragment;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Lets the user choose an archive file to backup to, or import from.
 *
 * @author pjw
 */
public class BackupAndRestoreActivity
        extends FileChooserBaseActivity
        implements
        ProgressDialogFragment.OnTaskFinishedListener,
        ImportDialogFragment.OnImportTypeSelectionDialogResultsListener,
        ExportDialogFragment.OnExportTypeSelectionDialogResultsListener {

    @CallSuper
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(isSave() ? R.string.lbl_backup : R.string.lbl_import_from_archive);
    }

    /**
     * @return the default file name: blank for 'open', date-based for save.
     */
    @NonNull
    private String getDefaultFileName() {
        if (isSave()) {
            final String sqlDate = DateUtils.localSqlDateForToday();
            return getString(R.string.app_name) + '-'
                    + sqlDate.replace(" ", "-")
                             .replace(":", "")
                    + BackupFileDetails.ARCHIVE_EXTENSION;
        } else {
            return "";
        }
    }

    /**
     * Create the fragment using the last backup for the path,
     * and the default file name (if saving).
     */
    @NonNull
    @Override
    protected FileChooserFragment createChooserFragment() {
        String lastBackupFile =
                App.getPrefs().getString(BackupManager.PREF_LAST_BACKUP_FILE,
                                         StorageUtils.getSharedStorage().getAbsolutePath());
        Objects.requireNonNull(lastBackupFile);
        return FileChooserFragment.newInstance(new File(lastBackupFile), getDefaultFileName());
    }

    /**
     * Start a task suited to building a list of backup files.
     */
    @Override
    public void startFileLister(@NonNull final FragmentActivity context,
                                @NonNull final File root) {

        FragmentManager fm = context.getSupportFragmentManager();
        if (fm.findFragmentByTag(BackupListerTask.TAG) == null) {
            ProgressDialogFragment<ArrayList<FileChooserFragment.FileDetails>> frag =
                    ProgressDialogFragment.newInstance(R.string.progress_msg_reading_directory,
                                                       true, 0);
            BackupListerTask task = new BackupListerTask(frag, root);
            frag.setTask(R.id.TASK_ID_FILE_LISTER, task);
            frag.show(fm, BackupListerTask.TAG);
            task.execute();
        }
    }

    /**
     * After a file was selected, ask the user for the next action.
     * - import
     * - cancel
     * - options
     */
    @Override
    public void onOpen(@NonNull final File file) {
        final ImportSettings settings = new ImportSettings();
        settings.file = file;

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_import_from_archive)
                .setMessage(R.string.import_option_info_all_books)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setNeutralButton(R.string.btn_options, (d, which) ->
                        ImportDialogFragment.show(getSupportFragmentManager(), settings))
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    // User wants to import all.
                    settings.what = ImportSettings.ALL;
                    RestoreTask.start(getSupportFragmentManager(), settings);
                })
                .create();
        dialog.show();
    }

    /**
     * User has set his choices for import... kick of the restore task.
     */
    @Override
    public void onImportTypeSelectionDialogResult(@NonNull final ImportSettings settings) {
        // sanity check
        if (settings.what == ImportSettings.NOTHING) {
            return;
        }
        RestoreTask.start(getSupportFragmentManager(), settings);
    }

    /**
     * If a file was selected, offer the user settings on how to save the archive.
     */
    @Override
    public void onSave(@NonNull final File file) {
        final ExportSettings settings = new ExportSettings();
        settings.file = file;

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_backup)
                .setMessage(R.string.export_info_backup_all)
                .setNegativeButton(android.R.string.cancel, (d, which) -> d.dismiss())
                .setNeutralButton(R.string.btn_options, (d, which) ->
                        ExportDialogFragment.show(getSupportFragmentManager(), settings))
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    // User wants to backup all.
                    settings.what = ExportSettings.ALL;
                    BackupTask.start(getSupportFragmentManager(), settings);
                })
                .create();
        dialog.show();

    }

    /**
     * User has set his choices for backup... check them, and kick of the backup task.
     */
    @Override
    public void onExportTypeSelectionDialogResult(@NonNull final ExportSettings settings) {
        // sanity check
        if (settings.what == ExportSettings.NOTHING) {
            return;
        }

        // backup 'since'
        if ((settings.what & ExportSettings.EXPORT_SINCE) != 0) {
            // no date set, use "since last backup."
            if (settings.dateFrom == null) {
                String lastBackup = App.getPrefs().getString(BackupManager.PREF_LAST_BACKUP_DATE,
                                                             null);
                if (lastBackup != null && !lastBackup.isEmpty()) {
                    settings.dateFrom = DateUtils.parseDate(lastBackup);
                }
            }
        } else {
            // make sure; cannot have a dateFrom when not asking for a time limited export
            settings.dateFrom = null;
        }

        BackupTask.start(getSupportFragmentManager(), settings);
    }

    /**
     * Listener for tasks.
     *
     * @param taskId  a task identifier
     * @param success <tt>true</tt> for success.
     * @param result  - archive backup : {@link ExportSettings}
     *                - archive restore: {@link ImportSettings}
     *                - file lister: not used
     */
    @Override
    public void onTaskFinished(final int taskId,
                               final boolean success,
                               @Nullable final Object result) {

        // Is it a task we care about?
        switch (taskId) {
            case R.id.TASK_ID_SAVE_TO_ARCHIVE:
                ExportSettings exportSettings = (ExportSettings) Objects.requireNonNull(result);
                handleSaveToArchiveResults(success, exportSettings);
                break;

            case R.id.TASK_ID_READ_FROM_ARCHIVE:
                ImportSettings importSettings = (ImportSettings) Objects.requireNonNull(result);
                handleReadFromArchiveResults(success, importSettings);
                break;

            case R.id.TASK_ID_FILE_LISTER:
                break;

            default:
                Logger.warnWithStackTrace(this, "Unknown taskId=" + taskId);
                break;
        }
    }

    private void handleReadFromArchiveResults(final boolean success,
                                              @NonNull final ImportSettings resultSettings) {
        if (!success) {
            String msg = getString(R.string.error_import_failed)
                    + ' ' + getString(R.string.error_storage_not_readable)
                    + "\n\n" + getString(R.string.error_if_the_problem_persists);
            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_import_from_archive)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                    .create();
            dialog.show();
            return;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
            Logger.debug(this, "handleReadFromArchiveResults",
                         "Imported: " + resultSettings);
        }
        // see if there are any pre-200 preferences that need migrating.
        if ((resultSettings.what & ImportSettings.PREFERENCES) != 0) {
            Prefs.migratePreV200preferences(App.PREF_LEGACY_BOOK_CATALOGUE);
        }

        // all done
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_import_from_archive)
                .setMessage(R.string.progress_end_import_complete)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    d.dismiss();
                    Intent data = new Intent()
                            .putExtra(UniqueId.BKEY_IMPORT_RESULT, resultSettings.what);
                    setResult(Activity.RESULT_OK, data);
                    finish();
                })
                .create();
        dialog.show();
    }

    private void handleSaveToArchiveResults(final boolean success,
                                            @NonNull final ExportSettings resultSettings) {
        if (!success) {
            String msg = getString(R.string.error_backup_failed)
                    + ' ' + getString(R.string.error_storage_not_writable)
                    + "\n\n" + getString(R.string.error_if_the_problem_persists);

            final AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.lbl_backup)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                    .create();
            dialog.show();
            return;
        }

        // all done
        //noinspection ConstantConditions
        String msg = getString(R.string.export_info_success_archive_details,
                               resultSettings.file.getParent(),
                               resultSettings.file.getName(),
                               Utils.formatFileSize(this, resultSettings.file.length()));
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lbl_backup)
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    d.dismiss();
                    Intent data = new Intent()
                            .putExtra(UniqueId.BKEY_EXPORT_RESULT, resultSettings.what);
                    setResult(Activity.RESULT_OK, data);
                    finish();
                })
                .create();
        dialog.show();
    }
}
