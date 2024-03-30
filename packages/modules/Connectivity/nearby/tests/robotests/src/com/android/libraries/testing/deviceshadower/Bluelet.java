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

import android.os.ParcelUuid;

/**
 * User interface for mocking and simulation of a Bluetooth device.
 */
public interface Bluelet {

    /**
     * See {@link #setCreateBondOutcome}.
     */
    enum CreateBondOutcome {
        SUCCESS,
        FAILURE,
        TIMEOUT
    }

    /**
     * See {@link #setIoCapabilities}. Note that Bluetooth specifies a few more choices, but this is
     * all DeviceShadower currently supports.
     */
    enum IoCapabilities {
        NO_INPUT_NO_OUTPUT,
        DISPLAY_YES_NO,
        KEYBOARD_ONLY
    }

    /**
     * See {@link #setFetchUuidsTiming}.
     */
    enum FetchUuidsTiming {
        BEFORE_BONDING,
        AFTER_BONDING,
        NEVER
    }

    /**
     * Set the initial state of the local Bluetooth adapter at the beginning of the test.
     * <p>This method is not associated with broadcast event and is intended to be called at the
     * beginning of the test. Allowed states:
     *
     * @see android.bluetooth.BluetoothAdapter#STATE_OFF
     * @see android.bluetooth.BluetoothAdapter#STATE_ON
     * </p>
     */
    Bluelet setAdapterInitialState(int state) throws IllegalArgumentException;

    /**
     * Set the bluetooth class of the local Bluetooth device at the beginning of the test.
     * <p>
     *
     * @see android.bluetooth.BluetoothClass.Device
     * @see android.bluetooth.BluetoothClass.Service
     */
    Bluelet setBluetoothClass(int bluetoothClass);

    /**
     * Set the scan mode of the local Bluetooth device at the beginning of the test.
     */
    Bluelet setScanMode(int scanMode);

    /**
     * Set the Bluetooth profiles supported by this device (e.g. A2DP Sink).
     */
    Bluelet setProfileUuids(ParcelUuid... profileUuids);

    /**
     * Makes bond attempts with this device succeed or fail.
     *
     * @param failureReason Ignored unless outcome is {@link CreateBondOutcome#FAILURE}. This is
     * delivered in the intent that indicates bond state has changed to BOND_NONE. Values:
     * https://cs.corp.google.com/android/frameworks/base/core/java/android/bluetooth/BluetoothDevice.java?rcl=38d9ee4cd661c10e012f71051d23644c65607eed&l=472
     */
    Bluelet setCreateBondOutcome(CreateBondOutcome outcome, int failureReason);

    /**
     * Sets the IO capabilities of this device. When bonding, a device states its IO capabilities in
     * the pairing request. The pairing variant used depends on the IO capabilities of both devices
     * (e.g. Just Works is the only available option for a NoInputNoOutput device, while Numeric
     * Comparison aka Passkey Confirmation is used if both devices have a display and the ability to
     * confirm/deny).
     *
     * @see <a href="https://blog.bluetooth.com/bluetooth-pairing-part-4">Bluetooth blog</a>
     */
    Bluelet setIoCapabilities(IoCapabilities ioCapabilities);

    /**
     * Make the device refuse connections. By default, connections are accepted.
     *
     * @param refuse Connections are refused if True.
     */
    Bluelet setRefuseConnections(boolean refuse);

    /**
     * Make the device refuse GATT connections. By default. connections are accepted.
     *
     * @param refuse GATT connections are refused if true.
     */
    Bluelet setRefuseGattConnections(boolean refuse);

    /**
     * When to send the ACTION_UUID broadcast. This can be {@link FetchUuidsTiming#BEFORE_BONDING},
     * {@link FetchUuidsTiming#AFTER_BONDING}, or {@link FetchUuidsTiming#NEVER}. The default is
     * {@link FetchUuidsTiming#AFTER_BONDING}.
     */
    Bluelet setFetchUuidsTiming(FetchUuidsTiming fetchUuidsTiming);

    /**
     * Adds a bonded device to the BluetoothAdapter.
     */
    Bluelet addBondedDevice(String address);

    /**
     * Enables the CVE-2019-2225 represents that the pairing variant will switch from Just Works to
     * Consent when local device's io capability is Display Yes/No and remote is NoInputNoOutput.
     *
     * @see <a href="https://source.android.com/security/bulletin/2019-12-01#system">the security
     * bulletin at 2019-12-01</a>
     */
    Bluelet enableCVE20192225(boolean value);
}
