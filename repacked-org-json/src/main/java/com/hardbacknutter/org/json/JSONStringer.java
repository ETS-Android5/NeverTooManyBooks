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

/*
Copyright (c) 2006 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package com.hardbacknutter.org.json;

import java.io.StringWriter;

/**
 * JSONStringer provides a quick and convenient way of producing JSON text.
 * The texts produced strictly conform to JSON syntax rules. No whitespace is
 * added, so the results are ready for transmission or storage. Each instance of
 * JSONStringer can produce one JSON text.
 * <p>
 * A JSONStringer instance provides a {@code value} method for appending
 * values to the
 * text, and a {@code key}
 * method for adding keys before values in objects. There are {@code array}
 * and {@code endArray} methods that make and bound array values, and
 * {@code object} and {@code endObject} methods which make and bound
 * object values. All of these methods return the JSONWriter instance,
 * permitting cascade style. For example, <pre>
 * myString = new JSONStringer()
 *     .object()
 *         .key("JSON")
 *         .value("Hello, World!")
 *     .endObject()
 *     .toString();</pre> which produces the string <pre>
 * {"JSON":"Hello, World!"}</pre>
 * <p>
 * The first method called must be {@code array} or {@code object}.
 * There are no methods for adding commas or colons. JSONStringer adds them for
 * you. Objects and arrays can be nested up to 200 levels deep.
 * <p>
 * This can sometimes be easier than using a JSONObject to build a string.
 *
 * @author JSON.org
 * @version 2015-12-09
 */
public class JSONStringer
        extends JSONWriter {

    /**
     * Make a fresh JSONStringer. It can be used to build one JSON text.
     */
    public JSONStringer() {
        super(new StringWriter());
    }

    /**
     * Return the JSON text. This method is used to obtain the product of the
     * JSONStringer instance. It will return {@code null} if there was a
     * problem in the construction of the JSON text (such as the calls to
     * {@code array} were not properly balanced with calls to
     * {@code endArray}).
     *
     * @return The JSON text.
     */
    @Override
    public String toString() {
        return this.mode == 'd' ? this.writer.toString() : null;
    }
}
