/*
 * @copyright 2011 Philip Warner
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

 package com.eleybourn.bookcatalogue.searches;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.Author;
import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.Series;
import com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.messaging.MessageSwitch;
import com.eleybourn.bookcatalogue.searches.amazon.SearchAmazonThread;
import com.eleybourn.bookcatalogue.searches.goodreads.SearchGoodreadsThread;
import com.eleybourn.bookcatalogue.searches.googlebooks.SearchGoogleBooksThread;
import com.eleybourn.bookcatalogue.searches.librarything.SearchLibraryThingThread;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.DateUtils;
import com.eleybourn.bookcatalogue.utils.IsbnUtils;
import com.eleybourn.bookcatalogue.utils.ManagedTask;
import com.eleybourn.bookcatalogue.utils.TaskManager;
import com.eleybourn.bookcatalogue.utils.TaskManager.TaskManagerListener;
import com.eleybourn.bookcatalogue.utils.Utils;

import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;

/**
 * Class to co-ordinate multiple SearchThread objects using an existing TaskManager.
 * 
 * It uses the task manager it is passed and listens to OnTaskEndedListener messages;
 * it maintain its own internal list of tasks and as tasks it knows about end, it
 * processes the data. Once all tasks are complete, it sends a message to its
 * creator via its SearchHandler.
 * 
 * @author Philip Warner
 */
public class SearchManager implements TaskManagerListener {
	
	/** Flag indicating a search source to use */
	public static final int SEARCH_GOOGLE = 1;
	/** Flag indicating a search source to use */
	public static final int SEARCH_AMAZON = 2;
	/** Flag indicating a search source to use */
	public static final int SEARCH_LIBRARY_THING = 4;
	/** Flag indicating a search source to use */
	public static final int SEARCH_GOODREADS = 8;
	/** Mask including all search sources */
	public static final int SEARCH_ALL = SEARCH_GOOGLE | SEARCH_AMAZON | SEARCH_LIBRARY_THING | SEARCH_GOODREADS;
	
	// ENHANCE: Allow user to change the default search data priority
	private static final int[] mDefaultSearchOrder = new int[] {SEARCH_AMAZON, SEARCH_GOODREADS, SEARCH_GOOGLE, SEARCH_LIBRARY_THING};
	// ENHANCE: Allow user to change the default search data priority
	private static final int[] mDefaultReliabilityOrder = new int[] {SEARCH_GOODREADS, SEARCH_AMAZON, SEARCH_GOOGLE, SEARCH_LIBRARY_THING};

	/** Flags applicable to *current* search */
	private int mSearchFlags;

	// TaskManager for threads; may have other threads tham the ones this object creates.
	private final TaskManager mTaskManager;
	// Accumulated book data
	private Bundle mBookData = null;
	// Flag indicating searches will be non-concurrent title/author found via ASIN
	private boolean mSearchingAsin = false;
	// Flag indicating searches will be non-concurrent until an ISBN is found
	private boolean mWaitingForIsbn = false;
	// Flag indicating a task was cancelled.
	private boolean mCancelledFlg = false;
	// Original author for search
	private String mAuthor;
	// Original title for search
	private String mTitle;
	// Original ISBN for search
	private String mIsbn;
	// Indicates original ISBN is really present
	private boolean mHasIsbn;
	// Whether of not to fetch thumbnails
	private boolean mFetchThumbnail;

	/** Output from search threads */
	private Hashtable<Integer,Bundle> mSearchResults = new Hashtable<>();

	// List of threads created by *this* object.
	private final ArrayList<ManagedTask> mRunningTasks = new ArrayList<>();

//	/**
//	 * Task handler for thread management; caller MUST implement this to get
//	 * search results.
//	 * 
//	 * @author Philip Warner
//	 */
//	public interface SearchResultHandler extends ManagedTask.TaskListener {
//		void onSearchFinished(Bundle bookData, boolean cancelled);
//	}

	/**
	 * Constructor.
	 * 
	 * @param taskManager	TaskManager to use
	 * @param taskHandler	SearchHandler to send results
	 */
	public SearchManager(TaskManager taskManager, SearchListener taskHandler) {
		mTaskManager = taskManager;
		if (taskManager == null)
			throw new RuntimeException("TaskManager must be specified");
		getMessageSwitch().addListener(getSenderId(), taskHandler, false);
	}

