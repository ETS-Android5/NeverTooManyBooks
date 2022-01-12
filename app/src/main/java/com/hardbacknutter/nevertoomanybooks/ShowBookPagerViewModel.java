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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistNavigatorDao;
import com.hardbacknutter.nevertoomanybooks.booklist.style.ListStyle;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;

public class ShowBookPagerViewModel
        extends ViewModel {

    private static final String TAG = "ShowBookPagerViewModel";

    /** Table name of the {@link Booklist} table. */
    public static final String BKEY_NAV_TABLE_NAME = TAG + ":LTName";
    /** The row id in the list table for the initial book to show. */
    public static final String BKEY_LIST_TABLE_ROW_ID = TAG + ":LTRow";

    /** <strong>Optionally</strong> passed. */
    @Nullable
    private BooklistNavigatorDao mNavHelper;

    /** <strong>Optionally</strong> passed. */
    @Nullable
    private String mStyleUuid;

    /**
     * The <strong>initial</strong> pager position being displayed.
     * This is {@code 0} based as it's the recycler view list position.
     */
    @IntRange(from = 0)
    private int mInitialPagerPosition;
    /** The <strong>initial</strong> book id to show. */
    private long mInitialBookId;

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    @Override
    public void onCleared() {
        if (mNavHelper != null) {
            mNavHelper.close();
        }
    }

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public void init(@NonNull final Bundle args) {
        if (mInitialBookId == 0) {
            mInitialBookId = args.getLong(DBKey.PK_ID, 0);
            SanityCheck.requirePositiveValue(mInitialBookId, DBKey.PK_ID);

            // optional
            mStyleUuid = args.getString(ListStyle.BKEY_STYLE_UUID);

            // the list is optional
            // If present, the user can swipe to the next/previous book in the list.
            final String navTableName = args.getString(BKEY_NAV_TABLE_NAME);
            if (navTableName != null && !navTableName.isEmpty()) {
                final long rowId = args.getLong(BKEY_LIST_TABLE_ROW_ID, 0);
                SanityCheck.requirePositiveValue(rowId, BKEY_LIST_TABLE_ROW_ID);
                mNavHelper = new BooklistNavigatorDao(navTableName);
                mInitialPagerPosition = mNavHelper.getRowNumber(rowId) - 1;
            } else {
                mInitialPagerPosition = 0;
            }
        }
    }

    @Nullable
    public String getStyleUuid() {
        return mStyleUuid;
    }

    /**
     * Get the initial position of the pager.
     * <strong>Use only to set {@link androidx.viewpager2.widget.ViewPager2#setCurrentItem}</strong>
     *
     * @return pager position
     */
    @IntRange(from = 0)
    int getInitialPagerPosition() {
        return mInitialPagerPosition;
    }

    @IntRange(from = 1)
    int getRowCount() {
        if (mNavHelper != null) {
            return mNavHelper.getRowCount();
        } else {
            return 1;
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public long getBookIdAtPosition(@IntRange(from = 0) final int position) {
        if (mNavHelper != null) {
            return mNavHelper.getBookIdAtRow(position + 1);
        }
        return mInitialBookId;
    }
}
