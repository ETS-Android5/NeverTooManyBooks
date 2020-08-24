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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import java.io.Closeable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.Filter;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.ListOfValuesFilter;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBHelper;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.Synchronizer.SyncLock;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.database.definitions.VirtualDomain;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_GROUP;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_KEY;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BL_NODE_LEVEL;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_AUTHOR_TYPE_BITMASK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_PUBLISHER_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_BOOK_SERIES_POSITION;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FK_BOOK;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_FTS_BOOK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.KEY_PK_ID;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_AUTHORS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_AUTHOR;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_BOOKSHELF;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_LOANEE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_PUBLISHER;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_FTS_BOOKS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_PUBLISHERS;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TMP_TBL_BOOK_LIST;

/**
 * Build and populate temporary tables with details of "flattened" books.
 * The generated list is used to display books in a list control and perform operation like
 * 'expand/collapse' on nodes in the list.
 * <p>
 * The BooklistBuilder "owns" the temporary database tables.
 * They get deleted when {@link #close()} is called.
 * A BooklistBuilder has a 1:1 relation to {@link BooklistCursor} objects.
 * The BooklistCursor holds a reference to the BooklistBuilder.
 */
public class BooklistBuilder
        implements AutoCloseable {

    /** id values for state preservation property. See {@link ListRebuildMode}. */
    public static final int PREF_REBUILD_SAVED_STATE = 0;
    public static final int PREF_REBUILD_ALWAYS_EXPANDED = 1;
    @SuppressWarnings("WeakerAccess")
    public static final int PREF_REBUILD_ALWAYS_COLLAPSED = 2;
    @SuppressWarnings("WeakerAccess")
    public static final int PREF_REBUILD_PREFERRED_STATE = 3;

    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _AND_ = " AND ";
    private static final String _ORDER_BY_ = " ORDER BY ";

    /** Log tag. */
    private static final String TAG = "BooklistBuilder";
    /**
     * Counter for BooklistBuilder ID's. Only increment.
     * Used to create unique table names etc... see {@link #mInstanceId}.
     */
    @NonNull
    private static final AtomicInteger ID_COUNTER = new AtomicInteger();
    /** DEBUG Instance counter. Increment and decrements to check leaks. */
    @NonNull
    private static final AtomicInteger DEBUG_INSTANCE_COUNTER = new AtomicInteger();
    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    // not in use for now
    // List of columns for the group-by clause, including COLLATE clauses. Set by build() method.
    //private String mGroupColumnList;
    /** Database Access. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SynchronizedDb mSyncedDb;

    /** Internal ID. Used to create unique names for the temporary tables. */
    private final int mInstanceId;
    /** Collection of 'extra' book level domains requested by caller. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final Map<String, VirtualDomain> mBookDomains = new HashMap<>();

    /** the list of Filters. */
    private final Collection<Filter<?>> mFilters = new ArrayList<>();
    /** Style to use while building the list. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final BooklistStyle mStyle;
    /** Show only books on this bookshelf. */
    @NonNull
    private final Bookshelf mBookshelf;
    @SuppressWarnings("FieldNotUsedInToString")
    @ListRebuildMode
    private int mRebuildState;

    /**
     * The temp table representing the booklist for the current bookshelf/style.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mListTable;

    /**
     * The temp table holding the state (expand,visibility,...) for all rows in the list-table.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private RowStateDAO mRowStateDAO;
    /** Table used by the triggers to store the snapshot of the most recent/current row headings. */
    @SuppressWarnings("FieldNotUsedInToString")
    private TableDefinition mTriggerHelperTable;
    /** DEBUG: double check on mCloseWasCalled to control {@link #DEBUG_INSTANCE_COUNTER}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private boolean mDebugReferenceDecremented;
    /** DEBUG: Indicates close() has been called. See {@link Closeable#close()}. */
    private boolean mCloseWasCalled;


    /** Total number of books in current list. e.g. a book can be listed under 2 authors. */
    private int mTotalBooks = -1;
    /** Total number of unique books in current list. */
    private int mDistinctBooks = -1;
    /** The current list cursor. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private BooklistCursor mCursor;

    /**
     * Constructor.
     *
     * @param style        Booklist style to use;
     *                     this is the resolved style as used by the passed bookshelf
     * @param bookshelf    the current bookshelf
     * @param rebuildState booklist state to use in next rebuild.
     */
    public BooklistBuilder(@NonNull final BooklistStyle style,
                           @NonNull final Bookshelf bookshelf,
                           @BooklistBuilder.ListRebuildMode final int rebuildState) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "ENTER|BooklistBuilder"
                       + "|style=" + style.getUuid()
                       + "|instances: " + DEBUG_INSTANCE_COUNTER.incrementAndGet(),
                  new Throwable());
        }
        // Allocate ID
        mInstanceId = ID_COUNTER.incrementAndGet();

        mRebuildState = rebuildState;
        mStyle = style;
        // Filter on the specified Bookshelf.
        mBookshelf = bookshelf;
        // The filter will only be added if the current style does not contain the Bookshelf group.
        if (!bookshelf.isAllBooks() && !mStyle.containsGroup(BooklistGroup.BOOKSHELF)) {
            mFilters.add(c -> '(' + TBL_BOOKSHELF.dot(KEY_PK_ID) + '=' + bookshelf.getId() + ')');
        }

        mSyncedDb = DAO.getSyncDb();
    }

    /**
     * Allows to override the build state set in the constructor.
     *
     * @param rebuildState booklist state to use in next rebuild.
     */
    public void setRebuildState(@ListRebuildMode final int rebuildState) {
        mRebuildState = rebuildState;
    }

    /**
     * Create a flattened table (a snapshot!) of ordered book ID's based on the underlying list.
     * Any old data is removed before the new table is created.
     *
     * @return the name of the created table.
     */
    @NonNull
    public String createFlattenedBooklist() {
        // reminder: do not drop this table. It needs to survive beyond the booklist screen.
        return FlattenedBooklist.createTable(mSyncedDb, mInstanceId, mRowStateDAO, mListTable);
    }

    /**
     * Add a domain to the resulting flattened list based on the details provided.
     *
     * @param vDomain Domain to add
     */
    public void addDomain(@NonNull final VirtualDomain vDomain) {
        if (!mBookDomains.containsKey(vDomain.getName())) {
            mBookDomains.put(vDomain.getName(), vDomain);

        } else {
            // adding a duplicate here is a bug.
            throw new IllegalArgumentException("Duplicate domain=`" + vDomain.getName() + '`');
        }
    }

    /**
     * Adds the FTS book table for a keyword match.
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param author        Author related keywords to find
     * @param title         Title related keywords to find
     * @param seriesTitle   Series title related keywords to find
     * @param publisherName Publisher name related keywords to find
     * @param keywords      Keywords to find anywhere in book; this includes all above fields
     */
    public void setFilter(@Nullable final String author,
                          @Nullable final String title,
                          @Nullable final String seriesTitle,
                          @Nullable final String publisherName,
                          @Nullable final String keywords) {

        final String query = DAO.SqlFTS.createMatchString(author, title, seriesTitle,
                                                          publisherName, keywords);
        if (!query.isEmpty()) {
            mFilters.add(context ->
                                 '(' + TBL_BOOKS.dot(KEY_PK_ID) + " IN ("
                                 // fetch the ID's only
                                 + SELECT_ + KEY_FTS_BOOK_ID
                                 + _FROM_ + TBL_FTS_BOOKS.getName()
                                 + _WHERE_ + TBL_FTS_BOOKS.getName() + " MATCH '" + query + "')"
                                 + ')');
        }
    }

    /**
     * Set the filter for only books lend to the named person (exact name).
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param filter the exact name of the person we lend books to.
     */
    public void setFilterOnLoanee(@Nullable final String filter) {
        if (filter != null && !filter.trim().isEmpty()) {
            mFilters.add(context ->
                                 "EXISTS(SELECT NULL FROM " + TBL_BOOK_LOANEE.ref()
                                 + _WHERE_ + TBL_BOOK_LOANEE.dot(KEY_LOANEE)
                                 + "=`" + DAO.encodeString(filter) + '`'
                                 + _AND_ + TBL_BOOK_LOANEE.fkMatch(TBL_BOOKS)
                                 + ')');
        }
    }

    /**
     * The where clause will add a "AND books._id IN (list)".
     * Be careful when combining with other criteria as you might get less than expected
     * <p>
     * An empty filter will silently be rejected.
     *
     * @param filter a list of book ID's.
     */
    public void setFilterOnBookIdList(@Nullable final List<Long> filter) {
        if (filter != null && !filter.isEmpty()) {
            mFilters.add(new ListOfValuesFilter<>(TBL_BOOKS, KEY_PK_ID, filter));
        }
    }

    /**
     * Clear and build the temporary list of books.
     * Criteria must be set before calling this method with one or more of the setCriteria calls.
     *
     * @param context Current context
     */
    public void build(@NonNull final Context context) {
        // Setup the tables (but don't create them yet)
        mListTable = new TableDefinition(TMP_TBL_BOOK_LIST);
        mListTable.setName(mListTable.getName() + mInstanceId);
        mListTable.addDomain(DOM_PK_ID);
        // {@link BooklistGroup#GroupKey}. The actual value is set on a by-group/book basis.
        mListTable.addDomain(DOM_BL_NODE_KEY);

        // Start building the table domains
        final BuildHelper helper = new BuildHelper(context, mListTable, mStyle, mBookshelf);

        // add the basic book domains

        // Always sort by level first; no expression, as this does not represent a value.
        helper.addDomain(new VirtualDomain(
                DOM_BL_NODE_LEVEL, null, VirtualDomain.SORT_ASC), false);
        // The level expression; for a book this is always 1 below the #groups obviously
        helper.addDomain(new VirtualDomain(
                DOM_BL_NODE_LEVEL, String.valueOf(mStyle.getGroupCount() + 1)), false);
        // The group is the book group (duh)
        helper.addDomain(new VirtualDomain(
                DOM_BL_NODE_GROUP, String.valueOf(BooklistGroup.BOOK)), false);

        // The book id itself
        helper.addDomain(new VirtualDomain(DOM_FK_BOOK, TBL_BOOKS.dot(KEY_PK_ID)), false);

        // add style-specified groups
        for (BooklistGroup group : mStyle.getGroups()) {
            helper.addGroup(group);
        }

        // add caller-specified book data
        for (VirtualDomain bookDomain : mBookDomains.values()) {
            helper.addDomain(bookDomain, false);
        }

        // and finally, add all filters to be used for the WHERE clause
        helper.addFilters(mFilters);

        // Construct the initial insert statement components.
        final String initialInsertSql = helper.build(context);

        // We are good to go.
        long t0 = 0;
        long t1_insert = 0;

        long initialInsertCount = 0;

        final SyncLock txLock = mSyncedDb.beginTransaction(true);
        try {
            //IMPORTANT: withConstraints MUST BE false
            mListTable.recreate(mSyncedDb, false);

            t0 = System.nanoTime();

            // get the triggers in place, ready to act on our upcoming initial insert.
            createTriggers(helper.getSortedDomains());

            // Build the lowest level (i.e. books) using our initial insert statement
            // The triggers will do the other levels.
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(initialInsertSql)) {
                initialInsertCount = stmt.executeUpdateDelete();
            }

            t1_insert = System.nanoTime();

            if (!DBHelper.isCollationCaseSensitive()) {
                // can't do this, IndexDefinition class does not support DESC columns for now.
                // mListTable.addIndex("SDI", false, helper.getSortedDomains());
                mSyncedDb.execSQL(
                        "CREATE INDEX " + mListTable.getName() + "_SDI ON " + mListTable.getName()
                        + "(" + helper.getSortedDomainsIndexColumns() + ")");
            }

            // The list table is now fully populated.
            mSyncedDb.analyze(mListTable);

            // build the row-state table for expansion/visibility tracking
            // clean up previously used table (if any)
            if (mRowStateDAO != null) {
                mRowStateDAO.close();
            }
            mRowStateDAO = new RowStateDAO(mSyncedDb, mInstanceId, mStyle, mBookshelf);
            mRowStateDAO.build(context, mListTable, mRebuildState);

            mSyncedDb.setTransactionSuccessful();

        } finally {
            mSyncedDb.endTransaction(txLock);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                Log.d(TAG, "build|insert(" + initialInsertCount + ") : "
                           + ((t1_insert - t0) / NANO_TO_MILLIS) + " ms");
            }
        }

        // Create the initial cursor, and run a pre-count.
        // When calling setAdapter() with our cursor, it will do a count() and potentially
        // block the UI thread while it pages through the entire cursor.
        // Doing it here makes subsequent calls faster.
        //
        newListCursor().getCount();
    }

    /**
     * Build a collection of triggers on the list table designed to fill in the summary/header
     * records as the data records are added in sorted order.
     *
     * @param sortedDomains the domains we need to sort by, in order.
     */
    private void createTriggers(@NonNull final Collection<VirtualDomain> sortedDomains) {

        mTriggerHelperTable = new TableDefinition(mListTable + "_th")
                .setAlias("tht");
        // Allow debug mode to use standard tables so we can export and inspect the content.
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOOK_LIST_USES_STANDARD_TABLES) {
            mTriggerHelperTable.setType(TableDefinition.TableType.Standard);
        } else {
            mTriggerHelperTable.setType(TableDefinition.TableType.Temporary);
        }

        // SQL statement to update the 'current' table
        final StringBuilder valuesColumns = new StringBuilder();
        // List of domain names for sorting
        final Collection<String> sortedDomainNames = new HashSet<>();

        // Build the 'current' header table definition and the sort column list
        sortedDomains.stream()
                     // don't add duplicate domains
                     .filter(sdi -> !sortedDomainNames.contains(sdi.getName()))
                     .forEach(sdi -> {
                         sortedDomainNames.add(sdi.getName());
                         if (valuesColumns.length() > 0) {
                             valuesColumns.append(",");
                         }
                         valuesColumns.append("NEW.").append(sdi.getName());
                         mTriggerHelperTable.addDomain(sdi.getDomain());
                     });

        /*
         * Create a temp table to store the most recent header details from the last row.
         * We use this in determining what needs to be inserted as header records for
         * any given row.
         *
         * This is just a simple technique to provide persistent context to the trigger.
         */
        //IMPORTANT: withConstraints MUST BE false
        mTriggerHelperTable.recreate(mSyncedDb, false);

        /*
         * For each grouping, starting with the lowest, build a trigger to update the next
         * level up as necessary. i.o.w. each level has a dedicated trigger.
         */
        for (int index = mStyle.getGroupCount() - 1; index >= 0; index--) {
            // Get the level number for this group
            final int level = index + 1;

            // Get the group
            final BooklistGroup group = mStyle.getGroupByLevel(level);

            // Create the INSERT columns clause for the next level up
            final StringBuilder listColumns = new StringBuilder()
                    .append(KEY_BL_NODE_LEVEL)
                    .append(',').append(KEY_BL_NODE_GROUP)
                    .append(',').append(KEY_BL_NODE_KEY);

            // Create the VALUES clause for the next level up
            final StringBuilder listValues = new StringBuilder()
                    .append(level)
                    .append(',').append(group.getId())
                    .append(",NEW.").append(KEY_BL_NODE_KEY);

            // Create the where-clause to detect if the next level up is already defined
            // (by checking the 'current' record/table)
            final StringBuilder whereClause = new StringBuilder();

            //noinspection ConstantConditions
            for (Domain groupDomain : group.getAccumulatedDomains()) {
                listColumns.append(',').append(groupDomain.getName());
                listValues.append(", NEW.").append(groupDomain.getName());

                // Only add to the where-clause if the group is part of the SORT list
                if (sortedDomainNames.contains(groupDomain.getName())) {
                    if (whereClause.length() > 0) {
                        whereClause.append(_AND_);
                    }
                    whereClause.append("COALESCE(")
                               .append(mTriggerHelperTable.dot(groupDomain.getName()))
                               .append(",'')=COALESCE(NEW.")
                               .append(groupDomain.getName())
                               .append(",'')")
                               .append(DAO._COLLATION);
                }
            }

            // (re)Create the trigger
            final String levelTgName = mListTable.getName() + "_TG_LEVEL_" + level;
            mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + levelTgName);
            final String levelTgSql =
                    "\nCREATE TEMPORARY TRIGGER " + levelTgName
                    + " BEFORE INSERT ON " + mListTable.getName() + " FOR EACH ROW"
                    + "\n WHEN NEW." + KEY_BL_NODE_LEVEL + '=' + (level + 1)
                    + " AND NOT EXISTS("
                    + /* */ "SELECT 1 FROM " + mTriggerHelperTable.ref() + _WHERE_ + whereClause
                    + /* */ ')'
                    + "\n BEGIN"
                    + "\n  INSERT INTO " + mListTable.getName() + " (" + listColumns + ")"
                    + /*             */ " VALUES(" + listValues + ");"
                    + "\n END";

            mSyncedDb.execSQL(levelTgSql);
        }

        // Create a trigger to maintain the 'current' value
        final String currentValueTgName = mListTable.getName() + "_TG_CURRENT";
        mSyncedDb.execSQL("DROP TRIGGER IF EXISTS " + currentValueTgName);
        // This is a single row only, so delete the previous value, and insert the current one
        final String currentValueTgSql =
                "\nCREATE TEMPORARY TRIGGER " + currentValueTgName
                + " AFTER INSERT ON " + mListTable.getName() + " FOR EACH ROW"
                + "\n WHEN NEW." + KEY_BL_NODE_LEVEL + '=' + mStyle.getGroupCount()
                + "\n BEGIN"
                + "\n  DELETE FROM " + mTriggerHelperTable.getName() + ';'
                + "\n  INSERT INTO " + mTriggerHelperTable.getName()
                + /*              */ " VALUES (" + valuesColumns + ");"
                + "\n END";

        mSyncedDb.execSQL(currentValueTgSql);
    }

    /**
     * Get the style used by this builder.
     *
     * @return the style
     */
    @NonNull
    public BooklistStyle getStyle() {
        return mStyle;
    }

    /** Wrapper for {@link RowStateDAO#getOffsetCursor}. */
    @NonNull
    Cursor getOffsetCursor(final int offset,
                           @SuppressWarnings("SameParameterValue") final int pageSize) {

        return mRowStateDAO.getOffsetCursor(mListTable, offset, pageSize);
    }

    /**
     * Get the list cursor. Will be created if needed.
     * <p>
     * Note this is a {@link BooklistCursor}
     *
     * @return cursor
     */
    @NonNull
    public BooklistCursor getListCursor() {
        if (mCursor == null) {
            mCursor = new BooklistCursor(this);
        }
        return mCursor;
    }

    @NonNull
    public BooklistCursor newListCursor() {
        if (mCursor != null) {
            mCursor.close();
        }

        mCursor = new BooklistCursor(this);
        return mCursor;
    }

    @Nullable
    public ArrayList<RowStateDAO.Node> getBookNodes(final long bookId) {
        return mRowStateDAO.getBookNodes(mListTable, bookId);
    }

    /**
     * Get the full list of all Books (their id only) which are currently in the list table.
     *
     * @return list of book ID's
     */
    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        final String sql = SELECT_ + KEY_FK_BOOK
                           + _FROM_ + mListTable.getName()
                           + _WHERE_ + KEY_BL_NODE_GROUP + "=?"
                           + _ORDER_BY_ + KEY_FK_BOOK;

        try (Cursor cursor = mSyncedDb.rawQuery(sql, new String[]{
                String.valueOf(BooklistGroup.BOOK)})) {
            if (cursor.moveToFirst()) {
                final ArrayList<Long> rows = new ArrayList<>(cursor.getCount());
                do {
                    final long id = cursor.getInt(0);
                    rows.add(id);
                } while (cursor.moveToNext());
                return rows;
            } else {
                return new ArrayList<>();
            }
        }
    }

    @Nullable
    public RowStateDAO.Node getNextBookWithoutCover(@NonNull final Context context,
                                                    final long rowId) {
        return mRowStateDAO.getNextBookWithoutCover(context, mListTable, rowId);
    }

    /**
     * Get the list of column names that will be in the list for cursor implementations.
     *
     * @return array with column names
     */
    @NonNull
    String[] getListColumnNames() {
        // Get the domains
        final List<Domain> domains = mListTable.getDomains();
        // Make the array +1 so we can add KEY_BL_LIST_VIEW_ROW_ID
        final String[] names = new String[domains.size() + 1];
        for (int i = 0; i < domains.size(); i++) {
            names[i] = domains.get(i).getName();
        }

        names[domains.size()] = KEY_BL_LIST_VIEW_NODE_ROW_ID;
        return names;
    }

    /**
     * Count the number of <strong>distinct</strong> book records in the list.
     *
     * @return count
     */
    public int getDistinctBookCount() {
        if (mDistinctBooks == -1) {
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                    "SELECT COUNT(DISTINCT " + KEY_FK_BOOK + ")"
                    + _FROM_ + mListTable.getName()
                    + _WHERE_ + KEY_BL_NODE_GROUP + "=?")) {
                stmt.bindLong(1, BooklistGroup.BOOK);
                mDistinctBooks = (int) stmt.simpleQueryForLongOrZero();
            }
        }
        return mDistinctBooks;
    }

    /**
     * Count the total number of book records in the list.
     *
     * @return count
     */
    public int getBookCount() {
        if (mTotalBooks == -1) {
            try (SynchronizedStatement stmt = mSyncedDb.compileStatement(
                    "SELECT COUNT(*)"
                    + _FROM_ + mListTable.getName()
                    + _WHERE_ + KEY_BL_NODE_GROUP + "=?")) {
                stmt.bindLong(1, BooklistGroup.BOOK);
                mTotalBooks = (int) stmt.simpleQueryForLongOrZero();
            }
        }
        return mTotalBooks;
    }

    /**
     * Wrapper for {@link RowStateDAO#countVisibleRows()}.
     *
     * @return count
     */
    int countVisibleRows() {
        return mRowStateDAO.countVisibleRows();
    }

    /**
     * Expand or collapse <strong>all</strong> nodes.
     * The internal cursor will be null'd but it's still the clients responsibility
     * to refresh their adapter.
     *
     * @param topLevel the desired top-level which must be kept visible
     * @param expand   the state to apply to levels 'below' the topLevel (level > topLevel),
     */
    public void expandAllNodes(@IntRange(from = 1) final int topLevel,
                               final boolean expand) {
        mRowStateDAO.expandAllNodes(topLevel, expand);
        mCursor = null;
    }

    /** Wrapper for {@link RowStateDAO}. */
    @NonNull
    RowStateDAO.Node getNodeByNodeId(final int rowId) {
        return mRowStateDAO.getNodeByNodeId(rowId);
    }

    /**
     * Toggle (expand/collapse) the given node.
     *
     * @param nodeRowId          list-view row id of the node in the list
     * @param nextState          the state to set the node to
     * @param relativeChildLevel up to and including this (relative to the node) child level;
     *
     * @return the node
     */
    @NonNull
    public RowStateDAO.Node toggleNode(final long nodeRowId,
                                       @RowStateDAO.Node.NodeNextState final int nextState,
                                       final int relativeChildLevel) {
        final RowStateDAO.Node node = mRowStateDAO.getNodeByNodeId(nodeRowId);
        node.setNextState(nextState);
        mRowStateDAO.setNode(node.getRowId(), node.getLevel(),
                             node.isExpanded(), relativeChildLevel);
        mRowStateDAO.findAndSetListPosition(node);
        return node;
    }

    /**
     * Close the builder.
     * <p>
     * RuntimeException are caught and ignored.
     * <p>
     * We cleanup temporary tables simply to free up no longer needed resources NOW.
     */
    @Override
    public void close() {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "|close|mInstanceId=" + mInstanceId);
        }
        mCloseWasCalled = true;

        if (mRowStateDAO != null) {
            mRowStateDAO.close();
        }
        if (mCursor != null) {
            mCursor.close();
        }
        if (mListTable != null) {
            mSyncedDb.drop(mListTable.getName());
        }
        if (mTriggerHelperTable != null) {
            mSyncedDb.drop(mTriggerHelperTable.getName());
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            if (!mDebugReferenceDecremented) {
                int inst = DEBUG_INSTANCE_COUNTER.decrementAndGet();
                // Only de-reference once! Paranoia ... close() might be called twice?
                Log.d(TAG, "close|instances left=" + inst);
            }
            mDebugReferenceDecremented = true;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(mInstanceId);
    }

    /**
     * Simple equality: two builders are equal if their ID's are the same.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BooklistBuilder that = (BooklistBuilder) obj;
        return mInstanceId == that.mInstanceId;
    }

    @Override
    @NonNull
    public String toString() {
        return "BooklistBuilder{"
               + "mInstanceId=" + mInstanceId
               + ", mCloseWasCalled=" + mCloseWasCalled
               + ", mTotalBooks=" + mTotalBooks
               + ", mDistinctBooks=" + mDistinctBooks
               + ", mFilters=" + mFilters
               + ", mBookshelf=" + mBookshelf
               + '}';
    }

    /**
     * DEBUG: if we see the warn in the logs, we know we have an issue to fix.
     */
    @SuppressWarnings("FinalizeDeclaration")
    @Override
    @CallSuper
    protected void finalize()
            throws Throwable {
        if (!mCloseWasCalled) {
            if (BuildConfig.DEBUG /* always */) {
                Log.w(TAG, "finalize|" + mInstanceId);
            }
            close();
        }
        super.finalize();
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PREF_REBUILD_SAVED_STATE,
             PREF_REBUILD_ALWAYS_EXPANDED,
             PREF_REBUILD_ALWAYS_COLLAPSED,
             PREF_REBUILD_PREFERRED_STATE})
    public @interface ListRebuildMode {

    }

    /**
     * A Builder to accumulates data while building the list table
     * and produce the initial SQL insert statement.
     */
    private static final class BuildHelper {

        @NonNull
        private final BooklistStyle mStyle;
        /** Set to {@code true} if we're filtering on a specific bookshelf. */
        private final boolean mFilteredOnBookshelf;
        /** the list of Filters. */
        private final Collection<Filter<?>> mFilters = new ArrayList<>();

        /** The table we'll be generating. */
        @NonNull
        private final TableDefinition mDestinationTable;

        /**
         * Domains required in output table.
         * During the build, the {@link Domain} is added to the {@link #mDestinationTable}
         * where they will be used to create the table.
         * <p>
         * The full {@link VirtualDomain} is kept this collection to build the SQL column string
         * for both the INSERT and the SELECT statement.
         */
        private final Collection<VirtualDomain> mDomains = new ArrayList<>();

        /** Domains belonging the current group including its outer groups. */
        private final List<Domain> mAccumulatedDomains = new ArrayList<>();

        /**
         * Domains that form part of the sort key.
         * These are typically a reduced set of the group domains since the group domains
         * may contain more than just the key
         */
        private final List<VirtualDomain> mOrderByDomains = new ArrayList<>();

        /** Guards from adding duplicates. */
        private final Map<Domain, String> mExpressionsDupCheck = new HashMap<>();
        /** Guards from adding duplicates. */
        private final Collection<String> mOrderByDupCheck = new HashSet<>();

        /**
         * Constructor.
         *
         * @param context          Current context
         * @param destinationTable the list table to build
         * @param style            to use
         * @param bookshelf        to use
         */
        BuildHelper(@NonNull final Context context,
                    @NonNull final TableDefinition destinationTable,
                    @NonNull final BooklistStyle style,
                    @NonNull final Bookshelf bookshelf) {
            mDestinationTable = destinationTable;
            mStyle = style;
            mFilteredOnBookshelf = !bookshelf.isAllBooks();
            // copy filter references, but not the actual style filter list
            // as we'll be adding to the local list
            mFilters.addAll(mStyle.getActiveFilters(context));
        }

        void addDomains(@NonNull final Iterable<VirtualDomain> vDomains,
                        final boolean isGroup) {
            for (VirtualDomain vDomain : vDomains) {
                addDomain(vDomain, isGroup);
            }
        }

        /**
         * Add a VirtualDomain.
         * This encapsulates the actual Domain, the expression, and the (optional) sort flag.
         *
         * @param vDomain VirtualDomain to add
         * @param isGroup flag: the added VirtualDomain is a group level domain.
         */
        void addDomain(@NonNull final VirtualDomain vDomain,
                       final boolean isGroup) {

            // Add to the table, if not already there
            final boolean added = mDestinationTable.addDomain(vDomain.getDomain());
            final String expression = vDomain.getExpression();

            // If the domain was already present, and it has an expression,
            // check the expression being different (or not) from the stored expression
            if (!added
                && expression != null
                && expression.equals(mExpressionsDupCheck.get(vDomain.getDomain()))) {
                // same expression, we do NOT want to add it.
                // This is NOT a bug, although one could argue it's an efficiency issue.
                return;
            }

            // If the expression is {@code null},
            // then the domain is just meant for the lowest level; i.e. the book.
            if (expression != null) {
                mDomains.add(vDomain);
                mExpressionsDupCheck.put(vDomain.getDomain(), expression);
            }

            // If needed, add to the order-by domains, if not already there
            if (vDomain.isSorted() && !mOrderByDupCheck.contains(vDomain.getDomain().getName())) {
                mOrderByDomains.add(vDomain);
                mOrderByDupCheck.add(vDomain.getDomain().getName());
            }

            // Accumulate the group domains.
            if (isGroup) {
                mAccumulatedDomains.add(vDomain.getDomain());
            }
        }

        /**
         * Add the domains for the given group.
         *
         * @param group to add
         */
        void addGroup(@NonNull final BooklistGroup group) {
            // dev sanity check
            if (group.getId() == BooklistGroup.BOOK) {
                throw new IllegalArgumentException("Cannot group by Book");
            }

            // Do this in 3 steps, allowing groups to override their parent (if any) group
            addDomain(group.getDisplayDomain(), true);
            addDomains(group.getGroupDomains(), true);
            addDomains(group.getBaseDomains(), false);

            /*
             * Copy all current groups to this group; this effectively accumulates
             * 'GROUP BY' domains down each level so that the top has fewest groups and
             * the bottom level has groups for all levels.
             *
             * Since BooklistGroup objects are processed in order, this allows us to get
             * the fields applicable to the currently processed group, including its outer groups.
             *
             * As subsequent addGroup calls will modify this collection,
             * we make a (shallow) copy of the list. (ArrayList as we'll need to parcel it)
             */
            group.setAccumulatedDomains(new ArrayList<>(mAccumulatedDomains));
        }

        /**
         * Add Filters.
         *
         * @param filters list of filters; only active filters will be added
         */
        void addFilters(@NonNull final Collection<Filter<?>> filters) {
            mFilters.addAll(filters);
        }

        /**
         * Get the list of domains used to sort the output.
         *
         * @return the list
         */
        @NonNull
        List<VirtualDomain> getSortedDomains() {
            return mOrderByDomains;
        }

        /**
         * Using the collected domain info, create the various SQL phrases used to build
         * the resulting flat list table and build the SQL that does the initial table load.
         *
         * @param context Current context
         *
         * @return initial insert statement
         */
        @NonNull
        String build(@NonNull final Context context) {

            // List of column names for the INSERT INTO... clause
            StringBuilder destColumns = new StringBuilder();
            // List of expressions for the SELECT... clause.
            StringBuilder sourceColumns = new StringBuilder();

            boolean first = true;
            for (VirtualDomain domain : mDomains) {
                if (first) {
                    first = false;
                } else {
                    destColumns.append(',');
                    sourceColumns.append(',');
                }

                destColumns.append(domain.getName());
                sourceColumns.append(domain.getExpression())
                             // 'AS' for SQL readability/debug only
                             .append(" AS ").append(domain.getName());
            }

            // add the node key column
            destColumns.append(',').append(KEY_BL_NODE_KEY);
            sourceColumns.append(',').append(buildNodeKey())
                         // 'AS' for SQL readability/debug only
                         .append(" AS ").append(DOM_BL_NODE_KEY);

            String sql = "INSERT INTO " + mDestinationTable.getName() + " (" + destColumns + ')'
                         + " SELECT " + sourceColumns
                         + _FROM_ + buildFrom(context) + buildWhere(context)
                         + _ORDER_BY_ + buildOrderBy();

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
                Log.d(TAG, "build|sql=" + sql);
            }
            return sql;
        }

        /**
         * Create the expression for the key column.
         * Will contain one key-value pair for each each group level.
         * "/key=value/key=value/key=value/..."
         * A {@code null} value is reformatted as an empty string
         *
         * @return column expression
         */
        private String buildNodeKey() {
            StringBuilder keyColumn = new StringBuilder();
            for (BooklistGroup group : mStyle.getGroups()) {
                keyColumn.append(group.getNodeKeyExpression()).append("||");
            }
            int len = keyColumn.length();
            // remove the trailing "||"
            return keyColumn.delete(len - 2, len).toString();
        }

        /**
         * Create the FROM clause based on the {@link BooklistGroup}s and extra criteria.
         * <ul>Always joined are:
         *      <li>{@link DBDefinitions#TBL_BOOK_AUTHOR}
         *      + {@link DBDefinitions#TBL_AUTHORS}</li>
         * </ul>
         * <ul>Optionally joined with:
         *      <li>{@link DBDefinitions#TBL_BOOK_SERIES}
         *      + {@link DBDefinitions#TBL_SERIES}</li>
         *      <li>{@link DBDefinitions#TBL_BOOK_PUBLISHER}
         *      + {@link DBDefinitions#TBL_PUBLISHERS}</li>
         *      <li>{@link DBDefinitions#TBL_BOOK_BOOKSHELF}
         *      + {@link DBDefinitions#TBL_BOOKSHELF}</li>
         *      <li>{@link DBDefinitions#TBL_BOOK_LOANEE}</li>
         * </ul>
         *
         * @param context Current context
         *
         * @return FROM clause
         */
        private String buildFrom(@NonNull final Context context) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            // Text of join statement
            final StringBuilder sql = new StringBuilder();

            // If there is a bookshelf specified (either as group or as a filter),
            // start the join there.
            if (mStyle.containsGroup(BooklistGroup.BOOKSHELF) || mFilteredOnBookshelf) {
                sql.append(TBL_BOOKSHELF.ref())
                   .append(TBL_BOOKSHELF.join(TBL_BOOK_BOOKSHELF))
                   .append(TBL_BOOK_BOOKSHELF.join(TBL_BOOKS));
            } else {
                // Otherwise, start with the BOOKS table.
                sql.append(TBL_BOOKS.ref());
            }

            if (DBDefinitions.isUsed(prefs, DBDefinitions.KEY_LOANEE)) {
                // get the loanee name, or a {@code null} for available books.
                sql.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_LOANEE));
            }

            // Join with the link table between Book and Author.
            sql.append(TBL_BOOKS.join(TBL_BOOK_AUTHOR));

            // Extend the join filtering on the primary Author unless
            // the user wants the book to show under all its Authors
            if (!mStyle.isShowBooksUnderEachAuthor(context)) {
                @Author.Type
                final int primaryAuthorType = mStyle.getPrimaryAuthorType(context);
                if (primaryAuthorType == Author.TYPE_UNKNOWN) {
                    // don't care about Author type, so just grab the primary (i.e. pos==1)
                    sql.append(_AND_)
                       .append(TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION)).append("=1");
                } else {
                    // grab the desired type, or if no such type, grab the 1st
                    //   AND (((type & TYPE)<>0) OR (((type &~ TYPE)=0) AND pos=1))
                    sql.append(" AND (((")
                       .append(TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_TYPE_BITMASK))
                       .append(" & ").append(primaryAuthorType).append(")<>0)")
                       .append(" OR (((")
                       .append(TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_TYPE_BITMASK))
                       .append(" &~ ").append(primaryAuthorType).append(")=0)")
                       .append(_AND_)
                       .append(TBL_BOOK_AUTHOR.dot(KEY_BOOK_AUTHOR_POSITION)).append("=1))");
                }
            }
            // Join with Authors to make the names available
            sql.append(TBL_BOOK_AUTHOR.join(TBL_AUTHORS));

            if (mStyle.containsGroup(BooklistGroup.SERIES)
                || DBDefinitions.isUsed(prefs, DBDefinitions.KEY_SERIES_TITLE)) {
                // Join with the link table between Book and Series.
                sql.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_SERIES));
                // Extend the join filtering on the primary Series unless
                // the user wants the book to show under all its Series
                if (!mStyle.isShowBooksUnderEachSeries(context)) {
                    sql.append(_AND_)
                       .append(TBL_BOOK_SERIES.dot(KEY_BOOK_SERIES_POSITION)).append("=1");
                }
                // Join with Series to make the titles available
                sql.append(TBL_BOOK_SERIES.leftOuterJoin(TBL_SERIES));
            }

            if (mStyle.containsGroup(BooklistGroup.PUBLISHER)
                || DBDefinitions.isUsed(prefs, DBDefinitions.KEY_PUBLISHER_NAME)) {
                // Join with the link table between Book and Publishers.
                sql.append(TBL_BOOKS.leftOuterJoin(TBL_BOOK_PUBLISHER));
                // Extend the join filtering on the primary Publisher unless
                // the user wants the book to show under all its Publishers
                if (!mStyle.isShowBooksUnderEachPublisher(context)) {
                    sql.append(_AND_)
                       .append(TBL_BOOK_PUBLISHER.dot(KEY_BOOK_PUBLISHER_POSITION)).append("=1");
                }
                // Join with Publishers to make the names available
                sql.append(TBL_BOOK_PUBLISHER.leftOuterJoin(TBL_PUBLISHERS));
            }
            return sql.toString();
        }

        /**
         * Create the WHERE clause based on all active filters (for in-use domains).
         *
         * @param context Current context
         *
         * @return WHERE clause, can be empty
         */
        private String buildWhere(@NonNull final Context context) {
            final StringBuilder where = new StringBuilder();

            mFilters.stream()
                    // Theoretically all filters should be active here, but paranoia...
                    .filter(filter -> filter.isActive(context))
                    .forEach(filter -> {
                        if (where.length() != 0) {
                            where.append(_AND_);
                        }
                        where.append(' ').append(filter.getExpression(context));
                    });

            if (where.length() > 0) {
                return _WHERE_ + where;
            } else {
                return "";
            }
        }

        /**
         * Process the 'sort-by' columns into a list suitable for an ORDER-BY statement.
         *
         * @return ORDER BY clause
         */
        private String buildOrderBy() {
            // List of column names appropriate for 'ORDER BY' clause
            final StringBuilder orderBy = new StringBuilder();
            for (VirtualDomain sd : mOrderByDomains) {
                if (sd.getDomain().isText()) {
                    // The order of this if/elseif/else is important, don't merge branch 1 and 3!
                    if (sd.getDomain().isPrePreparedOrderBy()) {
                        // always use a pre-prepared order-by column as-is
                        orderBy.append(sd.getName());

                    } else if (DBHelper.isCollationCaseSensitive()) {
                        // If {@link DAO#COLLATION} is case-sensitive, lowercase it.
                        // This should never happen, but see the DAO method docs.
                        orderBy.append("lower(").append(sd.getName()).append(')');

                    } else {
                        // hope for the best. This case might not handle non-[A..Z0..9] as expected
                        orderBy.append(sd.getName());
                    }

                    orderBy.append(DAO._COLLATION);
                } else {
                    orderBy.append(sd.getName());
                }

                orderBy.append(sd.getSortedExpression()).append(',');
            }

            final int len = orderBy.length();
            return orderBy.delete(len - 1, len).toString();
        }

        /**
         * Process the 'sort-by' columns into a list suitable for an CREATE-INDEX statement.
         *
         * @return column list clause
         */
        @NonNull
        String getSortedDomainsIndexColumns() {
            final StringBuilder indexCols = new StringBuilder();

            for (VirtualDomain sd : mOrderByDomains) {
                indexCols.append(sd.getName());
                if (sd.getDomain().isText()) {
                    indexCols.append(DAO._COLLATION);
                }
                indexCols.append(sd.getSortedExpression()).append(',');
            }
            final int len = indexCols.length();
            return indexCols.delete(len - 1, len).toString();
        }
    }
}
