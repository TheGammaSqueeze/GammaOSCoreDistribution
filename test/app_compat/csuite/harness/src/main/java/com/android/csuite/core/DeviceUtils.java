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

package com.android.csuite.core;

import android.service.dropbox.DropBoxManagerServiceDumpProto;
import android.service.dropbox.DropBoxManagerServiceDumpProto.Entry;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.error.DeviceErrorIdentifier;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** A utility class that contains common methods to interact with the test device. */
public class DeviceUtils {
    @VisibleForTesting static final String UNKNOWN = "Unknown";
    @VisibleForTesting static final String VERSION_CODE_PREFIX = "versionCode=";
    @VisibleForTesting static final String VERSION_NAME_PREFIX = "versionName=";
    @VisibleForTesting static final String RESET_PACKAGE_COMMAND_PREFIX = "pm clear ";
    public static final Set<String> DROPBOX_APP_CRASH_TAGS =
            Set.of(
                    "SYSTEM_TOMBSTONE",
                    "system_app_anr",
                    "system_app_native_crash",
                    "system_app_crash",
                    "data_app_anr",
                    "data_app_native_crash",
                    "data_app_crash");

    @VisibleForTesting
    static final String LAUNCH_PACKAGE_COMMAND_TEMPLATE =
            "monkey -p %s -c android.intent.category.LAUNCHER 1";

    private static final String VIDEO_PATH_ON_DEVICE_TEMPLATE = "/sdcard/screenrecord_%s.mp4";
    @VisibleForTesting static final int WAIT_FOR_SCREEN_RECORDING_START_TIMEOUT_MILLIS = 10 * 1000;
    @VisibleForTesting static final int WAIT_FOR_SCREEN_RECORDING_START_INTERVAL_MILLIS = 500;

    private final ITestDevice mDevice;
    private final Sleeper mSleeper;
    private final Clock mClock;
    private final RunUtilProvider mRunUtilProvider;
    private final TempFileSupplier mTempFileSupplier;

    public static DeviceUtils getInstance(ITestDevice device) {
        return new DeviceUtils(
                device,
                duration -> {
                    Thread.sleep(duration);
                },
                () -> System.currentTimeMillis(),
                () -> RunUtil.getDefault(),
                () -> Files.createTempFile(TestUtils.class.getName(), ".tmp"));
    }

    @VisibleForTesting
    DeviceUtils(
            ITestDevice device,
            Sleeper sleeper,
            Clock clock,
            RunUtilProvider runUtilProvider,
            TempFileSupplier tempFileSupplier) {
        mDevice = device;
        mSleeper = sleeper;
        mClock = clock;
        mRunUtilProvider = runUtilProvider;
        mTempFileSupplier = tempFileSupplier;
    }

    /**
     * A runnable that throws DeviceNotAvailableException. Use this interface instead of Runnable so
     * that the DeviceNotAvailableException won't need to be handled inside the run() method.
     */
    public interface RunnableThrowingDeviceNotAvailable {
        void run() throws DeviceNotAvailableException;
    }

