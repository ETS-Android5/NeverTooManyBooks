package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.searches.SearchAdminActivity;
import com.eleybourn.bookcatalogue.searches.SearchCoordinator;
import com.eleybourn.bookcatalogue.searches.Site;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.utils.NetworkUtils;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * Optionally limit the sites to search on by setting {@link UniqueId#BKEY_SEARCH_SITES}.
 * By default uses {@link Site#SEARCH_ALL}.
 */
public abstract class BookSearchBaseFragment
        extends Fragment
        implements SearchCoordinator.SearchCoordinatorListener {

    /** t. */
    private static final String TAG = BookSearchBaseFragment.class.getSimpleName();

    /** stores an active search id, or 0 when none active. */
    private static final String BKEY_SEARCH_MANAGER_ID = TAG + ":SearchManagerId";
    /** the last book data (intent) we got from a successful EditBook. */
    private static final String BKEY_LAST_BOOK_INTENT = TAG + ":LastBookIntent";
    /** activity request code. */
    private static final int REQ_PREFERRED_SEARCH_SITES = 10;

    /** hosting activity. */
    protected BookSearchActivity mActivity;

    /** Database instance. */
    protected DBA mDb;
    /** Objects managing current search. */
    long mSearchManagerId;
    /** The last Intent returned as a result of creating a book. */
    @Nullable
    Intent mLastBookData;
    /** sites to search on. Can be overridden by the user (option menu). */
    private int mSearchSites = Site.SEARCH_ALL;

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // make sure {@link #onCreateOptionsMenu} is called
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        mActivity = (BookSearchActivity) requireActivity();
        super.onActivityCreated(savedInstanceState);

        mDb = new DBA(mActivity);

        Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
        mSearchManagerId = args.getLong(BKEY_SEARCH_MANAGER_ID);
        // optional, use ALL if not there
        mSearchSites = args.getInt(UniqueId.BKEY_SEARCH_SITES, Site.SEARCH_ALL);

        if ((mSearchSites & Site.SEARCH_LIBRARY_THING) != 0) {
            LibraryThingManager.showLtAlertIfNecessary(mActivity, false, "search");
        }
        if (!NetworkUtils.isNetworkAvailable(mActivity)) {
            //noinspection ConstantConditions
            UserMessage.showUserMessage(getView(), R.string.error_no_internet_connection);
        }
    }

    @Override
    @CallSuper
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES, 0, R.string.lbl_search_sites)
            .setIcon(R.drawable.ic_search)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(requireContext(), SearchAdminActivity.class)
                        .putExtra(SearchAdminActivity.REQUEST_BKEY_TAB,
                                  SearchAdminActivity.TAB_ORDER);
                startActivityForResult(intent, REQ_PREFERRED_SEARCH_SITES);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * (re)connect with the {@link SearchCoordinator} by starting to listen to its messages.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onResume() {
        super.onResume();
        if (mSearchManagerId != 0) {
            SearchCoordinator.getMessageSwitch()
                             .addListener(mSearchManagerId, this, true);
        }
    }

    /**
     * Start the actual search with the {@link SearchCoordinator} in the background.
     * <p>
     * The results will arrive in
     * {@link SearchCoordinator.SearchCoordinatorListener#onSearchFinished(boolean, Bundle)}
     *
     * @return <tt>true</tt> if search was started.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean startSearch(@NonNull final String authorSearchText,
                        @NonNull final String titleSearchText,
                        @NonNull final String isbnSearchText) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_INTERNET) {
            Logger.debugEnter(this, "startSearch",
                              "isbn=" + isbnSearchText,
                              "author=" + authorSearchText,
                              "title=" + titleSearchText);
        }

        try {
            // Start the lookup in a background search task.
            final SearchCoordinator searchCoordinator =
                    new SearchCoordinator(mActivity.getTaskManager(), this);
            mSearchManagerId = searchCoordinator.getId();

            mActivity.getTaskManager().sendHeaderUpdate(R.string.progress_msg_searching);
            // kick of the searches
            searchCoordinator.search(mSearchSites, authorSearchText, titleSearchText,
                                     isbnSearchText, true);
            return true;

        } catch (RuntimeException e) {
            Logger.error(this, e);
            //noinspection ConstantConditions
            UserMessage.showUserMessage(getView(), R.string.error_search_failed);
            mActivity.setResult(Activity.RESULT_CANCELED);
            mActivity.finish();
        }
        return false;
    }

    /**
     * Cut us loose from the {@link SearchCoordinator} by stopping listening to its messages.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        if (mSearchManagerId != 0) {
            SearchCoordinator.getMessageSwitch().removeListener(mSearchManagerId, this);
        }
        super.onPause();
    }

    @Override
    @CallSuper
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case REQ_PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK) {
                    //noinspection ConstantConditions
                    mSearchSites = data.getIntExtra(SearchAdminActivity.RESULT_SEARCH_SITES,
                                                    mSearchSites);
                }
                break;

            case UniqueId.REQ_BOOK_EDIT:
                if (resultCode == Activity.RESULT_OK) {
                    // Created a book; save the intent
                    mLastBookData = data;
                    // and set that as the default result
                    mActivity.setResult(resultCode, mLastBookData);

                } else if (resultCode == Activity.RESULT_CANCELED) {
                    // if the edit was cancelled, set that as the default result code
                    mActivity.setResult(Activity.RESULT_CANCELED);
                }
                break;

            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
                    Logger.warnWithStackTrace(this, "BookSearchBaseFragment.onActivityResult",
                                "NOT HANDLED:",
                                "requestCode=" + requestCode,
                                "resultCode=" + resultCode);
                }
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
        Tracker.exitOnActivityResult(this);
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BKEY_SEARCH_MANAGER_ID, mSearchManagerId);
        outState.putParcelable(BKEY_LAST_BOOK_INTENT, mLastBookData);
    }
}
