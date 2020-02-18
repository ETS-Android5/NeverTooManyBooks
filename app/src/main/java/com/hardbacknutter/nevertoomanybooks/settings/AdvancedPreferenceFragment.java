/*
 * @Copyright 2020 HardBackNutter
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
package com.hardbacknutter.nevertoomanybooks.settings;

import android.os.Bundle;

import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.booklist.RowStateDAO;
import com.hardbacknutter.nevertoomanybooks.debug.DebugReport;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.utils.StorageUtils;

/**
 * Used/defined in xml/preferences.xml
 */
public class AdvancedPreferenceFragment
        extends BasePreferenceFragment {

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState,
                                    final String rootKey) {

        setPreferencesFromResource(R.xml.preferences_advanced, rootKey);

        initListeners();
    }

    /**
     * Hook up specific listeners/preferences.
     */
    private void initListeners() {
        Preference preference;

        // Purge BLNS database table.
        preference = findPreference(Prefs.PSK_PURGE_BLNS);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setTitle(R.string.lbl_purge_blns)
                        .setMessage(R.string.info_purge_blns_all)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) -> RowStateDAO.clearAll())
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_TIP_RESET_ALL);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                TipManager.reset(getContext());
                //noinspection ConstantConditions
                Snackbar.make(getView(), R.string.tip_reset_done, Snackbar.LENGTH_LONG).show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_PURGE_FILES);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                long bytes = StorageUtils.purgeFiles(getContext(), false);
                String formattedSize = StorageUtils.formatFileSize(getContext(), bytes);
                String msg = getString(R.string.info_cleanup_files_text, formattedSize,
                                       getString(R.string.lbl_send_debug_info));

                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.lbl_purge_files)
                        .setMessage(msg)
                        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
                        .setPositiveButton(android.R.string.ok, (d, w) ->
                                StorageUtils.purgeFiles(getContext(), true))
                        .create()
                        .show();
                return true;
            });
        }

        preference = findPreference(Prefs.PSK_SEND_DEBUG_INFO);
        if (preference != null) {
            preference.setOnPreferenceClickListener(p -> {
                //noinspection ConstantConditions
                new MaterialAlertDialogBuilder(getContext())
                        .setIcon(R.drawable.ic_warning)
                        .setTitle(R.string.debug)
                        .setMessage(R.string.debug_send_info_text)
                        .setNegativeButton(android.R.string.cancel, (dialog, which) ->
                                dialog.dismiss())
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            if (!DebugReport.sendDebugInfo(getContext())) {
                                //noinspection ConstantConditions
                                Snackbar.make(getView(), R.string.error_email_failed,
                                              Snackbar.LENGTH_LONG).show();
                            }
                        })
                        .create()
                        .show();
                return true;
            });
        }
    }
}
