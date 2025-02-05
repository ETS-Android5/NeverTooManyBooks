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
package com.hardbacknutter.nevertoomanybooks.covers;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;

public class CoverHandlerViewModel
        extends ViewModel {

    private final TransFormTask transFormTask = new TransFormTask();
    /** Used to display a tip dialog when the user rotates a camera image. */
    private boolean showTipAboutRotating = true;

    @NonNull
    public LiveData<LiveDataEvent<TaskResult<TransFormTask.TransformedData>>>
    onFinished() {
        return transFormTask.onFinished();
    }

    public void execute(@NonNull final Transformation transformation,
                        @NonNull final File destFile) {
        transFormTask.transform(transformation, destFile, CoverHandler.NextAction.Done);
    }

    public void execute(@NonNull final Transformation transformation,
                        @NonNull final File destFile,
                        @NonNull final CoverHandler.NextAction action) {
        transFormTask.transform(transformation, destFile, action);
    }

    public boolean isShowTipAboutRotating() {
        return showTipAboutRotating;
    }

    public void setShowTipAboutRotating(final boolean show) {
        showTipAboutRotating = show;
    }
}
