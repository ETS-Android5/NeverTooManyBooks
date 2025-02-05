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
package com.hardbacknutter.nevertoomanybooks.bookedit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.fields.Field;
import com.hardbacknutter.nevertoomanybooks.fields.FieldGroup;
import com.hardbacknutter.nevertoomanybooks.fields.FragmentId;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;

public class EditBookExternalIdFragment
        extends EditBookBaseFragment {

    public static boolean isShowTab() {
        return ServiceLocator.getPreferences()
                             .getBoolean(Prefs.pk_edit_book_tabs_external_id, false);
    }

    @NonNull
    @Override
    public FragmentId getFragmentId() {
        return FragmentId.ExternalId;
    }

    @Override
    @Nullable
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_book_external_id, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        vm.initFields(getContext(), FragmentId.ExternalId, FieldGroup.ExternalId);
    }

    @Override
    void onPopulateViews(@NonNull final List<Field<?, ? extends View>> fields,
                         @NonNull final Book book) {
        super.onPopulateViews(fields, book);

        getFab().setVisibility(View.INVISIBLE);

        // Force hidden fields to stay hidden; this will allow us to temporarily remove
        // some sites without removing the data.
        //noinspection ConstantConditions
        fields.forEach(field -> field.setVisibility(getView(), false, true));
    }
}
