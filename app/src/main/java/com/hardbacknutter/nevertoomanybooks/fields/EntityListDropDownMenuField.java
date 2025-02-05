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
package com.hardbacknutter.nevertoomanybooks.fields;

import android.content.Context;
import android.view.View;
import android.widget.AutoCompleteTextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import com.hardbacknutter.nevertoomanybooks.datamanager.DataManager;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Entity;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;

/**
 * A {@code null} value is always handled as the 0'th element in the list.
 */
public class EntityListDropDownMenuField<T extends Entity>
        extends BaseField<Long, AutoCompleteTextView> {

    @NonNull
    private final EntityArrayAdapter<T> adapter;
    @NonNull
    private final List<T> items;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public EntityListDropDownMenuField(@NonNull final FragmentId fragmentId,
                                       @IdRes final int fieldViewId,
                                       @NonNull final String fieldKey,
                                       @NonNull final Context context,
                                       @NonNull final List<T> items) {
        super(fragmentId, fieldViewId, fieldKey, fieldKey);
        this.items = items;
        adapter = new EntityArrayAdapter<>(context, items);

        SanityCheck.requirePositiveValue(adapter.getCount(), "adapter.getCount()");
    }

    @NonNull
    public EntityListDropDownMenuField<T> setTextInputLayoutId(@IdRes final int viewId) {
        addRelatedViews(viewId);
        return this;
    }

    @Override
    public void setParentView(@NonNull final View parent) {
        super.setParentView(parent);

        final AutoCompleteTextView view = requireView();
        view.setAdapter(adapter);
        view.setOnItemClickListener((p, v, position, id) -> {
            final Long previous = rawValue;
            rawValue = items.get(position).getId();
            notifyIfChanged(previous);
        });
    }

    @Override
    @NonNull
    public Long getValue() {
        return rawValue != null ? rawValue : items.get(0).getId();
    }

    @Override
    public void setValue(@Nullable final Long value) {
        super.setValue(value != null ? value : items.get(0).getId());

        final AutoCompleteTextView view = getView();
        if (view != null) {
            //noinspection ConstantConditions
            final T current = items.stream()
                                   .filter(item -> item.getId() == rawValue)
                                   .findFirst()
                                   .orElse(items.get(0));
            view.setText(current.getLabel(view.getContext()), false);
        }
    }

    @Override
    public void setInitialValue(@NonNull final DataManager source) {
        initialValue = source.getLong(fieldKey);
        setValue(initialValue);
    }

    @Override
    void internalPutValue(@NonNull final DataManager target) {
        target.putLong(fieldKey, getValue());
    }

    @Override
    boolean isEmpty(@Nullable final Long value) {
        return value == null || value == items.get(0).getId();
    }
}
