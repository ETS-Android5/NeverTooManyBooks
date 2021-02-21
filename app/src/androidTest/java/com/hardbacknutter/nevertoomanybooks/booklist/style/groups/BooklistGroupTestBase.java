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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleUtils;

import static org.junit.Assert.assertNotNull;

class BooklistGroupTestBase {

    @NonNull
    BuiltinStyle getStyle(final Context context) {
        final BuiltinStyle s1;
        s1 = (BuiltinStyle) StyleUtils.getStyle(context, StyleUtils.BuiltinStyles
                // This style has a filter by default.
                .UNREAD_AUTHOR_THEN_SERIES_UUID);
        assertNotNull(s1);
        return s1;
    }
}
