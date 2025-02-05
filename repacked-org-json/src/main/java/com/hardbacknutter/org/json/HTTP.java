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
Copyright (c) 2002 JSON.org

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

import java.util.Locale;

/**
 * Convert an HTTP header to a JSONObject and back.
 *
 * @author JSON.org
 * @version 2015-12-09
 */
public final class HTTP {

    /** Carriage return/line feed. */
    public static final String CRLF = "\r\n";

    private HTTP() {
    }

    /**
     * Convert an HTTP header string into a JSONObject. It can be a request
     * header or a response header. A request header will contain
     * <pre>{
     *    Method: "POST" (for example),
     *    "Request-URI": "/" (for example),
     *    "HTTP-Version": "HTTP/1.1" (for example)
     * }</pre>
     * A response header will contain
     * <pre>{
     *    "HTTP-Version": "HTTP/1.1" (for example),
     *    "Status-Code": "200" (for example),
     *    "Reason-Phrase": "OK" (for example)
     * }</pre>
     * In addition, the other parameters in the header will be captured, using
     * the HTTP field names as JSON names, so that <pre>{@code
     *    Date: Sun, 26 May 2002 18:06:04 GMT
     *    Cookie: Q=q2=PPEAsg--; B=677gi6ouf29bn&b=2&f=s
     *    Cache-Control: no-cache}</pre>
     * become
     * <pre>{@code
     *    Date: "Sun, 26 May 2002 18:06:04 GMT",
     *    Cookie: "Q=q2=PPEAsg--; B=677gi6ouf29bn&b=2&f=s",
     *    "Cache-Control": "no-cache",
     * ...}</pre>
     * It does no further checking or conversion. It does not parse dates.
     * It does not do '%' transforms on URLs.
     *
     * @param string An HTTP header string.
     *
     * @return A JSONObject containing the elements and attributes
     * of the XML string.
     *
     * @throws JSONException if a called function fails
     */
    public static JSONObject toJSONObject(final String string)
            throws JSONException {
        final JSONObject jo = new JSONObject();
        final HTTPTokener x = new HTTPTokener(string);
        final String token;

        token = x.nextToken();
        if (token.toUpperCase(Locale.ROOT).startsWith("HTTP")) {

// Response

            jo.put("HTTP-Version", token);
            jo.put("Status-Code", x.nextToken());
            jo.put("Reason-Phrase", x.nextTo('\0'));
            x.next();

        } else {

// Request

            jo.put("Method", token);
            jo.put("Request-URI", x.nextToken());
            jo.put("HTTP-Version", x.nextToken());
        }

// Fields

        while (x.more()) {
            final String name = x.nextTo(':');
            x.next(':');
            jo.put(name, x.nextTo('\0'));
            x.next();
        }
        return jo;
    }


    /**
     * Convert a JSONObject into an HTTP header. A request header must contain
     * <pre>{
     *    Method: "POST" (for example),
     *    "Request-URI": "/" (for example),
     *    "HTTP-Version": "HTTP/1.1" (for example)
     * }</pre>
     * A response header must contain
     * <pre>{
     *    "HTTP-Version": "HTTP/1.1" (for example),
     *    "Status-Code": "200" (for example),
     *    "Reason-Phrase": "OK" (for example)
     * }</pre>
     * Any other members of the JSONObject will be output as HTTP fields.
     * The result will end with two CRLF pairs.
     *
     * @param jo A JSONObject
     *
     * @return An HTTP header string.
     *
     * @throws JSONException if the object does not contain enough
     *                       information.
     */
    public static String toString(final JSONObject jo)
            throws JSONException {
        final StringBuilder sb = new StringBuilder();
        if (jo.has("Status-Code") && jo.has("Reason-Phrase")) {
            sb.append(jo.getString("HTTP-Version"));
            sb.append(' ');
            sb.append(jo.getString("Status-Code"));
            sb.append(' ');
            sb.append(jo.getString("Reason-Phrase"));
        } else if (jo.has("Method") && jo.has("Request-URI")) {
            sb.append(jo.getString("Method"));
            sb.append(' ');
            sb.append('"');
            sb.append(jo.getString("Request-URI"));
            sb.append('"');
            sb.append(' ');
            sb.append(jo.getString("HTTP-Version"));
        } else {
            throw new JSONException("Not enough material for an HTTP header.");
        }
        sb.append(CRLF);
        // Don't use the new entrySet API to maintain Android support
        for (final String key : jo.keySet()) {
            final String value = jo.optString(key);
            if (!"HTTP-Version".equals(key) && !"Status-Code".equals(key) &&
                !"Reason-Phrase".equals(key) && !"Method".equals(key) &&
                !"Request-URI".equals(key) && !JSONObject.NULL.equals(value)) {
                sb.append(key);
                sb.append(": ");
                sb.append(jo.optString(key));
                sb.append(CRLF);
            }
        }
        sb.append(CRLF);
        return sb.toString();
    }
}
