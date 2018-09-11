/*
 * @copyright 2011 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue;

import android.database.Cursor;
import android.os.Bundle;
import com.eleybourn.bookcatalogue.database.ColumnInfo;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.utils.*;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class to update all thumbnails (and some other data) in a background thread.
 *
 * @author Philip Warner
 */
public class UpdateThumbnailsThread extends ManagedTask {
    // The fields that the user requested to update
    private final FieldUsages mRequestedFields;

    // Lock help by pop and by push when an item was added to an empty stack.
    private final ReentrantLock mSearchLock = new ReentrantLock();
    // Signal for available items
    private final Condition mSearchDone = mSearchLock.newCondition();
    // Active search manager
    private final SearchManager mSearchManager;
    private String mFinalMessage;
    // Data related to current row being processed
    // - Original row data
    private Bundle mOrigData = null;
    // - current book ID
    private long mCurrId = 0;
    // - current book UUID
    private String mCurrUuid = null;
    // - The (subset) of fields relevant to the current book
    private FieldUsages mCurrFieldUsages;
    // DB connection
    private CatalogueDBAdapter mDb;

    /**
     * Constructor.
     *
     * @param manager         Object to manage background tasks
     * @param requestedFields fields to update
     */
    public UpdateThumbnailsThread(TaskManager manager, FieldUsages requestedFields, TaskListener listener) {
        super(manager);
        mDb = new CatalogueDBAdapter(BookCatalogueApp.getAppContext());
        mDb.open();

        mRequestedFields = requestedFields;
        SearchManager.SearchListener mSearchListener = new SearchManager.SearchListener() {

            @Override
            public boolean onSearchFinished(Bundle bookData, boolean cancelled) {
                return handleSearchFinished(bookData, cancelled);
            }
        };
        mSearchManager = new SearchManager(mManager, mSearchListener);
        mManager.doProgress(BookCatalogueApp.getResourceString(R.string.starting_search));
        getMessageSwitch().addListener(getSenderId(), listener, false);
    }

    private static <T extends Serializable> void combineArrays(String key, Bundle origData, Bundle newData) {
        // Each of the lists to combine
        ArrayList<T> origList = null;
        ArrayList<T> newList = null;
        // Get the list from the original, if present.
        if (origData.containsKey(key)) {
            origList = ArrayUtils.getListFromBundle(origData, key);
        }
        // Otherwise an empty list
        if (origList == null)
            origList = new ArrayList<>();

        // Get from the new data
        if (newData.containsKey(key)) {
            newList = ArrayUtils.getListFromBundle(newData, key);
        }
        if (newList == null)
            newList = new ArrayList<>();
        origList.addAll(newList);
        // Save combined version to the new data
        newData.putSerializable(key, origList);
    }

