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
package com.eleybourn.bookcatalogue.datamanager.datavalidators;

import androidx.annotation.NonNull;

import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Datum;

/**
 * 'Meta' Validator to evaluate a list of validators; any one being true is OK.
 *
 * @author Philip Warner
 */
public class OrValidator
        extends MetaValidator
        implements DataValidator {

    private static final long serialVersionUID = -467862551216306038L;

    public OrValidator(@NonNull final DataValidator v1,
                       @NonNull final DataValidator v2) {
        super(v1, v2);
    }

    public OrValidator(@NonNull final DataValidator v1,
                       @NonNull final DataValidator v2,
                       @NonNull final DataValidator v3) {
        super(v1, v2, v3);
    }

    @Override
    public void validate(@NonNull final DataManager data,
                         @NonNull final Datum datum,
                         final boolean crossValidating)
            throws ValidatorException {
        ValidatorException lastException = null;
        for (DataValidator v : this) {
            try {
                v.validate(data, datum, crossValidating);
                return;
            } catch (ValidatorException e) {
                // Do nothing...try next validator, but keep it for later
                lastException = e;
            } catch (RuntimeException e) {
                // Do nothing...try next validator
            }
        }
        if (lastException != null) {
            throw lastException;
        } else {
            throw new ValidatorException(R.string.vldt_failed, new Object[]{datum.getKey()});
        }
    }
}
