/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * Book Catalogue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Book Catalogue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.dialogs;

import android.annotation.SuppressLint;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.HashMap;
import java.util.Map;

import com.eleybourn.bookcatalogue.App;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Class to manage the display of 'hints' within the application. Each hint dialog has
 * a 'Do not show again' option, that results in an update to the preferences which
 * are checked by this code.
 * <p>
 * To add a new hint, create a string resource and add it to HINTS. Then, to display the
 * hint, simply call HintManager.displayHint(a, stringId).
 *
 * @author Philip Warner
 */
public final class HintManager {

    /** Preferences prefix. */
    private static final String PREF_PREFIX = "HintManager.";
    /** Preferences prefix for all hints. */
    private static final String PREF_HINT = PREF_PREFIX + "Hint.";

    /** All hints managed by this class. */
    @SuppressLint("UseSparseArrays")
    private static final Map<Integer, Hint> HINTS = new HashMap<>();

    static {
        HINTS.put(R.string.hint_booklist_style_menu,
                  new Hint("hint_booklist_style_menu"));
        HINTS.put(R.string.hint_booklist_styles_editor,
                  new Hint("BOOKLIST_STYLES_EDITOR"));
        HINTS.put(R.string.hint_booklist_style_groups,
                  new Hint("BOOKLIST_STYLE_GROUPS"));
        HINTS.put(R.string.hint_booklist_style_properties,
                  new Hint("BOOKLIST_STYLE_PROPERTIES"));
        // keep, might need again if re-implemented
        //HINTS.put(R.string.hint_booklist_global_properties,
        //         new Hint("BOOKLIST_GLOBAL_PROPERTIES"));

        HINTS.put(R.string.hint_authors_book_may_appear_more_than_once,
                  new Hint("BOOKLIST_MULTI_AUTHORS"));
        HINTS.put(R.string.hint_series_book_may_appear_more_than_once,
                  new Hint("BOOKLIST_MULTI_SERIES"));

        HINTS.put(R.string.hint_background_tasks,
                  new Hint("BACKGROUND_TASKS"));
        HINTS.put(R.string.hint_background_task_events,
                  new Hint("BACKGROUND_TASK_EVENTS"));

        HINTS.put(R.string.gr_explain_goodreads_no_isbn,
                  new Hint("explain_goodreads_no_isbn"));
        HINTS.put(R.string.gr_explain_goodreads_no_match,
                  new Hint("explain_goodreads_no_match"));

        HINTS.put(R.string.hint_autorotate_camera_images,
                  new Hint("hint_autorotate_camera_images"));
        HINTS.put(R.string.hint_view_only_book_details,
                  new Hint("hint_view_only_book_details"));
        HINTS.put(R.string.hint_view_only_help,
                  new Hint("hint_view_only_help"));
        HINTS.put(R.string.hint_book_list,
                  new Hint("hint_book_list"));
        HINTS.put(R.string.hint_book_search_by_text,
                  new Hint("hint_book_search_by_text"));
        // v200
        HINTS.put(R.string.pt_thumbnail_cropper_layer_type_summary,
                  new Hint("hint_pref_layer_type"));
    }

    private HintManager() {
    }

    /** Reset all hints to that they will be displayed again. */
    public static void resetHints() {
        for (Hint h : HINTS.values()) {
            h.reset();
        }
    }

    /**
     * Display the passed hint, if the user has not disabled it.
     *
     * @param inflater Inflater to use
     * @param stringId identifier for "from where" we want the hint to be displayed.
     *                 This allows two different places in the code use the same hint,
     *                 but one place being 'disable the hint' and another 'show'.
     * @param postRun  Optional Runnable to run after the hint was dismissed
     *                (or not displayed at all).
     * @param args     Optional arguments for the hint string
     */
    public static void displayHint(@NonNull final LayoutInflater inflater,
                                   @StringRes final int stringId,
                                   @Nullable final Runnable postRun,
                                   @Nullable final Object... args) {
        // Get the hint and return if it has been disabled.
        final Hint hint = HINTS.get(stringId);
        if (hint == null) {
            // log but ignore.
            Logger.warnWithStackTrace(HintManager.class, "displayHint", "not found",
                                      "stringId=" + stringId);
            return;
        }
        if (!hint.shouldBeShown()) {
            if (postRun != null) {
                postRun.run();
            }
            return;
        }
        hint.display(inflater, stringId, args, postRun);
    }

    public interface HintOwner {

        @StringRes
        int getHint();
    }


    /**
     * Class to represent a single Hint.
     */
    private static final class Hint {

        /** Preferences key suffix specific to this hint. */
        @NonNull
        private final String mKey;

        /** Indicates that this hint was displayed already in this instance of the app. */
        private boolean mHasBeenDisplayed;

        /**
         * Constructor.
         *
         * @param key Preferences key suffix specific to this hint
         */
        private Hint(@NonNull final String key) {
            mKey = PREF_HINT + key;
        }

        /**
         * Set the preference to indicate if this hint should be shown again.
         *
         * @param visible Flag indicating future visibility
         */
        private void setVisibility(final boolean visible) {
            App.getPrefs().edit().putBoolean(mKey, visible).apply();
        }

        /**
         * Check if this hint should be shown.
         */
        private boolean shouldBeShown() {
            return !mHasBeenDisplayed && App.getPrefs().getBoolean(mKey, true);
        }

        /**
         * display the hint.
         *
         * @param inflater to use
         * @param stringId for the message
         * @param args     for the message
         * @param postRun  Runnable to start afterwards
         */
        void display(@NonNull final LayoutInflater inflater,
                     @StringRes final int stringId,
                     @Nullable final Object[] args,
                     @Nullable final Runnable postRun) {

            // Build the hint dialog
            final View root = inflater.inflate(R.layout.dialog_hint, null);

            // Setup the message
            final TextView messageView = root.findViewById(R.id.hint);
            String hintText = inflater.getContext().getString(stringId, args);
            // allow links
            messageView.setText(Utils.linkifyHtml(hintText));
            // clicking a link, start a browser (or whatever)
            messageView.setMovementMethod(LinkMovementMethod.getInstance());

            final AlertDialog dialog = new AlertDialog.Builder(inflater.getContext())
                    .setView(root)
                    .setTitle(R.string.hint)
                    .setNegativeButton(R.string.btn_disable_message, (d, which) -> {
                        d.dismiss();
                        setVisibility(false);
                        if (postRun != null) {
                            postRun.run();
                        }
                    })
                    .setPositiveButton(android.R.string.ok, (d, which) -> {
                        d.dismiss();
                        if (postRun != null) {
                            postRun.run();
                        }
                    })
                    .create();

            dialog.show();
            mHasBeenDisplayed = true;
        }

        void reset() {
            setVisibility(true);
            mHasBeenDisplayed = false;
        }
    }
}
