/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License V3
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

package com.eleybourn.bookcatalogue.booklist;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.CallSuper;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.booklist.prefs.PBoolean;
import com.eleybourn.bookcatalogue.booklist.prefs.PPref;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.definitions.DomainDefinition;
import com.eleybourn.bookcatalogue.utils.UniqueMap;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_AUTHOR_FORMATTED;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOKSHELF;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_FORMAT;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_GENRE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LANGUAGE;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_LOCATION;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_PUBLISHER;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_BOOK_RATING;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ACQUIRED_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ACQUIRED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ACQUIRED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ADDED_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ADDED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_ADDED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_FIRST_PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_FIRST_PUBLICATION_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_LAST_UPDATE_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_PUBLISHED_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_PUBLISHED_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_READ_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_READ_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_READ_YEAR;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_UPDATE_DAY;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_DATE_UPDATE_MONTH;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_AUTHOR_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_FK_SERIES_ID;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_LOANED_TO;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_READ_STATUS;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_SERIES_NAME;
import static com.eleybourn.bookcatalogue.database.DatabaseDefinitions.DOM_TITLE_LETTER;

/**
 * Class representing a single level in the booklist hierarchy.
 * <p>
 * There is a one-to-one mapping with the members of a {@link RowKind}.
 * <p>
 * {@link RowKind}
 * <p>
 * Not Parcelable: we parcel the 'kind' of the groups in a style.
 * There is no need to parcel the actual group.
 * <p>
 * HOWEVER: The {@link #mDomains} must be set at runtime each time but that is ok as
 * they are only needed at list build time.
 *
 * @author Philip Warner
 */
