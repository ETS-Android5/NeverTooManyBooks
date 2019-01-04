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

import android.content.Context;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.backup.ImportSettings;
import com.eleybourn.bookcatalogue.backup.Importer;
import com.eleybourn.bookcatalogue.backup.csv.CsvImporter;
import com.eleybourn.bookcatalogue.backup.xml.XmlImporter;
import com.eleybourn.bookcatalogue.booklist.BooklistStyle;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Prefs;
import com.eleybourn.bookcatalogue.utils.RTE.DeserializationException;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

/**
 * Basic implementation of format-agnostic BackupReader methods using
 * only a limited set of methods from the base interface.
 *
 * @author pjw
 */
public abstract class BackupReaderAbstract
    implements BackupReader {

    @NonNull
    private final CatalogueDBAdapter mDb;

    private final String processPreferences;
    private final String processCover;
    private final String processBooklistStyles;

    protected BackupReaderAbstract() {
        Context context = BookCatalogueApp.getAppContext();
        mDb = new CatalogueDBAdapter(context);

        processPreferences = context.getString(R.string.progress_msg_process_preferences);
        processCover = context.getString(R.string.progress_msg_process_cover);
        processBooklistStyles = context.getString(R.string.progress_msg_process_booklist_style);
    }

    /**
     * Do a full restore, sending progress to the listener.
     *
     * @throws IOException on failure
     */
    @Override
    public void restore(@NonNull final ImportSettings settings,
                        @NonNull final BackupReaderListener listener
    )
        throws IOException {

        // keep track of what we read from the archive
        int entitiesRead = ImportSettings.NOTHING;

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

            listener.setMax(estimatedSteps);

            // Get first entity (this will be the entity AFTER the INFO entities)
            ReaderEntity entity = nextEntity();

            // process each entry based on type, unless we are cancelled, as in Nikita
            while (entity != null && !listener.isCancelled()) {
                if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                    Logger.info(this, "reading|entity=" + entity.getName());
                }
                switch (entity.getType()) {
                    case Cover: {
                        if ((settings.what & ImportSettings.COVERS) != 0) {
                            restoreCover(entity, settings, listener);
                            coverCount++;
                            // entitiesRead set when all done
                        }
                        break;
                    }
                    case Books: {
                        if ((settings.what & ImportSettings.BOOK_CSV) != 0) {
                            // a CSV file with all book data
                            restoreBooks(entity, settings, listener);
                            entitiesRead |= ImportSettings.BOOK_CSV;
                        }
                        break;
                    }
                    case Preferences: {
                        // current format
                        if ((settings.what & ImportSettings.PREFERENCES) != 0) {
                            listener.onProgressStep(processPreferences, 1);
                            try (XmlImporter importer = new XmlImporter()) {

                                importer.doPreferences(entity, Prefs.getPrefs());
                            }
                            entitiesRead |= ImportSettings.PREFERENCES;
                        }
                        break;
                    }

                    case BooklistStyles: {
                        // current format
                        if ((settings.what & ImportSettings.BOOK_LIST_STYLES) != 0) {
                            listener.onProgressStep(processBooklistStyles, 1);
                            try (XmlImporter importer = new XmlImporter()) {
                                importer.doEntity(entity);
                            }
                            entitiesRead |= ImportSettings.BOOK_LIST_STYLES;
                        }
                        break;
                    }
                    case XML: {
                        // skip, future extension
                        break;
                    }

                    case PreferencesPreV200: {
                        // pre-v200 format
                        if ((settings.what & ImportSettings.PREFERENCES) != 0) {
                            listener.onProgressStep(processPreferences, 1);
                            // read them into the 'old' prefs. Migration is done at a later stage.
                            try (XmlImporter importer = new XmlImporter()) {
                                importer.doPreferences(entity, Prefs.getPrefs("bookCatalogue"));
                            }
                            entitiesRead |= ImportSettings.PREFERENCES;
                        }
                        break;
                    }
                    case BooklistStylesPreV200: {
                        // pre-v200 format
                        if ((settings.what & ImportSettings.BOOK_LIST_STYLES) != 0) {
                            restorePreV200Style(entity, listener);
                            entitiesRead |= ImportSettings.BOOK_LIST_STYLES;
                        }
                        break;
                    }
                    case Database: {
                        // don't restore from archive; we're using the CSV file
                        break;
                    }

                    case Info: {
                        // skip, already handled, should in fact not be seen.
                        break;
                    }
                }
                entity = nextEntity();
            }
        } finally {
            if (coverCount > 0) {
                entitiesRead |= ImportSettings.COVERS;
            }
            // report what we actually imported
            settings.what = entitiesRead;

            if (DEBUG_SWITCHES.BACKUP && BuildConfig.DEBUG) {
                Logger.info(this, "imported covers#=" + coverCount);
            }
            try {
                close();
            } catch (IOException e) {
                Logger.error(e, "Failed to close reader");
            }
        }
    }

    /**
     * Restore the books from the export file.
     *
     * @throws IOException on failure
     */
    private void restoreBooks(@NonNull final ReaderEntity entity,
                              @NonNull final ImportSettings settings,
                              @NonNull final BackupReaderListener listener
    )
        throws IOException {
        // Listener for the 'doImport' function that just passes on the progress to our own listener
        Importer.ImportListener importListener = new Importer.ImportListener() {
            private int mLastPos = 0;

            @Override
            public void onProgress(@NonNull final String message,
                                   final int position
            ) {
                // The progress is sent periodically and has jumps, so we calculate deltas
                listener.onProgressStep(message, position - mLastPos);
                mLastPos = position;
            }

            @Override
            public boolean isCancelled() {
                return listener.isCancelled();
            }

            @Override
            public void setMax(final int max) {
                // Ignore; we know how many books there are
            }
        };

        // Now do the import
        try (CsvImporter importer = new CsvImporter(settings)) {
            importer.doImport(entity.getStream(), null, importListener);
        }
    }

    /**
     * Restore a cover file.
     *
     * @throws IOException on failure
     */
    private void restoreCover(@NonNull final ReaderEntity cover,
                              @NonNull final ImportSettings settings,
                              @NonNull final BackupReaderListener listener
    )
        throws IOException {
        listener.onProgressStep(processCover, 1);

        // see if we have this file already
        final File currentCover = StorageUtils.getRawCoverFile(cover.getName());
        final Date covDate = cover.getDateModified();

        if ((settings.what & ImportSettings.IMPORT_ONLY_NEW_OR_UPDATED) != 0) {
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
     * @throws IOException on failure
     */
    private void restorePreV200Style(@NonNull final ReaderEntity entity,
                                     @NonNull final BackupReaderListener listener)
        throws IOException {

        listener.onProgressStep(processBooklistStyles, 1);
        BooklistStyle booklistStyle = null;
        try {
            // deserialization will take care of writing the v200+ SharedPreference file
            booklistStyle = entity.getSerializable();
        } catch (DeserializationException e) {
            Logger.error(e, "Unable to restore style");
        }

        if (booklistStyle != null) {
            booklistStyle.save(mDb);
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
