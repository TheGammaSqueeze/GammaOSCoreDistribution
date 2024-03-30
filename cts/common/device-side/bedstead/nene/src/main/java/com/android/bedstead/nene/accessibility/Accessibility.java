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

package com.android.bedstead.nene.accessibility;

import static android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK;

import android.view.accessibility.AccessibilityManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.logging.Logger;

import java.util.Set;
import java.util.stream.Collectors;

/** Test APIs related to accessibility. */
@Experimental
public final class Accessibility {

    public static final Accessibility sInstance = new Accessibility();

    private static final AccessibilityManager sAccessibilityManager =
            TestApis.context().instrumentedContext().getSystemService(AccessibilityManager.class);

    private final Logger mLogger = Logger.forInstance(this);

    private Accessibility() {
        mLogger.constructor();
    }

    /**
     * Get installed accessibility services.
     *
     * <p>See {@link AccessibilityManager#getInstalledAccessibilityServiceList()}.
     */
    public Set<AccessibilityService> installedAccessibilityServices() {
        return mLogger.method("installedAccessibilityServices", () ->
                sAccessibilityManager
                        .getInstalledAccessibilityServiceList().stream()
                        .map(AccessibilityService::new)
                        .collect(Collectors.toSet()));
    }

    /**
     * Get enabled accessibility services.
     *
     * <p>See {@link AccessibilityManager#getEnabledAccessibilityServiceList(int)}.
     */
    public Set<AccessibilityService> enabledAccessibilityServices() {
        return mLogger.method("enabledAccessibilityServices", () ->
                sAccessibilityManager
                        .getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)
                        .stream().map(AccessibilityService::new)
                        .collect(Collectors.toSet()));
    }
}
