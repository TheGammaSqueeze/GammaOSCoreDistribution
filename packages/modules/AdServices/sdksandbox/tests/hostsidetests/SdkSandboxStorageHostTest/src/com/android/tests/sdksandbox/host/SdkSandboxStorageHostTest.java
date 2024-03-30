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

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.annotation.Nullable;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxStorageHostTest extends BaseHostJUnit4Test {

    private int mOriginalUserId;
    private int mSecondaryUserId = -1;
    private boolean mWasRoot;

    private static final String CODE_PROVIDER_APK = "StorageTestCodeProvider.apk";
    private static final String TEST_APP_STORAGE_PACKAGE = "com.android.tests.sdksandbox";
    private static final String TEST_APP_STORAGE_APK = "SdkSandboxStorageTestApp.apk";
    private static final String TEST_APP_STORAGE_V2_NO_SDK =
            "SdkSandboxStorageTestAppV2_DoesNotConsumeSdk.apk";
    private static final String SDK_PACKAGE = "com.android.tests.codeprovider.storagetest";

    private static final String SYS_PROP_DEFAULT_CERT_DIGEST =
            "debug.pm.uses_sdk_library_default_cert_digest";

    private static final long SWITCH_USER_COMPLETED_NUMBER_OF_POLLS = 60;
    private static final long SWITCH_USER_COMPLETED_POLL_INTERVAL_IN_MILLIS = 1000;

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);

    /**
     * Runs the given phase of a test by calling into the device.
     * Throws an exception if the test phase fails.
     * <p>
     * For example, <code>runPhase("testExample");</code>
     */
    private void runPhase(String phase) throws Exception {
        assertThat(runDeviceTests("com.android.tests.sdksandbox",
                "com.android.tests.sdksandbox.SdkSandboxStorageTestApp",
                phase)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        // TODO(b/209061624): See if we can remove root privilege when instrumentation support for
        // sdk sandbox is added.
        mWasRoot = getDevice().isAdbRoot();
        getDevice().enableAdbRoot();
        uninstallPackage(TEST_APP_STORAGE_PACKAGE);
        mOriginalUserId = getDevice().getCurrentUser();
        setSystemProperty(SYS_PROP_DEFAULT_CERT_DIGEST, getPackageCertDigest(CODE_PROVIDER_APK));
    }

    @After
    public void tearDown() throws Exception {
        removeSecondaryUserIfNecessary();
        uninstallPackage(TEST_APP_STORAGE_PACKAGE);
        setSystemProperty(SYS_PROP_DEFAULT_CERT_DIGEST, "invalid");
        if (!mWasRoot) {
            getDevice().disableAdbRoot();
        }
    }

    @Test
    public void testSelinuxLabel() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);

        assertSelinuxLabel("/data/misc_ce/0/sdksandbox", "sdk_sandbox_system_data_file");
        assertSelinuxLabel("/data/misc_de/0/sdksandbox", "sdk_sandbox_system_data_file");

        // Check label of /data/misc_{ce,de}/0/sdksandbox/<package-name>
        assertSelinuxLabel(getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true),
                "sdk_sandbox_system_data_file");
        assertSelinuxLabel(getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false),
                "sdk_sandbox_system_data_file");
        // Check label of /data/misc_{ce,de}/0/sdksandbox/<app-name>/shared
        assertSelinuxLabel(getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, true),
                "sdk_sandbox_data_file");
        assertSelinuxLabel(getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, false),
                "sdk_sandbox_data_file");
        // Check label of /data/misc_{ce,de}/0/sdksandbox/<app-name>/<sdk-package>
        assertSelinuxLabel(getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_PACKAGE, true),
                "sdk_sandbox_data_file");
        assertSelinuxLabel(getSdkDataPerSdkPath(0, TEST_APP_STORAGE_PACKAGE, SDK_PACKAGE, false),
                "sdk_sandbox_data_file");
    }

    /**
     * Verify that {@code /data/misc_{ce,de}/<user-id>/sdksandbox} is created when
     * {@code <user-id>} is created.
     */
    @Test
    public void testSdkSandboxDataRootDirectory_IsCreatedOnUserCreate() throws Exception {
        {
            // Verify root directory exists for primary user
            final String cePath = getSdkDataRootPath(0, true);
            final String dePath = getSdkDataRootPath(0, false);
            assertThat(getDevice().isDirectory(dePath)).isTrue();
            assertThat(getDevice().isDirectory(cePath)).isTrue();
        }

        {
            // Verify root directory is created for new user
            mSecondaryUserId = createAndStartSecondaryUser();
            final String cePath = getSdkDataRootPath(mSecondaryUserId, true);
            final String dePath = getSdkDataRootPath(mSecondaryUserId, false);
            assertThat(getDevice().isDirectory(dePath)).isTrue();
            assertThat(getDevice().isDirectory(cePath)).isTrue();
        }
    }

    /**
     * Verify that {@code /data/misc_{ce,de}/<user-id>/sdksandbox} is not accessible by apps
     */
    @Test
    public void testSdkSandboxDataRootDirectory_IsNotAccessibleByApps() throws Exception {
        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // Verify root directory exists for primary user
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(dePath)).isTrue();
        assertThat(getDevice().isDirectory(cePath)).isTrue();

        runPhase("testSdkSandboxDataRootDirectory_IsNotAccessibleByApps");
    }

    @Test
    public void testSdkSandboxDataAppDirectory_IsCreatedOnInstall() throws Exception {
        // Directory should not exist before install
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();

        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // Verify directory is created
        assertThat(getDevice().isDirectory(cePath)).isTrue();
        assertThat(getDevice().isDirectory(dePath)).isTrue();
    }

    @Test
    public void testSdkSandboxDataAppDirectory_IsNotCreatedWithoutSdkConsumption()
            throws Exception {
        // Install the an app that does not consume sdk
        installPackage(TEST_APP_STORAGE_V2_NO_SDK);

        // Verify directories are not created
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();
    }

    @Test
    public void testSdkSandboxDataAppDirectory_IsDestroyedOnUninstall() throws Exception {
        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        //Uninstall the app
        uninstallPackage(TEST_APP_STORAGE_PACKAGE);

        // Directory should not exist after uninstall
        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        // Verify directory is destoyed
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();
    }

    @Test
    public void testSdkSandboxDataAppDirectory_IsClearedOnClearAppData() throws Exception {
        // Install the app
        installPackage(TEST_APP_STORAGE_APK);
        {
            // Verify directory is not clear
            final String ceDataSharedPath =
                    getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, true);
            final String[] ceChildren = getDevice().getChildren(ceDataSharedPath);
            {
                final String fileToDelete = ceDataSharedPath + "/deleteme.txt";
                getDevice().executeShellCommand("echo something to delete > " + fileToDelete);
                assertThat(getDevice().doesFileExist(fileToDelete)).isTrue();
            }
            assertThat(ceChildren.length).isNotEqualTo(0);
            final String deDataSharedPath =
                    getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, false);
            final String[] deChildren = getDevice().getChildren(deDataSharedPath);
            {
                final String fileToDelete = deDataSharedPath + "/deleteme.txt";
                getDevice().executeShellCommand("echo something to delete > " + fileToDelete);
                assertThat(getDevice().doesFileExist(fileToDelete)).isTrue();
            }
            assertThat(deChildren.length).isNotEqualTo(0);
        }

        // Clear the app data
        getDevice().executeShellCommand("pm clear " + TEST_APP_STORAGE_PACKAGE);
        {
            // Verify directory is cleared
            final String ceDataSharedPath =
                    getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, true);
            final String[] ceChildren = getDevice().getChildren(ceDataSharedPath);
            assertThat(ceChildren.length).isEqualTo(0);
            final String deDataSharedPath =
                    getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, false);
            final String[] deChildren = getDevice().getChildren(deDataSharedPath);
            assertThat(deChildren.length).isEqualTo(0);
        }
    }
    // TODO(b/221946754): Need to write tests for clearing cache and clearing code cache

    @Test
    public void testSdkSandboxDataAppDirectory_IsDestroyedOnUserDeletion() throws Exception {
        // Create new user
        mSecondaryUserId = createAndStartSecondaryUser();

        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // delete the new user
        removeSecondaryUserIfNecessary();

        // Sdk Sandbox root directories should not exist as the user was removed
        final String ceSdkSandboxDataRootPath = getSdkDataRootPath(mSecondaryUserId, true);
        final String deSdkSandboxDataRootPath = getSdkDataRootPath(mSecondaryUserId, false);
        assertThat(getDevice().isDirectory(ceSdkSandboxDataRootPath)).isFalse();
        assertThat(getDevice().isDirectory(deSdkSandboxDataRootPath)).isFalse();
    }

    @Test
    public void testSdkSandboxDataAppDirectory_IsUserSpecific() throws Exception {
        // Install first before creating the user
        installPackage(TEST_APP_STORAGE_APK, "--user all");

        mSecondaryUserId = createAndStartSecondaryUser();

        // Data directories should not exist as the package is not installed on new user
        final String ceAppPath = getAppDataPath(mSecondaryUserId, TEST_APP_STORAGE_PACKAGE, true);
        final String deAppPath = getAppDataPath(mSecondaryUserId, TEST_APP_STORAGE_PACKAGE, false);
        final String cePath = getSdkDataPackagePath(mSecondaryUserId,
                TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(mSecondaryUserId,
                TEST_APP_STORAGE_PACKAGE, false);

        assertThat(getDevice().isDirectory(ceAppPath)).isFalse();
        assertThat(getDevice().isDirectory(deAppPath)).isFalse();
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();

        // Install the app on new user
        installPackage(TEST_APP_STORAGE_APK);

        assertThat(getDevice().isDirectory(cePath)).isTrue();
        assertThat(getDevice().isDirectory(dePath)).isTrue();
    }

    @Test
    public void testSdkDataPackageDirectory_SharedStorageIsUsable() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);

        // Verify that shared storage exist
        final String sharedCePath = getSdkDataSharedPath(0, TEST_APP_STORAGE_PACKAGE, true);
        assertThat(getDevice().isDirectory(sharedCePath)).isTrue();

        // Write a file in the shared storage that code needs to read and write it back
        // in another file
        String fileToRead = sharedCePath + "/readme.txt";
        getDevice().executeShellCommand("echo something to read > " + fileToRead);
        assertThat(getDevice().doesFileExist(fileToRead)).isTrue();

        runPhase("testSdkSandboxDataAppDirectory_SharedStorageIsUsable");

        // Assert that code was able to create file and directories
        assertThat(getDevice().isDirectory(sharedCePath + "/dir")).isTrue();
        assertThat(getDevice().doesFileExist(sharedCePath + "/dir/file")).isTrue();
        String content = getDevice().executeShellCommand("cat " + sharedCePath + "/dir/file");
        assertThat(content).isEqualTo("something to read");
    }

    @Test
    public void testSdkDataPackageDirectory_OnUpdateDoesNotConsumeSdk() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);

        final String cePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, true);
        final String dePath = getSdkDataPackagePath(0, TEST_APP_STORAGE_PACKAGE, false);
        assertThat(getDevice().isDirectory(cePath)).isTrue();
        assertThat(getDevice().isDirectory(dePath)).isTrue();

        // Update app so that it no longer consumes any sdk
        installPackage(TEST_APP_STORAGE_V2_NO_SDK);
        assertThat(getDevice().isDirectory(cePath)).isFalse();
        assertThat(getDevice().isDirectory(dePath)).isFalse();
    }

    @Test
    public void testSdkDataPerSdkDirectory_IsCreatedOnInstall() throws Exception {
        // Directory should not exist before install
        assertThat(getSdkDataPerSdkPath(
                    0, TEST_APP_STORAGE_PACKAGE, SDK_PACKAGE, true)).isNull();
        assertThat(getSdkDataPerSdkPath(
                    0, TEST_APP_STORAGE_PACKAGE, SDK_PACKAGE, false)).isNull();

        // Install the app
        installPackage(TEST_APP_STORAGE_APK);

        // Verify directory is created
        assertThat(getSdkDataPerSdkPath(
                    0, TEST_APP_STORAGE_PACKAGE, SDK_PACKAGE, true)).isNotNull();
        assertThat(getSdkDataPerSdkPath(
                    0, TEST_APP_STORAGE_PACKAGE, SDK_PACKAGE, false)).isNotNull();
    }

    @Test
    public void testSdkData_CanBeMovedToDifferentVolume() throws Exception {
        assumeTrue(isAdoptableStorageSupported());

        installPackage(TEST_APP_STORAGE_APK);

        // Create a new adoptable storage where we will be moving our installed package
        final String diskId = getAdoptionDisk();
        try {
            assertEmpty(getDevice().executeShellCommand("sm partition " + diskId + " private"));
            final LocalVolumeInfo vol = getAdoptionVolume();

            assertSuccess(getDevice().executeShellCommand(
                    "pm move-package " + TEST_APP_STORAGE_PACKAGE + " " + vol.uuid));

            // Verify that sdk data is moved
            for (int i = 0; i < 2; i++) {
                boolean isCeData = (i == 0) ? true : false;
                final String sdkDataRootPath = "/mnt/expand/" + vol.uuid
                        + (isCeData ? "/misc_ce" : "/misc_de") +  "/0/sdksandbox";
                final String sdkDataPackagePath = sdkDataRootPath + "/" + TEST_APP_STORAGE_PACKAGE;
                final String sdkDataSharedPath = sdkDataPackagePath + "/" + "shared";

                assertThat(getDevice().isDirectory(sdkDataRootPath)).isTrue();
                assertThat(getDevice().isDirectory(sdkDataPackagePath)).isTrue();
                assertThat(getDevice().isDirectory(sdkDataSharedPath)).isTrue();

                assertSelinuxLabel(sdkDataRootPath, "system_data_file");
                assertSelinuxLabel(sdkDataPackagePath, "system_data_file");
                assertSelinuxLabel(sdkDataSharedPath, "sdk_sandbox_data_file");
            }
        } finally {
            getDevice().executeShellCommand("sm partition " + diskId + " public");
            getDevice().executeShellCommand("sm forget all");
        }

    }

    @Test
    @Ignore("b/224763009")
    public void testSdkDataIsAttributedToApp() throws Exception {
        installPackage(TEST_APP_STORAGE_APK);
        runPhase("testSdkDataIsAttributedToApp");
    }

    private String getAppDataPath(int userId, String packageName, boolean isCeData) {
        if (isCeData) {
            return String.format("/data/user/%d/%s", userId, packageName);
        } else {
            return String.format("/data/user_de/%d/%s", userId, packageName);
        }
    }

    private String getSdkDataRootPath(int userId, boolean isCeData) {
        if (isCeData) {
            return String.format("/data/misc_ce/%d/sdksandbox", userId);
        } else {
            return String.format("/data/misc_de/%d/sdksandbox", userId);
        }
    }

    private String getSdkDataPackagePath(int userId, String packageName, boolean isCeData) {
        return String.format(
            "%s/%s", getSdkDataRootPath(userId, isCeData), packageName);
    }

    private String getSdkDataSharedPath(int userId, String packageName,
            boolean isCeData) {
        return String.format(
            "%s/shared", getSdkDataPackagePath(userId, packageName, isCeData));
    }

    // Per-Sdk directory has random suffix. So we need to iterate over the app-level directory
    // to find it.
    @Nullable
    private String getSdkDataPerSdkPath(int userId, String packageName, String sdkName,
            boolean isCeData) throws Exception {
        final String appLevelPath = getSdkDataPackagePath(userId, packageName, isCeData);
        final String[] children = getDevice().getChildren(appLevelPath);
        String result = null;
        for (String child : children) {
            String[] tokens = child.split("@");
            if (tokens.length != 2) {
                continue;
            }
            String sdkNameFound = tokens[0];
            if (sdkName.equals(sdkNameFound)) {
                if (result == null) {
                    result = appLevelPath + "/" + child;
                } else {
                    throw new IllegalStateException("Found two per-sdk directory for " + sdkName);
                }
            }
        }
        return result;
    }

    private void assertSelinuxLabel(@Nullable String path, String label) throws Exception {
        assertThat(path).isNotNull();
        final String output = getDevice().executeShellCommand("ls -ldZ " + path);
        assertThat(output).contains("u:object_r:" + label);
    }

    private int createAndStartSecondaryUser() throws Exception {
        String name = "SdkSandboxStorageHostTest_User" + System.currentTimeMillis();
        int newId = getDevice().createUser(name);
        getDevice().startUser(newId);
        // Note we can't install apps on a locked user
        awaitUserUnlocked(newId);
        return newId;
    }

    private void awaitUserUnlocked(int userId) throws Exception {
        for (int i = 0; i < SWITCH_USER_COMPLETED_NUMBER_OF_POLLS; ++i) {
            String userState = getDevice().executeShellCommand("am get-started-user-state "
                    + userId);
            if (userState.contains("RUNNING_UNLOCKED")) {
                return;
            }
            Thread.sleep(SWITCH_USER_COMPLETED_POLL_INTERVAL_IN_MILLIS);
        }
        fail("Timed out in unlocking user: " + userId);
    }

    private void removeSecondaryUserIfNecessary() throws Exception {
        if (mSecondaryUserId != -1) {
            // Can't remove the 2nd user without switching out of it
            assertThat(getDevice().switchUser(mOriginalUserId)).isTrue();
            getDevice().removeUser(mSecondaryUserId);
            mSecondaryUserId = -1;
        }
    }

    /**
     * Extracts the certificate used to sign an apk in HexEncoded form.
     */
    private String getPackageCertDigest(String apkFileName) throws Exception {
        File apkFile = mHostUtils.getTestFile(apkFileName);
        JarFile apkJar = new JarFile(apkFile);
        JarEntry manifestEntry = apkJar.getJarEntry("AndroidManifest.xml");
        // #getCertificate can only be called once the JarEntry has been completely
        // verified by reading from the entry input stream until the end of the
        // stream has been reached.
        byte[] readBuffer = new byte[8192];
        InputStream input = new BufferedInputStream(apkJar.getInputStream(manifestEntry));
        while (input.read(readBuffer, 0, readBuffer.length) != -1) {
            // not used
        }
        // We can now call #getCertificates
        Certificate[] certs = manifestEntry.getCertificates();

        // Create SHA256 digest of the certificate
        MessageDigest sha256DigestCreator = MessageDigest.getInstance("SHA-256");
        sha256DigestCreator.update(certs[0].getEncoded());
        byte[] digest = sha256DigestCreator.digest();
        return new String(encodeToHex(digest)).trim();
    }

    /**
     * Encodes the provided data as a sequence of hexadecimal characters.
     */
    private static char[] encodeToHex(byte[] data) {
        final char[] digits = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
        };
        char[] result = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            int resultIndex = 2 * i;
            result[resultIndex] = (digits[(b >> 4) & 0x0f]);
            result[resultIndex + 1] = (digits[b & 0x0f]);
        }

        return result;
    }

    private void setSystemProperty(String name, String value) throws Exception {
        assertThat(getDevice().executeShellCommand(
              "setprop " + name + " " + value)).isEqualTo("");
    }

    private boolean isAdoptableStorageSupported() throws Exception {
        boolean hasFeature = getDevice().hasFeature("feature:android.software.adoptable_storage");
        boolean hasFstab = Boolean.parseBoolean(getDevice().executeShellCommand(
                    "sm has-adoptable").trim());
        return hasFeature && hasFstab;

    }

    private String getAdoptionDisk() throws Exception {
        // In the case where we run multiple test we cleanup the state of the device. This
        // results in the execution of sm forget all which causes the MountService to "reset"
        // all its knowledge about available drives. This can cause the adoptable drive to
        // become temporarily unavailable.
        int attempt = 0;
        String disks = getDevice().executeShellCommand("sm list-disks adoptable");
        while ((disks == null || disks.isEmpty()) && attempt++ < 15) {
            Thread.sleep(1000);
            disks = getDevice().executeShellCommand("sm list-disks adoptable");
        }

        if (disks == null || disks.isEmpty()) {
            throw new AssertionError("Devices that claim to support adoptable storage must have "
                    + "adoptable media inserted during CTS to verify correct behavior");
        }
        return disks.split("\n")[0].trim();
    }

    private static void assertSuccess(String str) {
        if (str == null || !str.startsWith("Success")) {
            throw new AssertionError("Expected success string but found " + str);
        }
    }

    private static void assertEmpty(String str) {
        if (str != null && str.trim().length() > 0) {
            throw new AssertionError("Expected empty string but found " + str);
        }
    }

    private static class LocalVolumeInfo {
        public String volId;
        public String state;
        public String uuid;

        LocalVolumeInfo(String line) {
            final String[] split = line.split(" ");
            volId = split[0];
            state = split[1];
            uuid = split[2];
        }
    }

    private LocalVolumeInfo getAdoptionVolume() throws Exception {
        String[] lines = null;
        int attempt = 0;
        int mounted_count = 0;
        while (attempt++ < 15) {
            lines = getDevice().executeShellCommand("sm list-volumes private").split("\n");
            CLog.w("getAdoptionVolume(): " + Arrays.toString(lines));
            for (String line : lines) {
                final LocalVolumeInfo info = new LocalVolumeInfo(line.trim());
                if (!"private".equals(info.volId)) {
                    if ("mounted".equals(info.state)) {
                        // make sure the storage is mounted and stable for a while
                        mounted_count++;
                        attempt--;
                        if (mounted_count >= 3) {
                            return waitForVolumeReady(info);
                        }
                    } else {
                        mounted_count = 0;
                    }
                }
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Expected private volume; found " + Arrays.toString(lines));
    }

    private LocalVolumeInfo waitForVolumeReady(LocalVolumeInfo vol) throws Exception {
        int attempt = 0;
        while (attempt++ < 15) {
            if (getDevice().executeShellCommand("dumpsys package volumes").contains(vol.volId)) {
                return vol;
            }
            Thread.sleep(1000);
        }
        throw new AssertionError("Volume not ready " + vol.volId);
    }
}
