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

package com.android.libraries.testing.deviceshadower;

import com.android.libraries.testing.deviceshadower.Enums.Distance;
import com.android.libraries.testing.deviceshadower.internal.DeviceShadowEnvironmentImpl;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

/**
 * Environment to setup and config Bluetooth unit test.
 */
public class DeviceShadowEnvironment {

    private static final String TAG = "DeviceShadowEnvironment";
    private static final long RESET_TIMEOUT_MILLIS = 3000;

    private static boolean sIsInitialized = false;

    private DeviceShadowEnvironment() {
    }

    public static void init() {
        sIsInitialized = true;
        DeviceShadowEnvironmentImpl.reset();
    }

    public static void reset() {
        sIsInitialized = false;

        // Order matters because each steps check and manipulate internal objects in order.
        // Wait Scheduler and executors complete, and shut down executors.
        DeviceShadowEnvironmentImpl.await(RESET_TIMEOUT_MILLIS);

        // Throw RuntimeException if there is any internal exceptions.
        DeviceShadowEnvironmentImpl.checkInternalExceptions();

        // Clear internal exceptions, and devicelets.
        DeviceShadowEnvironmentImpl.reset();
    }

    public static boolean await(long timeoutMillis) {
        return DeviceShadowEnvironmentImpl.await(timeoutMillis);
    }

    public static Devicelet addDevice(final String address) {
        return DeviceShadowEnvironmentImpl.addDevice(address);
    }

    public static void removeDevice(String address) {
        DeviceShadowEnvironmentImpl.removeDevice(address);
    }

    public static void setLocalDevice(final String address) {
        DeviceShadowEnvironmentImpl.setLocalDevice(address);
    }

    public static void putNear(String address1, String address2) {
        DeviceShadowEnvironmentImpl.setDistance(address1, address2, Distance.NEAR);
    }

    public static void setDistance(String address1, String address2, Distance distance) {
        DeviceShadowEnvironmentImpl.setDistance(address1, address2, distance);
    }

    public static Future<Void> run(final String address, final Runnable snippet) {
        return run(
                address,
                () -> {
                    snippet.run();
                    return null;
                });
    }

    public static <T> Future<T> run(final String address, final Callable<T> snippet) {
        return DeviceShadowEnvironmentImpl.run(address, snippet);
    }

    /* package */
    static boolean isInitialized() {
        return sIsInitialized;
    }
}
