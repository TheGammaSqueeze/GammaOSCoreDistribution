/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.libraries.testing.deviceshadower.internal;

import android.content.ContentProvider;
import android.os.Looper;

import com.android.internal.annotations.VisibleForTesting;
import com.android.libraries.testing.deviceshadower.Enums.Distance;
import com.android.libraries.testing.deviceshadower.internal.bluetooth.BlueletImpl;
import com.android.libraries.testing.deviceshadower.internal.common.NamedRunnable;
import com.android.libraries.testing.deviceshadower.internal.common.Scheduler;
import com.android.libraries.testing.deviceshadower.internal.nfc.NfcletImpl;
import com.android.libraries.testing.deviceshadower.internal.sms.SmsContentProvider;
import com.android.libraries.testing.deviceshadower.internal.sms.SmsletImpl;
import com.android.libraries.testing.deviceshadower.internal.utils.Logger;

import com.google.common.collect.ImmutableList;

import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Proxy to manage internal data models, and help shadows to exchange data.
 */
public class DeviceShadowEnvironmentImpl {

    private static final Logger LOGGER = Logger.create("DeviceShadowEnvironmentImpl");
    private static final long SCHEDULER_WAIT_TIMEOUT_MILLIS = 5000L;

    // ThreadLocal to store local address for each device.
    private static InheritableThreadLocal<DeviceletImpl> sLocalDeviceletImpl =
            new InheritableThreadLocal<>();

    // Devicelets contains all registered devicelet to simulate a device.
    private static final Map<String, DeviceletImpl> DEVICELETS = new ConcurrentHashMap<>();

    @VisibleForTesting
    static final Map<String, ExecutorService> EXECUTORS = new ConcurrentHashMap<>();

    private static final List<DeviceShadowException> INTERNAL_EXCEPTIONS =
            Collections.synchronizedList(new ArrayList<DeviceShadowException>());

    private static final ContentProvider smsContentProvider = new SmsContentProvider();

    public static DeviceletImpl getDeviceletImpl(String address) {
        return DEVICELETS.get(address);
    }

    public static void checkInternalExceptions() {
        if (INTERNAL_EXCEPTIONS.size() > 0) {
            for (DeviceShadowException exception : INTERNAL_EXCEPTIONS) {
                LOGGER.e("Internal exception", exception);
            }
            INTERNAL_EXCEPTIONS.clear();
            throw new RuntimeException("DeviceShadower has internal exceptions");
        }
    }

    public static void reset() {
        // reset local devicelet for single device testing
        sLocalDeviceletImpl.remove();
        DEVICELETS.clear();
        BlueletImpl.reset();
        INTERNAL_EXCEPTIONS.clear();
    }

    public static boolean await(long timeoutMillis) {
        boolean schedulerDone = false;
        try {
            schedulerDone = Scheduler.await(timeoutMillis);
        } catch (InterruptedException e) {
            // no-op.
        } finally {
            if (!schedulerDone) {
                catchInternalException(new DeviceShadowException("Scheduler not complete"));
                for (DeviceletImpl devicelet : DEVICELETS.values()) {
                    LOGGER.e(
                            String.format(
                                    "Device %s\n\tUI: %s\n\tService: %s",
                                    devicelet.getAddress(),
                                    devicelet.getUiScheduler(),
                                    devicelet.getServiceScheduler()));
                }
                Scheduler.clear();
            }
        }
        for (ExecutorService executor : EXECUTORS.values()) {
            executor.shutdownNow();
        }
        boolean terminateSuccess = true;
        for (ExecutorService executor : EXECUTORS.values()) {
            try {
                executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                terminateSuccess = false;
            }
            if (!executor.isTerminated()) {
                LOGGER.e("Failed to terminate executor.");
                terminateSuccess = false;
            }
        }
        EXECUTORS.clear();
        return schedulerDone && terminateSuccess;
    }

    public static boolean hasLocalDeviceletImpl() {
        return sLocalDeviceletImpl.get() != null;
    }

    public static DeviceletImpl getLocalDeviceletImpl() {
        return sLocalDeviceletImpl.get();
    }

    public static List<DeviceletImpl> getDeviceletImpls() {
        return ImmutableList.copyOf(DEVICELETS.values());
    }

    public static BlueletImpl getLocalBlueletImpl() {
        return sLocalDeviceletImpl.get().blueletImpl();
    }

    public static BlueletImpl getBlueletImpl(String address) {
        DeviceletImpl devicelet = getDeviceletImpl(address);
        return devicelet == null ? null : devicelet.blueletImpl();
    }

    public static NfcletImpl getLocalNfcletImpl() {
        return sLocalDeviceletImpl.get().nfcletImpl();
    }

    public static NfcletImpl getNfcletImpl(String address) {
        DeviceletImpl devicelet = getDeviceletImpl(address);
        return devicelet == null ? null : devicelet.nfcletImpl();
    }

    public static SmsletImpl getLocalSmsletImpl() {
        return sLocalDeviceletImpl.get().smsletImpl();
    }

