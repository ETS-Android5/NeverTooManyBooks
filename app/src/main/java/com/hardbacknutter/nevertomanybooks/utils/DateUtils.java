/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.utils;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

import com.hardbacknutter.nevertomanybooks.App;
import com.hardbacknutter.nevertomanybooks.BuildConfig;
import com.hardbacknutter.nevertomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertomanybooks.debug.Logger;

public final class DateUtils {

    /** Month full names cache for each Locale. */
    private static final Map<String, String[]> MONTH_LONG_NAMES = new HashMap<>();
    /** Month abbreviated names cache for each Locale. */
    private static final Map<String, String[]> MONTH_SHORT_NAMES = new HashMap<>();

    /**
     * SQL Date formatter, System Locale.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat LOCAL_SQL_DATE;

    /**
     * SQL Datetime formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_TIME_HH_MM_SS;
    /**
     * SQL Datetime formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_TIME_HH_MM;
    /**
     * SQL Date formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_YYYY_MM_DD;
    /**
     * SQL Date formatter, UTC.
     * Used for *non-user* dates (date published etc).
     */
    private static final SimpleDateFormat UTC_SQL_DATE_YYYY_MM;

    /** Simple match for a 4 digit year. */
    private static final SimpleDateFormat YEAR =
            new SimpleDateFormat("yyyy", App.getSystemLocale());

    /**
     * List of formats we'll use to parse dates.
     * 2019-08-03: there are 22 formats, setting capacity to 25.
     */
    private static final ArrayList<SimpleDateFormat> PARSE_DATE_FORMATS = new ArrayList<>(25);

    static {
        // Used for formatting *user* dates, in the locale timezone, for SQL. e.g. date read...
        LOCAL_SQL_DATE = new SimpleDateFormat("yyyy-MM-dd", App.getSystemLocale());

        // Used for formatting *non-user* dates for SQL. e.g. publication dates...
        TimeZone TZ_UTC = TimeZone.getTimeZone("UTC");
        UTC_SQL_DATE_TIME_HH_MM_SS = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                                                          App.getSystemLocale());
        UTC_SQL_DATE_TIME_HH_MM_SS.setTimeZone(TZ_UTC);

        UTC_SQL_DATE_TIME_HH_MM = new SimpleDateFormat("yyyy-MM-dd HH:mm",
                                                       App.getSystemLocale());
        UTC_SQL_DATE_TIME_HH_MM.setTimeZone(TZ_UTC);

        UTC_SQL_DATE_YYYY_MM_DD = new SimpleDateFormat("yyyy-MM-dd", App.getSystemLocale());
        UTC_SQL_DATE_YYYY_MM_DD.setTimeZone(TZ_UTC);

