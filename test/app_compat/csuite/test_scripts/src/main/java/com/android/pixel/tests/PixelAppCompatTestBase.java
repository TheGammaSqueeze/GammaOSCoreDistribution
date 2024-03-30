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

package com.android.pixel.tests;

import static androidx.test.platform.app.InstrumentationRegistry.getArguments;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import android.app.KeyguardManager;
import android.support.test.uiautomator.UiDevice;

import com.android.pixel.utils.DeviceUtils;

import org.junit.After;
import org.junit.Before;

/** Base class for Pixel app compatibility tests. */
public abstract class PixelAppCompatTestBase {
    private static final String KEY_PACKAGE_NAME = "package";

    private DeviceUtils mDeviceUtils;
    private UiDevice mDevice;
    private KeyguardManager mKeyguardManager;
    private String mPackage;

    @Before
    public void setUp() throws Exception {
        getDeviceUtils().createLogDataDir();
        getDeviceUtils().wakeAndUnlockScreen();
        // Start from the home screen
        getDeviceUtils().backToHome(getUiDevice().getLauncherPackageName());
        getDeviceUtils().startRecording(getPackage());
    }

    @After
    public void tearDown() throws Exception {
        getDeviceUtils().stopRecording();
    }

    protected UiDevice getUiDevice() {
        if (mDevice == null) {
            mDevice = UiDevice.getInstance(getInstrumentation());
        }
        return mDevice;
    }

    protected DeviceUtils getDeviceUtils() {
        if (mDeviceUtils == null) {
            mDeviceUtils = new DeviceUtils(getUiDevice());
        }
        return mDeviceUtils;
    }

    protected KeyguardManager getKeyguardManager() {
        if (mKeyguardManager == null) {
            mKeyguardManager =
                    getInstrumentation().getContext().getSystemService(KeyguardManager.class);
        }
        return mKeyguardManager;
    }

    protected String getPackage() {
        if (mPackage == null) {
            mPackage = getArguments().getString(KEY_PACKAGE_NAME);
        }
        return mPackage;
    }
}
