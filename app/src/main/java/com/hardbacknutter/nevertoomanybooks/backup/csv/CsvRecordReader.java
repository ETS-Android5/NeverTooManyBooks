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
import android.database.sqlite.SQLiteDoneException;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.backupbase.BaseRecordReader;
import com.hardbacknutter.nevertoomanybooks.backup.csv.coders.BookCoder;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * Implementation of {@link RecordReader} that reads a CSV file.
 * <ul>Supports:
 *      <li>{@link RecordType#Books}</li>
 * </ul>
 * <p>
 * A CSV file which was not written by this app, should be careful about encoding the following
 * characters:
 *
 * <strong>DOUBLE escape the '*' character; i.e. '*' should be encoded as \\\\*</strong>
 *
 * <ul>Always <strong>escape:</strong>
 *      <li>"</li>
 *      <li>'</li>
 *      <li>\</li>
 *      <li>\r</li>
 *      <li>\n</li>
 *      <li>\t</li>
 *      <li>|</li>
 *      <li>(</li>
 *      <li>)</li>
 * </ul>
 *
 * <ul>Unescaped special characters:
 *      <li>',' is recognised/used in an Author name: "family, given-names",<br>
 *          and as a list separator in a list of Bookshelf names.</li>
 *      <li>'|' is used as an element separator for fields that take more than one value.<br>
 *          e.g. a list of Author names, Series, ...</li>
 *      <li>'*' is used in few places where an element itself can consist of multiple parts.
 *          e.g. See Author and Series object encoding. (for advanced usage)</li>
 *      <li>'(' and ')' are used to add numbers or dates (between the brackets) to items.
 *          e.g. TOC entries can contain a date, Series will have the book number,... </li>
 * </ul>
 * <p>
 * Space characters are encoded for Authors names while exporting,
 * but it's not strictly needed although some exotic names might get mangled;
 * when in doubt, escape.
 * <p>
 * ENHANCE: after an import offer to run an internet update on the list of book id's
 * This would/could be split up....
 * - books inserted which had a uuid/id
 * - books updated
 * - books inserted while not having a uuid/id; i.e. unknown input source -> would require ISBN
 */
public class CsvRecordReader
        extends BaseRecordReader {

    /** Log tag. */
    private static final String TAG = "CsvRecordReader";

    @NonNull
    private final Locale userLocale;
    @NonNull
    private final BookCoder bookCoder;

    /**
     * Constructor.
     * <p>
     * Only supports {@link RecordType#Books}.
     *
     * @param context Current context
     */
    @AnyThread
    public CsvRecordReader(@NonNull final Context context) {
        super(context);

        bookCoder = new BookCoder(context);
        userLocale = context.getResources().getConfiguration().getLocales().get(0);
    }

    @Override
    @NonNull
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ArchiveReaderRecord record,
                              @NonNull final ImportHelper helper,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException,
                   StorageException,
                   IOException {

        results = new ImportResults();

        if (record.getType().isPresent()) {
            if (record.getType().get() == RecordType.Books) {

                // Read the whole file content into a list of lines.
                final List<String> allLines;
                // Don't close this stream
                final InputStream is = record.getInputStream();
                final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                final BufferedReader reader = new BufferedReader(isr, BUFFER_SIZE);

                try {
                    allLines = reader.lines().collect(Collectors.toList());
                } catch (@NonNull final UncheckedIOException e) {
                    // caused by lines()
                    //noinspection ConstantConditions
                    throw e.getCause();
                }

                if (!allLines.isEmpty()) {
                    readBooks(context, helper, allLines, progressListener);
                }
            }
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
            Log.d(TAG, "read|mResults=" + results);
        }
        return results;
    }

    private void readBooks(@NonNull final Context context,
                           @NonNull final ImportHelper helper,
                           @NonNull final List<String> books,
                           @NonNull final ProgressListener progressListener)
            throws StorageException,
                   DataReaderException {

        // First line in the import file must be the column names.
        // Store them to use as keys into the book.
        final String[] csvColumnNames = parse(context, 0, books.get(0));
        // sanity check: make sure they are lower case
        for (int i = 0; i < csvColumnNames.length; i++) {
            csvColumnNames[i] = csvColumnNames[i].toLowerCase(userLocale);
        }
        // check for required columns
        final List<String> csvColumnNamesList = Arrays.asList(csvColumnNames);
        // If a sync was requested, we'll need this column or cannot proceed.
        if (helper.getUpdateOption() == DataReader.Updates.OnlyNewer) {
            requireColumnOrThrow(context, csvColumnNamesList, DBKey.DATE_LAST_UPDATED__UTC);
        }

        // One book == One row. We start after the headings row.
        int row = 1;
        // Instance in time when we last send a progress message
        long lastUpdateTime = 0;
        // Count the nr of books in between progress updates.
        int delta = 0;

        // not perfect, but good enough
        if (progressListener.getMaxPos() < books.size()) {
            progressListener.setMaxPos(books.size());
        }

        final SynchronizedDb db = ServiceLocator.getInstance().getDb();

        Synchronizer.SyncLock txLock = null;

        while (row < books.size() && !progressListener.isCancelled()) {

            if (!db.inTransaction()) {
                txLock = db.beginTransaction(true);
            }
            try {
                final String[] csvDataRow = parse(context, row, books.get(row));

                if (csvDataRow.length != csvColumnNames.length) {
                    throw new DataReaderException(context.getString(
                            R.string.error_import_csv_column_count_mismatch, row));
                }

                final Book book = bookCoder.decode(context, csvColumnNames, csvDataRow);

                // Do we have a DBDefinitions.KEY_BOOK_UUID in the import ?
                final boolean hasUuid = handleUuid(book);
                // Do we have a DBDefinitions.KEY_PK_ID in the import ?
                final long importNumericId = extractNumericId(book);

                // ALWAYS let the UUID trump the ID; we may be importing someone else's list
                if (hasUuid) {
                    importBookWithUuid(context, helper, book, importNumericId);

                } else if (importNumericId > 0) {
                    importBookWithId(context, helper, book, importNumericId);

                } else {
                    importBook(context, book);
                }

                if (txLock != null) {
                    db.setTransactionSuccessful();
                }
            } catch (@NonNull final DaoWriteException | DataReaderException
                    | SQLiteDoneException e) {
                results.handleRowException(context, row, e, null);

            } finally {
                if (txLock != null) {
                    db.endTransaction(txLock);
                }
            }

            row++;

            delta++;
            final long now = System.currentTimeMillis();
            if ((now - lastUpdateTime) > progressListener.getUpdateIntervalInMs()
                && !progressListener.isCancelled()) {
                final String msg = String.format(progressMessage,
                                                 booksString,
                                                 results.booksCreated,
                                                 results.booksUpdated,
                                                 results.booksSkipped);
                progressListener.publishProgress(delta, msg);
                lastUpdateTime = now;
                delta = 0;
            }
        }

        // minus 1 to compensate for the last increment
        results.booksProcessed = row - 1;
    }

    /**
     * insert or update a single book which has a potentially usable id.
     *
     * @param context Current context
     * @param book    to import
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    private void importBookWithId(@NonNull final Context context,
                                  @NonNull final ImportHelper helper,
                                  @NonNull final Book book,
                                  final long importNumericId)
            throws StorageException, DaoWriteException {
        // Add the importNumericId back to the book.
        book.putLong(DBKey.PK_ID, importNumericId);

        // Is that id already in use ?
        if (bookDao.bookExistsById(importNumericId)) {
            // The id is in use, we will be updating an existing book (if allowed).

            // This is risky as we might overwrite a different book which happens
            // to have the same id, but other than skipping there is no other option for now.
            // Ideally, we should ask the user presenting a choice "skip/overwrite"
            final DataReader.Updates updateOption = helper.getUpdateOption();
            switch (updateOption) {
                case Overwrite: {
                    bookDao.update(context, book, BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                                  | BookDao.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                    results.booksUpdated++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                        Log.d(TAG, "importNumericId=" + importNumericId
                                   + "|Overwrite|" + book.getTitle());
                    }
                    break;
                }
                case OnlyNewer: {
                    final LocalDateTime localDate = bookDao.getLastUpdateDate(importNumericId);
                    if (localDate != null) {
                        final LocalDateTime importDate = dateParser.parse(
                                book.getString(DBKey.DATE_LAST_UPDATED__UTC));

                        if (importDate != null && importDate.isAfter(localDate)) {

                            bookDao.update(context, book,
                                           BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                           | BookDao.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                            results.booksUpdated++;
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                                Log.d(TAG, "importNumericId=" + importNumericId
                                           + "|update|" + book.getTitle());
                            }
                        }
                    }
                    break;
                }
                case Skip: {
                    results.booksSkipped++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                        Log.d(TAG, "importNumericId=" + importNumericId
                                   + "|Skip|" + book.getTitle());
                    }
                    break;
                }
            }

        } else {
            // The id is not in use, simply insert the book using the given importNumericId,
            // explicitly allowing the id to be reused
            final long insId = bookDao.insert(context, book,
                                              BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                              | BookDao.BOOK_FLAG_USE_ID_IF_PRESENT);
            results.booksCreated++;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                Log.d(TAG, "importNumericId=" + importNumericId
                           + "|insert=" + insId
                           + "|" + book.getTitle());
            }

        }
    }

    private void importBook(@NonNull final Context context,
                            final Book book)
            throws StorageException,
                   DaoWriteException {
        // Always import books which have no UUID/ID, even if the book is a potential duplicate.
        // We don't try and search/match but leave it to the user.
        final long insId = bookDao.insert(context, book, BookDao.BOOK_FLAG_IS_BATCH_OPERATION);
        results.booksCreated++;
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
            Log.d(TAG, "UUID=''"
                       + "|ID=0"
                       + "|insert=" + insId
                       + "|" + book.getTitle());
        }
    }

    /**
     * Process the UUID if present.
     *
     * @param book the book
     *
     * @return {@code true} if the book has a UUID
     */
    private boolean handleUuid(@NonNull final Book book) {

        final String uuid;

        // Get the "book_uuid", and remove from book if null/blank
        if (book.contains(DBKey.BOOK_UUID)) {
            uuid = book.getString(DBKey.BOOK_UUID);
            if (uuid.isEmpty()) {
                book.remove(DBKey.BOOK_UUID);
            }

        } else if (book.contains("uuid")) {
            // second chance: see if we have a "uuid" column.
            uuid = book.getString("uuid");
            // ALWAYS remove as we won't use this key again.
            book.remove("uuid");
            // but if we got a UUID from it, store it again, using the correct key
            if (!uuid.isEmpty()) {
                book.putString(DBKey.BOOK_UUID, uuid);
            }
        } else {
            uuid = null;
        }

        return uuid != null && !uuid.isEmpty();
    }

    /**
     * Process the ID if present. <strong>It will be removed from the Book.</strong>
     *
     * @param book the book
     *
     * @return the id, if any.
     */
    private long extractNumericId(@NonNull final Book book) {
        // Do we have a numeric id in the import ?
        // String: see book init, we copied all fields we find in the import file as text.
        final String idStr = book.getString(DBKey.PK_ID);
        // ALWAYS remove here to avoid type-issues further down. We'll re-add if needed.
        book.remove(DBKey.PK_ID);

        if (!idStr.isEmpty()) {
            try {
                return Long.parseLong(idStr);
            } catch (@NonNull final NumberFormatException ignore) {
                // don't log, it's fine.
            }
        }
        return 0;
    }

    /**
     * This CSV parser is not a complete parser, but it is "good enough".
     *
     * @param context Current context
     * @param row     row number
     * @param line    with CSV fields
     *
     * @return an array representing the row
     *
     * @throws DataReaderException on failure to parse this line
     */
    @NonNull
    private String[] parse(@NonNull final Context context,
                           final int row,
                           @NonNull final String line)
            throws DataReaderException {
        try {
            // Fields found in row
            final Collection<String> fields = new ArrayList<>();
            // Temporary storage for current field
            final StringBuilder sb = new StringBuilder();

            // Current position
            int pos = 0;
            // In a quoted string
            boolean inQuotes = false;
            // Found an escape char
            boolean isEsc = false;
            // 'Current' char
            char c;
            // Last position in row
            final int endPos = line.length() - 1;
            // 'Next' char
            char next = (line.isEmpty()) ? '\0' : line.charAt(0);

            // '\0' is used as (and artificial) null character indicating end-of-string.
            while (next != '\0') {
                // Get current and next char
                c = next;
                if (pos < endPos) {
                    next = line.charAt(pos + 1);
                } else {
                    next = '\0';
                }

                if (isEsc) {
                    switch (c) {
                        case '\\':
                            sb.append('\\');
                            break;

                        case 'r':
                            sb.append('\r');
                            break;

                        case 't':
                            sb.append('\t');
                            break;

                        case 'n':
                            sb.append('\n');
                            break;

                        default:
                            sb.append(c);
                            break;
                    }
                    isEsc = false;

                } else if (inQuotes) {
                    switch (c) {
                        case '"':
                            if (next == '"') {
                                // substitute two successive quotes with one quote
                                pos++;
                                if (pos < endPos) {
                                    next = line.charAt(pos + 1);
                                } else {
                                    next = '\0';
                                }
                                sb.append(c);

                            } else {
                                // end of quoted string
                                inQuotes = false;
                            }
                            break;

                        case '\\':
                            isEsc = true;
                            break;

                        default:
                            sb.append(c);
                            break;
                    }
                } else {
                    // This is just a raw string; no escape or quote active.
                    // Ignore leading whitespace.
                    if ((c != ' ' && c != '\t') || sb.length() != 0) {
                        switch (c) {
                            case '"':
                                if (sb.length() > 0) {
                                    // Fields with inner quotes MUST be escaped
                                    throw new DataReaderException(context.getString(
                                            R.string.warning_import_csv_unescaped_quote, row, pos));
                                } else {
                                    inQuotes = true;
                                }
                                break;

                            case '\\':
                                isEsc = true;
                                break;

                            case ',':
                                // Add this field and reset for the next.
                                fields.add(sb.toString());
                                sb.setLength(0);
                                break;

                            default:
                                sb.append(c);
                                break;
                        }
                    }
                }
                pos++;
            }

            // Add the remaining chunk
            fields.add(sb.toString());

            // Return the result as a String[].
            final String[] columns = new String[fields.size()];
            fields.toArray(columns);

            return columns;

        } catch (@NonNull final StackOverflowError e) {
            // StackOverflowError has been seen when the StringBuilder overflows.
            // The stack at the time was 1040kb. Not reproduced as yet.
            Log.e(TAG, "line.length=" + line.length() + "\n" + line, e);
            throw new DataReaderException(context.getString(R.string.error_import_csv_line_to_long,
                                                            row, line.length()), e);
        }
    }

    /**
     * Require a column to be present. First one found; remainders are not needed.
     *
     * @param context        Current context
     * @param columnsPresent the column names which are present
     * @param names          columns which should be checked for, in order of preference
     *
     * @throws DataReaderException if no suitable column is present
     */
    private void requireColumnOrThrow(@NonNull final Context context,
                                      @NonNull final List<String> columnsPresent,
                                      @NonNull final String... names)
            throws DataReaderException {


        for (final String name : names) {
            if (columnsPresent.contains(name)) {
                return;
            }
        }

        throw new DataReaderException(context.getString(
                R.string.error_import_csv_missing_columns_x, String.join(",", names)));
    }
}
