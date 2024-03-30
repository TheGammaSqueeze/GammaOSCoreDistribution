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

package com.android.csuite.core;

import com.android.csuite.core.TestUtils.TestUtilsException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** A utility class to install APKs. */
public final class ApkInstaller {
    private static long sCommandTimeOut = TimeUnit.MINUTES.toMillis(4);
    private final String mDeviceSerial;
    private final List<Path> mInstalledBaseApks = new ArrayList<>();
    private final IRunUtil mRunUtil;
    private final PackageNameParser mPackageNameParser;

    public static ApkInstaller getInstance(ITestDevice device) {
        return getInstance(device.getSerialNumber());
    }

    public static ApkInstaller getInstance(String deviceSerial) {
        return new ApkInstaller(deviceSerial, new RunUtil(), new AaptPackageNameParser());
    }

    @VisibleForTesting
    ApkInstaller(String deviceSerial, IRunUtil runUtil, PackageNameParser packageNameParser) {
        mDeviceSerial = deviceSerial;
        mRunUtil = runUtil;
        mPackageNameParser = packageNameParser;
    }

    /**
     * Installs a package.
     *
     * @param apkPath Path to the apk files. Only accept file/directory path containing a single APK
     *     or split APK files for one package.
     * @param args Install args for the 'adb install-multiple' command.
     * @throws ApkInstallerException If the installation failed.
     * @throws IOException If an IO exception occurred.
     */
    public void install(Path apkPath, String... args) throws ApkInstallerException, IOException {
        List<Path> apkFilePaths;
        try {
            apkFilePaths = TestUtils.listApks(apkPath);
        } catch (TestUtilsException e) {
            throw new ApkInstallerException("Failed to list APK files from the path " + apkPath, e);
        }

        CLog.d("Installing a package from " + apkPath);

        String[] cmd = createInstallCommand(apkFilePaths, mDeviceSerial, args);

        CommandResult res = mRunUtil.runTimedCmd(sCommandTimeOut, cmd);
        if (res.getStatus() != CommandStatus.SUCCESS) {
            throw new ApkInstallerException(
                    String.format(
                            "Failed to install APKs from the path %s: %s",
                            apkPath, res.toString()));
        }

        mInstalledBaseApks.add(apkFilePaths.get(0));

        CLog.i("Successfully installed " + apkPath);
    }

    /**
     * Attempts to uninstall all the installed packages.
     *
     * <p>When failed to uninstall one of the installed packages, this method will still attempt to
     * uninstall all other packages before throwing an exception.
     *
     * @throws ApkInstallerException when failed to uninstall a package.
     */
    public void uninstallAllInstalledPackages() throws ApkInstallerException {
        StringBuilder errorMessage = new StringBuilder();
        mInstalledBaseApks.forEach(
                baseApk -> {
                    String packageName;
                    try {
                        packageName = mPackageNameParser.parsePackageName(baseApk);
                    } catch (IOException e) {
                        errorMessage.append(
                                String.format(
                                        "Failed to parse the package name from %s. Reason: %s.\n",
                                        baseApk, e.getMessage()));
                        return;
                    }

                    String[] cmd =
                            new String[] {"adb", "-s", mDeviceSerial, "uninstall", packageName};

                    CommandResult res = mRunUtil.runTimedCmd(sCommandTimeOut, cmd);
                    if (res.getStatus() != CommandStatus.SUCCESS) {
                        errorMessage.append(
                                String.format(
                                        "Failed to uninstall package %s from %s. Reason: %s.\n",
                                        packageName, baseApk, res.toString()));
                    }
                });

        if (errorMessage.length() > 0) {
            throw new ApkInstallerException(errorMessage.toString());
        }
    }

    private String[] createInstallCommand(
            List<Path> apkFilePaths, String deviceSerial, String[] args) {
        ArrayList<String> cmd = new ArrayList<>();
        cmd.addAll(Arrays.asList("adb", "-s", deviceSerial, "install-multiple"));

        cmd.addAll(Arrays.asList(args));

        apkFilePaths.stream().map(Path::toString).forEach(cmd::add);

        return cmd.toArray(new String[cmd.size()]);
    }

    /** An exception class representing ApkInstaller error. */
    public static final class ApkInstallerException extends Exception {
        /**
         * Constructs a new {@link ApkInstallerException} with a meaningful error message.
         *
         * @param message A error message describing the cause of the error.
         */
        private ApkInstallerException(String message) {
            super(message);
        }

        /**
         * Constructs a new {@link ApkInstallerException} with a meaningful error message, and a
         * cause.
         *
         * @param message A detailed error message.
         * @param cause A {@link Throwable} capturing the original cause of the {@link
         *     ApkInstallerException}.
         */
        private ApkInstallerException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new {@link ApkInstallerException} with a cause.
         *
         * @param cause A {@link Throwable} capturing the original cause of the {@link
         *     ApkInstallerException}.
         */
        private ApkInstallerException(Throwable cause) {
            super(cause);
        }
    }

    private static final class AaptPackageNameParser implements PackageNameParser {
        @Override
        public String parsePackageName(Path apkFile) throws IOException {
            String packageName = AaptParser.parse(apkFile.toFile()).getPackageName();
            if (packageName == null) {
                throw new IOException(
                        String.format("Failed to parse package name with AAPT for %s", apkFile));
            }
            return packageName;
        }
    }

    @VisibleForTesting
    interface PackageNameParser {
        String parsePackageName(Path apkFile) throws IOException;
    }
}
