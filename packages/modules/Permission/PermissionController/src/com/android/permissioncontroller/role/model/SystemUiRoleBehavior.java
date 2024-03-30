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
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;

import java.util.Arrays;
import java.util.List;

/** The role behavior for system ui. */
public class SystemUiRoleBehavior implements RoleBehavior {

    private static final List<String> WEAR_APP_OP_PERMISSIONS =
            Arrays.asList(android.Manifest.permission.SYSTEM_ALERT_WINDOW);

    @Override
    public void grant(@NonNull Role role, @NonNull String packageName, @NonNull Context context) {
        if (SdkLevel.isAtLeastT()) {
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                for (String permission : WEAR_APP_OP_PERMISSIONS) {
                    AppOpPermissions.grant(packageName, permission, true, context);
                }
            }
        }
    }

    @Override
    public void revoke(@NonNull Role role, @NonNull String packageName, @NonNull Context context) {
        if (SdkLevel.isAtLeastT()) {
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
                for (String permission : WEAR_APP_OP_PERMISSIONS) {
                    AppOpPermissions.revoke(packageName, permission, context);
                }
            }
        }
    }
}
