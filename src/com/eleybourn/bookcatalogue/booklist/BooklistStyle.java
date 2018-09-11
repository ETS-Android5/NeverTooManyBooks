/*
 * @copyright 2012 Philip Warner
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

package com.eleybourn.bookcatalogue.booklist;

import android.support.annotation.NonNull;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BooksMultitypeListHandler;
import com.eleybourn.bookcatalogue.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.DatabaseDefinitions;
import com.eleybourn.bookcatalogue.database.DomainDefinition;
import com.eleybourn.bookcatalogue.properties.BooleanListProperty;
import com.eleybourn.bookcatalogue.properties.BooleanProperty;
import com.eleybourn.bookcatalogue.properties.IntegerListProperty;
import com.eleybourn.bookcatalogue.properties.ListProperty.ItemEntries;
import com.eleybourn.bookcatalogue.properties.Properties;
import com.eleybourn.bookcatalogue.properties.Property;
import com.eleybourn.bookcatalogue.properties.PropertyGroup;
import com.eleybourn.bookcatalogue.properties.StringProperty;
import com.eleybourn.bookcatalogue.utils.SerializationUtils;
import com.eleybourn.bookcatalogue.utils.SerializationUtils.DeserializationException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;

/**
 * Represents a specific style of book list (eg. authors/series). Individual {@link BooklistGroup} objects
 * are added to a style in order to describe the resulting list style.
 *
 * ENHANCE: Allow for style-based overrides of things currently stored in preferences
 * This should include thumbnail presence/size, book-in-each-series etc. as well as font sizes.
 *
 * How to add a new Group:
 *
 * - add it to {@link BooklistGroup.RowKinds} Update {@link BooklistGroup.RowKinds#ROW_KIND_MAX}
 * - add new domain to {@link DatabaseDefinitions } (if necessary)
 * - modify {@link BooklistBuilder#build} to add the necessary grouped/sorted domains
 * - modify {@link BooksMultitypeListHandler}; if it is just a string field,
 *   then use a {@link BooksMultitypeListHandler.GenericStringHolder}.
 *   Otherwise add a new holder.
 *
 * Need to at least modify {@link BooksMultitypeListHandler#newHolder}
 *
 * @author Philip Warner
 */
public class BooklistStyle implements Iterable<BooklistGroup>, Serializable {
    private static final long serialVersionUID = 6615877148246388549L;
    /** version field used in serialised data reading from file, see {@link #readObject} */
    private static final long realSerialVersion = 5;

    /** Extra book data to show at lowest level */
    public static final int EXTRAS_BOOKSHELVES = 1;
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_LOCATION = 2;
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_PUBLISHER = 4;
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_AUTHOR = 8;
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_THUMBNAIL = 16;
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_THUMBNAIL_LARGE = 32;
    /** Extra book data to show at lowest level */
    public static final int EXTRAS_FORMAT = 64;

    /** Extra book data to show at lowest level */
    public static final int EXTRAS_ALL = EXTRAS_BOOKSHELVES | EXTRAS_LOCATION | EXTRAS_PUBLISHER
            | EXTRAS_AUTHOR | EXTRAS_THUMBNAIL | EXTRAS_THUMBNAIL_LARGE | EXTRAS_FORMAT;


    public static final int FILTER_READ = 1;
    public static final int FILTER_UNREAD = 2;
    public static final int FILTER_READ_AND_UNREAD = 3;

    public static final Integer SUMMARY_HIDE = 0;
    public static final Integer SUMMARY_SHOW_COUNT = 1;
    public static final Integer SUMMARY_SHOW_LEVEL_1 = 2;
    public static final Integer SUMMARY_SHOW_LEVEL_2 = 4;
    public static final Integer SUMMARY_SHOW_LEVEL_1_AND_COUNT = SUMMARY_SHOW_COUNT ^ SUMMARY_SHOW_LEVEL_1;
    public static final Integer SUMMARY_SHOW_ALL = 0xff;

    private static final String SFX_SHOW_BOOKSHELVES = "ShowBookshelves";
    private static final String SFX_SHOW_LOCATION = "ShowLocation";
    private static final String SFX_SHOW_PUBLISHER = "ShowPublisher";
    private static final String SFX_SHOW_AUTHOR = "ShowAuthor";
    private static final String SFX_SHOW_THUMBNAILS = "ShowThumbnails";
    private static final String SFX_LARGE_THUMBNAILS = "LargeThumbnails";
    private static final String SFX_SHOW_FORMAT = "ShowFormat";

