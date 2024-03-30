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

import static com.android.server.nearby.common.bluetooth.fastpair.AesCtrMultipleBlockEncryption.generateNonce;

import static com.google.common.primitives.Bytes.concat;

import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Message stream utilities for encoding raw packet with HMAC.
 *
 * <p>Encoded packet is:
 *
 * <ol>
 *   <li>Packet[0 - (data length - 1)]: the raw data.
 *   <li>Packet[data length - (data length + 7)]: the 8-byte message nonce.
 *   <li>Packet[(data length + 8) - (data length + 15)]: the 8-byte of HMAC.
 * </ol>
 */
public class MessageStreamHmacEncoder {
    public static final int EXTRACT_HMAC_SIZE = 8;
    public static final int SECTION_NONCE_LENGTH = 8;

    private MessageStreamHmacEncoder() {}

    /** Encodes Message Packet. */
    public static byte[] encodeMessagePacket(byte[] accountKey, byte[] sectionNonce, byte[] data)
            throws GeneralSecurityException {
        checkAccountKeyAndSectionNonce(accountKey, sectionNonce);

        if (data == null || data.length == 0) {
            throw new GeneralSecurityException("No input data for encodeMessagePacket");
        }

        byte[] messageNonce = generateNonce();
        byte[] extractedHmac =
                Arrays.copyOf(
                        HmacSha256.buildWith64BytesKey(
                                accountKey, concat(sectionNonce, messageNonce, data)),
                        EXTRACT_HMAC_SIZE);

        return concat(data, messageNonce, extractedHmac);
    }

    /** Verifies Hmac. */
    public static boolean verifyHmac(byte[] accountKey, byte[] sectionNonce, byte[] data)
            throws GeneralSecurityException {
        checkAccountKeyAndSectionNonce(accountKey, sectionNonce);
        if (data == null) {
            throw new GeneralSecurityException("data is null");
        }
        if (data.length <= EXTRACT_HMAC_SIZE + SECTION_NONCE_LENGTH) {
            throw new GeneralSecurityException("data.length too short");
        }

        byte[] hmac = Arrays.copyOfRange(data, data.length - EXTRACT_HMAC_SIZE, data.length);
        byte[] messageNonce =
                Arrays.copyOfRange(
                        data,
                        data.length - EXTRACT_HMAC_SIZE - SECTION_NONCE_LENGTH,
                        data.length - EXTRACT_HMAC_SIZE);
        byte[] rawData = Arrays.copyOf(
                data, data.length - EXTRACT_HMAC_SIZE - SECTION_NONCE_LENGTH);
        return Arrays.equals(
                Arrays.copyOf(
                        HmacSha256.buildWith64BytesKey(
                                accountKey, concat(sectionNonce, messageNonce, rawData)),
                        EXTRACT_HMAC_SIZE),
                hmac);
    }

    private static void checkAccountKeyAndSectionNonce(byte[] accountKey, byte[] sectionNonce)
            throws GeneralSecurityException {
        if (accountKey == null || accountKey.length == 0) {
            throw new GeneralSecurityException(
                    "Incorrect accountKey for encoding message packet, accountKey.length = "
                            + (accountKey == null ? "NULL" : accountKey.length));
        }

        if (sectionNonce == null || sectionNonce.length != SECTION_NONCE_LENGTH) {
            throw new GeneralSecurityException(
                    "Incorrect sectionNonce for encoding message packet, sectionNonce.length = "
                            + (sectionNonce == null ? "NULL" : sectionNonce.length));
        }
    }
}
