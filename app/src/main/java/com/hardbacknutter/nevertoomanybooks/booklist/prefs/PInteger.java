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
package com.hardbacknutter.nevertoomanybooks.booklist.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/**
 * Used for {@link androidx.preference.SeekBarPreference}.
 *
 * @see PInt
 */
public class PInteger
        extends PPrefBase<Integer>
        implements PInt {

    /**
     * Constructor. Uses the global setting as the default value,
     * or {@code 0} if there is no global default.
     *
     * @param sp           Style preferences reference.
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param key          key of preference
     */
    public PInteger(@NonNull final SharedPreferences sp,
                    final boolean isPersistent,
                    @NonNull final String key) {
        super(sp, isPersistent, key, 0);
    }

    /**
     * Constructor. Uses the global setting as the default value,
     * or the passed default if there is no global default.
     *
     * @param sp           Style preferences reference.
     * @param isPersistent {@code true} to persist the value, {@code false} for in-memory only.
     * @param key          key of preference
     * @param defValue     in memory default
     */
    public PInteger(@NonNull final SharedPreferences sp,
                    final boolean isPersistent,
                    @NonNull final String key,
                    @NonNull final Integer defValue) {
        super(sp, isPersistent, key, defValue);
    }

    @NonNull
    public Integer getGlobalValue(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getInt(getKey(), mDefaultValue);
    }

    @Override
    public void set(@NonNull final SharedPreferences.Editor ed,
                    @NonNull final Integer value) {
        ed.putInt(getKey(), value);
    }

    @NonNull
    @Override
    public Integer getValue(@NonNull final Context context) {
        if (mIsPersistent) {
            // reminder: it's a primitive so we must test on contains first
            if (mStylePrefs.contains(getKey())) {
                return mStylePrefs.getInt(getKey(), mDefaultValue);
            }
            return getGlobalValue(context);
        } else {
            return mNonPersistedValue != null ? mNonPersistedValue : mDefaultValue;
        }
    }
}
