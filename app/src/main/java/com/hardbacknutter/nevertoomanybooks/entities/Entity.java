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

import java.util.Locale;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.utils.ReorderHelper;

/**
 * An Entity always has an id and some user-friendly label (aka 'displayName')
 * and optionally it's own Locale.
 */
public interface Entity {

    /**
     * Get the database row id of the Entity.
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

    /**
     * Get the label to use for <strong>displaying</strong>.
     * <p>
     * The default implementation ignores the details/style parameters.
     *
     * @param context Current context
     * @param details the amount of details wanted
     * @param style   (optional) to use
     *
     * @return the label to use.
     */
    @NonNull
    default String getLabel(@NonNull final Context context,
                            @NonNull final Details details,
                            @Nullable final Style style) {
        return getLabel(context);
    }

    /**
     * Convenience method; called by (some) implementations of {@link #getLabel(Context)}.
     *
     * @param context Current context
     * @param source  unformatted string
     * @param locale  to use
     *
     * @return formatted string
     */
    @NonNull
    default String getLabel(@NonNull final Context context,
                            @NonNull final String source,
                            @NonNull final Supplier<Locale> locale) {
        if (ReorderHelper.forDisplay(context)) {
            return ReorderHelper.reorder(context, source, locale.get());
        } else {
            return source;
        }
    }

    /**
     * Get the Locale of the Entity.
     *
     * @param context        Current context
     * @param fallbackLocale Locale to use if the Entity does not have a Locale of its own.
     *
     * @return the Entity Locale, or the fallbackLocale.
     */
    @NonNull
    default Locale getLocale(@NonNull final Context context,
                             @NonNull final Locale fallbackLocale) {
        return fallbackLocale;
    }
}
