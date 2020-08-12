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
package com.hardbacknutter.nevertoomanybooks.searches.lastdodo;

import java.util.ArrayList;
import java.util.Locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hardbacknutter.nevertoomanybooks.JSoupBase;
import com.hardbacknutter.nevertoomanybooks._mocks.MockCaller;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.entities.Author;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.entities.Publisher;
import com.hardbacknutter.nevertoomanybooks.entities.Series;
import com.hardbacknutter.nevertoomanybooks.searches.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searches.SearchSites;
import com.hardbacknutter.nevertoomanybooks.searches.Site;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LastDodoTest
        extends JSoupBase {

    private LastDodoSearchEngine mSearchEngine;

    @BeforeEach
    public void setUp() {
        super.setUp();
        SearchEngineRegistry.create(mContext);
        mSearchEngine = (LastDodoSearchEngine) Site.Type.Data
                .getSite(SearchSites.LAST_DODO).getSearchEngine(mContext, new MockCaller());
    }

    @Test
    void parse01() {
        setLocale(Locale.FRANCE);
        //final String locationHeader = "https://www.lastdodo.nl/search?q=9789463064385&type=147";

        final String locationHeader = "https://www.lastdodo.nl/catalogus/strips/series-helden/"
                                      + "hauteville-house/7323911-de-37ste-parallel";
        final String filename = "/lastdodo/7323911-de-37ste-parallel.html";

        loadData(mSearchEngine, "UTF-8", locationHeader, filename, new boolean[]{false, false});

        assertEquals("De 37ste parallel", mRawData.getString(DBDefinitions.KEY_TITLE));
        assertEquals("9789463064385", mRawData.getString(DBDefinitions.KEY_ISBN));
        assertEquals("2018", mRawData.getString(DBDefinitions.KEY_DATE_PUBLISHED));
        assertEquals("48", mRawData.getString(DBDefinitions.KEY_PAGES));
        assertEquals("Hardcover", mRawData.getString(DBDefinitions.KEY_FORMAT));
        assertEquals("nld", mRawData.getString(DBDefinitions.KEY_LANGUAGE));
        assertEquals("Gekleurd", mRawData.getString(DBDefinitions.KEY_COLOR));

        final ArrayList<Publisher> allPublishers = mRawData
                .getParcelableArrayList(Book.BKEY_PUBLISHER_ARRAY);
        assertNotNull(allPublishers);
        assertEquals(1, allPublishers.size());
        assertEquals("Silvester", allPublishers.get(0).getName());

        final ArrayList<Series> allSeries = mRawData.getParcelableArrayList(Book.BKEY_SERIES_ARRAY);
        assertNotNull(allSeries);
        assertEquals(1, allSeries.size());

        Series series = allSeries.get(0);
        assertEquals("Hauteville House", series.getTitle());
        assertEquals("14", series.getNumber());

        final ArrayList<Author> authors = mRawData.getParcelableArrayList(Book.BKEY_AUTHOR_ARRAY);
        assertNotNull(authors);
        assertEquals(2, authors.size());

        Author author = authors.get(1);
        assertEquals("Duval", author.getFamilyName());
        assertEquals("Fred", author.getGivenNames());
        assertEquals(Author.TYPE_WRITER, author.getType());

        author = authors.get(0);
        assertEquals("Gioux", author.getFamilyName());
        assertEquals("Thierry", author.getGivenNames());
        assertEquals(Author.TYPE_ARTIST, author.getType());

//        author = authors.get(2);
//        assertEquals("Sayago", author.getFamilyName());
//        assertEquals("Nuria", author.getGivenNames());
//        assertEquals(Author.TYPE_COLORIST, author.getType());
    }
}
