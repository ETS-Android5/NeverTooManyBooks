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
package com.hardbacknutter.nevertoomanybooks.sync.goodreads.tasks;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.GrBaseTask;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.SendBooksGrTask;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.sync.goodreads.qtasks.taskqueue.TQTask;
import com.hardbacknutter.nevertoomanybooks.tasks.LTask;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;

/**
 * Start a background task that export books to Goodreads.
 * It can either send 'all' or 'updated-only' books.
 * <p>
 * We runs a network and authorization check first.
 * If successful, an actual GoodReads task {@link GrBaseTask}
 * is kicked of to do the actual work.
 */
public class SendBooksTask
        extends LTask<GrStatus> {

    /** Log tag. */
    private static final String TAG = "GR.SendBooksTask";

    /** Flag: send only starting from the last book we did earlier, or all books. */
    private boolean mFromLastBookId;
    /** Flag: send only the updated, or all books. */
    private boolean mUpdatesOnly;

    /**
     * Constructor.
     *
     * @param taskListener for sending progress and finish messages to.
     */
    public SendBooksTask(@NonNull final TaskListener<GrStatus> taskListener) {
        super(R.id.TASK_ID_GR_SEND_BOOKS, TAG, taskListener);
    }

    /**
     * Start sending.
     *
     * @param fromLastBookId {@code true} to send from the last book we did earlier,
     *                       {@code false} for all books.
     * @param updatesOnly    {@code true} to send updated books only,
     *                       {@code false} for all books.
     */
    public void send(final boolean fromLastBookId,
                     final boolean updatesOnly) {
        mFromLastBookId = fromLastBookId;
        mUpdatesOnly = updatesOnly;
        execute();
    }

    @NonNull
    @Override
    @WorkerThread
    protected GrStatus doWork(@NonNull final Context context) {

        if (!NetworkUtils.isNetworkAvailable()) {
            return new GrStatus(GrStatus.FAILED_NETWORK_UNAVAILABLE);
        }

        // Check that no other sync-related jobs are queued
        if (QueueManager.getInstance().hasActiveTasks(GrBaseTask.CAT_EXPORT)) {
            return new GrStatus(GrStatus.FAILED_EXPORT_TASK_ALREADY_QUEUED);
        }
        if (QueueManager.getInstance().hasActiveTasks(GrBaseTask.CAT_IMPORT)) {
            return new GrStatus(GrStatus.FAILED_IMPORT_TASK_ALREADY_QUEUED);
        }

        final GoodreadsAuth grAuth = new GoodreadsAuth();
        if (!grAuth.hasValidCredentials(context)) {
            return new GrStatus(GrStatus.FAILED_CREDENTIALS);
        }

        if (isCancelled()) {
            return new GrStatus(GrStatus.CANCELLED);
        }

        final String desc = context.getString(R.string.gr_title_send_book);
        final TQTask task = new SendBooksGrTask(desc, mFromLastBookId, mUpdatesOnly);
        QueueManager.getInstance().enqueueTask(QueueManager.Q_MAIN, task);

        return new GrStatus(GrStatus.SUCCESS_TASK_QUEUED);
    }
}
