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
package com.hardbacknutter.nevertoomanybooks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.IntRange;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.fastscroller.FastScroller;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AddBookBySearchContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.AuthorWorksContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.CalibreSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookFromBundleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookOutput;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookshelvesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ExportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ImportContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.PreferredStylesContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.SearchFtsContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ShowBookPagerContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.StripInfoSyncContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateBooklistContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.UpdateSingleBookContract;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.bookdetails.ShowBookDetailsFragment;
import com.hardbacknutter.nevertoomanybooks.bookedit.EditBookExternalIdFragment;
import com.hardbacknutter.nevertoomanybooks.booklist.BoBTask;
import com.hardbacknutter.nevertoomanybooks.booklist.BookChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistAdapter;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNode;
import com.hardbacknutter.nevertoomanybooks.booklist.RowChangedListener;
import com.hardbacknutter.nevertoomanybooks.booklist.TopLevelItemDecoration;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.database.CursorRow;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.BooksonbookshelfHeaderBinding;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditAuthorDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.EditBookshelfDialogFragment;
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
import com.hardbacknutter.nevertoomanybooks.entities.DataHolderUtils;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.calibre.CalibreHandler;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.MenuUtils;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtPopupMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.FabMenu;
import com.hardbacknutter.nevertoomanybooks.widgets.SpinnerInteractionListener;

/**
 * Activity that displays a flattened book hierarchy based on the Booklist* classes.
 * <p>
 * URGENT: This class is littered with ActivityResultLauncher and *DialogFragment.Launcher
 * objects etc... Refactor to sharing the VM is becoming VERY urgent.
 *
 * <p>
 * Notes on the local-search:
 * <ol>Advanced:
 *     <li>User clicks navigation panel menu search option</li>
 *     <li>{@link SearchFtsFragment} is started</li>
 *     <li>{@link SearchFtsFragment} returns an id-list and the fts search terms</li>
 *     <li>{@link #ftsSearchLauncher} sets the incoming fts criteria</li>
 *     <li>{@link #onResume} builds the list</li>
 * </ol>
 *
 * <ol>Standard:
 *     <li>User clicks option menu search icon</li>
 *     <li>shows the search widget, user types</li>
 *     <li>{@link #onNewIntent} gets called with the query data</li>
 *     <li>build the list</li>
 * </ol>
 * <p>
 * We check if we have search criteria, if not we just build and are done.<br>
 *
 * <ol>When we do have search criteria:
 *     <li>during display of the list, the action bar home icon is set to 'up'</li>
 *     <li>Allows the user to re-open the nav drawer and refine the search.</li>
 *     <li>any 'up/back' action will trigger {@link #onBackPressed}</li>
 *     <li>{@link #onBackPressed} checks if there are search criteria, if so, clears and
 *     rebuild and suppresses the 'back' action</li>
 * </ol>
 */
