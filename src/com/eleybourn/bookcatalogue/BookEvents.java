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

package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDoneException;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.cursors.BooksCursor;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.dialogs.HintManager.HintOwner;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.searches.goodreads.SendOneBookTask;
import com.eleybourn.bookcatalogue.tasks.BCQueueManager;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import net.philipwarner.taskqueue.BindableItemSQLiteCursor;
import net.philipwarner.taskqueue.ContextDialogItem;
import net.philipwarner.taskqueue.Event;
import net.philipwarner.taskqueue.EventsCursor;
import net.philipwarner.taskqueue.QueueManager;

import java.text.DateFormat;
import java.util.ArrayList;

import static com.eleybourn.bookcatalogue.UniqueId.KEY_ID;

/**
 * Class to define all book-related events that may be stored in the QueueManager.
 *
 * *************!!!!!!!!!!!!*************!!!!!!!!!!!!*************!!!!!!!!!!!!*************
 * NOTE: If internal data or data types need to change in this class after the application
 * has been deployed, CREATE A NEW CLASS OR EVENT. Otherwise, deployed serialized objects
 * may fail to reload. This is not a big issue (it's what LegacyTask and LegacyEvent are
 * for), but it is better to present data to the users cleanly.
 * *************!!!!!!!!!!!!*************!!!!!!!!!!!!*************!!!!!!!!!!!!*************
 *
 * @author Philip Warner
 */
