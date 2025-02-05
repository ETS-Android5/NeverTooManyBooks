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
package com.hardbacknutter.nevertoomanybooks;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.os.LocaleList;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xml.sax.SAXException;

import com.hardbacknutter.nevertoomanybooks._mocks.os.BundleMock;
import com.hardbacknutter.nevertoomanybooks._mocks.os.ContextMock;
import com.hardbacknutter.nevertoomanybooks._mocks.os.SharedPreferencesMock;
import com.hardbacknutter.nevertoomanybooks.debug.TestFlags;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.amazon.AmazonSearchEngine;
import com.hardbacknutter.nevertoomanybooks.searchengines.librarything.LibraryThingSearchEngine;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.utils.AppLocale;
import com.hardbacknutter.nevertoomanybooks.utils.Languages;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

/**
 * Strictness.LENIENT: this setup is shared + provides answers/returns for future additions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class Base {

    private static final String PACKAGE_NAME = "com.hardbacknutter.nevertoomanybooks";

    @Mock
    protected App mApp;
    @Mock
    protected Resources mResources;
    @Mock
    protected Configuration mConfiguration;
    @Mock
    protected LocaleList mLocaleList;

    protected Bundle mRawData;
    protected Context mContext;
    protected SharedPreferences mMockPreferences;

    /** set during setup() call. */
    @Nullable
    protected Locale mLocale0;

    private Locale mJdkLocale;

    @BeforeAll
    static void startUp() {
        TestFlags.isJUnit = true;
    }

    /**
     * @param locale0 to use for
     *                JDK
     *                context.getResources().getConfiguration().getLocales().get(0)
     */
    public void setLocale(@NonNull final Locale locale0) {
        mLocale0 = locale0;
        Locale.setDefault(mLocale0);
    }

    @AfterEach
    void tearDown() {
        mLocale0 = null;
        Locale.setDefault(mJdkLocale);
    }

    /**
     * Each test <strong>should</strong> call {@link #setLocale(Locale)} as needed.
     * The default is Locale.US.
     */
    @BeforeEach
    @CallSuper
    public void setup()
            throws ParserConfigurationException, SAXException {
        // save the JDK locale first
        mJdkLocale = Locale.getDefault();
        // set as default
        setLocale(Locale.US);

        mContext = ContextMock.create(PACKAGE_NAME);

        mMockPreferences = SharedPreferencesMock.create();

        mRawData = BundleMock.create();

        when(mApp.getApplicationContext()).thenReturn(mContext);

        when(mContext.getResources()).thenReturn(mResources);

        when(mContext.getExternalFilesDirs(eq(Environment.DIRECTORY_PICTURES))).thenAnswer(
                (Answer<File[]>) invocation -> {
                    final String tmpDir = System.getProperty("java.io.tmpdir");
                    final File[] dirs = new File[1];
                    dirs[0] = new File(tmpDir, "Pictures");
                    //noinspection ResultOfMethodCallIgnored
                    dirs[0].mkdir();
                    return dirs;
                });

        // Global prefs
        when(mContext.getSharedPreferences(eq(PACKAGE_NAME + "_preferences"), anyInt()))
                .thenReturn(mMockPreferences);

        // Styles
        when(mContext.getSharedPreferences(anyString(), anyInt()))
                .thenReturn(mMockPreferences);


        doAnswer(invocation -> mResources.getString(invocation.getArgument(0)))
                .when(mContext).getString(anyInt());

        when(mResources.getConfiguration()).thenReturn(mConfiguration);
        when(mConfiguration.getLocales()).thenReturn(mLocaleList);
        doNothing().when(mConfiguration).setLocale(any(Locale.class));

        when(mLocaleList.get(0)).thenAnswer((Answer<Locale>) invocation -> mLocale0);


        ServiceLocator.create(mContext, BundleMock::create);

        SearchEngineRegistry.create(mContext);

        setupStringResources(mResources);
        setupLanguageMap(mContext);
        setupSearchEnginePreferences();
    }

    private void setupLanguageMap(@NonNull final Context context) {
        /*
         * SharedPreferences for the language map.
         */
        final SharedPreferences mLanguageMap = SharedPreferencesMock.create();
        mLanguageMap.edit()
                    .putString("english", "eng")
                    .putString("engels", "eng")
                    .putString("anglais", "eng")
                    .putString("englisch", "eng")

                    .putString("french", "fra")
                    .putString("français", "fra")
                    .putString("französisch", "fra")
                    .putString("frans", "fra")

                    .putString("german", "ger")
                    .putString("allemand", "ger")
                    .putString("deutsch", "ger")
                    .putString("duits", "ger")

                    .putString("dutch", "nld")
                    .putString("néerlandais", "nld")
                    .putString("niederländisch", "nld")
                    .putString("nederlands", "nld")

                    .apply();

        when(context.getSharedPreferences(eq(Languages.LANGUAGE_MAP), anyInt()))
                .thenReturn(mLanguageMap);
    }

    private void setupSearchEnginePreferences() {
        mMockPreferences.edit()
                        .putString(Prefs.pk_ui_locale, AppLocale.SYSTEM_LANGUAGE)
                        // random some at true, some at false.
                        .putBoolean("search.site.amazon.data.enabled", true)
                        .putBoolean("search.site.googlebooks.data.enabled", false)
                        .putBoolean("search.site.isfdb.data.enabled", true)
                        .putBoolean("search.site.kbnl.data.enabled", false)
                        .putBoolean("search.site.lastdodo.data.enabled", true)
                        .putBoolean("search.site.librarything.data.enabled", false)
                        .putBoolean("search.site.openlibrary.data.enabled", true)
                        .putBoolean("search.site.stripinfo.data.enabled", false)

                        // deliberate added 4 (LibraryThing) and omitted 128/256
                        .putString("search.siteOrder.data", "64,32,16,8,4,2,1")
                        .putString("search.siteOrder.covers", "16,2,8,64,32")
                        .putString("search.siteOrder.alted", "16,4")

                        .apply();

        when(mMockPreferences.getString(eq(AmazonSearchEngine.PK_HOST_URL),
                                        nullable(String.class)))
                .thenAnswer((Answer<String>) invocation ->
                        getLocalizedSiteUrl("amazon", true));

        when(mMockPreferences.getString(eq(LibraryThingSearchEngine.PK_HOST_URL),
                                        nullable(String.class)))
                .thenAnswer((Answer<String>) invocation ->
                        getLocalizedSiteUrl("librarything", false));
    }

    @NonNull
    private String getLocalizedSiteUrl(@NonNull final String site,
                                       final boolean hasUkSite) {
        if (mLocale0 != null) {
            final String iso3 = mLocale0.getISO3Language();
            if (Locale.US.getISO3Language().equals(iso3)) {
                return "https://www." + site + ".com";
            } else if (Locale.FRANCE.getISO3Language().equals(iso3)) {
                return "https://www." + site + ".fr";
            } else if (Locale.GERMANY.getISO3Language().equals(iso3)) {
                return "https://www." + site + ".de";
            } else if (new Locale("nl").getISO3Language().equals(iso3)) {
                return "https://www." + site + ".nl";

            } else if (hasUkSite && Locale.UK.getISO3Language().equals(iso3)) {
                return "https://www." + site + ".co.uk";
            }
        }
        return "https://www." + site + ".com";
    }

    /*
     * String resources.
     */
    private void setupStringResources(@NonNull final Resources resources) {
        when(resources.getString(eq(R.string.book_format_paperback))).thenReturn("Paperback");
        when(resources.getString(eq(R.string.book_format_softcover))).thenReturn("Softcover");
        when(resources.getString(eq(R.string.book_format_hardcover))).thenReturn("Hardcover");
        when(resources.getString(eq(R.string.book_format_dimensions))).thenReturn("Dim");

        when(resources.getString(eq(R.string.unknown_title))).thenReturn("[Unknown title]");
        when(resources.getString(eq(R.string.unknown_author))).thenReturn("[Unknown author]");

        when(resources.getString(eq(R.string.lbl_book))).thenReturn("Book");
    }

}
