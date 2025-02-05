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

import com.hardbacknutter.nevertoomanybooks.AuthorWorksFragment;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.FragmentHostActivity;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;

public class AuthorWorksContract
        extends ActivityResultContract<AuthorWorksContract.Input, EditBookOutput> {

    private static final String TAG = "AuthorWorksContract";

    @NonNull
    @Override
    public Intent createIntent(@NonNull final Context context,
                               @NonNull final Input input) {
        return FragmentHostActivity
                .createIntent(context, AuthorWorksFragment.class)
                .putExtra(DBKey.FK_AUTHOR, input.authorId)
                .putExtra(DBKey.FK_BOOKSHELF, input.bookshelfId)
                .putExtra(Style.BKEY_UUID, input.styleUuid);
    }

    @Override
    @Nullable
    public EditBookOutput parseResult(final int resultCode,
                                      @Nullable final Intent intent) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.ON_ACTIVITY_RESULT) {
            Logger.d(TAG, "parseResult", "|resultCode=" + resultCode + "|intent=" + intent);
        }

        if (intent == null || resultCode != Activity.RESULT_OK) {
            return null;
        }

        return intent.getParcelableExtra(EditBookOutput.BKEY);
    }

    public static class Input {

        final long authorId;
        final long bookshelfId;
        @NonNull
        final String styleUuid;

        public Input(final long authorId,
                     final long bookshelfId,
                     @NonNull final String styleUuid) {
            this.authorId = authorId;
            this.bookshelfId = bookshelfId;
            this.styleUuid = styleUuid;
        }
    }
}
