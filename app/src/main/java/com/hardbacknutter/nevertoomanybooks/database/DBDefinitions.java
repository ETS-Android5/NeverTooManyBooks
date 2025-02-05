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
package com.hardbacknutter.nevertoomanybooks.database;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.booklist.Booklist;
import com.hardbacknutter.nevertoomanybooks.booklist.BooklistHeader;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BookDetailsFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BooklistFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.BuiltinStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.Style;
import com.hardbacknutter.nevertoomanybooks.database.definitions.ColumnInfo;
import com.hardbacknutter.nevertoomanybooks.database.definitions.Domain;
import com.hardbacknutter.nevertoomanybooks.database.definitions.TableDefinition;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;

/**
 * Static definitions of database objects.
 * This is a <strong>mostly</strong> complete representation of the application database.
 *
 * <strong>Note:</strong> Fields 'name' attribute must be in LOWER CASE.
 * <p>
 * TODO: Collated indexes need to be done manually. See {@link DBHelper#recreateIndices()}
 * <p>
 * Currently (2022-05-14) UTC datetime is used with::
 * <ul>Main database:
 *  <li>{@link #DOM_ADDED__UTC}</li>
 *  <li>{@link #DOM_LAST_UPDATED__UTC}</li>
 *  <li>{@link #DOM_CALIBRE_LIBRARY_LAST_SYNC__UTC}</li>
 *  <li>{@link #DOM_STRIP_INFO_BE_LAST_SYNC__UTC}</li>
 * </ul>
 * <ul>Covers cache database:
 *   <li>{@link CoversDbHelper}#DATETIME_LAST_UPDATED__UTC}</li>
 * </ul>
 * <p>
 * All others, are considered USER local time zone.
 * <p>
 * Rationale: given this is an app running on a device in the users pocket,
 * using UTC for those columns would only be useful to users who travel to other timezones,
 * reset their devices to the new timezone, and actively edit (user) date columns while
 * travelling.
 */
@SuppressWarnings("WeakerAccess")
public final class DBDefinitions {

    /**
     * A collection of all tables used to be able to rebuild indexes etc...,
     * added in order so interdependency's work out.
     * <p>
     * Only add standard tables. Do not add temporary/FTS tables.
     * app tables
     * TBL_BOOKLIST_STYLES,
     * <p>
     * basic user data tables
     * TBL_BOOKSHELF,
     * TBL_BOOKSHELF_FILTERS
     * TBL_AUTHORS,
     * TBL_SERIES,
     * TBL_PUBLISHERS,
     * TBL_BOOKS,
     * TBL_TOC_ENTRIES,
     * <p>
     * link tables
     * TBL_BOOK_BOOKSHELF,
     * TBL_BOOK_AUTHOR,
     * TBL_BOOK_SERIES,
     * TBL_BOOK_PUBLISHER,
     * TBL_BOOK_TOC_ENTRIES,
     * TBL_BOOK_LOANEE,
     * <p>
     * TBL_CALIBRE_BOOKS,
     * TBL_CALIBRE_LIBRARIES,
     * <p>
     * permanent booklist management tables
     * TBL_BOOK_LIST_NODE_STATE: storage of the expanded/collapsed status of the book list tree.
     * <p>
     * TBL_STRIPINFO_COLLECTION_TO_IMPORT: stores external id's for new books to import
     * from this site. Used as a means to split the relatively fast process of getting
     * the collection data (fast) and as a next step importing new books (slow).
     */
    public static final Map<String, TableDefinition> ALL_TABLES = new LinkedHashMap<>();

    /* ======================================================================================
     * Basic table definitions with type & alias set.
     * All these should be added to {@link #ALL_TABLES}.
     * ====================================================================================== */

    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKSHELF;
    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKSHELF_FILTERS;
    /** Basic table definition. */
    public static final TableDefinition TBL_AUTHORS;
    /** Basic table definition. */
    public static final TableDefinition TBL_SERIES;
    /** Basic table definition. */
    public static final TableDefinition TBL_PUBLISHERS;
    /** Basic table definition. */
    public static final TableDefinition TBL_TOC_ENTRIES;
    /** Basic table definition. */
    public static final TableDefinition TBL_BOOKS;
    /** link table. */
    public static final TableDefinition TBL_BOOK_BOOKSHELF;
    /** link table. */
    public static final TableDefinition TBL_BOOK_AUTHOR;
    /** link table. */
    public static final TableDefinition TBL_BOOK_SERIES;
    /** link table. */
    public static final TableDefinition TBL_BOOK_PUBLISHER;
    /** link table. */
    public static final TableDefinition TBL_BOOK_LOANEE;
    /** link table. */
    public static final TableDefinition TBL_BOOK_TOC_ENTRIES;

    /** User defined styles. */
    public static final TableDefinition TBL_BOOKLIST_STYLES;
    /** Keeps track of nodes in the list across application restarts. */
    public static final TableDefinition TBL_BOOK_LIST_NODE_STATE;

    /** A bridge to a Calibre database. Partially imported data. */
    public static final TableDefinition TBL_CALIBRE_BOOKS;
    /** The custom fields in Calibre, mapped to our local fields. */
    public static final TableDefinition TBL_CALIBRE_CUSTOM_FIELDS;
    /** The Calibre library(ies) to which we have/are connected. **/
    public static final TableDefinition TBL_CALIBRE_LIBRARIES;
    /** The mapping of a Calibre Library/Virtual Library to a Bookshelf. */
    public static final TableDefinition TBL_CALIBRE_VIRTUAL_LIBRARIES;

    /** A bridge to the stripinfo.be site. Site specific imported data. */
    public static final TableDefinition TBL_STRIPINFO_COLLECTION;

    /* ======================================================================================
     * Primary and Foreign key domain definitions.
     * ====================================================================================== */
    /** Primary key. */
    public static final Domain DOM_PK_ID;

    /** Foreign key. */
    public static final Domain DOM_FK_AUTHOR;
    /** Foreign key. */
    public static final Domain DOM_FK_BOOKSHELF;
    /** Foreign key. */
    public static final Domain DOM_FK_BOOK;
    /** Foreign key. */
    public static final Domain DOM_FK_SERIES;
    /** Foreign key. */
    public static final Domain DOM_FK_PUBLISHER;
    /** Foreign key. */
    public static final Domain DOM_FK_TOC_ENTRY;
    /** Foreign key. */
    public static final Domain DOM_FK_CALIBRE_LIBRARY;
    /**
     * Foreign key.
     * When a style is deleted, this key will be (re)set to
     * {@link BuiltinStyle#DEFAULT_ID}
     */
    public static final Domain DOM_FK_STYLE;

    /**
     * Foreign key between the list table {@link Booklist}
     * and the navigator table used in the ViewPager displaying individual books.
     */
    public static final Domain DOM_FK_BL_ROW_ID;

    /* ======================================================================================
     * Domain definitions.
     * ====================================================================================== */
    /** {@link #TBL_BOOKSHELF}. */
    public static final Domain DOM_BOOKSHELF_NAME;
    /** Virtual: build from "GROUP_CONCAT(" + TBL_BOOKSHELF.dot(KEY_BOOKSHELF) + ",', ')". */
    public static final Domain DOM_BOOKSHELF_NAME_CSV;
    /** Saved booklist adapter position of current top row. */
    public static final Domain DOM_BOOKSHELF_BL_TOP_POS;
    /** Saved booklist adapter top row offset from view top. */
    public static final Domain DOM_BOOKSHELF_BL_TOP_OFFSET;

