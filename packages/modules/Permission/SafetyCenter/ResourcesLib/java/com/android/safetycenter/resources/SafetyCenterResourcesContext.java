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

package com.android.safetycenter.resources;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.InputStream;
import java.util.List;

/**
 * Wrapper for context to override getResources method. Resources for the Safety Center that need to
 * be fetched from the dedicated resources APK.
 */
public class SafetyCenterResourcesContext extends ContextWrapper {
    private static final String TAG = "SafetyCenterResContext";

    /** Intent action that is used to identify the Safety Center resources APK */
    private static final String RESOURCES_APK_ACTION =
            "com.android.safetycenter.intent.action.SAFETY_CENTER_RESOURCES_APK";

    /** Permission APEX name */
    private static final String APEX_MODULE_NAME = "com.android.permission";

    /**
     * The path where the Permission apex is mounted.
     * Current value = "/apex/com.android.permission"
     */
    private static final String APEX_MODULE_PATH =
            new File("/apex", APEX_MODULE_NAME).getAbsolutePath();

    /** Raw XML config resource name */
    private static final String CONFIG_NAME = "safety_center_config";

    /** Intent action that is used to identify the Safety Center resources APK */
    @NonNull
    private final String mResourcesApkAction;

    /** The path where the Safety Center resources APK is expected to be installed */
    @Nullable
    private final String mResourcesApkPath;

    /** Raw XML config resource name */
    @NonNull
    private final String mConfigName;

    /** Specific flags used for retrieving resolve info */
    private final int mFlags;

    // Cached package name and resources from the resources APK
    @Nullable
    private String mResourcesApkPkgName;
    @Nullable
    private AssetManager mAssetsFromApk;
    @Nullable
    private Resources mResourcesFromApk;
    @Nullable
    private Resources.Theme mThemeFromApk;

    public SafetyCenterResourcesContext(@NonNull Context contextBase) {
        super(contextBase);
        mResourcesApkAction = RESOURCES_APK_ACTION;
        mResourcesApkPath = APEX_MODULE_PATH;
        mConfigName = CONFIG_NAME;
        mFlags = PackageManager.MATCH_SYSTEM_ONLY;
    }

    SafetyCenterResourcesContext(@NonNull Context contextBase, @NonNull String resourcesApkAction,
            @Nullable String resourcesApkPath, @NonNull String configName, int flags) {
        super(contextBase);
        mResourcesApkAction = requireNonNull(resourcesApkAction);
        mResourcesApkPath = resourcesApkPath;
        mConfigName = requireNonNull(configName);
        mFlags = flags;
    }

    /** Get the package name of the Safety Center resources APK. */
    @VisibleForTesting
    @Nullable
    String getResourcesApkPkgName() {
        if (mResourcesApkPkgName != null) {
            return mResourcesApkPkgName;
        }

        List<ResolveInfo> resolveInfos = getPackageManager().queryIntentActivities(
                new Intent(mResourcesApkAction), mFlags);

        if (resolveInfos.size() > 1) {
            // multiple apps found, log a warning, but continue
            Log.w(TAG, "Found > 1 APK that can resolve Safety Center resources APK intent:");
            final int resolveInfosSize = resolveInfos.size();
            for (int i = 0; i < resolveInfosSize; i++) {
                ResolveInfo resolveInfo = resolveInfos.get(i);
                Log.w(TAG, String.format("- pkg:%s at:%s",
                        resolveInfo.activityInfo.applicationInfo.packageName,
                        resolveInfo.activityInfo.applicationInfo.sourceDir));
            }
        }

        ResolveInfo info = null;
        // Assume the first good ResolveInfo is the one we're looking for
        final int resolveInfosSize = resolveInfos.size();
        for (int i = 0; i < resolveInfosSize; i++) {
            ResolveInfo resolveInfo = resolveInfos.get(i);
            if (mResourcesApkPath != null
                    && !resolveInfo.activityInfo.applicationInfo.sourceDir.startsWith(
                    mResourcesApkPath)) {
                // skip apps that don't live in the Permission apex
                continue;
            }
            info = resolveInfo;
            break;
        }

        if (info == null) {
            // Resource APK not loaded yet, print a stack trace to see where this is called from
            Log.e(TAG,
                    "Attempted to fetch resources before Safety Center resources APK is loaded!",
                    new IllegalStateException());
            return null;
        }

        mResourcesApkPkgName = info.activityInfo.applicationInfo.packageName;
        Log.i(TAG, "Found Safety Center resources APK at: " + mResourcesApkPkgName);
        return mResourcesApkPkgName;
    }

    /**
     * Get the raw XML resource representing the Safety Center configuration from the Safety Center
     * resources APK.
     */
    @Nullable
    public InputStream getSafetyCenterConfig() {
        String resourcePkgName = getResourcesApkPkgName();
        if (resourcePkgName == null) {
            return null;
        }
        Resources resources = getResources();
        if (resources == null) {
            return null;
        }
        int id = resources.getIdentifier(mConfigName, "raw", resourcePkgName);
        if (id == Resources.ID_NULL) {
            return null;
        }
        return resources.openRawResource(id);
    }

    @Nullable
    private Context getResourcesApkContext() {
        String name = getResourcesApkPkgName();
        if (name == null) {
            return null;
        }
        try {
            return createPackageContext(name, 0);
        } catch (PackageManager.NameNotFoundException e) {
            Log.wtf(TAG, "Failed to load resources", e);
        }
        return null;
    }

    /** Retrieve assets held in the Safety Center resources APK. */
    @Override
    public AssetManager getAssets() {
        if (mAssetsFromApk == null) {
            Context resourcesApkContext = getResourcesApkContext();
            if (resourcesApkContext != null) {
                mAssetsFromApk = resourcesApkContext.getAssets();
            }
        }
        return mAssetsFromApk;
    }

    /** Retrieve resources held in the Safety Center resources APK. */
    @Override
    public Resources getResources() {
        if (mResourcesFromApk == null) {
            Context resourcesApkContext = getResourcesApkContext();
            if (resourcesApkContext != null) {
                mResourcesFromApk = resourcesApkContext.getResources();
            }
        }
        return mResourcesFromApk;
    }

    /** Retrieve theme held in the Safety Center resources APK. */
    @Override
    public Resources.Theme getTheme() {
        if (mThemeFromApk == null) {
            Context resourcesApkContext = getResourcesApkContext();
            if (resourcesApkContext != null) {
                mThemeFromApk = resourcesApkContext.getTheme();
            }
        }
        return mThemeFromApk;
    }
}
