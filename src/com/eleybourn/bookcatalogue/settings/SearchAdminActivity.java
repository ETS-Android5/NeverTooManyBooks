package com.eleybourn.bookcatalogue.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.searches.SearchSites;
import com.eleybourn.bookcatalogue.searches.Site;
import com.google.android.material.tabs.TabLayout;

public class SearchAdminActivity
        extends BaseActivity {

    /**
     * Optional: set to one of the {@link SearchOrderFragment} tabs,
     * if we should *only* show that tab, and NOT save the new setting (i.e. the "use" scenario).
     */
    public static final String REQUEST_BKEY_TAB = "tab";
    /** Bundle key with the resulting site usage for the "use" scenario. */
    public static final String RESULT_SEARCH_SITES = "resultSearchSites";

    public static final int TAB_ORDER = 0;
    public static final int TAB_COVER_ORDER = 1;
    private static final int SHOW_ALL_TABS = -1;

    private ViewPagerAdapter mAdapter;

    private boolean mIsDirty;

    private boolean mUseScenario;

    public void setDirty(final boolean dirty) {
        mIsDirty = dirty;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin_search;
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        int requestedTab = args == null ? SHOW_ALL_TABS
                                        : args.getInt(REQUEST_BKEY_TAB, SHOW_ALL_TABS);

        ViewPager viewPager = findViewById(R.id.tab_fragment);
        TabLayout tabLayout = findViewById(R.id.tab_panel);
        mAdapter = new ViewPagerAdapter(getSupportFragmentManager());

        switch (requestedTab) {
            case TAB_ORDER:
                tabLayout.setVisibility(View.GONE);
                initSingleTab(SearchOrderFragment.TAG + TAB_ORDER,
                              R.string.lbl_books
                );
                break;

            case TAB_COVER_ORDER:
                tabLayout.setVisibility(View.GONE);
                initSingleTab(SearchOrderFragment.TAG + TAB_COVER_ORDER,
                              R.string.lbl_cover
                );
                break;

            default:
                setTitle(R.string.menu_search_internet);

                FragmentManager fm = getSupportFragmentManager();
                // add them in order! i.e. in the order the TAB_* constants are defined.
                mAdapter.add(new FragmentHolder(fm, SearchOrderFragment.TAG + TAB_ORDER,
                                                getString(R.string.lbl_books)));

                mAdapter.add(new FragmentHolder(fm, SearchOrderFragment.TAG + TAB_COVER_ORDER,
                                                getString(R.string.lbl_covers)));
                break;
        }

        // fire up the adapter.
        viewPager.setAdapter(mAdapter);
        tabLayout.setupWithViewPager(viewPager);
    }

    private void initSingleTab(@NonNull final String tag,
                               @StringRes final int titleId) {
        mUseScenario = true;

        setTitle(titleId);
        mAdapter.add(new FragmentHolder(getSupportFragmentManager(), tag, getString(titleId)));
    }

    @Override
    public void onBackPressed() {
        if (mIsDirty) {
            StandardDialogs.showConfirmUnsavedEditsDialog(this, () -> {
                // runs when user clicks 'exit'
                setResult(Activity.RESULT_CANCELED);
                finish();
            });
        } else {
            setResult(Activity.RESULT_CANCELED);
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {

        if (mUseScenario) {
            menu.add(Menu.NONE, R.id.MENU_USE, 0, R.string.btn_confirm_use)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        } else {
            menu.add(Menu.NONE, R.id.MENU_SAVE, 0, R.string.btn_confirm_save)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        menu.add(Menu.NONE, R.id.MENU_RESET, 0, R.string.btn_reset)
            .setIcon(R.drawable.ic_undo)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {

        ArrayList<Site> list;

        switch (item.getItemId()) {

            case R.id.MENU_USE:
                int sites = Site.SEARCH_ALL;
                list = ((SearchOrderFragment) mAdapter.getItem(0)).getList();
                //noinspection ConstantConditions
                for (Site site : list) {
                    sites = site.isEnabled() ? sites | site.id
                                             : sites & ~site.id;
                }
                Intent data = new Intent().putExtra(RESULT_SEARCH_SITES, sites);
                // don't commit any changes, we got data to use temporarily
                setResult(Activity.RESULT_OK, data);
                finish();
                return true;

            case R.id.MENU_SAVE:
                if (mIsDirty) {
                    //ENHANCE: compare this approach to what is used in EditBookFragment & children.
                    // Decide later...
                    list = ((SearchOrderFragment) mAdapter.getItem(TAB_ORDER)).getList();
                    if (list != null) {
                        SearchSites.setSearchOrder(list);
                    }

                    list = ((SearchOrderFragment) mAdapter.getItem(TAB_COVER_ORDER)).getList();
                    if (list != null) {
                        SearchSites.setCoverSearchOrder(list);
                    }
                }

                // no data to return
                setResult(Activity.RESULT_OK);
                finish();
                return true;

            case R.id.MENU_RESET:
                SearchSites.reset();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private static class ViewPagerAdapter
            extends FragmentPagerAdapter {

        private final List<FragmentHolder> mFragmentList = new ArrayList<>();

        /**
         * Constructor.
         *
         * @param fm FragmentManager
         */
        ViewPagerAdapter(@NonNull final FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @Override
        @NonNull
        public Fragment getItem(final int position) {
            return mFragmentList.get(position).fragment;
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void add(@NonNull final FragmentHolder fragmentHolder) {
            mFragmentList.add(fragmentHolder);
        }

        @Override
        @NonNull
        public CharSequence getPageTitle(final int position) {
            return mFragmentList.get(position).title;
        }
    }

    private static class FragmentHolder {

        @NonNull
        final String title;
        @NonNull
        Fragment fragment;

        /**
         * Constructor.
         *
         * @param fm FragmentManager
         */
        FragmentHolder(@NonNull final FragmentManager fm,
                       @NonNull final String tag,
                       @NonNull final String title) {
            this.title = title;
            //noinspection ConstantConditions
            fragment = fm.findFragmentByTag(tag);
            if (fragment == null) {

                ArrayList<Site> list;
                if (tag.equals(SearchOrderFragment.TAG + TAB_ORDER)) {
                    list = SearchSites.getSites();
                } else /* if (t.equals(SearchOrderFragment.TAG + TAB_COVER_ORDER)) */ {
                    list = SearchSites.getSitesForCoverSearches();
                }

                Bundle args = new Bundle();
                args.putParcelableArrayList(SearchSites.BKEY_SEARCH_SITES, list);
                fragment = new SearchOrderFragment();
                fragment.setArguments(args);
            }
        }
    }
}
