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

package com.android.server.wm.traces.common.region

import com.android.server.wm.traces.common.Rect
import com.android.server.wm.traces.common.RectF
import kotlin.math.min

/**
 * Wrapper for RegionProto (frameworks/native/services/surfaceflinger/layerproto/common.proto)
 *
 * Implementation based android.graphics.Region's native implementation found in SkRegion.cpp
 *
 * This class is used by flicker and Winscope
 *
 * It has a single constructor and different [from] functions on its companion because JS
 * doesn't support constructor overload
 */
class Region(rects: Array<Rect> = arrayOf()) {
    private var fBounds = Rect.EMPTY
    private var fRunHead: RunHead? = RunHead(isEmptyHead = true)

    init {
        if (rects.isEmpty()) {
            setEmpty()
        } else {
            for (rect in rects) {
                union(rect)
            }
        }
    }

    val rects get() = getRectsFromString(toString())

    val width: Int get() = bounds.width
    val height: Int get() = bounds.height
    // if null we are a rect not empty
    val isEmpty: Boolean get() = fRunHead?.isEmptyHead ?: false
    val isNotEmpty: Boolean get() = !isEmpty
    val bounds get() = fBounds

    /**
     * Set the region to the empty region
     */
    fun setEmpty(): Boolean {
        fBounds = Rect.EMPTY
        fRunHead = RunHead(isEmptyHead = true)

        return false
    }

    /**
     * Set the region to the specified region.
     */
    fun set(region: Region): Boolean {
        fBounds = region.fBounds.clone()
        fRunHead = region.fRunHead?.clone()
        return !(fRunHead?.isEmptyHead ?: false)
    }

    /**
     * Set the region to the specified rectangle
     */
    fun set(r: Rect): Boolean {
        return if (r.isEmpty ||
            SkRegion_kRunTypeSentinel == r.right ||
            SkRegion_kRunTypeSentinel == r.bottom) {
            this.setEmpty()
        } else {
            fBounds = r
            fRunHead = null
            true
        }
    }

    /**
     * Set the region to the specified rectangle
     */
    operator fun set(left: Int, top: Int, right: Int, bottom: Int): Boolean {
        return set(Rect(left, top, right, bottom))
    }

    fun isRect(): Boolean {
        return fRunHead == null
    }

    fun isComplex(): Boolean {
        return !this.isEmpty && !this.isRect()
    }

    fun contains(x: Int, y: Int): Boolean {
        if (!fBounds.contains(x, y)) {
            return false
        }
        if (this.isRect()) {
            return true
        }
        require(this.isComplex())

        val runs = fRunHead!!.findScanline(y)

        // Skip the Bottom and IntervalCount
        var runsIndex = 2

        // Just walk this scanline, checking each interval. The X-sentinel will
        // appear as a left-interval (runs[0]) and should abort the search.
        //
        // We could do a bsearch, using interval-count (runs[1]), but need to time
        // when that would be worthwhile.
        //
        while (true) {
            if (x < runs[runsIndex]) {
                break
            }
            if (x < runs[runsIndex + 1]) {
                return true
            }
            runsIndex += 2
        }
        return false
    }

    override fun toString(): String = prettyPrint()

    class Iterator(private val rgn: Region) {
        private var done: Boolean
        private var rect: Rect
        private var fRuns: List<Int>? = null
        private var fRunsIndex = 0

        init {
            fRunsIndex = 0
            if (rgn.isEmpty) {
                rect = Rect.EMPTY
                done = true
            } else {
                done = false
                if (rgn.isRect()) {
                    rect = rgn.fBounds
                    fRuns = null
                } else {
                    fRuns = rgn.fRunHead!!.readonlyRuns
                    rect = Rect(fRuns!![3], fRuns!![0],
                        fRuns!![4], fRuns!![1])
                    fRunsIndex = 5
                }
            }
        }