public class BooklistGroup
        implements Serializable, Parcelable {

    /** {@link Parcelable}. */
    public static final Creator<BooklistGroup> CREATOR =
            new Creator<BooklistGroup>() {
                @Override
                public BooklistGroup createFromParcel(@NonNull final Parcel source) {
                    return new BooklistGroup(source);
                }

                @Override
                public BooklistGroup[] newArray(final int size) {
                    return new BooklistGroup[size];
                }
            };
    /** */
    private static final long serialVersionUID = 1012206875683862714L;

    /**
     * the name of the Preference file (comes from the style that contains this group.
     */
    @Nullable
    String mUuid;

    /**
     * the kind of row/group we represent, see {@link RowKind}.
     * <p>
     * Do not rename or move this variable, deserialization will break.
     */
    private final int kind;

    /**
     * The domains represented by this group.
     * Set at runtime by builder based on current group and outer groups
     */
    @Nullable
    private transient ArrayList<DomainDefinition> mDomains;

    /**
     * Constructor.
     *
     * @param kind Kind of group to create
     * @param uuid of the style
     */
    private BooklistGroup(@IntRange(from = 0, to = RowKind.ROW_KIND_MAX) final int kind,
                          @NonNull final String uuid) {
        this.kind = kind;
        mUuid = uuid;
        initPrefs();
    }

    /**
     * Constructor.
     */
    protected BooklistGroup(@NonNull final Parcel in) {
        kind = in.readInt();
        mUuid = in.readString();
        mDomains = new ArrayList<>();
        in.readList(mDomains, getClass().getClassLoader());
        // now the prefs
        initPrefs();
    }

    /**
     * Create a new BooklistGroup of the specified kind, creating any specific
     * subclasses as necessary.
     *
     * @param kind Kind of group to create
     * @param uuid of the style
     *
     * @return a group based on the passed in kind
     */
    @NonNull
    public static BooklistGroup newInstance(@IntRange(from = 0, to = RowKind.ROW_KIND_MAX) final int kind,
                                            @NonNull final String uuid) {
        switch (kind) {
            case RowKind.AUTHOR:
                return new BooklistAuthorGroup(uuid);
            case RowKind.SERIES:
                return new BooklistSeriesGroup(uuid);
            default:
                return new BooklistGroup(kind, uuid);
        }
    }

    /**
     * @param style to get the groups from
     *
     * @return a list of BooklistGroups, one for each defined RowKind.
     */
    @NonNull
    public static List<BooklistGroup> getAllGroups(@NonNull final BooklistStyle style) {
        List<BooklistGroup> list = new ArrayList<>();
        //skip BOOK KIND
        for (int kind = 1; kind < RowKind.size(); kind++) {
            list.add(newInstance(kind, style.getUuid()));
        }
        return list;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeInt(kind);
        dest.writeString(mUuid);
        dest.writeList(mDomains);
        // now the prefs (none for now)
    }

    /** {@link Parcelable}. */
    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    public int getKind() {
        return kind;
    }

    String getName() {
        return RowKind.get(kind).getName();
    }

    @NonNull
    DomainDefinition getDisplayDomain() {
        return RowKind.get(kind).getDisplayDomain();
    }

    @NonNull
    CompoundKey getCompoundKey() {
        //noinspection ConstantConditions
        return RowKind.get(kind).getCompoundKey();
    }

    ArrayList<DomainDefinition> getDomains() {
        return mDomains;
    }

    void setDomains(@Nullable final ArrayList<DomainDefinition> domains) {
        mDomains = domains;
    }

    /**
     * Only ever init the Preferences if you have a valid UUID (null is valid).
     */
    protected void initPrefs() {
    }

    /**
     * @return the Preference objects that this group will contribute to a Style.
     */
    public Map<String, PPref> getPreferences() {
        return new LinkedHashMap<>();
    }

    /**
     * Preference UI support.
     * <p>
     * Add the Preference objects that this group will contribute to a Style.
     * TODO: could/should do this from xml instead I suppose.
     *
     * @param screen to add the prefs to
     */
    public void addPreferences(@NonNull final PreferenceScreen screen) {
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     * <p>
     * If we ever need to write objects here, don't forget to add a version field as first one.
     *
     * @see Serializable
     */
    private void writeObject(@NonNull final ObjectOutputStream out)
            throws IOException {
        out.defaultWriteObject();
    }

    /**
     * Custom serialization support. The signature of this method should never be changed.
     *
     * @see Serializable
     */
    private void readObject(@NonNull final ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initPrefs();
    }

    /**
     * Limited use for de-serialisation from a pre-v200 archive support.
     * Once the groups are processed, the UUID needs to be set manually
     * during de-serialization of the Style itself.
     *
     * @param uuid to set (from the Style)
     */
    public void setUuid(@NonNull final String uuid) {
        mUuid = uuid;
    }

    /** make it easy to display the name in generic functions. */
    @Override
    public String toString() {
        return getName();
    }

    /**
     * Do not rename or move this class, deserialization will break.
     * <p>
     * Specialized BooklistGroup representing a Series group. Includes extra attributes based
     * on preferences.
     */
    public static class BooklistSeriesGroup
            extends BooklistGroup
            implements Serializable, Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<BooklistSeriesGroup> CREATOR =
                new Creator<BooklistSeriesGroup>() {
                    @Override
                    public BooklistSeriesGroup createFromParcel(@NonNull final Parcel source) {
                        return new BooklistSeriesGroup(source);
                    }

                    @Override
                    public BooklistSeriesGroup[] newArray(final int size) {
                        return new BooklistSeriesGroup[size];
                    }
                };

        private static final long serialVersionUID = 9023218506278704155L;
        /** mAllSeries Parameter values and descriptions. */
        private static final String description =
                BookCatalogueApp.getResourceString(R.string.lbl_series);
        /** Show book under each series it appears in. */
        private transient PBoolean mAllSeries;

        /**
         * Constructor.
         *
         * @param uuid of the style
         */
        BooklistSeriesGroup(@NonNull final String uuid) {
            super(RowKind.SERIES, uuid);
        }

        /**
         * Constructor.
         */
        BooklistSeriesGroup(@NonNull final Parcel in) {
            super(in);
            initPrefs();
            mAllSeries.set(in);
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            mAllSeries.writeToParcel(dest);
        }

        /**
         * Only ever init the Preferences if you have a valid UUID (null is valid).
         */
        protected void initPrefs() {
            mAllSeries = new PBoolean(R.string.pk_bob_books_under_multiple_series, mUuid);
        }

        boolean showAllSeries() {
            return mAllSeries.isTrue();
        }

        /**
         * Get the Preference objects that this group will contribute to a Style.
         */
        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            Map<String, PPref> map = super.getPreferences();
            map.put(mAllSeries.getKey(), mAllSeries);
            return map;
        }

        /**
         * Preference UI support.
         * <p>
         * Add the Preference objects that this group will contribute to a Style.
         * TODO: could/should do this from xml instead I suppose.
         *
         * @param screen to add the prefs to
         */
        @Override
        public void addPreferences(@NonNull final PreferenceScreen screen) {
            PreferenceCategory category = (PreferenceCategory) screen.findPreference(
                    BookCatalogueApp.getResourceString(R.string.lbl_series));
            if (category != null) {
                category.setVisible(true);

                SwitchPreference pShowAll = new SwitchPreference(screen.getContext());
                pShowAll.setTitle(R.string.pt_bob_books_under_multiple_series);
                pShowAll.setIcon(R.drawable.ic_functions);
                pShowAll.setKey(BookCatalogueApp.getResourceString(
                        R.string.pk_bob_books_under_multiple_series));
                pShowAll.setDefaultValue(false);
                pShowAll.setSummaryOn(BookCatalogueApp.getResourceString(
                        R.string.pv_bob_books_under_multiple_show_book_under_each_1s, description));
                pShowAll.setSummaryOff(BookCatalogueApp.getResourceString(
                        R.string.pv_bob_books_under_multiple_show_under_primary_1s_only,
                        description));
                //pAllSeries.setHint(R.string.hint_series_book_may_appear_more_than_once);
                category.addPreference(pShowAll);
            }
        }

        /**
         * Custom serialization support. The signature of this method should never be changed.
         *
         * @see Serializable
         */
        private void writeObject(@NonNull final ObjectOutputStream out)
                throws IOException {
            out.defaultWriteObject();
            // version must use writeObject
            out.writeObject(BooklistStyle.realSerialVersion);

            out.writeObject(mAllSeries.get());
        }

        /**
         * We need to set the name resource ID for the properties since these may
         * change across versions.
         * <p>
         * Custom serialization support. The signature of this method should never be changed.
         *
         * @see Serializable
         */
        private void readObject(@NonNull final ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            initPrefs();

            Object object = in.readObject();
            if (object == null || object instanceof Boolean) {
                // pre v5.
                mAllSeries.set((Boolean) object);
                return;
            }
            //long version = (Long)object;
            mAllSeries.set((Boolean) in.readObject());
        }
    }

    /**
     * Do not rename or move this class, deserialization will break.
     * <p>
     * Specialized BooklistGroup representing an Author group. Includes extra attributes based
     * on preferences.
     */
    public static class BooklistAuthorGroup
            extends BooklistGroup
            implements Serializable, Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<BooklistAuthorGroup> CREATOR =
                new Creator<BooklistAuthorGroup>() {
                    @Override
                    public BooklistAuthorGroup createFromParcel(@NonNull final Parcel source) {
                        return new BooklistAuthorGroup(source);
                    }

                    @Override
                    public BooklistAuthorGroup[] newArray(final int size) {
                        return new BooklistAuthorGroup[size];
                    }
                };
        private static final long serialVersionUID = -1984868877792780113L;
        private static final String description = BookCatalogueApp.getResourceString(
                R.string.lbl_author);
        /** Support for 'Show All Authors of Book' property. */
        private transient PBoolean mAllAuthors;
        /** Support for 'Show Given Name First' property. */
        private transient PBoolean mGivenNameFirst;

        /**
         * Constructor.
         *
         * @param uuid of the style
         */
        BooklistAuthorGroup(@NonNull final String uuid) {
            super(RowKind.AUTHOR, uuid);
        }

        /**
         * Constructor.
         */
        BooklistAuthorGroup(@NonNull final Parcel in) {
            super(in);
            mAllAuthors.set(in);
            mGivenNameFirst.set(in);
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            super.writeToParcel(dest, flags);
            mAllAuthors.writeToParcel(dest);
            mGivenNameFirst.writeToParcel(dest);
        }

        /**
         * Only ever init the Preferences if you have a valid UUID (null is valid).
         */
        protected void initPrefs() {
            mAllAuthors = new PBoolean(R.string.pk_bob_books_under_multiple_authors, mUuid);
            mGivenNameFirst = new PBoolean(R.string.pk_bob_format_author_name, mUuid);
        }

        boolean showAllAuthors() {
            return mAllAuthors.isTrue();
        }

        boolean showGivenNameFirst() {
            return mGivenNameFirst.isTrue();
        }

        /**
         * Get the Preference objects that this group will contribute to a Style.
         */
        @Override
        @CallSuper
        public Map<String, PPref> getPreferences() {
            Map<String, PPref> map = super.getPreferences();
            map.put(mAllAuthors.getKey(), mAllAuthors);
            map.put(mGivenNameFirst.getKey(), mGivenNameFirst);
            return map;
        }

        /**
         * Preference UI support.
         * <p>
         * Add the Preference objects that this group will contribute to a Style.
         * TODO: could/should do this from xml instead I suppose.
         *
         * @param screen to add the prefs to
         */
        @Override
        public void addPreferences(@NonNull final PreferenceScreen screen) {
            PreferenceCategory category = (PreferenceCategory) screen.findPreference(
                    BookCatalogueApp.getResourceString(R.string.lbl_author));
            if (category != null) {
                category.setVisible(true);

                SwitchPreference pShowAll = new SwitchPreference(screen.getContext());
                pShowAll.setTitle(R.string.pt_bob_books_under_multiple_authors);
                pShowAll.setIcon(R.drawable.ic_functions);
                pShowAll.setKey(BookCatalogueApp.getResourceString(
                        R.string.pk_bob_books_under_multiple_authors));
                pShowAll.setDefaultValue(false);
                pShowAll.setSummaryOn(BookCatalogueApp.getResourceString(
                        R.string.pv_bob_books_under_multiple_show_book_under_each_1s, description));
                pShowAll.setSummaryOff(BookCatalogueApp.getResourceString(
                        R.string.pv_bob_books_under_multiple_show_under_primary_1s_only,
                        description));
                //pAllAuthors.setHint(R.string.hint_authors_book_may_appear_more_than_once)
                category.addPreference(pShowAll);

                SwitchPreference pGivenNameFirst = new SwitchPreference(screen.getContext());
                pGivenNameFirst.setTitle(R.string.pt_bob_format_author_name);
                pGivenNameFirst.setIcon(R.drawable.ic_title);
                pGivenNameFirst.setKey(
                        BookCatalogueApp.getResourceString(R.string.pk_bob_format_author_name));
                pGivenNameFirst.setDefaultValue(false);
                pGivenNameFirst.setSummaryOn(R.string.pv_bob_format_author_name_given_first);
                pGivenNameFirst.setSummaryOff(R.string.pv_bob_format_author_name_family_first);
                category.addPreference(pGivenNameFirst);
            }
        }

        /**
         * Custom serialization support. The signature of this method should never be changed.
         *
         * @see Serializable
         */
        private void writeObject(@NonNull final ObjectOutputStream out)
                throws IOException {
            out.defaultWriteObject();
            // version must use writeObject to be compat with original code
            out.writeObject(BooklistStyle.realSerialVersion);

            out.writeObject(mAllAuthors.get());
            out.writeObject(mGivenNameFirst.get());
        }

        /**
         * We need to set the name resource ID for the properties since these
         * may change across versions.
         * <p>
         * Custom serialization support. The signature of this method should never be changed.
         *
         * @see Serializable
         */
        private void readObject(@NonNull final ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            initPrefs();
            Object object = in.readObject();
            if (object == null || object instanceof Boolean) {
                // pre v5.
                mAllAuthors.set((Boolean) object);
                mGivenNameFirst.set((Boolean) in.readObject());
                return;
            }
            long version = (Long) object;
            mAllAuthors.set((Boolean) in.readObject());
            mGivenNameFirst.set((Boolean) in.readObject());
        }
    }

    /**
     * ENHANCE: add support for all? columns not handled yet. Status: 2019-01-06
     * {@link DatabaseDefinitions#DOM_BOOK_ANTHOLOGY_BITMASK}
     * {@link DatabaseDefinitions#DOM_BOOK_EDITION_BITMASK}
     * <p>
     * {@link DatabaseDefinitions#DOM_BOOK_PRICE_LISTED}
     * {@link DatabaseDefinitions#DOM_BOOK_PRICE_LISTED_CURRENCY}
     * {@link DatabaseDefinitions#DOM_BOOK_PRICE_PAID}
     * {@link DatabaseDefinitions#DOM_BOOK_PRICE_PAID_CURRENCY}
     * <p>
     * <p>
     * Get a RowKind with the static method: {@link #get(int kind)}.
     * <p>
     * We create them all once at startup and keep them cached,
     * so the RowKind class is for al intent and purpose static!
     */
    public static final class RowKind {

        // The code relies on BOOK being == 0
        public static final int BOOK = 0;
        public static final int AUTHOR = 1;
        public static final int SERIES = 2;
        public static final int GENRE = 3;
        public static final int PUBLISHER = 4;
        public static final int READ_STATUS = 5;
        public static final int LOANED = 6;
        public static final int DATE_PUBLISHED_YEAR = 7;
        public static final int DATE_PUBLISHED_MONTH = 8;
        public static final int TITLE_LETTER = 9;
        public static final int DATE_ADDED_YEAR = 10;
        public static final int DATE_ADDED_MONTH = 11;
        public static final int DATE_ADDED_DAY = 12;
        public static final int FORMAT = 13;
        public static final int DATE_READ_YEAR = 14;
        public static final int DATE_READ_MONTH = 15;
        public static final int DATE_READ_DAY = 16;
        public static final int LOCATION = 17;
        public static final int LANGUAGE = 18;
        public static final int DATE_LAST_UPDATE_YEAR = 19;
        public static final int DATE_LAST_UPDATE_MONTH = 20;
        public static final int DATE_LAST_UPDATE_DAY = 21;
        public static final int RATING = 22;
        public static final int BOOKSHELF = 23;
        public static final int DATE_ACQUIRED_YEAR = 24;
        public static final int DATE_ACQUIRED_MONTH = 25;
        public static final int DATE_ACQUIRED_DAY = 26;
        public static final int DATE_FIRST_PUBLICATION_YEAR = 27;
        public static final int DATE_FIRST_PUBLICATION_MONTH = 28;

        // NEWKIND: ROW_KIND_x
        // the highest valid index of kinds  ALWAYS update after adding a row kind...
        public static final int ROW_KIND_MAX = 28;

        private static final Map<Integer, RowKind> ALL_KINDS = new UniqueMap<>();

        static {
            RowKind rowKind;

            rowKind = new RowKind(BOOK, R.string.lbl_book, "", (DomainDefinition[]) null);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(AUTHOR, R.string.lbl_author, "a",
                                  DOM_FK_AUTHOR_ID);
            rowKind.setDisplayDomain(DOM_AUTHOR_FORMATTED);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(SERIES, R.string.lbl_series, "s",
                                  DOM_FK_SERIES_ID);
            rowKind.setDisplayDomain(DOM_SERIES_NAME);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            //all others will use the underlying domain as the displayDomain
            rowKind = new RowKind(GENRE, R.string.lbl_genre, "g",
                                  DOM_BOOK_GENRE);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(PUBLISHER, R.string.lbl_publisher, "p",
                                  DOM_BOOK_PUBLISHER);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(READ_STATUS, R.string.read_amp_unread, "r",
                                  DOM_READ_STATUS);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(LOANED, R.string.lbl_loaned, "l",
                                  DOM_LOANED_TO);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_PUBLISHED_YEAR, R.string.lbl_publication_year, "yrp",
                                  DOM_DATE_PUBLISHED_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_PUBLISHED_MONTH, R.string.lbl_publication_month, "mnp",
                                  DOM_DATE_PUBLISHED_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(TITLE_LETTER, R.string.style_builtin_title_first_letter, "t",
                                  DOM_TITLE_LETTER);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ADDED_YEAR, R.string.lbl_added_year, "yra",
                                  DOM_DATE_ADDED_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ADDED_MONTH, R.string.lbl_added_month, "mna",
                                  DOM_DATE_ADDED_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ADDED_DAY, R.string.lbl_added_day, "dya",
                                  DOM_DATE_ADDED_DAY);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(FORMAT, R.string.lbl_format, "fmt",
                                  DOM_BOOK_FORMAT);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_READ_YEAR, R.string.lbl_read_year, "yrr",
                                  DOM_DATE_READ_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_READ_MONTH, R.string.lbl_read_month, "mnr",
                                  DOM_DATE_READ_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_READ_DAY, R.string.lbl_read_day, "dyr",
                                  DOM_DATE_READ_DAY);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(LOCATION, R.string.lbl_location, "loc",
                                  DOM_BOOK_LOCATION);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(LANGUAGE, R.string.lbl_language, "lang",
                                  DOM_BOOK_LANGUAGE);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_LAST_UPDATE_YEAR, R.string.lbl_update_year, "yru",
                                  DOM_DATE_LAST_UPDATE_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_LAST_UPDATE_MONTH, R.string.lbl_update_month, "mnu",
                                  DOM_DATE_UPDATE_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_LAST_UPDATE_DAY, R.string.lbl_update_day, "dyu",
                                  DOM_DATE_UPDATE_DAY);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(RATING, R.string.lbl_rating, "rat",
                                  DOM_BOOK_RATING);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(BOOKSHELF, R.string.lbl_bookshelf, "shelf",
                                  DOM_BOOKSHELF);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ACQUIRED_YEAR, R.string.lbl_date_acquired_year, "yrac",
                                  DOM_DATE_ACQUIRED_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ACQUIRED_MONTH, R.string.lbl_date_acquired_month, "mnac",
                                  DOM_DATE_ACQUIRED_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_ACQUIRED_DAY, R.string.lbl_date_acquired_day, "dyac",
                                  DOM_DATE_ACQUIRED_DAY);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_FIRST_PUBLICATION_YEAR,
                                  R.string.lbl_first_publication_year, "yrfp",
                                  DOM_DATE_FIRST_PUBLICATION_YEAR);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            rowKind = new RowKind(DATE_FIRST_PUBLICATION_MONTH,
                                  R.string.lbl_first_publication_month, "mnfp",
                                  DOM_DATE_FIRST_PUBLICATION_MONTH);
            ALL_KINDS.put(rowKind.mKind, rowKind);

            // NEWKIND: ROW_KIND_x

            // Sanity check as our code relies on this
            for (int kind = 0; kind < (ROW_KIND_MAX - 1); kind++) {
                if (!ALL_KINDS.containsKey(kind)) {
                    throw new IllegalStateException("Missing kind " + kind);
                }
            }
            // Sanity check as our code relies on this (for() loop starting at 1)
            if (BOOK != 0) {
                throw new IllegalStateException("BOOK was " + BOOK);
            }
        }

        @IntRange(from = 0, to = RowKind.ROW_KIND_MAX)
        private final int mKind;

        @StringRes
        private final int mLabelId;

        @NonNull
        private final CompoundKey mCompoundKey;

        @Nullable
        private DomainDefinition mDisplayDomain;

        /**
         * @param domains all underlying domains.
         *                The first element will be used as the displayDomain.
         */
        private RowKind(@IntRange(from = 0, to = RowKind.ROW_KIND_MAX) final int kind,
                        @StringRes final int labelId,
                        @NonNull final String prefix,
                        @Nullable final DomainDefinition... domains) {
            mKind = kind;
            mLabelId = labelId;
            mCompoundKey = new CompoundKey(prefix, domains);
            if (domains != null && domains.length > 0) {
                mDisplayDomain = domains[0];
            }
        }

        /**
         * Don't use {@link #ROW_KIND_MAX} for code. Use this method.
         */
        public static int size() {
            return ALL_KINDS.size();
        }

        /**
         * @param kind to create
         *
         * @return a cached instance of a RowKind
         */
        @NonNull
        public static RowKind get(@IntRange(from = 0, to = RowKind.ROW_KIND_MAX) final int kind) {
            return ALL_KINDS.get(kind);
        }

        public int getKind() {
            return mKind;
        }

        /**
         * The display domain will never be null, except for a BOOK!
         */
        @NonNull
        public DomainDefinition getDisplayDomain() {
            return mDisplayDomain;
        }

        void setDisplayDomain(@NonNull final DomainDefinition displayDomain) {
            this.mDisplayDomain = displayDomain;
        }

        /**
         * Compound key of this RowKind ({@link BooklistGroup}).
         * <p>
         * The name will be of the form 'prefix/<n>' where 'prefix' is the prefix specific
         * to the RowKind, and <n> the id of the row, eg. 's/18' for Series with id=18
         */
        @NonNull
        CompoundKey getCompoundKey() {
            //noinspection ConstantConditions
            return mCompoundKey;
        }

        String getName() {
            return BookCatalogueApp.getResourceString(mLabelId);
        }

        @Override
        public String toString() {
            return "RowKind{" +
                    "name=" + getName() +
                    '}';
        }
    }

    /**
     * Represents a collection of domains that make a unique key for a given {@link RowKind}.
     */
    static class CompoundKey
            implements Parcelable {

        /** {@link Parcelable}. */
        public static final Creator<CompoundKey> CREATOR =
                new Creator<CompoundKey>() {
                    @Override
                    public CompoundKey createFromParcel(@NonNull final Parcel source) {
                        return new CompoundKey(source);
                    }

                    @Override
                    public CompoundKey[] newArray(final int size) {
                        return new CompoundKey[size];
                    }
                };

        /** Unique prefix used to represent a key in the hierarchy. */
        @NonNull
        private final String prefix;

        /** List of domains in key. */
        private final DomainDefinition[] domains;

        CompoundKey(@NonNull final String prefix,
                    @NonNull final DomainDefinition... domains) {
            this.prefix = prefix;
            this.domains = domains;
        }

        /** {@link Parcelable}. */
        CompoundKey(@NonNull final Parcel in) {
            prefix = in.readString();
            domains = in.createTypedArray(DomainDefinition.CREATOR);
        }

        /**
         * Never null but can be empty (for a BOOK)
         *
         * @return Unique prefix used to represent a key in the hierarchy.
         */
        @NonNull
        String getPrefix() {
            return prefix;
        }

        @Nullable
        DomainDefinition[] getDomains() {
            return domains;
        }

        @Override
        public void writeToParcel(@NonNull final Parcel dest,
                                  final int flags) {
            dest.writeString(prefix);
            dest.writeTypedArray(domains, flags);
        }

        /** {@link Parcelable}. */
        @SuppressWarnings("SameReturnValue")
        @Override
        public int describeContents() {
            return 0;
        }
    }
}