    /** {@link #TBL_BOOKSHELF_FILTERS}. */
    public static final Domain DOM_BOOKSHELF_FILTER_NAME;
    public static final Domain DOM_BOOKSHELF_FILTER_VALUE;

    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_FAMILY_NAME;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_FAMILY_NAME_OB;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_GIVEN_NAMES;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_GIVEN_NAMES_OB;
    /** {@link #TBL_AUTHORS}. */
    public static final Domain DOM_AUTHOR_IS_COMPLETE;

    /** {@link #TBL_SERIES}. */
    public static final Domain DOM_SERIES_TITLE;
    /** {@link #TBL_SERIES}. */
    public static final Domain DOM_SERIES_TITLE_OB;
    /** {@link #TBL_SERIES}. */
    public static final Domain DOM_SERIES_IS_COMPLETE;

    /** {@link #TBL_PUBLISHERS}. */
    public static final Domain DOM_PUBLISHER_NAME;
    /** {@link #TBL_PUBLISHERS}. */
    public static final Domain DOM_PUBLISHER_NAME_OB;
    /** Virtual: build from "GROUP_CONCAT(" + TBL_PUBLISHERS.dot(KEY_PUBLISHER_NAME) + ",', ')". */
    public static final Domain DOM_PUBLISHER_NAME_CSV;


    /** {@link #TBL_BOOKS} + {@link #TBL_TOC_ENTRIES}. */
    public static final Domain DOM_TITLE;
    /**
     * 'Order By' for the title. Lowercase, and stripped of spaces etc...
     * {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}.
     */
    public static final Domain DOM_TITLE_OB;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_ISBN;
    /** {@link #TBL_BOOKS}  {@link #TBL_TOC_ENTRIES}. */
    public static final Domain DOM_DATE_FIRST_PUBLICATION;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DATE_PUBLISHED;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRINT_RUN;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRICE_LISTED;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRICE_LISTED_CURRENCY;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PAGES;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_FORMAT;
    /** {@link #TBL_BOOKS}. Meant for comics or illustrated books. */
    public static final Domain DOM_BOOK_COLOR;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_LANGUAGE;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DESCRIPTION;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_UUID;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_EDITION;
    /** {@link #TBL_BOOKS}. See {@link Book.ContentType}. */
    public static final Domain DOM_BOOK_TOC_TYPE;
    /**
     * {@link #TBL_BOOKS}.
     * String typed. We cannot rely on prices fetched from the internet to be 100% parsable.
     */
    public static final Domain DOM_BOOK_PRICE_PAID;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRICE_PAID_CURRENCY;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DATE_ACQUIRED;
    /** {@link #TBL_BOOKS} added to the collection. */
    public static final Domain DOM_ADDED__UTC;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_LAST_UPDATED__UTC;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_GENRE;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_LOCATION;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_READ;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DATE_READ_START;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_DATE_READ_END;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_SIGNED;
    /** {@link #TBL_BOOKS}. A rating goes from 1 to 5 stars, in 0.5 increments; 0 == not set. */
    public static final Domain DOM_BOOK_RATING;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_PRIVATE_NOTES;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_CONDITION;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_BOOK_CONDITION_DUST_COVER;

    /** {@link #TBL_BOOKS}. Book ID, not 'work' ID. */
    public static final Domain DOM_ESID_GOODREADS_BOOK;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_ISFDB;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_LIBRARY_THING;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_OPEN_LIBRARY;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_STRIP_INFO_BE;
    /** {@link #TBL_BOOKS}. */
    public static final Domain DOM_ESID_LAST_DODO_NL;

    /**
     * {@link #TBL_CALIBRE_LIBRARIES}.
     * The physical Calibre library ID as needed in ajax calls.
     */
    public static final Domain DOM_CALIBRE_LIBRARY_STRING_ID;

    /** {@link #TBL_CALIBRE_LIBRARIES}. */
    public static final Domain DOM_CALIBRE_LIBRARY_LAST_SYNC__UTC;

    /** {@link #TBL_CALIBRE_LIBRARIES}. */
    public static final Domain DOM_CALIBRE_LIBRARY_UUID;
    /** {@link #TBL_CALIBRE_LIBRARIES} {@link #TBL_CALIBRE_VIRTUAL_LIBRARIES}. Display name. */
    public static final Domain DOM_CALIBRE_LIBRARY_NAME;

    /** {@link #TBL_CALIBRE_VIRTUAL_LIBRARIES}. Expression or {@code null} for the physical lib. */
    public static final Domain DOM_CALIBRE_VIRT_LIB_EXPR;

    /** {@link #TBL_CALIBRE_CUSTOM_FIELDS} */
    public static final Domain DOM_CALIBRE_CUSTOM_FIELD_NAME;
    /** {@link #TBL_CALIBRE_CUSTOM_FIELDS} */
    public static final Domain DOM_CALIBRE_CUSTOM_FIELD_TYPE;
    /** {@link #TBL_CALIBRE_CUSTOM_FIELDS} */
    public static final Domain DOM_CALIBRE_CUSTOM_FIELD_MAPPING;

    /** {@link #TBL_CALIBRE_BOOKS}. */
    public static final Domain DOM_CALIBRE_BOOK_ID;
    /** {@link #TBL_CALIBRE_BOOKS}. */
    public static final Domain DOM_CALIBRE_BOOK_UUID;
    /** {@link #TBL_CALIBRE_BOOKS}. */
    public static final Domain DOM_CALIBRE_BOOK_MAIN_FORMAT;

    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_COLLECTION_ID;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_OWNED;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_WANTED;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_AMOUNT;
    /** {@link #TBL_STRIPINFO_COLLECTION}. */
    public static final Domain DOM_STRIP_INFO_BE_LAST_SYNC__UTC;

    /** {@link #TBL_BOOK_LOANEE}. */
    public static final Domain DOM_LOANEE;

    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final Domain DOM_BOOK_AUTHOR_TYPE_BITMASK;
    /** {@link #TBL_BOOK_AUTHOR}. */
    public static final Domain DOM_BOOK_AUTHOR_POSITION;


    /** {@link #TBL_BOOK_TOC_ENTRIES}. */
    public static final Domain DOM_BOOK_TOC_ENTRY_POSITION;

    /** {@link #TBL_BOOKLIST_STYLES}. */
    public static final Domain DOM_STYLE_NAME;
    public static final Domain DOM_STYLE_IS_BUILTIN;
    public static final Domain DOM_STYLE_IS_PREFERRED;
    public static final Domain DOM_STYLE_MENU_POSITION;
    public static final Domain DOM_STYLE_EXP_LEVEL;
    public static final Domain DOM_STYLE_ROW_USES_PREF_HEIGHT;
    public static final Domain DOM_STYLE_AUTHOR_SORT_BY_GIVEN_NAME;
    public static final Domain DOM_STYLE_AUTHOR_SHOW_BY_GIVEN_NAME;
    public static final Domain DOM_STYLE_TEXT_SCALE;
    public static final Domain DOM_STYLE_COVER_SCALE;
    public static final Domain DOM_STYLE_LIST_HEADER;
    public static final Domain DOM_STYLE_DETAILS_SHOW_FIELDS;
    public static final Domain DOM_STYLE_LIST_SHOW_FIELDS;