        fun next() {
            if (done) {
                return
            }

            if (fRuns == null) { // rect case
                done = true
                return
            }

            val runs = fRuns!!
            var runsIndex = fRunsIndex

            if (runs[runsIndex] < SkRegion_kRunTypeSentinel) { // valid X value
                rect = Rect(runs[runsIndex], rect.top, runs[runsIndex + 1], rect.bottom)
                runsIndex += 2
            } else { // we're at the end of a line
                runsIndex += 1
                if (runs[runsIndex] < SkRegion_kRunTypeSentinel) { // valid Y value
                    val intervals = runs[runsIndex + 1]
                    if (0 == intervals) { // empty line
                        rect = Rect(rect.left, runs[runsIndex], rect.right, rect.bottom)
                        runsIndex += 3
                    } else {
                        rect = Rect(rect.left, rect.bottom, rect.right, rect.bottom)
                    }

                    assert_sentinel(runs[runsIndex + 2], false)
                    assert_sentinel(runs[runsIndex + 3], false)
                    rect =
                        Rect(runs[runsIndex + 2], rect.top, runs[runsIndex + 3], runs[runsIndex])
                    runsIndex += 4
                } else { // end of rgn
                    done = true
                }
            }
            fRunsIndex = runsIndex
        }

        fun done(): Boolean {
            return done
        }

        fun rect(): Rect {
            return rect
        }
    }

    fun prettyPrint(): String {
        val iter = Iterator(this)
        val result = StringBuilder("SkRegion(")
        while (!iter.done()) {
            val r = iter.rect()
            result.append("(${r.left},${r.top},${r.right},${r.bottom})")
            iter.next()
        }
        result.append(")")
        return result.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Region) return false
        if (!super.equals(other)) return false
        if (!rects.contentEquals(other.rects)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + rects.contentHashCode()
        return result
    }

    // the native values for these must match up with the enum in SkRegion.h
    enum class Op(val nativeInt: Int) {
        DIFFERENCE(0), INTERSECT(1), UNION(2), XOR(3),
        REVERSE_DIFFERENCE(4), REPLACE(5);
    }

    fun union(r: Rect): Boolean {
        return op(r, Op.UNION)
    }

    fun toRectF(): RectF {
        return bounds.toRectF()
    }

    private fun oper(rgnA: Region, rgnB: Region, op: Op): Boolean {
        // simple cases
        when (op) {
            Op.REPLACE -> {
                this.set(rgnB)
                return !rgnB.isEmpty
            }
            Op.REVERSE_DIFFERENCE -> {
                // collapse difference and reverse-difference into just difference
                return this.oper(rgnB, rgnA, Op.DIFFERENCE)
            }
            Op.DIFFERENCE -> {
                if (rgnA.isEmpty) {
                    this.setEmpty()
                    return false
                }
                if (rgnB.isEmpty || rgnA.bounds.intersection(rgnB.bounds).isEmpty) {
                    this.set(rgnA)
                    return !rgnA.isEmpty
                }
                if (rgnB.isRect() && rgnB.bounds.contains(rgnA.bounds)) {
                    this.setEmpty()
                    return false
                }
            }
            Op.INTERSECT -> {
                when {
                    rgnA.isEmpty || rgnB.isEmpty
                        || rgnA.bounds.intersection(rgnB.bounds).isEmpty -> {
                        this.setEmpty()
                        return false
                    }
                    rgnA.isRect() && rgnB.isRect() -> {
                        val rectIntersection = rgnA.bounds.intersection(rgnB.bounds)
                        this.set(rgnA.bounds.intersection(rgnB.bounds))
                        return !rectIntersection.isEmpty
                    }
                    rgnA.isRect() && rgnA.bounds.contains(rgnB.bounds) -> {
                        this.set(rgnB)
                        return !rgnB.isEmpty
                    }
                    rgnB.isRect() && rgnB.bounds.contains(rgnA.bounds) -> {
                        this.set(rgnA)
                        return !rgnA.isEmpty
                    }
                }
            }
            Op.UNION -> {
                when {
                    rgnA.isEmpty -> {
                        this.set(rgnB)
                        return !rgnB.isEmpty
                    }
                    rgnB.isEmpty -> {
                        this.set(rgnA)
                        return !rgnA.isEmpty
                    }
                    rgnA.isRect() && rgnA.bounds.contains(rgnB.bounds) -> {
                        this.set(rgnA)
                        return !rgnA.isEmpty
                    }
                    rgnB.isRect() && rgnB.bounds.contains(rgnA.bounds) -> {
                        this.set(rgnB)
                        return !rgnB.isEmpty
                    }
                }
            }
            Op.XOR -> {
                when {
                    rgnA.isEmpty -> {
                        this.set(rgnB)
                        return !rgnB.isEmpty
                    }
                    rgnB.isEmpty -> {
                        this.set(rgnA)
                        return !rgnA.isEmpty
                    }
                }
            }
        }

        val array = RunArray()
        val count = operate(rgnA.getRuns(), rgnB.getRuns(), array, op)
        require(count <= array.count)
        return this.setRuns(array, count)
    }

