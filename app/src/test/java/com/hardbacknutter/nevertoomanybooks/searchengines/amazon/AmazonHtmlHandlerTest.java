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
package com.hardbacknutter.nevertoomanybooks.searchengines.amazon;

import java.util.ArrayList;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCancellable;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;
import com.hardbacknutter.nevertoomanybooks.utils.Money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AmazonHtmlHandlerTest
        extends JSoupBase {

    private static final String UTF_8 = "UTF-8";

    private AmazonSearchEngine mSearchEngine;

    @BeforeEach
    public void setup()
            throws ParserConfigurationException, SAXException {
        super.setup();
        mSearchEngine = (AmazonSearchEngine) Site.Type.Data
                .getSite(SearchSites.AMAZON).getSearchEngine();
        mSearchEngine.setCaller(new MockCancellable());
    }

    @Test
    void parse01() {
        setLocale(Locale.UK);

        final String locationHeader = "https://www.amazon.co.uk/gp/product/0575090677";
        final String filename = "/amazon/0575090677.html";

        loadData(mContext, mSearchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{false, false});

        assertEquals("Bone Silence", mRawData.getString(DBKey.TITLE));
        assertEquals("978-0575090675", mRawData.getString(DBKey.BOOK_ISBN));
        assertEquals("30 Jan. 2020", mRawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("608", mRawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Hardcover", mRawData.getString(DBKey.FORMAT));
        assertEquals("eng", mRawData.getString(DBKey.LANGUAGE));
        assertEquals(14.49d, mRawData.getDouble(DBKey.PRICE_LISTED));
        assertEquals(Money.GBP, mRawData.getString(DBKey.PRICE_LISTED_CURRENCY));

        final ArrayList<Publisher> allPublishers = mRawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());

        assertEquals("Gollancz", allPublishers.get(0).getName());

        final ArrayList<Author> authors = mRawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertNotNull(authors);
        assertEquals(1, authors.size());
        assertEquals("Reynolds", authors.get(0).getFamilyName());
        assertEquals("Alastair", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
    }

    @Test
    void parse02() {
        setLocale(Locale.UK);
        final String locationHeader = "https://www.amazon.co.uk/gp/product/1473210208";
        final String filename = "/amazon/1473210208.html";

        loadData(mContext, mSearchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{false, false});

        assertEquals("The Medusa Chronicles", mRawData.getString(DBKey.TITLE));
        assertEquals("978-1473210202", mRawData.getString(DBKey.BOOK_ISBN));
        assertEquals("12 Jan. 2017", mRawData.getString(DBKey.BOOK_PUBLICATION__DATE));
        assertEquals("336", mRawData.getString(DBKey.PAGE_COUNT));
        assertEquals("Paperback", mRawData.getString(DBKey.FORMAT));
        assertEquals("eng", mRawData.getString(DBKey.LANGUAGE));
        assertEquals(5.84d, mRawData.getDouble(DBKey.PRICE_LISTED));
        assertEquals(Money.GBP, mRawData.getString(DBKey.PRICE_LISTED_CURRENCY));

        final ArrayList<Publisher> allPublishers = mRawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Gollancz", allPublishers.get(0).getName());

        final ArrayList<Author> authors = mRawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Reynolds", authors.get(0).getFamilyName());
        assertEquals("Alastair", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
        assertEquals("Baxter", authors.get(1).getFamilyName());
        assertEquals("Stephen", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(1).getType());
    }

    @Test
    void parse10() {
        setLocale(Locale.FRANCE);
        final String locationHeader = "https://www.amazon.fr/gp/product/2205057332";
        final String filename = "/amazon/2205057332.html";

        loadData(mContext, mSearchEngine, UTF_8, locationHeader, filename,
                 new boolean[]{false, false});

        assertEquals("Le retour à la terre, 1 : La vraie vie",
                     mRawData.getString(DBKey.TITLE));
        assertEquals("978-2205057331", mRawData.getString(DBKey.BOOK_ISBN));
        assertEquals(12d, mRawData.getDouble(DBKey.PRICE_LISTED));
        assertEquals(Money.EUR, mRawData.getString(DBKey.PRICE_LISTED_CURRENCY));

        final ArrayList<Publisher> allPublishers = mRawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_LIST);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Dargaud", allPublishers.get(0).getName());

        final ArrayList<Author> authors = mRawData.getParcelableArrayList(Book.BKEY_AUTHOR_LIST);
        assertNotNull(authors);
        assertEquals(2, authors.size());
        assertEquals("Jean-Yves", authors.get(0).getFamilyName());
        assertEquals("Ferri", authors.get(0).getGivenNames());
        assertEquals(Author.TYPE_WRITER, authors.get(0).getType());
        assertEquals("Manu", authors.get(1).getFamilyName());
        assertEquals("Larcenet", authors.get(1).getGivenNames());
        assertEquals(Author.TYPE_ARTIST, authors.get(1).getType());
    }
}
