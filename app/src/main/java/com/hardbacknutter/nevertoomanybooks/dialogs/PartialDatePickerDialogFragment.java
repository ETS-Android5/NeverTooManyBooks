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
package com.hardbacknutter.nevertoomanybooks.dialogs;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.snackbar.Snackbar;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.utils.dates.FullDateParser;
import com.hardbacknutter.nevertoomanybooks.utils.dates.PartialDate;

/**
 * DialogFragment class to allow for selection of partial dates from 0AD to 9999AD.
 * <p>
 * Seems reasonable to disable relevant day/month pickers if one is invalid, but it's actually
 * not very friendly when entering data for new books so we don't.
 * So for instance, if a day/month/year are set, and the user select "--" (unset) the month,
 * we leave the day setting unchanged.
 * A final validity check is done when trying to accept the date.
 */
public class PartialDatePickerDialogFragment
        extends FFBaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "PartialDatePickerDialog";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";
    /** a standard sql style date string, must be correct. */
    private static final String BKEY_DATE = TAG + ":date";
    /** Argument. */
    private static final String BKEY_FIELD_ID = TAG + ":fieldId";
    private static final String BKEY_DIALOG_TITLE = TAG + ":title";

    /** Displayed to user: unset month. */
    private static final String UNKNOWN_MONTH = "---";
    /** Displayed to user: unset day. */
    private static final String UNKNOWN_DAY = "--";
    /** or the date split into components, which can be partial. */
    private static final String SIS_YEAR = TAG + ":year";
    /** range: 0: 'not set' or 1..12. */
    private static final String SIS_MONTH = TAG + ":month";
    /** range: 0: 'not set' or 1..31. */
    private static final String SIS_BKEY_DAY = TAG + ":day";
    /** FragmentResultListener request key to use for our response. */
    private String requestKey;
    @IdRes
    private int fieldId;

    /** Currently displayed; {@code 0} if empty/invalid. */
    private int year;
    /**
     * Currently displayed; {@code 0} if invalid/empty.
     * <strong>IMPORTANT:</strong> 1..12 based. (the jdk internals expect 0..11).
     */
    private int month;
    /** Currently displayed; {@code 0} if empty/invalid. */
    private int day;

    private NumberPicker dayPicker;

    /** This listener is called after <strong>any change</strong> made to the pickers. */
    private final NumberPicker.OnValueChangeListener valueChangeListener =
            (picker, oldVal, newVal) -> {
                final int pickerId = picker.getId();

                if (pickerId == R.id.year) {
                    year = newVal;
                    // only February can be different number of days
                    if (month == 2) {
                        updateDaysInMonth();
                    }

                } else if (pickerId == R.id.month) {
                    month = newVal;
                    updateDaysInMonth();

                } else if (pickerId == R.id.day) {
                    day = newVal;

                } else {
                    if (BuildConfig.DEBUG /* always */) {
                        Log.d(TAG, "id=" + picker.getId());
                    }
                }
            };

    @StringRes
    private int dialogTitleId;

    /**
     * No-arg constructor for OS use.
     */
    public PartialDatePickerDialogFragment() {
        super(R.layout.dialog_partial_date_picker);
    }


    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);
        dialogTitleId = args.getInt(BKEY_DIALOG_TITLE);
        fieldId = args.getInt(BKEY_FIELD_ID);

        if (savedInstanceState == null) {
            // BKEY_DATE is only present in the original args
            parseDate(args.getString(BKEY_DATE));
        } else {
            // These are only present in the savedInstanceState
            year = savedInstanceState.getInt(SIS_YEAR);
            month = savedInstanceState.getInt(SIS_MONTH);
            day = savedInstanceState.getInt(SIS_BKEY_DAY);
        }

        // can't have a 0 year. (but month/day can be 0)
        // The user can/should use the "clear" button if they want no date at all.
        if (year == 0) {
            year = LocalDate.now().getYear();
        }
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Ensure components match current Locale order
        reorderPickers(view);

        dialogToolbar.setTitle(dialogTitleId != 0 ? dialogTitleId : R.string.action_edit);

        final NumberPicker yearPicker = view.findViewById(R.id.year);
        // 0: 'not set'
        yearPicker.setMinValue(0);
        // we're optimistic...
        yearPicker.setMaxValue(2100);
        yearPicker.setOnValueChangedListener(valueChangeListener);

        final NumberPicker monthPicker = view.findViewById(R.id.month);
        // 0: 'not set' + 1..12 real months
        monthPicker.setMinValue(0);
        monthPicker.setMaxValue(12);
        monthPicker.setDisplayedValues(getMonthAbbr());
        monthPicker.setOnValueChangedListener(valueChangeListener);

        dayPicker = view.findViewById(R.id.day);
        // 0: 'not set'
        dayPicker.setMinValue(0);
        // Make sure that the picker can initially take any 'day' value. Otherwise,
        // when a dialog is reconstructed after rotation, the 'day' field will not be
        // restored by Android.
        dayPicker.setMaxValue(31);
        dayPicker.setFormatter(value -> value == 0 ? UNKNOWN_DAY : String.valueOf(value));
        dayPicker.setOnValueChangedListener(valueChangeListener);

        // set the initial date
        yearPicker.setValue(year != 0 ? year : LocalDate.now().getYear());
        monthPicker.setValue(month);
        dayPicker.setValue(day);
        updateDaysInMonth();
    }

    @Override
    protected boolean onToolbarMenuItemClick(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.MENU_ACTION_CONFIRM) {
            if (saveChanges()) {
                dismiss();
            }
            return true;
        }
        return false;
    }

    private boolean saveChanges() {
        if (day != 0 && month == 0) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_if_day_set_month_and_year_must_be,
                          Snackbar.LENGTH_LONG).show();

        } else if (month != 0 && year == 0) {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.warning_if_month_set_year_must_be,
                          Snackbar.LENGTH_LONG).show();

        } else {
            Launcher.setResult(this, requestKey, fieldId, year, month, day);
            return true;
        }

        return false;
    }


    /**
     * Generate the month names (abbreviated). There are 13: first entry being 'unknown'.
     */
    private String[] getMonthAbbr() {
        final Locale userLocale = getResources().getConfiguration().getLocales().get(0);
        final String[] monthNames = new String[13];
        monthNames[0] = UNKNOWN_MONTH;
        for (int i = 1; i <= 12; i++) {
            monthNames[i] = Month.of(i).getDisplayName(TextStyle.SHORT, userLocale);
        }
        return monthNames;
    }

    /**
     * Depending on year/month selected, set the correct number of days.
     */
    private void updateDaysInMonth() {
        int currentlySelectedDay = day;

        // Determine the total days if we have a valid month/year
        int totalDays;
        if (year != 0 && month != 0) {
            try {
                // Should never throw here, but paranoia...
                totalDays = LocalDate.of(year, month, 1).lengthOfMonth();
            } catch (@NonNull final DateTimeException e) {
                totalDays = 31;
            }
        } else {
            // allow the user to start inputting with day first.
            totalDays = 31;
        }

        dayPicker.setMaxValue(totalDays);

        // Ensure selected day is valid
        if (currentlySelectedDay == 0) {
            dayPicker.setValue(0);
        } else {
            if (currentlySelectedDay > totalDays) {
                currentlySelectedDay = totalDays;
            }
            dayPicker.setValue(currentlySelectedDay);
        }
    }

    /**
     * Reorder the views in the dialog to suit the current Locale.
     *
     * @param root Root view
     */
    private void reorderPickers(@NonNull final View root) {
        final char[] order;
        try {
            // This actually throws exception in some versions of Android, specifically when
            // the Locale specific date format has the day name (EEE) in it. So we exit and
            // just use our default order in these cases.
            // See BC Issue #712.
            order = DateFormat.getDateFormatOrder(getContext());
        } catch (@NonNull final RuntimeException e) {
            return;
        }

        // Default order is {year, month, day} so if that's the order do nothing.
        if ((order[0] == 'y') && (order[1] == 'M')) {
            return;
        }

        // Remove the 3 pickers from their parent and add them back in the required order.
        final ViewGroup parent = root.findViewById(R.id.dateSelector);
        // Get the three views
        final ConstraintLayout y = parent.findViewById(R.id.yearSelector);
        final ConstraintLayout m = parent.findViewById(R.id.monthSelector);
        final ConstraintLayout d = parent.findViewById(R.id.daySelector);
        // Remove them
        parent.removeAllViews();
        // Re-add in the correct order.
        for (final char c : order) {
            switch (c) {
                case 'd':
                    parent.addView(d);
                    break;
                case 'M':
                    parent.addView(m);
                    break;
                default:
                    parent.addView(y);
                    break;
            }
        }
    }

    @Override
    @CallSuper
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SIS_YEAR, year);
        outState.putInt(SIS_MONTH, month);
        outState.putInt(SIS_BKEY_DAY, day);
    }

    /**
     * Parse the input ISO date string into the individual components.
     * <p>
     * Note we don't use {@link FullDateParser}
     * as we the current implementation always returns full dates.
     * Here, we explicitly need to support partial dates.
     *
     * <ul>Allowed formats:
     *      <li>yyyy-mm-dd time</li>
     *      <li>yyyy-mm-dd</li>
     *      <li>yyyy-mm</li>
     *      <li>yyyy</li>
     * </ul>
     *
     * @param dateString SQL formatted (partial) date, can be {@code null}.
     */
    private void parseDate(@Nullable final String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            year = 0;
            month = 0;
            day = 0;
            return;
        }

        int yyyy = 0;
        int mm = 0;
        int dd = 0;
        try {
            final String[] dateAndTime = dateString.split(" ");
            final String[] date = dateAndTime[0].split("-");
            yyyy = Integer.parseInt(date[0]);
            if (date.length > 1) {
                mm = Integer.parseInt(date[1]);
            }
            if (date.length > 2) {
                dd = Integer.parseInt(date[2]);
            }
        } catch (@NonNull final NumberFormatException ignore) {
            // ignore. Any values we did get, are used.
        }

        year = yyyy;
        month = mm;
        day = dd;
    }

    public abstract static class Launcher
            implements FragmentResultListener {

        private static final String FIELD_ID = "fieldId";
        private static final String YEAR = "year";
        private static final String MONTH = "month";
        private static final String DAY = "day";
        private String requestKey;
        private FragmentManager fragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @IdRes final int fieldId,
                              final int year,
                              final int month,
                              final int day) {
            final Bundle result = new Bundle(4);
            result.putInt(FIELD_ID, fieldId);
            result.putInt(YEAR, year);
            result.putInt(MONTH, month);
            result.putInt(DAY, day);

            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            this.fragmentManager = fragmentManager;
            this.requestKey = requestKey;
            this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
        }

        /**
         * Launch the dialog.
         *
         * @param dialogTitleId resource id for the dialog title
         * @param fieldId       this dialog operates on
         *                      (one launcher can serve multiple fields)
         * @param currentValue  the current value of the field
         * @param todayIfNone   {@code true} if we should use 'today' if the field was empty.
         */
        public void launch(@StringRes final int dialogTitleId,
                           @IdRes final int fieldId,
                           @Nullable final String currentValue,
                           final boolean todayIfNone) {
            final String dateStr;
            if (todayIfNone && (currentValue == null || currentValue.isEmpty())) {
                dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            } else {
                dateStr = Objects.requireNonNullElse(currentValue, "");
            }

            final Bundle args = new Bundle(4);
            args.putString(BKEY_REQUEST_KEY, requestKey);
            args.putInt(BKEY_DIALOG_TITLE, dialogTitleId);
            args.putInt(BKEY_FIELD_ID, fieldId);
            args.putString(BKEY_DATE, dateStr);

            final DialogFragment frag = new PartialDatePickerDialogFragment();
            frag.setArguments(args);
            frag.show(fragmentManager, TAG);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {

            onResult(result.getInt(FIELD_ID),
                     new PartialDate(result.getInt(YEAR),
                                     result.getInt(MONTH),
                                     result.getInt(DAY)));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param fieldId this destination field id
         * @param date    the picked date
         */
        public abstract void onResult(@IdRes int fieldId,
                                      @NonNull PartialDate date);
    }
}