public class BookEvents {
    /**
     * Method to retry sending a book to goodreads.
     */
    private static final OnClickListener mRetryButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            BookEvent.BookEventHolder holder = ViewTagger.getTag(v, R.id.TAG_BOOK_EVENT_HOLDER);
            ((GrSendBookEvent) holder.event).retry();
        }
    };

    /**
     * Method to edit a book details.
     */
    private static void editBook(@NonNull final Context ctx, final long bookId) {
        Intent i = new Intent(ctx, EditBookActivity.class);
        i.putExtra(KEY_ID, bookId);
        i.putExtra(EditBookActivity.TAB, EditBookActivity.TAB_EDIT);
        ctx.startActivity(i);
    }

    /**
     * Base class for all book-related events. Has a book ID and is capable of
     * displaying basic book data in a View.
     *
     * @author Philip Warner
     */
    public static class BookEvent extends Event {
        private static final long serialVersionUID = 74746691665235897L;

        final long mBookId;

        /**
         * Constructor
         *
         * @param bookId      ID of related book.
         * @param description Description of this event.
         */
        BookEvent(final long bookId, @NonNull final String description) {
            super(description);
            mBookId = bookId;
        }

        /**
         * Constructor
         *
         * @param bookId      ID of related book.
         * @param description Description of this event.
         * @param e           Exception related to this event.
         */
        BookEvent(final long bookId, @NonNull final String description, @NonNull final Exception e) {
            super(description, e);
            mBookId = bookId;
        }

        /**
         * Get the related Book ID.
         *
         * @return ID of related book.
         */
        public long getBookId() {
            return mBookId;
        }

        /**
         * Return a view capable of displaying basic book event details, ideally usable by all BookEvent subclasses.
         * This method also prepares the BookEventHolder object for the View.
         */
        @Override
        public View newListItemView(final LayoutInflater inflater,
                                    final Context context,
                                    final BindableItemSQLiteCursor cursor,
                                    final ViewGroup parent) {
            View view = inflater.inflate(R.layout.row_book_event_info, parent, false);
            ViewTagger.setTag(view, R.id.TAG_EVENT, this);
            BookEventHolder holder = new BookEventHolder();
            holder.event = this;
            holder.rowId = cursor.getId();

            holder.author = view.findViewById(R.id.author);
            holder.checkbox = view.findViewById(R.id.checked);
            holder.date = view.findViewById(R.id.date);
            holder.error = view.findViewById(R.id.error);
            holder.retry = view.findViewById(R.id.retry);
            holder.title = view.findViewById(R.id.title);

            ViewTagger.setTag(view, R.id.TAG_BOOK_EVENT_HOLDER, holder);
            ViewTagger.setTag(holder.checkbox, R.id.TAG_BOOK_EVENT_HOLDER, holder);
            ViewTagger.setTag(holder.retry, R.id.TAG_BOOK_EVENT_HOLDER, holder);

            return view;
        }

        /**
         * Display the related book details in the passed View object.
         */
        @Override
        public boolean bindView(final View view,
                                final Context context,
                                final BindableItemSQLiteCursor bindableCursor,
                                final Object appInfo) {
            final EventsCursor cursor = (EventsCursor) bindableCursor;
            BookEventHolder holder = ViewTagger.getTag(view, R.id.TAG_BOOK_EVENT_HOLDER);
            CatalogueDBAdapter db = (CatalogueDBAdapter) appInfo;

            // Update event info binding; the Views in the holder are unchanged, but when it is reused
            // the Event and ID will change.
            holder.event = this;
            holder.rowId = cursor.getId();

            ArrayList<Author> authors = db.getBookAuthorList(mBookId);
            String author;
            if (authors.size() > 0) {
                author = authors.get(0).getDisplayName();
                if (authors.size() > 1)
                    author = author + " et. al.";
            } else {
                author = context.getString(R.string.unknown_uc);
            }

            String title;
            try {
                title = db.getBookTitle(mBookId);
            } catch (SQLiteDoneException e) {
                title = context.getString(R.string.this_book_deleted_uc);
            }
            holder.title.setText(title);

            holder.author.setText(String.format(context.getString(R.string.by), author));

            Exception e = this.getException();
            if (e == null) {
                holder.error.setText(this.getDescription());
            } else {
                holder.error.setText(e.getMessage());
            }

            String date = String.format("(" + context.getString(R.string.occurred_at) + ")",
                    DateFormat.getDateTimeInstance().format(cursor.getEventDate()));
            holder.date.setText(date);

            holder.retry.setVisibility(View.GONE);

            holder.checkbox.setChecked(cursor.getIsSelected());
            holder.checkbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    BookEventHolder holder = ViewTagger.getTag(buttonView, R.id.TAG_BOOK_EVENT_HOLDER);
                    cursor.setIsSelected(holder.rowId, isChecked);
                }
            });

            return true;
        }

        /**
         * Add ContextDialogItems relevant for the specific book the selected View is associated with.
         * Subclass can override this and add items at end/start or just replace these completely.
         */
        @Override
        public void addContextMenuItems(final Context ctx,
                                        final AdapterView<?> parent,
                                        final View v,
                                        final int position, final long id,
                                        final ArrayList<ContextDialogItem> items,
                                        final Object appInfo) {

            // EDIT BOOK
            items.add(new ContextDialogItem(ctx.getString(R.string.edit_book), new Runnable() {
                @Override
                public void run() {
                    try {
                        GrSendBookEvent event = ViewTagger.getTag(v, R.id.TAG_EVENT);
                        editBook(ctx, event.getBookId());
                    } catch (Exception e) {
                        // not a book event?
                    }
                }
            }));
            // TODO Reinstate goodreads search when goodreads work.editions API is available
            //// SEARCH GOODREADS
            //items.add(new ContextDialogItem(ctx.getString(R.string.visit_goodreads), new Runnable() {
            //	@Override
            //	public void run() {
            //		BookEventHolder holder = (BookEventHolder)ViewTagger.getTag(v, R.id.TAG_BOOK_EVENT_HOLDER);
            //		Intent i = new Intent(ctx, GoodreadsSearchCriteria.class);
            //		i.putExtra(GoodreadsSearchCriteria.EXTRA_BOOK_ID, holder.event.getBookId());
            //		ctx.startActivity(i);
            //	}}));
            // DELETE EVENT
            items.add(new ContextDialogItem(ctx.getString(R.string.delete_event), new Runnable() {
                @Override
                public void run() {
                    BookCatalogueApp.getQueueManager().deleteEvent(id);
                }
            }));
        }

        /**
         * Class to implement the 'holder' model for view we create.
         *
         * @author Philip Warner
         */
        protected class BookEventHolder {
            long rowId;
            BookEvent event;
            TextView title;
            TextView author;
            TextView error;
            TextView date;
            Button retry;
            CheckBox checkbox;
        }

    }

    /**
     * Subclass of BookEvent that is the base class for all Event objects resulting from
     * sending books to goodreads.
     *
     * @author Philip Warner
     */
    public static class GrSendBookEvent extends BookEvent {
        private static final long serialVersionUID = 1L;

        GrSendBookEvent(long bookId, String message) {
            super(bookId, message);
        }

        /**
         * Resubmit this book and delete this event.
         */
        public void retry() {
            QueueManager qm = BookCatalogueApp.getQueueManager();
            SendOneBookTask task = new SendOneBookTask(mBookId);
            // TODO: MAKE IT USE THE SAME QUEUE? Why????
            qm.enqueueTask(task, BCQueueManager.QUEUE_SMALL_JOBS, 0);
            qm.deleteEvent(this.getId());
        }

        /**
         * Override to allow modification of view.
         */
        @Override
        public boolean bindView(final View view, final Context context, final BindableItemSQLiteCursor bindableCursor, final Object appInfo) {
            // Get the 'standard' view.
            super.bindView(view, context, bindableCursor, appInfo);

            // get book details
            final BookEventHolder holder = ViewTagger.getTag(view, R.id.TAG_BOOK_EVENT_HOLDER);
            final CatalogueDBAdapter db = (CatalogueDBAdapter) appInfo;
            final BooksCursor booksCursor = db.getBookForGoodreadsCursor(mBookId);
            final BooksRow book = booksCursor.getRowView();
            try {
                // Hide parts of view based on current book details.
                if (booksCursor.moveToFirst()) {
                    if (book.getIsbn().isEmpty()) {
                        holder.retry.setVisibility(View.GONE);
                    } else {
                        holder.retry.setVisibility(View.VISIBLE);
                        ViewTagger.setTag(holder.retry, this);
                        holder.retry.setOnClickListener(mRetryButtonListener);
                    }
                } else {
                    holder.retry.setVisibility(View.GONE);
                }
            } finally {
                // Always close
                if (booksCursor != null)
                    booksCursor.close();
            }

            return true;
        }

        /**
         * Override to allow a new context menu item.
         */
        @Override
        public void addContextMenuItems(final Context ctx, final AdapterView<?> parent, final View v, final int position, final long id, ArrayList<ContextDialogItem> items, Object appInfo) {
            super.addContextMenuItems(ctx, parent, v, position, id, items, appInfo);

            final CatalogueDBAdapter db = (CatalogueDBAdapter) appInfo;
            final BooksCursor booksCursor = db.getBookForGoodreadsCursor(mBookId);
            try {
                final BooksRow book = booksCursor.getRowView();
                if (booksCursor.moveToFirst()) {
                    if (!book.getIsbn().isEmpty()) {
                        items.add(new ContextDialogItem(ctx.getString(R.string.retry_task), new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    GrSendBookEvent event = ViewTagger.getTag(v, R.id.TAG_EVENT);
                                    event.retry();
                                    QueueManager.getQueueManager().deleteEvent(id);
                                } catch (Exception e) {
                                    // not a book event?
                                }
                            }
                        }));
                    }
                }
            } finally {
                booksCursor.close();
            }
        }
    }

