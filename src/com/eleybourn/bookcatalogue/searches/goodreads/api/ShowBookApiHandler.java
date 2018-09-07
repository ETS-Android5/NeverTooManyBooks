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

import com.eleybourn.bookcatalogue.Author;
import com.eleybourn.bookcatalogue.Series;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.database.dbaadapter.ColumnNames;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.searches.SearchManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.BookNotFoundException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NetworkException;
import com.eleybourn.bookcatalogue.searches.goodreads.GoodreadsManager.Exceptions.NotAuthorizedException;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter.XmlHandler;
import com.eleybourn.bookcatalogue.utils.ArrayUtils;
import com.eleybourn.bookcatalogue.utils.ImageUtils;

import org.apache.http.client.methods.HttpGet;

import java.io.IOException;
import java.util.ArrayList;

import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.BOOK_ID;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.BOOK_URL;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.IMAGE;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.ISBN13;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.IS_EBOOK;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.ORIG_PUBLICATION_DAY;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.ORIG_PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.ORIG_PUBLICATION_YEAR;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.ORIG_TITLE;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.PUBLICATION_DAY;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.PUBLICATION_MONTH;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.PUBLICATION_YEAR;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.RATING;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.REVIEW_ID;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.SHELVES;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.SMALL_IMAGE;
import static com.eleybourn.bookcatalogue.searches.goodreads.api.ShowBookApiHandler.ShowBookFieldNames.WORK_ID;

/**
 * Class to query and response to search.books api call. This is an abstract class
 * designed to be used by other classes that implement specific search methods. It does
 * the heavy lifting of parsing the results etc.
 *
 * @author Philip Warner
 */
public abstract class ShowBookApiHandler extends ApiHandler {

