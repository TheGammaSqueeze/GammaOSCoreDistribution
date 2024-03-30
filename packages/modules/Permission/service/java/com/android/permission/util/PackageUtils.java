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

package com.android.permission.util;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

/**
 * Utility class for dealing with packages.
 */
public final class PackageUtils {
    private PackageUtils() {}

    /**
     * Returns {@code true} if the calling package is able to query for details about the package.
     *
     * @see PackageManager#canPackageQuery
     */
    public static boolean canCallingOrSelfPackageQuery(@NonNull String packageName,
            @UserIdInt int userId, @NonNull Context context) {
        final Context userContext = context.createContextAsUser(UserHandle.of(userId), 0);
        final PackageManager userPackageManager = userContext.getPackageManager();
        try {
            userPackageManager.getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }
}
