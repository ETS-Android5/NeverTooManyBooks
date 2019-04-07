package com.eleybourn.bookcatalogue.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.WorkerThread;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.eleybourn.bookcatalogue.BuildConfig;
import com.eleybourn.bookcatalogue.DEBUG_SWITCHES;
import com.eleybourn.bookcatalogue.R;
import com.eleybourn.bookcatalogue.UniqueId;
import com.eleybourn.bookcatalogue.booklist.BooklistBuilder;
import com.eleybourn.bookcatalogue.database.CoversDBA;
import com.eleybourn.bookcatalogue.debug.Logger;
import com.eleybourn.bookcatalogue.tasks.GetImageTask;
import com.eleybourn.bookcatalogue.tasks.ImageCacheWriterTask;
import com.eleybourn.bookcatalogue.tasks.TerminatorConnection;

public final class ImageUtils {

    private static final int BUFFER_SIZE = 65536;

    private ImageUtils() {
    }

    /**
     * Load the image file into the destination view.
     * Scaling is done by Android, enforced by the view itself and the dimensions passed in.
     *
     * @param destView  The ImageView to load with the file or an appropriate icon
     * @param file      The file of the image
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param upscale   use the maximum h/w also as the minimum; thereby forcing upscaling.
     */
    @UiThread
    public static void setImageView(@NonNull final ImageView destView,
                                    @NonNull final File file,
                                    final int maxWidth,
                                    final int maxHeight,
                                    final boolean upscale) {

        // Get the file, if it exists. Otherwise set 'ic_broken_image' icon and exit.
        if (!file.exists()) {
            destView.setImageResource(R.drawable.ic_broken_image);
            return;
        }

        Bitmap bm = BitmapFactory.decodeFile(file.getAbsolutePath());
        setImageView(destView, bm, maxWidth, maxHeight, upscale);
    }

    /**
     * Load the image bitmap into the destination view.
     * Scaling is done by Android, enforced by the view itself and the dimensions passed in.
     *
     * @param destView  The ImageView to load with the bitmap or an appropriate icon
     * @param bm        The Bitmap of the image
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param upscale   use the maximum h/w also as the minimum; thereby forcing upscaling.
     */
    @UiThread
    public static void setImageView(@NonNull final ImageView destView,
                                    @Nullable final Bitmap bm,
                                    final int maxWidth,
                                    final int maxHeight,
                                    final boolean upscale) {
        if (BuildConfig.DEBUG) {
            Logger.info(ImageUtils.class, "setImageView",
                        "maxWidth=" + maxWidth,
                        "maxHeight=" + maxHeight,
                        "upscale=" + upscale,
                        bm != null ? "bm.width=" + bm.getWidth() : "no bm",
                        bm != null ? "bm.height=" + bm.getHeight() : "no bm");
        }

        destView.setMaxWidth(maxWidth);
        destView.setMaxHeight(maxHeight);

        if (bm == null) {
            // no bitmap
            destView.setImageResource(R.drawable.ic_broken_image);
        } else {
            // upscale only when needed.
            if (bm.getHeight() < maxHeight && upscale) {
                Bitmap scaledBitmap;
//                scaledBitmap = Bitmap.createScaledBitmap(bm, maxWidth, maxHeight, true);
                scaledBitmap = createScaledBitmap(bm, maxWidth, maxHeight);
                if (!bm.equals(scaledBitmap)) {
                    bm.recycle();
                    destView.setImageBitmap(scaledBitmap);
                    return;
                }
            }
            // if not upscaling, let Android decide on any other scaling as needed.
            destView.setImageBitmap(bm);
        }
    }

