package com.eleybourn.bookcatalogue.cropper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.eleybourn.bookcatalogue.debug.Logger;

import java.util.ArrayList;
import java.util.List;

class CropImageView extends CropImageViewTouchBase {

    private static final boolean ENSURE_VISIBLE = true;

    final List<CropHighlightView> mHighlightViews = new ArrayList<>();
    @NonNull
    private final Context mContext;
    @Nullable
    private CropHighlightView mMotionHighlightView = null;
    private float mLastX;
    private float mLastY;
    private int mMotionEdge;

    public CropImageView(@NonNull Context context, @NonNull AttributeSet attrs) {
        super(context, attrs);
        this.mContext = context;
    }

    @Override
    @CallSuper
    protected void onLayout(final boolean changed,
                            final int left,
                            final int top,
                            final int right,
                            final int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mBitmapDisplayed.getBitmap() != null) {
            for (CropHighlightView hv : mHighlightViews) {
                hv.mMatrix.set(getImageMatrix());
                hv.invalidate();
                if (hv.mIsFocused) {
                    centerBasedOnHighlightView(hv);
                }
            }
        }
    }

    @Override
    @CallSuper
    protected void zoomTo(final float scale, final float centerX, final float centerY) {
        super.zoomTo(scale, centerX, centerY);
        for (CropHighlightView hv : mHighlightViews) {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    @CallSuper
    protected void zoomIn() {
        super.zoomIn();
        for (CropHighlightView hv : mHighlightViews) {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    @CallSuper
    protected void zoomOut() {
        super.zoomOut();
        for (CropHighlightView hv : mHighlightViews) {
            hv.mMatrix.set(getImageMatrix());
            hv.invalidate();
        }
    }

    @Override
    @CallSuper
    protected void postTranslate(final float deltaX, final float deltaY) {
        super.postTranslate(deltaX, deltaY);
        for (int i = 0; i < mHighlightViews.size(); i++) {
            CropHighlightView hv = mHighlightViews.get(i);
            hv.mMatrix.postTranslate(deltaX, deltaY);
            hv.invalidate();
        }
    }

    // According to the event's position, change the focus to the first
    // hitting cropping rectangle.
    private void recomputeFocus(@NonNull final MotionEvent event) {
        for (int i = 0; i < mHighlightViews.size(); i++) {
            CropHighlightView hv = mHighlightViews.get(i);
            hv.setFocus(false);
            hv.invalidate();
        }

        for (int i = 0; i < mHighlightViews.size(); i++) {
            CropHighlightView hv = mHighlightViews.get(i);
            int edge = hv.getHit(event.getX(), event.getY());
            if (edge != CropHighlightView.GROW_NONE) {
                if (!hv.hasFocus()) {
                    hv.setFocus(true);
                    hv.invalidate();
                }
                break;
            }
        }
        invalidate();
    }

    @Override
    public boolean onTouchEvent(@NonNull final MotionEvent event) {
        CropImageActivity cropImage = (CropImageActivity) mContext;
        if (cropImage.mSaving) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (cropImage.mWaitingToPickFace) {
                    recomputeFocus(event);
                } else {
                    for (int i = 0; i < mHighlightViews.size(); i++) {
                        CropHighlightView hv = mHighlightViews.get(i);
                        int edge = hv.getHit(event.getX(), event.getY());
                        if (edge != CropHighlightView.GROW_NONE) {
                            mMotionEdge = edge;
                            mMotionHighlightView = hv;
                            mLastX = event.getX();
                            mLastY = event.getY();
                            mMotionHighlightView
                                    .setMode((edge == CropHighlightView.MOVE) ? CropHighlightView.ModifyMode.Move
                                            : CropHighlightView.ModifyMode.Grow);
                            break;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (cropImage.mWaitingToPickFace) {
                    for (int i = 0; i < mHighlightViews.size(); i++) {
                        CropHighlightView hv = mHighlightViews.get(i);
                        if (hv.hasFocus()) {
                            cropImage.mCrop = hv;
                            for (int j = 0; j < mHighlightViews.size(); j++) {
                                if (j == i) {
                                    continue;
                                }
                                mHighlightViews.get(j).setHidden(true);
                            }
                            centerBasedOnHighlightView(hv);
                            ((CropImageActivity) mContext).mWaitingToPickFace = false;
                        }
                    }
                } else if (mMotionHighlightView != null) {
                    centerBasedOnHighlightView(mMotionHighlightView);
                    mMotionHighlightView.setMode(CropHighlightView.ModifyMode.None);
                }
                mMotionHighlightView = null;
                center(true, true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (cropImage.mWaitingToPickFace) {
                    recomputeFocus(event);
                } else if (mMotionHighlightView != null) {
                    mMotionHighlightView.handleMotion(mMotionEdge, event.getX()
                            - mLastX, event.getY() - mLastY);
                    mLastX = event.getX();
                    mLastY = event.getY();

                    if (ENSURE_VISIBLE) {
                        // This section of code is optional. It has some user
                        // benefit in that moving the crop rectangle against
                        // the edge of the screen causes scrolling but it means
                        // that the crop rectangle is no longer fixed under
                        // the user's finger.
                        ensureVisible(mMotionHighlightView);
                    }
                }
                // if we're not zoomed then there's no point in even allowing
                // the user to move the image around. This call to center puts
                // it back to the normalized location (with false meaning don't
                // animate).
                if (getScale() == 1F) {
                    center(true, true);
                }
                break;
        }

        return true;
    }

    // Pan the displayed image to make sure the cropping rectangle is visible.
    private void ensureVisible(@NonNull final CropHighlightView hv) {
        Rect r = hv.mDrawRect;

        int panDeltaX1 = Math.max(0, mLeft - r.left);
        int panDeltaX2 = Math.min(0, mRight - r.right);

        int panDeltaY1 = Math.max(0, mTop - r.top);
        int panDeltaY2 = Math.min(0, mBottom - r.bottom);

        int panDeltaX = panDeltaX1 != 0 ? panDeltaX1 : panDeltaX2;
        int panDeltaY = panDeltaY1 != 0 ? panDeltaY1 : panDeltaY2;

        if (panDeltaX != 0 || panDeltaY != 0) {
            panBy(panDeltaX, panDeltaY);
        }
    }

    // If the cropping rectangle's size changed significantly, change the
    // view's center and SCALE according to the cropping rectangle.
    private void centerBasedOnHighlightView(@NonNull final CropHighlightView hv) {
        Rect drawRect = hv.mDrawRect;

        float width = drawRect.width();
        float height = drawRect.height();

        float thisWidth = getWidth();
        float thisHeight = getHeight();

        float z1 = thisWidth / width * .6F;
        float z2 = thisHeight / height * .6F;

        float zoom = Math.min(z1, z2);
        zoom = zoom * this.getScale();
        zoom = Math.max(1F, zoom);
        if ((Math.abs(zoom - getScale()) / zoom) > .1) {
            float[] coordinates = new float[]{hv.mCropRect.centerX(),
                    hv.mCropRect.centerY()};
            getImageMatrix().mapPoints(coordinates);
            zoomTo(zoom, coordinates[0], coordinates[1], 300F);
        }

        ensureVisible(hv);
    }

    @Override
    @CallSuper
    protected void onDraw(@NonNull final Canvas canvas) {
        try {
            super.onDraw(canvas);
            for (int i = 0; i < mHighlightViews.size(); i++) {
                mHighlightViews.get(i).draw(canvas);
            }
        } catch (Exception e) {
            Logger.error(e);
        }
    }

    public void add(@NonNull final CropHighlightView hv) {
        mHighlightViews.add(hv);
        invalidate();
    }
}