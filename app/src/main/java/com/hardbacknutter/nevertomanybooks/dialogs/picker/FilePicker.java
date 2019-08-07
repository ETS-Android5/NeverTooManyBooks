/*
 * @Copyright 2019 HardBackNutter
 * @License GNU General Public License
 *
 * This file is part of NeverToManyBooks.
 *
 * In August 2018, this project was forked from:
 * Book Catalogue 5.2.2 @copyright 2010 Philip Warner & Evan Leybourn
 *
 * Without their original creation, this project would not exist in its current form.
 * It was however largely rewritten/refactored and any comments on this fork
 * should be directed at HardBackNutter and not at the original creator.
 *
 * NeverToManyBooks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NeverToManyBooks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NeverToManyBooks. If not, see <http://www.gnu.org/licenses/>.
 */
package com.hardbacknutter.nevertomanybooks.dialogs.picker;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.hardbacknutter.nevertomanybooks.R;
import com.hardbacknutter.nevertomanybooks.utils.DateUtils;
import com.hardbacknutter.nevertomanybooks.utils.LocaleUtils;
import com.hardbacknutter.nevertomanybooks.utils.StorageUtils;

/**
 * Present a list of files for selection.
 */
public class FilePicker
        extends ValuePicker {

    /**
     * Constructor.
     *
     * @param inflater LayoutInflater to use
     * @param title    for the dialog
     * @param message  optional message
     * @param files    list to choose from
     * @param handler  which will receive the selected row item
     */
    public FilePicker(@NonNull final LayoutInflater inflater,
                      @Nullable final String title,
                      @Nullable final String message,
                      @NonNull final List<File> files,
                      @NonNull final PickListener<File> handler) {
        super(inflater, title, message, true);

        final FileItemListAdapter adapter = new FileItemListAdapter(inflater, files, item -> {
            dismiss();
            handler.onPicked(item);
        });
        setAdapter(adapter, 0);
    }

    private static class FileItemListAdapter
            extends RecyclerView.Adapter<Holder> {

        @NonNull
        private final Locale mLocale;
        @NonNull
        private final List<File> mList;
        @NonNull
        private final LayoutInflater mInflater;

        @NonNull
        private final PickListener<File> mListener;

        /**
         * Constructor.
         *
         * @param inflater LayoutInflater to use
         * @param list     List of items
         * @param listener called upon user selection
         */
        FileItemListAdapter(@NonNull final LayoutInflater inflater,
                            @NonNull final List<File> list,
                            @NonNull final PickListener<File> listener) {

            mInflater = inflater;
            mListener = listener;
            mLocale = LocaleUtils.from(inflater.getContext());
            mList = list;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            View root = mInflater.inflate(R.layout.row_file_list_item, parent, false);
            return new Holder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {

            Context context = mInflater.getContext();

            File item = mList.get(position);
            holder.nameView.setText(item.getName());
            holder.pathView.setText(item.getParent());
            holder.sizeView.setText(StorageUtils.formatFileSize(context, item.length()));
            holder.lastModDateView
                    .setText(DateUtils.toPrettyDateTime(mLocale, new Date(item.lastModified())));

            // onClick on the whole view.
            holder.itemView.setOnClickListener(v -> mListener.onPicked(item));
        }

        @Override
        public int getItemCount() {
            return mList.size();
        }
    }

    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        final TextView nameView;
        @NonNull
        final TextView pathView;
        @NonNull
        final TextView sizeView;
        @NonNull
        final TextView lastModDateView;


        Holder(@NonNull final View itemView) {
            super(itemView);

            nameView = itemView.findViewById(R.id.name);
            pathView = itemView.findViewById(R.id.path);
            sizeView = itemView.findViewById(R.id.size);
            lastModDateView = itemView.findViewById(R.id.date);
        }
    }
}
