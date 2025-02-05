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

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Locale;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.goodreads.GoodreadsSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.googlebooks.GoogleBooksSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.isfdb.IsfdbSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.kbnl.KbNlSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.lastdodo.LastDodoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.librarything.LibraryThingSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary.OpenLibrarySearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.stripinfo.StripInfoSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.sites.IsfdbPreferencesFragment;

/**
 * Manages the setup of {@link SearchEngine}'s.
 * <p>
 * To add a new site to search, follow these steps:
 * <ol>
 *     <li>Add an identifier (bit) in this class.</li>
 *     <li>Add this identifier to {@link EngineId} and {@link #DATA_RELIABILITY_ORDER}.</li>
 *     <li>Add a string resource with the name of the site engine in:
 *          "src/main/res/values/donottranslate.xml" (look for existing entries named 'site_*')
 *     </li>
 *     <li>Implement {@link SearchEngine} to create the new engine class
 *          extending {@link SearchEngineBase} or {@link JsoupSearchEngineBase}
 *          or a similar setup.<br>
 *          Don't forget the "@Keep" annotation on the required constructor.<br>
 *          Configure the engine using {@link SearchEngineConfig}.
 *      </li>
 *
 *     <li>Add the {@link SearchEngine} class to
 *     {@link #registerSearchEngineClasses(SearchEngineRegistry)}</li>
 *
 *      <li>Add a new {@link Site} instance to the one or more list(s)
 *          in {@link #createSiteList}</li>
 *
 *      <li>Optional: if the engine/site will store a external book id (or any other specific
 *          fields) in the local database, extra steps will need to be taken.
 *          TODO: document steps: search the code for "NEWTHINGS: adding a new search engine"
 *      </li>
 *
 *      <li>Optional: Add a preference fragment for the user to configure the engine.
 *          See the {@link IsfdbPreferencesFragment} for an example:
 *          a class, an xml file, and an entry in "src/main/res/xml/preferences.xml"."</li>
 * </ol>
 * <p>
 *
 * <strong>Note:</strong>
 * <strong>Never change the identifier of the sites</strong>, they get stored in the db.
 * Dev note: there is really only one place where the code relies on this being bit numbers...
 * but we might as well keep them as bits.
 */
public final class SearchSites {

    /** Site: all genres. */
    public static final int GOOGLE_BOOKS = 1;
    /** Site: all genres. */
    public static final int AMAZON = 1 << 1;
    /** Site: all genres. */
    public static final int LIBRARY_THING = 1 << 2;
    /** Site: all genres. */
    public static final int GOODREADS = 1 << 3;


    /** Site: Speculative Fiction only. e.g. Science-Fiction/Fantasy etc... */
    public static final int ISFDB = 1 << 4;

    /** Site: all genres. */
    public static final int OPEN_LIBRARY = 1 << 5;


    /** Site: Dutch language books & comics. */
    public static final int KB_NL = 1 << 6;
    /** Site: Dutch language (and to an extend French) comics. */
    public static final int STRIP_INFO_BE = 1 << 7;
    /** Site: Dutch language (and to an extend French) comics. */
    public static final int LAST_DODO = 1 << 8;

    // NEWTHINGS: adding a new search engine: add the engine id as a new bit

    /**
     * Simple CSV string with the search engine ids in reliability of data order.
     * Order is hardcoded based on experience. ENHANCE: make this user configurable
     * (Dev.note: it's a CSV because we store these kind of lists as strings in SharedPreferences)
     * NEWTHINGS: adding a new search engine: add the engine id
     */
    static final String DATA_RELIABILITY_ORDER =
            ISFDB
            + "," + STRIP_INFO_BE
            + "," + AMAZON
            + "," + GOOGLE_BOOKS
            + "," + LAST_DODO
//            + "," + KB_NL
            + "," + OPEN_LIBRARY;

    private SearchSites() {
    }

