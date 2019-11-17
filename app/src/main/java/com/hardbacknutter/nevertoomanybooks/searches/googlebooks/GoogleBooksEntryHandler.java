/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.searches.googlebooks;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Locale;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.UniqueId;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;

/**
 * An XML handler for the Google Books entry return.
 *
 * <pre>
 *   {@code
 *   <?xml version='1.0' encoding='UTF-8'?>
 *   <entry xmlns='http://www.w3.org/2005/Atom'
 *          xmlns:gbs='http://schemas.google.com/books/2008'
 *          xmlns:dc='http://purl.org/dc/terms'
 *          xmlns:batch='http://schemas.google.com/gdata/batch'
 *          xmlns:gd='http://schemas.google.com/g/2005'>
 *
 *   <id>http://www.google.com/books/feeds/volumes/A4NDPgAACAAJ</id>
 *   <updated>2010-02-28T10:49:24.000Z</updated>
 *   <category scheme='http://schemas.google.com/g/2005#kind'
 *     term='http://schemas.google.com/books/2008#volume'/>
 *   <title type='text'>The trigger</title>
 *   <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *     href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;ie=ISO-8859-1&amp;source=gbs_gdata'/>
 *   <link rel='http://schemas.google.com/books/2008/annotation'
 *     type='application/atom+xml' href='http://www.google.com/books/feeds/users/me/volumes'/>
 *   <link rel='alternate' type='text/html'
 *     href='http://books.google.com/books?id=A4NDPgAACAAJ&amp;ie=ISO-8859-1'/>
 *   <link rel='self' type='application/atom+xml'
 *     href='http://www.google.com/books/feeds/volumes/A4NDPgAACAAJ'/>
 *   <gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 *   <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 *   <gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 *   <dc:creator>Arthur Charles Clarke</dc:creator>
 *   <dc:creator>Michael P. Kube-McDowell</dc:creator>
 *   <dc:date>2000-01-01</dc:date>
 *   <dc:format>Dimensions 11.0x18.0x3.6 cm</dc:format>
 *   <dc:format>550 pages</dc:format>
 *   <dc:format>book</dc:format>
 *   <dc:identifier>A4NDPgAACAAJ</dc:identifier>
 *   <dc:identifier>ISBN:0006483836</dc:identifier>
 *   <dc:identifier>ISBN:9780006483830</dc:identifier>
 *   <dc:language>en</dc:language>
 *   <dc:publisher>Voyager</dc:publisher>
 *   <dc:subject>Fiction / Science Fiction / General</dc:subject>
 *   <dc:subject>Fiction / Technological</dc:subject>
 *   <dc:subject>Fiction / War &amp; Military</dc:subject>
 *   <dc:title>The trigger</dc:title>
 * </entry>
 *
 * <?xml version='1.0' encoding='UTF-8'?>
 * <entry xmlns='http://www.w3.org/2005/Atom'
 *        xmlns:gbs='http://schemas.google.com/books/2008'
 *        xmlns:dc='http://purl.org/dc/terms'
 *        xmlns:batch='http://schemas.google.com/gdata/batch'
 *        xmlns:gd='http://schemas.google.com/g/2005'>
 *
 * <id>http://www.google.com/books/feeds/volumes/lf2EMetoLugC</id>
 * <updated>2010-03-01T07:31:23.000Z</updated>
 * <category scheme='http://schemas.google.com/g/2005#kind'
 *           term='http://schemas.google.com/books/2008#volume'/>
 * <title type='text'>The Geeks' Guide to World Domination</title>
 * <link rel='http://schemas.google.com/books/2008/thumbnail' type='image/x-unknown'
 *      href='http://bks3.books.google.com/books?
 *      id=lf2EMetoLugC
 *      &amp;printsec=frontcover
 *      &amp;img=1
 *      &amp;zoom=5
 *      &amp;sig=ACfU3U1hcfy_NvWZbH46OzWwmQQCDV46lA
 *      &amp;source=gbs_gdata'/>
 * <link rel='http://schemas.google.com/books/2008/info' type='text/html'
 *     href='http://books.google.com/books?id=lf2EMetoLugC&amp;ie=ISO-8859-1&amp;source=gbs_gdata'/>
 * <link rel='http://schemas.google.com/books/2008/annotation' type='application/atom+xml'
 *      href='http://www.google.com/books/feeds/users/me/volumes'/>
 * <link rel='alternate' type='text/html'
 *      href='http://books.google.com/books?id=lf2EMetoLugC&amp;ie=ISO-8859-1'/>
 * <link rel='self' type='application/atom+xml'
 *      href='http://www.google.com/books/feeds/volumes/lf2EMetoLugC'/>
 * <gbs:embeddability value='http://schemas.google.com/books/2008#not_embeddable'/>
 * <gbs:openAccess value='http://schemas.google.com/books/2008#disabled'/>
 * <gbs:viewability value='http://schemas.google.com/books/2008#view_no_pages'/>
 * <dc:creator>Garth Sundem</dc:creator>
 * <dc:date>2009-03-10</dc:date>
 * <dc:description>These days, from blah blah ....the Geek Wars have</dc:description>
 * <dc:format>Dimensions 13.2x20.1x2.0 cm</dc:format>
 * <dc:format>288 pages</dc:format>
 * <dc:format>book</dc:format>
 * <dc:identifier>lf2EMetoLugC</dc:identifier>
 * <dc:identifier>ISBN:0307450341</dc:identifier>
 * <dc:identifier>ISBN:9780307450340</dc:identifier>
 * <dc:language>en</dc:language>
 * <dc:publisher>Three Rivers Press</dc:publisher>
 * <dc:subject>Curiosities and wonders/ Humor</dc:subject>
 * <dc:subject>Geeks (Computer enthusiasts)/ Humor</dc:subject>
 * <dc:subject>Curiosities and wonders</dc:subject>
 * <dc:subject>Geeks (Computer enthusiasts)</dc:subject>
 * <dc:subject>Humor / Form / Parodies</dc:subject>
 * <dc:subject>Humor / General</dc:subject>
 * <dc:subject>Humor / General</dc:subject>
 * <dc:subject>Humor / Form / Comic Strips &amp; Cartoons</dc:subject>
 * <dc:subject>Humor / Form / Essays</dc:subject>
 * <dc:subject>Humor / Form / Parodies</dc:subject>
 * <dc:subject>Reference / General</dc:subject>
 * <dc:subject>Reference / Curiosities &amp; Wonders</dc:subject>
 * <dc:subject>Reference / Encyclopedias</dc:subject>
 * <dc:title>The Geeks' Guide to World Domination</dc:title>
 * <dc:title>Be Afraid, Beautiful People</dc:title>
 * </entry>
 * }
 * </pre>
 * <p>
 * 0340198273
 */
