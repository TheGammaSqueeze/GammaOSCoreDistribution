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

package com.android.server.nearby.common.bluetooth.fastpair;

/**
 * Constants to share with the cloud syncing process.
 */
public class BroadcastConstants {

    // TODO: Set right value for AOSP.
    /** Package name of the cloud syncing logic. */
    public static final String PACKAGE_NAME = "PACKAGE_NAME";
    /** Service name of the cloud syncing instance. */
    public static final String SERVICE_NAME = PACKAGE_NAME + ".SERVICE_NAME";
    private static final String PREFIX = PACKAGE_NAME + ".PREFIX_NAME.";

    /** Action when a fast pair device is added. */
    public static final String ACTION_FAST_PAIR_DEVICE_ADDED =
            PREFIX + "ACTION_FAST_PAIR_DEVICE_ADDED";
    /**
     * The BLE address of a device. BLE is used here instead of public because the caller of the
     * library never knows what the device's public address is.
     */
    public static final String EXTRA_ADDRESS = PREFIX + "BLE_ADDRESS";
    /** The public address of a device. */
    public static final String EXTRA_PUBLIC_ADDRESS = PREFIX + "PUBLIC_ADDRESS";
    /** Account key. */
    public static final String EXTRA_ACCOUNT_KEY = PREFIX + "ACCOUNT_KEY";
    /** Whether a paring is retroactive. */
    public static final String EXTRA_RETROACTIVE_PAIR = PREFIX + "EXTRA_RETROACTIVE_PAIR";

    private BroadcastConstants() {
    }
}
