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
package com.hardbacknutter.nevertoomanybooks.searchengines.openlibrary;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.IntRange;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Collectors;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.covers.Size;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.TocEntry;
import com.hardbacknutter.nevertoomanybooks.network.FutureHttpGet;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchCoordinator;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineBase;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineConfig;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchException;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.utils.dates.DateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.StorageException;
import com.hardbacknutter.org.json.JSONArray;
import com.hardbacknutter.org.json.JSONException;
import com.hardbacknutter.org.json.JSONObject;

/**
 * <a href="https://openlibrary.org/dev/docs/api/books">API</a>
 * <p>
 * TLDR: works, but data not complete or not stable (maybe I am to harsh though)
 * <ol>
 *     <li>The Works API (by Work ID)</li>
 *     <li>The Editions API (by Edition ID)</li>
 *     <li>The ISBN API (by ISBN)
 *          <br>Pro: non-english books have a language tag embedded
 *          <br>Con: No Authors embedded (links instead), no covers (id's, but no sizes),
 *              key/values different from api 4 (below)
 *     </li>
 *     <li>The Books API (generic) - what we use; see below</li>
 * </ol>
 *
 * <a href="https://openlibrary.org/dev/docs/api/books">API books</a>
 * Allows searching by all identifiers. Example isbn:  bibkeys=ISBN:0201558025
 * <ul>
 *      <li>response format: jscmd=data:<br>
 *          Does not return all the info that is known to be present.
 *          (use the website itself to look up an isbn)
 *          Example: "physical_format": "Paperback" is NOT part of the response;
 *          Example: language info is missing
 *      </li>
 *      <li>response format: jscmd=detail:<br>
 *          The docs state: "It is advised to use jscmd=data instead of this as that is
 *          more stable format."
 *          The response seems to (mostly?) contain the same info as from 'data' but with
 *          additional fields. Some fields have a different schema: "identifiers" with
 *          "data" has sub object with all identifiers (including isbn).
 *          But "identifiers" with "detail" has no isbn number.
 *          Instead isbn numbers are on the same level as "identifiers" itself.<br>
 *          PRO: contains language and series tags!
 *      </li>
 * </ul>
 * <ul>Problems:
 *      <li>"data" does not contain all information that the site has</li>
 *      <li>"details" seems, by their own admission, not to be stable yet.</li>
 *      <li>both: dates are not structured, but {@link FullDateParser} can work around that.</li>
 *      <li>last update dates on the website & api docs are sometimes from years ago.
 * Is this still developed ?</li>
 * </ul>
 * Implementing SearchEngine.ByText is possible, but an issue is how much data could be returned.
 * Example: https://openlibrary.org/search.json?author=tolkien&title=hobbit
 * will return a 9000+ lines long document...
 * <p>
 *  Below is a rudimentary "data" implementation. "details" was tested with curl.
 */