    private static final String SFX_CONDENSED = "Condensed";
    private static final String SFX_SHOW_HEADER_INFO = "ShowHeaderInfo";

    /** Prefix for all prefs */
    private static final String TAG = "BookList";
    /** Prefix for all prefs */
    private static final String PREF_SHOW_EXTRAS_PREFIX = TAG + ".";

    /** Show header info in list */
    private static final String PREF_SHOW_HEADER_INFO = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_HEADER_INFO;
    /** Show list of bookshelves for each book */
    private static final String PREF_CONDENSED_TEXT = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_CONDENSED;

    // Extra fields to show at the book level

    /** Show list of bookshelves for each book */
    private static final String PREF_SHOW_BOOKSHELVES = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_BOOKSHELVES;
    /** Show location for each book */
    private static final String PREF_SHOW_LOCATION = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_LOCATION;
    /** Show author for each book */
    private static final String PREF_SHOW_AUTHOR = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_AUTHOR;
    /** Show publisher for each book */
    private static final String PREF_SHOW_PUBLISHER = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_PUBLISHER;
    /** Show thumbnail image for each book */
    private static final String PREF_SHOW_THUMBNAILS = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_THUMBNAILS;
    /** Show large thumbnail if thumbnails are shown */
    private static final String PREF_LARGE_THUMBNAILS = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_LARGE_THUMBNAILS;
    /** Show format for each book */
    private static final String PREF_SHOW_FORMAT = PREF_SHOW_EXTRAS_PREFIX + BooklistStyle.SFX_SHOW_FORMAT;
    /** Support for 'READ' filter */
    private static final ItemEntries<Integer> mReadFilterListItems = new ItemEntries<>();
    /** Support for 'Condensed' property */
    private static final ItemEntries<Boolean> mCondensedListItems = new ItemEntries<>();
    /** Support for 'Show List Header Info' property */
    private static final ItemEntries<Integer> mShowHeaderInfoListItems = new ItemEntries<>();

