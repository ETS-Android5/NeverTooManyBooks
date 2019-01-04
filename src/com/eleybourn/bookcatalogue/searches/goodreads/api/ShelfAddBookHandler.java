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


import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.goodreads.GoodreadsExceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.utils.xml.XmlFilter.XmlHandler;
import com.eleybourn.bookcatalogue.utils.xml.XmlResponseParser;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.util.ArrayList;

import androidx.annotation.NonNull;

/**
 * Class to add a book to a shelf. In this case, we do not care about the data returned.
 *
 * ENHANCE: Parse the result and store it against the bookshelf in the database.
 * Currently, this is not a simple thing to do because bookshelf naming rules in
 * goodreads are much more restrictive: no spaces, punctuation (at least).
 *
 * Need to add the following to bookshelf table:
 * - gr_bookshelf_id
 * - (perhaps) gr_bookshelf_name
 *
 * @author Philip Warner
 */
public class ShelfAddBookHandler extends ApiHandler {

    private long mReviewId = 0;
    private final XmlHandler mHandleReviewId = new XmlHandler() {
        @Override
        public void process(@NonNull final ElementContext context) {

            try {
                mReviewId = Long.parseLong(context.body.trim());
            } catch (NumberFormatException ignore) {
            }
        }
    };

    /**
     * <pre>
     *  <shelf>
     *     <created-at type='datetime' nil='true'></created-at>
     *     <position type='integer' nil='true'></position>
     *     <review-id type='integer'>254171613</review-id>
     *     <updated-at type='datetime' nil='true'></updated-at>
     *     <user-shelf-id type='integer'>16480894</user-shelf-id>
     *     <name>read</name>
     *   </shelf>
     * </pre>
     */
    public ShelfAddBookHandler(@NonNull final GoodreadsManager manager) {

        super(manager);
        buildFilters();
    }

    /**
     * Add the passed book to the passed shelf
     */
    public long add(@NonNull final String shelfName, final long grBookId)
            throws IOException,
                   NotAuthorizedException,
                   BookNotFoundException {

        return doCall(shelfName, grBookId, false);
    }

    /**
     * Remove the passed book from the passed shelf
     */
    @SuppressWarnings("UnusedReturnValue")
    public long remove(@NonNull final String shelfName, final long grBookId)
            throws IOException,
                   NotAuthorizedException,
                   BookNotFoundException {

        return doCall(shelfName, grBookId, true);
    }

    /**
     * Do the main work; same API call for add & remove
     */
    private long doCall(@NonNull final String shelfName, final long grBookId, final boolean isRemove)
            throws IOException,
                   NotAuthorizedException,
                   BookNotFoundException {

        mReviewId = 0;

        HttpPost post = new HttpPost(GoodreadsManager.BASE_URL + "/shelf/add_to_shelf.xml");

        ArrayList<NameValuePair> parameters = new ArrayList<>();
        if (isRemove) {
            parameters.add(new BasicNameValuePair("a", "remove"));
        }
        parameters.add(new BasicNameValuePair("book_id", String.valueOf(grBookId)));
        parameters.add(new BasicNameValuePair("name", shelfName));

        post.setEntity(new UrlEncodedFormEntity(parameters, "UTF8"));

        // Use a parser based on the filters
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        // Send call. Errors will result in an exception.
        mManager.execute(post, handler, true);

        return mReviewId;
    }

    private void buildFilters() {
		/* Typical output.
			<shelf>
			  <created-at type='datetime'>2012-01-02T19:07:12-08:00</created-at>
			  <id type='integer'>167676018</id>
			  <position type='integer'>13</position>
			  <review-id type='integer'>255221284</review-id>
			  <updated-at type='datetime'>2012-01-02T19:07:12-08:00</updated-at>
			  <user-shelf-id type='integer'>16737904</user-shelf-id>
			  <name>sci-fi-fantasy</name>
			</shelf>
		 */
        // We only care about review-id:
        XmlFilter.buildFilter(mRootFilter, XML_SHELF, XML_REVIEW_ID)
                .setEndAction(mHandleReviewId);
    }
}
