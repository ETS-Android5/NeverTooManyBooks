/*
 * @copyright 2010 Evan Leybourn
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

package com.eleybourn.bookcatalogue.searches.wikipedia;

import android.support.annotation.NonNull;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/*
 * An HTML handler for the Wikipedia search results return
 *
 */
public class SearchWikipediaHandler extends DefaultHandler {
    private static final String UL = "ul";
    private static final String LINK = "A";
    private final String[] link = {"", ""};
    public String id = "";
    private int count = 0;
    private boolean entry = false;

    public String[] getLinks() {
        return link;
    }

    public int getCount() {
        return count;
    }

    @Override
    public void endElement(@NonNull final String uri,
                           @NonNull final String localName,
                           @NonNull final String name) throws SAXException {
        super.endElement(uri, localName, name);
        // don't do anything if we are in the table of contents
        if (localName.equalsIgnoreCase(UL) && entry) {
            entry = false;
        }
    }

    @Override
    public void startElement(@NonNull final String uri,
                             @NonNull final String localName,
                             @NonNull final String name,
                             @NonNull final Attributes attributes) throws SAXException {
        super.startElement(uri, localName, name, attributes);
        if (localName.equalsIgnoreCase(UL)) {
            String className = attributes.getValue("class");
            if (className != null && "mw-search-results".equals(className)) {
                entry = true;
            }
        }
        if (entry) {
            if (localName.equalsIgnoreCase(LINK)) {
                String href = attributes.getValue("href");
                if (!href.contains("/wiki")) {
                    //only use /wiki/... urls
                    return;
                }
                // we only want the first 2 links
                if (count < 2) {
                    link[count] = href;
                    count++;
                } else {
                    entry = false;
                }
            }
        }
    }
}
 
