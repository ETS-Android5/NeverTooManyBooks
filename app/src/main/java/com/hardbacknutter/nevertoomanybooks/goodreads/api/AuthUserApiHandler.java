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
package com.hardbacknutter.nevertoomanybooks.goodreads.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;

import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsAuth;
import com.hardbacknutter.nevertoomanybooks.goodreads.GoodreadsManager;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.CredentialsException;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlFilter;
import com.hardbacknutter.nevertoomanybooks.utils.xml.XmlResponseParser;

/**
 * Handler for the "auth.user" call. Just gets the current user details.
 * <p>
 * <a href="https://www.goodreads.com/api/index#auth.user">auth.user</a>
 */
public class AuthUserApiHandler
        extends ApiHandler {

    private static final String URL = GoodreadsManager.BASE_URL + "/api/auth_user";

    private static final String XML_USER = "user";

    private long mUserId;
    @Nullable
    private String mUsername;

    /** XmlFilter root object. Used in extracting data file XML results. */
    @NonNull
    private final XmlFilter mRootFilter = new XmlFilter("");

    /**
     * Constructor.
     *
     * @param appContext Application context
     * @param grAuth     Authentication handler
     */
    public AuthUserApiHandler(@NonNull final Context appContext,
                              @NonNull final GoodreadsAuth grAuth) {
        super(appContext, grAuth);
        // don't ...if (!apiHandler.hasValidCredentials()) {

        buildFilters();
    }

    /**
     * Retrieve the user id.
     *
     * @return Resulting User ID, 0 if error/none.
     */
    public long getAuthUser() {
        mUserId = 0;
        try {
            final DefaultHandler handler = new XmlResponseParser(mRootFilter);
            executePost(URL, null, true, handler);
            // Return user found.
            return mUserId;

        } catch (@NonNull final CredentialsException | Http404Exception | IOException
                | RuntimeException ignore) {
            return 0;
        }
    }

    /**
     * Setup filters to process the XML parts we care about.
     * <p>
     * Typical response.
     * <pre>
     *  {@code
     *  <GoodreadsResponse>
     *    <Request>
     *      <authentication>true</authentication>
     *      <key><![CDATA[...]]></key>
     *      <method><![CDATA[api_auth_user]]></method>
     *    </Request>
     *
     *    <user id="5129458">
     *      <name><![CDATA[Grunthos]]></name>
     *      <link>
     *        <![CDATA[http://www.goodreads.com/user/show/5129458-grunthos?utm_medium=api]]>
     *      </link>
     *    </user>
     *   </GoodreadsResponse>
     *   }
     * </pre>
     */
    private void buildFilters() {
        // Leave "id" as string... it's an attribute for THIS tag.
        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XML_USER)
                 .setStartAction(ec -> mUserId = Long.parseLong(
                         ec.getAttributes().getValue("", "id")));

        XmlFilter.buildFilter(mRootFilter, XmlTags.XML_GOODREADS_RESPONSE, XML_USER,
                              XmlTags.XML_NAME)
                 .setEndAction(ec -> mUsername = ec.getBody());
    }

    @Nullable
    public String getUsername() {
        return mUsername;
    }

    public long getUserId() {
        return mUserId;
    }

}
