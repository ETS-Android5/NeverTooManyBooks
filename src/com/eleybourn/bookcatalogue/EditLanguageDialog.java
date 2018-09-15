/*
 * @copyright 2011 Philip Warner
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

package com.eleybourn.bookcatalogue;

import android.content.Context;
import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;

public class EditLanguageDialog extends EditStringDialog {
    EditLanguageDialog(@NonNull final Context context, @NonNull final CatalogueDBAdapter db, @NonNull final Runnable onChanged) {
        super(context, db, onChanged);
    }

    public void edit(@NonNull final String s) {
        super.edit(s, R.layout.dialog_edit_language, R.string.edit_language_details, R.string.name_can_not_be_blank);
    }

    @Override
    protected void confirmEdit(@NonNull final String from, @NonNull final String to) {
        if (to.equals(from)) {
            return;
        }
        mDb.globalReplaceLanguage(from, to);
        mOnChanged.run();
    }
}