    /**
     * Register all {@link SearchEngine} classes.
     * <p>
     * NEWTHINGS: adding a new search engine: add the search engine class
     */
    static void registerSearchEngineClasses(@NonNull final SearchEngineRegistry registry) {
        //dev note: we could scan for the annotation or for classes implementing the interface...
        // ... but that means traversing the class path. Not really worth the hassle.
        // For the BuildConfig.ENABLE_ usage: see app/build.gradle
        // The order added is not relevant

        // full/english functionality
        registry.add(AmazonSearchEngine.createConfig())
                .add(GoodreadsSearchEngine.createConfig())
                .add(IsfdbSearchEngine.createConfig())
                .add(OpenLibrarySearchEngine.createConfig());

        if (BuildConfig.ENABLE_GOOGLE_BOOKS) {
            registry.add(GoogleBooksSearchEngine.createConfig());
        }

        // Alternative Edition search only!
        if (BuildConfig.ENABLE_LIBRARY_THING_ALT_ED) {
            registry.add(LibraryThingSearchEngine.createConfig());
        }


        // Dutch.
        if (BuildConfig.ENABLE_KB_NL) {
            registry.add(KbNlSearchEngine.createConfig());
        }
        // Dutch.
        if (BuildConfig.ENABLE_LAST_DODO) {
            registry.add(LastDodoSearchEngine.createConfig());
        }
        // Dutch.
        if (BuildConfig.ENABLE_STRIP_INFO) {
            registry.add(StripInfoSearchEngine.createConfig());
        }
    }

    /**
     * Register all {@link Site} instances; called during startup.
     *
     * @param systemLocale device Locale <em>(passed in to allow mocking)</em>
     * @param userLocale   user locale <em>(passed in to allow mocking)</em>
     * @param type         the type of Site list
     */
    static void createSiteList(@NonNull final Locale systemLocale,
                               @NonNull final Locale userLocale,
                               @NonNull final Site.Type type) {

        // Certain sites are only enabled by default if the device or user set language
        // matches the site language.
        // Dutch websites:
        final boolean enableIfDutch = ServiceLocator.getInstance().getLanguages()
                                                    .isLang(systemLocale, userLocale, "nld");

        //NEWTHINGS: add new search engine: add to the 3 lists as needed.

        // yes, we could loop over the SearchEngine's, and detect their interfaces.
        // but this gives more control.
        // For the BuildConfig.ENABLE_ usage: see app/build.gradle
        //
        // The order added here is the default order they will be used, but the user
        // can reorder the lists in preferences.

        switch (type) {
            case Data: {
                type.addSite(AMAZON);

                if (BuildConfig.ENABLE_GOOGLE_BOOKS) {
                    type.addSite(GOOGLE_BOOKS);
                }

                type.addSite(ISFDB);

                if (BuildConfig.ENABLE_STRIP_INFO) {
                    type.addSite(STRIP_INFO_BE, enableIfDutch);
                }
                if (BuildConfig.ENABLE_LAST_DODO) {
                    type.addSite(LAST_DODO, enableIfDutch);
                }
                if (BuildConfig.ENABLE_KB_NL) {
                    type.addSite(KB_NL, enableIfDutch);
                }

                // Disabled by default as data from this site is not very complete.
                type.addSite(OPEN_LIBRARY, false);
                break;
            }
            case Covers: {
                // Only add sites here that implement {@link SearchEngine.CoverByIsbn}.

                type.addSite(AMAZON);

                type.addSite(ISFDB);

                if (BuildConfig.ENABLE_KB_NL) {
                    type.addSite(KB_NL, enableIfDutch);
                }

                // Disabled by default as this site lacks many covers.
                type.addSite(OPEN_LIBRARY, false);
                break;
            }
            case AltEditions: {
                //Only add sites here that implement {@link SearchEngine.AlternativeEditions}.

                if (BuildConfig.ENABLE_LIBRARY_THING_ALT_ED) {
                    type.addSite(LIBRARY_THING);
                }

                type.addSite(ISFDB);
                break;
            }

            case ViewOnSite: {
                // only add sites here that implement {@link SearchEngine.ViewBookByExternalId}.

                type.addSite(GOODREADS);
                type.addSite(ISFDB);

                if (BuildConfig.ENABLE_LIBRARY_THING_ALT_ED) {
                    type.addSite(LIBRARY_THING);
                }

                type.addSite(OPEN_LIBRARY);

                if (BuildConfig.ENABLE_STRIP_INFO) {
                    type.addSite(STRIP_INFO_BE, enableIfDutch);
                }
                if (BuildConfig.ENABLE_LAST_DODO) {
                    type.addSite(LAST_DODO, enableIfDutch);
                }
                break;
            }

            default:
                throw new IllegalArgumentException(String.valueOf(type));
        }
    }

    // NEWTHINGS: adding a new search engine: add the engine id
    @IntDef(flag = true, value = {
            GOOGLE_BOOKS, AMAZON, LIBRARY_THING, GOODREADS,
            ISFDB, OPEN_LIBRARY,
            KB_NL, STRIP_INFO_BE, LAST_DODO})
    @Retention(RetentionPolicy.SOURCE)
    public @interface EngineId {

    }
}
