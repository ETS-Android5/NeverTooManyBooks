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
package com.hardbacknutter.nevertoomanybooks.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import com.hardbacknutter.nevertoomanybooks.ServiceLocator;
import com.hardbacknutter.nevertoomanybooks.tasks.BuildLanguageMappingsTask;

/**
 * Languages.
 * <ul>
 *      <li>ISO 639-1: two-letter codes, one per language</li>
 *      <li>ISO 639-2: three-letter codes, for the same languages as 639-1</li>
 * </ul>
 * The JDK uses "ISO3" for the 3-character ISO 639-2 format (not to be confused with ISO 639-3)
 */
public class Languages {

    /**
     * The SharedPreferences name where we'll maintain our language to ISO mappings.
     * Uses the {@link Locale#getDisplayName()} for the key,
     * and {@link Locale#getISO3Language} for the value.
     */
    @VisibleForTesting
    public static final String LANGUAGE_MAP = "language2iso3";
    /** Prefix added to the iso code for the 'done' flag in the language cache. */
    private static final String LANG_CREATED_PREFIX = "___";

    @NonNull
    private final Map<String, String> mLang3ToLang2Map;

    /**
     * Constructor.
     */
    public Languages() {
        final String[] languages = Locale.getISOLanguages();
        mLang3ToLang2Map = new HashMap<>(languages.length);
        for (final String language : languages) {
            final Locale locale = new Locale(language);
            mLang3ToLang2Map.put(locale.getISO3Language(), language);
        }
    }

    /**
     * Try to convert a Language to a Locale.
     *
     * @param context  Current context
     * @param language ISO codes (2 or 3 char), or a display-string (4+ characters)
     *
     * @return the best matching Locale we could determine
     *
     * @see AppLocale#getLocale(Context, String)
     */
    @NonNull
    public static Locale toLocale(@NonNull final Context context,
                                  @Nullable final String language) {
        Locale locale;
        if (language == null || language.isEmpty()) {
            locale = context.getResources().getConfiguration().getLocales().get(0);
        } else {
            locale = ServiceLocator.getInstance().getAppLocale().getLocale(context, language);
            if (locale == null) {
                locale = context.getResources().getConfiguration().getLocales().get(0);
            }
        }
        return locale;
    }

