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
package com.hardbacknutter.nevertoomanybooks.tasks.messages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Value class holding Result data.
 *
 * @param <Result> type of the payload
 */
public class FinishedMessage<Result>
        implements LiveDataEvent {

    public static final String MISSING_TASK_RESULTS = "message.result";

    public final int taskId;

    /**
     * The result object from the task.
     * It can be {@code null} regardless of the task implementation.
     */
    @Nullable
    public final Result result;

    /** {@link LiveDataEvent}. */
    private boolean mHasBeenHandled;

    public FinishedMessage(final int taskId,
                           @Nullable final Result result) {
        this.taskId = taskId;
        this.result = result;
    }

    @Override
    public boolean isNewEvent() {
        final boolean isNew = !mHasBeenHandled;
        mHasBeenHandled = true;
        return isNew;
    }

    @Override
    @NonNull
    public String toString() {
        return "FinishedMessage{"
               + "mHasBeenHandled=" + mHasBeenHandled
               + ", taskId=" + taskId
               + ", result=" + result
               + '}';
    }
}
