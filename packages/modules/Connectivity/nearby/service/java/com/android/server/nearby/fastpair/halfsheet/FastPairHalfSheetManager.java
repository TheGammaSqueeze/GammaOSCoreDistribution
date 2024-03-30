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

package com.android.server.nearby.fastpair.halfsheet;

import static com.android.server.nearby.fastpair.Constant.DEVICE_PAIRING_FRAGMENT_TYPE;
import static com.android.server.nearby.fastpair.Constant.EXTRA_BINDER;
import static com.android.server.nearby.fastpair.Constant.EXTRA_BUNDLE;
import static com.android.server.nearby.fastpair.Constant.EXTRA_HALF_SHEET_INFO;
import static com.android.server.nearby.fastpair.Constant.EXTRA_HALF_SHEET_TYPE;
import static com.android.server.nearby.fastpair.FastPairManager.ACTION_RESOURCES_APK;

import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.nearby.FastPairDevice;
import android.nearby.FastPairStatusCallback;
import android.nearby.PairStatusMetadata;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.nearby.common.locator.LocatorContextWrapper;
import com.android.server.nearby.fastpair.FastPairController;
import com.android.server.nearby.fastpair.cache.DiscoveryItem;
import com.android.server.nearby.util.Environment;

import java.util.List;
import java.util.stream.Collectors;

import service.proto.Cache;

/**
 * Fast Pair ux manager for half sheet.
 */
public class FastPairHalfSheetManager {
    private static final String ACTIVITY_INTENT_ACTION = "android.nearby.SHOW_HALFSHEET";
    private static final String HALF_SHEET_CLASS_NAME =
            "com.android.nearby.halfsheet.HalfSheetActivity";
    private static final String TAG = "FPHalfSheetManager";

    private String mHalfSheetApkPkgName;
    private final LocatorContextWrapper mLocatorContextWrapper;

    FastPairUiServiceImpl mFastPairUiService;

    public FastPairHalfSheetManager(Context context) {
        this(new LocatorContextWrapper(context));
    }

    @VisibleForTesting
    FastPairHalfSheetManager(LocatorContextWrapper locatorContextWrapper) {
        mLocatorContextWrapper = locatorContextWrapper;
        mFastPairUiService = new FastPairUiServiceImpl();
    }

    /**
     * Invokes half sheet in the other apk. This function can only be called in Nearby because other
     * app can't get the correct component name.
     */
    public void showHalfSheet(Cache.ScanFastPairStoreItem scanFastPairStoreItem) {
        try {
            if (mLocatorContextWrapper != null) {
                String packageName = getHalfSheetApkPkgName();
                if (packageName == null) {
                    Log.e(TAG, "package name is null");
                    return;
                }
                mFastPairUiService.setFastPairController(
                        mLocatorContextWrapper.getLocator().get(FastPairController.class));
                Bundle bundle = new Bundle();
                bundle.putBinder(EXTRA_BINDER, mFastPairUiService);
                mLocatorContextWrapper
                        .startActivityAsUser(new Intent(ACTIVITY_INTENT_ACTION)
                                        .putExtra(EXTRA_HALF_SHEET_INFO,
                                                scanFastPairStoreItem.toByteArray())
                                        .putExtra(EXTRA_HALF_SHEET_TYPE,
                                                DEVICE_PAIRING_FRAGMENT_TYPE)
                                        .putExtra(EXTRA_BUNDLE, bundle)
                                        .setComponent(new ComponentName(packageName,
                                                HALF_SHEET_CLASS_NAME)),
                                UserHandle.CURRENT);
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Can't resolve package that contains half sheet");
        }
    }

    /**
     * Shows pairing fail half sheet.
     */
    public void showPairingFailed() {
        FastPairStatusCallback pairStatusCallback = mFastPairUiService.getPairStatusCallback();
        if (pairStatusCallback != null) {
            Log.v(TAG, "showPairingFailed: pairStatusCallback not NULL");
            pairStatusCallback.onPairUpdate(new FastPairDevice.Builder().build(),
                    new PairStatusMetadata(PairStatusMetadata.Status.FAIL));
        } else {
            Log.w(TAG, "FastPairHalfSheetManager failed to show success half sheet because "
                    + "the pairStatusCallback is null");
        }
    }

    /**
     * Get the half sheet status whether it is foreground or dismissed
     */
    public boolean getHalfSheetForegroundState() {
        return true;
    }

    /**
     * Show passkey confirmation info on half sheet
     */
    public void showPasskeyConfirmation(BluetoothDevice device, int passkey) {
    }

    /**
     * This function will handle pairing steps for half sheet.
     */
    public void showPairingHalfSheet(DiscoveryItem item) {
        Log.d(TAG, "show pairing half sheet");
    }

    /**
     * Shows pairing success info.
     */
    public void showPairingSuccessHalfSheet(String address) {
        FastPairStatusCallback pairStatusCallback = mFastPairUiService.getPairStatusCallback();
        if (pairStatusCallback != null) {
            pairStatusCallback.onPairUpdate(
                    new FastPairDevice.Builder().setBluetoothAddress(address).build(),
                    new PairStatusMetadata(PairStatusMetadata.Status.SUCCESS));
        } else {
            Log.w(TAG, "FastPairHalfSheetManager failed to show success half sheet because "
                    + "the pairStatusCallback is null");
        }
    }

    /**
     * Removes dismiss runnable.
     */
    public void disableDismissRunnable() {
    }

    /**
     * Destroys the bluetooth pairing controller.
     */
    public void destroyBluetoothPairController() {
    }

    /**
     * Notify manager the pairing has finished.
     */
    public void notifyPairingProcessDone(boolean success, String address, DiscoveryItem item) {
    }

    /**
     * Gets the package name of HalfSheet.apk
     * getHalfSheetApkPkgName may invoke PackageManager multiple times and it does not have
     * race condition check. Since there is no lock for mHalfSheetApkPkgName.
     */
    String getHalfSheetApkPkgName() {
        if (mHalfSheetApkPkgName != null) {
            return mHalfSheetApkPkgName;
        }
        List<ResolveInfo> resolveInfos = mLocatorContextWrapper
                .getPackageManager().queryIntentActivities(
                        new Intent(ACTION_RESOURCES_APK),
                        PackageManager.MATCH_SYSTEM_ONLY);

        // remove apps that don't live in the nearby apex
        resolveInfos.removeIf(info ->
                !Environment.isAppInNearbyApex(info.activityInfo.applicationInfo));

        if (resolveInfos.isEmpty()) {
            // Resource APK not loaded yet, print a stack trace to see where this is called from
            Log.e("FastPairManager", "Attempted to fetch resources before halfsheet "
                            + " APK is installed or package manager can't resolve correctly!",
                    new IllegalStateException());
            return null;
        }

        if (resolveInfos.size() > 1) {
            // multiple apps found, log a warning, but continue
            Log.w("FastPairManager", "Found > 1 APK that can resolve halfsheet APK intent: "
                    + resolveInfos.stream()
                    .map(info -> info.activityInfo.applicationInfo.packageName)
                    .collect(Collectors.joining(", ")));
        }

        // Assume the first ResolveInfo is the one we're looking for
        ResolveInfo info = resolveInfos.get(0);
        mHalfSheetApkPkgName = info.activityInfo.applicationInfo.packageName;
        Log.i("FastPairManager", "Found halfsheet APK at: " + mHalfSheetApkPkgName);
        return mHalfSheetApkPkgName;
    }
}
