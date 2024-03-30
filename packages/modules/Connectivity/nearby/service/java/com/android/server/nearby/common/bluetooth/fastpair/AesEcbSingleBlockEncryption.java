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

import android.annotation.SuppressLint;

import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utilities used for encrypting and decrypting Fast Pair packets.
 */
// SuppressLint for ""ecb encryption mode should not be used".
// Reasons:
//    1. FastPair data is guaranteed to be only 1 AES block in size, ECB is secure.
//    2. In each case, the encrypted data is less than 16-bytes and is
//       padded up to 16-bytes using random data to fill the rest of the byte array,
//       so the plaintext will never be the same.
@SuppressLint("GetInstance")
public final class AesEcbSingleBlockEncryption {

    public static final int AES_BLOCK_LENGTH = 16;
    public static final int KEY_LENGTH = 16;

    private AesEcbSingleBlockEncryption() {
    }

    /**
     * Generates a 16-byte AES key.
     */
    public static byte[] generateKey() throws NoSuchAlgorithmException {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(KEY_LENGTH * 8); // Ensure a 16-byte key is always used.
        return generator.generateKey().getEncoded();
    }

    /**
     * Encrypts data with the provided secret.
     */
    public static byte[] encrypt(byte[] secret, byte[] data) throws GeneralSecurityException {
        return doEncryption(Cipher.ENCRYPT_MODE, secret, data);
    }

    /**
     * Decrypts data with the provided secret.
     */
    public static byte[] decrypt(byte[] secret, byte[] data) throws GeneralSecurityException {
        return doEncryption(Cipher.DECRYPT_MODE, secret, data);
    }

    private static byte[] doEncryption(int mode, byte[] secret, byte[] data)
            throws GeneralSecurityException {
        if (data.length != AES_BLOCK_LENGTH) {
            throw new IllegalArgumentException("This encrypter only supports 16-byte inputs.");
        }
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(mode, new SecretKeySpec(secret, "AES"));
        return cipher.doFinal(data);
    }
}
