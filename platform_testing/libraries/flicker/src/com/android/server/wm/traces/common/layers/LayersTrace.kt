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

package com.android.server.wm.traces.common.layers

import com.android.server.wm.traces.common.ITrace

/**
 * Contains a collection of parsed Layers trace entries and assertions to apply over a single entry.
 *
 * Each entry is parsed into a list of [LayerTraceEntry] objects.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot
 * access internal Java/Android functionality
 *
 */
data class LayersTrace(
    override val entries: Array<BaseLayerTraceEntry>
) : ITrace<BaseLayerTraceEntry>, List<BaseLayerTraceEntry> by entries.toList() {
    constructor(entry: BaseLayerTraceEntry): this(arrayOf(entry))

    override fun toString(): String {
        return "LayersTrace(Start: ${entries.firstOrNull()}, " +
            "End: ${entries.lastOrNull()})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayersTrace) return false

        if (!entries.contentEquals(other.entries)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = entries.contentHashCode()
        return result
    }

    /**
     * Split the trace by the start and end timestamp.
     *
     * @param from the start timestamp
     * @param to the end timestamp
     * @return the subtrace trace(from, to)
     */
    fun filter(from: Long, to: Long): LayersTrace {
        return LayersTrace(
            this.entries
                .dropWhile { it.timestamp < from }
                .dropLastWhile { it.timestamp > to }
                .toTypedArray()
        )
    }
}