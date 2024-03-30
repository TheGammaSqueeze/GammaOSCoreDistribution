/*
 * Copyright 2022 The Android Open Source Project
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
import kotlin.collections.List
import platform.test.screenshot.proto.ScreenshotResultProto

/**
 * Bitmap matching that does an exact comparison of pixels between bitmaps.
 */
class PixelPerfectMatcher : BitmapMatcher() {

    override fun compareBitmaps(
        expected: IntArray,
        given: IntArray,
        width: Int,
        height: Int,
        regions: List<Rect>
    ): MatchResult {
        check(expected.size == given.size)

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
                if (referenceColor == testColor) {
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

        if (different > 0) {
            val diff = Bitmap.createBitmap(diffArray, width, height, Bitmap.Config.ARGB_8888)
            return MatchResult(matches = false, diff = diff, comparisonStatistics = stats)
        }
        return MatchResult(matches = true, diff = null, comparisonStatistics = stats)
    }

    private fun diffColor(referenceColor: Int, testColor: Int): Int {
        return if (referenceColor != testColor) {
            Color.MAGENTA
        } else {
            Color.TRANSPARENT
        }
    }
}
