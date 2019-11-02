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
package com.hardbacknutter.nevertoomanybooks.widgets;

import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.method.DigitsKeyListener;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatEditText;

import java.util.regex.Pattern;

/**
 * Provides a TextEdit field specific to editing an ISBN or ASIN number.
 * <p>
 * This hides & facilitates the internal needs of setting inputType, keyListener
 * and handling the virtual keyboard.
 * <p>
 * <strong>Developer Note:</strong> the crucial part in this class is {@link #getInputType()}.
 * <p>
 * Usage:
 * <p>
 * In the layout file, use {@code "<com.hardbacknutter.nevertoomanybooks.widgets.EditIsbn ... />"}
 * instead of {@code "<EditText ... />"}
 * <p>
 * In code, call {@link #setAllowAsin(boolean)} as needed.
 * At initialisation time, this widget is set to ISBN only.
 */
public class EditIsbn
        extends AppCompatEditText {

    /** all digits in ISBN strings. */
    private static final String ISBN_DIGITS = "0123456789xX";

    /** all digits allowed in ASIN strings. */
    private static final String ASIN_DIGITS =
            "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /** filter to remove all ASIN digits from ISBN strings (leaving x!). */
    private static final Pattern ISBN_INVALID_CHARS_PATTERN =
            Pattern.compile("[abcdefghijklmnopqrstuvwyz]",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private boolean mAllowAsin;

    public EditIsbn(@NonNull final Context context) {
        super(context);
    }

    public EditIsbn(@NonNull final Context context,
                    final AttributeSet attrs) {
        super(context, attrs);
    }

    public EditIsbn(@NonNull final Context context,
                    final AttributeSet attrs,
                    final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setAllowAsin(mAllowAsin);
    }

    public void setAllowAsin(final boolean isChecked) {

        mAllowAsin = isChecked;
        // stop the virtual keyboard from showing when using ISBN only
        setShowSoftInputOnFocus(mAllowAsin);

        // Always call setInputType *before* calling setKeyListener!
        if (isChecked) {
            setInputType(InputType.TYPE_CLASS_TEXT
                         | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                         | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

            setKeyListener(DigitsKeyListener.getInstance(ASIN_DIGITS));

        } else {
            setInputType(InputType.TYPE_NULL);
            setKeyListener(DigitsKeyListener.getInstance(ISBN_DIGITS));

            // hide the virtual keyboard.
            InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
            if (imm != null && imm.isActive(this)) {
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
            }

            // remove invalid digits
            Editable editable = getText();
            if (editable != null) {
                String txt = editable.toString().trim();
                if (!txt.isEmpty()) {
                    setText(ISBN_INVALID_CHARS_PATTERN.matcher(txt).replaceAll(""));
                    // force cursor to the end of field.
                    editable = getText();
                    Selection.setSelection(editable, editable.length());
                }
            }
        }
    }

    /**
     * <strong>THIS IS CRUCIAL</strong>
     * <p>
     * {@link #setInputType} and {@link #setKeyListener} seem to initiate a race condition
     * that one overrides the other no matter in which order they are called.
     *
     * @return what we really want this method to return.
     */
    @Override
    public int getInputType() {
        if (mAllowAsin) {
            return EditorInfo.TYPE_CLASS_TEXT
                   | EditorInfo.TYPE_TEXT_FLAG_CAP_CHARACTERS
                   | EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        } else {
            return EditorInfo.TYPE_NULL;
        }
    }
}
