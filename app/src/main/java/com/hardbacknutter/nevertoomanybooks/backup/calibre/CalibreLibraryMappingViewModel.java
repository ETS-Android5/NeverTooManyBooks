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
package com.hardbacknutter.nevertoomanybooks.backup.calibre;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.ImportViewModel;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.CalibreLibraryDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class CalibreLibraryMappingViewModel
        extends ImportViewModel {

    private final ArrayList<CalibreLibrary> mLibraries = new ArrayList<>();
    private CalibreLibrary mCurrentLibrary;

    @NonNull
    List<Bookshelf> getBookshelfList() {
        return BookshelfDao.getInstance().getAll();
    }

    @NonNull
    ArrayList<CalibreLibrary> getLibraries() {
        return mLibraries;
    }

    public void setLibraries(@NonNull final ArchiveMetaData result) {
        // at this moment, all server libs have been synced with our database
        // and are mapped to a valid bookshelf

        mLibraries.clear();
        mLibraries.addAll(Objects.requireNonNull(
                result.getBundle().getParcelableArrayList(CalibreContentServer.BKEY_LIBRARY_LIST),
                "mLibraries"));
    }

    @NonNull
    CalibreLibrary getCurrentLibrary() {
        return mCurrentLibrary;
    }

    void setCurrentLibrary(final int position) {
        mCurrentLibrary = mLibraries.get(position);
    }

    CalibreVirtualLibrary getVirtualLibrary(final int position) {
        return mCurrentLibrary.getVirtualLibraries().get(position);
    }


    void mapBookshelfToLibrary(@NonNull final Bookshelf bookshelf) {
        if (bookshelf.getId() != mCurrentLibrary.getMappedBookshelfId()) {
            mCurrentLibrary.setMappedBookshelf(bookshelf.getId());
            CalibreLibraryDao.getInstance().update(mCurrentLibrary);
        }
    }

    void mapBookshelfToVirtualLibrary(@NonNull final Bookshelf bookshelf,
                                      final int position) {

        final CalibreVirtualLibrary vlib = mCurrentLibrary.getVirtualLibraries().get(position);
        if (bookshelf.getId() != vlib.getMappedBookshelfId()) {
            vlib.setMappedBookshelf(bookshelf.getId());
            CalibreLibraryDao.getInstance().update(vlib);
        }
    }

    @NonNull
    Bookshelf createLibraryAsBookshelf(@NonNull final Context context)
            throws DaoWriteException {

        final Bookshelf mappedBookshelf = mCurrentLibrary.createAsBookshelf(context);
        CalibreLibraryDao.getInstance().update(mCurrentLibrary);
        return mappedBookshelf;
    }

    @NonNull
    Bookshelf createVirtualLibraryAsBookshelf(@NonNull final Context context,
                                              final int position)
            throws DaoWriteException {

        final CalibreVirtualLibrary vlib = mCurrentLibrary.getVirtualLibraries().get(position);
        final Bookshelf mappedBookshelf = vlib.createAsBookshelf(context);
        CalibreLibraryDao.getInstance().update(vlib);
        return mappedBookshelf;
    }
}