class GoogleBooksEntryHandler
        extends DefaultHandler {

    private static final String TAG = "GoogleBooksEntryHandler";

    /** file suffix for cover files. */
    private static final String FILENAME_SUFFIX = "_GB";

    /* XML tags/attrs we look for. */
    /**
     * Contains a direct link to this entry.
     * <id>http://www.google.com/books/feeds/volumes/IVnpNAAACAAJ</id>
     */
    private static final String ID = "id";
    /** <dc:creator>Jack Vance</dc:creator>. */
    private static final String XML_AUTHOR = "creator";
    /** <dc:title>The Anome</dc:title>. */
    private static final String XML_TITLE = "title";
    /**
     * First one is the google book id. Not worth storing for now.
     * <dc:identifier>IVnpNAAACAAJ</dc:identifier>
     * <dc:identifier>ISBN:0340198273</dc:identifier>
     * <dc:identifier>ISBN:9780340198278</dc:identifier>
     */
    private static final String XML_ISBN = "identifier";
    /**
     * An SQL date String.
     * <dc:date>1977</dc:date>
     * <dc:date>2011-12-19</dc:date>
     */
    private static final String XML_DATE_PUBLISHED = "date";
    /** <dc:publisher>Coronet</dc:publisher>. */
    private static final String XML_PUBLISHER = "publisher";
    /**
     * Rather annoyingly, can contain seemingly non-structured text.
     * <dc:format>206 pages</dc:format>
     * <dc:format>book</dc:format>
     */
    private static final String XML_FORMAT = "format";

    private static final String XML_LINK = "link";
    /** <dc:subject>English fiction</dc:subject>. */
    private static final String XML_GENRE = "subject";
    /** <dc:description>If they were to fight ... </dc:description>. */
    private static final String XML_DESCRIPTION = "description";
    /** <dc:language>en</dc:language>. */
    private static final String XML_LANGUAGE = "language";

    //  *      <gbs:price type='SuggestedRetailPrice'>
    // *        <gd:money amount='3.99' currencyCode='GBP'/>
    // *      </gbs:price>
    // *      <gbs:price type='RetailPrice'>
    // *        <gd:money amount='3.99' currencyCode='GBP'/>
    // *      </gbs:price>
    private static final String XML_PRICE = "price";
    private static final String XML_PRICE_SUGGESTED_RETAIL_PRICE = "SuggestedRetailPrice";
    private static final String XML_PRICE_RETAIL_PRICE = "RetailPrice";
    private static final String XML_MONEY = "money";


    /** flag if we should fetch a thumbnail. */
    private final boolean mFetchThumbnail;
    /** Bundle to save results in. */
    @NonNull
    private final Bundle mBookData;

    /** accumulate all authors for this book. */
    @NonNull
    private final ArrayList<Author> mAuthors = new ArrayList<>();
    /** accumulate all Publishers for this book. */
    private final ArrayList<Publisher> mPublishers = new ArrayList<>();


    /** XML content. */
    private final StringBuilder mBuilder = new StringBuilder();

    private final Locale mLocale;

    private boolean mInSuggestedRetailPriceTag;
    private boolean mInRetailPriceTag;

    /**
     * Constructor.
     *
     * @param bookData       Bundle to save results in
     * @param fetchThumbnail Set to {@code true} if we want to get a thumbnail
     */
    GoogleBooksEntryHandler(@NonNull final Bundle /* out */ bookData,
                            final boolean fetchThumbnail) {
        mBookData = bookData;
        mFetchThumbnail = fetchThumbnail;

        mLocale = App.getSystemLocale();
    }

    /**
     * Not present means either "not there" or "there, but empty".
     *
     * @param key   to use
     * @param value to store
     */
    private void addIfNotPresent(@NonNull final String key,
                                 @NonNull final String value) {
        String test = mBookData.getString(key);
        if (test == null || test.isEmpty()) {
            mBookData.putString(key, value);
        }
    }

    /**
     * Store the accumulated data in the results.
     */
    @Override
    public void endDocument() {

        if (!mAuthors.isEmpty()) {
            mBookData.putParcelableArrayList(UniqueId.BKEY_AUTHOR_ARRAY, mAuthors);
        }
        if (!mPublishers.isEmpty()) {
            mBookData.putParcelableArrayList(UniqueId.BKEY_PUBLISHER_ARRAY, mPublishers);
        }
    }

    /**
     * Start each XML element. Specifically identify when we are in the item
     * element and set the appropriate flag.
     */
    @Override
    @CallSuper
    public void startElement(@NonNull final String uri,
                             @NonNull final String localName,
                             @NonNull final String qName,
                             @NonNull final Attributes attributes)
            throws SAXException {

        // the url is an attribute of the xml element; not the content
        if (mFetchThumbnail && XML_LINK.equalsIgnoreCase(localName)) {
            // http; this is a schema and not an actual website url
            if ("http://schemas.google.com/books/2008/thumbnail"
                    .equals(attributes.getValue("", "rel"))) {

                String coverUrl = attributes.getValue("", "href");
                String name = mBookData.getString(DBDefinitions.KEY_ISBN, "");
                if (name.isEmpty()) {
                    // just use something...
                    name = String.valueOf(System.currentTimeMillis());
                }
                String fileSpec = ImageUtils.saveImage(coverUrl, name, FILENAME_SUFFIX, null);
                if (fileSpec != null) {
                    ArrayList<String> imageList =
                            mBookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
                    if (imageList == null) {
                        imageList = new ArrayList<>();
                    }
                    imageList.add(fileSpec);
                    mBookData.putStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY, imageList);
                }
            }
        } else if (XML_PRICE.equalsIgnoreCase(localName)) {
            switch (attributes.getValue("", "type")) {
                case XML_PRICE_SUGGESTED_RETAIL_PRICE:
                    mInSuggestedRetailPriceTag = true;
                    break;

                case XML_PRICE_RETAIL_PRICE:
                    mInRetailPriceTag = true;
                    break;

                default:
                    throw new SAXException(XML_PRICE + ", attributes: " + attributes);
            }
        } else if (XML_MONEY.equalsIgnoreCase(localName)) {

            if (mInSuggestedRetailPriceTag) {
                try {
                    double amount = Double.parseDouble(attributes.getValue("", "amount"));
                    mBookData.putDouble(DBDefinitions.KEY_PRICE_LISTED, amount);

                    String currencyCode = attributes.getValue("", "currencyCode");
                    mBookData.putString(DBDefinitions.KEY_PRICE_LISTED_CURRENCY, currencyCode);
                } catch (NumberFormatException ignore) {
                }
                mInSuggestedRetailPriceTag = false;

            } else if (mInRetailPriceTag) {
                // future use ?
                mInRetailPriceTag = false;

            }
        }
    }

    /**
     * Populate the results Bundle for each appropriate element.
     */
    @Override
    @CallSuper
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String qName) {

        switch (localName.toLowerCase(mLocale)) {
            case XML_TITLE:
                // there can be multiple listed, we only take the first one found
                addIfNotPresent(DBDefinitions.KEY_TITLE, mBuilder.toString());
                break;

            case XML_ISBN:
                // there can be multiple listed, we take the 'longest'
                String tmpIsbn = mBuilder.toString();
                if (tmpIsbn.indexOf("ISBN:") == 0) {
                    tmpIsbn = tmpIsbn.substring(5);
                    String isbn = mBookData.getString(DBDefinitions.KEY_ISBN);
                    // store the 'longest' isbn
                    if (isbn == null || tmpIsbn.length() > isbn.length()) {
                        mBookData.putString(DBDefinitions.KEY_ISBN, tmpIsbn);
                    }
                }
                break;

            case XML_LANGUAGE:
                // the language field can be empty, so check before storing it
                String iso = mBuilder.toString();
                if (!iso.isEmpty()) {
                    // the language is a proper iso code, just store.
                    addIfNotPresent(DBDefinitions.KEY_LANGUAGE, iso);
                }
                break;

            case XML_AUTHOR:
                mAuthors.add(Author.fromString(mBuilder.toString()));
                break;

            case XML_PUBLISHER:
                mPublishers.add(Publisher.fromString(mBuilder.toString()));
                break;

            case XML_DATE_PUBLISHED:
                addIfNotPresent(DBDefinitions.KEY_DATE_PUBLISHED, mBuilder.toString());
                break;

            case XML_FORMAT:
                /*
                 * <dc:format>Dimensions 13.2x20.1x2.0 cm</dc:format>
                 * <dc:format>288 pages</dc:format>
                 * <dc:format>book</dc:format>
                 *
                 * crude check for 'pages'
                 */
                String tmpFormat = mBuilder.toString();
                int index = tmpFormat.indexOf(" pages");
                if (index > -1) {
                    mBookData.putString(DBDefinitions.KEY_PAGES,
                                        tmpFormat.substring(0, index).trim());
                }
                break;

            case XML_GENRE:
                // there can be multiple listed, we only take the first one found
                // 2019-07-17: previously, we took the last one listed.
                addIfNotPresent(DBDefinitions.KEY_GENRE, mBuilder.toString());
                break;

            case XML_DESCRIPTION:
                addIfNotPresent(DBDefinitions.KEY_DESCRIPTION, mBuilder.toString());
                break;

            default:
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.XML) {
                    // see what we are missing.
                    Log.d(TAG, "endElement|Skipping|" + localName + "->`" + mBuilder + '`');
                }
                break;

        }

        // Always reset the length. This is not entirely the right thing to do, but works
        // because we always want strings from the lowest level (leaf) XML elements.
        // To be completely correct, we should maintain a stack of builders that are pushed and
        // popped as each startElement/endElement is called. But lets not be pedantic for now.
        mBuilder.setLength(0);
    }

    @Override
    @CallSuper
    public void characters(@NonNull final char[] ch,
                           final int start,
                           final int length) {
        mBuilder.append(ch, start, length);
    }
}
