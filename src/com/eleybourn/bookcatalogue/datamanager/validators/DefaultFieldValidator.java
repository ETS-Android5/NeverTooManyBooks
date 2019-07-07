/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.datamanager.validators;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;

/**
 * Validator to apply a default String value to empty fields.
 *
 * @author Philip Warner
 */
public class DefaultFieldValidator
        implements DataValidator {

    /** Default to apply if the field is empty. */
    @NonNull
    private final String mDefaultValue;

    /**
     * Constructor with default value.
     *
     * @param defaultValue Default to apply if the field is empty
     */
    DefaultFieldValidator(@NonNull final String defaultValue) {
        mDefaultValue = defaultValue;
    }

    /**
     * Gets the current value, and if {@code null} or empty,
     * replaces it with the mDefaultValue value.
     *
     * @param dataManager     The DataManager object containing the Datum being validated
     * @param datum           The Datum to validate
     * @param crossValidating Options indicating if this is the cross-validation pass.
     *
     * @throws ValidatorException on error
     */
    @Override
    @CallSuper
    public void validate(@NonNull final DataManager dataManager,
                         @NonNull final Datum datum,
                         final boolean crossValidating)
            throws ValidatorException {

        if (crossValidating) {
            return;
        }

        Object value = dataManager.get(datum);
        if (value != null && value.toString().trim().isEmpty()) {
            dataManager.putString(datum, mDefaultValue);
        }
    }
}
