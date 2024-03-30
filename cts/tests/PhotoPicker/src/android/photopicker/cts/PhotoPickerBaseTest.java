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

package android.photopicker.cts;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.Assume;
import org.junit.Before;

/**
 * Photo Picker Base class for Photo Picker tests. This includes common setup methods
 * required for all Photo Picker tests.
 */
public class PhotoPickerBaseTest {
    public static int REQUEST_CODE = 42;

    protected GetResultActivity mActivity;
    protected Context mContext;
    protected UiDevice mDevice;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(isHardwareSupported());

        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(inst);

        final String setSyncDelayCommand =
                "device_config put storage pickerdb.default_sync_delay_ms 0";
        mDevice.executeShellCommand(setSyncDelayCommand);

        mContext = inst.getContext();
        final Intent intent = new Intent(mContext, GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Wake up the device and dismiss the keyguard before the test starts
        mDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        mDevice.executeShellCommand("wm dismiss-keyguard");

        mActivity = (GetResultActivity) inst.startActivitySync(intent);
        // Wait for the UI Thread to become idle.
        inst.waitForIdleSync();
        mActivity.clearResult();
        mDevice.waitForIdle();
    }

    private static boolean isHardwareSupported() {
        final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        // These UI tests are not optimised for Watches, TVs, Auto;
        // IoT devices do not have a UI to run these UI tests
        PackageManager pm = inst.getContext().getPackageManager();
        return !pm.hasSystemFeature(pm.FEATURE_EMBEDDED)
                && !pm.hasSystemFeature(pm.FEATURE_WATCH)
                && !pm.hasSystemFeature(pm.FEATURE_LEANBACK)
                && !pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE);
    }
}
