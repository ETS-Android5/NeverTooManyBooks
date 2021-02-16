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
package com.hardbacknutter.nevertoomanybooks.widgets.fastscroller;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Display a label-style overlay, always at the same place near the top of the screen.
 */
class ClassicOverlay
        extends FastScrollerOverlay {

    /**
     * Constructor.
     *
     * @param view          to hook up
     * @param thumbDrawable the thumb/drag-handle
     */
    ClassicOverlay(@NonNull final RecyclerView view,
                   @Nullable final Rect padding,
                   @NonNull final Drawable thumbDrawable) {
        super(view, padding, thumbDrawable, PopupStyles.CLASSIC);
    }

    @Override
    void layoutView(@NonNull final View parent,
                    @NonNull final View popupView,
                    final int popupWidth,
                    final int popupHeight,
                    final int popupLeft,
                    final int popupTop) {

        final int width = parent.getWidth();

        // 75% of total available width
        final int left = width / 8;
        // 10% from top
        final int top = parent.getHeight() / 10;

        popupView.layout(left, top, width - left, top + popupHeight);
    }
}
