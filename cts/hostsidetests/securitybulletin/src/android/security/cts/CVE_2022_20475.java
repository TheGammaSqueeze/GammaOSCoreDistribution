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

package android.security.cts;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_20475 extends NonRootSecurityTestCase {

    /**
     * b/240663194
     * Vulnerable module : services.jar
     * Is Play Managed   : No
     */
    @AsbSecurityTest(cveBugId = 240663194)
    @Test
    public void testPocCVE_2022_20475() {
        try {
            final String testPkg = "android.security.cts.CVE_2022_20475_test";
            ITestDevice device = getDevice();

            // Install the test and target apps
            installPackage("CVE-2022-20475-test.apk");
            installPackage("CVE-2022-20475-target.apk");

            // Wake up the device
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

            // Run the test "testCVE_2022_20475"
            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testCVE_2022_20475");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
