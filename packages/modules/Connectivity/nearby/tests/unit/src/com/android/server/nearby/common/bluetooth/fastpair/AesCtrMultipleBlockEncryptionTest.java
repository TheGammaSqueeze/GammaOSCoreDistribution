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

import static com.android.server.nearby.common.bluetooth.fastpair.AesCtrMultipleBlockEncryption.NONCE_SIZE;
import static com.android.server.nearby.common.bluetooth.fastpair.AesEcbSingleBlockEncryption.KEY_LENGTH;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/** Unit tests for {@link AesCtrMultpleBlockEncryption}. */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AesCtrMultipleBlockEncryptionTest {

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void decryptEncryptedData_nonBlockSizeAligned_mustEqualToPlaintext() throws Exception {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        byte[] plaintext = "Someone's Google Headphone 2019".getBytes(UTF_8); // The length is 31.

        byte[] encrypted = AesCtrMultipleBlockEncryption.encrypt(secret, plaintext);
        byte[] decrypted = AesCtrMultipleBlockEncryption.decrypt(secret, encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void decryptEncryptedData_blockSizeAligned_mustEqualToPlaintext() throws Exception {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        byte[] plaintext =
                // The length is 32.
                base16().decode("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");

        byte[] encrypted = AesCtrMultipleBlockEncryption.encrypt(secret, plaintext);
        byte[] decrypted = AesCtrMultipleBlockEncryption.decrypt(secret, encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void generateNonceTwice_mustBeDifferent() {
        byte[] nonce1 = AesCtrMultipleBlockEncryption.generateNonce();
        byte[] nonce2 = AesCtrMultipleBlockEncryption.generateNonce();

        assertThat(nonce1).isNotEqualTo(nonce2);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void encryptedSamePlaintext_mustBeDifferentEncryptedResult() throws Exception {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        byte[] plaintext = "Someone's Google Headphone 2019".getBytes(UTF_8);

        byte[] encrypted1 = AesCtrMultipleBlockEncryption.encrypt(secret, plaintext);
        byte[] encrypted2 = AesCtrMultipleBlockEncryption.encrypt(secret, plaintext);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void encryptData_mustBeDifferentToUnencrypted() throws Exception {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        byte[] plaintext = "Someone's Google Headphone 2019".getBytes(UTF_8);

        byte[] encrypted = AesCtrMultipleBlockEncryption.encrypt(secret, plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputIncorrectKeySizeToEncrypt_mustThrowException() {
        byte[] secret = new byte[KEY_LENGTH + 1];
        byte[] plaintext = "Someone's Google Headphone 2019".getBytes(UTF_8);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> AesCtrMultipleBlockEncryption.encrypt(secret, plaintext));

        assertThat(exception)
                .hasMessageThat()
                .contains("Incorrect key length for encryption, only supports 16-byte AES Key.");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputIncorrectKeySizeToDecrypt_mustThrowException() {
        byte[] secret = new byte[KEY_LENGTH - 1];
        byte[] plaintext = "Someone's Google Headphone 2019".getBytes(UTF_8);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> AesCtrMultipleBlockEncryption.decrypt(secret, plaintext));

        assertThat(exception)
                .hasMessageThat()
                .contains("Incorrect key length for encryption, only supports 16-byte AES Key.");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputIncorrectDataSizeToDecrypt_mustThrowException()
            throws GeneralSecurityException {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        byte[] plaintext = "Someone's Google Headphone 2019".getBytes(UTF_8);

        byte[] encryptedData = Arrays.copyOfRange(
                AesCtrMultipleBlockEncryption.encrypt(secret, plaintext), /*from=*/ 0, NONCE_SIZE);

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> AesCtrMultipleBlockEncryption.decrypt(secret, encryptedData));

        assertThat(exception).hasMessageThat().contains("Incorrect data length");
    }

    // Add some random tests that for a certain amount of random plaintext of random length to prove
    // our encryption/decryption is correct. This is suggested by security team.
    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void decryptEncryptedRandomDataForCertainAmount_mustEqualToOriginalData()
            throws Exception {
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 1000; i++) {
            byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
            int dataLength = random.nextInt(64) + 1;
            byte[] data = new byte[dataLength];
            random.nextBytes(data);

            byte[] encrypted = AesCtrMultipleBlockEncryption.encrypt(secret, data);
            byte[] decrypted = AesCtrMultipleBlockEncryption.decrypt(secret, encrypted);

            assertThat(decrypted).isEqualTo(data);
        }
    }

    // Add some random tests that for a certain amount of random plaintext of random length to prove
    // our encryption is correct. This is suggested by security team.
    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void twoDistinctEncryptionOnSameRandomData_mustBeDifferentResult() throws Exception {
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 1000; i++) {
            byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
            int dataLength = random.nextInt(64) + 1;
            byte[] data = new byte[dataLength];
            random.nextBytes(data);

            byte[] encrypted1 = AesCtrMultipleBlockEncryption.encrypt(secret, data);
            byte[] encrypted2 = AesCtrMultipleBlockEncryption.encrypt(secret, data);

            assertThat(encrypted1).isNotEqualTo(encrypted2);
        }
    }

    // Adds this test example on spec. Also we can easily change the parameters(e.g. secret, data,
    // nonce) to clarify test results with partners.
    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputTestExampleToEncrypt_getCorrectResult() throws GeneralSecurityException {
        byte[] secret = base16().decode("0123456789ABCDEF0123456789ABCDEF");
        byte[] nonce = base16().decode("0001020304050607");

        // "Someone's Google Headphone".getBytes(UTF_8) is
        // base16().decode("536F6D656F6E65277320476F6F676C65204865616470686F6E65");
        byte[] encryptedData =
                AesCtrMultipleBlockEncryption.doAesCtr(
                        secret,
                        "Someone's Google Headphone".getBytes(UTF_8),
                        nonce);

        assertThat(encryptedData)
                .isEqualTo(base16().decode("EE4A2483738052E44E9B2A145E5DDFAA44B9E5536AF438E1E5C6"));
    }
}
