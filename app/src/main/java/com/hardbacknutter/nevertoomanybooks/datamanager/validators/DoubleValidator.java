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
package com.hardbacknutter.nevertoomanybooks.datamanager.validators;

import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Validator to apply a default value and validate as Double.
 * Float, Integer, Long are cast to Double.
 * <p>
 * {@code null} or empty string become 0d.
 */
public class DoubleValidator
        implements DataValidator {

    /** Default to apply if the field is {@code null} or empty. */
    private final double defaultValue;

    /**
     * Constructor; default value is 0d.
     * <p>
     * getSystemLocale: the user types it in (or it came from an external source)
     */
    public DoubleValidator() {
        defaultValue = 0d;
    }

    @Override
    @CallSuper
    public void validate(@NonNull final Context context,
                         @NonNull final DataManager dataManager,
                         @NonNull final String key,
                         @StringRes final int errorLabelResId)
            throws ValidatorException {

        final double value;
        final Object obj = dataManager.get(key);
        if (obj == null) {
            value = defaultValue;
        } else if (obj instanceof Number) {
            value = ((Number) obj).doubleValue();
        } else {
            final String stringValue = obj.toString().trim();
            if (stringValue.isEmpty()) {
                value = defaultValue;
            } else {
                try {
                    value = ParseUtils.parseDouble(stringValue, ServiceLocator.getSystemLocale());

                } catch (@NonNull final NumberFormatException e) {
                    throw new ValidatorException(
                            context.getString(R.string.vldt_real_expected_for_x,
                                              context.getString(errorLabelResId)));
                }
            }
        }
        dataManager.putDouble(key, value);
    }
}
