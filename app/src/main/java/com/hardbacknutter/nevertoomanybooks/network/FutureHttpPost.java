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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public class FutureHttpPost<T>
        extends FutureHttpBase<T> {

    private static final String POST = "POST";

    public FutureHttpPost(@StringRes final int siteResId) {
        super(siteResId);
    }

    @Nullable
    public T post(@NonNull final String url,
                  @NonNull final String postBody,
                  @Nullable final Function<BufferedInputStream, T> responseProcessor)
            throws StorageException,
                   CancellationException,
                   SocketTimeoutException,
                   IOException {

        return execute(url, POST, true, request -> {

            if (mThrottler != null) {
                mThrottler.waitUntilRequestAllowed();
            }

            try {
                try (OutputStream os = request.getOutputStream();
                     Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                     Writer writer = new BufferedWriter(osw)) {
                    writer.write(postBody);
                    writer.flush();
                }

                checkResponseCode(request);

                if (responseProcessor != null) {
                    try (InputStream is = request.getInputStream();
                         BufferedInputStream bis = new BufferedInputStream(is)) {
                        return responseProcessor.apply(bis);
                    }
                }
                return null;

            } catch (@NonNull final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
