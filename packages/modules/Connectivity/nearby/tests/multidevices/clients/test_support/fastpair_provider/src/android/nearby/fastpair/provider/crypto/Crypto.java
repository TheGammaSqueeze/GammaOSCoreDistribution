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

package android.nearby.fastpair.provider.crypto;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import android.annotation.SuppressLint;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** Cryptography utilities for ephemeral IDs. */
public final class Crypto {
    private static final int AES_BLOCK_SIZE = 16;
    private static final ImmutableSet<Integer> VALID_AES_KEY_SIZES = ImmutableSet.of(16, 24, 32);
    private static final String AES_ECB_NOPADDING_ENCRYPTION_ALGO = "AES/ECB/NoPadding";
    private static final String AES_ENCRYPTION_ALGO = "AES";

    /** Encrypts the provided data with the provided key using the AES/ECB/NoPadding algorithm. */
    public static ByteString aesEcbNoPaddingEncrypt(ByteString key, ByteString data) {
        return aesEcbOperation(key, data, Cipher.ENCRYPT_MODE);
    }

    /** Decrypts the provided data with the provided key using the AES/ECB/NoPadding algorithm. */
    public static ByteString aesEcbNoPaddingDecrypt(ByteString key, ByteString data) {
        return aesEcbOperation(key, data, Cipher.DECRYPT_MODE);
    }

    @SuppressLint("GetInstance")
    private static ByteString aesEcbOperation(ByteString key, ByteString data, int operation) {
        checkArgument(VALID_AES_KEY_SIZES.contains(key.size()));
        checkArgument(data.size() % AES_BLOCK_SIZE == 0);
        try {
            Cipher aesCipher = Cipher.getInstance(AES_ECB_NOPADDING_ENCRYPTION_ALGO);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.toByteArray(), AES_ENCRYPTION_ALGO);
            aesCipher.init(operation, secretKeySpec);
            ByteBuffer output = ByteBuffer.allocate(data.size());
            checkState(aesCipher.doFinal(data.asReadOnlyByteBuffer(), output) == data.size());
            output.rewind();
            return ByteString.copyFrom(output);
        } catch (GeneralSecurityException e) {
            // Should never happen.
            throw new AssertionError(e);
        }
    }

    private Crypto() {
    }
}
