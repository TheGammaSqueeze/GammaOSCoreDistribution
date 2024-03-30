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
import android.graphics.Rect
import kotlin.collections.List
import platform.test.screenshot.proto.ScreenshotResultProto.DiffResult.ComparisonStatistics

/**
 * The abstract class to implement to provide custom bitmap matchers.
 */
abstract class BitmapMatcher {
    /**
     * Compares the given bitmaps and returns result of the operation.
     *
     * The images need to have same size.
     *
     * @param expected The reference / golden image.
     * @param given The image taken during the test.
     * @param width Width of both of the images.
     * @param height Height of both of the images.
     * @param regions An optional array of interesting regions for screenshot diff.
     */
    abstract fun compareBitmaps(
        expected: IntArray,
        given: IntArray,
        width: Int,
        height: Int,
        regions: List<Rect> = emptyList<Rect>()
    ): MatchResult

    protected fun getFilter(width: Int, height: Int, regions: List<Rect>): IntArray {
        if (regions.isEmpty()) { return IntArray(width * height) { 1 } }
        val bitmapArray = IntArray(width * height) { 0 }
        for (region in regions) {
            for (i in region.top..region.bottom) {
                if (i >= height) { break }
                for (j in region.left..region.right) {
                    if (j >= width) { break }
                    bitmapArray[j + i * width] = 1
                }
            }
        }
        return bitmapArray
    }
}

/**
 * Result of the matching performed by [BitmapMatcher].
 *
 * @param matches True if bitmaps match.
 * @param comparisonStatistics Matching statistics provided by this matcher that performed the
 * comparison.
 * @param diff Diff bitmap that highlights the differences between the images. Can be null if match
 * was found.
 */
class MatchResult(
    val matches: Boolean,
    val comparisonStatistics: ComparisonStatistics,
    val diff: Bitmap?
)
