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

package com.android.server.nearby.provider;

import static android.Manifest.permission.READ_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;
import static android.provider.DeviceConfig.NAMESPACE_TETHERING;

import static com.android.server.nearby.NearbyConfiguration.NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import android.app.UiAutomation;
import android.content.Context;
import android.nearby.BroadcastCallback;
import android.nearby.BroadcastRequest;
import android.nearby.IBroadcastListener;
import android.nearby.PresenceBroadcastRequest;
import android.nearby.PresenceCredential;
import android.nearby.PrivateCredential;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;

/**
 * Unit test for {@link BroadcastProviderManager}.
 */
public class BroadcastProviderManagerTest {
    private static final byte[] IDENTITY = new byte[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private static final int MEDIUM_TYPE_BLE = 0;
    private static final byte[] SALT = {2, 3};
    private static final byte TX_POWER = 4;
    private static final int PRESENCE_ACTION = 123;
    private static final byte[] SECRET_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY = new byte[]{12, 13, 14};
    private static final String DEVICE_NAME = "test_device";

    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    @Mock
    IBroadcastListener mBroadcastListener;
    @Mock
    BleBroadcastProvider mBleBroadcastProvider;
    private Context mContext;
    private BroadcastProviderManager mBroadcastProviderManager;
    private BroadcastRequest mBroadcastRequest;
    private UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();

    @Before
    public void setUp() {
        mUiAutomation.adoptShellPermissionIdentity(WRITE_DEVICE_CONFIG, READ_DEVICE_CONFIG);
        DeviceConfig.setProperty(NAMESPACE_TETHERING, NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY,
                "true", false);

        mContext = ApplicationProvider.getApplicationContext();
        mBroadcastProviderManager = new BroadcastProviderManager(MoreExecutors.directExecutor(),
                mBleBroadcastProvider);

        PrivateCredential privateCredential =
                new PrivateCredential.Builder(SECRET_ID, AUTHENTICITY_KEY, IDENTITY, DEVICE_NAME)
                        .setIdentityType(PresenceCredential.IDENTITY_TYPE_PRIVATE)
                        .build();
        mBroadcastRequest =
                new PresenceBroadcastRequest.Builder(Collections.singletonList(MEDIUM_TYPE_BLE),
                        SALT, privateCredential)
                        .setTxPower(TX_POWER)
                        .setVersion(BroadcastRequest.PRESENCE_VERSION_V0)
                        .addAction(PRESENCE_ACTION).build();
    }

    @Test
    public void testStartAdvertising() {
        mBroadcastProviderManager.startBroadcast(mBroadcastRequest, mBroadcastListener);
        verify(mBleBroadcastProvider).start(any(byte[].class), any(
                BleBroadcastProvider.BroadcastListener.class));
    }

    @Test
    public void testStartAdvertising_featureDisabled() throws Exception {
        DeviceConfig.setProperty(NAMESPACE_TETHERING, NEARBY_ENABLE_PRESENCE_BROADCAST_LEGACY,
                "false", false);
        mBroadcastProviderManager = new BroadcastProviderManager(MoreExecutors.directExecutor(),
                mBleBroadcastProvider);
        mBroadcastProviderManager.startBroadcast(mBroadcastRequest, mBroadcastListener);
        verify(mBroadcastListener).onStatusChanged(eq(BroadcastCallback.STATUS_FAILURE));
    }

    @Test
    public void testOnStatusChanged() throws Exception {
        mBroadcastProviderManager.startBroadcast(mBroadcastRequest, mBroadcastListener);
        mBroadcastProviderManager.onStatusChanged(BroadcastCallback.STATUS_OK);
        verify(mBroadcastListener).onStatusChanged(eq(BroadcastCallback.STATUS_OK));
    }
}
