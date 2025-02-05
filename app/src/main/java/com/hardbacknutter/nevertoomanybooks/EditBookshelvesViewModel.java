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

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Objects;

import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;

public class EditBookshelvesViewModel
        extends ViewModel {

    /** Currently selected row. */
    private int selectedPosition = RecyclerView.NO_POSITION;

    /** The list we're editing. */
    private List<Bookshelf> list;

    /**
     * Pseudo constructor.
     *
     * @param args {@link Intent#getExtras()} or {@link Fragment#getArguments()}
     */
    void init(@Nullable final Bundle args) {
        if (list == null) {
            list = ServiceLocator.getInstance().getBookshelfDao().getAll();
            if (args != null) {
                final long id = args.getLong(DBKey.FK_BOOKSHELF);
                SanityCheck.requirePositiveValue(id, DBKey.FK_BOOKSHELF);
                selectedPosition = findSelectedPosition(id);
            }
        }
    }

    /**
     * Find the position in the list of the Bookshelf with the given id,
     *
     * @param id to find
     *
     * @return position
     */
    private int findSelectedPosition(final long id) {
        for (int i = 0; i < list.size(); i++) {
            final Bookshelf bookshelf = list.get(i);
            if (bookshelf.getId() == id) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @NonNull
    List<Bookshelf> getList() {
        // used directly by the adapter.
        return list;
    }

    /**
     * Get the currently selected Bookshelf.
     *
     * @return Bookshelf, or {@code null} if none selected (which should never happen... flw)
     */
    @Nullable
    Bookshelf getSelectedBookshelf() {
        if (selectedPosition != RecyclerView.NO_POSITION) {
            return list.get(selectedPosition);
        }
        return null;
    }

    @NonNull
    Bookshelf getBookshelf(final int position) {
        return Objects.requireNonNull(list.get(position), String.valueOf(position));
    }

    int getSelectedPosition() {
        return selectedPosition;
    }

    void setSelectedPosition(final int position) {
        selectedPosition = position;
    }

    /**
     * Verify and set the position 'above' as the new selected position.
     *
     * @return the new 'selected' position
     */
    public int findAndSelect(final int position) {
        selectedPosition = MathUtils.clamp(position - 1, 0, list.size() - 1);
        return selectedPosition;
    }

    /**
     * Called after a Bookshelf has been edited.
     * Reloads the entire list, and sets the edited row as the selected.
     *
     * @param bookshelfId id of the modified Bookshelf
     */
    void onBookshelfEdited(final long bookshelfId) {
        list.clear();
        list.addAll(ServiceLocator.getInstance().getBookshelfDao().getAll());
        selectedPosition = findSelectedPosition(bookshelfId);
    }

    /**
     * Delete the given Bookshelf.
     *
     * @param bookshelf to delete
     */
    void deleteBookshelf(@NonNull final Bookshelf bookshelf) {
        ServiceLocator.getInstance().getBookshelfDao().delete(bookshelf);
        list.remove(bookshelf);
        selectedPosition = RecyclerView.NO_POSITION;
    }

    /**
     * User explicitly wants to purge the BLNS for the given Bookshelf.
     *
     * @param bookshelfId to purge
     */
    void purgeBLNS(final long bookshelfId) {
        ServiceLocator.getInstance().getBookshelfDao()
                      .purgeNodeStates(bookshelfId);
    }
}
