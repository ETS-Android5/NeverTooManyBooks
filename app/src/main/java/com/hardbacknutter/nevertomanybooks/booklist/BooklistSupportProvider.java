/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.hardbacknutter.nevertomanybooks.booklist;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertomanybooks.database.cursors.BooklistMappedCursorRow;

/**
 * Interface for objects that can provide long-lived instances of Booklist-related items.
 * Typically this interface is implemented by objects that have a close() method (e.g. cursors)
 * so that resource-hungry objects can be cleaned up eventually.
 *
 * @author Philip Warner
 */
public interface BooklistSupportProvider {

    @NonNull
    BooklistMappedCursorRow getCursorRow();

    @NonNull
    BooklistBuilder getBuilder();
}
