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

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.backup.base.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.base.Options;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistBuilder;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.RowStateDAO;
import com.hardbacknutter.nevertoomanybooks.booklist.StylePickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.TopLevelItemDecoration;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditColorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditFormatDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditGenreDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLanguageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLenderDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditLocationDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditPublisherDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditSeriesDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.DataHolder;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsHandler;
import com.hardbacknutter.nevertoomanybooks.goodreads.GrStatus;
import com.hardbacknutter.nevertoomanybooks.goodreads.taskqueue.QueueManager;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrAuthTask;
import com.hardbacknutter.nevertoomanybooks.goodreads.tasks.GrSendOneBookTask;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.settings.styles.PreferredStylesActivity;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDialogFragment;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.ProgressMessage;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookDetailsFragmentViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BooksOnBookshelfModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.EditBookshelvesModel;
import com.hardbacknutter.nevertoomanybooks.widgets.FabMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.fastscroller.FastScroller;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 * <p>
 * Notes on the local-search:
 * <ol>Advanced:
 *     <li>User clicks navigation panel menu search option</li>
 *     <li>FTSSearch Activity is started</li>
 *     <li>FTS activity returns an id-list and the fts search terms</li>
 *     <li>#onActivityResult sets the incoming fts criteria</li>
 *     <li>#onResume builds the list</li>
 * </ol>
 *
 * <ol>Standard:
 *     <li>User clicks option menu search iconF</li>
 *     <li>shows the search widget, user types</li>
 *     <li>#onNewIntent() gets called with the query data</li>
 *     <li>build the list</li>
 * </ol>
 * <p>
 * We check if we have search criteria, if not we just build and are done.<br>
 *
 * <ol>When we do have search criteria:
 *     <li>during display of the list, the action bar home icon is set to 'up'</li>
 *     <li>Allows the user to re-open the nav drawer and refine the search.</li>
 *     <li>any 'up/back' action will trigger #onBackPressed</li>
 *     <li>#onBackPressed checks if there are search criteria, if so, clears and
 *     rebuild and suppresses the 'back' action</li>
 * </ol>
 */
