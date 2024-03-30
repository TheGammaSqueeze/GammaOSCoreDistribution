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

import com.android.sts.common.tradefed.testtype.RootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2022_20112 extends RootSecurityTestCase {

    // b/206987762
    // Vulnerable module : com.android.settings
    // Vulnerable apk : Settings.apk
    // Is play managed : No
    @AsbSecurityTest(cveBugId = 206987762)
    @Test
    public void testPocCVE_2022_20112() {
        final String testPkg = "android.security.cts.CVE_2022_20112";
        ITestDevice device = null;
        int currentUser = -1;
        int newUser = -1;
        try {
            device = getDevice();

            // Device wakeup and unlock
            AdbUtils.runCommandLine("input keyevent KEYCODE_WAKEUP", device);
            AdbUtils.runCommandLine("wm dismiss-keyguard", device);

            // Get current user
            currentUser = device.getCurrentUser();

            // Create new guest user 'CTSUser' for test
            newUser = device.createUser("CTSUser", true, false);

            // Start new guest user 'CTSUser'
            assumeTrue("Unable to create new guest user", device.startUser(newUser, true));

            // Switch to new user 'CTSUser'
            assumeTrue("Unable to switch to guest user", device.switchUser(newUser));

            // Install PoC application
            installPackage("CVE-2022-20112.apk");

            runDeviceTests(testPkg, testPkg + ".DeviceTest", "testprivateDnsPreferenceController");
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                if (currentUser != -1) {
                    // Switch back to previous user
                    device.switchUser(currentUser);
                }
                if (newUser != -1) {
                    // Stop user 'CTSUser'
                    device.stopUser(newUser);

                    // Remove user 'CTSUser'
                    device.removeUser(newUser);
                }
            } catch (Exception e) {
                // Ignore exception here
            }
        }
    }
}
