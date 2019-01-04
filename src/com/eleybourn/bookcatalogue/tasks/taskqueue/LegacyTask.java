/*
 * @copyright 2012 Philip Warner
 * @license GNU General Public License
 *
 * This file is part of Book Catalogue.
 *
 * TaskQueue is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TaskQueue is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Book Catalogue.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.eleybourn.bookcatalogue.tasks.taskqueue;

import android.annotation.SuppressLint;
import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.eleybourn.bookcatalogue.BookCatalogueApp;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.database.CatalogueDBAdapter;
import com.eleybourn.bookcatalogue.database.cursors.BindableItemCursor;
import com.eleybourn.bookcatalogue.dialogs.ContextDialogItem;

import java.util.List;

public class LegacyTask extends Task {
    private static final long serialVersionUID = 3596858518802582316L;

    private static final int TEXT_FIELD_1 = 1;
    private static final int TEXT_FIELD_2 = 2;
    private final byte[] mOriginal;

    public LegacyTask(byte[] original) {
        super(BookCatalogueApp.getResourceString(R.string.gr_tq_legacy_task));
        mOriginal = original;
    }

    @Override
    public int getCategory() {
        return Task.CAT_LEGACY;
    }

    @NonNull
    @Override
    public View newListItemView(@NonNull final LayoutInflater inflater,
                                @NonNull final Context context,
                                @NonNull final BindableItemCursor cursor,
                                @NonNull final ViewGroup parent) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams margins = new LinearLayout.LayoutParams(
                ViewGroup.MarginLayoutParams.MATCH_PARENT,
                ViewGroup.MarginLayoutParams.WRAP_CONTENT);

        TextView tv = new TextView(context);
        tv.setId(TEXT_FIELD_1);
        root.addView(tv, margins);

        tv = new TextView(context);
        tv.setId(TEXT_FIELD_2);
        root.addView(tv, margins);

        return root;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void bindView(@NonNull final View view,
                         @NonNull final Context context,
                         @NonNull final BindableItemCursor cursor,
                         @NonNull final CatalogueDBAdapter db) {
        ((TextView) view.findViewById(TEXT_FIELD_1))
                .setText("Legacy Task Placeholder for Task #" + this.getId());
        ((TextView) view.findViewById(TEXT_FIELD_2))
                .setText("This task is obsolete and can not be recovered. It is probably advisable to delete it.");
    }

    public byte[] getOriginal() {
        return mOriginal;
    }

    @Override
    public void addContextMenuItems(@NonNull final Context context,
                                    @NonNull AdapterView<?> parent,
                                    @NonNull final View view,
                                    final int position,
                                    final long id,
                                    @NonNull final List<ContextDialogItem> items,
                                    @NonNull final CatalogueDBAdapter db) {

        items.add(new ContextDialogItem(context.getString(R.string.gr_tq_menu_delete_task), new Runnable() {
            @Override
            public void run() {
                QueueManager.getQueueManager().deleteTask(LegacyTask.this.getId());
            }
        }));

    }

}
