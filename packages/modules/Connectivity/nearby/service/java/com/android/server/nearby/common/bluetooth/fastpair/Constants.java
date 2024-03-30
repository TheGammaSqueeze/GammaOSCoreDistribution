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

import static android.bluetooth.BluetoothProfile.A2DP;
import static android.bluetooth.BluetoothProfile.HEADSET;

import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothUuids.to128BitUuid;
import static com.android.server.nearby.common.bluetooth.fastpair.BluetoothUuids.toFastPair128BitUuid;

import static com.google.common.primitives.Bytes.concat;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothHeadset;
import android.util.Log;

import androidx.annotation.IntDef;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattConnection;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Shorts;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Random;
import java.util.UUID;

/**
 * Fast Pair and Transport Discovery Service constants.
 *
 * <p>Unless otherwise specified, these numbers come from
 * {https://www.bluetooth.com/specifications/gatt}.
 */
public final class Constants {

    /** A2DP sink service uuid. */
    public static final short A2DP_SINK_SERVICE_UUID = 0x110B;

    /** Headset service uuid. */
    public static final short HEADSET_SERVICE_UUID = 0x1108;

    /** Hands free sink service uuid. */
    public static final short HANDS_FREE_SERVICE_UUID = 0x111E;

    /** Bluetooth address length. */
    public static final int BLUETOOTH_ADDRESS_LENGTH = 6;

    private static final String TAG = Constants.class.getSimpleName();

    /**
     * Defined by https://developers.google.com/nearby/fast-pair/spec.
     */
    public static final class FastPairService {

        /** Fast Pair service UUID. */
        public static final UUID ID = to128BitUuid((short) 0xFE2C);

        /**
         * Characteristic to write verification bytes to during the key handshake.
         */
        public static final class KeyBasedPairingCharacteristic {

            private static final short SHORT_UUID = 0x1234;

            /**
             * Gets the new 128-bit UUID of this characteristic.
             *
             * <p>Note: For GATT server only. GATT client should use {@link
             * KeyBasedPairingCharacteristic#getId(BluetoothGattConnection)}.
             */
            public static final UUID CUSTOM_128_BIT_UUID = toFastPair128BitUuid(SHORT_UUID);

            /**
             * Gets the {@link UUID} of this characteristic.
             *
             * <p>This method is designed for being backward compatible with old version of UUID
             * therefore needs the {@link BluetoothGattConnection} parameter to check the supported
             * status of the Fast Pair provider.
             */
            public static UUID getId(BluetoothGattConnection gattConnection) {
                return getSupportedUuid(gattConnection, SHORT_UUID);
            }

            /**
             * Constants related to the decrypted request written to this characteristic.
             */
            public static final class Request {

                /**
                 * The size of this message.
                 */
                public static final int SIZE = 16;

                /**
                 * The index of this message for indicating the type byte.
                 */
                public static final int TYPE_INDEX = 0;

                /**
                 * The index of this message for indicating the flags byte.
                 */
                public static final int FLAGS_INDEX = 1;

                /**
                 * The index of this message for indicating the verification data start from.
                 */
                public static final int VERIFICATION_DATA_INDEX = 2;

                /**
                 * The length of verification data, it is Provider’s current BLE address or public
                 * address.
                 */
                public static final int VERIFICATION_DATA_LENGTH = BLUETOOTH_ADDRESS_LENGTH;

                /**
                 * The index of this message for indicating the seeker's public address start from.
                 */
                public static final int SEEKER_PUBLIC_ADDRESS_INDEX = 8;

                /**
                 * The index of this message for indicating event group.
                 */
                public static final int EVENT_GROUP_INDEX = 8;

                /**
                 * The index of this message for indicating event code.
                 */
                public static final int EVENT_CODE_INDEX = 9;

                /**
                 * The index of this message for indicating the length of additional data of the
                 * event.
                 */
                public static final int EVENT_ADDITIONAL_DATA_LENGTH_INDEX = 10;

                /**
                 * The index of this message for indicating the event additional data start from.
                 */
                public static final int EVENT_ADDITIONAL_DATA_INDEX = 11;

                /**
                 * The index of this message for indicating the additional data type used in the
                 * following Additional Data characteristic.
                 */
                public static final int ADDITIONAL_DATA_TYPE_INDEX = 10;

                /**
                 * The type of this message for Key-based Pairing Request.
                 */
                public static final byte TYPE_KEY_BASED_PAIRING_REQUEST = 0x00;

