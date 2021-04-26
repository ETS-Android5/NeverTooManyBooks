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
package com.hardbacknutter.nevertoomanybooks.backup.zip;

import android.content.Context;
import android.net.Uri;

import androidx.test.filters.MediumTest;

import java.io.File;
import java.io.IOException;
import java.security.cert.CertificateException;

import org.junit.Before;
import org.junit.Test;

import com.hardbacknutter.nevertoomanybooks.BaseDBTest;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.TestProgressListener;
import com.hardbacknutter.nevertoomanybooks.backup.ExportException;
import com.hardbacknutter.nevertoomanybooks.backup.ExportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ExportResults;
import com.hardbacknutter.nevertoomanybooks.backup.ImportException;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.RecordType;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveEncoding;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveReader;
import com.hardbacknutter.nevertoomanybooks.backup.base.ArchiveWriter;
import com.hardbacknutter.nevertoomanybooks.backup.base.InvalidArchiveException;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.DiskFullException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@MediumTest
public class ZipArchiveWriterTest
        extends BaseDBTest {

    private static final String TAG = "ZipArchiveWriterTest";

    private long mBookInDb;
    private int mNrOfStyles;

    @Before
    public void setup()
            throws DaoWriteException {
        super.setup();
        final Context context = ServiceLocator.getLocalizedAppContext();
        mBookInDb = ServiceLocator.getInstance().getBookDao().count();
        if (mBookInDb < 10) {
            throw new IllegalStateException("need at least 10 books for testing");
        }
        mNrOfStyles = ServiceLocator.getInstance().getStyles().getStyles(context, true).size();
    }

    @Test
    public void write()
            throws ImportException, ExportException,
                   InvalidArchiveException,
                   IOException, CertificateException, DiskFullException {
        final Context context = ServiceLocator.getLocalizedAppContext();
        final File file = new File(AppDir.Log.getDir(), TAG + ".zip");
        //noinspection ResultOfMethodCallIgnored
        file.delete();

        final ExportResults exportResults;

        // Full backup except covers.
        final ExportHelper exportHelper = new ExportHelper(
                RecordType.MetaData,
                RecordType.Books,
                RecordType.Preferences,
                RecordType.Certificates,
                RecordType.Styles);
        exportHelper.setEncoding(ArchiveEncoding.Zip);
        exportHelper.setUri(Uri.fromFile(file));

        try (ArchiveWriter writer = exportHelper.createArchiveWriter(context)) {
            exportResults = writer.write(context, new TestProgressListener(TAG + ":export"));
        }
        // assume success; a failure would have thrown an exception
        exportHelper.onSuccess(context);

        assertEquals(mBookInDb, exportResults.getBookCount());
        assertEquals(0, exportResults.getCoverCount());
        assertEquals(1, exportResults.preferences);
        assertEquals(mNrOfStyles, exportResults.styles);
        assertFalse(exportResults.database);

        final long exportCount = exportResults.getBookCount();

        final ImportHelper importHelper = ImportHelper.withFile(context, Uri.fromFile(file));
        importHelper.setImportEntry(RecordType.Books, true);

        // The default, fail if the default was changed without changing this test!
        assertTrue(importHelper.isNewAndUpdatedBooks());

        final ImportResults importResults;
        try (ArchiveReader reader = importHelper.createArchiveReader(context)) {

            final ArchiveMetaData archiveMetaData = reader.readMetaData(context);
            assertNotNull(archiveMetaData);

            assertEquals(mBookInDb, archiveMetaData.getBookCount());
            assertEquals(0, archiveMetaData.getCoverCount());

            importResults = reader.read(context, new TestProgressListener(TAG + ":header"));
        }
        assertEquals(exportCount, importResults.booksProcessed);

        assertEquals(0, importResults.booksCreated);
        assertEquals(exportCount, importResults.booksUpdated);
        assertEquals(0, importResults.booksSkipped);

    }
}
