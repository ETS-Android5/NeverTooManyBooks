/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.searches.goodreads;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.Toast;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BookData;
import com.eleybourn.bookcatalogue.BooksRow;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.entities.Series;
import com.eleybourn.bookcatalogue.searches.goodreads.api.ListReviewsApiHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;
import com.eleybourn.bookcatalogue.utils.StorageUtils;
import com.eleybourn.bookcatalogue.utils.Utils;

import com.eleybourn.bookcatalogue.taskqueue.QueueManager;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_DATE_ADDED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GOODREADS_BOOK_ID;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ListReviewsApiHandler.ListReviewsFieldNames.UPDATED;

/**
 * Import all a users 'reviews' from goodreads; a users 'reviews' consists of all the books that
 * they have placed on bookshelves, irrespective of whether they have rated or reviewed the book.
 *
 * @author Philip Warner
 */
class ImportAllTask extends GenericTask {
    private static final long serialVersionUID = -3535324410982827612L;
    /**
     * Number of books to retrieve in one batch; we are encouraged to make fewer API calls, so
     * setting this number high is good. 50 seems to take several seconds to retrieve, so it
     * was chosen.
     */
    private static final int BOOKS_PER_PAGE = 50;
    /** Date before which updates are irrelevant. Can be null, which implies all dates are included. */
    private final String mUpdatesAfter;
    /** Flag indicating this job is a sync job: on completion, it will start an export. */
    private final boolean mIsSync;
    /** Current position in entire list of reviews */
    private int mPosition;
    /** Total number of reviews user has */
    private int mTotalBooks;
    /** Flag indicating this is the first time *this* object instance has been called */
    private transient boolean mFirstCall = true;
    /** Date at which this job started downloading first page */
    private Date mStartDate = null;
    /** Lookup table of bookshelves defined currently and their goodreads canonical names */
    private transient Map<String, String> mBookshelfLookup = null;

    /**
     * Constructor
     */
    ImportAllTask(final boolean isSync) {
        super(BookCatalogueApp.getResourceString(R.string.import_all_from_goodreads));
        mPosition = 0;
        mIsSync = isSync;
        // If it's a sync job, then find date of last successful sync and only apply
        // records from after that date. If no other job, then get all.
        if (mIsSync) {
            Date lastSync = GoodreadsManager.getLastSyncDate();
            if (lastSync == null) {
                mUpdatesAfter = null;
            } else {
                mUpdatesAfter = DateUtils.toSqlDateTime(lastSync);
            }
        } else {
            mUpdatesAfter = null;
        }
    }

    /**
     * Do the actual work.
     */
    @Override
    public boolean run(@NonNull final QueueManager qMgr, @NonNull final Context context) {
        CatalogueDBAdapter db = new CatalogueDBAdapter(context);
        db.open();

        try {
            // Load the goodreads reviews
            boolean ok = processReviews(qMgr, db);
            // If it's a sync job, then start the 'send' part and save last syn date
            if (mIsSync) {
                GoodreadsManager.setLastSyncDate(mStartDate);
                QueueManager.getQueueManager().enqueueTask(new SendAllBooksTask(true), BCQueueManager.QUEUE_MAIN);
            }
            return ok;
        } catch (GoodreadsManager.Exceptions.NotAuthorizedException e) {
            Toast.makeText(context, R.string.goodreads_auth_failed, Toast.LENGTH_SHORT).show();
            throw new RuntimeException("Goodreads authorization failed");
        } finally {
            db.close();
        }
    }

