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

import com.hardbacknutter.nevertoomanybooks.entities.AuthorWork;

/**
 * Keys used as domain names / Bundle keys.
 */
@SuppressWarnings("WeakerAccess")
public final class DBKey {

    /** Primary key. */
    public static final String PK_ID = "_id";
    /** Foreign key. */
    public static final String FK_BOOK = "book";
    /** Foreign key. */
    public static final String FK_AUTHOR = "author";
    /** Foreign key. */
    public static final String FK_SERIES = "series_id";
    /** Foreign key. */
    public static final String FK_PUBLISHER = "publisher_id";
    /** Foreign key. */
    public static final String FK_BOOKSHELF = "bookshelf_id";
    /** Foreign key. */
    public static final String FK_TOC_ENTRY = "anthology";
    /** Foreign key. */
    public static final String FK_STYLE = "style";
    /** Foreign key. */
    public static final String FK_BL_ROW_ID = "bl_row_id";
    /** Foreign key. */
    public static final String FK_CALIBRE_LIBRARY = "clb_lib_id";

    /*
     * ======================================================================================
     * External Site id's.
     * ======================================================================================
     */
    /** External Site id. - Long. */
    public static final String SID_GOODREADS_BOOK = "goodreads_book_id";
    /** External Site id. - Long. */
    public static final String SID_ISFDB = "isfdb_book_id";
    /** External Site id. - Long. */
    public static final String SID_LIBRARY_THING = "lt_book_id";
    /** External Site id. - String. */
    public static final String SID_OPEN_LIBRARY = "ol_book_id";
    /** External Site id. - Long. */
    public static final String SID_STRIP_INFO = "si_book_id";
    /** External Site id. - Long. */
    public static final String SID_LAST_DODO_NL = "ld_book_id";

    //NEWTHINGS: adding a new search engine: optional: add external id KEY

    /** External Site id. - String. ENHANCE: set by search engines when found; not stored yet. */
    public static final String SID_ASIN = "asin";
    /** External Site id. - String. ENHANCE: set by search engines when found; not stored yet. */
    public static final String SID_GOOGLE = "google_book_id";
    /** External Site id. - String. ENHANCE: set by search engines when found; not stored yet. */
    public static final String SID_OCLC = "oclc_book_id";
    /** External Site id. - String. ENHANCE: set by search engines when found; not stored yet. */
    public static final String SID_LCCN = "lccn_book_id";

    /**
     * {@link DBDefinitions#TBL_BOOKS}.
     * Stripinfo.be synchronization.
     * <p>
     * The "CollectieId"; a secondary id used by the website for all books flagged
     * as being in the users collection.
     */
    public static final String STRIP_INFO_COLL_ID = "si_coll_id";
    public static final String STRIP_INFO_OWNED = "si_coll_owned";
    public static final String STRIP_INFO_WANTED = "si_coll_wanted";
    public static final String STRIP_INFO_AMOUNT = "si_coll_amount";
    public static final String STRIP_INFO_LAST_SYNC_DATE__UTC = "si_coll_last_sync";

    /** {@link DBDefinitions#TBL_CALIBRE_CUSTOM_FIELDS}. */
    public static final String CALIBRE_CUSTOM_FIELD_NAME = "clb_cf_name";
    public static final String CALIBRE_CUSTOM_FIELD_TYPE = "clb_cf_type";
    public static final String CALIBRE_CUSTOM_FIELD_MAPPING = "clb_cf_mapping";

    /** {@link DBDefinitions#TBL_CALIBRE_LIBRARIES}. */
    public static final String CALIBRE_LIBRARY_LAST_SYNC_DATE__UTC = "clb_lib_last_sync";
    public static final String CALIBRE_LIBRARY_STRING_ID = "clb_lib_id";
    public static final String CALIBRE_LIBRARY_UUID = "clb_lib_uuid";
    /**
     * {@link DBDefinitions#TBL_CALIBRE_LIBRARIES}
     * {@link DBDefinitions#TBL_CALIBRE_VIRTUAL_LIBRARIES}.
     */
    public static final String CALIBRE_LIBRARY_NAME = "clb_lib_name";
    /** {@link DBDefinitions#TBL_CALIBRE_VIRTUAL_LIBRARIES}. */
    public static final String CALIBRE_VIRT_LIB_EXPR = "clb_vlib_expr";
    /**
     * External to this app, but NOT an "external Site id"
     * as it comes from a user importing their Calibre libraries.
     * {@link DBDefinitions#TBL_CALIBRE_BOOKS}.
     */
    public static final String CALIBRE_BOOK_ID = "clb_book_id";
    public static final String CALIBRE_BOOK_UUID = "clb_book_uuid";
    public static final String CALIBRE_BOOK_MAIN_FORMAT = "clb_book_main_format";


