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
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_20223 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 223578534)
    @Test
    public void testPocCVE_2022_20223() {
        ITestDevice device = getDevice();
        final String testPkg = "android.security.cts.CVE_2022_20223";
        int userId = -1;
        try {
            // Wake up the screen
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_MENU", device);
            AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);

            // Create restricted user
            String commandOutput = AdbUtils.runCommandLine(
                    "pm create-user --restricted CVE_2022_20223_RestrictedUser", device);

            // Extract user id of the restricted user
            String[] tokens = commandOutput.split("\\s+");
            assumeTrue(tokens.length > 0);
            assumeTrue(tokens[0].equals("Success:"));
            userId = Integer.parseInt(tokens[tokens.length - 1]);

            // Install PoC application
            installPackage("CVE-2022-20223.apk");

            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testAppRestrictionsFragment");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // Back to home screen after test
                AdbUtils.runCommandLine("input keyevent KEYCODE_HOME", device);
                if (userId != -1) {
                    // Remove restricted user
                    AdbUtils.runCommandLine("pm remove-user " + userId, device);
                }
            } catch (Exception e) {
                assumeNoException(e);
            }
        }
    }
}
