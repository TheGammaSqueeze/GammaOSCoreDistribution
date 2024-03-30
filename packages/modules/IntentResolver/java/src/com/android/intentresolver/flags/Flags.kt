/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.intentresolver.flags

import com.android.systemui.flags.UnreleasedFlag

// Flag id, name and namespace should be kept in sync with [com.android.systemui.flags.Flags] to
// make the flags available in the flag flipper app (see go/sysui-flags).
object Flags {
    const val SHARESHEET_CUSTOM_ACTIONS_NAME = "sharesheet_custom_actions"
    const val SHARESHEET_RESELECTION_ACTION_NAME = "sharesheet_reselection_action"
    const val SHARESHEET_IMAGE_AND_TEXT_PREVIEW_NAME = "sharesheet_image_text_preview"
    const val SHARESHEET_SCROLLABLE_IMAGE_PREVIEW_NAME = "sharesheet_scrollable_image_preview"

    // TODO(b/266983432) Tracking Bug
    @JvmField
    val SHARESHEET_CUSTOM_ACTIONS = unreleasedFlag(
        1501, SHARESHEET_CUSTOM_ACTIONS_NAME, teamfood = true
    )

    // TODO(b/266982749) Tracking Bug
    @JvmField
    val SHARESHEET_RESELECTION_ACTION = unreleasedFlag(
        1502, SHARESHEET_RESELECTION_ACTION_NAME, teamfood = true
    )

    // TODO(b/266983474) Tracking Bug
    @JvmField
    val SHARESHEET_IMAGE_AND_TEXT_PREVIEW = unreleasedFlag(
        id = 1503, name = SHARESHEET_IMAGE_AND_TEXT_PREVIEW_NAME, teamfood = true
    )

    // TODO(b/267355521) Tracking Bug
    @JvmField
    val SHARESHEET_SCROLLABLE_IMAGE_PREVIEW = unreleasedFlag(
        1504, SHARESHEET_SCROLLABLE_IMAGE_PREVIEW_NAME, teamfood = true
    )

    private fun unreleasedFlag(id: Int, name: String, teamfood: Boolean = false) =
        UnreleasedFlag(id, name, "systemui", teamfood)
}
