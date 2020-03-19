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
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentEditBookFieldsBinding;
import com.hardbacknutter.nevertoomanybooks.datamanager.Fields;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.EditTextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldaccessors.TextAccessor;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.AuthorListFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.CsvFormatter;
import com.hardbacknutter.nevertoomanybooks.datamanager.fieldformatters.SeriesListFormatter;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.dialogs.entities.CheckListDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ViewFocusOrder;
import com.hardbacknutter.nevertoomanybooks.viewmodels.ScannerViewModel;

/**
 * This class is called by {@link EditBookFragment} and displays the main Books fields Tab.
 */
public class EditBookFieldsFragment
        extends EditBookBaseFragment
        implements CoverHandler.HostingFragment {

    /** Log tag. */
    private static final String TAG = "EditBookFieldsFragment";

    /** Handles cover replacement, rotation, etc. */
    private final CoverHandler[] mCoverHandler = new CoverHandler[2];

    /** manage the validation check next to the ISBN field. */
    private ISBN.ValidationTextWatcher mIsbnValidationTextWatcher;
    private ISBN.CleanupTextWatcher mIsbnCleanupTextWatcher;

    /**
     * Set to {@code true} limits to using ISBN-10/13.
     * Otherwise we also allow UPC/EAN codes.
     * False by default, as we don't want to mess with external codes unless the user really
     * wants to.
     * TODO: perhaps make this a preference?
     */
    private boolean mStrictIsbn = false;

    /** The scanner. */
    @Nullable
    private ScannerViewModel mScannerModel;
    /** View Binding. */
    private FragmentEditBookFieldsBinding mVb;

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        mVb = FragmentEditBookFieldsBinding.inflate(inflater, container, false);

        //noinspection ConstantConditions
        if (!DBDefinitions.isUsed(getContext(), DBDefinitions.KEY_THUMBNAIL)) {
            mVb.coverImage0.setVisibility(View.GONE);
            mVb.coverImage1.setVisibility(View.GONE);
        }

        if (EditBookActivity.showAuthSeriesOnTabs(getContext())) {
            mVb.lblAuthor.setVisibility(View.GONE);
            mVb.lblSeries.setVisibility(View.GONE);
        }

        return mVb.getRoot();
    }

    @CallSuper
    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        //noinspection ConstantConditions
        mScannerModel = new ViewModelProvider(getActivity()).get(ScannerViewModel.class);

        //noinspection ConstantConditions
        ViewFocusOrder.fix(getView());
    }

    @Override
    protected void onInitFields() {
        super.onInitFields();
        final Fields fields = mFragmentVM.getFields();

        //noinspection ConstantConditions
        final boolean showAuthSeriesOnTabs = EditBookActivity.showAuthSeriesOnTabs(getContext());

        // The buttons to bring up the fragment to edit Authors / Series.
        // Not shown if the user preferences are set to use an extra tab for this.
        if (!showAuthSeriesOnTabs) {
            fields.add(R.id.author, UniqueId.BKEY_AUTHOR_ARRAY,
                       new TextAccessor<>(new AuthorListFormatter(Author.Details.Short,
                                                                  true, false)),
                       DBDefinitions.KEY_FK_AUTHOR)
                  .setRelatedFields(R.id.lbl_author);

            fields.add(R.id.series_title, UniqueId.BKEY_SERIES_ARRAY,
                       new TextAccessor<>(new SeriesListFormatter(Series.Details.Short,
                                                                  true, false)),
                       DBDefinitions.KEY_SERIES_TITLE)
                  .setRelatedFields(R.id.lbl_series);
        }

        fields.add(R.id.title, DBDefinitions.KEY_TITLE, new EditTextAccessor<String>());

        fields.add(R.id.description, DBDefinitions.KEY_DESCRIPTION, new EditTextAccessor<String>())
              .setRelatedFields(R.id.lbl_description);

        // Not using a EditIsbn custom View, as we want to be able to enter invalid codes here.
        fields.add(R.id.isbn, DBDefinitions.KEY_ISBN, new EditTextAccessor<String>())
              .setRelatedFields(R.id.lbl_isbn);

        fields.add(R.id.genre, DBDefinitions.KEY_GENRE, new EditTextAccessor<String>())
              .setRelatedFields(R.id.lbl_genre);

        // Personal fields

        // The Bookshelves are a read-only text field. A click will bring up an editor.
        // Note how we combine an EditTextAccessor with a (non Edit) FieldFormatter
        fields.add(R.id.bookshelves, UniqueId.BKEY_BOOKSHELF_ARRAY,
                   new EditTextAccessor<>(new CsvFormatter()),
                   DBDefinitions.KEY_BOOKSHELF)
              .setRelatedFields(R.id.lbl_bookshelves);
    }

    @Override
    void onPopulateViews(@NonNull final Book book) {
        super.onPopulateViews(book);

        // handle special fields
        //noinspection ConstantConditions
        if (DBDefinitions.isUsed(getContext(), DBDefinitions.KEY_THUMBNAIL)) {
            // Hook up the indexed cover image.
            mCoverHandler[0] = new CoverHandler(this, mProgressBar,
                                                book, mVb.isbn, 0, mVb.coverImage0,
                                                ImageUtils.SCALE_MEDIUM);

            mCoverHandler[1] = new CoverHandler(this, mProgressBar,
                                                book, mVb.isbn, 1, mVb.coverImage1,
                                                ImageUtils.SCALE_MEDIUM);
        }

        // hide unwanted fields
        //noinspection ConstantConditions
        mFragmentVM.getFields().resetVisibility(getView(), false, false);
    }

    @Override
    public void onResume() {
        //noinspection ConstantConditions
        final boolean showAuthSeriesOnTabs = EditBookActivity.showAuthSeriesOnTabs(getContext());

        // If we're showing Author/Series on pop-up fragments, we need to prepare them here.
        if (!showAuthSeriesOnTabs) {
            mBookViewModel.prepareAuthorsAndSeries(getContext());
        }

        // the super will trigger the population of all defined Fields and their Views.
        super.onResume();

        // With all Views populated, (re-)add the helpers
        addAutocomplete(R.id.genre, mFragmentVM.getGenres());

        mIsbnValidationTextWatcher = new ISBN.ValidationTextWatcher(mVb.lblIsbn, mVb.isbn,
                                                                    mStrictIsbn);
        mVb.isbn.addTextChangedListener(mIsbnValidationTextWatcher);

        mIsbnCleanupTextWatcher = new ISBN.CleanupTextWatcher(mVb.isbn);
        if (mStrictIsbn) {
            mVb.isbn.addTextChangedListener(mIsbnCleanupTextWatcher);
        }

        mVb.btnScan.setOnClickListener(v -> {
            Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
            mScannerModel.scan(this, UniqueId.REQ_SCAN_BARCODE);
        });

        setOnClickListener(R.id.bookshelves, v -> CheckListDialogFragment
                .newInstance(R.string.lbl_bookshelves_long, R.id.bookshelves,
                             new ArrayList<>(mFragmentVM.getDb().getBookshelves()),
                             new ArrayList<>(mBookViewModel.getBook().getParcelableArrayList(
                                     UniqueId.BKEY_BOOKSHELF_ARRAY)))
                .show(getChildFragmentManager(), CheckListDialogFragment.TAG));


        if (!showAuthSeriesOnTabs) {
            setOnClickListener(R.id.author, v ->
                    showEditListFragment(new EditBookAuthorsFragment(),
                                         EditBookAuthorsFragment.TAG));

            setOnClickListener(R.id.series_title, v ->
                    showEditListFragment(new EditBookSeriesFragment(),
                                         EditBookSeriesFragment.TAG));
        }
    }

    /** Called by the CoverHandler when a context menu is selected. */
    @Override
    public void setCurrentCoverIndex(final int cIdx) {
        mFragmentVM.setCurrentCoverHandlerIndex(cIdx);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_STRICT_ISBN, 0, R.string.menu_strict_isbn)
            .setCheckable(true)
            .setChecked(mStrictIsbn)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_STRICT_ISBN: {
                final boolean checked = !item.isChecked();
                item.setChecked(checked);
                mIsbnValidationTextWatcher.setStrictIsbn(checked);
                if (checked) {
                    // don't add twice
                    mVb.isbn.removeTextChangedListener(mIsbnCleanupTextWatcher);
                    mVb.isbn.addTextChangedListener(mIsbnCleanupTextWatcher);
                } else {
                    mVb.isbn.removeTextChangedListener(mIsbnCleanupTextWatcher);
                }
                mStrictIsbn = checked;
                return true;
            }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }

        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case UniqueId.REQ_SCAN_BARCODE: {
                Objects.requireNonNull(mScannerModel, ErrorMsg.NULL_SCANNER_MODEL);
                mScannerModel.setScannerStarted(false);
                if (resultCode == Activity.RESULT_OK) {
                    if (BuildConfig.DEBUG) {
                        //noinspection ConstantConditions
                        mScannerModel.fakeScanInEmulator(getContext(), data);
                    }

                    //noinspection ConstantConditions
                    final String barCode =
                            mScannerModel.getScanner().getBarcode(getContext(), data);
                    if (barCode != null) {
                        mBookViewModel.getBook().putString(DBDefinitions.KEY_ISBN, barCode);
                        return;
                    }
                }
                return;
            }

            default: {
                int cIdx = mFragmentVM.getCurrentCoverHandlerIndex();
                // handle any cover image request codes
                if (cIdx >= -1) {
                    final boolean handled = mCoverHandler[cIdx]
                            .onActivityResult(requestCode, resultCode, data);
                    mFragmentVM.setCurrentCoverHandlerIndex(-1);
                    if (handled) {
                        break;
                    }
                }

                super.onActivityResult(requestCode, resultCode, data);
                break;
            }
        }
    }

    /**
     * Show the given fragment to edit the list of authors/series.
     *
     * @param frag the fragment to show
     * @param tag  the tag to use for the fragment
     */
    private void showEditListFragment(@NonNull final Fragment frag,
                                      @NonNull final String tag) {
        // The original intent was to simply add the new fragment on the same level
        // as the current one; using getParentFragment().getChildFragmentManager()
        // but we got: IllegalStateException: ViewPager2 does not support direct child views
        // So... we use the top-level fragment manager,
        // and have EditBookFragment#prepareSave explicitly check.

        //noinspection ConstantConditions
        getActivity().getSupportFragmentManager()
                     .beginTransaction()
                     .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                     .replace(R.id.main_fragment, frag, tag)
                     .addToBackStack(tag)
                     .commit();
    }
}
