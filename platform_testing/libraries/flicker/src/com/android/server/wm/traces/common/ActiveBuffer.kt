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
 * Wrapper for ActiveBufferProto (frameworks/native/services/surfaceflinger/layerproto/layers.proto)
 *
 * This class is used by flicker and Winscope
 */
class ActiveBuffer(
    width: Int,
    height: Int,
    val stride: Int,
    val format: Int
) : Size(width, height) {
    override fun prettyPrint(): String =
        "w:$width, h:$height, stride:$stride, format:$format"

    override fun equals(other: Any?): Boolean =
        other is ActiveBuffer &&
        other.height == height &&
        other.width == width &&
        other.stride == stride &&
        other.format == format

    override fun hashCode(): Int {
        var result = height
        result = 31 * result + width
        result = 31 * result + stride
        result = 31 * result + format
        return result
    }

    override fun toString(): String = prettyPrint()

    companion object {
        val EMPTY: ActiveBuffer = ActiveBuffer(0, 0, 0, 0)
    }
}
