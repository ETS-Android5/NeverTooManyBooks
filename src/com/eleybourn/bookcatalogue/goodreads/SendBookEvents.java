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

package com.eleybourn.bookcatalogue.goodreads;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.EditBookActivity;
import com.eleybourn.bookcatalogue.EditBookFragment;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BindableItemCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookCursor;
import com.eleybourn.bookcatalogue.database.cursors.BookRowView;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;
import com.eleybourn.bookcatalogue.dialogs.HintManager.HintOwner;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.tasks.taskqueue.Event;
import com.eleybourn.bookcatalogue.tasks.taskqueue.EventsCursor;
import com.eleybourn.bookcatalogue.tasks.taskqueue.QueueManager;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.ViewTagger;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to define all book-related events that may be stored in the QueueManager.
 *
 * @author Philip Warner
 */
final class SendBookEvents {

    private SendBookEvents() {
    }

    /**
     * All Event objects resulting from sending books to goodreads.
     *
     * @author Philip Warner
     */
    private static class GrSendBookEvent
            extends Event {

        /**
         * Retry sending a book to goodreads.
         */
        @Nullable
        static final OnClickListener mRetryButtonListener = new OnClickListener() {
            @Override
            public void onClick(@NonNull final View v) {
                BookEventHolder holder = ViewTagger.getTagOrThrow(v, R.id.TAG_BOOK_EVENT_HOLDER);
                holder.event.retry();
            }
        };
        private static final long serialVersionUID = 8056090158313083738L;
        private final long mBookId;

        /**
         * Constructor.
         *
         * @param bookId    ID of related book.
         * @param messageId Description of this event.
         */
        GrSendBookEvent(@NonNull final Context context,
                        final long bookId,
                        @StringRes final int messageId) {
            super(context.getResources().getString(messageId));
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
         * Resubmit this book and delete this event.
         */
        public void retry() {
            QueueManager qm = QueueManager.getQueueManager();
            SendOneBookTask task = new SendOneBookTask(mBookId);
            // TODO: MAKE IT USE THE SAME QUEUE? Why????
            qm.enqueueTask(task, QueueManager.QUEUE_SMALL_JOBS);
            qm.deleteEvent(this.getId());
        }

        /**
         * Return a view capable of displaying basic book event details,
         * ideally usable by all BookEvent subclasses.
         * This method also prepares the BookEventHolder object for the View.
         */
        @Override
        public View newListItemView(@NonNull final LayoutInflater inflater,
                                    @NonNull final Context context,
                                    @NonNull final BindableItemCursor cursor,
                                    @NonNull final ViewGroup parent) {
            View view = inflater.inflate(R.layout.row_book_event_info, parent, false);
            ViewTagger.setTag(view, R.id.TAG_EVENT, this);
            BookEventHolder holder = new BookEventHolder();
            holder.event = this;
            holder.rowId = cursor.getId();

            holder.author = view.findViewById(R.id.author);
            holder.button = view.findViewById(R.id.checked);
            holder.date = view.findViewById(R.id.date);
            holder.error = view.findViewById(R.id.error);
            holder.retry = view.findViewById(R.id.retry);
            holder.title = view.findViewById(R.id.title);

            ViewTagger.setTag(view, R.id.TAG_BOOK_EVENT_HOLDER, holder);
            ViewTagger.setTag(holder.button, R.id.TAG_BOOK_EVENT_HOLDER, holder);
            ViewTagger.setTag(holder.retry, R.id.TAG_BOOK_EVENT_HOLDER, holder);

            return view;
        }

        /**
         * Display the related book details in the passed View object.
         */
        @Override
        public void bindView(@NonNull final View view,
                             @NonNull final Context context,
                             @NonNull final BindableItemCursor cursor,
                             @NonNull final CatalogueDBAdapter db) {
            final EventsCursor eventsCursor = (EventsCursor) cursor;

            // Update event info binding; the Views in the holder are unchanged,
            // but when it is reused the Event and ID will change.
            BookEventHolder holder = ViewTagger.getTagOrThrow(view, R.id.TAG_BOOK_EVENT_HOLDER);
            holder.event = this;
            holder.rowId = eventsCursor.getId();

            ArrayList<Author> authors = db.getBookAuthorList(mBookId);
            String author;
            if (authors.size() > 0) {
                author = authors.get(0).getDisplayName();
                if (authors.size() > 1) {
                    author = author + ' ' + BookCatalogueApp.getResourceString(R.string.and_others);
                }
            } else {
                author = context.getString(R.string.unknown_uc);
            }

            String title = db.getBookTitle(mBookId);
            if (title == null) {
                title = context.getString(R.string.warning_book_was_deleted_uc);
            }

            holder.title.setText(title);
            holder.author.setText(
                    String.format(context.getString(R.string.lbl_by_authors), author));
            holder.error.setText(this.getDescription());

            String date = String.format(context.getString(R.string.gr_tq_occurred_at),
                                        DateUtils.toPrettyDateTime(eventsCursor.getEventDate()));
            holder.date.setText(date);

            holder.retry.setVisibility(View.GONE);

            holder.button.setChecked(eventsCursor.getIsSelected());
            holder.button.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(@NonNull final CompoundButton buttonView,
                                             final boolean isChecked) {
                    BookEventHolder holder = ViewTagger.getTagOrThrow(buttonView,
                                                                      R.id.TAG_BOOK_EVENT_HOLDER);
                    eventsCursor.setIsSelected(holder.rowId, isChecked);
                }
            });

            // get book details
            try (BookCursor bookCursor = db.fetchBookForGoodreadsCursor(mBookId)) {
                // Hide parts of view based on current book details.
                if (bookCursor.moveToFirst()) {
                    final BookRowView bookCursorRow = bookCursor.getCursorRow();
                    if (bookCursorRow.getIsbn().isEmpty()) {
                        holder.retry.setVisibility(View.GONE);
                    } else {
                        holder.retry.setVisibility(View.VISIBLE);
                        ViewTagger.setTag(holder.retry, this);
                        holder.retry.setOnClickListener(mRetryButtonListener);
                    }
                } else {
                    holder.retry.setVisibility(View.GONE);
                }
            }
        }

