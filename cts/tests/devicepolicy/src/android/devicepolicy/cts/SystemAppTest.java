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

package android.devicepolicy.cts;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.EnableSystemApp;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SystemAppTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().any();
    private static final TestAppInstance sTestAppInstance = sTestApp.install();

    @AfterClass
    public static void teardownClass() {
        sTestAppInstance.uninstall();
    }

    @CanSetPolicyTest(policy = EnableSystemApp.class)
    @Postsubmit(reason = "new test")
    public void enableSystemApp_nonSystemApp_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> sDeviceState.dpc().devicePolicyManager().enableSystemApp(
                        sDeviceState.dpc().componentName(), sTestApp.packageName()));
    }

    @CannotSetPolicyTest(policy = EnableSystemApp.class)
    @Postsubmit(reason = "new test")
    public void enableSystemApp_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().enableSystemApp(
                        sDeviceState.dpc().componentName(), sTestApp.packageName()));
    }
}