    class RunArray {
        private val kRunArrayStackCount = 256
        var runs: MutableList<Int> = MutableList(kRunArrayStackCount) { 0 }
        private var fCount: Int = kRunArrayStackCount

        val count: Int get() = fCount

        operator fun get(i: Int): Int {
            return runs[i]
        }

        fun resizeToAtLeast(_count: Int) {
            var count = _count
            if (count > fCount) {
                // leave at least 50% extra space for future growth.
                count += count shr 1
                val newRuns = MutableList(count) { 0 }
                runs.forEachIndexed { index, value ->
                    newRuns[index] = value
                }
                runs = newRuns
                fCount = count
            }
        }

        operator fun set(i: Int, value: Int) {
            runs[i] = value
        }

        fun subList(startIndex: Int, stopIndex: Int): RunArray {
            val subRuns = RunArray()
            subRuns.resizeToAtLeast(this.fCount)
            for (i in startIndex until stopIndex) {
                subRuns.runs[i - startIndex] = this.runs[i]
            }
            return subRuns
        }

        fun clone(): RunArray {
            val clone = RunArray()
            clone.runs = runs.toMutableList()
            clone.fCount = fCount
            return clone
        }
    }

    /**
     * Set this region to the result of performing the Op on the specified
     * regions. Return true if the result is not empty.
     */
    fun op(rgnA: Region, rgnB: Region, op: Op): Boolean {
        return this.oper(rgnA, rgnB, op)
    }

    private fun getRuns(): List<Int> {
        val runs: List<Int>
        if (this.isEmpty) {
            runs = MutableList(kRectRegionRuns) { 0 }
            runs[0] = SkRegion_kRunTypeSentinel
        } else if (this.isRect()) {
            runs = buildRectRuns(fBounds)
        } else {
            runs = fRunHead!!.readonlyRuns
        }

        return runs
    }

    private fun buildRectRuns(bounds: Rect): List<Int> {
        val runs = MutableList(kRectRegionRuns) { 0 }
        runs[0] = bounds.top
        runs[1] = bounds.bottom
        runs[2] = 1 // 1 interval for this scanline
        runs[3] = bounds.left
        runs[4] = bounds.right
        runs[5] = SkRegion_kRunTypeSentinel
        runs[6] = SkRegion_kRunTypeSentinel
        return runs
    }

    class RunHead(val isEmptyHead: Boolean = false) {
        fun setRuns(runs: RunArray, count: Int) {
            this.runs = runs
            this.fRunCount = count
        }

        fun computeRunBounds(): Rect {
            var runsIndex = 0
            val top = runs[runsIndex]
            runsIndex++

            var bot: Int
            var ySpanCount = 0
            var intervalCount = 0
            var left = Int.MAX_VALUE
            var right = Int.MIN_VALUE

            do {
                bot = runs[runsIndex]
                runsIndex++
                require(bot < SkRegion_kRunTypeSentinel)
                ySpanCount += 1

                val intervals = runs[runsIndex]
                runsIndex++
                require(intervals >= 0)
                require(intervals < SkRegion_kRunTypeSentinel)

                if (intervals > 0) {
                    val L = runs[runsIndex]
                    require(L < SkRegion_kRunTypeSentinel)
                    if (left > L) {
                        left = L
                    }

                    runsIndex += intervals * 2
                    val R = runs[runsIndex - 1]
                    require(R < SkRegion_kRunTypeSentinel)
                    if (right < R) {
                        right = R
                    }

                    intervalCount += intervals
                }
                require(SkRegion_kRunTypeSentinel == runs[runsIndex])
                runsIndex += 1 // skip x-sentinel

                // test Y-sentinel
            } while (SkRegion_kRunTypeSentinel > runs[runsIndex])

            fYSpanCount = ySpanCount
            fIntervalCount = intervalCount

            return Rect(left, top, right, bot)
        }

