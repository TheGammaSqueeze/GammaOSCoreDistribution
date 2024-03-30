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

package com.android.permissioncontroller.role.utils;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods about Notification permissions.
 */
public final class NotificationUtils {

    public static final String LOG_TAG = NotificationUtils.class.getSimpleName();

    private NotificationUtils() {}

    /**
     * Grants the NotificationListener access.
     *
     * @param context     the {@code Context} to retrieve system services
     * @param packageName the package name implements the NotificationListener
     */
    public static void grantNotificationAccessForPackage(@NonNull Context context,
            @NonNull String packageName) {
        setNotificationGrantStateForPackage(context, packageName, true);
    }

    /**
     * Revokes the NotificationListener access.
     *
     * @param context     the {@code Context} to retrieve system services
     * @param packageName the package name implements the NotificationListener
     */
    public static void revokeNotificationAccessForPackage(@NonNull Context context,
            @NonNull String packageName) {
        setNotificationGrantStateForPackage(context, packageName, false);
    }


    private static void setNotificationGrantStateForPackage(@NonNull Context context,
            @NonNull String packageName, boolean granted) {
        List<ComponentName> notificationListenersForPackage =
                getNotificationListenersForPackage(packageName, context);
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        for (ComponentName componentName : notificationListenersForPackage) {
            notificationManager.setNotificationListenerAccessGranted(
                    componentName, granted, false);
        }
    }

    private static List<ComponentName> getNotificationListenersForPackage(
            @NonNull String packageName, @NonNull Context context) {
        List<ResolveInfo> allListeners = context.getPackageManager().queryIntentServices(
                new Intent(NotificationListenerService.SERVICE_INTERFACE).setPackage(packageName),
                PackageManager.MATCH_DIRECT_BOOT_AWARE | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
        ArrayList<ComponentName> pkgListeners = new ArrayList<>();
        for (ResolveInfo service : allListeners) {
            ServiceInfo serviceInfo = service.serviceInfo;
            if (Objects.equals(serviceInfo.permission,
                    android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
                    && packageName.equals(serviceInfo.packageName)) {
                pkgListeners.add(new ComponentName(serviceInfo.packageName, serviceInfo.name));
            }
        }
        Log.d(LOG_TAG, "getNotificationListenersForPackage(" + packageName + "): " + pkgListeners);
        return pkgListeners;
    }

}