public class OpenLibrarySearchEngine
        extends SearchEngineBase
        implements SearchEngine.ByIsbn,
                   SearchEngine.ByExternalId,
                   SearchEngine.ViewBookByExternalId,
                   SearchEngine.CoverByIsbn {

    /**
     * bibkeys
     * <p>
     * List of IDs to request the information.
     * The API supports ISBNs, LCCNs, OCLC numbers and OLIDs (Open Library IDs).
     * <p>
     * ISBN
     * <p>
     * Ex. &bibkeys=ISBN:0451526538 (The API supports both ISBN 10 and 13.)
     * OCLC
     * <p>
     * &bibkeys=OCLC:#########
     * LCCN
     * <p>
     * &bibkeys=LCCN:#########
     * OLID
     * <p>
     * &bibkeys=OLID:OL123M
     * <p>
     * param 1: key-name, param 2: key-value
     * <p>
     * {@link #handleResponse} only tested with ISBN and OLID for now.
     */
    private static final String BASE_BOOK_URL =
            "/api/books?jscmd=data&format=json&bibkeys=%1$s:%2$s";

    /**
     * The covers are available in 3 sizes:
     * <p>
     * S: Small, suitable for use as a thumbnail on a results page on Open Library,
     * M: Medium, suitable for display on a details page on Open Library and,
     * L: Large
     * The URL pattern to access book covers is:
     * <p>
     * http://covers.openlibrary.org/b/$key/$value-$size.jpg
     * <p>
     * Where:
     * <p>
     * key can be any one of ISBN, OCLC, LCCN, OLID and ID (case-insensitive)
     * value is the value of the chosen key
     * size can be one of S, M and L for small, medium and large respectively.
     * <p>
     * param 1: key-name, param 2: key-value, param 3: L/M/S for the size.
     */
    private static final String BASE_COVER_URL =
            "https://covers.openlibrary.org/b/%1$s/%2$s-%3$s.jpg?default=false";

    /** The search keys in the json object we support: ISBN, external id. */
    private static final String SUPPORTED_KEYS = "ISBN,OLID";
    @Nullable
    private FutureHttpGet<String> futureHttpGet;

    /**
     * Constructor. Called using reflection, so <strong>MUST</strong> be <em>public</em>.
     *
     * @param config the search engine configuration
     */
    @Keep
    public OpenLibrarySearchEngine(@NonNull final SearchEngineConfig config) {
        super(config);
    }

    public static SearchEngineConfig createConfig() {
        return new SearchEngineConfig.Builder(OpenLibrarySearchEngine.class,
                                              SearchSites.OPEN_LIBRARY,
                                              R.string.site_open_library,
                                              "openlibrary",
                                              "https://openlibrary.org")

                .setSupportsMultipleCoverSizes(true)
                .setFilenameSuffix("OL")

                .setDomainKey(DBKey.SID_OPEN_LIBRARY)
                .setDomainViewId(R.id.site_open_library)
                .setDomainMenuId(R.id.MENU_VIEW_BOOK_AT_OPEN_LIBRARY)
                .build();
    }

    @NonNull
    @Override
    public String createBrowserUrl(@NonNull final String externalId) {
        return getSiteUrl() + "/books/" + externalId;
    }

    @NonNull
    @Override
    public Bundle searchByExternalId(@NonNull final Context context,
                                     @NonNull final String externalId,
                                     @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException {

        final Bundle bookData = ServiceLocator.newBundle();

        final String url = getSiteUrl() + String.format(BASE_BOOK_URL, "OLID", externalId);

        fetchBook(context, url, fetchCovers, bookData);
        return bookData;
    }

    /**
     * <a href="https://openlibrary.org/dev/docs/api/books">API books</a>.
     *
     * <br><br>{@inheritDoc}
     */
    @NonNull
    @Override
    public Bundle searchByIsbn(@NonNull final Context context,
                               @NonNull final String validIsbn,
                               @NonNull final boolean[] fetchCovers)
            throws StorageException, SearchException {

        final Bundle bookData = ServiceLocator.newBundle();

        final String url = getSiteUrl() + String.format(BASE_BOOK_URL, "ISBN", validIsbn);

        fetchBook(context, url, fetchCovers, bookData);
        return bookData;
    }

    /**
     * <a href="https://openlibrary.org/dev/docs/api/covers">API covers</a>.
     * <p>
     * {@code
     * http://covers.openlibrary.org/b/isbn/0385472579-S.jpg?default=false
     * }
     * <p>
     * S/M/L
     *
     * <br><br>{@inheritDoc}
     */
    @Nullable
    @Override
    @WorkerThread
    public String searchCoverByIsbn(@NonNull final Context context,
                                    @NonNull final String validIsbn,
                                    @IntRange(from = 0, to = 1) final int cIdx,
                                    @Nullable final Size size)
            throws StorageException {
        final String sizeParam;
        if (size == null) {
            sizeParam = "L";
        } else {
            switch (size) {
                case Small:
                    sizeParam = "S";
                    break;
                case Medium:
                    sizeParam = "M";
                    break;
                case Large:
                default:
                    sizeParam = "L";
                    break;
            }
        }

        final String url = String.format(BASE_COVER_URL, "isbn", validIsbn, sizeParam);
        return saveImage(url, validIsbn, cIdx, size);
    }

    @Override
    public void cancel() {
        synchronized (this) {
            super.cancel();
            if (futureHttpGet != null) {
                futureHttpGet.cancel();
            }
        }
    }

    /**
     * Fetch and parse.
     */
    private void fetchBook(@NonNull final Context context,
                           @NonNull final String url,
                           @NonNull final boolean[] fetchCovers,
                           @NonNull final Bundle bookData)
            throws StorageException,
                   SearchException {

        futureHttpGet = createFutureGetRequest();

        try {
            // get and store the result into a string.
            final String response = futureHttpGet.get(url, request -> {
                try (BufferedInputStream bis = new BufferedInputStream(
                        request.getInputStream())) {
                    return readResponseStream(bis);

                } catch (@NonNull final IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            if (handleResponse(context, response, fetchCovers, bookData)) {
                checkForSeriesNameInTitle(bookData);
            }

        } catch (@NonNull final IOException e) {
            throw new SearchException(getName(context), e);
        } finally {
            futureHttpGet = null;
        }
    }

    /**
     * Read the entire InputStream into a String.
     *
     * @param is to read
     *
     * @return the entire content
     *
     * @throws UncheckedIOException on any failure
     */
    @VisibleForTesting
    @NonNull
    String readResponseStream(@NonNull final InputStream is)
            throws UncheckedIOException {
        // Don't close this stream!
        final InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        final BufferedReader reader = new BufferedReader(isr);

        return reader.lines().collect(Collectors.joining());
    }


    /**
     * A search on ISBN returns:
     *
     * <pre>{@code
     *  "ISBN:9780980200447": {
     *    "publishers": [{"name": "Litwin Books"}],
     *    "pagination": "80p.",
     *    "identifiers": {
     *      "google": ["4LQU1YwhY6kC"],
     *      "lccn": ["2008054742"],
     *      "openlibrary": ["OL22853304M"],
     *      "isbn_13": ["9780980200447", "9781936117369"],
     *      "amazon": ["098020044X"],
     *      "isbn_10": ["1936117363"],
     *      "oclc": ["297222669"],
     *      "goodreads": ["6383507"],
     *      "librarything": ["8071257"]
     *    },
     *    "table_of_contents": [
     *      {"title": "The personal nature of slow reading",
     *        "label": "", "pagenum": "", "level": 0},
     *      {"title": "Slow reading in an information ecology",
     *        "label": "", "pagenum": "", "level": 0},
     *      {"title": "The slow movement and slow reading",
     *        "label": "", "pagenum": "", "level": 0},
     *      {"title": "The psychology of slow reading",
     *        "label": "", "pagenum": "", "level": 0},
     *      {"title": "The practice of slow reading.",
     *        "label": "", "pagenum": "", "level": 0}
     *    ],
     *    "links": [
     *      {"url": "http:\/\/johnmiedema.ca",
     *        "title": "Author's Website"
     *      },
     *      {"url": "http:\/\/litwinbooks.com\/slowreading-ch2.php",
     *        "title": "Chapter 2"
     *      },
     *      {"url": "http:\/\/www.powells.com\/biblio\/91-9781936117369-0",
     *        "title": "Get the e-book"
     *      }
     *    ],
     *    "weight": "1 grams",
     *    "title": "Slow reading",
     *    "url": "https:\/\/openlibrary.org\/books\/OL22853304M\/Slow_reading",
     *    "classifications": {
     *      "dewey_decimal_class": ["028\/.9"],
     *      "lc_classifications": ["Z1003 .M58 2009"]
     *    },
     *    "notes": "Includes bibliographical references and index.",
     *    "number_of_pages": 92,
     *    "cover": {
     *      "small": "https:\/\/covers.openlibrary.org\/b\/id\/5546156-S.jpg",
     *      "large": "https:\/\/covers.openlibrary.org\/b\/id\/5546156-L.jpg",
     *      "medium": "https:\/\/covers.openlibrary.org\/b\/id\/5546156-M.jpg"
     *    },
     *    "subjects": [{
     *        "url": "https:\/\/openlibrary.org\/subjects\/books_and_reading",
     *        "name": "Books and reading"},
     *      {"url": "https:\/\/openlibrary.org\/subjects\/in_library",
     *        "name": "In library"},
     *      {"url": "https:\/\/openlibrary.org\/subjects\/reading",
     *        "name": "Reading"}
     *    ],
     *    "publish_date": "March 2009",
     *    "key": "\/books\/OL22853304M",
     *    "authors": [{
     *        "url": "https:\/\/openlibrary.org\/authors\/OL6548935A\/John_Miedema",
     *        "name": "John Miedema"
     *      }],
     *    "by_statement": "by John Miedema.",
     *    "publish_places": [{
     *        "name": "Duluth, Minn"}],
     *    "ebooks": [{
     *        "checkedout": true,
     *        "formats": {},
     *        "preview_url": "https:\/\/archive.org\/details\/slowreading00mied",
     *        "borrow_url": "https:\/\/openlibrary.org\/books\/OL22853304M\/Slow_reading\/borrow",
     *        "availability": "borrow"
     *      }
     *    ]
     *       }
     *     }
     * }</pre>
     * <p>
     * A search on OLID returns:
     * <pre>{@code
     *  {
     *    "OLID:OL10393624M":{
     *      "publishers":[{"name":"Tor Books"}],
     *      "identifiers":{
     *          "openlibrary":["OL10393624M"],
     *          "isbn_13":["9780312859664"],
     *          "isbn_10":["031285966X"],
     *          "oclc":["32665311"],
     *          "librarything":["81766"],
     *          "goodreads":["872340"]},
     *      "subtitle":"Alastor 1716",
     *      "weight":"1.4 pounds",
     *      "title":"Alastor: Trullion : Alastor 2262  Marune : Alastor 933 Wyst ",
     *      "url":"https:\/\/openlibrary.org\/books\/OL10393624M\/
     *                Alastor_Trullion_Alastor_2262_Marune_Alastor_933_Wyst",
     *      "number_of_pages":479,
     *      "cover":{
     *          "small":"https:\/\/covers.openlibrary.org\/b\/id\/5059003-S.jpg",
     *          "large":"https:\/\/covers.openlibrary.org\/b\/id\/5059003-L.jpg",
     *          "medium":"https:\/\/covers.openlibrary.org\/b\/id\/5059003-M.jpg"},
     *      "subjects":[{
     *          "url":"https:\/\/openlibrary.org\/subjects\/internet_archive_wishlist",
     *          "name":"Internet Archive Wishlist"}],
     *      "publish_date":"September 1995",
     *      "key":"\/books\/OL10393624M",
     *      "authors":[{
     *          "url":"https:\/\/openlibrary.org\/authors\/OL253641A\/Jack_Vance",
     *          "name":"Jack Vance"}]
     *    }
     *  }
     * }</pre>
     * The keys (jsonObject.keys()) are:
     * "ISBN:9780980200447"
     *
     * @param context     Current context
     * @param response    the complete response; a String containing JSON
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param bookData    Bundle to update
     *
     * @return {@code true} on success, {@code false} if we were cancelled.
     */
    @VisibleForTesting
    boolean handleResponse(@NonNull final Context context,
                           @NonNull final String response,
                           @NonNull final boolean[] fetchCovers,
                           @NonNull final Bundle bookData)
            throws StorageException,
                   SearchException {

        try {
            final JSONObject jsonObject = new JSONObject(response);

            final Iterator<String> it = jsonObject.keys();
            // we only handle the first result for now.
            if (it.hasNext()) {
                if (isCancelled()) {
                    return false;
                }

                final String topLevelKey = it.next();
                final String[] data = topLevelKey.split(":");
                if (data.length == 2 && SUPPORTED_KEYS.contains(data[0])) {
                    parse(context, data[1],
                          jsonObject.getJSONObject(topLevelKey),
                          fetchCovers,
                          bookData);
                }
            }
        } catch (@NonNull final JSONException e) {
            throw new SearchException(getName(context), e);
        }

        return true;
    }

    /**
     * Parse the results, and build the bookData bundle.
     *
     * @param context     Current context
     * @param validIsbn   of the book
     * @param document    JSON result data
     * @param fetchCovers Set to {@code true} if we want to get covers
     * @param bookData    Bundle to update
     */
    private void parse(@NonNull final Context context,
                       @NonNull final String validIsbn,
                       @NonNull final JSONObject document,
                       @NonNull final boolean[] fetchCovers,
                       @NonNull final Bundle bookData)
            throws StorageException {

        final DateParser dateParser = new FullDateParser(context);

        // store the isbn; we might override it later on though (e.g. isbn 13v10)
        // not sure if this is needed though. Need more data.
        bookData.putString(DBKey.BOOK_ISBN, validIsbn);

        JSONObject element;
        JSONArray a;
        String s;
        final int i;

        s = document.optString("title");
        if (!s.isEmpty()) {
            bookData.putString(DBKey.TITLE, s);
        }

        // s = document.optString("subtitle");

        final ArrayList<Author> authors = new ArrayList<>();
        a = document.optJSONArray("authors");
        if (a != null && !a.isEmpty()) {
            for (int ai = 0; ai < a.length(); ai++) {
                element = a.optJSONObject(ai);
                if (element != null) {
                    final String name = element.optString("name");
                    if (!name.isEmpty()) {
                        authors.add(Author.from(name));
                    }
                }
            }
        }
        if (!authors.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_AUTHOR_LIST, authors);
        }

        // s = jsonObject.optString("pagination");
        // if (!s.isEmpty()) {
        //     bookData.putString(DBDefinitions.KEY_PAGES, s);
        // } else {
        i = document.optInt("number_of_pages");
        if (i > 0) {
            bookData.putString(DBKey.PAGE_COUNT, String.valueOf(i));
        }
        // }

        element = document.optJSONObject("identifiers");
        if (element != null) {
            processIdentifiers(element, bookData);
        }

        a = document.optJSONArray("publishers");
        if (a != null && !a.isEmpty()) {
            processPublishers(a, bookData);
        }

        s = document.optString("publish_date");
        if (!s.isEmpty()) {
            final LocalDateTime date = dateParser.parse(s, getLocale(context));
            if (date != null) {
                bookData.putString(DBKey.BOOK_PUBLICATION__DATE,
                                   date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
        }

        // "subjects": [
        //            {
        //                "name": "History",
        //                "url": "https://openlibrary.org/subjects/history"
        //            },
        // could be used for genres... but the subject list for a single book can be very large


        // "notes" is a specific (set of) remarks on this particular edition of the book.
        s = document.optString("notes");
        if (!s.isEmpty()) {
            bookData.putString(DBKey.DESCRIPTION, s);
        }


        // always use the first author only for TOC entries.
        a = document.optJSONArray("table_of_contents");
        if (a != null && !a.isEmpty()) {
            final ArrayList<TocEntry> toc = new ArrayList<>();
            for (int ai = 0; ai < a.length(); ai++) {
                element = a.optJSONObject(ai);
                if (element != null) {
                    final String title = element.optString("title");
                    if (!title.isEmpty()) {
                        toc.add(new TocEntry(authors.get(0), title));
                    }
                }
            }

            if (!toc.isEmpty()) {
                bookData.putParcelableArrayList(Book.BKEY_TOC_LIST, toc);
                if (toc.size() > 1) {
                    bookData.putLong(DBKey.TOC_TYPE__BITMASK, Book.ContentType.Collection.value);
                }
            }
        }

        if (isCancelled()) {
            return;
        }

        if (fetchCovers[0]) {
            final ArrayList<String> list = parseCovers(document, validIsbn, 0);
            if (!list.isEmpty()) {
                bookData.putStringArrayList(SearchCoordinator.BKEY_FILE_SPEC_ARRAY[0], list);
            }
        }
    }

    private void processPublishers(@NonNull final JSONArray a,
                                   @NonNull final Bundle bookData) {
        JSONObject element;
        final ArrayList<Publisher> publishers = new ArrayList<>();
        for (int ai = 0; ai < a.length(); ai++) {
            element = a.optJSONObject(ai);
            if (element != null) {
                final String name = element.optString("name");
                if (!name.isEmpty()) {
                    publishers.add(Publisher.from(name));
                }
            }
        }
        if (!publishers.isEmpty()) {
            bookData.putParcelableArrayList(Book.BKEY_PUBLISHER_LIST, publishers);
        }
    }

    private void processIdentifiers(@NonNull final JSONObject element,
                                    @NonNull final Bundle bookData) {

        JSONArray a;

        // see if we have a better isbn.
        a = element.optJSONArray("isbn_13");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKey.BOOK_ISBN, a.getString(0));
        } else {
            a = element.optJSONArray("isbn_10");
            if (a != null && !a.isEmpty()) {
                bookData.putString(DBKey.BOOK_ISBN, a.getString(0));
            }
        }
        a = element.optJSONArray("amazon");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKey.SID_ASIN, a.getString(0));
        }
        a = element.optJSONArray("openlibrary");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKey.SID_OPEN_LIBRARY, a.getString(0));
        }
        a = element.optJSONArray("librarything");
        if (a != null && !a.isEmpty()) {
            bookData.putLong(DBKey.SID_LIBRARY_THING, a.getLong(0));
        }
        a = element.optJSONArray("goodreads");
        if (a != null && !a.isEmpty()) {
            bookData.putLong(DBKey.SID_GOODREADS_BOOK, a.getLong(0));
        }
        a = element.optJSONArray("google");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKey.SID_GOOGLE, a.getString(0));
        }
        a = element.optJSONArray("lccn");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKey.SID_LCCN, a.getString(0));
        }
        a = element.optJSONArray("oclc");
        if (a != null && !a.isEmpty()) {
            bookData.putString(DBKey.SID_OCLC, a.getString(0));
        }
    }

    private ArrayList<String> parseCovers(@NonNull final JSONObject element,
                                          @NonNull final String validIsbn,
                                          @SuppressWarnings("SameParameterValue")
                                          @IntRange(from = 0, to = 1) final int cIdx)
            throws StorageException {

        final ArrayList<String> list = new ArrayList<>();

        // get the largest cover image available.
        final JSONObject o = element.optJSONObject("cover");
        if (o != null) {
            Size size = Size.Large;
            String coverUrl = o.optString("large");
            if (coverUrl.isEmpty()) {
                size = Size.Medium;
                coverUrl = o.optString("medium");
                if (coverUrl.isEmpty()) {
                    size = Size.Small;
                    coverUrl = o.optString("small");
                }
            }

            // we assume that the download will work if there is a url.
            if (!coverUrl.isEmpty()) {
                final String fileSpec = saveImage(coverUrl, validIsbn, cIdx, size);
                if (fileSpec != null) {
                    list.add(fileSpec);
                }
            }
        }

        return list;
    }
}
