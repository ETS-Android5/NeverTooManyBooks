/*
 * @copyright 2013 Philip Warner
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
package com.eleybourn.bookcatalogue.backup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;

import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter.ElementContext;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlFilter.XmlHandler;
import com.eleybourn.bookcatalogue.searches.goodreads.api.XmlResponseParser;
import com.eleybourn.bookcatalogue.utils.Base64;
import com.eleybourn.bookcatalogue.debug.Logger;

/**
 * Utility functions for backup/restore code
 * 
 * @author pjw
 */
public class BackupUtils {

	/**
	 * Class to provide access to a subset of the methods of collections.
	 * 
	 * @author pjw
	 *
	 * @param <T>	Type of the collection key
	 */
	private interface CollectionAccessor<T> {
		/** Get the collection of keys */
		Set<T> keySet();
		/** Get the object for the specified key */
		Object get(T key);
		/** Process the passed tring to store int the collection */
        void putItem(Bundle bundle, String key, String type, String value) throws IOException;
	}

	/**
	 * Collection accessor for bundles
	 * 
	 * @author pjw
	 *
	 */
	private static class BundleAccessor implements CollectionAccessor<String> {
		final Bundle mBundle;
		BundleAccessor(Bundle b) {
			mBundle = b;
		}
		@Override
		public Set<String> keySet() {
			return mBundle.keySet();
		}
		@Override
		public Object get(String key) {
			return mBundle.get(key);
		}	
		@Override
		public void putItem(Bundle bundle, String key, String type, String value) throws IOException {
			switch (type) {
				case BackupUtils.TYPE_INTEGER:
					mBundle.putInt(key, Integer.parseInt(value));
					break;
				case BackupUtils.TYPE_LONG:
					mBundle.putLong(key, Long.parseLong(value));
					break;
				case BackupUtils.TYPE_FLOAT:
					mBundle.putFloat(key, Float.parseFloat(value));
					break;
				case BackupUtils.TYPE_DOUBLE:
					mBundle.putDouble(key, Double.parseDouble(value));
					break;
				case BackupUtils.TYPE_STRING:
					mBundle.putString(key, value);
					break;
				case BackupUtils.TYPE_BOOLEAN:
					mBundle.putBoolean(key, Boolean.parseBoolean(value));
					break;
				case BackupUtils.TYPE_SERIALIZABLE:
					Serializable s = Base64.decode(value);
					mBundle.putSerializable(key, s);
					break;
			}
		}
	}

	/**
	 * Collection accessor for SharedPreferences
	 * 
	 * @author pjw
	 *
	 */
	private static class PreferencesAccessor implements CollectionAccessor<String> {
		final SharedPreferences mPrefs;
		final Map<String,?> mMap;
		Editor mEditor;

		PreferencesAccessor(SharedPreferences p) {
			mPrefs = p;
			mMap = p.getAll();
		}
		
		void beginEdit() {
			mEditor = mPrefs.edit();
			mEditor.clear();
		}

		void endEdit() {
			mEditor.commit();
			mEditor = null;
		}

		@Override
		public Set<String> keySet() {
			return mMap.keySet();
		}
		@Override
		public Object get(String key) {
			return mMap.get(key);
		}
		@Override
		public void putItem(Bundle bundle, String key, String type, String value) {
			switch (type) {
				case BackupUtils.TYPE_INTEGER:
					mEditor.putInt(key, Integer.parseInt(value));
					break;
				case BackupUtils.TYPE_LONG:
					mEditor.putLong(key, Long.parseLong(value));
					break;
				case BackupUtils.TYPE_FLOAT:
					mEditor.putFloat(key, Float.parseFloat(value));
					break;
				case BackupUtils.TYPE_STRING:
					mEditor.putString(key, value);
					break;
				case BackupUtils.TYPE_BOOLEAN:
					mEditor.putBoolean(key, Boolean.parseBoolean(value));
					break;
				default:
					throw new RuntimeException("Unable write data of type '" + type + "' to preferences");
			}
		}
	}

	/**
	 * Write preferences to an XML stream.
	 */
	public static void preferencesToXml(BufferedWriter out, SharedPreferences prefs) throws IOException {
		PreferencesAccessor a = new PreferencesAccessor(prefs);
		collectionToXml(out, a);
	}
	
