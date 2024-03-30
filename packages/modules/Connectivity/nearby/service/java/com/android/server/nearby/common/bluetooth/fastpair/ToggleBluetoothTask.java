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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/** Task for toggling Bluetooth on and back off again. */
interface ToggleBluetoothTask {

    /**
     * Toggles the bluetooth adapter off and back on again to help improve connection reliability.
     *
     * @throws InterruptedException when waiting for the bluetooth adapter's state to be set has
     *     been interrupted.
     * @throws ExecutionException when waiting for the bluetooth adapter's state to be set has
     *     failed.
     * @throws TimeoutException when the bluetooth adapter's state fails to be set on or off.
     */
    void toggleBluetooth() throws InterruptedException, ExecutionException, TimeoutException;
}
