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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.hardbacknutter.nevertoomanybooks.BuildConfig;
import com.hardbacknutter.nevertoomanybooks.DEBUG_SWITCHES;
import com.hardbacknutter.nevertoomanybooks.R;
import com.hardbacknutter.nevertoomanybooks.database.DAO;
import com.hardbacknutter.nevertoomanybooks.database.DBDefinitions;
import com.hardbacknutter.nevertoomanybooks.debug.SanityCheck;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPicker;
import com.hardbacknutter.nevertoomanybooks.dialogs.MenuPickerDialogFragment;
import com.hardbacknutter.nevertoomanybooks.dialogs.StandardDialogs;
import com.hardbacknutter.nevertoomanybooks.dialogs.TipManager;
import com.hardbacknutter.nevertoomanybooks.dialogs.ZoomedImageDialogFragment;
import com.hardbacknutter.nevertoomanybooks.entities.Book;
import com.hardbacknutter.nevertoomanybooks.settings.Prefs;
import com.hardbacknutter.nevertoomanybooks.tasks.messages.FinishedMessage;
import com.hardbacknutter.nevertoomanybooks.utils.AppDir;
import com.hardbacknutter.nevertoomanybooks.utils.FileUtils;
import com.hardbacknutter.nevertoomanybooks.utils.GenericFileProvider;
import com.hardbacknutter.nevertoomanybooks.utils.ISBN;
import com.hardbacknutter.nevertoomanybooks.utils.ParseUtils;
import com.hardbacknutter.nevertoomanybooks.utils.exceptions.ExternalStorageException;

/**
 * A delegate class for handling a displayed Cover.
 * Offers context menus and all operations applicable on a Cover image.
 */
public class CoverHandler {

    /** Log tag. */
    private static final String TAG = "CoverHandler";

    /** FragmentResultListener request key. Append the mCIdx value! */
    private static final String RK_MENU_PICKER = TAG + ":rk:" + MenuPickerDialogFragment.TAG;

    /** FragmentResultListener request key. Append the mCIdx value! */
    private static final String RK_COVER_BROWSER = TAG + ":rk:" + CoverBrowserDialogFragment.TAG;

    /**
     * After taking a picture, do nothing. Never change the value.
     * This is stored in user preferences.
     */
    private static final int ACTION_DONE = 0;
    /** After taking a picture, crop. */
    private static final int ACTION_CROP = 1;
    /** After taking a picture, edit. */
    private static final int ACTION_EDIT = 2;

    private static final String IMAGE_MIME_TYPE = "image/*";

    /** Index of the image we're handling. */
    @IntRange(from = 0, to = 1)
    private final int mCIdx;
    private final int mMaxWidth;
    private final int mMaxHeight;
    /** Database Access. */
    @NonNull
    private final DAO mDb;
    /** The hosting fragment. */
    private Fragment mFragment;
    /** Convenience cast of mFragment. */
    private CoverViewHost mCoverViewHost;

    private TransFormTaskViewModel mTransFormTaskViewModel;
    private ActivityResultLauncher<String> mCameraPermissionLauncher;
    private ActivityResultLauncher<Uri> mTakePictureLauncher;
    private ActivityResultLauncher<CropImageActivity.ResultContract.Input> mCropPictureLauncher;
    private ActivityResultLauncher<String> mGetFromFileLauncher;
    private ActivityResultLauncher<Intent> mEditPictureLauncher;
    private Supplier<Book> mBookSupplier;
    @NonNull
    private final CoverBrowserDialogFragment.Launcher mCoverBrowserLauncher =
            new CoverBrowserDialogFragment.Launcher() {
                @Override
                public void onResult(@NonNull final String fileSpec) {
                    onFileSelected(fileSpec);
                }
            };
    /** Using a Supplier so we can get the <strong>current</strong> value (e.g. when editing). */
    private Supplier<String> mCoverBrowserIsbnSupplier;
    /** Using a Supplier so we can get the <strong>current</strong> value (e.g. when editing). */
    private Supplier<String> mCoverBrowserTitleSupplier;
    /** Optional progress bar to display during operations. */
    @Nullable
    private ProgressBar mProgressBar;
    /** Used to display a hint if user rotates a camera image. */
    private boolean mShowHintAboutRotating;
    private final MenuPickerDialogFragment.Launcher mMenuLauncher =
            new MenuPickerDialogFragment.Launcher() {
                @Override
                public boolean onResult(@IdRes final int menuItemId,
                                        final int position) {
                    return onContextItemSelected(menuItemId, position);
                }
            };

