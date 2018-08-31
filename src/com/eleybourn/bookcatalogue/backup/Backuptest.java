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
package com.eleybourn.bookcatalogue.backup;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.backup.BackupReader.BackupReaderListener;
import com.eleybourn.bookcatalogue.backup.BackupWriter.BackupWriterListener;
import com.eleybourn.bookcatalogue.backup.tar.TarBackupContainer;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.StorageUtils;

import java.io.File;
import java.io.IOException;

/**
 * Test module. DEBUG ONLY!
 * 
 * @author pjw
 */
public class Backuptest {
	private static final String BACKUP_TAR = "backup.tar";

	public static void testBackupTar() {
		File f = StorageUtils.getFile(BACKUP_TAR);
		try {
			performBackupTar(f);
		} catch (IOException e) {
			Logger.logError(e);
		}
	}
	public static void testRestoreTar() {
		File f = StorageUtils.getFile(BACKUP_TAR);
		try {
			performRestoreTar(f);
		} catch (IOException e) {
			Logger.logError(e);
		}
	}

	private static void performBackupTar(File file) throws IOException {
		if (BuildConfig.DEBUG) {
			System.out.println("Starting " + file.getAbsolutePath());
		}
		TarBackupContainer bkp = new TarBackupContainer(file);

		BackupWriter wrt = bkp.newWriter();

		wrt.backup(new BackupWriterListener() {
			private long mMax;
			private String mMessage = "";
			private final boolean mIsCancelled = false;
			private int mPosition = 0;
			private int mTotalBooks;

			@Override
			public void setMax(int max) {
				mMax = max;
			}

			@Override
			public void step(String message, int delta) {
				if (message != null)
					mMessage = message;
				mPosition += delta;
				if (BuildConfig.DEBUG) {
					System.out.println("BKP: " + mMessage + " " + mPosition + " of " + mMax);
				}
			}

			@Override
			public boolean isCancelled() {
				return mIsCancelled;
			}

			@Override
			public void setTotalBooks(int books) {
				mTotalBooks = books;
			}

			@Override
			public int getTotalBooks() {
				return mTotalBooks;
			}}, Exporter.EXPORT_ALL, null);
		if (BuildConfig.DEBUG) {
			System.out.println("Finished " + file.getAbsolutePath() + ", size = " + file.length());
		}
	}

	private static void performRestoreTar(File file) throws IOException {
		if (BuildConfig.DEBUG) {
			System.out.println("Starting " + file.getAbsolutePath());
		}
		
		TarBackupContainer bkp = new TarBackupContainer(file);
		// Each format should provide a validator of some kind
		if (!bkp.isValid())
			throw new IOException("Not a valid backup file");
		BackupReader rdr = bkp.newReader();

		rdr.restore(new BackupReaderListener() {
			private long mMax;
			private String mMessage = "";
			private final boolean mIsCancelled = false;
			private int mPosition = 0;

			@Override
			public void setMax(int max) {
				mMax = max;
			}

			@Override
			public void step(String message, int delta) {
				if (message != null)
					mMessage = message;
				mPosition += delta;
				if (BuildConfig.DEBUG) {
					System.out.println("RST: " + mMessage + " " + mPosition + " of " + mMax);
				}
			}

			@Override
			public boolean isCancelled() {
				return mIsCancelled;
			}}, Importer.IMPORT_ALL);

		if (BuildConfig.DEBUG) {
			System.out.println("Finished " + file.getAbsolutePath() + ", size = " + file.length());
		}
	}


}
