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

package android.os.lib.app;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.SharedLibraryInfo;
import android.os.IBinder;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * On-device tests driven by StaticSharedLibsHostTests.
 */
@RunWith(AndroidJUnit4.class)
public class StaticSharedLibsTests {

    private static final String APK_BASE_PATH = "/data/local/tmp/cts/hostside/os/";
    private static final String STATIC_LIB_PROVIDER1_APK = APK_BASE_PATH
            + "CtsStaticSharedLibProviderApp1.apk";
    private static final String STATIC_LIB_PROVIDER1_NAME = "foo.bar.lib";
    private static final Long STATIC_LIB_PROVIDER1_VERSION = 1L;

    private static final String STATIC_LIB_PROVIDER2_APK = APK_BASE_PATH
            + "CtsStaticSharedLibProviderApp2.apk";
    private static final String STATIC_LIB_PROVIDER2_PKG = "android.os.lib.provider";
    private static final String STATIC_LIB_PROVIDER2_NAME = "foo.bar.lib";
    private static final Long STATIC_LIB_PROVIDER2_VERSION = 2L;

    private static final String STATIC_LIB_PROVIDER5_PKG = "android.os.lib.provider";
    private static final String STATIC_LIB_PROVIDER5_NAME = "android.os.lib.provider_2";
    private static final Long STATIC_LIB_PROVIDER5_VERSION = 1L;
    private static final TestApp TESTAPP_STATIC_LIB_PROVIDER5 = new TestApp(
            "TestStaticSharedLibProvider5", STATIC_LIB_PROVIDER5_PKG, 1, /*isApex*/ false,
            "CtsStaticSharedLibProviderApp5.apk");

    public static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    private static final ComponentName CONSUMERAPP1_TEST_SERVICE = ComponentName.createRelative(
            "android.os.lib.consumer1", ".TestService");

    private final ServiceTestRule mServiceTestRule = new ServiceTestRule();

    @Before
    public void setUp() throws Exception {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INSTALL_PACKAGES);
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testSamegradeStaticSharedLibFail() throws Exception {
        try {
            Install.single(TESTAPP_STATIC_LIB_PROVIDER5).commit();
            assertThat(
                    getSharedLibraryInfo(STATIC_LIB_PROVIDER5_NAME, STATIC_LIB_PROVIDER5_VERSION))
                    .isNotNull();

            InstallUtils.commitExpectingFailure(AssertionError.class,
                    "Packages declaring static-shared libs cannot be updated",
                    Install.single(TESTAPP_STATIC_LIB_PROVIDER5));
        } finally {
            uninstallPackage(STATIC_LIB_PROVIDER5_PKG);
        }
    }

    @Test
    public void testInstallStaticSharedLib_notKillDependentApp() throws Exception {
        final Intent intent = new Intent();
        intent.setComponent(CONSUMERAPP1_TEST_SERVICE);
        final CountDownLatch kill = new CountDownLatch(1);
        mServiceTestRule.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                kill.countDown();
            }
        }, Context.BIND_AUTO_CREATE);

        try {
            installPackage(STATIC_LIB_PROVIDER2_APK);
            assertThat(
                    getSharedLibraryInfo(STATIC_LIB_PROVIDER2_NAME, STATIC_LIB_PROVIDER2_VERSION))
                    .isNotNull();

            assertThat(kill.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        }
    }

    @Test
    public void testSamegradeStaticSharedLib_killDependentApp() throws Exception {
        final Intent intent = new Intent();
        intent.setComponent(CONSUMERAPP1_TEST_SERVICE);
        final CountDownLatch kill = new CountDownLatch(1);
        mServiceTestRule.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
            }
            @Override
            public void onServiceDisconnected(ComponentName name) {
                kill.countDown();
            }
        }, Context.BIND_AUTO_CREATE);
        assertThat(
                getSharedLibraryInfo(STATIC_LIB_PROVIDER1_NAME, STATIC_LIB_PROVIDER1_VERSION))
                .isNotNull();

        installPackage(STATIC_LIB_PROVIDER1_APK);
        assertThat(kill.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isTrue();
    }

    private SharedLibraryInfo getSharedLibraryInfo(String libName, long version) {
        final PackageManager packageManager = InstrumentationRegistry.getContext()
                .getPackageManager();
        final Optional<SharedLibraryInfo> libraryInfo =
                packageManager.getSharedLibraries(0 /* flags */).stream().filter(
                        lib -> lib.getName().equals(libName) && lib.getLongVersion() == version)
                        .findFirst();
        return libraryInfo.isPresent() ? libraryInfo.get() : null;
    }

    private boolean installPackage(String apkPath) {
        return runShellCommand("pm install -t " + apkPath).equals("Success\n");
    }

    private void uninstallPackage(String packageName) {
        runShellCommand("pm uninstall " + packageName);
    }
}
