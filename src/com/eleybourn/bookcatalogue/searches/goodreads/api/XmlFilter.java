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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A class to help parsing Sax Xml output. For goodreads XML output, 90% of the XML can be
 * thrown away but we do need to ensure we get the tags from the right context. The XmlFilter
 * objects build a tree of filters and XmlHandler objects that make this process more manageable.
 *
 * See SearchBooksApiHandler for an example of usage.
 *
 * @author Philip Warner
 */
public class XmlFilter {
    /** The tag for this specific filter */
    private final String mTagName;
    /** A HashMap to ensure that there are no more than one sub-filter per tag at a given level */
    private final Map<String, XmlFilter> mSubFilterHash = new HashMap<>();
    /** List of sub-filters for this filter */
    private final ArrayList<XmlFilter> mSubFilters = new ArrayList<>();
    /** Action to perform, if any, when the associated tag is started */
    private XmlHandler mStartAction = null;
    /** Optional parameter put in context before action is called */
    private Object mStartArg = null;

    /** Action to perform, if any, when the associated tag is finished */
    private XmlHandler mEndAction = null;
    /** Optional parameter put in context before action is called */
    private Object mEndArg = null;

    /**
     * Constructor
     *
     * @param pattern The tag that this filter handles
     */
    public XmlFilter(@NonNull final String pattern) {
        mTagName = pattern;
    }

    /**
     * Static method to add a filter to a passed tree and return the matching XmlFilter
     *
     * @param root    Root XmlFilter object.
     * @param filters Names of tags to add to tree, if not present.
     *
     * @return The filter matching the final tag name passed.
     */
    @Nullable
    public static XmlFilter buildFilter(@NonNull final XmlFilter root, final String... filters) {
        if (filters.length <= 0) {
            return null;
        }
        return buildFilter(root, 0, Arrays.asList(filters).iterator());
    }

    /**
     * Static method to add a filter to a passed tree and return the matching XmlFilter
     *
     * @param root    Root XmlFilter object.
     * @param filters Names of tags to add to tree, if not present.
     *
     * @return The filter matching the final tag name passed.
     */
    @Nullable
    static XmlFilter buildFilter(@NonNull final XmlFilter root, @NonNull final List<String> filters) {
        if (filters.size() <= 0) {
            return null;
        }
        return buildFilter(root, 0, filters.iterator());
    }

    /**
     * Internal implementation of method to add a filter to a passed tree and return the matching XmlFilter.
     * This is called recursively to process the filter list.
     *
     * @param root      Root XmlFilter object.
     * @param depth     Recursion depth
     * @param iterator  Names of tags to add to tree, if not present.
     *
     * @return The filter matching the final tag name passed.
     */
    @NonNull
    private static XmlFilter buildFilter(@NonNull final XmlFilter root, final int depth, @NonNull final Iterator<String> iterator) {
        //if (!root.matches(filters[depth]))
        //	throw new RuntimeException("Filter at depth=" + depth + " does not match first filter parameter");
        final String curr = iterator.next();
        XmlFilter sub = root.getSubFilter(curr);
        if (sub == null) {
            sub = new XmlFilter(curr);
            root.addFilter(sub);
        }
        if (!iterator.hasNext()) {
            // At end
            return sub;
        } else {
            // We are still finding leaf
            return buildFilter(sub, depth + 1, iterator);
        }
    }

    /**
     * Check if this filter matches the passed XML tag
     *
     * @param tag Tag name
     *
     * @return Boolean indicating it matches.
     */
    private boolean matches(@Nullable final String tag) {
        return mTagName.equalsIgnoreCase(tag);
    }

    /**
     * Find a sub-filter for the passed context.
     * Currently just used local_name from the context.
     */
    XmlFilter getSubFilter(@NonNull final ElementContext context) {
        return getSubFilter(context.localName);
    }

    /**
     * Find a sub-filter based on the passed tag name.
     *
     * @param name XML tag name
     *
     * @return Matching filter, or NULL
     */
    @Nullable
    private XmlFilter getSubFilter(@Nullable final String name) {
        for (XmlFilter f : mSubFilters) {
            if (f.matches(name)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Called when associated tag is started.
     */
    void processStart(@NonNull final ElementContext context) {
        if (mStartAction != null) {
            context.userArg = mStartArg;
            mStartAction.process(context);
        }
    }

    /**
     * Called when associated tag is finished.
     */
    void processEnd(@NonNull final ElementContext context) {
        if (mEndAction != null) {
            context.userArg = mEndArg;
            mEndAction.process(context);
        }
    }

    /**
     * Get the tag that this filter will match
     */
    private String getTagName() {
        return mTagName;
    }

    /**
     * Set the action to perform when the tag associated with this filter is finished.
     *
     * @param endAction XmlHandler to call
     *
     * @return This XmlFilter, to allow chaining
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    XmlFilter setEndAction(@NonNull final XmlHandler endAction) {
        return setEndAction(endAction, null);
    }

    @NonNull
    public XmlFilter setEndAction(@NonNull final XmlHandler endAction, @Nullable final Object userArg) {
        if (mEndAction != null) {
            throw new RuntimeException("End Action already set");
        }
        mEndAction = endAction;
        mEndArg = userArg;
        return this;
    }

    /**
     * Set the action to perform when the tag associated with this filter is started.
     *
     * @param startAction XmlHandler to call
     *
     * @return This XmlFilter, to allow chaining
     */
    @NonNull
    XmlFilter setStartAction(@NonNull final XmlHandler startAction) {
        return setStartAction(startAction, null);
    }

    @NonNull
    public XmlFilter setStartAction(@NonNull final XmlHandler startAction, @Nullable final Object userArg) {
        if (mStartAction != null) {
            throw new RuntimeException("Start Action already set");
        }
        mStartAction = startAction;
        mStartArg = userArg;
        return this;
    }

    /**
     * Add a filter at this level; ensure it is unique.
     *
     * @param filter filter to add
     */
    private void addFilter(@NonNull final XmlFilter filter) {
        String lcPat = filter.getTagName().toLowerCase();
        if (mSubFilterHash.containsKey(lcPat)) {
            throw new RuntimeException("Filter " + filter.getTagName() + " already exists");
        }
        mSubFilterHash.put(lcPat, filter);
        mSubFilters.add(filter);
    }

    /** Interface definition for filter handlers */
    public interface XmlHandler {
        void process(@NonNull final ElementContext context);
    }

    /** Interface definition for filter handlers */
    public interface XmlHandlerExt<T> {
        void process(@NonNull final ElementContext context, @NonNull final T arg);
    }

    /**
     * Class used to define the context of a specific tag. The 'body' element will only be
     * filled in the call to the 'processEnd' method.
     *
     * @author Philip Warner
     */
    public static class ElementContext {
        public final String uri;
        public final String localName;
        public final String name;
        public final Attributes attributes;
        public final String preText;
        public String body;
        public XmlFilter filter;
        public Object userArg;

        public ElementContext() {
            this.uri = null;
            this.localName = null;
            this.name = null;
            this.attributes = null;
            this.preText = null;
        }

        public ElementContext(@Nullable final String uri,
                              @Nullable final String localName,
                              @Nullable final String name,
                              @Nullable final Attributes attributes,
                              @Nullable final String preText) {
            this.uri = uri;
            this.localName = localName;
            this.name = name;
            this.attributes = attributes;
            this.preText = preText;
        }
    }

}
