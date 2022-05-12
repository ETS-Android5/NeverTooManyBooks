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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.settings.BasePreferenceFragment;

/**
 * Settings editor for a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is {@code ""}, then we're editing the global defaults.
 */
public abstract class StyleBaseFragment
        extends BasePreferenceFragment {

    StyleViewModel mVm;

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        //noinspection ConstantConditions
        mVm = new ViewModelProvider(getActivity()).get(StyleViewModel.class);
        //noinspection ConstantConditions
        mVm.init(getContext(), requireArguments());

        final ListStyle style = mVm.getStyle();
        if (!style.isGlobal()) {
            // non-global, set the correct UUID SharedPreferences to use
            // This MUST be done in onCreate/onCreatePreferences
            //getPreferenceManager().setSharedPreferencesName(style.getUuid());

            getPreferenceManager().setPreferenceDataStore(mVm.getStyleDataStore());
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Toolbar toolbar = getToolbar();
        final ListStyle style = mVm.getStyle();
        if (style.getId() == 0) {
            toolbar.setTitle(R.string.lbl_clone_style);
        } else {
            toolbar.setTitle(R.string.lbl_edit_style);
        }

        //noinspection ConstantConditions
        toolbar.setSubtitle(style.getLabel(getContext()));
    }
}
