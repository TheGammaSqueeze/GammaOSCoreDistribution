/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.google.common.primitives.Bytes;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link AesEcbSingleBlockEncryption}. */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AesEcbSingleBlockEncryptionTest {

    private static final byte[] PLAINTEXT = base16().decode("F30F4E786C59A7BBF3873B5A49BA97EA");

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void encryptDecryptSuccessful() throws Exception {
        byte[] secret = AesEcbSingleBlockEncryption.generateKey();
        byte[] encrypted = AesEcbSingleBlockEncryption.encrypt(secret, PLAINTEXT);
        assertThat(encrypted).isNotEqualTo(PLAINTEXT);
        byte[] decrypted = AesEcbSingleBlockEncryption.decrypt(secret, encrypted);
        assertThat(decrypted).isEqualTo(PLAINTEXT);
    }

    @Test(expected = IllegalArgumentException.class)
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void encryptionSizeLimitationEnforced() throws Exception {
        byte[] secret = AesEcbSingleBlockEncryption.generateKey();
        byte[] largePacket = Bytes.concat(PLAINTEXT, PLAINTEXT);
        AesEcbSingleBlockEncryption.encrypt(secret, largePacket);
    }

    @Test(expected = IllegalArgumentException.class)
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void decryptionSizeLimitationEnforced() throws Exception {
        byte[] secret = AesEcbSingleBlockEncryption.generateKey();
        byte[] largePacket = Bytes.concat(PLAINTEXT, PLAINTEXT);
        AesEcbSingleBlockEncryption.decrypt(secret, largePacket);
    }
}
