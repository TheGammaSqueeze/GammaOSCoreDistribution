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

package com.android.server.nearby.common.fastpair;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;

/** Utility methods for icon size verification. */
public class IconUtils {
    private static final int MIN_ICON_SIZE = 16;
    private static final int DESIRED_ICON_SIZE = 32;
    private static final double NOTIFICATION_BACKGROUND_PADDING_PERCENTAGE = 0.125;
    private static final double NOTIFICATION_BACKGROUND_ALPHA = 0.7;

    /**
     * Verify that the icon is non null and falls in the small bucket. Just because an icon isn't
     * small doesn't guarantee it is large or exists.
     */
    @VisibleForTesting
    static boolean isIconSizedSmall(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return false;
        }
        int min = MIN_ICON_SIZE;
        int desired = DESIRED_ICON_SIZE;
        return bitmap.getWidth() >= min
                && bitmap.getWidth() < desired
                && bitmap.getHeight() >= min
                && bitmap.getHeight() < desired;
    }

    /**
     * Verify that the icon is non null and falls in the regular / default size bucket. Doesn't
     * guarantee if not regular then it is small.
     */
    @VisibleForTesting
    static boolean isIconSizedRegular(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return false;
        }
        return bitmap.getWidth() >= DESIRED_ICON_SIZE
                && bitmap.getHeight() >= DESIRED_ICON_SIZE;
    }

    // All icons that are sized correctly (larger than the min icon size) are resize on the server
    // to the desired icon size so that they appear correct in notifications.

    /**
     * All icons that are sized correctly (larger than the min icon size) are resize on the server
     * to the desired icon size so that they appear correct in notifications.
     */
    public static boolean isIconSizeCorrect(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return false;
        }
        return isIconSizedSmall(bitmap) || isIconSizedRegular(bitmap);
    }

    /** Adds a circular, white background to the bitmap. */
    @Nullable
    public static Bitmap addWhiteCircleBackground(Context context, @Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }

        if (bitmap.getWidth() != bitmap.getHeight()) {
            return bitmap;
        }

        int padding = (int) (bitmap.getWidth() * NOTIFICATION_BACKGROUND_PADDING_PERCENTAGE);
        Bitmap bitmapWithBackground =
                Bitmap.createBitmap(
                        bitmap.getWidth() + (2 * padding),
                        bitmap.getHeight() + (2 * padding),
                        bitmap.getConfig());
        Canvas canvas = new Canvas(bitmapWithBackground);
        Paint paint = new Paint();
        paint.setColor(
                ColorUtils.setAlphaComponent(
                        Color.WHITE, (int) (255 * NOTIFICATION_BACKGROUND_ALPHA)));
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        canvas.drawCircle(
                bitmapWithBackground.getWidth() / 2f,
                bitmapWithBackground.getHeight() / 2f,
                bitmapWithBackground.getWidth() / 2f,
                paint);
        canvas.drawBitmap(bitmap, padding, padding, null);
        return bitmapWithBackground;
    }
}

