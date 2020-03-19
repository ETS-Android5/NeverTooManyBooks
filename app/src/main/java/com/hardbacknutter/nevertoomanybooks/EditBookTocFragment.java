/*
 * @Copyright 2020 HardBackNutter
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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogTocConfirmBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookTocBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditTocEntryDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.picker.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.Edition;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbGetBookTask;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbGetEditionsTask;
import com.hardbacknutter.nevertoomanybooks.searches.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.viewmodels.UpdateFieldsModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.IsfdbEditionsTaskModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.tasks.IsfdbGetBookTaskModel;
import com.hardbacknutter.nevertoomanybooks.widgets.DiacriticArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewAdapterBase;
import com.hardbacknutter.nevertoomanybooks.widgets.RecyclerViewViewHolderBase;
import com.hardbacknutter.nevertoomanybooks.widgets.SimpleAdapterDataObserver;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.SimpleItemTouchHelperCallback;
import com.hardbacknutter.nevertoomanybooks.widgets.ddsupport.StartDragListener;

/**
 * This class is called by {@link EditBookFragment} and displays the Content Tab.
 * <p>
 * Doesn't use {@link UpdateFieldsModel}
 * <p>
 * The ISFDB direct interaction should however be seen as temporary as this class should not
 * have to know about any specific search web site.
 * <p>
 * This is still not obsolete as the standard search engines can only return a single book,
 * and hence a single TOC. The interaction here with ISFDB allows the user to reject the first
 * (book)TOC found, and get the next one (etc...).
 */
