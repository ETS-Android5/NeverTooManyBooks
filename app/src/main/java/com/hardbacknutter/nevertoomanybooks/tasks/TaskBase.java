/*
 * @Copyright 2020 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.tasks;

import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.lang.ref.WeakReference;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;

/**
 * The base for a task.
 * <p>
 * The Progress parameter is always {@link ProgressMessage}, and Params always Void.
 *
 * @param <Result> the type of the result of the background computation.
 */
public abstract class TaskBase<Result>
        extends AsyncTask<Void, ProgressMessage, Result>
        implements Canceller {

    /** Log tag. */
    private static final String TAG = "TaskBase";

    /** id set at construction time, passed back in all messages. */
    private final int mTaskId;

    /** The client listener where to send our results to. */
    @NonNull
    private final WeakReference<TaskListener<Result>> mTaskListener;
    /**
     * {@link #doInBackground} should catch exceptions, and set this field.
     * {@link #onPostExecute} can check it.
     */
    @Nullable
    protected Exception mException;

    /**
     * Constructor.
     *
     * @param taskId       a task identifier, will be returned in the task listener.
     * @param taskListener for sending progress and finish messages to.
     */
    protected TaskBase(final int taskId,
                       @NonNull final TaskListener<Result> taskListener) {
        mTaskId = taskId;
        mTaskListener = new WeakReference<>(taskListener);
    }

    /**
     * Access for other classes.
     *
     * @return task ID
     */
    public int getTaskId() {
        return mTaskId;
    }

    @Override
    @UiThread
    protected void onProgressUpdate(@NonNull final ProgressMessage... values) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onProgress(values[0]);
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "onProgressUpdate|" + ErrorMsg.LISTENER_WAS_DEAD);
            }
        }
    }

    /**
     * <p>Runs on the UI thread after {@link #cancel(boolean)} is invoked and
     * {@link #doInBackground} has finished.</p>
     *
     * @param result The result, if any, computed in {@link #doInBackground}, can be {@code null}.
     *               If the task was cancelled before starting the result
     *               <strong>WILL ALWAYS BE {@code null}</strong>
     */
    @Override
    @CallSuper
    protected void onCancelled(@Nullable final Result result) {
        if (mTaskListener.get() != null) {
            mTaskListener.get().onCancelled(new FinishedMessage<>(mTaskId, result));
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "onCancelled|" + ErrorMsg.LISTENER_WAS_DEAD);
            }
        }
    }

    @Override
    @UiThread
    protected void onPostExecute(@NonNull final Result result) {
        if (mTaskListener.get() != null) {
            if (mException == null) {
                mTaskListener.get().onFinished(new FinishedMessage<>(mTaskId, result));
            } else {
                mTaskListener.get().onFailure(new FinishedMessage<>(mTaskId, mException));
            }
        } else {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "onPostExecute|" + ErrorMsg.LISTENER_WAS_DEAD);
            }
        }
    }
}
