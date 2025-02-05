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
package com.hardbacknutter.nevertoomanybooks.settings.styles;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;
import androidx.recyclerview.widget.RecyclerView;

import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.activityresultcontracts.EditStyleContract;
import com.hardbacknutter.nevertoomanybooks.booklist.style.StyleDataStore;
import com.hardbacknutter.nevertoomanybooks.booklist.style.UserStyle;
import com.hardbacknutter.nevertoomanybooks.booklist.style.groups.BooklistGroup;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.settings.widgets.MultiSelectListPreferenceSummaryProvider;

/**
 * Main fragment to edit a Style.
 * <p>
 * Passing in a style with a valid UUID, settings are read/written to the style specific file.
 * If the uuid is {@code null}, then we're editing the global defaults.
 */
public class StyleFragment
        extends StyleBaseFragment {

    /** Fragment manager tag. */
    private static final String TAG = "StylePreferenceFragment";
    private static final String SIS_NAME_SET = TAG + ":nameSet";

    private static final String PSK_LIST_SHOWS_BOOK_DETAILS = "psk_style_show_details";

    /** Set the hosting Activity result, and close it. */
    private final OnBackPressedCallback backPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    vm.updateOrInsertStyle();

                    final Intent resultIntent = EditStyleContract
                            .createResultIntent(vm.getTemplateUuid(),
                                                vm.isModified(),
                                                vm.getStyle().getUuid());

                    //noinspection ConstantConditions
                    getActivity().setResult(Activity.RESULT_OK, resultIntent);
                    getActivity().finish();
                }
            };
    @NonNull
    private final SwitchPreference[] pShowCoversOnDetailsScreen = new SwitchPreference[2];
    /** Flag: prompt for the name of cloned styles. */
    private boolean nameSet;
    private EditTextPreference pName;
    private Preference pCoverScale;
    private Preference pTextScale;
    private SeekBarPreference pExpansionLevel;
    private Preference pListHeader;
    private Preference pPrimaryAuthorType;
    private Preference pListFieldVisibility;
    private Preference pGroups;

    @SuppressWarnings("ConstantConditions")
    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState,
                                    @Nullable final String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);


        setPreferencesFromResource(R.xml.preferences_style, rootKey);

        if (savedInstanceState != null) {
            nameSet = savedInstanceState.getBoolean(SIS_NAME_SET);
        }

        pName = findPreference(StyleDataStore.PK_NAME);
        pExpansionLevel = findPreference(StyleDataStore.PK_EXPANSION_LEVEL);
        pCoverScale = findPreference(StyleDataStore.PK_COVER_SCALE);
        pTextScale = findPreference(StyleDataStore.PK_TEXT_SCALE);
        pListHeader = findPreference(StyleDataStore.PK_LIST_HEADER);
        pPrimaryAuthorType = findPreference(StyleDataStore.PK_GROUPS_AUTHOR_PRIMARY_TYPE);
        pListFieldVisibility = findPreference(PSK_LIST_SHOWS_BOOK_DETAILS);
        pGroups = findPreference(StyleDataStore.PK_GROUPS);

        pShowCoversOnDetailsScreen[0] = findPreference(
                StyleDataStore.PK_DETAILS_SHOW_COVER[0]);
        pShowCoversOnDetailsScreen[1] = findPreference(
                StyleDataStore.PK_DETAILS_SHOW_COVER[1]);

        pName.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
        pListHeader.setSummaryProvider(MultiSelectListPreferenceSummaryProvider.getInstance());
        pPrimaryAuthorType.setSummaryProvider(
                MultiSelectListPreferenceSummaryProvider.getInstance());

        pName.setOnBindEditTextListener(editText -> {
            editText.setInputType(InputType.TYPE_CLASS_TEXT
                                  | InputType.TYPE_TEXT_VARIATION_URI);
            editText.selectAll();
        });

        pShowCoversOnDetailsScreen[0].setOnPreferenceChangeListener((preference, newValue) -> {
            // Covers on DETAIL screen:
            // Setting cover 0 to false -> set cover 1 to false as well
            if (newValue instanceof Boolean && !(Boolean) newValue) {
                pShowCoversOnDetailsScreen[1].setChecked(false);
            }
            return true;
        });
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //noinspection ConstantConditions
        getActivity().getOnBackPressedDispatcher()
                     .addCallback(getViewLifecycleOwner(), backPressedCallback);

        vm.onModified().observe(getViewLifecycleOwner(), aVoid -> updateSummaries());

        if (savedInstanceState == null) {
            //noinspection ConstantConditions
            TipManager.getInstance()
                      .display(getContext(), R.string.tip_booklist_style_properties, null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // loop over all groups, add the preferences for groups we have
        // and hide for groups we don't/no longer have.
        final UserStyle style = vm.getStyle();

        final PreferenceScreen screen = getPreferenceScreen();
        for (final BooklistGroup group : BooklistGroup.getAllGroups(style)) {
            group.setPreferencesVisible(screen, style.hasGroup(group.getId()));
        }

        updateSummaries();

        // for new (i.e. cloned) styles, auto-popup the name field for the user to change it.
        if (style.getId() == 0) {
            pName.setViewId(R.id.STYLE_NAME_VIEW);
            // We need this convoluted approach as the view we want to click
            // will only exist after the RecyclerView has bound it.
            getListView().addOnChildAttachStateChangeListener(
                    new RecyclerView.OnChildAttachStateChangeListener() {
                        @Override
                        public void onChildViewAttachedToWindow(@NonNull final View view) {
                            if (view.getId() == R.id.STYLE_NAME_VIEW && !nameSet) {
                                // We only do this once. It IS legal to use the same name.
                                nameSet = true;
                                view.performClick();
                            }
                        }

                        @Override
                        public void onChildViewDetachedFromWindow(@NonNull final View view) {
                        }
                    });
        }
    }

    /**
     * The summary for these Preference's reflect what is selected on ANOTHER screen
     * or changes to ANOTHER KEY.
     * (this also means we cannot use SummaryProvider's for these)
     */
    private void updateSummaries() {
        final UserStyle style = vm.getStyle();
        final Context context = getContext();

        //noinspection ConstantConditions
        pListFieldVisibility.setSummary(style.getFieldVisibilitySummaryText(context));

        pGroups.setSummary(style.getGroupsSummaryText(context));

        pCoverScale.setSummary(context.getResources().getStringArray(
                R.array.pe_bob_thumbnail_scale)[style.getCoverScale()]);

        pTextScale.setSummary(context.getResources().getStringArray(
                R.array.pe_bob_text_scale)[style.getTextScale()]);

        // the 'level expansion' depends on the number of groups in use
        pExpansionLevel.setMax(style.getGroupCount());
        pExpansionLevel.setValue(style.getExpansionLevel());
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SIS_NAME_SET, nameSet);
    }
}
