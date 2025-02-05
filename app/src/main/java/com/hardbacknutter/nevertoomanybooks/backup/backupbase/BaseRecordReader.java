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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.time.LocalDateTime;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookDao;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.io.DataReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.ISODateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public abstract class BaseRecordReader
        implements RecordReader {

    private static final String TAG = "BaseRecordReader";

    /** Database Access. */
    @NonNull
    protected final BookDao bookDao;

    @NonNull
    protected final DateParser dateParser;
    /** cached localized "Books" string. */
    @NonNull
    protected final String booksString;
    /** cached localized progress string. */
    @NonNull
    protected final String progressMessage;

    protected ImportResults results;

    protected BaseRecordReader(@NonNull final Context context) {
        bookDao = ServiceLocator.getInstance().getBookDao();
        dateParser = new ISODateParser();

        booksString = context.getString(R.string.lbl_books);
        progressMessage = context.getString(R.string.progress_msg_x_created_y_updated_z_skipped);
    }

    /**
     * insert or update a single book which has a <strong>valid UUID</strong>.
     *
     * @param context Current context
     * @param book    to import
     *
     * @throws StorageException  The covers directory is not available
     * @throws DaoWriteException on failure
     */
    protected void importBookWithUuid(@NonNull final Context context,
                                      @NonNull final ImportHelper helper,
                                      @NonNull final Book book,
                                      final long importNumericId)
            throws StorageException,
                   DaoWriteException {
        // Verified to be valid earlier.
        final String uuid = book.getString(DBKey.BOOK_UUID);

        // check if the book exists in our database, and fetch it's id.
        final long databaseBookId = bookDao.getBookIdByUuid(uuid);
        if (databaseBookId > 0) {
            // The book exists in our database (matching UUID).
            // We'll use a delta: explicitly set the EXISTING id on the book
            // (the importBookId was removed earlier, and is IGNORED)
            book.putLong(DBKey.PK_ID, databaseBookId);

            // UPDATE the existing book (if allowed).
            final DataReader.Updates updateOption = helper.getUpdateOption();
            switch (updateOption) {
                case Overwrite: {
                    bookDao.update(context, book, BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                                  | BookDao.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                    results.booksUpdated++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                        Log.d(TAG, "UUID=" + uuid
                                   + "|databaseBookId=" + databaseBookId
                                   + "|Overwrite|" + book.getTitle());
                    }
                    break;
                }
                case OnlyNewer: {
                    final LocalDateTime localDate = bookDao.getLastUpdateDate(databaseBookId);
                    if (localDate != null) {
                        final LocalDateTime importDate = dateParser.parse(
                                book.getString(DBKey.DATE_LAST_UPDATED__UTC));

                        if (importDate != null && importDate.isAfter(localDate)) {

                            bookDao.update(context, book,
                                           BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                           | BookDao.BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT);
                            results.booksUpdated++;
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                                Log.d(TAG, "UUID=" + uuid
                                           + "|databaseBookId=" + databaseBookId
                                           + "|OnlyNewer|" + book.getTitle());
                            }
                        }
                    }
                    break;
                }
                case Skip: {
                    results.booksSkipped++;
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                        Log.d(TAG, "UUID=" + uuid
                                   + "|databaseBookId=" + databaseBookId
                                   + "|Skip|" + book.getTitle());
                    }
                    break;
                }
            }
        } else {
            // The book does NOT exist in our database (no match for the UUID), insert it.

            // If we have an importBookId, and it does not already exist, we reuse it.
            if (importNumericId > 0 && !bookDao.bookExistsById(importNumericId)) {
                book.putLong(DBKey.PK_ID, importNumericId);
            }

            // the Book object will contain:
            // - valid DBDefinitions.KEY_BOOK_UUID not existent in the database
            // - NO id, OR an id which does not exist in the database yet.
            // INSERT, explicitly allowing the id to be reused if present
            bookDao.insert(context, book, BookDao.BOOK_FLAG_IS_BATCH_OPERATION
                                          | BookDao.BOOK_FLAG_USE_ID_IF_PRESENT);
            results.booksCreated++;
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMPORT_CSV_BOOKS) {
                Log.d(TAG, "UUID=" + book.getString(DBKey.BOOK_UUID)
                           + "|importNumericId=" + importNumericId
                           + "|INSERT|book=" + book.getId() + "|" + book.getTitle());
            }
        }
    }
}