	/**
	 * When a task has ended, see if we are finished (no more tasks running).
	 * If so, finish.
	 */
	@Override
	public void onTaskEnded(TaskManager manager, ManagedTask task) {
		int size;
        if (BuildConfig.DEBUG) {
            System.out.println(task.getClass().getSimpleName() + "(" +  + task.getId() + ") onTaskEnded starting");
        }

		// Handle the result, and optionally queue another task
		if (task instanceof SearchThread)
			handleSearchTaskFinished((SearchThread)task);

		// Remove the finished task, and terminate if no more.
		synchronized(mRunningTasks) {
			mRunningTasks.remove(task);
			size = mRunningTasks.size();
            if (BuildConfig.DEBUG) {
                for(ManagedTask t: mRunningTasks) {
                	System.out.println(t.getClass().getSimpleName() + "(" +  + t.getId() + ") still running");
                }
            }
		}
		if (size == 0) {
			// Stop listening FIRST...otherwise, if sendResults() calls a listener that starts
			// a new task, we will stop listening for the new task.
			TaskManager.getMessageSwitch().removeListener(mTaskManager.getSenderId(), this);
			if (BuildConfig.DEBUG) {
				System.out.println("SearchManager not listening(1)");
			}
			// Notify the listeners.
			sendResults();
		}
		if (BuildConfig.DEBUG) {
            System.out.println(task.getClass().getSimpleName() + "(" + +task.getId() + ") onTaskEnded Exiting");
        }
	}

	/**
	 * Other taskManager messages...we ignore them
	 */
	@Override
	public void onProgress(int count, int max, String message) { }
	@Override
	public void onToast(String message) { }
	@Override
	public void onFinished() { }


	/**
	 * Utility routine to start a task
	 * 
	 * @param thread	Task to start
	 */
	private void startOne(SearchThread thread) {
		synchronized(mRunningTasks) {
			mRunningTasks.add(thread);
			mTaskManager.addTask(thread);
			if (BuildConfig.DEBUG) {
                System.out.println(thread.getClass().getSimpleName() + "(" +  + thread.getId() + ") STARTING");
            }
		}
		thread.start();
	}

