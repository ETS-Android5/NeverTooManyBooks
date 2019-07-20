/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.backup.archivebase;

import android.content.res.Resources;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ImportException;
import com.eleybourn.bookcatalogue.backup.ImportOptions;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.ProgressListener;
import com.eleybourn.bookcatalogue.backup.csv.CsvImporter;
import com.eleybourn.bookcatalogue.backup.xml.XmlImporter;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.DAO;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.settings.Prefs;
import com.eleybourn.bookcatalogue.utils.LocaleUtils;
import com.eleybourn.bookcatalogue.utils.SerializationUtils.DeserializationException;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

/**
 * Basic implementation of format-agnostic BackupReader methods using
 * only a limited set of methods from the base interface.
 *
 * @author pjw
 */
public abstract class BackupReaderAbstract
        implements BackupReader {

    /** Database access. */
    @NonNull
    private final DAO mDb;

    /** progress message. */
    private final String mProcessPreferences;
    /** progress message. */
    private final String mProcessCover;
    /** progress message. */
    private final String mProcessBooklistStyles;
    /** progress and cancellation listener. */
    private ProgressListener mProgressListener;

    /** what and how to import. */
    private ImportOptions mSettings;

    /**
     * Constructor.
     */
    protected BackupReaderAbstract() {
        mDb = new DAO();

        Resources resources = LocaleUtils.getLocalizedResources();
        mProcessPreferences = resources.getString(R.string.progress_msg_process_preferences);
        mProcessCover = resources.getString(R.string.progress_msg_process_cover);
        mProcessBooklistStyles = resources.getString(R.string.progress_msg_process_booklist_style);
    }

    /**
     * Do a full restore, sending progress to the listener.
     *
     * @throws IOException on failure
     */
    @Override
    public void restore(@NonNull final ImportOptions settings,
                        @NonNull final ProgressListener listener)
            throws IOException, ImportException {

        mSettings = settings;
        mProgressListener = listener;

        // keep track of what we read from the archive
        int entitiesRead = ImportOptions.NOTHING;

        // progress counters
        int coverCount = 0;
        int estimatedSteps = 1;

        try {
            final BackupInfo info = getInfo();
            estimatedSteps += info.getBookCount();
            if (info.hasCoverCount()) {
                // we got a count
                estimatedSteps += info.getCoverCount();
            } else {
                // We don't have a count, so take a guess...
                estimatedSteps *= 2;
            }

            mProgressListener.setMax(estimatedSteps);

            // Get first entity (this will be the entity AFTER the INFO entities)
            ReaderEntity entity = nextEntity();

            // process each entry based on type, unless we are cancelled, as in Nikita
            while (entity != null && !mProgressListener.isCancelled()) {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                    Logger.debug(this, "restore", "entity=" + entity.getName());
                }
                switch (entity.getType()) {
                    case Cover:
                        if ((mSettings.what & ImportOptions.COVERS) != 0) {
                            restoreCover(entity);
                            coverCount++;
                            // entitiesRead set when all done
                        }
                        break;

                    case Books:
                        if ((mSettings.what & ImportOptions.BOOK_CSV) != 0) {
                            // a CSV file with all book data
                            mSettings.results = restoreBooks(entity);
                            entitiesRead |= ImportOptions.BOOK_CSV;
                        }
                        break;

                    case Preferences:
                        // current format
                        if ((mSettings.what & ImportOptions.PREFERENCES) != 0) {
                            mProgressListener.onProgressStep(1, mProcessPreferences);
                            try (XmlImporter importer = new XmlImporter()) {

                                importer.doPreferences(entity, mProgressListener, App.getPrefs());
                            }
                            entitiesRead |= ImportOptions.PREFERENCES;
                        }
                        break;


                    case BooklistStyles:
                        // current format
                        if ((mSettings.what & ImportOptions.BOOK_LIST_STYLES) != 0) {
                            mProgressListener.onProgressStep(1, mProcessBooklistStyles);
                            try (XmlImporter importer = new XmlImporter()) {
                                importer.doEntity(entity, mProgressListener);
                            }
                            entitiesRead |= ImportOptions.BOOK_LIST_STYLES;
                        }
                        break;

                    case XML:
                        // skip, future extension
                        break;


                    case PreferencesPreV200:
                        // pre-v200 format
                        if ((mSettings.what & ImportOptions.PREFERENCES) != 0) {
                            mProgressListener.onProgressStep(1, mProcessPreferences);
                            // read them into the 'old' prefs. Migration is done at a later stage.
                            try (XmlImporter importer = new XmlImporter()) {
                                importer.doPreferences(entity, mProgressListener,
                                                       App.getPrefs(
                                                               Prefs.PREF_LEGACY_BOOK_CATALOGUE));
                            }
                            entitiesRead |= ImportOptions.PREFERENCES;
                        }
                        break;

                    case BooklistStylesPreV200:
                        // pre-v200 format
                        if ((mSettings.what & ImportOptions.BOOK_LIST_STYLES) != 0) {
                            restorePreV200Style(entity);
                            entitiesRead |= ImportOptions.BOOK_LIST_STYLES;
                        }
                        break;

                    case Database:
                        // don't restore from archive; we're using the CSV file
                        break;

                    case Info:
                        // skip, already handled
                        break;
                }
                entity = nextEntity();
            }
        } finally {
            // report what we actually imported
            if (coverCount > 0) {
                entitiesRead |= ImportOptions.COVERS;
                // sanity check... we would not have covers unless we had books? or would we?
                if (mSettings.results == null) {
                    mSettings.results = new Importer.Results();
                }
                mSettings.results.coversImported = coverCount;
            }
            mSettings.what = entitiesRead;

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.BACKUP) {
                Logger.debug(this, "restore", "imported covers#=" + coverCount);
            }
            try {
                close();
            } catch (@NonNull final IOException e) {
                Logger.error(this, e, "Failed to close reader");
            }
        }
    }

    /**
     * Restore the books from the export file.
     *
     * @param entity to restore
     *
     * @throws IOException on failure
     */
    private Importer.Results restoreBooks(@NonNull final ReaderEntity entity)
            throws IOException, ImportException {

        try (Importer importer = new CsvImporter(LocaleUtils.getLocalizedResources(), mSettings)) {
            return importer.doBooks(entity.getStream(), null, mProgressListener);
        }
    }

    /**
     * Restore a cover file.
     *
     * @param cover to restore
     *
     * @throws IOException on failure
     */
    private void restoreCover(@NonNull final ReaderEntity cover)
            throws IOException {
        mProgressListener.onProgressStep(1, mProcessCover);

        // see if we have this file already
        final File currentCover = StorageUtils.getRawCoverFile(cover.getName());
        final Date covDate = cover.getDateModified();

        if ((mSettings.what & ImportOptions.IMPORT_ONLY_NEW_OR_UPDATED) != 0) {
            if (currentCover.exists()) {
                Date currFileDate = new Date(currentCover.lastModified());
                if (currFileDate.compareTo(covDate) >= 0) {
                    return;
                }
            }
        }
        // save (and overwrite)
        cover.saveToDirectory(StorageUtils.getCoverStorage());
        //noinspection ResultOfMethodCallIgnored
        currentCover.setLastModified(covDate.getTime());
    }

    /**
     * Restore a serialized (pre-v200 archive) booklist style.
     *
     * @param entity to restore
     *
     * @throws IOException on failure
     */
    private void restorePreV200Style(@NonNull final ReaderEntity entity)
            throws IOException {

        mProgressListener.onProgressStep(1, mProcessBooklistStyles);
        try {
            // deserialization will take care of writing the v200+ SharedPreference file
            BooklistStyle style = entity.getSerializable();
            style.save(mDb);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.DUMP_STYLE) {
                Logger.debug(this, "restorePreV200Style", style);
            }
        } catch (@NonNull final DeserializationException e) {
            Logger.error(this, e, "Unable to restore style");
        }
    }

    /**
     * Actual reader should override and close their input.
     *
     * @throws IOException on failure
     */
    @Override
    @CallSuper
    public void close()
            throws IOException {
        mDb.close();
    }
}
