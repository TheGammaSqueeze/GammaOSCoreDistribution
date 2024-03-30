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

import static com.google.common.primitives.Bytes.concat;

import androidx.annotation.VisibleForTesting;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-CTR utilities used for encrypting and decrypting Fast Pair packets that contain multiple
 * blocks. Encrypts input data by:
 *
 * <ol>
 *   <li>encryptedBlock[i] = clearBlock[i] ^ AES(counter), and
 *   <li>concat(encryptedBlock[0], encryptedBlock[1],...) to create the encrypted result, where
 *   <li>counter: the 16-byte input of AES. counter = iv + block_index.
 *   <li>iv: extend 8-byte nonce to 16 bytes with zero padding. i.e. concat(0x0000000000000000,
 *       nonce).
 *   <li>nonce: the cryptographically random 8 bytes, must never be reused with the same key.
 * </ol>
 */
final class AesCtrMultipleBlockEncryption {

    /** Length for AES-128 key. */
    static final int KEY_LENGTH = AesEcbSingleBlockEncryption.KEY_LENGTH;

    @VisibleForTesting
    static final int AES_BLOCK_LENGTH = AesEcbSingleBlockEncryption.AES_BLOCK_LENGTH;

    /** Length of the nonce, a byte array of cryptographically random bytes. */
    static final int NONCE_SIZE = 8;

    private static final int IV_SIZE = AES_BLOCK_LENGTH;
    private static final int MAX_NUMBER_OF_BLOCKS = 4;

    private AesCtrMultipleBlockEncryption() {}

    /** Generates a 16-byte AES key. */
    static byte[] generateKey() throws NoSuchAlgorithmException {
        return AesEcbSingleBlockEncryption.generateKey();
    }

    /**
     * Encrypts data using AES-CTR by the given secret.
     *
     * @param secret AES-128 key.
     * @param data the plaintext to be encrypted.
     * @return the encrypted data with the 8-byte nonce appended to the front.
     */
    static byte[] encrypt(byte[] secret, byte[] data) throws GeneralSecurityException {
        byte[] nonce = generateNonce();
        return concat(nonce, doAesCtr(secret, data, nonce));
    }

    /**
     * Decrypts data using AES-CTR by the given secret and nonce.
     *
     * @param secret AES-128 key.
     * @param data the first 8 bytes is the nonce, and the remaining is the encrypted data to be
     *     decrypted.
     * @return the decrypted data.
     */
    static byte[] decrypt(byte[] secret, byte[] data) throws GeneralSecurityException {
        if (data == null || data.length <= NONCE_SIZE) {
            throw new GeneralSecurityException(
                    "Incorrect data length "
                            + (data == null ? "NULL" : data.length)
                            + " to decrypt, the data should contain nonce.");
        }
        byte[] nonce = Arrays.copyOf(data, NONCE_SIZE);
        byte[] encryptedData = Arrays.copyOfRange(data, NONCE_SIZE, data.length);
        return doAesCtr(secret, encryptedData, nonce);
    }

    /**
     * Generates cryptographically random NONCE_SIZE bytes nonce. This nonce can be used only once.
     * Always call this function to generate a new nonce before a new encryption.
     */
    // Suppression for a warning for potentially insecure random numbers on Android 4.3 and older.
    // Fast Pair service is only for Android 6.0+ devices.
    static byte[] generateNonce() {
        SecureRandom random = new SecureRandom();
        byte[] nonce = new byte[NONCE_SIZE];
        random.nextBytes(nonce);

        return nonce;
    }

    // AES-CTR implementation.
    @VisibleForTesting
    static byte[] doAesCtr(byte[] secret, byte[] data, byte[] nonce)
            throws GeneralSecurityException {
        if (secret.length != KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Incorrect key length for encryption, only supports 16-byte AES Key.");
        }
        if (nonce.length != NONCE_SIZE) {
            throw new IllegalArgumentException(
                    "Incorrect nonce length for encryption, "
                            + "Fast Pair naming scheme only supports 8-byte nonce.");
        }

        // Keeps the following operations on this byte[], returns it as the final AES-CTR result.
        byte[] aesCtrResult = new byte[data.length];
        System.arraycopy(data, /*srcPos=*/ 0, aesCtrResult, /*destPos=*/ 0, data.length);

        // Initializes counter as IV.
        byte[] counter = createIv(nonce);
        // The length of the given data is permitted to non-align block size.
        int numberOfBlocks =
                (data.length / AES_BLOCK_LENGTH) + ((data.length % AES_BLOCK_LENGTH == 0) ? 0 : 1);

        if (numberOfBlocks > MAX_NUMBER_OF_BLOCKS) {
            throw new IllegalArgumentException(
                    "Incorrect data size, Fast Pair naming scheme only supports 4 blocks.");
        }

        for (int i = 0; i < numberOfBlocks; i++) {
            // Performs the operation: encryptedBlock[i] = clearBlock[i] ^ AES(counter).
            counter[0] = (byte) (i & 0xFF);
            byte[] aesOfCounter = doAesSingleBlock(secret, counter);
            int start = i * AES_BLOCK_LENGTH;
            // The size of the last block of data may not be 16 bytes. If not, still do xor to the
            // last byte of data.
            int end = Math.min(start + AES_BLOCK_LENGTH, data.length);
            for (int j = 0; start < end; j++, start++) {
                aesCtrResult[start] ^= aesOfCounter[j];
            }
        }
        return aesCtrResult;
    }

    private static byte[] doAesSingleBlock(byte[] secret, byte[] counter)
            throws GeneralSecurityException {
        return AesEcbSingleBlockEncryption.encrypt(secret, counter);
    }

    /** Extends 8-byte nonce to 16 bytes with zero padding to create IV. */
    private static byte[] createIv(byte[] nonce) {
        return concat(new byte[IV_SIZE - NONCE_SIZE], nonce);
    }
}