public class BooksOnBookshelf
        extends BaseActivity
        implements BookChangedListener {

    private static final int FAB_4_SEARCH_EXTERNAL_ID = 4;
    /** Log tag. */
    private static final String TAG = "BooksOnBookshelf";

    /** {@link FragmentResultListener} request key. */
    private static final String RK_EDIT_BOOKSHELF = TAG + ":rk:" + EditBookshelfDialogFragment.TAG;
    /** {@link FragmentResultListener} request key. */
    private static final String RK_STYLE_PICKER = TAG + ":rk:" + StylePickerDialogFragment.TAG;
    /** {@link FragmentResultListener} request key. */
    private static final String RK_EDIT_LENDER = TAG + ":rk:" + EditLenderDialogFragment.TAG;

    private static final String RK_FILTERS = TAG + ":rk:" + BookshelfFiltersDialogFragment.TAG;

    /** Make a backup. */
    private final ActivityResultLauncher<Void> exportLauncher =
            registerForActivityResult(new ExportContract(), success -> {
            });
    /** Bring up the synchronization options. */
    @Nullable
    private ActivityResultLauncher<Void> stripInfoSyncLauncher;
    /** Bring up the synchronization options. */
    @Nullable
    private ActivityResultLauncher<Void> calibreSyncLauncher;
    /** Delegate to handle all interaction with a Calibre server. */
    @Nullable
    private CalibreHandler calibreHandler;
    /** Multi-type adapter to manage list connection to cursor. */
    @Nullable
    private BooklistAdapter adapter;
    /** The Activity ViewModel. */
    private BooksOnBookshelfViewModel vm;
    /** Display a Book. */
    private final ActivityResultLauncher<ShowBookPagerContract.Input> displayBookLauncher =
            registerForActivityResult(new ShowBookPagerContract(), this::onBookEditFinished);
    /** Do an import. */
    private final ActivityResultLauncher<Void> importLauncher =
            registerForActivityResult(new ImportContract(), this::onImportFinished);
    /** Manage the list of (preferred) styles. */
    private final ActivityResultLauncher<String> editStylesLauncher =
            registerForActivityResult(new PreferredStylesContract(), data -> {
                if (data != null) {
                    // we get the UUID for the selected style back.
                    if (data.uuid != null) {
                        vm.onStyleChanged(this, data.uuid);
                    }

                    // This is independent from the above style having been modified ot not.
                    if (data.isModified) {
                        vm.setForceRebuildInOnResume(true);
                    }
                }
            });

    /** Edit a Book. */
    private final ActivityResultLauncher<Long> editByIdLauncher =
            registerForActivityResult(new EditBookByIdContract(), this::onBookEditFinished);

    /** Duplicate and edit a Book. */
    private final ActivityResultLauncher<Bundle> duplicateLauncher =
            registerForActivityResult(new EditBookFromBundleContract(), this::onBookEditFinished);

    /** Update an individual Book with information from the internet. */
    private final ActivityResultLauncher<Book> updateBookLauncher =
            registerForActivityResult(new UpdateSingleBookContract(), this::onBookEditFinished);

    /** Add a Book by doing a search on the internet. */
    private final ActivityResultLauncher<AddBookBySearchContract.By> addBookBySearchLauncher =
            registerForActivityResult(new AddBookBySearchContract(), this::onBookEditFinished);

    /** Update a list of Books with information from the internet. */
    private final ActivityResultLauncher<UpdateBooklistContract.Input> updateBookListLauncher =
            registerForActivityResult(new UpdateBooklistContract(), this::onBookEditFinished);

    /** View all works of an Author. */
    private final ActivityResultLauncher<AuthorWorksContract.Input> authorWorksLauncher =
            registerForActivityResult(new AuthorWorksContract(), this::onBookEditFinished);
    /** The local FTS based search. */
    private final ActivityResultLauncher<SearchCriteria> ftsSearchLauncher =
            registerForActivityResult(new SearchFtsContract(), criteria -> {
                if (criteria != null) {
                    vm.setSearchCriteria(criteria);
                    vm.setForceRebuildInOnResume(true);
                }
            });

    /** Manage the book shelves. */
    private final ActivityResultLauncher<Long> manageBookshelvesLauncher =
            registerForActivityResult(new EditBookshelvesContract(), bookshelfId -> {
                if (bookshelfId != 0 && bookshelfId != vm.getCurrentBookshelf().getId()) {
                    vm.setCurrentBookshelf(this, bookshelfId);
                    vm.setForceRebuildInOnResume(true);
                }
            });

    /** Edit an individual style. */
    private final ActivityResultLauncher<EditStyleContract.Input> editStyleLauncher =
            registerForActivityResult(new EditStyleContract(), data -> {
                if (data != null) {
                    // We get here from the StylePickerDialogFragment (i.e. the style menu)
                    // when the user choose to EDIT a style.
                    if (data.uuid != null && !data.uuid.isEmpty()) {
                        vm.onStyleEdited(this, data.uuid);

                        // ALWAYS rebuild here, even when the style was not modified
                        // as we're handling this as a style-change
                        // (we could do checks... but it's not worth the effort.)
                        // i.e. same as in mOnStylePickerListener
                        vm.setForceRebuildInOnResume(true);
                    }
                }
            });

    private ToolbarMenuProvider toolbarMenuProvider;
    /** Encapsulates the FAB button/menu. */
    private FabMenu fabMenu;

    /** View Binding. */
    private BooksonbookshelfBinding vb;
    private final BookshelfFiltersDialogFragment.Launcher bookshelfFiltersLauncher =
            new BookshelfFiltersDialogFragment.Launcher() {
                @Override
                public void onResult(final boolean modified) {
                    if (modified) {
                        buildBookList();
                    }
                }
            };
    /** List layout manager. */
    private LinearLayoutManager layoutManager;

    /**
     * Accept the result from the dialog.
     */
    private final EditBookshelfDialogFragment.Launcher editBookshelfLauncher =
            new EditBookshelfDialogFragment.Launcher() {
                @Override
                public void onResult(final long bookshelfId) {
                    if (bookshelfId != vm.getCurrentBookshelf().getId()) {
                        onRowChanged(DBKey.FK_BOOKSHELF, bookshelfId);
                    }
                }
            };

    /** Listener for the Bookshelf Spinner. */
    private final SpinnerInteractionListener bookshelfSelectionChangedListener =
            new SpinnerInteractionListener() {
                @Override
                public void onItemSelected(final long id) {
                    if (id != vm.getCurrentBookshelf().getId()) {
                        saveListPosition();
                        vm.setCurrentBookshelf(BooksOnBookshelf.this, id);
                        buildBookList();
                    }
                }
            };

    /**
     * React to row changes made.
     * <p>
     * A number of dialogs use this common listener to report their changes back to us.
     * {@link EditAuthorDialogFragment},
     * {@link EditSeriesDialogFragment}
     * {@link EditPublisherDialogFragment}
     * and others.
     *
     * @see #onMenuItemSelected(MenuItem, int)
     */
    private final RowChangedListener rowChangedListener = new RowChangedListener() {
        @Override
        public void onChange(@NonNull final String key,
                             final long id) {
            BooksOnBookshelf.this.onRowChanged(key, id);
        }
    };

    /**
     * React to the user selecting a style to apply.
     * <p>
     * We get here after the user SELECTED a style on the {@link StylePickerDialogFragment}.
     * We do NOT come here when the user decided to EDIT a style,
     * which is handled {@link #editStyleLauncher}.
     */
    private final StylePickerDialogFragment.Launcher stylePickerLauncher =
            new StylePickerDialogFragment.Launcher() {
                @Override
                public void onResult(@NonNull final String uuid) {
                    saveListPosition();
                    vm.onStyleChanged(BooksOnBookshelf.this, uuid);
                    vm.resetPreferredListRebuildMode(BooksOnBookshelf.this);
                    buildBookList();
                }
            };

    /**
     * Accept the result from the dialog.
     */
    private final EditLenderDialogFragment.Launcher editLenderLauncher =
            new EditLenderDialogFragment.Launcher() {
                @Override
                public void onResult(@IntRange(from = 1) final long bookId,
                                     @NonNull final String loanee) {
                    onBookUpdated(vm.getBook(bookId), DBKey.LOANEE_NAME);
                }
            };

    private final BooklistAdapter.OnRowClickedListener onRowClickedListener =
            new BooklistAdapter.OnRowClickedListener() {

                /**
                 * User clicked a row.
                 * <ul>
                 *      <li>Book: open the details page.</li>
                 *      <li>Not a book: expand/collapse the section as appropriate.</li>
                 * </ul>
                 */
                @Override
                public void onItemClick(final int position) {
                    final Cursor cursor = adapter.getCursor();
                    // Move the cursor, so we can read the data for this row.
                    // Paranoia: if the user can click it, then this move should be fine.
                    if (!cursor.moveToPosition(position)) {
                        return;
                    }

                    final DataHolder rowData = new CursorRow(cursor);

                    if (rowData.getInt(DBKey.KEY_BL_NODE_GROUP) == BooklistGroup.BOOK) {
                        // It's a book, open the details page.
                        final long bookId = rowData.getLong(DBKey.FK_BOOK);
                        // store the id as the current 'central' book for repositioning
                        vm.setCurrentCenteredBookId(bookId);

                        if (hasEmbeddedDetailsFrame()) {
                            //  On larger screens, opens the book details fragment embedded.
                            openEmbeddedBookDetails(bookId);
                        } else {
                            //  On small screens, opens a ViewPager with the book details
                            //  and swipe prev/next functionality.
                            displayBookLauncher.launch(new ShowBookPagerContract.Input(
                                    bookId,
                                    vm.getStyle(BooksOnBookshelf.this).getUuid(),
                                    vm.getBookNavigationTableName(),
                                    rowData.getLong(DBKey.PK_ID)));
                        }
                    } else {
                        // it's a level, expand/collapse.
                        setNodeState(rowData, BooklistNode.NextState.Toggle, 1);
                    }
                }

                @Override
                public boolean onItemLongClick(@NonNull final View v,
                                               final int position) {
                    return onCreateContextMenu(v, position);
                }
            };

    /**
     * The adapter used to fill the Bookshelf selector.
     */
    private ExtArrayAdapter<Bookshelf> bookshelfAdapter;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        vb = BooksonbookshelfBinding.inflate(getLayoutInflater());
        setContentView(vb.getRoot());

        createFragmentResultListeners();
        createViewModel();
        createSyncDelegates();
        createHandlers();

        initNavDrawer();
        initToolbar();

        createBookshelfSpinner();
        // setup the list related stuff; the actual list data is generated in onResume
        createBooklistView();

        // Initialise adapter without a cursor. We'll recreate it with a cursor when
        // we're ready to display the book-list.
        // If we don't create it here then some Android internals cause problems.
        createListAdapter(false);

        createFabMenu();
        updateSyncMenuVisibility();

        // Popup the search widget when the user starts to type.
        setDefaultKeyMode(Activity.DEFAULT_KEYS_SEARCH_LOCAL);

        // check & get search text coming from a system search intent
        handleStandardSearchIntent(getIntent());

        if (savedInstanceState == null) {
            TipManager.getInstance().display(this, R.string.tip_book_list, null);

            if (vm.isProposeBackup()) {
                new MaterialAlertDialogBuilder(this)
                        .setIcon(R.drawable.ic_baseline_warning_24)
                        .setTitle(R.string.app_name)
                        .setMessage(R.string.warning_backup_request)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                exportLauncher.launch(null))
                        .create()
                        .show();
            }
        }
    }

    private void initToolbar() {
        setNavIcon();

        vb.toolbar.setNavigationOnClickListener(v -> {
            if (isRootActivity()) {
                vb.drawerLayout.openDrawer(GravityCompat.START);
            } else {
                onBackPressed();
            }
        });

        toolbarMenuProvider = new ToolbarMenuProvider();
        vb.toolbar.addMenuProvider(toolbarMenuProvider, this);
    }

    private void setNavIcon() {
        if (isRootActivity()) {
            vb.toolbar.setNavigationIcon(R.drawable.ic_baseline_menu_24);
        } else {
            vb.toolbar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        }
    }

    private boolean isRootActivity() {
        return isTaskRoot() && vm.getSearchCriteria().isEmpty();
    }

    private void createFragmentResultListeners() {
        final FragmentManager fm = getSupportFragmentManager();

        rowChangedListener.registerForFragmentResult(fm, this);

        editBookshelfLauncher.registerForFragmentResult(fm, RK_EDIT_BOOKSHELF, this);
        stylePickerLauncher.registerForFragmentResult(fm, RK_STYLE_PICKER, this);
        editLenderLauncher.registerForFragmentResult(fm, RK_EDIT_LENDER, this);
        bookshelfFiltersLauncher.registerForFragmentResult(fm, RK_FILTERS, this);
    }

    private void createViewModel() {
        // Does not use the full progress dialog. Instead uses the overlay progress bar.
        vm = new ViewModelProvider(this).get(BooksOnBookshelfViewModel.class);
        vm.init(this, getIntent().getExtras());

        vm.onCancelled().observe(this, this::onBuildCancelled);
        vm.onFailure().observe(this, this::onBuildFailed);
        vm.onFinished().observe(this, this::onBuildFinished);
    }

    /**
     * Create the optional launcher and delegates.
     */
    private void createSyncDelegates() {

        // Reminder: this method cannot be called from onResume... registerForActivityResult
        // can only be called from onCreate

        if (SyncServer.CalibreCS.isEnabled()) {
            if (calibreSyncLauncher == null) {
                calibreSyncLauncher = registerForActivityResult(
                        new CalibreSyncContract(), result -> {
                            // If we imported anything at all... rebuild
                            if (result == CalibreSyncContract.RESULT_READ_DONE) {
                                vm.setForceRebuildInOnResume(true);
                            }
                        });
            }
        }

        if (SyncServer.StripInfo.isEnabled()) {
            if (stripInfoSyncLauncher == null) {
                stripInfoSyncLauncher = registerForActivityResult(
                        new StripInfoSyncContract(), result -> {
                            // If we imported anything at all... rebuild
                            if (result == StripInfoSyncContract.RESULT_READ_DONE) {
                                vm.setForceRebuildInOnResume(true);
                            }
                        });
            }
        }
    }

    /**
     * Create the (optional) handlers.
     */
    private void createHandlers() {
        if (calibreHandler == null && SyncServer.CalibreCS.isEnabled()) {
            try {
                calibreHandler = new CalibreHandler(this, this)
                        .setProgressFrame(findViewById(R.id.progress_frame));
                calibreHandler.onViewCreated(this, vb.getRoot());
            } catch (@NonNull final CertificateException ignore) {
//                TipManager.getInstance().display(this, R.string.tip_calibre, null);
                // ignore
            }
        }
    }

    private void createBooklistView() {
        //noinspection ConstantConditions
        layoutManager = (LinearLayoutManager) vb.content.list.getLayoutManager();
        vb.content.list.addItemDecoration(new TopLevelItemDecoration(this));

        // Optional overlay
        final int overlayType = Prefs.getFastScrollerOverlayType(this);
        FastScroller.attach(vb.content.list, overlayType);

        // Number of views to cache offscreen arbitrarily set to 20; the default is 2.
        vb.content.list.setItemViewCacheSize(20);
        vb.content.list.setDrawingCacheEnabled(true);
        vb.content.list.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);
    }

    /**
     * Create the adapter and (optionally) set the cursor.
     * <p>
     * <strong>Developer note:</strong>
     * There seems to be no other solution but to always create the adapter
     * in {@link #onCreate} (with null cursor) and RECREATE it when we have a valid cursor.
     * Tested several strategies, but it seems to be impossible to RELIABLY
     * flush the adapter cache of View/ViewHolder.
     * i.e. {@link RecyclerView#getRecycledViewPool()} .clear() is not enough!
     * <p>
     * Not setting an adapter at all in {@link #onCreate} is not a solution either...
     * crashes assured! Also see {@link #buildBookList}.
     *
     * @param display set to {@code false} for initial creation!
     *
     * @return the item count of the list adapter.
     */
    @IntRange(from = 0)
    private int createListAdapter(final boolean display) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "createListAdapter|display=" + display, new Throwable());
        }

        if (display) {
            adapter = new BooklistAdapter(this);
            // install single and long-click listeners
            adapter.setOnRowClickedListener(onRowClickedListener);
            // hookup the cursor
            adapter.setCursor(this, vm.getNewListCursor(), vm.getStyle(this));

            // Combine the adapters for the list header and the actual list
            final ConcatAdapter concatAdapter = new ConcatAdapter(
                    new ConcatAdapter.Config.Builder()
                            .setIsolateViewTypes(true)
                            .setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS)
                            .build(),
                    new HeaderAdapter(this), adapter);

            vb.content.list.setAdapter(concatAdapter);

            // make the list view visible!
            vb.content.list.setVisibility(View.VISIBLE);

            return adapter.getItemCount();

        } else {
            vb.content.list.setVisibility(View.GONE);
            return 0;
        }
    }

    /**
     * Listener for clicks on the list.
     */
    private void createBookshelfSpinner() {
        // remove the default title to make space for the spinner.
        setTitle("");

        // The list is initially empty here; loading the list and
        // setting/selecting the current shelf are both done in onResume
        bookshelfAdapter = new EntityArrayAdapter<>(this, vm.getBookshelfList());

        vb.bookshelfSpinner.setAdapter(bookshelfAdapter);
        bookshelfSelectionChangedListener.attach(vb.bookshelfSpinner);
    }

    private void createFabMenu() {
        fabMenu = new FabMenu(vb.fab, vb.fabOverlay,
                              vb.fab0ScanBarcode,
                              vb.fab1SearchIsbn,
                              vb.fab2SearchText,
                              vb.fab3AddManually,
                              vb.fab4SearchExternalId);

        fabMenu.attach(vb.content.list);
        fabMenu.setOnClickListener(view -> onFabMenuItemSelected(view.getId()));
        fabMenu.getItem(FAB_4_SEARCH_EXTERNAL_ID)
               .setEnabled(EditBookExternalIdFragment.isShowTab());
    }

    /**
     * Show or hide the synchronization menu.
     */
    private void updateSyncMenuVisibility() {
        final boolean enable =
                (SyncServer.CalibreCS.isEnabled() && calibreSyncLauncher != null)
                ||
                (SyncServer.StripInfo.isEnabled() && stripInfoSyncLauncher != null);

        //noinspection ConstantConditions
        getNavigationMenuItem(R.id.SUBMENU_SYNC).setVisible(enable);
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
        vm.setForceRebuildInOnResume(true);
    }

    /**
     * Handle the standard search intent / suggestions click.
     * <p>
     * See
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
            // The ACTION_VIEW as set in src/main/res/xml/searchable.xml/searchSuggestIntentAction
            query = intent.getDataString();
        } else {
            query = null;
        }
        // actioning on the criteria wil happen automatically at list building time.
        vm.getSearchCriteria().setFtsKeywords(query);
    }

    @Override
    boolean onNavigationItemSelected(@NonNull final MenuItem menuItem) {
        final int itemId = menuItem.getItemId();

        if (itemId == R.id.SUBMENU_SYNC) {
            showNavigationSubMenu(R.id.SUBMENU_SYNC, menuItem, R.menu.sync);
            return false;
        }

        closeNavigationDrawer();

        if (itemId == R.id.MENU_ADVANCED_SEARCH) {
            ftsSearchLauncher.launch(vm.getSearchCriteria());
            return true;

        } else if (itemId == R.id.MENU_MANAGE_BOOKSHELVES) {
            // overridden, so we can pass the current bookshelf id.
            manageBookshelvesLauncher.launch(vm.getCurrentBookshelf().getId());
            return true;

        } else if (itemId == R.id.MENU_MANAGE_LIST_STYLES) {
            editStylesLauncher.launch(vm.getStyle(this).getUuid());
            return true;

        } else if (itemId == R.id.MENU_FILE_IMPORT) {
            importLauncher.launch(null);
            return true;

        } else if (itemId == R.id.MENU_FILE_EXPORT) {
            exportLauncher.launch(null);
            return true;

        } else if (itemId == R.id.MENU_SYNC_CALIBRE && calibreSyncLauncher != null) {
            calibreSyncLauncher.launch(null);
            return false;

        } else if (itemId == R.id.MENU_SYNC_STRIP_INFO && stripInfoSyncLauncher != null) {
            stripInfoSyncLauncher.launch(null);
            return false;
        }

        return super.onNavigationItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        // If the FAB menu is showing, hide it and suppress the back key.
        if (fabMenu.hideMenu()) {
            return;
        }

        // If the current list is has any search criteria enabled, clear them and rebuild the list.
        if (isTaskRoot() && !vm.getSearchCriteria().isEmpty()) {
            vm.getSearchCriteria().clear();
            setNavIcon();
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
        if (recreateVm.isRecreating() || isFinishing() || isDestroyed()) {
            return;
        }

        // If we have search criteria enabled (i.e. we're filtering the current list)
        // then we should display the 'up' indicator. See #onBackPressed.
        setNavIcon();

        updateSyncMenuVisibility();
        fabMenu.getItem(FAB_4_SEARCH_EXTERNAL_ID)
               .setEnabled(EditBookExternalIdFragment.isShowTab());

        // Initialize/Update the list of bookshelves
        vm.reloadBookshelfList(this);
        bookshelfAdapter.notifyDataSetChanged();
        // and select the current shelf.
        final int selectedPosition = vm.getSelectedBookshelfSpinnerPosition(this);
        vb.bookshelfSpinner.setSelection(selectedPosition);


        final boolean forceRebuildInOnResume = vm.isForceRebuildInOnResume();
        // always reset for next iteration.
        vm.setForceRebuildInOnResume(false);

        if (forceRebuildInOnResume || !vm.isListLoaded()) {
            buildBookList();

        } else {
            // no rebuild needed/done, just let the system redisplay the list state
            displayList(vm.getTargetNodes());
        }
    }

    @Override
    @CallSuper
    public void onPause() {
        fabMenu.hideMenu();
        saveListPosition();
        super.onPause();
    }

    /**
     * Create a context menu based on row group.
     *
     * @param v        View clicked
     * @param position The position of the item within the adapter's data set.
     *
     * @return {@code true} if there is a menu to show
     */
    private boolean onCreateContextMenu(@NonNull final View v,
                                        final int position) {

        //noinspection ConstantConditions
        final Cursor cursor = adapter.getCursor();
        // Move the cursor, so we can read the data for this row.
        // Paranoia: if the user can click it, then this move should be fine.
        if (!cursor.moveToPosition(position)) {
            return false;
        }

        final DataHolder rowData = new CursorRow(cursor);

        final ExtPopupMenu contextMenu = new ExtPopupMenu(this)
                .setGroupDividerEnabled();
        final Menu menu = contextMenu.getMenu();

        final int rowGroupId = rowData.getInt(DBKey.KEY_BL_NODE_GROUP);
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                final MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.book, menu);

                if (!hasEmbeddedDetailsFrame()) {
                    // explicitly hide; but otherwise leave it to the details-frame menu handler.
                    menu.findItem(R.id.MENU_SYNC_LIST_WITH_DETAILS).setVisible(false);
                }

                if (calibreHandler != null) {
                    calibreHandler.onCreateMenu(menu, inflater);
                }
                vm.getViewBookHandler().onCreateMenu(menu, inflater);
                vm.getAmazonHandler().onCreateMenu(menu, inflater);

                final boolean isRead = rowData.getBoolean(DBKey.READ__BOOL);
                menu.findItem(R.id.MENU_BOOK_SET_READ).setVisible(!isRead);
                menu.findItem(R.id.MENU_BOOK_SET_UNREAD).setVisible(isRead);

                // specifically check LOANEE_NAME independent from the style in use.
                final boolean useLending = GlobalFieldVisibility.isUsed(DBKey.LOANEE_NAME);
                final boolean isAvailable = vm.isAvailable(rowData);
                menu.findItem(R.id.MENU_BOOK_LOAN_ADD).setVisible(useLending && isAvailable);
                menu.findItem(R.id.MENU_BOOK_LOAN_DELETE).setVisible(useLending && !isAvailable);

                if (calibreHandler != null) {
                    final Book book = DataHolderUtils.requireBook(rowData);
                    calibreHandler.onPrepareMenu(this, menu, book);
                }

                vm.getViewBookHandler().onPrepareMenu(menu, rowData);
                vm.getAmazonHandler().onPrepareMenu(menu, rowData);
                break;
            }
            case BooklistGroup.AUTHOR: {
                final MenuInflater inflater = getMenuInflater();
                inflater.inflate(R.menu.author, menu);
                vm.getAmazonHandler().onCreateMenu(menu, inflater);

                final boolean complete = rowData.getBoolean(DBKey.AUTHOR_IS_COMPLETE);
                menu.findItem(R.id.MENU_AUTHOR_SET_COMPLETE).setVisible(!complete);
                menu.findItem(R.id.MENU_AUTHOR_SET_INCOMPLETE).setVisible(complete);

                vm.getAmazonHandler().onPrepareMenu(menu, rowData);
                break;
            }
            case BooklistGroup.SERIES: {
                if (rowData.getLong(DBKey.FK_SERIES) != 0) {
                    final MenuInflater inflater = getMenuInflater();
                    inflater.inflate(R.menu.series, menu);
                    vm.getAmazonHandler().onCreateMenu(menu, inflater);

                    final boolean complete = rowData.getBoolean(DBKey.SERIES_IS_COMPLETE);
                    menu.findItem(R.id.MENU_SERIES_SET_COMPLETE).setVisible(!complete);
                    menu.findItem(R.id.MENU_SERIES_SET_INCOMPLETE).setVisible(complete);

                    vm.getAmazonHandler().onPrepareMenu(menu, rowData);

                } else {
                    menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                             getResources().getInteger(R.integer.MENU_ORDER_UPDATE_FIELDS),
                             R.string.menu_update_books)
                        .setIcon(R.drawable.ic_baseline_cloud_download_24);
                }
                break;
            }
            case BooklistGroup.PUBLISHER: {
                if (rowData.getLong(DBKey.FK_PUBLISHER) != 0) {
                    getMenuInflater().inflate(R.menu.publisher, menu);
                } else {
                    menu.add(Menu.NONE, R.id.MENU_UPDATE_FROM_INTERNET,
                             getResources().getInteger(R.integer.MENU_ORDER_UPDATE_FIELDS),
                             R.string.menu_update_books)
                        .setIcon(R.drawable.ic_baseline_cloud_download_24);
                }
                break;
            }
            case BooklistGroup.BOOKSHELF: {
                if (!rowData.getString(DBKey.FK_BOOKSHELF).isEmpty()) {
                    getMenuInflater().inflate(R.menu.bookshelf, menu);
                }
                break;
            }
            case BooklistGroup.LANGUAGE: {
                if (!rowData.getString(DBKey.LANGUAGE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LANGUAGE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.LOCATION: {
                if (!rowData.getString(DBKey.LOCATION).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_LOCATION_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.GENRE: {
                if (!rowData.getString(DBKey.GENRE).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_GENRE_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.FORMAT: {
                if (!rowData.getString(DBKey.FORMAT).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_FORMAT_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            case BooklistGroup.COLOR: {
                if (!rowData.getString(DBKey.COLOR).isEmpty()) {
                    menu.add(Menu.NONE, R.id.MENU_COLOR_EDIT,
                             getResources().getInteger(R.integer.MENU_ORDER_EDIT),
                             R.string.action_edit_ellipsis)
                        .setIcon(R.drawable.ic_baseline_edit_24);
                }
                break;
            }
            default: {
                break;
            }
        }

        int menuOrder = getResources().getInteger(R.integer.MENU_ORDER_NEXT_MISSING_COVER);

        // forms its own group
        menu.add(R.id.MENU_NEXT_MISSING_COVER, R.id.MENU_NEXT_MISSING_COVER, menuOrder++,
                 R.string.lbl_next_book_without_cover)
            .setIcon(R.drawable.ic_baseline_broken_image_24);

        // if it's a level, add the expand option
        if (rowData.getInt(DBKey.KEY_BL_NODE_GROUP) != BooklistGroup.BOOK) {
            //noinspection UnusedAssignment
            menu.add(R.id.MENU_GROUP_BOB_EXPANSION, R.id.MENU_LEVEL_EXPAND, menuOrder++,
                     R.string.lbl_level_expand)
                .setIcon(R.drawable.ic_baseline_unfold_more_24);
        }

        // If we actually have a menu, show it.
        if (menu.size() > 0) {
            // we have a menu to show, set the title according to the level.
            final int level = rowData.getInt(DBKey.KEY_BL_NODE_LEVEL);
            contextMenu.setTitle(adapter.getLevelText(position, level));

            if (menu.size() < 5) {
                // small menu, show it anchored to the row
                contextMenu.showAsDropDown(v, menuItem -> onMenuItemSelected(menuItem, position));

            } else if (hasEmbeddedDetailsFrame()) {
                contextMenu.show(v, Gravity.START,
                                 menuItem -> onMenuItemSelected(menuItem, position));
            } else {
                contextMenu.show(v, Gravity.CENTER,
                                 menuItem -> onMenuItemSelected(menuItem, position));
            }
            return true;
        }

        return false;
    }

    /**
     * Using {@link ExtPopupMenu} for context menus.
     *
     * @param menuItem that was selected
     * @param position in the list
     *
     * @return {@code true} if handled.
     */
    private boolean onMenuItemSelected(@NonNull final MenuItem menuItem,
                                       final int position) {
        final int itemId = menuItem.getItemId();

        // Move the cursor, so we can read the data for this row.
        // The majority of the time this is not needed, but a fringe case (toggle node)
        // showed it should indeed be done.
        // Paranoia: if the user can click it, then this should be fine.
        Objects.requireNonNull(adapter, "adapter");
        final Cursor cursor = adapter.getCursor();
        if (!cursor.moveToPosition(position)) {
            return false;
        }

        final DataHolder rowData = new CursorRow(cursor);

        // Check for row-group independent options first.

        if (itemId == R.id.MENU_NEXT_MISSING_COVER) {
            final long nodeRowId = rowData.getLong(DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID);
            vm.getNextBookWithoutCover(nodeRowId).ifPresent(this::displayList);
            return true;

        } else if (itemId == R.id.MENU_LEVEL_EXPAND) {
            setNodeState(rowData, BooklistNode.NextState.Expand,
                         vm.getStyle(this).getGroupCount());
            return true;

        } else if (itemId == R.id.MENU_CALIBRE_SETTINGS) {
            final Intent intent = FragmentHostActivity
                    .createIntent(this, CalibrePreferencesFragment.class);
            startActivity(intent);
            return true;
        }

        // Specific row-group options

        final int rowGroupId = rowData.getInt(DBKey.KEY_BL_NODE_GROUP);
        //noinspection SwitchStatementWithoutDefaultBranch
        switch (rowGroupId) {
            case BooklistGroup.BOOK: {
                if (itemId == R.id.MENU_BOOK_SET_READ
                    || itemId == R.id.MENU_BOOK_SET_UNREAD) {
                    final long bookId = rowData.getLong(DBKey.FK_BOOK);
                    // toggle the read status
                    final boolean status = !rowData.getBoolean(DBKey.READ__BOOL);
                    if (vm.setBookRead(bookId, status)) {
                        onBookUpdated(vm.getBook(bookId), DBKey.READ__BOOL);
                    }
                    return true;

                } else if (itemId == R.id.MENU_BOOK_EDIT) {
                    final long bookId = rowData.getLong(DBKey.FK_BOOK);
                    editByIdLauncher.launch(bookId);
                    return true;

                } else if (itemId == R.id.MENU_BOOK_DUPLICATE) {
                    final Book book = DataHolderUtils.requireBook(rowData);
                    duplicateLauncher.launch(book.duplicate());
                    return true;

                } else if (itemId == R.id.MENU_BOOK_DELETE) {
                    final long bookId = rowData.getLong(DBKey.FK_BOOK);
                    final String title = rowData.getString(DBKey.TITLE);
                    final List<Author> authors = vm.getAuthorsByBookId(bookId);
                    StandardDialogs.deleteBook(this, title, authors, () -> {
                        if (vm.deleteBook(bookId)) {
                            onBookDeleted(bookId);
                        }
                    });
                    return true;

                } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                    final Book book = DataHolderUtils.requireBook(rowData);
                    updateBookLauncher.launch(book);
                    return true;

                } else if (itemId == R.id.MENU_BOOK_LOAN_ADD) {
                    final long bookId = rowData.getLong(DBKey.FK_BOOK);
                    editLenderLauncher.launch(bookId, rowData.getString(DBKey.TITLE));
                    return true;

                } else if (itemId == R.id.MENU_BOOK_LOAN_DELETE) {
                    final long bookId = rowData.getLong(DBKey.FK_BOOK);
                    vm.lendBook(bookId, null);
                    onBookUpdated(vm.getBook(bookId), DBKey.LOANEE_NAME);
                    return true;

                } else if (itemId == R.id.MENU_SHARE) {
                    final Book book = DataHolderUtils.requireBook(rowData);
                    startActivity(book.getShareIntent(this));
                    return true;
                }
                break;
            }
            case BooklistGroup.AUTHOR: {
                if (itemId == R.id.MENU_AUTHOR_WORKS) {
                    authorWorksLauncher.launch(new AuthorWorksContract.Input(
                            rowData.getLong(DBKey.FK_AUTHOR),
                            vm.getCurrentBookshelf().getId(),
                            vm.getStyle(this).getUuid()));
                    return true;

                } else if (itemId == R.id.MENU_AUTHOR_SET_COMPLETE
                           || itemId == R.id.MENU_AUTHOR_SET_INCOMPLETE) {
                    final long authorId = rowData.getLong(DBKey.FK_AUTHOR);
                    // toggle the complete status
                    final boolean status = !rowData.getBoolean(DBKey.AUTHOR_IS_COMPLETE);
                    if (vm.setAuthorComplete(authorId, status)) {
                        onRowChanged(DBKey.FK_AUTHOR, authorId);
                    }
                    return true;

                } else if (itemId == R.id.MENU_AUTHOR_EDIT) {
                    final Author author = DataHolderUtils.requireAuthor(rowData);
                    // results come back in mRowChangedListener
                    EditAuthorDialogFragment.launch(getSupportFragmentManager(), author);
                    return true;

                } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                    final String message = rowData.getString(DBKey.KEY_AUTHOR_FORMATTED);
                    updateBooksFromInternetData(position, rowData, message);
                    return true;
                }
                break;
            }
            case BooklistGroup.SERIES: {
                if (itemId == R.id.MENU_SERIES_SET_COMPLETE
                    || itemId == R.id.MENU_SERIES_SET_INCOMPLETE) {
                    final long seriesId = rowData.getLong(DBKey.FK_SERIES);
                    // toggle the complete status
                    final boolean status = !rowData.getBoolean(DBKey.SERIES_IS_COMPLETE);
                    if (vm.setSeriesComplete(seriesId, status)) {
                        onRowChanged(DBKey.FK_SERIES, seriesId);
                    }
                    return true;

                } else if (itemId == R.id.MENU_SERIES_EDIT) {
                    final Series series = DataHolderUtils.requireSeries(rowData);
                    // results come back in mRowChangedListener
                    EditSeriesDialogFragment.launch(getSupportFragmentManager(), series);
                    return true;

                } else if (itemId == R.id.MENU_SERIES_DELETE) {
                    final Series series = DataHolderUtils.requireSeries(rowData);
                    StandardDialogs.deleteSeries(this, series, () -> {
                        vm.delete(this, series);
                        onRowChanged(DBKey.FK_SERIES, series.getId());
                    });
                    return true;

                } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                    final String message = rowData.getString(DBKey.SERIES_TITLE);
                    updateBooksFromInternetData(position, rowData, message);
                    return true;
                }
                break;
            }
            case BooklistGroup.PUBLISHER: {
                if (itemId == R.id.MENU_PUBLISHER_EDIT) {
                    final Publisher publisher = DataHolderUtils.requirePublisher(rowData);
                    // results come back in mRowChangedListener
                    EditPublisherDialogFragment.launch(getSupportFragmentManager(), publisher);
                    return true;

                } else if (itemId == R.id.MENU_PUBLISHER_DELETE) {
                    final Publisher publisher = DataHolderUtils.requirePublisher(rowData);
                    StandardDialogs.deletePublisher(this, publisher, () -> {
                        vm.delete(this, publisher);
                        onRowChanged(DBKey.FK_PUBLISHER, publisher.getId());
                    });
                    return true;

                } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET) {
                    final String message = rowData.getString(DBKey.PUBLISHER_NAME);
                    updateBooksFromInternetData(position, rowData, message);
                    return true;
                }
                break;
            }
            case BooklistGroup.BOOKSHELF: {
                if (itemId == R.id.MENU_BOOKSHELF_EDIT) {
                    final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
                    editBookshelfLauncher.launch(bookshelf);
                    return true;

                } else if (itemId == R.id.MENU_BOOKSHELF_DELETE) {
                    final Bookshelf bookshelf = DataHolderUtils.requireBookshelf(rowData);
                    StandardDialogs.deleteBookshelf(this, bookshelf, () -> {
                        vm.delete(bookshelf);
                        onRowChanged(DBKey.FK_BOOKSHELF, bookshelf.getId());
                    });
                    return true;
                }
                break;
            }
            case BooklistGroup.LANGUAGE: {
                if (itemId == R.id.MENU_LANGUAGE_EDIT) {
                    EditLanguageDialogFragment.launch(getSupportFragmentManager(),
                                                      this, rowData.getString(DBKey.LANGUAGE));
                    return true;
                }
                break;
            }
            case BooklistGroup.LOCATION: {
                if (itemId == R.id.MENU_LOCATION_EDIT) {
                    EditLocationDialogFragment.launch(getSupportFragmentManager(),
                                                      rowData.getString(DBKey.LOCATION));
                    return true;
                }
                break;
            }
            case BooklistGroup.GENRE: {
                if (itemId == R.id.MENU_GENRE_EDIT) {
                    EditGenreDialogFragment.launch(getSupportFragmentManager(),
                                                   rowData.getString(DBKey.GENRE));
                    return true;
                }
                break;
            }
            case BooklistGroup.FORMAT: {
                if (itemId == R.id.MENU_FORMAT_EDIT) {
                    EditFormatDialogFragment.launch(getSupportFragmentManager(),
                                                    rowData.getString(DBKey.FORMAT));
                    return true;
                }
                break;
            }
            case BooklistGroup.COLOR: {
                if (itemId == R.id.MENU_COLOR_EDIT) {
                    EditColorDialogFragment.launch(getSupportFragmentManager(),
                                                   rowData.getString(DBKey.COLOR));
                    return true;
                }
                break;
            }
        }

        // other handlers.
        if (calibreHandler != null
            && calibreHandler.onMenuItemSelected(this, menuItem, rowData)) {
            return true;
        }
        if (vm.getAmazonHandler().onMenuItemSelected(this, menuItem, rowData)) {
            return true;
        }
        if (vm.getViewBookHandler().onMenuItemSelected(this, menuItem, rowData)) {
            return true;
        }
        return false;
    }

    private void onFabMenuItemSelected(@IdRes final int itemId) {

        if (itemId == R.id.fab0_scan_barcode) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.Scan);

        } else if (itemId == R.id.fab1_search_isbn) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.Isbn);

        } else if (itemId == R.id.fab2_search_text) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.Text);

        } else if (itemId == R.id.fab3_add_manually) {
            editByIdLauncher.launch(0L);

        } else if (itemId == R.id.fab4_search_external_id) {
            addBookBySearchLauncher.launch(AddBookBySearchContract.By.ExternalId);

        } else {
            throw new IllegalArgumentException(String.valueOf(itemId));
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void showNavigationSubMenu(@IdRes final int anchorMenuItemId,
                                       @NonNull final MenuItem menuItem,
                                       @MenuRes final int menuRes) {

        final View anchor = getNavigationMenuItemView(anchorMenuItemId);

        final ExtPopupMenu popupMenu = new ExtPopupMenu(this)
                .inflate(menuRes);
        final Menu menu = popupMenu.getMenu();
        if (menuItem.getItemId() == R.id.SUBMENU_SYNC) {
            menu.findItem(R.id.MENU_SYNC_CALIBRE)
                .setVisible(SyncServer.CalibreCS.isEnabled() && calibreSyncLauncher != null);

            menu.findItem(R.id.MENU_SYNC_STRIP_INFO)
                .setVisible(SyncServer.StripInfo.isEnabled() && stripInfoSyncLauncher != null);
        }

        popupMenu.setTitle(menuItem.getTitle())
                 .showAsDropDown(anchor, this::onNavigationItemSelected);
    }

    /**
     * IMPORTANT: this is from a context click on a row.
     * We pass the book ID's which are suited for that row.
     *
     * @param position in the list
     * @param rowData  for the row which was selected
     * @param message  to show to the user; can be 'null' if the group is a "no series" etc...
     */
    private void updateBooksFromInternetData(final int position,
                                             @NonNull final DataHolder rowData,
                                             @Nullable final CharSequence message) {
        final View anchor = layoutManager.findViewByPosition(position);

        //noinspection ConstantConditions
        new ExtPopupMenu(this)
                .inflate(R.menu.update_books)
                .setTitle(getString(R.string.menu_update_books))
                .setMessage(message)
                .showAsDropDown(anchor, menuItem -> {
                    final int itemId = menuItem.getItemId();
                    if (itemId == R.id.MENU_UPDATE_FROM_INTERNET_THIS_SHELF_ONLY) {
                        updateBooksFromInternetData(rowData, true);
                        return true;

                    } else if (itemId == R.id.MENU_UPDATE_FROM_INTERNET_ALL_SHELVES) {
                        updateBooksFromInternetData(rowData, false);
                        return true;
                    }

                    return false;
                });
    }

    private void updateBooksFromInternetData(@NonNull final DataHolder rowData,
                                             final boolean onlyThisShelf) {

        final int groupId = rowData.getInt(DBKey.KEY_BL_NODE_GROUP);
        final String nodeKey = rowData.getString(DBKey.KEY_BL_NODE_KEY);

        switch (groupId) {
            case BooklistGroup.AUTHOR: {
                final long id = rowData.getLong(DBKey.FK_AUTHOR);
                final ArrayList<Long> books = vm.getBookIdsByAuthor(nodeKey, onlyThisShelf, id);
                final String name = id != 0 ? rowData.getString(DBKey.KEY_AUTHOR_FORMATTED)
                                            : getString(R.string.bob_empty_author);

                updateBookListLauncher.launch(new UpdateBooklistContract.Input(
                        books, getString(R.string.name_colon_value,
                                         getString(R.string.lbl_author), name),
                        getString(R.string.name_colon_value,
                                  getString(R.string.lbl_books),
                                  String.valueOf(books.size()))));
                break;
            }
            case BooklistGroup.SERIES: {
                final long id = rowData.getLong(DBKey.FK_SERIES);
                final ArrayList<Long> books = vm.getBookIdsBySeries(nodeKey, onlyThisShelf, id);
                final String name = id != 0 ? rowData.getString(DBKey.SERIES_TITLE)
                                            : getString(R.string.bob_empty_series);

                updateBookListLauncher.launch(new UpdateBooklistContract.Input(
                        books, getString(R.string.name_colon_value,
                                         getString(R.string.lbl_series), name),
                        getString(R.string.name_colon_value,
                                  getString(R.string.lbl_books),
                                  String.valueOf(books.size()))));
                break;
            }
            case BooklistGroup.PUBLISHER: {
                final long id = rowData.getLong(DBKey.FK_PUBLISHER);
                final ArrayList<Long> books = vm.getBookIdsByPublisher(nodeKey, onlyThisShelf, id);
                final String name = id != 0 ? rowData.getString(DBKey.PUBLISHER_NAME)
                                            : getString(R.string.bob_empty_publisher);

                updateBookListLauncher.launch(new UpdateBooklistContract.Input(
                        books, getString(R.string.name_colon_value,
                                         getString(R.string.lbl_publisher), name),
                        getString(R.string.name_colon_value,
                                  getString(R.string.lbl_books),
                                  String.valueOf(books.size()))));
                break;
            }
            default: {
                if (BuildConfig.DEBUG /* always */) {
                    throw new IllegalArgumentException(
                            "updateBooksFromInternetData|not supported|groupId=" + groupId);
                }
                break;
            }
        }
    }

    public void editStyle(@NonNull final Style style,
                          final boolean setAsPreferred) {
        editStyleLauncher.launch(EditStyleContract.edit(style, setAsPreferred));
    }

    /**
     * React to (non-Book) row changes made.
     */
    private void onRowChanged(@NonNull final String key,
                              @IntRange(from = 0) final long id) {
        // ENHANCE: update the modified row without a rebuild.
        saveListPosition();
        buildBookList();
    }

    @Override
    public void onBookDeleted(final long bookId) {
        // ENHANCE: remove the row without a rebuild but this could quickly become complex...
        // e.g. if there is(was) only a single book on the level above
        saveListPosition();
        buildBookList();
    }

    @Override
    public void onSyncBook(final long bookId) {
        final List<BooklistNode> all = vm.getVisibleBookNodes(bookId);
        scrollTo(findBestNode(all));
    }

    /**
     * Receive notifications that a Book was updated.
     * <p>
     * For a limited set of keys, we directly update the list table which is very fast.
     * <p>
     * Other keys, or full books, will always trigger a list rebuild.
     *
     * @param book the book that changed,
     *             or {@code null} to indicate multiple books were potentially changed.
     * @param keys the item(s) that changed,
     *             or {@code null} to indicate ALL data was potentially changed.
     */
    @Override
    public void onBookUpdated(@Nullable final Book book,
                              @Nullable final String... keys) {

        // Reminder: the actual Book table (and/or relations) are ALREADY UPDATED.
        // The only thing we are updating here is the temporary BookList table
        // and the displayed data
        int[] positions = null;

        //TODO: optimize when/if we use more then 2 keys
        if (keys != null && Arrays.asList(keys).contains(DBKey.READ__BOOL)) {
            Objects.requireNonNull(book);
            positions = vm.onBookRead(book.getId(), book.getBoolean(DBKey.READ__BOOL));

        } else if (keys != null && Arrays.asList(keys).contains(DBKey.LOANEE_NAME)) {
            Objects.requireNonNull(book);
            positions = vm.onBookLend(book.getId(), book.getLoanee().orElse(null));

        } else {
            // ENHANCE: update the modified row without a rebuild.
            saveListPosition();
            buildBookList();
        }

        // Refresh the list data for the given positions only.
        if (positions != null) {
            // Yes, requery() is deprecated;
            // but check BooklistCursor were we do the right thing.
            //noinspection ConstantConditions,deprecation
            adapter.getCursor().requery();

            for (final int pos : positions) {
                adapter.notifyItemChanged(pos);
            }
        }
    }

    /**
     * This method is called from a ActivityResultContract after the result intent is parsed.
     * After this method is executed, the flow will take us to #onResume.
     *
     * @param data returned from the view/edit Activity
     */
    private void onBookEditFinished(@Nullable final EditBookOutput data) {
        if (data != null) {
            if (data.modified) {
                vm.setForceRebuildInOnResume(true);
            }

            // If we got an id back, make any (potential) rebuild re-position to it.
            if (data.bookId > 0) {
                vm.setCurrentCenteredBookId(data.bookId);
            }
        }
    }

    /**
     * Called when the user has finished an Import.
     * <p>
     * This method is called from a ActivityResultContract after the result intent is parsed.
     * After this method is executed, the flow will take us to #onResume.
     *
     * @param importResults returned from the import
     */
    private void onImportFinished(@Nullable final ImportResults importResults) {
        if (importResults != null) {
            if (importResults.styles > 0) {
                // Force a refresh of the cached styles
                ServiceLocator.getInstance().getStyles().clearCache();
            }
            if (importResults.preferences > 0) {
                // Refresh the preferred bookshelf. This also refreshes its style.
                vm.reloadSelectedBookshelf(this);
            }

            // styles, prefs, books, covers,... it all requires a rebuild.
            vm.setForceRebuildInOnResume(true);
        }
    }

    /**
     * Start the list builder.
     */
    private void buildBookList() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_INIT_BOOK_LIST) {
            Log.d(TAG, "buildBookList"
                       + "| isBuilding()=" + vm.isBuilding()
                       + "|called from:", new Throwable());
        }

        if (!vm.isBuilding()) {
            vb.progressCircle.show();
            // Invisible... theoretically this means the page should not re-layout
            vb.content.list.setVisibility(View.INVISIBLE);

            // If the book details frame is present, remove it
            final Fragment fragment = getEmbeddedDetailsFrame();
            if (fragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .remove(fragment)
                        .commit();
            }

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                final SimpleDateFormat dateFormat =
                        new SimpleDateFormat("yyyy_MM_dd_hh_mm_ss", Locale.getDefault());
                //noinspection UseOfObsoleteDateTimeApi
                Debug.startMethodTracing("trace-" + dateFormat.format(new Date()));
            }
            // force the adapter to stop displaying by disabling its cursor.
            // DO NOT REMOVE THE ADAPTER FROM FROM THE VIEW;
            // i.e. do NOT call mVb.list.setAdapter(null)... crashes assured when doing so.
            if (adapter != null) {
                adapter.clearCursor();
            }
            vm.buildBookList();
        }
    }

    /**
     * Called when the list build succeeded.
     *
     * @param message from the task; contains the (optional) target rows.
     */
    private void onBuildFinished(
            @NonNull final LiveDataEvent<TaskResult<BoBTask.Outcome>> message) {
        vb.progressCircle.hide();

        message.getData().map(TaskResult::requireResult).ifPresent(result -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BOB_THE_BUILDER_TIMERS) {
                Debug.stopMethodTracing();
            }
            vm.onBuildFinished(result);
            displayList(result.getTargetNodes());
        });
    }

    /**
     * Called when the list build failed or was cancelled.
     *
     * @param message from the task
     */
    private void onBuildCancelled(
            @NonNull final LiveDataEvent<TaskResult<BoBTask.Outcome>> message) {
        vb.progressCircle.hide();

        message.getData().ifPresent(data -> {
            vm.onBuildCancelled();

            if (vm.isListLoaded()) {
                displayList();
            } else {
                recoverAfterFailedBuild();
            }
        });
    }

    /**
     * Called when the list build failed or was cancelled.
     *
     * @param message from the task
     */
    private void onBuildFailed(@NonNull final LiveDataEvent<TaskResult<Exception>> message) {
        vb.progressCircle.hide();
        message.getData().ifPresent(data -> {
            Logger.error(TAG, data.getResult());

            vm.onBuildFailed();

            if (vm.isListLoaded()) {
                displayList();
            } else {
                recoverAfterFailedBuild();
            }
        });
    }

    private void recoverAfterFailedBuild() {
        // Something is REALLY BAD
        // This is usually (BUT NOT ALWAYS) due to the developer making an oopsie
        // with the Styles. i.e. the style used to build is very likely corrupt.
        // Another reason can be during development when the database structure
        // was changed...
        final Style style = vm.getStyle(this);
        // so we reset the style to recover.. and restarting the app will work.
        vm.onStyleChanged(this, BuiltinStyle.DEFAULT_UUID);
        // but we STILL FORCE A CRASH, SO WE CAN COLLECT DEBUG INFORMATION!
        throw new IllegalStateException("Style=" + style);
    }

    /**
     * Expand/Collapse the given position.
     *
     * @param rowData            for the book/level at the given position
     * @param nextState          the required next state of this node
     * @param relativeChildLevel how many child levels below the node should be modified likewise
     */
    private void setNodeState(@NonNull final DataHolder rowData,
                              @NonNull final BooklistNode.NextState nextState,
                              final int relativeChildLevel) {
        saveListPosition();

        final long nodeRowId = rowData.getLong(DBKey.KEY_BL_LIST_VIEW_NODE_ROW_ID);
        final BooklistNode node = vm.setNode(nodeRowId, nextState, relativeChildLevel);

        displayList(node);
    }

    /**
     * Display the list based on the given cursor, and scroll to the last saved position.
     */
    private void displayList() {
        if (createListAdapter(true) > 0) {
            vb.content.list.post(() -> {
                scrollToSavedPosition();
                vb.content.list.post(this::saveListPosition);
            });
        }
    }

    /**
     * Display the list based on the given cursor, and scroll to the desired node.
     *
     * @param node to show
     */
    private void displayList(@NonNull final BooklistNode node) {
        final List<BooklistNode> list = new ArrayList<>();
        list.add(node);
        displayList(list);
    }

    /**
     * Display the list based on the given cursor, and either scroll to the desired
     * target node(s) or, if none, to the last saved position.
     *
     * @param targetNodes (optional) to re-position to
     */
    private void displayList(@NonNull final List<BooklistNode> targetNodes) {
        if (createListAdapter(true) > 0) {

            // we can get here after a style change (or as initial build obviously)
            // so make sure the menu reflects the style
            toolbarMenuProvider.onPrepareMenu(vb.toolbar.getMenu());

            // Notice the "post()" usage. This is needed because the list
            // will potentially have moved after each step.
            vb.content.list.post(() -> {
                // scroll to the saved position - this should get us close to where we need to be
                scrollToSavedPosition();

                if (targetNodes.isEmpty()) {
                    vb.content.list.post(() -> {
                        // We're on the precise position
                        saveListPosition();
                        // Now show the book details if we can.
                        final long bookId = vm.getCurrentCenteredBookId();
                        if (bookId != 0 && hasEmbeddedDetailsFrame()) {
                            // We know exactly where we want to be,
                            // do NOT reset the stored book id positioning
                            openEmbeddedBookDetails(bookId);
                        } else {
                            // We didn't have visible embedded book detail;
                            // Make sure to disable the current stored book id positioning
                            vm.setCurrentCenteredBookId(0);
                        }
                    });
                } else {
                    // We have target nodes;
                    // Make sure to disable the current stored book id positioning
                    vm.setCurrentCenteredBookId(0);

                    // find the closest node showing the book, and scroll to it
                    final BooklistNode node = findBestNode(targetNodes);
                    vb.content.list.post(() -> {
                        scrollTo(node);
                        // after layout, save the final position
                        vb.content.list.post(() -> {
                            saveListPosition();
                            // and lastly, show the book details if we can.
                            final long bookId = node.getBookId();
                            if (bookId != 0 && hasEmbeddedDetailsFrame()) {
                                openEmbeddedBookDetails(bookId);
                            }
                        });
                    });
                }
            });
        }
    }

    @Nullable
    private Fragment getEmbeddedDetailsFrame() {
        return vb.content.detailsFrame == null ? null : vb.content.detailsFrame.getFragment();
    }

    private boolean hasEmbeddedDetailsFrame() {
        return vb.content.detailsFrame != null;
    }

    /**
     * Find the node which is physically closest to the current visible position.
     *
     * @param targetNodes to select from
     *
     * @return 'best' node
     */
    @NonNull
    private BooklistNode findBestNode(@NonNull final List<BooklistNode> targetNodes) {
        if (targetNodes.size() == 1) {
            return targetNodes.get(0);
        }

        // Position of the row in the (vertical) center of the screen
        final int center = (layoutManager.findLastVisibleItemPosition()
                            + layoutManager.findFirstVisibleItemPosition()) / 2;

        BooklistNode best = targetNodes.get(0);
        // distance from currently visible center row
        int distance = Math.abs(best.getAdapterPosition() - center);

        // Loop all other rows, looking for a nearer one
        for (int i = 1; i < targetNodes.size(); i++) {
            final BooklistNode node = targetNodes.get(i);
            final int newDist = Math.abs(node.getAdapterPosition() - center);
            if (newDist < distance) {
                distance = newDist;
                best = node;
            }
        }
        return best;
    }

    /**
     * Preserve the {@link LinearLayoutManager#findFirstVisibleItemPosition()}
     * for the CURRENT bookshelf/style combination.
     * <ol>
     *     <li>The row number at the top of the screen.</li>
     *     <li>The pixel offset of that row from the top of the screen.</li>
     * </ol>
     */
    private void saveListPosition() {
        if (!isDestroyed()) {
            final int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();
            if (firstVisiblePos == RecyclerView.NO_POSITION) {
                return;
            }
            vm.saveListPosition(this, firstVisiblePos, getViewOffset());
        }
    }

    /**
     * Get the number of pixels offset for the first visible View, can be negative.
     *
     * @return pixels
     */
    private int getViewOffset() {
        // the list.getChildAt; not the layoutManager.getChildAt (not sure why...)
        final View topView = vb.content.list.getChildAt(0);
        if (topView == null) {
            return 0;

        } else {
            // currently our padding is 0, but this is future-proof
            final int paddingTop = vb.content.list.getPaddingTop();
            final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams)
                    topView.getLayoutParams();
            return topView.getTop() - lp.topMargin - paddingTop;
        }
    }

    /**
     * Scroll the list to the position saved in {@link #saveListPosition}.
     * Saves the potentially changed position after the scrolling is done.
     */
    private void scrollToSavedPosition() {
        Objects.requireNonNull(adapter, "adapter");

        final Bookshelf bookshelf = vm.getCurrentBookshelf();
        final int position = bookshelf.getFirstVisibleItemPosition();

        // sanity check
        if (position < 0) {
            layoutManager.scrollToPositionWithOffset(0, 0);

        } else if (position >= adapter.getItemCount()) {
            // the list is shorter than it used to be, just scroll to the end
            layoutManager.scrollToPosition(position);

        } else {
            layoutManager.scrollToPositionWithOffset(position,
                                                     bookshelf.getFirstVisibleItemViewOffset());
        }
    }

    /**
     * Scroll the given node into user view.
     *
     * @param node to scroll to
     */
    private void scrollTo(@NonNull final BooklistNode node) {
        Objects.requireNonNull(adapter, "adapter");

        final int firstVisiblePos = layoutManager.findFirstVisibleItemPosition();
        // sanity check, we should never get here
        if (firstVisiblePos == RecyclerView.NO_POSITION) {
            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "scrollTo: empty list");
            }
            return;
        }

        // The mLayoutManager has the header at position 0, and the booklist rows starting at 1
        final int position = 1 + node.getAdapterPosition();

        // Dev notes...
        //
        // The recycler list will in fact extent at the top/bottom beyond the screen edge
        // It does this due to the CoordinatorLayout with the AppBar behaviour config.
        //
        // We can't simply add some padding to the RV (i.e. ?attr/actionBarSize)
        // as that would initially show a good result, but as soon as we scroll
        // would show up as a blank bit at the bottom.
        // Other people have the same issue:
        // https://stackoverflow.com/questions/38073272
        //
        // findLastVisibleItemPosition will find the CORRECT position...
        // ... except that it will be outside/below the screen by some 2-4 lines
        // and hence in practice NOT visible. Same to a lesser extent for the top.
        //
        // The logic/math here for the top of the screen works well.
        //
        // Handling the bottom is harder. It works good enough, but not perfect.
        // It depends on the fact if you just scrolled the page up or down, and then
        // expanded or collapsed the last row. There are more combinations... to many.
        final int lastVisiblePos = layoutManager.findLastVisibleItemPosition();

        // it should be on screen between the first and last visible rows.
        final boolean onScreen = (firstVisiblePos < position) && (position <= lastVisiblePos);
        final boolean isBook = node.getBookId() > 0;
        final boolean isExpanded = node.isExpanded();

        if (BuildConfig.DEBUG /* always */) {
            Log.d(TAG + ":scrollTo", "position=" + position
                                     + ", onScreen= " + onScreen
                                     + ", isExpanded= " + isExpanded
                                     + ", isBook= " + isBook
                 );
        }
        //FIXME: more fine-tuning needed
        if (onScreen && (isBook || isExpanded)) {
            layoutManager.scrollToPosition(position + 1);
        } else {
            // mLayoutManager.scrollToPosition(position);
            // 2022-03-13: trying this alternative, just show it at the top,
            // with an small offset(TODO: change according to sw)
            layoutManager.scrollToPositionWithOffset(position, 150);
            //FIXME: the show-at-top is to aggressive e.g. collapse a sub-section and it jumps top
        }
    }

    private void openEmbeddedBookDetails(final long bookId) {
        final FragmentManager fm = getSupportFragmentManager();

        Fragment fragment = fm.findFragmentByTag(ShowBookDetailsFragment.TAG);
        if (fragment == null) {
            fragment = ShowBookDetailsFragment.create(
                    bookId, vm.getStyle(this).getUuid(), true);
            fm.beginTransaction()
              .replace(R.id.details_frame, fragment, ShowBookDetailsFragment.TAG)
              .commit();
        } else {
            ((ShowBookDetailsFragment) fragment).reloadBook(bookId);
        }
    }

    @SuppressLint("LogConditional")
    private void dbgDumpPositions(@NonNull final String method,
                                  final int pos) {
        Log.d(method, String.format(" |savedPosition= %4d"
                                    + " |firstVisiblePos= %4d"
                                    + " |lastVisiblePos= %4d"
                                    + " |pos= %4d",
                                    vm.getCurrentBookshelf().getFirstVisibleItemPosition(),
                                    layoutManager.findFirstVisibleItemPosition(),
                                    layoutManager.findLastVisibleItemPosition(),
                                    pos));
    }

    private static class HeaderViewHolder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final BooksonbookshelfHeaderBinding vb;

        HeaderViewHolder(@NonNull final BooksonbookshelfHeaderBinding vb) {
            super(vb.getRoot());
            this.vb = vb;
        }
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.bob, menu);
            MenuUtils.setupSearchActionView(BooksOnBookshelf.this, menu);

            onPrepareMenu(menu);
        }

        @Override
        public void onPrepareMenu(@NonNull final Menu menu) {
            final boolean showPreferredOption =
                    vm.getStyle(BooksOnBookshelf.this).getExpansionLevel() > 1;
            menu.findItem(R.id.MENU_LEVEL_PREFERRED_EXPANSION).setVisible(showPreferredOption);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            fabMenu.hideMenu();

            final int itemId = menuItem.getItemId();

            if (itemId == R.id.MENU_FILTERS) {
                bookshelfFiltersLauncher.launch(vm.getCurrentBookshelf());
                return true;

            } else if (itemId == R.id.MENU_STYLE_PICKER) {
                stylePickerLauncher.launch(vm.getStyle(BooksOnBookshelf.this), false);
                return true;

            } else if (itemId == R.id.MENU_LEVEL_PREFERRED_EXPANSION) {
                expandAllNodes(vm.getStyle(BooksOnBookshelf.this).getExpansionLevel(), false);
                return true;

            } else if (itemId == R.id.MENU_LEVEL_EXPAND) {
                expandAllNodes(1, true);
                return true;

            } else if (itemId == R.id.MENU_LEVEL_COLLAPSE) {
                expandAllNodes(1, false);
                return true;
            }

            return false;
        }

        /**
         * Expand/Collapse the entire list <strong>starting</strong> from the given level.
         * <p>
         * This is called from the options menu:
         * <ul>
         *     <li>Preferred level</li>
         *     <li>expand all</li>
         *     <li>collapse all</li>
         * </ul>
         *
         * @param topLevel the desired top-level which must be kept visible
         * @param expand   desired state
         */
        private void expandAllNodes(@IntRange(from = 1) final int topLevel,
                                    final boolean expand) {
            // It is possible that the list will be empty, if so, ignore
            if (adapter != null && adapter.getItemCount() > 0) {
                saveListPosition();
                vm.expandAllNodes(topLevel, expand);
                displayList();
            }
        }
    }

    private class HeaderAdapter
            extends RecyclerView.Adapter<HeaderViewHolder> {

        @NonNull
        private final LayoutInflater inflater;

        HeaderAdapter(@NonNull final Context context) {
            inflater = LayoutInflater.from(context);
            setHasStableIds(true);
        }

        @NonNull
        @Override
        public HeaderViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                   final int viewType) {
            final BooksonbookshelfHeaderBinding hVb = BooksonbookshelfHeaderBinding
                    .inflate(inflater, parent, false);
            return new HeaderViewHolder(hVb);
        }

        @Override
        public void onBindViewHolder(@NonNull final HeaderViewHolder holder,
                                     final int position) {
            final BooklistHeader headerContent =
                    vm.getHeaderContent(holder.itemView.getContext());

            String header;
            header = headerContent.getStyleName();
            holder.vb.styleName.setText(header);
            holder.vb.styleName.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getFilterText();
            holder.vb.filterText.setText(header);
            holder.vb.filterText.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getSearchText();
            holder.vb.searchText.setText(header);
            holder.vb.searchText.setVisibility(header != null ? View.VISIBLE : View.GONE);

            header = headerContent.getBookCount();
            holder.vb.bookCount.setText(header);
            holder.vb.bookCount.setVisibility(header != null ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return 1;
        }
    }
}