    @Override
    public void onRun() throws InterruptedException {
        int counter = 0;
        /* Test write to the SDCard; abort if not writable */
        if (!StorageUtils.sdCardWritable()) {
            mFinalMessage = getString(R.string.thumbnail_failed_sdcard);
            return;
        }

        // ENHANCE: Allow caller to pass cursor (again) so that specific books can be updated (eg. just one book)
        try (Cursor books = mDb.fetchAllBooks("b." + ColumnInfo.KEY_ID, "", "", "", "", "", "")) {
            mManager.setMax(this, books.getCount());
            while (books.moveToNext() && !isCancelled()) {
                // Increment the progress counter
                counter++;

                // Copy the fields from the cursor and build a complete set of data for this book.
                // This only needs to include data that we can fetch (so, for example, bookshelves are ignored).
                mOrigData = new Bundle();
                for (int i = 0; i < books.getColumnCount(); i++) {
                    mOrigData.putString(books.getColumnName(i), books.getString(i));
                }
                // Get the book ID
                mCurrId = Convert.getAsLong(mOrigData, ColumnInfo.KEY_ID);
                // Get the book UUID
                mCurrUuid = mOrigData.getString(DatabaseDefinitions.DOM_BOOK_UUID.name);
                // Get the extra data about the book
                mOrigData.putSerializable(ColumnInfo.KEY_AUTHOR_ARRAY, mDb.getBookAuthorList(mCurrId));
                mOrigData.putSerializable(ColumnInfo.KEY_SERIES_ARRAY, mDb.getBookSeriesList(mCurrId));

                // Grab the searchable fields. Ideally we will have an ISBN but we may not.
                String isbn = mOrigData.getString(ColumnInfo.KEY_ISBN);
                // Make sure ISBN is not NULL (legacy data, and possibly set to null when adding new book)
                if (isbn == null)
                    isbn = "";
                String author = mOrigData.getString(ColumnInfo.KEY_AUTHOR_FORMATTED);
                String title = mOrigData.getString(ColumnInfo.KEY_TITLE);

                // Reset the fields we want for THIS book
                mCurrFieldUsages = new FieldUsages();

                // See if there is a reason to fetch ANY data by checking which fields this book needs.
                for (FieldUsage usage : mRequestedFields.values()) {
                    // Not selected, we dont want it
                    if (usage.selected) {
                        switch (usage.usage) {
                            case ADD_EXTRA:
                            case OVERWRITE:
                                // Add and Overwrite mean we always get the data
                                mCurrFieldUsages.put(usage);
                                break;
                            case COPY_IF_BLANK:
                                // Handle special cases
                                // - If it's a thumbnail, then see if it's missing or empty.
                                switch (usage.fieldName) {
                                    case ColumnInfo.KEY_THUMBNAIL:
                                        File file = ImageUtils.fetchThumbnailByUuid(mCurrUuid);
                                        if (!file.exists() || file.length() == 0)
                                            mCurrFieldUsages.put(usage);
                                        break;
                                    case ColumnInfo.KEY_AUTHOR_ARRAY:
                                        // We should never have a book with no authors, but lets be paranoid
                                        if (mOrigData.containsKey(usage.fieldName)) {
                                            ArrayList<Author> origAuthors = ArrayUtils.getAuthorsFromBundle(mOrigData);
                                            if (origAuthors == null || origAuthors.size() == 0)
                                                mCurrFieldUsages.put(usage);
                                        }
                                        break;
                                    case ColumnInfo.KEY_SERIES_ARRAY:
                                        if (mOrigData.containsKey(usage.fieldName)) {
                                            ArrayList<Series> origSeries = ArrayUtils.getSeriesFromBundle(mOrigData);
                                            if (origSeries == null || origSeries.size() == 0)
                                                mCurrFieldUsages.put(usage);
                                        }
                                        break;
                                    default:
                                        // If the original was blank, add to list
                                        if (!mOrigData.containsKey(usage.fieldName)
                                                || mOrigData.getString(usage.fieldName) == null
                                                || mOrigData.getString(usage.fieldName).isEmpty())
                                            mCurrFieldUsages.put(usage);
                                        break;
                                }
                                break;
                        }
                    }
                }

                // Cache the value to indicate we need thumbnails (or not).
                boolean tmpThumbWanted = mCurrFieldUsages.containsKey(ColumnInfo.KEY_THUMBNAIL);

                if (tmpThumbWanted) {
                    // delete any temporary thumbnails //
                    try {
                        File delthumb = ImageUtils.getTempThumbnail();
                        //noinspection ResultOfMethodCallIgnored
                        delthumb.delete();
                    } catch (Exception e) {
                        // do nothing - this is the expected behaviour
                    }
                }

                // Use this to flag if we actually need a search.
                boolean wantSearch = false;
                // Update the progress appropriately
                if (mCurrFieldUsages.size() == 0 || isbn.isEmpty() && (author.isEmpty() || title.isEmpty())) {
                    mManager.doProgress(String.format(getString(R.string.skip_title), title));
                } else {
                    wantSearch = true;
                    if (!title.isEmpty())
                        mManager.doProgress(title);
                    else
                        mManager.doProgress(isbn);
                }
                mManager.doProgress(this, null, counter);

                // Start searching if we need it, then wait...
                if (wantSearch) {
                    // TODO: Allow user-selection of search sources
                    mSearchManager.search(author, title, isbn, tmpThumbWanted, SearchManager.SEARCH_ALL);
                    // Wait for the search to complete; when the search has completed it uses class-level state
                    // data when processing the results. It will signal this lock when it no longer needs any class
                    // level state data (eg. mOrigData).
                    mSearchLock.lock();
                    try {
                        mSearchDone.await();
                    } finally {
                        mSearchLock.unlock();
                    }
                }
            }
        } finally {
            // Empty the progress.
            mManager.doProgress(null);
            // Make the final message
            mFinalMessage = String.format(getString(R.string.num_books_searched), "" + counter);
            if (isCancelled()) {
                mFinalMessage = String.format(BookCatalogueApp.getResourceString(R.string.cancelled_info), mFinalMessage);
            }
        }
    }

    @Override
    public void onThreadFinish() {
        try {
            mManager.doToast(mFinalMessage);
        } finally {
            cleanup();
        }
    }

    /**
     * Called in the main thread for this object when a search has completed.
     */
    @SuppressWarnings("SameReturnValue")
    private boolean handleSearchFinished(Bundle bookData, boolean cancelled) {
        if (BuildConfig.DEBUG) {
            System.out.println("onSearchFinished (cancel = " + cancelled + ")");
        }

        // Set cancelled flag if the task was cancelled
        if (cancelled) {
            cancelTask();
        } else if (bookData == null) {
            mManager.doToast("Unable to find book details");
        }

        // Save the local data from the context so we can start a new search
        long rowId = mCurrId;
        Bundle origData = mOrigData;
        FieldUsages requestedFields = mCurrFieldUsages;

        if (!isCancelled() && bookData != null)
            processSearchResults(rowId, mCurrUuid, requestedFields, bookData, origData);

        // Done! This need to go after processSearchResults() because doSearchDone() frees
        // main thread which may disconnect database connection if on last book.
        doSearchDone();

        return true;
    }