    /**
     * Repeatedly request review pages until we are done.
     */
    private boolean processReviews(@NonNull final QueueManager qMgr,
                                   @NonNull final CatalogueDBAdapter db) throws GoodreadsManager.Exceptions.NotAuthorizedException {
        GoodreadsManager gr = new GoodreadsManager();
        ListReviewsApiHandler api = new ListReviewsApiHandler(gr);

        int currPage = (mPosition / BOOKS_PER_PAGE);
        while (true) {
            // page numbers are 1-based; start at 0 and increment at start of each loop
            currPage++;

            // In case of a restart, reset position to first in page
            mPosition = BOOKS_PER_PAGE * (currPage - 1);

            Bundle books;

            // Call the API, return false if failed.
            try {
                // If we have not started successfully yet, record the date at which the run() was called.
                // This date is used if the job is a sync job.
                Date runDate = null;
                if (mStartDate == null) {
                    runDate = new Date();
                }
                books = api.run(currPage, BOOKS_PER_PAGE);
                // If we succeeded, and this is the first time, save the date
                if (mStartDate == null) {
                    mStartDate = runDate;
                }
            } catch (Exception e) {
                this.setException(e);
                return false;
            }

            // Get the total, and if first call, save the object again so the UI can update.
            mTotalBooks = (int) books.getLong(ListReviewsFieldNames.TOTAL);
            if (mFirstCall) {
                // So the details get updated
                qMgr.saveTask(this);
                mFirstCall = false;
            }

            // Get the reviews array and process it
            ArrayList<Bundle> reviews = books.getParcelableArrayList(ListReviewsFieldNames.REVIEWS);

            if (reviews == null || reviews.size() == 0)
                break;

            for (Bundle review : reviews) {
                // Always check for an abort request
                if (this.isAborting())
                    return false;

                if (mUpdatesAfter != null && review.containsKey(ListReviewsFieldNames.UPDATED)) {
                    if (review.getString(ListReviewsFieldNames.UPDATED).compareTo(mUpdatesAfter) > 0)
                        return true;
                }

                // Processing may involve a SLOW thumbnail download...don't run in TX!
                processReview(db, review);
                //SyncLock tx = db.startTransaction(true);
                //try {
                //	processReview(db, review);
                //	db.setTransactionSuccessful();
                //} finally {
                //	db.endTransaction(tx);
                //}

                // Update after each book. Mainly for a nice UI.
                qMgr.saveTask(this);
                mPosition++;
            }
        }
        try {
            db.analyzeDb();
        } catch (Exception e) {
            // Do nothing. Not a critical step.
            Logger.logError(e);
        }
        return true;
    }

    /**
     * Process one review (book).
     */
    private void processReview(@NonNull final CatalogueDBAdapter db, @NonNull final Bundle review) {
        long grId = review.getLong(ListReviewsFieldNames.GR_BOOK_ID);

        // Find the books in our database - NOTE: may be more than one!
        // First look by goodreads book ID
        BooksCursor c = db.fetchBooksByGoodreadsBookId(grId);
        try {
            boolean found = c.moveToFirst();
            if (!found) {
                // Not found by GR id, look via ISBNs
                c.close();
                c = null;

                List<String> isbns = extractIsbns(review);
                if (isbns != null && isbns.size() > 0) {
                    c = db.fetchBooksByIsbnList(isbns);
                    found = c.moveToFirst();
                }
            }

            if (found) {
                // If found, update ALL related books
                BooksRow rv = c.getRowView();
                do {
                    // Check for abort
                    if (this.isAborting())
                        break;
                    updateBook(db, rv, review);
                } while (c.moveToNext());
            } else {
                // Create the book
                insertBook(db, review);
            }
        } finally {
            if (c != null)
                c.close();
        }
    }

    /**
     * Passed a goodreads shelf name, return the best matching local bookshelf name, or the
     * original if no match found.
     *
     * @param db          Database adapter
     * @param grShelfName Goodreads shelf name
     *
     * @return Local name, or goodreads name if no match
     */
    private String translateBookshelf(@NonNull final CatalogueDBAdapter db, @NonNull final String grShelfName) {
        if (mBookshelfLookup == null) {
            mBookshelfLookup = new HashMap<>();
            for (Bookshelf b : db.getBookshelves()) {
                mBookshelfLookup.put(GoodreadsManager.canonicalizeBookshelfName(b.name), b.name);
            }
        }

        return mBookshelfLookup.containsKey(grShelfName.toLowerCase()) ? mBookshelfLookup.get(grShelfName.toLowerCase()) : grShelfName;
    }

    /**
     * Extract a list of ISBNs from the bundle
     */
    private List<String> extractIsbns(@NonNull final Bundle review) {
        List<String> isbns = new ArrayList<>();

        String isbn = review.getString(ListReviewsFieldNames.ISBN13).trim();
        if (!isbn.isEmpty())
            isbns.add(isbn);

        isbn = review.getString(UniqueId.KEY_ISBN).trim();
        if (!isbn.isEmpty())
            isbns.add(isbn);

        return isbns;
    }

