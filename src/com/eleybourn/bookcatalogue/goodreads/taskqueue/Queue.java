/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.goodreads.taskqueue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;

import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.goodreads.taskqueue.TaskQueueDBAdapter.ScheduledTask;

/**
 * Represents a thread that runs tasks from a related named queue.
 *
 * @author Philip Warner
 */
class Queue
        extends Thread {

    /** QueueManager that owns this Queue object. */
    @NonNull
    private final QueueManager mManager;
    /** Name of this Queue. */
    @NonNull
    private final String mName;
    /** TaskQueueDBAdapter used internally. */
    private TaskQueueDBAdapter mTQdba;

    /** Currently running task. */
    private WeakReference<Task> mTask;

    /** Flag to indicate process is terminating. */
    private boolean mTerminate;

    /**
     * Constructor. Nothing to see here, move along. Just save the properties and start the thread.
     *
     * @author Philip Warner
     */
    public Queue(@NonNull final QueueManager manager,
                 @NonNull final String queueName) {

        mName = queueName;
        mManager = manager;
        // Set the thread name to something helpful. This is distinct from the Queue name.
        setName("Queue " + queueName);

        // Add this object to the active queues list in the manager. It is important
        // that this is done in the constructor AND that new queues are created inside
        // code synchronized on the manager.
        mManager.onQueueStarting(this);

        start();
    }

    /**
     * @return the bare queue name, as opposed to the thread name.
     */
    @NonNull
    String getQueueName() {
        return mName;
    }

    /**
     * Terminate processing.
     */
    public void finish() {
        mTerminate = true;
        interrupt();
    }

    /**
     * Main worker thread logic.
     */
    public void run() {
        try {
            mTQdba = new TaskQueueDBAdapter();
            while (!mTerminate) {
                ScheduledTask scheduledTask;
                Task task;
                // All queue manipulation needs to be synchronized on the manager, as does
                // assignments of 'active' tasks in queues.
                synchronized (mManager) {
                    scheduledTask = mTQdba.getNextTask(mName);
                    if (scheduledTask == null) {
                        // No more tasks. Remove from manager and terminate.
                        mTerminate = true;
                        mManager.onQueueTerminating(this);
                        return;
                    }
                    if (scheduledTask.timeUntilRunnable == 0) {
                        // Ready to run now.
                        task = scheduledTask.getTask();
                        mTask = new WeakReference<>(task);
                    } else {
                        mTask = null;
                        task = null;
                    }
                }

                // If we get here, we have a task, or know that there is one waiting to run.
                // Just wait for any wait that is longer than a minute.
                if (task != null) {
                    runTask(task);
                } else {
                    // Not ready, just wait. Allow for possible wake-up calls if something
                    // else gets queued.
                    synchronized (this) {
                        wait(scheduledTask.timeUntilRunnable);
                    }
                }
            }
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception e) {
            Logger.error(e);
        } finally {
            try {
                if (mTQdba != null) {
                    mTQdba.getDb().close();
                }
                // Just in case (the queue manager does check the queue before doing the delete).
                synchronized (mManager) {
                    mManager.onQueueTerminating(this);
                }
            } catch (RuntimeException ignore) {
            }
        }
    }

    /**
     * Run the task then save the results.
     */
    private void runTask(@NonNull final Task task) {
        boolean result = false;
        boolean requeue = false;
        try {
            task.setException(null);
            // notify here, as we allow mManager.runTask to be overridden
            mManager.notifyTaskChange();
            result = mManager.runTask(task);
            requeue = !result;
        } catch (RuntimeException e) {
            // Don't overwrite exception set by handler
            if (task.getException() == null) {
                task.setException(e);
            }
            Logger.error(e, "Error running task " + task.getId());
        }
        handleTaskResult(task, result, requeue);
    }

    /**
     * Update the related database record to process the task correctly.
     *
     * @param task    Task object
     * @param result  <tt>true</tt> on Save, <tt>false</tt> on cancel
     * @param requeue <tt>true</tt> if requeue needed
     */
    private void handleTaskResult(@NonNull final Task task,
                                  final boolean result,
                                  final boolean requeue) {
        synchronized (mManager) {

            if (task.isAborting()) {
                mTQdba.deleteTask(task.getId());
            } else if (result) {
                mTQdba.setTaskOk(task);
            } else if (requeue) {
                mTQdba.setTaskRequeue(task);
            } else {
                Exception e = task.getException();
                String msg = null;
                if (e != null) {
                    msg = e.getLocalizedMessage();
                }
                mTQdba.setTaskFail(task, "Unhandled exception while running task: " + msg);
            }
            mTask.clear();
            mTask = null;
        }
        mManager.notifyTaskChange();
    }

    @Nullable
    public Task getTask() {
        if (mTask == null) {
            return null;
        } else {
            return mTask.get();
        }
    }

}
