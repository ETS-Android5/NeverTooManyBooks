/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverTooManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @2016 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its
 * current form. It was however largely rewritten/refactored and any
 * comments on this fork should be directed at HardBackNutter and not
 * at the original creators.
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
package com.hardbacknutter.nevertoomanybooks.datamanager;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DigitsKeyListener;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.App;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.debug.Logger;
import com.hardbacknutter.nevertoomanybooks.utils.Csv;
import com.hardbacknutter.nevertoomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertoomanybooks.utils.ImageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LanguageUtils;
import com.hardbacknutter.nevertoomanybooks.utils.LinkifyUtils;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * This is the class that manages data and views for an Activity; access to the data that
 * each view represents should be handled via this class (and its related classes) where
 * possible.
 * <ul>Features provides are:
 * <li> handling of visibility via preferences</li>
 * <li> handling of group(RowKind) visibility via the 'mGroup' property of a field.</li>
 * <li> understanding of kinds of views (setting a Checkbox (Checkable) value to 'true' will work
 * as expected as will setting the value of a Spinner). As new view types are added, it
 * will be necessary to add new {@link FieldDataAccessor} implementations.</li>
 * <li> Custom data accessors and formatter to provide application-specific data rules.</li>
 * <li> simplified extraction of data to a {@link ContentValues} collection.</li>
 * </ul>
 * <p>
 * Formatter and Accessors
 * <p>
 * It is up to each accessor to decide what to do with any formatter defined for a field.
 * The fields themselves have extract() and format() methods that will apply the formatter
 * functions (if present) or just pass the value through.
 * <p>
 * On a set(), the accessor should call format() function then apply the value
 * On a get() the accessor should retrieve the value and apply the extract() function.
 * <p>
 * The use of a formatter typically results in all values being converted to strings so
 * they should be avoided for most non-string data.
 * <ul>Data flows to and from a view as follows:
 * <li>IN  ( no formatter ):
 * <br>(DataManager/Bundle) -> transform (in accessor) -> View</li>
 * <li>IN  (with formatter):
 * <br>(DataManager/Bundle) -> format() (via accessor) -> transform (in accessor) -> View</li>
 * <li>OUT ( no formatter ):
 * <br>(DataManager/Bundle) -> transform (in accessor) -> Object</li>
 * <li>OUT (with formatter):
 * <br>(DataManager/Bundle) -> transform (in accessor) -> extract (via accessor) -> Object</li>
 * </ul>
 * <p>
 */
public class Fields {

    /** the list with all fields. */
    @SuppressLint("UseSparseArrays")
    private final Map<Integer, Field> mAllFields = new HashMap<>();

    /** TextEdit fields will be watched. */
    @Nullable
    private AfterFieldChangeListener mAfterFieldChangeListener;

    /**
     * @param listener the listener for field changes
     */
    public void setAfterFieldChangeListener(@Nullable final AfterFieldChangeListener listener) {
        mAfterFieldChangeListener = listener;
    }

    private void afterFieldChange(@NonNull final Field<?> field,
                                  @Nullable final Object newValue) {
        if (mAfterFieldChangeListener != null) {
            //noinspection unchecked
            mAfterFieldChangeListener.afterFieldChange(field, newValue);
        }
    }

    public boolean isEmpty() {
        return mAllFields.isEmpty();
    }

