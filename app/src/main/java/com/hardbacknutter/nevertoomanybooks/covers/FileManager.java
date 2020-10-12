/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;
import com.hardbacknutter.nevertoomanybooks.tasks.Canceller;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;

/**
 * Handles downloading, checking and cleanup of files.
 */
public class FileManager {

    /** Log tag. */
    private static final String TAG = "FileManager";

    /**
     * Downloaded files.
     * key = isbn
     */
    private final Map<String, ImageFileInfo> mFiles =
            Collections.synchronizedMap(new HashMap<>());

    /** The Sites the user wants to search for cover images. */
    private final List<Site> mSiteList;

    /**
     * Constructor.
     *
     * @param sites site list
     */
    FileManager(@NonNull final List<Site> sites) {
        mSiteList = sites;
    }

    /**
     * Get the requested ImageFileInfo.
     *
     * @param isbn to search
     *
     * @return a {@link ImageFileInfo} object with or without a valid fileSpec,
     * or {@code null} if there is no cached file at all
     */
    @Nullable
    @AnyThread
    ImageFileInfo getFileInfo(@NonNull final String isbn) {
        return mFiles.get(isbn);
    }

    /**
     * Search for a file according to preference of {@link ImageFileInfo.Size} and {@link Site}.
     * <p>
     * First checks the cache. If we already have a good image, abort the search and use it.
     * <p>
     * We loop on {@link ImageFileInfo.Size} first, and for each, loop again on {@link Site}.
     * The for() loop will break/return <strong>as soon as a cover file is found.</strong>
     * The first Site which has an image is accepted.
     * <p>
     *
     * @param context Current context
     * @param caller  to check for any cancellations
     * @param isbn    to search for, <strong>must</strong> be valid.
     * @param cIdx    0..n image index
     * @param sizes   a list of images sizes in order of preference
     *
     * @return a {@link ImageFileInfo} object with or without a valid fileSpec.
     */
    @NonNull
    @WorkerThread
    public ImageFileInfo search(@NonNull final Context context,
                                @NonNull final Canceller caller,
                                @NonNull final String isbn,
                                @IntRange(from = 0, to = 1) final int cIdx,
                                @NonNull final ImageFileInfo.Size... sizes) {

        final List<Site> enabledSites = Site.filterForEnabled(mSiteList);

        // We will disable sites on the fly for the *current* search without
        // modifying the list by using a simple bitmask.
        @SearchSites.EngineId
        int currentSearchSites = 0;
        for (final Site site : enabledSites) {
            currentSearchSites = currentSearchSites | site.engineId;
        }

        ImageFileInfo imageFileInfo;

        // We need to use the size as the outer loop.
        // The idea is to check all sites for the same size first.
        // If none respond with that size, try the next size inline.
        for (final ImageFileInfo.Size size : sizes) {
            if (caller.isCancelled()) {
                return new ImageFileInfo(isbn);
            }

            // Do we already have a file previously downloaded?
            imageFileInfo = mFiles.get(isbn);
            if (imageFileInfo != null) {
                // Does it have an actual file ?
                if (imageFileInfo.hasFileSpec()) {
                    // There is a file and it is good (as determined at download time)
                    // But is the size we have suitable ? (null check to satisfy lint)
                    // Bigger files are always better (we hope)...
                    if (imageFileInfo.getSize() != null
                        && imageFileInfo.getSize().compareTo(size) >= 0) {

                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                            Log.d(TAG, "search|PRESENT|SUCCESS"
                                       + "|imageFileInfo=" + imageFileInfo);
                        }

                        // YES, use the file we already have
                        return imageFileInfo;
                    }

                    // else drop through and search for it.
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                        Log.d(TAG, "search|PRESENT|TO SMALL"
                                   + "|imageFileInfo=" + imageFileInfo);
                    }
                } else {
                    if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                        Log.d(TAG, "search|PRESENT|NO FILE"
                                   + "|imageFileInfo=" + imageFileInfo);
                    }
                    // a previous search failed, there simply is NO file
                    return imageFileInfo;

                }
            }


            for (final Site site : enabledSites) {
                // Should we search this site ?
                if ((currentSearchSites & site.engineId) != 0) {

                    if (caller.isCancelled()) {
                        return new ImageFileInfo(isbn);
                    }

                    // Is this Site's SearchEngine available and suitable?
                    final SearchEngine searchEngine = site.getSearchEngine(context, caller);
                    if (searchEngine instanceof SearchEngine.CoverByIsbn
                        && searchEngine.isAvailable()) {

                        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                            Log.d(TAG, "search|SEARCHING"
                                       + "|searchEngine=" + searchEngine.getName()
                                       + "|isbn=" + isbn
                                       + "|cIdx=" + cIdx
                                       + "|size=" + size);
                        }

                        @Nullable
                        final String fileSpec = ((SearchEngine.CoverByIsbn) searchEngine)
                                .searchCoverImageByIsbn(isbn, cIdx, size);

                        if (fileSpec != null) {
                            // we got a file
                            imageFileInfo = new ImageFileInfo(isbn, fileSpec, size,
                                                              searchEngine.getId());
                            mFiles.put(isbn, imageFileInfo);

                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                                Log.d(TAG, "search|SUCCESS"
                                           + "|searchEngine=" + searchEngine.getName()
                                           + "|imageFileInfo=" + imageFileInfo);
                            }
                            // abort search, we got an image
                            return imageFileInfo;

                        } else {
                            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                                Log.d(TAG, "search|NO FILE"
                                           + "|searchEngine=" + searchEngine.getName()
                                           + "|isbn=" + isbn
                                           + "|cIdx=" + cIdx
                                           + "|size=" + size);
                            }
                        }

                        // if the site we just searched only supports one image,
                        // disable it for THIS search
                        if (!((SearchEngine.CoverByIsbn) searchEngine)
                                .supportsMultipleCoverSizes()) {
                            currentSearchSites &= ~site.engineId;
                        }
                    } else {
                        // if the site we just searched was not available,
                        // disable it for THIS search
                        currentSearchSites &= ~site.engineId;
                    }
                }
                // loop for next site
            }
            // loop for next size
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Log.d(TAG, "search|FAILED"
                       + "|isbn=" + isbn);
        }

        // Failed to find any size on all sites, record the failure to prevent future attempt
        // and return the failure
        imageFileInfo = new ImageFileInfo(isbn);
        mFiles.put(isbn, imageFileInfo);
        return imageFileInfo;
    }

    /**
     * Clean up all files we handled in this class.
     */
    public void purge() {
        mFiles.values()
              .stream()
              .filter(Objects::nonNull)
              .map(ImageFileInfo::getFile)
              .filter(Objects::nonNull)
              .forEach(FileUtils::delete);

        // not strictly needed, but future-proof
        mFiles.clear();
    }
}
