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

package com.android.sts.common;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.collect.ImmutableList;

import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities to setup malloc debug options on a process, check for malloc debug errors, and cleaning
 * up afterwards.
 */
public class MallocDebug implements AutoCloseable {
    private static final String LOG_TAG = MallocDebug.class.getSimpleName();
    private static final String MALLOC_DEBUG_OPTIONS_PROP = "libc.debug.malloc.options";
    private static final String MALLOC_DEBUG_PROGRAM_PROP = "libc.debug.malloc.program";
    private static final Pattern[] mallocDebugErrorPatterns = {
        Pattern.compile("^.*HAS A CORRUPTED FRONT GUARD.*$", Pattern.MULTILINE),
        Pattern.compile("^.*HAS A CORRUPTED REAR GUARD.*$", Pattern.MULTILINE),
        Pattern.compile("^.*USED AFTER FREE.*$", Pattern.MULTILINE),
        Pattern.compile("^.*leaked block of size.*$", Pattern.MULTILINE),
        Pattern.compile("^.*UNKNOWN POINTER \\(free\\).*$", Pattern.MULTILINE),
        Pattern.compile("^.*HAS INVALID TAG.*$", Pattern.MULTILINE),
    };

    private ITestDevice device;
    private String processName;
    private AutoCloseable setMallocDebugOptionsProperty;
    private AutoCloseable setAttachedProgramProperty;
    private AutoCloseable killProcess;

    private MallocDebug(
            ITestDevice device, String mallocDebugOption, String processName, boolean isService)
            throws DeviceNotAvailableException, TimeoutException, ProcessUtil.KillException {
        this.device = device;
        this.processName = processName;

        // It's an error if this is called while something else is also doing malloc debug.
        assertNull(
                MALLOC_DEBUG_OPTIONS_PROP + " is already set!",
                device.getProperty(MALLOC_DEBUG_OPTIONS_PROP));
        CommandUtil.runAndCheck(device, "logcat -c");

        try {
            this.setMallocDebugOptionsProperty =
                    SystemUtil.withProperty(device, MALLOC_DEBUG_OPTIONS_PROP, mallocDebugOption);
            this.setAttachedProgramProperty =
                    SystemUtil.withProperty(device, MALLOC_DEBUG_PROGRAM_PROP, processName);

            // Kill and wait for the process to come back if we're attaching to a service
            this.killProcess = null;
            if (isService) {
                this.killProcess = ProcessUtil.withProcessKill(device, processName, null);
                ProcessUtil.waitProcessRunning(device, processName);
            }
        } catch (Throwable e1) {
            try {
                if (setMallocDebugOptionsProperty != null) {
                    setMallocDebugOptionsProperty.close();
                }
                if (setAttachedProgramProperty != null) {
                    setAttachedProgramProperty.close();
                }
            } catch (Exception e2) {
                CLog.e(e2);
                fail(
                        "Could not enable malloc debug. Additionally, there was an"
                                + " exception while trying to reset device state. Tests after"
                                + " this may not work as expected!\n"
                                + e2);
            }
            assumeNoException("Could not enable malloc debug", e1);
        }
    }

    @Override
    public void close() throws Exception {
        device.waitForDeviceAvailable();
        setMallocDebugOptionsProperty.close();
        setAttachedProgramProperty.close();
        if (killProcess != null) {
            try {
                killProcess.close();
                ProcessUtil.waitProcessRunning(device, processName);
            } catch (TimeoutException e) {
                assumeNoException(
                        "Could not restart '" + processName + "' after disabling malloc debug", e);
            }
        }
        String logcat = CommandUtil.runAndCheck(device, "logcat -d *:S malloc_debug:V").getStdout();
        assertNoMallocDebugErrors(logcat);
    }

    /**
     * Restart the given service and enable malloc debug on it, asserting no malloc debug error upon
     * closing.
     *
     * @param device the device to use
     * @param mallocDebugOptions value to set libc.debug.malloc.options to.
     * @param processName the service process to attach libc malloc debug to. Should be running.
     * @return The AutoCloseable object that will restart/unattach the service, disable libc malloc
     *     debug, and check for malloc debug errors when closed.
     */
    public static AutoCloseable withLibcMallocDebugOnService(
            ITestDevice device, String mallocDebugOptions, String processName)
            throws DeviceNotAvailableException, IllegalArgumentException, TimeoutException,
                ProcessUtil.KillException {
        if (processName == null || processName.isEmpty()) {
            throw new IllegalArgumentException("Service processName can't be empty");
        }
        return new MallocDebug(device, mallocDebugOptions, processName, true);
    }

    /**
     * Set up so that malloc debug will attach to the given processName, and assert no malloc debug
     * error upon closing. Note that processName will need to be manually launched after this call.
     *
     * @param device the device to use
     * @param mallocDebugOptions value to set libc.debug.malloc.options to.
     * @param processName the process to attach libc malloc debug to. Should not be running yet.
     * @return The AutoCloseable object that will disable libc malloc debug and check for malloc
     *     debug errors when closed.
     */
    public static AutoCloseable withLibcMallocDebugOnNewProcess(
            ITestDevice device, String mallocDebugOptions, String processName)
            throws DeviceNotAvailableException, IllegalArgumentException, TimeoutException,
                ProcessUtil.KillException {
        if (processName == null || processName.isEmpty()) {
            throw new IllegalArgumentException("processName can't be empty");
        }
        if (ProcessUtil.pidsOf(device, processName).isPresent()) {
            throw new IllegalArgumentException(processName + " is already running!");
        }
        return new MallocDebug(device, mallocDebugOptions, processName, false);
    }

    /**
     * Start attaching libc malloc debug to all processes launching after this call, asserting no
     * malloc debug error upon closing.
     *
     * @param device the device to use
     * @param mallocDebugOptions value to set libc.debug.malloc.options to.
     * @return The AutoCloseable object that will disable libc malloc debug and check for malloc
     *     debug errors when closed.
     */
    public static AutoCloseable withLibcMallocDebugOnAllNewProcesses(
            ITestDevice device, String mallocDebugOptions)
            throws DeviceNotAvailableException, TimeoutException, ProcessUtil.KillException {
        return new MallocDebug(device, mallocDebugOptions, null, false);
    }

    static void assertNoMallocDebugErrors(String logcat) {
        ImmutableList.Builder<String> mallocDebugErrors = new ImmutableList.Builder<String>();
        for (Pattern p : mallocDebugErrorPatterns) {
            Matcher m = p.matcher(logcat);
            while (m.find()) {
                mallocDebugErrors.add(m.group());
            }
        }
        assertArrayEquals(
                "Found malloc debug errors.", new String[] {}, mallocDebugErrors.build().toArray());
    }
}
