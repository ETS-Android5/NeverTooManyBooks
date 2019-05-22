package com.eleybourn.bookcatalogue;

import android.os.Bundle;
import android.widget.ArrayAdapter;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;
import java.util.List;

import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.datamanager.DataEditor;
import com.eleybourn.bookcatalogue.datamanager.DataManager;
import com.eleybourn.bookcatalogue.datamanager.Fields;
import com.eleybourn.bookcatalogue.debug.Tracker;
import com.eleybourn.bookcatalogue.dialogs.CheckListDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.CheckListItem;
import com.eleybourn.bookcatalogue.dialogs.FieldPicker;
import com.eleybourn.bookcatalogue.dialogs.PartialDatePickerDialogFragment;
import com.eleybourn.bookcatalogue.dialogs.ValuePicker;
import com.eleybourn.bookcatalogue.entities.Book;
import com.eleybourn.bookcatalogue.entities.Bookshelf;
import com.eleybourn.bookcatalogue.utils.DateUtils;

/**
 * Base class for all fragments that appear in {@link EditBookFragment}.
 * <p>
 * Full list:
 * {@link EditBookFieldsFragment}
 * {@link EditBookPublicationFragment}
 * {@link EditBookNotesFragment}
 * {@link EditBookTocFragment}
 *
 * @param <T> type of the {@link CheckListItem}
 */
