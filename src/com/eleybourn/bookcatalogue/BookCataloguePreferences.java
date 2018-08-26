package com.eleybourn.bookcatalogue;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.eleybourn.bookcatalogue.booklist.BooklistGroup;

/**
 * Class to manage application preferences rather than rely on each activity knowing how to 
 * access them.
 * 
 * @author Philip Warner
 */
public class BookCataloguePreferences {
	/** Underlying SharedPreferences */
	private final SharedPreferences m_prefs = getSharedPreferences();

	/** Name to use for global preferences; non-global should be moved to appropriate Activity code */
	public static final String PREF_START_IN_MY_BOOKS = "start_in_my_books";
	public static final String PREF_INCLUDE_CLASSIC_MY_BOOKS = "App.includeClassicView";
	public static final String PREF_DISABLE_BACKGROUND_IMAGE = "App.DisableBackgroundImage";
	public static final String PREF_USE_EXTERNAL_IMAGE_CROPPER = "App.UseExternalImageCropper";
	public static final String PREF_CROP_FRAME_WHOLE_IMAGE = "App.CropFrameWholeImage";
	/** Is book info opened in read-only mode. */
	public static final String PREF_OPEN_BOOK_READ_ONLY = "App.OpenBookReadOnly";




	public static final String PREF_BOOKLIST_STYLE = "APP.BooklistStyle";

	public static final String PREF_AUTOROTATE_CAMERA_IMAGES = "App.AutorotateCameraImages";
	/** Force list construction to compatible mode (compatible with Android 1.6) */
	public static final String PREF_BOOKLIST_GENERATION_MODE = "App.BooklistGenerationMode";
	/** Last full backup date */
	public static final String PREF_LAST_BACKUP_DATE = "Backup.LastDate";
	/** Last full backup file path */
	public static final String PREF_LAST_BACKUP_FILE = "Backup.LastFile";
	/** Preferred interface locale */
	public static final String PREF_APP_LOCALE = "App.Locale";
	/** Theme */
	public static final String PREF_APP_THEME = "App.Theme";



	/** {@link BooklistGroup} uses these in serialisation storage */
	public static final String PREF_SHOW_ALL_AUTHORS = "APP.ShowAllAuthors";
	public static final String PREF_SHOW_ALL_SERIES = "APP.ShowAllSeries";
	public static final String PREF_DISPLAY_FIRST_THEN_LAST_NAMES = "APP.DisplayFirstThenLast";

	/***********************************************************************
	 * getters preferences
	 * for now, the defaults need to manually synchronized between below and
	 * {@link OtherPreferences}
	 ***********************************************************************/

	public boolean getStartInMyBook() {
		return getBoolean(PREF_START_IN_MY_BOOKS,false);
	}

	public boolean getIncludeClassicMyBook() {
		return getBoolean(PREF_INCLUDE_CLASSIC_MY_BOOKS, true);
	}

	public boolean getDisableBackgroundImage() {
		return getBoolean(PREF_DISABLE_BACKGROUND_IMAGE, false);
	}

	public boolean getUseExternalImageCropper() {
		return getBoolean(PREF_USE_EXTERNAL_IMAGE_CROPPER, false);
	}

	public boolean getPrefCropFrameWholeImage() {
		return getBoolean(PREF_CROP_FRAME_WHOLE_IMAGE, false);
	}

	public boolean getOpenBookReadOnly() {
		return getBoolean(PREF_OPEN_BOOK_READ_ONLY, true);
	}

	public String getBookListStyle(String defaultValue) {
		return getString(PREF_BOOKLIST_STYLE, defaultValue);
	}


	/* *********************************************************************
	 * Direct type access to the preferences, until we have get/set for all
	 ***********************************************************************/

	/** Get a named boolean preference */
	public boolean getBoolean(String name, boolean defaultValue) {
		boolean result;
		try {
			result = m_prefs.getBoolean(name, defaultValue);
		} catch (Exception e) {
			result = defaultValue;
		}
		return result;
	}
	/** Set a named boolean preference */
	public void setBoolean(String name, boolean value) {
		Editor ed = this.edit();
		try {
			ed.putBoolean(name, value);
		} finally {
			ed.commit();
		}
	}
	/** Get a named string preference */
	public String getString(String name, String defaultValue) {
		String result;
		try {
			result = m_prefs.getString(name, defaultValue);
		} catch (Exception e) {
			result = defaultValue;
		}
		return result;
	}
	/** Set a named string preference */
	public void setString(String name, String value) {
		Editor ed = this.edit();
		try {
			ed.putString(name, value);
		} finally {
			ed.commit();
		}
	}
	/** Get a named string preference */
	public int getInt(String name, int defaultValue) {
		int result;
		try {
			result = m_prefs.getInt(name, defaultValue);
		} catch (Exception e) {
			result = defaultValue;
		}
		return result;
	}
	/** Set a named string preference */
	public void setInt(String name, int value) {
		Editor ed = this.edit();
		try {
			ed.putInt(name, value);
		} finally {
			ed.commit();
		}
	}
	/** Get a standard preferences editor for mass updates */
	public Editor edit() {
		return m_prefs.edit();
	}

    /** Static preference object so that we can respond to events relating to changes */
    private static SharedPreferences mPrefs = null;
    /** Get (or create) the static shared preferences */
	public static SharedPreferences getSharedPreferences() {
        if (mPrefs == null) {
            mPrefs = BookCatalogueApp.getAppContext().getSharedPreferences("bookCatalogue", BookCatalogueApp.MODE_PRIVATE);
        }
		return mPrefs;
	}
}