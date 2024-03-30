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

package com.android.libraries.testing.deviceshadower.internal.bluetooth;

/**
 * A class to hold Bluetooth constants.
 */
public class BluetoothConstants {

    /*** Bluetooth Adapter State ***/
    // Must be identical to BluetoothAdapter hidden field STATE_BLE_TURNING_ON
    public static final int STATE_BLE_TURNING_ON = 14;

    // Must be identical to BluetoothAdapter hidden field STATE_BLE_ON
    public static final int STATE_BLE_ON = 15;

    // Must be identical to BluetoothAdapter hidden field STATE_BLE_TURNING_OFF
    public static final int STATE_BLE_TURNING_OFF = 16;

    // Must be identical to BluetoothAdapter hidden field ACTION_BLE_STATE_CHANGED
    public static final String ACTION_BLE_STATE_CHANGED =
            "android.bluetooth.adapter.action.BLE_STATE_CHANGED";

    /*** Rfcomm Socket ***/
    // Must be identical to BluetoothSocket field TYPE_RFCOMM.
    // The field was package-private before M.
    public static final int TYPE_RFCOMM = 1;

    public static final int SOCKET_CLOSE = -10000;

    // Android Bluetooth use -1 as port when creating server socket with uuid
    public static final int SERVER_SOCKET_CHANNEL_AUTO_ASSIGN = -1;

    // Android Bluetooth use -1 as port when creating socket with a uuid
    public static final int SOCKET_CHANNEL_CONNECT_WITH_UUID = -1;

    /*** BLE Advertise/Scan ***/
    // Must be identical to AdvertiseCallback hidden field ADVERTISE_SUCCESS.
    public static final int ADVERTISE_SUCCESS = 0;

    // Must be identical to ScanRecord field DATA_TYPE_FLAGS.
    public static final int DATA_TYPE_FLAGS = 0x01;

    // Must be identical to ScanRecord field DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE.
    public static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 0x07;

    // Must be identical to ScanRecord field DATA_TYPE_LOCAL_NAME_COMPLETE.
    public static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 0x09;

    // Must be identical to ScanRecord field DATA_TYPE_TX_POWER_LEVEL.
    public static final int DATA_TYPE_TX_POWER_LEVEL = 0x0A;

    // Must be identical to ScanRecord field DATA_TYPE_SERVICE_DATA.
    public static final int DATA_TYPE_SERVICE_DATA = 0x16;

    // Must be identical to ScanRecord field DATA_TYPE_MANUFACTURER_SPECIFIC_DATA.
    public static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 0xFF;

    /**
     * @see #DATA_TYPE_FLAGS
     */
    public interface Flags {

        byte LE_LIMITED_DISCOVERABLE_MODE = 1;
        byte LE_GENERAL_DISCOVERABLE_MODE = 1 << 1;
        byte BR_EDR_NOT_SUPPORTED = 1 << 2;
        byte SIMULTANEOUS_LE_AND_BR_EDR_CONTROLLER = 1 << 3;
        byte SIMULTANEOUS_LE_AND_BR_EDR_HOST = 1 << 4;
    }

    /**
     * Observed that Android sets this for {@link #DATA_TYPE_FLAGS} when a packet is connectable (on
     * a Nexus 6P running 7.1.2).
     */
    public static final byte FLAGS_IN_CONNECTABLE_PACKETS =
            Flags.BR_EDR_NOT_SUPPORTED
                    | Flags.LE_GENERAL_DISCOVERABLE_MODE
                    | Flags.SIMULTANEOUS_LE_AND_BR_EDR_CONTROLLER
                    | Flags.SIMULTANEOUS_LE_AND_BR_EDR_HOST;
}
