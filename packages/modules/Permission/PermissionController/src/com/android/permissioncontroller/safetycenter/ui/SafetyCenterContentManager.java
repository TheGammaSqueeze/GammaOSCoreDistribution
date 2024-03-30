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

import java.util.ArrayList;
import java.util.List;

/** A helper for interacting with UI content from the SafetyCenter API. */
public final class SafetyCenterContentManager {

    private static final Object sLock = new Object();

    /** Singleton instance. */
    static SafetyCenterContentManager sInstance;

    private SafetyCenterContentManager() {}

    /** Returns a singleton instance of {@link SafetyCenterContentManager}. */
    public static SafetyCenterContentManager getInstance() {
        if (sInstance != null) {
            return sInstance;
        }

        synchronized (sLock) {
            // Previously null sInstance can become non-null before lock is acquired.
            if (sInstance == null) {
                sInstance = new SafetyCenterContentManager();
            }
            return sInstance;
        }
    }

    /** Returns the list of safety entries. */
    public List<SafetyEntry> getSafetyEntries() {
        // TODO(b/206775474): Return entries from API
        return new ArrayList<>();
    }
}