    /**
     * Update the book using the GR data
     */
    private void updateBook(@NonNull final CatalogueDBAdapter db,
                            @NonNull final BooksRow booksRow,
                            @NonNull final Bundle review) {
        // Get last date book was sent to GR (may be null)
        final String lastGrSync = booksRow.getDateLastSyncedWithGoodReads();
        // If the review has an 'updated' date, then see if we can compare to book
        if (lastGrSync != null && review.containsKey(UPDATED)) {
            final String lastUpdate = review.getString(ListReviewsFieldNames.UPDATED);
            // If last update in GR was before last GR sync of book, then don't bother updating book.
            // This typically happens if the last update in GR was from us.
            if (lastUpdate != null && lastUpdate.compareTo(lastGrSync) < 0)
                return;
        }
        // We build a new book bundle each time since it will build on the existing
        // data for the given book, not just replace it.
        BookData bookData = buildBundle(db, booksRow, review);

        db.updateBook(booksRow.getId(), bookData, CatalogueDBAdapter.BOOK_UPDATE_SKIP_PURGE_REFERENCES | CatalogueDBAdapter.BOOK_UPDATE_USE_UPDATE_DATE_IF_PRESENT);
        //db.setGoodreadsSyncDate(rv.getId());
    }

    /**
     * Create a new book
     */
    private void insertBook(@NonNull final CatalogueDBAdapter db, @NonNull final Bundle review) {
        BookData bookData = buildBundle(db, null, review);
        long id = db.insertBook(bookData);
        if (id > 0) {
            if (bookData.getBoolean(UniqueId.BKEY_THUMBNAIL)) {
                String uuid = db.getBookUuid(id);
                File thumb = StorageUtils.getTempCoverFile();
                File real = StorageUtils.getCoverFile(uuid);
                StorageUtils.renameFile(thumb, real);
            }
            //db.setGoodreadsSyncDate(id);
        }
    }

