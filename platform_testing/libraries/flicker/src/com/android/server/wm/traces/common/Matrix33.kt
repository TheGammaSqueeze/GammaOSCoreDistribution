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

package com.android.server.wm.traces.common

/**
 * Representation of a matrix 3x3 used for layer transforms
 *
 *          |dsdx dsdy  tx|
 * matrix = |dtdx dtdy  ty|
 *          |0    0     1 |
 */
class Matrix33(
    dsdx: Float,
    dtdx: Float,
    val tx: Float,

    dsdy: Float,
    dtdy: Float,
    val ty: Float
) : Matrix22(dsdx, dtdx, dsdy, dtdy) {
    override fun prettyPrint(): String {
        val parentPrint = super.prettyPrint()
        val tx = FloatFormatter.format(dsdx)
        val ty = FloatFormatter.format(dtdx)
        return "$parentPrint   tx:$tx   ty:$ty"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matrix33) return false
        if (!super.equals(other)) return false

        if (tx != other.tx) return false
        if (ty != other.ty) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + tx.hashCode()
        result = 31 * result + ty.hashCode()
        return result
    }

    companion object {
        val EMPTY: Matrix33 = Matrix33(0f, 0f, 0f, 0f, 0f, 0f)
    }
}