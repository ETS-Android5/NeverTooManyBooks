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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * Interface definition for Field formatter.
 *
 * <strong>Do not store Context or View in a formatter.</strong>
 *
 * @param <T> type of Field value.
 */
public interface EditFieldFormatter<T>
        extends FieldFormatter<T> {

    /**
     * Extract the native typed value from the displayed version.
     *
     * @param view to extract the value from
     *
     * @return The extracted value
     */
    @NonNull
    T extract(@NonNull TextView view);
}
