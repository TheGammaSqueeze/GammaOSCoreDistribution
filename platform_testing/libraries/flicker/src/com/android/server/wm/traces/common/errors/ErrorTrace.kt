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

package com.android.server.wm.traces.common.errors

import com.android.server.wm.traces.common.ITrace

/**
 * Represents all the states with errors in an entire trace.
 * @param entries States with errors contained in this trace
 */
data class ErrorTrace(
    override val entries: Array<ErrorState>
) : ITrace<ErrorState>,
    List<ErrorState> by entries.toList() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ErrorTrace) return false
        if (!entries.contentEquals(other.entries)) return false
        return true
    }

    override fun hashCode(): Int = entries.contentDeepHashCode()

    override fun toString(): String = "FlickerErrorTrace(First: ${entries.firstOrNull()}," +
            "End: ${entries.lastOrNull()})"
}