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
Copyright (c) 2008 JSON.org

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

import androidx.annotation.Nullable;

/**
 * This provides static methods to convert an XML text into a JSONArray or
 * JSONObject, and to covert a JSONArray or JSONObject into an XML text using
 * the JsonML transform.
 *
 * @author JSON.org
 * @version 2016-01-30
 */
public final class JSONML {

    private JSONML() {
    }

    /**
     * Parse XML values and store them in a JSONArray.
     *
     * @param x           The XMLTokener containing the source string.
     * @param arrayForm   true if array form, false if object form.
     * @param ja          The JSONArray that is containing the current tag or null
     *                    if we are at the outermost level.
     * @param keepStrings Don't type-convert text nodes and attribute values
     *
     * @return A JSONArray if the value is the outermost tag, otherwise null.
     *
     * @throws JSONException if a parsing error occurs
     */
    private static Object parse(
            final XMLTokener x,
            final boolean arrayForm,
            @Nullable final JSONArray ja,
            final boolean keepStrings
                               )
            throws JSONException {
        String attribute;
        char c;
        String closeTag;
        int i;
        JSONArray newja;
        JSONObject newjo;
        Object token;
        String tagName;

// Test for and skip past these forms:
//      <!-- ... -->
//      <![  ... ]]>
//      <!   ...   >
//      <?   ...  ?>

        while (true) {
            if (!x.more()) {
                throw x.syntaxError("Bad XML");
            }
            token = x.nextContent();
            if (token == XML.LT) {
                token = x.nextToken();
                if (token instanceof Character) {
                    if (token == XML.SLASH) {

// Close tag </

                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw new JSONException(
                                    "Expected a closing name instead of '" +
                                    token + "'.");
                        }
                        if (x.nextToken() != XML.GT) {
                            throw x.syntaxError("Misshaped close tag");
                        }
                        return token;
                    } else if (token == XML.BANG) {

// <!

                        c = x.next();
                        if (c == '-') {
                            if (x.next() == '-') {
                                x.skipPast("-->");
                            } else {
                                x.back();
                            }
                        } else if (c == '[') {
                            token = x.nextToken();
                            if (token.equals("CDATA") && x.next() == '[') {
                                if (ja != null) {
                                    ja.put(x.nextCDATA());
                                }
                            } else {
                                throw x.syntaxError("Expected 'CDATA['");
                            }
                        } else {
                            i = 1;
                            do {
                                token = x.nextMeta();
                                if (token == null) {
                                    throw x.syntaxError("Missing '>' after '<!'.");
                                } else if (token == XML.LT) {
                                    i += 1;
                                } else if (token == XML.GT) {
                                    i -= 1;
                                }
                            } while (i > 0);
                        }
                    } else if (token == XML.QUEST) {

// <?

                        x.skipPast("?>");
                    } else {
                        throw x.syntaxError("Misshaped tag");
                    }

// Open tag <

                } else {
                    if (!(token instanceof String)) {
                        throw x.syntaxError("Bad tagName '" + token + "'.");
                    }
                    tagName = (String) token;
                    newja = new JSONArray();
                    newjo = new JSONObject();
                    if (arrayForm) {
                        newja.put(tagName);
                        if (ja != null) {
                            ja.put(newja);
                        }
                    } else {
                        newjo.put("tagName", tagName);
                        if (ja != null) {
                            ja.put(newjo);
                        }
                    }
                    token = null;
                    for (; ; ) {
                        if (token == null) {
                            token = x.nextToken();
                        }
                        if (token == null) {
                            throw x.syntaxError("Misshaped tag");
                        }
                        if (!(token instanceof String)) {
                            break;
                        }

// attribute = value

                        attribute = (String) token;
                        if (!arrayForm && ("tagName".equals(attribute) || "childNode".equals(
                                attribute))) {
                            throw x.syntaxError("Reserved attribute.");
                        }
                        token = x.nextToken();
                        if (token == XML.EQ) {
                            token = x.nextToken();
                            if (!(token instanceof String)) {
                                throw x.syntaxError("Missing value");
                            }
                            newjo.accumulate(attribute,
                                             keepStrings ? ((String) token) : XML.stringToValue(
                                                     (String) token));
                            token = null;
                        } else {
                            newjo.accumulate(attribute, "");
                        }
                    }
                    if (arrayForm && !newjo.isEmpty()) {
                        newja.put(newjo);
                    }

// Empty tag <.../>

                    if (token == XML.SLASH) {
                        if (x.nextToken() != XML.GT) {
                            throw x.syntaxError("Misshaped tag");
                        }
                        if (ja == null) {
                            if (arrayForm) {
                                return newja;
                            }
                            return newjo;
                        }

// Content, between <...> and </...>

                    } else {
                        if (token != XML.GT) {
                            throw x.syntaxError("Misshaped tag");
                        }
                        closeTag = (String) parse(x, arrayForm, newja, keepStrings);
                        if (!closeTag.equals(tagName)) {
                            throw x.syntaxError("Mismatched '" + tagName +
                                                "' and '" + closeTag + "'");
                        }
                        if (!arrayForm && !newja.isEmpty()) {
                            newjo.put("childNodes", newja);
                        }
                        if (ja == null) {
                            if (arrayForm) {
                                return newja;
                            }
                            return newjo;
                        }
                    }
                }
            } else {
                if (ja != null) {
                    if (token instanceof String) {
                        ja.put(keepStrings ? XML.unescape((String) token)
                                           : XML.stringToValue((String) token));
                    } else {
                        ja.put(token);
                    }
                }
            }
        }
    }


    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONArray using the JsonML transform. Each XML tag is represented as
     * a JSONArray in which the first element is the tag name. If the tag has
     * attributes, then the second element will be JSONObject containing the
     * name/value pairs. If the tag contains children, then strings and
     * JSONArrays will represent the child tags.
     * Comments, prologs, DTDs, and <pre>{@code &lt;[ [ ]]>}</pre> are ignored.
     *
     * @param string The source string.
     *
     * @return A JSONArray containing the structured data from the XML string.
     *
     * @throws JSONException Thrown on error converting to a JSONArray
     */
    public static JSONArray toJSONArray(final String string)
            throws JSONException {
        return (JSONArray) parse(new XMLTokener(string), true, null, false);
    }


    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONArray using the JsonML transform. Each XML tag is represented as
     * a JSONArray in which the first element is the tag name. If the tag has
     * attributes, then the second element will be JSONObject containing the
     * name/value pairs. If the tag contains children, then strings and
     * JSONArrays will represent the child tags.
     * As opposed to toJSONArray this method does not attempt to convert
     * any text node or attribute value to any type
     * but just leaves it as a string.
     * Comments, prologs, DTDs, and <pre>{@code &lt;[ [ ]]>}</pre> are ignored.
     *
     * @param string      The source string.
     * @param keepStrings If true, then values will not be coerced into boolean
     *                    or numeric values and will instead be left as strings
     *
     * @return A JSONArray containing the structured data from the XML string.
     *
     * @throws JSONException Thrown on error converting to a JSONArray
     */
    public static JSONArray toJSONArray(final String string,
                                        final boolean keepStrings)
            throws JSONException {
        return (JSONArray) parse(new XMLTokener(string), true, null, keepStrings);
    }


    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONArray using the JsonML transform. Each XML tag is represented as
     * a JSONArray in which the first element is the tag name. If the tag has
     * attributes, then the second element will be JSONObject containing the
     * name/value pairs. If the tag contains children, then strings and
     * JSONArrays will represent the child content and tags.
     * As opposed to toJSONArray this method does not attempt to convert
     * any text node or attribute value to any type
     * but just leaves it as a string.
     * Comments, prologs, DTDs, and <pre>{@code &lt;[ [ ]]>}</pre> are ignored.
     *
     * @param x           An XMLTokener.
     * @param keepStrings If true, then values will not be coerced into boolean
     *                    or numeric values and will instead be left as strings
     *
     * @return A JSONArray containing the structured data from the XML string.
     *
     * @throws JSONException Thrown on error converting to a JSONArray
     */
    public static JSONArray toJSONArray(final XMLTokener x,
                                        final boolean keepStrings)
            throws JSONException {
        return (JSONArray) parse(x, true, null, keepStrings);
    }


    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONArray using the JsonML transform. Each XML tag is represented as
     * a JSONArray in which the first element is the tag name. If the tag has
     * attributes, then the second element will be JSONObject containing the
     * name/value pairs. If the tag contains children, then strings and
     * JSONArrays will represent the child content and tags.
     * Comments, prologs, DTDs, and <pre>{@code &lt;[ [ ]]>}</pre> are ignored.
     *
     * @param x An XMLTokener.
     *
     * @return A JSONArray containing the structured data from the XML string.
     *
     * @throws JSONException Thrown on error converting to a JSONArray
     */
    public static JSONArray toJSONArray(final XMLTokener x)
            throws JSONException {
        return (JSONArray) parse(x, true, null, false);
    }


    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject using the JsonML transform. Each XML tag is represented as
     * a JSONObject with a "tagName" property. If the tag has attributes, then
     * the attributes will be in the JSONObject as properties. If the tag
     * contains children, the object will have a "childNodes" property which
     * will be an array of strings and JsonML JSONObjects.
     * <p>
     * Comments, prologs, DTDs, and <pre>{@code &lt;[ [ ]]>}</pre> are ignored.
     *
     * @param string The XML source text.
     *
     * @return A JSONObject containing the structured data from the XML string.
     *
     * @throws JSONException Thrown on error converting to a JSONObject
     */
    public static JSONObject toJSONObject(final String string)
            throws JSONException {
        return (JSONObject) parse(new XMLTokener(string), false, null, false);
    }


    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject using the JsonML transform. Each XML tag is represented as
     * a JSONObject with a "tagName" property. If the tag has attributes, then
     * the attributes will be in the JSONObject as properties. If the tag
     * contains children, the object will have a "childNodes" property which
     * will be an array of strings and JsonML JSONObjects.
     * <p>
     * Comments, prologs, DTDs, and <pre>{@code &lt;[ [ ]]>}</pre> are ignored.
     *
     * @param string      The XML source text.
     * @param keepStrings If true, then values will not be coerced into boolean
     *                    or numeric values and will instead be left as strings
     *
     * @return A JSONObject containing the structured data from the XML string.
     *
     * @throws JSONException Thrown on error converting to a JSONObject
     */
    public static JSONObject toJSONObject(final String string,
                                          final boolean keepStrings)
            throws JSONException {
        return (JSONObject) parse(new XMLTokener(string), false, null, keepStrings);
    }


    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject using the JsonML transform. Each XML tag is represented as
     * a JSONObject with a "tagName" property. If the tag has attributes, then
     * the attributes will be in the JSONObject as properties. If the tag
     * contains children, the object will have a "childNodes" property which
     * will be an array of strings and JsonML JSONObjects.
     * <p>
     * Comments, prologs, DTDs, and <pre>{@code &lt;[ [ ]]>}</pre> are ignored.
     *
     * @param x An XMLTokener of the XML source text.
     *
     * @return A JSONObject containing the structured data from the XML string.
     *
     * @throws JSONException Thrown on error converting to a JSONObject
     */
    public static JSONObject toJSONObject(final XMLTokener x)
            throws JSONException {
        return (JSONObject) parse(x, false, null, false);
    }


    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject using the JsonML transform. Each XML tag is represented as
     * a JSONObject with a "tagName" property. If the tag has attributes, then
     * the attributes will be in the JSONObject as properties. If the tag
     * contains children, the object will have a "childNodes" property which
     * will be an array of strings and JsonML JSONObjects.
     * <p>
     * Comments, prologs, DTDs, and <pre>{@code &lt;[ [ ]]>}</pre> are ignored.
     *
     * @param x           An XMLTokener of the XML source text.
     * @param keepStrings If true, then values will not be coerced into boolean
     *                    or numeric values and will instead be left as strings
     *
     * @return A JSONObject containing the structured data from the XML string.
     *
     * @throws JSONException Thrown on error converting to a JSONObject
     */
    public static JSONObject toJSONObject(final XMLTokener x,
                                          final boolean keepStrings)
            throws JSONException {
        return (JSONObject) parse(x, false, null, keepStrings);
    }


    /**
     * Reverse the JSONML transformation, making an XML text from a JSONArray.
     *
     * @param ja A JSONArray.
     *
     * @return An XML string.
     *
     * @throws JSONException Thrown on error converting to a string
     */
    public static String toString(final JSONArray ja)
            throws JSONException {
        int i;
        final JSONObject jo;
        final int length;
        Object object;
        final StringBuilder sb = new StringBuilder();
        String tagName;

// Emit <tagName

        tagName = ja.getString(0);
        XML.noSpace(tagName);
        tagName = XML.escape(tagName);
        sb.append('<');
        sb.append(tagName);

        object = ja.opt(1);
        if (object instanceof JSONObject) {
            i = 2;
            jo = (JSONObject) object;

// Emit the attributes

            // Don't use the new entrySet API to maintain Android support
            for (final String key : jo.keySet()) {
                final Object value = jo.opt(key);
                XML.noSpace(key);
                if (value != null) {
                    sb.append(' ');
                    sb.append(XML.escape(key));
                    sb.append('=');
                    sb.append('"');
                    sb.append(XML.escape(value.toString()));
                    sb.append('"');
                }
            }
        } else {
            i = 1;
        }

// Emit content in body

        length = ja.length();
        if (i >= length) {
            sb.append('/');
            sb.append('>');
        } else {
            sb.append('>');
            do {
                object = ja.get(i);
                i += 1;
                if (object != null) {
                    if (object instanceof String) {
                        sb.append(XML.escape(object.toString()));
                    } else if (object instanceof JSONObject) {
                        sb.append(toString((JSONObject) object));
                    } else if (object instanceof JSONArray) {
                        sb.append(toString((JSONArray) object));
                    } else {
                        sb.append(object);
                    }
                }
            } while (i < length);
            sb.append('<');
            sb.append('/');
            sb.append(tagName);
            sb.append('>');
        }
        return sb.toString();
    }

    /**
     * Reverse the JSONML transformation, making an XML text from a JSONObject.
     * The JSONObject must contain a "tagName" property. If it has children,
     * then it must have a "childNodes" property containing an array of objects.
     * The other properties are attributes with string values.
     *
     * @param jo A JSONObject.
     *
     * @return An XML string.
     *
     * @throws JSONException Thrown on error converting to a string
     */
    public static String toString(final JSONObject jo)
            throws JSONException {
        final StringBuilder sb = new StringBuilder();
        int i;
        final JSONArray ja;
        final int length;
        Object object;
        String tagName;
        Object value;

//Emit <tagName

        tagName = jo.optString("tagName");
        if (tagName == null) {
            return XML.escape(jo.toString());
        }
        XML.noSpace(tagName);
        tagName = XML.escape(tagName);
        sb.append('<');
        sb.append(tagName);

//Emit the attributes

        // Don't use the new entrySet API to maintain Android support
        for (final String key : jo.keySet()) {
            if (!"tagName".equals(key) && !"childNodes".equals(key)) {
                XML.noSpace(key);
                value = jo.opt(key);
                if (value != null) {
                    sb.append(' ');
                    sb.append(XML.escape(key));
                    sb.append('=');
                    sb.append('"');
                    sb.append(XML.escape(value.toString()));
                    sb.append('"');
                }
            }
        }

//Emit content in body

        ja = jo.optJSONArray("childNodes");
        if (ja == null) {
            sb.append('/');
            sb.append('>');
        } else {
            sb.append('>');
            length = ja.length();
            for (i = 0; i < length; i += 1) {
                object = ja.get(i);
                if (object != null) {
                    if (object instanceof String) {
                        sb.append(XML.escape(object.toString()));
                    } else if (object instanceof JSONObject) {
                        sb.append(toString((JSONObject) object));
                    } else if (object instanceof JSONArray) {
                        sb.append(toString((JSONArray) object));
                    } else {
                        sb.append(object);
                    }
                }
            }
            sb.append('<');
            sb.append('/');
            sb.append(tagName);
            sb.append('>');
        }
        return sb.toString();
    }
}
