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

package com.android.cts.net;

import android.compat.cts.CompatChangeGatingTestCase;

import java.util.Set;

/**
 * Tests for the {@link android.net.LinkProperties#EXCLUDED_ROUTES} compatibility change.
 *
 * TODO: see if we can delete this cumbersome host test by moving the coverage to CtsNetTestCases
 * and CtsNetTestCasesMaxTargetSdk31.
 */
public class HostsideLinkPropertiesGatingTests extends CompatChangeGatingTestCase {
    private static final String TEST_APK = "CtsHostsideNetworkTestsApp3.apk";
    private static final String TEST_APK_PRE_T = "CtsHostsideNetworkTestsApp3PreT.apk";
    private static final String TEST_PKG = "com.android.cts.net.hostside.app3";
    private static final String TEST_CLASS = ".ExcludedRoutesGatingTest";

    private static final long EXCLUDED_ROUTES_CHANGE_ID = 186082280;

    protected void tearDown() throws Exception {
        uninstallPackage(TEST_PKG, true);
    }

    public void testExcludedRoutesChangeEnabled() throws Exception {
        installPackage(TEST_APK, true);
        runDeviceCompatTest("testExcludedRoutesChangeEnabled");
    }

    public void testExcludedRoutesChangeDisabledPreT() throws Exception {
        installPackage(TEST_APK_PRE_T, true);
        runDeviceCompatTest("testExcludedRoutesChangeDisabled");
    }

    public void testExcludedRoutesChangeDisabledByOverrideOnDebugBuild() throws Exception {
        // Must install APK even when skipping test, because tearDown expects uninstall to succeed.
        installPackage(TEST_APK, true);

        // This test uses an app with a target SDK where the compat change is on by default.
        // Because user builds do not allow overriding compat changes, only run this test on debug
        // builds. This seems better than deleting this test and not running it anywhere because we
        // could in the future run this test on userdebug builds in presubmit.
        //
        // We cannot use assumeXyz here because CompatChangeGatingTestCase ultimately inherits from
        // junit.framework.TestCase, which does not understand assumption failures.
        if ("user".equals(getDevice().getProperty("ro.build.type"))) return;

        runDeviceCompatTestWithChangeDisabled("testExcludedRoutesChangeDisabled");
    }

    public void testExcludedRoutesChangeEnabledByOverridePreT() throws Exception {
        installPackage(TEST_APK_PRE_T, true);
        runDeviceCompatTestWithChangeEnabled("testExcludedRoutesChangeEnabled");
    }

    private void runDeviceCompatTest(String methodName) throws Exception {
        runDeviceCompatTest(TEST_PKG, TEST_CLASS, methodName, Set.of(), Set.of());
    }

    private void runDeviceCompatTestWithChangeEnabled(String methodName) throws Exception {
        runDeviceCompatTest(TEST_PKG, TEST_CLASS, methodName, Set.of(EXCLUDED_ROUTES_CHANGE_ID),
                Set.of());
    }

    private void runDeviceCompatTestWithChangeDisabled(String methodName) throws Exception {
        runDeviceCompatTest(TEST_PKG, TEST_CLASS, methodName, Set.of(),
                Set.of(EXCLUDED_ROUTES_CHANGE_ID));
    }
}
