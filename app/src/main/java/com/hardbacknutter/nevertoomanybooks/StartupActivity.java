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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.covers.CoverDir;
import com.hardbacknutter.nevertoomanybooks.databinding.ActivityStartupBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.SettingsFragment;
import com.hardbacknutter.nevertoomanybooks.utils.PackageInfoWrapper;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Single Activity to be the 'Main' activity for the app.
 * Does all preparation needed to start {@link BooksOnBookshelf}.
 */
public class StartupActivity
        extends AppCompatActivity {

    private static final String TAG = "StartupActivity";

    /** Self reference for use by database upgrades. */
    private static WeakReference<StartupActivity> sStartupActivity;

    /** The Activity ViewModel. */
    private StartupViewModel vm;

    /** View Binding. */
    private ActivityStartupBinding vb;

    private int volumeChangedOptionChosen;

    /**
     * Kludge to allow the database open-helper to get a reference to the currently running
     * StartupActivity, so it can send progress messages.
     *
     * @return Reference or {@code null}.
     */
    @Nullable
    public static StartupActivity getActiveActivity() {
        return sStartupActivity != null ? sStartupActivity.get() : null;
    }

    @Override
    protected void attachBaseContext(@NonNull final Context base) {
        final Context localizedContext = ServiceLocator.getInstance().getAppLocale().apply(base);
        super.attachBaseContext(localizedContext);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Are we going through a hot/warm start ?
        if (((App) getApplication()).isHotStart()) {
            // yes, skip the entire startup process
            startActivity(new Intent(this, BooksOnBookshelf.class));
            finish();
            return;
        }

        vm = new ViewModelProvider(this).get(StartupViewModel.class);
        vm.init(this);

        vb = ActivityStartupBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        // Display the version.
        final PackageInfoWrapper info = PackageInfoWrapper.create(this);
        vb.version.setText(info.getVersionName());

        vm.onProgress().observe(this, message -> message.getData().ifPresent(
                data -> onProgress(data.text)));

        // when all tasks are done, move on to next startup-stage
        vm.onFinished().observe(this, message -> message.getData().ifPresent(
                data -> nextStage()));

        // Not called for now, see {@link StartupViewModel} #mTaskListener.
        vm.onFailure().observe(this, message -> message.getData().ifPresent(
                data -> onFailure(data.getResult())));

        nextStage();
    }

    /**
     * Startup stages.
     */
    private void nextStage() {
        switch (vm.getNextStartupStage(4)) {
            case 1:
                initStorage();
                break;

            case 2:
                // create static self-reference for DBHelper callbacks.
                sStartupActivity = new WeakReference<>(this);
                initDb();
                break;

            case 3:
                startTasks();
                break;

            case 4:
            default: {
                // Remove the static self-reference
                if (sStartupActivity != null) {
                    sStartupActivity.clear();
                }
                // Any future hot start will skip the startup tasks
                ((App) getApplication()).setHotStart();
                // and hand over to the real main activity
                final Intent intent = new Intent(this, BooksOnBookshelf.class);
                if (vm.isProposeBackup()) {
                    intent.putExtra(BooksOnBookshelfViewModel.BKEY_PROPOSE_BACKUP, true);
                }
                startActivity(intent);
                finish();
                break;
            }
        }
    }

    private void initStorage() {
        final int storedVolumeIndex = CoverDir.getVolume(this);
        final int actualVolumeIndex;
        try {
            actualVolumeIndex = CoverDir.initVolume(this, storedVolumeIndex);

        } catch (@NonNull final StorageException e) {
            onFailure(e);
            return;
        }

        if (storedVolumeIndex == actualVolumeIndex) {
            // all ok
            nextStage();
        } else {
            onStorageVolumeChanged(actualVolumeIndex);
        }
    }

    /**
     * Create/Upgrade/Open the main database as needed.
     */
    private void initDb() {
        // This is crucial, catch ALL exceptions
        try {
            ServiceLocator.getInstance().getDb();
        } catch (@NonNull final Exception e) {
            onFailure(e);
            return;
        }

        nextStage();
    }

    /**
     * Start all essential startup tasks.
     * When the last tasks finishes, it will trigger the next startup stage.
     */
    private void startTasks() {
        if (!vm.startTasks(this)) {
            // If no task were started, simply move to the next startup stage.
            nextStage();
        }
        // else we wait until all tasks finish and mVm.onFinished() kicks in
    }

    /**
     * Show progress.
     *
     * @param message to display
     */
    public void onProgress(@Nullable final CharSequence message) {
        vb.progressMessage.setText(message);
    }

    /**
     * A fatal error happened preventing startup.
     *
     * @param e to report
     */
    private void onFailure(@Nullable final Exception e) {
        Logger.error(TAG, e, "");

        final String msg = ExMsg.map(this, e)
                                .orElse(getString(R.string.error_unknown_long,
                                                  getString(R.string.pt_maintenance)));

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_baseline_error_24)
                .setTitle(R.string.app_name)
                .setMessage(msg)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> finishAndRemoveTask())
                .setOnDismissListener(d -> finishAndRemoveTask())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    // We'll TRY to start the maintenance fragment
                    // which gives access to debug options
                    final Intent intent = FragmentHostActivity
                            .createIntent(this, MaintenanceFragment.class);
                    startActivity(intent);
                    finish();
                })
                .create()
                .show();
    }

    private void onStorageVolumeChanged(final int actualVolumeIndex) {
        final StorageManager storage = (StorageManager) getSystemService(
                Context.STORAGE_SERVICE);
        final StorageVolume volume = storage.getStorageVolumes().get(actualVolumeIndex);

        final CharSequence[] items = {
                getString(R.string.lbl_storage_quit_and_reinsert_sdcard),
                getString(R.string.lbl_storage_select, volume.getDescription(this)),
                getString(R.string.lbl_edit_settings)};

        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_baseline_warning_24)
                .setTitle(R.string.lbl_storage_volume)
                // this dialog is important. Make sure the user pays some attention
                .setCancelable(false)
                .setSingleChoiceItems(items, 0, (d, w) -> volumeChangedOptionChosen = w)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    switch (volumeChangedOptionChosen) {
                        case 0: {
                            // exit the app, and let the user insert the correct sdcard
                            finishAndRemoveTask();
                            break;
                        }
                        case 1: {
                            // Just set the new location and continue startup
                            CoverDir.setVolume(this, actualVolumeIndex);
                            nextStage();
                            break;
                        }
                        case 2:
                        default: {
                            // take user to the settings screen
                            final Intent intent = FragmentHostActivity
                                    .createIntent(this, SettingsFragment.class)
                                    .putExtra(BasePreferenceFragment.BKEY_AUTO_SCROLL_TO_KEY,
                                              Prefs.pk_storage_volume)
                                    .putExtra(SettingsFragment.BKEY_STORAGE_WAS_MISSING, true);

                            startActivity(intent);
                            // and quit, this will make sure the user exists our app afterwards
                            finish();
                            break;
                        }
                    }
                })
                .create()
                .show();
    }
}
