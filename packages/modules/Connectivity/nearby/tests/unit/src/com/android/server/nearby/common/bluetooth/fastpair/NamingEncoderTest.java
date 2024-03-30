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
import static com.android.server.nearby.common.bluetooth.fastpair.AesCtrMultipleBlockEncryption.NONCE_SIZE;
import static com.android.server.nearby.common.bluetooth.fastpair.NamingEncoder.EXTRACT_HMAC_SIZE;
import static com.android.server.nearby.common.bluetooth.fastpair.NamingEncoder.MAX_LENGTH_OF_NAME;

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
 * Unit tests for {@link NamingEncoder}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class NamingEncoderTest {

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void decodeEncodedNamingPacket_mustGetSameName() throws GeneralSecurityException {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        String name = "Someone's Google Headphone";

        byte[] encodedNamingPacket = NamingEncoder.encodeNamingPacket(secret, name);

        assertThat(NamingEncoder.decodeNamingPacket(secret, encodedNamingPacket)).isEqualTo(name);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputIncorrectKeySizeToEncode_mustThrowException() {
        byte[] secret = new byte[KEY_LENGTH - 1];
        String data = "Someone's Google Headphone";

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> NamingEncoder.encodeNamingPacket(secret, data));

        assertThat(exception).hasMessageThat()
                .contains("Incorrect secret for encoding name packet");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputIncorrectKeySizeToDecode_mustThrowException() {
        byte[] secret = new byte[KEY_LENGTH - 1];
        byte[] data = new byte[50];

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> NamingEncoder.decodeNamingPacket(secret, data));

        assertThat(exception).hasMessageThat()
                .contains("Incorrect secret for decoding name packet");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputTooSmallPacketSize_mustThrowException() {
        byte[] secret = new byte[KEY_LENGTH];
        byte[] data = new byte[EXTRACT_HMAC_SIZE - 1];

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> NamingEncoder.decodeNamingPacket(secret, data));

        assertThat(exception).hasMessageThat().contains("Naming packet size is incorrect");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputTooLargePacketSize_mustThrowException() throws GeneralSecurityException {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        byte[] namingPacket = new byte[MAX_LENGTH_OF_NAME + EXTRACT_HMAC_SIZE + NONCE_SIZE + 1];

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> NamingEncoder.decodeNamingPacket(secret, namingPacket));

        assertThat(exception).hasMessageThat().contains("Naming packet size is incorrect");
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void inputIncorrectHmacToDecode_mustThrowException() throws GeneralSecurityException {
        byte[] secret = AesCtrMultipleBlockEncryption.generateKey();
        String name = "Someone's Google Headphone";

        byte[] encodedNamingPacket = NamingEncoder.encodeNamingPacket(secret, name);
        encodedNamingPacket[0] = (byte) ~encodedNamingPacket[0];

        GeneralSecurityException exception =
                assertThrows(
                        GeneralSecurityException.class,
                        () -> NamingEncoder.decodeNamingPacket(secret, encodedNamingPacket));

        assertThat(exception)
                .hasMessageThat()
                .contains("Verify HMAC failed, could be incorrect key or naming packet.");
    }

    // Adds this test example on spec. Also we can easily change the parameters(e.g. secret, naming
    // packet) to clarify test results with partners.
    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void decodeTestNamingPacket_mustGetSameName() throws GeneralSecurityException {
        byte[] secret = base16().decode("0123456789ABCDEF0123456789ABCDEF");
        byte[] namingPacket = base16().decode(
                "55EC5E6055AF6E920001020304050607EE4A2483738052E44E9B2A145E5DDFAA44B9E5536AF438"
                        + "E1E5C6");

        assertThat(NamingEncoder.decodeNamingPacket(secret, namingPacket))
                .isEqualTo("Someone's Google Headphone");
    }
}
