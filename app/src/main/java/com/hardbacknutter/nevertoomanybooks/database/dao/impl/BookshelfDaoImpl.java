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
package com.hardbacknutter.nevertoomanybooks.database.dao.impl;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.FilterFactory;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.BookshelfDao;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.EntityMerger;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKLIST_STYLES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF_FILTERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LIST_NODE_STATE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.BOOKSHELF_NAME;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.FK_STYLE;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.PK_ID;

public class BookshelfDaoImpl
        extends BaseDaoImpl
        implements BookshelfDao {

    /** Log tag. */
    private static final String TAG = "BookshelfDaoImpl";

    private static final String INSERT =
            INSERT_INTO_ + TBL_BOOKSHELF.getName()
            + '(' + DBKey.BOOKSHELF_NAME
            + ',' + DBKey.FK_STYLE
            + ',' + DBKey.BOOKSHELF_BL_TOP_POS
            + ',' + DBKey.BOOKSHELF_BL_TOP_OFFSET
            + ") VALUES (?,?,?,?)";

    private static final String BOOK_LIST_NODE_STATE_BY_BOOKSHELF =
            DELETE_FROM_ + TBL_BOOK_LIST_NODE_STATE.getName()
            + _WHERE_ + DBKey.FK_BOOKSHELF + "=?";

    /** Delete a {@link Bookshelf}. */
    private static final String DELETE_BY_ID =
            DELETE_FROM_ + TBL_BOOKSHELF.getName()
            + _WHERE_ + DBKey.PK_ID + "=?";

    /**
     * Get the id of a {@link Bookshelf} by name.
     * The lookup is by EQUALITY and CASE-SENSITIVE.
     */
    private static final String FIND_ID =
            SELECT_ + DBKey.PK_ID + _FROM_ + TBL_BOOKSHELF.getName()
            + _WHERE_ + DBKey.BOOKSHELF_NAME + "=?" + _COLLATION;

    /** All {@link Bookshelf}, all columns; linked with the styles table. */
    private static final String SELECT_ALL =
            SELECT_ + TBL_BOOKSHELF.dotAs(DBKey.PK_ID,
                                          DBKey.BOOKSHELF_NAME,
                                          DBKey.BOOKSHELF_BL_TOP_POS,
                                          DBKey.BOOKSHELF_BL_TOP_OFFSET,
                                          DBKey.FK_STYLE)
            + ',' + TBL_BOOKLIST_STYLES.dotAs(DBKey.STYLE_UUID)
            + _FROM_ + TBL_BOOKSHELF.startJoin(TBL_BOOKLIST_STYLES);

    /** User defined {@link Bookshelf}, all columns; linked with the styles table. */
    private static final String SELECT_ALL_USER_SHELVES =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.PK_ID) + ">0"
            + _ORDER_BY_ + DBKey.BOOKSHELF_NAME + _COLLATION;

    /** Get a {@link Bookshelf} by the Bookshelf id; linked with the styles table. */
    private static final String SELECT_BY_ID =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.PK_ID) + "=?";

    /** Get a {@link Bookshelf} by its name; linked with the styles table. */
    private static final String FIND =
            SELECT_ALL + _WHERE_ + TBL_BOOKSHELF.dot(DBKey.BOOKSHELF_NAME) + "=?"
            + _COLLATION;

    /** All Bookshelves for a Book; ordered by name. */
    private static final String BOOKSHELVES_BY_BOOK_ID =
            SELECT_DISTINCT_ + TBL_BOOKSHELF.dotAs(DBKey.PK_ID,
                                                   DBKey.BOOKSHELF_NAME,
                                                   DBKey.BOOKSHELF_BL_TOP_POS,
                                                   DBKey.BOOKSHELF_BL_TOP_OFFSET,
                                                   DBKey.FK_STYLE)
            + ',' + TBL_BOOKLIST_STYLES.dotAs(DBKey.STYLE_UUID)

            + _FROM_ + TBL_BOOK_BOOKSHELF.startJoin(TBL_BOOKSHELF, TBL_BOOKLIST_STYLES)
            + _WHERE_ + TBL_BOOK_BOOKSHELF.dot(DBKey.FK_BOOK) + "=?"
            + _ORDER_BY_ + TBL_BOOKSHELF.dot(DBKey.BOOKSHELF_NAME) + _COLLATION;


    private static final String SELECT_FILTERS =
            SELECT_ + DBKey.FILTER_DBKEY + ',' + DBKey.FILTER_VALUE
            + _FROM_ + TBL_BOOKSHELF_FILTERS.getName()
            + _WHERE_ + DBKey.FK_BOOKSHELF + "=?";

    private static final String DELETE_FILTERS =
            DELETE_FROM_ + TBL_BOOKSHELF_FILTERS.getName()
            + _WHERE_ + DBKey.FK_BOOKSHELF + "=?";

    private static final String INSERT_FILTER =
            INSERT_INTO_ + TBL_BOOKSHELF_FILTERS.getName()
            + '(' + DBKey.FK_BOOKSHELF
            + ',' + DBKey.FILTER_DBKEY
            + ',' + DBKey.FILTER_VALUE
            + ") VALUES (?,?,?)";

    /**
     * Constructor.
     */
    public BookshelfDaoImpl() {
        super(TAG);
    }

    /**
     * Run at installation time to add the 'all' and default shelves to the database.
     *
     * @param context Current context
     * @param db      Database Access
     */
    public static void onPostCreate(@NonNull final Context context,
                                    @NonNull final SQLiteDatabase db) {
        // inserts a 'All Books' bookshelf with _id==-1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + PK_ID
                   + ',' + BOOKSHELF_NAME
                   + ',' + FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.ALL_BOOKS
                   + ",'" + context.getString(R.string.bookshelf_all_books)
                   + "'," + BuiltinStyle.DEFAULT_ID
                   + ')');

        // inserts a 'Default' bookshelf with _id==1, see {@link Bookshelf}.
        db.execSQL("INSERT INTO " + TBL_BOOKSHELF
                   + '(' + PK_ID
                   + ',' + BOOKSHELF_NAME
                   + ',' + FK_STYLE
                   + ") VALUES ("
                   + Bookshelf.DEFAULT
                   + ",'" + context.getString(R.string.bookshelf_my_books)
                   + "'," + BuiltinStyle.DEFAULT_ID
                   + ')');
    }

    @Override
    @Nullable
    public Bookshelf getById(final long id) {
        try (Cursor cursor = mDb.rawQuery(SELECT_BY_ID, new String[]{String.valueOf(id)})) {
            if (cursor.moveToFirst()) {
                return new Bookshelf(id, new CursorRow(cursor));
            } else {
                return null;
            }
        }
    }

    @Override
    @Nullable
    public Bookshelf findByName(@NonNull final String name) {

        try (Cursor cursor = mDb.rawQuery(FIND, new String[]{name})) {
            if (cursor.moveToFirst()) {
                final DataHolder rowData = new CursorRow(cursor);
                return new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData);
            } else {
                return null;
            }
        }
    }

    @Override
    public long find(@NonNull final Bookshelf bookshelf) {

        try (SynchronizedStatement stmt = mDb.compileStatement(FIND_ID)) {
            stmt.bindString(1, bookshelf.getName());
            return stmt.simpleQueryForLongOrZero();
        }
    }

    @Override
    @NonNull
    public List<Bookshelf> getAll() {
        final List<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = fetchAllUserShelves()) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData));
            }
        }
        return list;
    }

    @Override
    @NonNull
    public Cursor fetchAllUserShelves() {
        return mDb.rawQuery(SELECT_ALL_USER_SHELVES, null);
    }

    @NonNull
    @Override
    public List<PFilter<?>> getFilters(final long bookshelfId) {
        final List<PFilter<?>> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(SELECT_FILTERS,
                                          new String[]{String.valueOf(bookshelfId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                final String dbKey = rowData.getString(DBKey.FILTER_DBKEY);
                final String value = rowData.getString(DBKey.FILTER_VALUE, null);
                if (value != null) {
                    final PFilter<?> filter = FilterFactory.createFilter(dbKey);
                    if (filter != null) {
                        filter.setPersistedValue(value);
                        list.add(filter);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Store the <strong>active filter</strong>.
     *
     * @param context     Current context
     * @param bookshelfId the Bookshelf id; passed separately to allow clean inserts
     * @param bookshelf   to store
     */
    public void storeFilters(final Context context,
                             final long bookshelfId,
                             @NonNull final Bookshelf bookshelf) {

        // prune the filters so we only keep the active ones
        final List<PFilter<?>> list = bookshelf.pruneFilters(context);

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }
            try (SynchronizedStatement stmt = mDb.compileStatement(DELETE_FILTERS)) {
                stmt.bindLong(1, bookshelfId);
                stmt.executeUpdateDelete();
            }

            if (list.isEmpty()) {
                return;
            }

            try (SynchronizedStatement stmt = mDb.compileStatement(INSERT_FILTER)) {
                list.forEach(filter -> {
                    stmt.bindLong(1, bookshelfId);
                    stmt.bindString(2, filter.getDBKey());
                    stmt.bindString(3, filter.getPersistedValue());
                    stmt.executeInsert();
                });
            }

            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }
    }

    @Override
    public boolean pruneList(@NonNull final Collection<Bookshelf> list) {
        if (list.isEmpty()) {
            return false;
        }

        final EntityMerger<Bookshelf> entityMerger = new EntityMerger<>(list);
        while (entityMerger.hasNext()) {
            final Bookshelf current = entityMerger.next();
            fixId(current);
            entityMerger.merge(current);
        }

        return entityMerger.isListModified();
    }

    @Override
    public long fixId(@NonNull final Bookshelf bookshelf) {
        final long id = find(bookshelf);
        bookshelf.setId(id);
        return id;
    }

    @Override
    public long insert(@NonNull final Context context,
                       @NonNull final Bookshelf bookshelf) {

        // validate the style first
        final long styleId = bookshelf.getStyle(context).getId();

        final long iId;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }
            try (SynchronizedStatement stmt = mDb.compileStatement(INSERT)) {
                stmt.bindString(1, bookshelf.getName());
                stmt.bindLong(2, styleId);
                stmt.bindLong(3, bookshelf.getFirstVisibleItemPosition());
                stmt.bindLong(4, bookshelf.getFirstVisibleItemViewOffset());
                iId = stmt.executeInsert();
                if (iId > 0) {
                    storeFilters(context, iId, bookshelf);
                }
            }
            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }

        if (iId > 0) {
            bookshelf.setId(iId);
        }
        return iId;
    }

    @Override
    public boolean update(@NonNull final Context context,
                          @NonNull final Bookshelf bookshelf) {

        // validate the style first
        final long styleId = bookshelf.getStyle(context).getId();

        final int rowsAffected;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            final ContentValues cv = new ContentValues();
            cv.put(DBKey.BOOKSHELF_NAME, bookshelf.getName());
            cv.put(DBKey.BOOKSHELF_BL_TOP_POS, bookshelf.getFirstVisibleItemPosition());
            cv.put(DBKey.BOOKSHELF_BL_TOP_OFFSET, bookshelf.getFirstVisibleItemViewOffset());

            cv.put(DBKey.FK_STYLE, styleId);

            rowsAffected = mDb.update(TBL_BOOKSHELF.getName(), cv, DBKey.PK_ID + "=?",
                                      new String[]{String.valueOf(bookshelf.getId())});

            storeFilters(context, bookshelf.getId(), bookshelf);

            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }
        return 0 < rowsAffected;
    }

    @Override
    @SuppressWarnings("UnusedReturnValue")
    public boolean delete(@NonNull final Bookshelf bookshelf) {

        final int rowsAffected;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            purgeNodeStates(bookshelf.getId());

            try (SynchronizedStatement stmt = mDb.compileStatement(DELETE_BY_ID)) {
                stmt.bindLong(1, bookshelf.getId());
                rowsAffected = stmt.executeUpdateDelete();
            }
            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }

        if (rowsAffected > 0) {
            bookshelf.setId(0);
        }
        return rowsAffected == 1;
    }

    @Override
    @SuppressWarnings("UnusedReturnValue")
    public int merge(@NonNull final Bookshelf source,
                     final long destId) {

        final ContentValues cv = new ContentValues();
        cv.put(DBKey.FK_BOOKSHELF, destId);

        final int rowsAffected;

        Synchronizer.SyncLock txLock = null;
        try {
            if (!mDb.inTransaction()) {
                txLock = mDb.beginTransaction(true);
            }

            // we don't hold 'position' for shelves... so just do a mass update
            rowsAffected = mDb.update(TBL_BOOK_BOOKSHELF.getName(), cv,
                                      DBKey.FK_BOOKSHELF + "=?",
                                      new String[]{String.valueOf(source.getId())});

            // delete the obsolete source.
            delete(source);

            if (txLock != null) {
                mDb.setTransactionSuccessful();
            }
        } finally {
            if (txLock != null) {
                mDb.endTransaction(txLock);
            }
        }

        return rowsAffected;
    }

    @Override
    public void purgeNodeStates(final long bookshelfId) {
        try (SynchronizedStatement stmt = mDb
                .compileStatement(BOOK_LIST_NODE_STATE_BY_BOOKSHELF)) {
            stmt.bindLong(1, bookshelfId);
            stmt.executeUpdateDelete();
        }
    }

    @Override
    @NonNull
    public ArrayList<Bookshelf> getBookshelvesByBookId(@IntRange(from = 1) final long bookId) {
        final ArrayList<Bookshelf> list = new ArrayList<>();
        try (Cursor cursor = mDb.rawQuery(BOOKSHELVES_BY_BOOK_ID,
                                          new String[]{String.valueOf(bookId)})) {
            final DataHolder rowData = new CursorRow(cursor);
            while (cursor.moveToNext()) {
                list.add(new Bookshelf(rowData.getLong(DBKey.PK_ID), rowData));
            }
            return list;
        }
    }
}