    /** {@link DBDefinitions#TBL_BOOKSHELF}. */
    public static final String BOOKSHELF_NAME = "bookshelf_name";
    public static final String BOOKSHELF_BL_TOP_POS = "bl_top_pos";
    public static final String BOOKSHELF_BL_TOP_OFFSET = "bl_top_offset";
    /** Alias. */
    public static final String KEY_BOOKSHELF_NAME_CSV = "bs_name_csv";

    /** {@link DBDefinitions#TBL_BOOKSHELF_FILTERS}. */
    public static final String FILTER_DBKEY = "filter_name";
    public static final String FILTER_VALUE = "filter_value";


    /** {@link DBDefinitions#TBL_AUTHORS} */
    public static final String AUTHOR_FAMILY_NAME = "family_name";
    public static final String AUTHOR_GIVEN_NAMES = "given_names";
    public static final String AUTHOR_IS_COMPLETE = "author_complete";
    /** Aliases for CASE expressions. */
    public static final String KEY_AUTHOR_FORMATTED = "author_formatted";
    public static final String KEY_AUTHOR_FORMATTED_GIVEN_FIRST = "author_formatted_given_first";

    /** {@link DBDefinitions#TBL_BOOK_AUTHOR} */
    public static final String AUTHOR_TYPE__BITMASK = "author_type";
    public static final String BOOK_AUTHOR_POSITION = "author_position";


    /** {@link DBDefinitions#TBL_SERIES} {@link DBDefinitions#TBL_BOOK_SERIES} */
    public static final String SERIES_TITLE = "series_name";
    public static final String SERIES_IS_COMPLETE = "series_complete";
    public static final String SERIES_BOOK_NUMBER = "series_num";
    public static final String BOOK_SERIES_POSITION = "series_position";


    /** {@link DBDefinitions#TBL_PUBLISHERS} {@link DBDefinitions#TBL_BOOK_PUBLISHER} */
    public static final String PUBLISHER_NAME = "publisher_name";
    public static final String BOOK_PUBLISHER_POSITION = "publisher_position";
    /** Alias. */
    public static final String KEY_PUBLISHER_NAME_CSV = "pub_name_csv";


    /** {@link DBDefinitions#TBL_TOC_ENTRIES}. */
    public static final String KEY_BOOK_TOC_ENTRY_POSITION = "toc_entry_position";


    /** Suffix added to a price column name to create a joined currency column. */
    public static final String SUFFIX_KEY_CURRENCY = "_currency";


    /** {@link DBDefinitions#TBL_BOOKS}. */
    public static final String DATE_ADDED__UTC = "date_added";
    public static final String DATE_LAST_UPDATED__UTC = "last_update_date";

    public static final String BOOK_UUID = "book_uuid";
    public static final String TITLE = "title";
    public static final String BOOK_ISBN = "isbn";
    public static final String FIRST_PUBLICATION__DATE = "first_publication";
    public static final String BOOK_PUBLICATION__DATE = "date_published";
    public static final String PRINT_RUN = "print_run";
    public static final String PRICE_LISTED = "list_price";
    public static final String PRICE_LISTED_CURRENCY = PRICE_LISTED + SUFFIX_KEY_CURRENCY;
    public static final String PAGE_COUNT = "pages";
    public static final String FORMAT = "format";
    public static final String COLOR = "color";
    public static final String LANGUAGE = "language";
    public static final String GENRE = "genre";
    public static final String DESCRIPTION = "description";

    public static final String EDITION__BITMASK = "edition_bm";
    public static final String TOC_TYPE__BITMASK = "anthology";


    /** {@link DBDefinitions#TBL_BOOKS} Personal data. */
    public static final String PRICE_PAID = "price_paid";
    public static final String PRICE_PAID_CURRENCY = PRICE_PAID + SUFFIX_KEY_CURRENCY;
    public static final String DATE_ACQUIRED = "date_acquired";
    public static final String LOCATION = "location";
    public static final String READ__BOOL = "read";
    public static final String READ_START__DATE = "read_start";
    public static final String READ_END__DATE = "read_end";
    public static final String SIGNED__BOOL = "signed";
    /** A rating goes from 1 to 5 stars, in 0.5 increments; 0 == not set. */
    public static final String RATING = "rating";
    public static final String PERSONAL_NOTES = "notes";
    public static final String BOOK_CONDITION = "cond_bk";
    public static final String BOOK_CONDITION_COVER = "cond_cvr";


    /** {@link DBDefinitions#TBL_BOOK_LOANEE}. */
    public static final String LOANEE_NAME = "loaned_to";

