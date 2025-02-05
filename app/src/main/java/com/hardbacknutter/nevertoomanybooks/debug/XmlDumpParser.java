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
package com.hardbacknutter.nevertoomanybooks.debug;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.CallSuper;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.helpers.DefaultHandler;

/**
 * DEBUG only.
 */
@SuppressLint("LogConditional")
public class XmlDumpParser
        extends DefaultHandler {

    /** Log tag. */
    private static final String TAG = "XmlDumpParser";

    private boolean mNamespaceBegin;

    private String mCurrentNamespace;

    private String mCurrentNamespaceUri;

    @SuppressWarnings("FieldCanBeLocal")
    private Locator mLocator;

    @CallSuper
    public void setDocumentLocator(final Locator locator) {
        this.mLocator = locator;
    }

    @CallSuper
    public void startDocument() {
        Log.d(TAG, "<?xml version=\"1.0\"?>");
    }

    @CallSuper
    public void endDocument() {
    }

    @CallSuper
    public void startPrefixMapping(final String prefix,
                                   final String uri) {
        mNamespaceBegin = true;
        mCurrentNamespace = prefix;
        mCurrentNamespaceUri = uri;
    }

    @CallSuper
    public void endPrefixMapping(final String prefix) {
    }


    @CallSuper
    public void startElement(final String namespaceURI,
                             final String localName,
                             final String qName,
                             final Attributes attributes) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<").append(qName);
        if (mNamespaceBegin) {
            sb.append(" xmlns:").append(mCurrentNamespace).append("=\"")
              .append(mCurrentNamespaceUri).append("\"");
            mNamespaceBegin = false;
        }
        for (int i = 0; i < attributes.getLength(); i++) {
            sb.append(" ").append(attributes.getQName(i)).append("=\"")
              .append(attributes.getValue(i)).append("\"");
        }
        sb.append(">");
        Log.d(TAG, sb.toString());
    }

    @CallSuper
    public void endElement(final String namespaceURI,
                           final String localName,
                           final String qName) {
        Log.d(TAG, "</" + qName + ">");
    }

    @CallSuper
    public void characters(final char[] ch,
                           final int start,
                           final int length) {
        final StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + length; i++) {
            sb.append(ch[i]);
        }
        Log.d(TAG, sb.toString());
    }

    @CallSuper
    public void ignorableWhitespace(final char[] ch,
                                    final int start,
                                    final int length) {
        final StringBuilder sb = new StringBuilder();
        for (int i = start; i < start + length; i++) {
            sb.append(ch[i]);
        }
        Log.d(TAG, sb.toString());
    }

    @CallSuper
    public void processingInstruction(final String target,
                                      final String data) {
        Log.d(TAG, "<?" + target + " " + data + "?>");
    }

    @CallSuper
    public void skippedEntity(final String name) {
        Log.d(TAG, "&" + name + ";");
    }
}
