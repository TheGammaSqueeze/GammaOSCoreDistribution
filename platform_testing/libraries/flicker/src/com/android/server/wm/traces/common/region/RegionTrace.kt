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

import com.android.server.wm.traces.common.FlickerComponentName
import com.android.server.wm.traces.common.ITrace

/**
 * Contains a collection of parsed Region trace entries.
 *
 * Each entry is parsed into a list of [RegionEntry] objects.
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot
 * access internal Java/Android functionality
 *
 */
data class RegionTrace(
    val components: Array<out FlickerComponentName>,
    override val entries: Array<RegionEntry>
) : ITrace<RegionEntry>,
        List<RegionEntry> by entries.toList() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RegionTrace) return false

        if (!components.contentEquals(other.components)) return false
        if (!entries.contentEquals(other.entries)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = components.contentHashCode()
        result = 31 * result + entries.contentHashCode()
        return result
    }
}