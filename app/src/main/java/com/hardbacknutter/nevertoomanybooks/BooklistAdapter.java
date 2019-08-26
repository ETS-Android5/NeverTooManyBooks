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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup.RowKind;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistMappedCursorRow;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistSupportProvider;
import com.hardbacknutter.nevertoomanybooks.database.ColumnNotPresentException;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.cursors.CursorMapper;
import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.FastScrollerOverlay;

/**
 * Handles all views in a multi-type list showing books, authors, series etc.
 * <p>
 * Each row(level) needs to have a layout like:
 * <pre>
 *     {@code
 *          <layout id="@id/ROW_INFO">
 *          <TextView id="@id/name" />
 *          ...
 *      }
 * </pre>
 * <p>
 * ROW_INFO is important, as it's that one that gets shown/hidden when needed.
 */
public class BooklistAdapter
        extends RecyclerView.Adapter<BooklistAdapter.RowViewHolder>
        implements FastScrollerOverlay.SectionIndexerV2 {

    /** The padding indent (in pixels) added for each level: padding = (level-1) * mLevelIndent. */
    private final int mLevelIndent;
    /** Database Access. */
    @NonNull
    private final DAO mDb;
    @NonNull
    private final LayoutInflater mInflater;
    /** The cursor is the equivalent of the 'list of items'. */
    @Nullable
    private Cursor mCursor;
    @NonNull
    private BooklistStyle mStyle;
    @Nullable
    private View.OnClickListener mOnItemClick;
    @Nullable
    private View.OnLongClickListener mOnItemLongClick;

    /**
     * Constructor.
     *
     * @param inflater LayoutInflater to use
     * @param style    The style is used by (some) individual rows.
     * @param db       Database Access
     */
    public BooklistAdapter(@NonNull final LayoutInflater inflater,
                           @NonNull final BooklistStyle style,
                           @NonNull final DAO db,
                           @Nullable final Cursor cursor) {
        mInflater = inflater;
        mStyle = style;
        mDb = db;
        mCursor = cursor;
        mLevelIndent = mInflater.getContext().getResources()
                                .getDimensionPixelSize(R.dimen.booklist_level_indent);
    }

    /**
     * Sets the cursor and notifies the adapter.
     *
     * @param cursor to use.
     */
    public void setCursor(@Nullable final Cursor cursor) {
        mCursor = cursor;
        notifyDataSetChanged();
    }

    /**
     * Set the (new) style.
     *
     * @param style to use
     */
    public void setStyle(@NonNull final BooklistStyle style) {
        mStyle = style;
    }

    void setOnItemClickListener(@NonNull final View.OnClickListener onItemClick) {
        mOnItemClick = onItemClick;
    }

    void setOnItemLongClickListener(@NonNull final View.OnLongClickListener onItemLongClick) {
        mOnItemLongClick = onItemLongClick;
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                            final int viewType) {
        //noinspection ConstantConditions
        CursorMapper row = ((BooklistSupportProvider) mCursor).getCursorMapper();

        // The view depends on the viewType + level.
        View view = createView(parent, viewType, row.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));

        // do holder type dependent init.
        return createHolder(viewType, view);
    }

    @Override
    public void onBindViewHolder(@NonNull final RowViewHolder holder,
                                 final int position) {

        // tag for the position, so the click-listeners can get it.
        holder.itemView.setTag(R.id.TAG_POSITION, position);
        holder.itemView.setOnClickListener(mOnItemClick);
        holder.itemView.setOnLongClickListener(mOnItemLongClick);

        // position the data we need to bind.
        //noinspection ConstantConditions
        mCursor.moveToPosition(position);
        CursorMapper row = ((BooklistSupportProvider) mCursor).getCursorMapper();
        // actual binding depends on the type of row (i.e. holder), so let the holder do it.
        holder.onBindViewHolder(row, mStyle);
    }

    @Override
    public int getItemViewType(final int position) {
        if (mCursor == null) {
            return 0;
        }
        mCursor.moveToPosition(position);
        CursorMapper row = ((BooklistSupportProvider) mCursor).getCursorMapper();
        return row.getInt(DBDefinitions.KEY_BL_NODE_ROW_KIND);
    }

    @Override
    public int getItemCount() {
        return mCursor == null ? 0 : mCursor.getCount();
    }

    private View createView(@NonNull final ViewGroup parent,
                            final int viewType,
                            final int level) {
        @LayoutRes
        int layoutId;

        // Indent (0..) based on level (1..)
        int indent = level - 1;

        // A Book occurs always at the lowest level regardless of the groups in the style.
        if (viewType == RowKind.BOOK) {
            switch (mStyle.getThumbnailScaleFactor()) {

                case ImageUtils.SCALE_2X_LARGE:
                    layoutId = R.layout.booksonbookshelf_row_book_3x_large_image;
                    break;

                case ImageUtils.SCALE_X_LARGE:
                    layoutId = R.layout.booksonbookshelf_row_book_2x_large_image;
                    break;

                default:
                    layoutId = R.layout.booksonbookshelf_row_book;
                    break;
            }

            // "out-dent" books. Looks better.
            if (indent > 0) {
                --indent;
            }

        } else {
            // for all other types, the level determines the view
            switch (level) {
                case 1:
                    layoutId = R.layout.booksonbookshelf_row_level_1;
                    break;
                case 2:
                    layoutId = R.layout.booksonbookshelf_row_level_2;
                    break;

                default:
                    // level 3 and higher all use the same layout.
                    layoutId = R.layout.booksonbookshelf_row_level_3;
                    break;
            }
        }

        View view = mInflater.inflate(layoutId, parent, false);
        view.setPaddingRelative(indent * mLevelIndent, 0, 0, 0);

        // Scale text if required
        float scale = mStyle.getScaleFactor();
        if (scale != 1.0f) {
            scaleTextViews(scale, view);
        }
        return view;
    }

    /**
     * Scale text in a View (and children) as per user preferences.
     * <p>
     * Note that ImageView experiments from the original code never worked.
     * Bottom line is that Android will scale *down* (i.e. image to big ? make it smaller)
     * but will NOT scale up to fill the provided space. This means scaling needs to be done
     * at bind time (as we need <strong>actual</strong> size of the image), not at create time
     * of the view.
     * <br>So this method only deals with TextView instances.
     *
     * @param root the view (and its children) we'll scale
     */
    private void scaleTextViews(final float scale,
                                @NonNull final View root) {
        // text gets scaled
        if (root instanceof TextView) {
            TextView textView = (TextView) root;
            float px = textView.getTextSize();
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, px * scale);
        }

        // all elements get scaled padding; using the absolute padding values.
        root.setPadding((int) (scale * root.getPaddingLeft()),
                        (int) (scale * root.getPaddingTop()),
                        (int) (scale * root.getPaddingRight()),
                        (int) (scale * root.getPaddingBottom()));

        // go recursive if needed
        if (root instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) root;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                scaleTextViews(scale, viewGroup.getChildAt(i));
            }
        }
    }

    /**
     * Return a Holder object for the row pointed to by row.
     *
     * @return the holder
     */
    private RowViewHolder createHolder(final int viewType,
                                       @NonNull final View itemView) {

        // a BookHolder is based on multiple columns, the holder itself will sort them out.
        if (viewType == RowKind.BOOK) {
            return new BookHolder(itemView, mDb, mStyle);
        }

        // Except for CheckableStringHolder which uses an additional fixed column,
        // all other rows are based on a single column
        String columnName = RowKind.get(viewType).getDisplayDomain().name;
        //noinspection ConstantConditions
        int columnIndex = mCursor.getColumnIndex(columnName);
        if (columnIndex < 0) {
            throw new ColumnNotPresentException(columnName);
        }

        switch (viewType) {
            // NEWKIND: ROW_KIND_x

            case RowKind.AUTHOR:
                return new CheckableStringHolder(itemView, columnIndex,
                                                 DBDefinitions.KEY_AUTHOR_IS_COMPLETE,
                                                 R.string.hint_field_not_set_with_brackets);

            case RowKind.SERIES:
                return new CheckableStringHolder(itemView, columnIndex,
                                                 DBDefinitions.KEY_SERIES_IS_COMPLETE,
                                                 R.string.hint_field_not_set_with_brackets);

            // Months are displayed by name
            case RowKind.DATE_PUBLISHED_MONTH:
            case RowKind.DATE_FIRST_PUBLICATION_MONTH:
            case RowKind.DATE_ACQUIRED_MONTH:
            case RowKind.DATE_ADDED_MONTH:
            case RowKind.DATE_READ_MONTH:
            case RowKind.DATE_LAST_UPDATE_MONTH:
                return new MonthHolder(itemView, columnIndex,
                                       R.string.hint_field_not_set_with_brackets);

            // some special formatting holders
            case RowKind.RATING:
                return new RatingHolder(itemView, columnIndex,
                                        R.string.hint_field_not_set_with_brackets);
            case RowKind.LANGUAGE:
                return new LanguageHolder(itemView, columnIndex,
                                          R.string.hint_field_not_set_with_brackets);
            case RowKind.READ_STATUS:
                return new ReadUnreadHolder(itemView, columnIndex,
                                            R.string.hint_field_not_set_with_brackets);

            // plain old Strings
//            case RowKind.TITLE_LETTER:
//            case RowKind.PUBLISHER:
//            case RowKind.GENRE:
//            case RowKind.FORMAT:
//            case RowKind.LOCATION:
//            case RowKind.LOANED:
//            case RowKind.BOOKSHELF:
//            case RowKind.DATE_PUBLISHED_YEAR:
//            case RowKind.DATE_FIRST_PUBLICATION_YEAR:
//            case RowKind.DATE_ACQUIRED_YEAR:
//            case RowKind.DATE_ACQUIRED_DAY:
//            case RowKind.DATE_ADDED_YEAR:
//            case RowKind.DATE_ADDED_DAY:
//            case RowKind.DATE_READ_YEAR:
//            case RowKind.DATE_READ_DAY:
//            case RowKind.DATE_LAST_UPDATE_YEAR:
//            case RowKind.DATE_LAST_UPDATE_DAY:
            default:
                return new GenericStringHolder(itemView, columnIndex,
                                               R.string.hint_field_not_set_with_brackets);
        }
    }

    /**
     * Get the text to display for the row at the current cursor position.
     * <p>
     * <br>{@inheritDoc}
     */
    @Nullable
    @Override
    public String[] getSectionText(@NonNull final Context context,
                                   final int position) {

        // sanity check.
        if (mCursor == null || position < 0 || position >= getItemCount()) {
            return null;
        }

        String[] section;

        // temporary move the cursor to the requested position, restore after we got the text.
        synchronized (mCursor) {
            final int savedPos = mCursor.getPosition();
            mCursor.moveToPosition(position);
            BooklistMappedCursorRow row = ((BooklistSupportProvider) mCursor).getCursorRow();
            section = row.getLevelText(context);
            mCursor.moveToPosition(savedPos);
        }
        return section;
    }

    /**
     * Background task to get 'extra' details for a book row.
     * Doing this in a background task keeps the booklist cursor simple and small.
     * Used by {@link BookHolder}.
     * <p>
     * See {@link BooklistStyle#extrasByTask()}
     */
    private static class GetBookExtrasTask
            extends AsyncTask<Void, Void, Boolean> {

        /** Format string. */
        @NonNull
        private final String mX_bracket_Y_bracket;

        /** The listener for the tasks result. */
        @NonNull
        private final WeakReference<GetBookExtrasTaskFinishedListener> mTaskListener;

        /** Database Access. */
        @NonNull
        private final DAO mDb;

        /** Locale to use for formatting. */
        @NonNull
        private final Locale mLocale;

        /** The book ID to fetch. */
        private final long mBookId;

        private final int mExtraFields;

        /** Resulting data. */
        private final Bundle mResults = new Bundle();

        /**
         * Constructor.
         *
         * @param context      Current context
         * @param db           Database Access
         * @param bookId       Book to fetch
         * @param taskListener View holder for the book, used as callback for task results.
         * @param extraFields  bit mask with the fields that should be fetched.
         */
        @UiThread
        GetBookExtrasTask(@NonNull final Context context,
                          @NonNull final DAO db,
                          final long bookId,
                          @NonNull final GetBookExtrasTaskFinishedListener taskListener,
                          final int extraFields) {

            mLocale = LocaleUtils.from(context);
            mDb = db;
            mBookId = bookId;
            mTaskListener = new WeakReference<>(taskListener);
            mExtraFields = extraFields;

            mX_bracket_Y_bracket = context.getString(R.string.a_bracket_b_bracket);
        }

        @Override
        @WorkerThread
        protected Boolean doInBackground(final Void... params) {
            Thread.currentThread().setName("GetBookExtrasTask " + mBookId);

            try (Cursor cursor = mDb.fetchBookExtrasById(mBookId)) {
                // Bail out if we don't have a book.
                if (!cursor.moveToFirst()) {
                    return false;
                }

                CursorMapper mapper = new CursorMapper(cursor);

                String tmp;
                if ((mExtraFields & BooklistStyle.EXTRAS_AUTHOR) != 0) {
                    tmp = mapper.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_AUTHOR_FORMATTED, tmp);
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_LOCATION) != 0) {
                    tmp = mapper.getString(DBDefinitions.KEY_LOCATION);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_LOCATION, tmp);
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_FORMAT) != 0) {
                    tmp = mapper.getString(DBDefinitions.KEY_FORMAT);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_FORMAT, tmp);
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_BOOKSHELVES) != 0) {
                    tmp = mapper.getString(DBDefinitions.KEY_BOOKSHELF);
                    if (!tmp.isEmpty()) {
                        // note the destination is KEY_BOOKSHELF_CSV
                        mResults.putString(DBDefinitions.KEY_BOOKSHELF_CSV, tmp);
                    }
                }

                if ((mExtraFields & BooklistStyle.EXTRAS_ISBN) != 0) {
                    tmp = mapper.getString(DBDefinitions.KEY_ISBN);
                    if (!tmp.isEmpty()) {
                        mResults.putString(DBDefinitions.KEY_ISBN, tmp);
                    }
                }

                tmp = getPublisherAndPubDateText(mapper);
                if (tmp != null && !tmp.isEmpty()) {
                    mResults.putString(DBDefinitions.KEY_PUBLISHER, tmp);
                }

            } catch (@NonNull final NumberFormatException e) {
                Logger.error(this, e);
                return false;
            }
            return true;
        }

        @Nullable
        String getPublisherAndPubDateText(@NonNull final CursorMapper mapper) {
            String tmp = null;
            if ((mExtraFields & BooklistStyle.EXTRAS_PUBLISHER) != 0) {
                tmp = mapper.getString(DBDefinitions.KEY_PUBLISHER);
            }
            String tmpPubDate = null;
            if ((mExtraFields & BooklistStyle.EXTRAS_PUB_DATE) != 0) {
                tmpPubDate = mapper.getString(DBDefinitions.KEY_DATE_PUBLISHED);
            }
            // show combined Publisher and Pub. Date
            if ((tmp != null) && (tmpPubDate != null)) {
                if (tmpPubDate.length() == 4) {
                    // 4 digits is just the year.
                    tmp = String.format(mX_bracket_Y_bracket, tmp, tmpPubDate);
                } else if (tmpPubDate.length() > 4) {
                    // parse/format the date
                    tmp = String.format(mX_bracket_Y_bracket, tmp,
                                        DateUtils.toPrettyDate(mLocale, tmpPubDate));
                }
            } else if (tmpPubDate != null) {
                // there was no publisher, just use the date
                if (tmpPubDate.length() == 4) {
                    tmp = tmpPubDate;
                } else if (tmpPubDate.length() > 4) {
                    tmp = DateUtils.toPrettyDate(mLocale, tmpPubDate);
                }
            }

            return tmp;
        }

        @Override
        @UiThread
        protected void onPostExecute(@NonNull final Boolean result) {
            if (!result) {
                return;
            }
            // Fields not used will be null.
            if (mTaskListener.get() != null) {
                mTaskListener.get().onGetBookExtrasTaskFinished(mResults);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Logger.debug(this, "onPostExecute",
                                 Logger.WEAK_REFERENCE_TO_LISTENER_WAS_DEAD);
                }
            }
        }

        interface GetBookExtrasTaskFinishedListener {

            /**
             * Results from fetching the extras.
             * Theoretically individual fields could be {@code null} (but shouldn't).
             *
             * @param results a bundle with the result field.
             */
            void onGetBookExtrasTaskFinished(@NonNull Bundle results);
        }
    }

    /**
     * Base for all row Holder classes.
     */
    abstract static class RowViewHolder
            extends RecyclerView.ViewHolder {

        /** Absolute position of this row. */
        int absolutePosition;

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         */
        RowViewHolder(@NonNull final View itemView) {
            super(itemView);
        }

        /**
         * Bind the data to the views in the holder.
         *
         * @param rowData the data to bind
         * @param style   to use
         */
        @CallSuper
        void onBindViewHolder(@NonNull final CursorMapper rowData,
                              @NonNull final BooklistStyle style) {
            absolutePosition = rowData.getInt(DBDefinitions.KEY_BL_ABSOLUTE_POSITION);
        }
    }

    /**
     * Holder for a {@link RowKind#BOOK} row.
     */
    public static class BookHolder
            extends RowViewHolder {

        /** View that stores the related book field. */
        final TextView titleView;

        /** The "I've read it" checkbox. */
        final CompoundButton readView;
        /** The "on loan" checkbox. */
        final CompoundButton onLoanView;

        /** View that stores the related book field. */
        final ImageView coverView;

        /** View that stores the series number when it is a short piece of text. */
        final TextView seriesNumView;
        /** View that stores the series number when it is a long piece of text. */
        final TextView seriesNumLongView;

        /** View that stores the related book field. */
        final TextView authorView;
        /** View that stores the related book field. */
        final TextView publisherView;
        /** View that stores the related book field. */
        final TextView isbnView;
        /** View that stores the related book field. */
        final TextView formatView;

        /** View that stores the related book field. */
        final TextView locationView;
        /** View that stores the related book field. */
        final TextView bookshelvesView;

        /** Database Access. */
        @NonNull
        private final DAO mDb;

        /** Bookshelves label resource string. */
        @NonNull
        private final String mShelvesLabel;
        /** Location label resource string. */
        @NonNull
        private final String mLocationLabel;

        @NonNull
        private final Locale mLocale;

        private final boolean mPublisherIsUsed;
        private final boolean mPubDateIsUsed;
        private final boolean mReadIsUsed;
        private final boolean mSeriesIsUsed;
        private final boolean mLoaneeIsUsed;
        private final boolean mCoverIsUsed;
        private final int mExtraFieldsUsed;

        private final int mMaxCoverSize;

        /** Format string. */
        @NonNull
        private final String mName_colon_value;
        @NonNull
        private final String mX_bracket_Y_bracket;

        private final GetBookExtrasTask.GetBookExtrasTaskFinishedListener mTaskListener =
                new GetBookExtrasTask.GetBookExtrasTaskFinishedListener() {
                    @Override
                    public void onGetBookExtrasTaskFinished(@NonNull final Bundle results) {
                        showOrHide(authorView,
                                   results.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                        showOrHide(publisherView,
                                   results.getString(DBDefinitions.KEY_PUBLISHER));
                        showOrHide(isbnView,
                                   results.getString(DBDefinitions.KEY_ISBN));
                        showOrHide(formatView,
                                   results.getString(DBDefinitions.KEY_FORMAT));
                        showOrHide(locationView, mLocationLabel,
                                   results.getString(DBDefinitions.KEY_LOCATION));
                        showOrHide(bookshelvesView, mShelvesLabel,
                                   results.getString(DBDefinitions.KEY_BOOKSHELF_CSV));
                    }
                };

        /**
         * Constructor.
         *
         * @param itemView the view specific for this holder
         * @param db       Database Access.
         * @param style    to use
         */
        BookHolder(@NonNull final View itemView,
                   @NonNull final DAO db,
                   @NonNull final BooklistStyle style) {
            super(itemView);
            mDb = db;

            Context context = itemView.getContext();
            // fetch once and re-use later.
            mName_colon_value = context.getString(R.string.name_colon_value);
            mX_bracket_Y_bracket = context.getString(R.string.a_bracket_b_bracket);
            mShelvesLabel = context.getString(R.string.lbl_bookshelves);
            mLocationLabel = context.getString(R.string.lbl_location);

            mLocale = LocaleUtils.from(context);

            mPublisherIsUsed = style.isUsed(DBDefinitions.KEY_PUBLISHER);
            mPubDateIsUsed = style.isUsed(DBDefinitions.KEY_DATE_PUBLISHED);
            mReadIsUsed = App.isUsed(DBDefinitions.KEY_READ);
            mSeriesIsUsed = App.isUsed(DBDefinitions.KEY_SERIES_TITLE);
            mLoaneeIsUsed = App.isUsed(DBDefinitions.KEY_LOANEE);
            mCoverIsUsed = style.isUsed(UniqueId.BKEY_IMAGE);
            mExtraFieldsUsed = style.getExtraFieldsStatus();

            mMaxCoverSize = ImageUtils.getMaxImageSize(style.getThumbnailScaleFactor());
            // always visible
            titleView = itemView.findViewById(R.id.title);

            // visibility is independent from actual data, so set here.
            readView = itemView.findViewById(R.id.read);
            readView.setVisibility(mReadIsUsed ? View.VISIBLE : View.GONE);

            // visibility is independent from actual data, so set here.
            onLoanView = itemView.findViewById(R.id.on_loan);
            onLoanView.setVisibility(mLoaneeIsUsed ? View.VISIBLE : View.GONE);

            // visibility is independent from actual data, so set here.
            coverView = itemView.findViewById(R.id.coverImage);
            coverView.setVisibility(mCoverIsUsed ? View.VISIBLE : View.GONE);

            // visibility depends on actual data
            seriesNumView = itemView.findViewById(R.id.series_num);
            seriesNumView.setVisibility(mSeriesIsUsed ? View.VISIBLE : View.GONE);
            seriesNumLongView = itemView.findViewById(R.id.series_num_long);
            seriesNumLongView.setVisibility(mSeriesIsUsed ? View.VISIBLE : View.GONE);

            // Lookup all the 'extras' fields and hide them all by default.
            authorView = itemView.findViewById(R.id.author);
            authorView.setVisibility(View.GONE);
            publisherView = itemView.findViewById(R.id.publisher);
            publisherView.setVisibility(View.GONE);
            isbnView = itemView.findViewById(R.id.isbn);
            isbnView.setVisibility(View.GONE);
            formatView = itemView.findViewById(R.id.format);
            formatView.setVisibility(View.GONE);
            locationView = itemView.findViewById(R.id.location);
            locationView.setVisibility(View.GONE);
            bookshelvesView = itemView.findViewById(R.id.shelves);
            bookshelvesView.setVisibility(View.GONE);
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorMapper rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            titleView.setText(rowData.getString(DBDefinitions.KEY_TITLE));

            if (mReadIsUsed) {
                readView.setChecked(rowData.getInt(DBDefinitions.KEY_READ) != 0);
            }

            if (mLoaneeIsUsed && rowData.contains(DBDefinitions.KEY_LOANEE_AS_BOOLEAN)) {
                onLoanView.setChecked(!rowData.getBoolean(DBDefinitions.KEY_LOANEE_AS_BOOLEAN));
            }

            if (mCoverIsUsed) {
                // store the uuid for use in the onClick
                coverView.setTag(R.id.TAG_UUID, rowData.getString(DBDefinitions.KEY_BOOK_UUID));

                String uuid = rowData.getString(DBDefinitions.KEY_BOOK_UUID);
                boolean isSet = ImageUtils.setImageView(coverView, uuid,
                                                        mMaxCoverSize, mMaxCoverSize);
                if (isSet) {
                    //Allow zooming by clicking on the image
                    coverView.setOnClickListener(v -> {
                        FragmentActivity activity = (FragmentActivity) v.getContext();
                        String currentUuid = (String) v.getTag(R.id.TAG_UUID);
                        ZoomedImageDialogFragment.show(activity.getSupportFragmentManager(),
                                                       StorageUtils.getCoverFile(currentUuid));
                    });
                }
            }

            if (mSeriesIsUsed) {
                String number = rowData.getString(DBDefinitions.KEY_BOOK_NUM_IN_SERIES);
                if (!number.isEmpty()) {
                    // Display it in one of the views, based on the size of the text.
                    if (number.length() > 4) {
                        seriesNumView.setVisibility(View.GONE);
                        seriesNumLongView.setText(number);
                        seriesNumLongView.setVisibility(View.VISIBLE);
                    } else {
                        seriesNumView.setText(number);
                        seriesNumView.setVisibility(View.VISIBLE);
                        seriesNumLongView.setVisibility(View.GONE);
                    }
                } else {
                    seriesNumView.setVisibility(View.GONE);
                    seriesNumLongView.setVisibility(View.GONE);
                }
            }

            if (style.extrasByTask()) {
                // If there are extras to get, start a background task.
                if ((mExtraFieldsUsed & BooklistStyle.EXTRAS_BY_TASK) != 0) {
                    // Fill in the extras field as blank initially.
                    authorView.setText("");
                    bookshelvesView.setText("");

                    publisherView.setText("");
                    isbnView.setText("");
                    formatView.setText("");
                    locationView.setText("");

                    // Queue the task.
                    new GetBookExtrasTask(itemView.getContext(), mDb,
                                          rowData.getLong(DBDefinitions.KEY_FK_BOOK),
                                          mTaskListener, mExtraFieldsUsed)
                            .execute();
                }
            } else {
                showOrHide(style, rowData, DBDefinitions.KEY_BOOKSHELF_CSV, bookshelvesView);

                showOrHide(style, rowData, DBDefinitions.KEY_AUTHOR_FORMATTED, authorView);
                showOrHide(style, rowData, DBDefinitions.KEY_ISBN, isbnView);
                showOrHide(style, rowData, DBDefinitions.KEY_FORMAT, formatView);
                showOrHide(style, rowData, DBDefinitions.KEY_LOCATION, locationView);

                if (mPublisherIsUsed || mPubDateIsUsed) {
                    showOrHide(publisherView, getPublisherAndPubDateText(style, rowData));
                }
            }
        }

        /**
         * Temporarily duplication.
         * {@link GetBookExtrasTask#getPublisherAndPubDateText}
         */
        @Nullable
        String getPublisherAndPubDateText(@NonNull final BooklistStyle style,
                                          @NonNull final CursorMapper mapper) {
            String tmp = null;
            if (style.isUsed(DBDefinitions.KEY_PUBLISHER)) {
                tmp = mapper.getString(DBDefinitions.KEY_PUBLISHER);
            }
            String tmpPubDate = null;
            if (style.isUsed(DBDefinitions.KEY_DATE_PUBLISHED)) {
                tmpPubDate = mapper.getString(DBDefinitions.KEY_DATE_PUBLISHED);
            }
            // show combined Publisher and Pub. Date
            if ((tmp != null) && (tmpPubDate != null)) {
                if (tmpPubDate.length() == 4) {
                    // 4 digits is just the year.
                    tmp = String.format(mX_bracket_Y_bracket, tmp, tmpPubDate);
                } else if (tmpPubDate.length() > 4) {
                    // parse/format the date
                    tmp = String.format(mX_bracket_Y_bracket, tmp,
                                        DateUtils.toPrettyDate(mLocale, tmpPubDate));
                }
            } else if (tmpPubDate != null) {
                // there was no publisher, just use the date
                if (tmpPubDate.length() == 4) {
                    tmp = tmpPubDate;
                } else if (tmpPubDate.length() > 4) {
                    tmp = DateUtils.toPrettyDate(mLocale, tmpPubDate);
                }
            }

            return tmp;
        }

        /**
         * Conditionally display 'text'.
         */
        private void showOrHide(@NonNull final TextView view,
                                @Nullable final String text) {
            if (text != null && !text.isEmpty()) {
                view.setText(text);
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
        }

        /**
         * Conditionally display 'label: text'.
         */
        private void showOrHide(@NonNull final TextView view,
                                @NonNull final String label,
                                @Nullable final String text) {
            if (text != null && !text.isEmpty()) {
                view.setText(String.format(mName_colon_value, label, text));
                view.setVisibility(View.VISIBLE);
            } else {
                view.setVisibility(View.GONE);
            }
        }

        void showOrHide(@NonNull final BooklistStyle style,
                        @NonNull final CursorMapper rowData,
                        @NonNull final String key,
                        @NonNull final TextView view) {
            if (style.isUsed(key)) {
                if (rowData.contains(key)) {
                    showOrHide(view, rowData.getString(key));
                } else {
                    view.setVisibility(View.GONE);
                }
            }
        }
    }

    /**
     * Holder to handle any field that can be displayed as a simple string.
     * Assumes there is a 'name' TextView and an optional enclosing ViewGroup called ROW_INFO.
     */
    public static class GenericStringHolder
            extends RowViewHolder {

        /** Index of related data column. */
        final int mSourceCol;
        /** String ID to use when data is blank. */
        @StringRes
        final int mNoDataId;

        /*** View to populate. */
        @NonNull
        final
        TextView mTextView;
        /** Pointer to the container of all info for this row. */
        @NonNull
        private final View mRowDetailsView;
        /**
         * (optional) Pointer to the constraint group that controls visibility of all widgets
         * inside a ViewGroup. Used with ConstraintLayout only.
         */
        @Nullable
        private final View mVisibilityControlView;

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        GenericStringHolder(@NonNull final View itemView,
                            final int columnIndex,
                            @StringRes final int noDataId) {
            super(itemView);
            mSourceCol = columnIndex;
            mNoDataId = noDataId;

            mRowDetailsView = itemView.findViewById(R.id.BLB_ROW_DETAILS);
            mTextView = itemView.findViewById(R.id.name);
            // optional
            mVisibilityControlView = mRowDetailsView.findViewById(R.id.group);
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorMapper rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            setText(rowData.getString(mSourceCol), rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
        }

        /**
         * Syntax sugar.
         *
         * @return {@code true} if the details view is visible.
         */
        public boolean isVisible() {
            return mRowDetailsView.getVisibility() == View.VISIBLE;
        }

        /**
         * For a simple row, just set the text (or hide it).
         *
         * @param textId String to display
         * @param level  for this row
         */
        public void setText(@StringRes final int textId,
                            @IntRange(from = 1) final int level) {
            setText(mTextView.getContext().getString(textId), level);
        }

        /**
         * For a simple row, just set the text (or hide it).
         *
         * @param text  String to display; can be {@code null} or empty
         * @param level for this row
         */
        public void setText(@Nullable final String text,
                            @IntRange(from = 1) final int level) {
            int visibility = View.VISIBLE;

            if (text != null && !text.isEmpty()) {
                // if we have text, show it.
                mTextView.setText(text);
            } else {
                // we don't have text, but...
                if (level == 1) {
                    // we never hide level 1 and show the place holder text instead.
                    mTextView.setText(mNoDataId);
                } else {
                    visibility = View.GONE;
                }
            }

            mRowDetailsView.setVisibility(visibility);

            /*
                this is really annoying: setting visibility of the ConstraintLayout to GONE
                does NOT shrink it to size zero. You're forced to set all widgets inside also.
                Potentially this could be solved by fiddling with the constraints more.
            */
            if (mVisibilityControlView != null) {
                mVisibilityControlView.setVisibility(visibility);
            }
        }
    }

    /**
     * Holder for a row that displays a 'rating'.
     */
    public static class RatingHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        RatingHolder(@NonNull final View itemView,
                     final int columnIndex,
                     @StringRes final int noDataId) {
            super(itemView, columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorMapper rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            String s = rowData.getString(mSourceCol);
            if (s != null) {
                try {
                    int i = (int) Float.parseFloat(s);
                    // If valid, format the description
                    if (i >= 0 && i <= Book.RATING_STARS) {
                        s = itemView.getResources().getQuantityString(R.plurals.n_stars, i, i);
                    }
                } catch (@NonNull final NumberFormatException e) {
                    Logger.error(this, e);
                }
            }
            setText(s, rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
        }
    }

    /**
     * Holder for a row that displays a 'language'.
     */
    public static class LanguageHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        LanguageHolder(@NonNull final View itemView,
                       final int columnIndex,
                       @StringRes final int noDataId) {
            super(itemView, columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorMapper rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            String s = rowData.getString(mSourceCol);
            if (s != null && !s.isEmpty()) {
                s = LocaleUtils.getDisplayName(itemView.getContext(), s);
            }
            setText(s, rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
        }
    }

    /**
     * Holder for a row that displays a 'read/unread' (as text) status.
     */
    public static class ReadUnreadHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        ReadUnreadHolder(@NonNull final View itemView,
                         final int columnIndex,
                         @StringRes final int noDataId) {
            super(itemView, columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorMapper rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            if (DataManager.toBoolean(rowData.getString(mSourceCol), true)) {
                setText(R.string.lbl_read, rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
            } else {
                setText(R.string.lbl_unread, rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
            }
        }
    }

    /**
     * Holder for a row that displays a 'month'.
     * This code turns a month number into a locale-based month name.
     */
    public static class MonthHolder
            extends GenericStringHolder {

        /**
         * Constructor.
         *
         * @param itemView    the view specific for this holder
         * @param columnIndex index in SQL result set
         * @param noDataId    String ID to use when data is blank
         */
        MonthHolder(@NonNull final View itemView,
                    final int columnIndex,
                    @StringRes final int noDataId) {
            super(itemView, columnIndex, noDataId);
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorMapper rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            String s = rowData.getString(mSourceCol);
            if (s != null) {
                Locale locale = LocaleUtils.from(itemView.getContext());
                try {
                    int i = Integer.parseInt(s);
                    // If valid, get the short name
                    if (i > 0 && i <= 12) {
                        s = DateUtils.getMonthName(locale, i, false);
                    }
                } catch (@NonNull final NumberFormatException e) {
                    Logger.error(this, e);
                }
            }
            setText(s, rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL));
        }
    }

    /**
     * Holder for a row that displays a generic string, but with a 'lock' icon at the 'end'.
     */
    public static class CheckableStringHolder
            extends GenericStringHolder {

        /** Column name of related boolean column. */
        private final String mIsLockedSourceCol;

        /**
         * Constructor.
         *
         * @param itemView       the view specific for this holder
         * @param columnIndex    index in SQL result set
         * @param isLockedSource Column name to use for the boolean 'lock' status
         * @param noDataId       String ID to use when data is blank
         */
        CheckableStringHolder(@NonNull final View itemView,
                              final int columnIndex,
                              @NonNull final String isLockedSource,
                              @StringRes final int noDataId) {
            super(itemView, columnIndex, noDataId);
            mIsLockedSourceCol = isLockedSource;
        }

        @Override
        public void onBindViewHolder(@NonNull final CursorMapper rowData,
                                     @NonNull final BooklistStyle style) {
            super.onBindViewHolder(rowData, style);

            Drawable lock = null;
            if (isVisible() && rowData.getBoolean(mIsLockedSourceCol)) {
                lock = mTextView.getContext().getDrawable(R.drawable.ic_lock);
            }

            mTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    null, null, lock, null);
        }
    }
}
