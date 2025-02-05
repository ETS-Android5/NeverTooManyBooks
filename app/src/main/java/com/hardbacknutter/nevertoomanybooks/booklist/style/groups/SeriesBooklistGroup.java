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
package com.hardbacknutter.nevertoomanybooks.booklist.style.groups;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.DomainExpression;
import com.hardbacknutter.nevertoomanybooks.entities.Series;

import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BL_BOOK_NUM_IN_SERIES_AS_FLOAT;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_BOOK_NUM_IN_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_FK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.DOM_SERIES_IS_COMPLETE;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_BOOK_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBDefinitions.TBL_SERIES;
import static com.hardbacknutter.nevertoomanybooks.database.DBKey.KEY_SERIES_TITLE_OB;


/**
 * Specialized BooklistGroup representing a {@link Series} group.
 * Includes extra attributes based on preferences.
 * <p>
 * {@link #getDisplayDomainExpression()} returns a customized display domain
 * {@link #getGroupDomainExpressions} adds the group/sorted domain based on the OB column.
 */
public class SeriesBooklistGroup
        extends AbstractLinkedTableBooklistGroup {

    public static final boolean DEFAULT_SHOW_BOOKS_UNDER_EACH = false;

    /** For sorting. */
    private static final Domain DOM_SORTING;

    static {
        DOM_SORTING = new Domain.Builder(DBKey.KEY_BL_SERIES_SORT, ColumnInfo.TYPE_TEXT)
                .build();
    }

    /**
     * Constructor.
     *
     * @param style Style reference.
     */
    SeriesBooklistGroup(@NonNull final Style style) {
        super(style, SERIES);

        underEach = DEFAULT_SHOW_BOOKS_UNDER_EACH;
    }

    /**
     * Copy constructor.
     *
     * @param style Style reference.
     * @param group to copy from
     */
    SeriesBooklistGroup(@NonNull final Style style,
                        @NonNull final SeriesBooklistGroup group) {
        super(style, group);
    }

    @Override
    @NonNull
    public GroupKey createGroupKey() {
        // We use the foreign ID to create the key domain.
        // We override the display domain in #createDisplayDomainExpression.
        return new GroupKey(R.string.lbl_series, "s",
                            new DomainExpression(DOM_FK_SERIES,
                                                 TBL_SERIES.dot(DBKey.PK_ID)))
                .addGroupDomain(
                        // We do not sort on the key domain but add the OB column instead
                        new DomainExpression(DOM_SORTING,
                                             TBL_SERIES.dot(KEY_SERIES_TITLE_OB),
                                             DomainExpression.SORT_ASC))
                .addGroupDomain(
                        // Group by id (we want the id available and there is
                        // a chance two Series will have the same name)
                        new DomainExpression(DOM_FK_SERIES,
                                             TBL_BOOK_SERIES.dot(DBKey.FK_SERIES)))
                .addGroupDomain(
                        // Group by complete-flag
                        new DomainExpression(DOM_SERIES_IS_COMPLETE,
                                             TBL_SERIES.dot(DBKey.SERIES_IS_COMPLETE)))
                .addBaseDomain(
                        // The series number in the base data in sorted order.
                        // This field is NOT displayed.
                        // Casting it as a float allows for the possibility of 3.1,
                        // or even 3.1|Omnibus 3-10" as a series number.
                        new DomainExpression(DOM_BL_BOOK_NUM_IN_SERIES_AS_FLOAT,
                                             "CAST("
                                             + TBL_BOOK_SERIES.dot(DBKey.SERIES_BOOK_NUMBER)
                                             + " AS REAL)",
                                             DomainExpression.SORT_ASC))
                .addBaseDomain(
                        // The series number in the base data in sorted order.
                        // This field is displayed.
                        // Covers non-numeric data (where the above float would fail)
                        new DomainExpression(DOM_BOOK_NUM_IN_SERIES,
                                             TBL_BOOK_SERIES.dot(DBKey.SERIES_BOOK_NUMBER),
                                             DomainExpression.SORT_ASC));
    }

    @Override
    @NonNull
    protected DomainExpression createDisplayDomainExpression(@NonNull final Style style) {
        // Not sorted; we sort on the OB domain as defined in #createGroupKey.
        return new DomainExpression(DBDefinitions.DOM_SERIES_TITLE,
                                    DBDefinitions.TBL_SERIES.dot(DBKey.SERIES_TITLE));
    }

    @Override
    public void setPreferencesVisible(@NonNull final PreferenceScreen screen,
                                      final boolean visible) {

        final PreferenceCategory category = screen.findPreference(StyleDataStore.PSK_STYLE_SERIES);
        if (category != null) {
            final String[] keys = {StyleDataStore.PK_GROUPS_SERIES_SHOW_BOOKS_UNDER_EACH};
            setPreferenceVisibility(category, keys, visible);
        }
    }

    @Override
    @NonNull
    public String toString() {
        return "SeriesBooklistGroup{"
               + super.toString()
               + '}';
    }
}
