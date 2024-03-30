/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.car;

import android.car.Car;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.ArrayMap;

import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Utility class */
public final class CarServiceUtils {

    private static final String TAG = CarLog.tagFor(CarServiceUtils.class);
    /** Empty int array */
    public  static final int[] EMPTY_INT_ARRAY = new int[0];
    private static final String COMMON_HANDLER_THREAD_NAME =
            "CarServiceUtils_COMMON_HANDLER_THREAD";

    private static final String PACKAGE_NOT_FOUND = "Package not found:";

    /** K: class name, V: HandlerThread */
    private static final ArrayMap<String, HandlerThread> sHandlerThreads = new ArrayMap<>();

    /** do not construct. static only */
    private CarServiceUtils() {}

    /**
     * Check if package name passed belongs to UID for the current binder call.
     * @param context
     * @param packageName
     */
    public static void assertPackageName(Context context, String packageName)
            throws IllegalArgumentException, SecurityException {
        if (packageName == null) {
            throw new IllegalArgumentException("Package name null");
        }
        ApplicationInfo appInfo = null;
        try {
            appInfo = context.getPackageManager().getApplicationInfo(packageName,
                    0);
        } catch (NameNotFoundException e) {
            String msg = PACKAGE_NOT_FOUND + packageName;
            Slogf.w(CarLog.TAG_SERVICE,  msg, e);
            throw new SecurityException(msg, e);
        }
        if (appInfo == null) {
            throw new SecurityException(PACKAGE_NOT_FOUND + packageName);
        }
        int uid = Binder.getCallingUid();
        if (uid != appInfo.uid) {
            throw new SecurityException("Wrong package name:" + packageName +
                    ", The package does not belong to caller's uid:" + uid);
        }
    }

    /**
     * Execute a runnable on the main thread
     *
     * @param action The code to run on the main thread.
     */
    public static void runOnMain(Runnable action) {
        runOnLooper(Looper.getMainLooper(), action);
    }

    /**
     * Execute a runnable in the given looper
     * @param looper Looper to run the action.
     * @param action The code to run.
     */
    public static void runOnLooper(Looper looper, Runnable action) {
        new Handler(looper).post(action);
    }

    /**
     * Execute a call on the application's main thread, blocking until it is
     * complete.  Useful for doing things that are not thread-safe, such as
     * looking at or modifying the view hierarchy.
     *
     * @param action The code to run on the main thread.
     */
    public static void runOnMainSync(Runnable action) {
        runOnLooperSync(Looper.getMainLooper(), action);
    }

    /**
     * Execute a delayed call on the application's main thread, blocking until it is
     * complete. See {@link #runOnMainSync(Runnable)}
     *
     * @param action The code to run on the main thread.
     * @param delayMillis The delay (in milliseconds) until the Runnable will be executed.
     */
    public static void runOnMainSyncDelayed(Runnable action, long delayMillis) {
        runOnLooperSyncDelayed(Looper.getMainLooper(), action, delayMillis);
    }

    /**
     * Execute a call on the given Looper thread, blocking until it is
     * complete.
     *
     * @param looper Looper to run the action.
     * @param action The code to run on the looper thread.
     */
    public static void runOnLooperSync(Looper looper, Runnable action) {
        runOnLooperSyncDelayed(looper, action, /* delayMillis */ 0L);
    }

    /**
     * Executes a delayed call on the given Looper thread, blocking until it is complete.
     *
     * @param looper Looper to run the action.
     * @param action The code to run on the looper thread.
     * @param delayMillis The delay (in milliseconds) until the Runnable will be executed.
     */
    public static void runOnLooperSyncDelayed(Looper looper, Runnable action, long delayMillis) {
        if (Looper.myLooper() == looper) {
            // requested thread is the same as the current thread. call directly.
            action.run();
        } else {
            Handler handler = new Handler(looper);
            SyncRunnable sr = new SyncRunnable(action);
            handler.postDelayed(sr, delayMillis);
            sr.waitForComplete();
        }
    }

