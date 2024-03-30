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

package com.android.server.wm.flicker.region

import com.android.server.wm.flicker.assertThrows
import com.android.server.wm.flicker.traces.region.RegionSubject
import com.android.server.wm.traces.common.Rect
import com.google.common.truth.Truth
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

/**
 * Contains [RegionSubject] tests. To run this test:
 * `atest FlickerLibTest:RegionSubjectTest`
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RegionSubjectTest {
    private fun assertFail(expectedMessage: String, predicate: () -> Any) {
        val error = assertThrows(AssertionError::class.java) {
            predicate()
        }
        Truth.assertThat(error).hasCauseThat().hasMessageThat().contains(expectedMessage)
    }

    private fun expectAllFailPositionChange(expectedMessage: String, rectA: Rect, rectB: Rect) {
        assertFail(expectedMessage) {
            RegionSubject.assertThat(rectA, timestamp = 0).isHigher(rectB)
        }
        assertFail(expectedMessage) {
            RegionSubject.assertThat(rectA, timestamp = 0).isHigherOrEqual(rectB)
        }
        assertFail(expectedMessage) {
            RegionSubject.assertThat(rectA, timestamp = 0).isLower(rectB)
        }
        assertFail(expectedMessage) {
            RegionSubject.assertThat(rectA, timestamp = 0).isLowerOrEqual(rectB)
        }
    }

    @Test
    fun detectPositionChangeHigher() {
        val rectA = Rect(left = 0, top = 0, right = 1, bottom = 1)
        val rectB = Rect(left = 0, top = 1, right = 1, bottom = 2)
        RegionSubject.assertThat(rectA, timestamp = 0).isHigher(rectB)
        RegionSubject.assertThat(rectA, timestamp = 0).isHigherOrEqual(rectB)
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject.assertThat(rectA, timestamp = 0).isLower(rectB)
        }
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject.assertThat(rectA, timestamp = 0).isLowerOrEqual(rectB)
        }
    }

    @Test
    fun detectPositionChangeLower() {
        val rectA = Rect(left = 0, top = 2, right = 1, bottom = 3)
        val rectB = Rect(left = 0, top = 0, right = 1, bottom = 1)
        RegionSubject.assertThat(rectA, timestamp = 0).isLower(rectB)
        RegionSubject.assertThat(rectA, timestamp = 0).isLowerOrEqual(rectB)
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject.assertThat(rectA, timestamp = 0).isHigher(rectB)
        }
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject.assertThat(rectA, timestamp = 0).isHigherOrEqual(rectB)
        }
    }

    @Test
    fun detectPositionChangeEqualHigherLower() {
        val rectA = Rect(left = 0, top = 1, right = 1, bottom = 0)
        val rectB = Rect(left = 1, top = 1, right = 2, bottom = 0)
        RegionSubject.assertThat(rectA, timestamp = 0).isHigherOrEqual(rectB)
        RegionSubject.assertThat(rectA, timestamp = 0).isLowerOrEqual(rectB)
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject.assertThat(rectA, timestamp = 0).isHigher(rectB)
        }
        assertFail(RegionSubject.MSG_ERROR_TOP_POSITION) {
            RegionSubject.assertThat(rectA, timestamp = 0).isLower(rectB)
        }
    }

    @Test
    fun detectPositionChangeInvalid() {
        val rectA = Rect(left = 0, top = 1, right = 2, bottom = 2)
        val rectB = Rect(left = 1, top = 1, right = 2, bottom = 2)
        val rectC = Rect(left = 0, top = 1, right = 3, bottom = 1)
        expectAllFailPositionChange(RegionSubject.MSG_ERROR_LEFT_POSITION, rectA, rectB)
        expectAllFailPositionChange(RegionSubject.MSG_ERROR_RIGHT_POSITION, rectA, rectC)
    }

    @Test
    fun detectCoversAtLeast() {
        val rectA = Rect(left = 1, top = 1, right = 2, bottom = 2)
        val rectB = Rect(left = 0, top = 0, right = 2, bottom = 2)
        RegionSubject.assertThat(rectA, timestamp = 0).coversAtLeast(rectA)
        RegionSubject.assertThat(rectB, timestamp = 0).coversAtLeast(rectA)
        assertFail("SkRegion((0,0,2,1)(0,1,1,2))") {
            RegionSubject.assertThat(rectA, timestamp = 0).coversAtLeast(rectB)
        }
    }

    @Test
    fun detectCoversAtMost() {
        val rectA = Rect(left = 1, top = 1, right = 2, bottom = 2)
        val rectB = Rect(left = 0, top = 0, right = 2, bottom = 2)
        RegionSubject.assertThat(rectA, timestamp = 0).coversAtMost(rectA)
        RegionSubject.assertThat(rectA, timestamp = 0).coversAtMost(rectB)
        assertFail("SkRegion((0,0,2,1)(0,1,1,2))") {
            RegionSubject.assertThat(rectB, timestamp = 0).coversAtMost(rectA)
        }
    }

    @Test
    fun detectCoversExactly() {
        val rectA = Rect(left = 1, top = 1, right = 2, bottom = 2)
        val rectB = Rect(left = 0, top = 0, right = 2, bottom = 2)
        RegionSubject.assertThat(rectA, timestamp = 0).coversExactly(rectA)
        assertFail("SkRegion((0,0,2,1)(0,1,1,2))") {
            RegionSubject.assertThat(rectA, timestamp = 0).coversExactly(rectB)
        }
    }

    @Test
    fun detectOverlaps() {
        val rectA = Rect(left = 1, top = 1, right = 2, bottom = 2)
        val rectB = Rect(left = 0, top = 0, right = 2, bottom = 2)
        val rectC = Rect(left = 2, top = 2, right = 3, bottom = 3)
        RegionSubject.assertThat(rectA, timestamp = 0).overlaps(rectB)
        RegionSubject.assertThat(rectB, timestamp = 0).overlaps(rectA)
        assertFail("Overlap region: SkRegion()") {
            RegionSubject.assertThat(rectA, timestamp = 0).overlaps(rectC)
        }
    }

    @Test
    fun detectsNotOverlaps() {
        val rectA = Rect(left = 1, top = 1, right = 2, bottom = 2)
        val rectB = Rect(left = 2, top = 2, right = 3, bottom = 3)
        val rectC = Rect(left = 0, top = 0, right = 2, bottom = 2)
        RegionSubject.assertThat(rectA, timestamp = 0).notOverlaps(rectB)
        RegionSubject.assertThat(rectB, timestamp = 0).notOverlaps(rectA)
        assertFail("SkRegion((1,1,2,2))") {
            RegionSubject.assertThat(rectA, timestamp = 0).notOverlaps(rectC)
        }
    }
}