    /**
     * Called in the UI thread, will either (1) use a cached cover or
     * (2) start a background task to create and load it.
     * <p>
     * If a cached image is used a background task is still started to check the file date vs
     * the cache date. If the cached image date is < the file, it is rebuilt.
     *
     * @param destView  View to populate
     * @param uuid      UUID of book to retrieve.
     * @param maxWidth  Max width of resulting image
     * @param maxHeight Max height of resulting image
     */
    @UiThread
    public static void setImageView(@NonNull final ImageView destView,
                                    @NonNull final String uuid,
                                    final int maxWidth,
                                    final int maxHeight) {

        boolean cacheWasChecked = false;

        // 1. If we want to check the cache, AND we don't have cache building happening, check it.
        if (BooklistBuilder.imagesAreCached()
                && !GetImageTask.hasActiveTasks()
                && !ImageCacheWriterTask.hasActiveTasks()) {
            try (CoversDBA coversDBAdapter = CoversDBA.getInstance()) {
                final Bitmap bm = coversDBAdapter.getImage(uuid, maxWidth, maxHeight);
                if (bm != null) {
                    // Remove any tasks that may be getting the image because they may overwrite
                    // anything we do.
                    // Remember: the view may have been re-purposed and have a different associated
                    // task which must be removed from the view and removed from the queue.
                    GetImageTask.clearOldTaskFromView(destView);
                    ImageUtils.setImageView(destView, bm, maxWidth, maxHeight, true);
                    return;
                }
            }
            cacheWasChecked = true;
        }

        // 2. The image is not in the cache but the original exists, queue a task if allowed.
        if (BooklistBuilder.imagesAreGeneratedInBackground()) {
            // use place holder to indicate an image is coming
            destView.setImageResource(R.drawable.ic_image);
            GetImageTask.createAndStart(uuid, destView, maxWidth, maxHeight, cacheWasChecked);
            return;
        }

        // 3. Check if the file exists; if it does not set 'ic_broken_image' icon and exit.
        File file = StorageUtils.getCoverFile(uuid);
        if (!file.exists()) {
            // no image
            destView.setImageResource(R.drawable.ic_broken_image);
            return;
        }

        // 4. go get it from the file system.
        setImageView(destView, file, maxWidth, maxHeight, true);
    }

    /**
     * Custom version of {@link Bitmap#createScaledBitmap}.
     * <p>
     * The ratio correction was taken from the original BC code,
     * but the file-decode scaling logic removed.
     * <p>
     * Creates a new bitmap, scaled from an existing bitmap, when possible. If the
     * specified width and height are the same as the current width and height of
     * the source bitmap, the source bitmap is returned and no new bitmap is
     * created.
     *
     * @param src       The source bitmap.
     * @param dstWidth  The new bitmap's desired width.
     * @param dstHeight The new bitmap's desired height.
     *
     * @return The new scaled bitmap or the source bitmap if no scaling is required.
     *
     * @throws IllegalArgumentException if width is <= 0, or height is <= 0
     */
    @NonNull
    @AnyThread
    public static Bitmap createScaledBitmap(@NonNull final Bitmap src,
                                            final int dstWidth,
                                            final int dstHeight) {
        Matrix m = new Matrix();

        final int width = src.getWidth();
        final int height = src.getHeight();
        if (width != dstWidth || height != dstHeight) {
            final float sx = (float) dstWidth / width;
            final float sy = (float) dstHeight / height;
            // Next line from original method: using this still causes distortion,
            // m.setScale(sx, sy);
            // instead work out the ratio so that it fits exactly
            float ratio = (sx < sy) ? sx : sy;
            m.setScale(ratio, ratio);
        }
        return Bitmap.createBitmap(src, 0, 0, width, height, m, true);
    }

    /**
     * Get the image from the file specification.
     * This is the original code BC code, using BitmapFactory.Options + File
     * TEST: which one is better/faster ? original or simplified ?
     * <p>
     * TODO: createScaledBitmap is an expensive operation. Make sure you really need it.
     *
     * @param fileSpec  the file specification (NOT the uuid!)
     * @param maxWidth  Maximum desired width of the image
     * @param maxHeight Maximum desired height of the image
     * @param exact     if true, the image will be proportionally scaled to fit box.
     *
     * @return The bitmap, or null
     */
    @Nullable
    @AnyThread
    public static Bitmap createScaledBitmap(@NonNull final String fileSpec,
                                            final int maxWidth,
                                            final int maxHeight,
                                            final boolean exact) {

        // Read the file to get the bitmap size
        final BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;
        if (new File(fileSpec).exists()) {
            BitmapFactory.decodeFile(fileSpec, opt);
        }

        // If no size info, or a single pixel, assume file bad.
        if (opt.outHeight <= 0 || opt.outWidth <= 0 || (opt.outHeight == 1 && opt.outWidth == 1)) {
            return null;
        }

        // Next time we don't just want the bounds, we want the file itself
        opt.inJustDecodeBounds = false;

        // Work out how to SCALE the file to fit in required box
        final float widthRatio = (float) maxWidth / opt.outWidth;
        final float heightRatio = (float) maxHeight / opt.outHeight;

        // Work out SCALE so that it fits exactly
        float ratio = (widthRatio < heightRatio) ? widthRatio : heightRatio;

        // Note that inSampleSize seems to ALWAYS be forced to a power of 2, no matter what we
        // specify, so we just work with powers of 2.
        final int idealSampleSize = (int) Math.ceil(1 / ratio);
        // Get the nearest *bigger* power of 2.
        final int samplePow2 =
                (int) Math.pow(2, Math.ceil(Math.log(idealSampleSize) / Math.log(2)));

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Logger.info(ImageUtils.class, "createScaledBitmap",
                        "filename = " + fileSpec,
                        "exact=" + exact,
                        "maxWidth=" + maxWidth,
                        "opt.outWidth=" + opt.outWidth,
                        "widthRatio=" + widthRatio,
                        "maxHeight=" + maxHeight,
                        "opt.outHeight=" + opt.outHeight,
                        "heightRatio=" + heightRatio,
                        "ratio=" + ratio,
                        "idealSampleSize =" + idealSampleSize,
                        "samplePow2=" + samplePow2);
        }

