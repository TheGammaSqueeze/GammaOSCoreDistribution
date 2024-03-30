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

import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.nearby.BroadcastCallback;

import androidx.test.core.app.ApplicationProvider;

import com.android.server.nearby.injector.ContextHubManagerAdapter;
import com.android.server.nearby.injector.Injector;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Unit test for {@link BleBroadcastProvider}.
 */
public class BleBroadcastProviderTest {
    @Rule
    public final MockitoRule mocks = MockitoJUnit.rule();

    @Mock
    private BleBroadcastProvider.BroadcastListener mBroadcastListener;
    private BleBroadcastProvider mBleBroadcastProvider;

    @Before
    public void setUp() {
        mBleBroadcastProvider = new BleBroadcastProvider(new TestInjector(),
                MoreExecutors.directExecutor());
    }

    @Test
    public void testOnStatus_success() {
        byte[] advertiseBytes = new byte[]{1, 2, 3, 4};
        mBleBroadcastProvider.start(advertiseBytes, mBroadcastListener);

        AdvertiseSettings settings = new AdvertiseSettings.Builder().build();
        mBleBroadcastProvider.onStartSuccess(settings);
        verify(mBroadcastListener).onStatusChanged(eq(BroadcastCallback.STATUS_OK));
    }

    @Test
    public void testOnStatus_failure() {
        byte[] advertiseBytes = new byte[]{1, 2, 3, 4};
        mBleBroadcastProvider.start(advertiseBytes, mBroadcastListener);

        mBleBroadcastProvider.onStartFailure(BroadcastCallback.STATUS_FAILURE);
        verify(mBroadcastListener, times(1))
                .onStatusChanged(eq(BroadcastCallback.STATUS_FAILURE));
    }

    private static class TestInjector implements Injector {

        @Override
        public BluetoothAdapter getBluetoothAdapter() {
            Context context = ApplicationProvider.getApplicationContext();
            BluetoothManager bluetoothManager = context.getSystemService(BluetoothManager.class);
            return bluetoothManager.getAdapter();
        }

        @Override
        public ContextHubManagerAdapter getContextHubManagerAdapter() {
            return null;
        }

        @Override
        public AppOpsManager getAppOpsManager() {
            return null;
        }
    }
}
