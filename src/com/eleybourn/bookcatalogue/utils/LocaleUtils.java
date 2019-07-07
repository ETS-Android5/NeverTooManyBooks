package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.settings.Prefs;

public final class LocaleUtils {

    /** The SharedPreferences name where we'll maintain our language to ISO3 mappings. */
    private static final String LANGUAGE_MAP = "language2iso3";
    /**
     * A Map to translate currency symbols to their official ISO3 code.
     * <p>
     * ENHANCE: surely this can be done more intelligently ?
     */
    private static final Map<String, String> CURRENCY_MAP = new HashMap<>();
    /**
     * The locale used at startup; so that we can revert to system locale if we want to.
     * TODO: move this to App, where it's actually (re)set.
     */
    private static Locale sSystemInitialLocale;

    private LocaleUtils() {
    }

    /**
     * Needs to be called from main thread at App startup.
     * <p>
     * It's guarded against being called more then once.
     */
    @UiThread
    public static void init(@NonNull final Locale systemLocale) {

        // preserve startup==system Locale
        sSystemInitialLocale = systemLocale;

        if (CURRENCY_MAP.isEmpty()) {
            // key in map should always be lowercase
            CURRENCY_MAP.put("", "");
            CURRENCY_MAP.put("€", "EUR");
        /*
        English
        https://en.wikipedia.org/wiki/List_of_territorial_entities_where_English_is_an_official_language
         */
            CURRENCY_MAP.put("a$", "AUD"); // Australian Dollar
            CURRENCY_MAP.put("nz$", "NZD"); // New Zealand Dollar
            CURRENCY_MAP.put("£", "GBP"); // British Pound
            CURRENCY_MAP.put("$", "USD"); // Trump Disney's

            CURRENCY_MAP.put("c$", "CAD"); // Canadian Dollar
            CURRENCY_MAP.put("ir£", "IEP"); // Irish Punt
            CURRENCY_MAP.put("s$", "SGD"); // Singapore dollar

            // supported locales (including pre-euro)

            CURRENCY_MAP.put("br", "RUB"); // Russian Rouble
            CURRENCY_MAP.put("zł", "PLN"); // Polish Zloty
            CURRENCY_MAP.put("kč", "CZK "); // Czech Koruna
            CURRENCY_MAP.put("kc", "CZK "); // Czech Koruna
            CURRENCY_MAP.put("dm", "DEM"); //german marks
            CURRENCY_MAP.put("ƒ", "NLG"); // Dutch Guilder
            CURRENCY_MAP.put("fr", "BEF"); // Belgian Franc
            CURRENCY_MAP.put("fr.", "BEF"); // Belgian Franc
            CURRENCY_MAP.put("f", "FRF"); // French Franc
            CURRENCY_MAP.put("ff", "FRF"); // French Franc
            CURRENCY_MAP.put("pta", "ESP"); // Spanish Peseta
            CURRENCY_MAP.put("L", "ITL"); // Italian Lira
            CURRENCY_MAP.put("Δρ", "GRD"); // Greek Drachma
            CURRENCY_MAP.put("₺", "TRY "); // Turkish Lira

            // some others as seen on ISFDB site
            CURRENCY_MAP.put("r$", "BRL"); // Brazilian Real
            CURRENCY_MAP.put("kr", "DKK"); // Denmark Krone
            CURRENCY_MAP.put("Ft", "HUF"); // Hungarian Forint
        }
    }