    /** {@link DBDefinitions#TBL_BOOKLIST_STYLES}. */
    public static final String STYLE_UUID = "uuid";
    public static final String STYLE_NAME = "name";
    public static final String STYLE_IS_BUILTIN = "builtin";
    public static final String STYLE_IS_PREFERRED = "preferred";
    public static final String STYLE_MENU_POSITION = "menu_order";
    public static final String STYLE_EXP_LEVEL = "exp_level";
    public static final String STYLE_ROW_USES_PREF_HEIGHT = "row_pref_height";
    public static final String STYLE_AUTHOR_SORT_BY_GIVEN_NAME = "author_sort_gn";
    public static final String STYLE_AUTHOR_SHOW_BY_GIVEN_NAME = "author_show_gn";
    public static final String STYLE_TEXT_SCALE = "text_scale";
    public static final String STYLE_COVER_SCALE = "cover_scale";
    public static final String STYLE_LIST_HEADER = "list_header";
    public static final String STYLE_DETAILS_SHOW_FIELDS = "detail_fields_vis";
    public static final String STYLE_LIST_SHOW_FIELDS = "list_fields_vis";

    public static final String STYLE_GROUPS = "groups";
    public static final String STYLE_GROUPS_AUTHOR_SHOW_UNDER_EACH =
            "groups_author_under_each";
    public static final String STYLE_GROUPS_AUTHOR_PRIMARY_TYPE =
            "groups_author_prim_type";
    public static final String STYLE_GROUPS_SERIES_SHOW_UNDER_EACH =
            "groups_series_under_each";
    public static final String STYLE_GROUPS_PUBLISHER_SHOW_UNDER_EACH =
            "groups_publisher_under_each";
    public static final String STYLE_GROUPS_BOOKSHELF_SHOW_UNDER_EACH =
            "groups_bookshelf_under_each";


    /** Alias. */
    public static final String KEY_BOOK_COUNT = "book_count";
    /** Booklist. Virtual domains. */
    public static final String KEY_BL_AUTHOR_SORT = "bl_aut_sort";
    public static final String KEY_BL_SERIES_SORT = "bl_ser_sort";
    public static final String KEY_BL_PUBLISHER_SORT = "bl_pub_sort";
    public static final String KEY_BL_BOOKSHELF_SORT = "bl_shelf_sort";
    public static final String KEY_BL_SERIES_NUM_FLOAT = "bl_ser_num_float";
    public static final String KEY_BL_PRIMARY_SERIES_COUNT = "bl_prim_ser_cnt";


    /** {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE}. */
    public static final String KEY_BL_NODE_KEY = "node_key";
    /** {@link DBDefinitions#TBL_BOOK_LIST_NODE_STATE}. */
    public static final String KEY_BL_NODE_LEVEL = "node_level";
    public static final String KEY_BL_NODE_GROUP = "node_group";
    public static final String KEY_BL_NODE_VISIBLE = "node_visible";
    public static final String KEY_BL_NODE_EXPANDED = "node_expanded";


    /**
     * Column alias.
     * <p>
     * Booklist: an alias for the rowId
     * listViewRowPosition = KEY_BL_LIST_VIEW_ROW_ID - 1.
     * <p>
     * DOM_BL_LIST_VIEW_ROW_ID =
     * new Domain.Builder(KEY_BL_LIST_VIEW_ROW_ID, ColumnInfo.TYPE_INTEGER)
     * .notNull().build();
     */
    public static final String KEY_BL_LIST_VIEW_NODE_ROW_ID = "lv_node_row_id";


    /** Column alias for {@link AuthorWork.Type} */
    public static final String KEY_TOC_TYPE = "type";

    /** FTS Primary key. */
    public static final String FTS_BOOK_ID = "docid";
    /** {@link DBDefinitions#TBL_FTS_BOOKS}. Semi-colon concatenated authors. */
    public static final String FTS_AUTHOR_NAME = "author_name";
    /** {@link DBDefinitions#TBL_FTS_BOOKS}. Semi-colon concatenated titles. */
    public static final String FTS_TOC_ENTRY_TITLE = "toc_title";


    /**
     * All money keys.
     * Used with {@code MONEY_KEYS.contains(key)} to check if a key is about money.
     */
    private static final String MONEY_KEYS = PRICE_LISTED + ',' + PRICE_PAID;


    /** Suffix added to a column name to create a specific 'order by' copy of that column. */
    private static final String SUFFIX_KEY_ORDER_BY = "_ob";
    public static final String KEY_AUTHOR_FAMILY_NAME_OB =
            AUTHOR_FAMILY_NAME + SUFFIX_KEY_ORDER_BY;
    public static final String KEY_AUTHOR_GIVEN_NAMES_OB =
            AUTHOR_GIVEN_NAMES + SUFFIX_KEY_ORDER_BY;
    public static final String KEY_SERIES_TITLE_OB = SERIES_TITLE + SUFFIX_KEY_ORDER_BY;
    public static final String KEY_PUBLISHER_NAME_OB = PUBLISHER_NAME + SUFFIX_KEY_ORDER_BY;
    public static final String KEY_TITLE_OB = TITLE + SUFFIX_KEY_ORDER_BY;

    private DBKey() {
    }

    public static boolean isMoneyKey(@NonNull final CharSequence key) {
        return MONEY_KEYS.contains(key);
    }
}