                /**
                 * The bit indicating that the Fast Pair device should temporarily become
                 * discoverable.
                 */
                public static final byte REQUEST_DISCOVERABLE = (byte) (1 << 7);

                /**
                 * The bit indicating that the requester (Seeker) has included their public address
                 * in bytes [7,12] of the request, and the Provider should initiate bonding to that
                 * address.
                 */
                public static final byte PROVIDER_INITIATES_BONDING = (byte) (1 << 6);

                /**
                 * The bit indicating that Seeker requests Provider shall return the existing name.
                 */
                public static final byte REQUEST_DEVICE_NAME = (byte) (1 << 5);

                /**
                 * The bit to request retroactive pairing.
                 */
                public static final byte REQUEST_RETROACTIVE_PAIR = (byte) (1 << 4);

                /**
                 * The type of this message for action over BLE.
                 */
                public static final byte TYPE_ACTION_OVER_BLE = 0x10;

                private Request() {
                }
            }

            /**
             * Enumerates all flags of key-based pairing request.
             */
            @Retention(RetentionPolicy.SOURCE)
            @IntDef(
                    value = {
                            KeyBasedPairingRequestFlag.REQUEST_DISCOVERABLE,
                            KeyBasedPairingRequestFlag.PROVIDER_INITIATES_BONDING,
                            KeyBasedPairingRequestFlag.REQUEST_DEVICE_NAME,
                            KeyBasedPairingRequestFlag.REQUEST_RETROACTIVE_PAIR,
                    })
            public @interface KeyBasedPairingRequestFlag {
                /**
                 * The bit indicating that the Fast Pair device should temporarily become
                 * discoverable.
                 */
                int REQUEST_DISCOVERABLE = (byte) (1 << 7);
                /**
                 * The bit indicating that the requester (Seeker) has included their public address
                 * in bytes [7,12] of the request, and the Provider should initiate bonding to that
                 * address.
                 */
                int PROVIDER_INITIATES_BONDING = (byte) (1 << 6);
                /**
                 * The bit indicating that Seeker requests Provider shall return the existing name.
                 */
                int REQUEST_DEVICE_NAME = (byte) (1 << 5);
                /**
                 * The bit indicating that the Seeker request retroactive pairing.
                 */
                int REQUEST_RETROACTIVE_PAIR = (byte) (1 << 4);
            }

            /**
             * Enumerates all flags of action over BLE request, see Fast Pair spec for details.
             */
            @IntDef(
                    value = {
                            ActionOverBleFlag.DEVICE_ACTION,
                            ActionOverBleFlag.ADDITIONAL_DATA_CHARACTERISTIC,
                    })
            public @interface ActionOverBleFlag {
                /**
                 * The bit indicating that the handshaking is for Device Action.
                 */
                int DEVICE_ACTION = (byte) (1 << 7);
                /**
                 * The bit indicating that this handshake will be followed by Additional Data
                 * characteristic.
                 */
                int ADDITIONAL_DATA_CHARACTERISTIC = (byte) (1 << 6);
            }


            /**
             * Constants related to the decrypted response sent back in a notify.
             */
            public static final class Response {

                /**
                 * The type of this message = Key-based Pairing Response.
                 */
                public static final byte TYPE = 0x01;

                private Response() {
                }
            }

            private KeyBasedPairingCharacteristic() {
            }
        }

        /**
         * Characteristic used during Key-based Pairing, to exchange the encrypted passkey.
         */
        public static final class PasskeyCharacteristic {

            private static final short SHORT_UUID = 0x1235;

            /**
             * Gets the new 128-bit UUID of this characteristic.
             *
             * <p>Note: For GATT server only. GATT client should use {@link
             * PasskeyCharacteristic#getId(BluetoothGattConnection)}.
             */
            public static final UUID CUSTOM_128_BIT_UUID = toFastPair128BitUuid(SHORT_UUID);

            /**
             * Gets the {@link UUID} of this characteristic.
             *
             * <p>This method is designed for being backward compatible with old version of UUID
             * therefore
             * needs the {@link BluetoothGattConnection} parameter to check the supported status of
             * the Fast Pair provider.
             */
            public static UUID getId(BluetoothGattConnection gattConnection) {
                return getSupportedUuid(gattConnection, SHORT_UUID);
            }

            /**
             * The type of the Passkey Block message.
             */
            @IntDef(
                    value = {
                            Type.SEEKER,
                            Type.PROVIDER,
                    })
            public @interface Type {
                /**
                 * Seeker's Passkey.
                 */
                int SEEKER = (byte) 0x02;
                /**
                 * Provider's Passkey.
                 */
                int PROVIDER = (byte) 0x03;
            }

