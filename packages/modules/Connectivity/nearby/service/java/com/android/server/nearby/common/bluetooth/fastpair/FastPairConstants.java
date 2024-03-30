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

import android.bluetooth.BluetoothDevice;

/** Constants to share with other team. */
public class FastPairConstants {
    private static final String PACKAGE_NAME = "com.android.server.nearby";
    private static final String PREFIX = PACKAGE_NAME + ".common.bluetooth.fastpair.";

    /** MODEL_ID item name for extended intent field. */
    public static final String EXTRA_MODEL_ID = PREFIX + "MODEL_ID";
    /** CONNECTION_ID item name for extended intent field. */
    public static final String EXTRA_CONNECTION_ID = PREFIX + "CONNECTION_ID";
    /** BLUETOOTH_MAC_ADDRESS item name for extended intent field. */
    public static final String EXTRA_BLUETOOTH_MAC_ADDRESS = PREFIX + "BLUETOOTH_MAC_ADDRESS";
    /** COMPANION_SCAN_ITEM item name for extended intent field. */
    public static final String EXTRA_SCAN_ITEM = PREFIX + "COMPANION_SCAN_ITEM";
    /** BOND_RESULT item name for extended intent field. */
    public static final String EXTRA_BOND_RESULT = PREFIX + "EXTRA_BOND_RESULT";

    /**
     * The bond result of the {@link BluetoothDevice} when FastPair launches the companion app, it
     * means device is BONDED but the pairing process is not triggered by FastPair.
     */
    public static final int BOND_RESULT_SUCCESS_WITHOUT_FP = 0;

    /**
     * The bond result of the {@link BluetoothDevice} when FastPair launches the companion app, it
     * means device is BONDED and the pairing process is triggered by FastPair.
     */
    public static final int BOND_RESULT_SUCCESS_WITH_FP = 1;

    /**
     * The bond result of the {@link BluetoothDevice} when FastPair launches the companion app, it
     * means the pairing process triggered by FastPair is failed due to the lack of PIN code.
     */
    public static final int BOND_RESULT_FAIL_WITH_FP_WITHOUT_PIN = 2;

    /**
     * The bond result of the {@link BluetoothDevice} when FastPair launches the companion app, it
     * means the pairing process triggered by FastPair is failed due to the PIN code is not
     * confirmed by the user.
     */
    public static final int BOND_RESULT_FAIL_WITH_FP_WITH_PIN_NOT_CONFIRMED = 3;

    /**
     * The bond result of the {@link BluetoothDevice} when FastPair launches the companion app, it
     * means the pairing process triggered by FastPair is failed due to the user thinks the PIN is
     * wrong.
     */
    public static final int BOND_RESULT_FAIL_WITH_FP_WITH_PIN_WRONG = 4;

    /**
     * The bond result of the {@link BluetoothDevice} when FastPair launches the companion app, it
     * means the pairing process triggered by FastPair is failed even after the user confirmed the
     * PIN code is correct.
     */
    public static final int BOND_RESULT_FAIL_WITH_FP_WITH_PIN_CORRECT = 5;

    private FastPairConstants() {}
}
