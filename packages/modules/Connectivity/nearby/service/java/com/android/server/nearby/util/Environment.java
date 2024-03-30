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

package com.android.server.nearby.util;

import android.content.ApexEnvironment;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;

import java.io.File;

/**
 * Provides function to make sure the function caller is from the same apex.
 */
public class Environment {
    /**
     * NEARBY apex name.
     */
    private static final String NEARBY_APEX_NAME = "com.android.tethering";

    /**
     * The path where the Nearby apex is mounted.
     * Current value = "/apex/com.android.tethering"
     */
    private static final String NEARBY_APEX_PATH =
            new File("/apex", NEARBY_APEX_NAME).getAbsolutePath();

    /**
     * Nearby shared folder.
     */
    public static File getNearbyDirectory() {
        return ApexEnvironment.getApexEnvironment(NEARBY_APEX_NAME).getDeviceProtectedDataDir();
    }

    /**
     * Nearby user specific folder.
     */
    public static File getNearbyDirectory(int userId) {
        return ApexEnvironment.getApexEnvironment(NEARBY_APEX_NAME)
                .getCredentialProtectedDataDirForUser(UserHandle.of(userId));
    }

    /**
     * Returns true if the app is in the nearby apex, false otherwise.
     * Checks if the app's path starts with "/apex/com.android.tethering".
     */
    public static boolean isAppInNearbyApex(ApplicationInfo appInfo) {
        return appInfo.sourceDir.startsWith(NEARBY_APEX_PATH);
    }
}
