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
package com.hardbacknutter.nevertoomanybooks.sync;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.io.WriterResults;

/**
 * Value class to report back what was witten.
 */
public class SyncWriterResults
        extends WriterResults {

    /** {@link Parcelable}. */
    public static final Creator<SyncWriterResults> CREATOR = new Creator<>() {
        @Override
        public SyncWriterResults createFromParcel(@NonNull final Parcel in) {
            return new SyncWriterResults(in);
        }

        @Override
        public SyncWriterResults[] newArray(final int size) {
            return new SyncWriterResults[size];
        }
    };

    private int bookCount;
    private int coverCount;

    public SyncWriterResults() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private SyncWriterResults(@NonNull final Parcel in) {
        bookCount = in.readInt();
        coverCount = in.readInt();
    }

    @Override
    public void addBook(final long bookId) {
        bookCount++;
    }

    @Override
    public int getBookCount() {
        return bookCount;
    }

    @Override
    public void addCover(@NonNull final String path) {
        coverCount++;
    }

    @Override
    public int getCoverCount() {
        return coverCount;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(bookCount);
        dest.writeInt(coverCount);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "SyncWriterResults{"
               + "bookCount=" + bookCount
               + ", coverCount=" + coverCount
               + '}';
    }
}
