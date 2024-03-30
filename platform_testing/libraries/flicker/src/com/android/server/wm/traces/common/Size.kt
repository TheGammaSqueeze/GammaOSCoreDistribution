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
 * Wrapper for SizeProto (frameworks/native/services/surfaceflinger/layerproto/common.proto)
 *
 * This class is used by flicker and Winscope
 */
open class Size(val width: Int, val height: Int) {
    open val isEmpty: Boolean
        get() = height == 0 || width == 0

    val isNotEmpty: Boolean
        get() = !isEmpty

    open fun prettyPrint(): String = "$width x $height"

    override fun toString(): String = if (isEmpty) "[empty]" else prettyPrint()

    override fun equals(other: Any?): Boolean =
        other is Size &&
            other.height == height &&
            other.width == width

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        return result
    }

    companion object {
        val EMPTY: Size = Size(0, 0)
    }
}