        final Bitmap bm;
        try {
            if (exact) {
                // Create one bigger than needed and SCALE it;
                // this is an attempt to improve quality.
                opt.inSampleSize = samplePow2 / 2;
                if (opt.inSampleSize < 1) {
                    opt.inSampleSize = 1;
                }

                final Bitmap tmpBm = BitmapFactory.decodeFile(fileSpec, opt);
                if (tmpBm == null) {
                    // We ran out of memory, most likely
                    // TODO: Need a way to try loading images after GC().
                    // Otherwise, covers in cover browser will stay blank.
                    Logger.error("Unexpectedly failed to decode bitmap; memory exhausted?");
                    return null;
                }

                final android.graphics.Matrix matrix = new android.graphics.Matrix();
                // Fixup ratio based on new sample size and SCALE it.
                ratio = ratio / (1.0f / opt.inSampleSize);
                matrix.postScale(ratio, ratio);
                bm = Bitmap.createBitmap(tmpBm, 0, 0, opt.outWidth, opt.outHeight, matrix, true);
                // Recycle if original was not returned
                if (!bm.equals(tmpBm)) {
                    tmpBm.recycle();
                }
            } else {
                // Use a SCALE that will make image *no larger than* the desired size
                if (ratio < 1.0f) {
                    opt.inSampleSize = samplePow2;
                }
                bm = BitmapFactory.decodeFile(fileSpec, opt);
            }
        } catch (OutOfMemoryError e) {
            Logger.error(e);
            return null;
        }

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Logger.info(ImageUtils.class, "createScaledBitmap",
                        "bm.width=" + bm.getWidth(),
                        "bm.height=" + bm.getHeight());
        }

        return bm;
    }

    /**
     * Given a URL, get an image and save to a file.
     *
     * @param url  Image file URL
     * @param name for the file.
     *
     * @return Downloaded fileSpec, or null on failure
     */
    @Nullable
    @WorkerThread
    public static String saveImage(@NonNull final String url,
                                   @NonNull final String name) {
        boolean success = false;
        final File file = StorageUtils.getTempCoverFile(name);
        try (TerminatorConnection con = TerminatorConnection.getConnection(url)) {
            success = StorageUtils.saveInputStreamToFile(con.inputStream, file);
        } catch (IOException e) {
            if (BuildConfig.DEBUG /* always log */) {
                Logger.debug(e);
            }
        }

        return success ? file.getAbsolutePath() : null;
    }

    /**
     * Given a URL, get an image and return as a byte array.
     *
     * @param url Image file URL
     *
     * @return Downloaded byte[] or null upon failure
     */
    @Nullable
    @WorkerThread
    public static byte[] getBytes(@NonNull final String url) {
        try (TerminatorConnection con = TerminatorConnection.getConnection(url)) {
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                // Save the output to a byte output stream
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                //noinspection ConstantConditions
                while ((len = con.inputStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, len);
                }
                return out.toByteArray();
            }
        } catch (IOException e) {
            Logger.error(e);
        }
        return null;
    }

    /**
     * Given byte array that represents an image (jpg, png etc), return as a bitmap.
     *
     * @param bytes Raw byte data
     *
     * @return bitmap
     */
    @Nullable
    @AnyThread
    public static Bitmap getBitmap(@NonNull final byte[] bytes) {
        if (bytes.length == 0) {
            return null;
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length,
                                                      new BitmapFactory.Options());

        if (BuildConfig.DEBUG && DEBUG_SWITCHES.IMAGE_UTILS) {
            Logger.info(ImageUtils.class, "getBitmap",
                        "Array " + bytes.length + " bytes",
                        "bitmap " + bitmap.getHeight() + 'x' + bitmap.getWidth());
        }
        return bitmap;
    }

    /**
     * Read {@link UniqueId#BKEY_FILE_SPEC_ARRAY}.
     * If there are images, pick the largest one, rename it, and delete the others.
     * Finally, remove the key and set {@link UniqueId#BKEY_COVER_IMAGE} to true
     */
    @AnyThread
    public static void cleanupImages(@Nullable final Bundle /* in/out */ bookData) {
        if (bookData == null) {
            return;
        }

        ArrayList<String> imageList = bookData.getStringArrayList(UniqueId.BKEY_FILE_SPEC_ARRAY);
        if (imageList == null || imageList.isEmpty()) {
            return;
        }

        cleanupImages(imageList);

        // Finally, cleanup the data
        bookData.remove(UniqueId.BKEY_FILE_SPEC_ARRAY);
        // and indicate we got a file with the default name
        bookData.putBoolean(UniqueId.BKEY_COVER_IMAGE, true);
    }

    /**
     * If there are images, pick the largest one, rename it, and delete the others.
     *
     * @param imageList a list of images
     */
    @AnyThread
    private static void cleanupImages(@NonNull final ArrayList<String> imageList) {

        long bestFileSize = -1;
        int bestFileIndex = -1;

        // Just read the image files to get file size
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;

        // Loop, finding biggest image
        for (int i = 0; i < imageList.size(); i++) {
            String fileSpec = imageList.get(i);
            if (new File(fileSpec).exists()) {
                BitmapFactory.decodeFile(fileSpec, opt);
                // If no size info, assume file bad and skip
                if (opt.outHeight > 0 && opt.outWidth > 0) {
                    long size = opt.outHeight * opt.outWidth;
                    if (size > bestFileSize) {
                        bestFileSize = size;
                        bestFileIndex = i;
                    }
                }
            }
        }

        // Delete all but the best one. Note there *may* be no best one,
        // so all would be deleted. We do this first in case the list
        // contains a file with the same name as the target of our rename.
        for (int i = 0; i < imageList.size(); i++) {
            if (i != bestFileIndex) {
                StorageUtils.deleteFile(new File(imageList.get(i)));
            }
        }
        // Get the best file (if present) and rename it.
        if (bestFileIndex >= 0) {
            File source = new File(imageList.get(bestFileIndex));
            File destination = StorageUtils.getTempCoverFile();
            StorageUtils.renameFile(source, destination);
        }
    }

    @NonNull
    public static DisplaySizes getDisplaySizes(@NonNull final Context context) {
        return new DisplaySizes(context);
    }

    /**
     * NEWKIND: if we need more display sizes, add a field calculation here.
     * <p>
     * small:  Minimum of MAX_SIZE_SMALL and 1/3rd of largest screen dimension
     * standard: Minimum of MAX_SIZE_STANDARD and 2/3rd of largest screen dimension
     * large:  Minimum of MAX_SIZE_LARGE and largest screen dimension.
     */
    public static class DisplaySizes {

        /** Target size of an image - on the Edit Screens. */
        private static final int MAX_SIZE_SMALL = 256;
        /** on the View Screens. */
        private static final int MAX_SIZE_STANDARD = 512;
        /** in zoomed mode. */
        private static final int MAX_SIZE_LARGE = 1024;

        /** Display size in pixels. */
        public final int small;
        public final int standard;
        public final int large;

        DisplaySizes(@NonNull final Context context) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();

            // ENHANCE: should use density
            int maxMetrics = Math.max(metrics.widthPixels, metrics.heightPixels);

            small = Math.min(MAX_SIZE_SMALL, maxMetrics / 3);
            standard = Math.min(MAX_SIZE_STANDARD, maxMetrics * 2 / 3);
            large = Math.min(MAX_SIZE_LARGE, maxMetrics);

            if (BuildConfig.DEBUG) {
                Logger.info(this,"ImageSize",
                            "metrics.widthPixels=" + metrics.widthPixels,
                            "metrics.heightPixels=" + metrics.heightPixels,
                            "small=" + small,
                            "standard=" + standard,
                            "large=" + large);
            }
        }
    }
}