            /**
             * Constructs the encrypted value to write to the characteristic.
             */
            public static byte[] encrypt(@Type int type, byte[] secret, int passkey)
                    throws GeneralSecurityException {
                Preconditions.checkArgument(
                        0 < passkey && passkey < /*2^24=*/ 16777216,
                        "Passkey %s must be positive and fit in 3 bytes",
                        passkey);
                byte[] passkeyBytes =
                        new byte[]{(byte) (passkey >>> 16), (byte) (passkey >>> 8), (byte) passkey};
                byte[] salt =
                        new byte[AesEcbSingleBlockEncryption.AES_BLOCK_LENGTH - 1
                                - passkeyBytes.length];
                new Random().nextBytes(salt);
                return AesEcbSingleBlockEncryption.encrypt(
                        secret, concat(new byte[]{(byte) type}, passkeyBytes, salt));
            }

            /**
             * Extracts the passkey from the encrypted characteristic value.
             */
            public static int decrypt(@Type int type, byte[] secret,
                    byte[] passkeyCharacteristicValue)
                    throws GeneralSecurityException {
                byte[] decrypted = AesEcbSingleBlockEncryption
                        .decrypt(secret, passkeyCharacteristicValue);
                if (decrypted[0] != (byte) type) {
                    throw new GeneralSecurityException(
                            "Wrong Passkey Block type (expected " + type + ", got "
                                    + decrypted[0] + ")");
                }
                return ByteBuffer.allocate(4)
                        .put((byte) 0)
                        .put(decrypted, /*offset=*/ 1, /*length=*/ 3)
                        .getInt(0);
            }

            private PasskeyCharacteristic() {
            }
        }

        /**
         * Characteristic to write to during the key exchange.
         */
        public static final class AccountKeyCharacteristic {

            private static final short SHORT_UUID = 0x1236;

            /**
             * Gets the new 128-bit UUID of this characteristic.
             *
             * <p>Note: For GATT server only. GATT client should use {@link
             * AccountKeyCharacteristic#getId(BluetoothGattConnection)}.
             */
            public static final UUID CUSTOM_128_BIT_UUID = toFastPair128BitUuid(SHORT_UUID);

            /**
             * Gets the {@link UUID} of this characteristic.
             *
             * <p>This method is designed for being backward compatible with old version of UUID
             * therefore
             * needs the {@link BluetoothGattConnection} parameter to check the supported status of
             * the Fast Pair provider.
             */
            public static UUID getId(BluetoothGattConnection gattConnection) {
                return getSupportedUuid(gattConnection, SHORT_UUID);
            }

            /**
             * The type for this message, account key request.
             */
            public static final byte TYPE = 0x04;

            private AccountKeyCharacteristic() {
            }
        }

        /**
         * Characteristic to write to and notify on for handling personalized name, see {@link
         * NamingEncoder}.
         */
        public static final class NameCharacteristic {

            private static final short SHORT_UUID = 0x1237;

            /**
             * Gets the new 128-bit UUID of this characteristic.
             *
             * <p>Note: For GATT server only. GATT client should use {@link
             * NameCharacteristic#getId(BluetoothGattConnection)}.
             */
            public static final UUID CUSTOM_128_BIT_UUID = toFastPair128BitUuid(SHORT_UUID);

            /**
             * Gets the {@link UUID} of this characteristic.
             *
             * <p>This method is designed for being backward compatible with old version of UUID
             * therefore
             * needs the {@link BluetoothGattConnection} parameter to check the supported status of
             * the Fast Pair provider.
             */
            public static UUID getId(BluetoothGattConnection gattConnection) {
                return getSupportedUuid(gattConnection, SHORT_UUID);
            }

            private NameCharacteristic() {
            }
        }

        /**
         * Characteristic to write to and notify on for handling additional data, see
         * https://developers.google.com/nearby/fast-pair/early-access/spec#AdditionalData
         */
        public static final class AdditionalDataCharacteristic {

            private static final short SHORT_UUID = 0x1237;

            public static final int DATA_ID_INDEX = 0;
            public static final int DATA_LENGTH_INDEX = 1;
            public static final int DATA_START_INDEX = 2;

            /**
             * Gets the new 128-bit UUID of this characteristic.
             *
             * <p>Note: For GATT server only. GATT client should use {@link
             * AdditionalDataCharacteristic#getId(BluetoothGattConnection)}.
             */
            public static final UUID CUSTOM_128_BIT_UUID = toFastPair128BitUuid(SHORT_UUID);

