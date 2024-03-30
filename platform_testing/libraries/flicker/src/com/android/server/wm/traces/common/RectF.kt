/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.wm.traces.common

/**
 * Wrapper for FloatRectProto (frameworks/native/services/surfaceflinger/layerproto/layers.proto)
 *
 * This class is used by flicker and Winscope
 */
data class RectF(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f
) {
    val height: Float get() = bottom - top
    val width: Float get() = right - left

    /**
     * Returns true if the rectangle is empty (left >= right or top >= bottom)
     */
    val isEmpty: Boolean
        get() = width <= 0f || height <= 0f
    val isNotEmpty: Boolean
        get() = !isEmpty

    /**
     * Returns a [Rect] version fo this rectangle.
     *
     * All fractional parts are rounded to 0
     */
    fun toRect(): Rect {
        return Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
    }

    /**
     * Returns true iff the specified rectangle r is inside or equal to this
     * rectangle. An empty rectangle never contains another rectangle.
     *
     * @param r The rectangle being tested for containment.
     * @return true iff the specified rectangle r is inside or equal to this
     *              rectangle
     */
    operator fun contains(r: RectF): Boolean {
        // check for empty first
        return this.left < this.right && this.top < this.bottom && // now check for containment
                left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom
    }

    /**
     * Returns a [RectF] where the dimensions don't exceed those of [crop]
     *
     * @param crop The crop that should be applied to this layer
     */
    fun crop(crop: RectF): RectF {
        val newLeft = maxOf(left, crop.left)
        val newTop = maxOf(top, crop.top)
        val newRight = minOf(right, crop.right)
        val newBottom = minOf(bottom, crop.bottom)
        return RectF(newLeft, newTop, newRight, newBottom)
    }

    /**
     * If the rectangle specified by left,top,right,bottom intersects this
     * rectangle, return true and set this rectangle to that intersection,
     * otherwise return false and do not change this rectangle. No check is
     * performed to see if either rectangle is empty. Note: To just test for
     * intersection, use intersects()
     *
     * @param left The left side of the rectangle being intersected with this
     * rectangle
     * @param top The top of the rectangle being intersected with this rectangle
     * @param right The right side of the rectangle being intersected with this
     * rectangle.
     * @param bottom The bottom of the rectangle being intersected with this
     * rectangle.
     * @return A rectangle with the intersection coordinates
     */
    fun intersection(left: Float, top: Float, right: Float, bottom: Float): RectF {
        if (this.left < right && left < this.right && this.top <= bottom && top <= this.bottom) {
            var intersectionLeft = this.left
            var intersectionTop = this.top
            var intersectionRight = this.right
            var intersectionBottom = this.bottom

            if (this.left < left) {
                intersectionLeft = left
            }
            if (this.top < top) {
                intersectionTop = top
            }
            if (this.right > right) {
                intersectionRight = right
            }
            if (this.bottom > bottom) {
                intersectionBottom = bottom
            }
            return RectF(intersectionLeft, intersectionTop, intersectionRight, intersectionBottom)
        }
        return EMPTY
    }

    /**
     * If the specified rectangle intersects this rectangle, return true and set
     * this rectangle to that intersection, otherwise return false and do not
     * change this rectangle. No check is performed to see if either rectangle
     * is empty. To just test for intersection, use intersects()
     *
     * @param r The rectangle being intersected with this rectangle.
     * @return A rectangle with the intersection coordinates
     */
    fun intersection(r: RectF): RectF = intersection(r.left, r.top, r.right, r.bottom)

    fun prettyPrint(): String =
        if (isEmpty) {
            "[empty]"
        } else {
            val left = FloatFormatter.format(left)
            val top = FloatFormatter.format(top)
            val right = FloatFormatter.format(right)
            val bottom = FloatFormatter.format(bottom)
            "($left, $top) - ($right, $bottom)"
        }

    override fun toString(): String = prettyPrint()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RectF) return false

        if (left != other.left) return false
        if (top != other.top) return false
        if (right != other.right) return false
        if (bottom != other.bottom) return false

        return true
    }

    override fun hashCode(): Int {
        var result = left.hashCode()
        result = 31 * result + top.hashCode()
        result = 31 * result + right.hashCode()
        result = 31 * result + bottom.hashCode()
        return result
    }

    companion object {
        val EMPTY: RectF = RectF()
    }
}
