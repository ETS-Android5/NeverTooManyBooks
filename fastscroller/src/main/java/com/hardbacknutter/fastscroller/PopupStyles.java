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

/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hardbacknutter.fastscroller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.function.Consumer;


/**
 * Original code from <a href="https://github.com/zhanghai/AndroidFastScroll">
 * https://github.com/zhanghai/AndroidFastScroll</a>.
 */
final class PopupStyles {

    @SuppressLint("UseCompatLoadingForDrawables")
    static final Consumer<TextView> MD = popupView -> {
        final Resources res = popupView.getResources();
        final int minimumSize = res.getDimensionPixelSize(R.dimen.fs_md_popup_min_size);
        popupView.setMinimumWidth(minimumSize);
        popupView.setMinimumHeight(minimumSize);

        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)
                popupView.getLayoutParams();
        layoutParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        layoutParams.setMarginEnd(res.getDimensionPixelOffset(R.dimen.fs_md_popup_margin_end));
        popupView.setLayoutParams(layoutParams);

        final Context context = popupView.getContext();
        popupView.setBackground(context.getDrawable(R.drawable.fastscroll_overlay_default));

        popupView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        popupView.setGravity(Gravity.CENTER);
        popupView.setIncludeFontPadding(false);
        popupView.setSingleLine(false);

        popupView.setTextAppearance(R.style.TextAppearance_Material3_TitleMedium);
    };

    @SuppressLint("UseCompatLoadingForDrawables")
    static final Consumer<TextView> CLASSIC = popupView -> {
        final Resources res = popupView.getResources();
        popupView.setMinimumWidth(res.getDimensionPixelSize(R.dimen.fs_classic_popup_min_width));
        popupView.setMinimumHeight(res.getDimensionPixelSize(R.dimen.fs_classic_popup_min_height));

        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)
                popupView.getLayoutParams();
        layoutParams.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        layoutParams.setMarginEnd(res.getDimensionPixelOffset(R.dimen.fs_classic_popup_margin_end));
        popupView.setLayoutParams(layoutParams);

        final Context context = popupView.getContext();
        popupView.setBackground(context.getDrawable(R.drawable.fastscroll_overlay_classic));

        popupView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        popupView.setGravity(Gravity.START);
        popupView.setIncludeFontPadding(false);
        popupView.setSingleLine(false);

        popupView.setTextAppearance(R.style.TextAppearance_Material3_TitleMedium);
    };

    static final Consumer<TextView> MD2 = popupView -> {
        final Resources res = popupView.getResources();
        popupView.setMinimumWidth(res.getDimensionPixelSize(R.dimen.fs_md2_popup_min_width));
        popupView.setMinimumHeight(res.getDimensionPixelSize(R.dimen.fs_md2_popup_min_height));

        final FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)
                popupView.getLayoutParams();
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        layoutParams.setMarginEnd(res.getDimensionPixelOffset(R.dimen.fs_md2_popup_margin_end));
        popupView.setLayoutParams(layoutParams);

        final Context context = popupView.getContext();
        //reminder: don't use colorSurface; that's already used for the list view background.
        popupView.setBackground(new Md2PopupBackground(context, R.attr.colorBackgroundFloating));
        popupView.setElevation(res.getDimensionPixelOffset(R.dimen.fs_md2_popup_elevation));

        popupView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        popupView.setGravity(Gravity.CENTER);
        popupView.setIncludeFontPadding(false);
        popupView.setSingleLine(false);

        popupView.setTextAppearance(R.style.TextAppearance_Material3_TitleMedium);
    };


    private PopupStyles() {
    }
}