        fun clone(): RunHead {
            val clone = RunHead(isEmptyHead)
            clone.fIntervalCount = fIntervalCount
            clone.fYSpanCount = fYSpanCount
            clone.runs = runs.clone()
            clone.fRunCount = fRunCount
            return clone
        }

        /**
         *  Return the scanline that contains the Y value. This requires that the Y
         *  value is already known to be contained within the bounds of the region,
         *  and so this routine never returns nullptr.
         *
         *  It returns the beginning of the scanline, starting with its Bottom value.
         */
        fun findScanline(y: Int): List<Int> {
            val runs = readonlyRuns

            // if the top-check fails, we didn't do a quick check on the bounds
            require(y >= runs[0])

            var runsIndex = 1 // skip top-Y
            while (true) {
                val bottom = runs[runsIndex]
                // If we hit this, we've walked off the region, and our bounds check
                // failed.
                require(bottom < SkRegion_kRunTypeSentinel)
                if (y < bottom) {
                    break
                }
                runsIndex = SkipEntireScanline(runsIndex)
            }
            return runs.subList(runsIndex, runs.size - runsIndex)
        }

        /**
         *  Given a scanline (including its Bottom value at runs[0]), return the next
         *  scanline. Asserts that there is one (i.e. runs[0] < Sentinel)
         */
        fun SkipEntireScanline(_runsIndex: Int): Int {
            var runsIndex = _runsIndex
            // we are not the Y Sentinel
            require(runs[runsIndex] < SkRegion_kRunTypeSentinel)

            val intervals = runs[runsIndex + 1]
            require(runs[runsIndex + 2 + intervals * 2] == SkRegion_kRunTypeSentinel)

            // skip the entire line [B N [L R] S]
            runsIndex += 1 + 1 + intervals * 2 + 1
            return runsIndex
        }

        private var fIntervalCount: Int = 0
        private var fYSpanCount: Int = 0
        var runs = RunArray()
        var fRunCount: Int = 0

        val readonlyRuns: List<Int> get() = runs.runs
    }

    private fun setRuns(runs: RunArray, _count: Int): Boolean {
        require(_count > 0)

        var count = _count

        if (isRunCountEmpty(count)) {
            assert_sentinel(runs[count - 1], true)
            return this.setEmpty()
        }

        // trim off any empty spans from the top and bottom
        // weird I should need this, perhaps op() could be smarter...
        var trimmedRuns = runs
        if (count > kRectRegionRuns) {
            var stopIndex = count
            assert_sentinel(runs[0], false) // top
            assert_sentinel(runs[1], false) // bottom
            // runs[2] is uncomputed intervalCount

            var trimLeft = false
            if (runs[3] == SkRegion_kRunTypeSentinel) { // should be first left...
                trimLeft = true
                assert_sentinel(runs[1], false) // bot: a sentinal would mean two in a row
                assert_sentinel(runs[2], false) // intervalcount
                assert_sentinel(runs[3], false) // left
                assert_sentinel(runs[4], false) // right
            }

            assert_sentinel(runs[stopIndex - 1], true)
            assert_sentinel(runs[stopIndex - 2], true)

            var trimRight = false
            // now check for a trailing empty span
            if (runs[stopIndex - 5] == SkRegion_kRunTypeSentinel) {
                // eek, stop[-4] was a bottom with no x-runs
                trimRight = true
            }

            var startIndex = 0
            if (trimLeft) {
                startIndex += 3
                trimmedRuns = trimmedRuns.subList(startIndex, count) // skip empty initial span
                trimmedRuns[0] = runs[1] // set new top to prev bottom
            }
            if (trimRight) {
                // kill empty last span
                trimmedRuns[stopIndex - 4] = SkRegion_kRunTypeSentinel
                stopIndex -= 3
                assert_sentinel(runs[stopIndex - 1], true)    // last y-sentinel
                assert_sentinel(runs[stopIndex - 2], true)    // last x-sentinel
                assert_sentinel(runs[stopIndex - 3], false)   // last right
                assert_sentinel(runs[stopIndex - 4], false)   // last left
                assert_sentinel(runs[stopIndex - 5], false)   // last interval-count
                assert_sentinel(runs[stopIndex - 6], false)   // last bottom
                trimmedRuns = trimmedRuns.subList(startIndex, stopIndex)
            }

            count = stopIndex - startIndex
        }

        require(count >= kRectRegionRuns)

        if (runsAreARect(trimmedRuns, count)) {
            fBounds = Rect(trimmedRuns[3], trimmedRuns[0], trimmedRuns[4], trimmedRuns[1])
            return this.setRect(fBounds)
        }

        //  if we get here, we need to become a complex region
        if (!this.isComplex() || fRunHead!!.fRunCount != count) {
            fRunHead = RunHead()
            fRunHead!!.fRunCount = count
            require(this.isComplex())
        }

        // must call this before we can write directly into runs()
        // in case we are sharing the buffer with another region (copy on write)
        // fRunHead = fRunHead->ensureWritable();
        // memcpy(fRunHead, runs, count * sizeof(RunType))
        fRunHead!!.setRuns(trimmedRuns, count)
        fBounds = fRunHead!!.computeRunBounds()

        // Our computed bounds might be too large, so we have to check here.
        if (fBounds.isEmpty) {
            return this.setEmpty()
        }

        return true
    }

