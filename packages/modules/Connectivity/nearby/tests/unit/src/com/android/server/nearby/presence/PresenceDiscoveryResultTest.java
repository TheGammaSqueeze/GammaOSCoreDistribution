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

package com.android.server.nearby.presence;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.PresenceCredential;
import android.nearby.PresenceDevice;
import android.nearby.PresenceScanFilter;
import android.nearby.PublicCredential;

import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

/**
 * Unit tests for {@link PresenceDiscoveryResult}.
 */
public class PresenceDiscoveryResultTest {
    private static final int PRESENCE_ACTION = 123;
    private static final int TX_POWER = -1;
    private static final int RSSI = -41;
    private static final byte[] SALT = new byte[]{12, 34};
    private static final byte[] SECRET_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY = new byte[]{12, 13, 14};
    private static final byte[] PUBLIC_KEY = new byte[]{1, 1, 2, 2};
    private static final byte[] ENCRYPTED_METADATA = new byte[]{1, 2, 3, 4, 5};
    private static final byte[] METADATA_ENCRYPTION_KEY_TAG = new byte[]{1, 1, 3, 4, 5};

    private PresenceDiscoveryResult.Builder mBuilder;
    private PublicCredential mCredential;

    @Before
    public void setUp() {
        mCredential =
                new PublicCredential.Builder(SECRET_ID, AUTHENTICITY_KEY, PUBLIC_KEY,
                        ENCRYPTED_METADATA, METADATA_ENCRYPTION_KEY_TAG)
                        .setIdentityType(PresenceCredential.IDENTITY_TYPE_PRIVATE)
                        .build();
        mBuilder = new PresenceDiscoveryResult.Builder()
                .setPublicCredential(mCredential)
                .setSalt(SALT)
                .setTxPower(TX_POWER)
                .setRssi(RSSI)
                .addPresenceAction(PRESENCE_ACTION);
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testToDevice() {
        PresenceDiscoveryResult discoveryResult = mBuilder.build();
        PresenceDevice presenceDevice = discoveryResult.toPresenceDevice();

        assertThat(presenceDevice.getRssi()).isEqualTo(RSSI);
        assertThat(Arrays.equals(presenceDevice.getSalt(), SALT)).isTrue();
        assertThat(Arrays.equals(presenceDevice.getSecretId(), SECRET_ID)).isTrue();
    }

    @Test
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testMatches() {
        PresenceScanFilter scanFilter = new PresenceScanFilter.Builder()
                .setMaxPathLoss(80)
                .addPresenceAction(PRESENCE_ACTION)
                .addCredential(mCredential)
                .build();

        PresenceDiscoveryResult discoveryResult = mBuilder.build();
        assertThat(discoveryResult.matches(scanFilter)).isTrue();
    }

}
