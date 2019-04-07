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

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import java.util.ArrayList;

import com.eleybourn.bookcatalogue.adapters.SimpleListAdapter;
import com.eleybourn.bookcatalogue.baseactivity.EditObjectListActivity;
import com.eleybourn.bookcatalogue.database.DBDefinitions;
import com.eleybourn.bookcatalogue.dialogs.StandardDialogs;
import com.eleybourn.bookcatalogue.dialogs.fieldeditdialog.EditAuthorBaseDialogFragment;
import com.eleybourn.bookcatalogue.entities.Author;
import com.eleybourn.bookcatalogue.utils.UserMessage;
import com.eleybourn.bookcatalogue.utils.Utils;

/**
 * Activity to edit a list of authors provided in an ArrayList<Author> and return an updated list.
 * <p>
 * Calling point is a Book; see {@link EditBookAuthorDialogFragment} for list
 *
 * @author Philip Warner
 */
public class EditAuthorListActivity
        extends EditObjectListActivity<Author> {

    /** Main screen Author name field. */
    private AutoCompleteTextView mAuthorNameView;

    /** Adapter for mAuthorNameView. */
    @SuppressWarnings("FieldCanBeLocal")
    private ArrayAdapter<String> mAuthorAdapter;

    /** flag indicating global changes were made. Used in setResult. */
    private boolean mGlobalChangeMade;

    /**
     * Constructor.
     */
    public EditAuthorListActivity() {
        super(UniqueId.BKEY_AUTHOR_ARRAY);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_edit_list_author;
    }

    @Override
    @CallSuper
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(mBookTitle);

        mAuthorAdapter = new ArrayAdapter<>(this,
                                            android.R.layout.simple_dropdown_item_1line,
                                            mDb.getAuthorsFormattedName());

        mAuthorNameView = findViewById(R.id.author);
        mAuthorNameView.setAdapter(mAuthorAdapter);
    }

    /**
     * The user entered new data in the edit field and clicked 'save'.
     *
     * @param target The view that was clicked ('add' button).
     */
    @Override
    protected void onAdd(@NonNull final View target) {
        String authorName = mAuthorNameView.getText().toString().trim();
        if (authorName.isEmpty()) {
            UserMessage.showUserMessage(mAuthorNameView, R.string.warning_required_name);
            return;
        }

        Author newAuthor = Author.fromString(authorName);
        // see if it already exists
        newAuthor.fixupId(mDb);
        // and check it's not already in the list.
        for (Author author : mList) {
            if (author.equals(newAuthor)) {
                UserMessage.showUserMessage(mAuthorNameView, R.string.warning_author_already_in_list);
                return;
            }
        }
        // add the new one to the list. It is NOT saved at this point!
        mList.add(newAuthor);
        onListChanged();

        // and clear for next entry.
        mAuthorNameView.setText("");
    }

    /**
     * Handle the edits.
     *
     * @param author        the original data.
     * @param newAuthorData a holder for the edited data.
     */
    private void processChanges(@NonNull final Author author,
                                @NonNull final Author newAuthorData) {

        // See if the old one is used by any other books.
        long nrOfReferences = mDb.countBooksByAuthor(author)
                + mDb.countTocEntryByAuthor(author);
        boolean usedByOthers = nrOfReferences > (mRowId == 0 ? 0 : 1);

        // if it's not, then we can simply re-use the old object.
        if (!usedByOthers) {
            /*
             * Use the original author, but update its fields
             *
             * see below and {@link DBA#insertBookDependents} where an *insert* will be done
             * The 'old' author will be orphaned.
             * TODO: simplify / don't orphan?
             */
            author.copyFrom(newAuthorData);
            Utils.pruneList(mDb, mList);
            onListChanged();
            return;
        }

        // When we get here, we know the names are genuinely different and the old author
        // is used in more than one place. Ask the user if they want to make the changes globally.
        String allBooks = getString(R.string.bookshelf_all_books);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_apply_author_changed,
                                      author.getSortName(), newAuthorData.getSortName(), allBooks))
                .setTitle(R.string.title_scope_of_change)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .create();

        /*
         * choosing 'this book':
         * Copy the data fields (name,..) from the holder to the 'old' author.
         * and remove any duplicates.
         *
         * When the actual book is saved, {@link DBA#updateBook} will call
         * {@link DBA#insertBookDependents} which when updating TBL_BOOK_AUTHORS
         * will first try and find the author based on name.
         * If its names differ -> new Author -> inserts the new author.
         * Result: *this* book now uses the modified/new author,
         * while all others keep using the original one.
         *
         * TODO: speculate if it would not be easier to:
         * - fixup(newAuthor) and  if id == 0 insert newAuthor
         * - remove old author from book
         * - add new author to book
         */
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.btn_this_book),
                         (d, which) -> {
                             d.dismiss();

                             author.copyFrom(newAuthorData);
                             Utils.pruneList(mDb, mList);
                             onListChanged();
                         });

        /*
         * Choosing 'all books':
         * globalReplaceAuthor:
         * - Find/update or insert the new Author.
         * - update the TOC of all books so they use the new author id.
         * - update TBL_BOOK_AUTHORS for all books to use the new author id
         * - re-order the 'position' if needed.
         * Result:
         * - all books previously using the olf author, now point to the new author.
         * - the old author will still exist, but won't be in use.
         *
         * Copy the data fields (name,..) from the holder to the 'old' author.
         * and remove any duplicates.
         *
         * When the actual book is saved, {@link DBA#updateBook} will call
         * {@link DBA#insertBookDependents} which when updating TBL_BOOK_AUTHORS
         * will first try and find the author (with the old id) based on name.
         * => it will find the NEW author, and update the id in memory (old becomes new)
         * Result:
         * - this book uses the new author (by recycling the old object with all new id/data)
         *
         * TODO: speculate if this can be simplified.
         */
        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, allBooks,
                         (d, which) -> {
                             d.dismiss();

                             mGlobalChangeMade = mDb.globalReplaceAuthor(author, newAuthorData);

                             author.copyFrom(newAuthorData);
                             Utils.pruneList(mDb, mList);
                             onListChanged();
                         });

        dialog.show();
    }

    /**
     * Called when user clicks the 'Save' button.
     *
     * @param data A newly created Intent to store output if necessary.
     *             Comes pre-populated with data.putExtra(mBKey, mList);
     *
     * @return <tt>true</tt> if activity should exit, <tt>false</tt> to abort exit.
     */
    @Override
    protected boolean onSave(@NonNull final Intent data) {
        final AutoCompleteTextView view = findViewById(R.id.author);
        String str = view.getText().toString().trim();
        if (str.isEmpty()) {
            // no current edit, so we're good to go. Add the global flag.
            data.putExtra(UniqueId.BKEY_GLOBAL_CHANGES_MADE, mGlobalChangeMade);
            return super.onSave(data);
        }

        StandardDialogs.showConfirmUnsavedEditsDialog(
                this,
                /* run when user clicks 'exit' */
                () -> {
                    view.setText("");
                    findViewById(R.id.confirm).performClick();
                });
        return false;
    }

    @Override
    protected ArrayAdapter<Author> createListAdapter(@NonNull final ArrayList<Author> list) {
        return new AuthorListAdapter(this, list);
    }

    /**
     * Edit an Author from the list.
     * It could exist (i.e. have an id) or could be a previously added/new one (id==0).
     */
    public static class EditBookAuthorDialogFragment
            extends EditAuthorBaseDialogFragment {

        /** Fragment manager tag. */
        public static final String TAG = EditBookAuthorDialogFragment.class.getSimpleName();

        /**
         * Constructor.
         *
         * @param author to edit.
         *
         * @return the instance
         */
        public static EditBookAuthorDialogFragment newInstance(@NonNull final Author author) {
            EditBookAuthorDialogFragment frag = new EditBookAuthorDialogFragment();
            Bundle args = new Bundle();
            args.putParcelable(DBDefinitions.KEY_AUTHOR, author);
            frag.setArguments(args);
            return frag;
        }

        /**
         * Handle the edits.
         *
         * @param author        the original data.
         * @param newAuthorData a holder for the edited data.
         */
        protected void confirmChanges(@NonNull final Author author,
                                      @NonNull final Author newAuthorData) {
            ((EditAuthorListActivity) mActivity).processChanges(author, newAuthorData);
        }
    }

    /**
     * Holder pattern for each row.
     */
    private static class Holder {

        @NonNull
        final TextView rowAuthorView;
        @NonNull
        final TextView rowAuthorSortView;

        Holder(@NonNull final View rowView) {
            rowAuthorView = rowView.findViewById(R.id.row_author);
            rowAuthorSortView = rowView.findViewById(R.id.row_author_sort);

            rowView.setTag(this);
        }
    }

    protected class AuthorListAdapter
            extends SimpleListAdapter<Author> {

        AuthorListAdapter(@NonNull final Context context,
                          @NonNull final ArrayList<Author> items) {
            super(context, R.layout.row_edit_author_list, items);
        }

        @Override
        protected void onGetView(@NonNull final View convertView,
                                 @NonNull final Author item) {
            Holder holder = (Holder) convertView.getTag();
            if (holder == null) {
                holder = new Holder(convertView);
            }

            // Setup the variant fields in the holder.
            holder.rowAuthorView.setText(item.getDisplayName());

            if (item.getDisplayName().equals(item.getSortName())) {
                holder.rowAuthorSortView.setVisibility(View.GONE);
            } else {
                holder.rowAuthorSortView.setVisibility(View.VISIBLE);
                holder.rowAuthorSortView.setText(item.getSortName());
            }

        }

        /**
         * edit the item we clicked on.
         */
        @Override
        protected void onRowClick(@NonNull final Author item,
                                  final int position) {
            FragmentManager fm = getSupportFragmentManager();
            if (fm.findFragmentByTag(EditBookAuthorDialogFragment.TAG) == null) {
                EditBookAuthorDialogFragment.newInstance(item)
                                            .show(fm, EditBookAuthorDialogFragment.TAG);
            }
        }

        /**
         * delegate to the ListView host.
         */
        @Override
        protected void onListChanged() {
            EditAuthorListActivity.this.onListChanged();
        }
    }
}
