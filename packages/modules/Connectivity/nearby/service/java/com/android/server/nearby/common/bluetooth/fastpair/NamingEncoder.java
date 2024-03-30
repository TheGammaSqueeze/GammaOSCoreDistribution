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

import static java.nio.charset.StandardCharsets.UTF_8;

import android.annotation.TargetApi;
import android.os.Build.VERSION_CODES;

import com.google.common.base.Utf8;

import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Naming utilities for encoding naming packet, decoding naming packet and verifying both the data
 * integrity and the authentication of a message by checking HMAC.
 *
 * <p>Naming packet is:
 *
 * <ol>
 *   <li>Naming_Packet[0 - 7]: the first 8-byte of HMAC.
 *   <li>Naming_Packet[8 - var]: the encrypted name (with 8-byte nonce appended to the front).
 * </ol>
 */
@TargetApi(VERSION_CODES.M)
public final class NamingEncoder {

    static final int EXTRACT_HMAC_SIZE = 8;
    static final int MAX_LENGTH_OF_NAME = 48;

    private NamingEncoder() {
    }

    /**
     * Encodes the name to naming packet by the given secret.
     *
     * @param secret AES-128 key for encryption.
     * @param name the given name to be encoded.
     * @return the encrypted data with the 8-byte extracted HMAC appended to the front.
     * @throws GeneralSecurityException if the given key or name is invalid for encoding.
     */
    public static byte[] encodeNamingPacket(byte[] secret, String name)
            throws GeneralSecurityException {
        if (secret == null || secret.length != AesCtrMultipleBlockEncryption.KEY_LENGTH) {
            throw new GeneralSecurityException(
                    "Incorrect secret for encoding name packet, secret.length = "
                            + (secret == null ? "NULL" : secret.length));
        }

        if ((name == null) || (name.length() == 0) || (Utf8.encodedLength(name)
                > MAX_LENGTH_OF_NAME)) {
            throw new GeneralSecurityException(
                    "Invalid name for encoding name packet, Utf8.encodedLength(name) = "
                            + (name == null ? "NULL" : Utf8.encodedLength(name)));
        }

        byte[] encryptedData = AesCtrMultipleBlockEncryption.encrypt(secret, name.getBytes(UTF_8));
        byte[] extractedHmac =
                Arrays.copyOf(HmacSha256.build(secret, encryptedData), EXTRACT_HMAC_SIZE);

        return concat(extractedHmac, encryptedData);
    }

    /**
     * Decodes the name from naming packet by the given secret.
     *
     * @param secret AES-128 key used in the encryption to decrypt data.
     * @param namingPacket naming packet which is encoded by the given secret..
     * @return the name decoded from the given packet.
     * @throws GeneralSecurityException if the given key or naming packet is invalid for decoding.
     */
    public static String decodeNamingPacket(byte[] secret, byte[] namingPacket)
            throws GeneralSecurityException {
        if (secret == null || secret.length != AesCtrMultipleBlockEncryption.KEY_LENGTH) {
            throw new GeneralSecurityException(
                    "Incorrect secret for decoding name packet, secret.length = "
                            + (secret == null ? "NULL" : secret.length));
        }
        if (namingPacket == null
                || namingPacket.length <= EXTRACT_HMAC_SIZE
                || namingPacket.length > (MAX_LENGTH_OF_NAME + EXTRACT_HMAC_SIZE + NONCE_SIZE)) {
            throw new GeneralSecurityException(
                    "Naming packet size is incorrect, namingPacket.length is "
                            + (namingPacket == null ? "NULL" : namingPacket.length));
        }

        if (!verifyHmac(secret, namingPacket)) {
            throw new GeneralSecurityException(
                    "Verify HMAC failed, could be incorrect key or naming packet.");
        }
        byte[] encryptedData = Arrays
                .copyOfRange(namingPacket, EXTRACT_HMAC_SIZE, namingPacket.length);
        return new String(AesCtrMultipleBlockEncryption.decrypt(secret, encryptedData), UTF_8);
    }

    // Computes the HMAC of the given key and name, and compares the first 8-byte of the HMAC result
    // with the one from name packet. Must call constant-time comparison to prevent a possible
    // timing attack, e.g. time the same MAC with all different first byte for a given ciphertext,
    // the right one will take longer as it will fail on the second byte's verification.
    private static boolean verifyHmac(byte[] key, byte[] namingPacket)
            throws GeneralSecurityException {
        byte[] packetHmac = Arrays.copyOfRange(namingPacket, /* from= */ 0, EXTRACT_HMAC_SIZE);
        byte[] encryptedData = Arrays
                .copyOfRange(namingPacket, EXTRACT_HMAC_SIZE, namingPacket.length);
        byte[] computedHmac = Arrays
                .copyOf(HmacSha256.build(key, encryptedData), EXTRACT_HMAC_SIZE);

        return HmacSha256.compareTwoHMACs(packetHmac, computedHmac);
    }
}
