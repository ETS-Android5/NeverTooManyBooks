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
package com.hardbacknutter.nevertoomanybooks.fields.formatters;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.fields.EditTextField;
import com.hardbacknutter.nevertoomanybooks.fields.TextViewField;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * FieldFormatter for 'date' fields.
 * <ul>
 *      <li>Multiple fields: <strong>yes</strong> but sharing the same Locale.</li>
 * </ul>
 * <p>
 * This class can be used in two ways:
 * <ol>
 *     <li>with a {@link TextViewField}: the value is stored in the object,<br>
 *         This is meant to be used with a Date-Picker (i.e. the user selects a date).
 *         i.e. the {@link #extract(Context, String)} method is <strong>NOT</strong> called
 *     </li>
 *     <li>with an {@link EditTextField}: the value will be extracted from the View.<br>
 *         This is meant to be used as a free-entry field (i.e. the user types in the date).
 *     </li>
 * </ol>
 */
public class DateFieldFormatter
        implements EditFieldFormatter<String> {

    @NonNull
    private final Locale locale;

    /**
     * Constructor.
     *
     * @param locale to use
     */
    public DateFieldFormatter(@NonNull final Locale locale) {
        this.locale = locale;
    }

    /**
     * Display as a human-friendly date, local timezone.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public String format(@NonNull final Context context,
                         @Nullable final String rawValue) {
        if (rawValue == null || rawValue.isEmpty()) {
            return "";
        } else {
            return new PartialDate(rawValue).toDisplay(locale, rawValue);
        }
    }

    /**
     * Extract as an ISO date (full or partial).
     *
     * @return a (partial) date
     */
    @Override
    @NonNull
    public String extract(@NonNull final Context context,
                          @NonNull final String text) {
        return new PartialDate(text).getIsoString();
    }
}
