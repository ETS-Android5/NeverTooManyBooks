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
package com.hardbacknutter.nevertoomanybooks.covers;

import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

import com.hardbacknutter.nevertoomanybooks.BaseActivity;
import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DBKey;
import com.hardbacknutter.nevertoomanybooks.databinding.DialogCoverBrowserBinding;
import com.hardbacknutter.nevertoomanybooks.databinding.RowCoverBrowserGalleryBinding;
import com.hardbacknutter.nevertoomanybooks.dialogs.FFBaseDialogFragment;
import com.hardbacknutter.nevertoomanybooks.searchengines.SearchEngineRegistry;
import com.hardbacknutter.nevertoomanybooks.searchengines.Site;

/**
 * Displays and manages a cover image browser in a dialog, allowing the user to select
 * an image from a list to use as the (new) book cover image.
 * <p>
 * Displays gallery images using {@link CoverBrowserViewModel#getGalleryDisplayExecutor()}.
 * Displays preview image on the UI thread.
 * <p>
 * The progress bar is visible while fetching the edition list and the selected image.
 * It's not visible while gallery pictures are loading.
 * <p>
 * ENHANCE: allow configuring search-sites on the fly
 */
public class CoverBrowserDialogFragment
        extends FFBaseDialogFragment {

    /** Log tag. */
    public static final String TAG = "CoverBrowserFragment";
    private static final String BKEY_REQUEST_KEY = TAG + ":rk";

    /** FragmentResultListener request key to use for our response. */
    private String requestKey;

    /** The adapter for the horizontal scrolling covers list. */
    @Nullable
    private GalleryAdapter galleryAdapter;

    /** The max width to be used for the preview image. */
    private int previewMaxWidth;
    /** The max height to be used for the preview image. */
    private int previewMaxHeight;

    /** The ViewModel. */
    private CoverBrowserViewModel vm;

    /** View Binding. */
    private DialogCoverBrowserBinding vb;

    private ImageViewLoader previewLoader;

    /**
     * No-arg constructor for OS use.
     */
    public CoverBrowserDialogFragment() {
        super(R.layout.dialog_cover_browser);
        setFloatingDialogWidth(R.dimen.floating_dialogs_cover_browser_width);
    }

    /**
     * ENHANCE: pass in a {@link Site.Type#Covers} list / set it on the fly.
     */
    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = requireArguments();
        requestKey = Objects.requireNonNull(args.getString(BKEY_REQUEST_KEY), BKEY_REQUEST_KEY);

        vm = new ViewModelProvider(this).get(CoverBrowserViewModel.class);
        vm.init(args);

        final Resources res = getResources();
        previewMaxWidth = res.getDimensionPixelSize(R.dimen.cover_browser_preview_width);
        previewMaxHeight = res.getDimensionPixelSize(R.dimen.cover_browser_preview_height);
    }

    @Override
    public void onViewCreated(@NonNull final View view,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vb = DialogCoverBrowserBinding.bind(view);

        final String bookTitle = Objects.requireNonNull(
                requireArguments().getString(DBKey.TITLE), DBKey.TITLE);
        vb.toolbar.setSubtitle(bookTitle);

        // LayoutManager is set in the layout xml
        final LinearLayoutManager galleryLM = Objects.requireNonNull(
                (LinearLayoutManager) vb.gallery.getLayoutManager(),
                "Missing LinearLayoutManager");
        //noinspection ConstantConditions
        vb.gallery.addItemDecoration(
                new MaterialDividerItemDecoration(getContext(), galleryLM.getOrientation()));
        galleryAdapter = new GalleryAdapter(vm.getGalleryDisplayExecutor());
        vb.gallery.setAdapter(galleryAdapter);

        vm.onGalleryImage().observe(getViewLifecycleOwner(), this::setGalleryImage);
        vm.onShowGalleryProgress().observe(getViewLifecycleOwner(), show -> {
            if (show) {
                vb.progressBar.show();
            } else {
                vb.progressBar.hide();
            }
        });

        // dismiss silently
        vm.onSearchEditionsTaskCancelled().observe(getViewLifecycleOwner(), message -> dismiss());
        // the task throws no exceptions; but paranoia... dismiss silently is fine
        vm.onSearchEditionsTaskFailure().observe(getViewLifecycleOwner(), message -> dismiss());
        vm.onSearchEditionsTaskFinished().observe(getViewLifecycleOwner(), message
                -> message.getData().ifPresent(data -> showGallery(data.getResult())));

        vm.onSelectedImage().observe(getViewLifecycleOwner(), this::setSelectedImage);
        previewLoader = new ImageViewLoader(vm.getPreviewDisplayExecutor(),
                                            previewMaxWidth, previewMaxHeight);

        // When the preview image is clicked, send the fileSpec back to the caller and terminate.
        vb.preview.setOnClickListener(v -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "preview.onClick|filePath=" + vm.getSelectedFileAbsPath());
            }

            if (vm.getSelectedFileAbsPath() != null) {
                Launcher.setResult(this, requestKey, vm.getSelectedFileAbsPath());
            }
            // close the CoverBrowserDialogFragment
            dismiss();
        });
    }

    @Override
    public void onCancel(@NonNull final DialogInterface dialog) {
        vm.cancelAllTasks();
        super.onCancel(dialog);
    }

    @Override
    public void onResume() {
        super.onResume();
        // if the task is NOT already running and we have no editions loaded before
        if (!vm.isSearchEditionsTaskRunning()) {
            if (vm.getEditions().isEmpty()) {
                // start the task
                vb.statusMessage.setText(R.string.progress_msg_searching_editions);
                vb.progressBar.show();
                vm.searchEditions();
            }
        }

        // If currently not shown, set a reasonable size for the preview image
        // so the progress overlay will be shown in the correct position
        if (vb.preview.getVisibility() != View.VISIBLE) {
            final ViewGroup.LayoutParams previewLp = vb.preview.getLayoutParams();
            previewLp.width = previewMaxWidth;
            previewLp.height = previewMaxHeight;
            vb.preview.setLayoutParams(previewLp);
        }
    }

    /**
     * Show the user a selection of other covers and allow selection of a replacement.
     */
    private void showGallery(@Nullable final Collection<String> result) {
        Objects.requireNonNull(galleryAdapter, "galleryAdapter");

        if (result == null || result.isEmpty()) {
            vb.progressBar.hide();
            vb.statusMessage.setText(R.string.warning_no_editions);
            vb.statusMessage.postDelayed(this::dismiss, BaseActivity.ERROR_DELAY_MS);
        } else {
            // set the list and trigger the adapter
            vm.setEditions(result);
            galleryAdapter.notifyDataSetChanged();
            // Show help message
            vb.statusMessage.setText(R.string.txt_tap_on_thumbnail_to_zoom);
        }
    }

    /**
     * Display the given image in the gallery View.
     * If it's invalid in any way, the placeholder/edition will be removed.
     * <p>
     * (Dev note: we should do the non-view processing in the model but having it here
     * makes it uniform with {@link #setSelectedImage}.)
     *
     * @param imageFileInfo to display
     */
    private void setGalleryImage(@Nullable final ImageFileInfo imageFileInfo) {
        Objects.requireNonNull(galleryAdapter, "galleryAdapter");

        final int editionIndex;
        if (imageFileInfo != null) {
            editionIndex = vm.getEditions().indexOf(imageFileInfo.getIsbn());

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "setGalleryImage"
                           + "|editionIndex=" + editionIndex
                           + "|" + imageFileInfo);
            }
        } else {
            editionIndex = -1;
        }

        if (editionIndex >= 0) {
            final Optional<File> tmpFile = imageFileInfo.getFile();
            if (tmpFile.isPresent()) {
                tmpFile.get().deleteOnExit();
                // Tell the adapter to refresh the entry.
                // It will get the image from the file-manager.
                galleryAdapter.notifyItemChanged(editionIndex);
                return;
            }

            // No file. Remove the defunct view from the gallery
            vm.getEditions().remove(editionIndex);
            galleryAdapter.notifyItemRemoved(editionIndex);
        }

        // if none left, dismiss.
        if (galleryAdapter.getItemCount() == 0) {
            vb.progressBar.hide();
            vb.statusMessage.setText(R.string.warning_image_not_found);
            vb.statusMessage.postDelayed(this::dismiss, BaseActivity.ERROR_DELAY_MS);
        }
    }

    /**
     * The user clicked an image in the gallery.
     * Display the given file in the preview View.
     * Starts a task to fetch a large(r) image if needed.
     *
     * @param imageFileInfo to use
     */
    private void onGalleryImageSelected(@NonNull final ImageFileInfo imageFileInfo) {
        imageFileInfo.getFile().ifPresent(file -> {
            if (Size.Large == imageFileInfo.getSize()) {
                // the gallery image IS a valid large image, so just display it
                setSelectedImage(imageFileInfo);
            } else {
                vb.preview.setVisibility(View.INVISIBLE);
                vb.previewProgressBar.show();

                // start a task to fetch a larger image
                vm.fetchSelectedImage(imageFileInfo);
            }
        });
    }

    /**
     * Display the given image in the preview View.
     *
     * @param imageFileInfo to display
     */
    private void setSelectedImage(@Nullable final ImageFileInfo imageFileInfo) {
        // Always reset the preview and hide the progress bar
        vm.setSelectedFile(null);
        vb.preview.setVisibility(View.INVISIBLE);
        vb.previewProgressBar.hide();

        if (imageFileInfo != null) {
            final Optional<File> file = imageFileInfo.getFile();
            if (file.isPresent()) {
                previewLoader.fromFile(vb.preview, file.get(), (bitmap) -> {
                    // Set AFTER it was successfully loaded and displayed for maximum reliability
                    vm.setSelectedFile(file.get());
                    vb.preview.setVisibility(View.VISIBLE);
                    vb.statusMessage.setText(R.string.txt_tap_on_image_to_select);
                });
                return;
            }
        }

        Snackbar.make(vb.preview, R.string.warning_image_not_found,
                      Snackbar.LENGTH_LONG).show();
        vb.statusMessage.setText(R.string.txt_tap_on_thumbnail_to_zoom);

    }

    public abstract static class Launcher
            implements FragmentResultListener {

        private static final String COVER_FILE_SPEC = "fileSpec";
        private String requestKey;
        private FragmentManager fragmentManager;

        static void setResult(@NonNull final Fragment fragment,
                              @NonNull final String requestKey,
                              @NonNull final String fileSpec) {
            final Bundle result = new Bundle(1);
            result.putString(COVER_FILE_SPEC, fileSpec);
            fragment.getParentFragmentManager().setFragmentResult(requestKey, result);
        }

        /**
         * Launch the dialog.
         *
         * @param bookTitle to display
         * @param isbn      ISBN of book
         * @param cIdx      0..n image index
         */
        public void launch(@NonNull final String bookTitle,
                           @NonNull final String isbn,
                           @IntRange(from = 0, to = 1) final int cIdx) {

            final Bundle args = new Bundle(4);
            args.putString(BKEY_REQUEST_KEY, requestKey);
            args.putString(DBKey.TITLE, bookTitle);
            args.putString(DBKey.BOOK_ISBN, isbn);
            args.putInt(CoverBrowserViewModel.BKEY_FILE_INDEX, cIdx);

            final DialogFragment fragment = new CoverBrowserDialogFragment();
            fragment.setArguments(args);
            fragment.show(fragmentManager, TAG);
        }

        public void registerForFragmentResult(@NonNull final FragmentManager fragmentManager,
                                              @NonNull final String requestKey,
                                              @NonNull final LifecycleOwner lifecycleOwner) {
            this.fragmentManager = fragmentManager;
            this.requestKey = requestKey;
            this.fragmentManager.setFragmentResultListener(this.requestKey, lifecycleOwner, this);
        }

        @Override
        public void onFragmentResult(@NonNull final String requestKey,
                                     @NonNull final Bundle result) {
            onResult(Objects.requireNonNull(result.getString(COVER_FILE_SPEC), COVER_FILE_SPEC));
        }

        /**
         * Callback handler with the user's selection.
         *
         * @param fileSpec for the selected file
         */
        public abstract void onResult(@NonNull String fileSpec);
    }

    /**
     * Row ViewHolder for {@link GalleryAdapter}.
     */
    private static class Holder
            extends RecyclerView.ViewHolder {

        @NonNull
        private final RowCoverBrowserGalleryBinding vb;

        Holder(@NonNull final RowCoverBrowserGalleryBinding vb,
               final int maxWidth,
               final int maxHeight) {
            super(vb.getRoot());
            this.vb = vb;

            vb.coverImage0.getLayoutParams().width = maxWidth;
            vb.coverImage0.getLayoutParams().height = maxHeight;
        }
    }

    private class GalleryAdapter
            extends RecyclerView.Adapter<Holder> {

        @SuppressWarnings("InnerClassFieldHidesOuterClassField")
        private static final String TAG = "GalleryAdapter";

        /** A single image fixed width. */
        private final int maxWidth;
        /** A single image fixed height. */
        private final int maxHeight;

        @NonNull
        private final ImageViewLoader imageLoader;

        /**
         * Constructor.
         *
         * @param executor to use for loading images
         */
        @SuppressWarnings("SameParameterValue")
        GalleryAdapter(@NonNull final Executor executor) {
            final Resources res = getResources();
            maxWidth = res.getDimensionPixelSize(R.dimen.cover_browser_gallery_width);
            maxHeight = res.getDimensionPixelSize(R.dimen.cover_browser_gallery_height);

            imageLoader = new ImageViewLoader(executor, maxWidth, maxHeight);
        }

        @Override
        @NonNull
        public Holder onCreateViewHolder(@NonNull final ViewGroup parent,
                                         final int viewType) {

            final RowCoverBrowserGalleryBinding hVb = RowCoverBrowserGalleryBinding
                    .inflate(getLayoutInflater(), parent, false);
            return new Holder(hVb, maxWidth, maxHeight);
        }

        @Override
        public void onBindViewHolder(@NonNull final Holder holder,
                                     final int position) {
            if (vm.isCancelled()) {
                return;
            }

            final String isbn = vm.getEditions().get(position);
            final ImageFileInfo imageFileInfo = vm.getFileInfo(isbn);

            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "onBindViewHolder"
                           + "|position=" + position
                           + "|imageFileInfo=" + imageFileInfo);
            }

            if (imageFileInfo == null) {
                // not in the cache,; use a placeholder but preserve the available space
                imageLoader.placeholder(holder.vb.coverImage0,
                                        R.drawable.ic_baseline_image_24);
                // and queue a request for it.
                vm.fetchGalleryImage(isbn);
                holder.vb.lblSite.setText("");

            } else {
                // check if it's good
                final Optional<File> file = imageFileInfo.getFile();
                if (file.isPresent()) {
                    // YES, load it into the view.
                    imageLoader.fromFile(holder.vb.coverImage0, file.get(), null);

                    // keep this statement here, or we would need to call file.exists() twice
                    holder.vb.coverImage0.setOnClickListener(
                            v -> onGalleryImageSelected(imageFileInfo));

                    holder.vb.lblSite.setText(SearchEngineRegistry
                                                      .getInstance()
                                                      .getByEngineId(imageFileInfo.getEngineId())
                                                      .getLabelResId());

                } else {
                    // no file. Theoretically we should not get here,
                    // as a failed search should have removed the isbn from the edition list,
                    // but race-conditions + paranoia...
                    imageLoader.placeholder(holder.vb.coverImage0,
                                            R.drawable.ic_baseline_broken_image_24);
                    holder.vb.lblSite.setText("");
                }
            }
        }

        @Override
        public int getItemCount() {
            return vm.getEditions().size();
        }
    }
}
