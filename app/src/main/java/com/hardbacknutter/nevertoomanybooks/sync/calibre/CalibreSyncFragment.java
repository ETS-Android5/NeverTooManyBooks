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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuProvider;

import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentSyncCalibreBinding;
import com.hardbacknutter.nevertoomanybooks.settings.CalibrePreferencesFragment;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderFragment;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.sync.SyncWriterFragment;

/**
 * Starting point for sending and importing books with Calibre.
 * <p>
 * The user can specify the usual import/export options for new and/or updated books.
 * We do not yet support updating single books, nor a list of book ids.
 * <p>
 * The sync-date is set on the <strong>Library</strong> and NOT on individual books.
 */
@Keep
public class CalibreSyncFragment
        extends BaseFragment {

    @SuppressWarnings("FieldCanBeLocal")
    private MenuProvider toolbarMenuProvider;

    /** View Binding. */
    private FragmentSyncCalibreBinding vb;

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentSyncCalibreBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        toolbarMenuProvider = new ToolbarMenuProvider();
        toolbar.addMenuProvider(toolbarMenuProvider, getViewLifecycleOwner());
        toolbar.setTitle(R.string.action_synchronize);

        vb.btnLibMap.setOnClickListener(v -> {
            final String url = CalibreContentServer.getHostUrl(v.getContext());
            if (url.isEmpty()) {
                openSettings();
            } else {
                replaceFragment(CalibreLibraryMappingFragment.create(),
                                CalibreLibraryMappingFragment.TAG);
            }
        });
        vb.btnImport.setOnClickListener(v -> {
            if (CalibreContentServer.getHostUrl(v.getContext()).isEmpty()) {
                openSettings();
            } else {
                replaceFragment(SyncReaderFragment.create(SyncServer.CalibreCS),
                                SyncReaderFragment.TAG);
            }
        });
        vb.btnExport.setOnClickListener(v -> {
            if (CalibreContentServer.getHostUrl(v.getContext()).isEmpty()) {
                openSettings();
            } else {
                replaceFragment(SyncWriterFragment.create(SyncServer.CalibreCS),
                                SyncWriterFragment.TAG);
            }
        });
    }

    private void openSettings() {
        replaceFragment(new CalibrePreferencesFragment(), CalibrePreferencesFragment.TAG);
    }

    private class ToolbarMenuProvider
            implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull final Menu menu,
                                 @NonNull final MenuInflater menuInflater) {
            menu.add(Menu.NONE, R.id.MENU_CALIBRE_SETTINGS, 0, R.string.lbl_settings)
                .setIcon(R.drawable.ic_baseline_settings_24);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull final MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.MENU_CALIBRE_SETTINGS) {
                openSettings();
                return true;
            }
            return false;
        }
    }
}
