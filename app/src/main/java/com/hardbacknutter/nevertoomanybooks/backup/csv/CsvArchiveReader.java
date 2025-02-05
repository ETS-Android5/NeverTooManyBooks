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
package com.hardbacknutter.nevertoomanybooks.backup.csv;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * A minimal implementation of {@link DataReader} which reads a plain CSV file with books.
 */
public class CsvArchiveReader
        implements DataReader<ArchiveMetaData, ImportResults> {

    private static final String DB_BACKUP_NAME = "DbCsvBackup.db";
    private static final int DB_BACKUP_COPIES = 3;

    /** Import configuration. */
    @NonNull
    private final ImportHelper importHelper;

    /**
     * Constructor.
     *
     * @param helper import configuration
     */
    public CsvArchiveReader(@NonNull final ImportHelper helper) {
        importHelper = helper;
    }

    @NonNull
    @Override
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException, IOException, StorageException {

        // Importing CSV which we didn't create can be dangerous.
        // Backup the database, keeping up to CSV_BACKUP_COPIES copies.
        // ENHANCE: For now we don't inform the user of this nor offer a restore.

        FileUtils.copyWithBackup(new File(ServiceLocator.getInstance().getDb().getDatabasePath()),
                                 new File(ServiceLocator.getUpgradesDir(), DB_BACKUP_NAME),
                                 DB_BACKUP_COPIES);

        try (InputStream is = context.getContentResolver().openInputStream(importHelper.getUri());
             RecordReader recordReader = new CsvRecordReader(context)) {
            if (is == null) {
                throw new FileNotFoundException(importHelper.getUri().toString());
            }
            final ArchiveReaderRecord record = new CsvArchiveRecord(
                    importHelper.getUriInfo().getDisplayName(context), is);

            return recordReader.read(context, record, importHelper, progressListener);
        }
    }

    @Override
    public void close() {
        ServiceLocator.getInstance().getMaintenanceDao().purge();
    }

    @VisibleForTesting
    public static class CsvArchiveRecord
            implements ArchiveReaderRecord {

        @NonNull
        private final String mName;

        /** The record source stream. */
        @NonNull
        private final InputStream mIs;

        /**
         * Constructor.
         *
         * @param name of this record
         * @param is   InputStream to use
         */
        CsvArchiveRecord(@NonNull final String name,
                         @NonNull final InputStream is) {
            mName = name;
            mIs = is;
        }

        @NonNull
        public Optional<RecordType> getType() {
            return Optional.of(RecordType.Books);
        }

        @NonNull
        @Override
        public Optional<RecordEncoding> getEncoding() {
            return Optional.of(RecordEncoding.Csv);
        }

        @NonNull
        @Override
        public String getName() {
            return mName;
        }

        @Override
        public long getLastModifiedEpochMilli() {
            // just pretend
            return Instant.now().toEpochMilli();
        }

        @NonNull
        @Override
        public InputStream getInputStream() {
            return mIs;
        }
    }
}