    private fun setRect(r: Rect): Boolean {
        if (r.isEmpty || SkRegion_kRunTypeSentinel == r.right ||
            SkRegion_kRunTypeSentinel == r.bottom) {
            return this.setEmpty()
        }
        fBounds = r
        fRunHead = null
        return true
    }

    private fun isRunCountEmpty(count: Int): Boolean {
        return count <= 2
    }

    private fun runsAreARect(runs: RunArray, count: Int): Boolean {
        require(count >= kRectRegionRuns)

        if (count == kRectRegionRuns) {
            assert_sentinel(runs[1], false) // bottom
            require(1 == runs[2])
            assert_sentinel(runs[3], false)    // left
            assert_sentinel(runs[4], false)    // right
            assert_sentinel(runs[5], true)
            assert_sentinel(runs[6], true)

            require(runs[0] < runs[1])    // valid height
            require(runs[3] < runs[4])    // valid width

            return true
        }
        return false
    }

    class RgnOper(var top: Int, private val runArray: RunArray, op: Op) {
        private val fMin = gOpMinMax[op]!!.min
        private val fMax = gOpMinMax[op]!!.max

        private var fStartDst = 0
        private var fPrevDst = 1
        private var fPrevLen = 0

        fun addSpan(
            bottom: Int,
            aRuns: List<Int>,
            bRuns: List<Int>,
            aRunsIndex: Int,
            bRunsIndex: Int
        ) {
            // skip X values and slots for the next Y+intervalCount
            val start = fPrevDst + fPrevLen + 2
            // start points to beginning of dst interval
            val stop =
                operateOnSpan(aRuns, bRuns, aRunsIndex, bRunsIndex, runArray, start, fMin, fMax)
            val len = stop - start
            require(len >= 1 && (len and 1) == 1)
            require(SkRegion_kRunTypeSentinel == runArray[stop - 1])

            // Assert memcmp won't exceed fArray->count().
            require(runArray.count >= start + len - 1)
            if (fPrevLen == len &&
                (1 == len || runArray.subList(fPrevDst, fPrevDst + len).runs
                    == runArray.subList(start, start + len).runs)) {
                // update Y value
                runArray[fPrevDst - 2] = bottom
            } else { // accept the new span
                if (len == 1 && fPrevLen == 0) {
                    top = bottom // just update our bottom
                } else {
                    runArray[start - 2] = bottom
                    runArray[start - 1] = len / 2 // len shr 1
                    fPrevDst = start
                    fPrevLen = len
                }
            }
        }