        UTC_SQL_DATE_YYYY_MM = new SimpleDateFormat("yyyy-MM", App.getSystemLocale());
        UTC_SQL_DATE_YYYY_MM.setTimeZone(TZ_UTC);

    }

    private DateUtils() {
    }

    /**
     * create the parser list. These will be tried IN THE ORDER DEFINED HERE.
     * <p>
     * FIXME: these are created at first use, so do not support switching Locale on the fly.
     * the month is (localized) text, or english
     */
    private static void createParseDateFormats() {
        // check the device language
        final boolean userIsEnglishSpeaking =
                Objects.equals(Locale.ENGLISH.getISO3Language(),
                               App.getSystemLocale().getISO3Language());

        // allow re-creating.
        PARSE_DATE_FORMATS.clear();

        //TODO: the order of adding the set of 3 "MM-dd" and "dd-MM" should be
        // made dependent on the users locale. For now we favour US style first.
        // numerical formats
        addParseDateFormat("MM-dd-yyyy HH:mm:ss", false);
        addParseDateFormat("MM-dd-yyyy HH:mm", false);
        addParseDateFormat("MM-dd-yyyy", false);

        addParseDateFormat("dd-MM-yyyy HH:mm:ss", false);
        addParseDateFormat("dd-MM-yyyy HH:mm", false);
        addParseDateFormat("dd-MM-yyyy", false);

        // SQL date formats, numerical
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_TIME_HH_MM_SS);
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_TIME_HH_MM);
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_YYYY_MM_DD);
        PARSE_DATE_FORMATS.add(UTC_SQL_DATE_YYYY_MM);

        // add english if the user's System Locale is not English.
        // This is done because most (all?) internet sites we search are english.
        addParseDateFormat("dd-MMM-yyyy HH:mm:ss", !userIsEnglishSpeaking);
        addParseDateFormat("dd-MMM-yyyy HH:mm", !userIsEnglishSpeaking);
        addParseDateFormat("dd-MMM-yyyy", !userIsEnglishSpeaking);

        addParseDateFormat("dd-MMM-yy HH:mm:ss", !userIsEnglishSpeaking);
        addParseDateFormat("dd-MMM-yy HH:mm", !userIsEnglishSpeaking);
        addParseDateFormat("dd-MMM-yy", !userIsEnglishSpeaking);

        // "13 March 2009" added due to OpenLibrary
        addParseDateFormat("dd MMM yyyy", !userIsEnglishSpeaking);
        // "January 12, 1987" added due to OpenLibrary
        addParseDateFormat("MMM d, yyyy", !userIsEnglishSpeaking);

        // Dates of the form: 'Fri May 5 17:23:11 -0800 2012'
        addParseDateFormat("EEE MMM dd HH:mm:ss ZZZZ yyyy", !userIsEnglishSpeaking);
        addParseDateFormat("EEE MMM dd HH:mm ZZZZ yyyy", !userIsEnglishSpeaking);
        addParseDateFormat("EEE MMM dd ZZZZ yyyy", !userIsEnglishSpeaking);

        // TEST: PARTIAL format... "March 2009" added due to OpenLibrary
        addParseDateFormat("MMM yyyy", !userIsEnglishSpeaking);
    }

    /**
     * Add a format to the parser list. It's always added in the System Locale format,
     * and optionally in English.
     *
     * @param format     date format to add
     * @param addEnglish if set, also add the localized english version
     */
    private static void addParseDateFormat(@NonNull final String format,
                                           final boolean addEnglish) {
        PARSE_DATE_FORMATS.add(new SimpleDateFormat(format, App.getSystemLocale()));
        if (addEnglish) {
            PARSE_DATE_FORMATS.add(new SimpleDateFormat(format, Locale.ENGLISH));
        }
    }

    /**
     * Attempt to parse a date string based on a range of possible formats.
     *
     * @param dateString String to parse
     *
     * @return Resulting date if parsed, otherwise {@code null}
     */
    @Nullable
    public static Date parseDate(@Nullable final String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        // shortcut for plain 4 digit years.
        if (dateString.length() == 4) {
            try {
                return YEAR.parse(dateString);
            } catch (@NonNull final ParseException ignore) {
            }
        }

        // First try to parse using strict rules
        Date d = parseDate(dateString, false);
        if (d != null) {
            return d;
        }
        // OK, be lenient
        return parseDate(dateString, true);
    }

    /**
     * Attempt to parse a date string based on a range of possible formats; allow
     * for caller to specify if the parsing should be strict or lenient.
     *
     * @param dateString String to parse
     * @param lenient    {@code true} if parsing should be lenient
     *
     * @return Resulting date if successfully parsed, otherwise {@code null}
     */
    @Nullable
    private static Date parseDate(@NonNull final String dateString,
                                  final boolean lenient) {
        // create on first use.
        if (PARSE_DATE_FORMATS.isEmpty()) {
            createParseDateFormats();
        }

        // try all formats until one fits.
        for (SimpleDateFormat sdf : PARSE_DATE_FORMATS) {
            try {
                sdf.setLenient(lenient);
                return sdf.parse(dateString);
            } catch (@NonNull final ParseException ignore) {
            }
        }

        // try Default Locale.
        try {
            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT);
            df.setLenient(lenient);
            return df.parse(dateString);
        } catch (@NonNull final ParseException ignore) {
        }

        // try System Locale.
        try {
            DateFormat df = DateFormat.getDateInstance(DateFormat.SHORT,
                                                       App.getSystemLocale());
            df.setLenient(lenient);
            return df.parse(dateString);
        } catch (@NonNull final ParseException ignore) {
        }
        return null;
    }

    /**
     * Pretty format a (potentially partial) SQL date;  Locale based.
     *
     * @param locale     to use
     * @param dateString SQL formatted date.
     *
     * @return human readable date string
     *
     * @throws NumberFormatException on failure to parse
     */
    public static String toPrettyDate(@NonNull final Locale locale,
                                      @NonNull final String dateString)
            throws NumberFormatException {
        switch (dateString.length()) {
            case 10:
                // YYYY-MM-DD
                Date date = parseDate(dateString);
                if (date != null) {
                    return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date);
                }
                // failed to parse
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.DATETIME) {
                    Logger.warnWithStackTrace(DateUtils.class, "failed: " + dateString);
                }
                return dateString;

            case 7:
                // input: YYYY-MM
                int month = Integer.parseInt(dateString.substring(5));
                // just swap: MMM YYYY
                return getMonthName(locale, month, true) + ' ' + dateString.substring(0, 4);

            case 4:
                // input: YYYY
                return dateString;

            default:
                // failed to parse
                if (BuildConfig.DEBUG && DEBUG_SWITCHES.DATETIME) {
                    Logger.warnWithStackTrace(DateUtils.class, "failed: " + dateString);
                }
                return dateString;
        }
    }

    /**
     * Pretty format a datetime; Locale based.
     *
     * @param locale to use
     * @param date   to format
     */
    @NonNull
    public static String toPrettyDateTime(@NonNull final Locale locale,
                                          @NonNull final Date date) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM,
                                              locale).format(date);
    }

    /**
     * Get today's date.
     * Should be used for dates directly related to the user (date acquired, date read, etc...)
     *
     * @return SQL datetime-string, for the System Locale.
     */
    @NonNull
    public static String localSqlDateForToday() {
        Calendar calendar = Calendar.getInstance(App.getSystemLocale());
        return LOCAL_SQL_DATE.format(calendar.getTime());
    }

    /**
     * Convert a Date to a UTC based SQL datetime-string.
     *
     * @return SQL datetime-string, for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDateTime(@NonNull final Date date) {
        return UTC_SQL_DATE_TIME_HH_MM_SS.format(date);
    }

    /**
     * @return today's SQL datetime-string, for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDateTimeForToday() {
        return UTC_SQL_DATE_TIME_HH_MM_SS.format(
                Calendar.getInstance(App.getSystemLocale()).getTime());
    }

    /**
     * Convert a Date to a UTC based SQL date-string.
     *
     * @return SQL date-string, for the UTC timezone.
     */
    @NonNull
    public static String utcSqlDate(@NonNull final Date date) {
        return UTC_SQL_DATE_YYYY_MM_DD.format(date);
    }

    /**
     * @param locale    to use
     * @param month     1-12 based month number
     * @param shortName {@code true} to get the abbreviated name instead of the full name.
     *
     * @return localised name of Month
     */
    @NonNull
    public static String getMonthName(@NonNull final Locale locale,
                                      @IntRange(from = 1, to = 12) final int month,
                                      final boolean shortName) {

        String iso = locale.getISO3Language();
        String[] longNames = MONTH_LONG_NAMES.get(iso);
        String[] shortNames = MONTH_SHORT_NAMES.get(iso);

        if (longNames == null) {
            // Build the cache for this locale.
            Calendar calendar = Calendar.getInstance(locale);
            SimpleDateFormat longNameFormatter = new SimpleDateFormat("MMMM", locale);
            SimpleDateFormat shortNameFormatter = new SimpleDateFormat("MMM", locale);

            longNames = new String[12];
            shortNames = new String[12];
            for (int m = 0; m < 12; m++) {
                // prevent wrapping
                calendar.set(Calendar.DATE, 1);
                calendar.set(Calendar.MONTH, m);
                longNames[m] = longNameFormatter.format(calendar.getTime());
                shortNames[m] = shortNameFormatter.format(calendar.getTime());
            }
            MONTH_LONG_NAMES.put(iso, longNames);
            MONTH_SHORT_NAMES.put(iso, shortNames);
        }

        if (shortName) {
            //noinspection ConstantConditions
            return shortNames[month - 1];
        } else {
            return longNames[month - 1];
        }
    }

    /**
     * Passed date components build a (partial) SQL format date string.
     * Locale independent.
     *
     * @return Formatted date, e.g. '2011-11-01' or '2011-11'
     */
    @NonNull
    public static String buildPartialDate(@Nullable final Integer year,
                                          @Nullable final Integer month,
                                          @Nullable final Integer day) {
        if (year == null || year == 0) {
            return "";
        } else {
            String value = String.format("%04d", year);
            if (month != null && month > 0) {
                String mm = month.toString();
                if (mm.length() == 1) {
                    mm = '0' + mm;
                }

                value += '-' + mm;

                if (day != null && day > 0) {
                    String dd = day.toString();
                    if (dd.length() == 1) {
                        dd = '0' + dd;
                    }
                    value += '-' + dd;
                }
            }
            return value;
        }
    }
}
