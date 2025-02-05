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
package com.hardbacknutter.nevertoomanybooks.entities;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.database.dao.SeriesDao;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;

/**
 * Represents a Series.
 *
 * <p>
 * <strong>Note:</strong> the Series "number" is a column of {@link DBDefinitions#TBL_BOOK_SERIES}
 * So this class does not strictly represent a Series, but a "BookInSeries" without the book-id...
 * When the number is disregarded, it is a real Series representation.
 * (and I agree, this is NOT good practice).
 * <p>
 * The patterns defined are certainly not foolproof.
 * The extraction of numbers and the meaning of brackets works well enough for books,
 * but a particular pain is the titles/series for comics.
 */
public class Series
        implements ParcelableEntity, Mergeable {

    /** {@link Parcelable}. */
    public static final Creator<Series> CREATOR = new Creator<>() {
        @Override
        public Series createFromParcel(@NonNull final Parcel source) {
            return new Series(source);
        }

        @Override
        public Series[] newArray(final int size) {
            return new Series[size];
        }
    };

    /**
     * Parse "some text (some more text)" into "some text" and "some more text".
     * <p>
     * We want a "some text" that does not START with a bracket!
     */
    public static final Pattern TEXT1_BR_TEXT2_BR_PATTERN =
            Pattern.compile("([^(]+.*)\\s*\\((.*)\\).*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Variant of the above, with an additional 3rd part. */
    private static final Pattern TEXT1_BR_TEXT2_BR_TEXT3_PATTERN =
            Pattern.compile("([^(]+.*)\\s*\\((.*)\\)\\s*(.*)\\s*",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String NUMBER_REGEXP =
            // The possible prefixes to a number as seen in the wild.
            "(?:"
            + ",|"
            + "#|"
            + ",\\s*#|"
            + "number|num|num.|no|no.|nr|nr.|"
            + "book|bk|bk.|"
            + "volume|vol|vol.|"
            + "tome|"
            + "part|pt.|"
            + "deel|dl.|"
            // or no prefix
            + ")"
            // whitespace between prefix and actual number
            + "\\s*"
            // Capture the number group
            + "("

            // numeric numerals allowing for .-_ but must start with a digit.
            + /* */ "[0-9][0-9.\\-_]*"
            // no alphanumeric suffix

            + "|"

            // numeric numerals allowing for .-_ but must start with a digit.
            + /* */ "[0-9][0-9.\\-_]*"
            // optional alphanumeric suffix if separated by a '|'
            + /* */ "\\|\\S*?"

            + "|"

            // roman numerals prefixed by either '(' or whitespace
            + /* */ "\\s[(]?"
            // roman numerals
            + "(?=.)M*(C[MD]|D?C{0,3})(X[CL]|L?X{0,3})(I[XV]|V?I{0,3})"
//            + /* */ "[ivxlcm.]+"
            // must be suffixed by either ')' or EOL
            // no alphanumeric suffix
            + /* */ "[)]?$"

            + ")";

    /**
     * Parse a string into title + number. Used by {@link #from(String)}.
     * Formats supported: see unit test for this class.
     * <p>
     * FAIL: "Blake's 7" and similar Series titles will fail UNLESS there is an actual number:
     * i.e. "Blake's 7 1" should give "Blake's 7" and number 1
     * but "Blake's 7" will give "Blake's" and number 7
     */
    private static final Pattern TITLE_NUMBER_PATTERN = Pattern.compile(
            // whitespace at the start
            "^\\s*"
            // Capture the title group(1)
            + "(.*?)"
            // delimiter ',' and/or whitespace
            + "\\s*[,]*\\s*"
            // Capture the number group(2)
            + /* */ NUMBER_REGEXP
            // whitespace to the end
            + "\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Remove extraneous text from Series number. Used by {@link #from}.
     */
    private static final Pattern NUMBER_CLEANUP_PATTERN =
            Pattern.compile("^\\s*" + NUMBER_REGEXP + "\\s*$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Remove any leading zeros from Series number. */
    private static final Pattern PURE_NUMERICAL_PATTERN = Pattern.compile("^[0-9]+$");
    /** Row ID. */
    private long mId;
    /** Series title. */
    @NonNull
    private String mTitle;
    /** whether we have all we want from this Series / if the Series is finished. */
    private boolean mIsComplete;
    /** number (alphanumeric) of a book in this Series. */
    @NonNull
    private String mNumber;

    /**
     * Constructor.
     *
     * @param title of the Series
     */
    public Series(@NonNull final String title) {
        mTitle = title;
        mNumber = "";
    }

    /**
     * Constructor.
     *
     * @param title      of the Series
     * @param isComplete whether a Series is completed, i.e if the user has all
     *                   they want from this Series.
     */
    public Series(@NonNull final String title,
                  final boolean isComplete) {
        mTitle = title;
        mIsComplete = isComplete;
        mNumber = "";
    }

    /**
     * Full constructor with optional book number.
     *
     * @param id      ID of the Series in the database.
     * @param rowData with data
     */
    public Series(final long id,
                  @NonNull final DataHolder rowData) {
        mId = id;
        mTitle = rowData.getString(DBKey.SERIES_TITLE);
        mIsComplete = rowData.getBoolean(DBKey.SERIES_IS_COMPLETE);
        // optional domain, not always used.
        if (rowData.contains(DBKey.SERIES_BOOK_NUMBER)) {
            mNumber = rowData.getString(DBKey.SERIES_BOOK_NUMBER);
        } else {
            mNumber = "";
        }
    }

    /**
     * {@link Parcelable} Constructor.
     *
     * @param in Parcel to construct the object from
     */
    private Series(@NonNull final Parcel in) {
        mId = in.readLong();
        //noinspection ConstantConditions
        mTitle = in.readString();
        mIsComplete = in.readByte() != 0;
        //noinspection ConstantConditions
        mNumber = in.readString();
    }

    /**
     * Constructor that will attempt to parse a single string into a Series title and number.
     *
     * @param text string to decode
     *
     * @return the Series
     */
    @NonNull
    public static Series from(@NonNull final String text) {
        // First check if we can simplify the decoding.
        // This makes the pattern easier to maintain.
        Matcher matcher = TEXT1_BR_TEXT2_BR_PATTERN.matcher(text);
        if (matcher.find()) {
            final String g1 = matcher.group(1);
            if (g1 != null) {
                return from(g1, matcher.group(2));
            }
        }

        // HORRENDOUS, HORRIBLE HACK...
        if ("Blake's 7".equalsIgnoreCase(text)) {
            return new Series(text);
        }

        // We now know that brackets do NOT separate the number part
        matcher = TITLE_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {

            final String uTitle = ParseUtils.unEscape(matcher.group(1));
            String uNumber = ParseUtils.unEscape(matcher.group(2));

            final Series newSeries = new Series(uTitle);
            // If it's purely numeric, remove any leading zeros.
            if (PURE_NUMERICAL_PATTERN.matcher(uNumber).find()) {
                uNumber = String.valueOf(Long.parseLong(uNumber));
            }
            newSeries.setNumber(uNumber);
            return newSeries;

        } else {
            // no number part found
            final String uTitle = ParseUtils.unEscape(text.trim());
            return new Series(uTitle);
        }
    }

    /**
     * Variant of {@link Series#from(String)} allowing 3 parts.
     * <p>
     * "Some Title (I) 12"  ==> "Some Title", "1.12"
     * "Some Title (II) 13"  ==> "Some Title", "2.13"
     * "Some Title (III) 14"  ==> "Some Title", "3.14"
     * "Some Title (Special) 15"  ==> "Some Title (Special)", "15"
     *
     * <strong>Note:</strong> we could make this method the default {@link Series#from}
     * but that would add overhead for most sites.
     *
     * @param text string to decode
     *
     * @return the Series
     */
    @NonNull
    public static Series from3(@NonNull final String text) {
        final Series series;

        // Detect "title (middle) number" and "title (number)"
        final Matcher matcher = TEXT1_BR_TEXT2_BR_TEXT3_PATTERN.matcher(text);
        if (matcher.find()) {
            final String prefix = matcher.group(1);
            final String middle = matcher.group(2);
            final String suffix = matcher.group(3);

            if (prefix != null) {
                if (suffix != null && !suffix.isEmpty()) {
                    // the suffix group is the number.
                    series = from(prefix, suffix);

                    // Cover a special case were the middle group is potentially
                    // a roman numeral which should be prefixed to the number.
                    if ("I".equals(middle)) {
                        series.setNumber("1." + series.getNumber());
                    } else if ("II".equals(middle)) {
                        series.setNumber("2." + series.getNumber());
                    } else if ("III".equals(middle)) {
                        series.setNumber("3." + series.getNumber());
                    } else {
                        // But if it wasn't... add it back to the title including
                        // the brackets we stripped off initially.
                        series.setTitle(prefix + '(' + middle + ')');
                    }
                    return series;

                } else {
                    // the middle group is the number.
                    return from(prefix, middle);
                }
            }
        }

        // didn't match the specific pattern, handle as normal.
        return from(text);
    }

    /**
     * Constructor that will attempt to parse a number.
     *
     * @param title  for the Series; used as is.
     * @param number (optional) number for the Series; will get cleaned up.
     *
     * @return the Series
     */
    @NonNull
    public static Series from(@NonNull final String title,
                              @Nullable final String number) {
        final String uTitle = ParseUtils.unEscape(title);
        final String uNumber = ParseUtils.unEscape(number);

        final Series newSeries = new Series(uTitle);
        if (!uNumber.isEmpty()) {
            final Matcher matcher = NUMBER_CLEANUP_PATTERN.matcher(uNumber);
            if (matcher.find()) {
                String cleanNumber = matcher.group(1);
                if (cleanNumber != null && !cleanNumber.isEmpty()) {
                    // If it's purely numeric, remove any leading zeros.
                    if (PURE_NUMERICAL_PATTERN.matcher(cleanNumber).find()) {
                        try {
                            cleanNumber = String.valueOf(Long.parseLong(cleanNumber));
                        } catch (@NonNull final NumberFormatException ignore) {
                            // ignore
                        }
                    }
                    newSeries.setNumber(cleanNumber);
                } else {
                    newSeries.setNumber(uNumber);
                }
            } else {
                newSeries.setNumber(uNumber);
            }
        }

        return newSeries;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest,
                              final int flags) {
        dest.writeLong(mId);
        dest.writeString(mTitle);
        dest.writeByte((byte) (mIsComplete ? 1 : 0));
        dest.writeString(mNumber);
    }

    @SuppressWarnings("SameReturnValue")
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Get the 'complete' status of the Series.
     *
     * @return {@code true} if the Series is complete
     */
    public boolean isComplete() {
        return mIsComplete;
    }

    /**
     * Set the 'complete' status of the Series.
     *
     * @param isComplete Flag indicating the user considers this Series to be 'complete'
     */
    public void setComplete(final boolean isComplete) {
        mIsComplete = isComplete;
    }

    @Override
    public long getId() {
        return mId;
    }

    @Override
    public void setId(final long id) {
        mId = id;
    }

    /**
     * Get the user visible title.
     *
     * @param context Current context
     *
     * @return "title" or "title (nr)"
     */
    @Override
    @NonNull
    public String getLabel(@NonNull final Context context) {
        // Using the locale here is overkill;  see #getLocale(..)
        final String title = getLabel(context, mTitle, () -> null);

        if (mNumber.isEmpty()) {
            return title;
        } else {
            return title + " (" + mNumber + ')';
        }
    }

    /**
     * Get the <strong>unformatted</strong> title.
     *
     * @return the title
     */
    @NonNull
    public String getTitle() {
        return mTitle;
    }

    /**
     * Set the unformatted title; as entered manually by the user.
     *
     * @param title to use
     */
    public void setTitle(@NonNull final String title) {
        mTitle = title;
    }

    /**
     * Get the unformatted number.
     *
     * @return number (as a string)
     */
    @NonNull
    public String getNumber() {
        return mNumber;
    }

    /**
     * Set the unformatted number; as entered manually by the user.
     *
     * @param number to use, a {@code null} is replaced by "".
     */
    public void setNumber(@Nullable final String number) {
        mNumber = Objects.requireNonNullElse(number, "");
    }

    /**
     * Replace local details from another Series.
     *
     * @param source            Series to copy from
     * @param includeBookFields Flag to force copying the Book related fields as well
     */
    public void copyFrom(@NonNull final Series source,
                         final boolean includeBookFields) {
        mTitle = source.mTitle;
        mIsComplete = source.mIsComplete;
        if (includeBookFields) {
            mNumber = source.mNumber;
        }
    }

    /**
     * Get the Locale for a Series.
     * This is defined as the Locale for the language from the first book in the Series.
     *
     * @param context    Current context
     * @param bookLocale Locale to use if the Series does not have a Locale of its own.
     *
     * @return the Locale of the Series
     */
    @Override
    @NonNull
    public Locale getLocale(@NonNull final Context context,
                            @NonNull final Locale bookLocale) {
        //TODO: need a reliable way to cache the Locale here. i.e. store the language of a series.
        // See also {@link #pruneList}
        // were we use batch mode. Also: a french book belonging to a dutch series...
        // the series title OB is wrong. For now this is partially mitigated by making
        // entering the book language mandatory.
        final String lang = ServiceLocator.getInstance().getSeriesDao().getLanguage(mId);
        if (!lang.isEmpty()) {
            final Locale seriesLocale = ServiceLocator.getInstance().getAppLocale()
                                                      .getLocale(context, lang);
            if (seriesLocale != null) {
                return seriesLocale;
            }
        }
        return bookLocale;
    }

    @Override
    public boolean merge(@NonNull final Mergeable mergeable) {
        final Series incoming = (Series) mergeable;

        // If the incoming Series has no number set, we're done
        if (incoming.getNumber().isEmpty()) {
            if (mId == 0 && incoming.getId() > 0) {
                mId = incoming.getId();
            }
            return true;
        }

        // If this Series has no number set, copy the incoming data
        if (mNumber.isEmpty()) {
            mNumber = incoming.getNumber();
            if (mId == 0 && incoming.getId() > 0) {
                mId = incoming.getId();
            }
            return true;
        }

        // Both have a number set.
        // If they are the same, we're done
        if (mNumber.equals(incoming.getNumber())) {
            if (mId == 0 && incoming.getId() > 0) {
                mId = incoming.getId();
            }
            return true;
        }

        // The book has two numbers in a series.
        // This might be strange, but absolutely valid.
        // The user can clean up manually if needed.
        // While we cannot merge the actual objects,
        // we CAN copy the id if appropriate, as Series are distinguished by id AND number
        if (mId == 0 && incoming.getId() > 0) {
            mId = incoming.getId();
        }
        return false;
    }

    @Override
    public int asciiHashCodeNoId() {
        return Objects.hash(ParseUtils.toAscii(mTitle));
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mTitle);
    }

    /**
     * Equality: <strong>id, title</strong>.
     * <ul>
     *   <li>'number' is on a per book basis. See {@link SeriesDao#pruneList}.</li>
     *   <li>'isComplete' is a user setting and is ignored.</li>
     * </ul>
     *
     * <strong>Comparing is DIACRITIC and CASE SENSITIVE</strong>:
     * This allows correcting case mistakes even with identical ID.
     */
    @Override
    public boolean equals(@Nullable final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final Series that = (Series) obj;
        // if both 'exist' but have different ID's -> different.
        if (mId != 0 && that.mId != 0 && mId != that.mId) {
            return false;
        }
        return Objects.equals(mTitle, that.mTitle);
    }

    @Override
    @NonNull
    public String toString() {
        return "Series{"
               + "mId=" + mId
               + ", mTitle=`" + mTitle + '`'
               + ", mIsComplete=" + mIsComplete
               + ", mNumber=`" + mNumber + '`'
               + '}';
    }
}
