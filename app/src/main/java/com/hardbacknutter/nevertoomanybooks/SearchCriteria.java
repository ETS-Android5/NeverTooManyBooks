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

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.database.dao.FtsDao;

/**
 * Holder class for search criteria with some methods to bulk manipulate them.
 */
public class SearchCriteria
        implements Parcelable {

    public static final Creator<SearchCriteria> CREATOR = new Creator<>() {
        @Override
        public SearchCriteria createFromParcel(@NonNull final Parcel in) {
            return new SearchCriteria(in);
        }

        @Override
        public SearchCriteria[] newArray(final int size) {
            return new SearchCriteria[size];
        }
    };

    /** Log tag. */
    private static final String TAG = "SearchCriteria";
    public static final String BKEY = TAG + ":a";
    /**
     * Bundle key for Author search text
     * (all DB KEY's and the ARRAY key is for authors with verified names).
     */
    public static final String BKEY_SEARCH_TEXT_AUTHOR = TAG + ":author";
    /**
     * Bundle key for Publisher search text
     * (all DB KEY's and the ARRAY key is for publishers with verified names).
     */
    public static final String BKEY_SEARCH_TEXT_PUBLISHER = TAG + ":publisher";
    /** Bundle key for generic search text. */
    private static final String BKEY_SEARCH_TEXT_KEYWORDS = TAG + ":keywords";
    /**
     * List of book ID's to display.
     * The RESULT of a search with {@link SearchFtsFragment}
     * which can be re-used for the builder.
     */
    @NonNull
    private final ArrayList<Long> bookIdList = new ArrayList<>();

    /** Book title to use in FTS search query. */
    @Nullable
    private String ftsBookTitle;
    /** Series title to use in FTS search query. */
    @Nullable
    private String ftsSeriesTitle;
    /** Author to use in FTS search query. */
    @Nullable
    private String ftsAuthor;
    /** Publisher to use in FTS search query. */
    @Nullable
    private String ftsPublisher;
    /**
     * Keywords to use in FTS search query.
     * <p>
     * Always use {@link #setFtsKeywords(String)} as we need to intercept the "." character.
     */
    @Nullable
    private String ftsKeywords;
    /** Name of the person we lend books to, to use in search query. */
    @Nullable
    private String loanee;

    SearchCriteria() {
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private SearchCriteria(@NonNull final Parcel in) {
        in.readList(bookIdList, getClass().getClassLoader());
        ftsBookTitle = in.readString();
        ftsSeriesTitle = in.readString();
        ftsAuthor = in.readString();
        ftsPublisher = in.readString();
        ftsKeywords = in.readString();
        loanee = in.readString();
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeList(bookIdList);
        dest.writeString(ftsBookTitle);
        dest.writeString(ftsSeriesTitle);
        dest.writeString(ftsAuthor);
        dest.writeString(ftsPublisher);
        dest.writeString(ftsKeywords);
        dest.writeString(loanee);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public void clear() {
        bookIdList.clear();
        ftsBookTitle = null;
        ftsSeriesTitle = null;
        ftsAuthor = null;
        ftsPublisher = null;
        ftsKeywords = null;
        loanee = null;
    }

    @NonNull
    public ArrayList<Long> getBookIdList() {
        // used directly!
        return bookIdList;
    }

    public void setBookIdList(@Nullable final List<Long> bookIdList) {
        this.bookIdList.clear();
        if (bookIdList != null) {
            this.bookIdList.addAll(bookIdList);
        }
    }

    @NonNull
    public Optional<String> getFtsMatchQuery() {
        return FtsDao.createMatchString(ftsBookTitle,
                                        ftsSeriesTitle,
                                        ftsAuthor,
                                        ftsPublisher,
                                        ftsKeywords);
    }

    @Nullable
    public String getFtsBookTitle() {
        return ftsBookTitle;
    }

    public void setFtsBookTitle(@Nullable final String ftsBookTitle) {
        this.ftsBookTitle = ftsBookTitle;
    }

    @Nullable
    public String getFtsSeriesTitle() {
        return ftsSeriesTitle;
    }

    public void setFtsSeriesTitle(@Nullable final String ftsSeriesTitle) {
        this.ftsSeriesTitle = ftsSeriesTitle;
    }

    @Nullable
    public String getFtsAuthor() {
        return ftsAuthor;
    }

    public void setFtsAuthor(@Nullable final String ftsAuthor) {
        this.ftsAuthor = ftsAuthor;
    }

    @Nullable
    public String getFtsPublisher() {
        return ftsPublisher;
    }

    public void setFtsPublisher(@Nullable final String ftsPublisher) {
        this.ftsPublisher = ftsPublisher;
    }

    @Nullable
    public String getFtsKeywords() {
        return ftsKeywords;
    }

    public void setFtsKeywords(@Nullable final String keywords) {
        if (keywords == null || keywords.isEmpty() || ".".equals(keywords)) {
            ftsKeywords = null;
        } else {
            ftsKeywords = keywords.trim();
        }
    }

    /** Not supported by FTS. */
    @NonNull
    public Optional<String> getLoanee() {
        if (loanee != null && !loanee.trim().isEmpty()) {
            return Optional.of(loanee.trim());
        } else {
            return Optional.empty();
        }
    }

    public void setLoanee(@Nullable final String loanee) {
        this.loanee = loanee;
    }

    public void search(@NonNull final FtsDao dao,
                       final int maxSuggestions) {
        bookIdList.clear();
        bookIdList.addAll(dao.search(ftsAuthor,
                                     ftsBookTitle,
                                     ftsSeriesTitle,
                                     ftsPublisher,
                                     ftsKeywords,
                                     maxSuggestions));
    }

    /**
     * Get a list with all search words, for displaying.
     *
     * @return an Optional with the list of criteria
     */
    @NonNull
    public Optional<List<String>> getDisplayText() {
        final List<String> list = new ArrayList<>();

        if (ftsBookTitle != null && !ftsBookTitle.isEmpty()) {
            list.add(ftsBookTitle);
        }
        if (ftsSeriesTitle != null && !ftsSeriesTitle.isEmpty()) {
            list.add(ftsSeriesTitle);
        }
        if (ftsAuthor != null && !ftsAuthor.isEmpty()) {
            list.add(ftsAuthor);
        }
        if (ftsPublisher != null && !ftsPublisher.isEmpty()) {
            list.add(ftsPublisher);
        }
        if (ftsKeywords != null && !ftsKeywords.isEmpty()) {
            list.add(ftsKeywords);
        }
        if (loanee != null && !loanee.isEmpty()) {
            list.add(loanee);
        }

        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(list);
        }
    }

    public boolean isEmpty() {
        return bookIdList.isEmpty()
               && (ftsBookTitle == null || ftsBookTitle.isEmpty())
               && (ftsSeriesTitle == null || ftsSeriesTitle.isEmpty())
               && (ftsAuthor == null || ftsAuthor.isEmpty())
               && (ftsPublisher == null || ftsPublisher.isEmpty())
               && (ftsKeywords == null || ftsKeywords.isEmpty())
               && (loanee == null || loanee.isEmpty());
    }

    @Override
    @NonNull
    public String toString() {
        return "SearchCriteria{"
               + "ftsBookTitle=`" + ftsBookTitle + '`'
               + ", ftsSeriesTitle=`" + ftsSeriesTitle + '`'
               + ", ftsAuthor=`" + ftsAuthor + '`'
               + ", ftsPublisher=`" + ftsPublisher + '`'
               + ", ftsKeywords=`" + ftsKeywords + '`'
               + ", loanee=`" + loanee + '`'
               + ", bookIdList=" + bookIdList
               + '}';
    }
}
