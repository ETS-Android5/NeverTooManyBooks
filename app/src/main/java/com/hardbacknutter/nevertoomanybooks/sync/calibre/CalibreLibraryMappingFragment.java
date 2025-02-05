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
package com.hardbacknutter.nevertoomanybooks.sync.calibre;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.Optional;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BaseFragment;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.dao.DaoWriteException;
import com.hardbacknutter.nevertoomanybooks.databinding.FragmentCalibreLibraryMapperBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowEditCalibreLibraryBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.entities.Bookshelf;
import com.hardbacknutter.nevertoomanybooks.entities.EntityArrayAdapter;
import com.hardbacknutter.nevertoomanybooks.sync.SyncReaderMetaData;
import com.hardbacknutter.nevertoomanybooks.sync.SyncServer;
import com.hardbacknutter.nevertoomanybooks.tasks.LiveDataEvent;
import com.hardbacknutter.nevertoomanybooks.tasks.ProgressDelegate;
import com.hardbacknutter.nevertoomanybooks.tasks.TaskResult;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExMsg;
import com.hardbacknutter.nevertoomanybooks.widgets.ExtArrayAdapter;

public class CalibreLibraryMappingFragment
        extends BaseFragment {

    /** Log tag. */
    public static final String TAG = "CalibreLibraryMapFrag";

    private CalibreLibraryMappingViewModel vm;

    /** View Binding. */
    private FragmentCalibreLibraryMapperBinding vb;

    private List<Bookshelf> bookshelfList;
    private ExtArrayAdapter<CalibreLibrary> libraryAdapter;
    private ExtArrayAdapter<Bookshelf> bookshelfAdapter;

    @Nullable
    private ProgressDelegate progressDelegate;

    @NonNull
    public static Fragment create() {
        final Fragment fragment = new CalibreLibraryMappingFragment();
        final Bundle args = new Bundle(1);
        args.putParcelable(SyncServer.BKEY_SITE, SyncServer.CalibreCS);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection ConstantConditions
        vm = new ViewModelProvider(getActivity()).get(CalibreLibraryMappingViewModel.class);
        vm.init(requireArguments());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        vb = FragmentCalibreLibraryMapperBinding.inflate(inflater, container, false);
        return vb.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vm.onReadMetaDataFinished().observe(getViewLifecycleOwner(), this::onMetaDataRead);
        vm.onReadMetaDataCancelled().observe(getViewLifecycleOwner(), this::onMetaDataCancelled);
        vm.onReadMetaDataFailure().observe(getViewLifecycleOwner(), this::onMetaDataFailure);

        //noinspection ConstantConditions
        libraryAdapter = new EntityArrayAdapter<>(getContext(), vm.getLibraries());
        vb.libraryName.setAdapter(libraryAdapter);
        vb.libraryName.setOnItemClickListener(
                (av, v, position, id) -> onLibrarySelected(position));

        bookshelfList = vm.getBookshelfList();
        bookshelfAdapter = new EntityArrayAdapter<>(getContext(), bookshelfList);
        vb.bookshelf.setAdapter(bookshelfAdapter);
        vb.bookshelf.setOnItemClickListener((av, v, position, id) -> {
            final Bookshelf bookshelf = bookshelfAdapter.getItem(position);
            //noinspection ConstantConditions
            vm.mapBookshelfToLibrary(bookshelf);
            vb.bookshelf.setText(bookshelf.getName());
        });

        vb.infExtNotInstalled.setOnClickListener(StandardDialogs::infoPopup);

        vb.btnCreate.setOnClickListener(btn -> {
            try {
                btn.setEnabled(false);
                final Bookshelf bookshelf = vm.createLibraryAsBookshelf(getContext());
                addBookshelf(bookshelf, vb.bookshelf);

            } catch (@NonNull final DaoWriteException e) {
                Snackbar.make(vb.getRoot(), e.getUserMessage(getContext()),
                              Snackbar.LENGTH_LONG).show();
            }
        });

        // We're only using the meta-data task, so just check if we already have libraries
        if (vm.getLibraries().isEmpty()) {
            readMetaData();
        } else {
            showOptions();
        }
    }

    private void readMetaData() {
        // There will be no progress messages as reading the data itself is very fast, but
        // connection can take a long time, so bring up the progress dialog now
        if (progressDelegate == null) {
            progressDelegate = new ProgressDelegate(getProgressFrame())
                    .setTitle(R.string.progress_msg_connecting)
                    .setPreventSleep(true)
                    .setIndeterminate(true)
                    .setOnCancelListener(v -> vm.cancelTask(R.id.TASK_ID_READ_META_DATA));
        }
        //noinspection ConstantConditions
        progressDelegate.show(() -> getActivity().getWindow());
        vm.readMetaData();
    }

    private void onMetaDataRead(@NonNull final LiveDataEvent<TaskResult<
            Optional<SyncReaderMetaData>>> message) {
        closeProgressDialog();

        message.getData().flatMap(TaskResult::requireResult).ifPresent(result -> {
            vm.extractLibraryData(result);
            libraryAdapter.notifyDataSetChanged();

            showOptions();
        });
    }

    private void onMetaDataCancelled(@NonNull final LiveDataEvent<TaskResult<
            Optional<SyncReaderMetaData>>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            //noinspection ConstantConditions
            Snackbar.make(getView(), R.string.cancelled, Snackbar.LENGTH_LONG)
                    .show();
            //noinspection ConstantConditions
            getView().postDelayed(() -> getActivity().finish(), BaseActivity.ERROR_DELAY_MS);
        });
    }

    private void onMetaDataFailure(@NonNull final LiveDataEvent<TaskResult<Exception>> message) {
        closeProgressDialog();

        message.getData().ifPresent(data -> {
            final Context context = getContext();
            //noinspection ConstantConditions
            final String msg = ExMsg.map(context, data.getResult())
                                    .orElse(getString(R.string.error_network_site_access_failed,
                                                      CalibreContentServer.getHostUrl(context)));

            new MaterialAlertDialogBuilder(context)
                    .setIcon(R.drawable.ic_baseline_error_24)
                    .setTitle(R.string.lbl_calibre_content_server)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        d.dismiss();
                        // just pop, we're always called from a fragment
                        getParentFragmentManager().popBackStack();
                    })
                    .create()
                    .show();
        });
    }

    /**
     * Update the screen with server specific options and values.
     */
    private void showOptions() {
        onLibrarySelected(0);
        vb.getRoot().setVisibility(View.VISIBLE);

        vb.infExtNotInstalled.setVisibility(vm.isExtInstalled() ? View.GONE : View.VISIBLE);
    }

    private void addBookshelf(@NonNull final Bookshelf bookshelf,
                              @NonNull final AutoCompleteTextView view) {
        bookshelfAdapter.add(bookshelf);
        view.setText(bookshelf.getName(), false);
        // ESSENTIAL STEP: force the internal filtered list to ALSO add the new object
        // See internal code for ArrayAdapter#add and the use of mObjects/mOriginalValues
        bookshelfAdapter.getFilter().filter(bookshelf.getName());
    }

    private void onLibrarySelected(final int position) {
        // remember and display data for the selected library
        vm.setCurrentLibrary(position);
        final CalibreLibrary library = vm.getCurrentLibrary();
        vb.libraryName.setText(library.getName(), false);

        bookshelfList.stream()
                     .filter(bookshelf -> bookshelf.getId() == library.getMappedBookshelfId())
                     .map(Bookshelf::getName)
                     .findFirst()
                     .ifPresent(vb.bookshelf::setText);

        vb.btnCreate.setEnabled(bookshelfList.stream()
                                             .map(Bookshelf::getName)
                                             .noneMatch(name -> name.equals(library.getName())));

        if (library.getVirtualLibraries().isEmpty()) {
            vb.headerVlibs.setVisibility(View.GONE);
            vb.virtualLibraries.setVisibility(View.GONE);
            vb.virtualLibraries.setAdapter(null);

        } else {
            vb.headerVlibs.setVisibility(View.VISIBLE);
            vb.virtualLibraries.setVisibility(View.VISIBLE);
            //noinspection ConstantConditions
            vb.virtualLibraries.setAdapter(new VirtualLibraryMapperAdapter(getContext()));
        }
    }

    private void closeProgressDialog() {
        if (progressDelegate != null) {
            //noinspection ConstantConditions
            progressDelegate.dismiss(getActivity().getWindow());
            progressDelegate = null;
        }
    }

    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowEditCalibreLibraryBinding mVb;

        Holder(@NonNull final View itemView) {
            super(itemView);
            mVb = RowEditCalibreLibraryBinding.bind(itemView);
        }
    }

    public class VirtualLibraryMapperAdapter
            extends RecyclerView.Adapter<Holder> {

        /** Cached inflater. */
        private final LayoutInflater mInflater;

        /**
         * Constructor.
         *
         * @param context Current context
         */
        VirtualLibraryMapperAdapter(@NonNull final Context context) {
            mInflater = LayoutInflater.from(context);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {
            final View itemView = mInflater
                    .inflate(R.layout.row_edit_calibre_library, parent, false);
            final Holder holder = new Holder(itemView);

            holder.mVb.bookshelf.setAdapter(bookshelfAdapter);
            holder.mVb.bookshelf.setOnItemClickListener((av, v, position, id) -> {
                final Bookshelf bookshelf = bookshelfAdapter.getItem(position);
                //noinspection ConstantConditions
                vm.mapBookshelfToVirtualLibrary(bookshelf, holder.getBindingAdapterPosition());
                holder.mVb.bookshelf.setText(bookshelf.getName());
            });

            holder.mVb.btnCreate.setOnClickListener(btn -> {
                try {
                    btn.setEnabled(false);
                    //noinspection ConstantConditions
                    final Bookshelf bookshelf = vm.createVirtualLibraryAsBookshelf(
                            getContext(), holder.getBindingAdapterPosition());
                    addBookshelf(bookshelf, holder.mVb.bookshelf);

                } catch (@NonNull final DaoWriteException e) {
                    Snackbar.make(holder.itemView, e.getUserMessage(getContext()),
                                  Snackbar.LENGTH_LONG).show();
                }
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            final CalibreVirtualLibrary vlib = vm.getVirtualLibrary(position);
            holder.mVb.libraryName.setText(vlib.getName());

            bookshelfList.stream()
                         .filter(bookshelf -> bookshelf.getId() == vlib.getMappedBookshelfId())
                         .map(Bookshelf::getName)
                         .findFirst()
                         .ifPresent(holder.mVb.bookshelf::setText);

            holder.mVb.btnCreate.setEnabled(
                    bookshelfList.stream()
                                 .map(Bookshelf::getName)
                                 .noneMatch(name -> name.equals(vlib.getName())));
        }

        @Override
        public int getItemCount() {
            return vm.getCurrentLibrary().getVirtualLibraries().size();
        }
    }
}