    static {
        mReadFilterListItems.add(FILTER_UNREAD, R.string.select_unread_only);
        mReadFilterListItems.add(FILTER_READ, R.string.select_read_only);
        mReadFilterListItems.add(FILTER_READ_AND_UNREAD, R.string.all_books);

        mCondensedListItems.add(null, R.string.use_default_setting);
        mCondensedListItems.add(false, R.string.normal);
        mCondensedListItems.add(true, R.string.smaller);

        mShowHeaderInfoListItems.add(null, R.string.use_default_setting);
        mShowHeaderInfoListItems.add(SUMMARY_HIDE, R.string.hide_summary_details);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_COUNT, R.string.show_book_count);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_LEVEL_1_AND_COUNT, R.string.show_first_level_and_book_count);
        mShowHeaderInfoListItems.add(SUMMARY_SHOW_ALL, R.string.show_all_summary_details);
    }

    /** List of groups */
    private final ArrayList<BooklistGroup> mGroups;
    /** ID if string representing name of this style. Used for standard system-defined styles */
    private int mNameStringId;
    /** User-defined name of this style. Used for user-defined styles */
    @SuppressWarnings("unused")
    private String mName; // TODO: Legacy field designed for backward serialization compatibility
    /** replaces mName */
    private transient StringProperty mNameProperty;

    // ENHANCE: Add filters based on 'loaned', 'anthology' and (maybe) duplicate books
    /** Row id of database row from which this object comes */
    private long mRowId = 0;
    /** Extra details to show on book rows */
    private transient BooleanProperty mXtraShowThumbnails;
    /** Extra details to show on book rows */
    private transient BooleanProperty mXtraLargeThumbnails;
    /** Extra details to show on book rows */
    private transient BooleanProperty mXtraShowBookshelves;
    /** Extra details to show on book rows */
    private transient BooleanProperty mXtraShowLocation;
    /** Extra details to show on book rows */
    private transient BooleanProperty mXtraShowPublisher;
    /** Extra details to show on book rows */
    private transient BooleanProperty mXtraShowFormat;
    /** Extra details to show on book rows */
    private transient BooleanProperty mXtraShowAuthor;
    /** Extra details to show on book rows */
    private transient IntegerListProperty mXtraReadUnreadAll;

    /** Show list using smaller text */
    private transient BooleanListProperty mCondensed;
    /** Show list header info */
    private transient IntegerListProperty mShowHeaderInfo;

    /**
     * Flag indicating this style was in the 'preferred' set when it was added to its Styles collection
     * The value is not dynamically checked.
     */
    private boolean mIsPreferred;

    /**
     * Constructor for system-defined styles.
     */
    BooklistStyle(int stringId) {
        mNameStringId = stringId;
        mGroups = new ArrayList<>();
        initProperties();
        mNameProperty.set((String) null);
    }

    /**
     * Constructor for user-defined styles.
     */
    BooklistStyle(String name) {
        initProperties();
        mNameStringId = 0;
        mGroups = new ArrayList<>();
        mNameProperty.set(name);
    }

    public int getReadFilter() {
        return mXtraReadUnreadAll.getResolvedValue();
    }

    /**
     * Accessor for flag indicating style is among preferred styles.
     */
    public boolean isPreferred() {
        return mIsPreferred;
    }

    /**
     * Accessor for flag indicating style is among preferred styles.
     */
    public void setPreferred(boolean isPreferred) {
        mIsPreferred = isPreferred;
    }

    /**
     * Accessor. Returns system name or user-defined name based on kind of style this object defines.
     */
    public String getDisplayName() {
        String s = mNameProperty.getResolvedValue();
        if (!s.isEmpty())
            return s;
        else
            return BookCatalogueApp.getResourceString(mNameStringId);
    }

    /**
     * Accessor. Sets user-defined name.
     */
    public void setName(String name) {
        mNameProperty.set(name);
        mNameStringId = 0;
    }

    /**
     * Accessor. Returns a standardised form of the style name. This name is unique.
     */
    public String getCanonicalName() {
        if (isUserDefined())
            return getRowId() + "-u";
        else {
            String name = getDisplayName().trim().toLowerCase();
            return name + "-s";
        }
    }

    public void addGroup(BooklistGroup group) {
        mGroups.add(group);
    }

    /**
     * Add a group to this style below any already added groups.
     *
     * @param kind Kind of group to add.
     *
     * @return Newly created group.
     */
    @SuppressWarnings("UnusedReturnValue")
    public BooklistGroup addGroup(int kind) {
        BooklistGroup g = BooklistGroup.newGroup(kind);
        addGroup(g);
        return g;
    }

    /**
     * Remove a group from this style.
     *
     * @param kind Kind of group to add.
     *
     * @return Newly created group.
     */
    @SuppressWarnings("UnusedReturnValue")
    public BooklistGroup removeGroup(int kind) {
        BooklistGroup toRemove = null;
        for (BooklistGroup g : mGroups) {
            if (g.kind == kind) {
                toRemove = g;
                break;
            }
        }
        if (toRemove != null)
            mGroups.remove(toRemove);

        return toRemove;
    }

    /**
     * @return true if this style is user-defined.
     */
    public boolean isUserDefined() {
        return (mNameStringId == 0 || mRowId != 0);
    }

    private void initProperties() {
        mXtraShowThumbnails = new BooleanProperty("XThumbnails",
                PropertyGroup.GRP_THUMBNAILS,
                R.string.show_thumbnails,
                PREF_SHOW_THUMBNAILS,
                true);
        mXtraShowThumbnails.setWeight(-100);

        mXtraLargeThumbnails = new BooleanProperty("XLargeThumbnails",
                PropertyGroup.GRP_THUMBNAILS,
                R.string.prefer_large_thumbnails,
                PREF_LARGE_THUMBNAILS,
                false);
        mXtraLargeThumbnails.setWeight(-99);

        mXtraShowBookshelves = new BooleanProperty("XBookshelves",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS,
                R.string.bookshelves,
                PREF_SHOW_BOOKSHELVES,
                false);

        mXtraShowLocation = new BooleanProperty("XLocation",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS,
                R.string.location,
                PREF_SHOW_LOCATION,
                false);

        mXtraShowPublisher = new BooleanProperty("XPublisher",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS,
                R.string.publisher,
                PREF_SHOW_PUBLISHER,
                false);

        mXtraShowFormat = new BooleanProperty("XFormat",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS,
                R.string.format,
                PREF_SHOW_FORMAT,
                false);

        mXtraShowAuthor = new BooleanProperty("XAuthor",
                PropertyGroup.GRP_EXTRA_BOOK_DETAILS,
                R.string.author,
                PREF_SHOW_AUTHOR,
                false);

        mXtraReadUnreadAll = new IntegerListProperty(mReadFilterListItems,
                "XReadUnreadAll",
                PropertyGroup.GRP_EXTRA_FILTERS,
                R.string.select_based_on_read_status,
                FILTER_READ_AND_UNREAD);

        mNameProperty = new StringProperty("StyleName",
                PropertyGroup.GRP_GENERAL,
                R.string.name);

        mNameProperty.setRequireNonBlank(true);
        // Put it at top of its group
        mNameProperty.setWeight(-100);

        mCondensed = new BooleanListProperty(mCondensedListItems,
                PREF_CONDENSED_TEXT,
                PropertyGroup.GRP_GENERAL,
                R.string.size_of_booklist_items,
                null,
                PREF_CONDENSED_TEXT,
                false);

        mShowHeaderInfo = new IntegerListProperty(mShowHeaderInfoListItems,
                PREF_SHOW_HEADER_INFO,
                PropertyGroup.GRP_GENERAL,
                R.string.summary_details_in_header,
                null,
                PREF_SHOW_HEADER_INFO,
                SUMMARY_SHOW_ALL);
    }

    /**
     * Get all of the properties of this Style and its groups.
     */
    public Properties getProperties() {
        Properties props = new Properties();

        props.add(mXtraShowThumbnails);
        props.add(mXtraLargeThumbnails);
        props.add(mXtraShowBookshelves);
        props.add(mXtraShowLocation);
        props.add(mXtraShowPublisher);
        props.add(mXtraShowFormat);
        props.add(mXtraShowAuthor);
        props.add(mXtraReadUnreadAll);
        props.add(mCondensed);
        props.add(mNameProperty);
        props.add(mShowHeaderInfo);

        for (BooklistGroup g : mGroups) {
            g.getStyleProperties(props);
        }

        return props;
    }

    /**
     * Passed a Properties object, update the properties of this style
     * based on the values of the passed properties.
     */
    public void setProperties(Properties newProps) {
        Properties props = getProperties();
        for (Property newVal : newProps) {
            Property thisProp = props.get(newVal.getUniqueName());
            if (thisProp != null) {
                thisProp.set(newVal);
            }
        }
    }

    /**
     * Passed a template style, copy the group structure to this style.
     */
    public void setGroups(BooklistStyle fromStyle) {
        Properties newProps = new Properties();

        // Save the current groups
        Hashtable<Integer, BooklistGroup> oldGroups = new Hashtable<>();
        for (BooklistGroup g : this) {
            oldGroups.put(g.kind, g);
        }
        // Clear the current groups, and rebuild, reusing old values where possible
        mGroups.clear();
        for (BooklistGroup g : fromStyle) {
            BooklistGroup saved = oldGroups.get(g.kind);
            if (saved != null) {
                this.addGroup(saved);
            } else {
                g.getStyleProperties(newProps);
                this.addGroup(g.kind);
            }
        }
        // Copy any properties from new groups.
        this.setProperties(newProps);
    }

    /**
     * Accessor.
     */
    public int getExtras() {
        int extras = 0;

        if (mXtraShowThumbnails.getResolvedValue())
            extras |= EXTRAS_THUMBNAIL;

        if (mXtraLargeThumbnails.getResolvedValue())
            extras |= EXTRAS_THUMBNAIL_LARGE;

        if (mXtraShowBookshelves.getResolvedValue())
            extras |= EXTRAS_BOOKSHELVES;

        if (mXtraShowLocation.getResolvedValue())
            extras |= EXTRAS_LOCATION;

        if (mXtraShowPublisher.getResolvedValue())
            extras |= EXTRAS_PUBLISHER;

        if (mXtraShowFormat.getResolvedValue())
            extras |= EXTRAS_FORMAT;

        if (mXtraShowAuthor.getResolvedValue())
            extras |= EXTRAS_AUTHOR;

        return extras;
    }

    /**
     * Check if ths style has the specified group
     */
    public boolean hasKind(int kind) {
        for (BooklistGroup g : mGroups) {
            if (g.kind == kind)
                return true;
        }
        return false;
    }

    /**
     * Get the group at the passed index.
     */
    public BooklistGroup getGroupAt(int index) {
        return mGroups.get(index);
    }

    /**
     * Get the number of groups in this style
     */
    public int size() {
        return mGroups.size();
    }

    /**
     * Accessor for underlying database row id, if this object is from a database. 0 if not from database.
     */
    public long getRowId() {
        return mRowId;
    }

    /**
     * Accessor for underlying database row id, set by query that retrieves the object.
     */
    public void setRowId(long rowId) {
        mRowId = rowId;
    }

    /**
     * Iterable support
     */
    @NonNull
    @Override
    public Iterator<BooklistGroup> iterator() {
        return mGroups.iterator();
    }

    /**
     * Custom serialization support.
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeObject(realSerialVersion); // always write latest
        out.writeObject(mXtraShowThumbnails.get());
        out.writeObject(mXtraLargeThumbnails.get());
        out.writeObject(mXtraShowBookshelves.get());
        out.writeObject(mXtraShowLocation.get());
        out.writeObject(mXtraShowPublisher.get());
        out.writeObject(mXtraShowAuthor.get());
        out.writeObject(mXtraReadUnreadAll.get());
        out.writeObject(mCondensed.get());
        out.writeObject(mNameProperty.get());
        out.writeObject(mShowHeaderInfo.get());
        out.writeObject(mXtraShowFormat.get());
    }

    /**
     * Pseudo-constructor for custom serialization support.
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initProperties();
        Object o = in.readObject();
        long version = 0;
        if (o instanceof Long) {
            // Its the version
            version = ((Long) o);
            // Get the next object
            o = in.readObject();
        } // else it's a pre-version object...just use it

        mXtraShowThumbnails.set((Boolean) o);
        mXtraLargeThumbnails.set((Boolean) in.readObject());
        mXtraShowBookshelves.set((Boolean) in.readObject());
        mXtraShowLocation.set((Boolean) in.readObject());
        mXtraShowPublisher.set((Boolean) in.readObject());
        mXtraShowAuthor.set((Boolean) in.readObject());
        mXtraReadUnreadAll.set((Integer) in.readObject());
        if (version > 0)
            mCondensed.set((Boolean) in.readObject());
        if (version > 1)
            mNameProperty.set((String) in.readObject());
        else
            mNameProperty.set(mName);
        // Added mShowHeaderInfo with version 3
        if (version > 2) {
            // Changed it from Boolean to Integer in version 4
            if (version == 3) {
                Boolean isSet = (Boolean) in.readObject();
                if (isSet == null) {
                    mShowHeaderInfo.set((Integer) null);
                } else {
                    mShowHeaderInfo.set(isSet ? SUMMARY_SHOW_ALL : SUMMARY_HIDE);
                }
            } else {
                // version 4
                mShowHeaderInfo.set((Integer) in.readObject());
            }
        }
        if (version > 4) {
            mXtraShowFormat.set((Boolean) in.readObject());
        }
    }

    public boolean isCondensed() {
        return mCondensed.getResolvedValue();
    }

    public void setCondensed(boolean condensed) {
        mCondensed.set(condensed);
    }

    @SuppressWarnings("unused")
    public boolean showThumbnails() {
        return mXtraShowThumbnails.getResolvedValue();
    }

    public void setShowThumbnails(boolean show) {
        mXtraShowThumbnails.set(show);
    }

    @SuppressWarnings("unused")
    public Integer getReadUnreadAll() {
        return mXtraReadUnreadAll.getResolvedValue();
    }

    public void setReadUnreadAll(Integer readUnreadAll) {
        mXtraReadUnreadAll.set(readUnreadAll);
    }

    public int getShowHeaderInfo() {
        return mShowHeaderInfo.getResolvedValue();
    }

    /**
     * Save this style as a custom user style to the database.
     * Either updates or creates as necessary
     */
    public void saveToDb(CatalogueDBAdapter db) {
        if (getRowId() == 0)
            mRowId = db.insertBooklistStyle(this);
        else
            db.updateBooklistStyle(this);
    }

    /**
     * Delete this style from the database
     */
    public void deleteFromDb(CatalogueDBAdapter db) {
        if (getRowId() == 0)
            throw new RuntimeException("Style is not stored in the database, can not be deleted");
        db.deleteBooklistStyle(this.getRowId());
    }

    /**
     * Convenience function to return a list of group names.
     */
    public String getGroupListDisplayNames() {
        StringBuilder groups = new StringBuilder();
        boolean first = true;
        for (BooklistGroup g : this) {
            if (first)
                first = false;
            else
                groups.append(" / ");
            groups.append(g.getName());
        }
        return groups.toString();
    }

    /**
     * Construct a deep clone of this object.
     */
    public BooklistStyle getClone() throws DeserializationException {
        return SerializationUtils.cloneObject(this);
    }

    /**
     * Accessor to allow setting of Extras value directly.
     */
    public void setShowAuthor(boolean show) {
        mXtraShowAuthor.set(show);
    }

    /**
     * Represents a collection of domains that make a unique key for a given group.
     *
     * @author Philip Warner
     */
    public static class CompoundKey {
        /** Unique prefix used to represent a key in the hierarchy */
        final String prefix;
        /** List of domains in key */
        final DomainDefinition[] domains;

        /** Constructor */
        CompoundKey(String prefix, DomainDefinition... domains) {
            this.prefix = prefix;
            this.domains = domains;
        }
    }
}

