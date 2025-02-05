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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.database.dao.AuthorDao;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * Abstracts/shield a {@link Book}, {@link BookLight}, {@link TocEntry}
 * for use in a list of works by an {@link Author}.
 * i.e. {@link AuthorDao#getAuthorWorks}.
 * <p>
 * The {@link Type#value} allows us to return the type directly from a DAO using an SQL column.
 */
public interface AuthorWork {

    /**
     * Get the type of this entry.
     *
     * @return type
     */
    @NonNull
    Type getWorkType();

    /**
     * Get the database row id of the entity.
     *
     * @return id
     */
    long getId();

    /**
     * Get the label to use for <strong>displaying</strong>.
     *
     * @param context Current context
     *
     * @return the label to use.
     */
    @NonNull
    String getLabel(@NonNull Context context);

    @NonNull
    PartialDate getFirstPublicationDate();

    @Nullable
    Author getPrimaryAuthor();

    /**
     * Get the list of book titles this work is present in.
     * <p>
     * The embedded titles should/will be unformatted.
     *
     * @param context Current context
     *
     * @return list
     */
    @NonNull
    List<BookLight> getBookTitles(@NonNull Context context);

    /**
     * Get the number of books this work is present in.
     * <p>
     * The default implementation assumes the work <strong>is</strong> a Book.
     * <p>
     * Dev. note: calling this <strong>should</strong> be faster than
     * calling {@link #getBookTitles(Context)} and the size of that list.
     *
     * @return count
     */
    default int getBookCount() {
        return 1;
    }

    enum Type {
        /** 'T' as returned by the DAO SQL. */
        TocEntry('T'),
        /** 'L'  as returned by the DAO SQL. */
        BookLight('L'),

        Book('B');

        public final char value;

        Type(final char value) {
            this.value = value;
        }

        public static Type getType(final char value) {
            switch (value) {
                case 'T':
                    return TocEntry;
                case 'L':
                    return BookLight;
                case 'B':
                    return Book;
                default:
                    throw new IllegalArgumentException(String.valueOf(value));
            }
        }
    }
}
