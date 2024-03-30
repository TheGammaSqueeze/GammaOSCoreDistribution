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

import static com.android.server.nearby.common.bluetooth.fastpair.AesCtrMultipleBlockEncryption.NONCE_SIZE;

import static com.google.common.primitives.Bytes.concat;

import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Utilities for encoding/decoding the additional data packet and verifying both the data integrity
 * and the authentication.
 *
 * <p>Additional Data packet is:
 *
 * <ol>
 *   <li>AdditionalData_Packet[0 - 7]: the first 8-byte of HMAC.
 *   <li>AdditionalData_Packet[8 - var]: the encrypted message by AES-CTR, with 8-byte nonce
 *       appended to the front.
 * </ol>
 *
 * See https://developers.google.com/nearby/fast-pair/spec#AdditionalData.
 */
public final class AdditionalDataEncoder {

    static final int EXTRACT_HMAC_SIZE = 8;
    static final int MAX_LENGTH_OF_DATA = 64;

    /**
     * Encodes the given data to additional data packet by the given secret.
     */
    static byte[] encodeAdditionalDataPacket(byte[] secret, byte[] additionalData)
            throws GeneralSecurityException {
        if (secret == null || secret.length != AesCtrMultipleBlockEncryption.KEY_LENGTH) {
            throw new GeneralSecurityException(
                    "Incorrect secret for encoding additional data packet, secret.length = "
                            + (secret == null ? "NULL" : secret.length));
        }

        if ((additionalData == null)
                || (additionalData.length == 0)
                || (additionalData.length > MAX_LENGTH_OF_DATA)) {
            throw new GeneralSecurityException(
                    "Invalid data for encoding additional data packet, data = "
                            + (additionalData == null ? "NULL" : additionalData.length));
        }

        byte[] encryptedData = AesCtrMultipleBlockEncryption.encrypt(secret, additionalData);
        byte[] extractedHmac =
                Arrays.copyOf(HmacSha256.build(secret, encryptedData), EXTRACT_HMAC_SIZE);

        return concat(extractedHmac, encryptedData);
    }

    /**
     * Decodes additional data packet by the given secret.
     *
     * @param secret AES-128 key used in the encryption to decrypt data
     * @param additionalDataPacket additional data packet which is encoded by the given secret
     * @return the data byte array decoded from the given packet
     * @throws GeneralSecurityException if the given key or additional data packet is invalid for
     * decoding
     */
    static byte[] decodeAdditionalDataPacket(byte[] secret, byte[] additionalDataPacket)
            throws GeneralSecurityException {
        if (secret == null || secret.length != AesCtrMultipleBlockEncryption.KEY_LENGTH) {
            throw new GeneralSecurityException(
                    "Incorrect secret for decoding additional data packet, secret.length = "
                            + (secret == null ? "NULL" : secret.length));
        }
        if (additionalDataPacket == null
                || additionalDataPacket.length <= EXTRACT_HMAC_SIZE
                || additionalDataPacket.length
                > (MAX_LENGTH_OF_DATA + EXTRACT_HMAC_SIZE + NONCE_SIZE)) {
            throw new GeneralSecurityException(
                    "Additional data packet size is incorrect, additionalDataPacket.length is "
                            + (additionalDataPacket == null ? "NULL"
                            : additionalDataPacket.length));
        }

        if (!verifyHmac(secret, additionalDataPacket)) {
            throw new GeneralSecurityException(
                    "Verify HMAC failed, could be incorrect key or packet.");
        }
        byte[] encryptedData =
                Arrays.copyOfRange(
                        additionalDataPacket, EXTRACT_HMAC_SIZE, additionalDataPacket.length);
        return AesCtrMultipleBlockEncryption.decrypt(secret, encryptedData);
    }

    // Computes the HMAC of the given key and additional data, and compares the first 8-byte of the
    // HMAC result with the one from additional data packet.
    // Must call constant-time comparison to prevent a possible timing attack, e.g. time the same
    // MAC with all different first byte for a given ciphertext, the right one will take longer as
    // it will fail on the second byte's verification.
    private static boolean verifyHmac(byte[] key, byte[] additionalDataPacket)
            throws GeneralSecurityException {
        byte[] packetHmac =
                Arrays.copyOfRange(additionalDataPacket, /* from= */ 0, EXTRACT_HMAC_SIZE);
        byte[] encryptedData =
                Arrays.copyOfRange(
                        additionalDataPacket, EXTRACT_HMAC_SIZE, additionalDataPacket.length);
        byte[] computedHmac = Arrays.copyOf(
                HmacSha256.build(key, encryptedData), EXTRACT_HMAC_SIZE);

        return HmacSha256.compareTwoHMACs(packetHmac, computedHmac);
    }

    private AdditionalDataEncoder() {
    }
}
