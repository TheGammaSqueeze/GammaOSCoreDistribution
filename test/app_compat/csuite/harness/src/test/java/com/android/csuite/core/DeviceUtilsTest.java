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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.service.dropbox.DropBoxManagerServiceDumpProto;

import com.android.csuite.core.DeviceUtils.DeviceTimestamp;
import com.android.csuite.core.DeviceUtils.DeviceUtilsException;
import com.android.csuite.core.DeviceUtils.DropboxEntry;
import com.android.tradefed.device.DeviceRuntimeException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.IRunUtil;

import com.google.common.jimfs.Jimfs;
import com.google.protobuf.ByteString;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(JUnit4.class)
public final class DeviceUtilsTest {
    private ITestDevice mDevice = Mockito.mock(ITestDevice.class);
    private IRunUtil mRunUtil = Mockito.mock(IRunUtil.class);
    private final FileSystem mFileSystem =
            Jimfs.newFileSystem(com.google.common.jimfs.Configuration.unix());

    @Test
    public void launchPackage_packageDoesNotExist_returnsFalse() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey -p")))
                .thenReturn(createFailedCommandResult());
        DeviceUtils sut = createSubjectUnderTest();

        assertThrows(DeviceUtilsException.class, () -> sut.launchPackage("package.name"));
    }

    @Test
    public void launchPackage_successfullyLaunchedThePackage_returnsTrue() throws Exception {
        when(mDevice.executeShellV2Command(Mockito.startsWith("monkey -p")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        DeviceUtils sut = createSubjectUnderTest();

        sut.launchPackage("package.name");
    }

    @Test
    public void currentTimeMillis_deviceCommandFailed_throwsException() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createFailedCommandResult());

        assertThrows(DeviceRuntimeException.class, () -> sut.currentTimeMillis());
    }

    @Test
    public void currentTimeMillis_unexpectedFormat_throwsException() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        assertThrows(DeviceRuntimeException.class, () -> sut.currentTimeMillis());
    }

    @Test
    public void currentTimeMillis_successful_returnsTime() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.startsWith("echo")))
                .thenReturn(createSuccessfulCommandResultWithStdout("123"));

        DeviceTimestamp result = sut.currentTimeMillis();

        assertThat(result.get()).isEqualTo(Long.parseLong("123"));
    }

    @Test
    public void runWithScreenRecording_recordingDidNotStart_jobIsExecuted() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenReturn(Mockito.mock(Process.class));
        when(mDevice.executeShellV2Command(Mockito.startsWith("ls")))
                .thenReturn(createFailedCommandResult());
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnableThrowingDeviceNotAvailable job = () -> executed.set(true);

        sut.runWithScreenRecording(job, video -> {});

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_recordCommandThrowsException_jobIsExecuted()
            throws Exception {
        when(mRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenThrow(new IOException());
        DeviceUtils sut = createSubjectUnderTest();
        AtomicBoolean executed = new AtomicBoolean(false);
        DeviceUtils.RunnableThrowingDeviceNotAvailable job = () -> executed.set(true);

        sut.runWithScreenRecording(job, video -> {});

        assertThat(executed.get()).isTrue();
    }

    @Test
    public void runWithScreenRecording_jobThrowsException_videoFileIsHandled() throws Exception {
        when(mRunUtil.runCmdInBackground(Mockito.argThat(contains("shell", "screenrecord"))))
                .thenReturn(Mockito.mock(Process.class));
        when(mDevice.executeShellV2Command(Mockito.startsWith("ls")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));
        DeviceUtils sut = createSubjectUnderTest();
        DeviceUtils.RunnableThrowingDeviceNotAvailable job =
                () -> {
                    throw new RuntimeException();
                };
        AtomicBoolean handled = new AtomicBoolean(false);

        assertThrows(
                RuntimeException.class,
                () -> sut.runWithScreenRecording(job, video -> handled.set(true)));

        assertThat(handled.get()).isTrue();
    }

    @Test
    public void getPackageVersionName_deviceCommandFailed_returnsUnknown() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(createFailedCommandResult());

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "unexpected " + DeviceUtils.VERSION_NAME_PREFIX));

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionName_deviceCommandSucceed_returnsVersionName() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionName")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_NAME_PREFIX + "123"));

        String result = sut.getPackageVersionName("any");

        assertThat(result).isEqualTo("123");
    }

    @Test
    public void getPackageVersionCode_deviceCommandFailed_returnsUnknown() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(createFailedCommandResult());

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandReturnsUnexpected_returnsUnknown()
            throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                "unexpected " + DeviceUtils.VERSION_CODE_PREFIX));

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo(DeviceUtils.UNKNOWN);
    }

    @Test
    public void getPackageVersionCode_deviceCommandSucceed_returnVersionCode() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mDevice.executeShellV2Command(Mockito.endsWith("grep versionCode")))
                .thenReturn(
                        createSuccessfulCommandResultWithStdout(
                                " " + DeviceUtils.VERSION_CODE_PREFIX + "123"));

        String result = sut.getPackageVersionCode("any");

        assertThat(result).isEqualTo("123");
    }

    @Test
    public void getDropboxEntries_noEntries_returnsEmptyList() throws Exception {
        DeviceUtils sut = createSubjectUnderTest();
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(),
                        Mockito.eq("sh"),
                        Mockito.eq("-c"),
                        Mockito.contains("dumpsys dropbox")))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        List<DropboxEntry> result = sut.getDropboxEntries(Set.of(""));

        assertThat(result).isEmpty();
    }

    @Test
    public void getDropboxEntries_entryExists_returnsEntry() throws Exception {
        Path dumpFile = Files.createTempFile(mFileSystem.getPath("/"), "test", ".tmp");
        long time = 123;
        String data = "abc";
        String tag = "tag";
        DropBoxManagerServiceDumpProto proto =
                DropBoxManagerServiceDumpProto.newBuilder()
                        .addEntries(
                                DropBoxManagerServiceDumpProto.Entry.newBuilder()
                                        .setTimeMs(time)
                                        .setData(ByteString.copyFromUtf8(data)))
                        .build();
        Files.write(dumpFile, proto.toByteArray());
        DeviceUtils sut = createSubjectUnderTestWithTempFile(dumpFile);
        when(mRunUtil.runTimedCmd(
                        Mockito.anyLong(), Mockito.eq("sh"), Mockito.eq("-c"), Mockito.anyString()))
                .thenReturn(createSuccessfulCommandResultWithStdout(""));

        List<DropboxEntry> result = sut.getDropboxEntries(Set.of(tag));

        assertThat(result.get(0).getTime()).isEqualTo(time);
        assertThat(result.get(0).getData()).isEqualTo(data);
        assertThat(result.get(0).getTag()).isEqualTo(tag);
    }

    private DeviceUtils createSubjectUnderTestWithTempFile(Path tempFile) {
        when(mDevice.getSerialNumber()).thenReturn("SERIAL");
        FakeClock fakeClock = new FakeClock();
        return new DeviceUtils(
                mDevice, fakeClock.getSleeper(), fakeClock, () -> mRunUtil, () -> tempFile);
    }

    private DeviceUtils createSubjectUnderTest() {
        when(mDevice.getSerialNumber()).thenReturn("SERIAL");
        FakeClock fakeClock = new FakeClock();
        return new DeviceUtils(
                mDevice,
                fakeClock.getSleeper(),
                fakeClock,
                () -> mRunUtil,
                () -> Files.createTempFile(mFileSystem.getPath("/"), "test", ".tmp"));
    }

    private static class FakeClock implements DeviceUtils.Clock {
        private long mCurrentTime = System.currentTimeMillis();
        private DeviceUtils.Sleeper mSleeper = duration -> mCurrentTime += duration;

        private DeviceUtils.Sleeper getSleeper() {
            return mSleeper;
        }

        @Override
        public long currentTimeMillis() {
            return mCurrentTime += 1;
        }
    }

    private static ArgumentMatcher<String[]> contains(String... args) {
        return array -> Arrays.asList(array).containsAll(Arrays.asList(args));
    }

    private static CommandResult createSuccessfulCommandResultWithStdout(String stdout) {
        CommandResult commandResult = new CommandResult(CommandStatus.SUCCESS);
        commandResult.setExitCode(0);
        commandResult.setStdout(stdout);
        commandResult.setStderr("");
        return commandResult;
    }

    private static CommandResult createFailedCommandResult() {
        CommandResult commandResult = new CommandResult(CommandStatus.FAILED);
        commandResult.setExitCode(1);
        commandResult.setStdout("");
        commandResult.setStderr("error");
        return commandResult;
    }
}
