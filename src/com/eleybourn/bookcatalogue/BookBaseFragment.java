/*
 * @copyright 2013 Philip Warner
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.eleybourn.bookcatalogue.baseactivity.BaseActivity;
import com.eleybourn.bookcatalogue.database.DBA;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.DataViewer;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.datamanager.Fields.AfterFieldChangeListener;
import com.eleybourn.bookcatalogue.datamanager.Fields.Field;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.SimpleDialog;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListEditorDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.editordialog.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.editordialog.TextFieldEditorDialogFragment;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.BookManager;

/**
 * Based class for {@link BookFragment} and {@link EditBookBaseFragment}.
 * <p>
 * This class supports the loading of a book. See {@link #loadFieldsFrom}.
 *
 * @author pjw
 */
public abstract class BookBaseFragment
        extends Fragment
        implements DataViewer {

    /** Database instance. */
    protected DBA mDb;
    /** */
    Fields mFields;
    /** A link to the Activity, cached to avoid requireActivity() all over the place. */
    private BaseActivity mActivity;

    private void setActivityTitle(@NonNull final Book book) {
        ActionBar actionBar = mActivity.getSupportActionBar();
        if (actionBar != null) {
            if (book.getId() > 0) {
                // an existing book
                actionBar.setTitle(book.getString(DBDefinitions.KEY_TITLE));
                actionBar.setSubtitle(book.getAuthorTextShort(mActivity));
            } else {
                // new book
                actionBar.setTitle(R.string.title_add_book);
                actionBar.setSubtitle(null);
            }
        }
    }

    /* ------------------------------------------------------------------------------------------ */

    /**
     * @return the BookManager which is (should be) the only way to get/set Book properties.
     */
    protected abstract BookManager getBookManager();

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment startup">

    @Override
    @CallSuper
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // make sure {@link #onCreateOptionsMenu} is called
        setHasOptionsMenu(true);
    }

    /**
     * If the child class is a {@link BookManager} then load the {@link Book}.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        mActivity = (BaseActivity) requireActivity();
        super.onActivityCreated(savedInstanceState);

        mDb = new DBA(mActivity);

        if (this instanceof BookManager) {
            Bundle args = savedInstanceState == null ? requireArguments() : savedInstanceState;
            Bundle bookData = args.getBundle(UniqueId.BKEY_BOOK_DATA);
            Book book;
            if (bookData != null) {
                // if we have a populated bundle, use that.
                book = new Book(bookData);
            } else {
                // otherwise, try to load from the database. If that fails, it's a new book.
                long bookId = args.getLong(DBDefinitions.KEY_ID, 0);
                book = new Book(bookId, mDb);
            }
            getBookManager().setBook(book);
        }

        initFields();
    }

    /**
     * Add any {@link Field} we need to {@link Fields}.
     * <p>
     * Set corresponding validators/formatters (if any)
     * Set onClickListener etc...
     * <p>
     * Note this is NOT where we set values.
     * <p>
     * Override as needed, but call super FIRST
     */
    @CallSuper
    protected void initFields() {
        mFields = new Fields(this);
    }

    /**
     * Trigger the Fragment to load it's Fields from the Book.
     * <p>
     * {@inheritDoc}
     */
    @Override
    @CallSuper
    public void onResume() {
        Tracker.enterOnResume(this);
        super.onResume();

        populateFieldsFromBook();
        Tracker.exitOnResume(this);
    }

    /**
     * Populate all Fields with the data from the Book.
     * <p>
     * Used as normal when the fragment loads, but also when the user flings left/right
     * through the flattened book list. The latter does a load of the book followed by a call
     * here to re-populate all fields on the fragment.
     */
    final void populateFieldsFromBook() {
        // load the book, while disabling the AfterFieldChangeListener
        mFields.setAfterFieldChangeListener(null);
        Book book = getBookManager().getBook();
        loadFieldsFrom(book);
        mFields.setAfterFieldChangeListener(new AfterFieldChangeListener() {
            private static final long serialVersionUID = -4893882810164263510L;

            @Override
            public void afterFieldChange(@NonNull final Field field,
                                         @Nullable final String newValue) {
                getBookManager().setDirty(true);
            }
        });
    }

    /**
     * This is 'final' because we want inheritors to implement {@link #onLoadFieldsFromBook}.
     * <p>
     * Load the data while preserving the isDirty() status.
     */
    @Override
    public final <T extends DataManager> void loadFieldsFrom(@NonNull final T dataManager) {
        final boolean wasDirty = getBookManager().isDirty();
        onLoadFieldsFromBook((Book) dataManager, false);
        getBookManager().setDirty(wasDirty);

        setActivityTitle((Book) dataManager);
    }

    /**
     * Default implementation of code to load the Book object.
     * Override as needed, calling super as the first step.
     * <p>
     * This is where you should populate all the fields with the values coming from the book.
     * This base class manages all the actual fields, but 'special' fields can/should be handled
     * in overrides.
     *
     * @param book       to load from
     * @param setAllFrom flag indicating {@link Fields#setAllFrom(DataManager)}
     *                   has already been called or not
     */
    @CallSuper
    protected void onLoadFieldsFromBook(@NonNull final Book book,
                                        final boolean setAllFrom) {
        Tracker.enterOnLoadFieldsFromBook(this, book.getId());
        if (!setAllFrom) {
            mFields.setAllFrom(book);
        }
        Tracker.exitOnLoadFieldsFromBook(this, book.getId());
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Fragment shutdown">

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (this instanceof BookManager) {
            outState.putLong(DBDefinitions.KEY_ID, getBookManager().getBook().getId());
            outState.putBundle(UniqueId.BKEY_BOOK_DATA, getBookManager().getBook().getRawData());
        }
    }

    @Override
    @CallSuper
    public void onDestroy() {
        if (mDb != null) {
            mDb.close();
        }
        super.onDestroy();
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Menu handlers">

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {

        /*
         * MENU_GROUP_BOOK is shown only when the book is persisted in the database.
         */
        menu.add(R.id.MENU_GROUP_BOOK, R.id.MENU_BOOK_DELETE, 0, R.string.menu_delete_book)
            .setIcon(R.drawable.ic_delete);
        menu.add(R.id.MENU_GROUP_BOOK, R.id.MENU_BOOK_DUPLICATE, 0, R.string.menu_duplicate_book)
            .setIcon(R.drawable.ic_content_copy);
        menu.add(R.id.MENU_GROUP_BOOK,
                 R.id.MENU_BOOK_UPDATE_FROM_INTERNET, 0, R.string.menu_internet_update_fields)
            .setIcon(R.drawable.ic_search);
        menu.add(R.id.MENU_GROUP_BOOK, R.id.MENU_SHARE, 0, R.string.menu_share_this)
            .setIcon(R.drawable.ic_share);

        if (Fields.isVisible(DBDefinitions.KEY_LOANEE)) {
            menu.add(R.id.MENU_BOOK_EDIT_LOAN,
                     R.id.MENU_BOOK_EDIT_LOAN, 0, R.string.menu_loan_lend_book);
            menu.add(R.id.MENU_BOOK_LOAN_RETURNED,
                     R.id.MENU_BOOK_LOAN_RETURNED, 0, R.string.menu_loan_return_book);
        }
        MenuHandler.addAmazonSearchSubMenu(menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * Set visibility of menu items as appropriate.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        Book book = getBookManager().getBook();

        boolean bookExists = book.getId() != 0;
        menu.setGroupVisible(R.id.MENU_GROUP_BOOK, bookExists);

        if (Fields.isVisible(DBDefinitions.KEY_LOANEE)) {
            boolean isAvailable = mDb.getLoaneeByBookId(book.getId()) == null;
            menu.setGroupVisible(R.id.MENU_BOOK_EDIT_LOAN, bookExists && isAvailable);
            menu.setGroupVisible(R.id.MENU_BOOK_LOAN_RETURNED, bookExists && !isAvailable);
        }

        MenuHandler.prepareAmazonSearchSubMenu(menu, book);
    }

    @Override
    @CallSuper
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        final Book book = getBookManager().getBook();

        switch (item.getItemId()) {
            case R.id.MENU_BOOK_DELETE:
                StandardDialogs.deleteBookAlert(mActivity, mDb, book.getId(), () -> {
                    mActivity.setResult(UniqueId.ACTIVITY_RESULT_DELETED_SOMETHING);
                    mActivity.finish();
                });
                return true;

            case R.id.MENU_BOOK_DUPLICATE:
                Intent intent = new Intent(mActivity, EditBookActivity.class)
                        .putExtra(UniqueId.BKEY_BOOK_DATA, book.duplicate());
                startActivityForResult(intent, UniqueId.REQ_BOOK_DUPLICATE);
                return true;

            case R.id.MENU_BOOK_UPDATE_FROM_INTERNET:
                Intent intentUpdateFields =
                        new Intent(mActivity, UpdateFieldsFromInternetActivity.class)
                                .putExtra(DBDefinitions.KEY_ID, book.getId())
                                .putExtra(DBDefinitions.KEY_TITLE,
                                          book.getString(DBDefinitions.KEY_TITLE))
                                .putExtra(DBDefinitions.KEY_AUTHOR_FORMATTED,
                                          book.getString(DBDefinitions.KEY_AUTHOR_FORMATTED));
                startActivityForResult(intentUpdateFields,
                                       UniqueId.REQ_UPDATE_BOOK_FIELDS_FROM_INTERNET);
                return true;

            case R.id.MENU_SHARE:
                startActivity(Intent.createChooser(book.getShareBookIntent(mActivity),
                                                   getString(R.string.menu_share_this)));
                return true;

            default:
                if (MenuHandler.handleAmazonSearchSubMenu(mActivity, item, book)) {
                    return true;
                }
                return super.onOptionsItemSelected(item);
        }
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    //<editor-fold desc="Field editors">

    /**
     * The 'drop-down' menu button next to an AutoCompleteTextView field.
     * Allows us to show a {@link SimpleDialog#selectFieldDialog} with a list of strings
     * to choose from.
     *
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param fieldButtonId field/button to bind the OnClickListener to (can be same as fieldId)
     * @param list          list of strings to choose from.
     */
    void initValuePicker(@NonNull final Field field,
                         @StringRes final int dialogTitleId,
                         @IdRes final int fieldButtonId,
                         @NonNull final List<String> list) {
        // only bother when visible
        if (!field.isVisible()) {
            return;
        }

        // Get the list to use in the AutoCompleteTextView
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(mActivity, android.R.layout.simple_dropdown_item_1line, list);
        mFields.setAdapter(field.id, adapter);

        // Get the drop-down button for the list and setup dialog
        requireView().findViewById(fieldButtonId).setOnClickListener(
                v -> SimpleDialog.selectFieldDialog(mActivity.getLayoutInflater(),
                                                    getString(dialogTitleId), field, list));
    }

    /**
     * bind a field (button) to bring up a text editor in an overlapping dialog.
     * <p>
     * TODO: no in use right now (remove?) / cancel/ok buttons are hidden by soft keyboard.
     *
     * @param callerTag     the fragment class that is calling the editor
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param fieldButtonId field/button to bind the OnClickListener to (can be same as field.id)
     * @param multiLine     <tt>true</tt> if the dialog box should offer a multi-line input.
     */
    @SuppressWarnings("SameParameterValue")
    void initTextFieldEditor(@NonNull final String callerTag,
                             @NonNull final Field field,
                             @StringRes final int dialogTitleId,
                             @IdRes final int fieldButtonId,
                             final boolean multiLine) {
        // only bother when visible
        if (!field.isVisible()) {
            return;
        }

        requireView().findViewById(fieldButtonId).setOnClickListener(v -> {
            FragmentManager fm = requireFragmentManager();
            if (fm.findFragmentByTag(TextFieldEditorDialogFragment.TAG) == null) {
                TextFieldEditorDialogFragment
                        .newInstance(callerTag, field, dialogTitleId, multiLine)
                        .show(fm, TextFieldEditorDialogFragment.TAG);
            }
        });
    }

    /**
     * @param callerTag     the fragment class that is calling the editor
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param todayIfNone   if true, and if the field was empty, pre-populate with today's date
     */
    void initPartialDatePicker(@NonNull final String callerTag,
                               @NonNull final Field field,
                               @StringRes final int dialogTitleId,
                               final boolean todayIfNone) {
        // only bother when visible
        if (!field.isVisible()) {
            return;
        }

        field.getView().setOnClickListener(v -> {
            FragmentManager fm = requireFragmentManager();
            if (fm.findFragmentByTag(PartialDatePickerDialogFragment.TAG) == null) {
                PartialDatePickerDialogFragment
                        .newInstance(callerTag, field, dialogTitleId, todayIfNone)
                        .show(fm, PartialDatePickerDialogFragment.TAG);
            }
        });
    }

    /**
     * @param callerTag     the fragment class that is calling the editor
     * @param field         {@link Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param listGetter    {@link CheckListEditorDialogFragment.CheckListEditorListGetter <T>} interface to get the *current* list
     * @param <T>           type of the {@link CheckListItem}
     */
    <T> void initCheckListEditor(@NonNull final String callerTag,
                                 @NonNull final Field field,
                                 @StringRes final int dialogTitleId,
                                 @NonNull final CheckListEditorDialogFragment.CheckListEditorListGetter<T> listGetter) {
        // only bother when visible
        if (!field.isVisible()) {
            return;
        }

        field.getView().setOnClickListener(v -> {
            FragmentManager fm = requireFragmentManager();
            if (fm.findFragmentByTag(CheckListEditorDialogFragment.TAG) == null) {
                CheckListEditorDialogFragment
                        .newInstance(callerTag, field, dialogTitleId, listGetter)
                        .show(fm, CheckListEditorDialogFragment.TAG);
            }
        });
    }

    //</editor-fold>

    /* ------------------------------------------------------------------------------------------ */

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        Tracker.enterOnActivityResult(this, requestCode, resultCode, data);

        switch (requestCode) {
            case UniqueId.REQ_UPDATE_BOOK_FIELDS_FROM_INTERNET:
                if (resultCode == Activity.RESULT_OK) {
                    Objects.requireNonNull(data);
                    long bookId = data.getLongExtra(DBDefinitions.KEY_ID, 0);
                    if (bookId > 0) {
                        // replace current book with the updated one,
                        // ENHANCE: merge if in edit mode.
                        Book book = new Book(bookId, mDb);
                        getBookManager().setBook(book);
                        populateFieldsFromBook();
                    } else {
                        boolean wasCancelled =
                                data.getBooleanExtra(UniqueId.BKEY_CANCELED, false);
                        Logger.info(this, "onActivityResult", "wasCancelled= " + wasCancelled);
                    }
                }
                break;

            default:
                // lowest level of our Fragment, see if we missed anything
                Logger.info(this, "BookBaseFragment.onActivityResult",
                            "NOT HANDLED:"
                                    + " requestCode=" + requestCode + ", resultCode=" + resultCode);
                super.onActivityResult(requestCode, resultCode, data);
                break;

        }

        Tracker.exitOnActivityResult(this);
    }

    /**
     * Hides unused fields if they have no useful data.
     * Should normally be called at the *end* of {@link #onLoadFieldsFromBook}
     * <p>
     * Authors & Title are always visible as they are required fields.
     * <p>
     * Series is done in:
     * {@link EditBookFieldsFragment#populateSeriesListField}
     * {@link BookFragment#populateSeriesListField}
     * <p>
     * Special fields not checked here:
     * - toc
     * - edition
     *
     * @param hideIfEmpty set to <tt>true</tt> when displaying; <tt>false</tt> when editing.
     */
    void showHideFields(final boolean hideIfEmpty) {
        mFields.setVisibility();

        // actual book
        showHide(hideIfEmpty, R.id.coverImage);
        showHide(hideIfEmpty, R.id.isbn, R.id.lbl_isbn);
        showHide(hideIfEmpty, R.id.description, R.id.lbl_description);

        showHide(hideIfEmpty, R.id.pages, R.id.lbl_pages);
        showHide(hideIfEmpty, R.id.format, R.id.lbl_format);
        // Hide the baseline if both fields are gone.
        setVisibilityGoneOr(R.id.lbl_pages_baseline, View.INVISIBLE, R.id.pages, R.id.format);

        showHide(hideIfEmpty, R.id.genre, R.id.lbl_genre);
        showHide(hideIfEmpty, R.id.language, R.id.lbl_language);
        showHide(hideIfEmpty, R.id.first_publication, R.id.lbl_first_publication);
//        showHide(hideIfEmpty, R.id.toc, R.id.row_toc);

        showHide(hideIfEmpty, R.id.publisher);
        showHide(hideIfEmpty, R.id.date_published);
        // Hide the baseline if both fields are gone.
        setVisibilityGoneOr(R.id.lbl_publisher_baseline, View.INVISIBLE,
                            R.id.publisher, R.id.date_published);

        // Hide the label if none of the publishing fields are shown.
        setVisibilityGoneOr(R.id.lbl_publishing, View.VISIBLE,
                            R.id.publisher, R.id.date_published,
                            R.id.price_listed, R.id.first_publication);

        showHide(hideIfEmpty, R.id.price_listed, R.id.price_listed_currency, R.id.lbl_price_listed);
        // Hide the baseline if price listed fields are gone.
        setVisibilityGoneOr(R.id.lbl_price_listed_baseline, View.INVISIBLE, R.id.price_listed);

        // personal fields
        showHide(hideIfEmpty, R.id.bookshelves, R.id.name, R.id.lbl_bookshelves);
        showHide(hideIfEmpty, R.id.read);

        //showHide(hideIfEmpty, R.id.edition, R.id.lbl_edition);

        showHide(hideIfEmpty, R.id.notes);
        showHide(hideIfEmpty, R.id.location, R.id.lbl_location, R.id.lbl_location_long);
        showHide(hideIfEmpty, R.id.date_acquired, R.id.lbl_date_acquired);

        showHide(hideIfEmpty, R.id.price_paid, R.id.price_paid_currency, R.id.lbl_price_paid);
        // Hide the baseline if both price paid fields are gone.
        setVisibilityGoneOr(R.id.lbl_price_paid_baseline, View.INVISIBLE, R.id.price_paid);

        showHide(hideIfEmpty, R.id.read_start, R.id.lbl_read_start);
        showHide(hideIfEmpty, R.id.read_end, R.id.lbl_read_end);
        // Hide the baseline if both fields are gone.
        setVisibilityGoneOr(R.id.lbl_read_start_end_baseline, View.INVISIBLE,
                            R.id.lbl_read_start, R.id.lbl_read_end);
        // Hide the baseline if both fields are gone.
        setVisibilityGoneOr(R.id.read_start_end_baseline, View.INVISIBLE,
                            R.id.lbl_read_start_end_baseline);

        showHide(hideIfEmpty, R.id.signed, R.id.lbl_signed);
        showHide(hideIfEmpty, R.id.rating, R.id.lbl_rating);

        showHide(hideIfEmpty, R.id.loaned_to);

        //NEWKIND: new fields
    }

    /**
     * Text fields:
     * Hide text field (View.GONE) if it does not have any useful data.
     * Don't show a field if it is already hidden (assumed by user preference)
     * <p>
     * ImageView:
     * use the visibility status of the ImageView itself for the relatedFields
     *
     * @param hideIfEmpty   hide if empty
     * @param fieldId       layout resource id of the field
     * @param relatedFields list of fields whose visibility will also be set based
     *                      on the first field
     */
    private void showHide(final boolean hideIfEmpty,
                          @IdRes final int fieldId,
                          @NonNull @IdRes final int... relatedFields) {
        final View view = requireView().findViewById(fieldId);
        if (view != null) {
            int visibility = view.getVisibility();
            if (hideIfEmpty) {
                if (visibility != View.GONE) {
                    // Determine if we should hide it
                    if (view instanceof Checkable) {
                        visibility = ((Checkable) view).isChecked() ? View.VISIBLE : View.GONE;
                        view.setVisibility(visibility);
                    } else if (view instanceof ImageView) {
                        // skip.
                    } else {
                        // all other fields.
                        final String value = mFields.getField(fieldId).getValue().toString().trim();
                        visibility = !value.isEmpty() ? View.VISIBLE : View.GONE;
                        view.setVisibility(visibility);
                    }
                }
            }

            setVisibility(visibility, relatedFields);
        }
    }

    /**
     * If all 'fields' are View.GONE, set 'fieldToSet' to View.GONE as well.
     * Otherwise, set 'fieldToSet' to the desired visibility.
     *
     * @param fieldToSet field to set
     * @param visibility to use for the fieldToSet
     * @param fields     to test
     */
    private void setVisibilityGoneOr(@IdRes final int fieldToSet,
                                     final int visibility,
                                     @NonNull @IdRes final int... fields) {
        final View baselineField = requireView().findViewById(fieldToSet);
        if (baselineField != null) {
            baselineField.setVisibility(allFieldsAreGone(fields) ? View.GONE : visibility);
        }
    }

    /**
     * @param fields to check
     *
     * @return <tt>true</tt> if all fields have visibility == View.GONE
     */
    private boolean allFieldsAreGone(@IdRes @NonNull final int[] fields) {
        boolean isGone = true;
        for (int fieldId : fields) {
            View field = requireView().findViewById(fieldId);
            if (field != null) {
                // all fields must be gone to result into isGone==true
                isGone = isGone && (field.getVisibility() == View.GONE);
            }
        }
        return isGone;
    }

    /**
     * Set the visibility for a list of fields.
     */
    private void setVisibility(final int visibility,
                               @NonNull @IdRes final int... fields) {
        for (int fieldId : fields) {
            View field = requireView().findViewById(fieldId);
            if (field != null) {
                field.setVisibility(visibility);
            }
        }
    }

    static final class ViewUtils {

        private ViewUtils() {
        }

        /**
         * Gets the total number of rows from the adapter, then use that to set
         * the ListView to the full height so all rows are visible (no scrolling).
         * <p>
         * Does nothing if the adapter is null, or if the view is not visible.
         */
        static void adjustListViewHeightBasedOnChildren(@NonNull final ListView listView) {
            ListAdapter adapter = listView.getAdapter();
            if (adapter == null || listView.getVisibility() != View.VISIBLE) {
                return;
            }

            int totalHeight = 0;
            for (int i = 0; i < adapter.getCount(); i++) {
                View listItem = adapter.getView(i, null, listView);
                listItem.measure(0, 0);
                totalHeight += listItem.getMeasuredHeight();
            }

            ViewGroup.LayoutParams layoutParams = listView.getLayoutParams();
            layoutParams.height = totalHeight
                    + (listView.getDividerHeight() * (adapter.getCount()));
            listView.setLayoutParams(layoutParams);
            listView.requestLayout();
        }

        /**
         * Ensure that next up/down/left/right View is visible for all
         * sub-views of the passed view.
         */
        static void fixFocusSettings(@NonNull final View root) {
            try {
                final INextView getDown = new INextView() {
                    @Override
                    public int getNext(@NonNull final View v) {
                        return v.getNextFocusDownId();
                    }

                    @Override
                    public void setNext(@NonNull final View v,
                                        @IdRes final int id) {
                        v.setNextFocusDownId(id);
                    }
                };
                final INextView getUp = new INextView() {
                    @Override
                    public int getNext(@NonNull final View v) {
                        return v.getNextFocusUpId();
                    }

                    @Override
                    public void setNext(@NonNull final View v,
                                        @IdRes final int id) {
                        v.setNextFocusUpId(id);
                    }
                };
                final INextView getLeft = new INextView() {
                    @Override
                    public int getNext(@NonNull final View v) {
                        return v.getNextFocusLeftId();
                    }

                    @Override
                    public void setNext(@NonNull final View v,
                                        @IdRes final int id) {
                        v.setNextFocusLeftId(id);
                    }
                };
                final INextView getRight = new INextView() {
                    @Override
                    public int getNext(@NonNull final View v) {
                        return v.getNextFocusRightId();
                    }

                    @Override
                    public void setNext(@NonNull final View v,
                                        @IdRes final int id) {
                        v.setNextFocusRightId(id);
                    }
                };

                @SuppressLint("UseSparseArrays")
                Map<Integer, View> vh = new HashMap<>();
                getViews(root, vh);

                for (Map.Entry<Integer, View> ve : vh.entrySet()) {
                    final View v = ve.getValue();
                    if (v.getVisibility() == View.VISIBLE) {
                        fixNextView(vh, v, getDown);
                        fixNextView(vh, v, getUp);
                        fixNextView(vh, v, getLeft);
                        fixNextView(vh, v, getRight);
                    }
                }
            } catch (RuntimeException e) {
                // Log, but ignore. This is a non-critical feature that prevents crashes
                // when the 'next' key is pressed and some views have been hidden.
                Logger.error(e);
            }
        }

        /**
         * Passed a collection of views, a specific View and an INextView, ensure that the
         * currently set 'next' view is actually a visible view, updating it if necessary.
         *
         * @param list   Collection of all views
         * @param view   View to check
         * @param getter Methods to get/set 'next' view
         */
        private static void fixNextView(@NonNull final Map<Integer, View> list,
                                        @NonNull final View view,
                                        @NonNull final INextView getter) {
            int nextId = getter.getNext(view);
            if (nextId != View.NO_ID) {
                int actualNextId = getNextView(list, nextId, getter);
                if (actualNextId != nextId) {
                    getter.setNext(view, actualNextId);
                }
            }
        }

        /**
         * Passed a collection of views, a specific view and an INextView object find the
         * first VISIBLE object returned by INextView when called recursively.
         *
         * @param list   Collection of all views
         * @param nextId ID of 'next' view to get
         * @param getter Interface to lookup 'next' ID given a view
         *
         * @return ID if first visible 'next' view
         */
        private static int getNextView(@NonNull final Map<Integer, View> list,
                                       final int nextId,
                                       @NonNull final INextView getter) {
            final View v = list.get(nextId);
            if (v == null) {
                return View.NO_ID;
            }

            if (v.getVisibility() == View.VISIBLE) {
                return nextId;
            }

            return getNextView(list, getter.getNext(v), getter);
        }

        /**
         * Passed a parent view, add it and all children view (if any) to the passed collection.
         *
         * @param parent Parent View
         * @param list   Collection
         */
        private static void getViews(@NonNull final View parent,
                                     @NonNull final Map<Integer, View> list) {
            // Get the view ID and add it to collection if not already present.
            @IdRes
            final int id = parent.getId();
            if (id != View.NO_ID && !list.containsKey(id)) {
                list.put(id, parent);
            }
            // If it's a ViewGroup, then process children recursively.
            if (parent instanceof ViewGroup) {
                final ViewGroup g = (ViewGroup) parent;
                final int nChildren = g.getChildCount();
                for (int i = 0; i < nChildren; i++) {
                    getViews(g.getChildAt(i), list);
                }
            }
        }

        /**
         * Dump an entire view hierarchy to the output.
         */
        @SuppressWarnings("unused")
        static void debugDumpViewTree(final int depth,
                                      @NonNull final View view) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < depth * 4; i++) {
                sb.append(' ');
            }
            sb.append(view.getClass().getCanonicalName())
              .append(" (").append(view.getId()).append(')')
              .append(" ->");

            if (view instanceof TextView) {
                String s = ((TextView) view).getText().toString().trim();
                s = s.substring(0, Math.min(s.length(), 20));
                sb.append(s);
            } else {
                Logger.info(BookBaseFragment.class, "debugDumpViewTree", sb.toString());
            }
            if (view instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) view;
                for (int i = 0; i < g.getChildCount(); i++) {
                    debugDumpViewTree(depth + 1, g.getChildAt(i));
                }
            }
        }

        private interface INextView {

            int getNext(@NonNull View v);

            void setNext(@NonNull View v,
                         @IdRes int id);
        }
    }
}
