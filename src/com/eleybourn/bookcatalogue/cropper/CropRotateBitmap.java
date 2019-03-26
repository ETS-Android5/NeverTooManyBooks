/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.eleybourn.bookcatalogue.cropper;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class CropRotateBitmap {

    private Bitmap mBitmap;
    private int mRotation;

    CropRotateBitmap() {
    }

    CropRotateBitmap(@Nullable final Bitmap bitmap) {
        mBitmap = bitmap;
        mRotation = 0;
    }

    public int getRotation() {
        return mRotation;
    }

    public void setRotation(int rotation) {
        mRotation = rotation;
    }

    /**
     * Can return null
     *
     * @see #recycle()
     */
    @Nullable
    public Bitmap getBitmap() {
        return mBitmap;
    }

    public void setBitmap(@Nullable final Bitmap bitmap) {
        mBitmap = bitmap;
    }

    @NonNull
    public Matrix getRotateMatrix() {
        // By default this is an identity matrix.
        Matrix matrix = new Matrix();
        if (mRotation != 0) {
            // We want to do the rotation at origin, but since the bounding
            // rectangle will be changed after rotation, so the delta values
            // are based on old & new width/height respectively.
            int cx = mBitmap.getWidth() / 2;
            int cy = mBitmap.getHeight() / 2;
            matrix.preTranslate(-cx, -cy);
            matrix.postRotate(mRotation);
            matrix.postTranslate(getWidth() / 2f, getHeight() / 2f);
        }
        return matrix;
    }

    private boolean isOrientationChanged() {
        return (mRotation / 90) % 2 != 0;
    }

    public int getHeight() {
        if (isOrientationChanged()) {
            return mBitmap.getWidth();
        } else {
            return mBitmap.getHeight();
        }
    }

    public int getWidth() {
        if (isOrientationChanged()) {
            return mBitmap.getHeight();
        } else {
            return mBitmap.getWidth();
        }
    }

    public void recycle() {
        if (mBitmap != null) {
            mBitmap.recycle();
            mBitmap = null;
        }
    }
}
