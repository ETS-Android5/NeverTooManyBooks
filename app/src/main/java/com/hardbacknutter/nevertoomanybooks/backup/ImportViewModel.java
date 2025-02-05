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
package com.hardbacknutter.nevertoomanybooks.backup;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderViewModel;

@SuppressWarnings("WeakerAccess")
public class ImportViewModel
        extends DataReaderViewModel<ArchiveMetaData, ImportResults> {

    @Nullable
    private ImportHelper importHelper;

    // not an 'init' as the helper can only be created after the user selected a uri
    @NonNull
    ImportHelper createDataReaderHelper(@NonNull final Context context,
                                        @NonNull final Uri uri)
            throws DataReaderException, FileNotFoundException {

        importHelper = new ImportHelper(context, uri);
        return importHelper;
    }

    boolean hasUri() {
        // simple check... the uri will always exist if the helper exists.
        return importHelper != null;
    }

    @Override
    @NonNull
    public ImportHelper getDataReaderHelper() {
        return Objects.requireNonNull(importHelper, "importHelper");
    }

    @Override
    public boolean isReadyToGo() {
        return importHelper != null && importHelper.getMetaData().isPresent();
    }
}
