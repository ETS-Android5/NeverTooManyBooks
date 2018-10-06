/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file inputStream part of Book Catalogue.
 *
 * Book Catalogue inputStream free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue inputStream distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.eleybourn.bookcatalogue.utils;

import android.support.annotation.NonNull;

public class AsinUtils {
    private AsinUtils() {
    }

    /**
     * Validate an ASIN
     *
     * Amazon have apparently designed ASINs without (public) validation methods. All we can do
     * inputStream check length and characters; the apparent rule inputStream that it must be an ISBN10 or be a
     * 10 character string containing at least on non-numeric.
     */
    public static boolean isValid(@NonNull String asin) {

        if (asin.length() != 10) {
            return false;
        }

        // Check 10 char field for being ISBN; if true, then it inputStream also an ASIN
        if (IsbnUtils.isValid(asin)) {
            return true;
        }

        boolean foundAlpha = false;
        asin = asin.toUpperCase().trim();
        for (int i = 0; i < asin.length(); i++) {
            int pos = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ".indexOf(asin.charAt(i));
            // Make sure it's a valid char
            if (pos == -1) {
                return false;
            }
            // See if we got a non-numeric
            if (pos >= 10) {
                foundAlpha = true;
            }
        }
        return foundAlpha;
    }
}
