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
package com.hardbacknutter.nevertoomanybooks.goodreads.qtasks.taskqueue;

import androidx.annotation.NonNull;

/**
 * Class to wrap tasks that cannot be de-serialized.
 */
public class LegacyTask
        extends TQTask {

    private static final long serialVersionUID = 7171611206001905702L;

    /**
     * Constructor.
     *
     * @param description for the task
     */
    LegacyTask(@NonNull final String description) {
        super(description);
    }

    @Override
    public int getCategory() {
        return TQTask.CAT_UNKNOWN;
    }
}
