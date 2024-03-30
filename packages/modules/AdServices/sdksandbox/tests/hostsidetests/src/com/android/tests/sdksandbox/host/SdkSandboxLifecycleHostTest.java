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

package com.android.tests.sdksandbox.host;


import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxLifecycleHostTest extends BaseHostJUnit4Test {

    private static final String APP_PACKAGE = "com.android.sdksandbox.app";
    private static final String APP_2_PACKAGE = "com.android.sdksandbox.app2";

    private static final String APP_ACTIVITY = "SdkSandboxTestActivity";
    private static final String APP_2_ACTIVITY = "SdkSandboxTestActivity2";

    private static final String CODE_APK = "TestCodeProvider.apk";
    private static final String CODE_APK_2 = "TestCodeProvider2.apk";

    private void clearProcess(String pkg) throws Exception {
        getDevice().executeShellCommand(String.format("pm clear %s", pkg));
    }

    private void startActivity(String pkg, String activity) throws Exception {
        getDevice().executeShellCommand(String.format("am start -W -n %s/.%s", pkg, activity));
    }

    private void killApp(String pkg) throws Exception {
        getDevice().executeShellCommand(String.format("am force-stop %s", pkg));
    }

    private String getUidForPackage(String pkg) throws Exception {
        String pid = getDevice().getProcessPid(pkg);
        if (pid == null) {
            throw new Exception(String.format("Could not find PID for %s", pkg));
        }
        String result = getDevice().executeAdbCommand("shell", "ps", "-p", pid, "-o", "uid");
        String[] sections = result.split("\n");
        return sections[sections.length - 1];
    }

    // TODO(b/216302023): Update sdk sandbox process name format
    private String getSdkSandboxNameForPackage(String pkg) throws Exception {
        String appUid = getUidForPackage(pkg);
        return String.format("sdk_sandbox_%s", appUid);
    }

    @Before
    public void setUp() throws Exception {
        assertThat(getBuild()).isNotNull();
        assertThat(getDevice()).isNotNull();

        // Ensure neither app is currently running
        for (String pkg : new String[]{APP_PACKAGE, APP_2_PACKAGE}) {
            clearProcess(pkg);
        }

        // Workaround for autoTeardown which removes packages installed in test
        for (String apk : new String[]{CODE_APK, CODE_APK_2}) {
            if (!isPackageInstalled(apk)) {
                installPackage(apk, "-d");
            }
        }
    }

    @Test
    public void testSdkSandboxIsDestroyedOnAppDestroy() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);
        String sdkSandbox = getSdkSandboxNameForPackage(APP_PACKAGE);
        assertThat(processDump).contains(sdkSandbox);

        killApp(APP_PACKAGE);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_PACKAGE);
        assertThat(processDump).doesNotContain(sdkSandbox);
    }

    @Test
    public void testSdkSandboxIsCreatedPerApp() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);
        String sdkSandbox1 = getSdkSandboxNameForPackage(APP_PACKAGE);
        assertThat(processDump).contains(sdkSandbox1);

        startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_2_PACKAGE);
        String sdkSandbox2 = getSdkSandboxNameForPackage(APP_2_PACKAGE);
        assertThat(processDump).contains(sdkSandbox2);
        assertThat(processDump).contains(APP_PACKAGE);
        assertThat(processDump).contains(sdkSandbox1);

        killApp(APP_2_PACKAGE);
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_2_PACKAGE);
        assertThat(processDump).doesNotContain(sdkSandbox2);
        assertThat(processDump).contains(APP_PACKAGE);
        assertThat(processDump).contains(sdkSandbox1);
    }

    @Test
    public void testAppAndSdkSandboxAreKilledOnLoadedSdkUpdate() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);

        String sdkSandbox = getSdkSandboxNameForPackage(APP_PACKAGE);
        assertThat(processDump).contains(sdkSandbox);

        // Update package loaded by app
        installPackage(CODE_APK, "-d");

        // Should no longer see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).doesNotContain(APP_PACKAGE);
        assertThat(processDump).doesNotContain(sdkSandbox);
    }

    @Test
    public void testAppAndSdkSandboxAreNotKilledForNonLoadedSdkUpdate() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);

        // Should see app/sdk sandbox running
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);

        String sdkSandbox = getSdkSandboxNameForPackage(APP_PACKAGE);
        assertThat(processDump).contains(sdkSandbox);

        // Simulate update of package not loaded by app
        installPackage(CODE_APK_2, "-d");

        // Should still see app/sdk sandbox running
        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);
        assertThat(processDump).contains(sdkSandbox);
    }

    @Test
    public void testOnlyRelevantAppIsKilledForLoadedSdkUpdate() throws Exception {
        startActivity(APP_PACKAGE, APP_ACTIVITY);
        startActivity(APP_2_PACKAGE, APP_2_ACTIVITY);

        // See processes for both apps
        String processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);

        String sdkSandbox = getSdkSandboxNameForPackage(APP_PACKAGE);
        assertThat(processDump).contains(sdkSandbox);

        assertThat(processDump).contains(APP_2_PACKAGE);

        String sdkSandbox2 = getSdkSandboxNameForPackage(APP_PACKAGE);
        assertThat(processDump).contains(sdkSandbox2);

        installPackage(CODE_APK_2, "-d");

        processDump = getDevice().executeAdbCommand("shell", "ps", "-A");
        assertThat(processDump).contains(APP_PACKAGE);
        assertThat(processDump).doesNotContain(APP_2_PACKAGE);

        // TODO(b/215012578) check that sdk sandbox for app 1 is still running
    }
}