    /**
     * Add a field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<String> addString(@IdRes final int fieldId,
                                   @NonNull final View fieldView,
                                   @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<String> field = new Field<>(this, fieldId, fieldView, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a field to this collection.
     *
     * @param fieldId         Layout ID
     * @param key             Key used to access a {@link DataManager} or {@code Bundle}.
     *                        Set to "" to suppress all access.
     * @param visibilityGroup Group name to determine visibility.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<String> addString(@IdRes final int fieldId,
                                   @NonNull final View fieldView,
                                   @NonNull final String key,
                                   @NonNull final String visibilityGroup) {
        Field<String> field = new Field<>(this, fieldId, fieldView, key, visibilityGroup);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a Boolean field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @NonNull
    public Field<Boolean> addBoolean(@IdRes final int fieldId,
                                     @NonNull final View fieldView,
                                     @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<Boolean> field = new Field<>(this, fieldId, fieldView, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a Long field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Field<Long> addLong(@IdRes final int fieldId,
                               @NonNull final View fieldView,
                               @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<Long> field = new Field<>(this, fieldId, fieldView, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a Float field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @SuppressWarnings("UnusedReturnValue")
    @NonNull
    public Field<Float> addFloat(@IdRes final int fieldId,
                                 @NonNull final View fieldView,
                                 @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<Float> field = new Field<>(this, fieldId, fieldView, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a Monetary field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Field<Double> addMonetary(@IdRes final int fieldId,
                                     @NonNull final View fieldView,
                                     @NonNull final String key) {
        if (BuildConfig.DEBUG /* always */) {
            // sanity check
            if (key.isEmpty()) {
                throw new IllegalArgumentException("key should not be empty");
            }
        }
        Field<Double> field = new Field<>(this, fieldId, fieldView, key, key);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Add a Monetary field to this collection.
     *
     * @param fieldId Layout ID
     * @param key     Key used to access a {@link DataManager} or {@code Bundle}.
     *
     * @return The resulting Field.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Field<Double> addMonetary(@IdRes final int fieldId,
                                     @NonNull final View fieldView,
                                     @NonNull final String key,
                                     @NonNull final String visibilityGroup) {
        Field<Double> field = new Field<>(this, fieldId, fieldView, key, visibilityGroup);
        mAllFields.put(fieldId, field);
        return field;
    }

    /**
     * Return the Field associated with the passed layout ID.
     *
     * @param <T> type of Field value.
     *
     * @return Associated Field.
     *
     * @throws IllegalArgumentException if the field does not exist.
     */
    @NonNull
    public <T> Field<T> getField(@IdRes final int fieldId) {
        //noinspection unchecked
        Field<T> field = (Field<T>) mAllFields.get(fieldId);
        if (field != null) {
            return field;
        }

        throw new IllegalArgumentException("fieldId= 0x" + Integer.toHexString(fieldId));
    }

    /**
     * Load fields from the passed bundle.
     *
     * @param rawData       with data to load.
     * @param withOverwrite if {@code true}, all fields are copied.
     *                      If {@code false}, only non-existent fields are copied.
     */
    public void setAllFrom(@NonNull final Bundle rawData,
                           final boolean withOverwrite) {
        if (withOverwrite) {
            for (Field field : mAllFields.values()) {
                field.setValueFrom(rawData);
            }
        } else {
            for (Field field : mAllFields.values()) {
                if (!field.getKey().isEmpty() && rawData.containsKey(field.getKey())) {
                    Object value = rawData.get(field.getKey());
                    if (value != null) {
                        //noinspection unchecked
                        field.setValue(value);
                    }
                }
            }
        }
    }

    /**
     * Load all fields from the passed {@link DataManager}.
     *
     * @param dataManager DataManager to load Field objects from.
     */
    public void setAllFrom(@NonNull final DataManager dataManager) {
        for (Field field : mAllFields.values()) {
            field.setValueFrom(dataManager);
        }
    }

    /**
     * Save all fields to the passed {@link DataManager}.
     *
     * @param dataManager DataManager to put Field objects in.
     */
    public void putAllInto(@NonNull final DataManager dataManager) {
        for (Field field : mAllFields.values()) {
            if (!field.getKey().isEmpty()) {
                field.putValueInto(dataManager);
            }
        }
    }

    /**
     * Reset all field visibility based on user preferences.
     *
     * @param parent      parent view for all fields.
     * @param hideIfEmpty hide the field if it's empty
     */
    public void resetVisibility(@NonNull final View parent,
                                final boolean hideIfEmpty) {
        for (Field field : mAllFields.values()) {
            field.resetVisibility(parent, hideIfEmpty);
        }
    }

    public void setParentView(@NonNull final View parentView) {
        for (Field field : mAllFields.values()) {
            field.setParentView(parentView);
        }
    }


    /**
     * added to the Fields collection with (2018-11-11) a simple call to setDirty(true).
     *
     * @param <T> type of Field value.
     */
    public interface AfterFieldChangeListener<T> {

        void afterFieldChange(@NonNull Field<T> field,
                              @Nullable T newValue);
    }

    /**
     * Interface for view-specific accessors. One of these must be implemented for
     * each view type that is supported.
     *
     * @param <T> type of Field value.
     */
    public interface FieldDataAccessor<T> {

        /**
         * Hook up the view. Reminder: do <strong>NOT</strong> set the view in the constructor.
         */
        void setView(@NonNull View view);

        /**
         * Set the optional formatter.
         *
         * @param formatter to use
         */
        void setFormatter(@NonNull FieldFormatter formatter);

        /**
         * Call the wrapped formatter. See {@link FieldFormatter}.
         *
         * @param source Input value
         *
         * @return The formatted value.
         */
        @NonNull
        String format(@Nullable T source);

        /**
         * Call the wrapped formatter. See {@link FieldFormatter}.
         *
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        T extract(@NonNull String source);

        /**
         * Get the value from the view associated with the Field and return it as an Object.
         *
         * <strong>Note:</strong> an implementation should always return a value.
         * This would/should usually be the default the Widget.
         * e.g. a text based widget should return "" even if the value was never set.
         *
         * @return the value
         */
        @NonNull
        T getValue();

        /**
         * Use the passed value to set the Field.
         * <p>
         * If {@code null} is passed in, the implementation should set the widget to
         * its native default value. (e.g. string -> "", number -> 0, etc)
         *
         * @param value to set.
         */
        void setValue(@Nullable T value);

        /**
         * Fetch the value from the passed Bundle, and set the Field.
         *
         * @param source Collection to load data from.
         */
        void setValue(@NonNull Bundle source);

        /**
         * Fetch the value from the passed DataManager, and set the Field.
         *
         * @param source Collection to load data from.
         */
        void setValue(@NonNull DataManager source);

        /**
         * Get the value from the view associated with the Field
         * and put a <strong>native typed value</strong> in the passed collection.
         *
         * @param target Collection to save value into.
         */
        void getValueAndPut(@NonNull DataManager target);

        /**
         * Check if this field is considered to be 'empty'.
         * The encapsulated type decides what 'empty' means.
         * <p>
         * If the test can be optimized, then you should override this default method.
         *
         * @return {@code true} if empty.
         */
        default boolean isEmpty() {
            return isEmpty(getValue());
        }

        /**
         * Generic helper method to see if the given value is considered empty.
         * <p>
         * This default implementation considers numbers == 0, boolean == false
         * and empty strings to be empty.
         *
         * @param value to test
         *
         * @return {@code true} if empty.
         */
        default boolean isEmpty(@Nullable final T value) {
            return value == null
                   || value instanceof Number && ((Number) value).doubleValue() == 0.0d
                   || value instanceof Boolean && !(Boolean) value
                   || value.toString().isEmpty();
        }
    }

    /**
     * Interface definition for Field formatter.
     *
     * <strong>Do not store Context or View in a formatter.</strong>
     *
     * @param <T> type of Field value.
     */
    public interface FieldFormatter<T> {

        /**
         * Format a string for applying to a View.
         * If the source is {@code null}, implementations should return "" (and log an error)
         *
         * @param field  The field
         * @param source Input value
         *
         * @return The formatted value.
         */
        @NonNull
        String format(@NonNull Field<T> field,
                      @Nullable T source);

        /**
         * This method is intended to be called from a {@link FieldDataAccessor}.
         * <p>
         * Extract a formatted {@code String} from the displayed version.
         *
         * @param field  The field
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        T extract(@NonNull Field<T> field,
                  @NonNull String source);
    }

    /**
     * TextWatcher for EditText fields. Sets the Field value after each EditText change.
     */
    private static class DecimalTextWatcher
            implements TextWatcher {
        //    public static class DecimalDigitsInputFilter implements InputFilter {
//
//        Pattern mPattern;
//
//        public DecimalDigitsInputFilter(int digitsBeforeZero,
//                                        int digitsAfterZero) {
//            DecimalFormatSymbols d = new DecimalFormatSymbols(Locale.getDefault());
//            String s = "\\" + d.getDecimalSeparator();
//            mPattern = Pattern.compile(
//                    "[0-9]{0," + (digitsBeforeZero - 1) + "}+"
//                    + "((" + s + "[0-9]{0," + (digitsAfterZero - 1) + "})?)"
//                    + ""
//                    + "|(" + s + ")?");
//        }
//
//        @Override
//        public CharSequence filter(CharSequence source,
//                                   int start,
//                                   int end,
//                                   Spanned dest,
//                                   int dstart,
//                                   int dend) {
//
//            Matcher matcher = mPattern.matcher(dest);
//            if (!matcher.matches())
//                return "";
//            return null;
//        }
//    }

        private static final String DIGITS = "0123456789";

        private final String mDecimalSeparator;
        /**
         * Strong reference to View is fine.
         * This watcher will get destroyed when the View gets destroyed.
         * <strong>Note:</strong> do NOT keep a strong reference to the watcher itself!
         */
        @NonNull
        private final EditText mView;

        DecimalTextWatcher(@NonNull final EditText view) {
            mView = view;
            DecimalFormat nf = (DecimalFormat) DecimalFormat.getInstance(Locale.getDefault());
            DecimalFormatSymbols symbols = nf.getDecimalFormatSymbols();
            mDecimalSeparator = Character.toString(symbols.getDecimalSeparator());
        }

        @Override
        public void beforeTextChanged(@NonNull final CharSequence s,
                                      final int start,
                                      final int count,
                                      final int after) {

        }

        @Override
        public void onTextChanged(@NonNull final CharSequence s,
                                  final int start,
                                  final int before,
                                  final int count) {

        }

        @Override
        public void afterTextChanged(@NonNull final Editable editable) {
            // allow only one decimal separator
            if (editable.toString().contains(mDecimalSeparator)) {
                mView.setKeyListener(DigitsKeyListener.getInstance(DIGITS));
            } else {
                mView.setKeyListener(DigitsKeyListener.getInstance(DIGITS + mDecimalSeparator));
            }

        }
    }

    /**
     * TextWatcher for EditText fields. Sets the Field value after each EditText change.
     *
     * @param <T> type of Field value.
     */
    private static class ReformatTextWatcher<T>
            implements TextWatcher {

        private static final String TAG = "ReformatTextWatcher";

        @NonNull
        private final Field<T> mField;

        ReformatTextWatcher(@NonNull final Field<T> field) {
            mField = field;
        }

        @Override
        public void beforeTextChanged(@NonNull final CharSequence s,
                                      final int start,
                                      final int count,
                                      final int after) {
        }

        @Override
        public void onTextChanged(@NonNull final CharSequence s,
                                  final int start,
                                  final int before,
                                  final int count) {
        }

        @Override
        public void afterTextChanged(@NonNull final Editable s) {
            // extract to native value
            T value = mField.extract(s.toString().trim());

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.FIELD_TEXT_WATCHER) {
                Log.d(TAG, "afterTextChanged|s=`" + s.toString() + '`'
                           + "|extract=`" + value + '`');
            }

            // Set the field with the new data.
            // This will also redisplay the data, re-formatted as needed.
            mField.setValue(value);
        }
    }

    /**
     * Base implementation.
     *
     * @param <T> type of Field value.
     */
    private abstract static class BaseDataAccessor<T>
            implements FieldDataAccessor<T> {

        private static final String TAG = "BaseDataAccessor";

        @NonNull
        final Field<T> mField;
        @Nullable
        WeakReference<View> mView;
        @Nullable
        private FieldFormatter<T> mFormatter;

        BaseDataAccessor(@NonNull final Field<T> field) {
            mField = field;
        }

        @Override
        public void setView(@NonNull final View view) {
            mView = new WeakReference<>(view);
        }

        /**
         * Add on onTouch listener that signals a 'dirty' event when touched.
         *
         * @param view The view to watch
         */
        @SuppressLint("ClickableViewAccessibility")
        void addTouchSignalsDirty(@NonNull final View view) {
            // Touching this View is considered a change to the field.
            //TODO: We need to find a better way to handle this as this can cause a false-positive
            view.setOnTouchListener((v, event) -> {
                if (MotionEvent.ACTION_UP == event.getAction()) {
                    mField.getFields().afterFieldChange(mField, null);
                }
                return false;
            });
        }

        public void setFormatter(@NonNull final FieldFormatter formatter) {
            //noinspection unchecked
            mFormatter = formatter;
        }

        /**
         * Wrapper around {@link FieldFormatter#format}.
         *
         * @param value to format
         *
         * @return The formatted value
         */
        @NonNull
        public String format(@Nullable final T value) {
            if (mFormatter != null) {
                try {
                    return mFormatter.format(mField, value);

                } catch (@NonNull final ClassCastException e) {
                    // Due to the way a Book loads data from the database,
                    // it's possible that it gets the column type wrong.
                    // See {@link BookCursor} class docs.
                    Logger.error(App.getAppContext(), TAG, e, value);
                }
            }

            // if we don't have a formatter, or if we had a ClassCastException
            if (isEmpty(value)) {
                return "";
            }
            //noinspection ConstantConditions
            return value.toString();
        }

        /**
         * Wrapper around {@link FieldFormatter#extract}.
         *
         * @param source The value to be back-translated
         *
         * @return The extracted value
         */
        @NonNull
        public T extract(@NonNull final String source) {
            if (mFormatter != null) {
                return mFormatter.extract(mField, source);
            } else {
                // all non-String field should have formatters.
                // If we get an Exception here then the developer made a boo-boo.
                return (T) source;
            }
        }
    }

    /**
     * Implementation that stores and retrieves data from a local string variable
     * for fields without a layout.
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    private static class StringDataAccessor
            extends BaseDataAccessor<String> {

        @NonNull
        private String mLocalValue = "";

        StringDataAccessor(@NonNull final Field<String> field) {
            super(field);
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            target.putString(mField.getKey(), mLocalValue);
        }

        @Override
        public boolean isEmpty() {
            return mLocalValue.isEmpty();
        }

        @NonNull
        @Override
        public String getValue() {
            return mLocalValue;
        }

        @Override
        public void setValue(@Nullable final String value) {
            mLocalValue = value != null ? value.trim() : "";
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getString(mField.getKey(), ""));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(mField.getKey()));
        }
    }

    /**
     * Base implementation for TextView/EditText.
     *
     * @param <T> type of Field value.
     */
    private abstract static class TextFieldAccessor<T>
            extends BaseDataAccessor<T> {

        /**
         * Constructor.
         *
         * @param field to use
         */
        TextFieldAccessor(@NonNull final Field<T> field) {
            super(field);
        }

        @Override
        public boolean isEmpty() {
            String value = getValue().toString();
            return value.isEmpty()
                   // Integer/Long
                   || "0".equals(value)
                   // Float/Double
                   || "0.0".equals(value)
                   // Boolean (theoretically this should not happen... but paranoia... ).
                   || "false".equals(value);
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            target.put(mField.getKey(), getValue());
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue((T) source.get(mField.getKey()));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue((T) source.get(mField.getKey()));
        }
    }

    /**
     * Implementation that stores and retrieves data from a TextView.
     * This is treated differently to an EditText in that HTML is (optionally) displayed properly.
     * <p>
     * The actual value is simply stored in a local variable. No attempt to extract is done.
     * <p>
     * Uses {@link FieldFormatter#format} only.
     *
     * @param <T> type of Field value.
     */
    private static class TextViewAccessor<T>
            extends TextFieldAccessor<T> {

        private boolean mFormatHtml;

        @Nullable
        private T mRawValue;

        /**
         * Constructor.
         *
         * @param field to use
         */
        TextViewAccessor(@NonNull final Field<T> field) {
            super(field);
        }

        @NonNull
        @Override
        public T getValue() {
            if (mRawValue != null) {
                return mRawValue;
            } else {
                // The only situation where mRawValue would be null is when T is a String.
                // If we violate that, the next line will cause an Exception
                return (T) "";
            }
        }

        @Override
        public void setValue(@Nullable final T value) {
            //noinspection ConstantConditions
            TextView view = (TextView) mView.get();
            if (view != null) {
                mRawValue = value;
                if (mFormatHtml) {
                    String body = format(mRawValue);

                    view.setText(LinkifyUtils.fromHtml(body));
                    view.setMovementMethod(LinkMovementMethod.getInstance());

                    view.setFocusable(true);
                    view.setTextIsSelectable(true);

                } else {
                    view.setText(format(mRawValue));
                }
            }
        }

        /**
         * Set the TextViewAccessor to support HTML.
         *
         * @param showHtml if {@code true} this view will display HTML
         */
        @SuppressWarnings("WeakerAccess")
        public void setShowHtml(final boolean showHtml) {
            mFormatHtml = showHtml;
        }
    }

    /**
     * Implementation that stores and retrieves data from an EditText.
     * <p>
     * Uses {@link FieldFormatter#format} and {@link FieldFormatter#extract}.
     *
     * @param <T> type of Field value.
     */
    private static class EditTextAccessor<T>
            extends TextFieldAccessor<T> {

        @NonNull
        private final TextWatcher mReformatTextWatcher;
        private boolean mIsDecimal;

        /**
         * Constructor.
         *
         * @param field to use
         */
        EditTextAccessor(@NonNull final Field<T> field) {
            super(field);
            mReformatTextWatcher = new ReformatTextWatcher<>(field);
        }

        @NonNull
        @Override
        public T getValue() {
            //noinspection ConstantConditions
            return extract(((EditText) mView.get()).getText().toString().trim());
        }

        @Override
        public void setValue(@Nullable final T value) {
            //noinspection ConstantConditions
            EditText view = (EditText) mView.get();
            if (view != null) {
                // 2018-12-11: There was recursion due to the setText call.
                // So now disabling the TextWatcher while doing the latter.
                // We don't want another thread re-enabling the listener before we're done
                synchronized (mReformatTextWatcher) {
                    view.removeTextChangedListener(mReformatTextWatcher);

                    String newVal = format(value);
                    // do not use extract, we compare formatted/formatted value
                    String oldVal = view.getText().toString().trim();
                    if (!newVal.equals(oldVal)) {
                        if (view instanceof AutoCompleteTextView) {
                            // prevent auto-completion to kick in / stop the dropdown from opening.
                            // this happened if the field had the focus when we'd be populating it.
                            ((AutoCompleteTextView) view).setText(newVal, false);
                        } else {
                            view.setText(newVal);
                        }
                    }
                    view.addTextChangedListener(mReformatTextWatcher);
                }
            }
        }

        /**
         * For Locales which use ',' as the decimal separator, the input panel only allows '.'.
         * See class docs: {@link com.hardbacknutter.nevertoomanybooks.utils.ParseUtils}.
         */
        void setDecimalInput() {
            mIsDecimal = true;
        }

        @Override
        public void setView(@NonNull final View view) {
            super.setView(view);
            EditText editText = (EditText) view;

            editText.addTextChangedListener(mReformatTextWatcher);

            if (mIsDecimal) {
                // do not keep a reference to the watcher! Doing so would cause a leak.
                editText.addTextChangedListener(new DecimalTextWatcher(editText));
            }
        }
    }

    /**
     * Spinner accessor. Assumes the Spinner contains a list of Strings and
     * sets the spinner to the matching item.
     * <p>
     * Uses {@link FieldFormatter#format} and {@link FieldFormatter#extract}.
     */
    private static class SpinnerAccessor
            extends BaseDataAccessor<String> {

        SpinnerAccessor(@NonNull final Field<String> field) {
            super(field);
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            target.putString(mField.getKey(), getValue());
        }

        @Override
        public boolean isEmpty() {
            return getValue().isEmpty();
        }

        @Override
        @NonNull
        public String getValue() {
            //noinspection ConstantConditions
            Spinner spinner = (Spinner) mView.get();
            Object selItem = spinner.getSelectedItem();
            if (selItem != null) {
                return extract(selItem.toString().trim());
            } else {
                return "";
            }
        }

        @Override
        public void setValue(@Nullable final String value) {
            //noinspection ConstantConditions
            Spinner spinner = (Spinner) mView.get();
            if (spinner != null) {
                String formatted = format(value);
                for (int i = 0; i < spinner.getCount(); i++) {
                    if (spinner.getItemAtPosition(i).equals(formatted)) {
                        spinner.setSelection(i);
                        return;
                    }
                }
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getString(mField.getKey(), ""));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(mField.getKey()));
        }
    }

    /**
     * Checkable accessor.
     * <p>
     * When set to {@code false} we make it {@code View.GONE} as well.
     *
     * <ul>{@link Checkable} covers more then just a Checkbox:
     * <li>CheckBox, RadioButton, Switch</li>
     * <li>ToggleButton extend CompoundButton</li>
     * <li>CheckedTextView extends TextView</li>
     * </ul>
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    private static class CheckableAccessor
            extends BaseDataAccessor<Boolean> {

        CheckableAccessor(@NonNull final Field<Boolean> field) {
            super(field);
        }

        @Override
        public void setView(@NonNull final View view) {
            super.setView(view);
            addTouchSignalsDirty(view);
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            target.putBoolean(mField.getKey(), getValue());
        }

        @Override
        public boolean isEmpty() {
            return !getValue();
        }

        @NonNull
        @Override
        public Boolean getValue() {
            //noinspection ConstantConditions
            Checkable cb = (Checkable) mView.get();
            return cb.isChecked();
        }

        @Override
        public void setValue(@Nullable final Boolean value) {
            //noinspection ConstantConditions
            Checkable cb = (Checkable) mView.get();
            if (cb != null) {
                if (value != null) {
                    ((View) cb).setVisibility(value ? View.VISIBLE : View.GONE);
                    cb.setChecked(value);
                } else {
                    cb.setChecked(false);
                }
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getBoolean(mField.getKey()));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getBoolean(mField.getKey()));
        }
    }

    /**
     * RatingBar accessor.
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    private static class RatingBarAccessor
            extends BaseDataAccessor<Float> {

        RatingBarAccessor(@NonNull final Field<Float> field) {
            super(field);
        }

        @Override
        public void setView(@NonNull final View view) {
            super.setView(view);
            addTouchSignalsDirty(view);
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            target.putFloat(mField.getKey(), getValue());
        }

        @Override
        public boolean isEmpty() {
            return getValue().equals(0.0f);
        }

        @NonNull
        @Override
        public Float getValue() {
            //noinspection ConstantConditions
            RatingBar bar = (RatingBar) mView.get();
            return bar.getRating();
        }

        @Override
        public void setValue(@Nullable final Float value) {
            //noinspection ConstantConditions
            RatingBar bar = (RatingBar) mView.get();
            if (bar != null) {
                if (value != null) {
                    bar.setRating(value);
                } else {
                    bar.setRating(0.0f);
                }
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getFloat(mField.getKey()));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getFloat(mField.getKey()));
        }
    }

    /**
     * ImageView accessor. Uses the UUID to load the image into the view.
     * Sets a tag {@link R.id#TAG_UUID} on the view with the UUID.
     * <p>
     * Does not use a {@link FieldFormatter}.
     */
    private static class ImageViewAccessor
            extends BaseDataAccessor<String> {

        private int mMaxWidth;
        private int mMaxHeight;

        /**
         * Constructor.
         * Sets the scale to the default {@link ImageUtils#SCALE_MEDIUM}.
         * Override with {@link #setScale(int)}.
         */
        @SuppressWarnings("SameParameterValue")
        ImageViewAccessor(@NonNull final Field<String> field) {
            super(field);
            setScale(ImageUtils.SCALE_MEDIUM);
        }

        public void setScale(@ImageUtils.Scale final int scale) {
            int maxSize = ImageUtils.getMaxImageSize(scale);
            mMaxHeight = maxSize;
            mMaxWidth = maxSize;
        }

        @Override
        public void getValueAndPut(@NonNull final DataManager target) {
            // not applicable; do NOT put the uuid into the target!
        }

        @Override
        public boolean isEmpty() {
            // should really get the view, and check if it has a bitmap; but this will do for now.
            return getValue().isEmpty();
        }

        /**
         * Not really used, but returning the uuid makes sense (as opposed to returning the bitmap).
         *
         * @return the UUID
         */
        @NonNull
        @Override
        public String getValue() {
            //noinspection ConstantConditions
            return (String) mView.get().getTag(R.id.TAG_UUID);
        }

        /**
         * Populates the view and sets the UUID (incoming value) as a tag on the view.
         *
         * @param uuid the book UUID
         */
        @Override
        public void setValue(@Nullable final String uuid) {
            //noinspection ConstantConditions
            ImageView view = (ImageView) mView.get();
            if (view != null) {

                if (uuid != null) {
                    File imageFile;
                    if (uuid.isEmpty()) {
                        imageFile = StorageUtils.getTempCoverFile();
                    } else {
                        view.setTag(R.id.TAG_UUID, uuid);
                        imageFile = StorageUtils.getCoverFileForUuid(uuid);
                    }
                    ImageUtils.setImageView(view, imageFile, mMaxWidth, mMaxHeight, true);
                } else {
                    view.setImageResource(R.drawable.ic_image);
                }
            }
        }

        @Override
        public void setValue(@NonNull final Bundle source) {
            setValue(source.getString(mField.getKey(), ""));
        }

        @Override
        public void setValue(@NonNull final DataManager source) {
            setValue(source.getString(mField.getKey()));
        }
    }


    /**
     * FieldFormatter for 'date' fields.
     * <ul>
     * <li>Multiple fields: <strong>yes</strong></li>
     * <li>Extract: <strong>SQL date, UTC timezone</strong></li>
     * </ul>
     */
    public static class DateFieldFormatter
            implements FieldFormatter<String> {

        /**
         * Display as a human-friendly date, local timezone.
         */
        @Override
        @NonNull
        public String format(@NonNull final Field<String> field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            return DateUtils.toPrettyDate(source);
        }

        /**
         * Extract as an SQL date, UTC timezone.
         */
        @Override
        @NonNull
        public String extract(@NonNull final Field<String> field,
                              @NonNull final String source) {
            Date d = DateUtils.parseDate(source);
            if (d != null) {
                return DateUtils.utcSqlDate(d);
            }
            return source;
        }
    }

    /**
     * FieldFormatter for 'price' fields.
     * <ul>
     * <li>Multiple fields: <strong>no</strong></li>
     * <li>Extract: <strong>local variable</strong></li>
     * </ul>
     */
    public static class MonetaryFormatter
            implements FieldFormatter<Double> {

        private static final String TAG = "MonetaryFormatter";

        /** Optional; if null we use the default Locale. */
        @Nullable
        private Locale mLocale;

        /** Optional. */
        @Nullable
        private String mCurrencyCode;

        @NonNull
        private Double mRawValue = 0.0D;

        /**
         * Set the Locale for the formatter/parser.
         *
         * @param locale to use (if any)
         */
        public MonetaryFormatter setLocale(@Nullable final Locale locale) {
            mLocale = locale;
            return this;
        }

        /**
         * Set the currency code.
         *
         * @param currencyCode to use (if any)
         */
        public MonetaryFormatter setCurrencyCode(@Nullable final String currencyCode) {
            mCurrencyCode = currencyCode;
            return this;
        }

        @NonNull
        @Override
        public String format(@NonNull final Field<Double> field,
                             @Nullable final Double source) {
            if (source == null || source.equals(0.0d)) {
                mRawValue = 0.0d;
                return "";
            }
            mRawValue = source;

            if (mCurrencyCode == null || mCurrencyCode.isEmpty()) {
                return String.valueOf(source);
            }

            if (mLocale == null) {
                mLocale = Locale.getDefault();
            }

            try {
                // Always use the current Locale for formatting
                DecimalFormat nf = (DecimalFormat) DecimalFormat.getCurrencyInstance(mLocale);
                nf.setCurrency(Currency.getInstance(mCurrencyCode));
                // the result is rather dire... most currency symbols are shown as 3-char codes
                // e.g. 'EUR','US$',...
                return nf.format(source);

            } catch (@NonNull final IllegalArgumentException e) {
                Log.d(TAG, "currencyCode=" + mCurrencyCode + "|source=" + source, e);

                // fallback if getting a Currency instance fail.
                return mCurrencyCode + ' ' + String.format(mLocale, "%.2f", source);
            }
        }

        @NonNull
        @Override
        public Double extract(@NonNull final Field<Double> field,
                              @NonNull final String source) {
            return mRawValue;
        }

        // The ICU NumberFormatter is only available from ICU level 60, but Android lags behind:
        // https://developer.android.com/guide/topics/resources/internationalization
        // #versioning-nougat
        // So you need Android 9 (API level 28) and even then, the NumberFormatter
        // is not available in android.icu.* so you still would need to bundle the full ICU lib
        // For now, this is to much overkill.
//        @TargetApi(28)
//        private String icuFormat(@NonNull final Float money) {
//            https://github.com/unicode-org/icu/blob/master/icu4j/main/classes/core/src/
//            com/ibm/icu/number/NumberFormatter.java
//            and UnitWidth.NARROW
//            return "";
//        }
    }

    /**
     * FieldFormatter for 'page' fields. If the value is numerical, output "x pages"
     * Otherwise outputs the original source value.
     * <ul>
     * <li>Multiple fields: <strong>no</strong></li>
     * <li>Extract: <strong>local variable</strong></li>
     * </ul>
     */
    public static class PagesFormatter
            implements FieldFormatter<String> {

        @NonNull
        private final String mPagesString;
        @NonNull
        private String mRawValue = "";

        public PagesFormatter(@NonNull final Context context) {
            mPagesString = context.getString(R.string.lbl_x_pages);
        }

        @NonNull
        @Override
        public String format(@NonNull final Field<String> field,
                             @Nullable final String source) {

            if (source != null && !source.isEmpty() && !"0".equals(source)) {
                mRawValue = source;

                try {
                    int pages = Integer.parseInt(source);
                    return String.format(mPagesString, pages);
                } catch (@NonNull final NumberFormatException ignore) {
                    // don't log, both formats are valid.
                }
                // stored pages was alphanumeric.
                return source;
            }

            mRawValue = "";
            return "";
        }

        @NonNull
        @Override
        public String extract(@NonNull final Field<String> field,
                              @NonNull final String source) {
            return mRawValue;
        }
    }

    /**
     * FieldFormatter for language fields.
     * <ul>
     * <li>Multiple fields: <strong>yes</strong></li>
     * <li>Extract: <strong>ISO3 code</strong></li>
     * </ul>
     */
    public static class LanguageFormatter
            implements FieldFormatter<String> {

        @NonNull
        @Override
        public String format(@NonNull final Field<String> field,
                             @Nullable final String source) {
            if (source == null || source.isEmpty()) {
                return "";
            }

            return LanguageUtils.getDisplayName(source);
        }

        /**
         * Transform a localised language name to its ISO equivalent.
         *
         * @param source The value to be back-translated
         *
         * @return the ISO3 code for the language
         */
        @NonNull
        @Override
        public String extract(@NonNull final Field<String> field,
                              @NonNull final String source) {
            return LanguageUtils.getISO3FromDisplayName(App.getAppContext(), source);
        }
    }

    /**
     * FieldFormatter for a 'bitmask' field. Formats the checked items as a CSV String.
     * <ul>
     * <li>Multiple fields: <strong>no</strong></li>
     * <li>Extract: <strong>local variable</strong></li>
     * </ul>
     */
    public static class BitMaskFormatter
            implements FieldFormatter<Long> {

        /**
         * Editions.
         * Key: the edition bit.
         * Value: the label.
         */
        @NonNull
        private final Map<Integer, String> mMap;

        private Long mRawValue = 0L;

        /**
         * Constructor.
         *
         * @param editions a map with all supported editions
         */
        public BitMaskFormatter(@NonNull final Map<Integer, String> editions) {
            mMap = editions;
        }

        @NonNull
        @Override
        public String format(@NonNull final Field<Long> field,
                             @Nullable final Long source) {
            if (source == null || source == 0) {
                mRawValue = 0L;
                return "";
            }

            mRawValue = source;
            return TextUtils.join(", ", Csv.bitmaskToList(mMap, mRawValue));
        }

        @NonNull
        @Override
        public Long extract(@NonNull final Field<Long> field,
                            @NonNull final String source) {
            return mRawValue;
        }
    }

    /**
     * Field definition contains all information and methods necessary to manage display and
     * extraction of data in a view.
     *
     * <strong>Note:</strong> visibility/isUsed is only visual.
     * The logic around the view itself is still put in place and called upon.
     * i.e. invisible fields are still populated with data etc...
     *
     * @param <T> type of Field value.
     */
    public static class Field<T> {

        private static final String TAG = "Field";

        /** Field ID. */
        @IdRes
        private final int mId;
        /**
         * Key (can be blank) used to access a {@link DataManager} or {@code Bundle}.
         * <p>
         * - key is set, and doNoFetch==false:
         * ==> fetched from the {@link DataManager} (or Bundle), and populated on the screen
         * ==> extracted from the screen and put in {@link DataManager} (or Bundle)
         * <p>
         * - key is set, and doNoFetch==true:
         * ==> fetched from the {@link DataManager} (or Bundle), but populating
         * the screen must be done manually.
         * ==> extracted from the screen and put in {@link DataManager} (or Bundle)
         * <p>
         * - key is not set (""): field is defined, but data handling is fully manual.
         */
        @NonNull
        private final String mKey;
        /** Visibility group name. Used in conjunction with preferences to show/hide Views. */
        @NonNull
        private final String mGroup;
        /**
         * Accessor to use (automatically defined).
         * Encapsulates the formatter.
         */
        @NonNull
        private final FieldDataAccessor<T> mFieldDataAccessor;
        /** DEBUG - Owning collection. */
        @SuppressWarnings("FieldNotUsedInToString")
        @NonNull
        private final Fields mFields;
        @Nullable
        @IdRes
        private int[] mRelatedFields;
        /** Is the field in use; i.e. is it enabled in the user-preferences. **/
        private boolean mIsUsed;
        /**
         * Option indicating that even though field has a key, it should NOT be fetched
         * from a {@link DataManager} (or Bundle).
         * This is usually done for synthetic fields needed when putting the data
         * into the {@link DataManager} (or Bundle).
         */
        private boolean mDoNoFetch;


        /**
         * Constructor.
         *
         * @param fields          Parent collection
         * @param fieldId         Layout ID
         * @param view            for this field. Is only used to determine the type.
         * @param key             Key used to access a {@link DataManager} or {@code Bundle}.
         *                        Set to "" to suppress all access.
         * @param visibilityGroup Visibility group. Can be blank.
         */
        @VisibleForTesting
        public Field(@NonNull final Fields fields,
                     @IdRes final int fieldId,
                     @NonNull final View view,
                     @NonNull final String key,
                     @NonNull final String visibilityGroup) {

            mFields = fields;
            mId = fieldId;
            mKey = key;
            mGroup = visibilityGroup;

            // check if the user actually uses this group.
            mIsUsed = App.isUsed(mGroup);
            if (!mIsUsed) {
                view.setVisibility(View.GONE);
            }

            mFieldDataAccessor = createAccessor(view);
        }

        /**
         * Automatically determine a suitable FieldDataAccessor based on the View type.
         *
         * @param view to process
         *
         * @return FieldDataAccessor
         */
        @NonNull
        private FieldDataAccessor<T> createAccessor(@NonNull final View view) {

            FieldDataAccessor accessor;

            if (view instanceof CompoundButton) {
                //noinspection unchecked
                accessor = new CheckableAccessor((Field<Boolean>) this);

                // this was nasty... a MaterialButton implements Checkable,
                // but you have to double check (pardon the pun) whether it IS checkable.
                // now replaced by above CompoundButton; but leaving this comment for the future.
//            } else if ((view instanceof MaterialButton) && ((MaterialButton) view).isCheckable()){
//                //noinspection unchecked
//                accessor = new CheckableAccessor((Field<Boolean>) this);
//
//            } else if (!((view instanceof MaterialButton)) && (view instanceof Checkable)) {
//                // the opposite of above, do not accept the MaterialButton.
//                //noinspection unchecked
//                accessor = new CheckableAccessor((Field<Boolean>) this);

            } else if (view instanceof EditText) {
                accessor = new EditTextAccessor<>(this);

            } else if (view instanceof Button) {
                // a Button *is* a TextView
                accessor = new TextViewAccessor<>(this);

            } else if (view instanceof TextView) {
                accessor = new TextViewAccessor<>(this);

            } else if (view instanceof ImageView) {
                //noinspection unchecked
                accessor = new ImageViewAccessor((Field<String>) this);

            } else if (view instanceof RatingBar) {
                //noinspection unchecked
                accessor = new RatingBarAccessor((Field<Float>) this);

            } else if (view instanceof Spinner) {
                //noinspection unchecked
                accessor = new SpinnerAccessor((Field<String>) this);

            } else {
                // field has no layout, store in a dummy String.
                //noinspection unchecked
                accessor = new StringDataAccessor((Field<String>) this);
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "Using StringDataAccessor|key=" + mKey);
                }
            }

            //noinspection unchecked
            return (FieldDataAccessor<T>) accessor;
        }

        /**
         * The View is set in the constructor, which passes it on to
         * the {@link FieldDataAccessor} which keeps a WeakReference.
         * <p>
         * After a restart of the hosting fragment, we need to set the view again.
         */
        void setParentView(@NonNull final View parentView) {
            View view = parentView.findViewById(mId);
            mFieldDataAccessor.setView(view);
        }

        @NonNull
        Fields getFields() {
            return mFields;
        }

        /**
         * @param formatter to use
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field<T> setFormatter(@NonNull final FieldFormatter formatter) {
            mFieldDataAccessor.setFormatter(formatter);
            return this;
        }

        /**
         * Enable HTML (only applicable to TextView based fields).
         *
         * @return field (for chaining)
         */
        @SuppressWarnings("UnusedReturnValue")
        @NonNull
        public Field<T> setShowHtml(final boolean showHtml) {
            if (mFieldDataAccessor instanceof TextViewAccessor) {
                ((TextViewAccessor) mFieldDataAccessor).setShowHtml(showHtml);
            } else if (BuildConfig.DEBUG /* always */) {
                throw new IllegalStateException("Field is not a TextView");
            }
            return this;
        }

        /**
         * Enable decimal editing restriction(only applicable to EditText based fields).
         *
         * @return field (for chaining)
         */
        @SuppressWarnings("UnusedReturnValue")
        @NonNull
        public Field<T> setInputIsDecimal() {
            if (mFieldDataAccessor instanceof EditTextAccessor) {
                ((EditTextAccessor) mFieldDataAccessor).setDecimalInput();
            } else if (BuildConfig.DEBUG /* always */) {
                throw new IllegalStateException("Field is not an EditText");
            }
            return this;
        }

        /**
         * Set scaling (only applicable to ImageView based fields).
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field<T> setScale(@ImageUtils.Scale final int scale) {
            if (mFieldDataAccessor instanceof ImageViewAccessor) {
                ((ImageViewAccessor) mFieldDataAccessor).setScale(scale);
            } else if (BuildConfig.DEBUG /* always */) {
                throw new IllegalStateException("Field is not an ImageView");
            }
            return this;
        }

        /**
         * @param doNoFetch {@code true} to stop the field being fetched from the database
         *
         * @return field (for chaining)
         */
        @NonNull
        public Field<T> setDoNotFetch(final boolean doNoFetch) {
            mDoNoFetch = doNoFetch;
            return this;
        }

        /**
         * set the field ids which should follow visibility with this Field.
         *
         * @param relatedFields labels etc
         */
        public Field<T> setRelatedFields(@NonNull @IdRes final int... relatedFields) {
            mRelatedFields = relatedFields;
            return this;
        }

        /**
         * Is the field in use; i.e. is it enabled in the user-preferences.
         *
         * @return {@code true} if the field *can* be visible
         */
        public boolean isUsed() {
            return mIsUsed;
        }

        /**
         * Set the visibility for the field.
         *
         * @param parent      parent view for all fields.
         * @param hideIfEmpty hide the field if it's empty
         */
        private void resetVisibility(@NonNull final View parent,
                                     final boolean hideIfEmpty) {

            mIsUsed = App.isUsed(mGroup);
            int visibility = mIsUsed ? View.VISIBLE : View.GONE;
            View view = parent.findViewById(mId);

            if (mIsUsed && hideIfEmpty) {
                if (view instanceof Checkable) {
                    // hide any unchecked Checkable.
                    visibility = ((Checkable) view).isChecked() ? View.VISIBLE : View.GONE;

                } else if (!(view instanceof ImageView)) {
                    // skip ImageView, but all other fields can be tested on being empty
                    visibility = !isEmpty() ? View.VISIBLE : View.GONE;
                }
            }

            view.setVisibility(visibility);

            if (mRelatedFields != null) {
                for (int fieldId : mRelatedFields) {
                    view = parent.findViewById(fieldId);
                    if (view != null) {
                        view.setVisibility(visibility);
                    }
                }
            }
        }

        @IdRes
        public int getId() {
            return mId;
        }

        @NonNull
        public String getKey() {
            return mKey;
        }

        /**
         * Return the current value of this field.
         *
         * @return Current value in native form.
         */
        @NonNull
        public T getValue() {
            return mFieldDataAccessor.getValue();
        }

        /**
         * Set the value of this Field.
         *
         * Calls {@link Fields#afterFieldChange(Field, Object)}.
         *
         * @param source New value
         */
        public void setValue(@NonNull final T source) {
            mFieldDataAccessor.setValue(source);
            mFields.afterFieldChange(this, source);
        }

        /**
         * Convenience method to check if the value is considered empty.
         *
         * @return {@code true} if this field is empty.
         */
        public boolean isEmpty() {
            return mFieldDataAccessor.isEmpty();
        }

        /**
         * Get the current value of this field and put it into the DataManager.
         */
        void putValueInto(@NonNull final DataManager target) {
            mFieldDataAccessor.getValueAndPut(target);
        }

        /**
         * Set the value of this field from the passed Bundle.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        void setValueFrom(@NonNull final Bundle source) {
            if (!mKey.isEmpty() && !mDoNoFetch) {
                // do NOT call afterFieldChange, as this is the initial load
                mFieldDataAccessor.setValue(source);
            }
        }

        /**
         * Set the value of this field from the passed DataManager.
         * Useful for getting access to raw data values from a saved data bundle.
         */
        public void setValueFrom(@NonNull final DataManager source) {
            if (!mKey.isEmpty() && !mDoNoFetch) {
                // do NOT call afterFieldChange, as this is the initial load
                mFieldDataAccessor.setValue(source);
            }
        }

        /**
         * Wrapper to {@link FieldDataAccessor#format}.
         *
         * @param source String to format
         *
         * @return The formatted value. Or "" if the source is {@code null}.
         */
        @NonNull
        public String format(@Nullable final T source) {
            if (source == null) {
                return "";
            }
            return mFieldDataAccessor.format(source);
        }

        /**
         * Wrapper to {@link FieldDataAccessor#extract(String)}.
         *
         * @param source String to extract
         *
         * @return The extracted value
         */
        @NonNull
        T extract(@NonNull final String source) {
            return mFieldDataAccessor.extract(source);
        }

        @Override
        @NonNull
        public String toString() {
            return "Field{"
                   + "mId=" + mId
                   + ", mGroup='" + mGroup + '\''
                   + ", mKey='" + mKey + '\''
                   + ", mIsUsed=" + mIsUsed
                   + ", mDoNoFetch=" + mDoNoFetch
                   + ", mFieldDataAccessor=" + mFieldDataAccessor
                   + ", mRelatedFields=" + Arrays.toString(mRelatedFields)
                   + '}';
        }
    }
}