    public static ContentProvider getSmsContentProvider() {
        return smsContentProvider;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    public static DeviceletImpl addDevice(String address) {
        EXECUTORS.put(address, Executors.newCachedThreadPool());

        // DeviceShadower keeps track of the "local" device based on the current thread. It uses an
        // InheritableThreadLocal, so threads created by the current thread also get the same
        // thread-local value. Add the device on its own thread, to set the thread local for that
        // thread and its children.
        try {
            EXECUTORS
                    .get(address)
                    .submit(
                            () -> {
                                DeviceletImpl devicelet = new DeviceletImpl(address);
                                DEVICELETS.put(address, devicelet);
                                setLocalDevice(address);
                                // Ensure these threads are actually created, by posting one empty
                                // runnable.
                                devicelet.getServiceScheduler()
                                        .post(NamedRunnable.create("Init", () -> {
                                        }));
                                devicelet.getUiScheduler().post(NamedRunnable.create("Init", () -> {
                                }));
                            })
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        }

        return DEVICELETS.get(address);
    }

    public static void removeDevice(String address) {
        DEVICELETS.remove(address);
        EXECUTORS.remove(address);
    }

    public static void setInterruptibleBluetooth(int identifier) {
        getLocalBlueletImpl().setInterruptible(identifier);
    }

    public static void interruptBluetooth(String address, int identifier) {
        getBlueletImpl(address).interrupt(identifier);
    }

    public static void setDistance(String address1, String address2, final Distance distance) {
        final DeviceletImpl device1 = getDeviceletImpl(address1);
        final DeviceletImpl device2 = getDeviceletImpl(address2);

        Future<Void> result1 = null;
        Future<Void> result2 = null;
        if (device1.updateDistance(address2, distance)) {
            result1 =
                    run(
                            address1,
                            () -> {
                                device1.onDistanceChange(device2, distance);
                                return null;
                            });
        }

        if (device2.updateDistance(address1, distance)) {
            result2 =
                    run(
                            address2,
                            () -> {
                                device2.onDistanceChange(device1, distance);
                                return null;
                            });
        }

        try {
            if (result1 != null) {
                result1.get();
            }
            if (result2 != null) {
                result2.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            catchInternalException(new DeviceShadowException(e));
        }
    }

    /**
     * Set local Bluelet for current thread.
     *
     * <p>This can be used to convert current running thread to hold a bluelet object, so that unit
     * test does not have to call BluetoothEnvironment.run() to run code.
     */
    @VisibleForTesting
    public static void setLocalDevice(String address) {
        DeviceletImpl local = DEVICELETS.get(address);
        if (local == null) {
            throw new RuntimeException(address + " is not initialized by BluetoothEnvironment");
        }
        sLocalDeviceletImpl.set(local);
    }

    public static <T> Future<T> run(final String address, final Callable<T> snippet) {
        return EXECUTORS
                .get(address)
                .submit(
                        () -> {
                            DeviceShadowEnvironmentImpl.setLocalDevice(address);
                            ShadowLooper mainLooper = Shadows.shadowOf(Looper.getMainLooper());
                            try {
                                T result = snippet.call();

                                // Avoid idling the main looper in paused mode since doing so is
                                // only allowed from the main thread.
                                if (!mainLooper.isPaused()) {
                                    // In Robolectric, runnable doesn't run when posting thread
                                    // differs from looper thread, idle main looper explicitly to
                                    // execute posted Runnables.
                                    ShadowLooper.idleMainLooper();
                                }

                                // Wait all scheduled runnables complete.
                                Scheduler.await(SCHEDULER_WAIT_TIMEOUT_MILLIS);
                                return result;
                            } catch (Exception e) {
                                LOGGER.e("Fail to call code on device: " + address, e);
                                if (!mainLooper.isPaused()) {
                                    // reset() is not supported in paused mode.
                                    mainLooper.reset();
                                }
                                throw new RuntimeException(e);
                            }
                        });
    }

    // @CanIgnoreReturnValue
    // Return value can be ignored because {@link Scheduler} will call
    // {@link catchInternalException} to catch exceptions, and throw when test completes.
    public static Future<?> runOnUi(String address, NamedRunnable snippet) {
        Scheduler scheduler = DeviceShadowEnvironmentImpl.getDeviceletImpl(address)
                .getUiScheduler();
        return run(scheduler, address, snippet);
    }

    // @CanIgnoreReturnValue
    // Return value can be ignored because {@link Scheduler} will call
    // {@link catchInternalException} to catch exceptions, and throw when test completes.
    public static Future<?> runOnService(String address, NamedRunnable snippet) {
        Scheduler scheduler =
                DeviceShadowEnvironmentImpl.getDeviceletImpl(address).getServiceScheduler();
        return run(scheduler, address, snippet);
    }

    // @CanIgnoreReturnValue
    // Return value can be ignored because {@link Scheduler} will call
    // {@link catchInternalException} to catch exceptions, and throw when test completes.
    private static Future<?> run(
            Scheduler scheduler, final String address, final NamedRunnable snippet) {
        return scheduler.post(
                NamedRunnable.create(
                        snippet.toString(),
                        () -> {
                            DeviceShadowEnvironmentImpl.setLocalDevice(address);
                            snippet.run();
                        }));
    }

    public static void catchInternalException(Exception exception) {
        INTERNAL_EXCEPTIONS.add(new DeviceShadowException(exception));
    }

    // This is used to test Device Shadower internal.
    @VisibleForTesting
    public static void setDeviceletForTest(String address, DeviceletImpl devicelet) {
        DEVICELETS.put(address, devicelet);
    }

    @VisibleForTesting
    public static void setExecutorForTest(String address) {
        setExecutorForTest(address, Executors.newCachedThreadPool());
    }

    @VisibleForTesting
    public static void setExecutorForTest(String address, ExecutorService executor) {
        EXECUTORS.put(address, executor);
    }
}