    /**
     * Get the current device timestamp in milliseconds.
     *
     * @return The device time
     * @throws DeviceNotAvailableException When the device is not available.
     * @throws DeviceRuntimeException When the command to get device time failed or failed to parse
     *     the timestamp.
     */
    public DeviceTimestamp currentTimeMillis()
            throws DeviceNotAvailableException, DeviceRuntimeException {
        CommandResult result = mDevice.executeShellV2Command("echo ${EPOCHREALTIME:0:14}");
        if (result.getStatus() != CommandStatus.SUCCESS) {
            throw new DeviceRuntimeException(
                    "Failed to get device time: " + result,
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
        try {
            return new DeviceTimestamp(Long.parseLong(result.getStdout().replace(".", "").trim()));
        } catch (NumberFormatException e) {
            CLog.e("Cannot parse device time string: " + result.getStdout());
            throw new DeviceRuntimeException(
                    "Cannot parse device time string: " + result.getStdout(),
                    DeviceErrorIdentifier.DEVICE_UNEXPECTED_RESPONSE);
        }
    }

    /**
     * Record the device screen while running a task.
     *
     * <p>This method will not throw exception when the screenrecord command failed unless the
     * device is unresponsive.
     *
     * @param action A runnable job that throws DeviceNotAvailableException.
     * @param handler A file handler that process the output screen record mp4 file located on the
     *     host.
     * @throws DeviceNotAvailableException When the device is unresponsive.
     */
    public void runWithScreenRecording(
            RunnableThrowingDeviceNotAvailable action, ScreenrecordFileHandler handler)
            throws DeviceNotAvailableException {
        String videoPath = String.format(VIDEO_PATH_ON_DEVICE_TEMPLATE, new Random().nextInt());
        mDevice.deleteFile(videoPath);

        // Start screen recording
        Process recordingProcess = null;
        try {
            recordingProcess =
                    mRunUtilProvider
                            .get()
                            .runCmdInBackground(
                                    String.format(
                                                    "adb -s %s shell screenrecord %s",
                                                    mDevice.getSerialNumber(), videoPath)
                                            .split("\\s+"));
        } catch (IOException ioException) {
            CLog.e("Exception is thrown when starting screen recording process: %s", ioException);
        }

        try {
            long start = mClock.currentTimeMillis();
            // Wait for the recording to start since it may take time for the device to start
            // recording
            while (recordingProcess != null) {
                CommandResult result = mDevice.executeShellV2Command("ls " + videoPath);
                if (result.getStatus() == CommandStatus.SUCCESS) {
                    break;
                }

                CLog.d(
                        "Screenrecord not started yet. Waiting %s milliseconds.",
                        WAIT_FOR_SCREEN_RECORDING_START_INTERVAL_MILLIS);

                try {
                    mSleeper.sleep(WAIT_FOR_SCREEN_RECORDING_START_INTERVAL_MILLIS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                if (mClock.currentTimeMillis() - start
                        > WAIT_FOR_SCREEN_RECORDING_START_TIMEOUT_MILLIS) {
                    CLog.e(
                            "Screenrecord did not start within %s milliseconds.",
                            WAIT_FOR_SCREEN_RECORDING_START_TIMEOUT_MILLIS);
                    break;
                }
            }

            action.run();
        } finally {
            if (recordingProcess != null) {
                recordingProcess.destroy();
            }
            // Try to pull, handle, and delete the video file from the device anyway.
            handler.handleScreenRecordFile(mDevice.pullFile(videoPath));
            mDevice.deleteFile(videoPath);
        }
    }

    /** A file handler for screen record results. */
    public interface ScreenrecordFileHandler {
        /**
         * Handles the screen record mp4 file located on the host.
         *
         * @param screenRecord The mp4 file located on the host. If screen record failed then the
         *     input could be null.
         */
        void handleScreenRecordFile(File screenRecord);
    }

    /**
     * Freeze the screen rotation to the default orientation.
     *
     * @return True if succeed; False otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean freezeRotation() throws DeviceNotAvailableException {
        CommandResult result =
                mDevice.executeShellV2Command(
                        "content insert --uri content://settings/system --bind"
                                + " name:s:accelerometer_rotation --bind value:i:0");
        if (result.getStatus() != CommandStatus.SUCCESS || result.getExitCode() != 0) {
            CLog.e("The command to disable auto screen rotation failed: %s", result);
            return false;
        }

        return true;
    }

    /**
     * Unfreeze the screen rotation to the default orientation.
     *
     * @return True if succeed; False otherwise.
     * @throws DeviceNotAvailableException
     */
    public boolean unfreezeRotation() throws DeviceNotAvailableException {
        CommandResult result =
                mDevice.executeShellV2Command(
                        "content insert --uri content://settings/system --bind"
                                + " name:s:accelerometer_rotation --bind value:i:1");
        if (result.getStatus() != CommandStatus.SUCCESS || result.getExitCode() != 0) {
            CLog.e("The command to enable auto screen rotation failed: %s", result);
            return false;
        }

        return true;
    }

    /**
     * Launches a package on the device.
     *
     * @param packageName The package name to launch.
     * @throws DeviceNotAvailableException When device was lost.
     * @throws DeviceUtilsException When failed to launch the package.
     */
    public void launchPackage(String packageName)
            throws DeviceUtilsException, DeviceNotAvailableException {
        CommandResult result =
                mDevice.executeShellV2Command(
                        String.format(LAUNCH_PACKAGE_COMMAND_TEMPLATE, packageName));
        if (result.getStatus() != CommandStatus.SUCCESS || result.getExitCode() != 0) {
            throw new DeviceUtilsException(
                    String.format(
                            "The command to launch package %s failed: %s", packageName, result));
        }
    }

    /**
     * Gets the version name of a package installed on the device.
     *
     * @param packageName The full package name to query
     * @return The package version name, or 'Unknown' if the package doesn't exist or the adb
     *     command failed.
     * @throws DeviceNotAvailableException
     */
    public String getPackageVersionName(String packageName) throws DeviceNotAvailableException {
        CommandResult cmdResult =
                mDevice.executeShellV2Command(
                        String.format("dumpsys package %s | grep versionName", packageName));

        if (cmdResult.getStatus() != CommandStatus.SUCCESS
                || !cmdResult.getStdout().trim().startsWith(VERSION_NAME_PREFIX)) {
            return UNKNOWN;
        }

        return cmdResult.getStdout().trim().substring(VERSION_NAME_PREFIX.length());
    }

    /**
     * Gets the version code of a package installed on the device.
     *
     * @param packageName The full package name to query
     * @return The package version code, or 'Unknown' if the package doesn't exist or the adb
     *     command failed.
     * @throws DeviceNotAvailableException
     */
    public String getPackageVersionCode(String packageName) throws DeviceNotAvailableException {
        CommandResult cmdResult =
                mDevice.executeShellV2Command(
                        String.format("dumpsys package %s | grep versionCode", packageName));

        if (cmdResult.getStatus() != CommandStatus.SUCCESS
                || !cmdResult.getStdout().trim().startsWith(VERSION_CODE_PREFIX)) {
            return UNKNOWN;
        }

        return cmdResult.getStdout().trim().split(" ")[0].substring(VERSION_CODE_PREFIX.length());
    }

    /**
     * Stops a running package on the device.
     *
     * @param packageName
     * @throws DeviceNotAvailableException
     */
    public void stopPackage(String packageName) throws DeviceNotAvailableException {
        mDevice.executeShellV2Command("am force-stop " + packageName);
    }

    /**
     * Resets a package's data storage on the device.
     *
     * @param packageName The package name of an app to reset.
     * @return True if the package exists and its data was reset; False otherwise.
     * @throws DeviceNotAvailableException If the device was lost.
     */
    public boolean resetPackage(String packageName) throws DeviceNotAvailableException {
        return mDevice.executeShellV2Command(RESET_PACKAGE_COMMAND_PREFIX + packageName).getStatus()
                == CommandStatus.SUCCESS;
    }

    /**
     * Gets dropbox entries from the device filtered by the provided tags.
     *
     * @param tags Dropbox tags to query.
     * @return A list of dropbox entries.
     * @throws IOException when failed to dump or read the dropbox protos.
     */
    public List<DropboxEntry> getDropboxEntries(Set<String> tags) throws IOException {
        List<DropboxEntry> entries = new ArrayList<>();

        for (String tag : tags) {
            Path dumpFile = mTempFileSupplier.get();

            CommandResult res =
                    mRunUtilProvider
                            .get()
                            .runTimedCmd(
                                    6000,
                                    "sh",
                                    "-c",
                                    String.format(
                                            "adb -s %s shell dumpsys dropbox --proto %s > %s",
                                            mDevice.getSerialNumber(), tag, dumpFile));
            if (res.getStatus() != CommandStatus.SUCCESS) {
                throw new IOException("Dropbox dump command failed: " + res);
            }

            DropBoxManagerServiceDumpProto p =
                    DropBoxManagerServiceDumpProto.parseFrom(Files.readAllBytes(dumpFile));
            Files.delete(dumpFile);

            for (Entry entry : p.getEntriesList()) {
                entries.add(
                        new DropboxEntry(entry.getTimeMs(), tag, entry.getData().toStringUtf8()));
            }
        }

        return entries;
    }

    /** A class that stores the information of a dropbox entry. */
    public static final class DropboxEntry {
        private final long mTime;
        private final String mTag;
        private final String mData;

        /** Returns the entrt's time stamp on device. */
        public long getTime() {
            return mTime;
        }

        /** Returns the entrt's tag. */
        public String getTag() {
            return mTag;
        }

        /** Returns the entrt's data. */
        public String getData() {
            return mData;
        }

        @VisibleForTesting
        DropboxEntry(long time, String tag, String data) {
            mTime = time;
            mTag = tag;
            mData = data;
        }
    }

    /** A general exception class representing failed device utility operations. */
    public static final class DeviceUtilsException extends Exception {
        /**
         * Constructs a new {@link DeviceUtilsException} with a meaningful error message.
         *
         * @param message A error message describing the cause of the error.
         */
        private DeviceUtilsException(String message) {
            super(message);
        }

        /**
         * Constructs a new {@link DeviceUtilsException} with a meaningful error message, and a
         * cause.
         *
         * @param message A detailed error message.
         * @param cause A {@link Throwable} capturing the original cause of the {@link
         *     DeviceUtilsException}.
         */
        private DeviceUtilsException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Constructs a new {@link DeviceUtilsException} with a cause.
         *
         * @param cause A {@link Throwable} capturing the original cause of the {@link
         *     DeviceUtilsException}.
         */
        private DeviceUtilsException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * A class to contain a device timestamp.
     *
     * <p>Use this class instead of long to pass device timestamps so that they are less likely to
     * be confused with host timestamps.
     */
    public static class DeviceTimestamp {
        private final long mTimestamp;

        public DeviceTimestamp(long timestamp) {
            mTimestamp = timestamp;
        }

        /** Gets the timestamp on a device. */
        public long get() {
            return mTimestamp;
        }
    }

    @VisibleForTesting
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }

    @VisibleForTesting
    interface Clock {
        long currentTimeMillis();
    }

    @VisibleForTesting
    interface RunUtilProvider {
        IRunUtil get();
    }

    @VisibleForTesting
    interface TempFileSupplier {
        Path get() throws IOException;
    }
}
