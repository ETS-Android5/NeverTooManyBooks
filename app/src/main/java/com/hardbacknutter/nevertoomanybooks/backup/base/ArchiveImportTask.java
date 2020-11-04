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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.tasks.VMTask;

/**
 * Input: {@link ImportHelper}.
 * Output: the updated {@link ImportHelper} with the {@link ImportResults }.
 */
public class ArchiveImportTask
        extends VMTask<Boolean> {

    /** Log tag. */
    private static final String TAG = "ArchiveImportTask";

    /** import configuration. */
    private ImportHelper mHelper;

    /**
     * Start the task.
     *
     * @param helper import configuration
     */
    @UiThread
    public void startImport(@NonNull final ImportHelper helper) {
        mHelper = helper;
        execute(R.id.TASK_ID_IMPORT);
    }

    @NonNull
    @Override
    @WorkerThread
    protected Boolean doWork(@NonNull final Context context)
            throws IOException, ImportException, InvalidArchiveException {
        Thread.currentThread().setName(TAG);

        try (ArchiveReader reader = mHelper.getArchiveReader(context)) {
            mHelper.setResults(reader.read(context, this));

        }
        return true;
    }
}
