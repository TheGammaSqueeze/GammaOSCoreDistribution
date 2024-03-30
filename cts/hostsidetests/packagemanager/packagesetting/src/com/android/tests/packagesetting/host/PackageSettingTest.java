/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tests.packagesetting.host;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.PackageInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

@RunWith(DeviceJUnit4ClassRunner.class)
public class PackageSettingTest extends BaseHostJUnit4Test {
    private static final String TEST_APK = "PackageSettingTestApp.apk";
    private static final String TEST_PACKAGE = "com.android.tests.packagesetting.app";
    private static final String TEST_CLASS = TEST_PACKAGE + "." + "PackageSettingDeviceTest";
    private static final String CODE_PATH_ROOT = "/data/app";
    private static final long DEFAULT_TIMEOUT = 10 * 60 * 1000L;
    private int mSecondUser = -1;

    /** Uninstall apps after tests. */
    @After
    public void cleanUp() throws Exception {
        uninstallPackage(getDevice(), TEST_PACKAGE);
        Assert.assertFalse(isPackageInstalled(TEST_PACKAGE));
        if (mSecondUser != -1) {
            stopAndRemoveUser(mSecondUser);
        }
    }

    @Test
    @AppModeFull
    public void testAppInstallsWithReboot() throws Exception {
        installPackage(TEST_APK);
        Assert.assertTrue(isPackageInstalled(TEST_PACKAGE));
        final String codePathFromDumpsys = getCodePathFromDumpsys(TEST_PACKAGE);
        Assert.assertTrue(codePathFromDumpsys.startsWith(CODE_PATH_ROOT));
        testCodePathMatchesDumpsys(codePathFromDumpsys);
        // Code paths should still be valid after reboot
        getDevice().reboot();
        final String codePathFromDumpsysAfterReboot = getCodePathFromDumpsys(TEST_PACKAGE);
        Assert.assertEquals(codePathFromDumpsys, codePathFromDumpsysAfterReboot);
        testCodePathMatchesDumpsys(codePathFromDumpsys);
    }

    private String getCodePathFromDumpsys(String packageName)
            throws DeviceNotAvailableException {
        PackageInfo packageInfo = getDevice().getAppPackageInfo(packageName);
        return packageInfo.getCodePath();
    }

    private void testCodePathMatchesDumpsys(String codePathToMatch) throws Exception {
        final Map<String, String> testArgs = new HashMap<>();
        testArgs.put("expectedCodePath", codePathToMatch);
        runDeviceTests(getDevice(), null, TEST_PACKAGE, TEST_CLASS, "testCodePathMatchesExpected",
                null, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT, 0L, true, false, testArgs);
    }

    @Test
    @AppModeFull
    public void testFirstInstallTimeWithReboot() throws Exception {
        Assume.assumeTrue("device does not support multi-user",
                getDevice().getMaxNumberOfUsersSupported() > 1);
        installPackage(TEST_APK);
        final int currentUser = getDevice().getCurrentUser();
        final String firstInstallTimeForCurrentUser = getFirstInstallTimeForUserFromDumpsys(
                TEST_PACKAGE, currentUser);
        Assert.assertNotNull(firstInstallTimeForCurrentUser);
        testFirstInstallTimeMatchesDumpsys(firstInstallTimeForCurrentUser, currentUser);
        // firstInstallTime should be the same after reboot
        getDevice().reboot();
        Assert.assertEquals(firstInstallTimeForCurrentUser,
                getFirstInstallTimeForUserFromDumpsys(TEST_PACKAGE, currentUser));

        mSecondUser = createAndStartSecondUser();
        installPackageOnExistingUser(TEST_PACKAGE, mSecondUser);
        final String firstInstallTimeForSecondUser = getFirstInstallTimeForUserFromDumpsys(
                TEST_PACKAGE, mSecondUser);
        Assert.assertNotNull(firstInstallTimeForSecondUser);
        Assert.assertNotEquals(firstInstallTimeForCurrentUser, firstInstallTimeForSecondUser);
        testFirstInstallTimeMatchesDumpsys(firstInstallTimeForSecondUser, mSecondUser);
        getDevice().reboot();
        Assert.assertEquals(firstInstallTimeForSecondUser,
                getFirstInstallTimeForUserFromDumpsys(TEST_PACKAGE, mSecondUser));
    }

    private int createAndStartSecondUser() throws Exception {
        String output = getDevice().executeShellCommand("pm create-user SecondUser");
        Assert.assertTrue(output.startsWith("Success"));
        int userId = Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
        output = getDevice().executeShellCommand("am start-user -w " + userId);
        Assert.assertFalse(output.startsWith("Error"));
        output = getDevice().executeShellCommand("am get-started-user-state " + userId);
        Assert.assertTrue(output.contains("RUNNING_UNLOCKED"));
        return userId;
    }

    private void stopAndRemoveUser(int userId) throws Exception {
        getDevice().executeShellCommand("am stop-user -w -f " + userId);
        getDevice().executeShellCommand("pm remove-user " + userId);
    }

    private void installPackageOnExistingUser(String packageName, int userId) throws Exception {
        final String output = getDevice().executeShellCommand(
                String.format("pm install-existing --user %d %s", userId, packageName));
        Assert.assertEquals("Package " + packageName + " installed for user: " + userId + "\n",
                output);
    }

    private String getFirstInstallTimeForUserFromDumpsys(String packageName, int userId)
            throws Exception {
        PackageInfo packageInfo = getDevice().getAppPackageInfo(packageName);
        return packageInfo.getFirstInstallTime(userId);
    }

    private void testFirstInstallTimeMatchesDumpsys(String firstInstallTime, int userId)
            throws Exception {
        final Map<String, String> testArgs = new HashMap<>();
        // Notice the printed timestamp in dumpsys is formatted and has lost sub-second precision
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone(getDeviceTimezone()));
        final long firstInstallTs = sdf.parse(firstInstallTime).getTime();
        testArgs.put("userId", String.valueOf(userId));
        testArgs.put("expectedFirstInstallTime", String.valueOf(firstInstallTs));
        runDeviceTests(getDevice(), null, TEST_PACKAGE, TEST_CLASS,
                "testFirstInstallTimeMatchesExpected", null, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT,
                0L, true, false, testArgs);
    }

    private String getDeviceTimezone() throws Exception {
        final String timezone = getDevice().getProperty("persist.sys.timezone");
        if (timezone != null) {
            return timezone.trim();
        }
        return "GMT";
    }

}
