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

package com.android.server.wm.flicker.traces.region

import androidx.annotation.VisibleForTesting
import com.android.server.wm.flicker.assertions.FlickerSubject
import com.android.server.wm.flicker.traces.FlickerFailureStrategy
import com.android.server.wm.traces.common.Rect
import com.android.server.wm.traces.common.RectF
import com.android.server.wm.traces.common.region.Region
import com.android.server.wm.traces.common.region.RegionEntry
import com.google.common.truth.Fact
import com.google.common.truth.FailureMetadata
import com.google.common.truth.StandardSubjectBuilder
import com.google.common.truth.Subject.Factory
import kotlin.math.abs

/**
 * Truth subject for [Rect] objects, used to make assertions over behaviors that occur on a
 * rectangle.
 */
class RegionSubject(
    fm: FailureMetadata,
    override val parent: FlickerSubject?,
    val regionEntry: RegionEntry,
    override val timestamp: Long
) : FlickerSubject(fm, regionEntry) {

    val region = regionEntry.region

    private val topPositionSubject
        get() = check(MSG_ERROR_TOP_POSITION).that(region.bounds.top)
    private val bottomPositionSubject
        get() = check(MSG_ERROR_BOTTOM_POSITION).that(region.bounds.bottom)
    private val leftPositionSubject
        get() = check(MSG_ERROR_LEFT_POSITION).that(region.bounds.left)
    private val rightPositionSubject
        get() = check(MSG_ERROR_RIGHT_POSITION).that(region.bounds.right)
    private val areaSubject
        get() = check(MSG_ERROR_AREA).that(region.bounds.area)

    private val android.graphics.Rect.area get() = this.width() * this.height()
    private val Rect.area get() = this.width * this.height

    override val selfFacts = listOf(Fact.fact("Region - Covered", region.toString()))

    /**
     * {@inheritDoc}
     */
    override fun fail(reason: List<Fact>): FlickerSubject {
        val newReason = reason.toMutableList()
        return super.fail(newReason)
    }

    private fun assertLeftRightAndAreaEquals(other: Region) {
        leftPositionSubject.isEqualTo(other.bounds.left)
        rightPositionSubject.isEqualTo(other.bounds.right)
        areaSubject.isEqualTo(other.bounds.area)
    }

    /**
     * Subtracts [other] from this subject [region]
     */
    fun minus(other: Region): RegionSubject {
        val remainingRegion = Region.from(this.region)
        remainingRegion.op(other, Region.Op.XOR)
        return assertThat(remainingRegion, this, timestamp)
    }

    /**
     * Adds [other] to this subject [region]
     */
    fun plus(other: Region): RegionSubject {
        val remainingRegion = Region.from(this.region)
        remainingRegion.op(other, Region.Op.UNION)
        return assertThat(remainingRegion, this, timestamp)
    }

    /**
     * Asserts that the top and bottom coordinates of [RegionSubject.region] are smaller
     * or equal to those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isHigherOrEqual(subject: RegionSubject): RegionSubject = apply {
        isHigherOrEqual(subject.region)
    }

    /**
     * Asserts that the top and bottom coordinates of [other] are smaller or equal to
     * those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isHigherOrEqual(other: Rect): RegionSubject = apply {
        isHigherOrEqual(Region.from(other))
    }

    /**
     * Asserts that the top and bottom coordinates of [other] are smaller or equal to
     * those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isHigherOrEqual(other: Region): RegionSubject = apply {
        assertLeftRightAndAreaEquals(other)
        topPositionSubject.isAtMost(other.bounds.top)
        bottomPositionSubject.isAtMost(other.bounds.bottom)
    }

    /**
     * Asserts that the top and bottom coordinates of [RegionSubject.region] are greater
     * or equal to those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isLowerOrEqual(subject: RegionSubject): RegionSubject = apply {
        isLowerOrEqual(subject.region)
    }

    /**
     * Asserts that the top and bottom coordinates of [other] are greater or equal to
     * those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isLowerOrEqual(other: Rect): RegionSubject = apply {
        isLowerOrEqual(Region.from(other))
    }

    /**
     * Asserts that the top and bottom coordinates of [other] are greater or equal to
     * those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isLowerOrEqual(other: Region): RegionSubject = apply {
        assertLeftRightAndAreaEquals(other)
        topPositionSubject.isAtLeast(other.bounds.top)
        bottomPositionSubject.isAtLeast(other.bounds.bottom)
    }

    /**
     * Asserts that the top and bottom coordinates of [RegionSubject.region] are smaller than
     * those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isHigher(subject: RegionSubject): RegionSubject = apply {
        isHigher(subject.region)
    }

    /**
     * Asserts that the top and bottom coordinates of [other] are smaller than those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isHigher(other: Rect): RegionSubject = apply {
        isHigher(Region.from(other))
    }

    /**
     * Asserts that the top and bottom coordinates of [other] are smaller than those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isHigher(other: Region): RegionSubject = apply {
        assertLeftRightAndAreaEquals(other)
        topPositionSubject.isLessThan(other.bounds.top)
        bottomPositionSubject.isLessThan(other.bounds.bottom)
    }

    /**
     * Asserts that the top and bottom coordinates of [RegionSubject.region] are greater than
     * those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isLower(subject: RegionSubject): RegionSubject = apply {
        isLower(subject.region)
    }

    /**
     * Asserts that the top and bottom coordinates of [other] are greater than those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isLower(other: Rect): RegionSubject = apply {
        isLower(Region.from(other))
    }

    /**
     * Asserts that the top and bottom coordinates of [other] are greater than those of [region].
     *
     * Also checks that the left and right positions, as well as area, don't change
     */
    fun isLower(other: Region): RegionSubject = apply {
        assertLeftRightAndAreaEquals(other)
        topPositionSubject.isGreaterThan(other.bounds.top)
        bottomPositionSubject.isGreaterThan(other.bounds.bottom)
    }

    /**
     * Asserts that [region] covers at most [testRegion], that is, its area doesn't cover any
     * point outside of [testRegion].
     *
     * @param testRegion Expected covered area
     */
    fun coversAtMost(testRegion: Region): RegionSubject = apply {
        val testRect = testRegion.bounds
        val intersection = Region.from(region)
        val covers = intersection.op(testRect, Region.Op.INTERSECT) &&
            !intersection.op(region, Region.Op.XOR)

        if (!covers) {
            fail(
                Fact.fact("Region to test", testRegion),
                Fact.fact("Covered region", region),
                Fact.fact("Out-of-bounds region", intersection)
            )
        }
    }

    /**
     * Asserts that [region] covers at most [testRect], that is, its area doesn't cover any
     * point outside of [testRect].
     *
     * @param testRect Expected covered area
     */
    fun coversAtMost(testRect: Rect): RegionSubject = apply {
        coversAtMost(Region.from(testRect))
    }

    /**
     * Asserts that [region] is not bigger than [testRegion], even if the regions don't overlap.
     *
     * @param testRegion Area to compare to
     */
    fun notBiggerThan(testRegion: Region): RegionSubject = apply {
        val testArea = testRegion.bounds.area
        val area = region.bounds.area

        if (area > testArea) {
            fail(
                Fact.fact("Region to test", testRegion),
                Fact.fact("Area of test region", testArea),
                Fact.fact("Covered region", region),
                Fact.fact("Area of region", area)
            )
        }
    }

    /**
     * Asserts that [region] is positioned to the right and bottom from [testRegion], but the
     * regions can overlap and [region] can be smaller than [testRegion]
     *
     * @param testRegion Area to compare to
     * @param threshold Offset threshold by which the position might be off
     */
    fun isToTheRightBottom(testRegion: Region, threshold: Int): RegionSubject = apply {
        val horizontallyPositionedToTheRight =
            testRegion.bounds.left - threshold <= region.bounds.left
        val verticallyPositionedToTheBottom = testRegion.bounds.top - threshold <= region.bounds.top

        if (!horizontallyPositionedToTheRight || !verticallyPositionedToTheBottom) {
            fail(
                Fact.fact("Region to test", testRegion),
                Fact.fact("Actual region", region)
            )
        }
    }

    /**
     * Asserts that [region] covers at least [testRegion], that is, its area covers each point
     * in the region
     *
     * @param testRegion Expected covered area
     */
    fun coversAtLeast(testRegion: Region): RegionSubject = apply {
        val intersection = Region.from(region)
        val covers = intersection.op(testRegion, Region.Op.INTERSECT) &&
            !intersection.op(testRegion, Region.Op.XOR)

        if (!covers) {
            fail(
                Fact.fact("Region to test", testRegion),
                Fact.fact("Covered region", region),
                Fact.fact("Uncovered region", intersection)
            )
        }
    }

    /**
     * Asserts that [region] covers at least [testRect], that is, its area covers each point
     * in the region
     *
     * @param testRect Expected covered area
     */
    fun coversAtLeast(testRect: Rect): RegionSubject = apply {
        coversAtLeast(Region.from(testRect))
    }

    /**
     * Asserts that [region] covers at exactly [testRegion]
     *
     * @param testRegion Expected covered area
     */
    fun coversExactly(testRegion: Region): RegionSubject = apply {
        val intersection = Region.from(region)
        val isNotEmpty = intersection.op(testRegion, Region.Op.XOR)

        if (isNotEmpty) {
            fail(
                Fact.fact("Region to test", testRegion),
                Fact.fact("Covered region", region),
                Fact.fact("Uncovered region", intersection)
            )
        }
    }

    /**
     * Asserts that [region] covers at exactly [testRect]
     *
     * @param testRect Expected covered area
     */
    fun coversExactly(testRect: Rect): RegionSubject = apply {
        coversExactly(Region.from(testRect))
    }

    /**
     * Asserts that [region] and [testRegion] overlap
     *
     * @param testRegion Other area
     */
    fun overlaps(testRegion: Region): RegionSubject = apply {
        val intersection = Region.from(region)
        val isEmpty = !intersection.op(testRegion, Region.Op.INTERSECT)

        if (isEmpty) {
            fail(
                Fact.fact("Region to test", testRegion),
                Fact.fact("Covered region", region),
                Fact.fact("Overlap region", intersection)
            )
        }
    }

    /**
     * Asserts that [region] and [testRect] overlap
     *
     * @param testRect Other area
     */
    fun overlaps(testRect: Rect): RegionSubject = apply {
        overlaps(Region.from(testRect))
    }

    /**
     * Asserts that [region] and [testRegion] don't overlap
     *
     * @param testRegion Other area
     */
    fun notOverlaps(testRegion: Region): RegionSubject = apply {
        val intersection = Region.from(region)
        val isEmpty = !intersection.op(testRegion, Region.Op.INTERSECT)

        if (!isEmpty) {
            fail(
                Fact.fact("Region to test", testRegion),
                Fact.fact("Covered region", region),
                Fact.fact("Overlap region", intersection)
            )
        }
    }

    /**
     * Asserts that [region] and [testRect] don't overlap
     *
     * @param testRect Other area
     */
    fun notOverlaps(testRect: Rect): RegionSubject = apply {
        notOverlaps(Region.from(testRect))
    }

    /**
     * Asserts that [region] and [previous] have same aspect ratio, margin of error up to 0.1.
     *
     * @param other Other region
     */
    fun isSameAspectRatio(other: RegionSubject): RegionSubject = apply {
        val aspectRatio = this.region.width.toFloat() /
            this.region.height
        val otherAspectRatio = other.region.width.toFloat() /
            other.region.height
        check("Should have same aspect ratio, old is $aspectRatio and new is $otherAspectRatio")
            .that(abs(aspectRatio - otherAspectRatio) > 0.1).isFalse()
    }

    companion object {
        @VisibleForTesting
        const val MSG_ERROR_TOP_POSITION = "Incorrect top position"

        @VisibleForTesting
        const val MSG_ERROR_BOTTOM_POSITION = "Incorrect top position"

        @VisibleForTesting
        const val MSG_ERROR_LEFT_POSITION = "Incorrect left position"

        @VisibleForTesting
        const val MSG_ERROR_RIGHT_POSITION = "Incorrect right position"

        @VisibleForTesting
        const val MSG_ERROR_AREA = "Incorrect rect area"

        private fun mergeRegions(regions: Array<Region>): Region {
            val result = Region.EMPTY
            regions.forEach { region ->
                region.rects.forEach { rect ->
                    result.op(rect, Region.Op.UNION)
                }
            }
            return result
        }

        /**
         * Boiler-plate Subject.Factory for RectSubject
         */
        @JvmStatic
        fun getFactory(
            parent: FlickerSubject?,
            timestamp: Long
        ) = Factory { fm: FailureMetadata, region: Region? ->
            val regionEntry = RegionEntry(region ?: Region.EMPTY, timestamp.toString())
            RegionSubject(fm, parent, regionEntry, timestamp)
        }

        /**
         * User-defined entry point for existing android regions
         */
        @JvmStatic
        fun assertThat(
            region: Region?,
            parent: FlickerSubject? = null,
            timestamp: Long
        ): RegionSubject {
            val strategy = FlickerFailureStrategy()
            val subject = StandardSubjectBuilder.forCustomFailureStrategy(strategy)
                .about(getFactory(parent, timestamp))
                .that(region ?: Region.EMPTY) as RegionSubject
            strategy.init(subject)
            return subject
        }

        /**
         * User-defined entry point for existing rects
         */
        @JvmStatic
        @JvmOverloads
        fun assertThat(rect: Array<Rect>, parent: FlickerSubject? = null, timestamp: Long):
            RegionSubject = assertThat(Region(rect), parent, timestamp)

        /**
         * User-defined entry point for existing rects
         */
        @JvmStatic
        @JvmOverloads
        fun assertThat(rect: Rect?, parent: FlickerSubject? = null, timestamp: Long):
            RegionSubject = assertThat(Region.from(rect), parent, timestamp)

        /**
         * User-defined entry point for existing rects
         */
        @JvmStatic
        @JvmOverloads
        fun assertThat(rect: RectF?, parent: FlickerSubject? = null, timestamp: Long):
            RegionSubject = assertThat(rect?.toRect(), parent, timestamp)

        /**
         * User-defined entry point for existing rects
         */
        @JvmStatic
        @JvmOverloads
        fun assertThat(rect: Array<RectF>, parent: FlickerSubject? = null, timestamp: Long):
            RegionSubject = assertThat(
            mergeRegions(
                rect.map { Region.from(it.toRect()) }.toTypedArray()
            ),
            parent, timestamp
        )

        /**
         * User-defined entry point for existing regions
         */
        @JvmStatic
        @JvmOverloads
        fun assertThat(regions: Array<Region>, parent: FlickerSubject? = null, timestamp: Long):
            RegionSubject = assertThat(mergeRegions(regions), parent, timestamp)

        /**
         * User-defined entry point
         *
         * @param regionEntry to assert
         * @param parent containing the entry
         */
        @JvmStatic
        @JvmOverloads
        fun assertThat(regionEntry: RegionEntry?, parent: FlickerSubject? = null, timestamp: Long):
            RegionSubject = assertThat(regionEntry?.region, parent, timestamp)
    }
}