    /**
     * Try to convert a Language ISO code to the display name.
     *
     * @param context Current context
     * @param iso3    the ISO code
     *
     * @return the display name for the language,
     *         or the input string itself if it was an invalid ISO code
     */
    @NonNull
    public String getDisplayNameFromISO3(@NonNull final Context context,
                                         @NonNull final String iso3) {
        final Locale langLocale = ServiceLocator.getInstance().getAppLocale()
                                                .getLocale(context, iso3);
        if (langLocale == null) {
            return iso3;
        }
        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);
        return langLocale.getDisplayLanguage(userLocale);
    }

    /**
     * Try to convert a Language DisplayName to an ISO3 code.
     * At installation time we generated the users System Locale + Locale.ENGLISH
     * Each time the user switches language, we generate an additional set.
     * That probably covers a lot if not all.
     *
     * @param locale      the locale of the displayName
     * @param displayName the string as normally produced by {@link Locale#getDisplayLanguage}
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public String getISO3FromDisplayName(@NonNull final Locale locale,
                                         @NonNull final String displayName) {

        final String source = displayName.trim().toLowerCase(locale);
        if (source.isEmpty()) {
            return "";
        }
        //noinspection ConstantConditions
        return getCacheFile().getString(source, source);
    }

    /**
     * Try to convert a "language-country" code to an ISO3 code.
     *
     * @param code a standard ISO string like "en" or "en-GB" or "en-GB*"
     *
     * @return the ISO code, or if conversion failed, the input string
     */
    @NonNull
    public String getISO3FromCode(@NonNull final String code) {
        // shortcut for English "en", "en-GB", etc
        if ("en".equals(code) || code.startsWith("en_") || code.startsWith("en-")) {
            return "eng";
        } else {
            try {
                return ServiceLocator.getInstance().getAppLocale().create(code).getISO3Language();
            } catch (@NonNull final MissingResourceException ignore) {
                return code;
            }
        }
    }

    /**
     * Map an ISO 639-2 (3-char) language code to a language code suited
     * to be use with {@code new Locale(x)}.
     * <pre>
     * Rant:
     * Java 8 as bundled with Android Studio 3.5 on Windows:
     * new Locale("fr") ==> valid French Locale
     * new Locale("fre") ==> valid French Locale
     * new Locale("fra") ==> INVALID French Locale
     *
     * Android 8.0 in emulator bundled with Android Studio 3.5 on Windows:
     * new Locale("fr") ==> valid French Locale
     * new Locale("fre") ==> INVALID French Locale
     * new Locale("fra") ==>  valid French Locale
     * </pre>
     * A possible explanation is that Android use ICU classes internally.<br>
     * Also see {@link #toBibliographic} and {@link #toTerminology}.
     * <br><br>
     * <strong>Note:</strong> check the javadoc on {@link Locale#getISOLanguages()} for caveats.
     *
     * @param context Current context
     * @param iso3    ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     *
     * @return a language code that can be used with {@code new Locale(x)},
     *         or the incoming string if conversion failed.
     */
    @NonNull
    String getLocaleIsoFromISO3(@NonNull final Context context,
                                @NonNull final String iso3) {

        String iso2 = mLang3ToLang2Map.get(iso3);
        if (iso2 != null) {
            return iso2;
        }

        final Locale userLocale = context.getResources().getConfiguration().getLocales().get(0);

        // try again ('terminology' seems to be preferred/standard on Android (ICU?)
        String lang = toTerminology(userLocale, iso3);
        iso2 = mLang3ToLang2Map.get(lang);
        if (iso2 != null) {
            return iso2;
        }

        // desperate and last attempt using 'bibliographic'.
        lang = toBibliographic(userLocale, iso3);
        iso2 = mLang3ToLang2Map.get(lang);
        if (iso2 != null) {
            return iso2;
        }

        // give up
        return iso3;
    }

    /**
     * Convert the 3-char terminology code to bibliographic code.
     *
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">iso639-2</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     *
     * @param iso3 ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     */
    @NonNull
    public String toBibliographic(@NonNull final Locale locale,
                                  @NonNull final String iso3) {
        final String source = iso3.trim().toLowerCase(locale);
        if (source.length() != 3) {
            return source;
        }

        switch (source) {
            // Albanian
            case "sqi":
                return "alb";
            // Armenian
            case "hye":
                return "arm";
            // Basque
            case "eus":
                return "baq";
            // Burmese
            case "mya":
                return "bur";
            // Chinese
            case "zho":
                return "chi";
            // Czech
            case "ces":
                return "cze";
            // Dutch
            case "dut":
                return "nld";
            // French
            case "fra":
                return "fre";
            // Georgian
            case "kat":
                return "geo";
            // German
            case "deu":
                return "ger";
            // Greek
            case "ell":
                return "gre";
            // Icelandic
            case "isl":
                return "ice";
            // Macedonian
            case "mkd":
                return "mac";
            // Maori
            case "mri":
                return "mao";
            // Malay
            case "msa":
                return "may";
            // Persian
            case "fas":
                return "per";
            // Romanian
            case "ron":
                return "rum";
            // Slovak
            case "slk":
                return "slo";
            // Tibetan
            case "bod":
                return "tib";
            // Welsh
            case "cym":
                return "wel";

            default:
                return source;
        }
    }

    /**
     * Convert the 3-char bibliographic code to terminology code.
     *
     * <a href="https://www.loc.gov/standards/iso639-2/php/code_list.php">iso639-2</a>
     * <p>
     * This is the entire set correct as on 2019-08-22.
     *
     * @param iso3 ISO 639-2 (3-char) language code (either bibliographic or terminology coded)
     */
    @SuppressWarnings({"unused", "WeakerAccess"})
    @NonNull
    public String toTerminology(@NonNull final Locale locale,
                                @NonNull final String iso3) {
        final String source = iso3.trim().toLowerCase(locale);
        if (source.length() != 3) {
            return source;
        }
        switch (source) {
            // Albanian
            case "alb":
                return "sqi";
            // Armenian
            case "arm":
                return "hye";
            // Basque
            case "baq":
                return "eus";
            // Burmese
            case "bur":
                return "mya";
            // Chinese
            case "chi":
                return "zho";
            // Czech
            case "cze":
                return "ces";
            // Dutch
            case "dut":
                return "nld";
            // French
            case "fre":
                return "fra";
            // Georgian
            case "geo":
                return "kat";
            // German
            case "ger":
                return "deu";
            // Greek
            case "gre":
                return "ell";
            // Icelandic
            case "ice":
                return "isl";
            // Macedonian
            case "mac":
                return "mkd";
            // Maori
            case "mao":
                return "mri";
            // Malay
            case "may":
                return "msa";
            // Persian
            case "per":
                return "fas";
            // Romanian
            case "rum":
                return "ron";
            // Slovak
            case "slo":
                return "slk";
            // Tibetan
            case "tib":
                return "bod";
            // Welsh
            case "wel":
                return "cym";

            default:
                return source;
        }
    }


    /**
     * Create all cache files. This method is called during startup
     * from {@link BuildLanguageMappingsTask}.
     *
     * @param context Current context
     */
    public void createLanguageMappingCache(@NonNull final Context context) {
        // the one the user is using our app in (can be different from the system one)
        createLanguageMappingCache(context.getResources().getConfiguration().getLocales().get(0));
        // the system default
        createLanguageMappingCache(ServiceLocator.getSystemLocale());
        // Always add English
        createLanguageMappingCache(Locale.ENGLISH);

        //NEWTHINGS: adding a new search engine: add mappings for site specific languages

        // Dutch: StripInfoSearchEngine, KbNlSearchEngine
        createLanguageMappingCache(new Locale("nl"));
    }

    /**
     * Generate language mappings for a given Locale.
     *
     * @param locale the Locale for which to create a mapping
     */
    private void createLanguageMappingCache(@NonNull final Locale locale) {
        final SharedPreferences cacheFile = getCacheFile();

        // just return if already done for this Locale.
        if (cacheFile.getBoolean(LANG_CREATED_PREFIX + locale.getISO3Language(), false)) {
            return;
        }
        final SharedPreferences.Editor ed = cacheFile.edit();
        for (final Locale loc : Locale.getAvailableLocales()) {
            ed.putString(loc.getDisplayLanguage(locale).toLowerCase(locale),
                         loc.getISO3Language());
        }
        // signal this Locale was done
        ed.putBoolean(LANG_CREATED_PREFIX + locale.getISO3Language(), true);
        ed.apply();
    }

    /**
     * Convenience method to get the language SharedPreferences file.
     *
     * @return the SharedPreferences representing the language mapper
     */
    @NonNull
    private SharedPreferences getCacheFile() {
        return ServiceLocator.getAppContext().getSharedPreferences(LANGUAGE_MAP,
                                                                   Context.MODE_PRIVATE);
    }

    /**
     * Check if the device or user locale matches the given language.
     * <p>
     * Non-english sites are by default only enabled if either the device or
     * this app is running in the specified language.
     * The user can still enable/disable them at will of course.
     *
     * @param systemLocale device Locale <em>(passed in to allow mocking)</em>
     * @param userLocale   user Locale <em>(passed in to allow mocking)</em>
     * @param iso          language code to check
     *
     * @return {@code true} if sites should be enabled by default.
     */
    public boolean isLang(@NonNull final Locale systemLocale,
                          @NonNull final Locale userLocale,
                          @SuppressWarnings("SameParameterValue")
                          @NonNull final String iso) {
        return iso.equals(systemLocale.getISO3Language())
               || iso.equals(userLocale.getISO3Language());
    }
}
