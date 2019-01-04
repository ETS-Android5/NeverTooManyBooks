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

package com.eleybourn.bookcatalogue.searches.goodreads.api;

import android.os.Bundle;

import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * Class to call the search.books api (using a Goodreads 'work' ID).
 *
 * @author Philip Warner
 */
public class ShowBookByIdApiHandler extends ShowBookApiHandler {

    public ShowBookByIdApiHandler(@NonNull final GoodreadsManager manager) {

        super(manager, true);
    }

    /**
     * Perform a search and handle the results.
     *
     * @return the array of GoodreadsWork objects.
     */
    @NonNull
    public Bundle get(final long workId, final boolean fetchThumbnail)
            throws NotAuthorizedException,
                   BookNotFoundException,
                   IOException {

        // Setup API call
        final String urlBase = GoodreadsManager.BASE_URL + "/book/show/%1$s.xml?key=%2$s";
        final String url = String.format(urlBase, workId, mManager.getDevKey());
        HttpGet get = new HttpGet(url);

        return sendRequest(get, fetchThumbnail);
    }
}
