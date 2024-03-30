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

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.nearby.injector.ContextHubManagerAdapter;
import com.android.server.nearby.injector.Injector;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class BleDiscoveryProviderTest {

    private BluetoothAdapter mBluetoothAdapter;
    private BleDiscoveryProvider mBleDiscoveryProvider;
    @Mock
    private AbstractDiscoveryProvider.Listener mListener;

    @Before
    public void setup() {
        initMocks(this);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        Injector injector = new TestInjector();

        mBluetoothAdapter = context.getSystemService(BluetoothManager.class).getAdapter();
        mBleDiscoveryProvider = new BleDiscoveryProvider(context, injector);
    }

    @Test
    public void test_callback() throws InterruptedException {
        mBleDiscoveryProvider.getController().setListener(mListener);
        mBleDiscoveryProvider.onStart();
        mBleDiscoveryProvider.getScanCallback()
                .onScanResult(CALLBACK_TYPE_ALL_MATCHES, createScanResult());

        // Wait for callback to be invoked
        Thread.sleep(500);
        verify(mListener, times(1)).onNearbyDeviceDiscovered(any());
    }

    @Test
    public void test_stopScan() {
        mBleDiscoveryProvider.onStart();
        mBleDiscoveryProvider.onStop();
    }

    private class TestInjector implements Injector {
        @Override
        public BluetoothAdapter getBluetoothAdapter() {
            return mBluetoothAdapter;
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

    private ScanResult createScanResult() {
        BluetoothDevice bluetoothDevice = mBluetoothAdapter
                .getRemoteDevice("11:22:33:44:55:66");
        byte[] scanRecord = new byte[] {2, 1, 6, 6, 22, 44, -2, 113, -116, 23, 2, 10, -11, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        return new ScanResult(
                bluetoothDevice,
                /* eventType= */ 0,
                /* primaryPhy= */ 0,
                /* secondaryPhy= */ 0,
                /* advertisingSid= */ 0,
                -31,
                -50,
                /* periodicAdvertisingInterval= */ 0,
                parseScanRecord(scanRecord),
                1645579363003L);
    }

    private static ScanRecord parseScanRecord(byte[] bytes) {
        Class<?> scanRecordClass = ScanRecord.class;
        try {
            Method method = scanRecordClass
                    .getDeclaredMethod("parseFromBytes", byte[].class);
            return (ScanRecord) method.invoke(null, bytes);
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException e) {
            return null;
        }
    }
}
