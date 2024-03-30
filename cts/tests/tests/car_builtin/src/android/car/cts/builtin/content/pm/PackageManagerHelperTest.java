/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.car.cts.builtin.content.pm;

import static android.car.builtin.content.pm.PackageManagerHelper.PROPERTY_CAR_SERVICE_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.cts.builtin.R;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.function.ToIntFunction;

@RunWith(AndroidJUnit4.class)
public final class PackageManagerHelperTest {

    private static final String TAG = PackageManagerHelperTest.class.getSimpleName();
    private static final String ANDROID_CAR_PKG = "com.android.car";
    private static final String ANDROID_CAR_SHELL_PKG = "com.android.shell";
    private static final String ANDROID_CAR_SHELL_PKG_SHARED = "shared:android.uid.shell";
    private static final String CAR_BUILTIN_CTS_PKG = "android.car.cts.builtin";
    private static final String[] CAR_BUILTIN_CTS_SERVICE_NAMES = {
        "android.car.cts.builtin.os.SharedMemoryTestService",
        "android.car.cts.builtin.os.ServiceManagerTestService"
    };

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final PackageManager mPackageManager = mContext.getPackageManager();

    @Test
    public void testGetPackageInfoAsUser() throws Exception {
        String expectedActivityName = "android.car.cts.builtin.activity.SimpleActivity";
        int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_INSTRUMENTATION
                | PackageManager.GET_SERVICES;
        int curUser = UserHandle.myUserId();

        PackageInfo pkgInfoUser = PackageManagerHelper.getPackageInfoAsUser(mPackageManager,
                CAR_BUILTIN_CTS_PKG, flags, curUser);
        ApplicationInfo appInfo = pkgInfoUser.applicationInfo;
        ActivityInfo[] activities = pkgInfoUser.activities;
        ServiceInfo[] services = pkgInfoUser.services;

        assertThat(appInfo).isNotNull();
        assertThat(appInfo.descriptionRes).isEqualTo(R.string.app_description);
        assertThat(activities).isNotNull();
        assertThat(hasActivity(expectedActivityName, activities)).isTrue();
        assertThat(services).isNotNull();
    }

    @Test
    public void testAppTypeChecking() throws Exception {
        ApplicationInfo systemApp = mPackageManager
                .getApplicationInfo(ANDROID_CAR_PKG, /* flags= */ 0);
        ApplicationInfo ctsApp = mPackageManager
                .getApplicationInfo(CAR_BUILTIN_CTS_PKG, /* flags= */ 0);

        assertThat(PackageManagerHelper.isSystemApp(systemApp)).isTrue();
        assertThat(PackageManagerHelper.isUpdatedSystemApp(systemApp)).isFalse();
        assertThat(PackageManagerHelper.isSystemExtApp(systemApp)).isFalse();
        assertThat(PackageManagerHelper.isOemApp(systemApp)).isFalse();
        assertThat(PackageManagerHelper.isOdmApp(systemApp)).isFalse();
        assertThat(PackageManagerHelper.isVendorApp(systemApp)).isFalse();
        assertThat(PackageManagerHelper.isProductApp(systemApp)).isFalse();

        assertThat(PackageManagerHelper.isSystemApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isUpdatedSystemApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isSystemExtApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isOemApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isOdmApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isVendorApp(ctsApp)).isFalse();
        assertThat(PackageManagerHelper.isProductApp(ctsApp)).isFalse();
    }

    @Test
    public void testGetSystemUiPackageName() throws Exception {
        String systemuiPackageName = PackageManagerHelper.getSystemUiPackageName(mContext);
        // The default SystemUI package name is com.android.systemui. But OEMs can override
        // it via com.android.internal.R.string.config_systemUIServiceComponent.So, it can
        // not assert with respect to a specific (constant) value.
        Log.d(TAG, "System UI package name=" + systemuiPackageName);
        assertThat(systemuiPackageName).isNotNull();
    }

