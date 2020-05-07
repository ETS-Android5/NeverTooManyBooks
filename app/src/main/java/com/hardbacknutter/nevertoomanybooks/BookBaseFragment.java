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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.ErrorMsg;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Fields;
import com.hardbacknutter.nevertoomanybooks.searches.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.utils.PermissionsHelper;
import com.hardbacknutter.nevertoomanybooks.viewmodels.BookViewModel;

/**
 * Base class for {@link BookDetailsFragment} and {@link EditBookBaseFragment}.
 * <p>
 * This class supports the loading of a book. See {@link #populateViews}.
 */
public abstract class BookBaseFragment
        extends Fragment
        implements PermissionsHelper.RequestHandler {

    /** Log tag. */
    private static final String TAG = "BookBaseFragment";

    /** simple indeterminate progress spinner to show while doing lengthy work. */
    ProgressBar mProgressBar;

    /** The book. Must be in the Activity scope. */
    BookViewModel mBookViewModel;

    /** Listener for all field changes. Must keep strong reference. */
    private final Fields.AfterChangeListener mAfterChangeListener =
            new Fields.AfterChangeListener() {
                @Override
                public void afterFieldChange(@IdRes final int fieldId) {
                    mBookViewModel.setDirty(true);
                }
            };

    abstract Fields getFields();

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        // Camera permissions
        onRequestPermissionsResultCallback(requestCode, permissions, grantResults);
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        mBookViewModel = new ViewModelProvider(getActivity()).get(BookViewModel.class);
        //noinspection ConstantConditions
        mBookViewModel.init(getContext(), getArguments());
    }

    @Override
    @CallSuper
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        mProgressBar = getActivity().findViewById(R.id.progressBar);
    }

    /**
     * Hook up the Views, and populate them with the book data.
     *
     * <br><br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onResume() {
        super.onResume();

        // This is done here using isVisible() due to ViewPager2
        // It ensures fragments in a ViewPager2 will refresh their individual option menus.
        // Children NOT running in a ViewPager2 must override this in their onResume()
        // with a simple setHasOptionsMenu(true);
        // https://issuetracker.google.com/issues/144442240
        // TEST: supposedly fixed in viewpager2:1.1.0-alpha01
        setHasOptionsMenu(isVisible());

        // load the book data into the views
        populateViews();
    }

    /**
     * Load all Views from the book.
     * <p>
     * Loads the data while preserving the isDirty() status.
     * Normally called from the base {@link #onResume},
     * but can explicitly be called after {@link Book#reload}.
     * <p>
     * This is final; Inheritors should implement {@link #onPopulateViews}.
     */
    final void populateViews() {
        //noinspection ConstantConditions
        getFields().setParentView(getView());
        getFields().setAfterChangeListener(null);
        // preserve the 'dirty' status.
        final boolean wasDirty = mBookViewModel.isDirty();
        // make it so!
        onPopulateViews(mBookViewModel.getBook());
        // restore the dirt-status and install the AfterChangeListener
        mBookViewModel.setDirty(wasDirty);
        getFields().setAfterChangeListener(mAfterChangeListener);

        // Set the activity title depending on View or Edit mode.
        // This is a good place to do this, as we use data from the book for the title.
        //noinspection ConstantConditions
        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            Book book = mBookViewModel.getBook();
            if (book.isNew()) {
                // EDIT NEW book
                actionBar.setTitle(R.string.lbl_add_book);
                actionBar.setSubtitle(null);
            } else {
                // VIEW or EDIT existing book
                actionBar.setTitle(book.getString(DBDefinitions.KEY_TITLE));
                //noinspection ConstantConditions
                actionBar.setSubtitle(book.getAuthorTextShort(getContext()));
            }
        }
    }

    /**
     * This is where you should populate all the fields with the values coming from the book.
     * The base class (this one) manages all the actual fields, but 'special' fields can/should
     * be handled in overrides, calling super as the first step.
     */
    @CallSuper
    void onPopulateViews(@NonNull final Book book) {
        getFields().setAll(book);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        if (menu.findItem(R.id.MENU_SEARCH) == null) {
            inflater.inflate(R.menu.sm_search, menu);
            //noinspection ConstantConditions
            MenuHandler.setupSearch(getActivity(), menu);
        }

        if (menu.findItem(R.id.SUBMENU_VIEW_BOOK_AT_SITE) == null) {
            inflater.inflate(R.menu.sm_view_on_site, menu);
        }
        if (menu.findItem(R.id.SUBMENU_AMAZON_SEARCH) == null) {
            inflater.inflate(R.menu.sm_search_on_amazon, menu);
        }
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        final Context context = getContext();

        final Book book = mBookViewModel.getBook();

        switch (item.getItemId()) {
            case R.id.MENU_UPDATE_FROM_INTERNET: {
                ArrayList<Long> bookIdList = new ArrayList<>();
                bookIdList.add(book.getId());
                Intent intent = new Intent(context, BookSearchActivity.class)
                        .putExtra(BaseActivity.BKEY_FRAGMENT_TAG, UpdateFieldsFragment.TAG)
                        .putExtra(Book.BKEY_BOOK_ID_ARRAY, bookIdList)
                        // pass the title for displaying to the user
                        .putExtra(DBDefinitions.KEY_TITLE, book.getString(DBDefinitions.KEY_TITLE))
                        // pass the author for displaying to the user
                        .putExtra(DBDefinitions.KEY_AUTHOR_FORMATTED,
                                  book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                startActivityForResult(intent, RequestCode.UPDATE_FIELDS_FROM_INTERNET);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR: {
                //noinspection ConstantConditions
                AmazonSearchEngine.openWebsite(context, book.getPrimaryAuthor(context), null);
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_IN_SERIES: {
                //noinspection ConstantConditions
                AmazonSearchEngine.openWebsite(context, null, book.getPrimarySeriesTitle());
                return true;
            }
            case R.id.MENU_AMAZON_BOOKS_BY_AUTHOR_IN_SERIES: {
                //noinspection ConstantConditions
                AmazonSearchEngine.openWebsite(context,
                                               book.getPrimaryAuthor(context),
                                               book.getPrimarySeriesTitle());
                return true;
            }

            default: {
                //noinspection ConstantConditions
                if (MenuHandler.handleOpenOnWebsiteMenus(context, item.getItemId(), book)) {
                    return true;
                }
                return super.onOptionsItemSelected(item);
            }
        }
    }

    /**
     * Allows the ViewModel to send us a message to display to the user.
     *
     * @param message to display
     */
    void showUserMessage(@Nullable final String message) {
        final View view = getView();
        if (view != null && message != null && !message.isEmpty()) {
            Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.enterOnActivityResult(TAG, requestCode, resultCode, data);
        }
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case RequestCode.UPDATE_FIELDS_FROM_INTERNET:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data, ErrorMsg.NULL_INTENT_DATA);

                    final long newId = data.getLongExtra(DBDefinitions.KEY_PK_ID, 0);
                    if (newId != 0) {
                        // replace current book with the updated one,
                        // ENHANCE: merge if in edit mode.
                        mBookViewModel.loadBook(newId);
                    }
                }
                break;

            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Log.d(TAG, "onActivityResult|NOT HANDLED"
                               + "|requestCode=" + requestCode
                               + "|resultCode=" + resultCode, new Throwable());
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    public interface FieldsViewModel {

        @NonNull
        Fields getFields();
    }
}
