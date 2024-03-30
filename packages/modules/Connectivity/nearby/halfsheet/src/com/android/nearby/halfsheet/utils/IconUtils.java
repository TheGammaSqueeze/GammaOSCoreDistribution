/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.nearby.halfsheet.utils;

import static com.android.nearby.halfsheet.HalfSheetActivity.TAG;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * Utility class for icon size verification.
 */
public class IconUtils {

    private static final float NOTIFICATION_BACKGROUND_PADDING_PERCENT = 0.125f;
    private static final float NOTIFICATION_BACKGROUND_ALPHA = 0.7f;
    private static final int MIN_ICON_SIZE = 16;
    private static final int DESIRED_ICON_SIZE = 32;

    /**
     * Verify that the icon is non null and falls in the small bucket. Just because an icon isn't
     * small doesn't guarantee it is large or exists.
     */
    public static boolean isIconSizedSmall(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return false;
        }
        return bitmap.getWidth() >= MIN_ICON_SIZE
                && bitmap.getWidth() < DESIRED_ICON_SIZE
                && bitmap.getHeight() >= MIN_ICON_SIZE
                && bitmap.getHeight() < DESIRED_ICON_SIZE;
    }

    /**
     * Verify that the icon is non null and falls in the regular / default size bucket. Doesn't
     * guarantee if not regular then it is small.
     */
    static boolean isIconSizedRegular(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return false;
        }
        return bitmap.getWidth() >= DESIRED_ICON_SIZE && bitmap.getHeight() >= DESIRED_ICON_SIZE;
    }

    /**
     * All icons that are sized correctly (larger than the MIN_ICON_SIZE icon size)
     * are resize on the server to the DESIRED_ICON_SIZE icon size so that
     * they appear correct.
     */
    public static boolean isIconSizeCorrect(@Nullable Bitmap bitmap) {
        if (bitmap == null) {
            return false;
        }
        return isIconSizedSmall(bitmap) || isIconSizedRegular(bitmap);
    }

    /**
     * Returns the bitmap from the byte array. Returns null if cannot decode or not in correct size.
     */
    @Nullable
    public static Bitmap getIcon(byte[] imageData, int size) {
        try {
            Bitmap icon =
                    BitmapFactory.decodeByteArray(imageData, /* offset= */ 0, size);
            if (IconUtils.isIconSizeCorrect(icon)) {
                // Do not add background for Half Sheet.
                return IconUtils.addWhiteCircleBackground(icon);
            }
        } catch (OutOfMemoryError e) {
            Log.w(TAG, "getIcon: Failed to decode icon, returning null.", e);
        }
        return null;
    }

    /** Adds a circular, white background to the bitmap. */
    @Nullable
    public static Bitmap addWhiteCircleBackground(Bitmap bitmap) {
        if (bitmap == null) {
            Log.w(TAG, "addWhiteCircleBackground: Bitmap is null, not adding background.");
            return null;
        }

        if (bitmap.getWidth() != bitmap.getHeight()) {
            Log.w(TAG, "addWhiteCircleBackground: Bitmap dimensions not square. Skipping"
                    + "adding background.");
            return bitmap;
        }

        int padding = (int) (bitmap.getWidth() * NOTIFICATION_BACKGROUND_PADDING_PERCENT);
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
                bitmapWithBackground.getWidth() / 2,
                bitmapWithBackground.getHeight() / 2,
                bitmapWithBackground.getWidth() / 2,
                paint);
        canvas.drawBitmap(bitmap, padding, padding, null);

        return bitmapWithBackground;
    }
}

