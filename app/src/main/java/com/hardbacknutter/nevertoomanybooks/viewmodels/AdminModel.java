/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;

public class AdminModel
        extends ViewModel {

    /** collected from all started activities, will be passed up the chain in our own setResult. */
    private final Intent mResultData = new Intent();

    public void addToResults(@NonNull final Intent data) {
        mResultData.putExtras(data);
    }

    public Intent getResultData() {
        return mResultData;
    }

    @Nullable
    private TaskBase mTask;

    @Nullable
    public TaskBase getTask() {
        return mTask;
    }

    public void setTask(@Nullable final TaskBase task) {
        mTask = task;
    }
}
