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

import java.io.Closeable;
import java.io.IOException;

import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;

public interface Importer
        extends Closeable {

    /**
     * Read a {@link ReaderEntity}.
     *
     * @param context          Current context
     * @param entity           to read data from
     * @param progressListener Progress and cancellation provider
     *
     * @return {@link ImportResults}
     *
     * @throws IOException     on failure
     * @throws ImportException on failure
     */
    ImportResults read(@NonNull Context context,
                       @NonNull ReaderEntity entity,
                       @NonNull ProgressListener progressListener)
            throws IOException, ImportException;
}