    public static final Domain DOM_STYLE_GROUPS;
    public static final Domain DOM_STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH;
    public static final Domain DOM_STYLE_GROUPS_AUTHOR_PRIMARY_TYPE;
    public static final Domain DOM_STYLE_GROUPS_SERIES_SHOW_UNDER_EACH;
    public static final Domain DOM_STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH;
    public static final Domain DOM_STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH;



    /** {@link #TBL_BOOK_SERIES}. */
    public static final Domain DOM_BOOK_NUM_IN_SERIES;
    /**
     * {@link #TBL_BOOK_SERIES}.
     * The Series position is the order the Series show up in a book.
     * Particularly important for "primary series"
     * and in lists where 'all' Series are shown.
     */
    public static final Domain DOM_BOOK_SERIES_POSITION;

    /**
     * {@link #TBL_BOOK_PUBLISHER}.
     * The Publisher position is the order the Publishers show up in a book.
     * Particularly important for "primary Publisher"
     * and in lists where 'all' Publisher are shown.
     */
    public static final Domain DOM_BOOK_PUBLISHER_POSITION;

    /** {@link #TBL_BOOKLIST_STYLES} java.util.UUID value stored as a string. */
    public static final Domain DOM_STYLE_UUID;


    /* ======================================================================================
     *  {@link Booklist} domains.
     * ====================================================================================== */


    /**
     * Series number, cast()'d for sorting purposes in {@link Booklist}
     * so we can sort it numerically regardless of content.
     */
    public static final Domain DOM_BL_BOOK_NUM_IN_SERIES_AS_FLOAT;

    /** {@link Booklist}. */
    public static final Domain DOM_BL_PRIMARY_SERIES_COUNT;

    /**
     * {@link #TBL_BOOK_LIST_NODE_STATE}
     * {@link Booklist}.
     * <p>
     * Expression from the original tables that represent the hierarchical key for the node.
     * Stored in each row and used to determine the expand/collapse results.
     */
    public static final Domain DOM_BL_NODE_KEY;
    /** {@link #TBL_BOOK_LIST_NODE_STATE} {@link Booklist}. */
    public static final Domain DOM_BL_NODE_GROUP;
    /** {@link Booklist}. */
    public static final Domain DOM_BL_NODE_LEVEL;

    /**
     * {@link Booklist}.
     * An expanded node, should always be visible!
     */
    public static final Domain DOM_BL_NODE_EXPANDED;
    /** {@link Booklist}. */
    public static final Domain DOM_BL_NODE_VISIBLE;

    /**
     * reminder: no need for a type nor constraints: https://sqlite.org/fts3.html
     */
    public static final TableDefinition TBL_FTS_BOOKS;

    public static final String ON_DELETE_CASCADE_ON_UPDATE_CASCADE =
            "ON DELETE CASCADE ON UPDATE CASCADE";

    /**
     * {@link #TBL_FTS_BOOKS}
     * specific formatted list; example: "stephen baxter;arthur c. clarke;"
     */
    static final Domain DOM_FTS_AUTHOR_NAME;
    static final Domain DOM_FTS_TOC_ENTRY_TITLE;

