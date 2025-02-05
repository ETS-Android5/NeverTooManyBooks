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
package com.hardbacknutter.nevertoomanybooks.database.dao;

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.TypedCursor;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.TransactionException;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.BookLight;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

public interface BookDao {

    /**
     * Book Insert/update flag.
     * If set, relax some rules which would affect performance otherwise.
     * This is/should only be used during imports.
     */
    int BOOK_FLAG_IS_BATCH_OPERATION = 1;
    /**
     * Book Insert/update flag.
     * If set, and the book bundle has an id !=0, force the id to be used.
     * This is/should only be used during imports of new books
     * i.e. during import of a backup archive/csv
     */
    int BOOK_FLAG_USE_ID_IF_PRESENT = 1 << 1;
    /**
     * Book Insert/update flag.
     * If set, the UPDATE_DATE field from the bundle should be trusted.
     * If this flag is not set, the UPDATE_DATE will be set based on the current time
     */
    int BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT = 1 << 2;

    /**
     * Update the 'last updated' of the given book.
     * If successful, the book itself will also be updated with
     * the current date-time (which will be very slightly 'later' then what we store).
     *
     * @param book to update
     *
     * @return {@code true} on success
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean touch(@NonNull final Book book);

    /**
     * Create a new Book using the details provided.
     * <p>
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set. May contain extra data.
     *                The id will be updated.
     * @param flags   See {@link BookFlags} for flag definitions; {@code 0} for 'normal'.
     *
     * @return the row id of the newly inserted row
     *
     * @throws StorageException The covers directory is not available
     * @throws DaoWriteException     on failure
     */
    @IntRange(from = 1, to = Integer.MAX_VALUE)
    long insert(@NonNull Context context,
                @NonNull Book /* in/out */ book,
                @BookFlags int flags)
            throws StorageException, DaoWriteException;

    /**
     * Update the given {@link Book}.
     * This will update <strong>ONLY</strong> the fields present in the passed in Book.
     * Non-present fields will not be touched. i.e. this is a delta operation.
     *
     * <strong>Transaction:</strong> participate, or runs in new.
     *
     * @param context Current context
     * @param book    A collection with the columns to be set.
     *                May contain extra data which will be ignored.
     * @param flags   See {@link BookFlags} for flag definitions; {@code 0} for 'normal'.
     *
     * @throws StorageException The covers directory is not available
     * @throws DaoWriteException     on failure
     */
    void update(@NonNull Context context,
                @NonNull Book book,
                @BookFlags int flags)
            throws StorageException, DaoWriteException;

    /**
     * Delete the given book (and its covers).
     *
     * @param book to delete
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@NonNull Book book);

    boolean delete(@NonNull BookLight bookLight);

    /**
     * Delete the given book (and its covers).
     *
     * @param id of the book.
     *
     * @return {@code true} if a row was deleted
     */
    boolean delete(@IntRange(from = 1) long id);

    /**
     * Create the link between {@link Book} and {@link Author}.
     * {@link DBDefinitions#TBL_BOOK_AUTHOR}
     * <p>
     * The list is pruned before storage.
     * New authors are added to the Author table, existing ones are NOT updated.
     *
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param list         the list of authors
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertAuthors(@NonNull Context context,
                       @IntRange(from = 1) long bookId,
                       @NonNull Collection<Author> list,
                       boolean lookupLocale,
                       @NonNull Locale bookLocale)
            throws DaoWriteException;

    /**
     * Create the link between {@link Book} and {@link Series}.
     * {@link DBDefinitions#TBL_BOOK_SERIES}
     * <p>
     * The list is pruned before storage.
     * New series are added to the Series table, existing ones are NOT updated.
     *
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param list         the list of Series
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertSeries(@NonNull Context context,
                      @IntRange(from = 1) long bookId,
                      @NonNull Collection<Series> list,
                      boolean lookupLocale,
                      @NonNull Locale bookLocale)
            throws DaoWriteException;

    /**
     * Create the link between {@link Book} and {@link Publisher}.
     * {@link DBDefinitions#TBL_BOOK_PUBLISHER}
     * <p>
     * The list is pruned before storage.
     * New Publishers are added to the Publisher table, existing ones are NOT updated.
     *
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param list         the list of Publishers
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertPublishers(@NonNull Context context,
                          @IntRange(from = 1) long bookId,
                          @NonNull Collection<Publisher> list,
                          boolean lookupLocale,
                          @NonNull Locale bookLocale)
            throws DaoWriteException;

    /**
     * Saves a list of {@link TocEntry} items.
     * <ol>
     *     <li>The list is pruned first.</li>
     *     <li>New authors will be inserted. No updates.</li>
     *     <li>TocEntry's existing in the database will be updated, new ones inserted.</li>
     *     <li>Creates the links between {@link Book} and {@link TocEntry}
     *         in {@link DBDefinitions#TBL_BOOK_TOC_ENTRIES}</li>
     * </ol>
     *
     * <strong>Transaction:</strong> required
     *
     * @param context      Current context
     * @param bookId       of the book
     * @param list         the list of {@link TocEntry}
     * @param lookupLocale set to {@code true} to force a database lookup of the locale.
     *                     This can be (relatively) slow, and hence should be {@code false}
     *                     during for example an import.
     * @param bookLocale   Locale to use if the item has none set,
     *                     or if lookupLocale was {@code false}
     *
     * @throws DaoWriteException    on failure
     * @throws TransactionException a transaction must be started before calling this method
     */
    void insertOrUpdateToc(@NonNull Context context,
                           @IntRange(from = 1) long bookId,
                           @NonNull Collection<TocEntry> list,
                           boolean lookupLocale,
                           @NonNull Locale bookLocale)
            throws DaoWriteException;

