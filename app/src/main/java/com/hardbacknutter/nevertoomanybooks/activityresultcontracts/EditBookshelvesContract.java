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
package com.hardbacknutter.nevertoomanybooks.activityresultcontracts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.EditBookshelvesFragment;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class EditBookshelvesContract
        extends ActivityResultContract<Long, Long> {

    private static final String TAG = "EditBookshelvesContract";

    @NonNull
    public static Intent createResultIntent(@Nullable final Bookshelf selectedBookshelf) {
        final Intent resultIntent = new Intent();
        if (selectedBookshelf != null) {
            resultIntent.putExtra(DBKey.FK_BOOKSHELF, selectedBookshelf.getId());
        }
        return resultIntent;
    }

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Long bookshelfId) {
        final Intent intent = FragmentHostActivity
                .createIntent(context, EditBookshelvesFragment.class);
        if (bookshelfId != 0) {
            intent.putExtra(DBKey.FK_BOOKSHELF, (long) bookshelfId);
        }
        return intent;
    }

    @NonNull
    @Override
    public Long parseResult(final int resultCode,
                            @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return 0L;
        }

        // the last edited/inserted shelf
        return intent.getLongExtra(DBKey.FK_BOOKSHELF, Bookshelf.DEFAULT);
    }
}