    /**
     * Executes a runnable on the common thread. Useful for doing any kind of asynchronous work
     * across the car related code that doesn't need to be on the main thread.
     *
     * @param action The code to run on the common thread.
     */
    public static void runOnCommon(Runnable action) {
        runOnLooper(getCommonHandlerThread().getLooper(), action);
    }

    private static final class SyncRunnable implements Runnable {
        private final Runnable mTarget;
        private volatile boolean mComplete = false;

        public SyncRunnable(Runnable target) {
            mTarget = target;
        }

        @Override
        public void run() {
            mTarget.run();
            synchronized (this) {
                mComplete = true;
                notifyAll();
            }
        }

        public void waitForComplete() {
            synchronized (this) {
                while (!mComplete) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    public static float[] toFloatArray(List<Float> list) {
        int size = list.size();
        float[] array = new float[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    public static long[] toLongArray(List<Long> list) {
        int size = list.size();
        long[] array = new long[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    public static int[] toIntArray(List<Integer> list) {
        int size = list.size();
        int[] array = new int[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    public static byte[] toByteArray(List<Byte> list) {
        int size = list.size();
        byte[] array = new byte[size];
        for (int i = 0; i < size; ++i) {
            array[i] = list.get(i);
        }
        return array;
    }

    /**
     * Returns delta between elapsed time to uptime = {@link SystemClock#elapsedRealtime()} -
     * {@link SystemClock#uptimeMillis()}. Note that this value will be always >= 0.
     */
    public static long getUptimeToElapsedTimeDeltaInMillis() {
        int retry = 0;
        int max_retry = 2; // try only up to twice
        while (true) {
            long elapsed1 = SystemClock.elapsedRealtime();
            long uptime = SystemClock.uptimeMillis();
            long elapsed2 = SystemClock.elapsedRealtime();
            if (elapsed1 == elapsed2) { // avoid possible 1 ms fluctuation.
                return elapsed1 - uptime;
            }
            retry++;
            if (retry >= max_retry) {
                return elapsed1 - uptime;
            }
        }
    }

    /**
     * Gets a static instance of {@code HandlerThread} for the given {@code name}. If the thread
     * does not exist, create one and start it before returning.
     */
    public static HandlerThread getHandlerThread(String name) {
        synchronized (sHandlerThreads) {
            HandlerThread thread = sHandlerThreads.get(name);
            if (thread == null || !thread.isAlive()) {
                Slogf.i(TAG, "Starting HandlerThread:" + name);
                thread = new HandlerThread(name);
                thread.start();
                sHandlerThreads.put(name, thread);
            }
            return thread;
        }
    }

    /**
     * Gets the static instance of the common {@code HandlerThread} meant to be used across
     * CarService.
     */
    public static HandlerThread getCommonHandlerThread() {
        return getHandlerThread(COMMON_HANDLER_THREAD_NAME);
    }

    /**
     * Finishes all queued {@code Handler} tasks for {@code HandlerThread} created via
     * {@link#getHandlerThread(String)}. This is useful only for testing.
     */
    @VisibleForTesting
    public static void finishAllHandlerTasks() {
        ArrayList<HandlerThread> threads;
        synchronized (sHandlerThreads) {
            threads = new ArrayList<>(sHandlerThreads.values());
        }
        ArrayList<SyncRunnable> syncs = new ArrayList<>(threads.size());
        for (int i = 0; i < threads.size(); i++) {
            if (!threads.get(i).isAlive()) {
                continue;
            }
            Handler handler = new Handler(threads.get(i).getLooper());
            SyncRunnable sr = new SyncRunnable(() -> { });
            if (handler.post(sr)) {
                // Track the threads only where SyncRunnable is posted successfully.
                syncs.add(sr);
            }
        }
        for (int i = 0; i < syncs.size(); i++) {
            syncs.get(i).waitForComplete();
        }
    }

    /**
     * Assert if binder call is coming from system process like system server or if it is called
     * from its own process even if it is not system. The latter can happen in test environment.
     * Note that car service runs as system user but test like car service test will not.
     */
    public static void assertCallingFromSystemProcessOrSelf() {
        if (isCallingFromSystemProcessOrSelf()) {
            throw new SecurityException("Only allowed from system or self");
        }
    }

    /**
     * @return true if binder call is coming from system process like system server or if it is
     * called from its own process even if it is not system.
     */
    public static boolean isCallingFromSystemProcessOrSelf() {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        return uid != Process.SYSTEM_UID && pid != Process.myPid();
    }


    /** Utility for checking permission */
    public static void assertVehicleHalMockPermission(Context context) {
        assertPermission(context, Car.PERMISSION_MOCK_VEHICLE_HAL);
    }

    /** Utility for checking permission */
    public static void assertNavigationManagerPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_NAVIGATION_MANAGER);
    }

    /** Utility for checking permission */
    public static void assertClusterManagerPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL);
    }

    /** Utility for checking permission */
    public static void assertPowerPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_POWER);
    }

    /** Utility for checking permission */
    public static void assertProjectionPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_PROJECTION);
    }