        fun flush(): Int {
            runArray[fStartDst] = top
            // Previously reserved enough for TWO sentinals.
            // SkASSERT(fArray->count() > SkToInt(fPrevDst + fPrevLen));
            runArray[fPrevDst + fPrevLen] = SkRegion_kRunTypeSentinel
            return fPrevDst - fStartDst + fPrevLen + 1
        }

        class SpanRect(
            private val aRuns: List<Int>,
            private val bRuns: List<Int>,
            aIndex: Int,
            bIndex: Int
        ) {
            var fLeft: Int = 0
            var fRight: Int = 0
            var fInside: Int = 0

            var fALeft: Int
            var fARight: Int
            var fBLeft: Int
            var fBRight: Int
            var fARuns: Int
            var fBRuns: Int

            init {
                fALeft = aRuns[aIndex]
                fARight = aRuns[aIndex + 1]
                fBLeft = bRuns[bIndex]
                fBRight = bRuns[bIndex + 1]
                fARuns = aIndex + 2
                fBRuns = bIndex + 2
            }

            fun done(): Boolean {
                require(fALeft <= SkRegion_kRunTypeSentinel)
                require(fBLeft <= SkRegion_kRunTypeSentinel)
                return fALeft == SkRegion_kRunTypeSentinel &&
                    fBLeft == SkRegion_kRunTypeSentinel
            }

            fun next() {
                var inside = 0
                var left = 0
                var right = 0
                var aFlush = false
                var bFlush = false

                var aLeft = fALeft
                var aRight = fARight
                var bLeft = fBLeft
                var bRight = fBRight

                if (aLeft < bLeft) {
                    inside = 1
                    left = aLeft
                    if (aRight <= bLeft) { // [...] <...>
                        right = aRight
                        aFlush = true
                    } else { // [...<..]...> or [...<...>...]
                        aLeft = bLeft
                        right = bLeft
                    }
                } else if (bLeft < aLeft) {
                    inside = 2
                    left = bLeft
                    if (bRight <= aLeft) { // [...] <...>
                        right = bRight
                        bFlush = true
                    } else { // [...<..]...> or [...<...>...]
                        bLeft = aLeft
                        right = aLeft
                    }
                } else { // a_left == b_left
                    inside = 3
                    left = aLeft // or b_left
                    if (aRight <= bRight) {
                        bLeft = aRight
                        right = aRight
                        aFlush = true
                    }
                    if (bRight <= aRight) {
                        aLeft = bRight
                        right = bRight
                        bFlush = true
                    }
                }

                if (aFlush) {
                    aLeft = aRuns[fARuns]
                    fARuns++
                    aRight = aRuns[fARuns]
                    fARuns++
                }
                if (bFlush) {
                    bLeft = bRuns[fBRuns]
                    fBRuns++
                    bRight = bRuns[fBRuns]
                    fBRuns++
                }

                require(left <= right)

                // now update our state
                fALeft = aLeft
                fARight = aRight
                fBLeft = bLeft
                fBRight = bRight

                fLeft = left
                fRight = right
                fInside = inside
            }
        }

        private fun operateOnSpan(
            a_runs: List<Int>,
            b_runs: List<Int>,
            a_run_index: Int,
            b_run_index: Int,
            array: RunArray,
            dstOffset: Int,
            min: Int,
            max: Int
        ): Int {
            // This is a worst-case for this span plus two for TWO terminating sentinels.
            array.resizeToAtLeast(
                dstOffset + distance_to_sentinel(a_runs, a_run_index) +
                    distance_to_sentinel(b_runs, b_run_index) + 2)
            var dstIndex = dstOffset

            val rec = SpanRect(a_runs, b_runs, a_run_index, b_run_index)
            var firstInterval = true

            while (!rec.done()) {
                rec.next()

                val left = rec.fLeft
                val right = rec.fRight

                // add left,right to our dst buffer (checking for coincidence
                if ((rec.fInside - min).toUInt() <= (max - min).toUInt() &&
                    left < right) { // skip if equal
                    if (firstInterval || array[dstIndex - 1] < left) {
                        array[dstIndex] = left
                        dstIndex++
                        array[dstIndex] = right
                        dstIndex++
                        firstInterval = false
                    } else {
                        // update the right edge
                        array[dstIndex - 1] = right
                    }
                }
            }

            array[dstIndex] = SkRegion_kRunTypeSentinel
            dstIndex++
            return dstIndex // dst - &(*array)[0]
        }