public class BooksOnBookshelf
        extends BaseActivity {

    /** Log tag. */
    private static final String TAG = "BooksOnBookshelf";
    /** Multi-type adapter to manage list connection to cursor. */
    private BooklistAdapter mAdapter;
    /** The adapter used to fill the mBookshelfSpinner. */
    private ArrayAdapter<BooksOnBookshelfModel.BookshelfSpinnerEntry> mBookshelfSpinnerAdapter;
    /** The ViewModel. */
    private BooksOnBookshelfModel mModel;
    /** Full progress dialog to show while importing. */
    @Nullable
    private ProgressDialogFragment mProgressDialog;
    private GrSendOneBookTask mGrSendOneBookTask;
    private GrAuthTask mGrAuthTask;
    /** List layout manager. */
    private LinearLayoutManager mLayoutManager;
    /** Encapsulates the FAB button/menu. */
    private FabMenu mFabMenu;
    private BooksonbookshelfBinding mVb;
    /** React to row changes made. ENHANCE: update the modified row without a rebuild. */
    private final BookChangedListener mBookChangedListener =
            new BookChangedListener() {
                @Override
                public void onChange(final long bookId,
                                     final int fieldsChanged,
                                     @Nullable final Bundle data) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                        Log.d(TAG, "onBookChanged"
                                   + "|bookId=" + bookId
                                   + "|fieldsChanged=0b" + Integer.toBinaryString(fieldsChanged)
                                   + "|data=" + data);
                    }
                    BooksOnBookshelf.this.saveListPosition();
                    // go create
                    buildBookList();

                    // changes were made to a single book
//        if (bookId > 0) {
//            if ((fieldsChanged & BookChangedListener.BOOK_READ) != 0) {
//                saveListPosition();
//                initBookList();
//
//          } else if ((fieldsChanged & BookChangedListener.BOOK_LOANEE) != 0) {
//                if (data != null) {
//                    String loanee = data.getString(DBDefinitions.KEY_LOANEE);
//                }
//                saveListPosition();
//                initBookList();
//
//            } else if ((fieldsChanged & BookChangedListener.BOOK_DELETED) != 0) {
//                saveListPosition();
//                initBookList();
//            }
//        } else {
//            // changes (Author, Series, ...) were made to (potentially) the whole list
//            if (fieldsChanged != 0) {
//                saveListPosition();
//                initBookList();
//            }
//        }
                }
            };
    /** Listener for the Bookshelf Spinner. */
    private final OnItemSelectedListener mOnBookshelfSelectionChanged =
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(@NonNull final AdapterView<?> parent,
                                           @NonNull final View view,
                                           final int position,
                                           final long id) {
                    @Nullable
                    final BooksOnBookshelfModel.BookshelfSpinnerEntry selected =
                            (BooksOnBookshelfModel.BookshelfSpinnerEntry)
                                    parent.getItemAtPosition(position);

                    if (selected != null) {
                        saveListPosition();

                        // make the new shelf the current, and build the new list
                        mModel.setCurrentBookshelf(BooksOnBookshelf.this,
                                                   selected.getBookshelf());
                        buildBookList();
                    }
                }

                @Override
                public void onNothingSelected(@NonNull final AdapterView<?> parent) {
                    // Do Nothing
                }
            };
    /** (re)attach the result listener when a fragment gets started. */
    private final FragmentOnAttachListener mFragmentOnAttachListener =
            new FragmentOnAttachListener() {
                @Override
                public void onAttachFragment(@NonNull final FragmentManager fragmentManager,
                                             @NonNull final Fragment fragment) {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.ATTACH_FRAGMENT) {
                        Log.d(getClass().getName(), "onAttachFragment: " + fragment.getTag());
                    }

                    if (fragment instanceof BookChangedListener.Owner) {
                        ((BookChangedListener.Owner) fragment).setListener(mBookChangedListener);

                    } else if (fragment instanceof StylePickerDialogFragment) {
                        ((StylePickerDialogFragment) fragment).setListener(mStyleChangedListener);
                    }
                }
            };
    /** Listener for clicks on the list. */
    private final BooklistAdapter.OnRowClickedListener mOnRowClickedListener =
            new BooklistAdapter.OnRowClickedListener() {

                /**
                 * User clicked a row.
                 * <ul>
                 *      <li>Book: open the details screen.</li>
                 *      <li>Not a book: expand/collapse the section as appropriate.</li>
                 * </ul>
                 */
                @Override
                public void onItemClick(final int position) {
                    final Cursor cursor = mModel.getListCursor();
                    // Move the cursor, so we can read the data for this row.
                    // Paranoia: if the user can click it, then this move should be fine.
                    if (!cursor.moveToPosition(position)) {
                        return;
                    }
                    final DataHolder rowData = new CursorRow(cursor);

                    // If it's a book, open the details screen.
                    if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
                        final long rowId = rowData.getLong(DBDefinitions.KEY_PK_ID);
                        final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                        // Note we (re)create the flat table *every time* the user click a book.
                        // This guarantees an exact match in rowId'
                        // (which turns out tricky if we cache the table)
                        // ENHANCE: re-implement flat table caching
                        final String navTableName = mModel.createFlattenedBooklist();
                        final Intent intent = new Intent(BooksOnBookshelf.this,
                                                         BookDetailsActivity.class)
                                .putExtra(DBDefinitions.KEY_PK_ID, bookId)
                                .putExtra(BookDetailsFragmentViewModel.BKEY_NAV_TABLE, navTableName)
                                .putExtra(BookDetailsFragmentViewModel.BKEY_NAV_ROW_ID, rowId);
                        startActivityForResult(intent, RequestCode.BOOK_VIEW);

                    } else {
                        // it's a level, expand/collapse.
                        final int nodeRowId =
                                rowData.getInt(DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID);
                        final RowStateDAO.Node node = mModel.toggleNode(
                                nodeRowId, RowStateDAO.Node.NEXT_STATE_TOGGLE, 1);
                        refreshNodePosition(node);
                    }
                }

                /**
                 * User long-clicked on a row. Bring up a context menu as appropriate.
                 */
                @Override
                public boolean onItemLongClick(final int position) {
                    final Cursor cursor = mModel.getListCursor();
                    // Move the cursor, so we can read the data for this row.
                    // Paranoia: if the user can click it, then this move should be fine.
                    if (!cursor.moveToPosition(position)) {
                        return false;
                    }
                    final DataHolder rowData = new CursorRow(cursor);

                    // build the menu for this row
                    final Menu menu = MenuPicker.createMenu(BooksOnBookshelf.this);
                    if (onCreateContextMenu(menu, rowData)) {
                        // we have a menu to show, set the title according to the level.
                        final int level = rowData.getInt(DBDefinitions.KEY_BL_NODE_LEVEL);
                        final String title = mAdapter.getLevelText(position, level);

                        // bring up the context menu
                        new MenuPicker(BooksOnBookshelf.this, title, menu, position,
                                       BooksOnBookshelf.this::onContextItemSelected)
                                .show();
                    }
                    return true;
                }
            };
    /** React to the user selecting a style to apply. */
    private final StylePickerDialogFragment.StyleChangedListener mStyleChangedListener =
            new StylePickerDialogFragment.StyleChangedListener() {
                public void onStyleChanged(@NonNull final String uuid) {
                    // preserve position for current bookshelf/style combination
                    saveListPosition();
                    // apply the new style
                    mModel.onStyleChanged(BooksOnBookshelf.this, uuid);
                    // Set the rebuild state like this is the first time in,
                    // which it sort of is, given we are changing style.
                    mModel.setPreferredListRebuildState(BooksOnBookshelf.this);
                    // and do a rebuild
                    buildBookList();
                }
            };

    @Override
    protected void onSetContentView() {
        mVb = BooksonbookshelfBinding.inflate(getLayoutInflater());
        setContentView(mVb.getRoot());

        mFabMenu = new FabMenu(mVb.fab, mVb.fabOverlay,
                               mVb.fab0, mVb.fab1, mVb.fab2, mVb.fab3, mVb.fab4);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        // Create the Goodreads QueueManager. This (re)starts stored tasks.
        QueueManager.create(this);

        super.onCreate(savedInstanceState);

        getSupportFragmentManager().addFragmentOnAttachListener(mFragmentOnAttachListener);

        // Does not use the full progress dialog. Instead uses the overlay progress bar.
        mModel = new ViewModelProvider(this).get(BooksOnBookshelfModel.class);
        mModel.init(this, getIntent().getExtras(), savedInstanceState);
        mModel.onCancelled().observe(this, message -> onRestorePreviousList());
        mModel.onFailure().observe(this, message -> onRestorePreviousList());
        mModel.onFinished().observe(this, message -> onDisplayList(message.result));

        mGrAuthTask = new ViewModelProvider(this).get(GrAuthTask.class);
        mGrAuthTask.onProgressUpdate().observe(this, this::onProgress);
        mGrAuthTask.onCancelled().observe(this, this::onCancelled);
        mGrAuthTask.onFailure().observe(this, this::onGrFailure);
        mGrAuthTask.onFinished().observe(this, this::onGrFinished);

        mGrSendOneBookTask = new ViewModelProvider(this).get(GrSendOneBookTask.class);
        mGrSendOneBookTask.onProgressUpdate().observe(this, this::onProgress);
        mGrSendOneBookTask.onCancelled().observe(this, this::onCancelled);
        mGrSendOneBookTask.onFailure().observe(this, this::onGrFailure);
        mGrSendOneBookTask.onFinished().observe(this, this::onGrFinished);

        // enable the navigation menu
        setNavigationItemVisibility(R.id.nav_manage_list_styles, true);
        setNavigationItemVisibility(R.id.nav_manage_bookshelves, true);
        setNavigationItemVisibility(R.id.nav_export, true);
        setNavigationItemVisibility(R.id.nav_import, true);
        setNavigationItemVisibility(R.id.nav_goodreads, GoodreadsHandler.isShowSyncMenus(this));

        // initialize but do not populate the list; the latter is done in onResume
        mLayoutManager = new LinearLayoutManager(this);
        mVb.list.setLayoutManager(mLayoutManager);
        mVb.list.addItemDecoration(new TopLevelItemDecoration(this));
        FastScroller.attach(mVb.list);

        // initialize but do not populate the list;  the latter is done in setBookShelfSpinner
        mBookshelfSpinnerAdapter = new ArrayAdapter<>(
                this, R.layout.bookshelf_spinner_selected, mModel.getBookshelfSpinnerList());
        mBookshelfSpinnerAdapter.setDropDownViewResource(R.layout.dropdown_menu_popup_item);
        mVb.bookshelfSpinner.setAdapter(mBookshelfSpinnerAdapter);

        mFabMenu.attach(mVb.list);
        mFabMenu.getItem(0).setOnClickListener(v -> addByIsbn(true));
        mFabMenu.getItem(1).setOnClickListener(v -> addByIsbn(false));
        mFabMenu.getItem(2).setOnClickListener(v -> addBySearch(BookSearchByTextFragment.TAG));
        mFabMenu.getItem(3).setOnClickListener(v -> addManually());
        if (Prefs.showEditBookTabNativeId(this)) {
            mFabMenu.getItem(4).setEnabled(true);
            mFabMenu.getItem(4).setOnClickListener(
                    v -> addBySearch(BookSearchByNativeIdFragment.TAG));
        } else {
            mFabMenu.getItem(4).setEnabled(false);
        }

        // Popup the search widget when the user starts to type.
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);
        // check & get search text coming from a system search intent
        handleStandardSearchIntent(getIntent());

        if (savedInstanceState == null) {
            TipManager.display(this, R.string.tip_book_list, null);
        }
    }

    public void onRestorePreviousList() {
        mVb.progressBar.setVisibility(View.GONE);
        // just restore the old list if we can
        if (mModel.isListLoaded()) {
            initAdapter(mModel.getListCursor());
        } else {
            // Something is REALLY BAD
            throw new IllegalStateException();
        }
    }

    /**
     * Entry point for the system search request.
     *
     * @param intent to use
     */
    @Override
    protected void onNewIntent(@NonNull final Intent intent) {
        super.onNewIntent(intent);
        // make this the Activity intent.
        setIntent(intent);

        handleStandardSearchIntent(intent);
        buildBookList();
    }

    /**
     * Handle the standard search intent / suggestions click.
     *
     * <a href="https://developer.android.com/guide/topics/search/search-dialog#ReceivingTheQuery">
     * ReceivingTheQuery</a>
     */
    private void handleStandardSearchIntent(@NonNull final Intent intent) {
        final String query;
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            // Handle the standard search intent.
            query = intent.getStringExtra(SearchManager.QUERY);

        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            // Handle a suggestions click.
            // The ACTION_VIEW as set in res/xml/searchable.xml/searchSuggestIntentAction
            query = intent.getDataString();
        } else {
            query = null;
        }
        // actioning on the criteria wil happen automatically at list building time.
        mModel.getSearchCriteria().setKeywords(query);
    }

    @Override
    protected boolean onNavigationItemSelected(@NonNull final MenuItem item) {
        closeNavigationDrawer();
        switch (item.getItemId()) {
            case R.id.nav_advanced_search: {
                // overridden, so we can pass the current criteria
                final Intent intent = new Intent(this, FTSSearchActivity.class);
                mModel.getSearchCriteria().to(intent);
                startActivityForResult(intent, RequestCode.ADVANCED_LOCAL_SEARCH);
                return true;
            }
            case R.id.nav_manage_bookshelves: {
                // overridden, so we can pass the current bookshelf id.
                final Intent intent = new Intent(this, AdminActivity.class)
                        .putExtra(BKEY_FRAGMENT_TAG, EditBookshelvesFragment.TAG)
                        .putExtra(EditBookshelvesModel.BKEY_CURRENT_BOOKSHELF,
                                  mModel.getCurrentBookshelf().getId());
                startActivityForResult(intent, RequestCode.NAV_PANEL_MANAGE_BOOKSHELVES);
                return true;
            }
            case R.id.nav_manage_list_styles: {
                final Intent intent = new Intent(this, PreferredStylesActivity.class)
                        .putExtra(BooklistStyle.BKEY_STYLE_UUID,
                                  mModel.getCurrentStyle(this).getUuid());
                startActivityForResult(intent, RequestCode.NAV_PANEL_MANAGE_STYLES);
                return true;
            }

            case R.id.nav_import: {
                final Intent intent = new Intent(this, AdminActivity.class)
                        .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, ImportFragment.TAG);
                startActivityForResult(intent, RequestCode.NAV_PANEL_IMPORT);
                return true;
            }
            case R.id.nav_export: {
                final Intent intent = new Intent(this, AdminActivity.class)
                        .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, ExportFragment.TAG);
                startActivityForResult(intent, RequestCode.NAV_PANEL_EXPORT);
                return true;
            }

            default:
                return super.onNavigationItemSelected(item);
        }
    }

    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        getMenuInflater().inflate(R.menu.bob, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onPrepareOptionsMenu(@NonNull final Menu menu) {
        mFabMenu.hide();

        final boolean showECPreferred = mModel.getCurrentStyle(this).getTopLevel(this) > 1;
        menu.findItem(R.id.MENU_LEVEL_PREFERRED_COLLAPSE).setVisible(showECPreferred);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        mFabMenu.hide();

        switch (item.getItemId()) {
            case R.id.MENU_SORT: {
                StylePickerDialogFragment
                        .newInstance(mModel.getCurrentStyle(this), false)
                        .show(getSupportFragmentManager(), StylePickerDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_LEVEL_PREFERRED_COLLAPSE: {
                expandAllNodes(mModel.getCurrentStyle(this).getTopLevel(this), false);
                return true;
            }
            case R.id.MENU_LEVEL_EXPAND: {
                expandAllNodes(1, true);
                return true;
            }
            case R.id.MENU_LEVEL_COLLAPSE: {
                expandAllNodes(1, false);
                return true;
            }
            case R.id.MENU_UPDATE_FROM_INTERNET: {
                // IMPORTANT: this is from an options menu selection.
                // We pass the book ID's for the currently displayed list.
                final ArrayList<Long> bookIdList = mModel.getCurrentBookIdList();
                final Intent intent = new Intent(this, BookSearchActivity.class)
                        .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, UpdateFieldsFragment.TAG)
                        .putExtra(Book.BKEY_BOOK_ID_ARRAY, bookIdList);
                startActivityForResult(intent, RequestCode.UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Create a context menu based on row group.
     *
     * @param menu    to populate
     * @param rowData current cursorRow
     *
     * @return {@code true} if there is a menu to show
     */
    boolean onCreateContextMenu(@NonNull final Menu menu,
                                @NonNull final DataHolder rowData) {
        menu.clear();

        final int rowGroupId = rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP);
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                getMenuInflater().inflate(R.menu.book, menu);
                getMenuInflater().inflate(R.menu.sm_view_on_site, menu);
                getMenuInflater().inflate(R.menu.sm_search_on_amazon, menu);

                final boolean isRead = rowData.getBoolean(DBDefinitions.KEY_READ);
                menu.findItem(R.id.MENU_BOOK_READ).setVisible(!isRead);
                menu.findItem(R.id.MENU_BOOK_UNREAD).setVisible(isRead);

                // specifically check App.isUsed for KEY_LOANEE independent from the style in use.
                final boolean useLending = DBDefinitions.isUsed(this, DBDefinitions.KEY_LOANEE);
                final boolean isAvailable = mModel.isAvailable(rowData);
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

                menu.findItem(R.id.MENU_BOOK_SEND_TO_GOODREADS)
                    .setVisible(GoodreadsHandler.isShowSyncMenus(this));

                MenuHandler.prepareOptionalMenus(menu, rowData);
                break;
            }
            case BooklistGroup.AUTHOR: {
                getMenuInflater().inflate(R.menu.author, menu);

                final boolean complete = rowData.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
                menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
                menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

                MenuHandler.prepareOptionalMenus(menu, rowData);
                break;
            }
            case BooklistGroup.SERIES: {
                if (rowData.getLong(DBDefinitions.KEY_FK_SERIES) != 0) {
                    getMenuInflater().inflate(R.menu.series, menu);

                    final boolean complete =
                            rowData.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
                    menu.findItem(R.id.MENU_SERIES_SET_COMPLETE).setVisible(!complete);
                    menu.findItem(R.id.MENU_SERIES_SET_INCOMPLETE).setVisible(complete);

                    MenuHandler.prepareOptionalMenus(menu, rowData);
                }
                break;
            }
            case BooklistGroup.PUBLISHER: {
                if (rowData.getLong(DBDefinitions.KEY_FK_PUBLISHER) != 0) {
                    getMenuInflater().inflate(R.menu.publisher, menu);
                }
                break;
            }
            case BooklistGroup.LANGUAGE: {
                if (!rowData.getString(DBDefinitions.KEY_LANGUAGE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.LOCATION: {
                if (!rowData.getString(DBDefinitions.KEY_LOCATION).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.GENRE: {
                if (!rowData.getString(DBDefinitions.KEY_GENRE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.FORMAT: {
                if (!rowData.getString(DBDefinitions.KEY_FORMAT).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            case BooklistGroup.COLOR: {
                if (!rowData.getString(DBDefinitions.KEY_COLOR).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_COLOR_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_edit);
                }
                break;
            }
            default: {
                break;
            }
        }

        int menuOrder = getResources().getInteger(R.integer.MENU_NEXT_MISSING_COVER);
        if (menu.size() > 0) {
            menu.add(Menu.NONE, R.id.MENU_DIVIDER, menuOrder++, "")
                .setEnabled(false);
        }
        menu.add(Menu.NONE, R.id.MENU_NEXT_MISSING_COVER, menuOrder++,
                 R.string.lbl_next_book_without_cover)
            .setIcon(R.drawable.ic_broken_image);

        // if it's a level, add the expand option
        if (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP) != BooklistGroup.BOOK) {
            //noinspection UnusedAssignment
            menu.add(Menu.NONE, R.id.MENU_LEVEL_EXPAND, menuOrder++, R.string.lbl_level_expand)
                .setIcon(R.drawable.ic_unfold_more);
        }

        return menu.size() > 0;
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean onContextItemSelected(@IdRes final int menuItem,
                                          final int position) {

        final Cursor cursor = mModel.getListCursor();
        // Move the cursor, so we can read the data for this row.
        // The majority of the time this is not needed, but a fringe case (toggle node)
        // showed it should indeed be done.
        // Paranoia: if the user can click it, then this move should be fine.
        if (!cursor.moveToPosition(position)) {
            return false;
        }

        final DataHolder rowData = new CursorRow(cursor);

        switch (menuItem) {
            case R.id.MENU_BOOK_EDIT: {
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                final Intent intent = new Intent(this, EditBookActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID, bookId);
                startActivityForResult(intent, RequestCode.BOOK_EDIT);
                return true;
            }
            case R.id.MENU_BOOK_DELETE: {
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                final String title = rowData.getString(DBDefinitions.KEY_TITLE);
                final List<Author> authors = mModel.getDb().getAuthorsByBookId(bookId);
                StandardDialogs.deleteBook(this, title, authors, () -> {
                    if (mModel.getDb().deleteBook(this, bookId)) {
                        mBookChangedListener
                                .onChange(bookId, BookChangedListener.BOOK_DELETED, null);
                    }
                });
                return true;
            }
            case R.id.MENU_BOOK_DUPLICATE: {
                final Book book = mModel.getBook(rowData);
                if (book != null) {
                    final Intent intent = new Intent(this, EditBookActivity.class)
                            .putExtra(Book.BKEY_BOOK_DATA, book.duplicate());
                    startActivityForResult(intent, RequestCode.BOOK_DUPLICATE);
                }
                return true;
            }

            case R.id.MENU_BOOK_READ:
            case R.id.MENU_BOOK_UNREAD: {
                // toggle the read status
                final boolean status = !rowData.getBoolean(DBDefinitions.KEY_READ);
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                if (mModel.getDb().setBookRead(bookId, status)) {
                    mBookChangedListener.onChange(bookId, BookChangedListener.BOOK_READ, null);
                }
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_BOOK_LOAN_ADD: {
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                EditLenderDialogFragment
                        .newInstance(bookId, rowData.getString(DBDefinitions.KEY_TITLE))
                        .show(getSupportFragmentManager(), EditLenderDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_BOOK_LOAN_DELETE: {
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                mModel.getDb().lendBook(bookId, null);
                mBookChangedListener.onChange(bookId, BookChangedListener.BOOK_LOANEE, null);
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_SHARE: {
                final Book book = mModel.getBook(rowData);
                if (book != null) {
                    startActivity(book.getShareIntent(this));
                }
                return true;
            }
            case R.id.MENU_BOOK_SEND_TO_GOODREADS: {
                Snackbar.make(mVb.list, R.string.progress_msg_connecting,
                              Snackbar.LENGTH_LONG).show();
                final long bookId = rowData.getLong(DBDefinitions.KEY_FK_BOOK);
                mGrSendOneBookTask.startTask(bookId);
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_UPDATE_FROM_INTERNET: {
                // IMPORTANT: this is from a context click on a row.
                // We pass the book ID's which are suited for that row.
                final Intent intent = new Intent(this, BookSearchActivity.class)
                        .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, UpdateFieldsFragment.TAG);

                switch (rowData.getInt(DBDefinitions.KEY_BL_NODE_GROUP)) {

                    case BooklistGroup.BOOK: {
                        final ArrayList<Long> bookIdList = new ArrayList<>();
                        bookIdList.add(rowData.getLong(DBDefinitions.KEY_FK_BOOK));
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_TITLE))
                              .putExtra(Book.BKEY_BOOK_ID_ARRAY,
                                        bookIdList);
                        break;
                    }
                    case BooklistGroup.AUTHOR: {
                        final long authorId = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_AUTHOR_FORMATTED))
                              .putExtra(Book.BKEY_BOOK_ID_ARRAY,
                                        mModel.getDb().getBookIdsByAuthor(authorId));
                        break;
                    }
                    case BooklistGroup.SERIES: {
                        final long seriesId = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_SERIES_TITLE))
                              .putExtra(Book.BKEY_BOOK_ID_ARRAY,
                                        mModel.getDb().getBookIdsBySeries(seriesId));
                        break;
                    }
                    case BooklistGroup.PUBLISHER: {
                        final long publisherId = rowData.getLong(DBDefinitions.KEY_FK_PUBLISHER);
                        intent.putExtra(StandardDialogs.BKEY_DIALOG_TITLE,
                                        rowData.getString(DBDefinitions.KEY_PUBLISHER_NAME))
                              .putExtra(Book.BKEY_BOOK_ID_ARRAY,
                                        mModel.getDb().getBookIdsByPublisher(publisherId));
                        break;
                    }
                    default: {
                        if (BuildConfig.DEBUG /* always */) {
                            Log.d(TAG, "onContextItemSelected"
                                       + "|MENU_BOOK_UPDATE_FROM_INTERNET not supported"
                                       + "|Group=" + rowData
                                               .getInt(DBDefinitions.KEY_BL_NODE_GROUP));
                        }
                        return true;
                    }
                }

                startActivityForResult(intent, RequestCode.UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }

            /* ********************************************************************************** */

            case R.id.MENU_SERIES_EDIT: {
                final Series series = mModel.getSeries(rowData);
                if (series != null) {
                    EditSeriesDialogFragment
                            .newInstance(series)
                            .show(getSupportFragmentManager(), EditSeriesDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_SERIES_SET_COMPLETE:
            case R.id.MENU_SERIES_SET_INCOMPLETE: {
                final long seriesId = rowData.getLong(DBDefinitions.KEY_FK_SERIES);
                // toggle the complete status
                final boolean status = !rowData.getBoolean(DBDefinitions.KEY_SERIES_IS_COMPLETE);
                if (mModel.getDb().setSeriesComplete(seriesId, status)) {
                    mBookChangedListener.onChange(0, BookChangedListener.SERIES, null);
                }
                return true;
            }
            case R.id.MENU_SERIES_DELETE: {
                final Series series = mModel.getSeries(rowData);
                if (series != null) {
                    StandardDialogs.deleteSeries(this, series, () -> {
                        mModel.getDb().deleteSeries(this, series.getId());
                        mBookChangedListener.onChange(0, BookChangedListener.SERIES, null);
                    });
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_AUTHOR_WORKS: {
                final Intent intent = new Intent(this, AuthorWorksActivity.class)
                        .putExtra(DBDefinitions.KEY_PK_ID,
                                  rowData.getLong(DBDefinitions.KEY_FK_AUTHOR))
                        .putExtra(DBDefinitions.KEY_FK_BOOKSHELF,
                                  mModel.getCurrentBookshelf().getId());
                startActivityForResult(intent, RequestCode.AUTHOR_WORKS);
                return true;
            }

            case R.id.MENU_AUTHOR_EDIT: {
                final Author author = mModel.getAuthor(rowData);
                if (author != null) {
                    EditAuthorDialogFragment
                            .newInstance(author)
                            .show(getSupportFragmentManager(), EditAuthorDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_AUTHOR_SET_COMPLETE:
            case R.id.MENU_AUTHOR_SET_INCOMPLETE: {
                final long authorId = rowData.getLong(DBDefinitions.KEY_FK_AUTHOR);
                // toggle the complete status
                final boolean status = !rowData.getBoolean(DBDefinitions.KEY_AUTHOR_IS_COMPLETE);
                if (mModel.getDb().setAuthorComplete(authorId, status)) {
                    mBookChangedListener.onChange(0, BookChangedListener.AUTHOR, null);
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_PUBLISHER_EDIT: {
                final Publisher publisher = mModel.getPublisher(rowData);
                if (publisher != null) {
                    EditPublisherDialogFragment
                            .newInstance(publisher)
                            .show(getSupportFragmentManager(), EditPublisherDialogFragment.TAG);
                }
                return true;
            }
            case R.id.MENU_PUBLISHER_DELETE: {
                final Publisher publisher = mModel.getPublisher(rowData);
                if (publisher != null) {
                    StandardDialogs.deletePublisher(this, publisher, () -> {
                        mModel.getDb().deletePublisher(this, publisher.getId());
                        mBookChangedListener.onChange(0, BookChangedListener.PUBLISHER, null);
                    });
                }
                return true;
            }
            /* ********************************************************************************** */

            case R.id.MENU_FORMAT_EDIT: {
                EditFormatDialogFragment
                        .newInstance(rowData.getString(DBDefinitions.KEY_FORMAT))
                        .show(getSupportFragmentManager(), EditFormatDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_COLOR_EDIT: {
                EditColorDialogFragment
                        .newInstance(rowData.getString(DBDefinitions.KEY_COLOR))
                        .show(getSupportFragmentManager(), EditColorDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_GENRE_EDIT: {
                EditGenreDialogFragment
                        .newInstance(rowData.getString(DBDefinitions.KEY_GENRE))
                        .show(getSupportFragmentManager(), EditGenreDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_LANGUAGE_EDIT: {
                EditLanguageDialogFragment
                        .newInstance(this, rowData.getString(DBDefinitions.KEY_LANGUAGE))
                        .show(getSupportFragmentManager(), EditLanguageDialogFragment.TAG);
                return true;
            }
            case R.id.MENU_LOCATION_EDIT: {
                EditLocationDialogFragment
                        .newInstance(rowData.getString(DBDefinitions.KEY_LOCATION))
                        .show(getSupportFragmentManager(), EditLocationDialogFragment.TAG);
                return true;
            }

            /* ********************************************************************************** */
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                final Author author = mModel.getAuthor(rowData);
                if (author != null) {
                    AmazonSearchEngine.openWebsite(this, author.getLabel(this), null);
                }
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                final Series series = mModel.getSeries(rowData);
                if (series != null) {
                    AmazonSearchEngine.openWebsite(this, null, series.getTitle());
                }
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                final Author author = mModel.getAuthor(rowData);
                final Series series = mModel.getSeries(rowData);
                if (author != null && series != null) {
                    AmazonSearchEngine.openWebsite(this, author.getLabel(this), series.getTitle());
                }
                return true;
            }

            case R.id.MENU_LEVEL_EXPAND: {
                final long nodeRowId = rowData.getLong(DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID);
                final int relativeChildLevel = mModel.getCurrentStyle(this).getGroupCount();
                final RowStateDAO.Node node = mModel.toggleNode(
                        nodeRowId, RowStateDAO.Node.NEXT_STATE_EXPANDED, relativeChildLevel);
                refreshNodePosition(node);
                return true;
            }

            case R.id.MENU_NEXT_MISSING_COVER: {
                final long nodeRowId = rowData.getLong(DBDefinitions.KEY_BL_LIST_VIEW_NODE_ROW_ID);
                final RowStateDAO.Node node = mModel.getNextBookWithoutCover(this, nodeRowId);
                if (node != null) {
                    scrollTo(node);
                    refreshNodePosition(node);
                }
                return true;
            }

            default:
                return MenuHandler.handleOpenOnWebsiteMenus(this, menuItem, rowData);
        }
    }

    /**
     * Reminder: don't do any commits on the fragment manager.
     * This includes showing fragments, or starting tasks that show fragments.
     * Do this in {@link #onResume} which will be called after onActivityResult.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        switch (requestCode) {
            case RequestCode.ADVANCED_LOCAL_SEARCH:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    if (mModel.setSearchCriteria(data.getExtras(), true)) {
                        //URGENT: switch bookshelf? all-books?
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;

            case RequestCode.UPDATE_FIELDS_FROM_INTERNET:
            case RequestCode.BOOK_VIEW:
            case RequestCode.BOOK_EDIT:
            case RequestCode.BOOK_DUPLICATE:
            case RequestCode.BOOK_SEARCH:
            case RequestCode.AUTHOR_WORKS: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    final Bundle extras = data.getExtras();
                    if (extras != null) {
                        if (extras.getBoolean(BookViewModel.BKEY_BOOK_CREATED, false)) {
                            mModel.setForceRebuildInOnResume(true);
                        }
                        if (extras.getBoolean(BookViewModel.BKEY_BOOK_MODIFIED, false)) {
                            mModel.setForceRebuildInOnResume(true);
                        }
                        if (extras.getBoolean(BookViewModel.BKEY_BOOK_DELETED, false)) {
                            mModel.setForceRebuildInOnResume(true);
                        }
                        if (extras.containsKey(BooksOnBookshelfModel.BKEY_LIST_STATE)) {
                            int state = extras.getInt(BooksOnBookshelfModel.BKEY_LIST_STATE,
                                                      BooklistBuilder.PREF_REBUILD_SAVED_STATE);
                            mModel.setRebuildState(state);
                            mModel.setForceRebuildInOnResume(true);
                        }

                        // if we got an id back, make any rebuild re-position to it.
                        long bookId = extras.getLong(DBDefinitions.KEY_PK_ID, 0);
                        if (bookId != 0) {
                            mModel.setDesiredCentralBookId(bookId);
                        }
                    }
                }
                break;
            }
            case RequestCode.NAV_PANEL_MANAGE_BOOKSHELVES: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    // the last edited/inserted shelf
                    final long bookshelfId = data.getLongExtra(DBDefinitions.KEY_PK_ID,
                                                               Bookshelf.DEFAULT);
                    if (bookshelfId != mModel.getCurrentBookshelf().getId()) {
                        mModel.setCurrentBookshelf(this, bookshelfId);
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;
            }

            case RequestCode.NAV_PANEL_MANAGE_STYLES: {
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    // we get the UUID for the selected style back.
                    final String styleUuid = data.getStringExtra(BooklistStyle.BKEY_STYLE_UUID);
                    if (styleUuid != null) {
                        mModel.onStyleChanged(this, styleUuid);
                    }

                    if (data.getBooleanExtra(BooklistStyle.BKEY_STYLE_MODIFIED, false)) {
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;
            }

            case RequestCode.EDIT_STYLE: {
                // We get here from the StylePickerDialogFragment (i.e. the style menu)
                // when the user choose to EDIT a style.
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);
                    // We get the ACTUAL style back.
                    // This style might be new (id==0) or already existing (id!=0).
                    @Nullable
                    final BooklistStyle style = data.getParcelableExtra(BooklistStyle.BKEY_STYLE);
                    if (style != null) {
                        mModel.onStyleChanged(this, style);
                    }

                    if (data.getBooleanExtra(BooklistStyle.BKEY_STYLE_MODIFIED, false)) {
                        mModel.setForceRebuildInOnResume(true);
                    }
                }
                break;
            }

            // from BaseActivity Nav Panel
            case RequestCode.NAV_PANEL_EXPORT:
                break;

            // from BaseActivity Nav Panel
            case RequestCode.NAV_PANEL_IMPORT: {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    if (data.hasExtra(ImportResults.BKEY_IMPORT_RESULTS)) {
                        final int options = data.getIntExtra(ImportResults.BKEY_IMPORT_RESULTS,
                                                             Options.NOTHING);
                        if (options != 0) {
                            if ((options & Options.STYLES) != 0) {
                                // Force a refresh of the list of all user styles.
                                BooklistStyle.StyleDAO.clear();
                            }
                            if ((options & Options.PREFS) != 0) {
                                // Refresh the preferred bookshelf. This also refreshes its style.
                                mModel.reloadCurrentBookshelf(this);
                            }

                            // styles, prefs, books, covers,... it all requires a rebuild.
                            mModel.setForceRebuildInOnResume(true);
                        }
                    }
                }
                break;
            }

            default: {
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    @Override
    public void onBackPressed() {
        // If the FAB menu is showing, hide it and suppress the back key.
        if (mFabMenu.isShown()) {
            mFabMenu.show(false);
            return;
        }

        // If the current list is has any search criteria enabled, clear them and rebuild the list.
        if (isTaskRoot() && !mModel.getSearchCriteria().isEmpty()) {
            mModel.getSearchCriteria().clear();
            // go create
            buildBookList();
            return;
        }

        // Otherwise handle the back-key as normal.
        super.onBackPressed();
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();

        // don't build the list needlessly
        if (isRecreating() || isFinishing() || isDestroyed()) {
            return;
        }

        // clear the adapter; we'll prepare a new one and meanwhile the view/adapter
        // should obviously NOT try to display the old list.
        // We don't clear the cursor on the model, so we have the option of re-using it.
        mVb.list.setAdapter(null);
        mAdapter = null;

        // Update the list of bookshelves + set the current bookshelf.
        // If the shelf was changed, it will have triggered a rebuild.
        // This also takes care of the initial build.
        final boolean bookshelfChanged = setBookShelfSpinner();

        final boolean forceRebuildInOnResume = mModel.isForceRebuildInOnResume();
        // always reset for next iteration.
        mModel.setForceRebuildInOnResume(false);

        // This if/else is to be able to debug/log *why* we're rebuilding
        if (bookshelfChanged) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|bookshelfChanged");
            }
            // DO NOTHING. THE CHANGE IN BOOKSHELF ALREADY TRIGGERED A REBUILD.

        } else if (forceRebuildInOnResume) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|isForceRebuildInOnResume");
            }
            // go create
            buildBookList();

        } else if (!mModel.isListLoaded()) {
            //TEST: this branch is almost certainly never reached.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "onResume|initial build");
            }
            // go create
            buildBookList();

        } else {
            // no rebuild needed/done, just re-display
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
                Log.d(TAG, "onResume|reusing existing list");
            }
            mModel.createNewListCursor();
            onDisplayList(mModel.getTargetNodes());
        }
    }

    /**
     * Save position when paused.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        mFabMenu.hide();
        saveListPosition();
        super.onPause();
    }

    private void addBySearch(@NonNull final String tag) {
        final Intent intent = new Intent(this, BookSearchActivity.class)
                .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, tag);
        startActivityForResult(intent, RequestCode.BOOK_SEARCH);
    }

    private void addByIsbn(final boolean scanMode) {
        final Intent intent = new Intent(this, BookSearchActivity.class)
                .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, BookSearchByIsbnFragment.TAG)
                .putExtra(BookSearchByIsbnFragment.BKEY_SCAN_MODE, scanMode);
        startActivityForResult(intent, RequestCode.BOOK_SEARCH);
    }

    private void addManually() {
        final Intent intent = new Intent(this, EditBookActivity.class);
        startActivityForResult(intent, RequestCode.BOOK_EDIT);
    }

    private void buildBookList() {
        mVb.progressBar.setVisibility(View.VISIBLE);
        mModel.buildBookList();
    }

    /**
     * Display the current cursor in the ListView.
     *
     * @param targetNodes (optional) change the position to the 'best' of these nodes.
     */
    private void onDisplayList(@Nullable final List<RowStateDAO.Node> targetNodes) {
        mVb.progressBar.setVisibility(View.GONE);

        // create and hookup the list adapter.
        initAdapter(mModel.getListCursor());
        final int count = mModel.getListCursor().getCount();

        final Bookshelf currentBookshelf = mModel.getCurrentBookshelf();
        int position = currentBookshelf.getTopItemPosition();

        // Scroll to the saved position
        if (position >= count) {
            // the list is shorter than it used to be, just scroll to the end
            mLayoutManager.scrollToPosition(position);

        } else if (position != RecyclerView.NO_POSITION) {
            // need to map the row id to the new adapter/cursor. Ideally they will be the same.
            final long actualRowId = mAdapter.getItemId(position);

            // but if they are not equal,
            final long desiredRowId = currentBookshelf.getTopRowId();
            if (actualRowId != desiredRowId) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "position=" + position
                               + "|desiredRowId=" + desiredRowId
                               + "|actualRowId=" + actualRowId);
                }
//                // TODO: the intention is to TRY to FIND the correct position obviously;
//                //  --/++ are placeholders and do not work
//                if (actualRowId < desiredRowId) {
//                    position++;
//                } else {
//                    position--;
//                }
            }

            if (position < 0) {
                position = 0;
            }
            mLayoutManager.scrollToPositionWithOffset(position,
                                                      currentBookshelf.getTopViewOffset());
        }

        // If a target position array is set, then queue a runnable to scroll to the target
        if (targetNodes != null) {
            mVb.list.post(() -> scrollTo(targetNodes));

        } else {
            // we're at the final position, save it.
            saveListPosition();
        }

        // Prepare the list header fields.
        setHeaders();

        // If we have search criteria enabled (i.e. we're filtering the current list)
        // then we should display the 'up' indicator. See #onBackPressed.
        updateActionBar(mModel.getSearchCriteria().isEmpty());
    }

    /**
     * A new adapter is created each time the list is prepared,
     * as the underlying data can be very different from list to list.
     *
     * @param cursor with list of items
     */
    private void initAdapter(@NonNull final Cursor cursor) {
        mAdapter = new BooklistAdapter(this, mModel.getCurrentStyle(this), cursor);
        mAdapter.setOnRowClickedListener(mOnRowClickedListener);
        mVb.list.setAdapter(mAdapter);
    }

    /**
     * Save current position information.
     * <p>
     * TODO: https://guides.codepath.com/android/Handling-Configuration-Changes#recyclerview
     * but we'd still need to do some manual stuff to keep the position in between
     * app restarts.
     */
    void saveListPosition() {
        if (!isDestroyed()) {
            final int position = mLayoutManager.findFirstVisibleItemPosition();
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            // The number of pixels offset for the first visible row.
            final int topViewOffset;
            final View topView = mVb.list.getChildAt(0);
            if (topView != null) {
                topViewOffset = topView.getTop();
            } else {
                topViewOffset = 0;
            }

            mModel.saveListPosition(this, position, topViewOffset, mAdapter.getItemId(position));
        }
    }

    /**
     * Expand/Collapse the current position in the list.
     *
     * @param topLevel the desired top-level which must be kept visible
     * @param expand   desired state
     */
    private void expandAllNodes(@IntRange(from = 1) final int topLevel,
                                final boolean expand) {
        // It is possible that the list will be empty, if so, ignore
        if (mLayoutManager.findFirstCompletelyVisibleItemPosition() != RecyclerView.NO_POSITION) {
            // save current position in case anything goes wrong during expanding
            saveListPosition();
            // set new states
            mModel.expandAllNodes(topLevel, expand);
            // Save the new top row position.
            saveListPosition();
            // Finally pass in a new cursor and display the list.
            mModel.createNewListCursor();
            onDisplayList(null);
        }
    }

    /**
     * Refresh the cursor/adapter as needed to make the node visible to the user.
     *
     * @param node to put into view.
     */
    public void refreshNodePosition(@NonNull final RowStateDAO.Node node) {
        // make sure the cursor has valid rows for the new position.
        final Cursor cursor = mModel.getListCursor();
        if (cursor.requery()) {
            mAdapter.notifyDataSetChanged();
            if (node.isExpanded) {
                int position = node.getListPosition();
                // if the user expanded the line at the bottom of the screen,
                final int lastPos = mLayoutManager.findLastCompletelyVisibleItemPosition();
                if ((position + 1 == lastPos) || (position == lastPos)) {
                    // then we move the list a minimum of 2 positions upwards
                    // to make the expanded rows visible. Using 3 for comfort.
                    mLayoutManager.scrollToPosition(position + 3);
                }
            }
        } else {
            if (BuildConfig.DEBUG /* always */) {
                throw new IllegalStateException("requery() failed");
            }
        }
    }

    /**
     * Set the position once we know how many items appear in a typical
     * view and we can tell if it is already in the view.
     * <p>
     * called from {@link #onDisplayList}
     *
     * @param targetNodes list of rows of which we want one to be visible to the user.
     */
    private void scrollTo(@NonNull final List<RowStateDAO.Node> targetNodes) {
        // sanity check
        if (targetNodes.isEmpty()) {
            return;
        }

        final int firstVisibleItemPosition = mLayoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItemPosition == RecyclerView.NO_POSITION) {
            // empty list
            return;
        }

        final int lastVisibleItemPosition = mLayoutManager.findLastVisibleItemPosition();
        final int middle = (lastVisibleItemPosition + firstVisibleItemPosition) / 2;

        // Get the first 'target' and make it 'best candidate'
        RowStateDAO.Node best = targetNodes.get(0);
        // distance from currently visible middle row
        int dist = Math.abs(best.getListPosition() - middle);

        // Loop all other rows, looking for a nearer one
        for (int i = 1; i < targetNodes.size(); i++) {
            final RowStateDAO.Node node = targetNodes.get(i);
            final int newDist = Math.abs(node.getListPosition() - middle);
            if (newDist < dist) {
                dist = newDist;
                best = node;
            }
        }

        // If the 'best' row is not in view, or at the edge, scroll it into view.
        scrollTo(best);
    }

    /**
     * Scroll the given node into view.
     *
     * @param node to scroll to
     */
    private void scrollTo(@NonNull final RowStateDAO.Node node) {
        final int firstVisibleItemPosition = mLayoutManager.findFirstVisibleItemPosition();
        if (firstVisibleItemPosition == RecyclerView.NO_POSITION) {
            // empty list
            return;
        }

        final int pos = node.getListPosition();
        // We always scroll 1 more then needed for comfort.
        if (pos <= firstVisibleItemPosition) {
            mLayoutManager.scrollToPosition(pos - 1);
        } else if (pos >= mLayoutManager.findLastVisibleItemPosition()) {
            mLayoutManager.scrollToPosition(pos + 1);
        }
        saveListPosition();

        //   // Without this call some positioning may be off by one row.
        //   final int newPos = pos;
        //   mVb.list.post(() -> {
        //     mVb.list.smoothScrollToPosition(newPos);
        //     // not entirely sure this is needed
        //     mModel.saveAllNodes();
        //     // but this is
        //     saveListPosition();
        //   });
    }

    /**
     * Populate the BookShelf list in the Spinner and set the current bookshelf/style.
     *
     * @return {@code true} if the selected shelf was changed (or set for the first time).
     */
    private boolean setBookShelfSpinner() {
        @Nullable
        final BooksOnBookshelfModel.BookshelfSpinnerEntry previous =
                (BooksOnBookshelfModel.BookshelfSpinnerEntry) mVb.bookshelfSpinner
                        .getSelectedItem();

        // disable the listener while we add the list.
        mVb.bookshelfSpinner.setOnItemSelectedListener(null);
        // (re)load the list of names
        final int currentPos = mModel.initBookshelfNameList(this);
        // and tell the adapter about it.
        mBookshelfSpinnerAdapter.notifyDataSetChanged();
        // Set the current bookshelf.
        mVb.bookshelfSpinner.setSelection(currentPos);
        // See onResume: the listener WILL get triggered!!
        // (re-)enable the listener
        mVb.bookshelfSpinner.setOnItemSelectedListener(mOnBookshelfSelectionChanged);

        final long selected = mModel.getCurrentBookshelf().getId();

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "populateBookShelfSpinner"
                       + "|previous=" + previous
                       + "|selected=" + selected);
        }

        // Flag up if the selection was different.
        return previous == null || selected != previous.getBookshelf().getId();
    }

    /**
     * Prepare visibility for the header lines and set the fixed header fields.
     */
    private void setHeaders() {

        // remove the default title to make space for the bookshelf spinner.
        setTitle("");

        String text;
        text = mModel.getHeaderStyleName(this);
        mVb.listHeader.styleName.setText(text);
        mVb.listHeader.styleName.setVisibility(text != null ? View.VISIBLE : View.GONE);

        text = mModel.getHeaderFilterText(this);
        mVb.listHeader.filterText.setText(text);
        mVb.listHeader.filterText.setVisibility(text != null ? View.VISIBLE : View.GONE);

        text = mModel.getHeaderBookCount(this);
        mVb.listHeader.bookCount.setText(text);
        mVb.listHeader.bookCount.setVisibility(text != null ? View.VISIBLE : View.GONE);
    }


    private void onCancelled(@NonNull final FinishedMessage<GrStatus> message) {
        closeProgressDialog();
        Snackbar.make(mVb.list, R.string.cancelled, Snackbar.LENGTH_LONG).show();
    }

    private void onGrFailure(@NonNull final FinishedMessage<Exception> message) {
        closeProgressDialog();
        Snackbar.make(mVb.list, GrStatus.getMessage(this, message.result),
                      Snackbar.LENGTH_LONG).show();
    }

    private void onGrFinished(@NonNull final FinishedMessage<GrStatus> message) {
        closeProgressDialog();

        Objects.requireNonNull(message.result, ErrorMsg.NULL_TASK_RESULTS);
        if (message.result.getStatus() == GrStatus.FAILED_CREDENTIALS) {
            mGrAuthTask.prompt(this);
        } else {
            Snackbar.make(mVb.list, message.result.getMessage(this),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onProgress(@NonNull final ProgressMessage message) {
        if (mProgressDialog == null) {
            mProgressDialog = getOrCreateProgressDialog(message.taskId);
        }
        mProgressDialog.onProgress(message);
    }

    private void closeProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    @NonNull
    private ProgressDialogFragment getOrCreateProgressDialog(final int taskId) {
        FragmentManager fm = getSupportFragmentManager();

        // get dialog after a fragment restart
        ProgressDialogFragment dialog = (ProgressDialogFragment)
                fm.findFragmentByTag(ProgressDialogFragment.TAG);

        // not found? create it
        if (dialog == null) {
            switch (taskId) {
                case R.id.TASK_ID_GR_REQUEST_AUTH:
                    dialog = ProgressDialogFragment
                            .newInstance(R.string.lbl_registration, false, true, 0);
                    break;
                case R.id.TASK_ID_GR_SEND_ONE_BOOK:
                    dialog = ProgressDialogFragment
                            .newInstance(R.string.gr_title_send_book, false, true, 0);
                    break;

                default:
                    throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + "id=" + taskId);
            }
            dialog.show(fm, ProgressDialogFragment.TAG);
        }

        // hook the task up.
        switch (taskId) {
            case R.id.TASK_ID_GR_REQUEST_AUTH:
                dialog.setCanceller(mGrAuthTask);
                break;
            case R.id.TASK_ID_GR_SEND_ONE_BOOK:
                dialog.setCanceller(mGrSendOneBookTask);
                break;

            default:
                throw new IllegalArgumentException(ErrorMsg.UNEXPECTED_VALUE + "taskId=" + taskId);
        }
        return dialog;
    }
}
