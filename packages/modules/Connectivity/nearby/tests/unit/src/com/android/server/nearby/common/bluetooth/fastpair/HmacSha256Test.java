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

import static com.android.server.nearby.common.bluetooth.fastpair.AesCtrMultipleBlockEncryption.KEY_LENGTH;
import static com.android.server.nearby.common.bluetooth.fastpair.HmacSha256.HMAC_SHA256_BLOCK_SIZE;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.primitives.Bytes.concat;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Random;

/**
 * Unit tests for {@link HmacSha256}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class HmacSha256Test {

    private static final int EXTRACT_HMAC_SIZE = 8;
    private static final byte OUTER_PADDING_BYTE = 0x5c;
    private static final byte INNER_PADDING_BYTE = 0x36;

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void compareResultWithOurImplementation_mustBeIdentical()
            throws GeneralSecurityException {
        Random random = new Random(0xFE2C);

        for (int i = 0; i < 1000; i++) {
            byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
            // Avoid too small data size that may cause false alarm.
            int dataLength = random.nextInt(64);
            byte[] data = new byte[dataLength];
            random.nextBytes(data);

            assertThat(HmacSha256.build(secret, data)).isEqualTo(doHmacSha256(secret, data));
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputIncorrectKeySizeToDecrypt_mustThrowException() {
        byte[] secret = new byte[KEY_LENGTH - 1];
        byte[] data = base16().decode("1234567890ABCDEF1234567890ABCDEF1234567890ABCD");

        GeneralSecurityException exception =
                assertThrows(GeneralSecurityException.class, () -> HmacSha256.build(secret, data));

        assertThat(exception)
                .hasMessageThat()
                .contains("Incorrect key length, should be the AES-128 key.");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputTwoIdenticalArrays_compareTwoHmacMustReturnTrue() {
        Random random = new Random(0x1237);
        byte[] array1 = new byte[EXTRACT_HMAC_SIZE];
        random.nextBytes(array1);
        byte[] array2 = Arrays.copyOf(array1, array1.length);

        assertThat(HmacSha256.compareTwoHMACs(array1, array2)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputTwoRandomArrays_compareTwoHmacMustReturnFalse() {
        Random random = new Random(0xff);
        byte[] array1 = new byte[EXTRACT_HMAC_SIZE];
        random.nextBytes(array1);
        byte[] array2 = new byte[EXTRACT_HMAC_SIZE];
        random.nextBytes(array2);

        assertThat(HmacSha256.compareTwoHMACs(array1, array2)).isFalse();
    }

    // HMAC-SHA256 may not be previously defined on Bluetooth platforms, so we explicitly create
    // the code on test case. This will allow us to easily detect where partner implementation might
    // have gone wrong or where our spec isn't clear enough.
    static byte[] doHmacSha256(byte[] key, byte[] data) {

        Preconditions.checkArgument(
                key != null && key.length == KEY_LENGTH && data != null,
                "Parameters can't be null.");

        // Performs SHA256(concat((key ^ opad),SHA256(concat((key ^ ipad), data)))), where
        // key is the given secret extended to 64 bytes by concat(secret, ZEROS).
        // opad is 64 bytes outer padding, consisting of repeated bytes valued 0x5c.
        // ipad is 64 bytes inner padding, consisting of repeated bytes valued 0x36.
        byte[] keyIpad = new byte[HMAC_SHA256_BLOCK_SIZE];
        byte[] keyOpad = new byte[HMAC_SHA256_BLOCK_SIZE];

        for (int i = 0; i < KEY_LENGTH; i++) {
            keyIpad[i] = (byte) (key[i] ^ INNER_PADDING_BYTE);
            keyOpad[i] = (byte) (key[i] ^ OUTER_PADDING_BYTE);
        }
        Arrays.fill(keyIpad, KEY_LENGTH, HMAC_SHA256_BLOCK_SIZE, INNER_PADDING_BYTE);
        Arrays.fill(keyOpad, KEY_LENGTH, HMAC_SHA256_BLOCK_SIZE, OUTER_PADDING_BYTE);

        byte[] innerSha256Result = Hashing.sha256().hashBytes(concat(keyIpad, data)).asBytes();
        return Hashing.sha256().hashBytes(concat(keyOpad, innerSha256Result)).asBytes();
    }

    // Adds this test example on spec. Also we can easily change the parameters(e.g. secret, data)
    // to clarify test results with partners.
    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputTestExampleToHmacSha256_getCorrectResult() {
        byte[] secret = base16().decode("0123456789ABCDEF0123456789ABCDEF");
        byte[] data =
                base16().decode(
                        "0001020304050607EE4A2483738052E44E9B2A145E5DDFAA44B9E5536AF438E1E5C6");

        byte[] hmacResult = doHmacSha256(secret, data);

        assertThat(hmacResult)
                .isEqualTo(base16().decode(
                        "55EC5E6055AF6E92618B7D8710D4413709AB5DA27CA26A66F52E5AD4E8209052"));
    }
}
