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

package android.view.cts.surfacevalidator;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.util.Log;

public class BitmapPixelChecker {
    private static final String TAG = "BitmapPixelChecker";
    private final PixelColor mPixelColor;
    private final boolean mLogWhenNoMatch;

    public BitmapPixelChecker(int color) {
        this(color, true);
    }

    public BitmapPixelChecker(int color, boolean logWhenNoMatch) {
        mPixelColor = new PixelColor(color);
        mLogWhenNoMatch = logWhenNoMatch;
    }

    public int getNumMatchingPixels(Bitmap bitmap, Rect bounds) {
        int numMatchingPixels = 0;
        int numErrorsLogged = 0;
        for (int x = bounds.left; x < bounds.right; x++) {
            for (int y = bounds.top; y < bounds.bottom; y++) {
                int color = bitmap.getPixel(x, y);
                if (getExpectedColor(x, y).matchesColor(color)) {
                    numMatchingPixels++;
                } else if (mLogWhenNoMatch && numErrorsLogged < 100) {
                    // We don't want to spam the logcat with errors if something is really
                    // broken. Only log the first 100 errors.
                    PixelColor expected = getExpectedColor(x, y);
                    int expectedColor = Color.argb(expected.mAlpha, expected.mRed,
                            expected.mGreen, expected.mBlue);
                    Log.e(TAG, String.format(
                            "Failed to match (%d, %d) color=0x%08X expected=0x%08X", x, y,
                            color, expectedColor));
                    numErrorsLogged++;
                }
            }
        }
        return numMatchingPixels;
    }

    public PixelColor getExpectedColor(int x, int y) {
        return mPixelColor;
    }
}