        private fun distance_to_sentinel(runs: List<Int>, startIndex: Int): Int {
            var index = startIndex
            if (runs.size <= index) {
                println("We fucked up...")
            }
            while (runs[index] != SkRegion_kRunTypeSentinel) {
                if (runs.size <= index + 2) {
                    println("We fucked up...")
                    return 256
                }
                index += 2
            }
            return index - startIndex
        }
    }

    private fun operate(
        aRuns: List<Int>,
        bRuns: List<Int>,
        dst: RunArray,
        op: Op,
        _aRunsIndex: Int = 0,
        _bRunsIndex: Int = 0
    ): Int {
        var aRunsIndex = _aRunsIndex
        var bRunsIndex = _bRunsIndex

        var aTop = aRuns[aRunsIndex]
        aRunsIndex++
        var aBot = aRuns[aRunsIndex]
        aRunsIndex++
        var bTop = bRuns[bRunsIndex]
        bRunsIndex++
        var bBot = bRuns[bRunsIndex]
        bRunsIndex++

        aRunsIndex++ // skip the intervalCount
        bRunsIndex++ // skip the intervalCount

        val gEmptyScanline: List<Int> = listOf(
            0, // fake bottom value
            0, // zero intervals
            SkRegion_kRunTypeSentinel,
            // just need a 2nd value, since spanRec.init() reads 2 values, even
            // though if the first value is the sentinel, it ignores the 2nd value.
            // w/o the 2nd value here, we might read uninitialized memory.
            // This happens when we are using gSentinel, which is pointing at
            // our sentinel value.
            0
        )
        val gSentinel = 2

        // Now aRuns and bRuns to their intervals (or sentinel)

        assert_sentinel(aTop, false)
        assert_sentinel(aBot, false)
        assert_sentinel(bTop, false)
        assert_sentinel(bBot, false)

        val oper = RgnOper(min(aTop, bTop), dst, op)

        var prevBot = SkRegion_kRunTypeSentinel // so we fail the first test

        while (aBot < SkRegion_kRunTypeSentinel || bBot < SkRegion_kRunTypeSentinel) {
            var top: Int
            var bot = 0

            var run0 = gEmptyScanline
            var run0Index = gSentinel
            var run1 = gEmptyScanline
            var run1Index = gSentinel
            var aFlush = false
            var bFlush = false

            if (aTop < bTop) {
                top = aTop
                run0 = aRuns
                run0Index = aRunsIndex
                if (aBot <= bTop) { // [...] <...>
                    bot = aBot
                    aFlush = true
                } else { // [...<..]...> or [...<...>...]
                    aTop = bTop
                    bot = bTop
                }
            } else if (bTop < aTop) {
                top = bTop
                run1 = bRuns
                run1Index = bRunsIndex
                if (bBot <= aTop) { // [...] <...>
                    bot = bBot
                    bFlush = true
                } else { // [...<..]...> or [...<...>...]
                    bTop = aTop
                    bot = aTop
                }
            } else { // aTop == bTop
                top = aTop // or bTop
                run0 = aRuns
                run0Index = aRunsIndex
                run1 = bRuns
                run1Index = bRunsIndex
                if (aBot <= bBot) {
                    bTop = aBot
                    bot = aBot
                    aFlush = true
                }
                if (bBot <= aBot) {
                    aTop = bBot
                    bot = bBot
                    bFlush = true
                }
            }

            if (top > prevBot) {
                oper.addSpan(top, gEmptyScanline, gEmptyScanline, gSentinel, gSentinel)
            }
            oper.addSpan(bot, run0, run1, run0Index, run1Index)

            if (aFlush) {
                aRunsIndex = skipIntervals(aRuns, aRunsIndex)
                aTop = aBot
                aBot = aRuns[aRunsIndex]
                aRunsIndex++ // skip to next index
                aRunsIndex++ // skip uninitialized intervalCount
                if (aBot == SkRegion_kRunTypeSentinel) {
                    aTop = aBot
                }
            }
            if (bFlush) {
                bRunsIndex = skipIntervals(bRuns, bRunsIndex)
                bTop = bBot
                bBot = bRuns[bRunsIndex]
                bRunsIndex++ // skip to next index
                bRunsIndex++ // skip uninitialized intervalCount
                if (bBot == SkRegion_kRunTypeSentinel) {
                    bTop = bBot
                }
            }

            prevBot = bot
        }

        return oper.flush()
    }

