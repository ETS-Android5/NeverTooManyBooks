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
package com.hardbacknutter.nevertoomanybooks.backup.csv.coders;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.entities.Publisher;

/**
 * StringList factory for a Publisher.
 * <p>
 * Format: Name
 */
public class PublisherCoder
        implements StringList.Coder<Publisher> {

    private static final char[] ESCAPE_CHARS = {'(', ')'};

    PublisherCoder() {
    }

    @NonNull
    @Override
    public String encode(@NonNull final Publisher publisher) {
        return escape(publisher.getName(), ESCAPE_CHARS);
    }

    @Override
    @NonNull
    public Publisher decode(@NonNull final String element) {
        return Publisher.from(element);
    }
}
