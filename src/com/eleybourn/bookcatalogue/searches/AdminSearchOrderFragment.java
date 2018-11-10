package com.eleybourn.bookcatalogue.searches;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.adapters.SimpleListAdapterRowActionListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AdminSearchOrderFragment extends Fragment {

    public static final String TAG = "AdminSearchOrderFragment";

    private ListView mListView;
    private ArrayList<SearchSites.Site> mList;
    private SearchSiteListAdapter mAdapter;

    private boolean isCreated;

    @Override
    public View onCreateView(final @NonNull LayoutInflater inflater,
                             final @Nullable ViewGroup container,
                             final @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_search_order, container, false);
    }

    @Override
    @CallSuper
    public void onActivityCreated(final @Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle args = getArguments();
        Objects.requireNonNull(args);
        //noinspection unchecked
        mList = (ArrayList<SearchSites.Site>) args.getSerializable(SearchSites.BKEY_SEARCH_SITES);

        mAdapter = new SearchSiteListAdapter(requireContext(), R.layout.row_edit_searchsite, mList);
        //noinspection ConstantConditions
        mListView = getView().findViewById(android.R.id.list);
        mListView.setAdapter(mAdapter);
        isCreated = true;
    }

    @Nullable
    public ArrayList<SearchSites.Site> getList() {
        if (isCreated) {
            ArrayList<SearchSites.Site> newList = new ArrayList<>(mList);
            for (int i = 0; i < mListView.getChildCount(); i++) {
                // get the current position of each site, and store that back into the site object.
                View child = mListView.getChildAt(i);
                int pos = mAdapter.getViewRow(child);
                SearchSites.Site site = mAdapter.getItem(pos);
                //noinspection ConstantConditions
                site.order = i;
                newList.set(site.order, site);
            }
            mList = newList;
        }
        return mList;
    }

    private class SearchSiteListAdapter extends SimpleListAdapter<SearchSites.Site> implements SimpleListAdapterRowActionListener<SearchSites.Site> {

        SearchSiteListAdapter(final @NonNull Context context,
                              final int rowViewId,
                              final @NonNull List<SearchSites.Site> list) {
            super(context, rowViewId, list);
        }

        @Override
        public void onGetView(final @NonNull View convertView, final @NonNull SearchSites.Site item) {
            final TextView name = convertView.findViewById(R.id.row_name);
            name.setText(item.name);

            final CheckedTextView enabled = convertView.findViewById(R.id.row_enabled);
            enabled.setChecked(item.enabled);
            enabled.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    enabled.setChecked(!enabled.isChecked());
                    item.enabled = enabled.isChecked();
                }
            });
        }
    }
}
