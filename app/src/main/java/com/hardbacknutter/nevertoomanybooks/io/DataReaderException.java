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
package com.hardbacknutter.nevertoomanybooks.io;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.LocalizedException;

/**
 * Importing data can give a detailed reason of failure and the message passed
 * to the constructors <strong>MUST</strong> be localized. It can/will be shown to the user.
 */
public class DataReaderException
        extends Exception
        implements LocalizedException {

    private static final long serialVersionUID = -1993624111906098070L;

    public DataReaderException(@NonNull final String localizedMessage) {
        super(localizedMessage);
    }

    public DataReaderException(@NonNull final Throwable cause) {
        super(cause);
    }

    public DataReaderException(@NonNull final String localizedMessage,
                               @NonNull final Throwable cause) {
        super(localizedMessage, cause);
    }

    @Override
    @NonNull
    public String getUserMessage(@NonNull final Context context) {
        // if a custom message was added, use that.
        if (getMessage() != null) {
            return getMessage();
        }

        return ExMsg.map(context, getCause())
                    .orElse(context.getString(R.string.error_import_file_not_supported));
    }
}
