/*
 * @Copyright 2019 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hardbacknutter.nevertoomanybooks.booklist.BooklistStyle;

/**
 * An item (entity) in a database table always has an id and some user-friendly label
 * aka 'displayName'.
 */
public interface Entity {

    /**
     * Get the database row id of the entity.
     *
     * @return id
     */
    long getId();

    /**
     * Get the label to use.
     * <p>
     * TODO: this should be the optional method and call getLabel(App.getFakeUserContext())
     * but currently we have on the {@link BooklistStyle#getLabel(Context)},
     * while the non-context variant is used multiple time.
     *
     * @return the label.
     */
    String getLabel();

    /**
     * Optional.
     * <p>
     * TODO: and this the mandatory method.
     *
     * @param context Current context
     *
     * @return the label to use.
     */
    default String getLabel(@NonNull final Context context) {
        return getLabel();
    }

}
