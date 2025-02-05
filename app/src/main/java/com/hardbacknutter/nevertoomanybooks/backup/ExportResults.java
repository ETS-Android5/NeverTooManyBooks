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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.io.DataWriter;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.io.RecordWriter;
import com.hardbacknutter.nevertoomanybooks.io.WriterResults;

/**
 * Value class to report back what was exported.
 * <p>
 * Used by {@link RecordWriter} and accumulated in {@link DataWriter}.
 */
public class ExportResults
        extends WriterResults {

    /** {@link Parcelable}. */
    public static final Creator<ExportResults> CREATOR = new Creator<>() {
        @Override
        public ExportResults createFromParcel(@NonNull final Parcel in) {
            return new ExportResults(in);
        }

        @Override
        public ExportResults[] newArray(final int size) {
            return new ExportResults[size];
        }
    };

    /** id's of books we exported. */
    private final List<Long> booksExported = new ArrayList<>();
    /** filenames of covers exported. */
    private final List<String> coversExported = new ArrayList<>();
    /** #styles we exported. */
    public int styles;
    /** #bookshelves we exported. */
    public int bookshelves;
    /** #calibreLibraries we exported. */
    public int calibreLibraries;
    /** #calibreLibraries we exported. */
    public int calibreCustomFields;
    /** #preferences we exported. */
    public int preferences;
    /** #certificates we exported. */
    public int certificates;
    /** whether we exported the actual database. */
    public boolean database;

    public ExportResults() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private ExportResults(@NonNull final Parcel in) {
        in.readList(booksExported, getClass().getClassLoader());
        in.readStringList(coversExported);

        bookshelves = in.readInt();
        calibreLibraries = in.readInt();
        calibreCustomFields = in.readInt();
        styles = in.readInt();
        preferences = in.readInt();
        certificates = in.readInt();
        database = in.readByte() != 0;
    }

    public boolean has(@NonNull final RecordType recordType) {
        switch (recordType) {
            case Styles:
                return styles > 0;
            case Preferences:
                return preferences > 0;
            case Certificates:
                return certificates > 0;
            case Bookshelves:
                return bookshelves > 0;
            case CalibreLibraries:
                return calibreLibraries > 0;
            case CalibreCustomFields:
                return calibreCustomFields > 0;
            case Books:
                return getBookCount() > 0;
            case Cover:
                return getCoverCount() > 0;

            case Database:
                return database;

            case MetaData:
            case AutoDetect:
            default:
                throw new IllegalArgumentException();
        }
    }

    /**
     * Add a set of results to the current set of results.
     *
     * @param results to add
     */
    public void add(@NonNull final ExportResults results) {
        booksExported.addAll(results.booksExported);
        coversExported.addAll(results.coversExported);

        bookshelves += results.bookshelves;
        calibreLibraries += results.calibreLibraries;
        calibreCustomFields += results.calibreCustomFields;
        styles += results.styles;
        preferences += results.preferences;
        certificates += results.certificates;

        if (results.database) {
            database = true;
        }
    }

    @Override
    public void addBook(@IntRange(from = 1) final long bookId) {
        booksExported.add(bookId);
    }

    @Override
    public int getBookCount() {
        return booksExported.size();
    }

    @VisibleForTesting
    @NonNull
    public List<Long> getBooksExported() {
        return booksExported;
    }

    @Override
    public void addCover(@NonNull final String path) {
        coversExported.add(path);
    }

    @Override
    public int getCoverCount() {
        return coversExported.size();
    }

    /**
     * Return the full list of cover filenames as collected with {@link #addCover}.
     * <p>
     * This is used/needed for the two-step backup process, where step one exports books,
     * and collects cover filenames, and than (calling this method) in a second step exports
     * the covers.
     *
     * @return list
     */
    @NonNull
    public List<String> getCoverFileNames() {
        return coversExported;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeList(booksExported);
        dest.writeStringList(coversExported);

        dest.writeInt(bookshelves);
        dest.writeInt(calibreLibraries);
        dest.writeInt(calibreCustomFields);
        dest.writeInt(styles);
        dest.writeInt(preferences);
        dest.writeInt(certificates);
        dest.writeByte((byte) (database ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    @NonNull
    public String toString() {
        return "ExportResults{"
               + "booksExported=" + booksExported
               + ", coversExported=" + coversExported
               + ", bookshelves=" + bookshelves
               + ", calibreLibraries=" + calibreLibraries
               + ", calibreCustomFields=" + calibreCustomFields
               + ", styles=" + styles
               + ", preferences=" + preferences
               + ", certificates=" + certificates
               + ", database=" + database
               + '}';
    }
}
