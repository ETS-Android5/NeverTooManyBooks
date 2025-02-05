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
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;

/**
 * <a href="https://developer.android.com/reference/androidx/core/content/FileProvider">
 * https://developer.android.com/reference/androidx/core/content/FileProvider</a>
 * <p>
 * Note that the only reason we create this subclass is to have a central point to manage the
 * authority string transparently to the rest of the app.
 * <p>
 * The {@link #AUTHORITY} must match the Manifest attribute {@code "android:authorities"}:
 * <pre>
 * {@code
 * <provider
 *     android:name=".utils.GenericFileProvider"
 *     android:authorities="${applicationId}.GenericFileProvider"
 *     android:exported="false"
 *     android:grantUriPermissions="true">
 *     <meta-data
 *         android:name="android.support.FILE_PROVIDER_PATHS"
 *         android:resource="@xml/provider_paths" />
 * </provider>
 * }
 *  res/xml/provider_paths.xml
 * {@code
 * <paths>
 *     <external-files-path
 *         name="external-path-pics"
 *         path="Pictures/" />
 *     <files-path
 *         name="files-path-root"
 *         path="./" />
 *     <cache-path
 *         name="cache-path-root"
 *         path="./" />
 * </paths>
 * }
 * </pre>
 */
public class GenericFileProvider
        extends FileProvider {

    private static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".GenericFileProvider";

    /**
     * Get a FileProvider URI for the given file.
     *
     * @param context Current context
     * @param file    to uri-fy
     *
     * @return the uri
     */
    @NonNull
    public static Uri createUri(@NonNull final Context context,
                                @NonNull final File file) {
        return getUriForFile(context, AUTHORITY, file);
    }

    /**
     * Get a FileProvider URI for the given file.
     *
     * @param context     Current context
     * @param file        to uri-fy
     * @param displayName the name to display instead of the original file name
     *
     * @return the uri
     */
    @NonNull
    public static Uri createUri(@NonNull final Context context,
                                @NonNull final File file,
                                @NonNull final String displayName) {
        return getUriForFile(context, AUTHORITY, file, displayName);
    }
}
