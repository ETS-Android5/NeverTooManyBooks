/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.viewmodels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BooksOnBookshelf;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FTSSearchActivity;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistPseudoCursor;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
import com.hardbacknutter.nevertoomanybooks.database.cursors.TrackedCursor;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GoodreadsTasks;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskBase;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;

/**
 * First attempt to split of into a model for BoB.
 */
public class BooksOnBookshelfModel
        extends ViewModel {

    private static final String TAG = "BooksOnBookshelf";
    /** Preference name - Saved position of last top row. */
    public static final String PREF_BOB_TOP_ROW = TAG + ".TopRow";
    /** Preference name - Saved position of last top row offset from view top. */
    public static final String PREF_BOB_TOP_ROW_OFFSET = TAG + ".TopRowOffset";

    /** collapsed/expanded. */
    public static final String BKEY_LIST_STATE = TAG + ":list.state";

    /** The result of building the booklist. */
    private final MutableLiveData<BuilderHolder> mBuilderResult = new MutableLiveData<>();
    /** Allows progress message from a task to update the user. */
    private final MutableLiveData<Object> mUserMessage = new MutableLiveData<>();
    /** Inform user that Goodreads needs authentication/authorization. */
    private final MutableLiveData<Boolean> mNeedsGoodreads = new MutableLiveData<>();

    /**
     * Holder for all search criteria.
     * See {@link SearchCriteria} for more info.
     */
    private final SearchCriteria mSearchCriteria = new SearchCriteria();
    /** Cache for all bookshelf names / spinner list. */
    private final List<String> mBookshelfNameList = new ArrayList<>();
    /** Database Access. */
    private DAO mDb;
    /** Lazy init, always use {@link #getGoodreadsTaskListener()}. */
    private TaskListener<Integer> mOnGoodreadsTaskListener;
    /**
     * Flag (potentially) set in {@link BooksOnBookshelf} #onActivityResult.
     * Indicates if list rebuild is needed in {@link BooksOnBookshelf}#onResume.
     * {@code null} means no rebuild at all, otherwise full or partial rebuild.
     */
    @Nullable
    private Boolean mDoFullRebuildAfterOnActivityResult;
    /** Flag to indicate that a list has been successfully loaded. */
    private boolean mListHasBeenLoaded;
    /** Currently selected bookshelf. */
    @Nullable
    private Bookshelf mCurrentBookshelf;
    /** Stores the book id for the current list position, e.g. while a book is viewed/edited. */
    private long mCurrentPositionedBookId;
    /** Used by onScroll to detect when the top row has actually changed. */
    private int mLastTopRow = -1;
    /** Saved position of top row. */
    private int mTopRow;
    /**
     * Saved position of last top row offset from view top.
     * <p>
     * See {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}
     */
    private int mTopRowOffset;
    /** Preferred booklist state in next rebuild. */
    private int mRebuildState;
    /** Total number of books in current list. e.g. a book can be listed under 2 authors. */
    private int mTotalBooks;
    /** Total number of unique books in current list. */
    private int mUniqueBooks;
    /**
     * Listener for {@link GetBookListTask} results.
     */
    private final TaskListener<BuilderHolder> mOnGetBookListTaskListener =
            new TaskListener<BuilderHolder>() {
                @Override
                public void onTaskFinished(
                        @NonNull final TaskFinishedMessage<BuilderHolder> message) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                        Logger.debugEnter(this, "onTaskFinished", message);
                    }

                    // Save a flag to say list was loaded at least once successfully (or not)
                    mListHasBeenLoaded = message.wasSuccessful;

                    if (mListHasBeenLoaded) {
                        // always copy modified fields.
                        mCurrentPositionedBookId = message.result.currentPositionedBookId;
                        mRebuildState = message.result.rebuildState;

                        // always copy these results
                        mTotalBooks = message.result.resultTotalBooks;
                        mUniqueBooks = message.result.resultUniqueBooks;

                        // do not copy the result.resultListCursor, as it might be null
                        // in which case we will use the old value
                    }

                    // always call back, even if there is no new list.
                    mBuilderResult.setValue(message.result);
                }
            };
    /** Current displayed list cursor. */
    @Nullable
    private BooklistPseudoCursor mCursor;

    @Override
    protected void onCleared() {

        if (mCursor != null) {
            mCursor.getBuilder().close();
            mCursor.close();
            mCursor = null;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACKED_CURSOR) {
            TrackedCursor.dumpCursors();
        }

        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args,
                     @Nullable final Bundle savedInstanceState) {

        if (mDb == null) {
            mDb = new DAO();

            if (args != null) {
                mSearchCriteria.from(args, true);
            }
        }

        Bundle currentArgs = savedInstanceState != null ? savedInstanceState : args;

        if (currentArgs == null) {
            // Get preferred booklist state to use from preferences;
            // always do this here in init, as the prefs might have changed anytime.
            mRebuildState = BooklistBuilder.getPreferredListRebuildState();

        } else {
            // Unless set by the caller, preserve state when rebuilding/recreating etc
            mRebuildState = currentArgs.getInt(BKEY_LIST_STATE,
                                               BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Restore list position on bookshelf
        mTopRow = prefs.getInt(PREF_BOB_TOP_ROW, 0);
        mTopRowOffset = prefs.getInt(PREF_BOB_TOP_ROW_OFFSET, 0);

        // Debug; makes list structures vary across calls to ensure code is correct...
        mCurrentPositionedBookId = -1;
    }

    /**
     * NEVER close this database.
     *
     * @return the DAO
     */
    public DAO getDb() {
        return mDb;
    }


    @NonNull
    public List<String> getBookshelfNameList() {
        return mBookshelfNameList;
    }

    /**
     * @param context Current context.
     *
     * @return the position that reflects the current bookshelf.
     */
    public int initBookshelfNameList(@NonNull final Context context) {
        mBookshelfNameList.clear();
        mBookshelfNameList.add(context.getString(R.string.bookshelf_all_books));
        // default to 'All Books'
        int currentPos = 0;
        // start at 1, as position 0 is 'All Books'
        int position = 1;

        for (Bookshelf bookshelf : mDb.getBookshelves()) {
            if (bookshelf.getId() == getCurrentBookshelf().getId()) {
                currentPos = position;
            }
            position++;
            mBookshelfNameList.add(bookshelf.getName());
        }

        return currentPos;
    }

    /**
     * Load and set the desired Bookshelf as the preferred.
     *
     * @param context Current context
     * @param name    of desired Bookshelf
     */
    public void setCurrentBookshelf(@NonNull final Context context,
                                    @NonNull final String name) {

        mCurrentBookshelf = Bookshelf.getBookshelf(context, mDb, name, true);
        mCurrentBookshelf.setAsPreferred();
    }

    @NonNull
    public Bookshelf getCurrentBookshelf() {
        Objects.requireNonNull(mCurrentBookshelf);
        return mCurrentBookshelf;
    }

    /**
     * Load and set the desired Bookshelf; do NOT set it as the preferred.
     *
     * @param id of Bookshelf
     */
    public void setCurrentBookshelf(final long id) {
        mCurrentBookshelf = mDb.getBookshelf(id);
    }

    @NonNull
    public BooklistStyle getCurrentStyle() {
        Objects.requireNonNull(mCurrentBookshelf);
        return mCurrentBookshelf.getStyle(mDb);
    }

    public void setCurrentStyle(@NonNull final Context context,
                                @NonNull final Locale userLocale,
                                @NonNull final BooklistStyle style) {
        Objects.requireNonNull(mCurrentBookshelf);
        mCurrentBookshelf.setStyle(context, userLocale, mDb, style);
    }

    /**
     * Set the style and position.
     *
     * @param style    that was selected
     * @param topRow   the top row to store
     * @param listView used to derive the top row offset
     */
    public void onStyleChanged(@NonNull final Context context,
                               @NonNull final Locale userLocale,
                               @NonNull final BooklistStyle style,
                               final int topRow,
                               @NonNull final RecyclerView listView) {
        Objects.requireNonNull(mCurrentBookshelf);

        // save the new bookshelf/style combination
        mCurrentBookshelf.setAsPreferred();
        mCurrentBookshelf.setStyle(context, userLocale, mDb, style);

        // Set the rebuild state like this is the first time in, which it sort of is,
        // given we are changing style.
        mRebuildState = BooklistBuilder.getPreferredListRebuildState();

        /* There is very little ability to preserve position when going from
         * a list sorted by author/series to on sorted by unread/addedDate/publisher.
         * Keeping the current row/pos is probably the most useful thing we can
         * do since we *may* come back to a similar list.
         */
        savePosition(topRow, listView);
    }

    public int getTopRow() {
        return mTopRow;
    }

    public int getTopRowOffset() {
        return mTopRowOffset;
    }

    public int getLastTopRow() {
        return mLastTopRow;
    }

    public void setLastTopRow(final int lastTopRow) {
        mLastTopRow = lastTopRow;
    }

    /**
     * Save current position information in the preferences, including view nodes that are expanded.
     * We do this to preserve this data across application shutdown/startup.
     *
     * <p>
     * ENHANCE: Handle positions a little better when books are deleted.
     * <p>
     * Deleting a book by 'n' authors from the last author in list results in the list decreasing
     * in length by, potentially, n*2 items. The current code will return to the old position
     * in the list after such an operation...which will be too far down.
     *
     * @param topRow   the position of the top visible row in the list
     * @param listView used to derive the top row offset
     */
    public void savePosition(final int topRow,
                             @NonNull final RecyclerView listView) {
        if (mListHasBeenLoaded) {
            mTopRow = topRow;
            View topView = listView.getChildAt(0);
            if (topView != null) {
                mTopRowOffset = topView.getTop();
            } else {
                mTopRowOffset = 0;
            }

            Context context = listView.getContext();
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                             .putInt(PREF_BOB_TOP_ROW, mTopRow)
                             .putInt(PREF_BOB_TOP_ROW_OFFSET, mTopRowOffset)
                             .apply();
        }
    }

    /**
     * Queue a rebuild of the underlying cursor and data.
     *
     * @param context       Current context
     * @param isFullRebuild Indicates whole table structure needs rebuild,
     *                      versus just do a reselect of underlying data
     */
    public void initBookList(@NonNull final Context context,
                             final boolean isFullRebuild) {
        Objects.requireNonNull(mCurrentBookshelf);

        Locale locale = LocaleUtils.getLocale(context);

        BooklistBuilder blb;

        if (mCursor != null && !isFullRebuild) {
            // use the current builder to re-query the underlying data
            blb = mCursor.getBuilder();

        } else {
            BooklistStyle style = mCurrentBookshelf.getStyle(mDb);

            // get a new builder and add the required extra domains
            blb = new BooklistBuilder(context, style);

            // Title for displaying
            blb.requireDomain(DBDefinitions.DOM_TITLE,
                              DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_TITLE),
                              false);

            // Title for sorting
            blb.requireDomain(DBDefinitions.DOM_TITLE_OB,
                              DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_TITLE_OB),
                              true);

            blb.requireDomain(DBDefinitions.DOM_BOOK_READ,
                              DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_READ),
                              false);

            // external site ID's
            blb.requireDomain(DBDefinitions.DOM_BOOK_ISFDB_ID,
                              DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_ISFDB_ID),
                              false);
            blb.requireDomain(DBDefinitions.DOM_BOOK_GOODREADS_ID,
                              DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_GOODREADS_ID),
                              false);
            blb.requireDomain(DBDefinitions.DOM_BOOK_LIBRARY_THING_ID,
                              DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_LIBRARY_THING_ID),
                              false);
            blb.requireDomain(DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID,
                              DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_OPEN_LIBRARY_ID),
                              false);

            /*
             * URGENT: experimental... {@link BooklistStyle#extrasByTask()}.
             * When set to false, "extra" field bookshelves (if selected) will not be populated.
             * Depending on the device speed, and user habits, BOTH methods can be advantageous.
             * ==> now a preference <strong>per style</strong>
             */
            if (!style.extrasByTask()) {
                //ENHANCE:  see DAO#fetchBookExtrasById ... this needs work.
//                if (style.isUsed(DBDefinitions.KEY_BOOKSHELF)) {
//                    blb.requireDomain(DBDefinitions.DOM_BOOKSHELF_CSV,
//                                   "GROUP_CONCAT("
//                                   + DBDefinitions.TBL_BOOKSHELF.dot(DBDefinitions.DOM_BOOKSHELF)
//                                   + ",', ')",
//                                   false);
//                    blb.requireJoin(DBDefinitions.TBL_BOOKSHELF);
//                }

                if (style.isUsed(DBDefinitions.KEY_AUTHOR_FORMATTED)) {
                    blb.requireDomain(DBDefinitions.DOM_AUTHOR_FORMATTED,
                                      style.showAuthorGivenNameFirst(context)
                                      ? DAO.SqlColumns.EXP_AUTHOR_FORMATTED_GIVEN_SPACE_FAMILY
                                      : DAO.SqlColumns.EXP_AUTHOR_FORMATTED_FAMILY_COMMA_GIVEN,
                                      false);
                }
                if (style.isUsed(DBDefinitions.KEY_PUBLISHER)) {
                    blb.requireDomain(DBDefinitions.DOM_BOOK_PUBLISHER,
                                      DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_PUBLISHER),
                                      false);
                }
                if (style.isUsed(DBDefinitions.KEY_DATE_PUBLISHED)) {
                    blb.requireDomain(DBDefinitions.DOM_BOOK_DATE_PUBLISHED,
                                      DBDefinitions.TBL_BOOKS.dot(
                                              DBDefinitions.DOM_BOOK_DATE_PUBLISHED),
                                      false);
                }
                if (style.isUsed(DBDefinitions.KEY_ISBN)) {
                    blb.requireDomain(DBDefinitions.DOM_BOOK_ISBN,
                                      DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_ISBN),
                                      false);
                }
                if (style.isUsed(DBDefinitions.KEY_FORMAT)) {
                    blb.requireDomain(DBDefinitions.DOM_BOOK_FORMAT,
                                      DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_FORMAT),
                                      false);
                }
                if (style.isUsed(DBDefinitions.KEY_LOCATION)) {
                    blb.requireDomain(DBDefinitions.DOM_BOOK_LOCATION,
                                      DBDefinitions.TBL_BOOKS.dot(DBDefinitions.DOM_BOOK_LOCATION),
                                      false);
                }
            }

            // if we have a list of ID's, ignore other criteria.
            if (mSearchCriteria.hasIdList()) {
                blb.setFilterOnBookIdList(mSearchCriteria.bookList);

            } else {
                // always limit to the current bookshelf.
                blb.setFilterOnBookshelfId(mCurrentBookshelf.getId());

                // Criteria supported by FTS
                blb.setFilter(locale,
                              mSearchCriteria.ftsAuthor,
                              mSearchCriteria.ftsTitle,
                              mSearchCriteria.ftsKeywords);

                // non-FTS
                blb.setFilterOnSeriesName(mSearchCriteria.series);
                blb.setFilterOnLoanedToPerson(mSearchCriteria.loanee);
            }
        }

        new GetBookListTask(blb, isFullRebuild,
                            mCursor, mCurrentPositionedBookId, mRebuildState,
                            mOnGetBookListTaskListener)
                .execute();
    }

    public int getTotalBooks() {
        return mTotalBooks;
    }

    public int getUniqueBooks() {
        return mUniqueBooks;
    }

    /**
     * The result of {@link GetBookListTask}.
     *
     * @return a BuilderHolder with result fields populated.
     */
    public MutableLiveData<BuilderHolder> getBuilderResult() {
        return mBuilderResult;
    }

    @Nullable
    public BooklistPseudoCursor getListCursor() {
        return mCursor;
    }

    public void setListCursor(@NonNull final BooklistPseudoCursor listCursor) {
        mCursor = listCursor;
    }

    /** Convenience method. */
    @Nullable
    public BooklistBuilder getBuilder() {
        return mCursor != null ? mCursor.getBuilder() : null;
    }

    /**
     * Check if, and which type of, rebuild is needed.
     * <ul>Returns:
     * <li>{@code null} if no rebuild is requested</li>
     * <li>{@code true} if we need a full rebuild.</li>
     * <li>{@code false} if we need a partial rebuild.</li>
     * </ul>
     *
     * @return rebuild type needed
     */
    @Nullable
    public Boolean isForceFullRebuild() {
        return mDoFullRebuildAfterOnActivityResult;
    }

    public boolean isListLoaded() {
        return mListHasBeenLoaded;
    }

    /**
     * Request a full or partial rebuild at the next onResume.
     *
     * @param fullRebuild {@code true} for a full rebuild; {@code false} for a partial rebuild;
     *                    {@code null} for no rebuild.
     */
    public void setFullRebuild(@Nullable final Boolean fullRebuild) {
        mDoFullRebuildAfterOnActivityResult = fullRebuild;
    }

    public void setCurrentPositionedBookId(final long currentPositionedBookId) {
        mCurrentPositionedBookId = currentPositionedBookId;
    }

    /**
     * Check if this book is lend out, or not.
     *
     * @param mapper cursor row with book data
     *
     * @return {@code true} if this book is available for lending.
     */
    public boolean isAvailable(@NonNull final CursorMapper mapper) {
        String loanee;
        if (mapper.contains(DBDefinitions.KEY_LOANEE)) {
            loanee = mapper.getString(DBDefinitions.KEY_LOANEE);
        } else {
            loanee = mDb.getLoaneeByBookId(mapper.getLong(DBDefinitions.KEY_FK_BOOK));
        }
        return (loanee == null) || loanee.isEmpty();
    }

    /**
     * Return the 'human readable' version of the name (e.g. 'Isaac Asimov').
     *
     * @param mapper cursor row with book data
     *
     * @return formatted Author name
     */
    @Nullable
    public String getAuthorFromRow(@NonNull final CursorMapper mapper) {
        if (mapper.contains(DBDefinitions.KEY_FK_AUTHOR)
            && mapper.getLong(DBDefinitions.KEY_FK_AUTHOR) > 0) {
            Author author = mDb.getAuthor(mapper.getLong(DBDefinitions.KEY_FK_AUTHOR));
            if (author != null) {
                return author.getLabel();
            }

        } else if (mapper.getInt(DBDefinitions.KEY_BL_NODE_ROW_KIND)
                   == BooklistGroup.RowKind.BOOK) {
            List<Author> authors = mDb.getAuthorsByBookId(
                    mapper.getLong(DBDefinitions.KEY_FK_BOOK));
            if (!authors.isEmpty()) {
                return authors.get(0).getLabel();
            }
        }
        return null;
    }

    /**
     * Get the Series name.
     *
     * @param mapper cursor row with book data
     *
     * @return the unformatted Series name (i.e. without the number)
     */
    @Nullable
    public String getSeriesFromRow(@NonNull final CursorMapper mapper) {
        if (mapper.contains(DBDefinitions.KEY_FK_SERIES)
            && mapper.getLong(DBDefinitions.KEY_FK_SERIES) > 0) {
            Series series = mDb.getSeries(mapper.getLong(DBDefinitions.KEY_FK_SERIES));
            if (series != null) {
                return series.getTitle();
            }
        } else if (mapper.getInt(DBDefinitions.KEY_BL_NODE_ROW_KIND)
                   == BooklistGroup.RowKind.BOOK) {
            ArrayList<Series> series =
                    mDb.getSeriesByBookId(mapper.getLong(DBDefinitions.KEY_FK_BOOK));
            if (!series.isEmpty()) {
                return series.get(0).getTitle();
            }
        }
        return null;
    }


    @NonNull
    public SearchCriteria getSearchCriteria() {
        return mSearchCriteria;
    }

    @NonNull
    public ArrayList<Long> getCurrentBookIdList() {
        //noinspection ConstantConditions
        return mCursor.getBuilder().getCurrentBookIdList();
    }

    public void restoreCurrentBookshelf(@NonNull final Context context) {
        mCurrentBookshelf = Bookshelf.getBookshelf(context, mDb, true);
    }

    public boolean reloadCurrentBookshelf(@NonNull final Context context) {
        Bookshelf newBookshelf = Bookshelf.getBookshelf(context, mDb, true);
        if (!newBookshelf.equals(mCurrentBookshelf)) {
            // if it was.. switch to it.
            mCurrentBookshelf = newBookshelf;
            return true;
        }
        return false;
    }

    public MutableLiveData<Object> getUserMessage() {
        return mUserMessage;
    }

    public MutableLiveData<Boolean> getNeedsGoodreads() {
        return mNeedsGoodreads;
    }

    public TaskListener<Integer> getGoodreadsTaskListener() {
        if (mOnGoodreadsTaskListener == null) {
            mOnGoodreadsTaskListener = new TaskListener<Integer>() {

                @Override
                public void onTaskFinished(@NonNull final TaskFinishedMessage<Integer> message) {
                    String msg = GoodreadsTasks.handleResult(App.getLocalizedAppContext(), message);
                    if (msg != null) {
                        mUserMessage.setValue(msg);
                    } else {
                        // Need authorization
                        mNeedsGoodreads.setValue(true);
                    }
                }

                @Override
                public void onTaskCancelled(@NonNull final TaskFinishedMessage<Integer> message) {
                    mUserMessage.setValue(R.string.progress_end_cancelled);
                }

                @Override
                public void onTaskProgress(@NonNull final TaskProgressMessage message) {
                    if (message.values != null && message.values.length > 0) {
                        mUserMessage.setValue(message.values[0]);
                    }
                }
            };
        }
        return mOnGoodreadsTaskListener;
    }

    public String debugBuilderTables() {
        if (mCursor != null) {
            return mCursor.getBuilder().debugInfoForTables();
        } else {
            return "no cursor";
        }
    }

    public boolean isReadOnly(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                                .getBoolean(Prefs.pk_bob_open_book_read_only, true);
    }

    /**
     * Holder class for search criteria with some methods to bulk manipulate them.
     */
    public static class SearchCriteria {

        /**
         * List of bookId's to display. The RESULT of a search with {@link FTSSearchActivity}
         * which can be re-used for the builder.
         */
        @Nullable
        ArrayList<Long> bookList;

        /**
         * Author to use in FTS search query.
         * Supported in the builder and {@link FTSSearchActivity}.
         */
        @Nullable
        String ftsAuthor;

        /**
         * Title to use in FTS search query.
         * Supported in the builder and {@link FTSSearchActivity}.
         */
        @Nullable
        String ftsTitle;

        /**
         * Series to use in search query.
         * Supported in the builder, but not user-settable yet.
         */
        @Nullable
        String series;

        /**
         * Name of the person we loaned books to, to use in search query.
         * Supported in the builder, but not user-settable yet.
         */
        @Nullable
        String loanee;

        /**
         * Keywords to use in FTS search query.
         * Supported in the builder and {@link FTSSearchActivity}.
         * <p>
         * Always use the setter as we need to intercept the "." character.
         */
        @Nullable
        private String ftsKeywords;

        public void clear() {
            ftsKeywords = null;
            ftsAuthor = null;
            ftsTitle = null;

            series = null;
            loanee = null;

            bookList = null;
        }

        /**
         * Get a single string with all search words, for displaying.
         *
         * @return csv string, can be empty, never {@code null}.
         */
        @NonNull
        public String getDisplayString() {
            List<String> list = new ArrayList<>();

            if (ftsAuthor != null && !ftsAuthor.isEmpty()) {
                list.add(ftsAuthor);
            }
            if (ftsTitle != null && !ftsTitle.isEmpty()) {
                list.add(ftsTitle);
            }
            if (ftsKeywords != null && !ftsKeywords.isEmpty()) {
                list.add(ftsKeywords);
            }
            return TextUtils.join(",", list);
        }

        public void setKeywords(@Nullable final String keywords) {
            if (keywords == null || keywords.isEmpty() || ".".equals(keywords)) {
                ftsKeywords = null;
            } else {
                ftsKeywords = keywords;
            }
        }

        /**
         * Only copies the criteria which are set.
         * Criteria not set in the bundle, are preserved!
         *
         * @param bundle     with criteria.
         * @param clearFirst Flag to force clearing all before loading the new criteria
         */
        public void from(@NonNull final Bundle bundle,
                         final boolean clearFirst) {
            if (clearFirst) {
                clear();
            }

            if (bundle.containsKey(UniqueId.BKEY_SEARCH_TEXT)) {
                setKeywords(bundle.getString(UniqueId.BKEY_SEARCH_TEXT));
            }
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_AUTHOR)) {
                ftsAuthor = bundle.getString(UniqueId.BKEY_SEARCH_AUTHOR);
            }
            if (bundle.containsKey(UniqueId.BKEY_SEARCH_TITLE)) {
                ftsTitle = bundle.getString(UniqueId.BKEY_SEARCH_TITLE);
            }

            if (bundle.containsKey(DBDefinitions.KEY_SERIES_TITLE)) {
                series = bundle.getString(DBDefinitions.KEY_SERIES_TITLE);
            }
            if (bundle.containsKey(DBDefinitions.KEY_LOANEE)) {
                loanee = bundle.getString(DBDefinitions.KEY_LOANEE);
            }
            if (bundle.containsKey(UniqueId.BKEY_ID_LIST)) {
                //noinspection unchecked
                bookList = (ArrayList<Long>) bundle.getSerializable(UniqueId.BKEY_ID_LIST);
            }
        }

        /**
         * Put the search criteria as extras in the Intent.
         *
         * @param intent which will be used for a #startActivityForResult call
         */
        public void to(@NonNull final Intent intent) {
            intent.putExtra(UniqueId.BKEY_SEARCH_TEXT, ftsKeywords)
                  .putExtra(UniqueId.BKEY_SEARCH_AUTHOR, ftsAuthor)
                  .putExtra(UniqueId.BKEY_SEARCH_TITLE, ftsTitle)

                  .putExtra(DBDefinitions.KEY_SERIES_TITLE, series)
                  .putExtra(DBDefinitions.KEY_LOANEE, loanee)

                  .putExtra(UniqueId.BKEY_ID_LIST, bookList);
        }

        public boolean isEmpty() {
            return (ftsKeywords == null || ftsKeywords.isEmpty())
                   && (ftsAuthor == null || ftsAuthor.isEmpty())
                   && (ftsTitle == null || ftsTitle.isEmpty())
                   && (series == null || series.isEmpty())
                   && (loanee == null || loanee.isEmpty())
                   && (bookList == null || bookList.isEmpty());
        }

        boolean hasIdList() {
            return bookList != null && !bookList.isEmpty();
        }
    }

    /**
     * Background task to build and retrieve the list of books based on current settings.
     */
    private static class GetBookListTask
            extends TaskBase<BuilderHolder> {

        /**
         * Indicates whole table structure needs rebuild,
         * versus just do a reselect of underlying data.
         */
        private final boolean mIsFullRebuild;
        /** the builder. */
        @NonNull
        private final BooklistBuilder mBooklistBuilder;
        /** Holds the input/output and output-only fields to be returned to the activity. */
        @NonNull
        private final BuilderHolder mHolder;
        @Nullable
        private final BooklistPseudoCursor mCurrentListCursor;
        /** Resulting Cursor. */
        private BooklistPseudoCursor tempListCursor;


        /**
         * Constructor.
         *
         * @param bookListBuilder         the builder
         * @param isFullRebuild           Indicates whole table structure needs rebuild,
         * @param currentListCursor       Current displayed list cursor.
         * @param currentPositionedBookId Current position in the list.
         * @param rebuildState            Requested list state (expanded,collapsed, preserved)
         * @param taskListener            TaskListener
         */
        @UiThread
        GetBookListTask(@NonNull final BooklistBuilder bookListBuilder,
                        final boolean isFullRebuild,
                        @Nullable final BooklistPseudoCursor currentListCursor,
                        final long currentPositionedBookId,
                        final int rebuildState,
                        final TaskListener<BuilderHolder> taskListener) {
            super(R.id.TASK_ID_GET_BOOKLIST, taskListener);

            mBooklistBuilder = bookListBuilder;
            mIsFullRebuild = isFullRebuild;
            mCurrentListCursor = currentListCursor;

            // input/output fields for the task.
            mHolder = new BuilderHolder(currentPositionedBookId, rebuildState);
        }

        /**
         * Try to sync the previously selected book ID.
         *
         * @return the target rows, or {@code null} if none.
         */
        private ArrayList<BooklistBuilder.BookRowInfo> syncPreviouslySelectedBookId() {
            // no input, no output...
            if (mHolder.currentPositionedBookId == 0) {
                return null;
            }

            // get all positions of the book
            ArrayList<BooklistBuilder.BookRowInfo> rows =
                    mBooklistBuilder.getBookAbsolutePositions(mHolder.currentPositionedBookId);

            if (rows != null && !rows.isEmpty()) {
                // First, get the ones that are currently visible...
                ArrayList<BooklistBuilder.BookRowInfo> visibleRows = new ArrayList<>();
                for (BooklistBuilder.BookRowInfo rowInfo : rows) {
                    if (rowInfo.visible) {
                        visibleRows.add(rowInfo);
                    }
                }

                // If we have any visible rows, only consider those for the new position
                if (!visibleRows.isEmpty()) {
                    rows = visibleRows;
                } else {
                    // Make them all visible
                    for (BooklistBuilder.BookRowInfo rowInfo : rows) {
                        if (!rowInfo.visible) {
                            mBooklistBuilder.ensureAbsolutePositionVisible(
                                    rowInfo.absolutePosition);
                        }
                    }
                    // Recalculate all positions
                    for (BooklistBuilder.BookRowInfo rowInfo : rows) {
                        rowInfo.listPosition = mBooklistBuilder.getPosition(
                                rowInfo.absolutePosition);
                    }
                }
                // Find the nearest row to the recorded 'top' row.
//                        int targetRow = bookRows[0];
//                        int minDist = Math.abs(mModel.getTopRow() - b.getPosition(targetRow));
//                        for (int i = 1; i < bookRows.length; i++) {
//                            int pos = b.getPosition(bookRows[i]);
//                            int dist = Math.abs(mModel.getTopRow() - pos);
//                            if (dist < minDist) {
//                                targetRow = bookRows[i];
//                            }
//                        }
//                        // Make sure the target row is visible/expanded.
//                        b.ensureAbsolutePositionVisible(targetRow);
//                        // Now find the position it will occupy in the view
//                        mTargetPos = b.getPosition(targetRow);
            }
            return rows;
        }

        @Override
        @NonNull
        @WorkerThread
        protected BuilderHolder doInBackground(final Void... params) {
            Thread.currentThread().setName("GetBookListTask");
            try {
                long t0;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t0 = System.nanoTime();
                }
                // Build the underlying data
                if (mCurrentListCursor != null && !mIsFullRebuild) {
                    mBooklistBuilder.rebuild();
                } else {
                    mBooklistBuilder.build(mHolder.rebuildState, mHolder.currentPositionedBookId);
                    // After first build, always preserve this object state
                    mHolder.rebuildState = BooklistBuilder.PREF_LIST_REBUILD_STATE_PRESERVED;
                }

                if (isCancelled()) {
                    return mHolder;
                }

                long t1;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t1 = System.nanoTime();
                }

                mHolder.resultTargetRows = syncPreviouslySelectedBookId();

                if (isCancelled()) {
                    return mHolder;
                }

                long t2;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t2 = System.nanoTime();
                }

                // Now we have the expanded groups as needed, get the list cursor
                tempListCursor = mBooklistBuilder.getNewListCursor();

                // Clear it so it won't be reused.
                mHolder.currentPositionedBookId = 0;

                long t3;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t3 = System.nanoTime();
                }
                // get a count() from the cursor in background task because the setAdapter() call
                // will do a count() and potentially block the UI thread while it pages through the
                // entire cursor. If we do it here, subsequent calls will be fast.
                int count = tempListCursor.getCount();

                long t4;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t4 = System.nanoTime();
                }
                // pre-fetch this count
                mHolder.resultUniqueBooks = tempListCursor.getBuilder().getUniqueBookCount();

                if (isCancelled()) {
                    return mHolder;
                }

                long t5;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t5 = System.nanoTime();
                }
                // pre-fetch this count
                mHolder.resultTotalBooks = tempListCursor.getBuilder().getBookCount();

                long t6;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    t6 = System.nanoTime();
                }

                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TIMERS) {
                    Logger.debug(this, "doInBackground",
                                 "\n Build: " + (t1 - t0),
                                 "\n Position: " + (t2 - t1),
                                 "\n Select: " + (t3 - t2),
                                 "\n Count(" + count + "): " + (t4 - t3)
                                 + '/' + (t5 - t4) + '/' + (t6 - t5),
                                 "\n ====== ",
                                 "\n Total time: " + (t6 - t0) + "nano");
                }

                if (isCancelled()) {
                    return mHolder;
                }

                // Set the results.
                mHolder.resultListCursor = tempListCursor;

            } catch (@NonNull final RuntimeException e) {
                Logger.error(this, e);
                mException = e;
                cleanup();
            }

            return mHolder;
        }

        @Override
        protected void onCancelled(@Nullable final BuilderHolder result) {
            cleanup();
            super.onCancelled(result);
        }

        @AnyThread
        private void cleanup() {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Logger.debug(this, "cleanup",
                             "exception=" + mException);
            }
            if (tempListCursor != null && tempListCursor != mCurrentListCursor) {
                if (mCurrentListCursor == null
                    || (!tempListCursor.getBuilder().equals(mCurrentListCursor.getBuilder()))) {
                    tempListCursor.getBuilder().close();
                }
                tempListCursor.close();
            }
            tempListCursor = null;
        }
    }

    /** value class for the Builder. */
    public static class BuilderHolder {

        /** input/output field. */
        long currentPositionedBookId;
        /** input/output field. */
        int rebuildState;

        /**
         * Resulting Cursor; can be {@code null} if the list did not get build.
         */
        @Nullable
        BooklistPseudoCursor resultListCursor;

        /**
         * output field. Pre-fetched from the resultListCursor's builder.
         * {@link BooklistBuilder#getBookCount()}
         * Should be ignored if resultListCursor is {@code null}
         */
        int resultTotalBooks;
        /**
         * output field. Pre-fetched from the resultListCursor's builder.
         * {@link BooklistBuilder#getUniqueBookCount()}
         * Should be ignored if resultListCursor is {@code null}
         */
        int resultUniqueBooks;

        /**
         * output field. Used to determine new cursor position; can be {@code null}.
         * Should be ignored if resultListCursor is {@code null}
         */
        @Nullable
        ArrayList<BooklistBuilder.BookRowInfo> resultTargetRows;

        /**
         * Constructor: these are the fields we need as input.
         */
        BuilderHolder(final long currentPositionedBookId,
                      final int rebuildState) {
            this.currentPositionedBookId = currentPositionedBookId;
            this.rebuildState = rebuildState;
        }

        @Nullable
        public BooklistPseudoCursor getResultListCursor() {
            return resultListCursor;
        }

        @Nullable
        public ArrayList<BooklistBuilder.BookRowInfo> getResultTargetRows() {
            return resultTargetRows;
        }

        @Override
        @NonNull
        public String toString() {
            return "BuilderHolder{"
                   + ", currentPositionedBookId=" + currentPositionedBookId
                   + ", rebuildState=" + rebuildState
                   + ", resultTotalBooks=" + resultTotalBooks
                   + ", resultUniqueBooks=" + resultUniqueBooks
                   + ", resultListCursor=" + resultListCursor
                   + ", resultTargetRows=" + resultTargetRows
                   + '}';
        }
    }
}