    /**
     * Update the 'read' status and the 'read_end' date of the book.
     * This method should only be called from places where only the book id is available.
     * If the full Book is available, use {@link #setRead(Book, boolean)} instead.
     *
     * @param id     id of the book to update
     * @param isRead the status to set
     *
     * @return {@code true} for success.
     */
    boolean setRead(@IntRange(from = 1) long id,
                    boolean isRead);

    /**
     * Update the 'read' status and the 'read_end' date of the book.
     * The book will be updated.
     *
     * @param book   to update
     * @param isRead the status to set
     *
     * @return {@code true} for success.
     */
    boolean setRead(@NonNull Book book,
                    boolean isRead);

    /**
     * Count all books.
     *
     * @return number of books
     */
    long count();

    /**
     * Return a Cursor with the Book for the given {@link Book} id.
     *
     * @param id to retrieve
     *
     * @return A Book Cursor with 0..1 row
     */
    @NonNull
    TypedCursor fetchById(@IntRange(from = 1) long id);

    /**
     * Return an Cursor with all Books for the given external book ID.
     * <strong>Note:</strong> MAY RETURN MORE THAN ONE BOOK
     *
     * @param key        to use
     * @param externalId to retrieve
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    TypedCursor fetchByKey(@NonNull String key,
                           @NonNull String externalId);

    /**
     * Return an Cursor with all Books for the given list of {@link Book} ID's.
     *
     * @param idList List of book ID's to retrieve; should not be empty!
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     *
     * @throws SanityCheck.MissingValueException if the list is empty
     */
    @NonNull
    TypedCursor fetchById(@NonNull List<Long> idList);

    /**
     * Return an Cursor with all Books for the given list of ISBN numbers.
     *
     * @param isbnList list of ISBN numbers; should not be empty!
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     *
     * @throws SanityCheck.MissingValueException if the list is empty
     */
    @NonNull
    TypedCursor fetchByIsbn(@NonNull List<String> isbnList);


    /**
     * Return an Cursor with all Books where the {@link Book} id > the given id.
     * Pass in {@code 0} for all books.
     *
     * @param id the lowest book id to start from.
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    TypedCursor fetchFromIdOnwards(long id);


    /**
     * Can be called before {@link #fetchBooksForExport(LocalDateTime)} to count
     * the number of books before starting the actual export.
     *
     * @param since to select all books added/modified since that date/time (UTC based).
     *              Set to {@code null} for *all* books.
     *
     * @return number of books that would be exported
     */
    int countBooksForExport(@Nullable LocalDateTime since);

    /**
     * Return an Cursor with all Books, or with all updated Books since the given date/time.
     *
     * @param since to select all books added/modified since that date/time (UTC based).
     *              Set to {@code null} for *all* books.
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    TypedCursor fetchBooksForExport(@Nullable LocalDateTime since);

    /**
     * Same as {@link #fetchBooksForExport(LocalDateTime)} but for a specific Calibre library.
     *
     * @param libraryId to use
     * @param since     to select all books added/modified since that date/time (UTC based).
     *                  Set to {@code null} for *all* books.
     *
     * @return A Book Cursor with 0..n rows; ordered by book id
     */
    @NonNull
    TypedCursor fetchBooksForExportToCalibre(long libraryId,
                                             @Nullable LocalDateTime since);

    @NonNull
    TypedCursor fetchBooksForExportToStripInfo(@Nullable LocalDateTime since);

    /**
     * Fetch all book UUID, and return them as a List.
     *
     * @return a list of all book UUID in the database.
     */
    @NonNull
    ArrayList<String> getBookUuidList();

    /**
     * Check that a book with the passed UUID exists and return the id of the book, or zero.
     *
     * @param uuid UUID of the book
     *
     * @return id of the book, or 0 'new' if not found
     */
    @IntRange(from = 0)
    long getBookIdByUuid(@NonNull String uuid);

    /**
     * Return the book title+isbn based on the id.
     *
     * @param id of the book
     *
     * @return the title+isbn as a Pair, never {@code null} but the first/second CAN be null
     */
    @NonNull
    Pair<String, String> getBookTitleAndIsbnById(@IntRange(from = 1) long id);

    /**
     * Get a list of book id/title's (most often just the one) for the given ISBN.
     *
     * @param isbn to search for; can be generic/non-valid
     *
     * @return list with book id/title
     */
    @NonNull
    ArrayList<Pair<Long, String>> getBookIdAndTitleByIsbn(@NonNull ISBN isbn);

    /**
     * Check that a book with the passed id exists.
     *
     * @param id of the book
     *
     * @return {@code true} if exists
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean bookExistsById(@IntRange(from = 1) long id);

    /**
     * Check that a book with the passed isbn exists.
     *
     * @param isbnStr of the book
     *
     * @return {@code true} if exists
     */
    boolean bookExistsByIsbn(@NonNull String isbnStr);

    /**
     * Get a unique list of all currencies for the specified domain (from the Books table).
     *
     * @param domainName for which to collect the used currency codes
     *                   If a non-currency domain name is passed, you'll get garbage but no error.
     *
     * @return The list; values are always in uppercase.
     */
    @NonNull
    ArrayList<String> getCurrencyCodes(@NonNull String domainName);


    @Nullable
    LocalDateTime getLastUpdateDate(@IntRange(from = 1) long id);

    @IntDef(flag = true, value = {BOOK_FLAG_IS_BATCH_OPERATION,
                                  BOOK_FLAG_USE_ID_IF_PRESENT,
                                  BOOK_FLAG_USE_UPDATE_DATE_IF_PRESENT})
    @Retention(RetentionPolicy.SOURCE)
    @interface BookFlags {

    }
}
