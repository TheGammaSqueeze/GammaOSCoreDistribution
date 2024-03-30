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

package com.android.webview.tests;

import com.android.csuite.core.ApkInstaller;
import com.android.csuite.core.ApkInstaller.ApkInstallerException;
import com.android.csuite.core.DeviceUtils;
import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DeviceUtils.DeviceUtilsException;
import com.android.csuite.core.TestUtils;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner.TestLogData;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A test that verifies that a single app can be successfully launched. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class WebviewAppLaunchTest extends BaseHostJUnit4Test {
    @Rule public TestLogData mLogData = new TestLogData();
    private ApkInstaller mApkInstaller;
    private List<File> mOrderedWebviewApks = new ArrayList<>();

    @Option(name = "record-screen", description = "Whether to record screen during test.")
    private boolean mRecordScreen;

    @Option(name = "package-name", description = "Package name of testing app.")
    private String mPackageName;

    @Option(
            name = "install-apk",
            description =
                    "The path to an apk file or a directory of apk files of a singe package to be"
                            + " installed on device. Can be repeated.")
    private List<File> mApkPaths = new ArrayList<>();

    @Option(
            name = "install-arg",
            description = "Arguments for the 'adb install-multiple' package installation command.")
    private final List<String> mInstallArgs = new ArrayList<>();

    @Option(
            name = "app-launch-timeout-ms",
            description = "Time to wait for an app to launch in msecs.")
    private int mAppLaunchTimeoutMs = 20000;

    @Option(
            name = "webview-apk-dir",
            description = "The path to the webview apk.",
            importance = Importance.ALWAYS)
    private File mWebviewApkDir;

    @Before
    public void setUp() throws DeviceNotAvailableException, ApkInstallerException, IOException {
        Assert.assertNotNull("Package name cannot be null", mPackageName);

        readWebviewApkDirectory();

        mApkInstaller = ApkInstaller.getInstance(getDevice());
        for (File apkPath : mApkPaths) {
            CLog.d("Installing " + apkPath);
            mApkInstaller.install(
                    apkPath.toPath(), mInstallArgs.toArray(new String[mInstallArgs.size()]));
        }

        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        deviceUtils.freezeRotation();

        printWebviewVersion();
    }

    @Test
    public void testAppLaunch()
            throws DeviceNotAvailableException, ApkInstallerException, IOException {
        AssertionError lastError = null;
        // Try the latest webview version
        WebviewPackage lastWebviewInstalled = installWebview(mOrderedWebviewApks.get(0));
        try {
            assertAppLaunchNoCrash();
        } catch (AssertionError e) {
            lastError = e;
        } finally {
            uninstallWebview();
        }

        // If the app doesn't crash, complete the test.
        if (lastError == null) {
            return;
        }

        // If the app crashes, try the app with the original webview version that comes with the
        // device.
        try {
            assertAppLaunchNoCrash();
        } catch (AssertionError newError) {
            CLog.w(
                    "The app %s crashed both with and without the webview installation,"
                            + " ignoring the failure...",
                    mPackageName);
            return;
        }

        for (int idx = 1; idx < mOrderedWebviewApks.size(); idx++) {
            lastWebviewInstalled = installWebview(mOrderedWebviewApks.get(idx));
            try {
                assertAppLaunchNoCrash();
            } catch (AssertionError e) {
                lastError = e;
                continue;
            } finally {
                uninstallWebview();
            }
            break;
        }

        throw new AssertionError(
                String.format(
                        "Package %s crashed since webview version %s",
                        mPackageName, lastWebviewInstalled.getVersion()),
                lastError);
    }

    @After
    public void tearDown() throws DeviceNotAvailableException, ApkInstallerException {
        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);
        testUtils.collectScreenshot(mPackageName);

        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        deviceUtils.stopPackage(mPackageName);
        deviceUtils.unfreezeRotation();

        mApkInstaller.uninstallAllInstalledPackages();
        printWebviewVersion();
    }

    private void readWebviewApkDirectory() {
        mOrderedWebviewApks = Arrays.asList(mWebviewApkDir.listFiles());
        Collections.sort(
                mOrderedWebviewApks,
                new Comparator<File>() {
                    @Override
                    public int compare(File apk1, File apk2) {
                        return getVersionCode(apk2).compareTo(getVersionCode(apk1));
                    }

                    private Long getVersionCode(File apk) {
                        return Long.parseLong(AaptParser.parse(apk).getVersionCode());
                    }
                });
    }

    private void printWebviewVersion(WebviewPackage currentWebview)
            throws DeviceNotAvailableException {
        CLog.i("Current webview implementation: %s", currentWebview.getPackageName());
        CLog.i("Current webview version: %s", currentWebview.getVersion());
    }

    private void printWebviewVersion() throws DeviceNotAvailableException {
        WebviewPackage currentWebview = getCurrentWebviewPackage();
        printWebviewVersion(currentWebview);
    }

    private WebviewPackage installWebview(File apk)
            throws ApkInstallerException, IOException, DeviceNotAvailableException {
        ApkInstaller.getInstance(getDevice()).install(apk.toPath());
        CommandResult res =
                getDevice()
                        .executeShellV2Command(
                                "cmd webviewupdate set-webview-implementation com.android.webview");
        Assert.assertEquals(
                "Failed to set webview update: " + res, res.getStatus(), CommandStatus.SUCCESS);
        WebviewPackage currentWebview = getCurrentWebviewPackage();
        printWebviewVersion(currentWebview);
        return currentWebview;
    }

    private void uninstallWebview() throws DeviceNotAvailableException {
        getDevice()
                .executeShellCommand(
                        "cmd webviewupdate set-webview-implementation com.google.android.webview");
        getDevice().executeAdbCommand("uninstall", "com.android.webview");
    }

    private WebviewPackage getCurrentWebviewPackage() throws DeviceNotAvailableException {
        String dumpsys = getDevice().executeShellCommand("dumpsys webviewupdate");
        return WebviewPackage.parseFrom(dumpsys);
    }

    private static class WebviewPackage {
        private final String mPackageName;
        private final String mVersion;

        private WebviewPackage(String packageName, String version) {
            mPackageName = packageName;
            mVersion = version;
        }

        static WebviewPackage parseFrom(String dumpsys) {
            Pattern pattern =
                    Pattern.compile("Current WebView package \\(name, version\\): \\((.*?)\\)");
            Matcher matcher = pattern.matcher(dumpsys);
            Assert.assertTrue("Cannot parse webview package info from: " + dumpsys, matcher.find());
            String[] packageInfo = matcher.group(1).split(",");
            return new WebviewPackage(packageInfo[0].strip(), packageInfo[1].strip());
        }

        String getPackageName() {
            return mPackageName;
        }

        String getVersion() {
            return mVersion;
        }
    }

    private void assertAppLaunchNoCrash() throws DeviceNotAvailableException {
        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        deviceUtils.resetPackage(mPackageName);
        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);

        if (mRecordScreen) {
            testUtils.collectScreenRecord(
                    () -> {
                        launchPackageAndCheckForCrash();
                    },
                    mPackageName);
        } else {
            launchPackageAndCheckForCrash();
        }
    }

    private void launchPackageAndCheckForCrash() throws DeviceNotAvailableException {
        CLog.d("Launching package: %s.", mPackageName);

        DeviceUtils deviceUtils = DeviceUtils.getInstance(getDevice());
        TestUtils testUtils = TestUtils.getInstance(getTestInformation(), mLogData);

        DeviceTimestamp startTime = deviceUtils.currentTimeMillis();
        try {
            deviceUtils.launchPackage(mPackageName);
        } catch (DeviceUtilsException e) {
            Assert.fail(e.getMessage());
        }

        CLog.d("Waiting %s milliseconds for the app to launch fully.", mAppLaunchTimeoutMs);
        RunUtil.getDefault().sleep(mAppLaunchTimeoutMs);

        CLog.d("Completed launching package: %s", mPackageName);

        try {
            String crashLog = testUtils.getDropboxPackageCrashLog(mPackageName, startTime, true);
            if (crashLog != null) {
                Assert.fail(crashLog);
            }
        } catch (IOException e) {
            Assert.fail("Error while getting dropbox crash log: " + e);
        }
    }
}
