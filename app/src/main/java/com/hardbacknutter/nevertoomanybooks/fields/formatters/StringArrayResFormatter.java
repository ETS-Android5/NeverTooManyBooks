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

import android.content.Context;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Why Long? See Sqlite docs... storage class INTEGER.
 * TLDR: we always get a long from the database even if the column stores an int.
 */
public class StringArrayResFormatter
        implements FieldFormatter<Long> {

    @NonNull
    private final String[] mStringArray;

    public StringArrayResFormatter(@NonNull final Context context,
                                   @ArrayRes final int arrayResId) {
        mStringArray = context.getResources().getStringArray(arrayResId);
    }

    @NonNull
    @Override
    public String format(@NonNull final Context context,
                         @Nullable final Long rawValue) {
        if (rawValue == null || rawValue < 0 || rawValue >= mStringArray.length) {
            return mStringArray[0];
        } else {
            return mStringArray[rawValue.intValue()];
        }
    }
}
