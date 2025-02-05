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

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.Cancellable;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;

/**
 * The interface a search engine for a {@link Site} needs to implement.
 * <p>
 * More details in {@link SearchSites}.
 * <p>
 * At least one of the sub-interfaces needs to be implemented:
 * <ul>
 *      <li>{@link ByExternalId}</li>
 *      <li>{@link ByIsbn}</li>
 *      <li>{@link ByText}</li>
 * </ul>
 * and if the site supports fetching images by ISBN: {@link CoverByIsbn}.
 * <p>
 * ENHANCE: it seems most implementations can return multiple book bundles quite easily.
 * <p>
 * The searches can throw 3 Exceptions:
 * <ul>
 *     <li>{@link CredentialsException}: We cannot authenticate to the site,
 *                                       the user MUST take action on it NOW.</li>
 *     <li>{@link StorageException}:     Specific local storage issues,
 *                                       the user MUST take action on it NOW.</li>
 *     <li>{@link SearchException}:      The embedded Exception has the details,
 *                                       should be reported to the user,
 *                                       but action is optional.</li>
 * </ul>
 */
public interface SearchEngine
        extends Cancellable {

    /** Log tag. */
    String TAG = "SearchEngine";

    /**
     * Get the configuration for this engine.
     *
     * @return config
     */
    @AnyThread
    @NonNull
    SearchEngineConfig getConfig();

    /**
     * Get the engine id.
     *
     * @return engine id
     */
    @SearchSites.EngineId
    default int getEngineId() {
        return getConfig().getEngineId();
    }

    /**
     * Get the name for this engine.
     *
     * @return name
     */
    @NonNull
    default String getName(@NonNull final Context context) {
        return getConfig().getName(context);
    }

    /**
     * Get the site url.
     * <p>
     * Override if the URL needs to be user configurable.
     *
     * @return url, including scheme.
     */
    @AnyThread
    @NonNull
    default String getSiteUrl() {
        return getConfig().getHostUrl();
    }

    /**
     * Get the Locale for this engine.
     * <p>
     * Override if the Locale needs to be user configurable.
     * (Presumably depending on the site url: see {@link AmazonSearchEngine} for an example)
     *
     * @param context Current context
     *
     * @return site locale
     */
    @AnyThread
    @NonNull
    default Locale getLocale(@NonNull final Context context) {
        return getConfig().getLocale();
    }

    /**
     * Generic test to be implemented by individual site search managers to check if
     * this site can be consider for searching.
     * <p>
     * Implementations can for example check for developer keys, ...
     * <strong>Should NOT run network code / test for connection.</strong>
     *
     * @return {@code true} if we can consider this site for searching.
     */
    @AnyThread
    default boolean isAvailable() {
        return true;
    }

    /**
     * Reset the engine, ready for a new search.
     * This is called by {@link Site#getSearchEngine()}.
     * <p>
     * The default implementation calls {@code setCaller(null)}.
     * Custom implementations should do the same.
     */
    default void reset() {
        setCaller(null);
    }

    /**
     * Set the caller to allow <strong>PULL</strong> checks if we should cancel the search.
     * i.e. the engine will ask the caller at semi-regular intervals if it should quit.
     *
     * @param caller to check with
     */
    void setCaller(@Nullable Cancellable caller);

    /**
     * Optional to implement: sites which need a registration of some sorts.
     * <p>
     * Check if we have a key/account; if not alert the user.
     *
     * @param context        Current context; <strong>MUST</strong> be passed in
     *                       as this call might do UI interaction.
     * @param required       {@code true} if we <strong>must</strong> have access to the site.
     *                       {@code false} if it would be beneficial but not mandatory.
     * @param callerIdString String used to flag in preferences if we showed the alert from
     *                       that caller already or not.
     * @param onResult       called after user selects an outcome
     *
     * @return {@code true} if an alert is currently shown
     */
    @UiThread
    default boolean promptToRegister(@NonNull final Context context,
                                     final boolean required,
                                     @Nullable final String callerIdString,
                                     @Nullable final Consumer<RegistrationAction> onResult) {
        return false;
    }

    /**
     * Show a registration request dialog.
     *
     * @param context        Current context
     * @param required       {@code true} if we <strong>must</strong> have access.
     *                       {@code false} if it would be beneficial.
     * @param callerIdString String used to flag in preferences if we showed the alert from
     *                       that caller already or not.
     *
     * @return {@code true} if an alert is currently shown
     */
    @UiThread
    default boolean showRegistrationDialog(@NonNull final Context context,
                                           final boolean required,
                                           @Nullable final String callerIdString,
                                           @NonNull final Consumer<RegistrationAction> onResult) {

        final SearchEngineConfig config = getConfig();
        final String key;
        if (callerIdString != null) {
            key = config.getPreferenceKey() + ".hide_alert." + callerIdString;
        } else {
            key = null;
        }

        final boolean showAlert;
        if (required || key == null) {
            showAlert = true;
        } else {
            showAlert = !PreferenceManager.getDefaultSharedPreferences(context)
                                          .getBoolean(key, false);
        }

        if (showAlert) {
            final String siteName = config.getName(context);

            final AlertDialog.Builder dialogBuilder = new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_warning_24)
                    .setTitle(context.getString(R.string.lbl_registration, siteName))
                    .setNegativeButton(R.string.action_not_now, (d, w) ->
                            onResult.accept(RegistrationAction.NotNow))
                    .setPositiveButton(R.string.btn_learn_more, (d, w) ->
                            onResult.accept(RegistrationAction.Register))
                    .setOnCancelListener(
                            d -> onResult.accept(RegistrationAction.Cancelled));

            if (required) {
                dialogBuilder.setMessage(context.getString(
                        R.string.confirm_registration_required, siteName));

            } else {
                dialogBuilder.setMessage(context.getString(
                        R.string.confirm_registration_benefits, siteName,
                        context.getString(R.string.lbl_credentials)));

                // If it's not required, allow the user to permanently hide this alert
                // for the given caller.
                if (key != null) {
                    dialogBuilder.setPositiveButton(context.getString(
                            R.string.btn_disable_message), (d, w) -> {
                        PreferenceManager.getDefaultSharedPreferences(context)
                                         .edit().putBoolean(key, true).apply();
                        onResult.accept(RegistrationAction.NotEver);
                    });
                }
            }
            dialogBuilder.create().show();
        }

        return showAlert;
    }

    @NonNull
    default <FRT> FutureHttpGet<FRT> createFutureGetRequest() {
        final SearchEngineConfig config = getConfig();
        final FutureHttpGet<FRT> httpGet = new FutureHttpGet<>(config.getLabelResId());
        httpGet.setConnectTimeout(config.getConnectTimeoutInMs())
               .setReadTimeout(config.getReadTimeoutInMs())
               .setThrottler(config.getThrottler());
        return httpGet;
    }

    enum RegistrationAction {
        /** User selected to 'learn more' and register on the given site. */
        Register,
        /** User does not want to bother now, but wants to be reminded later. */
        NotNow,
        /** Not interested, don't bother the user again. */
        NotEver,
        /** Cancelled without selecting any option. */
        Cancelled
    }

    /** Optional. */
    interface ByExternalId
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param context     Current context
         * @param externalId  the external id (as a String) for this particular search site.
         * @param fetchCovers Set to {@code true} if we want to get covers
         *                    The array is guaranteed to have at least one element.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         */
        @WorkerThread
        @NonNull
        Bundle searchByExternalId(@NonNull Context context,
                                  @NonNull String externalId,
                                  @NonNull boolean[] fetchCovers)
                throws StorageException,
                       SearchException,
                       CredentialsException;
    }

    interface ViewBookByExternalId
            extends SearchEngine {

        /**
         * Create a url to open a book on the website with the external id.
         *
         * @param externalId to open
         *
         * @return url
         */
        @AnyThread
        @NonNull
        String createBrowserUrl(@NonNull String externalId);
    }

    /** Optional. Every engine should really implement this. */
    interface ByIsbn
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param context     Current context
         * @param validIsbn   to search for, <strong>will</strong> be valid.
         * @param fetchCovers Set to {@code true} if we want to get covers
         *                    The array is guaranteed to have at least one element.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         */
        @WorkerThread
        @NonNull
        Bundle searchByIsbn(@NonNull Context context,
                            @NonNull String validIsbn,
                            @NonNull boolean[] fetchCovers)
                throws StorageException,
                       SearchException,
                       CredentialsException;

        /**
         * Indicates if ISBN code should be forced down to ISBN10 (if possible) before a search.
         * <p>
         * By default, we search on the ISBN entered by the user.
         * A preference setting per site can override this.
         * If set, and an ISBN13 is passed in, it will be translated to an ISBN10 before starting
         * the search.
         * <p>
         * This default implementation returns the global setting.
         *
         * @return {@code true} if ISBN10 should be preferred.
         */
        @AnyThread
        default boolean isPreferIsbn10() {
            return ServiceLocator.getPreferences()
                                 .getBoolean(Prefs.pk_search_isbn_prefer_10, false);
        }
    }

    /**
     * Optional.
     * Implement if the engine can search generic bar codes, aside of strict ISBN only.
     */
    interface ByBarcode
            extends ByIsbn {

        /**
         * Called by the {@link SearchCoordinator#search}.
         *
         * @param context     Current context
         * @param barcode     to search for, <strong>will</strong> be valid.
         * @param fetchCovers Set to {@code true} if we want to get covers
         *                    The array is guaranteed to have at least one element.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         */
        @WorkerThread
        @NonNull
        Bundle searchByBarcode(@NonNull Context context,
                               @NonNull String barcode,
                               @NonNull boolean[] fetchCovers)
                throws StorageException,
                       SearchException,
                       CredentialsException;
    }

    /**
     * Optional.
     * The engine can search by author/title/... without a valid ISBN.
     */
    interface ByText
            extends SearchEngine {

        /**
         * Called by the {@link SearchCoordinator#search}.
         * <p>
         * The code parameter might or might not be valid.
         * Checking the arguments <strong>MUST</strong> be done inside the implementation,
         * as they generally will depend on what the engine can do with them.
         *
         * @param context     Current context
         * @param code        isbn, barcode or generic code to search for
         * @param author      to search for
         * @param title       to search for
         * @param publisher   optional and in addition to author/title.
         *                    i.e. author and/or title must be valid;
         *                    only then the publisher is taken into account.
         * @param fetchCovers Set to {@code true} if we want to get covers
         *                    The array is guaranteed to have at least one element.
         *
         * @return bundle with book data. Can be empty, but never {@code null}.
         */
        @WorkerThread
        @NonNull
        Bundle search(@NonNull Context context,
                      @Nullable String code,
                      @Nullable String author,
                      @Nullable String title,
                      @Nullable String publisher,
                      @NonNull boolean[] fetchCovers)
                throws StorageException,
                       SearchException,
                       CredentialsException;
    }

    /** Optional. */
    interface CoverByIsbn
            extends SearchEngine {

        /**
         * Get a single cover image of the specified size.
         * <p>
         * <strong>Important</strong> this method should never throw any {@link RuntimeException}.
         * For the latter, simply return {@code null} when an error occurs (but log the error).
         * <p>
         * See {@link #searchBestCoverByIsbn} for sites with support for multiple cover sizes.
         *
         * @param context   Current context
         * @param validIsbn to search for, <strong>must</strong> be valid.
         * @param cIdx      0..n image index
         * @param size      of image to get.
         *
         * @return fileSpec, or {@code null} when none found (or any other failure)
         */
        @WorkerThread
        @Nullable
        String searchCoverByIsbn(@NonNull Context context,
                                 @NonNull String validIsbn,
                                 @IntRange(from = 0, to = 1) int cIdx,
                                 @Nullable Size size)
                throws StorageException,
                       SearchException,
                       CredentialsException;

        /**
         * Helper method for sites which support multiple image sizes.
         * It's a wrapper around {@link #searchCoverByIsbn} which
         * will try to get an image in order of large, medium, small.
         * i.e. the 'best' image being the largest we can find.
         *
         * @param context   Current context
         * @param validIsbn to search for, <strong>must</strong> be valid.
         * @param cIdx      0..n image index
         *
         * @return ArrayList with a single fileSpec (This is for convenience, as the result
         *         is meant to be stored into the book-data as a parcelable array;
         *         and it allows extending to multiple images at a future time)
         */
        @WorkerThread
        @NonNull
        default ArrayList<String> searchBestCoverByIsbn(@NonNull final Context context,
                                                        @NonNull final String validIsbn,
                                                        @IntRange(from = 0, to = 1) final int cIdx)
                throws StorageException,
                       SearchException,
                       CredentialsException {

            final ArrayList<String> list = new ArrayList<>();
            String fileSpec = searchCoverByIsbn(context, validIsbn, cIdx,
                                                Size.Large);
            if (fileSpec == null && getConfig().supportsMultipleCoverSizes()) {
                fileSpec = searchCoverByIsbn(context, validIsbn, cIdx,
                                             Size.Medium);
                if (fileSpec == null) {
                    fileSpec = searchCoverByIsbn(context, validIsbn, cIdx,
                                                 Size.Small);
                }
            }
            if (fileSpec != null) {
                list.add(fileSpec);
            }
            return list;
        }
    }

    /** Optional. */
    @FunctionalInterface
    interface AlternativeEditions {

        /**
         * Find alternative editions (their ISBN) for the given ISBN.
         *
         * @param context   Current context
         * @param validIsbn to search for, <strong>must</strong> be valid.
         *
         * @return a list of isbn numbers for alternative editions of the original, can be empty.
         */
        @WorkerThread
        @NonNull
        List<String> searchAlternativeEditions(@NonNull Context context,
                                               @NonNull String validIsbn)
                throws SearchException,
                       CredentialsException;
    }
}
