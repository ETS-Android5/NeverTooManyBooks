/*
 * @Copyright 2020 HardBackNutter
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

import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.ArrayList;

import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditBookByIdContract;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.ScannerContract;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentBooksearchByIsbnBinding;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ActivityResultViewModel;
import com.hardbacknutter.nevertoomanybooks.viewmodels.SearchBookByIsbnViewModel;

/**
 * The input field is not being limited in length. This is to allow entering UPC_A numbers.
 */
public class SearchBookByIsbnFragment
        extends SearchBookBaseFragment {

    /** Log tag. */
    public static final String TAG = "BookSearchByIsbnFrag";
    private static final String BKEY_STARTED = TAG + ":started";
    /** See remarks in {@link com.hardbacknutter.nevertoomanybooks.backup.ImportFragment}. */
    private static final String MIME_TYPES = "*/*";
    /** flag indicating the scanner is already started. */
    private boolean mScannerStarted;
    /** View Binding. */
    private FragmentBooksearchByIsbnBinding mVb;
    /** manage the validation check next to the field. */
    private ISBN.ValidationTextWatcher mIsbnValidationTextWatcher;
    private ISBN.CleanupTextWatcher mIsbnCleanupTextWatcher;
    private SearchBookByIsbnViewModel mVm;
    /** After a successful scan/search, the data is offered for editing. */
    private final ActivityResultLauncher<Long> mEditExistingBookLauncher =
            registerForActivityResult(new EditBookByIdContract(), this::onBookEditingDone);
    /** The scanner. */
    private final ActivityResultLauncher<Fragment> mScannerLauncher =
            registerForActivityResult(new ScannerContract(), this::onBarcodeScanned);
    /** Importing a list of ISBN. */
    private final ActivityResultLauncher<String> mOpenUriLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onOpenUri);

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        if (savedInstanceState != null) {
            mScannerStarted = savedInstanceState.getBoolean(BKEY_STARTED, false);
        }
    }

    @NonNull
    @Override
    public ActivityResultViewModel getActivityResultViewModel() {
        return mVm;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentBooksearchByIsbnBinding.inflate(inflater, container, false);
        return mVb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mVm = new ViewModelProvider(this).get(SearchBookByIsbnViewModel.class);
        mVm.init(getArguments());

        //noinspection ConstantConditions
        getActivity().setTitle(R.string.lbl_search_isbn);

        mVb.isbn.setText(mCoordinator.getIsbnSearchText());

        mVb.key0.setOnClickListener(v -> mVb.isbn.onKey('0'));
        mVb.key1.setOnClickListener(v -> mVb.isbn.onKey('1'));
        mVb.key2.setOnClickListener(v -> mVb.isbn.onKey('2'));
        mVb.key3.setOnClickListener(v -> mVb.isbn.onKey('3'));
        mVb.key4.setOnClickListener(v -> mVb.isbn.onKey('4'));
        mVb.key5.setOnClickListener(v -> mVb.isbn.onKey('5'));
        mVb.key6.setOnClickListener(v -> mVb.isbn.onKey('6'));
        mVb.key7.setOnClickListener(v -> mVb.isbn.onKey('7'));
        mVb.key8.setOnClickListener(v -> mVb.isbn.onKey('8'));
        mVb.key9.setOnClickListener(v -> mVb.isbn.onKey('9'));
        mVb.keyX.setOnClickListener(v -> mVb.isbn.onKey('X'));

        mVb.isbnDel.setOnClickListener(v -> mVb.isbn.onKey(KeyEvent.KEYCODE_DEL));
        mVb.isbnDel.setOnLongClickListener(v -> {
            mVb.isbn.setText("");
            return true;
        });

        // The search preference determines the level here; NOT the 'edit book'
        final int isbnValidityCheck = mCoordinator.isStrictIsbn() ? ISBN.VALIDITY_STRICT
                                                                  : ISBN.VALIDITY_NONE;

        mIsbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(mVb.isbn, isbnValidityCheck);
        mVb.isbn.addTextChangedListener(mIsbnCleanupTextWatcher);

        mIsbnValidationTextWatcher =
                new ISBN.ValidationTextWatcher(mVb.lblIsbn, mVb.isbn, isbnValidityCheck);
        mVb.isbn.addTextChangedListener(mIsbnValidationTextWatcher);

        mVb.btnSearch.setOnClickListener(this::onBarcodeEntered);

        mVb.btnClearQueue.setOnClickListener(v -> {
            mVm.getScanQueue().clear();
            mVb.queue.removeAllViews();
            mVb.queueGroup.setVisibility(View.GONE);
        });

        //noinspection VariableNotUsedInsideIf
        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            Site.promptToRegister(getContext(), mCoordinator.getSiteList(),
                                  "searchByIsbn", this::afterOnViewCreated);
        } else {
            afterOnViewCreated();
        }
    }

    private void afterOnViewCreated() {
        if (mVm.isAutoStart()) {
            scan();
        } else {
            populateQueueView();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        final Resources r = getResources();
        menu.add(Menu.NONE, R.id.MENU_SCAN_BARCODE,
                 r.getInteger(R.integer.MENU_ORDER_SCAN_BARCODE),
                 R.string.menu_scan_barcode)
            .setIcon(R.drawable.ic_barcode)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(Menu.NONE, R.id.MENU_SCAN_BARCODE_BATCH,
                 r.getInteger(R.integer.MENU_ORDER_SCAN_BARCODE_BATCH),
                 R.string.menu_scan_barcode_batch_start)
            .setIcon(R.drawable.ic_barcode_batch)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        menu.add(Menu.NONE, R.id.MENU_SCAN_BARCODE_IMPORT,
                 r.getInteger(R.integer.MENU_ORDER_SCAN_BARCODE_IMPORT),
                 R.string.menu_import)
            .setIcon(R.drawable.ic_file_download)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        menu.add(Menu.NONE, R.id.MENU_ISBN_VALIDITY_STRICT,
                 r.getInteger(R.integer.MENU_ORDER_SEARCH_STRICT_ISBN),
                 R.string.lbl_strict_isbn)
            .setCheckable(true)
            .setChecked(mCoordinator.isStrictIsbn())
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final int itemId = item.getItemId();

        if (itemId == R.id.MENU_SCAN_BARCODE) {
            mVm.setScannerMode(SearchBookByIsbnViewModel.SCANNER_MODE_SINGLE);
            scan();
            return true;

        } else if (itemId == R.id.MENU_SCAN_BARCODE_BATCH) {
            mVm.setScannerMode(SearchBookByIsbnViewModel.SCANNER_MODE_BATCH);
            scan();
            return true;

        } else if (itemId == R.id.MENU_SCAN_BARCODE_IMPORT) {
            mOpenUriLauncher.launch(MIME_TYPES);
            return true;

        } else if (itemId == R.id.MENU_ISBN_VALIDITY_STRICT) {
            final boolean checked = !item.isChecked();
            item.setChecked(checked);
            mCoordinator.setStrictIsbn(checked);

            final int validity = checked ? ISBN.VALIDITY_STRICT : ISBN.VALIDITY_NONE;
            mIsbnCleanupTextWatcher.setValidityLevel(validity);
            mIsbnValidationTextWatcher.setValidityLevel(validity);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        //noinspection ConstantConditions
        mCoordinator.setIsbnSearchText(mVb.isbn.getText().toString().trim());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BKEY_STARTED, mScannerStarted);
    }

    /**
     * Start scanner activity.
     */
    public void scan() {
        if (!mScannerStarted) {
            mScannerStarted = true;
            mScannerLauncher.launch(this);
        }
    }

    private void onBarcodeScanned(@Nullable String barCode) {
        mScannerStarted = false;

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.FAKE_BARCODE_SCANNER) {
            // Goodreads best books of 2020
            final String[] testCodes = {
                    // random == 0 -> cancel scanning
                    null,
                    "9780316310420",
                    "9781250762849",
                    "9781524763169",
                    "9780062868930",
                    "9781786892713",
                    "9780349701462",
                    "9781635574043",
                    "9781538719985",
                    "9780593230251",
                    "9780063032491",
                    };
            final int random = (int) Math.floor(Math.random() * 10);
            barCode = testCodes[random];
            Log.d(TAG, "onBarcodeScanned|Faking barcode=" + barCode);
        }

        if (barCode != null) {
            final boolean strictIsbn = mCoordinator.isStrictIsbn();
            final ISBN code = new ISBN(barCode, strictIsbn);

            if (code.isValid(strictIsbn)) {
                if (strictIsbn) {
                    //noinspection ConstantConditions
                    ScannerContract.onValidBarcodeBeep(getContext());
                }

                if (mVm.getScannerMode() == SearchBookByIsbnViewModel.SCANNER_MODE_BATCH) {
                    // batch mode, queue the code, and scan next book
                    mVm.addToQueue(code);
                    scan();

                } else {
                    // single-scan mode, keep the scanner on and go edit the book
                    mVb.isbn.setText(code.asText());
                    prepareSearch(code);
                }
            } else {
                //noinspection ConstantConditions
                ScannerContract.onInvalidBarcodeBeep(getContext());
                showError(mVb.lblIsbn, getString(R.string.warning_x_is_not_a_valid_code,
                                                 code.asText()));

                if (mVm.getScannerMode() == SearchBookByIsbnViewModel.SCANNER_MODE_BATCH) {
                    // batch mode, scan next book
                    scan();
                } else {
                    // single-scan mode, quit scanning, let the user edit the code
                    mVm.setScannerMode(SearchBookByIsbnViewModel.SCANNER_OFF);
                    mVb.isbn.setText(code.asText());
                }
            }

        } else if (mVm.getScannerMode() == SearchBookByIsbnViewModel.SCANNER_MODE_BATCH) {
            // no barcode received, batch mode, quit scanning and present the queue to the user
            mVm.setScannerMode(SearchBookByIsbnViewModel.SCANNER_OFF);
            populateQueueView();

        } else {
            // no barcode received, single-scan mode, quit scanning
            mVm.setScannerMode(SearchBookByIsbnViewModel.SCANNER_OFF);
        }
    }

    /**
     * The user entered a barcode and clicked the search button.
     */
    private void onBarcodeEntered(@NonNull final View btn) {
        //noinspection ConstantConditions
        final String userEntry = mVb.isbn.getText().toString().trim();
        final boolean strictIsbn = mCoordinator.isStrictIsbn();
        final ISBN code = new ISBN(userEntry, strictIsbn);

        if (code.isValid(strictIsbn)) {
            prepareSearch(code);
        } else {
            showError(mVb.lblIsbn, getString(R.string.warning_x_is_not_a_valid_code, userEntry));
        }
    }

    /**
     * Search with ISBN or, if allowed, with a generic code.
     *
     * @param code to search for
     */
    private void prepareSearch(@NonNull final ISBN code) {
        mCoordinator.setIsbnSearchText(code.asText());

        // See if ISBN already exists in our database, if not then start the search.
        final ArrayList<Pair<Long, String>> existingIds = mVm.getBookIdAndTitlesByIsbn(code);
        if (existingIds.isEmpty()) {
            startSearch();

        } else {
            // always quit scanning as the safe option, the user can restart the scanner,
            // or restart the queue processing at will.
            mVm.setScannerMode(SearchBookByIsbnViewModel.SCANNER_OFF);

            // we always use the first one... really should offer the user a choice.
            final long firstFound = existingIds.get(0).first;
            // Show the "title (isbn)" with a caution message
            final String msg = getString(R.string.a_bracket_b_bracket,
                                         existingIds.get(0).second, code.asText())
                               + "\n\n" + getString(R.string.confirm_duplicate_book_message);

            //noinspection ConstantConditions
            new MaterialAlertDialogBuilder(getContext())
                    .setIcon(R.drawable.ic_warning)
                    .setTitle(R.string.lbl_duplicate_book)
                    .setMessage(msg)
                    // this dialog is important. Make sure the user pays some attention
                    .setCancelable(false)
                    // User aborts this isbn
                    .setNegativeButton(android.R.string.cancel, (d, w) -> onClearSearchCriteria())
                    // User wants to review the existing book
                    .setNeutralButton(R.string.action_edit, (d, w)
                            -> mEditExistingBookLauncher.launch(firstFound))
                    // User wants to add regardless
                    .setPositiveButton(R.string.action_add, (d, w) -> startSearch())
                    .create()
                    .show();
        }
    }

    @Override
    void onSearchResults(@NonNull final Bundle bookData) {
        // A non-empty result will have a title, or at least 3 fields:
        // The isbn field should be present as we searched on one.
        // The title field, *might* be there but *might* be empty.
        // So a valid result means we either need a title, or a third field.
        final String title = bookData.getString(DBDefinitions.KEY_TITLE);
        if ((title == null || title.isEmpty()) && bookData.size() <= 2) {
            showError(mVb.lblIsbn, R.string.warning_no_matching_book_found);
            return;
        }
        // edit book
        super.onSearchResults(bookData);
    }

    @Override
    void onSearchCancelled() {
        super.onSearchCancelled();
        // Quit scan mode until the user manually starts it again
        mVm.setScannerMode(SearchBookByIsbnViewModel.SCANNER_OFF);
    }

    @Override
    void onClearSearchCriteria() {
        super.onClearSearchCriteria();
        //mVb.isbn.setText("");
    }

    @Override
    void onBookEditingDone(@Nullable final Bundle data) {
        super.onBookEditingDone(data);
        if (mVm.getScannerMode() == SearchBookByIsbnViewModel.SCANNER_MODE_SINGLE) {
            // scan another book until the user cancels
            scan();
        }
    }

    private void onOpenUri(@Nullable final Uri uri) {
        if (uri != null) {
            try {
                //noinspection ConstantConditions
                mVm.readQueue(getContext(), uri, mCoordinator.isStrictIsbn());
            } catch (@NonNull final IOException ignore) {
                Snackbar.make(mVb.getRoot(), R.string.error_import_failed, Snackbar.LENGTH_LONG)
                        .show();
            }

            populateQueueView();
        }
    }

    private void populateQueueView() {
        if (mVb.queue.getChildCount() > 0) {
            mVb.queue.removeAllViews();
        }

        //noinspection SimplifyStreamApiCallChains
        mVm.getScanQueue().stream().forEachOrdered(code -> {
            //noinspection ConstantConditions
            final Chip chip = new Chip(getContext(), null, R.attr.appChipQueueStyle);
            // RTL-friendly Chip Layout
            chip.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
            chip.setOnClickListener(v -> {
                final ISBN clickedCode = removeFromQueue(v);
                mVb.isbn.setText(clickedCode.asText());
                prepareSearch(clickedCode);
            });
            chip.setOnCloseIconClickListener(this::removeFromQueue);
            chip.setTag(code);
            chip.setText(code.asText());
            mVb.queue.addView(chip);
        });

        mVb.queueGroup.setVisibility(mVb.queue.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private ISBN removeFromQueue(@NonNull final View chip) {
        final ISBN code = (ISBN) chip.getTag();
        mVb.queue.removeView(chip);
        mVb.queueGroup.setVisibility(mVb.queue.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        mVm.getScanQueue().remove(code);
        return code;
    }
}
