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

package com.eleybourn.bookcatalogue.database;

import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.database.DbSync.SynchronizedCursor;
import com.eleybourn.bookcatalogue.database.DbSync.Synchronizer;

import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * DEBUG CLASS to help com.eleybourn.bookcatalogue.debug cursor leakage.
 * 
 * By using TrackedCursorFactory it is possible to use this class to analyze when and
 * where cursors are being allocated, and whether they are being deallocated in a timely
 * fashion.
 *
 * Most code is removed by BuildConfig.DEBUG for production.
 *
 * @author Philip Warner
 */
public class TrackedCursor extends SynchronizedCursor  implements Closeable{
	
	/* Static Data */
	/* =========== */

	/** Used as a collection of known cursors */
	private static final HashSet<WeakReference<TrackedCursor>> mCursors = new HashSet<>();
	/** Global counter for unique cursor IDs */
	private static Long mIdCounter = 0L;

	/* Instance Data */
	/* ============= */

	/** ID of the current cursor */
	private Long mId;
	/** We record a stack track when a cursor is created. */
	private StackTraceElement[] mStackTrace;
	/** Weak reference to this object, used in cursor collection */
	private WeakReference<TrackedCursor> mWeakRef;
	/** Already closed */
	private boolean mIsClosedFlg = false;

	/** Debug counter */
	private static Integer mInstanceCount = 0;

	public TrackedCursor(SQLiteCursorDriver driver, String editTable, SQLiteQuery query, Synchronizer sync) {
		super(driver, editTable, query, sync);

		if (BuildConfig.DEBUG) {
			synchronized(mInstanceCount) {
				mInstanceCount++;
				System.out.println("Cursor instances: " + mInstanceCount);
			}

			// Record who called us. It's only from about the 7th element that matters.
			mStackTrace = Thread.currentThread().getStackTrace();

			// Get the next ID
			synchronized(mIdCounter)
			{
				mId = ++mIdCounter;
			}
			// Save this cursor in the collection
			synchronized(mCursors) {
				mWeakRef = new WeakReference<>(this);
				mCursors.add(mWeakRef);
			}			
		}
	}

	/**
	 * Remove from collection on close.
	 */
	@Override
	public void close() {
		super.close();
		if (BuildConfig.DEBUG) {
			if (!mIsClosedFlg) {
				synchronized(mInstanceCount) {
					mInstanceCount--;
					System.out.println("Cursor instances: " + mInstanceCount);
				}
				if (mWeakRef != null)
					synchronized(mCursors) {
						mCursors.remove(mWeakRef);
						mWeakRef.clear();
						mWeakRef = null;
					}
				mIsClosedFlg = true;
			}
		}
	}

	/**
	 * Finalizer that does sanity check. Setting a break here can catch the exact moment that
	 * a cursor is deleted before being closed.
	 *
	 * Note this is not guaranteed to be called by the JVM !
	 */
	@Override
	public void finalize() {
		if (BuildConfig.DEBUG) {
			if (mWeakRef != null) {
				// This is a cursor that is being deleted before it is closed.
				// Setting a break here is sometimes useful.
				synchronized(mCursors) {
					mCursors.remove(mWeakRef);
					mWeakRef.clear();
					mWeakRef = null;
				}
			}
		}
		super.finalize();
	}
	/**
	 * Get the stack trace recorded when cursor created
	 */
    private StackTraceElement[] getStackTrace() {
		return mStackTrace;
	}
	/**
	 * Get the ID of this cursor
	 */
	private long getCursorId() {
		return mId;
	}

	/**
	 * Get the total number of cursors that have not called close(). This is subtly
	 * different from the list of open cursors because non-referenced cursors may 
	 * have been deleted and the finalizer not called.
	 */
	@SuppressWarnings("unused")
	public static long getCursorCountApproximate() {
		long count = 0;
		if (BuildConfig.DEBUG) {
			synchronized(mCursors) {
				count = mCursors.size();
			}
		}
		return count;
	}

	/**
	 * Get the total number of open cursors; verifies that existing weak refs are valid
	 * and removes from collection if not. 
	 * 
	 * Note: This is not a *cheap* operation.
	 */
    @SuppressWarnings("unused")
	public static long getCursorCount() {
		long count = 0;

		if (BuildConfig.DEBUG) {
			ArrayList<WeakReference<TrackedCursor>> list = new ArrayList<>();
			synchronized(mCursors) {
				for(WeakReference<TrackedCursor> r : mCursors) {
					TrackedCursor c = r.get();
					if (c != null) {
						count++;
					} else {
						list.add(r);
					}
				}
				for(WeakReference<TrackedCursor> r : list) {
					mCursors.remove(r);
				}
			}
		}
		return count;
	}

	/**
	 * Dump all open cursors to System.out.
	 */
	public static void dumpCursors() {
		if (BuildConfig.DEBUG) {
			for(TrackedCursor c : getCursors()) {
				System.out.println("Cursor " + c.getCursorId());
				for (StackTraceElement s : c.getStackTrace()) {
					System.out.println(s.getFileName() + "    Line " + s.getLineNumber() + " Method " + s.getMethodName());
				}
			}			
		}
	}

	/**
	 * Get a collection of open cursors at the current time.
	 */
	private static ArrayList<TrackedCursor> getCursors() {
		ArrayList<TrackedCursor> list = new ArrayList<>();
		if (BuildConfig.DEBUG) {
			synchronized(mCursors) {
				for(WeakReference<TrackedCursor> r : mCursors) {
					TrackedCursor c = r.get();
					if (c != null) {
						list.add(c);
					}
				}
			}			
		}
		return list;		
	}
}