    @Test
    public void testGetNamesForUids() throws Exception {
        String[] initPackageNames = {ANDROID_CAR_SHELL_PKG, CAR_BUILTIN_CTS_PKG};
        // The CarShell has package name as com.android.shell but it also has sharedUserId as
        // android.uid.shell. Therefore, the return from Android framework should be
        // shared:com.android.shell instead of com.android.shell
        String[][] expectedPackageNames = {
            {ANDROID_CAR_SHELL_PKG_SHARED, CAR_BUILTIN_CTS_PKG},
            {ANDROID_CAR_SHELL_PKG_SHARED},
            {CAR_BUILTIN_CTS_PKG}
        };

        int[] packageUids = convertPackageNamesToUids(initPackageNames);
        int[][] packageUidsList = {
            packageUids,
            {packageUids[0]},
            {packageUids[1]}
        };

        for (int index = 0; index < expectedPackageNames.length; index++) {
            String[] packageNames = PackageManagerHelper
                    .getNamesForUids(mPackageManager, packageUidsList[index]);
            assertThat(packageNames).isEqualTo(expectedPackageNames[index]);
        }
    }

    @Test
    public void testGetPackageUidAsUser() throws Exception {
        int userId = UserHandle.SYSTEM.getIdentifier();
        int expectedUid = UserHandle.SYSTEM.getUid(Process.SYSTEM_UID);

        // com.android.car package has the shared SYSTEM_UID
        int actualUid = PackageManagerHelper
                .getPackageUidAsUser(mPackageManager, ANDROID_CAR_PKG, userId);

        assertThat(actualUid).isEqualTo(expectedUid);
    }

    @Test
    public void testGetComponentName() throws Exception {
        int flags = PackageManager.GET_ACTIVITIES | PackageManager.GET_INSTRUMENTATION
                | PackageManager.GET_SERVICES;
        int curUser = UserHandle.myUserId();
        PackageInfo pkgInfoUser = PackageManagerHelper.getPackageInfoAsUser(mPackageManager,
                CAR_BUILTIN_CTS_PKG, flags, curUser);
        ServiceInfo[] serviceInfos = pkgInfoUser.services;

        assertThat(serviceInfos).isNotNull();
        ArraySet<String> serviceClassSet = new ArraySet<>();
        for (ServiceInfo info : serviceInfos) {
            ComponentName componentName = PackageManagerHelper.getComponentName(info);
            Log.d(TAG, "class name: " + componentName.flattenToString());
            assertThat(componentName).isNotNull();
            assertThat(componentName.getPackageName()).isEqualTo(CAR_BUILTIN_CTS_PKG);
            serviceClassSet.add(componentName.getClassName());
        }

        assertThat(serviceClassSet.containsAll(Arrays.asList(CAR_BUILTIN_CTS_SERVICE_NAMES)))
                .isTrue();
    }

    @Test
    public void testCarServicePackageName() throws Exception {
        // The property must exist.
        String packageName = SystemProperties.get(
                PROPERTY_CAR_SERVICE_PACKAGE_NAME, /* def= */null);

        assertWithMessage("Property %s not defined", PROPERTY_CAR_SERVICE_PACKAGE_NAME).that(
                packageName).isNotNull();

        // The package must exist.
        PackageInfo info = mPackageManager.getPackageInfo(packageName, /* flags= */ 0);

        assertWithMessage("Package %s not found", packageName).that(info).isNotNull();
    }

    private boolean hasActivity(String activityName, ActivityInfo[] activities) {
        return Arrays.stream(activities).anyMatch(a -> activityName.equals(a.name));
    }

    private int[] convertPackageNamesToUids(String[] packageNames) {
        ToIntFunction<String> packageNameToUid = (pkgName) -> {
            int uid = Process.INVALID_UID;
            try {
                uid = mPackageManager.getPackageUid(pkgName, /* flags= */0);
            } catch (PackageManager.NameNotFoundException e) {
                Log.wtf(TAG, pkgName + " does not exist", e);
            }
            return uid;
        };
        return Arrays.stream(packageNames).mapToInt(packageNameToUid).toArray();
    }
}
