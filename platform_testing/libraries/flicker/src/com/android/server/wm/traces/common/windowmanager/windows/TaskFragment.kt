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

package com.android.server.wm.traces.common.windowmanager.windows

/**
 * Represents a task fragment in the window manager hierarchy
 *
 * This is a generic object that is reused by both Flicker and Winscope and cannot
 * access internal Java/Android functionality
 *
 */
open class TaskFragment(
    override val activityType: Int,
    val displayId: Int,
    val minWidth: Int,
    val minHeight: Int,
    windowContainer: WindowContainer
) : WindowContainer(windowContainer) {
    val tasks: Array<Task>
        get() = this.children.reversed().filterIsInstance<Task>().toTypedArray()
    val taskFragments: Array<TaskFragment>
        get() = this.children.reversed().filterIsInstance<TaskFragment>().toTypedArray()
    val activities: Array<Activity>
        get() = this.children.reversed().filterIsInstance<Activity>().toTypedArray()

    override fun toString(): String {
        return "${this::class.simpleName}: {$token $title} bounds=$bounds"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TaskFragment) return false

        if (activityType != other.activityType) return false
        if (displayId != other.displayId) return false
        if (minWidth != other.minWidth) return false
        if (minHeight != other.minHeight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + activityType
        result = 31 * result + displayId
        result = 31 * result + minWidth
        result = 31 * result + minHeight
        return result
    }
}