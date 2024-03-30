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

package com.android.server.wm.traces.common.tags

import com.android.server.wm.traces.common.ITrace

/**
 * Holds the entire list of [TagState]s representing an entire trace that has been tagged.
 * @param entries Array of tagged states within the trace
 */
data class TagTrace(
    override val entries: Array<TagState>
) : ITrace<TagState>,
    List<TagState> by entries.toList() {
    override fun toString(): String = "FlickerTagTrace(${entries.firstOrNull()?.timestamp ?: 0}, " +
            "${entries.lastOrNull()?.timestamp ?: 0})"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TagTrace) return false
        if (!entries.contentEquals(other.entries)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = entries.contentDeepHashCode()
        return result
    }
}