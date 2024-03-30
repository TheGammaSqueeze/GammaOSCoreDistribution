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

import static com.android.server.nearby.common.bluetooth.fastpair.AesEcbSingleBlockEncryption.generateKey;

import com.android.server.nearby.common.bluetooth.fastpair.Constants.FastPairService.AccountKeyCharacteristic;

import java.security.NoSuchAlgorithmException;

/**
 * This is to generate account key with fast-pair style.
 */
public final class AccountKeyGenerator {

    // Generate a key where the first byte is always defined as the type, 0x04. This maintains 15
    // bytes of entropy in the key while also allowing providers to verify that they have received
    // a properly formatted key and decrypted it correctly, minimizing the risk of replay attacks.

    /**
     * Creates account key.
     */
    public static byte[] createAccountKey() throws NoSuchAlgorithmException {
        byte[] accountKey = generateKey();
        accountKey[0] = AccountKeyCharacteristic.TYPE;
        return accountKey;
    }

    private AccountKeyGenerator() {
    }
}