    static {
        /* ======================================================================================
         *  Table definitions
         * ====================================================================================== */

        // never change the "authors" "a" alias. It's hardcoded elsewhere.
        TBL_AUTHORS = new TableDefinition("authors").setAlias("a");
        // never change the "books" "b" alias. It's hardcoded elsewhere.
        TBL_BOOKS = new TableDefinition("books").setAlias("b");
        // never change the "series" "s" alias. It's hardcoded elsewhere.
        TBL_SERIES = new TableDefinition("series").setAlias("s");
        // never change the "publishers" "p" alias. It's hardcoded elsewhere.
        TBL_PUBLISHERS = new TableDefinition("publishers").setAlias("p");

        TBL_BOOKSHELF = new TableDefinition("bookshelf").setAlias("bsh");
        TBL_BOOKSHELF_FILTERS = new TableDefinition("bookshelf_filters").setAlias("bshf");

        TBL_TOC_ENTRIES = new TableDefinition("anthology").setAlias("an");

        TBL_BOOK_BOOKSHELF = new TableDefinition("book_bookshelf").setAlias("bbsh");
        TBL_BOOK_AUTHOR = new TableDefinition("book_author").setAlias("ba");
        TBL_BOOK_SERIES = new TableDefinition("book_series").setAlias("bs");
        TBL_BOOK_PUBLISHER = new TableDefinition("book_publisher").setAlias("bp");
        TBL_BOOK_LOANEE = new TableDefinition("loan").setAlias("l");
        TBL_BOOK_TOC_ENTRIES = new TableDefinition("book_anthology").setAlias("bat");

        TBL_CALIBRE_LIBRARIES = new TableDefinition("calibre_lib")
                .setAlias("clb_l");
        TBL_CALIBRE_VIRTUAL_LIBRARIES = new TableDefinition("calibre_vlib")
                .setAlias("clb_vl");
        TBL_CALIBRE_CUSTOM_FIELDS = new TableDefinition("calibre_custom_fields")
                .setAlias("clb_cf");
        TBL_CALIBRE_BOOKS = new TableDefinition("calibre_books")
                .setAlias("clb_b");

        TBL_BOOKLIST_STYLES = new TableDefinition("book_list_styles")
                .setAlias("bls");

        TBL_BOOK_LIST_NODE_STATE = new TableDefinition("book_list_node_settings")
                .setAlias("bl_ns");

        TBL_STRIPINFO_COLLECTION = new TableDefinition("stripinfo_collection")
                .setAlias("si_c");

        /* ======================================================================================
         *  Primary and Foreign Key definitions
         * ====================================================================================== */

        DOM_PK_ID = new Domain.Builder(DBKey.PK_ID, ColumnInfo.TYPE_INTEGER)
                .primaryKey()
                .build();

        DOM_FK_AUTHOR =
                new Domain.Builder(DBKey.FK_AUTHOR, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_AUTHORS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_BOOKSHELF =
                new Domain.Builder(DBKey.FK_BOOKSHELF, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_BOOKSHELF, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_BOOK =
                new Domain.Builder(DBKey.FK_BOOK, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_BOOKS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_SERIES =
                new Domain.Builder(DBKey.FK_SERIES, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_SERIES, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_PUBLISHER =
                new Domain.Builder(DBKey.FK_PUBLISHER, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_PUBLISHERS, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_TOC_ENTRY =
                new Domain.Builder(DBKey.FK_TOC_ENTRY, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_TOC_ENTRIES, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_CALIBRE_LIBRARY =
                new Domain.Builder(DBKey.FK_CALIBRE_LIBRARY, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .references(TBL_CALIBRE_LIBRARIES, ON_DELETE_CASCADE_ON_UPDATE_CASCADE)
                        .build();
        DOM_FK_STYLE =
                new Domain.Builder(DBKey.FK_STYLE, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(BuiltinStyle.DEFAULT_ID)
                        .references(TBL_BOOKLIST_STYLES, "ON DELETE SET DEFAULT ON UPDATE CASCADE")
                        .build();

        /* ======================================================================================
         *  Multi table domains
         * ====================================================================================== */

        DOM_TITLE =
                new Domain.Builder(DBKey.TITLE, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .localized()
                        .build();

        DOM_TITLE_OB =
                new Domain.Builder(DBKey.KEY_TITLE_OB, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .prePreparedOrderBy()
                        .build();

        DOM_DATE_FIRST_PUBLICATION =
                new Domain.Builder(DBKey.FIRST_PUBLICATION__DATE, ColumnInfo.TYPE_DATE)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_LAST_UPDATED__UTC =
                new Domain.Builder(DBKey.DATE_LAST_UPDATED__UTC, ColumnInfo.TYPE_DATETIME)
                        .notNull()
                        .withDefaultCurrentTimeStamp()
                        .build();

        /* ======================================================================================
         *  Bookshelf domains
         * ====================================================================================== */

        DOM_BOOKSHELF_NAME =
                new Domain.Builder(DBKey.BOOKSHELF_NAME, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();

        DOM_BOOKSHELF_NAME_CSV =
                new Domain.Builder(DBKey.KEY_BOOKSHELF_NAME_CSV, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();

        DOM_BOOKSHELF_BL_TOP_POS =
                new Domain.Builder(DBKey.BOOKSHELF_BL_TOP_POS, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        // RecyclerView.NO_POSITION == -1
                        .withDefault(-1)
                        .build();

        DOM_BOOKSHELF_BL_TOP_OFFSET =
                new Domain.Builder(DBKey.BOOKSHELF_BL_TOP_OFFSET, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(0)
                        .build();

        DOM_BOOKSHELF_FILTER_NAME =
                new Domain.Builder(DBKey.FILTER_DBKEY, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();
        DOM_BOOKSHELF_FILTER_VALUE =
                new Domain.Builder(DBKey.FILTER_VALUE, ColumnInfo.TYPE_TEXT)
                        .build();

        /* ======================================================================================
         *  Author domains
         * ====================================================================================== */

        DOM_AUTHOR_FAMILY_NAME =
                new Domain.Builder(DBKey.AUTHOR_FAMILY_NAME, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .localized()
                        .build();

        DOM_AUTHOR_FAMILY_NAME_OB =
                new Domain.Builder(DBKey.KEY_AUTHOR_FAMILY_NAME_OB, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .prePreparedOrderBy()
                        .build();

        DOM_AUTHOR_GIVEN_NAMES =
                new Domain.Builder(DBKey.AUTHOR_GIVEN_NAMES, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_AUTHOR_GIVEN_NAMES_OB =
                new Domain.Builder(DBKey.KEY_AUTHOR_GIVEN_NAMES_OB, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .prePreparedOrderBy()
                        .build();

        DOM_AUTHOR_IS_COMPLETE =
                new Domain.Builder(DBKey.AUTHOR_IS_COMPLETE, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();

        /* ======================================================================================
         *  Series domains
         * ====================================================================================== */

        DOM_SERIES_TITLE =
                new Domain.Builder(DBKey.SERIES_TITLE, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .localized()
                        .build();

        DOM_SERIES_TITLE_OB =
                new Domain.Builder(DBKey.KEY_SERIES_TITLE_OB, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .prePreparedOrderBy()
                        .build();

        DOM_SERIES_IS_COMPLETE =
                new Domain.Builder(DBKey.SERIES_IS_COMPLETE, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();

        /* ======================================================================================
         *  Publisher domains
         * ====================================================================================== */
        DOM_PUBLISHER_NAME =
                new Domain.Builder(DBKey.PUBLISHER_NAME, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .localized()
                        .build();

        DOM_PUBLISHER_NAME_OB =
                new Domain.Builder(DBKey.KEY_PUBLISHER_NAME_OB, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .prePreparedOrderBy()
                        .build();

        DOM_PUBLISHER_NAME_CSV =
                new Domain.Builder(DBKey.KEY_PUBLISHER_NAME_CSV, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();
        /* ======================================================================================
         *  Book domains
         * ====================================================================================== */

        DOM_BOOK_ISBN =
                new Domain.Builder(DBKey.BOOK_ISBN, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_DATE_PUBLISHED =
                new Domain.Builder(DBKey.BOOK_PUBLICATION__DATE, ColumnInfo.TYPE_DATE)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PRINT_RUN =
                new Domain.Builder(DBKey.PRINT_RUN, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PRICE_LISTED =
                new Domain.Builder(DBKey.PRICE_LISTED, ColumnInfo.TYPE_REAL)
                        .notNull()
                        .withDefault(0d)
                        .build();

        DOM_BOOK_PRICE_LISTED_CURRENCY =
                new Domain.Builder(DBKey.PRICE_LISTED_CURRENCY, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PAGES =
                new Domain.Builder(DBKey.PAGE_COUNT, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_FORMAT =
                new Domain.Builder(DBKey.FORMAT, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_COLOR =
                new Domain.Builder(DBKey.COLOR, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_LANGUAGE =
                new Domain.Builder(DBKey.LANGUAGE, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_GENRE =
                new Domain.Builder(DBKey.GENRE, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_DESCRIPTION =
                new Domain.Builder(DBKey.DESCRIPTION, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_TOC_TYPE =
                new Domain.Builder(DBKey.TOC_TYPE__BITMASK, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(Book.ContentType.Book.value)
                        .build();

        /* ======================================================================================
         *  Book personal data domains
         * ====================================================================================== */

        DOM_BOOK_UUID =
                new Domain.Builder(DBKey.BOOK_UUID, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefault("(lower(hex(randomblob(16))))")
                        .build();

        DOM_BOOK_EDITION =
                new Domain.Builder(DBKey.EDITION__BITMASK, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(0)
                        .build();

        DOM_BOOK_DATE_ACQUIRED =
                new Domain.Builder(DBKey.DATE_ACQUIRED, ColumnInfo.TYPE_DATE)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_PRICE_PAID =
                new Domain.Builder(DBKey.PRICE_PAID, ColumnInfo.TYPE_REAL)
                        .notNull()
                        .withDefault(0d)
                        .build();

        DOM_BOOK_PRICE_PAID_CURRENCY =
                new Domain.Builder(DBKey.PRICE_PAID_CURRENCY, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_ADDED__UTC =
                new Domain.Builder(DBKey.DATE_ADDED__UTC, ColumnInfo.TYPE_DATETIME)
                        .notNull()
                        .withDefaultCurrentTimeStamp()
                        .build();

        DOM_BOOK_LOCATION =
                new Domain.Builder(DBKey.LOCATION, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_BOOK_READ =
                new Domain.Builder(DBKey.READ__BOOL, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();
        DOM_BOOK_DATE_READ_START =
                new Domain.Builder(DBKey.READ_START__DATE, ColumnInfo.TYPE_DATE)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();
        DOM_BOOK_DATE_READ_END =
                new Domain.Builder(DBKey.READ_END__DATE, ColumnInfo.TYPE_DATE)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();
        DOM_BOOK_SIGNED =
                new Domain.Builder(DBKey.SIGNED__BOOL, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();
        DOM_BOOK_RATING =
                new Domain.Builder(DBKey.RATING, ColumnInfo.TYPE_REAL)
                        .notNull()
                        .withDefault(0)
                        .build();
        DOM_BOOK_PRIVATE_NOTES =
                new Domain.Builder(DBKey.PERSONAL_NOTES, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_BOOK_CONDITION =
                new Domain.Builder(DBKey.BOOK_CONDITION, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(0)
                        .build();
        DOM_BOOK_CONDITION_DUST_COVER =
                new Domain.Builder(DBKey.BOOK_CONDITION_COVER, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(0)
                        .build();

        /* ======================================================================================
         *  Book external website id domains
         * ====================================================================================== */
        //NEWTHINGS: adding a new search engine: optional: add external id domain
        DOM_ESID_GOODREADS_BOOK =
                new Domain.Builder(DBKey.SID_GOODREADS_BOOK, ColumnInfo.TYPE_INTEGER)
                        .build();

        DOM_ESID_ISFDB =
                new Domain.Builder(DBKey.SID_ISFDB, ColumnInfo.TYPE_INTEGER)
                        .build();

        DOM_ESID_LIBRARY_THING =
                new Domain.Builder(DBKey.SID_LIBRARY_THING, ColumnInfo.TYPE_INTEGER)
                        .build();

        DOM_ESID_OPEN_LIBRARY =
                new Domain.Builder(DBKey.SID_OPEN_LIBRARY, ColumnInfo.TYPE_TEXT)
                        .build();

        DOM_ESID_STRIP_INFO_BE =
                new Domain.Builder(DBKey.SID_STRIP_INFO, ColumnInfo.TYPE_INTEGER)
                        .build();

        DOM_ESID_LAST_DODO_NL =
                new Domain.Builder(DBKey.SID_LAST_DODO_NL, ColumnInfo.TYPE_INTEGER)
                        .build();

        //NEWTHINGS: adding a new search engine: optional: add specific/extra domains.

        /* ======================================================================================
         *  StripInfo.be synchronization domains
         * ====================================================================================== */
        DOM_STRIP_INFO_BE_COLLECTION_ID =
                new Domain.Builder(DBKey.STRIP_INFO_COLL_ID, ColumnInfo.TYPE_INTEGER)
                        .build();

        DOM_STRIP_INFO_BE_OWNED =
                new Domain.Builder(DBKey.STRIP_INFO_OWNED, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STRIP_INFO_BE_WANTED =
                new Domain.Builder(DBKey.STRIP_INFO_WANTED, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STRIP_INFO_BE_AMOUNT =
                new Domain.Builder(DBKey.STRIP_INFO_AMOUNT, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(0)
                        .build();

        DOM_STRIP_INFO_BE_LAST_SYNC__UTC =
                new Domain.Builder(DBKey.STRIP_INFO_LAST_SYNC_DATE__UTC, ColumnInfo.TYPE_DATETIME)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        /* ======================================================================================
         *  Calibre bridge table domains
         * ====================================================================================== */
        DOM_CALIBRE_BOOK_UUID =
                new Domain.Builder(DBKey.CALIBRE_BOOK_UUID, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();

        DOM_CALIBRE_BOOK_ID =
                new Domain.Builder(DBKey.CALIBRE_BOOK_ID, ColumnInfo.TYPE_INTEGER)
                        .build();

        DOM_CALIBRE_BOOK_MAIN_FORMAT =
                new Domain.Builder(DBKey.CALIBRE_BOOK_MAIN_FORMAT, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_CALIBRE_CUSTOM_FIELD_NAME =
                new Domain.Builder(DBKey.CALIBRE_CUSTOM_FIELD_NAME, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();

        DOM_CALIBRE_CUSTOM_FIELD_TYPE =
                new Domain.Builder(DBKey.CALIBRE_CUSTOM_FIELD_TYPE, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();

        DOM_CALIBRE_CUSTOM_FIELD_MAPPING =
                new Domain.Builder(DBKey.CALIBRE_CUSTOM_FIELD_MAPPING, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .build();

        DOM_CALIBRE_LIBRARY_LAST_SYNC__UTC =
                new Domain.Builder(DBKey.CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC,
                                   ColumnInfo.TYPE_DATETIME)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_CALIBRE_LIBRARY_STRING_ID =
                new Domain.Builder(DBKey.CALIBRE_LIBRARY_STRING_ID, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        // can be empty when our Calibre extension is not installed
        DOM_CALIBRE_LIBRARY_UUID =
                new Domain.Builder(DBKey.CALIBRE_LIBRARY_UUID, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        DOM_CALIBRE_LIBRARY_NAME =
                new Domain.Builder(DBKey.CALIBRE_LIBRARY_NAME, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .localized()
                        .build();

        // not sure if we should allow empty?
        DOM_CALIBRE_VIRT_LIB_EXPR =
                new Domain.Builder(DBKey.CALIBRE_VIRT_LIB_EXPR, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        /* ======================================================================================
         *  Loanee domains
         * ====================================================================================== */

        DOM_LOANEE =
                new Domain.Builder(DBKey.LOANEE_NAME, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .localized()
                        .build();

        /* ======================================================================================
         *  Link table domains
         * ====================================================================================== */

        DOM_BOOK_AUTHOR_TYPE_BITMASK =
                new Domain.Builder(DBKey.AUTHOR_TYPE__BITMASK, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(0)
                        .build();

        DOM_BOOK_AUTHOR_POSITION =
                new Domain.Builder(DBKey.BOOK_AUTHOR_POSITION, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .build();

        DOM_BOOK_SERIES_POSITION =
                new Domain.Builder(DBKey.BOOK_SERIES_POSITION, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .build();

        DOM_BOOK_NUM_IN_SERIES =
                new Domain.Builder(DBKey.SERIES_BOOK_NUMBER, ColumnInfo.TYPE_TEXT)
                        .localized()
                        .build();

        DOM_BOOK_PUBLISHER_POSITION =
                new Domain.Builder(DBKey.BOOK_PUBLISHER_POSITION, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .build();

        DOM_BOOK_TOC_ENTRY_POSITION =
                new Domain.Builder(DBKey.KEY_BOOK_TOC_ENTRY_POSITION, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .build();

        /* ======================================================================================
         *  Style domains
         * ====================================================================================== */

        DOM_STYLE_UUID =
                new Domain.Builder(DBKey.STYLE_UUID, ColumnInfo.TYPE_TEXT)
                        .notNull()
                        .withDefaultEmptyString()
                        .build();

        DOM_STYLE_IS_BUILTIN =
                new Domain.Builder(DBKey.STYLE_IS_BUILTIN, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_IS_PREFERRED =
                new Domain.Builder(DBKey.STYLE_IS_PREFERRED, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_MENU_POSITION =
                new Domain.Builder(DBKey.STYLE_MENU_POSITION, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(Style.MENU_POSITION_NOT_PREFERRED)
                        .build();


        DOM_STYLE_NAME =
                new Domain.Builder(DBKey.STYLE_NAME, ColumnInfo.TYPE_TEXT)
                        .build();

        DOM_STYLE_GROUPS =
                new Domain.Builder(DBKey.STYLE_GROUPS, ColumnInfo.TYPE_TEXT)
                        .build();

        DOM_STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH,
                                   ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_GROUPS_AUTHOR_PRIMARY_TYPE =
                new Domain.Builder(DBKey.STYLE_GROUPS_AUTHOR_PRIMARY_TYPE,
                                   ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(Author.TYPE_UNKNOWN)
                        .build();

        DOM_STYLE_GROUPS_SERIES_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE_GROUPS_SERIES_SHOW_UNDER_EACH,
                                   ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH,
                                   ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH =
                new Domain.Builder(DBKey.STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH,
                                   ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();


        DOM_STYLE_EXP_LEVEL =
                new Domain.Builder(DBKey.STYLE_EXP_LEVEL, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(1)
                        .build();

        DOM_STYLE_ROW_USES_PREF_HEIGHT =
                new Domain.Builder(DBKey.STYLE_ROW_USES_PREF_HEIGHT, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(true)
                        .build();

        DOM_STYLE_AUTHOR_SORT_BY_GIVEN_NAME =
                new Domain.Builder(DBKey.STYLE_AUTHOR_SORT_BY_GIVEN_NAME, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();
        DOM_STYLE_AUTHOR_SHOW_BY_GIVEN_NAME =
                new Domain.Builder(DBKey.STYLE_AUTHOR_SHOW_BY_GIVEN_NAME, ColumnInfo.TYPE_BOOLEAN)
                        .notNull()
                        .withDefault(false)
                        .build();

        DOM_STYLE_TEXT_SCALE =
                new Domain.Builder(DBKey.STYLE_TEXT_SCALE, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(Style.DEFAULT_TEXT_SCALE)
                        .build();
        DOM_STYLE_COVER_SCALE =
                new Domain.Builder(DBKey.STYLE_COVER_SCALE, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(Style.DEFAULT_COVER_SCALE)
                        .build();

        DOM_STYLE_LIST_HEADER =
                new Domain.Builder(DBKey.STYLE_LIST_HEADER, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(BooklistHeader.BITMASK_ALL)
                        .build();

        DOM_STYLE_DETAILS_SHOW_FIELDS =
                new Domain.Builder(DBKey.STYLE_DETAILS_SHOW_FIELDS, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(BookDetailsFieldVisibility.DEFAULT)
                        .build();

        DOM_STYLE_LIST_SHOW_FIELDS =
                new Domain.Builder(DBKey.STYLE_LIST_SHOW_FIELDS, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .withDefault(BooklistFieldVisibility.DEFAULT)
                        .build();

        /* ======================================================================================
         *  Booklist domains
         * ====================================================================================== */

        DOM_BL_BOOK_NUM_IN_SERIES_AS_FLOAT =
                new Domain.Builder(DBKey.KEY_BL_SERIES_NUM_FLOAT, ColumnInfo.TYPE_REAL)
                        .build();

        DOM_BL_PRIMARY_SERIES_COUNT =
                new Domain.Builder(DBKey.KEY_BL_PRIMARY_SERIES_COUNT, ColumnInfo.TYPE_INTEGER)
                        .build();

        DOM_BL_NODE_KEY =
                new Domain.Builder(DBKey.KEY_BL_NODE_KEY, ColumnInfo.TYPE_TEXT)
                        .build();

        DOM_BL_NODE_GROUP =
                new Domain.Builder(DBKey.KEY_BL_NODE_GROUP, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .build();

        DOM_BL_NODE_LEVEL =
                new Domain.Builder(DBKey.KEY_BL_NODE_LEVEL, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .build();

        DOM_BL_NODE_VISIBLE =
                new Domain.Builder(DBKey.KEY_BL_NODE_VISIBLE, ColumnInfo.TYPE_INTEGER)
                        .withDefault(0)
                        .build();

        DOM_BL_NODE_EXPANDED =
                new Domain.Builder(DBKey.KEY_BL_NODE_EXPANDED, ColumnInfo.TYPE_INTEGER)
                        .withDefault(0)
                        .build();

        DOM_FK_BL_ROW_ID =
                new Domain.Builder(DBKey.FK_BL_ROW_ID, ColumnInfo.TYPE_INTEGER)
                        .notNull()
                        .build();

        /* ======================================================================================
         *  app tables
         * ====================================================================================== */

        TBL_BOOKLIST_STYLES
                .addDomains(DOM_PK_ID,
                            DOM_STYLE_IS_BUILTIN,
                            DOM_STYLE_IS_PREFERRED,
                            DOM_STYLE_MENU_POSITION,
                            DOM_STYLE_UUID,
                            DOM_STYLE_NAME,

                            DOM_STYLE_GROUPS,
                            DOM_STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH,
                            DOM_STYLE_GROUPS_AUTHOR_PRIMARY_TYPE,
                            DOM_STYLE_GROUPS_SERIES_SHOW_UNDER_EACH,
                            DOM_STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH,
                            DOM_STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH,

                            DOM_STYLE_EXP_LEVEL,
                            DOM_STYLE_ROW_USES_PREF_HEIGHT,
                            DOM_STYLE_AUTHOR_SORT_BY_GIVEN_NAME,
                            DOM_STYLE_AUTHOR_SHOW_BY_GIVEN_NAME,
                            DOM_STYLE_TEXT_SCALE,
                            DOM_STYLE_COVER_SCALE,
                            DOM_STYLE_LIST_HEADER,
                            DOM_STYLE_DETAILS_SHOW_FIELDS,
                            DOM_STYLE_LIST_SHOW_FIELDS)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex(DBKey.STYLE_UUID, true, DOM_STYLE_UUID)
                .addIndex(DBKey.STYLE_NAME, true, DOM_STYLE_NAME)
                .addIndex(DBKey.STYLE_MENU_POSITION, false, DOM_STYLE_MENU_POSITION);
        ALL_TABLES.put(TBL_BOOKLIST_STYLES.getName(), TBL_BOOKLIST_STYLES);

        /* ======================================================================================
         *  basic user data tables
         * ====================================================================================== */

        TBL_BOOKSHELF
                .addDomains(DOM_PK_ID,
                            DOM_FK_STYLE,
                            DOM_BOOKSHELF_NAME,
                            DOM_BOOKSHELF_BL_TOP_POS,
                            DOM_BOOKSHELF_BL_TOP_OFFSET)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_BOOKLIST_STYLES, DOM_FK_STYLE)
                .addIndex(DBKey.BOOKSHELF_NAME, true, DOM_BOOKSHELF_NAME);
        ALL_TABLES.put(TBL_BOOKSHELF.getName(), TBL_BOOKSHELF);

        TBL_BOOKSHELF_FILTERS
                .addDomains(DOM_FK_BOOKSHELF,
                            DOM_BOOKSHELF_FILTER_NAME,
                            DOM_BOOKSHELF_FILTER_VALUE)
                .setPrimaryKey(DOM_FK_BOOKSHELF, DOM_BOOKSHELF_FILTER_NAME)
                .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF);
        ALL_TABLES.put(TBL_BOOKSHELF_FILTERS.getName(), TBL_BOOKSHELF_FILTERS);


        TBL_AUTHORS
                .addDomains(DOM_PK_ID,
                            DOM_AUTHOR_FAMILY_NAME,
                            DOM_AUTHOR_FAMILY_NAME_OB,
                            DOM_AUTHOR_GIVEN_NAMES,
                            DOM_AUTHOR_GIVEN_NAMES_OB,
                            DOM_AUTHOR_IS_COMPLETE)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex(DBKey.KEY_AUTHOR_FAMILY_NAME_OB, false, DOM_AUTHOR_FAMILY_NAME_OB)
                .addIndex(DBKey.AUTHOR_FAMILY_NAME, false, DOM_AUTHOR_FAMILY_NAME)
                .addIndex(DBKey.KEY_AUTHOR_GIVEN_NAMES_OB, false, DOM_AUTHOR_GIVEN_NAMES_OB)
                .addIndex(DBKey.AUTHOR_GIVEN_NAMES, false, DOM_AUTHOR_GIVEN_NAMES);
        ALL_TABLES.put(TBL_AUTHORS.getName(), TBL_AUTHORS);

        TBL_SERIES
                .addDomains(DOM_PK_ID,
                            DOM_SERIES_TITLE,
                            DOM_SERIES_TITLE_OB,
                            DOM_SERIES_IS_COMPLETE)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("id", true, DOM_PK_ID)
                .addIndex(DBKey.KEY_SERIES_TITLE_OB, false, DOM_SERIES_TITLE_OB)
                .addIndex(DBKey.SERIES_TITLE, false, DOM_SERIES_TITLE);
        ALL_TABLES.put(TBL_SERIES.getName(), TBL_SERIES);

        TBL_PUBLISHERS
                .addDomains(DOM_PK_ID,
                            DOM_PUBLISHER_NAME,
                            DOM_PUBLISHER_NAME_OB)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("id", true, DOM_PK_ID)
                .addIndex(DBKey.KEY_PUBLISHER_NAME_OB, false, DOM_PUBLISHER_NAME_OB)
                .addIndex(DBKey.PUBLISHER_NAME, false, DOM_PUBLISHER_NAME);
        ALL_TABLES.put(TBL_PUBLISHERS.getName(), TBL_PUBLISHERS);

        TBL_BOOKS
                .addDomains(DOM_PK_ID,
                            // book data
                            DOM_TITLE,
                            DOM_TITLE_OB,
                            DOM_BOOK_ISBN,
                            DOM_BOOK_DATE_PUBLISHED,
                            DOM_DATE_FIRST_PUBLICATION,
                            DOM_BOOK_PRINT_RUN,

                            DOM_BOOK_PRICE_LISTED,
                            DOM_BOOK_PRICE_LISTED_CURRENCY,

                            DOM_BOOK_TOC_TYPE,
                            DOM_BOOK_FORMAT,
                            DOM_BOOK_COLOR,
                            DOM_BOOK_GENRE,
                            DOM_BOOK_LANGUAGE,
                            DOM_BOOK_PAGES,

                            DOM_BOOK_DESCRIPTION,

                            // personal data
                            DOM_BOOK_PRICE_PAID,
                            DOM_BOOK_PRICE_PAID_CURRENCY,
                            DOM_BOOK_DATE_ACQUIRED,

                            DOM_BOOK_READ,
                            DOM_BOOK_DATE_READ_START,
                            DOM_BOOK_DATE_READ_END,

                            DOM_BOOK_EDITION,
                            DOM_BOOK_SIGNED,
                            DOM_BOOK_RATING,
                            DOM_BOOK_LOCATION,
                            DOM_BOOK_PRIVATE_NOTES,
                            DOM_BOOK_CONDITION,
                            DOM_BOOK_CONDITION_DUST_COVER,

                            // external id/data
                            //NEWTHINGS: adding a new search engine: optional: add external id DOM
                            DOM_ESID_GOODREADS_BOOK,
                            DOM_ESID_ISFDB,
                            DOM_ESID_LIBRARY_THING,
                            DOM_ESID_OPEN_LIBRARY,
                            DOM_ESID_STRIP_INFO_BE,
                            DOM_ESID_LAST_DODO_NL,
                            //NEWTHINGS: adding a new search engine:
                            // optional: add engine specific DOM

                            // internal data
                            DOM_BOOK_UUID,
                            DOM_ADDED__UTC,
                            DOM_LAST_UPDATED__UTC)

                .setPrimaryKey(DOM_PK_ID)
                .addIndex(DBKey.KEY_TITLE_OB, false, DOM_TITLE_OB)
                .addIndex(DBKey.TITLE, false, DOM_TITLE)
                .addIndex(DBKey.BOOK_ISBN, false, DOM_BOOK_ISBN)
                .addIndex(DBKey.BOOK_UUID, true, DOM_BOOK_UUID)
                //NEWTHINGS: adding a new search engine: optional: add indexes as needed.

                .addIndex(DBKey.SID_GOODREADS_BOOK, false, DOM_ESID_GOODREADS_BOOK)
                .addIndex(DBKey.SID_ISFDB, false, DOM_ESID_ISFDB)
                .addIndex(DBKey.SID_OPEN_LIBRARY, false, DOM_ESID_OPEN_LIBRARY)
                .addIndex(DBKey.SID_STRIP_INFO, false, DOM_ESID_STRIP_INFO_BE)
        // we probably do not need this one (and have not created it)
        //.addIndex(KEY_ESID_LIBRARY_THING, false, DOM_ESID_LIBRARY_THING)
        ;
        ALL_TABLES.put(TBL_BOOKS.getName(), TBL_BOOKS);


        TBL_TOC_ENTRIES
                .addDomains(DOM_PK_ID,
                            DOM_FK_AUTHOR,
                            DOM_TITLE,
                            DOM_TITLE_OB,
                            DOM_DATE_FIRST_PUBLICATION)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_AUTHORS, DOM_FK_AUTHOR)
                .addIndex(DBKey.FK_AUTHOR, false, DOM_FK_AUTHOR)
                .addIndex(DBKey.KEY_TITLE_OB, false, DOM_TITLE_OB)
                .addIndex("pk", true,
                          DOM_FK_AUTHOR,
                          DOM_TITLE_OB);
        ALL_TABLES.put(TBL_TOC_ENTRIES.getName(), TBL_TOC_ENTRIES);


        /* ======================================================================================
         *  link tables
         * ====================================================================================== */

        TBL_BOOK_BOOKSHELF
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_BOOKSHELF)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_BOOKSHELF)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF)
                .addIndex(DBKey.FK_BOOK, false, DOM_FK_BOOK)
                .addIndex(DBKey.FK_BOOKSHELF, false, DOM_FK_BOOKSHELF);
        ALL_TABLES.put(TBL_BOOK_BOOKSHELF.getName(), TBL_BOOK_BOOKSHELF);


        TBL_BOOK_AUTHOR
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_AUTHOR,
                            DOM_BOOK_AUTHOR_POSITION,
                            DOM_BOOK_AUTHOR_TYPE_BITMASK)
                // enforce: only one author on a particular position for a book.
                // allow: multiple copies of that author and multiple types.
                // The latter has some restrictions handled in code.
                //FIXME: should add DOM_FK_AUTHOR to the primary key
                .setPrimaryKey(DOM_FK_BOOK, DOM_BOOK_AUTHOR_POSITION)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_AUTHORS, DOM_FK_AUTHOR)
                .addIndex(DBKey.FK_AUTHOR, true,
                          DOM_FK_AUTHOR,
                          DOM_FK_BOOK)
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_FK_AUTHOR);
        ALL_TABLES.put(TBL_BOOK_AUTHOR.getName(), TBL_BOOK_AUTHOR);


        TBL_BOOK_SERIES
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_SERIES,
                            DOM_BOOK_NUM_IN_SERIES,
                            DOM_BOOK_SERIES_POSITION)
                // enforce: only one series on a particular position for a book.
                // allow: multiple copies of that series and multiple numbers.
                // The latter has some restrictions handled in code.
                // in contract to TBL_BOOK_AUTHOR we don't want to add the DOM_FK_SERIES
                // to the primary key, as want want to allow a single book to be
                // present in a series multiple times (each time with a different number).
                .setPrimaryKey(DOM_FK_BOOK, DOM_BOOK_SERIES_POSITION)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_SERIES, DOM_FK_SERIES)
                .addIndex(DBKey.FK_SERIES, true,
                          DOM_FK_SERIES,
                          DOM_FK_BOOK,
                          DOM_BOOK_NUM_IN_SERIES)
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_FK_SERIES,
                          DOM_BOOK_NUM_IN_SERIES);
        ALL_TABLES.put(TBL_BOOK_SERIES.getName(), TBL_BOOK_SERIES);


        TBL_BOOK_PUBLISHER
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_PUBLISHER,
                            DOM_BOOK_PUBLISHER_POSITION)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_PUBLISHER, DOM_BOOK_PUBLISHER_POSITION)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_PUBLISHERS, DOM_FK_PUBLISHER)
                .addIndex(DBKey.FK_PUBLISHER, true,
                          DOM_FK_PUBLISHER,
                          DOM_FK_BOOK)
                .addIndex(DBKey.FK_BOOK, true,
                          DOM_FK_BOOK,
                          DOM_FK_PUBLISHER);
        ALL_TABLES.put(TBL_BOOK_PUBLISHER.getName(), TBL_BOOK_PUBLISHER);


        TBL_BOOK_TOC_ENTRIES
                .addDomains(DOM_FK_BOOK,
                            DOM_FK_TOC_ENTRY,
                            DOM_BOOK_TOC_ENTRY_POSITION)
                .setPrimaryKey(DOM_FK_BOOK, DOM_FK_TOC_ENTRY)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_TOC_ENTRIES, DOM_FK_TOC_ENTRY)
                .addIndex(DBKey.FK_TOC_ENTRY, false, DOM_FK_TOC_ENTRY)
                .addIndex(DBKey.FK_BOOK, false, DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_TOC_ENTRIES.getName(), TBL_BOOK_TOC_ENTRIES);


        TBL_BOOK_LOANEE
                .addDomains(DOM_PK_ID,
                            DOM_FK_BOOK,
                            DOM_LOANEE)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addIndex(DBKey.FK_BOOK, true, DOM_FK_BOOK);
        ALL_TABLES.put(TBL_BOOK_LOANEE.getName(), TBL_BOOK_LOANEE);



        TBL_CALIBRE_BOOKS
                .addDomains(DOM_FK_BOOK,
                            DOM_CALIBRE_BOOK_ID,
                            DOM_CALIBRE_BOOK_UUID,
                            DOM_CALIBRE_BOOK_MAIN_FORMAT,
                            DOM_FK_CALIBRE_LIBRARY)
                .setPrimaryKey(DOM_FK_BOOK)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                .addReference(TBL_CALIBRE_LIBRARIES, DOM_FK_CALIBRE_LIBRARY)
                // false: leave it open to have multiple calibre books (i.e. different formats)
                .addIndex(DBKey.FK_BOOK, false, DOM_FK_BOOK);
        ALL_TABLES.put(TBL_CALIBRE_BOOKS.getName(), TBL_CALIBRE_BOOKS);

        TBL_CALIBRE_LIBRARIES
                .addDomains(DOM_PK_ID,
                            DOM_FK_BOOKSHELF,
                            DOM_CALIBRE_LIBRARY_UUID,
                            DOM_CALIBRE_LIBRARY_STRING_ID,
                            DOM_CALIBRE_LIBRARY_NAME,
                            DOM_CALIBRE_LIBRARY_LAST_SYNC__UTC)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF)
                .addIndex(DBKey.CALIBRE_LIBRARY_NAME, true,
                          DOM_CALIBRE_LIBRARY_STRING_ID,
                          DOM_CALIBRE_LIBRARY_NAME);
        ALL_TABLES.put(TBL_CALIBRE_LIBRARIES.getName(), TBL_CALIBRE_LIBRARIES);

        TBL_CALIBRE_VIRTUAL_LIBRARIES
                .addDomains(DOM_PK_ID,
                            DOM_FK_BOOKSHELF,
                            DOM_FK_CALIBRE_LIBRARY,
                            DOM_CALIBRE_LIBRARY_NAME,
                            DOM_CALIBRE_VIRT_LIB_EXPR)
                .setPrimaryKey(DOM_PK_ID)
                .addReference(TBL_BOOKSHELF, DOM_FK_BOOKSHELF)
                .addReference(TBL_CALIBRE_LIBRARIES, DOM_FK_CALIBRE_LIBRARY)
                .addIndex(DBKey.CALIBRE_LIBRARY_NAME, true,
                          DOM_FK_CALIBRE_LIBRARY,
                          DOM_CALIBRE_LIBRARY_NAME);
        ALL_TABLES.put(TBL_CALIBRE_VIRTUAL_LIBRARIES.getName(), TBL_CALIBRE_VIRTUAL_LIBRARIES);

        TBL_CALIBRE_CUSTOM_FIELDS
                .addDomains(DOM_PK_ID,
                            DOM_CALIBRE_CUSTOM_FIELD_NAME,
                            DOM_CALIBRE_CUSTOM_FIELD_TYPE,
                            DOM_CALIBRE_CUSTOM_FIELD_MAPPING)
                .setPrimaryKey(DOM_PK_ID);
        ALL_TABLES.put(TBL_CALIBRE_CUSTOM_FIELDS.getName(), TBL_CALIBRE_CUSTOM_FIELDS);


        TBL_STRIPINFO_COLLECTION
                .addDomains(DOM_FK_BOOK,
                            DOM_ESID_STRIP_INFO_BE,
                            DOM_STRIP_INFO_BE_COLLECTION_ID,
                            DOM_STRIP_INFO_BE_OWNED,
                            DOM_STRIP_INFO_BE_WANTED,
                            DOM_STRIP_INFO_BE_AMOUNT,
                            DOM_STRIP_INFO_BE_LAST_SYNC__UTC)
                .setPrimaryKey(DOM_FK_BOOK)
                .addReference(TBL_BOOKS, DOM_FK_BOOK)
                // not unique: allow multiple local books to point to the same online book
                .addIndex(DBKey.SID_STRIP_INFO, false,
                          DOM_ESID_STRIP_INFO_BE);
        ALL_TABLES.put(TBL_STRIPINFO_COLLECTION.getName(),
                       TBL_STRIPINFO_COLLECTION);



        TBL_BOOK_LIST_NODE_STATE
                .addDomains(DOM_PK_ID,
                            DOM_FK_BOOKSHELF,
                            DOM_FK_STYLE,

                            DOM_BL_NODE_KEY,
                            DOM_BL_NODE_LEVEL,
                            DOM_BL_NODE_GROUP,
                            DOM_BL_NODE_EXPANDED,
                            DOM_BL_NODE_VISIBLE)
                .setPrimaryKey(DOM_PK_ID)
                .addIndex("BOOKSHELF_STYLE", false,
                          DOM_FK_BOOKSHELF,
                          DOM_FK_STYLE);
        ALL_TABLES.put(TBL_BOOK_LIST_NODE_STATE.getName(),
                       TBL_BOOK_LIST_NODE_STATE);
    }

    static {
        DOM_FTS_AUTHOR_NAME =
                new Domain.Builder(DBKey.FTS_AUTHOR_NAME, ColumnInfo.TYPE_TEXT)
                        .build();

        DOM_FTS_TOC_ENTRY_TITLE =
                new Domain.Builder(DBKey.FTS_TOC_ENTRY_TITLE, ColumnInfo.TYPE_TEXT)
                        .build();

        TBL_FTS_BOOKS = createFtsTableDefinition("books_fts");
    }

    private DBDefinitions() {
    }

    @NonNull
    public static TableDefinition createFtsTableDefinition(@NonNull final String name) {
        return new TableDefinition(name)
                .setType(TableDefinition.TableType.FTS4)
                .addDomains(DOM_TITLE,
                            DOM_FTS_AUTHOR_NAME,
                            DOM_SERIES_TITLE,
                            DOM_PUBLISHER_NAME,

                            DOM_BOOK_DESCRIPTION,
                            DOM_BOOK_PRIVATE_NOTES,
                            DOM_BOOK_GENRE,
                            DOM_BOOK_LOCATION,
                            DOM_BOOK_ISBN,

                            DOM_FTS_TOC_ENTRY_TITLE);
    }
}
