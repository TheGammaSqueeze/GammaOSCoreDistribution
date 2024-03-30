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

package com.android.permissioncontroller.permission.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.Attribution;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * Utils related to subattribution.
 */
public class SubattributionUtils {

    /**
     * Returns true if the app supports subattribution.
     */
    public static boolean isSubattributionSupported(Context context, ApplicationInfo appInfo) {
        if (!SdkLevel.isAtLeastS()) {
            return false;
        }
        return appInfo.areAttributionsUserVisible();
    }

    /**
     * Returns the attribution label map for the package if the app supports subattribtion; Returns
     * {@code null} otherwise.
     */
    @Nullable
    @SuppressLint("NewApi") // isSubattributionSupported checks api level
    public static Map<Integer, String> getAttributionLabels(Context context,
            PackageInfo pkgInfo) {
        if (!isSubattributionSupported(context, pkgInfo.applicationInfo)) {
            return null;
        }
        return getAttributionLabelsInternal(context, pkgInfo);
    }

    /**
     * Returns the attribution label map for the package if the app supports subattribtion; Returns
     * {@code null} otherwise.
     */
    @Nullable
    @SuppressLint("NewApi") // isSubattributionSupported checks api level
    public static Map<Integer, String> getAttributionLabels(Context context,
            ApplicationInfo appInfo) {
        if (!isSubattributionSupported(context, appInfo)) {
            return null;
        }

        PackageInfo packageInfo;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(appInfo.packageName,
                    PackageManager.GET_PERMISSIONS | PackageManager.GET_ATTRIBUTIONS);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        return getAttributionLabelsInternal(context, packageInfo);
    }

    @Nullable
    @RequiresApi(Build.VERSION_CODES.S)
    private static Map<Integer, String> getAttributionLabelsInternal(Context context,
            PackageInfo pkgInfo) {
        Context pkgContext;
        try {
            pkgContext = context.createPackageContext(pkgInfo.packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        Map<Integer, String> attributionLabels = new HashMap<>();
        for (Attribution attribution : pkgInfo.attributions) {
            int label = attribution.getLabel();
            try {
                String resourceForLabel = pkgContext.getString(attribution.getLabel());
                attributionLabels.put(label, resourceForLabel);
            } catch (Resources.NotFoundException e) {
                // should never happen
            }
        }
        return attributionLabels;
    }
}
