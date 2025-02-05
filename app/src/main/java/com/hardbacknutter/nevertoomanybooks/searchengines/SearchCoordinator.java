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
package com.hardbacknutter.nevertoomanybooks.searchengines;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseLongArray;

import androidx.annotation.AnyThread;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.SearchCriteria;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.booklist.style.FieldVisibility;
import com.hardbacknutter.nevertoomanybooks.booklist.style.GlobalFieldVisibility;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.network.NetworkUtils;
import com.hardbacknutter.nevertoomanybooks.sync.ColorMapper;
import com.hardbacknutter.nevertoomanybooks.sync.FormatMapper;
import com.hardbacknutter.nevertoomanybooks.sync.Mapper;
import com.hardbacknutter.nevertoomanybooks.tasks.ASyncExecutor;
import com.hardbacknutter.nevertoomanybooks.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskListener;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskProgress;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;

/**
 * Co-ordinate multiple {@link SearchTask}.
 * <p>
 * It maintains its own internal list of tasks {@link #activeTasks} and as tasks finish,
 * it processes the data. Once all tasks are complete, it reports back using the
 * {@link MutableLiveData}.
 * <p>
 * The {@link Site#engineId} is used as the task id.
 */
public class SearchCoordinator
        extends ViewModel
        implements Cancellable {

    /** Log tag. */
    private static final String TAG = "SearchCoordinator";
    /** The data returned from the search can contain this key with error messages. */
    public static final String BKEY_SEARCH_ERROR = TAG + ":error";

    /**
     * List of front/back cover file specs as collected during the search.
     * <p>
     * <br>type: {@code ArrayList<String>}
     */
    public static final String[] BKEY_FILE_SPEC_ARRAY = {
            TAG + ":fileSpec_array:0",
            TAG + ":fileSpec_array:1"
    };

    /** divider to convert nanoseconds to milliseconds. */
    private static final int NANO_TO_MILLIS = 1_000_000;

    protected final MutableLiveData<LiveDataEvent<TaskProgress>>
            searchCoordinatorProgress = new MutableLiveData<>();
    protected final MutableLiveData<LiveDataEvent<TaskResult<Bundle>>>
            searchCoordinatorCancelled = new MutableLiveData<>();
    private final MutableLiveData<LiveDataEvent<TaskResult<Bundle>>>
            searchCoordinatorFinished = new MutableLiveData<>();


    /** List of Tasks being managed by *this* object. */
    private final Collection<SearchTask> activeTasks = new HashSet<>();

    /** Flag indicating we're shutting down. */
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /** Accumulates the results from <strong>individual</strong> search tasks. */
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, Bundle> searchResultsBySite = new HashMap<>();

    /** Accumulates the last message from <strong>individual</strong> search tasks. */
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, Exception> searchErrorsBySite =
            Collections.synchronizedMap(new HashMap<>());

    /** Accumulates the results from <strong>individual</strong> search tasks. */
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, TaskProgress> searchProgressBySite = new HashMap<>();

    /** Reference to the registry. */
    private SearchEngineRegistry searchEngineRegistry;
    /**
     * Sites to search on. If this list is empty, all searches will return {@code false}.
     * This list includes both enabled and disabled sites.
     */
    private ArrayList<Site> allSites;
    /** Base message for progress updates. */
    @Nullable
    private String baseMessage;

    /** Flag indicating searches will be non-concurrent until an ISBN is found. */
    private boolean waitingForIsbnOrCode;
    /** Original ISBN text for search. */
    @NonNull
    private String isbnSearchText = "";
    /** {@code true} for strict ISBN checking, {@code false} for allowing generic codes. */
    private boolean strictIsbn = true;
    /** Created by {@link #prepareSearch()}. NonNull afterwards. */
    private ISBN isbn;
    /** Site external id for search. */
    @Nullable
    private SparseArray<String> externalIdSearchText;
    /** Original author for search. */
    @NonNull
    private String authorSearchText = "";
    /** Original title for search. */
    @NonNull
    private String titleSearchText = "";
    /** Original publisher for search. */
    @NonNull
    private String publisherSearchText = "";
    /** Whether of not to fetch thumbnails. */
    @Nullable
    private boolean[] fetchCover;


    /** DEBUG timer. */
    private long searchStartTime;
    /** DEBUG timer. */
    private SparseLongArray searchTasksStartTime;
    /** DEBUG timer. */
    private SparseLongArray searchTasksEndTime;


    /** Cached string resource. */
    private String listElementPrefixString;

    private ResultsAccumulator resultsAccumulator;

    /** Observable. */
    @NonNull
    public LiveData<LiveDataEvent<TaskProgress>> onProgress() {
        return searchCoordinatorProgress;
    }

    /**
     * Handles both Successful and Failed searches.
     * <p>
     * The Bundle will (optionally) contain {@link #BKEY_SEARCH_ERROR} with a list of errors.
     */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Bundle>>> onSearchFinished() {
        return searchCoordinatorFinished;
    }

    /** Observable. */
    @NonNull
    public LiveData<LiveDataEvent<TaskResult<Bundle>>> onSearchCancelled() {
        return searchCoordinatorCancelled;
    }

    /**
     * Cancel all searches.
     */
    public void cancel() {
        cancelled.set(true);
        synchronized (activeTasks) {
            for (final SearchTask searchTask : activeTasks) {
                searchTask.cancel();
            }
        }
    }

    @Override
    protected void onCleared() {
        cancel();
    }

    public void cancelTask(@IdRes final int taskId) {
        // reminder: this object, the SearchCoordinator is a pseudo task
        // we're only using "cancelTask" to conform with other usage
        cancel();
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Pseudo constructor.
     *
     * @param context Current context
     * @param args    {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@NonNull final Context context,
                     @Nullable final Bundle args) {

        if (searchEngineRegistry == null) {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
                searchTasksStartTime = new SparseLongArray();
                searchTasksEndTime = new SparseLongArray();
            }

            searchEngineRegistry = SearchEngineRegistry.getInstance();
            allSites = Site.Type.Data.getSites();

            resultsAccumulator = new ResultsAccumulator(context);

            listElementPrefixString = context.getString(R.string.list_element);

            if (args != null) {
                fetchCover = new boolean[]{
                        GlobalFieldVisibility.isUsed(FieldVisibility.COVER[0]),
                        GlobalFieldVisibility.isUsed(FieldVisibility.COVER[1])
                };

                isbnSearchText = args.getString(DBKey.BOOK_ISBN, "");

                titleSearchText = args.getString(DBKey.TITLE, "");

                authorSearchText = args.getString(
                        SearchCriteria.BKEY_SEARCH_TEXT_AUTHOR, "");

                publisherSearchText = args.getString(
                        SearchCriteria.BKEY_SEARCH_TEXT_PUBLISHER, "");
            }
        }
    }

    /**
     * Creates {@link TaskProgress} with the global/total progress of all tasks.
     *
     * @return instance
     */
    @NonNull
    private TaskProgress accumulateProgress() {

        // Sum the current & max values for each active task.
        int progressMax = 0;
        int progressCount = 0;

        // Start with the base message if we have one.
        final StringBuilder sb;
        if (baseMessage != null && !baseMessage.isEmpty()) {
            sb = new StringBuilder(baseMessage);
        } else {
            sb = new StringBuilder();
        }

        synchronized (searchProgressBySite) {
            if (!searchProgressBySite.isEmpty()) {
                // if there was a baseMessage, add a linefeed to it.
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                // Append each task message
                sb.append(searchProgressBySite
                                  .values().stream()
                                  .map(msg -> String.format(listElementPrefixString, msg.text))
                                  .collect(Collectors.joining("\n")));

                for (final TaskProgress taskProgress : searchProgressBySite.values()) {
                    progressMax += taskProgress.maxPosition;
                    progressCount += taskProgress.position;
                }

            }
        }

        return new TaskProgress(R.id.TASK_ID_SEARCH_COORDINATOR, sb.toString(),
                                progressMax, progressCount, null);
    }

    /**
     * Search the given engine with the site specific book id.
     *
     * @param searchEngine         to use
     * @param externalIdSearchText to search for
     *
     * @return {@code true} if the search was started.
     */
    public boolean searchByExternalId(@NonNull final SearchEngine searchEngine,
                                      @NonNull final String externalIdSearchText) {
        SanityCheck.requireValue(externalIdSearchText, "externalIdSearchText");

        // remove all other criteria (this is CRUCIAL)
        clearSearchCriteria();

        this.externalIdSearchText = new SparseArray<>();
        this.externalIdSearchText.put(searchEngine.getEngineId(), externalIdSearchText);
        prepareSearch();

        return startSearch(searchEngine);
    }

    public boolean isSearchActive() {
        synchronized (activeTasks) {
            return !activeTasks.isEmpty();
        }
    }

    /**
     * Called after the search criteria are ready, and before starting the actual search.
     * Clears a number of parameters so we can start the search with a clean slate.
     */
    private void prepareSearch() {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchStartTime = System.nanoTime();
        }

        // Developer sanity checks
        if (BuildConfig.DEBUG /* always */) {
            if (!NetworkUtils.isNetworkAvailable(ServiceLocator.getAppContext())) {
                throw new IllegalStateException("network should be checked before starting search");
            }

            if (!activeTasks.isEmpty()) {
                throw new IllegalStateException("a search is already running");
            }

            // Note we don't care about publisher.
            if (authorSearchText.isEmpty()
                && titleSearchText.isEmpty()
                && isbnSearchText.isEmpty()
                && (externalIdSearchText == null || externalIdSearchText.size() == 0)) {
                throw new SanityCheck.MissingValueException("empty criteria");
            }
        }

        // reset flags
        waitingForIsbnOrCode = false;
        cancelled.set(false);

        // no synchronized needed here
        searchResultsBySite.clear();
        // no synchronized needed here
        searchErrorsBySite.clear();

        isbn = new ISBN(isbnSearchText, strictIsbn);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "prepareSearch"
                       + "|mExternalIdSearchText=" + externalIdSearchText
                       + "|mIsbnSearchText=" + isbnSearchText
                       + "|mIsbn=" + isbn
                       + "|mStrictIsbn=" + strictIsbn
                       + "|mAuthor=" + authorSearchText
                       + "|mTitle=" + titleSearchText
                       + "|mPublisher=" + publisherSearchText);
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksStartTime.clear();
            searchTasksEndTime.clear();
        }
    }

    /**
     * Get the <strong>current</strong> preferred search sites.
     *
     * @return list with the enabled sites
     */
    @NonNull
    public ArrayList<Site> getSiteList() {
        return allSites;
    }

    /**
     * Override the initial list.
     *
     * @param sites to use
     */
    public void setSiteList(@NonNull final ArrayList<Site> sites) {
        allSites = sites;
    }

    /**
     * Process the message and start another task if required.
     *
     * @param taskId of task
     * @param result of a search (can be null for failed/cancelled searches)
     */
    @SuppressLint("WrongConstant")
    private synchronized void onSearchTaskFinished(final int taskId,
                                                   @Nullable final Bundle result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksEndTime.put(taskId, System.nanoTime());
        }

        final Context context = ServiceLocator.getInstance().getLocalizedAppContext();

        // Remove the finished task from our list
        synchronized (activeTasks) {
            activeTasks.stream()
                       .filter(searchTask -> searchTask.getTaskId() == taskId)
                       .findFirst()
                       .ifPresent(activeTasks::remove);


            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                Log.d(TAG, "onSearchTaskFinished|finished="
                           + searchEngineRegistry.getByEngineId(taskId)
                                                 .getName(context));

                for (final SearchTask searchTask : activeTasks) {
                    Log.d(TAG, "onSearchTaskFinished|running="
                               + searchEngineRegistry.getByEngineId(searchTask.getTaskId())
                                                     .getName(context));
                }
            }
        }

        // ALWAYS store, even when null!
        // Presence of the site/task id in the map is an indication that the site ws processed
        synchronized (searchResultsBySite) {
            searchResultsBySite.put(taskId, result);
        }

        // clear obsolete progress status
        synchronized (searchProgressBySite) {
            searchProgressBySite.remove(taskId);
        }


        // Start new search(es) as needed/allowed.
        boolean searchStarted = false;
        if (!cancelled.get()) {
            //  update our listener with the current progress status
            searchCoordinatorProgress.setValue(new LiveDataEvent<>(accumulateProgress()));

            if (waitingForIsbnOrCode) {
                if (result != null && hasIsbn(result)) {
                    waitingForIsbnOrCode = false;
                    // replace the search text with the (we hope) exact ISBN/code
                    isbnSearchText = result.getString(DBKey.BOOK_ISBN, "");

                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                        Log.d(TAG, "onSearchTaskFinished|waitingForExactCode|isbn="
                                   + isbnSearchText);
                    }

                    // Start the remaining searches, even if they have run before.
                    // They will redo the search WITH the ISBN/code.
                    searchStarted = startSearch();
                } else {
                    // sequentially start the next search which has not run yet.
                    searchStarted = startNextSearch();
                }
            }
        }

        // any searches still running or did we get cancelled?
        final boolean stopSearching;
        synchronized (activeTasks) {
            // if we didn't start a new search (which might not be active yet!),
            // and there are no previous searches still running
            // (or we got cancelled) then we are done.
            stopSearching = !searchStarted && (activeTasks.isEmpty() || cancelled.get());
        }

        if (stopSearching) {
            final long processTime = System.nanoTime();

            final Bundle bookData = accumulateResults(context);
            final String searchErrors = accumulateErrors(context);
            if (searchErrors != null && !searchErrors.isEmpty()) {
                bookData.putString(BKEY_SEARCH_ERROR, searchErrors);
            }

            final LiveDataEvent<TaskResult<Bundle>> message =
                    new LiveDataEvent<>(new TaskResult<>(R.id.TASK_ID_SEARCH_COORDINATOR,
                                                         bookData));
            if (cancelled.get()) {
                searchCoordinatorCancelled.setValue(message);
            } else {
                searchCoordinatorFinished.setValue(message);
            }

            if (BuildConfig.DEBUG /* always */) {
                Log.d(TAG, "onSearchTaskFinished"
                           + "|cancelled=" + cancelled.get()
                           + "|searchErrors=" + searchErrors);

                if (DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
                    for (int i = 0; i < searchTasksStartTime.size(); i++) {
                        final long start = searchTasksStartTime.valueAt(i);
                        // use the key, not the index!
                        final int key = searchTasksStartTime.keyAt(i);
                        final long end = searchTasksEndTime.get(key);

                        final String engineName = searchEngineRegistry.getByEngineId(key)
                                                                      .getName(context);

                        if (end != 0) {
                            Log.d(TAG, String.format(
                                    Locale.ENGLISH,
                                    "onSearchTaskFinished|engine=%20s:%10d ms",
                                    engineName,
                                    (end - start) / NANO_TO_MILLIS));
                        } else {
                            Log.d(TAG, String.format(
                                    Locale.ENGLISH,
                                    "onSearchTaskFinished|engine=%20s|never finished",
                                    engineName));
                        }
                    }

                    Log.d(TAG, String.format(
                            Locale.ENGLISH,
                            "onSearchTaskFinished|total search time: %10d ms",
                            (processTime - searchStartTime)
                            / NANO_TO_MILLIS));
                    Log.d(TAG, String.format(
                            Locale.ENGLISH,
                            "onSearchTaskFinished|processing time: %10d ms",
                            (System.nanoTime() - processTime)
                            / NANO_TO_MILLIS));
                }
            }
        }
    }

    /**
     * Indicate we want a thumbnail.
     *
     * @param fetchCovers Set to {@code true} if we want to get covers
     */
    protected void setFetchCover(@Nullable final boolean[] fetchCovers) {
        fetchCover = fetchCovers;
    }

    /**
     * Clear all search criteria.
     */
    public void clearSearchCriteria() {
        externalIdSearchText = null;
        isbnSearchText = "";
        authorSearchText = "";
        titleSearchText = "";
        publisherSearchText = "";
    }

    /**
     * Search criteria.
     *
     * @param externalIds one or more ID's
     *                    The key is the engine id,
     *                    The value us the value of the external domain for that engine
     */
    public void setExternalIds(@Nullable final SparseArray<String> externalIds) {
        externalIdSearchText = externalIds;
    }

    /** Listener for <strong>individual</strong> search tasks. */
    private final TaskListener<Bundle> mSearchTaskListener = new TaskListener<>() {

        @Override
        public void onProgress(@NonNull final TaskProgress message) {
            synchronized (searchProgressBySite) {
                searchProgressBySite.put(message.taskId, message);
            }
            // forward the accumulated progress
            searchCoordinatorProgress.setValue(new LiveDataEvent<>(accumulateProgress()));
        }

        @Override
        public void onFinished(final int taskId,
                               @Nullable final Bundle result) {
            onSearchTaskFinished(taskId, Objects.requireNonNull(result, "result"));
        }

        @Override
        public void onCancelled(final int taskId,
                                @Nullable final Bundle result) {
            // we'll deliver what we have found up to now (includes previous searches)
            onSearchTaskFinished(taskId, result);
        }

        @Override
        public void onFailure(final int taskId,
                              @Nullable final Exception exception) {
            synchronized (searchErrorsBySite) {
                // Always store, even if null
                searchErrorsBySite.put(taskId, exception);
            }
            onSearchTaskFinished(taskId, null);
        }
    };

    @NonNull
    public String getIsbnSearchText() {
        return isbnSearchText;
    }

    public void setIsbnSearchText(@NonNull final String isbnSearchText) {
        this.isbnSearchText = isbnSearchText;
    }

    public boolean isStrictIsbn() {
        return strictIsbn;
    }

    public void setStrictIsbn(final boolean strictIsbn) {
        this.strictIsbn = strictIsbn;
    }

    /**
     * Search criteria.
     *
     * @param isbnSearchText to search for
     * @param strictIsbn     Flag: set to {@link false} to allow invalid isbn numbers
     *                       to be passed to the searches
     */
    public void setIsbnSearchText(@NonNull final String isbnSearchText,
                                  final boolean strictIsbn) {
        this.isbnSearchText = isbnSearchText;
        this.strictIsbn = strictIsbn;
    }

    @NonNull
    public String getAuthorSearchText() {
        return authorSearchText;
    }

    /**
     * Search criteria.
     *
     * @param authorSearchText to search for
     */
    public void setAuthorSearchText(@NonNull final String authorSearchText) {
        this.authorSearchText = authorSearchText;
    }

    @NonNull
    public String getTitleSearchText() {
        return titleSearchText;
    }

    /**
     * Search criteria.
     *
     * @param titleSearchText to search for
     */
    public void setTitleSearchText(@NonNull final String titleSearchText) {
        this.titleSearchText = titleSearchText;
    }

    @NonNull
    public String getPublisherSearchText() {
        return publisherSearchText;
    }

    /**
     * Search criteria.
     *
     * @param publisherSearchText to search for
     */
    public void setPublisherSearchText(@NonNull final String publisherSearchText) {
        this.publisherSearchText = publisherSearchText;
    }

    /**
     * Start a search.
     * <p>
     * If there is a valid ISBN/code, we start a concurrent search on all sites.
     * When all sites are searched, we're done.
     * <p>
     * Otherwise, we start a serial search using author/title (and optional publisher)
     * until we find an ISBN/code or until we searched all sites.
     * Once/if an ISBN/code is found, the serial search is abandoned, and a new concurrent search
     * is started on all sites using the ISBN/code.
     *
     * @return {@code true} if at least one search was started.
     */
    public boolean search() {
        prepareSearch();

        // If we have one or more ID's
        if ((externalIdSearchText != null && externalIdSearchText.size() > 0)
            // or we have a valid code
            || isbn.isValid(strictIsbn)) {

            // then start a concurrent search
            waitingForIsbnOrCode = false;
            return startSearch();

        } else {
            // We really want to ensure we get the same book from each,
            // so if the ISBN/code is NOT PRESENT, search the sites
            // one at a time until we get a ISBN/code.
            waitingForIsbnOrCode = true;
            return startNextSearch();
        }
    }

    /**
     * Start <strong>all</strong>> searches, which have not been run yet, in parallel.
     *
     * @return {@code true} if at least one search was started, {@code false} if none
     */
    private boolean startSearch() {
        // refuse new searches if we're shutting down.
        if (cancelled.get()) {
            return false;
        }

        boolean atLeastOneStarted = false;
        for (final Site site : Site.filterForEnabled(allSites)) {
            // If the site has not been searched yet, search it
            if (!searchResultsBySite.containsKey(site.engineId)) {
                if (startSearch(site.getSearchEngine())) {
                    atLeastOneStarted = true;
                }
            }
        }
        return atLeastOneStarted;
    }

    protected void setBaseMessage(@Nullable final String baseMessage) {
        this.baseMessage = baseMessage;
    }

    /**
     * Check if passed Bundle contains a non-blank ISBN string. Does not check if the ISBN is valid.
     *
     * @param bundle to check
     *
     * @return Present/absent
     */
    private boolean hasIsbn(@NonNull final Bundle bundle) {
        final String isbnStr = bundle.getString(DBKey.BOOK_ISBN);
        return isbnStr != null && !isbnStr.trim().isEmpty();
    }

    /**
     * Start specified site search.
     *
     * @param searchEngine to use
     *
     * @return {@code true} if the search was started.
     */
    private boolean startSearch(@NonNull final SearchEngine searchEngine) {

        // refuse new searches if we're shutting down.
        if (cancelled.get()) {
            return false;
        }

        if (!searchEngine.isAvailable()) {
            return false;
        }

        // check for a external id matching the site.
        String externalId = null;
        if (externalIdSearchText != null
            && externalIdSearchText.size() > 0) {
            final int engineId = searchEngine.getEngineId();
            if (externalIdSearchText.get(engineId) != null) {
                externalId = externalIdSearchText.get(engineId);
            }
        }

        final SearchTask task = new SearchTask(searchEngine, mSearchTaskListener);
        task.setExecutor(ASyncExecutor.MAIN);

        task.setFetchCovers(fetchCover);

        if (externalId != null && !externalId.isEmpty()
            && (searchEngine instanceof SearchEngine.ByExternalId)) {
            task.setSearchBy(SearchTask.By.ExternalId);
            task.setExternalId(externalId);

        } else if (isbn.isValid(true)
                   && (searchEngine instanceof SearchEngine.ByIsbn)) {
            task.setSearchBy(SearchTask.By.Isbn);
            if (((SearchEngine.ByIsbn) searchEngine).isPreferIsbn10()
                && isbn.isIsbn10Compat()) {
                task.setIsbn(isbn.asText(ISBN.Type.Isbn10));
            } else {
                task.setIsbn(isbn.asText());
            }

        } else if (isbn.isValid(false)
                   && (searchEngine instanceof SearchEngine.ByBarcode)) {
            task.setSearchBy(SearchTask.By.Barcode);
            task.setIsbn(isbn.asText());

        } else if (searchEngine instanceof SearchEngine.ByText) {
            task.setSearchBy(SearchTask.By.Text);
            task.setIsbn(isbn.asText());
            task.setAuthor(authorSearchText);
            task.setTitle(titleSearchText);
            task.setPublisher(publisherSearchText);

        } else {
            // search data and engine have nothing in common, abort silently.
            return false;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR_TIMERS) {
            searchTasksStartTime.put(task.getTaskId(), System.nanoTime());
        }

        synchronized (activeTasks) {
            activeTasks.add(task);
        }
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
            Log.d(TAG, "startSearch|searchEngine="
                       + searchEngine.getName(ServiceLocator.getAppContext()));
        }

        task.startSearch();
        return true;
    }

    /**
     * Start a single task.
     *
     * @return {@code true} if a search was started, {@code false} if not
     */
    private boolean startNextSearch() {
        // refuse new searches if we're shutting down.
        if (cancelled.get()) {
            return false;
        }

        for (final Site site : Site.filterForEnabled(allSites)) {
            // If the site has not been searched yet, search it
            if (!searchResultsBySite.containsKey(site.engineId)) {
                return startSearch(site.getSearchEngine());
            }
        }
        return false;
    }

    /**
     * Called when all is said and done. Collects all individual website errors (if any)
     * into a single user-formatted message.
     *
     * @param context Current context
     *
     * @return the error message
     */
    @Nullable
    private String accumulateErrors(@NonNull final Context context) {
        // no synchronized needed, at this point all other threads have finished.
        if (!searchErrorsBySite.isEmpty()) {
            final String msg = searchErrorsBySite
                    .values()
                    .stream()
                    .map(exception -> ExMsg
                            .map(context, exception)
                            .orElseGet(() -> {
                                // generic network related IOException message
                                if (exception instanceof IOException) {
                                    return context.getString(R.string.error_search_failed_network);
                                }
                                // generic unknown message
                                return context.getString(R.string.error_unknown);
                            }))
                    .collect(Collectors.joining("\n"));

            searchErrorsBySite.clear();
            return msg;
        }
        return null;
    }

    /**
     * Called when all is said and done. Accumulate data from all sites.
     *
     * <strong>Developer note:</strong> before you think you can simplify this method
     * by working directly with engine-id and SearchEngines... DON'T
     * Read class docs for {@link SearchSites} and {@link Site.Type#getDataSitesByReliability}.
     *
     * @param context Current context
     *
     * @return the accumulated book data bundle
     */
    @NonNull
    private Bundle accumulateResults(@NonNull final Context context) {
        // This list will be the actual order of the result we apply, based on the
        // actual results and the default order.
        final List<Site> sites = new ArrayList<>();

        final Bundle bookData = ServiceLocator.newBundle();

        // determine the order of the sites which should give us the most reliable data.
        if (isbn.isValid(true)) {
            // If an ISBN was passed, ignore entries with the wrong ISBN,
            // and put entries without ISBN at the end
            final Collection<Site> sitesWithoutIsbn = new ArrayList<>();
            for (final Site site : Site.Type.getDataSitesByReliability()) {
                if (searchResultsBySite.containsKey(site.engineId)) {
                    final Bundle siteData = searchResultsBySite.get(site.engineId);
                    // any results for this site?
                    if (siteData != null && !siteData.isEmpty()) {
                        // yes; check isbn to determine the order in which we'll use the results
                        if (siteData.containsKey(DBKey.BOOK_ISBN)) {
                            final String isbnFound = siteData.getString(DBKey.BOOK_ISBN);
                            // do they match?
                            if (isbnFound != null && !isbnFound.isEmpty()
                                && isbn.equals(ISBN.createISBN(isbnFound))) {
                                sites.add(site);
                            }

                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                                Log.d(TAG, "accumulateResults"
                                           + "|mIsbn=" + isbn
                                           + "|isbnFound=" + isbnFound);
                            }
                        } else {
                            sitesWithoutIsbn.add(site);
                        }
                    }
                }
            }
            // now add the less reliable ones at the end of the list.
            sites.addAll(sitesWithoutIsbn);
            // Add the passed ISBN first;
            // avoids overwriting with potentially different isbn from the sites
            bookData.putString(DBKey.BOOK_ISBN, isbnSearchText);

        } else {
            // If an ISBN was not passed, then just use the default order
            sites.addAll(Site.Type.getDataSitesByReliability());
        }

        // Merge the data we have in the order as decided upon above.
        resultsAccumulator.process(context, sites, searchResultsBySite, bookData);

        // If we did not get an ISBN, use the one we originally searched for.
        final String isbnStr = bookData.getString(DBKey.BOOK_ISBN);
        if (isbnStr == null || isbnStr.isEmpty()) {
            bookData.putString(DBKey.BOOK_ISBN, isbnSearchText);
        }

        // If we did not get an title, use the one we originally searched for.
        final String title = bookData.getString(DBKey.TITLE);
        if (title == null || title.isEmpty()) {
            bookData.putString(DBKey.TITLE, titleSearchText);
        }

        return bookData;
    }

    private static class ResultsAccumulator {

        private static final Set<String> LIST_KEYS = Set.of(Book.BKEY_AUTHOR_LIST,
                                                            Book.BKEY_SERIES_LIST,
                                                            Book.BKEY_PUBLISHER_LIST,
                                                            Book.BKEY_TOC_LIST,
                                                            BKEY_FILE_SPEC_ARRAY[0],
                                                            BKEY_FILE_SPEC_ARRAY[1]);
        private static final Set<String> DATE_KEYS = Set.of(DBKey.BOOK_PUBLICATION__DATE,
                                                            DBKey.FIRST_PUBLICATION__DATE);

        private final CoverFilter mCoverFilter = new CoverFilter();

        private final DateParser mDateParser;
        /** Mappers to apply. */
        private final Collection<Mapper> mMappers = new ArrayList<>();

        ResultsAccumulator(@NonNull final Context context) {
            mDateParser = new FullDateParser(context);

            if (FormatMapper.isMappingAllowed()) {
                mMappers.add(new FormatMapper());
            }
            if (ColorMapper.isMappingAllowed()) {
                mMappers.add(new ColorMapper());
            }
        }

        /**
         * Accumulate all data from the given sites.
         * <p>
         * The Bundle will contain by default only String and ArrayList based data.
         * Long etc... types will be stored as String data.
         * <p>
         * NEWTHINGS: if you add a new Search task that adds non-string based data,
         * handle that here.
         *
         * @param context  Current context
         * @param sites    the ordered list of sites
         * @param bookData Destination bundle
         */
        public void process(@NonNull final Context context,
                            @NonNull final List<Site> sites,
                            @NonNull final Map<Integer, Bundle> searchResultsBySite,
                            @NonNull final Bundle bookData) {
            sites.stream()
                 .map(Site::getSearchEngine)
                 .forEach(searchEngine -> {
                     final Bundle siteData = searchResultsBySite.get(searchEngine.getEngineId());
                     if (siteData != null && !siteData.isEmpty()) {
                         final Locale siteLocale = searchEngine.getLocale(context);

                         for (final String key : siteData.keySet()) {
                             if (DATE_KEYS.contains(key)) {
                                 processDate(siteLocale, key, siteData, bookData);

                             } else if (LIST_KEYS.contains(key)) {
                                 processList(key, siteData, bookData);

                             } else {
                                 //FIXME: doing this will for example put a LONG id in
                                 // the bundle as a String. This is as-designed, but you
                                 // do get an Exception in the log when the data gets to
                                 // the EditBook formatters. Harmless, but not clean.

                                 // handle all normal String based entries
                                 processString(key, siteData, bookData);
                             }
                         }
                     }
                 });

            // run the mappers
            mMappers.forEach(mapper -> mapper.map(context, bookData));

            // Pick the best covers for each list (if any) and clean/delete all others.
            mCoverFilter.filter(bookData);
        }

        /**
         * Grabs the 'new' date and checks if it's parsable.
         * If so, then check if the previous date was actually valid at all.
         * if not, use new date.
         *
         * @param siteLocale the specific Locale of the website
         * @param key        for the date field
         * @param siteData   Source Bundle
         * @param bookData   Destination bundle
         */
        private void processDate(@NonNull final Locale siteLocale,
                                 @NonNull final String key,
                                 @NonNull final Bundle siteData,
                                 @NonNull final Bundle bookData) {
            final String currentDateHeld = bookData.getString(key);
            final String dataToAdd = siteData.getString(key);

            if (currentDateHeld == null || currentDateHeld.isEmpty()) {
                // copy, even if the incoming date might not be valid.
                // We'll deal with that later.
                bookData.putString(key, dataToAdd);

            } else {
                // FIXME: there is overlap with some SearchEngines which already do a full
                //  validity check on the dates they gather. We should avoid a double-check.
                //
                // Overwrite with the new date if we can parse it and
                // if the current one was present but not valid.
                if (dataToAdd != null) {
                    final LocalDateTime newDate = mDateParser.parse(dataToAdd, siteLocale);
                    if (newDate != null) {
                        if (mDateParser.parse(currentDateHeld, siteLocale) == null) {
                            // current date was invalid, use the new one instead.
                            // (theoretically this check was not needed, as we should not have
                            // an invalid date stored anyhow... but paranoia rules)
                            bookData.putString(key,
                                               newDate.format(DateTimeFormatter.ISO_LOCAL_DATE));
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                                Log.d(TAG, "processDate|copied"
                                           + "|key=" + key + "|value=`" + dataToAdd + '`');
                            }
                        } else {
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                                Log.d(TAG, "processDate|skipped|key=" + key);
                            }
                        }
                    }
                }
            }
        }

        /**
         * Accumulate ParcelableArrayList data.
         * Add if not present, or append.
         *
         * @param <T>      type of items in the ArrayList
         * @param key      Key of data
         * @param siteData Source Bundle
         * @param bookData Destination bundle
         */
        private <T extends Parcelable> void processList(@NonNull final String key,
                                                        @NonNull final Bundle siteData,
                                                        @NonNull final Bundle bookData) {
            final ArrayList<T> dataToAdd = siteData.getParcelableArrayList(key);
            if (dataToAdd == null || dataToAdd.isEmpty()) {
                return;
            }

            ArrayList<T> dest = bookData.getParcelableArrayList(key);
            if (dest == null || dest.isEmpty()) {
                // just copy
                dest = dataToAdd;
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "processList|copied"
                               + "|key=" + key + "|value=`" + dataToAdd + '`');
                }
            } else {
                // append
                dest.addAll(dataToAdd);
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "processList|appended"
                               + "|key=" + key + "|value=`" + dataToAdd + '`');
                }
            }
            bookData.putParcelableArrayList(key, dest);
        }

        /**
         * Accumulate String data.
         * Handles other types via a .toString()
         *
         * @param key      Key of data
         * @param siteData Source Bundle
         * @param bookData Destination bundle
         */
        private void processString(@NonNull final String key,
                                   @NonNull final Bundle siteData,
                                   @NonNull final Bundle bookData) {
            final Object dataToAdd = siteData.get(key);
            if (dataToAdd == null || dataToAdd.toString().trim().isEmpty()) {
                return;
            }

            final String dest = bookData.getString(key);
            if (dest == null || dest.isEmpty()) {
                // just use it
                bookData.putString(key, dataToAdd.toString());
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "processString|copied"
                               + "|key=" + key + "|value=`" + dataToAdd + '`');
                }
            } else {
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.SEARCH_COORDINATOR) {
                    Log.d(TAG, "processString|skipping|key=" + key);
                }
            }
        }
    }

    private static class CoverFilter {

        /**
         * Filter the {@link #BKEY_FILE_SPEC_ARRAY} present, selecting only the best
         * image for each index, and store those in {@link Book#BKEY_TMP_FILE_SPEC}.
         * This may result in removing ALL images if none are found suitable.
         *
         * @param bookData to filter
         */
        @AnyThread
        public void filter(@NonNull final Bundle bookData) {
            for (int cIdx = 0; cIdx < 2; cIdx++) {
                final ArrayList<String> imageList =
                        bookData.getStringArrayList(BKEY_FILE_SPEC_ARRAY[cIdx]);

                if (imageList != null && !imageList.isEmpty()) {
                    // ALWAYS call even if we only have 1 image...
                    // We want to remove bad ones if needed.
                    final String coverName = getBestImage(imageList);
                    if (coverName != null) {
                        bookData.putString(Book.BKEY_TMP_FILE_SPEC[cIdx], coverName);
                    }
                }
                bookData.remove(BKEY_FILE_SPEC_ARRAY[cIdx]);
            }
        }

        /**
         * Pick the largest image from the given list, and delete all others.
         *
         * @param imageList a list of images
         *
         * @return name of cover found, or {@code null} for none.
         */
        @AnyThread
        @Nullable
        private String getBestImage(@NonNull final ArrayList<String> imageList) {

            // biggest size based on height * width
            long bestImageSize = -1;
            // index of the file which is the biggest
            int bestFileIndex = -1;

            // Just read the image files to get file size
            final BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;

            // Loop, finding biggest image
            for (int i = 0; i < imageList.size(); i++) {
                final String fileSpec = imageList.get(i);
                if (new File(fileSpec).exists()) {
                    BitmapFactory.decodeFile(fileSpec, opt);
                    // If no size info, assume file bad and skip
                    if (opt.outHeight > 0 && opt.outWidth > 0) {
                        final long size = (long) opt.outHeight * (long) opt.outWidth;
                        if (size > bestImageSize) {
                            bestImageSize = size;
                            bestFileIndex = i;
                        }
                    }
                }
            }

            // Delete all but the best one.
            // Note there *may* be no best one, so all would be deleted. This is fine.
            for (int i = 0; i < imageList.size(); i++) {
                if (i != bestFileIndex) {
                    FileUtils.delete(new File(imageList.get(i)));
                }
            }

            if (bestFileIndex >= 0) {
                return imageList.get(bestFileIndex);
            }

            return null;
        }
    }

}
