package com.eleybourn.bookcatalogue;

/**
 * Global constants
 */
public class UniqueId {
    // Codes used for startActivityForResult / onActivityResult
    public static final int ACTIVITY_CREATE_BOOK_MANUALLY = 1;
    public static final int ACTIVITY_CREATE_BOOK_ISBN = 2;
    public static final int ACTIVITY_CREATE_BOOK_SCAN = 3;
    public static final int ACTIVITY_EDIT_BOOK = 4;
    public static final int ACTIVITY_ADMIN = 5;
    public static final int ACTIVITY_HELP = 6;
    public static final int ACTIVITY_PREFERENCES = 7;
    public static final int ACTIVITY_BOOKLIST_STYLE = 8;
    public static final int ACTIVITY_BOOKLIST_STYLE_PROPERTIES = 9;
    public static final int ACTIVITY_BOOKLIST_STYLE_GROUPS = 10;
    public static final int ACTIVITY_BOOKLIST_STYLES = 11;
    public static final int ACTIVITY_GOODREADS_EXPORT_FAILURES = 12;
    public static final int ACTIVITY_ADMIN_FINISH = 13;
    public static final int ACTIVITY_SORT = 14;
    public static final int ACTIVITY_SCAN = 15;
    public static final int ACTIVITY_ABOUT = 16;
    public static final int ACTIVITY_DONATE = 17;
    public static final int ACTIVITY_BOOKSHELF = 18;
    public static final int ACTIVITY_VIEW_BOOK = 19;
    public static final int ACTIVITY_DUPLICATE_BOOK = 20;

    public static final int DIALOG_PROGRESS_DETERMINATE = 101;
    public static final int DIALOG_PROGRESS_INDETERMINATE = 102;

    /* BKEY_* and BVAL_* which ae used in more then one class should be moved here */
    public static final String BKEY_NOCOVER = "nocover";

    /* other global constants */
    public static final String GOODREADS_FILENAME_SUFFIX = "_GR";
}
