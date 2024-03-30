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

package com.android.server.nearby.common.bluetooth;

import java.util.UUID;

/**
 * Bluetooth constants.
 */
public class BluetoothConsts {

    /**
     * Default MTU when value is unknown.
     */
    public static final int DEFAULT_MTU = 23;

    // The following random uuids are used to indicate that the device has dynamic services.
    /**
     * UUID of dynamic service.
     */
    public static final UUID SERVICE_DYNAMIC_SERVICE =
            UUID.fromString("00000100-0af3-11e5-a6c0-1697f925ec7b");

    /**
     * UUID of dynamic characteristic.
     */
    public static final UUID SERVICE_DYNAMIC_CHARACTERISTIC =
            UUID.fromString("00002A05-0af3-11e5-a6c0-1697f925ec7b");
}
