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

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.booklist.filters.PFilter;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;

public class BooklistHeader {

    /**
     * The amount of details to show in the header.
     * <strong>Never change these values</strong>, they get stored in the db.
     * <p>
     * not in use:
     * 1 << 1
     */
    public static final int SHOW_BOOK_COUNT = 1;
    public static final int SHOW_SEARCH_CRITERIA = 1 << 2;
    public static final int SHOW_STYLE_NAME = 1 << 3;
    public static final int SHOW_FILTERS = 1 << 4;
    /** the amount of details to show in the header. This is also the default. */
    public static final int BITMASK_ALL =
            SHOW_BOOK_COUNT
            | SHOW_SEARCH_CRITERIA
            | SHOW_STYLE_NAME
            | SHOW_FILTERS;

    @Nullable
    private String styleName;
    @Nullable
    private String bookCount;
    @Nullable
    private String filterText;
    @Nullable
    private String searchText;

    public BooklistHeader(@NonNull final Context context,
                          @NonNull final Style style,
                          final int totalBooks,
                          final int distinctBooks,
                          @NonNull final List<PFilter<?>> filters,
                          @Nullable final SearchCriteria searchCriteria) {

        if (style.isShowHeaderField(SHOW_STYLE_NAME)) {
            styleName = style.getLabel(context);
        }

        if (style.isShowHeaderField(SHOW_BOOK_COUNT)) {
            if (distinctBooks == totalBooks) {
                // Using a plural, so this covers zero books as well
                bookCount = context.getResources()
                                   .getQuantityString(R.plurals.displaying_n_books,
                                                      distinctBooks, totalBooks);
            } else {
                bookCount = context.getString(R.string.txt_displaying_n_books_in_m_entries,
                                              distinctBooks, totalBooks);
            }
        }

        if (style.isShowHeaderField(SHOW_SEARCH_CRITERIA)) {
            if (searchCriteria != null) {
                searchCriteria.getDisplayText().ifPresent(
                        list -> searchText = String.join(", ", list));
            }
        }

        if (style.isShowHeaderField(SHOW_FILTERS)) {
            final String text = filters
                    .stream()
                    .filter(f -> f.isActive(context))
                    .map(filter -> filter.getValueText(context))
                    .collect(Collectors.joining(", "));

            if (!text.isEmpty()) {
                filterText = context.getString(R.string.lbl_search_filtered_on_x, text);
            }
        }
    }

    @Nullable
    public String getStyleName() {
        return styleName;
    }

    @Nullable
    public String getBookCount() {
        return bookCount;
    }

    @Nullable
    public String getFilterText() {
        return filterText;
    }

    @Nullable
    public String getSearchText() {
        return searchText;
    }

    @IntDef(flag = true, value = {SHOW_BOOK_COUNT,
                                  SHOW_SEARCH_CRITERIA,
                                  SHOW_STYLE_NAME,
                                  SHOW_FILTERS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Option {

    }
}
