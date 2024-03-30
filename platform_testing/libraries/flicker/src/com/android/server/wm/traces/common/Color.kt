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
 * Wrapper for ColorProto (frameworks/native/services/surfaceflinger/layerproto/common.proto)
 *
 * This class is used by flicker and Winscope
 */
class Color(r: Float, g: Float, b: Float, val a: Float) : Color3(r, g, b) {
    override val isEmpty: Boolean
        get() = a == 0f || r < 0 || g < 0 || b < 0

    override val isNotEmpty: Boolean
        get() = !isEmpty

    override fun prettyPrint(): String {
        val parentPrint = super.prettyPrint()
        return "$parentPrint a:$a"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Color) return false
        if (!super.equals(other)) return false

        if (a != other.a) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + a.hashCode()
        return result
    }

    companion object {
        val EMPTY: Color = Color(r = -1f, g = -1f, b = -1f, a = 0f)
    }
}