public abstract class EditBookBaseFragment<T>
        extends BookBaseFragment
        implements DataEditor {

    private final CheckListDialogFragment.CheckListResultsListener<T>
            mCheckListResultsListener = (destinationFieldId, list) -> {
        Book book = mBookBaseFragmentModel.getBook();

        if (destinationFieldId == R.id.bookshelves) {
            ArrayList<Bookshelf> bsList = (ArrayList<Bookshelf>) list;
            book.putParcelableArrayList(UniqueId.BKEY_BOOKSHELF_ARRAY, bsList);
            mFields.getField(destinationFieldId)
                   .setValue(Bookshelf.toDisplayString(bsList));

        } else if (destinationFieldId == R.id.edition) {
            book.putEditions((ArrayList<Integer>) list);
            mFields.getField(destinationFieldId)
                   .setValue(book.getString(DBDefinitions.KEY_EDITION_BITMASK));
        }
    };

    private final PartialDatePickerDialogFragment.PartialDatePickerResultsListener
            mPartialDatePickerResultsListener = (destinationFieldId, year, month, day) ->
            mFields.getField(destinationFieldId)
                   .setValue(DateUtils.buildPartialDate(year, month, day));

    @Override
    public void onAttachFragment(@NonNull final Fragment childFragment) {

        if (PartialDatePickerDialogFragment.TAG.equals(childFragment.getTag())) {
            ((PartialDatePickerDialogFragment) childFragment)
                    .setListener(mPartialDatePickerResultsListener);

        } else if (CheckListDialogFragment.TAG.equals(childFragment.getTag())) {
            //noinspection unchecked
            ((CheckListDialogFragment<T>) childFragment)
                    .setListener(mCheckListResultsListener);
        }
    }

    @Override
    protected void onLoadFieldsFromBook() {
        super.onLoadFieldsFromBook();

        // new book ?
        if (!mBookBaseFragmentModel.isExistingBook()) {
            populateNewBookFieldsFromBundle(getArguments());
        }
    }

    /**
     * Uses the values from the Bundle to populate the Book but don't overwrite existing values.
     * <p>
     * Can/should be overwritten for handling specific field defaults, e.g. Bookshelf.
     *
     * @param bundle to load values from
     */
    protected void populateNewBookFieldsFromBundle(@Nullable final Bundle bundle) {
        // Check if we have any data, for example from a Search
        if (bundle != null) {
            Bundle values = bundle.getBundle(UniqueId.BKEY_BOOK_DATA);
            if (values != null) {
                // if we do, add if not there yet
                mFields.setAllFrom(values, false);
            }
        }
    }

    /**
     * Trigger the Fragment to save its Fields to the Book.
     * <p>
     * This is always done, even when the user 'cancel's the edit.
     * The latter will then result in a "are you sure" where they can 'cancel the cancel'
     * and continue with all data present.
     * <p>
     * <br>{@inheritDoc}
     */
    @Override
    @CallSuper
    public void onPause() {
        Tracker.enterOnPause(this);
        saveFields();
        super.onPause();
        Tracker.exitOnPause(this);
    }

    /**
     * <br>{@inheritDoc}
     * <br>
     * <p>This is 'final' because we want inheritors to implement {@link #onSaveFieldsToBook}
     */
    @Override
    public final void saveFields() {
        onSaveFieldsToBook();
    }

    /**
     * Default implementation of code to save existing data to the Book object.
     * We simply copy all {@link Fields.Field} into the {@link DataManager} e.g. the {@link Book}
     * <p>
     * Override as needed.
     */
    @CallSuper
    protected void onSaveFieldsToBook() {
        mFields.putAllInto(mBookBaseFragmentModel.getBook());
    }

    /**
     * The 'drop-down' menu button next to an AutoCompleteTextView field.
     * Allows us to show a {@link FieldPicker#FieldPicker} with a list of strings
     * to choose from.
     * <p>
     * Note that a {@link ValuePicker} uses a plain AlertDialog.
     *
     * @param field         {@link Fields.Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param fieldButtonId field/button to bind the PickListener to (can be same as fieldId)
     * @param list          list of strings to choose from.
     */
    void initValuePicker(@NonNull final Fields.Field field,
                         @StringRes final int dialogTitleId,
                         @IdRes final int fieldButtonId,
                         @NonNull final List<String> list) {
        // only bother when it's in use
        if (!field.isUsed()) {
            return;
        }

        // Get the list to use in the AutoCompleteTextView
        @SuppressWarnings("ConstantConditions")
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(getContext(), android.R.layout.simple_dropdown_item_1line, list);
        mFields.setAdapter(field.id, adapter);

        // Get the drop-down button for the list and setup dialog
        //noinspection ConstantConditions
        getView().findViewById(fieldButtonId).setOnClickListener(v -> {
            FieldPicker<String> picker = new FieldPicker<>(getContext(),
                                                           getString(dialogTitleId),
                                                           field, list);
            picker.show();
        });
    }

    /**
     * @param field         {@link Fields.Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param todayIfNone   if true, and if the field was empty, pre-populate with today's date
     */
    void initPartialDatePicker(@NonNull final Fields.Field field,
                               @StringRes final int dialogTitleId,
                               final boolean todayIfNone) {
        // only bother when it's in use
        if (!field.isUsed()) {
            return;
        }

        field.getView().setOnClickListener(v -> {
            FragmentManager fm = getChildFragmentManager();
            if (fm.findFragmentByTag(PartialDatePickerDialogFragment.TAG) == null) {
                PartialDatePickerDialogFragment
                        .newInstance(field.id, field.getValue().toString(),
                                     dialogTitleId, todayIfNone)
                        .show(fm, PartialDatePickerDialogFragment.TAG);
            }
        });
    }

    /**
     * @param field         {@link Fields.Field} to edit
     * @param dialogTitleId title of the dialog box.
     * @param listGetter    {@link CheckListDialogFragment.CheckListEditorListGetter <T>}
     *                      interface to get the *current* list
     */
    void initCheckListEditor(@NonNull final Fields.Field field,
                             @StringRes final int dialogTitleId,
                             @NonNull final CheckListDialogFragment.CheckListEditorListGetter<T> listGetter) {
        // only bother when it's in use
        if (!field.isUsed()) {
            return;
        }

        field.getView().setOnClickListener(v -> {
            FragmentManager fm = getChildFragmentManager();
            if (fm.findFragmentByTag(CheckListDialogFragment.TAG) == null) {
                CheckListDialogFragment
                        .newInstance(field.id, dialogTitleId, listGetter)
                        .show(fm, CheckListDialogFragment.TAG);
            }
        });
    }
}
