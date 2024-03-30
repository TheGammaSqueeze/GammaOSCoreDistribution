/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.qc.view;

import android.annotation.ColorInt;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.qc.R;

/**
 * Utility class used by {@link QCTileView} and {@link QCRowView}
 */
public class QCViewUtils {
    private static QCViewUtils sInstance;

    private final Context mContext;
    private final Drawable mDefaultToggleBackground;
    private final Drawable mUnavailableToggleBackground;
    private final ColorStateList mDefaultToggleIconTint;
    @ColorInt
    private final int mUnavailableToggleIconTint;
    private final int mToggleForegroundIconInset;

    private QCViewUtils(@NonNull Context context) {
        mContext = context.getApplicationContext();
        mDefaultToggleBackground = mContext.getDrawable(R.drawable.qc_toggle_background);
        mUnavailableToggleBackground = mContext.getDrawable(
                R.drawable.qc_toggle_unavailable_background);
        mDefaultToggleIconTint = mContext.getColorStateList(R.color.qc_toggle_icon_fill_color);
        mUnavailableToggleIconTint = mContext.getColor(R.color.qc_toggle_unavailable_color);
        mToggleForegroundIconInset = mContext.getResources()
                .getDimensionPixelSize(R.dimen.qc_toggle_foreground_icon_inset);
    }

    /**
     * Get an instance of {@link QCViewUtils}
     */
    public static QCViewUtils getInstance(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new QCViewUtils(context);
        }
        return sInstance;
    }

    /**
     * Create a return a Quick Control toggle icon - used for tiles and action toggles.
     */
    public Drawable getToggleIcon(@Nullable Icon icon, boolean available) {
        Drawable background = available
                ? mDefaultToggleBackground.getConstantState().newDrawable().mutate()
                : mUnavailableToggleBackground.getConstantState().newDrawable().mutate();
        if (icon == null) {
            return background;
        }

        Drawable iconDrawable = icon.loadDrawable(mContext);
        if (iconDrawable == null) {
            return background;
        }

        if (!available) {
            iconDrawable.setTint(mUnavailableToggleIconTint);
        } else {
            iconDrawable.setTintList(mDefaultToggleIconTint);
        }

        Drawable[] layers = {background, iconDrawable};
        LayerDrawable drawable = new LayerDrawable(layers);
        drawable.setLayerInsetRelative(/* index= */ 1, mToggleForegroundIconInset,
                mToggleForegroundIconInset, mToggleForegroundIconInset,
                mToggleForegroundIconInset);
        return drawable;
    }
}
