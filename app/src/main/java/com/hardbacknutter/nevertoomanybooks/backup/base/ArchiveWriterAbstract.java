/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.backup.base;

import android.content.Context;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Implementation of <strong>encoding-agnostic</strong> {@link ArchiveWriter} methods.
 * <p>
 * The {@link #write(Context, ProgressListener)} method executes the interface method flow.
 * <p>
 * 2020-12-21: we've moved to only having a single child class:
 * {@link com.hardbacknutter.nevertoomanybooks.backup.zip.ZipArchiveWriter}
 * so we could eliminate this class. But let's keep it open for the future for now.
 */
public abstract class ArchiveWriterAbstract
        implements ArchiveWriter {

    /** Log tag. */
    private static final String TAG = "ArchiveWriterAbstract";

    /**
     * See {@link ArchiveReaderAbstract} class docs for the version descriptions.
     * <p>
     * RELEASE: set correct archiver version
     */
    private static final int VERSION = 3;

    /**
     * Arbitrary number of steps added to the progress max value.
     * This covers the styles/prefs/etc... and a small extra safety.
     */
    private static final int EXTRA_STEPS = 10;
    /** Database Access. */
    @NonNull
    protected final DAO mDb;
    /** Export configuration. */
    @NonNull
    private final ExportHelper mHelper;
    /** The accumulated results. */
    @NonNull
    private final ExportResults mResults = new ExportResults();

    /** {@link #prepareBooks} writes to this file; {@link #writeBooks} copies it to the archive. */
    @Nullable
    private File mTmpBooksFile;

    /**
     * Constructor.
     *
     * @param helper export configuration
     */
    protected ArchiveWriterAbstract(@NonNull final ExportHelper helper) {
        mHelper = helper;
        mDb = new DAO(TAG);
    }

    /**
     * We always write the latest version archives (no backwards compatibility).
     */
    @Override
    public int getVersion() {
        return VERSION;
    }


    /**
     * Do a full backup.
     *
     * @param context          Current context
     * @param progressListener to send progress updates to
     *
     * @throws IOException on failure
     */
    @NonNull
    @Override
    @WorkerThread
    public ExportResults write(@NonNull final Context context,
                               @NonNull final ProgressListener progressListener)
            throws IOException {

        // do a cleanup before we start writing
        mDb.purge();

        final Set<RecordType> exportEntities = mHelper.getExporterEntries();

        final boolean writeCovers = this instanceof SupportsCovers
                                    && exportEntities.contains(RecordType.Cover);

        try {
            int steps = mDb.countBooksForExport(mHelper.getUtcDateTimeSince());
            if (writeCovers) {
                // assume 1 book == 1 cover
                steps = 2 * steps;
            }
            // set as an estimated max value
            progressListener.setMaxPos(steps + EXTRA_STEPS);

            // Prepare data/files we need information of BEFORE we can write the archive header
            if (!progressListener.isCancelled()
                && exportEntities.contains(RecordType.Books)) {
                prepareBooks(context, progressListener);
            }

            // Recalculate the progress max value using the exact number of books/covers
            // which will now include the back-covers.
            progressListener.setMaxPos(mResults.getBookCount()
                                       + mResults.getCoverCount()
                                       + EXTRA_STEPS);

            // Start with the archive header
            writeMetaData(ArchiveMetaData.create(context, getVersion(), mResults));

            // Write styles first, and preferences next! This will facilitate & speedup
            // importing as we'll be seeking in the input archive for these.

            if (!progressListener.isCancelled()
                && exportEntities.contains(RecordType.Styles)) {
                writeRecord(context, RecordType.Styles, getEncoding(RecordType.Styles),
                            progressListener);
            }

            if (!progressListener.isCancelled()
                && exportEntities.contains(RecordType.Preferences)) {
                writeRecord(context, RecordType.Preferences, getEncoding(RecordType.Preferences),
                            progressListener);
            }

            // Add the previously generated books file.
            if (!progressListener.isCancelled()
                && exportEntities.contains(RecordType.Books)) {
                writeBooks();
            }

            // Always do the covers as the last step
            if (!progressListener.isCancelled() && writeCovers && mResults.getCoverCount() > 0) {
                ((SupportsCovers) this).writeCovers(context, progressListener);
            }

        } finally {
            // closing a very large archive may take a while, so keep the progress dialog open
            progressListener.setIndeterminate(true);
            progressListener.publishProgressStep(
                    0, context.getString(R.string.progress_msg_please_wait));
            // reset; won't take effect until the next publish call.
            progressListener.setIndeterminate(null);
        }

        return mResults;
    }

    /**
     * Write a {@link RecordType#MetaData} record.
     *
     * @param metaData the bundle of information to write
     *
     * @throws IOException on failure
     */
    private void writeMetaData(@NonNull final ArchiveMetaData metaData)
            throws IOException {

        final RecordEncoding encoding = getEncoding(RecordType.MetaData);

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, 1024);
             RecordWriter recordWriter = encoding.createWriter(mHelper.getUtcDateTimeSince())) {
            recordWriter.writeMetaData(bw, metaData);
        }

        putByteArray(RecordType.MetaData.getName() + encoding.getFileExt(),
                     os.toByteArray(), true);
    }

    /**
     * Writes a single record. One of:
     * <ul>
     *     <li>{@link RecordType#Styles}</li>
     *     <li>{@link RecordType#Preferences}</li>
     * </ul>
     *
     * @param context          Current context
     * @param recordType       of record
     * @param encoding         for the record
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    private void writeRecord(@NonNull final Context context,
                             @NonNull final RecordType recordType,
                             @NonNull final RecordEncoding encoding,
                             @NonNull final ProgressListener progressListener)
            throws IOException {

        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        try (Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
             RecordWriter recordWriter = encoding.createWriter(mHelper.getUtcDateTimeSince())) {
            mResults.add(recordWriter.write(context, bw, EnumSet.of(recordType),
                                            mHelper.getOptions(), progressListener));
        }
        putByteArray(recordType.getName() + encoding.getFileExt(), os.toByteArray(), true);
    }

    /**
     * Prepare the Books and Covers.
     * <p>
     * For each book which will be exported, the {@link RecordWriter} implementation should call
     * {@link ExportResults#addBook(long)} and {@link ExportResults#addCover(String)} as needed.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    private void prepareBooks(@NonNull final Context context,
                              @NonNull final ProgressListener progressListener)
            throws IOException {

        final RecordEncoding encoding = getEncoding(RecordType.Books);

        // Filter to valid options for this step.
        final Set<RecordType> recordTypes = EnumSet.noneOf(RecordType.class);

        if (mHelper.getExporterEntries().contains(RecordType.Books)) {
            recordTypes.add(RecordType.Books);
        }
        if (mHelper.getExporterEntries().contains(RecordType.Cover)) {
            recordTypes.add(RecordType.Cover);
        }

        mTmpBooksFile = File.createTempFile("books_", encoding.getFileExt());
        mTmpBooksFile.deleteOnExit();
        try (OutputStream os = new FileOutputStream(mTmpBooksFile);
             Writer osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             Writer bw = new BufferedWriter(osw, RecordWriter.BUFFER_SIZE);
             RecordWriter recordWriter = encoding.createWriter(mHelper.getUtcDateTimeSince())) {
            mResults.add(recordWriter.write(context, bw, recordTypes, mHelper.getOptions(),
                                            progressListener));
        }
    }

    /**
     * Write the books.
     *
     * @throws IOException on failure
     */
    private void writeBooks()
            throws IOException {
        Objects.requireNonNull(mTmpBooksFile);

        final String filename =
                RecordType.Books.getName() + getEncoding(RecordType.Books).getFileExt();
        try {
            putFile(filename, mTmpBooksFile, true);
        } finally {
            FileUtils.delete(mTmpBooksFile);
        }
    }

    /**
     * An archive-agnostic default implementation for writing cover files.
     * Not supported by all child classes.
     * <p>
     * Write each cover file as collected in {@link #prepareBooks}
     * to the archive.
     *
     * @param context          Current context
     * @param progressListener Listener to receive progress information.
     *
     * @throws IOException on failure
     */
    public void writeCovers(@NonNull final Context context,
                            @NonNull final ProgressListener progressListener)
            throws IOException {

        progressListener.publishProgressStep(0, context.getString(R.string.lbl_covers_long));

        int exported = 0;
        int delta = 0;
        long lastUpdate = 0;

        final String coverStr = context.getString(R.string.lbl_covers);
        for (final String filename : mResults.getCoverFileNames()) {
            // We're using jpg, png.. don't bother compressing.
            // Compressing might actually make some image files bigger!
            putFile(filename, AppDir.Covers.getFile(context, filename), false);
            exported++;

            delta++;
            final long now = System.currentTimeMillis();
            if ((now - lastUpdate) > progressListener.getUpdateIntervalInMs()) {
                final String msg = context.getString(R.string.name_colon_value,
                                                     coverStr,
                                                     String.valueOf(exported));
                progressListener.publishProgressStep(delta, msg);
                lastUpdate = now;
                delta = 0;
            }
        }
    }

    @NonNull
    @AnyThread
    protected abstract RecordEncoding getEncoding(@NonNull final RecordType recordType);

    /**
     * Write a generic byte array to the archive.
     *
     * @param name     for the entry
     * @param bytes    to store in the archive
     * @param compress Flag: compress the data if the writer supports it.
     *
     * @throws IOException on failure
     */
    protected abstract void putByteArray(@NonNull String name,
                                         @NonNull byte[] bytes,
                                         @SuppressWarnings("SameParameterValue") boolean compress)
            throws IOException;

    /**
     * Write a generic file to the archive.
     *
     * @param name     for the entry;  allows easier overriding of the file name
     * @param file     to store in the archive
     * @param compress Flag: compress the file if the writer supports it.
     *
     * @throws IOException on failure
     */
    protected abstract void putFile(@NonNull final String name,
                                    @NonNull final File file,
                                    final boolean compress)
            throws IOException;

    /**
     * Concrete writer should override and close its output.
     *
     * @throws IOException on failure
     */
    @Override
    @CallSuper
    public void close()
            throws IOException {
        mDb.close();
    }

    public interface SupportsCovers {

        /**
         * Write the covers.
         *
         * @param context          Current context
         * @param progressListener Listener to receive progress information.
         *
         * @throws IOException on failure
         */
        @WorkerThread
        void writeCovers(@NonNull Context context,
                         @NonNull ProgressListener progressListener)
                throws IOException;
    }
}