    /**
     * Passed the old & new data, construct the update data and perform the update.
     *
     * @param bookId   Book ID
     * @param newData  Data gathered from internet
     * @param origData Original data
     */
    private void processSearchResults(long bookId, String bookUuid, FieldUsages requestedFields, Bundle newData, Bundle origData) {
        // First, filter the data to remove keys we don't care about
        ArrayList<String> toRemove = new ArrayList<>();
        for (String key : newData.keySet()) {
            if (!requestedFields.containsKey(key) || !requestedFields.get(key).selected)
                toRemove.add(key);
        }
        for (String key : toRemove) {
            newData.remove(key);
        }

        // For each field, process it according the the usage.
        for (FieldUsage usage : requestedFields.values()) {
            if (newData.containsKey(usage.fieldName)) {
                // Handle thumbnail specially
                if (usage.fieldName.equals(ColumnInfo.KEY_THUMBNAIL)) {
                    File downloadedFile = ImageUtils.getTempThumbnail();
                    boolean copyThumb = false;
                    if (usage.usage == FieldUsages.Usages.COPY_IF_BLANK) {
                        File file = ImageUtils.fetchThumbnailByUuid(bookUuid);
                        copyThumb = (!file.exists() || file.length() == 0);
                    } else if (usage.usage == FieldUsages.Usages.OVERWRITE) {
                        copyThumb = true;
                    }
                    if (copyThumb) {
                        File file = ImageUtils.fetchThumbnailByUuid(bookUuid);
                        //noinspection ResultOfMethodCallIgnored
                        downloadedFile.renameTo(file);
                    } else {
                        //noinspection ResultOfMethodCallIgnored
                        downloadedFile.delete();
                    }
                } else {
                    switch (usage.usage) {
                        case OVERWRITE:
                            // Nothing to do; just use new data
                            break;
                        case COPY_IF_BLANK:
                            // Handle special cases
                            switch (usage.fieldName) {
                                case ColumnInfo.KEY_AUTHOR_ARRAY:
                                    if (origData.containsKey(usage.fieldName)) {
                                        ArrayList<Author> origAuthors = ArrayUtils.getAuthorsFromBundle(origData);
                                        if (origAuthors != null && origAuthors.size() > 0)
                                            newData.remove(usage.fieldName);
                                    }
                                    break;
                                case ColumnInfo.KEY_SERIES_ARRAY:
                                    if (origData.containsKey(usage.fieldName)) {
                                        ArrayList<Series> origSeries = ArrayUtils.getSeriesFromBundle(origData);
                                        if (origSeries != null && origSeries.size() > 0)
                                            newData.remove(usage.fieldName);
                                    }
                                    break;
                                default:
                                    // If the original was non-blank, erase from list
                                    if (origData.containsKey(usage.fieldName)
                                            && origData.getString(usage.fieldName) != null
                                            && !origData.getString(usage.fieldName).isEmpty()) {
                                        newData.remove(usage.fieldName);
                                    }
                                    break;
                            }
                            break;
                        case ADD_EXTRA:
                            // Handle arrays
                            switch (usage.fieldName) {
                                case ColumnInfo.KEY_AUTHOR_ARRAY:
                                    UpdateThumbnailsThread.<Author>combineArrays(usage.fieldName, origData, newData);
                                    break;
                                case ColumnInfo.KEY_SERIES_ARRAY:
                                    UpdateThumbnailsThread.<Series>combineArrays(usage.fieldName, origData, newData);
                                    break;
                                default:
                                    // No idea how to handle this for non-arrays
                                    throw new RuntimeException("Illegal usage '" + usage.usage + "' specified for field '" + usage.fieldName + "'");
                            }
                            break;
                    }

                }
            }
        }

        // Update
        if (!newData.isEmpty()) {
            mDb.updateBook(bookId, new BookData(newData), 0);
        }

    }

    /**
     * Called to signal that the search is complete AND the class-level data has
     * been cached by the processing thread, so that a new search can begin.
     */
    private void doSearchDone() {
        // Let another search begin
        mSearchLock.lock();
        try {
            mSearchDone.signal();
        } finally {
            mSearchLock.unlock();
        }
    }

    /**
     * Cleanup any DB connection etc after main task has run.
     */
    private void cleanup() {
        if (mDb != null) {
            mDb.close();
            mDb = null;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        cleanup();
        super.finalize();
    }

}