	/**
	 * Start an Amazon search
	 */
	private boolean startAmazon() {
		if (!mCancelledFlg) {
			startOne( new SearchAmazonThread(mTaskManager, mAuthor, mTitle, mIsbn, mFetchThumbnail) );
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Start a Google search
	 */
	private boolean startGoogle() {
		if (!mCancelledFlg) {
			startOne( new SearchGoogleBooksThread(mTaskManager, mAuthor, mTitle, mIsbn, mFetchThumbnail) );
			return true;
		} else {
			return false;			
		}
	}
	/**
	 * Start an Amazon search
	 */
	private boolean startLibraryThing(){
		if (!mCancelledFlg && mHasIsbn) {
			startOne( new SearchLibraryThingThread(mTaskManager, mAuthor, mTitle, mIsbn, mFetchThumbnail));
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Start an Goodreads search
	 */
	private boolean startGoodreads(){
		if (!mCancelledFlg) {
			startOne( new SearchGoodreadsThread(mTaskManager, mAuthor, mTitle, mIsbn, mFetchThumbnail));
			return true;
		} else {
			return false;			
		}
	}

	/**
	 * Start a search
	 * 
	 * @param author	Author to search for
	 * @param title		Title to search for
	 * @param isbn		ISBN to search for
	 */
	public void search(String author, String title, String isbn, boolean fetchThumbnail, int searchFlags) {
		if ( (searchFlags & SEARCH_ALL) == 0)
			throw new RuntimeException("Must specify at least one source to use");

		if (mRunningTasks.size() > 0) {
			throw new RuntimeException("Attempting to start new search while previous search running");			
		}

		// Save the flags
		mSearchFlags = searchFlags;
		if (!Utils.USE_LT) {
			mSearchFlags &= ~SEARCH_LIBRARY_THING;
		}

		// Save the input and initialize
		mBookData = new Bundle();
		mSearchResults = new Hashtable<>();

		mWaitingForIsbn = false;
		mCancelledFlg = false;

		mAuthor = author;
		mTitle = title;
		mIsbn = isbn;
		mHasIsbn = mIsbn != null && mIsbn.trim().length() > 0 && IsbnUtils.isValid(mIsbn);

		mFetchThumbnail = fetchThumbnail;

		// XXXX: Not entirely sure why this code was targetted at the UI thread.
		doSearch();
		//if (mTaskManager.runningInUiThread()) {
		//	doSearch();
		//} else {
		//	mTaskManager.postToUiThread(new Runnable() {
		//		@Override
		//		public void run() {
		//			doSearch();
		//		}});
		//}
	}

	private void doSearch() {
		// List for task ends
		TaskManager.getMessageSwitch().addListener(mTaskManager.getSenderId(), this, false);
        if (BuildConfig.DEBUG) {
            System.out.println("SearchManager.doSearch listener started");
        }

		// We really want to ensure we get the same book from each, so if isbn is not present, do
		// these in series.

		boolean tasksStarted = false;
		mSearchingAsin = false;
		try {
			if (mIsbn != null && mIsbn.length() > 0) {
				if (IsbnUtils.isValid(mIsbn)) {
					// We have an ISBN, just do the search
					mWaitingForIsbn = false;
					tasksStarted = this.startSearches(mSearchFlags);
				} else {
					// Assume it's an ASIN, and just search Amazon
					mSearchingAsin = true;
					mWaitingForIsbn = false;
					//mSearchFlags = SEARCH_AMAZON;
					tasksStarted = startOneSearch(SEARCH_AMAZON);
					//tasksStarted = this.startSearches(mSearchFlags);
				}
			} else {
				// Run one at a time, startNext() defined the order.
				mWaitingForIsbn = true;
				tasksStarted = startNext();
			}			
		} finally {
			if (!tasksStarted) {
				sendResults();
				TaskManager.getMessageSwitch().removeListener(mTaskManager.getSenderId(), this);
				if (BuildConfig.DEBUG) {
					System.out.println("SearchManager.doSearch listener stopped");
				}
			}
		}

	}

	/**
	 * Utility routine to append text data from one Bundle to another
	 * 
	 * @param key		Key of data
	 * @param source	Source Bundle
	 * @param dest		Destination Bundle
	 */
	private void appendData(String key, Bundle source, Bundle dest) {
		String res = dest.getString(key) + "|" + source.getString(key);
		dest.putString(key, res);
	}

	/**
	 * Copy data from passed Bundle to current accumulated data. Does some careful
	 * processing of the data.
	 * 
	 * @param searchId	Source
	 */
	private void accumulateData(int searchId) {
		// See if we got data from this source
		if (!mSearchResults.containsKey(searchId))
			return;
		Bundle bookData = mSearchResults.get(searchId);

		// See if we REALLY got data from this source
		if (bookData == null)
			return;

		for (String k : bookData.keySet()) {
			// If its not there, copy it.
			if (!mBookData.containsKey(k) || mBookData.getString(k) == null || mBookData.getString(k).trim().isEmpty())
				mBookData.putString(k, bookData.get(k).toString());
			else {
				// Copy, append or update data as appropriate.
				switch (k) {
					case ColumnNames.KEY_AUTHOR_DETAILS:
						appendData(k, bookData, mBookData);
						break;
					case ColumnNames.KEY_SERIES_DETAILS:
						appendData(k, bookData, mBookData);
						break;
					case ColumnNames.KEY_DATE_PUBLISHED:
						// Grab a different date if we can parse it.
						Date newDate = DateUtils.parseDate(bookData.getString(k));
						if (newDate != null) {
							String curr = mBookData.getString(k);
							if (DateUtils.parseDate(curr) == null) {
								mBookData.putString(k, DateUtils.toSqlDateOnly(newDate));
							}
						}
						break;
					case "__thumbnail":
						appendData(k, bookData, mBookData);
						break;
				}
			}
		}
	}

	/**
	 * Combine all the data and create a book or display an error.
	 */
	private void sendResults() {
		// This list will be the actual order of the result we apply, based on the
		// actual results and the default order.
		ArrayList<Integer> results = new ArrayList<>();
		
		if (mHasIsbn) {
			// If ISBN was passed, ignore entries with the wrong ISBN, and put entries with no ISBN at the end
			ArrayList<Integer> uncertain = new ArrayList<>();
			for(int i: mDefaultReliabilityOrder) {
				if (mSearchResults.containsKey(i)) {
					Bundle bookData = mSearchResults.get(i);
					if (bookData.containsKey(ColumnNames.KEY_ISBN)) {
						if (IsbnUtils.matches(mIsbn, bookData.getString(ColumnNames.KEY_ISBN))) {
							results.add(i);
						}
					} else {
						uncertain.add(i);
					}						
				}
			}
			results.addAll(uncertain);
			// Add the passed ISBN first; avoid overwriting
			mBookData.putString(ColumnNames.KEY_ISBN, mIsbn);
		} else {
			// If ISBN was not passed, then just used the default order
			for(int i: mDefaultReliabilityOrder)
				results.add(i);
		}

		
		// Merge the data we have. We do this in a fixed order rather than as the threads finish.
		for(int i: results)
			accumulateData(i);

		// If there are thumbnails present, pick the biggest, delete others and rename.
		Utils.cleanupThumbnails(mBookData);

		// Try to use/construct authors
		String authors = null;
		try {
			authors = mBookData.getString(ColumnNames.KEY_AUTHOR_DETAILS);
		} catch (Exception ignored) {}

		if (authors == null || authors.isEmpty()) {
			authors = mAuthor;
		}

		if (authors != null && !authors.isEmpty()) {
			ArrayList<Author> aa = ArrayUtils.getAuthorUtils().decodeList(authors, '|', false);
			mBookData.putSerializable(ColumnNames.KEY_AUTHOR_ARRAY, aa);
		}

		// Try to use/construct title
		String title = null;
		try {
			title = mBookData.getString(ColumnNames.KEY_TITLE);
		} catch (Exception ignored) {}

		if (title == null || title.isEmpty())
			title = mTitle;

		if (title != null && !title.isEmpty()) {
			mBookData.putString(ColumnNames.KEY_TITLE, title);
		}

		// Try to use/construct isbn
		String isbn = null;
		try {
			isbn = mBookData.getString(ColumnNames.KEY_ISBN);
		} catch (Exception ignored) {}

		if (isbn == null || isbn.isEmpty())
			isbn = mIsbn;

		if (isbn != null && !isbn.isEmpty()) {
			mBookData.putString(ColumnNames.KEY_ISBN, isbn);
		}
		
		// Try to use/construct series
		String series = null;
		try {
			series = mBookData.getString(ColumnNames.KEY_SERIES_DETAILS);
		} catch (Exception ignored) {}

		if (series != null && !series.isEmpty()) {
			try {
				ArrayList<Series> sa = ArrayUtils.getSeriesUtils().decodeList(series, '|', false);
				mBookData.putSerializable(ColumnNames.KEY_SERIES_ARRAY, sa);
			} catch (Exception e) {
				Logger.logError(e);
			}
		} else {
			//add series to stop crashing
			mBookData.putSerializable(ColumnNames.KEY_SERIES_ARRAY, new ArrayList<Series>());
		}

		//
		// TODO: this needs to be locale-specific. Currently we probably get good-enough data without 
		// forcing a cleanup.
		//
		// Removed 20-Jan-2016 PJW; see Issue 717.
		//
		// Cleanup other fields
		//Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_TITLE);			
		//Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_PUBLISHER);
		//Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_DATE_PUBLISHED);
		//Utils.doProperCase(mBookData, CatalogueDBAdapter.KEY_SERIES_NAME);
		
		// If book is not found or missing required data, warn the user
		if (authors == null || authors.isEmpty() || title == null || title.isEmpty()) {
			mTaskManager.doToast(BookCatalogueApp.getResourceString(R.string.book_not_found));
		}
		// Pass the data back
		sendSearchFinished();
	}

	private void sendSearchFinished() {
		mMessageSwitch.send(mMessageSenderId, new MessageSwitch.Message<SearchListener>() {
			@Override
			public boolean deliver(SearchListener listener) {
				return listener.onSearchFinished(mBookData, mCancelledFlg);
			}}
		);		
	}
	/**
	 * When running in single-stream mode, start the next thread that has no data.
	 * While Google is reputedly most likely to succeed, it also produces garbage a lot. 
	 * So we search Amazon, Goodreads, Google and LT last as it REQUIRES an ISBN.
	 */
	private boolean startNext() {
		// Loop though in 'search-priority' order
		for (int source: mDefaultSearchOrder) {
			// If this search includes the source, check it
			if ( (mSearchFlags & source) != 0) {
				// If the source has not been search, search it
				if (!mSearchResults.containsKey(source)) {
					return startOneSearch(source);
				}
			}
		}
		return false;
	}

	/**
	 * Start all searches listed in passed parameter that have not been run yet.
	 */
	private boolean startSearches(int sources) {
		// Scan searches in priority order
		boolean started = false;
		for(int source: mDefaultSearchOrder) {
			// If requested search contains this source...
			if ((sources & source) != 0)
				// If we have not run this search...
				if (!mSearchResults.containsKey(source)) {
					// Run it now
					if (startOneSearch(source))
						started = true;
				}
		}
		return started;
	}
	
	/**
	 * Start specific search listed in passed parameter.
	 */
	private boolean startOneSearch(int source) {
		switch(source) {
		case SEARCH_GOOGLE:
			return startGoogle();
		case SEARCH_AMAZON:
			return startAmazon();
		case SEARCH_LIBRARY_THING:
			return startLibraryThing();
		case SEARCH_GOODREADS:
			return startGoodreads();
		default:
			throw new RuntimeException("Unexpected search source: " + source);				
		}
	}

	/**
	 * Handle task search results; start another task if necessary.
	 */
	private void handleSearchTaskFinished(final SearchThread t) {
		mCancelledFlg = t.isCancelled();
		Bundle bookData = t.getBookData();
		mSearchResults.put(t.getSearchId(), bookData);
		if (mCancelledFlg) {
			mWaitingForIsbn = false;
		} else {
			if (mSearchingAsin) {
				// If we searched AMAZON for an Asin, then see what we found
				mSearchingAsin = false;
				// Clear the 'isbn'
				mIsbn = "";
				if (Utils.isNonBlankString(bookData, ColumnNames.KEY_ISBN)) {
					// We got an ISBN, so pretend we were searching for an ISBN
					mWaitingForIsbn = true;
				} else {
					// See if we got author/title
					mAuthor = bookData.getString(ColumnNames.KEY_AUTHOR_NAME);
					mTitle = bookData.getString(ColumnNames.KEY_TITLE);
					if (mAuthor != null && !mAuthor.isEmpty() && mTitle != null && !mTitle.isEmpty()) {
						// We got them, so pretend we are searching by author/title now, and waiting for an ASIN...
						mWaitingForIsbn = true;
					}
				}
			}
			if (mWaitingForIsbn) {
				if (Utils.isNonBlankString(bookData, ColumnNames.KEY_ISBN)) {
					mWaitingForIsbn = false;
					// Start the other two...even if they have run before
					mIsbn = bookData.getString(ColumnNames.KEY_ISBN);
					startSearches(mSearchFlags);
				} else {
					// Start next one that has not run. 
					startNext();
				}
			}				
		}
	}		

	/* ===================================================================== 
	 * Message Switchboard implementation
	 * =====================================================================
	 */
	/**
	 * Allows other objects to know when a task completed.
	 * 
	 * @author Philip Warner
	 */
	public interface SearchListener {
		boolean onSearchFinished(Bundle bookData, boolean cancelled);
	}

	public interface SearchController {
		void requestAbort();
		SearchManager getSearchManager();
	}
	
	private final SearchController mController = new SearchController() {
		public void requestAbort() {
			mTaskManager.cancelAllTasks();
		}
		@Override
		public SearchManager getSearchManager() {
			return SearchManager.this;
		}
	};

	/**
	 * 	STATIC Object for passing messages from background tasks to activities that may be recreated 
	 *
	 *  This object handles all underlying OnTaskEndedListener messages for every instance of this class.
	 */
	public static class TaskSwitch extends MessageSwitch<SearchListener, SearchController> {}

    private static final TaskSwitch mMessageSwitch = new TaskSwitch();
	public static TaskSwitch getMessageSwitch() { return mMessageSwitch; }

	private final long mMessageSenderId = mMessageSwitch.createSender(mController);
	public long getSenderId() { return mMessageSenderId; }

}
