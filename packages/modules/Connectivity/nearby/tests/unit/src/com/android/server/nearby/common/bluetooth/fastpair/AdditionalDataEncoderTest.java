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

import static com.android.server.nearby.common.bluetooth.fastpair.AdditionalDataEncoder.MAX_LENGTH_OF_DATA;
import static com.android.server.nearby.common.bluetooth.fastpair.AesCtrMultipleBlockEncryption.KEY_LENGTH;
import static com.android.server.nearby.common.bluetooth.fastpair.AesCtrMultipleBlockEncryption.NONCE_SIZE;
import static com.android.server.nearby.common.bluetooth.fastpair.NamingEncoder.EXTRACT_HMAC_SIZE;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.GeneralSecurityException;

/**
 * Unit tests for {@link AdditionalDataEncoder}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class AdditionalDataEncoderTest {

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void decodeEncodedAdditionalDataPacket_mustGetSameRawData()
            throws GeneralSecurityException {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        byte[] rawData = base16().decode("00112233445566778899AABBCCDDEEFF");

        byte[] encodedAdditionalDataPacket =
                AdditionalDataEncoder.encodeAdditionalDataPacket(secret, rawData);
        byte[] additionalData =
                AdditionalDataEncoder
                        .decodeAdditionalDataPacket(secret, encodedAdditionalDataPacket);

        assertThat(additionalData).isEqualTo(rawData);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputIncorrectKeySizeToEncode_mustThrowException() {
        byte[] secret = new byte[KEY_LENGTH - 1];
        byte[] rawData = base16().decode("00112233445566778899AABBCCDDEEFF");

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> AdditionalDataEncoder.encodeAdditionalDataPacket(secret, rawData));

        assertThat(exception)
                .hasMessageThat()
                .contains("Incorrect secret for encoding additional data packet");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputIncorrectKeySizeToDecode_mustThrowException() {
        byte[] secret = new byte[KEY_LENGTH - 1];
        byte[] packet = base16().decode("01234567890123456789");

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> AdditionalDataEncoder.decodeAdditionalDataPacket(secret, packet));

        assertThat(exception)
                .hasMessageThat()
                .contains("Incorrect secret for decoding additional data packet");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputTooSmallPacketSize_mustThrowException() {
        byte[] secret = new byte[KEY_LENGTH];
        byte[] packet = new byte[EXTRACT_HMAC_SIZE - 1];

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> AdditionalDataEncoder.decodeAdditionalDataPacket(secret, packet));

        assertThat(exception).hasMessageThat().contains("Additional data packet size is incorrect");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputTooLargePacketSize_mustThrowException() throws GeneralSecurityException {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        byte[] packet = new byte[MAX_LENGTH_OF_DATA + EXTRACT_HMAC_SIZE + NONCE_SIZE + 1];

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> AdditionalDataEncoder.decodeAdditionalDataPacket(secret, packet));

        assertThat(exception).hasMessageThat().contains("Additional data packet size is incorrect");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputIncorrectHmacToDecode_mustThrowException() throws GeneralSecurityException {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        byte[] rawData = base16().decode("00112233445566778899AABBCCDDEEFF");

        byte[] additionalDataPacket = AdditionalDataEncoder
                .encodeAdditionalDataPacket(secret, rawData);
        additionalDataPacket[0] = (byte) ~additionalDataPacket[0];

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> AdditionalDataEncoder
                                .decodeAdditionalDataPacket(secret, additionalDataPacket));

        assertThat(exception)
                .hasMessageThat()
                .contains("Verify HMAC failed, could be incorrect key or packet.");
    }
}
