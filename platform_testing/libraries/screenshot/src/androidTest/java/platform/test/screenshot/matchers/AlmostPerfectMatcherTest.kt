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

import android.graphics.Color
import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import platform.test.screenshot.toIntArray
import platform.test.screenshot.utils.loadBitmap

class AlmostPerfectMatcherTest {
    val matcher = AlmostPerfectMatcher()

    @Test
    fun diffColor_exactMatch() {
        val expected = Color.valueOf(
                Color.green(200) or
                        Color.blue(200) or
                        Color.red(5)).toArgb()
        val test = Color.valueOf(
                Color.green(200) or
                        Color.blue(200) or
                        Color.red(5)).toArgb()

        val result = matcher.compareBitmaps(
                expected = intArrayOf(expected),
                given = intArrayOf(test),
                width = 1,
                height = 1,
                regions = emptyList())

        assertThat(result.matches).isTrue()
        assertThat(result.diff).isNull()
    }

    @Test
    fun diffColor_almostMatchLowRed() {
        val expected = Color.valueOf(
                Color.green(200) or
                        Color.blue(200) or
                        Color.red(5)).toArgb()
        val test = Color.valueOf(
                Color.green(200) or
                        Color.blue(201) or
                        Color.red(6)).toArgb()

        val result = matcher.compareBitmaps(
                expected = intArrayOf(expected),
                given = intArrayOf(test),
                width = 1,
                height = 1,
                regions = emptyList())

        assertThat(result.matches).isTrue()
        assertThat(result.diff).isNull()
    }

    @Test
    fun diffColor_almostMatchHighRed() {
        val expected = Color.valueOf(
                Color.green(200) or
                        Color.blue(200) or
                        Color.red(200)).toArgb()
        val test = Color.valueOf(
                Color.green(201) or
                        Color.blue(199) or
                        Color.red(200)).toArgb()

        val result = matcher.compareBitmaps(
                expected = intArrayOf(expected),
                given = intArrayOf(test),
                width = 1,
                height = 1,
                regions = emptyList())

        assertThat(result.matches).isTrue()
        assertThat(result.diff).isNull()
    }

    @Test
    fun diffColor_notMatch() {
        val expected = Color.valueOf(
                Color.green(200) or
                        Color.blue(200) or
                        Color.red(200)).toArgb()
        val test = Color.valueOf(
                Color.green(212) or
                        Color.blue(194) or
                        Color.red(203)).toArgb()

        val result = matcher.compareBitmaps(
                expected = intArrayOf(expected),
                given = intArrayOf(test),
                width = 1,
                height = 1,
                regions = emptyList())

        assertThat(result.matches).isFalse()
        assertThat(result.diff).isNotNull()
    }

    @Test
    fun performDiff_sameBitmaps() {
        val first = loadBitmap("round_rect_gray")
        val second = loadBitmap("round_rect_gray")

        val matcher = PixelPerfectMatcher()
        val result = matcher.compareBitmaps(
                expected = first.toIntArray(), given = second.toIntArray(),
                width = first.width, height = first.height
        )

        assertThat(result.matches).isTrue()
    }

    @Test
    fun performDiff_sameSize_partialCompare_checkDiffImage() {
        val first = loadBitmap("qmc-folder1")
        val second = loadBitmap("qmc-folder2")
        val matcher = PixelPerfectMatcher()
        val interestingRegion = Rect(/* left= */10, /* top= */15, /* right= */70, /* bottom= */50)
        val result = matcher.compareBitmaps(
                expected = first.toIntArray(), given = second.toIntArray(),
                width = first.width, height = first.height, regions = listOf(interestingRegion)
        )
        val diffImage = result.diff!!.toIntArray()

        assertThat(result.matches).isFalse()
        for (i in 0..first.height - 1) {
            for (j in 0..first.width - 1) {
                val rowInRange = i >= interestingRegion.top && i <= interestingRegion.bottom
                val colInRange = j >= interestingRegion.left && j <= interestingRegion.right
                if (!(rowInRange && colInRange)) {
                    assertThat(diffImage[i * first.width + j] == 0).isTrue()
                }
            }
        }
    }
}
