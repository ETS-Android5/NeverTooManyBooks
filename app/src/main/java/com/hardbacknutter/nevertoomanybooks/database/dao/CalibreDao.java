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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreLibrary;

public interface CalibreDao {

    /**
     * Update existing, or insert the Calibre data for the given {@link Book}.
     *
     * @param book to process
     *
     * @return {@code true} if an <strong>insert</strong> was done.
     * {@code false} if no insert was <strong>attempted</strong>
     *
     * @throws DaoWriteException on failure
     */
    boolean updateOrInsert(@NonNull Book book)
            throws DaoWriteException;

    /**
     * Store the Calibre data for the given {@link Book}.
     * <p>
     * New {@link CalibreLibrary} are added, existing ones are NOT updated.
     *
     * @param book to process
     *
     * @return {@code true} if an insert was done.
     * {@code false} if no insert was <strong>attempted</strong>
     *
     * @throws DaoWriteException on failure to insert
     */
    boolean insert(@NonNull Book book)
            throws DaoWriteException;

    /**
     * Delete all data related to Calibre from the database (but leaves them in the book object).
     *
     * @param book to process
     */
    void delete(@NonNull Book book);
}