    private fun skipIntervals(runs: List<Int>, index: Int): Int {
        val intervals = runs[index - 1]
        return index + intervals * 2 + 1
    }

    /**
     * Perform the specified Op on this region and the specified region. Return
     * true if the result of the op is not empty.
     */
    fun op(region: Region, op: Op): Boolean {
        return op(this, region, op)
    }

    /**
     * Perform the specified Op on this region and the specified rect. Return
     * true if the result of the op is not empty.
     */
    fun op(left: Int, top: Int, right: Int, bottom: Int, op: Op): Boolean {
        return op(Rect(left, top, right, bottom), op)
    }

    /**
     * Perform the specified Op on this region and the specified rect. Return
     * true if the result of the op is not empty.
     */
    fun op(r: Rect, op: Op): Boolean {
        return op(from(r), op)
    }

    /**
     * Set this region to the result of performing the Op on the specified rect
     * and region. Return true if the result is not empty.
     */
    fun op(rect: Rect, region: Region, op: Op): Boolean {
        return op(from(rect), region, op)
    }

    fun minus(other: Region): Region {
        val thisRegion = from(this)
        thisRegion.op(other, Region.Op.XOR)
        return thisRegion
    }

    companion object {
        val EMPTY get() = Region()

        const val SkRegion_kRunTypeSentinel = 0x7FFFFFFF

        const val kRectRegionRuns = 7

        class MinMax(val min: Int, val max: Int)

        val gOpMinMax = mapOf(
            Op.DIFFERENCE to MinMax(1, 1),
            Op.INTERSECT to MinMax(3, 3),
            Op.UNION to MinMax(1, 3),
            Op.XOR to MinMax(1, 2)
        )

        fun from(
            left: Int,
            top: Int,
            right: Int,
            bottom: Int
        ): Region = from(Rect(left, top, right, bottom))

        fun from(region: Region): Region = Region().also { it.set(region) }

        fun from(rect: Rect? = null): Region = Region().also {
            it.fRunHead = null
            it.setRect(rect ?: Rect.EMPTY)
        }

        fun from(rect: RectF?): Region = from(rect?.toRect())

        fun from(): Region = from(Rect.EMPTY)

        private fun SkRegionValueIsSentinel(value: Int): Boolean {
            return value == SkRegion_kRunTypeSentinel
        }

        private fun assert_sentinel(value: Int, isSentinel: Boolean) {
            require(SkRegionValueIsSentinel(value) == isSentinel)
        }

        private fun getRectsFromString(regionString: String): Array<Rect> {
            val rects: ArrayList<Rect> = ArrayList()

            if (regionString == "SkRegion()") {
                return rects.toTypedArray()
            }

            var nativeRegionString = regionString.replace("SkRegion", "")
            nativeRegionString = nativeRegionString.substring(2, nativeRegionString.length - 2)
            nativeRegionString = nativeRegionString.replace(")(", ",")

            var rect = Rect.EMPTY
            for ((i, coord) in nativeRegionString.split(",").withIndex()) {
                when (i % 4) {
                    0 -> rect = Rect(coord.toInt(), 0, 0, 0)
                    1 -> rect = Rect(rect.left, coord.toInt(), 0, 0)
                    2 -> rect = Rect(rect.left, rect.top, coord.toInt(), 0)
                    3 -> {
                        rect = Rect(rect.left, rect.top, rect.right, coord.toInt())
                        rects.add(rect)
                    }
                }
            }

            return rects.toTypedArray()
        }
    }
}
