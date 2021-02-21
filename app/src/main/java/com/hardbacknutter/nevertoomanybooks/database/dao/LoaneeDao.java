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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;

public final class LoaneeDao
        extends BaseDao {

    /** Log tag. */
    private static final String TAG = "LoaneeDao";

    /** Get the name of the loanee of a {@link Book} by the Book id. */
    private static final String SELECT_BY_BOOK_ID =
            SELECT_ + KEY_LOANEE + _FROM_ + TBL_BOOK_LOANEE.getName()
            + _WHERE_ + KEY_FK_BOOK + "=?";

    /** name only. */
    private static final String SELECT_ALL =
            SELECT_DISTINCT_ + KEY_LOANEE
            + _FROM_ + TBL_BOOK_LOANEE.getName()
            + _WHERE_ + KEY_LOANEE + "<> ''"
            + _ORDER_BY_ + KEY_LOANEE + _COLLATION;

    /** Lend a book. */
    private static final String INSERT =
            INSERT_INTO_ + TBL_BOOK_LOANEE.getName()
            + '(' + KEY_FK_BOOK
            + ',' + KEY_LOANEE
            + ") VALUES(?,?)";

    /** Delete the loan of a {@link Book}; i.e. 'return the book'. */
    private static final String DELETE_BY_BOOK_ID =
            DELETE_FROM_ + TBL_BOOK_LOANEE.getName() + _WHERE_ + KEY_FK_BOOK + "=?";

    /** Singleton. */
    private static LoaneeDao sInstance;

    /**
     * Constructor.
     *
     * @param context Current context
     * @param logTag  of this DAO for logging.
     */
    private LoaneeDao(@NonNull final Context context,
                      @NonNull final String logTag) {
        super(context, logTag);
    }

    public static LoaneeDao getInstance() {
        if (sInstance == null) {
            sInstance = new LoaneeDao(App.getDatabaseContext(), TAG);
        }

        return sInstance;
    }

    /**
     * Lend out a book / return a book.
     * <p>
     * This method should only be called from places where only the book id is available.
     * If the full Book is available, use {@link #setLoanee(Book, String)} instead.
     *
     * @param bookId book to lend
     * @param loanee person to lend to; set to {@code null} or {@code ""} to delete the loan
     *
     * @return {@code true} for success.
     */
    public boolean setLoanee(@IntRange(from = 1) final long bookId,
                             @Nullable final String loanee) {
        final boolean success = setLoaneeInternal(bookId, loanee);
        if (success) {
            touchBook(bookId);
        }
        return success;
    }

    /**
     * Lend out a book / return a book.
     *
     * @param book   to lend
     * @param loanee person to lend to; set to {@code null} or {@code ""} to delete the loan
     *
     * @return {@code true} for success.
     */
    public boolean setLoanee(@NonNull final Book book,
                             @Nullable final String loanee) {

        final boolean success = setLoaneeInternal(book.getId(), loanee);
        if (success) {
            touchBook(book);
        }
        return success;
    }

    /**
     * Lend out a book / return a book.
     * The book's {@link DBDefinitions#KEY_UTC_LAST_UPDATED} <strong>will NOT</strong> be updated.
     *
     * @param bookId book to lend
     * @param loanee person to lend to; set to {@code null} or {@code ""} to delete the loan
     *
     * @return {@code true} for success.
     */
    private boolean setLoaneeInternal(@IntRange(from = 1) final long bookId,
                                      @Nullable final String loanee) {

        boolean success = false;

        if (loanee == null || loanee.isEmpty()) {
            try (SynchronizedStatement stmt = mSyncedDb
                    .compileStatement(DELETE_BY_BOOK_ID)) {
                stmt.bindLong(1, bookId);
                success = stmt.executeUpdateDelete() == 1;
            }
        } else {

            final String current = getLoaneeByBookId(bookId);
            if (current == null || current.isEmpty()) {
                try (SynchronizedStatement stmt = mSyncedDb
                        .compileStatement(INSERT)) {
                    stmt.bindLong(1, bookId);
                    stmt.bindString(2, loanee);
                    success = stmt.executeInsert() > 0;
                }

            } else if (!loanee.equals(current)) {
                final ContentValues cv = new ContentValues();
                cv.put(KEY_LOANEE, loanee);
                success = 0 < mSyncedDb.update(TBL_BOOK_LOANEE.getName(), cv,
                                               KEY_FK_BOOK + "=?",
                                               new String[]{String.valueOf(bookId)});
            }
        }
        return success;
    }

    /**
     * Get the name of the loanee for a given book, if any.
     *
     * @param bookId book to search for
     *
     * @return Who the book is lend to, or {@code null} when not lend out
     */
    @Nullable
    public String getLoaneeByBookId(@IntRange(from = 1) final long bookId) {

        try (SynchronizedStatement stmt = mSyncedDb.compileStatement(SELECT_BY_BOOK_ID)) {
            stmt.bindLong(1, bookId);
            return stmt.simpleQueryForStringOrNull();
        }
    }

    /**
     * Returns a unique list of all loanee in the database.
     *
     * @return The list
     */
    @NonNull
    public ArrayList<String> getList() {
        try (Cursor cursor = mSyncedDb.rawQuery(SELECT_ALL, null)) {
            return getFirstColumnAsList(cursor);
        }
    }
}