//	/**
//	 * 'General' purpose exception class
//	 *
//	 * @author Philip Warner
//	 */
//	public static class GrGeneralBookEvent extends GrSendBookEvent {
//		private static final long serialVersionUID = -7684121345325648066L;
//
//		public GrGeneralBookEvent(final long bookId, @NonNull final Exception e, String message) {
//			super(bookId, message, e);
//		}
//	}

    /**
     * Exception indicating the book's ISBN could not be found at GoodReads
     *
     * @author Philip Warner
     */
    public static class GrNoMatchEvent extends GrSendBookEvent implements HintOwner {
        private static final long serialVersionUID = -7684121345325648066L;

        public GrNoMatchEvent(final long bookId) {
            super(bookId, BookCatalogueApp.getResourceString(R.string.no_matching_book_found));
        }

        @Override
        public int getHint() {
            return R.string.explain_goodreads_no_match;
        }

    }

    /**
     * Exception indicating the book's ISBN was blank
     *
     * @author Philip Warner
     */
    public static class GrNoIsbnEvent extends GrSendBookEvent implements HintOwner {
        private static final long serialVersionUID = 7260496259505914311L;

        public GrNoIsbnEvent(final long bookId) {
            super(bookId, BookCatalogueApp.getResourceString(R.string.no_isbn_stored_for_book));
        }

        @Override
        public int getHint() {
            return R.string.explain_goodreads_no_isbn;
        }

    }
}
