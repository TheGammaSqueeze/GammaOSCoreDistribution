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

import static com.android.server.nearby.common.bluetooth.fastpair.AesCtrMultipleBlockEncryption.KEY_LENGTH;

import androidx.annotation.VisibleForTesting;

import java.security.GeneralSecurityException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 utility used to generate key-SHA256 based message authentication code. This is
 * specific for Fast Pair GATT connection exchanging data to verify both the data integrity and the
 * authentication of a message. It is defined as:
 *
 * <ol>
 *   <li>SHA256(concat((key ^ opad),SHA256(concat((key ^ ipad), data)))), where
 *   <li>key is the given secret extended to 64 bytes by concat(secret, ZEROS).
 *   <li>opad is 64 bytes outer padding, consisting of repeated bytes valued 0x5c.
 *   <li>ipad is 64 bytes inner padding, consisting of repeated bytes valued 0x36.
 * </ol>
 *
 */
final class HmacSha256 {
    @VisibleForTesting static final int HMAC_SHA256_BLOCK_SIZE = 64;

    private HmacSha256() {}

    /**
     * Generates the HMAC for given parameters, this is specific for Fast Pair GATT connection
     * exchanging data which is encrypted using AES-CTR.
     *
     * @param secret 16 bytes shared secret.
     * @param data the data encrypted using AES-CTR and the given nonce.
     * @return HMAC-SHA256 result.
     */
    static byte[] build(byte[] secret, byte[] data) throws GeneralSecurityException {
        // Currently we only accept AES-128 key here, the second check is to secure we won't
        // modify KEY_LENGTH to > HMAC_SHA256_BLOCK_SIZE by mistake.
        if (secret.length != KEY_LENGTH) {
            throw new GeneralSecurityException("Incorrect key length, should be the AES-128 key.");
        }
        if (KEY_LENGTH > HMAC_SHA256_BLOCK_SIZE) {
            throw new GeneralSecurityException("KEY_LENGTH > HMAC_SHA256_BLOCK_SIZE!");
        }

        return buildWith64BytesKey(secret, data);
    }

    /**
     * Generates the HMAC for given parameters, this is specific for Fast Pair GATT connection
     * exchanging data which is encrypted using AES-CTR.
     *
     * @param secret 16 bytes shared secret.
     * @param data the data encrypted using AES-CTR and the given nonce.
     * @return HMAC-SHA256 result.
     */
    static byte[] buildWith64BytesKey(byte[] secret, byte[] data) throws GeneralSecurityException {
        if (secret.length > HMAC_SHA256_BLOCK_SIZE) {
            throw new GeneralSecurityException("KEY_LENGTH > HMAC_SHA256_BLOCK_SIZE!");
        }

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret, "HmacSHA256");
        mac.init(keySpec);

        return mac.doFinal(data);
    }

    /**
     * Constant-time HMAC comparison to prevent a possible timing attack, e.g. time the same MAC
     * with all different first byte for a given ciphertext, the right one will take longer as it
     * will fail on the second byte's verification.
     *
     * @param hmac1 HMAC want to be compared with.
     * @param hmac2 HMAC want to be compared with.
     * @return true if and ony if the give 2 HMACs are identical and non-null.
     */
    static boolean compareTwoHMACs(byte[] hmac1, byte[] hmac2) {
        if (hmac1 == null || hmac2 == null) {
            return false;
        }

        if (hmac1.length != hmac2.length) {
            return false;
        }
        // This is for constant-time comparison, don't optimize it.
        int res = 0;
        for (int i = 0; i < hmac1.length; i++) {
            res |= hmac1[i] ^ hmac2[i];
        }
        return res == 0;
    }
}
