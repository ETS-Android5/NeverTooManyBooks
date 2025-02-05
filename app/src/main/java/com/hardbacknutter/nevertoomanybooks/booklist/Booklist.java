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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.database.Cursor;
import android.util.Log;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedDb;
import com.hardbacknutter.nevertoomanybooks.database.dbsync.SynchronizedStatement;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

public class Booklist
        implements AutoCloseable {

    /**
     * DEBUG Instance counter. Increment and decrements to check leaks.
     * This is probably no longer needed.
     */
    @NonNull
    static final AtomicInteger DEBUG_INSTANCE_COUNTER = new AtomicInteger();

    private static final String SELECT_COUNT_FROM_ = "SELECT COUNT(*) FROM ";
    private static final String SELECT_ = "SELECT ";
    private static final String _FROM_ = " FROM ";
    private static final String _AS_ = " AS ";
    private static final String _WHERE_ = " WHERE ";
    private static final String _AND_ = " AND ";
    private static final String _ORDER_BY_ = " ORDER BY ";
    private static final String UPDATE_ = "UPDATE ";
    private static final String _SET_ = " SET ";

    /** Log tag. */
    private static final String TAG = "Booklist";

    /** Database Access. */
    @SuppressWarnings("FieldNotUsedInToString")
    @NonNull
    private final SynchronizedDb db;

    /**
     * Internal ID. Used to create unique names for the temporary tables.
     * Used in this class for logging and {@link #equals}.
     */
    private final int instanceId;

    /**
     * The temp table representing the booklist for the current bookshelf/style.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private final TableDefinition listTable;

    /**
     * The navigation table for next/prev moving between books.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private final TableDefinition navTable;

    /**
     * A helper DAO to maintain the current list table.
     * <p>
     * Reminder: this is a {@link TableDefinition.TableType#Temporary}.
     */
    @SuppressWarnings("FieldNotUsedInToString")
    private final BooklistNodeDao nodeDao;

    /** Total number of books in current list. e.g. a book can be listed under 2 authors. */
    private int totalBooks = -1;

    /** Total number of unique books in current list. */
    private int distinctBooks = -1;

    /** The current list cursor. */
    @SuppressWarnings("FieldNotUsedInToString")
    @Nullable
    private Cursor listCursor;

    /** {@link #getVisibleBookNodes}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String sqlGetBookNodes;

    /** {@link #ensureNodeIsVisible}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String sqlEnsureNodeIsVisible;

    /** {@link #getNodeByRowId}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String sqlGetNodeByRowId;

    /** {@link #getBookIdsForNodeKey}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String sqlGetBookIdListForNodeKey;

    /** {@link #getNextBookWithoutCover(long)}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String sqlGetNextBookWithoutCover;

    /** {@link #getOffsetCursor(int, int)}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String sqlGetOffsetCursor;

    /** {@link #updateBookRead(long, boolean)}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String sqlUpdateBookRead;

    /** {@link #updateBookLoanee(long, String)}. */
    @SuppressWarnings("FieldNotUsedInToString")
    private String sqlUpdateBookLoanee;

    Booklist(final int instanceId,
             @NonNull final SynchronizedDb db,
             @NonNull final TableDefinition listTable,
             @NonNull final TableDefinition navTable,
             @NonNull final BooklistNodeDao nodeDao) {

        this.instanceId = instanceId;
        this.db = db;
        this.listTable = listTable;
        this.navTable = navTable;
        this.nodeDao = nodeDao;
    }

    @NonNull
    public String getNavigationTableName() {
        return navTable.getName();
    }

    /**
     * Count the total number of book records in the list.
     *
     * @return count
     */
    public int countBooks() {
        if (totalBooks == -1) {
            try (SynchronizedStatement stmt = db.compileStatement(
                    SELECT_COUNT_FROM_ + listTable.getName()
                    + _WHERE_ + DBKey.KEY_BL_NODE_GROUP + "=?")) {
                stmt.bindLong(1, BooklistGroup.BOOK);
                totalBooks = (int) stmt.simpleQueryForLongOrZero();
            }
        }
        return totalBooks;
    }

    /**
     * Count the number of <strong>distinct</strong> book records in the list.
     *
     * @return count
     */
    public int countDistinctBooks() {
        if (distinctBooks == -1) {
            try (SynchronizedStatement stmt = db.compileStatement(
                    "SELECT COUNT(DISTINCT " + DBKey.FK_BOOK + ")"
                    + _FROM_ + listTable.getName()
                    + _WHERE_ + DBKey.KEY_BL_NODE_GROUP + "=?")) {

                stmt.bindLong(1, BooklistGroup.BOOK);
                distinctBooks = (int) stmt.simpleQueryForLongOrZero();
            }
        }
        return distinctBooks;
    }

    /**
     * Count the number of visible rows.
     *
     * @return count
     */
    int countVisibleRows() {
        try (SynchronizedStatement stmt = db.compileStatement(
                SELECT_COUNT_FROM_ + listTable.getName()
                + _WHERE_ + DBKey.KEY_BL_NODE_VISIBLE + "=1")) {
            return (int) stmt.simpleQueryForLongOrZero();
        }
    }

    /**
     * Get the list cursor.
     *
     * @return cursor
     */
    @NonNull
    public Cursor getNewListCursor() {
        if (listCursor != null) {
            listCursor.close();
        }

        listCursor = new BooklistCursor(this);
        return listCursor;
    }

    /**
     * Gets a 'window' on the result set, starting at 'offset' and 'pageSize' rows.
     * We only retrieve visible rows.
     *
     * @param offset   the offset position (SQL OFFSET clause)
     * @param pageSize the amount of results maximum to return (SQL LIMIT clause)
     *
     * @return a list cursor starting at a given offset, using a given limit.
     */
    @NonNull
    Cursor getOffsetCursor(final int offset,
                           @SuppressWarnings("SameParameterValue") final int pageSize) {

        if (sqlGetOffsetCursor == null) {
            sqlGetOffsetCursor =
                    SELECT_ + listTable.getDomains()
                                       .stream()
                                       .map(Domain::getName)
                                       .map(listTable::dot)
                                       .collect(Collectors.joining(","))
                    + ',' + (listTable.dot(DBKey.PK_ID)
                             + _AS_ + DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID)
                    + _FROM_ + listTable.ref()
                    + _WHERE_ + listTable.dot(DBKey.KEY_BL_NODE_VISIBLE) + "=1"
                    + _ORDER_BY_ + listTable.dot(DBKey.PK_ID)
                    + " LIMIT ? OFFSET ?";
        }

        return db.rawQuery(sqlGetOffsetCursor, new String[]{
                String.valueOf(pageSize),
                String.valueOf(offset)});
    }

    /**
     * Get the list of column names that will be in the list for cursor implementations.
     *
     * @return array with column names
     */
    @NonNull
    String[] getListColumnNames() {
        // Get the domains
        final List<Domain> domains = listTable.getDomains();
        // Make the array +1 so we can add KEY_BL_LIST_VIEW_ROW_ID
        final String[] names = new String[domains.size() + 1];
        for (int i = 0; i < domains.size(); i++) {
            names[i] = domains.get(i).getName();
        }

        names[domains.size()] = DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID;
        return names;
    }

    /**
     * Expand or collapse <strong>all</strong> nodes.
     * The internal cursor will be set to {@code null} but it's still the clients responsibility
     * to refresh their adapter.
     *
     * @param topLevel the desired top-level which must be kept visible
     * @param expand   the state to apply to levels 'below' the topLevel (level > topLevel),
     */
    public void setAllNodes(@IntRange(from = 1) final int topLevel,
                            final boolean expand) {
        nodeDao.setAllNodes(topLevel, expand);
        if (listCursor != null) {
            listCursor.close();
        }
        listCursor = null;
    }

    /**
     * Toggle (expand/collapse) the given node.
     *
     * @param rowId              list-view row id of the node in the list
     * @param nextState          the state to set the node to
     * @param relativeChildLevel up to and including this (relative to the node) child level;
     *
     * @return the node
     */
    @NonNull
    public BooklistNode setNode(final long rowId,
                                @NonNull final BooklistNode.NextState nextState,
                                final int relativeChildLevel) {
        final BooklistNode node = getNodeByRowId(rowId);
        node.setNextState(nextState);
        nodeDao.setNode(node.getRowId(), node.getLevel(),
                        node.isExpanded(), relativeChildLevel);
        node.updateAdapterPosition(db, listTable);
        return node;
    }

    boolean isNodeExpanded(final long rowId) {
        return getNodeByRowId(rowId).isExpanded();
    }

    /**
     * Get the node for the the given row id (any row, not limited to books).
     * It will contain the current state of the node as stored in the database.
     *
     * @param rowId to get
     *
     * @return the node
     */
    @NonNull
    private BooklistNode getNodeByRowId(final long rowId) {
        if (sqlGetNodeByRowId == null) {
            sqlGetNodeByRowId = SELECT_ + BooklistNode.getColumns(listTable)
                                + _FROM_ + listTable.ref()
                                + _WHERE_ + listTable.dot(DBKey.PK_ID) + "=?";
        }

        try (Cursor cursor = db.rawQuery(sqlGetNodeByRowId, new String[]{
                String.valueOf(rowId)})) {

            if (cursor.moveToFirst()) {
                final BooklistNode node = new BooklistNode();
                node.from(cursor);
                node.updateAdapterPosition(db, listTable);
                return node;
            } else {
                throw new IllegalArgumentException("rowId not found: " + rowId);
            }
        }
    }

    /**
     * Find the nodes that show the given book.
     * Either returns a list of <strong>already visible</strong> nodes,
     * or all nodes (after making them visible).
     *
     * @param bookId the book to find
     *
     * @return list of visible nodes, can be empty, but never {@code null}
     */
    @NonNull
    public List<BooklistNode> getVisibleBookNodes(@IntRange(from = 0) final long bookId) {
        final List<BooklistNode> nodeList = getBookNodes(bookId);

        if (nodeList.isEmpty()) {
            // the book is not present
            return nodeList;
        }

        // We have nodes; first get the ones that are currently visible
        final List<BooklistNode> visibleNodes =
                nodeList.stream()
                        .filter(BooklistNode::isVisible)
                        .collect(Collectors.toList());

        // If we have nodes already visible, return those
        if (!visibleNodes.isEmpty()) {
            return visibleNodes;

        } else {
            // Make them all visible
            nodeList.forEach(this::ensureNodeIsVisible);

            // Recalculate all positions
            nodeList.forEach(node -> node.updateAdapterPosition(db, listTable));

            return nodeList;
        }
    }

    /**
     * Get <strong>all</strong> nodes for the given book id
     *
     * @param bookId to use
     *
     * @return list of nodes, can be empty, but never {@code null}
     */
    @NonNull
    private List<BooklistNode> getBookNodes(@IntRange(from = 0) final long bookId) {
        final List<BooklistNode> nodeList = new ArrayList<>();

        // sanity check
        if (bookId == 0) {
            return nodeList;
        }

        if (sqlGetBookNodes == null) {
            sqlGetBookNodes =
                    SELECT_ + BooklistNode.getColumns(listTable)
                    + _FROM_ + listTable.ref()
                    + _WHERE_ + listTable.dot(DBKey.FK_BOOK) + "=?";
        }

        // get all positions the book is on
        try (Cursor cursor = db.rawQuery(sqlGetBookNodes, new String[]{
                String.valueOf(bookId)})) {

            final BooklistNode node = new BooklistNode();
            while (cursor.moveToNext()) {
                node.from(cursor);
                node.updateAdapterPosition(db, listTable);
                nodeList.add(node);
            }
        }
        return nodeList;
    }

    /**
     * Ensure al nodes up to the root node for the given (book) node (inclusive) are visible.
     *
     * @param node we want to become visible
     */
    private void ensureNodeIsVisible(@NonNull final BooklistNode node) {

        if (sqlEnsureNodeIsVisible == null) {
            sqlEnsureNodeIsVisible =
                    SELECT_ + DBKey.PK_ID + _FROM_ + listTable.getName()
                    // follow the node hierarchy
                    + _WHERE_ + DBKey.KEY_BL_NODE_KEY + " LIKE ?"
                    // we'll loop for all levels
                    + _AND_ + DBKey.KEY_BL_NODE_LEVEL + "=?";
        }

        node.setFullyVisible();
        String nodeKey = node.getKey();

        // levels are 1.. based; start with lowest level above books, working up to root.
        // Pair: rowId/Level
        final Deque<Pair<Long, Integer>> nodes = new ArrayDeque<>();
        for (int level = node.getLevel() - 1; level >= 1; level--) {
            try (Cursor cursor = db.rawQuery(sqlEnsureNodeIsVisible, new String[]{
                    nodeKey + "%",
                    String.valueOf(level)})) {

                while (cursor.moveToNext()) {
                    final long rowId = cursor.getLong(0);
                    nodes.push(new Pair<>(rowId, level));
                }
            }
            nodeKey = nodeKey.substring(0, nodeKey.lastIndexOf('/'));
        }

        // Now process the collected nodes from the root downwards.
        for (final Pair<Long, Integer> n : nodes) {
            nodeDao.setNode(n.first, n.second,
                            // Expand (and make visible) the given node
                            true,
                            // do this for only ONE level
                            1);
        }
    }

    /**
     * Allows updating the current list-table without requiring a whole new build
     * with the 'read' status of a book. Will update all nodes for the given book.
     *
     * @param bookId to update
     * @param read   status to set
     *
     * @return list with the nodes which were changed.
     */
    @NonNull
    public List<BooklistNode> updateBookRead(@IntRange(from = 1) final long bookId,
                                             final boolean read) {
        final boolean hasDomain = listTable.getDomains()
                                           .stream()
                                           .map(Domain::getName)
                                           .anyMatch(name -> name.equals(DBKey.READ__BOOL));
        if (hasDomain) {
            if (sqlUpdateBookRead == null) {
                sqlUpdateBookRead = UPDATE_ + listTable.getName()
                                    + _SET_ + DBKey.READ__BOOL + "=?"
                                    + _WHERE_ + DBKey.FK_BOOK + "=?"
                                    + _AND_ + DBKey.KEY_BL_NODE_GROUP + "=" + BooklistGroup.BOOK;
            }

            try (SynchronizedStatement stmt = db.compileStatement(sqlUpdateBookRead)) {
                stmt.bindBoolean(1, read);
                stmt.bindLong(2, bookId);
                stmt.executeUpdateDelete();
            }
        }
        return getBookNodes(bookId);
    }

    /**
     * Allows updating the current list-table without requiring a whole new build
     * with the 'loanee' of a book. Will update all nodes for the given book.
     *
     * @param bookId to update
     * @param loanee loanee to set
     *
     * @return list with the nodes which were changed.
     */
    @NonNull
    public List<BooklistNode> updateBookLoanee(@IntRange(from = 1) final long bookId,
                                               @Nullable final String loanee) {
        final boolean hasDomain = listTable.getDomains()
                                           .stream()
                                           .map(Domain::getName)
                                           .anyMatch(name -> name.equals(DBKey.LOANEE_NAME));
        if (hasDomain) {
            if (sqlUpdateBookLoanee == null) {
                sqlUpdateBookLoanee = UPDATE_ + listTable.getName()
                                      + _SET_ + DBKey.LOANEE_NAME + "=?"
                                      + _WHERE_ + DBKey.FK_BOOK + "=?"
                                      + _AND_ + DBKey.KEY_BL_NODE_GROUP + "=" + BooklistGroup.BOOK;
            }

            try (SynchronizedStatement stmt = db.compileStatement(sqlUpdateBookLoanee)) {
                stmt.bindString(1, loanee != null ? loanee : "");
                stmt.bindLong(2, bookId);
                stmt.executeUpdateDelete();
            }
        }
        return getBookNodes(bookId);
    }

    /**
     * Get the ids of all Books for the given node key.
     *
     * @param nodeKey to use
     *
     * @return list of book ID's
     */
    @NonNull
    public ArrayList<Long> getBookIdsForNodeKey(@NonNull final String nodeKey) {
        if (sqlGetBookIdListForNodeKey == null) {
            sqlGetBookIdListForNodeKey =
                    SELECT_ + DBKey.FK_BOOK
                    + _FROM_ + listTable.getName()
                    + _WHERE_ + DBKey.KEY_BL_NODE_KEY + "=?"
                    + _AND_ + DBKey.KEY_BL_NODE_GROUP + "=" + BooklistGroup.BOOK
                    + _ORDER_BY_ + DBKey.FK_BOOK;
        }

        try (Cursor cursor = db.rawQuery(sqlGetBookIdListForNodeKey, new String[]{nodeKey})) {
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

    @NonNull
    public Optional<BooklistNode> getNextBookWithoutCover(final long rowId) {

        if (sqlGetNextBookWithoutCover == null) {
            sqlGetNextBookWithoutCover =
                    SELECT_ + BooklistNode.getColumns(listTable)
                    + ',' + listTable.dot(DBKey.BOOK_UUID)
                    + _FROM_ + listTable.ref()
                    + _WHERE_ + listTable.dot(DBKey.KEY_BL_NODE_GROUP) + "=?"
                    + _AND_ + listTable.dot(DBKey.PK_ID) + ">?";
        }

        try (Cursor cursor = db.rawQuery(sqlGetNextBookWithoutCover, new String[]{
                String.valueOf(BooklistGroup.BOOK),
                String.valueOf(rowId)})) {

            final BooklistNode node = new BooklistNode();
            while (cursor.moveToNext()) {
                final int nextCol = node.from(cursor);
                final String uuid = cursor.getString(nextCol);
                final Optional<File> file = Book.getPersistedCoverFile(uuid, 0);
                if (file.isPresent()) {
                    // FIRST make the node visible
                    ensureNodeIsVisible(node);
                    // only now calculate the list position
                    node.updateAdapterPosition(db, listTable);
                    return Optional.of(node);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Cleanup.
     * <p>
     * RuntimeException are caught and ignored.
     * <p>
     * We cleanup temporary tables to free up no longer needed resources NOW.
     */
    @Override
    public void close() {
        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG, "|close|mInstanceId=" + instanceId);
        }

        if (listCursor != null) {
            listCursor.close();
        }
        db.drop(listTable.getName());

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER) {
            Log.d(TAG, "close|instances left=" + DEBUG_INSTANCE_COUNTER.decrementAndGet());
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(instanceId);
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
        final Booklist that = (Booklist) obj;
        return instanceId == that.instanceId;
    }

    @Override
    @NonNull
    public String toString() {
        return "Booklist{"
               + "instanceId=" + instanceId
               + ", totalBooks=" + totalBooks
               + ", distinctBooks=" + distinctBooks
               + '}';
    }

}