    /**
     * Check if the user-preferred Locale is different from the currently in use Locale.
     *
     * @return {@code true} if there is a change (difference)
     */
    public static boolean isChanged(@NonNull final Context context) {
        boolean changed = !from(context).equals(getPreferredLocal());
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debug(LocaleUtils.class, "isChanged", "=" + changed);
        }
        return changed;
    }

    /**
     * Load the Locale setting from the users SharedPreference if needed.
     *
     * @param context to apply the user-preferred locale to.
     */
    public static void applyPreferred(@NonNull final Context context) {

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugEnter(LocaleUtils.class, "applyPreferred", toDebugString(context));
        }

        if (!isChanged(context)) {
            return;
        }

        Locale userLocale = getPreferredLocal();

        // Apply the user-preferred Locale globally.... but this does not override already
        // loaded resources. So.. this is for FUTURE use when new resources get initialised.
        Locale.setDefault(userLocale);

        // Apply to the resources as passed in.
        // create a delta-configuration; i.e. only to be used to modify the added items.
        Configuration deltaOnlyConfig = new Configuration();
        deltaOnlyConfig.setLocale(userLocale);

        context.getResources().updateConfiguration(deltaOnlyConfig,
                                                   context.getResources().getDisplayMetrics());


        // see if we need to add mappings for the new/current locale
        createLanguageMappingCache(userLocale);

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugExit(LocaleUtils.class, "applyPreferred",
                             toDebugString(context));
        }

    }

    /**
     * Test if the passed Locale is actually a 'real' Locale by checking ISO3 codes.
     */
    public static boolean isValid(@Nullable final Locale locale) {
        if (locale == null) {
            return false;
        }
        try {
            // MissingResourceException
            // NullPointerException can be thrown from within, when the ISO3Language fails.
            return locale.getISO3Language() != null && locale.getISO3Country() != null;
        } catch (@NonNull final MissingResourceException e) {
            // log but ignore.
            Logger.debug(LocaleUtils.class, "isValid",
                         "e=" + e.getLocalizedMessage(),
                         "locale=" + locale);
            return false;

        } catch (@NonNull final RuntimeException ignore) {
            return false;
        }
    }

    /**
     * Return the *REAL* Locale; i.e. the device itself.
     * This is important for using the ac
     *
     * @return the actual System Locale.
     */
    public static Locale getSystemLocale() {
        return sSystemInitialLocale;
    }

    /**
     * @return the user-preferred Locale as stored in the preferences.
     */
    @NonNull
    public static Locale getPreferredLocal() {
        String lang = App.getPrefString(Prefs.pk_ui_language);
        // the string "system" is also hardcoded in the preference string-array and
        // in the default setting in the preference screen.
        if (lang.isEmpty() || "system".equalsIgnoreCase(lang)) {
            return sSystemInitialLocale;
        } else {
            return from(lang);
        }
    }

    /**
     * syntax sugar...
     *
     * @return the current Locale for the passed context.
     */
    @NonNull
    public static Locale from(@NonNull final Context context) {
        return context.getResources().getConfiguration().locale;
    }

    /**
     * Creates a Locale from a concatenated locale string.
     *
     * @param code Locale name (e.g. 'de', 'en_AU')
     *
     * @return Locale corresponding to passed name
     */
    @NonNull
    private static Locale from(@NonNull final String code) {
        String[] parts;
        if (code.contains("_")) {
            parts = code.split("_");
        } else {
            parts = code.split("-");
        }
        Locale locale;
        switch (parts.length) {
            case 1:
                locale = new Locale(parts[0]);
                break;
            case 2:
                locale = new Locale(parts[0], parts[1]);
                break;
            default:
                locale = new Locale(parts[0], parts[1], parts[2]);
                break;
        }
        return locale;
    }

    /**
     * Try to convert a DisplayName to an ISO3 code.
     * At installation (or upgrade to v200) we generated the users System Locale + Locale.ENGLISH
     * Each time the user switches language, we generate an additional set.
     * That probably covers a lot if not all.
     *
     * @param displayName the string as normally produced by {@link Locale#getDisplayLanguage}
     *
     * @return the ISO3 code, or if conversion failed, the input string
     */
    @NonNull
    public static String getISO3Language(@NonNull final String displayName) {
        String iso = getLanguageCache().getString(displayName, null);
        if (iso != null) {
            return iso;
        }
        return displayName;
    }

    /**
     * Convert the passed string with a (hopefully valid) currency unit, into the ISO3 code
     * for that currency.
     *
     * @param currency to convert
     *
     * @return ISO3 code.
     */
    @Nullable
    public static String currencyToISO(@NonNull final String currency) {
        return CURRENCY_MAP.get(currency.trim().toLowerCase(getSystemLocale()));
    }

    /**
     * Translate the Language ISO3 code to the display name.
     *
     * @param iso the iso3 code
     *
     * @return the display name for the language,
     * or the input string itself if it was an invalid ISO3 code
     */
    @NonNull
    public static String getDisplayName(@NonNull final Context context,
                                        @NonNull final String iso) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.RECREATE_ACTIVITY) {
            Logger.debugEnter(LocaleUtils.class, "getLabel",
                              "iso=" + iso, toDebugString(context));
        }
        return getDisplayName(from(context), iso);
    }

    /**
     * Translate the Language ISO3 code to the display name.
     *
     * @param locale to use for the output language
     * @param iso    the iso3 code
     *
     * @return the display name for the language,
     * or the input string itself if it was an invalid ISO3 code
     */
    @NonNull
    public static String getDisplayName(@NonNull final Locale locale,
                                        @NonNull final String iso) {
        Locale isoLocale = new Locale(iso);
        if (isValid(isoLocale)) {
            return isoLocale.getDisplayLanguage(locale);
        }
        return iso;
    }
    /**
     * Load a Resources set for the specified Locale.
     *
     * @param context Current context
     * @param locale  the desired Locale
     *
     * @return the Resources
     */
    @NonNull
    public static Resources getLocalizedResources(@NonNull final Context context,
                                                  @NonNull final Locale locale) {
        Configuration conf = new Configuration(context.getResources().getConfiguration());
        String lang = locale.getLanguage();
        //FIXME: resources want 2-chars, locale 3-chars... is there a better way ?
        if (lang.length() == 2) {
            conf.setLocale(locale);
        } else {
            // any 3-character code needs to be converted to be able to find the resource.
            conf.setLocale(new Locale(mapLanguageCode(lang)));
        }

        Context localizedContext = context.createConfigurationContext(conf);
        return localizedContext.getResources();
    }

    /**
     * Map an ISO 639-3 language code to an ISO 639-1 language code.
     * <p>
     * There is one entry here for each language supported.
     * NEWKIND: if a new resource language is added, enable the mapping here.
     *
     * @param iso3LanguageCode ISO 639-3 language code
     *
     * @return ISO 639-1 language code
     */
    private static String mapLanguageCode(@NonNull final String iso3LanguageCode) {

        switch (iso3LanguageCode) {
            case "eng":
                // English
                return "en";
            case "ces":
                // Czech
                return "cs";
            case "deu":
                // German
                return "de";
            case "ell":
                // Greek
                return "el";
            case "spa":
                // Spanish
                return "es";
            case "fra":
                // French
                return "fr";
            case "ita":
                // Italian
                return "it";
            case "nld":
                // Dutch
                return "nl";
            case "pol":
                // Polish
                return "pl";
            case "rus":
                // Russian
                return "ru";
            case "tur":
                // Turkish
                return "tr";
            default:
                // English
                return "en";
//
//            case "afr":
//                // Afrikaans
//                return "af";
//            case "sqi":
//                // Albanian
//                return "sq";
//            case "ara":
//                // Arabic
//                return "ar";
//            case "aze":
//                // Azeri
//                return "az";
//            case "eus":
//                // Basque
//                return "eu";
//            case "bel":
//                // Belarusian
//                return "be";
//            case "ben":
//                // Bengali
//                return "bn";
//            case "bul":
//                // Bulgarian
//                return "bg";
//            case "cat":
//                // Catalan
//                return "ca";
//            case "chi_sim":
//                // Chinese (Simplified)
//                return "zh-CN";
//            case "chi_tra":
//                // Chinese (Traditional)
//                return "zh-TW";
//            case "hrv":
//                // Croatian
//                return "hr";
//            case "dan":
//                // Danish
//                return "da";
//            case "est":
//                // Estonian
//                return "et";
//            case "fin":
//                // Finnish
//                return "fi";
//            case "glg":
//                // Galician
//                return "gl";
//            case "heb":
//                // Hebrew
//                return "he";
//            case "hin":
//                // Hindi
//                return "hi";
//            case "hun":
//                // Hungarian
//                return "hu";
//            case "isl":
//                // Icelandic
//                return "is";
//            case "ind":
//                // Indonesian
//                return "id";
//            case "jpn":
//                // Japanese
//                return "ja";
//            case "kan":
//                // Kannada
//                return "kn";
//            case "kor":
//                // Korean
//                return "ko";
//            case "lav":
//                // Latvian
//                return "lv";
//            case "lit":
//                // Lithuanian
//                return "lt";
//            case "mkd":
//                // Macedonian
//                return "mk";
//            case "msa":
//                // Malay
//                return "ms";
//            case "mal":
//                // Malayalam
//                return "ml";
//            case "mlt":
//                // Maltese
//                return "mt";
//            case "nor":
//                // Norwegian
//                return "no";
//            case "por":
//                // Portuguese
//                return "pt";
//            case "ron":
//                // Romanian
//                return "ro";
//            case "srp":
//                // Serbian (Latin)
//                // TODO is google expecting Cyrillic?
//                return "sr";
//            case "slk":
//                // Slovak
//                return "sk";
//            case "slv":
//                // Slovenian
//                return "sl";
//            case "swa":
//                // Swahili
//                return "sw";
//            case "swe":
//                // Swedish
//                return "sv";
//            case "tgl":
//                // Tagalog
//                return "tl";
//            case "tam":
//                // Tamil
//                return "ta";
//            case "tel":
//                // Telugu
//                return "te";
//            case "tha":
//                // Thai
//                return "th";
//            case "ukr":
//                // Ukrainian
//                return "uk";
//            case "vie":
//                // Vietnamese
//                return "vi";
//            default:
//                return iso3LanguageCode;
        }
    }

    /**
     * generate initial language2iso mappings.
     */
    public static void createLanguageMappingCache() {
        // the system default
        createLanguageMappingCache(sSystemInitialLocale);
        // the one the user has configured our app into using
        createLanguageMappingCache(Locale.getDefault());
        // and English for compatibility with lots of websites.
        createLanguageMappingCache(Locale.ENGLISH);
    }

    /** Convenience method to get the language SharedPreferences file. */
    private static SharedPreferences getLanguageCache() {
        return App.getAppContext().getSharedPreferences(LANGUAGE_MAP, Context.MODE_PRIVATE);
    }

    /**
     * Generate language mappings for a given locale.
     */
    private static void createLanguageMappingCache(@NonNull final Locale myLocale) {
        SharedPreferences prefs = getLanguageCache();
        // just return if already done for this locale.
        if (prefs.getBoolean(myLocale.getISO3Language(), false)) {
            return;
        }

        SharedPreferences.Editor ed = prefs.edit();
        for (Locale loc : Locale.getAvailableLocales()) {
            ed.putString(loc.getDisplayLanguage(myLocale), loc.getISO3Language());
        }
        // signal this locale was done
        ed.putBoolean(myLocale.getISO3Language(), true);
        ed.apply();
    }

    public static String toDebugString(@NonNull final Context context) {
        Locale cur = from(context);
        return "\nsSystemInitialLocale            : " + sSystemInitialLocale.getDisplayName()
                + "\nsSystemInitialLocale(cur)       : " + sSystemInitialLocale.getDisplayName(cur)
                + "\nconfiguration.locale            : "
                + context.getResources().getConfiguration().locale.getDisplayName()
                + "\nconfiguration.locale(cur)       : "
                + context.getResources().getConfiguration().locale.getDisplayName(cur)

                + "\nLocale.getDefault()             : " + Locale.getDefault().getDisplayName()
                + "\nLocale.getDefault(cur)          : " + Locale.getDefault().getDisplayName(cur)
                + "\ngetPreferredLocal()             : " + getPreferredLocal().getDisplayName()
                + "\ngetPreferredLocal(cur)          : " + getPreferredLocal().getDisplayName(cur)

                + "\nApp.isInNeedOfRecreating()      : " + App.isInNeedOfRecreating()
                + "\nApp.isRecreating()              : " + App.isRecreating();
    }
}
