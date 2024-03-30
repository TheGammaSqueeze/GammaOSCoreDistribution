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

import static com.android.server.nearby.common.bluetooth.fastpair.MessageStreamHmacEncoder.EXTRACT_HMAC_SIZE;
import static com.android.server.nearby.common.bluetooth.fastpair.MessageStreamHmacEncoder.SECTION_NONCE_LENGTH;

import static com.google.common.primitives.Bytes.concat;
import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Unit tests for {@link MessageStreamHmacEncoder}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class MessageStreamHmacEncoderTest {

    private static final int ACCOUNT_KEY_LENGTH = 16;

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void encodeMessagePacket() throws GeneralSecurityException {
        int messageLength = 2;
        SecureRandom secureRandom = new SecureRandom();
        byte[] accountKey = new byte[ACCOUNT_KEY_LENGTH];
        secureRandom.nextBytes(accountKey);
        byte[] data = new byte[messageLength];
        secureRandom.nextBytes(data);
        byte[] sectionNonce = new byte[SECTION_NONCE_LENGTH];
        secureRandom.nextBytes(sectionNonce);

        byte[] result = MessageStreamHmacEncoder
                .encodeMessagePacket(accountKey, sectionNonce, data);

        assertThat(result).hasLength(messageLength + SECTION_NONCE_LENGTH + EXTRACT_HMAC_SIZE);
        // First bytes are raw message bytes.
        assertThat(Arrays.copyOf(result, messageLength)).isEqualTo(data);
        // Following by message nonce.
        byte[] messageNonce =
                Arrays.copyOfRange(result, messageLength, messageLength + SECTION_NONCE_LENGTH);
        byte[] extractedHmac =
                Arrays.copyOf(
                        HmacSha256.buildWith64BytesKey(accountKey,
                                concat(sectionNonce, messageNonce, data)),
                        EXTRACT_HMAC_SIZE);
        // Finally hash mac.
        assertThat(Arrays.copyOfRange(result, messageLength + SECTION_NONCE_LENGTH, result.length))
                .isEqualTo(extractedHmac);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void verifyHmac() throws GeneralSecurityException {
        int messageLength = 2;
        SecureRandom secureRandom = new SecureRandom();
        byte[] accountKey = new byte[ACCOUNT_KEY_LENGTH];
        secureRandom.nextBytes(accountKey);
        byte[] data = new byte[messageLength];
        secureRandom.nextBytes(data);
        byte[] sectionNonce = new byte[SECTION_NONCE_LENGTH];
        secureRandom.nextBytes(sectionNonce);
        byte[] result = MessageStreamHmacEncoder
                .encodeMessagePacket(accountKey, sectionNonce, data);

        assertThat(MessageStreamHmacEncoder.verifyHmac(accountKey, sectionNonce, result)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void verifyHmac_failedByAccountKey() throws GeneralSecurityException {
        int messageLength = 2;
        SecureRandom secureRandom = new SecureRandom();
        byte[] accountKey = new byte[ACCOUNT_KEY_LENGTH];
        secureRandom.nextBytes(accountKey);
        byte[] data = new byte[messageLength];
        secureRandom.nextBytes(data);
        byte[] sectionNonce = new byte[SECTION_NONCE_LENGTH];
        secureRandom.nextBytes(sectionNonce);
        byte[] result = MessageStreamHmacEncoder
                .encodeMessagePacket(accountKey, sectionNonce, data);
        secureRandom.nextBytes(accountKey);

        assertThat(MessageStreamHmacEncoder.verifyHmac(accountKey, sectionNonce, result)).isFalse();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void verifyHmac_failedBySectionNonce() throws GeneralSecurityException {
        int messageLength = 2;
        SecureRandom secureRandom = new SecureRandom();
        byte[] accountKey = new byte[ACCOUNT_KEY_LENGTH];
        secureRandom.nextBytes(accountKey);
        byte[] data = new byte[messageLength];
        secureRandom.nextBytes(data);
        byte[] sectionNonce = new byte[SECTION_NONCE_LENGTH];
        secureRandom.nextBytes(sectionNonce);
        byte[] result = MessageStreamHmacEncoder
                .encodeMessagePacket(accountKey, sectionNonce, data);
        secureRandom.nextBytes(sectionNonce);

        assertThat(MessageStreamHmacEncoder.verifyHmac(accountKey, sectionNonce, result)).isFalse();
    }
}