            /**
             * Gets the {@link UUID} of this characteristic.
             *
             * <p>This method is designed for being backward compatible with old version of UUID
             * therefore
             * needs the {@link BluetoothGattConnection} parameter to check the supported status of
             * the Fast Pair provider.
             */
            public static UUID getId(BluetoothGattConnection gattConnection) {
                return getSupportedUuid(gattConnection, SHORT_UUID);
            }

            /**
             * Enumerates all types of additional data.
             */
            @Retention(RetentionPolicy.SOURCE)
            @IntDef(
                    value = {
                            AdditionalDataType.PERSONALIZED_NAME,
                            AdditionalDataType.UNKNOWN,
                    })
            public @interface AdditionalDataType {
                /**
                 * The value indicating that the type is for personalized name.
                 */
                int PERSONALIZED_NAME = (byte) 0x01;
                int UNKNOWN = (byte) 0x00; // and all others.
            }
        }

        /**
         * Characteristic to control the beaconing feature (FastPair+Eddystone).
         */
        public static final class BeaconActionsCharacteristic {

            private static final short SHORT_UUID = 0x1238;

            /**
             * Gets the new 128-bit UUID of this characteristic.
             *
             * <p>Note: For GATT server only. GATT client should use {@link
             * BeaconActionsCharacteristic#getId(BluetoothGattConnection)}.
             */
            public static final UUID CUSTOM_128_BIT_UUID = toFastPair128BitUuid(SHORT_UUID);

            /**
             * Gets the {@link UUID} of this characteristic.
             *
             * <p>This method is designed for being backward compatible with old version of UUID
             * therefore
             * needs the {@link BluetoothGattConnection} parameter to check the supported status of
             * the Fast Pair provider.
             */
            public static UUID getId(BluetoothGattConnection gattConnection) {
                return getSupportedUuid(gattConnection, SHORT_UUID);
            }

            /**
             * Enumerates all types of beacon actions.
             */
            /** Fast Pair Bond State. */
            @Retention(RetentionPolicy.SOURCE)
            @IntDef(
                    value = {
                            BeaconActionType.READ_BEACON_PARAMETERS,
                            BeaconActionType.READ_PROVISIONING_STATE,
                            BeaconActionType.SET_EPHEMERAL_IDENTITY_KEY,
                            BeaconActionType.CLEAR_EPHEMERAL_IDENTITY_KEY,
                            BeaconActionType.READ_EPHEMERAL_IDENTITY_KEY,
                            BeaconActionType.RING,
                            BeaconActionType.READ_RINGING_STATE,
                            BeaconActionType.UNKNOWN,
                    })
            public @interface BeaconActionType {
                int READ_BEACON_PARAMETERS = (byte) 0x00;
                int READ_PROVISIONING_STATE = (byte) 0x01;
                int SET_EPHEMERAL_IDENTITY_KEY = (byte) 0x02;
                int CLEAR_EPHEMERAL_IDENTITY_KEY = (byte) 0x03;
                int READ_EPHEMERAL_IDENTITY_KEY = (byte) 0x04;
                int RING = (byte) 0x05;
                int READ_RINGING_STATE = (byte) 0x06;
                int UNKNOWN = (byte) 0xFF; // and all others
            }

            /** Converts value to enum. */
            public static @BeaconActionType int valueOf(byte value) {
                switch(value) {
                    case BeaconActionType.READ_BEACON_PARAMETERS:
                    case BeaconActionType.READ_PROVISIONING_STATE:
                    case BeaconActionType.SET_EPHEMERAL_IDENTITY_KEY:
                    case BeaconActionType.CLEAR_EPHEMERAL_IDENTITY_KEY:
                    case BeaconActionType.READ_EPHEMERAL_IDENTITY_KEY:
                    case BeaconActionType.RING:
                    case BeaconActionType.READ_RINGING_STATE:
                    case BeaconActionType.UNKNOWN:
                        return value;
                    default:
                        return BeaconActionType.UNKNOWN;
                }
            }
        }


        /**
         * Characteristic to read for checking firmware version. 0X2A26 is assigned number from
         * bluetooth SIG website.
         */
        public static final class FirmwareVersionCharacteristic {

            /** UUID for firmware version. */
            public static final UUID ID = to128BitUuid((short) 0x2A26);

            private FirmwareVersionCharacteristic() {
            }
        }

