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
package com.hardbacknutter.nevertoomanybooks.booklist;

import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import java.util.Objects;

/**
 * Allows to be notified of non-book changes made.
 * The {@link #REQUEST_KEY} is shared between all implementations.
 * The listener {@link #onChange(String, long)} must interpret the key to deduce what the id means.
 */
public abstract class RowChangedListener
        implements FragmentResultListener {

    private static final String TAG = "RowChangedListener";
    public static final String REQUEST_KEY = TAG + ":rk";
    private static final String KEY = "key";
    private static final String ITEM_ID = "item";

    /**
     * Notify changes where made.
     *
     * @param requestKey for use with the FragmentResultListener
     * @param dataKey    what changed
     * @param id         the item being modified,
     *                   or {@code 0} for for a books-table inline item or any global change
     */
    public static void setResult(@NonNull final Fragment fragment,
                                 @NonNull final String requestKey,
                                 @NonNull final String dataKey,
                                 final long id) {
        final Bundle result = new Bundle(2);
        result.putString(KEY, dataKey);
        result.putLong(ITEM_ID, id);
        fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
    }

    public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                          @NonNull final LifecycleOwner lifecycleOwner) {
        fragmentManager.setFragmentResultListener(REQUEST_KEY, lifecycleOwner, this);
    }

    @Override
    public void onFragmentResult(@NonNull final String requestKey,
                                 @NonNull final Bundle result) {
        onChange(Objects.requireNonNull(result.getString(KEY), KEY),
                 result.getLong(ITEM_ID));
    }

    /**
     * Called when changes were made.
     *
     * @param key what changed
     * @param id  the item being modified,
     *            or {@code 0} for for a books-table inline item or any global change
     */
    public abstract void onChange(@NonNull String key,
                                  @IntRange(from = 0) long id);
}
