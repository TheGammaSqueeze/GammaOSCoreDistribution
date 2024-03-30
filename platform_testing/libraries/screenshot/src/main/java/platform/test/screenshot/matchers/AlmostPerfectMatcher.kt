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
package platform.test.screenshot.matchers

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.sqrt
import platform.test.screenshot.proto.ScreenshotResultProto

/**
 * Matcher for differences not detectable by human eye
 * The relaxed threshold allows for low quality png storage
 * TODO(b/238758872): replace after b/238758872 is closed
 */
class AlmostPerfectMatcher(
    private val acceptableThreshold: Double = 0.0,
) : BitmapMatcher() {
    override fun compareBitmaps(
            expected: IntArray,
            given: IntArray,
            width: Int,
            height: Int,
            regions: List<Rect>
    ): MatchResult {
        check(expected.size == given.size) { "Size of two bitmaps does not match" }

        val filter = getFilter(width, height, regions)
        var different = 0
        var same = 0
        var ignored = 0

        val diffArray = IntArray(width * height)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val index = x + y * width
                if (filter[index] == 0) {
                    ignored++
                    continue
                }
                val referenceColor = expected[index]
                val testColor = given[index]
                if (areSame(referenceColor, testColor)) {
                    ++same
                } else {
                    ++different
                }
                diffArray[index] =
                        diffColor(
                                referenceColor,
                                testColor
                        )
            }
        }

        val stats = ScreenshotResultProto.DiffResult.ComparisonStatistics
                .newBuilder()
                .setNumberPixelsCompared(width * height)
                .setNumberPixelsIdentical(same)
                .setNumberPixelsDifferent(different)
                .setNumberPixelsIgnored(ignored)
                .build()

        if (different > (acceptableThreshold * width * height)) {
            val diff = Bitmap.createBitmap(diffArray, width, height, Bitmap.Config.ARGB_8888)
            return MatchResult(matches = false, diff = diff, comparisonStatistics = stats)
        }
        return MatchResult(matches = true, diff = null, comparisonStatistics = stats)
    }

    private fun diffColor(referenceColor: Int, testColor: Int): Int {
        return if (areSame(referenceColor, testColor)) {
            Color.TRANSPARENT
        } else {
            Color.MAGENTA
        }
    }

    // ref
    // R. F. Witzel, R. W. Burnham, and J. W. Onley. Threshold and suprathreshold perceptual color
    // differences. J. Optical Society of America, 63:615{625, 1973. 14
    private fun areSame(referenceColor: Int, testColor: Int): Boolean {
        val green = Color.green(referenceColor) - Color.green(testColor)
        val blue = Color.blue(referenceColor) - Color.blue(testColor)
        val red = Color.red(referenceColor) - Color.red(testColor)
        val redDelta = abs(red)
        val redScalar = if (redDelta < 128) 2 else 3
        val blueScalar = if (redDelta < 128) 3 else 2
        val greenScalar = 4
        val correction = sqrt((
                (redScalar * red * red) +
                        (greenScalar * green * green) +
                        (blueScalar * blue * blue))
                .toDouble())
        // 1.5 no difference
        // 3.0 observable by experienced human observer
        // 6.0 minimal difference
        // 12.0 perceivable difference
        return correction <= 3.0
    }
}
