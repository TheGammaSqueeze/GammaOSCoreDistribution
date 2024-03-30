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

package com.android.systemui.car.displayarea;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.android.systemui.CoreStartable;
import com.android.systemui.dagger.SysUISingleton;

import javax.inject.Inject;

/**
 * Dagger Subcomponent for DisplayAreas within SysUI.
 */
@SysUISingleton
public class DisplayAreaComponent implements CoreStartable {
    public static final String TAG = "DisplayAreaComponent";
    // action name for the intent when to update the foreground DA visibility
    public static final String DISPLAY_AREA_VISIBILITY_CHANGED =
            "DISPLAY_AREA_VISIBILITY_CHANGED";
    // key name for the intent's extra that tells the DA's visibility status
    public static final String INTENT_EXTRA_IS_DISPLAY_AREA_VISIBLE =
            "EXTRA_IS_DISPLAY_AREA_VISIBLE";

    private final CarDisplayAreaController mCarDisplayAreaController;
    private final Context mContext;
    final Handler mHandler = new Handler(Looper.myLooper());

    @Inject
    public DisplayAreaComponent(Context context,
            CarDisplayAreaController carDisplayAreaController) {
        mContext = context;
        mCarDisplayAreaController = carDisplayAreaController;
    }

    @Override
    public void start() {
        logIfDebuggable("start:");
        if (CarDisplayAreaUtils.isCustomDisplayPolicyDefined(mContext)) {
            // Register the DA's
            mCarDisplayAreaController.register();

            IntentFilter filter = new IntentFilter();
            // add a receiver to listen to ACTION_BOOT_COMPLETED where we will perform tasks that
            // require system to be ready. For example, search list of activities with a specific
            // Intent. This cannot be done while the component is created as that is too early in
            // the lifecycle of system starting and the results returned by package manager is
            // not reliable. So we want to wait until system is ready before we query for list of
            // activities.
            filter.addAction(Intent.ACTION_BOOT_COMPLETED);
            mContext.registerReceiverForAllUsers(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                        mCarDisplayAreaController.updateVoicePlateActivityMap();
                    }
                }
            }, filter, /* broadcastPermission= */ null, /* scheduler= */ null);

            IntentFilter packageChangeFilter = new IntentFilter();
            // add a receiver to listen to ACTION_PACKAGE_ADDED to perform any action when a new
            // application is installed on the system.
            packageChangeFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
            packageChangeFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            packageChangeFilter.addDataScheme("package");
            mContext.registerReceiverForAllUsers(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mCarDisplayAreaController.updateVoicePlateActivityMap();
                }
            }, packageChangeFilter, null, null);
        }
    }

    /**
     * enum to define the state of display area possible.
     * CONTROL_BAR state is when only control bar is visible.
     * FULL state is when display area hosting default apps  cover the screen fully.
     * DEFAULT state where maps are shown above DA for default apps.
     */
    public enum FOREGROUND_DA_STATE {
        CONTROL_BAR, DEFAULT, FULL, FULL_TO_DEFAULT
    }

    private static void logIfDebuggable(String message) {
        if (Build.IS_DEBUGGABLE) {
            Log.d(TAG, message);
        }
    }
}
