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

package com.android.permissioncontroller.role.model;

import android.content.Context;
import android.os.Process;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.permissioncontroller.role.utils.NotificationUtils;
import com.android.permissioncontroller.role.utils.UserUtils;

/**
 * Class for behavior of the "App Streaming" Companion device profile role.
 */
public class CompanionDeviceAppStreamingRoleBehavior implements RoleBehavior {

    @Override
    public void grant(@NonNull Role role, @NonNull String packageName, @NonNull Context context) {
        UserHandle user = Process.myUserHandle();
        if (!UserUtils.isManagedProfile(user, context)) {
            NotificationUtils.grantNotificationAccessForPackage(context, packageName);
        }
    }

    @Override
    public void revoke(@NonNull Role role, @NonNull String packageName, @NonNull Context context) {
        UserHandle user = Process.myUserHandle();
        if (!UserUtils.isManagedProfile(user, context)) {
            NotificationUtils.revokeNotificationAccessForPackage(context, packageName);
        }
    }
}
