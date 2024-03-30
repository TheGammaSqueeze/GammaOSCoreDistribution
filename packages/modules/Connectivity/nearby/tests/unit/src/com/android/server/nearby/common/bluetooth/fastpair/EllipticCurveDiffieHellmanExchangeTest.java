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

import static com.google.common.io.BaseEncoding.base64;
import static com.google.common.primitives.Bytes.concat;
import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link EllipticCurveDiffieHellmanExchange}.
 */
@Presubmit
@SmallTest
@RunWith(AndroidJUnit4.class)
public class EllipticCurveDiffieHellmanExchangeTest {

    public static final byte[] ANTI_SPOOF_PUBLIC_KEY = base64().decode(
            "d2JTfvfdS6u7LmGfMOmco3C7ra3lW1k17AOly0LrBydDZURacfTYIMmo5K1ejfD9e8b6qHs"
                    + "DTNzselhifi10kQ==");
    public static final byte[] ANTI_SPOOF_PRIVATE_KEY =
            base64().decode("Rn9GbLRPQTFc2O7WFVGkydzcUS9Tuj7R9rLh6EpLtuU=");


    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void generateCommonKey() throws Exception {
        EllipticCurveDiffieHellmanExchange bob = EllipticCurveDiffieHellmanExchange.create();
        EllipticCurveDiffieHellmanExchange alice = EllipticCurveDiffieHellmanExchange.create();

        assertThat(bob.getPublicKey()).isNotEqualTo(alice.getPublicKey());
        assertThat(bob.getPrivateKey()).isNotEqualTo(alice.getPrivateKey());

        assertThat(bob.generateSecret(alice.getPublicKey()))
                .isEqualTo(alice.generateSecret(bob.getPublicKey()));
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void generateCommonKey_withExistingPrivateKey() throws Exception {
        EllipticCurveDiffieHellmanExchange bob = EllipticCurveDiffieHellmanExchange.create();
        EllipticCurveDiffieHellmanExchange alice =
                EllipticCurveDiffieHellmanExchange.create(ANTI_SPOOF_PRIVATE_KEY);

        assertThat(alice.generateSecret(bob.getPublicKey()))
                .isEqualTo(bob.generateSecret(ANTI_SPOOF_PUBLIC_KEY));
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void generateCommonKey_soundcoreAntiSpoofingKey_generatedTooShort() throws Exception {
        // This soundcore device has a public key that was generated which starts with 0x0. This was
        // stripped out in our database, but this test confirms that adding that byte back fixes the
        // issue and allows the generated secrets to match each other.
        byte[] soundCorePublicKey = concat(new byte[]{0}, base64().decode(
                "EYapuIsyw/nwHAdMxr12FCtAi4gY3EtuW06JuKDg4SA76IoIDVeol2vsGKy0Ea2Z00"
                        + "ArOTiBDsk0L+4Xo9AA"));
        byte[] soundCorePrivateKey = base64()
                .decode("lW5idsrfX7cBC8kO/kKn3w3GXirqt9KnJoqXUcOMhjM=");
        EllipticCurveDiffieHellmanExchange bob = EllipticCurveDiffieHellmanExchange.create();
        EllipticCurveDiffieHellmanExchange alice =
                EllipticCurveDiffieHellmanExchange.create(soundCorePrivateKey);

        assertThat(alice.generateSecret(bob.getPublicKey()))
                .isEqualTo(bob.generateSecret(soundCorePublicKey));
    }
}
