/*
 * @copyright 2011 Philip Warner
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

package com.eleybourn.bookcatalogue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivityWithTasks;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.searches.SearchAdminActivity;
import com.eleybourn.bookcatalogue.searches.Site;
import com.eleybourn.bookcatalogue.searches.UpdateFieldsFromInternetTask;
import com.eleybourn.bookcatalogue.searches.librarything.LibraryThingManager;
import com.eleybourn.bookcatalogue.tasks.managedtasks.ManagedTask;
import com.eleybourn.bookcatalogue.utils.UserMessage;

/**
 * NEWKIND: must stay in sync with {@link UpdateFieldsFromInternetTask}.
 * <p>
 * FIXME: ... re-test and see why the progress stops.
 *  Seems we hit some limit in number of HTTP connections (server imposed ?)
 */
public class UpdateFieldsFromInternetActivity
        extends BaseActivityWithTasks {

    /** RequestCode for editing the search sites order. */
    private static final int REQ_PREFERRED_SEARCH_SITES = 0;

    /**
     * optionally limit the sites to search on.
     * By default uses {@link Site#SEARCH_ALL}
     */
    private static final String REQUEST_BKEY_SEARCH_SITES = "SearchSites";
    /** which fields to update and how. */
    private final Map<String, Fields.FieldUsage> mFieldUsages = new LinkedHashMap<>();
    /** where to look. */
    private int mSearchSites = Site.SEARCH_ALL;
    /** 0 for all books, or a specific book. */
    private long mBookId;

    /** the ViewGroup where we'll add the list of fields. */
    private ViewGroup mListContainer;

    /** senderId of the update task. */
    private long mUpdateSenderId;

    /** this is where the results can be 'consumed' before finishing this activity. */
    private final ManagedTask.ManagedTaskListener mSearchTaskListener =
            new ManagedTask.ManagedTaskListener() {
                @Override
                public void onTaskFinished(@NonNull final ManagedTask task) {
                    mUpdateSenderId = 0;
                    Intent data = new Intent()
                            .putExtra(UniqueId.BKEY_CANCELED, task.isCancelled())
                            // 0 if we did 'all books' or the id of the (hopefully) updated book.
                            .putExtra(DBDefinitions.KEY_ID, mBookId);
                    if (mBookId == 0) {
                        // task cancelled does not mean that nothing was done.
                        // Books *will* be updated until the cancelling happened
                        setResult(Activity.RESULT_OK, data);
                    } else {
                        // but if a single book was cancelled, flag that up
                        setResult(Activity.RESULT_CANCELED, data);
                    }
                    finish();
                }
            };
    /** display reminder only. */
    private String mAuthorFormatted;
    /** display reminder only. */
    private String mTitle;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_update_from_internet;
    }

    private void readArgs(@NonNull final Bundle args) {
        mSearchSites = args.getInt(REQUEST_BKEY_SEARCH_SITES, Site.SEARCH_ALL);
        mBookId = args.getLong(DBDefinitions.KEY_ID, 0L);
        if (mBookId > 0) {
            mAuthorFormatted = args.getString(DBDefinitions.KEY_AUTHOR_FORMATTED);
            mTitle = args.getString(DBDefinitions.KEY_TITLE);
        }
    }

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                readArgs(extras);
            }
        } else {
            readArgs(savedInstanceState);
        }

        setTitle(R.string.lbl_update_fields_to_update);

        // we're only requesting ONE book to be updated.
        if (mBookId > 0) {
            TextView authorView = findViewById(R.id.author);
            authorView.setText(mAuthorFormatted);
            TextView titleView = findViewById(R.id.title);
            titleView.setText(mTitle);
            findViewById(R.id.title).setVisibility(View.VISIBLE);
            findViewById(R.id.author).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.title).setVisibility(View.GONE);
            findViewById(R.id.author).setVisibility(View.GONE);
        }

        if ((mSearchSites & Site.SEARCH_LIBRARY_THING) != 0) {
            LibraryThingManager.showLtAlertIfNecessary(this, false,
                                                       "update_from_internet");
        }

        mListContainer = findViewById(R.id.manage_fields_scrollview);

        initFields();
        populateFields();

        // start the update
        findViewById(R.id.confirm).setOnClickListener(v -> handleConfirm());

        // don't start update, just quit.
        findViewById(R.id.cancel).setOnClickListener(v -> finish());
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(REQUEST_BKEY_SEARCH_SITES, mSearchSites);
        outState.putLong(DBDefinitions.KEY_ID, mBookId);
        outState.putString(DBDefinitions.KEY_AUTHOR_FORMATTED, mAuthorFormatted);
        outState.putString(DBDefinitions.KEY_TITLE, mTitle);
    }

    /**
     * Entries are displayed in the order they are added here.
     */
    private void initFields() {
        addIfVisible(UniqueId.BKEY_AUTHOR_ARRAY, DBDefinitions.KEY_AUTHOR,
                     R.string.lbl_author, Fields.FieldUsage.Usage.AddExtra, true);
        addIfVisible(DBDefinitions.KEY_TITLE,
                     R.string.lbl_title, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_ISBN,
                     R.string.lbl_isbn, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.BKEY_COVER_IMAGE,
                     R.string.lbl_cover, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(UniqueId.BKEY_SERIES_ARRAY, DBDefinitions.KEY_SERIES,
                     R.string.lbl_series, Fields.FieldUsage.Usage.AddExtra, true);
        addIfVisible(UniqueId.BKEY_TOC_ENTRY_ARRAY, DBDefinitions.KEY_TOC_BITMASK,
                     R.string.lbl_table_of_content, Fields.FieldUsage.Usage.AddExtra, true);
        addIfVisible(DBDefinitions.KEY_PUBLISHER,
                     R.string.lbl_publisher, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_DATE_PUBLISHED,
                     R.string.lbl_date_published, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_DATE_FIRST_PUBLISHED,
                     R.string.lbl_first_publication, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_DESCRIPTION,
                     R.string.lbl_description, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_PAGES,
                     R.string.lbl_pages, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_PRICE_LISTED,
                     R.string.lbl_price_listed, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_FORMAT,
                     R.string.lbl_format, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_GENRE,
                     R.string.lbl_genre, Fields.FieldUsage.Usage.CopyIfBlank, false);
        addIfVisible(DBDefinitions.KEY_LANGUAGE,
                     R.string.lbl_language, Fields.FieldUsage.Usage.CopyIfBlank, false);
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param fieldId      name to use in FieldUsages + check for visibility
     * @param nameStringId of field label string
     * @param defaultUsage Usage to apply.
     * @param isList       if the field is a list to which we can append to
     */
    private void addIfVisible(@NonNull final String fieldId,
                              @StringRes final int nameStringId,
                              @NonNull final Fields.FieldUsage.Usage defaultUsage,
                              final boolean isList) {

        if (Fields.isUsed(fieldId)) {
            mFieldUsages.put(fieldId,
                             new Fields.FieldUsage(fieldId, nameStringId, defaultUsage, isList));
        }
    }

    /**
     * Add a FieldUsage if the specified field has not been hidden by the user.
     *
     * @param fieldId      name to use in FieldUsages
     * @param visField     Field name to check for visibility.
     * @param nameStringId of field label string
     * @param defaultUsage Usage to apply.
     * @param isList       if the field is a list to which we can append to
     */
    private void addIfVisible(@NonNull final String fieldId,
                              @NonNull final String visField,
                              @StringRes final int nameStringId,
                              @NonNull final Fields.FieldUsage.Usage defaultUsage,
                              final boolean isList) {

        if (Fields.isUsed(visField)) {
            mFieldUsages.put(fieldId,
                             new Fields.FieldUsage(fieldId, nameStringId, defaultUsage, isList));
        }
    }

    /**
     * Display the list of fields, dynamically adding them in a loop.
     */
    private void populateFields() {

        for (Fields.FieldUsage usage : mFieldUsages.values()) {
            View row = getLayoutInflater().inflate(R.layout.row_update_from_internet,
                                                   mListContainer, false);

            TextView fieldLabel = row.findViewById(R.id.field);
            fieldLabel.setText(usage.getLabel(this));

            CompoundButton cb = row.findViewById(R.id.usage);
            cb.setChecked(usage.isSelected());
            cb.setText(usage.getUsageInfo(UpdateFieldsFromInternetActivity.this));
            cb.setOnClickListener(v -> {
                // ENHANCE: The check is really a FOUR-state.
                final CompoundButton cb1 = (CompoundButton) v;
                final Fields.FieldUsage fieldUsage = (Fields.FieldUsage) cb1.getTag();
                fieldUsage.nextState();
                cb1.setChecked(fieldUsage.isSelected());
                cb1.setText(fieldUsage.getUsageInfo(UpdateFieldsFromInternetActivity.this));
            });

            cb.setTag(usage);
            mListContainer.addView(row);
        }
    }

    /**
     * After confirmation, start the process.
     */
    private void handleConfirm() {
        // sanity check
        if (!hasSelections()) {
            UserMessage.showUserMessage(UpdateFieldsFromInternetActivity.this,
                                        R.string.warning_select_min_1_field);
            return;
        }

        // If the user has selected thumbnails, check if they want to download ALL
        final Fields.FieldUsage coversWanted = mFieldUsages.get(UniqueId.BKEY_COVER_IMAGE);
        // but don't ask if its a single book only; just download it.
        //noinspection ConstantConditions
        if (mBookId == 0 && coversWanted.isSelected()) {
            // Verify - this can be a dangerous operation
            final AlertDialog dialog =
                    new AlertDialog.Builder(UpdateFieldsFromInternetActivity.this)
                            .setMessage(R.string.confirm_overwrite_thumbnail)
                            .setTitle(R.string.lbl_update_fields)
                            .setIconAttribute(android.R.attr.alertDialogIcon)
                            .create();

            dialog.setButton(
                    AlertDialog.BUTTON_POSITIVE, getString(R.string.yes),
                    (d, which) -> {
                        coversWanted.usage = Fields.FieldUsage.Usage.Overwrite;
                        startUpdate(mBookId);
                    });
            dialog.setButton(
                    AlertDialog.BUTTON_NEGATIVE, getString(android.R.string.cancel),
                    (d, which) -> {
                        //do nothing
                    });
            dialog.setButton(
                    AlertDialog.BUTTON_NEUTRAL, getString(R.string.no),
                    (d, which) -> {
                        coversWanted.usage = Fields.FieldUsage.Usage.CopyIfBlank;
                        startUpdate(mBookId);
                    });
            dialog.show();
        } else {
            startUpdate(mBookId);
        }
    }

    @Override
    @CallSuper
    public boolean onCreateOptionsMenu(@NonNull final Menu menu) {
        menu.add(Menu.NONE, R.id.MENU_PREFS_SEARCH_SITES, 0, R.string.lbl_search_sites)
            .setIcon(R.drawable.ic_search)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (item.getItemId()) {
            case R.id.MENU_PREFS_SEARCH_SITES:
                Intent intent = new Intent(this, SearchAdminActivity.class)
                        .putExtra(SearchAdminActivity.REQUEST_BKEY_TAB,
                                  SearchAdminActivity.TAB_ORDER);
                startActivityForResult(intent, REQ_PREFERRED_SEARCH_SITES);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    @CallSuper
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            // no changes committed, we got data to use temporarily
            case REQ_PREFERRED_SEARCH_SITES:
                if (resultCode == Activity.RESULT_OK) {
                    //noinspection ConstantConditions
                    mSearchSites = data.getIntExtra(SearchAdminActivity.RESULT_SEARCH_SITES,
                                                    mSearchSites);
                }
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }

        Tracker.exitOnActivityResult(this);
    }

    /**
     * Count the checked fields, we need at least one selected to make sense.
     *
     * @return {@code true} if at least one field is selected
     */
    private boolean hasSelections() {
        int nChildren = mListContainer.getChildCount();
        for (int i = 0; i < nChildren; i++) {
            View view = mListContainer.getChildAt(i);
            CompoundButton cb = view.findViewById(R.id.usage);
            if (cb != null) {
                Fields.FieldUsage usage = (Fields.FieldUsage) cb.getTag();
                if (usage.isSelected()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * TODO: allow the use of {@link UpdateFieldsFromInternetTask#setBookId(List)}.
     *
     * @param bookId 0 for all books, or a valid book id for one book
     */
    private void startUpdate(final long bookId) {
        UpdateFieldsFromInternetTask updateTask =
                new UpdateFieldsFromInternetTask(getTaskManager(),
                                                 mSearchSites,
                                                 mFieldUsages,
                                                 mSearchTaskListener);
        if (bookId > 0) {
            updateTask.setBookId(bookId);
        }

        mUpdateSenderId = updateTask.getSenderId();
        UpdateFieldsFromInternetTask.getMessageSwitch()
                                    .addListener(mUpdateSenderId, mSearchTaskListener, false);
        updateTask.start();
    }

    @Override
    @CallSuper
    protected void onPause() {
        if (mUpdateSenderId != 0) {
            UpdateFieldsFromInternetTask.getMessageSwitch()
                                        .removeListener(mUpdateSenderId, mSearchTaskListener);
        }
        super.onPause();
    }

    @Override
    @CallSuper
    protected void onResume() {
        super.onResume();
        if (mUpdateSenderId != 0) {
            UpdateFieldsFromInternetTask.getMessageSwitch()
                                        .addListener(mUpdateSenderId, mSearchTaskListener, true);
        }
    }
}
