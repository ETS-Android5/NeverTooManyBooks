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

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;

/**
 * Exception class for all validation errors. String ID and args are stored for later retrieval.
 *
 * @author Philip Warner
 */
public class ValidatorException extends RuntimeException {
    public static final long serialVersionUID = 1L;
    // String ID of resource string
    private final int mStringId;
    // Args to pass to format function
    private final Object[] mArgs;

    // Constructor
    public ValidatorException(@StringRes final int stringId, @NonNull final Object[] args) {
        mStringId = stringId;
        mArgs = args;
    }

    public String getFormattedMessage(@NonNull final Resources res) {
        return res.getString(mStringId, mArgs);
    }
}