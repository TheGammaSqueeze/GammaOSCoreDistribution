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

package com.android.cts.appcloning;

import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.NativeDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import java.util.function.BooleanSupplier;


abstract class BaseHostTestCase extends BaseHostJUnit4Test {
    private int mCurrentUserId = NativeDevice.INVALID_USER_ID;
    private static final String ERROR_MESSAGE_TAG = "[ERROR]";
    protected ITestDevice mDevice = null;

    protected void setDevice() {
        mDevice = getDevice();
    }

    protected String executeShellCommand(String cmd, Object... args) throws Exception {
        return mDevice.executeShellCommand(String.format(cmd, args));
    }

    protected CommandResult executeShellV2Command(String cmd, Object... args) throws Exception {
        return mDevice.executeShellV2Command(String.format(cmd, args));
    }

    protected boolean isPackageInstalled(String packageName, String userId) throws Exception {
        return mDevice.isPackageInstalled(packageName, userId);
    }

    // TODO (b/174775905) remove after exposing the check from ITestDevice.
    protected boolean isHeadlessSystemUserMode() throws DeviceNotAvailableException {
        String result = mDevice
                .executeShellCommand("getprop ro.fw.mu.headless_system_user").trim();
        return "true".equalsIgnoreCase(result);
    }

    protected boolean supportsMultipleUsers() throws DeviceNotAvailableException {
        return mDevice.getMaxNumberOfUsersSupported() > 1;
    }

    protected boolean isAtLeastS() throws DeviceNotAvailableException {
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(mDevice);
        return deviceSdkLevel.isDeviceAtLeastS();
    }

    protected boolean isAtLeastT() throws DeviceNotAvailableException {
        DeviceSdkLevel deviceSdkLevel = new DeviceSdkLevel(mDevice);
        return deviceSdkLevel.isDeviceAtLeastT();
    }

    protected static void throwExceptionIfTimeout(long start, long timeoutMillis, Throwable e) {
        if (System.currentTimeMillis() - start < timeoutMillis) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException(e);
        }
    }

    protected static void eventually(ThrowingRunnable r, long timeoutMillis) {
        long start = System.currentTimeMillis();

        while (true) {
            try {
                r.run();
                return;
            } catch (Throwable e) {
                throwExceptionIfTimeout(start, timeoutMillis, e);
            }
        }
    }

    protected static void eventually(ThrowingBooleanSupplier booleanSupplier,
            long timeoutMillis, String failureMessage) {
        long start = System.currentTimeMillis();

        while (true) {
            try {
                if (booleanSupplier.getAsBoolean()) {
                    return;
                }

                throw new RuntimeException(failureMessage);
            } catch (Throwable e) {
                throwExceptionIfTimeout(start, timeoutMillis, e);
            }
        }
    }

    protected int getCurrentUserId() throws Exception {
        setCurrentUserId();

        return mCurrentUserId;
    }

    protected boolean isSuccessful(CommandResult result) {
        if (!CommandStatus.SUCCESS.equals(result.getStatus())) {
            return false;
        }
        String stdout = result.getStdout();
        if (stdout.contains(ERROR_MESSAGE_TAG)) {
            return false;
        }
        String stderr = result.getStderr();
        return (stderr == null || stderr.trim().isEmpty());
    }

    private void setCurrentUserId() throws Exception {
        if (mCurrentUserId != NativeDevice.INVALID_USER_ID) return;

        mCurrentUserId = mDevice.getCurrentUser();
        CLog.i("Current user: %d");
    }

    protected interface ThrowingRunnable {
        /**
         * Similar to {@link Runnable#run} but has {@code throws Exception}.
         */
        void run() throws Exception;
    }

    protected interface ThrowingBooleanSupplier {
        /**
         * Similar to {@link BooleanSupplier#getAsBoolean} but has {@code throws Exception}.
         */
        boolean getAsBoolean() throws Exception;
    }
}
