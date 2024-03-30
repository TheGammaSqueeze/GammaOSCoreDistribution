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

package com.android.permissioncontroller.safetycenter.ui;

import com.android.permissioncontroller.R;

/** A severity level used for Safety Center entries and warnings. */
enum SeverityLevel {

    SEVERITY_LEVEL_UNKNOWN(
            R.drawable.ic_safety_empty,
            R.drawable.ic_safety_empty
    ),
    NONE(
            R.drawable.ic_safety_null_state,
            R.drawable.ic_safety_null_state
    ),
    INFORMATION(
            R.drawable.ic_safety_info,
            R.drawable.ic_safety_info_outline
    ),
    RECOMMENDATION(
            R.drawable.ic_safety_recommendation,
            R.drawable.ic_safety_recommendation_outline
    ),
    CRITICAL_WARNING(
            R.drawable.ic_safety_warn,
            R.drawable.ic_safety_warn_outline
    );

    private final int mEntryIconResId;
    private final int mWarningCardIconResId;

    SeverityLevel(int entryIconResId, int warningCardIconResId) {
        mEntryIconResId = entryIconResId;
        mWarningCardIconResId = warningCardIconResId;
    }

    /** Returns the res id of the icon that should be used for a safety entry of this severity. */
    public int getEntryIconResId() {
        return mEntryIconResId;
    }

    /** Returns the res id of the icon that should be used for a warning card of this severity. */
    public int getWarningCardIconResId() {
        return mWarningCardIconResId;
    }

}
