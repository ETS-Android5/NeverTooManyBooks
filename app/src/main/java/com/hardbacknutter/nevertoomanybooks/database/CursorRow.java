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
package com.hardbacknutter.nevertoomanybooks.database;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;

/**
 * A handy wrapper allowing to fetch columns by name.
 * <p>
 * <strong>Note:</strong> converts {@code null} Strings to an empty String.
 * <p>
 * Tip: when using a CursorRow as a parameter to a constructor, e.g.
 * {@link com.hardbacknutter.nevertoomanybooks.entities.Bookshelf#Bookshelf(long, DataHolder)}
 * always pass the id additionally/separately. This gives the calling code a change to use
 * for example the foreign key id.
 */
public class CursorRow
        implements DataHolder {

    /** the mapped cursor. */
    private final Cursor mCursor;

    /**
     * Constructor.
     *
     * @param cursor to read from
     */
    public CursorRow(@NonNull final Cursor cursor) {
        mCursor = cursor;
    }

    @Override
    @NonNull
    public Set<String> keySet() {
        return Set.copyOf(Arrays.asList(mCursor.getColumnNames()));
    }

    /**
     * @param key the domain to get
     *
     * @return {@code true} if this cursor contains the specified domain.
     */
    @Override
    public boolean contains(@NonNull final String key) {
        return mCursor.getColumnIndex(key) > -1;
    }

    @Override
    @Nullable
    public String getString(@NonNull final String key,
                            @Nullable final String defValue)
            throws ColumnNotPresentException {

        final int col = mCursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        if (mCursor.isNull(col)) {
            return defValue;
        }
        return mCursor.getString(col);
    }

    /**
     * @param key to get
     *
     * @return the boolean value of the column ({@code null} comes back as false).
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public boolean getBoolean(@NonNull final String key)
            throws ColumnNotPresentException {
        return getInt(key) == 1;
    }

    /**
     * @param key to get
     *
     * @return the int value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public int getInt(@NonNull final String key)
            throws ColumnNotPresentException {

        final int col = mCursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (mCursor.isNull(col)) {
        //     return 0;
        // }
        return mCursor.getInt(col);
    }

    /**
     * @param key to get
     *
     * @return the long value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public long getLong(@NonNull final String key)
            throws ColumnNotPresentException {

        final int col = mCursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (mCursor.isNull(col)) {
        //     return 0;
        // }
        return mCursor.getLong(col);
    }

    /**
     * @param key to get
     *
     * @return the double value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public double getDouble(@NonNull final String key)
            throws ColumnNotPresentException {

        final int col = mCursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (mCursor.isNull(col)) {
        //     return 0;
        // }
        return mCursor.getDouble(col);
    }

    /**
     * @param key to get
     *
     * @return the double value of the column ({@code null} comes back as 0)
     *
     * @throws ColumnNotPresentException if the column was not present.
     */
    @Override
    public float getFloat(@NonNull final String key)
            throws ColumnNotPresentException {

        final int col = mCursor.getColumnIndex(key);
        if (col == -1) {
            throw new ColumnNotPresentException(key);
        }
        // if (mCursor.isNull(col)) {
        //     return 0;
        // }
        return mCursor.getFloat(col);
    }

//    /**
//     * @param key to get
//     *
//     * @return the byte array (blob) of the column
//     *
//     * @throws ColumnNotPresentException if the column was not present.
//     */
//    public byte[] getBlob(@NonNull final String key)
//            throws ColumnNotPresentException {
//
//        final int col = mCursor.getColumnIndex(key);
//        if (col == -1) {
//            throw new ColumnNotPresentException(key);
//        }
//        // if (mCursor.isNull(col)) {
//        //     return null;
//        // }
//        return mCursor.getBlob(col);
//    }

}
