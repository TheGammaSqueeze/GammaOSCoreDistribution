/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.nearby.fastpair.provider;

import static com.android.server.nearby.common.bluetooth.fastpair.AesEcbSingleBlockEncryption.decrypt;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.BLUETOOTH_ADDRESS_LENGTH;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.ActionOverBleFlag.ADDITIONAL_DATA_CHARACTERISTIC;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.ActionOverBleFlag.DEVICE_ACTION;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.KeyBasedPairingRequestFlag.PROVIDER_INITIATES_BONDING;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.KeyBasedPairingRequestFlag.REQUEST_DEVICE_NAME;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.KeyBasedPairingRequestFlag.REQUEST_DISCOVERABLE;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.KeyBasedPairingRequestFlag.REQUEST_RETROACTIVE_PAIR;
import static com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request.ADDITIONAL_DATA_TYPE_INDEX;

import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.AdditionalDataCharacteristic.AdditionalDataType;
import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.KeyBasedPairingCharacteristic.Request;

import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * A wrapper for Fast Pair Provider to access decoded handshake request from the Seeker.
 *
 * @see {go/fast-pair-early-spec-handshake}
 */
public class HandshakeRequest {

    /**
     * 16 bytes data: 1-byte for type, 1-byte for flags, 6-bytes for provider's BLE address, 8 bytes
     * optional data.
     *
     * @see {go/fast-pair-spec-handshake-message1}
     */
    private final byte[] mDecryptedMessage;

    /** Enumerates the handshake message types. */
    public enum Type {
        KEY_BASED_PAIRING_REQUEST(Request.TYPE_KEY_BASED_PAIRING_REQUEST),
        ACTION_OVER_BLE(Request.TYPE_ACTION_OVER_BLE),
        UNKNOWN((byte) 0xFF);

        private final byte mValue;

        Type(byte type) {
            mValue = type;
        }

        public byte getValue() {
            return mValue;
        }

        public static Type valueOf(byte value) {
            for (Type type : Type.values()) {
                if (type.getValue() == value) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    public HandshakeRequest(byte[] key, byte[] encryptedPairingRequest)
            throws GeneralSecurityException {
        mDecryptedMessage = decrypt(key, encryptedPairingRequest);
    }

    /**
     * Gets the type of this handshake request. Currently, we have 2 types: 0x00 for Key-based
     * Pairing Request and 0x10 for Action Request.
     */
    public Type getType() {
        return Type.valueOf(mDecryptedMessage[Request.TYPE_INDEX]);
    }

    /**
     * Gets verification data of this handshake request.
     * Currently, we use Provider's BLE address.
     */
    public byte[] getVerificationData() {
        return Arrays.copyOfRange(
                mDecryptedMessage,
                Request.VERIFICATION_DATA_INDEX,
                Request.VERIFICATION_DATA_INDEX + Request.VERIFICATION_DATA_LENGTH);
    }

    /** Gets Seeker's public address of the handshake request. */
    public byte[] getSeekerPublicAddress() {
        return Arrays.copyOfRange(
                mDecryptedMessage,
                Request.SEEKER_PUBLIC_ADDRESS_INDEX,
                Request.SEEKER_PUBLIC_ADDRESS_INDEX + BLUETOOTH_ADDRESS_LENGTH);
    }

    /** Checks whether the Seeker request discoverability from flags byte. */
    public boolean requestDiscoverable() {
        return (getFlags() & REQUEST_DISCOVERABLE) != 0;
    }

    /**
     * Checks whether the Seeker requests that the Provider shall initiate bonding from flags byte.
     */
    public boolean requestProviderInitialBonding() {
        return (getFlags() & PROVIDER_INITIATES_BONDING) != 0;
    }

    /** Checks whether the Seeker requests that the Provider shall notify the existing name. */
    public boolean requestDeviceName() {
        return (getFlags() & REQUEST_DEVICE_NAME) != 0;
    }

    /** Checks whether this is for retroactively writing account key. */
    public boolean requestRetroactivePair() {
        return (getFlags() & REQUEST_RETROACTIVE_PAIR) != 0;
    }

    /** Gets the flags of this handshake request. */
    private byte getFlags() {
        return mDecryptedMessage[Request.FLAGS_INDEX];
    }

    /** Checks whether the Seeker requests a device action. */
    public boolean requestDeviceAction() {
        return (getFlags() & DEVICE_ACTION) != 0;
    }

    /**
     * Checks whether the Seeker requests an action which will be followed by an additional data
     * .
     */
    public boolean requestFollowedByAdditionalData() {
        return (getFlags() & ADDITIONAL_DATA_CHARACTERISTIC) != 0;
    }

    /** Gets the {@link AdditionalDataType} of this handshake request. */
    @AdditionalDataType
    public int getAdditionalDataType() {
        if (!requestFollowedByAdditionalData()
                || mDecryptedMessage.length <= ADDITIONAL_DATA_TYPE_INDEX) {
            return AdditionalDataType.UNKNOWN;
        }
        return mDecryptedMessage[ADDITIONAL_DATA_TYPE_INDEX];
    }
}
