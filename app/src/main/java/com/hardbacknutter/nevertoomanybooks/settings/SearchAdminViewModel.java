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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hardbacknutter.nevertoomanybooks.searchengines.Site;

/**
 * Shared between ALL tabs (fragments) and the hosting Activity.
 */
public class SearchAdminViewModel
        extends ViewModel {

    /** Log tag. */
    private static final String TAG = "SearchAdminViewModel";
    /**
     * Single-list/tab mode parameter.
     * <p>
     * Type: {@code java.util.ArrayList<? extends android.os.Parcelable>)}
     */
    public static final String BKEY_LIST = TAG + ":list";

    /** Ordered list. */
    private final Map<Site.Type, ArrayList<Site>> typeAndSites = new LinkedHashMap<>();

    /**
     * Pseudo constructor.
     * <p>
     * If a single tab is asked for, we read the list first from the arguments if present.
     * When all tabs are asked for, we get the system/user preferred lists.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    public void init(@Nullable final Bundle args) {
        if (typeAndSites.isEmpty()) {
            // editing a single list ? load the TEMPORARY list of sites from the args.
            if (args != null) {
                final ArrayList<Site> siteList = args.getParcelableArrayList(BKEY_LIST);
                if (siteList != null && !siteList.isEmpty()) {
                    // all sites have the same type, just grab it from the first one.
                    typeAndSites.put(siteList.get(0).getType(), siteList);
                }
            }

            if (typeAndSites.isEmpty()) {
                typeAndSites.put(Site.Type.Data,
                                 Site.Type.Data.getSites());
                typeAndSites.put(Site.Type.Covers,
                                 Site.Type.Covers.getSites());
                typeAndSites.put(Site.Type.AltEditions,
                                 Site.Type.AltEditions.getSites());
            }
        }
    }

    /**
     * Get the list of types we're handling.
     *
     * @return new List
     */
    public List<Site.Type> getTypes() {
        return new ArrayList<>(typeAndSites.keySet());
    }

    /**
     * Get the list for the given type.
     *
     * @param type type of list
     *
     * @return the list of sites
     */
    @NonNull
    ArrayList<Site> getList(@NonNull final Site.Type type) {
        final ArrayList<Site> list = typeAndSites.get(type);
        if (list == null) {
            throw new IllegalStateException("type not found: " + type);
        }
        return list;
    }

    /**
     * Validate if each list handled has at least one site enabled.
     * <p>
     * Can be called in single-list, AND in all-lists mode.
     *
     * @return {@code true} if each list handled has at least one site enabled.
     */
    public boolean validate() {
        for (final ArrayList<Site> list : typeAndSites.values()) {
            if (list.stream().noneMatch(Site::isEnabled)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Persist ALL lists.
     */
    void persist() {
        for (final Map.Entry<Site.Type, ArrayList<Site>> entry : typeAndSites.entrySet()) {
            entry.getKey().setSiteList(entry.getValue());
        }
    }

}