    /**
     * Build a book bundle based on the goodreads 'review' data. Some data is just copied
     * while other data is processed (eg. dates) and other are combined (authors & series).
     */
    private BookData buildBundle(@NonNull final CatalogueDBAdapter db, @Nullable final BooksRow booksRow, @NonNull final Bundle review) {
        BookData bookData = new BookData();

        addStringIfNonBlank(review, ListReviewsFieldNames.DB_TITLE, bookData, ListReviewsFieldNames.DB_TITLE);
        addStringIfNonBlank(review, ListReviewsFieldNames.DB_DESCRIPTION, bookData, ListReviewsFieldNames.DB_DESCRIPTION);
        addStringIfNonBlank(review, ListReviewsFieldNames.DB_FORMAT, bookData, ListReviewsFieldNames.DB_FORMAT);
        // Do not sync Notes<->Review. We will add a 'Review' field later.
        //addStringIfNonBlank(review, ListReviewsFieldNames.DB_NOTES, book, ListReviewsFieldNames.DB_NOTES);
        addLongIfPresent(review, ListReviewsFieldNames.DB_PAGES, bookData, ListReviewsFieldNames.DB_PAGES);
        addStringIfNonBlank(review, ListReviewsFieldNames.DB_PUBLISHER, bookData, ListReviewsFieldNames.DB_PUBLISHER);
        Double rating = addDoubleIfPresent(review, ListReviewsFieldNames.DB_RATING, bookData, ListReviewsFieldNames.DB_RATING);
        addDateIfValid(review, ListReviewsFieldNames.DB_READ_START, bookData, ListReviewsFieldNames.DB_READ_START);
        String readEnd = addDateIfValid(review, ListReviewsFieldNames.DB_READ_END, bookData, ListReviewsFieldNames.DB_READ_END);

        // If it has a rating or a 'read_end' date, assume it's read. If these are missing then
        // DO NOT overwrite existing data since it *may* be read even without these fields.
        if ((rating != null && rating > 0) || (readEnd != null && readEnd.length() > 0)) {
            bookData.putBoolean(UniqueId.KEY_BOOK_READ, true);
        }

        addStringIfNonBlank(review, ListReviewsFieldNames.DB_TITLE, bookData, ListReviewsFieldNames.DB_TITLE);
        addLongIfPresent(review, ListReviewsFieldNames.GR_BOOK_ID, bookData, DOM_BOOK_GOODREADS_BOOK_ID.name);

        // Find the best (longest) isbn.
        List<String> isbns = extractIsbns(review);
        if (isbns.size() > 0) {
            String best = isbns.get(0);
            int bestLen = best.length();
            for (int i = 1; i < isbns.size(); i++) {
                String curr = isbns.get(i);
                if (curr.length() > bestLen) {
                    best = curr;
                    bestLen = best.length();
                }
            }
            if (bestLen > 0) {
                bookData.putString(UniqueId.KEY_ISBN, best);
            }
        }

        /* Build the pub date based on the components */
        String pubDate = GoodreadsManager.buildDate(review,
                ListReviewsFieldNames.PUB_YEAR, ListReviewsFieldNames.PUB_MONTH, ListReviewsFieldNames.PUB_DAY,
                null);
        if (pubDate != null && !pubDate.isEmpty())
            bookData.putString(UniqueId.KEY_BOOK_DATE_PUBLISHED, pubDate);

        ArrayList<Bundle> grAuthors = review.getParcelableArrayList(ListReviewsFieldNames.AUTHORS);
        if (grAuthors == null) {
            Logger.logError("grAuthors was null");
            return bookData;
        }

        ArrayList<Author> authors;
        if (booksRow == null) {
            // It's a new book. Start a clean list.
            authors = new ArrayList<>();
        } else {
            // it's an update. Get current authors.
            authors = db.getBookAuthorList(booksRow.getId());
        }

        for (Bundle grAuthor : grAuthors) {
            String name = grAuthor.getString(ListReviewsFieldNames.DB_AUTHOR_NAME);
            if (name != null && !name.trim().isEmpty()) {
                authors.add(new Author(name));
            }
        }
        bookData.putSerializable(UniqueId.BKEY_AUTHOR_ARRAY, authors);

        if (booksRow == null) {
            // Use the GR added date for new books
            addStringIfNonBlank(review, ListReviewsFieldNames.ADDED, bookData, DOM_BOOK_DATE_ADDED.name);
            // Also fetch thumbnail if add
            String thumbnail;
            if (review.containsKey(ListReviewsFieldNames.LARGE_IMAGE)
                    && !review.getString(ListReviewsFieldNames.LARGE_IMAGE).toLowerCase().contains(UniqueId.BKEY_NOCOVER)) {
                thumbnail = review.getString(ListReviewsFieldNames.LARGE_IMAGE);
            } else if (review.containsKey(ListReviewsFieldNames.SMALL_IMAGE)
                    && !review.getString(ListReviewsFieldNames.SMALL_IMAGE).toLowerCase().contains(UniqueId.BKEY_NOCOVER)) {
                thumbnail = review.getString(ListReviewsFieldNames.SMALL_IMAGE);
            } else {
                thumbnail = null;
            }
            if (thumbnail != null) {
                String fileSpec = ImageUtils.saveThumbnailFromUrl(thumbnail, GoodreadsUtils.GOODREADS_FILENAME_SUFFIX);
                if (fileSpec.length() > 0)
                    bookData.appendOrAdd(UniqueId.BKEY_THUMBNAIL_USCORE, fileSpec);
                bookData.cleanupThumbnails();
            }
        }

        /*
         * Cleanup the title by removing series name, if present
         */
        if (bookData.containsKey(UniqueId.KEY_TITLE)) {
            String thisTitle = bookData.getString(UniqueId.KEY_TITLE);
            Series.SeriesDetails details = Series.findSeriesFromBookTitle(thisTitle);
            if (details != null && details.name.length() > 0) {
                ArrayList<Series> allSeries;
                if (booksRow == null)
                    allSeries = new ArrayList<>();
                else
                    allSeries = db.getBookSeriesList(booksRow.getId());

                allSeries.add(new Series(details.name, details.position));
                bookData.putString(UniqueId.KEY_TITLE, thisTitle.substring(0, details.startChar - 1));

                Utils.pruneSeriesList(allSeries);
                bookData.putSerializable(UniqueId.BKEY_SERIES_ARRAY, allSeries);
            }
        }

        // Process any bookshelves
        if (review.containsKey(ListReviewsFieldNames.SHELVES)) {
            ArrayList<Bundle> shelves = review.getParcelableArrayList(ListReviewsFieldNames.SHELVES);
            if (shelves == null) {
                Logger.logError("shelves was null");
                return bookData;
            }
            StringBuilder shelfNames = null;
            for (Bundle sb : shelves) {
                String shelf = translateBookshelf(db, sb.getString(ListReviewsFieldNames.SHELF));
                if (shelf != null && !shelf.isEmpty()) {
                    shelf = ArrayUtils.encodeListItem(Bookshelf.SEPARATOR, shelf);
                    if (shelfNames == null)
                        shelfNames = new StringBuilder(shelf);
                    else
                        shelfNames.append(Bookshelf.SEPARATOR).append(shelf);
                }
            }
            if (shelfNames != null && shelfNames.length() > 0)
                bookData.setBookshelfList(shelfNames.toString());
        }

        // We need to set BOTH of these fields, otherwise the add/update method will set the
        // last_update_date for us, and that will most likely be set ahead of the GR update date
        Date now = new Date();
        bookData.putString(UniqueId.KEY_GOODREADS_LAST_SYNC_DATE, DateUtils.toSqlDateTime(now));
        bookData.putString(UniqueId.KEY_LAST_UPDATE_DATE, DateUtils.toSqlDateTime(now));

        return bookData;
    }

