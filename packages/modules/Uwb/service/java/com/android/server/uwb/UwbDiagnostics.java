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

package com.android.server.uwb;

import android.content.Context;
import android.os.BugreportManager;
import android.os.BugreportParams;
import android.util.Log;

/**
 * A class to trigger bugreport and other logs for UWB related failures
 */
public class UwbDiagnostics {
    private static final  String TAG = "UwbDiagnostics";
    private final Context mContext;
    private final SystemBuildProperties mSystemBuildProperties;
    private final UwbInjector mUwbInjector;
    private long mLastBugReportTimeMs;
    public UwbDiagnostics(
            Context context, UwbInjector uwbInjector, SystemBuildProperties systemBuildProperties) {
        mContext = context;
        mSystemBuildProperties = systemBuildProperties;
        mUwbInjector = uwbInjector;
    }

    /**
     * Take a bug report if it is not in user build and there is no recent bug report
     */
    public void takeBugReport(String bugTitle) {
        if (mSystemBuildProperties.isUserBuild()) {
            return;
        }
        long currentTimeMs = mUwbInjector.getElapsedSinceBootMillis();
        if ((currentTimeMs - mLastBugReportTimeMs)
                < mUwbInjector.getDeviceConfigFacade().getBugReportMinIntervalMs()
                && mLastBugReportTimeMs > 0) {
            return;
        }
        mLastBugReportTimeMs = currentTimeMs;
        BugreportManager bugreportManager = mContext.getSystemService(BugreportManager.class);
        BugreportParams params = new BugreportParams(BugreportParams.BUGREPORT_MODE_FULL);
        try {
            bugreportManager.requestBugreport(params, bugTitle, bugTitle);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error taking bugreport: " + e);
        }
    }
}
