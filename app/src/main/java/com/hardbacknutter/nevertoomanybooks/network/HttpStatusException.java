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
package com.hardbacknutter.nevertoomanybooks.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.IOException;
import java.net.URL;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.LocalizedException;

/**
 * Signals that a HTTP request resulted in a not OK HTTP response.
 */
public class HttpStatusException
        extends IOException
        implements LocalizedException {

    private static final long serialVersionUID = 7064030911654231924L;

    private final int mStatusCode;
    @NonNull
    private final String mStatusMessage;


    @Nullable
    private final URL mUrl;

    /** The site that caused the issue. */
    @StringRes
    private final int mSiteResId;

    /**
     * @param siteResId     the site string res; which will be embedded in a default user message
     * @param statusCode    the original status code from the HTTP request
     * @param statusMessage the original status message from the HTTP request
     * @param url           (optional) The full url, for debugging
     */
    HttpStatusException(@StringRes final int siteResId,
                        final int statusCode,
                        @NonNull final String statusMessage,
                        @Nullable final URL url) {
        super(statusMessage);
        mSiteResId = siteResId;
        mStatusCode = statusCode;
        mStatusMessage = statusMessage;
        mUrl = url;
    }

    @StringRes
    int getSiteResId() {
        return mSiteResId;
    }

    public int getStatusCode() {
        return mStatusCode;
    }

    @NonNull
    public String getStatusMessage() {
        return mStatusMessage;
    }

    @Nullable
    public URL getUrl() {
        return mUrl;
    }

    @NonNull
    @Override
    public String getUserMessage(@NonNull final Context context) {
        final String msg;
        if (getSiteResId() != 0) {
            msg = context.getString(R.string.error_network_site_access_failed,
                                    context.getString(getSiteResId()));
        } else {
            msg = context.getString(R.string.httpError);
        }

        return msg + " (" + mStatusCode + ")";
    }

    @Override
    @NonNull
    public String toString() {
        return "HttpStatusException{"
               + "mStatusCode=" + mStatusCode
               + ", mStatusMessage=" + mStatusMessage
               + ", mUrl=" + mUrl
               + ", mSiteResId=" + ServiceLocator.getAppContext().getString(mSiteResId)
               + ", " + super.toString()
               + '}';
    }
}