    /**
     * Utility to copy a non-blank and valid date string to the book bundle; will
     * attempt to translate as appropriate and will not add the date if it cannot
     * be parsed.
     *
     * @return reformatted sql date, or null if not able to parse
     */
    @Nullable
    private String addDateIfValid(@NonNull final Bundle source,
                                  @NonNull final String sourceField,
                                  @NonNull final BookData dest,
                                  @NonNull final String destField) {
        if (!source.containsKey(sourceField))
            return null;

        String val = source.getString(sourceField);
        if (val == null || val.isEmpty())
            return null;

        Date d = DateUtils.parseDate(val);
        if (d == null)
            return null;

        val = DateUtils.toSqlDateTime(d);
        dest.putString(destField, val);
        return val;
    }

    /**
     * Utility to copy a non-blank string to the book bundle.
     */
    private void addStringIfNonBlank(@NonNull final Bundle source,
                                     @NonNull final String sourceField,
                                     @NonNull final BookData dest,
                                     @NonNull final String destField) {
        if (source.containsKey(sourceField)) {
            String val = source.getString(sourceField);
            if (val != null && !val.isEmpty()) {
                dest.putString(destField, val);
            }
        }
    }

    /**
     * Utility to copy a Long value to the book bundle.
     */
    private void addLongIfPresent(@NonNull final Bundle source,
                                  @NonNull final String sourceField,
                                  @NonNull final BookData dest,
                                  @NonNull final String destField) {
        if (source.containsKey(sourceField)) {
            long val = source.getLong(sourceField);
            dest.putLong(destField, val);
        }
    }

    /**
     * Utility to copy a Double value to the book bundle.
     */
    @Nullable
    private Double addDoubleIfPresent(@NonNull final Bundle source,
                                      @SuppressWarnings("SameParameterValue") @NonNull final String sourceField,
                                      @NonNull final BookData dest,
                                      @SuppressWarnings("SameParameterValue") @NonNull final String destField) {
        if (source.containsKey(sourceField)) {
            double val = source.getDouble(sourceField);
            dest.putDouble(destField, val);
            return val;
        } else {
            return null;
        }
    }

    /**
     * Make a more informative description
     */
    @Override
    @NonNull
    public String getDescription() {
        String base = super.getDescription();
        if (mUpdatesAfter == null)
            return base + " (" + BookCatalogueApp.getResourceString(R.string.x_of_y, mPosition, mTotalBooks) + ")";
        else
            return base + " (" + mPosition + ")";
    }

    @Override
    public int getCategory() {
        return BCQueueManager.CAT_GOODREADS_IMPORT_ALL;
    }

    /**
     * Custom serialization support.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }

    /**
     * Pseudo-constructor for custom serialization support.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        mFirstCall = true;
    }
}
