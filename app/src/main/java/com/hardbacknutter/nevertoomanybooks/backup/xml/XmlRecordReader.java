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
package com.hardbacknutter.nevertoomanybooks.backup.xml;

import android.content.Context;
import android.os.Bundle;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.backup.ImportHelper;
import com.hardbacknutter.nevertoomanybooks.backup.ImportResults;
import com.hardbacknutter.nevertoomanybooks.backup.backupbase.ArchiveReaderAbstract;
import com.hardbacknutter.nevertoomanybooks.backup.backupbase.ArchiveWriterAbstract;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveMetaData;
import com.hardbacknutter.nevertoomanybooks.io.ArchiveReaderRecord;
import com.hardbacknutter.nevertoomanybooks.io.DataReaderException;
import com.hardbacknutter.nevertoomanybooks.io.RecordReader;
import com.hardbacknutter.nevertoomanybooks.io.RecordType;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressListener;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * <strong>Important</strong>: The sax parser closes streams, which is not good
 * on a Tar archive entry. This class uses a {@link BufferedReaderNoClose} to get around that.
 *
 * @deprecated the main backup to a zip file is storing all text data in JSON
 *         This reader only supports reading {@link RecordType#MetaData}
 *         so we're able to read the info block from older backups;
 *         i.e. {@link ArchiveWriterAbstract} version 2.
 *         See {@link ArchiveReaderAbstract} class docs for the version descriptions.
 *         <p>
 *         Most of the remaining code here is overkill and should be rationalized some day.
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public class XmlRecordReader
        implements RecordReader {

    private static final String ERROR_UNABLE_TO_PROCESS_XML_RECORD = "Unable to process XML ";

    @Nullable
    private final Locale userLocale;

    /**
     * Stack for popping tags on if we go into one.
     * This is of course overkill, just to handle the list/set set,
     * but it's clean and future proof
     */
    private final Deque<TagInfo> tagStack = new ArrayDeque<>();

    /** a simple Holder for the current tag name and attributes. Pushed/pulled on the stack. */
    private TagInfo currentTag;

    /**
     * Constructor.
     *
     * @param context Current context
     */
    public XmlRecordReader(@NonNull final Context context) {
        userLocale = context.getResources().getConfiguration().getLocales().get(0);
    }

    @Override
    @NonNull
    public Optional<ArchiveMetaData> readMetaData(@NonNull final Context context,
                                                  @NonNull final ArchiveReaderRecord record)
            throws DataReaderException,
                   IOException {
        final Bundle bundle = ServiceLocator.newBundle();
        fromXml(record, new InfoReader(bundle));
        if (bundle.isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(new ArchiveMetaData(bundle));
        }
    }

    @Override
    @NonNull
    public ImportResults read(@NonNull final Context context,
                              @NonNull final ArchiveReaderRecord record,
                              @NonNull final ImportHelper unused,
                              @NonNull final ProgressListener progressListener)
            throws DataReaderException,
                   IOException {
        // No longer supported
        return new ImportResults();
    }

    /**
     * Internal routine to update the passed EntityAccessor from an XML file.
     *
     * @param record   source to read from
     * @param accessor the EntityReader to convert XML to the object
     *
     * @throws DataReaderException on a decoding/parsing of data issue
     * @throws IOException         on failure
     */
    private void fromXml(@NonNull final ArchiveReaderRecord record,
                         @NonNull final EntityReader<String> accessor)
            throws DataReaderException,
                   IOException {

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        final DefaultHandler handler = new XmlFilterHandler(buildFilters(accessor));

        try {
            // Don't close this stream
            final InputStream is = record.getInputStream();
            final Reader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
            // The sax parser closes streams, which is not good on a Tar archive entry.
            final BufferedReader reader = new BufferedReaderNoClose(isr, RecordReader.BUFFER_SIZE);
            final InputSource source = new InputSource(reader);

            final SAXParser parser = factory.newSAXParser();
            parser.parse(source, handler);

        } catch (@NonNull final SAXException e) {
            // unwrap SAXException using getException() !
            final Exception cause = e.getException();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            // wrap other parser exceptions
            throw new DataReaderException(e);

        } catch (@NonNull final ParserConfigurationException e) {
            throw new DataReaderException(e);
        }
    }

    private XmlFilter buildFilters(@NonNull final EntityReader<String> accessor) {
        final String listRootElement = accessor.getRootTag();
        final String rootElement = accessor.getElementTag();

        // used to read in Set/List data
        final Collection<String> currentStringList = new ArrayList<>();

        final XmlFilter rootFilter = new XmlFilter();
        // A new element under the root
        rootFilter.addFilter(listRootElement, rootElement)
                  .setStartAction(elementContext -> {
                      // use as top-tag
                      currentTag = new TagInfo(elementContext);
                      // we only have a version on the top tag, not on every tag.
                      final String version = elementContext.getAttributes()
                                                           .getValue(XmlUtils.ATTR_VERSION);
                      accessor.startElement(version == null ? 0 : Integer.parseInt(version),
                                            currentTag);
                  })
                  .setEndAction(elementContext -> accessor.endElement());

        // typed tag starts. for both attribute and body based elements.
        final Consumer<ElementContext> startTypedTag = elementContext -> {
            tagStack.push(currentTag);
            currentTag = new TagInfo(elementContext);

            // if we have a value attribute, this tag is done. Handle here.
            if (currentTag.value != null) {
                switch (currentTag.type) {
                    case XmlUtils.TAG_STRING:
                        // attribute Strings are encoded.
                        accessor.putString(currentTag.name, XmlUtils.decode(currentTag.value));
                        break;

                    case XmlUtils.TAG_BOOLEAN:
                        accessor.putBoolean(currentTag.name, Boolean.parseBoolean(
                                currentTag.value));
                        break;

                    case XmlUtils.TAG_INT:
                        accessor.putInt(currentTag.name, Integer.parseInt(currentTag.value));
                        break;

                    case XmlUtils.TAG_LONG:
                        accessor.putLong(currentTag.name, Long.parseLong(currentTag.value));
                        break;

                    case XmlUtils.TAG_FLOAT:
                        accessor.putFloat(currentTag.name, ParseUtils.parseFloat(
                                currentTag.value, userLocale));
                        break;

                    case XmlUtils.TAG_DOUBLE:
                        accessor.putDouble(currentTag.name, ParseUtils.parseDouble(
                                currentTag.value, userLocale));
                        break;

                    default:
                        break;
                }
                currentTag = tagStack.pop();
            }
        };

        // the end of a typed tag with a body
        final Consumer<ElementContext> endTypedTag = elementContext -> {
            try {
                switch (currentTag.type) {
                    case XmlUtils.TAG_STRING:
                        // body Strings use CDATA
                        accessor.putString(currentTag.name, elementContext.getBody());
                        break;

                    case XmlUtils.TAG_SET:
                        accessor.putStringSet(currentTag.name, currentStringList);
                        // cleanup, ready for the next Set
                        currentStringList.clear();
                        break;

                    case XmlUtils.TAG_LIST:
                        accessor.putStringList(currentTag.name, currentStringList);
                        // cleanup, ready for the next List
                        currentStringList.clear();
                        break;

                    case XmlUtils.TAG_SERIALIZABLE:
                        accessor.putSerializable(currentTag.name,
                                                 Base64.decode(elementContext.getBody(),
                                                               Base64.DEFAULT));
                        break;

                    default:
                        break;
                }

                currentTag = tagStack.pop();

            } catch (@NonNull final RuntimeException e) {
                throw new RuntimeException(ERROR_UNABLE_TO_PROCESS_XML_RECORD + currentTag.name
                                           + '(' + currentTag.type + ')', e);
            }
        };

        // typed tags that only use a value attribute only need action on the start of a tag
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_BOOLEAN)
                  .setStartAction(startTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_INT)
                  .setStartAction(startTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_LONG)
                  .setStartAction(startTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_FLOAT)
                  .setStartAction(startTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_DOUBLE)
                  .setStartAction(startTypedTag);

        // typed tags that have bodies.
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_STRING)
                  .setStartAction(startTypedTag)
                  .setEndAction(endTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_SET)
                  .setStartAction(startTypedTag)
                  .setEndAction(endTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_LIST)
                  .setStartAction(startTypedTag)
                  .setEndAction(endTypedTag);
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_SERIALIZABLE)
                  .setStartAction(startTypedTag)
                  .setEndAction(endTypedTag);

        /*
         * The RecordWriter is generating List/Set tags with String/Int sub tags properly,
         * but importing an Element in a Collection is always done as a String in a List (for now?)
         */
        // set/list elements with attributes.
        final Consumer<ElementContext> startElementInCollection = elementContext -> {
            tagStack.push(currentTag);
            currentTag = new TagInfo(elementContext);

            // if we have a value attribute, this tag is done. Handle here.
            if (currentTag.value != null) {
                // yes, switch is silly here. But let's keep it generic and above all, clear!
                switch (currentTag.type) {
                    case XmlUtils.TAG_BOOLEAN:
                    case XmlUtils.TAG_INT:
                    case XmlUtils.TAG_LONG:
                    case XmlUtils.TAG_FLOAT:
                    case XmlUtils.TAG_DOUBLE:
                        currentStringList.add(currentTag.value);
                        break;

                    default:
                        break;
                }

                currentTag = tagStack.pop();
            }
        };

        // set/list elements with bodies.
        final Consumer<ElementContext> endElementInCollection = elementContext -> {
            // handle tags with bodies.
            try {
                // yes, switch is silly here. But let's keep it generic and above all, clear!
                switch (currentTag.type) {
                    // No support for list/set inside a list/set (no point)
                    case XmlUtils.TAG_SERIALIZABLE:
                        // serializable is indeed just added as a string...
                        // this 'case' is only here for completeness sake.
                    case XmlUtils.TAG_STRING:
                        // body strings use CDATA
                        currentStringList.add(elementContext.getBody());
                        break;

                    default:
                        break;
                }

                currentTag = tagStack.pop();

            } catch (@NonNull final RuntimeException e) {
                throw new RuntimeException(ERROR_UNABLE_TO_PROCESS_XML_RECORD + currentTag, e);
            }
        };


        // Set<String>. The String's are body based.
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_SET, XmlUtils.TAG_STRING)
                  .setStartAction(startElementInCollection)
                  .setEndAction(endElementInCollection);
        // List<String>. The String's are body based.
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_LIST, XmlUtils.TAG_STRING)
                  .setStartAction(startElementInCollection)
                  .setEndAction(endElementInCollection);

        // Set<Integer>. The ints are attribute based.
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_SET, XmlUtils.TAG_INT)
                  .setStartAction(startElementInCollection);
        // List<Integer>. The ints are attribute based.
        rootFilter.addFilter(listRootElement, rootElement, XmlUtils.TAG_LIST, XmlUtils.TAG_INT)
                  .setStartAction(startElementInCollection);

        return rootFilter;
    }

    /**
     * Class to provide access to a subset of the methods of collections.
     *
     * @param <K> Type of the collection key
     */
    interface EntityReader<K> {

        /**
         * @return the tag name for the list
         */
        @NonNull
        String getRootTag();

        /**
         * @return the tag name for an element in the list
         */
        @NonNull
        String getElementTag();

        /**
         * Callback at the start of each element in the list.
         *
         * @param version of the XML schema for this element, or 0 if not present
         * @param tag     the info about the top tag
         */
        default void startElement(final int version,
                                  @NonNull final TagInfo tag) {

        }

        /**
         * Callback at the end of each element in the list.
         */
        default void endElement() {

        }

        /**
         * Subtag of an element consisting of name/value pairs for each potentially supported type.
         */
        void putString(@NonNull K key,
                       @NonNull String value);

        void putBoolean(@NonNull K key,
                        boolean value);

        void putInt(@NonNull K key,
                    int value);

        default void putFloat(@NonNull final K key,
                              final float value) {
            throw new IllegalArgumentException("Float, key=" + key);
        }

        default void putLong(@NonNull final K key,
                             final long value) {
            throw new IllegalArgumentException("Long, key=" + key);
        }

        default void putDouble(@NonNull final K key,
                               final double value) {
            throw new IllegalArgumentException("Double, key=" + key);
        }

        /**
         * Note the values can be passed in as a List or a Set,
         * but store should be a Set of some type.
         */
        default void putStringSet(@NonNull final K key,
                                  @NonNull final Collection<String> value) {
            throw new IllegalArgumentException("StringSet, key=" + key);
        }

        /**
         * Note the values can be passed in as a List or a Set,
         * but store should be a List of some type.
         */
        default void putStringList(@NonNull final K key,
                                   @NonNull final Collection<String> value) {
            throw new IllegalArgumentException("StringList, key=" + key);
        }

        default void putSerializable(@NonNull final K key,
                                     @NonNull final Serializable value) {
            throw new IllegalArgumentException("Serializable, key=" + key);
        }
    }

    /**
     * Value class to preserve data while parsing XML input.
     */
    public static class TagInfo {

        /**
         * attribute with the key into the collection.
         * For convenience, also part of {@link #attrs}.
         */
        @NonNull
        public final String name;
        /**
         * optional. {@code 0} if none.
         * For convenience, also part of {@link #attrs}.
         */
        public final int id;
        /** The type of the element as set by the tag itself. */
        @NonNull
        final String type;
        /** All attributes on this tag. */
        final Attributes attrs;
        /**
         * optional value attribute (e.g. int,boolean,...),
         * not used when the tag body is used (String,..).
         * <p>
         * <p>
         * For convenience, also part of {@link #attrs}.
         */
        @Nullable
        final String value;

        /**
         * Constructor.
         *
         * @param elementContext of the XML tag
         */
        TagInfo(@NonNull final ElementContext elementContext) {
            type = elementContext.getLocalName();
            attrs = elementContext.getAttributes();

            name = attrs.getValue(XmlUtils.ATTR_NAME);
            value = attrs.getValue(XmlUtils.ATTR_VALUE);

            int tmpId = 0;
            final String idStr = attrs.getValue(XmlUtils.ATTR_ID);
            if (idStr != null) {
                try {
                    tmpId = Integer.parseInt(idStr);
                } catch (@NonNull final NumberFormatException ignore) {
                    // ignore
                }
            }
            id = tmpId;
        }

        @Override
        @NonNull
        public String toString() {
            return "TagInfo{"
                   + "name=`" + name + '`'
                   + ", attrs=" + attrs
                   + ", type=`" + type + '`'
                   + ", id=" + id
                   + ", value=`" + value + '`'
                   + '}';
        }
    }

    /**
     * Supports a *single* {@link RecordType#MetaData} block,
     * enclosed inside a {@link InfoReader#TAG_ROOT}.
     */
    static class InfoReader
            implements EntityReader<String> {

        static final String TAG_ROOT = "info-list";
        @NonNull
        private final Bundle mBundle;

        InfoReader(@NonNull final Bundle bundle) {
            mBundle = bundle;
        }

        @Override
        @NonNull
        public String getRootTag() {
            return TAG_ROOT;
        }

        @Override
        @NonNull
        public String getElementTag() {
            return RecordType.MetaData.getName();
        }

        @Override
        public void putString(@NonNull final String key,
                              @NonNull final String value) {
            mBundle.putString(key, value);
        }

        @Override
        public void putBoolean(@NonNull final String key,
                               final boolean value) {
            mBundle.putBoolean(key, value);
        }

        @Override
        public void putInt(@NonNull final String key,
                           final int value) {
            mBundle.putInt(key, value);
        }

        @Override
        public void putLong(@NonNull final String key,
                            final long value) {
            mBundle.putLong(key, value);
        }

        @Override
        public void putFloat(@NonNull final String key,
                             final float value) {
            mBundle.putFloat(key, value);
        }

        @Override
        public void putDouble(@NonNull final String key,
                              final double value) {
            mBundle.putDouble(key, value);
        }

        @Override
        public void putSerializable(@NonNull final String key,
                                    @NonNull final Serializable value) {
            mBundle.putSerializable(key, value);
        }
    }

    /**
     * The sax parser closes streams, which is not good on a Tar archive entry.
     */
    private static class BufferedReaderNoClose
            extends BufferedReader {

        BufferedReaderNoClose(@NonNull final Reader in,
                              @SuppressWarnings("SameParameterValue") final int flags) {
            super(in, flags);
        }

        @Override
        public void close() {
            // ignore the close call from the SAX parser.
            // We'll close it ourselves when appropriate.
        }
    }
}