    /**
     * Constructor.
     *
     * @param db        Database access
     * @param cIdx      0..n image index
     * @param maxWidth  the maximum width for the cover
     * @param maxHeight the maximum height for the cover
     */
    public CoverHandler(@NonNull final DAO db,
                        @IntRange(from = 0, to = 1) final int cIdx,
                        final int maxWidth,
                        final int maxHeight) {
        mDb = db;
        mCIdx = cIdx;
        mMaxWidth = maxWidth;
        mMaxHeight = maxHeight;
    }

    /**
     * Should be called from {@link Fragment#onViewCreated}.
     *
     * @param fragment hosting fragment
     */
    public void onFragmentViewCreated(@NonNull final Fragment fragment) {
        mFragment = fragment;
        mCoverViewHost = (CoverViewHost) fragment;

        mCameraPermissionLauncher = mFragment.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        takePicture(true);
                    }
                });
        mTakePictureLauncher = mFragment.registerForActivityResult(
                new ActivityResultContracts.TakePicture(), this::onTakePictureResult);

        mGetFromFileLauncher = mFragment.registerForActivityResult(
                new ActivityResultContracts.GetContent(), this::onGetContentResult);

        mEditPictureLauncher = mFragment.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::onEditPictureResult);

        mCropPictureLauncher = mFragment.registerForActivityResult(
                new CropImageActivity.ResultContract(), this::onGetContentResult);

        mCoverBrowserLauncher.register(mFragment, RK_COVER_BROWSER + mCIdx);

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            mMenuLauncher.register(mFragment, RK_MENU_PICKER + mCIdx);
        }

        mTransFormTaskViewModel = new ViewModelProvider(mFragment)
                .get(String.valueOf(mCIdx), TransFormTaskViewModel.class);

        mTransFormTaskViewModel.onFinished().observe(mFragment.getViewLifecycleOwner(), event -> {
            if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
                Log.d(TAG, "mTransFormTaskViewModel.onFinished()"
                           + "|event=" + event);
            }
            showProgress(false);
            if (event.isNewEvent()) {
                Objects.requireNonNull(event.result, FinishedMessage.MISSING_TASK_RESULTS);
                onAfterTransform(event.result);
            }
        });
    }

    public void setProgressBar(@Nullable final ProgressBar progressBar) {
        mProgressBar = progressBar;
    }

    public void setBookSupplier(@NonNull final Supplier<Book> bookSupplier) {
        mBookSupplier = bookSupplier;
    }

    public void onBindView(@NonNull final ImageView view,
                           @NonNull final Book book) {
        // dev warning: in NO circumstances keep a reference to either the view or book!
        final File file = book.getCoverFile(view.getContext(), mCIdx);
        if (file != null) {
            new ImageViewLoader(view, mMaxWidth, mMaxHeight, file, null)
                    .execute();
            view.setBackground(null);
        } else {
            ImageUtils.setPlaceholder(view, mMaxWidth, mMaxHeight,
                                      R.drawable.ic_add_a_photo, R.drawable.outline_rounded);
        }
    }

    public void attachOnClickListeners(@NonNull final ImageView view) {
        // dev warning: in NO circumstances keep a reference to the view!
        view.setOnClickListener(this::onClick);
        view.setOnLongClickListener(this::onCreateContextMenu);
    }

    /**
     * Called when the image-view has been clicked.
     *
     * @param view The view that was clicked.
     */
    private void onClick(@NonNull final View view) {
        // Allow zooming by clicking on the image;
        final File file = mBookSupplier.get().getCoverFile(view.getContext(), mCIdx);
        if (file != null) {
            ZoomedImageDialogFragment.launch(mFragment.getChildFragmentManager(), file);
        }
    }

    /**
     * Context menu for the image.
     *
     * @param view The view that was clicked and held.
     *
     * @return {@code true} for compatibility with setOnLongClickListener
     */
    private boolean onCreateContextMenu(@NonNull final View view) {
        final Book book = mBookSupplier.get();
        final Context context = view.getContext();

        Menu menu = MenuPicker.createMenu(context);
        new MenuInflater(context).inflate(R.menu.image, menu);

        final String title;
        final File uuidCoverFile = book.getCoverFile(context, mCIdx);
        if (uuidCoverFile != null) {
            if (BuildConfig.DEBUG /* always */) {
                // show the size of the image in the title bar
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(uuidCoverFile.getAbsolutePath(), opts);
                title = "" + opts.outWidth + "x" + opts.outHeight;
            } else {
                title = context.getString(R.string.lbl_cover_long);
            }
        } else {
            // there is no current image; only show the replace menu
            final MenuItem menuItem = menu.findItem(R.id.SUBMENU_THUMB_REPLACE);
            menu = menuItem.getSubMenu();
            title = menuItem.getTitle().toString();
        }

        // we only support alternative edition covers for the front cover.
        menu.findItem(R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS).setVisible(mCIdx == 0);

        if (BuildConfig.MENU_PICKER_USES_FRAGMENT) {
            mMenuLauncher.launch(title, null, menu, mCIdx);
        } else {
            new MenuPicker(context, title, null, menu, mCIdx, this::onContextItemSelected)
                    .show();
        }

        return true;
    }

    /**
     * Using {@link MenuPicker} for context menus.
     *
     * @param itemId   which was selected
     * @param position in the list (i.e. mCIdx)
     *
     * @return {@code true} if handled.
     */
    private boolean onContextItemSelected(@IdRes final int itemId,
                                          final int position) {
        final Book book = mBookSupplier.get();
        final Context context = mFragment.getContext();

        if (itemId == R.id.MENU_DELETE) {
            //noinspection ConstantConditions
            book.setCover(context, mDb, mCIdx, null);
            mCoverViewHost.refresh(mCIdx);
            return true;

        } else if (itemId == R.id.SUBMENU_THUMB_ROTATE) {
            // Just a submenu; skip, but display a hint if user is rotating a camera image
            if (mShowHintAboutRotating) {
                //noinspection ConstantConditions
                TipManager.getInstance()
                          .display(context, R.string.tip_autorotate_camera_images, null);
                mShowHintAboutRotating = false;
            }
            return true;

        } else if (itemId == R.id.MENU_THUMB_ROTATE_CW) {
            startRotation(90);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ROTATE_CCW) {
            startRotation(-90);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ROTATE_180) {
            startRotation(180);
            return true;

        } else if (itemId == R.id.MENU_THUMB_CROP) {
            try {
                //noinspection ConstantConditions
                mCropPictureLauncher.launch(new CropImageActivity.ResultContract.Input(
                        // source
                        book.createTempCoverFile(context, mCIdx),
                        // destination
                        getTempFile(context)));

            } catch (@NonNull final ExternalStorageException e) {
                StandardDialogs.showError(context, e);
            } catch (@NonNull final IOException e) {
                StandardDialogs.showError(context, R.string.error_storage_not_accessible);
            }
            return true;

        } else if (itemId == R.id.MENU_EDIT) {
            try {
                //noinspection ConstantConditions
                editPicture(context, book.createTempCoverFile(context, mCIdx));

            } catch (@NonNull final ExternalStorageException e) {
                StandardDialogs.showError(context, e);
            } catch (@NonNull final IOException e) {
                StandardDialogs.showError(context, R.string.error_storage_not_accessible);
            }
            return true;

        } else if (itemId == R.id.MENU_THUMB_ADD_FROM_CAMERA) {
            takePicture(false);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ADD_FROM_FILE_SYSTEM) {
            mGetFromFileLauncher.launch(IMAGE_MIME_TYPE);
            return true;

        } else if (itemId == R.id.MENU_THUMB_ADD_FROM_ALT_EDITIONS) {
            startCoverBrowser();
            return true;
        }
        return false;
    }

    public void setCoverBrowserIsbnSupplier(@NonNull final Supplier<String> supplier) {
        mCoverBrowserIsbnSupplier = supplier;
    }

    public void setCoverBrowserTitleSupplier(@NonNull final Supplier<String> supplier) {
        mCoverBrowserTitleSupplier = supplier;
    }

    /**
     * Use the isbn to fetch other possible images from the internet
     * and present to the user to choose one.
     * <p>
     * The results comes back in {@link #onFileSelected(String)}
     */
    private void startCoverBrowser() {
        final Book book = mBookSupplier.get();

        final String isbnStr;
        if (mCoverBrowserIsbnSupplier != null) {
            isbnStr = mCoverBrowserIsbnSupplier.get();
        } else {
            isbnStr = book.getString(DBDefinitions.KEY_ISBN);
        }

        if (!isbnStr.isEmpty()) {
            final ISBN isbn = ISBN.createISBN(isbnStr);
            if (isbn.isValid(true)) {
                final String bookTitle;
                if (mCoverBrowserTitleSupplier != null) {
                    bookTitle = mCoverBrowserTitleSupplier.get();
                } else {
                    bookTitle = book.getTitle();
                }
                mCoverBrowserLauncher.launch(bookTitle, isbn.asText(), mCIdx);
                return;
            }
        }

        //noinspection ConstantConditions
        Snackbar.make(mFragment.getView(), R.string.warning_requires_isbn,
                      Snackbar.LENGTH_LONG).show();
    }

    /**
     * Called when the user clicks the large preview in the {@link CoverBrowserDialogFragment}.
     *
     * @param fileSpec the selected image
     */
    private void onFileSelected(@NonNull final String fileSpec) {
        SanityCheck.requireValue(fileSpec, "fileSpec");

        final File srcFile = new File(fileSpec);
        if (srcFile.exists()) {
            //noinspection ConstantConditions
            mBookSupplier.get().setCover(mFragment.getContext(), mDb, mCIdx, srcFile);
        } else {
            //noinspection ConstantConditions
            mBookSupplier.get().setCover(mFragment.getContext(), mDb, mCIdx, null);
        }

        mCoverViewHost.refresh(mCIdx);
    }

    /**
     * Edit the image using an external application.
     *
     * @param context Current context
     * @param srcFile to edit
     */
    private void editPicture(@NonNull final Context context,
                             @NonNull final File srcFile)
            throws ExternalStorageException {

        final File dstFile = getTempFile(context);
        FileUtils.delete(dstFile);

        //TODO: we really should revoke the permissions afterwards
        final Uri srcUri = GenericFileProvider.createUri(context, srcFile);
        final Uri dstUri = GenericFileProvider.createUri(context, dstFile);

        final Intent intent = new Intent(Intent.ACTION_EDIT)
                .setDataAndType(srcUri, IMAGE_MIME_TYPE)
                // read access to the input uri
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // write access see below
                .putExtra(MediaStore.EXTRA_OUTPUT, dstUri);

        final List<ResolveInfo> resInfoList =
                context.getPackageManager()
                       .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (!resInfoList.isEmpty()) {
            // We do not know which app will be used, so need to grant permission to all.
            for (final ResolveInfo resolveInfo : resInfoList) {
                final String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, dstUri,
                                           Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            final String prompt = context.getString(R.string.action_edit);
            mEditPictureLauncher.launch(Intent.createChooser(intent, prompt));

        } else {
            //noinspection ConstantConditions
            Snackbar.make(mFragment.getView(), context.getString(R.string.error_no_image_editor),
                          Snackbar.LENGTH_LONG).show();
        }
    }

    private void onEditPictureResult(@NonNull final ActivityResult activityResult) {
        final Context context = mFragment.getContext();
        if (activityResult.getResultCode() == Activity.RESULT_OK) {
            try {
                //noinspection ConstantConditions
                final File file = getTempFile(context);
                if (file.exists()) {
                    showProgress(true);
                    mTransFormTaskViewModel.startTask(
                            new TransFormTaskViewModel.Transformation(file)
                                    .setScale(true));
                    return;
                }
            } catch (@NonNull final ExternalStorageException e) {
                StandardDialogs.showError(context, e);
            }
        }

        //noinspection ConstantConditions
        removeTempFile(context);
    }

    /**
     * Called when the user selected an image from storage,
     * or after the cropping an image.
     *
     * @param uri to load the image from
     */
    private void onGetContentResult(@Nullable final Uri uri) {
        final Context context = mFragment.getContext();
        if (uri != null) {
            File file = null;
            //noinspection ConstantConditions
            try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                // copy the data, and retrieve the (potentially) resolved file
                file = FileUtils.copyInputStream(context, is, getTempFile(context));

            } catch (@NonNull final ExternalStorageException e) {
                StandardDialogs.showError(context, e);

            } catch (@NonNull final IOException e) {
                if (BuildConfig.DEBUG /* always */) {
                    Log.d(TAG, "Unable to copy content to file", e);
                }
            }

            if (file != null) {
                showProgress(true);
                mTransFormTaskViewModel.startTask(
                        new TransFormTaskViewModel.Transformation(file)
                                .setScale(true));
            } else {
                StandardDialogs.showError(context, R.string.warning_image_copy_failed);
            }
        }
    }

    /**
     * Start the camera to get an image.
     *
     * @param alreadyGranted set to {@code true} if we already got granted access.
     *                       i.e. when called from the {@link #mCameraPermissionLauncher}
     */
    private void takePicture(final boolean alreadyGranted) {
        final Context context = mFragment.getContext();
        //noinspection ConstantConditions
        if (alreadyGranted ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {

            try {
                //noinspection ConstantConditions
                final File dstFile = getTempFile(context);
                FileUtils.delete(dstFile);
                final Uri uri = GenericFileProvider.createUri(context, dstFile);
                mTakePictureLauncher.launch(uri);

            } catch (@NonNull final ExternalStorageException e) {
                StandardDialogs.showError(context, e);
            }

        } else {
            mCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void onTakePictureResult(final boolean result) {
        if (result) {
            final Context context = mFragment.getContext();
            File file = null;
            try {
                //noinspection ConstantConditions
                file = getTempFile(context);
            } catch (@NonNull final ExternalStorageException e) {
                StandardDialogs.showError(context, e);
            }

            if (file != null && file.exists()) {
                final SharedPreferences global = PreferenceManager
                        .getDefaultSharedPreferences(context);

                // Should we apply an explicit rotation angle?
                final int explicitRotation = ParseUtils
                        .getIntListPref(global, Prefs.pk_camera_image_autorotate, 0);

                //noinspection ConstantConditions
                final int surfaceRotation = mFragment.getActivity()
                                                     .getWindowManager().getDefaultDisplay()
                                                     .getRotation();

                // What action (if any) should we take after we're done?
                @NextAction
                final int action = ParseUtils
                        .getIntListPref(global, Prefs.pk_camera_image_action, ACTION_DONE);

                showProgress(true);
                mTransFormTaskViewModel.startTask(
                        new TransFormTaskViewModel.Transformation(file)
                                .setScale(true)
                                .setSurfaceRotation(surfaceRotation)
                                .setRotation(explicitRotation)
                                .setReturnCode(action));
            }
        }
    }

    /**
     * Rotate the image by the given angle.
     *
     * @param angle to rotate.
     */
    private void startRotation(final int angle) {
        final Context context = mFragment.getContext();
        try {
            //noinspection ConstantConditions
            final File srcFile = mBookSupplier.get().createTempCoverFile(context, mCIdx);
            showProgress(true);
            mTransFormTaskViewModel.startTask(
                    new TransFormTaskViewModel.Transformation(srcFile)
                            .setRotation(angle));

        } catch (@NonNull final ExternalStorageException e) {
            StandardDialogs.showError(context, e);
        } catch (@NonNull final IOException e) {
            StandardDialogs.showError(context, R.string.error_storage_not_writable);
        }
    }

    private void onAfterTransform(@NonNull final TransFormTaskViewModel.TransformedData result) {
        if (BuildConfig.DEBUG && DEBUG_SWITCHES.COVERS) {
            Log.d(TAG, "onAfterTransform"
                       + "|returnCode=" + result.getReturnCode()
                       + "|bitmap=" + (result.getBitmap() != null)
                       + "|file=" + result.getFile().getAbsolutePath());
        }

        final Context context = mFragment.getContext();

        // The bitmap != null decides if the operation was successful.
        if (null != result.getBitmap()) {
            // sanity check: if the bitmap was good, the file will be good.
            Objects.requireNonNull(result.getFile(), "file");
            try {
                switch (result.getReturnCode()) {
                    case ACTION_CROP:
                        //noinspection ConstantConditions
                        mCropPictureLauncher.launch(new CropImageActivity.ResultContract.Input(
                                result.getFile(), getTempFile(context)));
                        return;

                    case ACTION_EDIT:
                        //noinspection ConstantConditions
                        editPicture(context, result.getFile());
                        return;

                    case ACTION_DONE:
                    default:
                        //noinspection ConstantConditions
                        mBookSupplier.get().setCover(context, mDb, mCIdx, result.getFile());
                        // must use a post to force the View to update.
                        mCoverViewHost.postRefresh(mCIdx);
                        return;
                }
            } catch (@NonNull final ExternalStorageException e) {
                StandardDialogs.showError(context, e);
            }
        }

        // transformation failed
        //noinspection ConstantConditions
        mBookSupplier.get().setCover(context, mDb, mCIdx, null);
        // must use a post to force the View to update.
        mCoverViewHost.postRefresh(mCIdx);
    }

    /**
     * Get the temporary file.
     *
     * @param context Current context
     *
     * @return file
     */
    @NonNull
    private File getTempFile(@NonNull final Context context)
            throws ExternalStorageException {
        return AppDir.Cache.getFile(context, TAG + "_" + mCIdx + ".jpg");
    }

    /**
     * remove any orphaned file.
     *
     * @param context Current context
     */
    private void removeTempFile(@NonNull final Context context) {
        try {
            FileUtils.delete(getTempFile(context));
        } catch (@NonNull final ExternalStorageException ignore) {
            // ignore
        }
    }

    private void showProgress(final boolean show) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    public interface CoverViewHost {

        void refresh(int cIdx);

        void postRefresh(int cIdx);
    }

    @IntDef({ACTION_DONE, ACTION_CROP, ACTION_EDIT})
    @Retention(RetentionPolicy.SOURCE)
    @interface NextAction {

    }
}