        private FastPairService() {
        }
    }

    /**
     * Defined by the BR/EDR Handover Profile. Pre-release version here:
     * {https://jfarfel.users.x20web.corp.google.com/Bluetooth%20Handover%20d09.pdf}
     */
    public interface TransportDiscoveryService {

        UUID ID = to128BitUuid((short) 0x1824);

        byte BLUETOOTH_SIG_ORGANIZATION_ID = 0x01;
        byte SERVICE_UUIDS_16_BIT_LIST_TYPE = 0x01;
        byte SERVICE_UUIDS_32_BIT_LIST_TYPE = 0x02;
        byte SERVICE_UUIDS_128_BIT_LIST_TYPE = 0x03;

        /**
         * Writing to this allows you to activate the BR/EDR transport.
         */
        interface ControlPointCharacteristic {

            UUID ID = to128BitUuid((short) 0x2ABC);
            byte ACTIVATE_TRANSPORT_OP_CODE = 0x01;
        }

        /**
         * Info necessary to pair (mostly the Bluetooth Address).
         */
        interface BrHandoverDataCharacteristic {

            UUID ID = to128BitUuid((short) 0x2C01);

            /**
             * All bits are reserved for future use.
             */
            byte BR_EDR_FEATURES = 0x00;
        }

        /**
         * This characteristic exists only to wrap the descriptor.
         */
        interface BluetoothSigDataCharacteristic {

            UUID ID = to128BitUuid((short) 0x2C02);

            /**
             * The entire Transport Block data (e.g. supported Bluetooth services).
             */
            interface BrTransportBlockDataDescriptor {

                UUID ID = to128BitUuid((short) 0x2C03);
            }
        }
    }

    public static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR_UUID =
            to128BitUuid((short) 0x2902);

    /**
     * Wrapper for Bluetooth profile
     */
    public static class Profile {

        public final int type;
        public final String name;
        public final String connectionStateAction;

        private Profile(int type, String name, String connectionStateAction) {
            this.type = type;
            this.name = name;
            this.connectionStateAction = connectionStateAction;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * {@link BluetoothHeadset} is used for both Headset and HandsFree (HFP).
     */
    private static final Profile HEADSET_AND_HANDS_FREE_PROFILE =
            new Profile(
                    HEADSET, "HEADSET_AND_HANDS_FREE",
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);

    /** Fast Pair supported profiles. */
    public static final ImmutableMap<Short, Profile> PROFILES =
            ImmutableMap.<Short, Profile>builder()
                    .put(
                            Constants.A2DP_SINK_SERVICE_UUID,
                            new Profile(A2DP, "A2DP",
                                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED))
                    .put(Constants.HEADSET_SERVICE_UUID, HEADSET_AND_HANDS_FREE_PROFILE)
                    .put(Constants.HANDS_FREE_SERVICE_UUID, HEADSET_AND_HANDS_FREE_PROFILE)
                    .build();

    static short[] getSupportedProfiles() {
        return Shorts.toArray(PROFILES.keySet());
    }

    /**
     * Helper method of getting 128-bit UUID for Fast Pair custom GATT characteristics.
     *
     * <p>This method is designed for being backward compatible with old version of UUID therefore
     * needs the {@link BluetoothGattConnection} parameter to check the supported status of the Fast
     * Pair provider.
     *
     * <p>Note: For new custom GATT characteristics, don't need to use this helper and please just
     * call {@code toFastPair128BitUuid(shortUuid)} to get the UUID. Which also implies that callers
     * don't need to provide {@link BluetoothGattConnection} to get the UUID anymore.
     */
    private static UUID getSupportedUuid(BluetoothGattConnection gattConnection, short shortUuid) {
        // In worst case (new characteristic not found), this method's performance impact is about
        // 6ms
        // by using Pixel2 + JBL LIVE220. And the impact should be less and less along with more and
        // more devices adopt the new characteristics.
        try {
            // Checks the new UUID first.
            if (gattConnection
                    .getCharacteristic(FastPairService.ID, toFastPair128BitUuid(shortUuid))
                    != null) {
                Log.d(TAG, "Uses new KeyBasedPairingCharacteristic.ID");
                return toFastPair128BitUuid(shortUuid);
            }
        } catch (BluetoothException e) {
            Log.d(TAG, "Uses old KeyBasedPairingCharacteristic.ID");
        }
        // Returns the old UUID for default.
        return to128BitUuid(shortUuid);
    }

    private Constants() {
    }
}
