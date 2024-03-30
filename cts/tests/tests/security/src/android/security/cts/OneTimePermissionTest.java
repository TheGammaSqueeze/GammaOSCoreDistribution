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

import static android.Manifest.permission.CAMERA;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.compatibility.common.util.SystemUtil.eventually;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.DeviceConfig;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UiAutomatorUtils;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OneTimePermissionTest {

    private static final String CUSTOM_CAMERA_PERM_APP_PKG_NAME =
            "android.permission.cts.appthatrequestcustomcamerapermission";
    private static final String CUSTOM_CAMERA_PERM_APK =
            "/data/local/tmp/cts/permissions/CtsAppThatRequestCustomCameraPermission.apk";

    public static final String CUSTOM_PERMISSION = "appthatrequestcustomcamerapermission.CUSTOM";

    private static final long ONE_TIME_TIMEOUT_MILLIS = 5000;
    private static final long ONE_TIME_KILLED_DELAY_MILLIS = 5000;

    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private String mOldOneTimePermissionTimeoutValue;
    private String mOldOneTimePermissionKilledDelayValue;

    @Before
    public void wakeUpScreen() {
        SystemUtil.runShellCommand("input keyevent KEYCODE_WAKEUP");

        SystemUtil.runShellCommand("input keyevent 82");
    }

    @Before
    public void installApp() {
        runShellCommand("pm install -r " + CUSTOM_CAMERA_PERM_APK);
    }

    @Before
    public void prepareDeviceForOneTime() {
        runWithShellPermissionIdentity(() -> {
            mOldOneTimePermissionTimeoutValue = DeviceConfig.getProperty("permissions",
                    "one_time_permissions_timeout_millis");
            mOldOneTimePermissionKilledDelayValue = DeviceConfig.getProperty("permissions",
                    "one_time_permissions_killed_delay_millis");
            DeviceConfig.setProperty("permissions", "one_time_permissions_timeout_millis",
                    Long.toString(ONE_TIME_TIMEOUT_MILLIS), false);
            DeviceConfig.setProperty("permissions",
                    "one_time_permissions_killed_delay_millis",
                    Long.toString(ONE_TIME_KILLED_DELAY_MILLIS), false);
        });
    }

    @After
    public void uninstallApp() {
        runShellCommand("pm uninstall " + CUSTOM_CAMERA_PERM_APP_PKG_NAME);
    }

    @After
    public void restoreDeviceForOneTime() {
        runWithShellPermissionIdentity(
                () -> {
                    DeviceConfig.setProperty("permissions", "one_time_permissions_timeout_millis",
                            mOldOneTimePermissionTimeoutValue, false);
                    DeviceConfig.setProperty("permissions",
                            "one_time_permissions_killed_delay_millis",
                            mOldOneTimePermissionKilledDelayValue, false);
                });
    }

    @Test
    @AsbSecurityTest(cveBugId = 237405974L)
    public void testCustomPermissionIsGrantedOneTime() throws Throwable {
        Intent startApp = new Intent()
                .setComponent(new ComponentName(CUSTOM_CAMERA_PERM_APP_PKG_NAME,
                        CUSTOM_CAMERA_PERM_APP_PKG_NAME + ".RequestCameraPermission"))
                .addFlags(FLAG_ACTIVITY_NEW_TASK);

        mContext.startActivity(startApp);

        // We're only manually granting CAMERA, but the app will later request CUSTOM and get it
        // granted silently. This is intentional since it's in the same group but both should
        // eventually be revoked
        clickOneTimeButton();

        // Just waiting for the revocation
        eventually(() -> Assert.assertEquals(PackageManager.PERMISSION_DENIED,
                mContext.getPackageManager()
                        .checkPermission(CAMERA, CUSTOM_CAMERA_PERM_APP_PKG_NAME)));

        // This checks the vulnerability
        eventually(() -> Assert.assertEquals(PackageManager.PERMISSION_DENIED,
                mContext.getPackageManager()
                        .checkPermission(CUSTOM_PERMISSION, CUSTOM_CAMERA_PERM_APP_PKG_NAME)));

    }

    private void clickOneTimeButton() throws Throwable {
        final UiObject2 uiObject = UiAutomatorUtils.waitFindObject(By.res(
                "com.android.permissioncontroller:id/permission_allow_one_time_button"), 10000);
        Thread.sleep(500);
        uiObject.click();
    }
}
