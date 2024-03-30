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

package android.car.cts.builtin.power;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.builtin.power.PowerManagerHelper;
import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class PowerManagerHelperTest {

    @Test
    public void testSetDisplayState() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getContext();
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        UiAutomation uiAutomation = instrumentation.getUiAutomation();

        uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.DEVICE_POWER);

        try {
            PowerManagerHelper.setDisplayState(context, /* on= */ true, SystemClock.uptimeMillis());
            assertWithMessage("Screen on").that(powerManager.isInteractive()).isTrue();

            PowerManagerHelper.setDisplayState(context, /* on= */ false,
                    SystemClock.uptimeMillis());
            assertWithMessage("Screen on").that(powerManager.isInteractive()).isFalse();

            PowerManagerHelper.setDisplayState(context, /* on= */ true, SystemClock.uptimeMillis());
            assertWithMessage("Screen on").that(powerManager.isInteractive()).isTrue();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }
}