        /**
         * Add ContextDialogItems relevant for the specific book the selected View is
         * associated with.
         * Subclass can override this and add items at end/start or just replace these completely.
         */
        @Override
        public void addContextMenuItems(@NonNull final Context context,
                                        @NonNull final AdapterView<?> parent,
                                        @NonNull final View view,
                                        final int position,
                                        final long id,
                                        @NonNull final List<ContextDialogItem> items,
                                        @NonNull final CatalogueDBAdapter db) {

            // EDIT BOOK
            items.add(new ContextDialogItem(
                    context.getString(R.string.menu_edit_book),
                    new Runnable() {
                        @Override
                        public void run() {
                            try {
                                GrSendBookEvent event =
                                        ViewTagger.getTagOrThrow(view, R.id.TAG_EVENT);
                                Intent intent = new Intent(context, EditBookActivity.class);
                                intent.putExtra(UniqueId.KEY_ID, event.getBookId());
                                intent.putExtra(EditBookFragment.REQUEST_BKEY_TAB,
                                                EditBookFragment.TAB_EDIT);
                                context.startActivity(intent);
                            } catch (RuntimeException ignore) {
                                // not a book event?
                            }
                        }
                    }));

            // ENHANCE: Reinstate goodreads search when goodreads work.editions API is available
//            // SEARCH GOODREADS
//            items.add(new ContextDialogItem(
//                    context.getString(R.string.gr_search_goodreads),
//                    new Runnable() {
//                        @Override
//                        public void run() {
//                            BookEventHolder holder =
//                                    ViewTagger.getTagOrThrow(view,
//                                                             R.id.TAG_BOOK_EVENT_HOLDER);
//                            Intent intent = new Intent(context,
//                                                       GoodreadsSearchCriteriaActivity.class);
//                            intent.putExtra(
//                                    GoodreadsSearchCriteriaActivity.REQUEST_BKEY_BOOK_ID,
//                                    holder.event.getBookId());
//                            context.startActivity(intent);
//                        }
//                    }));

            // DELETE EVENT
            items.add(
                    new ContextDialogItem(
                            context.getString(R.string.gr_tq_menu_delete_event),
                            new Runnable() {
                                @Override
                                public void run() {
                                    QueueManager.getQueueManager().deleteEvent(id);
                                }
                            }));

            // RETRY EVENT
            try (BookCursor bookCursor = db.fetchBookForGoodreadsCursor(mBookId)) {
                final BookRowView bookCursorRow = bookCursor.getCursorRow();
                if (bookCursor.moveToFirst()) {
                    if (!bookCursorRow.getIsbn().isEmpty()) {
                        items.add(
                                new ContextDialogItem(
                                        context.getString(R.string.retry),
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    GrSendBookEvent event =
                                                            ViewTagger.getTagOrThrow(
                                                                    view, R.id.TAG_EVENT);
                                                    event.retry();
                                                    QueueManager.getQueueManager().deleteEvent(id);
                                                } catch (RuntimeException ignore) {
                                                    // not a book event?
                                                }
                                            }
                                        }));
                    }
                }
            }
        }

        /**
         * Class to implement the 'holder' model for view we create.
         */
        static class BookEventHolder {

            long rowId;
            GrSendBookEvent event;
            TextView title;
            TextView author;
            TextView error;
            TextView date;
            Button retry;
            CompoundButton button;
        }

    }

    /**
     * Exception indicating the book's ISBN could not be found at Goodreads.
     */
    public static class GrNoMatchEvent
            extends GrSendBookEvent
            implements HintOwner {

        private static final long serialVersionUID = -7684121345325648066L;

        GrNoMatchEvent(@NonNull final Context context,
                       final long bookId) {
            super(context, bookId, R.string.warning_no_matching_book_found);
        }

        @Override
        public int getHint() {
            return R.string.gr_explain_goodreads_no_match;
        }

    }

    /**
     * Exception indicating the book's ISBN was blank.
     */
    public static class GrNoIsbnEvent
            extends GrSendBookEvent
            implements HintOwner {

        private static final long serialVersionUID = 7260496259505914311L;

        GrNoIsbnEvent(@NonNull final Context context,
                      final long bookId) {
            super(context, bookId, R.string.warning_no_isbn_stored_for_book);
        }

        @Override
        public int getHint() {
            return R.string.gr_explain_goodreads_no_isbn;
        }

    }
}