public class EditBookTocFragment
        extends EditBookBaseFragment {

    /** If the list changes, the book is dirty. */
    private final SimpleAdapterDataObserver mAdapterDataObserver =
            new SimpleAdapterDataObserver() {
                @Override
                public void onChanged() {
                    mBookViewModel.setDirty(true);
                }
            };

    /** View Binding. */
    private FragmentEditBookTocBinding mVb;

    /** The book. */
    @Nullable
    private String mIsbn;
    /** primary author of the book. */
    private Author mBookAuthor;
    /** the rows. */
    private ArrayList<TocEntry> mList;
    /** The adapter for the list. */
    private TocListEditAdapter mListAdapter;
    /** Drag and drop support for the list view. */
    private ItemTouchHelper mItemTouchHelper;

    /**
     * ISFDB editions of a book(isbn).
     * We'll try them one by one if the user asks for a re-try.
     */
    @Nullable
    private ArrayList<Edition> mIsfdbEditions;

    private IsfdbEditionsTaskModel mIsfdbEditionsTaskModel;
    private IsfdbGetBookTaskModel mIsfdbGetBookTaskModel;

    /** Hold the item position in the ist while we're editing an item. */
    @Nullable
    private Integer mEditPosition;
    private DiacriticArrayAdapter<String> mAuthorAdapter;

    private final ConfirmTocDialogFragment.ConfirmTocResults mConfirmTocResultsListener =
            new ConfirmTocDialogFragment.ConfirmTocResults() {
                /**
                 * The user approved, so add the TOC to the list and refresh the screen.
                 */
                @Override
                public void commitIsfdbData(@Book.TocBits final long tocBitMask,
                                            @NonNull final List<TocEntry> tocEntries) {
                    if (tocBitMask != 0) {
                        Book book = mBookViewModel.getBook();
                        book.putLong(DBDefinitions.KEY_TOC_BITMASK, tocBitMask);
                        populateTocBits(book);
                    }

                    // append! the new data
                    mList.addAll(tocEntries);
                    mListAdapter.notifyDataSetChanged();
                }

                /**
                 * Start a task to get the next edition of this book (that we know of).
                 */
                @Override
                public void getNextEdition() {
                    // remove the top one, and try again
                    mIsfdbEditions.remove(0);
                    searchIsfdb();
                }
            };
    private final EditTocEntryDialogFragment.EditTocEntryResults mEditTocEntryResultsListener =
            new EditTocEntryDialogFragment.EditTocEntryResults() {
                /**
                 * Add the author/title from the edit fields as a new row in the TOC list.
                 */
                @Override
                public void addOrUpdateEntry(@NonNull final TocEntry tocEntry,
                                             final boolean hasMultipleAuthors) {

                    updateMultiAuthor(hasMultipleAuthors);

                    if (mEditPosition == null) {
                        // add the new entry
                        mList.add(tocEntry);
                    } else {
                        // find and update
                        TocEntry original = mList.get(mEditPosition);
                        original.copyFrom(tocEntry);
                    }
                    mListAdapter.notifyDataSetChanged();
                }
            };

    private void searchIsfdb() {
        if (mIsfdbEditions != null && !mIsfdbEditions.isEmpty()) {
            final IsfdbGetBookTask task = new IsfdbGetBookTask(mIsfdbEditions,
                                                               isAddSeriesFromToc(),
                                                               mIsfdbGetBookTaskModel
                                                                       .getTaskListener());
            mIsfdbGetBookTaskModel.setTask(task);
            task.execute();
        } else {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_no_editions, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {
        if (ConfirmTocDialogFragment.TAG.equals(childFragment.getTag())) {
            ((ConfirmTocDialogFragment) childFragment).setListener(mConfirmTocResultsListener);

        } else if (EditTocEntryDialogFragment.TAG.equals(childFragment.getTag())) {
            ((EditTocEntryDialogFragment) childFragment).setListener(mEditTocEntryResultsListener);
        }
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditBookTocBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mIsfdbEditionsTaskModel = new ViewModelProvider(this).get(IsfdbEditionsTaskModel.class);
        mIsfdbEditionsTaskModel.onTaskFinished().observe(getViewLifecycleOwner(),
                                                         this::onGetEditionsTaskFinished);
        mIsfdbGetBookTaskModel = new ViewModelProvider(this).get(IsfdbGetBookTaskModel.class);
        mIsfdbGetBookTaskModel.onTaskFinished().observe(getViewLifecycleOwner(),
                                                        this::onGetBookTaskFinished);

        // set up the list view. The adapter is setup in onPopulateViews
        final LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mVb.tocList.setLayoutManager(linearLayoutManager);
        //noinspection ConstantConditions
        mVb.tocList.addItemDecoration(
                new DividerItemDecoration(getContext(), linearLayoutManager.getOrientation()));
        mVb.tocList.setHasFixedSize(true);

        mVb.cbxMultipleAuthors.setOnCheckedChangeListener(
                (v, isChecked) -> updateMultiAuthor(isChecked));
        // adding a new entry
        mVb.btnAdd.setOnClickListener(v -> onAdd());

        //noinspection ConstantConditions
        ViewFocusOrder.fix(getView());
    }

    /**
     * we got one or more editions from ISFDB.
     * Stores the url's locally as the user might want to try the next in line
     */
    private void onGetEditionsTaskFinished(
            @NonNull final TaskListener.FinishMessage<ArrayList<Edition>> message) {
        mIsfdbEditions = message.result != null ? message.result : new ArrayList<>();
        searchIsfdb();
    }

    private void onGetBookTaskFinished(@NonNull final TaskListener.FinishMessage<Bundle> message) {
        Bundle bookData = message.result;
        if (bookData == null) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_book_not_found, Snackbar.LENGTH_LONG)
                    .show();
            return;
        }

        Book book = mBookViewModel.getBook();

        // update the book with Series information that was gathered from the TOC
        List<Series> series = bookData.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
        if (series != null && !series.isEmpty()) {
            ArrayList<Series> inBook = book.getParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY);
            // add, weeding out duplicates
            for (Series s : series) {
                if (!inBook.contains(s)) {
                    inBook.add(s);
                }
            }
            book.putParcelableArrayList(UniqueId.BKEY_SERIES_ARRAY, inBook);
        }

        // update the book with the first publication date that was gathered from the TOC
        final String bookFirstPublication =
                bookData.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION);
        if (bookFirstPublication != null) {
            if (book.getString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION).isEmpty()) {
                book.putString(DBDefinitions.KEY_DATE_FIRST_PUBLICATION, bookFirstPublication);
            }
        }

        // finally the TOC itself;  only put on display for the user to approve
        boolean hasOtherEditions = (mIsfdbEditions != null) && (mIsfdbEditions.size() > 1);
        ConfirmTocDialogFragment.newInstance(bookData, hasOtherEditions)
                                .show(getChildFragmentManager(), ConfirmTocDialogFragment.TAG);
    }

    @Override
    void onPopulateViews(@NonNull final Book book) {
        super.onPopulateViews(book);

        // used to call Search sites to populate the TOC
        mIsbn = book.getString(DBDefinitions.KEY_ISBN);

        // Author to use if mVb.cbxMultipleAuthors is set to false
        final List<Author> authorList = book.getParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY);
        if (!authorList.isEmpty()) {
            mBookAuthor = authorList.get(0);
        } else {
            //noinspection ConstantConditions
            mBookAuthor = Author.createUnknownAuthor(getContext());
        }

        // Populate the list view with the book content table.
        mList = book.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);

        //noinspection ConstantConditions
        mListAdapter = new TocListEditAdapter(getContext(), mList,
                                              vh -> mItemTouchHelper.startDrag(vh));
        mListAdapter.registerAdapterDataObserver(mAdapterDataObserver);
        mVb.tocList.setAdapter(mListAdapter);

        SimpleItemTouchHelperCallback sitHelperCallback =
                new SimpleItemTouchHelperCallback(mListAdapter);
        mItemTouchHelper = new ItemTouchHelper(sitHelperCallback);
        mItemTouchHelper.attachToRecyclerView(mVb.tocList);

        populateTocBits(book);

        // hide unwanted fields
        //noinspection ConstantConditions
        mFragmentVM.getFields().resetVisibility(getView(), false, false);
    }

    @Override
    public void onSaveFields(@NonNull final Book book) {
        super.onSaveFields(book);

        book.setBit(DBDefinitions.KEY_TOC_BITMASK, Book.TOC_MULTIPLE_WORKS,
                    mVb.cbxIsAnthology.isChecked());
        book.setBit(DBDefinitions.KEY_TOC_BITMASK, Book.TOC_MULTIPLE_AUTHORS,
                    mVb.cbxMultipleAuthors.isChecked());

        // The toc list is not a 'real' field. Hence the need to store it manually here.
        book.putParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY, mList);
    }

    @Override
    public boolean hasUnfinishedEdits() {
        // We only check the title field; disregarding the author and first-publication fields.
        //noinspection ConstantConditions
        return !mVb.title.getText().toString().isEmpty();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_POPULATE_TOC_FROM_ISFDB, 0, R.string.isfdb_menu_populate_toc)
            .setIcon(R.drawable.ic_autorenew);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_POPULATE_TOC_FROM_ISFDB: {
                long isfdbId = mBookViewModel.getBook().getLong(DBDefinitions.KEY_EID_ISFDB);
                if (isfdbId != 0) {
                    //noinspection ConstantConditions
                    Snackbar.make(getView(), R.string.progress_msg_connecting,
                                  Snackbar.LENGTH_LONG).show();
                    final IsfdbGetBookTask task =
                            new IsfdbGetBookTask(isfdbId, isAddSeriesFromToc(),
                                                 mIsfdbGetBookTaskModel.getTaskListener());
                    mIsfdbGetBookTaskModel.setTask(task);
                    task.execute();
                    return true;
                }

                if (mIsbn != null && !mIsbn.isEmpty()) {
                    final ISBN isbn = ISBN.createISBN(mIsbn);
                    if (isbn.isValid(true)) {
                        //noinspection ConstantConditions
                        Snackbar.make(getView(), R.string.progress_msg_connecting,
                                      Snackbar.LENGTH_LONG).show();
                        final IsfdbGetEditionsTask task =
                                new IsfdbGetEditionsTask(isbn.asText(),
                                                         mIsfdbEditionsTaskModel.getTaskListener());
                        mIsfdbEditionsTaskModel.setTask(task);
                        task.execute();
                        return true;
                    }
                }
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.warning_requires_isbn,
                              Snackbar.LENGTH_LONG).show();
                return false;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onCreateContextMenu(final int position) {
        final Resources r = getResources();

        final TocEntry item = mList.get(position);
        //noinspection ConstantConditions
        final Menu menu = MenuPicker.createMenu(getContext());
        menu.add(Menu.NONE, R.id.MENU_EDIT,
                 r.getInteger(R.integer.MENU_ORDER_EDIT),
                 R.string.menu_edit)
            .setIcon(R.drawable.ic_edit);
        menu.add(Menu.NONE, R.id.MENU_DELETE,
                 r.getInteger(R.integer.MENU_ORDER_DELETE),
                 R.string.menu_delete)
            .setIcon(R.drawable.ic_delete);

        final String title = item.getLabel(getContext());
        new MenuPicker<>(getContext(), title, menu, position, this::onContextItemSelected)
                .show();
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@NonNull final MenuItem menuItem,
                                          @NonNull final Integer position) {
        final TocEntry tocEntry = mList.get(position);

        switch (menuItem.getItemId()) {
            case R.id.MENU_EDIT:
                editEntry(tocEntry, position);
                return true;

            case R.id.MENU_DELETE:
                //noinspection ConstantConditions
                StandardDialogs.deleteTocEntry(getContext(), tocEntry, () -> {
                    if (mFragmentVM.getDb().deleteTocEntry(tocEntry.getId()) == 1) {
                        mList.remove(tocEntry);
                        mListAdapter.notifyItemRemoved(position);
                    }
                });
                return true;

            default:
                return false;
        }
    }

    private void populateTocBits(@NonNull final Book book) {
        mVb.cbxIsAnthology.setChecked(book.isBitSet(DBDefinitions.KEY_TOC_BITMASK,
                                                    Book.TOC_MULTIPLE_WORKS));
        updateMultiAuthor(book.isBitSet(DBDefinitions.KEY_TOC_BITMASK,
                                        Book.TOC_MULTIPLE_AUTHORS));
    }

    private void updateMultiAuthor(final boolean isChecked) {
        mVb.cbxMultipleAuthors.setChecked(isChecked);
        if (isChecked) {
            if (mAuthorAdapter == null) {
                //noinspection ConstantConditions
                mAuthorAdapter = new DiacriticArrayAdapter<>(
                        getContext(), R.layout.dropdown_menu_popup_item,
                        mFragmentVM.getAuthorNames());
                mVb.author.setAdapter(mAuthorAdapter);
            }

            //noinspection ConstantConditions
            mVb.author.setText(mBookAuthor.getLabel(getContext()));
            mVb.author.selectAll();
            mVb.lblAuthor.setVisibility(View.VISIBLE);
            mVb.author.setVisibility(View.VISIBLE);
        } else {
            mVb.lblAuthor.setVisibility(View.GONE);
            mVb.author.setVisibility(View.GONE);
        }
    }

    /**
     * Create a new entry.
     */
    private void onAdd() {
        final Author author;
        if (mVb.cbxMultipleAuthors.isChecked()) {
            author = Author.fromString(mVb.author.getText().toString().trim());
        } else {
            author = mBookAuthor;
        }
        //noinspection ConstantConditions
        final TocEntry tocEntry = new TocEntry(author,
                                               mVb.title.getText().toString().trim(),
                                               mVb.firstPublication.getText().toString().trim());
        mList.add(tocEntry);

        if (mVb.cbxMultipleAuthors.isChecked()) {
            //noinspection ConstantConditions
            mVb.author.setText(mBookAuthor.getLabel(getContext()));
            mVb.author.selectAll();
        }
        mVb.title.setText("");
        mVb.firstPublication.setText("");
    }

    /**
     * Start the fragment dialog to edit a Bookshelf.
     *
     * @param tocEntry to edit
     * @param position the item position which will be used to update the data after editing.
     */
    private void editEntry(@NonNull final TocEntry tocEntry,
                           @Nullable final Integer position) {
        mEditPosition = position;

        EditTocEntryDialogFragment.newInstance(tocEntry, mVb.cbxMultipleAuthors.isChecked())
                                  .show(getChildFragmentManager(), EditTocEntryDialogFragment.TAG);

    }

    private boolean isAddSeriesFromToc() {
        //noinspection ConstantConditions
        return PreferenceManager.getDefaultSharedPreferences(getContext())
                                .getBoolean(IsfdbSearchEngine.PREFS_SERIES_FROM_TOC, false);
    }

    /**
     * Dialog that shows the downloaded TOC titles for approval by the user.
     * <p>
     * Show with the {@link Fragment#getChildFragmentManager()}
     * <p>
     * Uses {@link Fragment#getParentFragment()} for sending results back.
     */
    public static class ConfirmTocDialogFragment
            extends DialogFragment {

        /** Log tag. */
        private static final String TAG = "ConfirmTocDialogFrag";

        private static final String BKEY_HAS_OTHER_EDITIONS = TAG + ":hasOtherEditions";

        private boolean mHasOtherEditions;
        @Book.TocBits
        private long mTocBitMask;
        private ArrayList<TocEntry> mTocEntries;

        private WeakReference<ConfirmTocResults> mListener;

        /**
         * Constructor.
         *
         * @param hasOtherEditions flag
         *
         * @return the instance
         */
        static ConfirmTocDialogFragment newInstance(@NonNull final Bundle bookData,
                                                    final boolean hasOtherEditions) {
            final ConfirmTocDialogFragment frag = new ConfirmTocDialogFragment();
            bookData.putBoolean(BKEY_HAS_OTHER_EDITIONS, hasOtherEditions);
            frag.setArguments(bookData);
            return frag;
        }

        /**
         * Call this from {@link #onAttachFragment} in the parent.
         *
         * @param listener the object to send the result to.
         */
        void setListener(@NonNull final ConfirmTocResults listener) {
            mListener = new WeakReference<>(listener);
        }

        @Override
        public void onCreate(@Nullable final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final Bundle args = requireArguments();
            mTocEntries = args.getParcelableArrayList(UniqueId.BKEY_TOC_ENTRY_ARRAY);
            Objects.requireNonNull(mTocEntries, ErrorMsg.ARGS_MISSING_TOC_ENTRIES);

            mTocBitMask = args.getLong(DBDefinitions.KEY_TOC_BITMASK);
            mHasOtherEditions = args.getBoolean(BKEY_HAS_OTHER_EDITIONS, false);
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
            // Reminder: *always* use the activity inflater here.
            //noinspection ConstantConditions
            final LayoutInflater inflater = getActivity().getLayoutInflater();
            // custom payout, as we want the text to be smaller.
            final DialogTocConfirmBinding vb = DialogTocConfirmBinding.inflate(inflater);

            final boolean hasToc = mTocEntries != null && !mTocEntries.isEmpty();
            if (hasToc) {
                //noinspection ConstantConditions
                final StringBuilder message =
                        new StringBuilder(getString(R.string.warning_toc_confirm))
                                .append("\n\n")
                                .append(Csv.join(mTocEntries,
                                                 tocEntry -> tocEntry.getLabel(getContext())));
                vb.content.setText(message);

            } else {
                vb.content.setText(getString(R.string.error_auto_toc_population_failed));
            }

            //noinspection ConstantConditions
            final AlertDialog dialog =
                    new MaterialAlertDialogBuilder(getContext())
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .setView(vb.getRoot())
                            .setNegativeButton(android.R.string.cancel, (d, which) -> dismiss())
                            .create();

            if (hasToc) {
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(android.R.string.ok),
                                 this::onCommitToc);
            }

            // if we found multiple editions, allow a re-try with the next edition
            if (mHasOtherEditions) {
                dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.retry),
                                 this::onGetNext);
            }

            return dialog;
        }

        private void onCommitToc(@SuppressWarnings("unused") @NonNull final DialogInterface d,
                                 @SuppressWarnings("unused") final int which) {
            if (mListener.get() != null) {
                mListener.get().commitIsfdbData(mTocBitMask, mTocEntries);
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onCommitToc|" + ErrorMsg.WEAK_REFERENCE);
                }
            }
        }

        private void onGetNext(@SuppressWarnings("unused") @NonNull final DialogInterface d,
                               @SuppressWarnings("unused") final int which) {
            if (mListener.get() != null) {
                mListener.get().getNextEdition();
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.TRACE_WEAK_REFERENCES) {
                    Log.d(TAG, "onGetNext|" + ErrorMsg.WEAK_REFERENCE);
                }
            }
        }

        interface ConfirmTocResults {

            void commitIsfdbData(@Book.TocBits long tocBitMask,
                                 @NonNull List<TocEntry> tocEntries);

            void getNextEdition();
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder
            extends RecyclerViewViewHolderBase {

        @NonNull
        final TextView titleView;
        @NonNull
        final TextView authorView;
        @NonNull
        final TextView firstPublicationView;

        Holder(@NonNull final View itemView) {
            super(itemView);

            titleView = rowDetailsView.findViewById(R.id.title);
            authorView = rowDetailsView.findViewById(R.id.author);
            firstPublicationView = rowDetailsView.findViewById(R.id.year);
        }
    }

    private class TocListEditAdapter
            extends RecyclerViewAdapterBase<TocEntry, Holder> {

        /**
         * Constructor.
         *
         * @param context           Current context
         * @param items             List of TocEntry's
         * @param dragStartListener Listener to handle the user moving rows up and down
         */
        TocListEditAdapter(@NonNull final Context context,
                           @NonNull final List<TocEntry> items,
                           @NonNull final StartDragListener dragStartListener) {
            super(context, items, dragStartListener);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final View view = getLayoutInflater()
                    .inflate(R.layout.row_edit_toc_entry, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            super.onBindViewHolder(holder, position);

            final TocEntry item = getItem(position);

            holder.titleView.setText(item.getTitle());
            holder.authorView.setText(item.getAuthor().getLabel(getContext()));

            final String year = item.getFirstPublication();
            if (year.isEmpty()) {
                holder.firstPublicationView.setVisibility(View.GONE);
            } else {
                holder.firstPublicationView.setVisibility(View.VISIBLE);
                holder.firstPublicationView.setText(getString(R.string.brackets, year));
            }

            // click -> edit
            holder.rowDetailsView.setOnClickListener(
                    v -> editEntry(item, holder.getAdapterPosition()));

            holder.rowDetailsView.setOnLongClickListener(v -> {
                onCreateContextMenu(holder.getAdapterPosition());
                return true;
            });
        }
    }
}
