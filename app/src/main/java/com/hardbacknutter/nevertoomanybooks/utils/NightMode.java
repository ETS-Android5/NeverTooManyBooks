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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.math.MathUtils;

import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public final class NightMode {

    /**
     * We're not using the actual {@link AppCompatDelegate} mode constants
     * due to 'day-night' being different depending on the OS version.
     */
    private static final int[] NIGHT_MODES = {
            // day-night
            Build.VERSION.SDK_INT >= 29 ? AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                                        : AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY,
            // light
            AppCompatDelegate.MODE_NIGHT_NO,
            // dark
            AppCompatDelegate.MODE_NIGHT_YES
    };

    private NightMode() {
    }

    /**
     * Apply the user's preferred NightMode.
     *
     * @param context Current context
     */
    public static void apply(@NonNull final Context context) {
        final int mode = Prefs.getIntListPref(context, Prefs.pk_ui_theme, 0);
        AppCompatDelegate.setDefaultNightMode(NIGHT_MODES[MathUtils.clamp(mode, 0, 2)]);
    }
}
