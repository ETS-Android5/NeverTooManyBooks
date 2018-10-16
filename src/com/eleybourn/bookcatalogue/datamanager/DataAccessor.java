/*
 * @copyright 2013 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.datamanager;

import android.os.Bundle;
import android.support.annotation.NonNull;

/**
 * Interface implemented for custom data access
 *
 * @author pjw
 */
public interface DataAccessor {
    /** Get the specified Datum from the passed DataManager and bundle */
    @NonNull
    Object get(@NonNull final DataManager data, @NonNull final Datum datum, @NonNull final Bundle rawData);

    /** Set the specified Datum in the passed DataManager and bundle */
    void set(@SuppressWarnings("unused") @NonNull final DataManager data,
             @NonNull final Datum datum,
             @NonNull final Bundle rawData,
             @NonNull final Object value);

    /** Check if the specified Datum is present in the passed DataManager and bundle */
    boolean isPresent(@SuppressWarnings("unused") @NonNull final DataManager data,
                      @SuppressWarnings("unused") @NonNull final Datum datum,
                      @NonNull final Bundle rawData);
}