	/**
	 * Read preferences from an XML stream.
	 */
	public static void preferencesFromXml(BufferedReader in, SharedPreferences prefs) throws IOException {
		PreferencesAccessor a = new PreferencesAccessor(prefs);
		a.beginEdit();
		collectionFromXml(in, a);
		a.endEdit();
	}

	/**
	 * Write Bundle to an XML stream.
	 */
	public static void bundleToXml(BufferedWriter out, Bundle bundle) throws IOException {
		BundleAccessor a = new BundleAccessor(bundle);
		collectionToXml(out, a);
	}

	/**
	 * Read Bundle from an XML stream.
	 */
	public static Bundle bundleFromXml(BufferedReader in) throws IOException {
		final Bundle bundle = new Bundle();
		BundleAccessor a = new BundleAccessor(bundle);
		collectionFromXml(in, a);
		return bundle;
	}

	/**
	 * Internal routine to send the passed CollectionAccessor data to an XML file.
	 */
	private static void collectionToXml(BufferedWriter out, CollectionAccessor<String> col) throws IOException {
		out.append("<collection>\n");
		for(String key: col.keySet() ) {
			String type;
			String value;
			Object o = col.get(key);
			if (o instanceof Integer) {
				type = BackupUtils.TYPE_INTEGER;
				value = o.toString();
			} else if (o instanceof Long) {
				type = BackupUtils.TYPE_LONG;
				value = o.toString();
			} else if (o instanceof Float) {
				type = BackupUtils.TYPE_FLOAT;
				value = o.toString();
			} else if (o instanceof Double) {
				type = BackupUtils.TYPE_DOUBLE;
				value = o.toString();
			} else if (o instanceof String) {
				type = BackupUtils.TYPE_STRING;
				value = o.toString();
			} else if (o instanceof Boolean) {
				type = BackupUtils.TYPE_BOOLEAN;
				value = o.toString();
			} else if (o instanceof Serializable) {
				type = BackupUtils.TYPE_SERIALIZABLE;
				value = Base64.encodeObject((Serializable)o);
			} else {
				throw new RuntimeException("Unable write data of type '" + o.getClass().getSimpleName() + "' to XML");
			}
			out.append("<item name=\"").append(key).append("\" type=\"").append(type).append("\">").append(value).append("</item>\n");
		}
		out.append("</collection>\n");		
	}

	/**
	 * Record to preservr data while parsing XML input
	 * 
	 * @author pjw
	 */
	private static class ItemInfo {
		public String name;
		public String type;
	}

	/**
	 * Internal routine to update the passed CollectionAccessor from an XML file.
	 */
	private static void collectionFromXml(BufferedReader in, final CollectionAccessor<String> accessor) throws IOException {
		final Bundle bundle = new Bundle();
		XmlFilter rootFilter;
		rootFilter = new XmlFilter("");
		final ItemInfo info = new ItemInfo();

		XmlFilter.buildFilter(rootFilter, "collection", "item")
			.setStartAction(new XmlHandler(){
				@Override
				public void process(ElementContext context) {
					info.name = context.attributes.getValue("name");
					info.type = context.attributes.getValue("type");
				}}, null)
			.setEndAction(new XmlHandler(){
				@Override
				public void process(ElementContext context) {
					try {
						accessor.putItem(bundle, info.name, info.type, context.body);
					} catch (IOException e) {
						Logger.logError(e);
						throw new RuntimeException("Unable to process XML entity " + info.name + " (" + info.type + ")", e);
					}
				}}, null);

		XmlResponseParser handler = new XmlResponseParser(rootFilter);
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser parser;
		try {
			parser = factory.newSAXParser();
		} catch (Exception e) {
			Logger.logError(e);
			throw new RuntimeException("Unable to create XML parser", e);
		}

		InputSource is = new InputSource();
		is.setCharacterStream(in);
		try {
			parser.parse(is, handler);
		} catch (SAXException e) {
			Logger.logError(e);
			throw new IOException("Malformed XML");
		}

	}

	public static final String TYPE_INTEGER = "Int";
	public static final String TYPE_LONG = "Long";
	public static final String TYPE_DOUBLE = "Dbl";
	public static final String TYPE_FLOAT = "Flt";
	public static final String TYPE_BOOLEAN = "Bool";
	public static final String TYPE_STRING = "Str";
	public static final String TYPE_SERIALIZABLE = "Serial";
}