    /** Verify the calling context has the {@link Car#PERMISSION_CAR_PROJECTION_STATUS} */
    public static void assertProjectionStatusPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_PROJECTION_STATUS);
    }

    /** Utility for checking permission */
    public static void assertAnyDiagnosticPermission(Context context) {
        assertAnyPermission(context,
                Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL,
                Car.PERMISSION_CAR_DIAGNOSTIC_CLEAR);
    }

    /** Utility for checking permission */
    public static void assertDrivingStatePermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_DRIVING_STATE);
    }

    /**
     * Verify the calling context has either {@link Car#PERMISSION_VMS_SUBSCRIBER} or
     * {@link Car#PERMISSION_VMS_PUBLISHER}
     */
    public static void assertAnyVmsPermission(Context context) {
        assertAnyPermission(context,
                Car.PERMISSION_VMS_SUBSCRIBER,
                Car.PERMISSION_VMS_PUBLISHER);
    }

    /** Utility for checking permission */
    public static void assertVmsPublisherPermission(Context context) {
        assertPermission(context, Car.PERMISSION_VMS_PUBLISHER);
    }

    /** Utility for checking permission */
    public static void assertVmsSubscriberPermission(Context context) {
        assertPermission(context, Car.PERMISSION_VMS_SUBSCRIBER);
    }

    /** Utility for checking permission */
    public static void assertPermission(Context context, String permission) {
        if (context.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("requires " + permission);
        }
    }

    /**
     * Checks to see if the caller has a permission.
     *
     * @return boolean TRUE if caller has the permission.
     */
    public static boolean hasPermission(Context context, String permission) {
        return context.checkCallingOrSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Utility for checking permission */
    public static void assertAnyPermission(Context context, String... permissions) {
        for (String permission : permissions) {
            if (context.checkCallingOrSelfPermission(permission)
                    == PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        throw new SecurityException("requires any of " + Arrays.toString(permissions));
    }

    /**
     * Turns a {@code SubscribeOptions} to {@code
     * android.hardware.automotive.vehicle.V2_0.SubscribeOptions}
     */
    public static android.hardware.automotive.vehicle.V2_0.SubscribeOptions subscribeOptionsToHidl(
            SubscribeOptions options) {
        android.hardware.automotive.vehicle.V2_0.SubscribeOptions hidlOptions =
                new android.hardware.automotive.vehicle.V2_0.SubscribeOptions();
        hidlOptions.propId = options.propId;
        hidlOptions.sampleRate = options.sampleRate;
        // HIDL backend requires flags to be set although it is not used any more.
        hidlOptions.flags = android.hardware.automotive.vehicle.V2_0.SubscribeFlags.EVENTS_FROM_CAR;
        // HIDL backend does not support area IDs, so we ignore options.areaId field.
        return hidlOptions;
    }
}
