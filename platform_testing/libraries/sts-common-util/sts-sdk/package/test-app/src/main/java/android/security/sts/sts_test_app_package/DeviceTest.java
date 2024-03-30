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

package android.security.sts.sts_test_app_package;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeNoException;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.IntegerRes;
import androidx.annotation.StringRes;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {

    Context mAppContext;

    int getInteger(@IntegerRes int resId) {
        return mAppContext.getResources().getInteger(resId);
    }

    String getString(@StringRes int resId) {
        return mAppContext.getResources().getString(resId);
    }

    @Test
    public void testDeviceSideMethod() {
        try {
            mAppContext = getApplicationContext();
            UiDevice device = UiDevice.getInstance(getInstrumentation());
            device.executeShellCommand(
                    "am start -n com.android.nfc/.handover.ConfirmConnectActivity");
            long startTime = System.currentTimeMillis();
            while ((System.currentTimeMillis() - startTime)
                    < getInteger(R.integer.MAX_WAIT_TIME_MS)) {
                SharedPreferences sh =
                        mAppContext.getSharedPreferences(
                                getString(R.string.SHARED_PREFERENCE), Context.MODE_APPEND);
                int result =
                        sh.getInt(getString(R.string.RESULT_KEY), getInteger(R.integer.DEFAULT));
                assertNotEquals(
                        "NFC Android App broadcasts Bluetooth device information!",
                        result,
                        getInteger(R.integer.FAIL));
                Thread.sleep(getInteger(R.integer.SLEEP_TIME_MS));
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