    private static final String GOODREADS_RESPONSE = "GoodreadsResponse";
    private static final String BOOK = "book";
    private static final String BVAL_FORMAT_EBOOK = "Ebook";
    /**
     * Flag to indicate if request should be signed. Signed requests via ISB cause server errors
     * and unsigned requests do not return review (not a big problem for searches)
     */
    private final boolean mSignRequest;
    private final XmlHandler mHandleSeriesStart = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
            //mCurrSeries = new Series();
        }
    };
    private final XmlHandler mHandleSeriesId = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
			/*
			try {
				mCurrSeriesId = Integer.parseInt(context.body.trim());
			} catch (Exception ignore) {
			}
			*/
        }
    };
    private final XmlHandler mHandleAuthorStart = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
            //mCurrAuthor = new Author();
        }
    };
    private final XmlHandler mHandleAuthorId = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
			/*
			try {
				mCurrAuthorId = Long.parseLong(context.body.trim());
			} catch (Exception ignore) {
			}
			*/
        }
    };
    /**
     * Transient global data for current work in search results.
     */
    private Bundle mBook;

    // Current author being processed
    //private long mCurrAuthorId = 0;
    private final XmlHandler mHandleText = new XmlHandler() {

        @Override
        public void process(ElementContext context) {
            final String name = (String) context.userArg;
            mBook.putString(name, context.body.trim());
        }
    };
    private final XmlHandler mHandleLong = new XmlHandler() {

        @Override
        public void process(ElementContext context) {
            final String name = (String) context.userArg;
            try {
                long l = Long.parseLong(context.body.trim());
                mBook.putLong(name, l);
            } catch (Exception e) {
                // Ignore but dont add
            }
        }
    };

    // Current series being processed
    //private int mCurrSeriesId = 0;
    private final XmlHandler mHandleFloat = new XmlHandler() {

        @Override
        public void process(ElementContext context) {
            final String name = (String) context.userArg;
            try {
                double d = Double.parseDouble(context.body.trim());
                mBook.putDouble(name, d);
            } catch (Exception e) {
                // Ignore but dont add
            }
        }
    };
    private final XmlHandler mHandleBoolean = new XmlHandler() {

        @Override
        public void process(ElementContext context) {
            final String name = (String) context.userArg;
            try {
                String s = context.body.trim();
                boolean b;
                if (s.isEmpty()) {
                    b = false;
                } else if ("false".equalsIgnoreCase(s)) {
                    b = false;
                } else if ("true".equalsIgnoreCase(s)) {
                    b = true;
                } else if ("f".equalsIgnoreCase(s)) {
                    b = false;
                } else if ("t".equalsIgnoreCase(s)) {
                    b = true;
                } else {
                    long l = Long.parseLong(s);
                    b = (l != 0);
                }
                mBook.putBoolean(name, b);
            } catch (Exception ignore) {
            }
        }
    };
    /**
     * Local storage for series book appears in
     */
    private ArrayList<Series> mSeries = null;

	/*
	 * Typical result:

			<GoodreadsResponse>
				<Request>
					<authentication>true</authentication>
					<key>GJ59HZyvOM5KGm6Wn8GDzg</key>
					<method>book_show</method>
				</Request>
				<book>
					<id>50</id>
					<title>Hatchet (Hatchet, #1)</title>
					<isbn>0689840926</isbn>
					<isbn13>9780689840920</isbn13>
					<asin></asin>
					<image_url>http://www.goodreads.com/images/nocover-111x148.jpg</image_url>
					<small_image_url>http://www.goodreads.com/images/nocover-60x80.jpg</small_image_url>
					<publication_year>2000</publication_year>
					<publication_month>4</publication_month>
					<publication_day>1</publication_day>
					<publisher/><language_code/>
					<is_ebook>false</is_ebook>
					<description><p>Since it was first published in 1987, the story of thirteen-year-old Brian Robeson's survival following a plane crash has become a modern classic. Stranded in the desolate wilderness, Brian uses his instincts and his hatchet to stay alive for fifty-four harrowing days. <p> This twentieth-anniversary edition of <em>Hatchet</em> contains a new introduction and sidebar commentary by Gary Paulsen, written especially for this volume. Drew Willis's detailed pen-and-ink illustrations complement the descriptions in the text and add a new dimension to the book. This handsome edition of the Newbery Honor book will be treasured by <em>Hatchet</em> fans as well as by readers encountering Brian's unforgettable story for the first time.</p></p></description>
					<work>
						<best_book_id type="integer">50</best_book_id>
						<books_count type="integer">47</books_count>
						<id type="integer">1158125</id>
						<media_type>book</media_type>
						<original_language_id type="integer" nil="true"/>
						<original_publication_day type="integer">1</original_publication_day>
						<original_publication_month type="integer">1</original_publication_month>
						<original_publication_year type="integer">1987</original_publication_year>
						<original_title>Hatchet</original_title>
						<rating_dist>5:12626|4:17440|3:15621|2:6008|1:2882|total:54577</rating_dist>
						<ratings_count type="integer">54545</ratings_count>
						<ratings_sum type="integer">194541</ratings_sum>
						<reviews_count type="integer">64752</reviews_count>
						<text_reviews_count type="integer">3705</text_reviews_count>
					</work>
					<average_rating>3.57</average_rating>
					<num_pages>208</num_pages>
					<format>Hardcover</format>
					<edition_information></edition_information>
					<ratings_count>51605</ratings_count>
					<text_reviews_count>3299</text_reviews_count>
					<url>http://www.goodreads.com/book/show/50.Hatchet</url>
					<link>http://www.goodreads.com/book/show/50.Hatchet</link>
					<authors>
						<author>
							<id>18</id>
							<name>Gary Paulsen</name>
							<image_url>http://photo.goodreads.com/authors/1309159225p5/18.jpg</image_url>
							<small_image_url>http://photo.goodreads.com/authors/1309159225p2/18.jpg</small_image_url>
							<link>http://www.goodreads.com/author/show/18.Gary_Paulsen</link>
							<average_rating>3.64</average_rating>
							<ratings_count>92755</ratings_count>
							<text_reviews_count>9049</text_reviews_count>
						</author>
					</authors>
					<my_review>
						<id>255221284</id>
						<rating>0</rating>
						<votes>0</votes>
						<spoiler_flag>false</spoiler_flag>
						<spoilers_state>none</spoilers_state>
						<shelves>
							<shelf name="sci-fi-fantasy"/>
							<shelf name="to-read"/>
							<shelf name="default"/>
							<shelf name="environment"/>
							<shelf name="games"/>
							<shelf name="history"/>
						</shelves>
						<recommended_for></recommended_for>
						<recommended_by></recommended_by>
						<started_at/>
						<read_at/>
						<date_added>Mon Jan 02 19:07:11 -0800 2012</date_added>
						<date_updated>Sat Mar 03 08:10:09 -0800 2012</date_updated>
						<read_count/>
						<body>Test again</body>
						<comments_count>0</comments_count>
						<url>http://www.goodreads.com/review/show/255221284</url>
						<link>http://www.goodreads.com/review/show/255221284</link>
						<owned>0</owned>
					</my_review>
   					<friend_reviews>
					</friend_reviews>
					<reviews_widget>....</reviews_widget>
					<popular_shelves>
						<shelf name="to-read" count="3496"/>
						<shelf name="young-adult" count="810"/>
						<shelf name="fiction" count="537"/>
						<shelf name="currently-reading" count="284"/>
						<shelf name="adventure" count="247"/>
						<shelf name="childrens" count="233"/>
						<shelf name="ya" count="179"/>
						<shelf name="survival" count="170"/>
						<shelf name="favorites" count="164"/>
						<shelf name="classics" count="155"/>
					</popular_shelves>
					<book_links>
						<book_link>
							<id>3</id>
							<name>Barnes & Noble</name>
							<link>http://www.goodreads.com/book_link/follow/3?book_id=50</link>
						</book_link>
						<book_link>
							<id>8</id>
							<name>WorldCat</name>
							<link>http://www.goodreads.com/book_link/follow/8?book_id=50</link>
						</book_link>
						<book_link>
							<id>1027</id>
							<name>Kobo</name>
							<link>http://www.goodreads.com/book_link/follow/1027?book_id=50</link>
						</book_link>
						<book_link>
							<id>9</id>
							<name>Indigo</name>
							<link>http://www.goodreads.com/book_link/follow/9?book_id=50</link>
						</book_link>
						<book_link><id>4</id><name>Abebooks</name><link>http://www.goodreads.com/book_link/follow/4?book_id=50</link></book_link>
						<book_link><id>2</id><name>Half.com</name><link>http://www.goodreads.com/book_link/follow/2?book_id=50</link></book_link>
						<book_link><id>10</id><name>Audible</name><link>http://www.goodreads.com/book_link/follow/10?book_id=50</link></book_link>
						<book_link><id>5</id><name>Alibris</name><link>http://www.goodreads.com/book_link/follow/5?book_id=50</link></book_link>
						<book_link><id>2102</id><name>iBookstore</name><link>http://www.goodreads.com/book_link/follow/2102?book_id=50</link></book_link>
						<book_link><id>1602</id><name>Google eBooks</name><link>http://www.goodreads.com/book_link/follow/1602?book_id=50</link></book_link>
						<book_link><id>107</id><name>Better World Books</name><link>http://www.goodreads.com/book_link/follow/107?book_id=50</link></book_link>
						<book_link><id>7</id><name>IndieBound</name><link>http://www.goodreads.com/book_link/follow/7?book_id=50</link></book_link>
						<book_link><id>1</id><name>Amazon</name><link>http://www.goodreads.com/book_link/follow/1?book_id=50</link></book_link>
					</book_links>
					<series_works>
						<series_work>
							<id>268218</id>
							<user_position>1</user_position>
							<series>
								<id>62223</id>
								<title>Brian's Saga</title>
								<description></description>
								<note></note>
								<series_works_count>7</series_works_count>
								<primary_work_count>5</primary_work_count>
								<numbered>true</numbered>
							</series>
						</series_work>
					</series_works>
				</book>
			</GoodreadsResponse>

	 */
    /**
     * Local storage for series book appears in
     */
    private ArrayList<Author> mAuthors = null;
    /**
     * Local storage for shelf names
     */
    private ArrayList<String> mShelves = null;
    /**
     * Create a new shelves collection when the "shelves" tag is encountered.
     */
    private final XmlHandler mHandleShelvesStart = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
            mShelves = new ArrayList<>();
        }
    };
    /**
     * Add a shelf to the array
     */
    private final XmlHandler mHandleShelf = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
            String name;
            try {
                name = context.attributes.getValue("name");
                mShelves.add(name);
            } catch (Exception e) {
                Logger.logError(e);
            }
        }
    };
    /**
     * Current author being processed
     */
    private String mCurrAuthorName = null;
    private final XmlHandler mHandleAuthorEnd = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
            if (mCurrAuthorName != null && mCurrAuthorName.length() > 0) {
                if (mAuthors == null)
                    mAuthors = new ArrayList<>();
                mAuthors.add(new Author(mCurrAuthorName));
                mCurrAuthorName = null;
            }
        }
    };
    private final XmlHandler mHandleAuthorName = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
            mCurrAuthorName = context.body.trim();
        }
    };
    /**
     * Current series being processed
     */
    private String mCurrSeriesName = null;
    private final XmlHandler mHandleSeriesName = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
            mCurrSeriesName = context.body.trim();
        }
    };
    /**
     * Current series being processed
     */
    private Integer mCurrSeriesPosition = null;
    private final XmlHandler mHandleSeriesEnd = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
            if (mCurrSeriesName != null && mCurrSeriesName.length() > 0) {
                if (mSeries == null)
                    mSeries = new ArrayList<>();
                if (mCurrSeriesPosition == null) {
                    mSeries.add(new Series(mCurrSeriesName, ""));
                } else {
                    mSeries.add(new Series(mCurrSeriesName, mCurrSeriesPosition + ""));
                }
                mCurrSeriesName = null;
                mCurrSeriesPosition = null;
            }
        }
    };
    private final XmlHandler mHandleSeriesPosition = new XmlHandler() {
        @Override
        public void process(ElementContext context) {
            try {
                mCurrSeriesPosition = Integer.parseInt(context.body.trim());
            } catch (Exception e) {
                // Ignore
            }
        }
    };


    ShowBookApiHandler(GoodreadsManager manager, boolean signRequest) {
        super(manager);
        mSignRequest = signRequest;
        // Build the XML filters needed to get the data we're interested in.
        buildFilters();
    }

    /**
     * Perform a search and handle the results.
     *
     * @param request        HttpGet request to use
     * @param fetchThumbnail Indicates if thumbnail file should be retrieved
     *
     * @return the Bundle of data.
     */
    Bundle sendRequest(HttpGet request, boolean fetchThumbnail) throws
            OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException,
            NotAuthorizedException, BookNotFoundException, IOException, NetworkException {

        mBook = new Bundle();

        mShelves = null;

        // Get a handler and run query.
        XmlResponseParser handler = new XmlResponseParser(mRootFilter);
        // We sign the GET request so we get shelves
        mManager.execute(request, handler, mSignRequest);

        // When we get here, the data has been collected but needs to be processed into standard form.

        // Use ISBN13 by preference
        if (mBook.containsKey(ISBN13)) {
            String s = mBook.getString(ISBN13);
            if (s.length() == 13)
                mBook.putString(ColumnNames.KEY_ISBN, s);
        }

        // TODO: Evaluate if ShowBook should store GR book ID.
        // Pros: easier sync
        // Cons: Overwrite GR id when it should not

        //if (mBook.containsKey(BOOK_ID)) {
        //	mBook.putLong(DatabaseDefinitions.DOM_GOODREADS_BOOK_ID.name, mBook.getLong(BOOK_ID));
        //}

        if (fetchThumbnail) {
            String bestImage = null;
            if (mBook.containsKey(IMAGE)) {
                bestImage = mBook.getString(IMAGE);
                if (bestImage.contains(UniqueId.BKEY_NOCOVER) && mBook.containsKey(SMALL_IMAGE)) {
                    bestImage = mBook.getString(SMALL_IMAGE);
                    if (bestImage.contains(UniqueId.BKEY_NOCOVER))
                        bestImage = null;
                }
            }
            if (bestImage != null) {
                String filename = ImageUtils.saveThumbnailFromUrl(bestImage, UniqueId.GOODREADS_FILENAME_SUFFIX);
                if (filename.length() > 0)
                    ArrayUtils.appendOrAdd(mBook, SearchManager.BKEY_THUMBNAIL_SEARCHES, filename);
            }
        }

        /* Build the pub date based on the components */
        GoodreadsManager.buildDate(mBook, PUBLICATION_YEAR, PUBLICATION_MONTH, PUBLICATION_DAY, ColumnNames.KEY_DATE_PUBLISHED);

        if (mBook.containsKey(IS_EBOOK) && mBook.getBoolean(IS_EBOOK))
            mBook.putString(ColumnNames.KEY_FORMAT, BVAL_FORMAT_EBOOK);

        /*
         * Cleanup the title by removing series name, if present
         */
        if (mBook.containsKey(ColumnNames.KEY_TITLE)) {
            String thisTitle = mBook.getString(ColumnNames.KEY_TITLE);
            Series.SeriesDetails details = Series.findSeries(thisTitle);
            if (details != null && details.name.length() > 0) {
                if (mSeries == null)
                    mSeries = new ArrayList<>();
                mSeries.add(new Series(details.name, details.position));
                // Tempting to replace title with ORIG_TITLE, but that does bad things to translations (it used the original language)
                mBook.putString(ColumnNames.KEY_TITLE, thisTitle.substring(0, details.startChar - 1));
                //if (mBook.containsKey(ORIG_TITLE)) {
                //	mBook.putString(ColumnNames.KEY_TITLE, mBook.getString(ORIG_TITLE));
                //} else {
                //	mBook.putString(ColumnNames.KEY_TITLE, thisTitle.substring(0, details.startChar-1));
                //}
            }
        } else if (mBook.containsKey(ORIG_TITLE)) {
            mBook.putString(ColumnNames.KEY_TITLE, mBook.getString(ORIG_TITLE));
        }

        // ENHANCE Store WORK_ID = "__work_id" into GR_WORK_ID;
        // ENHANCE: Store ORIGINAL_PUBLICATION_DATE in database

        // If no published date, try original date
        if (!mBook.containsKey(ColumnNames.KEY_DATE_PUBLISHED)) {
            String origDate = GoodreadsManager.buildDate(mBook, ORIG_PUBLICATION_YEAR, ORIG_PUBLICATION_MONTH, ORIG_PUBLICATION_DAY, null);
            if (origDate != null && origDate.length() > 0)
                mBook.putString(ColumnNames.KEY_DATE_PUBLISHED, origDate);
        }

        //public static final String RATING = "__rating";
        //public static final String BOOK_URL = "__url";

        if (mAuthors != null && mAuthors.size() > 0)
            mBook.putString(ColumnNames.KEY_AUTHOR_DETAILS, ArrayUtils.getAuthorUtils().encodeList(mAuthors, '|'));

        if (mSeries != null && mSeries.size() > 0)
            mBook.putString(ColumnNames.KEY_SERIES_DETAILS, ArrayUtils.getSeriesUtils().encodeList(mSeries, '|'));

        if (mShelves != null && mShelves.size() > 0)
            mBook.putStringArrayList(SHELVES, mShelves);
        // Return parsed results.
        return mBook;
    }

    /**
     * Setup filters to process the XML parts we care about.
     */
    @SuppressWarnings("ConstantConditions")
    private void buildFilters() {
		/*
		   Stuff we care about

			<GoodreadsResponse>
				...
				<book>
					<id>50</id>
					<title>Hatchet (Hatchet, #1)</title>
					<isbn>0689840926</isbn>
					<isbn13>9780689840920</isbn13>
					...
					<image_url>http://www.goodreads.com/images/nocover-111x148.jpg</image_url>
					<small_image_url>http://www.goodreads.com/images/nocover-60x80.jpg</small_image_url>

					<publication_year>2000</publication_year>
					<publication_month>4</publication_month>
					<publication_day>1</publication_day>

					<publisher/><language_code/>
					<is_ebook>false</is_ebook>
					<description><p>Since it was first published in 1987, the story of thirteen-year-old Brian Robeson's survival following a plane crash has become a modern classic. Stranded in the desolate wilderness, Brian uses his instincts and his hatchet to stay alive for fifty-four harrowing days. <p> This twentieth-anniversary edition of <em>Hatchet</em> contains a new introduction and sidebar commentary by Gary Paulsen, written especially for this volume. Drew Willis's detailed pen-and-ink illustrations complement the descriptions in the text and add a new dimension to the book. This handsome edition of the Newbery Honor book will be treasured by <em>Hatchet</em> fans as well as by readers encountering Brian's unforgettable story for the first time.</p></p></description>
					<work>
						...
						<id type="integer">1158125</id>
						...
						<original_publication_day type="integer">1</original_publication_day>
						<original_publication_month type="integer">1</original_publication_month>
						<original_publication_year type="integer">1987</original_publication_year>
						<original_title>Hatchet</original_title>
						...
					</work>
					<average_rating>3.57</average_rating>
					<num_pages>208</num_pages>
					<format>Hardcover</format>
					...
					<url>http://www.goodreads.com/book/show/50.Hatchet</url>
					<link>http://www.goodreads.com/book/show/50.Hatchet</link>

					<authors>
						<author>
							<id>18</id>
							<name>Gary Paulsen</name>
							...
						</author>
					</authors>
					<my_review>
						<id>255221284</id>
						<rating>0</rating>
						...
						<shelves>
							<shelf name="sci-fi-fantasy"/>
							<shelf name="to-read"/>
							<shelf name="default"/>
							<shelf name="environment"/>
							<shelf name="games"/>
							<shelf name="history"/>
						</shelves>
						...
						<date_added>Mon Jan 02 19:07:11 -0800 2012</date_added>
						<date_updated>Sat Mar 03 08:10:09 -0800 2012</date_updated>
						<body>Test again</body>
					</my_review>
					...
					<series_works>
						<series_work>
							<id>268218</id>
							<user_position>1</user_position>
							<series>
								<id>62223</id>
								<title>Brian's Saga</title>
								...
							</series>
						</series_work>
					</series_works>
				</book>
			</GoodreadsResponse>
		 */

        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "id")
                .setEndAction(mHandleLong, BOOK_ID);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "title")
                .setEndAction(mHandleText, ColumnNames.KEY_TITLE);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "isbn")
                .setEndAction(mHandleText, ColumnNames.KEY_ISBN);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "isbn13")
                .setEndAction(mHandleText, ISBN13);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "image_url")
                .setEndAction(mHandleText, IMAGE);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "small_image_url")
                .setEndAction(mHandleText, SMALL_IMAGE);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "publication_year")
                .setEndAction(mHandleLong, PUBLICATION_YEAR);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "publication_month")
                .setEndAction(mHandleLong, PUBLICATION_MONTH);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "publication_day")
                .setEndAction(mHandleLong, PUBLICATION_DAY);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "publisher")
                .setEndAction(mHandleText, ColumnNames.KEY_PUBLISHER);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "is_ebook")
                .setEndAction(mHandleBoolean, IS_EBOOK);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "description")
                .setEndAction(mHandleText, ColumnNames.KEY_DESCRIPTION);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "work", "id")
                .setEndAction(mHandleLong, WORK_ID);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "work", "original_publication_day")
                .setEndAction(mHandleLong, ORIG_PUBLICATION_DAY);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "work", "original_publication_month")
                .setEndAction(mHandleLong, ORIG_PUBLICATION_MONTH);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "work", "original_publication_year")
                .setEndAction(mHandleLong, ORIG_PUBLICATION_YEAR);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "work", "original_title")
                .setEndAction(mHandleText, ORIG_TITLE);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "average_rating")
                .setEndAction(mHandleFloat, RATING);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "num_pages")
                .setEndAction(mHandleLong, ColumnNames.KEY_PAGES);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "format")
                .setEndAction(mHandleText, ColumnNames.KEY_FORMAT);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "url")
                .setEndAction(mHandleText, BOOK_URL);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "authors", "author")
                .setStartAction(mHandleAuthorStart)
                .setEndAction(mHandleAuthorEnd);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "authors", "author", "id")
                .setEndAction(mHandleAuthorId);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "authors", "author", "name")
                .setEndAction(mHandleAuthorName);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "my_review", "id")
                .setEndAction(mHandleLong, REVIEW_ID);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "my_review", "shelves")
                .setStartAction(mHandleShelvesStart);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "my_review", "shelves", "shelf")
                .setStartAction(mHandleShelf);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "series_works", "series_work")
                .setStartAction(mHandleSeriesStart)
                .setEndAction(mHandleSeriesEnd);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "series_works", "series_work", "user_position")
                .setEndAction(mHandleSeriesPosition);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "series_works", "series_work", "series", "id")
                .setEndAction(mHandleSeriesId);
        XmlFilter.buildFilter(mRootFilter, GOODREADS_RESPONSE, BOOK, "series_works", "series_work", "series", "title")
                .setEndAction(mHandleSeriesName);
    }

    /**
     * Field names we add to the bundle based on parsed XML data
     *
     * @author Philip Warner
     */
    public static final class ShowBookFieldNames {
        public static final String BOOK_ID = "__book_id";
        public static final String REVIEW_ID = "__review_id";
        public static final String ISBN13 = "__isbn13";
        public static final String IMAGE = "__image";
        public static final String SMALL_IMAGE = "__smallImage";
        public static final String PUBLICATION_YEAR = "__pub_year";
        public static final String PUBLICATION_MONTH = "__pub_month";
        public static final String PUBLICATION_DAY = "__pub_day";
        public static final String IS_EBOOK = "__is_ebook";
        public static final String WORK_ID = "__work_id";
        public static final String ORIG_PUBLICATION_YEAR = "__orig_pub_year";
        public static final String ORIG_PUBLICATION_MONTH = "__orig_pub_month";
        public static final String ORIG_PUBLICATION_DAY = "__orig_pub_day";
        public static final String ORIG_TITLE = "__orig_title";
        public static final String RATING = "__rating";
        public static final String SHELVES = "__shelves";
        public static final String BOOK_URL = "__url";
    }

}
