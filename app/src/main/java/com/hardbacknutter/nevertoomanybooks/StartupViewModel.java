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
import android.content.SharedPreferences;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;
import androidx.annotation.UiThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.HashSet;
import java.util.stream.Stream;

import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.database.tasks.DBCleanerTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.OptimizeDbTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildFtsTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildIndexesTask;
import com.hardbacknutter.nevertoomanybooks.database.tasks.RebuildTitleOrderByColumnTask;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.tasks.BuildLanguageMappingsTask;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

/**
 * <strong>Note:</strong> yes, this is overkill for the startup. Call it an experiment.
 * We're using MutableLiveData to report back, but our tasks are LTasks.
 */
public class StartupViewModel
        extends ViewModel {

    /** Flag to indicate the OrderBy column for titles must be rebuild at startup. */
    @RebuildFlag
    public static final String PK_REBUILD_TITLE_OB = "startup.task.rebuild.title.sorting";

    /** Flag to indicate all indexes must be rebuild at startup. */
    @RebuildFlag
    public static final String PK_REBUILD_INDEXES = "startup.task.rebuild.index";
    /** Flag to indicate FTS rebuild is required at startup. */
    @RebuildFlag
    public static final String PK_REBUILD_FTS = "startup.task.rebuild.fts";
    /** Flag to indicate maintenance (cleaner) is required at startup. */
    @RebuildFlag
    public static final String PK_RUN_MAINTENANCE = "startup.task.maintenance";

    /** Triggers some actions when the countdown reaches 0; then gets reset. */
    private static final String PK_MAINTENANCE_COUNTDOWN = "startup.startCountdown";
    /** Number of app startup's between some periodic action. */
    private static final int MAINTENANCE_COUNTDOWN = 5;

    /** Number of times the app has been started. */
    private static final String PK_STARTUP_COUNT = "startup.startCount";

    private final MutableLiveData<LiveDataEvent<Boolean>> finishedLiveData =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TaskResult<Exception>>> failureLiveData =
            new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TaskProgress>> progressLiveData =
            new MutableLiveData<>();

    /** TaskId holder. Added when started. Removed when stopped. */
    @NonNull
    private final Collection<Integer> allTasks = new HashSet<>(6);

    private final TaskListener<Boolean> taskListener = new TaskListener<>() {
        /**
         * Called when any startup task completes. If no more tasks, let the activity know.
         */
        @Override
        public void onFinished(final int taskId,
                               @Nullable final Boolean result) {
            cleanup(taskId);
        }

        @Override
        public void onCancelled(final int taskId,
                                @Nullable final Boolean result) {
            // We should not get here as the user cannot cancel tasks
            cleanup(taskId);
        }

        @Override
        public void onFailure(final int taskId,
                              @Nullable final Exception exception) {
            // We don't care about the status; just finish
            cleanup(taskId);
        }

        private void cleanup(final int taskId) {
            synchronized (allTasks) {
                allTasks.remove(taskId);
                if (!isRunning()) {
                    finishedLiveData.setValue(new LiveDataEvent<>(true));
                }
            }
        }

        @Override
        public void onProgress(@NonNull final TaskProgress message) {
            progressLiveData.setValue(new LiveDataEvent<>(message));
        }
    };

    /** Flag to ensure tasks are only ever started once. */
    private boolean startTasks = true;

    /** stage the startup is at. */
    private int startupStage;

    /** Flag we need to prompt the user to make a backup after startup. */
    private boolean proposeBackup;

    /** Triggers periodic maintenance tasks. */
    private boolean maintenanceNeeded;


    public static void schedule(@NonNull final Context context,
                                @RebuildFlag @NonNull final String key,
                                final boolean flag) {
        final SharedPreferences.Editor ed = PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit();
        if (flag) {
            ed.putBoolean(key, true);
        } else {
            ed.remove(key);
        }
        ed.apply();
    }

    public boolean isRunning() {
        return !allTasks.isEmpty();
    }

    boolean isProposeBackup() {
        return proposeBackup;
    }

    int getNextStartupStage(@SuppressWarnings("SameParameterValue") final int max) {
        if (startupStage < max) {
            startupStage++;
        }
        return startupStage;
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     */
    public void init(@NonNull final Context context) {
        if (startTasks) {

            cleanObsoleteDirectories(context);

            // from here on, we have access to our log file
            Logger.cycleLogs();

            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            // prepare the maintenance flags and counters.
            final int maintenanceCountdown = prefs.getInt(PK_MAINTENANCE_COUNTDOWN,
                                                          MAINTENANCE_COUNTDOWN);
            final int backupCountdown = prefs.getInt(ExportHelper.PK_BACKUP_COUNTDOWN,
                                                     ExportHelper.BACKUP_COUNTDOWN_DEFAULT);

            prefs.edit()
                 .putInt(PK_MAINTENANCE_COUNTDOWN,
                         maintenanceCountdown == 0 ? MAINTENANCE_COUNTDOWN
                                                   : maintenanceCountdown - 1)
                 .putInt(ExportHelper.PK_BACKUP_COUNTDOWN,
                         backupCountdown == 0 ? ExportHelper.BACKUP_COUNTDOWN_DEFAULT
                                              : backupCountdown - 1)

                 // The number of times the app was opened.
                 .putInt(PK_STARTUP_COUNT, prefs.getInt(PK_STARTUP_COUNT, 0) + 1)
                 .apply();

            maintenanceNeeded = maintenanceCountdown == 0;
            proposeBackup = backupCountdown == 0;
        }
    }

    private void cleanObsoleteDirectories(@NonNull final Context context) {
        // Just delete the obsolete dirs.
        final File root = context.getExternalFilesDir(null);
        // If the user created sub dirs (we did not), then this will fail... oh well.
        //noinspection ResultOfMethodCallIgnored
        Stream.of("tmp", "log", "Upgrades")
              .map(s -> new File(root, s))
              .filter(File::exists)
              .forEach(File::delete);
    }

    /**
     * Run a number of essential tasks (sequentially).
     *
     * @param context Current context
     */
    boolean startTasks(@NonNull final Context context) {
        if (!startTasks) {
            return false;
        }

        // Clear the flag
        startTasks = false;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // unconditional
        startTask(new BuildLanguageMappingsTask(taskListener));

        boolean optimizeDb = false;

        if (maintenanceNeeded || prefs.getBoolean(PK_RUN_MAINTENANCE, false)) {
            // cleaner must be started after the language mapper task,
            // but before the rebuild tasks.
            startTask(new DBCleanerTask(taskListener));
            optimizeDb = true;
        }

        if (prefs.getBoolean(PK_REBUILD_TITLE_OB, false)) {
            startTask(new RebuildTitleOrderByColumnTask(taskListener));
            optimizeDb = true;
        }

        if (prefs.getBoolean(PK_REBUILD_INDEXES, false)) {
            startTask(new RebuildIndexesTask(taskListener));
            optimizeDb = true;
        }

        if (prefs.getBoolean(PK_REBUILD_FTS, false)) {
            startTask(new RebuildFtsTask(taskListener));
            optimizeDb = true;
        }

        // triggered by any of the above as needed
        // This should always be the last task.
        if (optimizeDb) {
            startTask(new OptimizeDbTask(taskListener));
        }

        if (!isRunning()) {
            finishedLiveData.setValue(new LiveDataEvent<>(true));
        }

        return true;
    }

    private void startTask(@NonNull final StartupTask task) {
        synchronized (allTasks) {
            allTasks.add(task.getTaskId());
            task.start();
        }
    }

    /**
     * Called when all tasks have finished.
     */
    @NonNull
    public LiveData<LiveDataEvent<Boolean>> onFinished() {
        return finishedLiveData;
    }

    /**
     * Called when a task fails with an Exception.
     *
     * @return the result is the Exception
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Exception>>> onFailure() {
        return failureLiveData;
    }

    /**
     * Forwards progress messages for the client to display.
     *
     * @return a {@link TaskProgress} with the progress counter, a text message, ...
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return progressLiveData;
    }

    public interface StartupTask {

        @AnyThread
        int getTaskId();

        @UiThread
        void start();
    }

    @StringDef({PK_REBUILD_FTS,
                PK_REBUILD_INDEXES,
                PK_REBUILD_TITLE_OB,
                PK_RUN_MAINTENANCE})
    @Retention(RetentionPolicy.SOURCE)
    private @interface RebuildFlag {

    }
}
