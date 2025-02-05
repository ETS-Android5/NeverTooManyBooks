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
package com.hardbacknutter.nevertoomanybooks.backup.backupbase;

import android.content.ContentResolver;
import android.content.Context;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.bin.CoverRecordReader;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordEncoding;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * This is the base for full-fledged archives which can contain all {@link RecordType}.
 * <p>
 * It's encoding-agnostic with the exception of {@link RecordEncoding#Cover}.
 * <p>
 * Currently supported formats.
 * <ul>
 *     <li>v5: identical to v4,
 *              but a number of internal preferences have been changed/deleted.
 *     </li>
 *     <li>v4: Books will contain REFERENCES to Bookshelves and CalibreLibraries;
 *              and FULL data on other related objects.
 *         <ul>
 *             <li>{@link RecordType#MetaData} :            {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Styles} :              {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Preferences} :         {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Certificates} :        {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Bookshelves} :         {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#CalibreLibraries} :    {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#CalibreCustomFields} : {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Books} :               {@link RecordEncoding#Json}</li>
 *             <li>Multiple {@link RecordType#Cover}</li>
 *         </ul>
 *     </li>
 *     <li>v3: Books will contain FULL data on related objects.
 *         <ul>
 *             <li>{@link RecordType#MetaData} :     {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Styles} :       {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Preferences} :  {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Certificates} : {@link RecordEncoding#Json}</li>
 *             <li>{@link RecordType#Books} :        {@link RecordEncoding#Json}</li>
 *             <li>Multiple {@link RecordType#Cover}</li>
 *         </ul>
 *     </li>
 *     <li>v2: xml/csv
 *         <ul>
 *             <li>{@link RecordType#MetaData} :     {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Styles} :       {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Preferences} :  {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Certificates} : {@link RecordEncoding#Xml}</li>
 *             <li>{@link RecordType#Books} :        {@link RecordEncoding#Csv}</li>
 *             <li>Multiple {@link RecordType#Cover}</li>
 *         </ul>
 *     </li>
 *     <li>v1: obsolete</li>
 * </ul>
 */
public abstract class ArchiveReaderAbstract
        implements DataReader<ArchiveMetaData, ImportResults> {

    /** Import configuration. */
    @NonNull
    private final ImportHelper importHelper;
    /** Provide access to the Uri InputStream. */
    @NonNull
    private final ContentResolver contentResolver;
    /** progress message. */
    private final String coversText;
    /** progress message. */
    private final String progressMessage;
    /** The accumulated results. */
    @NonNull
    private final ImportResults results = new ImportResults();

    /** Re-usable cover reader. */
    @Nullable
    private RecordReader coverReader;
    /** The INFO data read from the start of the archive. */
    @Nullable
    private ArchiveMetaData metaData;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param helper  import configuration
     */
    protected ArchiveReaderAbstract(@NonNull final Context context,
                                    @NonNull final ImportHelper helper) {
        importHelper = helper;
        contentResolver = context.getContentResolver();

        coversText = context.getString(R.string.lbl_covers);
        progressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    @WorkerThread
    @Override
    public void validate(@NonNull final Context context)
            throws DataReaderException, IOException {
        if (metaData == null) {
            // reading it will either assign a value to mMetaData, or throw exceptions
            readMetaData(context);
        }

        // the info block will/can do more checks.
        metaData.validate(context);
    }

    @NonNull
    protected InputStream openInputStream()
            throws FileNotFoundException {
        final InputStream is = contentResolver.openInputStream(importHelper.getUri());
        if (is == null) {
            throw new FileNotFoundException(importHelper.getUri().toString());
        }
        return new BufferedInputStream(is, RecordReader.BUFFER_SIZE);
    }

    /**
     * An archive based on this class <strong>MUST</strong> have an info block.
     * <p>
     * On any failure, this method throws an exception + {@link #metaData} will be {@code null}.
     *
     * @param context Current context
     *
     * @return a populated Optional with the metadata + {@link #metaData} assigned.
     */
    @Override
    @WorkerThread
    @NonNull
    public Optional<ArchiveMetaData> readMetaData(@NonNull final Context context)
            throws DataReaderException, IOException {

        if (metaData == null) {
            RecordReader reader = null;
            try {
                final ArchiveReaderRecord record = seek(RecordType.MetaData)
                        .orElseThrow(() -> new DataReaderException(
                                context.getString(R.string.error_file_not_recognized)));

                final RecordEncoding encoding = record
                        .getEncoding().orElseThrow(() -> new DataReaderException(
                                context.getString(R.string.error_file_not_recognized)));

                reader = encoding
                        .createReader(context, EnumSet.of(RecordType.MetaData))
                        .orElseThrow(() -> new DataReaderException(
                                context.getString(R.string.error_file_not_recognized)));

                metaData = reader
                        .readMetaData(context, record)
                        .orElseThrow(() -> new DataReaderException(
                                context.getString(R.string.error_file_not_recognized)));

                // Once here, the metaData should never be null, but AndroidStudio
                // does not seem to realise this; hence adding if/throw
                if (metaData == null) {
                    throw new DataReaderException(context.getString(
                            R.string.error_file_not_recognized));
                }
            } finally {
                if (reader != null) {
                    reader.close();
                }
                // We MUST reset the stream here, so the caller gets a pristine stream.
                closeInputStream();
            }
        }

        return Optional.of(metaData);
    }

    /**
     * Do a full import.
     * <ol>
     * <li>The header is assumed to already have been read by {@link #readMetaData(Context)}</li>
     * <li>Seek and read {@link RecordType#Styles}</li>
     * <li>Seek and read {@link RecordType#Preferences}</li>
     * <li>Sequentially read records as encountered.</li>
     * </ol>
     *
     * @param context          Current context
     * @param progressListener Progress and cancellation interface
     *
     * @return results summary
     *
     * @throws IOException         on failure
     * @throws DataReaderException on record format failures
     */
    @NonNull
    @Override
    @WorkerThread
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException, IOException, StorageException {

        // Sanity check: the archive info should have been read during the validate phase
        // This is also a check that the validate method has been called.
        Objects.requireNonNull(metaData, "metaData");

        final int archiveVersion = metaData.getArchiveVersion();
        switch (archiveVersion) {
            case 5:
            case 4:
            case 3:
            case 2:
                // The reader is flexible enough to detect the different versions for now.
                readV2(context, metaData, progressListener);
                break;

            case 1:
            default:
                throw new DataReaderException(context.getString(
                        R.string.error_unsupported_version_v, archiveVersion));
        }

        return results;
    }

    private void readV2(@NonNull final Context context,
                        @NonNull final ArchiveMetaData metaData,
                        @NonNull final ProgressListener progressListener)
            throws DataReaderException, IOException, StorageException {

        final Set<RecordType> recordTypes = importHelper.getRecordTypes();
        RecordType.addRelatedTypes(recordTypes);

        int estimatedSteps = 1 + metaData.getBookCount().orElse(0);

        final boolean readCovers = recordTypes.contains(RecordType.Cover);
        if (readCovers) {
            coverReader = new CoverRecordReader();

            final Optional<Integer> coverCount = metaData.getCoverCount();
            if (coverCount.isPresent()) {
                estimatedSteps += coverCount.get();
            } else {
                // We don't have a count, so assume each book has 1 cover.
                estimatedSteps *= 2;
            }
        }
        progressListener.setMaxPos(estimatedSteps);

        try {
            // Seek the styles record first.
            // We'll need them to resolve styles referenced in Preferences and Bookshelves.
            if (recordTypes.contains(RecordType.Styles)) {
                progressListener.publishProgress(
                        1, context.getString(R.string.lbl_styles_long));
                final Optional<ArchiveReaderRecord> record = seek(RecordType.Styles);
                if (record.isPresent()) {
                    readRecord(context, recordTypes, record.get(), progressListener);
                }
                closeInputStream();
            }

            // Seek the preferences record next, so we can apply any prefs while reading data.
            if (recordTypes.contains(RecordType.Preferences)) {
                progressListener.publishProgress(
                        1, context.getString(R.string.lbl_settings));
                final Optional<ArchiveReaderRecord> record = seek(RecordType.Preferences);
                if (record.isPresent()) {
                    readRecord(context, recordTypes, record.get(), progressListener);
                }
                closeInputStream();
            }

            Optional<ArchiveReaderRecord> nextRecord;
            // process each entry based on type, unless we are cancelled.
            while ((nextRecord = next()).isPresent()
                   && !progressListener.isCancelled()) {

                final ArchiveReaderRecord record = nextRecord.get();
                if (record.getType().isPresent()) {
                    final RecordType type = record.getType().get();

                    if ((type == RecordType.Cover && readCovers)

                        || (type == RecordType.Books
                            && recordTypes.contains(RecordType.Books))

                        || (type == RecordType.Bookshelves
                            && recordTypes.contains(RecordType.Bookshelves))

                        || (type == RecordType.Certificates
                            && recordTypes.contains(RecordType.Certificates))

                        || (type == RecordType.CalibreLibraries
                            && recordTypes.contains(RecordType.CalibreLibraries))

                        || (type == RecordType.CalibreCustomFields
                            && recordTypes.contains(RecordType.CalibreCustomFields))

                    ) {
                        readRecord(context, recordTypes, record, progressListener);
                    }
                }
            }
        } finally {
            try {
                close();
            } catch (@NonNull final IOException ignore) {
                // ignore
            }
        }
    }

    private void readRecord(@NonNull final Context context,
                            @NonNull final Set<RecordType> importEntriesAllowed,
                            @NonNull final ArchiveReaderRecord record,
                            @NonNull final ProgressListener progressListener)
            throws DataReaderException, IOException, StorageException {

        final Optional<RecordEncoding> recordEncoding = record.getEncoding();
        if (recordEncoding.isPresent()) {
            final RecordEncoding encoding = recordEncoding.get();

            // there will be many covers... we're re-using a single RecordReader
            if (encoding == RecordEncoding.Cover) {
                //noinspection ConstantConditions
                results.add(coverReader.read(context, record, importHelper, progressListener));
                // send accumulated progress for the total nr of covers
                final String msg = String.format(progressMessage,
                                                 coversText,
                                                 results.coversCreated,
                                                 results.coversUpdated,
                                                 results.coversSkipped);
                progressListener.publishProgress(1, msg);

            } else {
                // everything else, keep it clean and create a new reader for each entry.
                RecordReader reader = null;
                try {
                    final Optional<RecordReader> optReader = encoding
                            .createReader(context, importEntriesAllowed);
                    if (optReader.isPresent()) {
                        reader = optReader.get();
                        results.add(reader.read(context, record, importHelper, progressListener));
                    } else {
                        results.recordsSkipped++;
                    }
                } finally {
                    if (reader != null) {
                        reader.close();
                    }
                }
            }
        } else {
            results.recordsSkipped++;
        }
    }

    /**
     * Concrete reader should implement {@link #closeInputStream}.
     *
     * @throws IOException on failure
     */
    @Override
    @CallSuper
    public void close()
            throws IOException {
        closeInputStream();

        if (coverReader != null) {
            coverReader.close();
        }

        ServiceLocator.getInstance().getMaintenanceDao().purge();
    }

    /**
     * Read the next {@link ArchiveReaderRecord} from the backup.
     *
     * @return The next record, or {@code null} if at end
     *
     * @throws IOException on failure
     */
    @WorkerThread
    @NonNull
    protected abstract Optional<ArchiveReaderRecord> next()
            throws IOException;

    /**
     * Scan the input for the desired record type.
     * It's the responsibility of the caller to call {@link #closeInputStream} as needed.
     *
     * @param type to get
     *
     * @return Optional with the record
     *
     * @throws DataReaderException wraps a format specific Exception
     * @throws IOException         on failure
     */
    @WorkerThread
    @NonNull
    protected abstract Optional<ArchiveReaderRecord> seek(@NonNull RecordType type)
            throws DataReaderException, IOException;

    /**
     * Reset the reader so {@link #next()} will get the first record.
     *
     * @throws IOException on failure
     */
    protected abstract void closeInputStream()
            throws IOException;
}
