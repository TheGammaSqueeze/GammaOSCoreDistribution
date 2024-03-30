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
 * Reserved UUIDS by BT SIG.
 * <p>
 * See https://developer.bluetooth.org for more details.
 */
public class ReservedUuids {
    /** UUIDs reserved for services. */
    public static class Services {
        /**
         * The Device Information Service exposes manufacturer and/or vendor info about a device.
         * <p>
         * See reserved UUID org.bluetooth.service.device_information.
         */
        public static final UUID DEVICE_INFORMATION = fromShortUuid((short) 0x180A);

        /**
         * Generic attribute service.
         * <p>
         * See reserved UUID org.bluetooth.service.generic_attribute.
         */
        public static final UUID GENERIC_ATTRIBUTE = fromShortUuid((short) 0x1801);
    }

    /** UUIDs reserved for characteristics. */
    public static class Characteristics {
        /**
         * The value of this characteristic is a UTF-8 string representing the firmware revision for
         * the firmware within the device.
         * <p>
         * See reserved UUID org.bluetooth.characteristic.firmware_revision_string.
         */
        public static final UUID FIRMWARE_REVISION_STRING = fromShortUuid((short) 0x2A26);

        /**
         * Service change characteristic.
         * <p>
         * See reserved UUID org.bluetooth.characteristic.gatt.service_changed.
         */
        public static final UUID SERVICE_CHANGE = fromShortUuid((short) 0x2A05);
    }

    /** UUIDs reserved for descriptors. */
    public static class Descriptors {
        /**
         * This descriptor shall be persistent across connections for bonded devices. The Client
         * Characteristic Configuration descriptor is unique for each client. A client may read and
         * write this descriptor to determine and set the configuration for that client.
         * Authentication and authorization may be required by the server to write this descriptor.
         * The default value for the Client Characteristic Configuration descriptor is 0x00. Upon
         * connection of non-binded clients, this descriptor is set to the default value.
         * <p>
         * See reserved UUID org.bluetooth.descriptor.gatt.client_characteristic_configuration.
         */
        public static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION =
                fromShortUuid((short) 0x2902);
    }

    /** The base 128-bit UUID representation of a 16-bit UUID */
    public static final UUID BASE_16_BIT_UUID =
            UUID.fromString("00000000-0000-1000-8000-00805F9B34FB");

    /** Converts from short UUId to UUID. */
    public static UUID fromShortUuid(short shortUuid) {
        return new UUID(((((long) shortUuid) << 32) & 0x0000FFFF00000000L)
                | ReservedUuids.BASE_16_BIT_UUID.getMostSignificantBits(),
                ReservedUuids.BASE_16_BIT_UUID.getLeastSignificantBits());
    }
}
