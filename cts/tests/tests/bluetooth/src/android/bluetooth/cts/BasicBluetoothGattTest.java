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

package android.bluetooth.cts;

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.List;

/**
 * Tests a small part of the {@link BluetoothGatt} methods without a real Bluetooth device.
 * Other tests that run with real bluetooth connections are located in CtsVerifier.
 */
public class BasicBluetoothGattTest extends AndroidTestCase {
    private static final String TAG = BasicBluetoothGattTest.class.getSimpleName();

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothGatt mBluetoothGatt;

    @Override
    public void setUp() {
        if (!TestUtils.isBleSupported(getContext())) {
            return;
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_CONNECT);

        mBluetoothAdapter = mContext.getSystemService(BluetoothManager.class).getAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            assertTrue(BTAdapterUtils.enableAdapter(mBluetoothAdapter, mContext));
        }
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mBluetoothGatt = mBluetoothDevice.connectGatt(
                mContext, /*autoConnect=*/ true, new BluetoothGattCallback() {});
        if (mBluetoothGatt == null) {
            try {
                Thread.sleep(500); // Bt is not binded yet. Wait and retry
            } catch (InterruptedException e) {
                Log.e(TAG, "delay connectGatt interrupted");
            }
            mBluetoothGatt = mBluetoothDevice.connectGatt(
                    mContext, /*autoConnect=*/ true, new BluetoothGattCallback() {});
        }
        assertNotNull(mBluetoothGatt);

    }

    @Override
    public void tearDown() {
        if (!TestUtils.isBleSupported(getContext())) {
            // mBluetoothAdapter == null.
            return;
        }
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .dropShellPermissionIdentity();
    }

    public void testGetServices() throws Exception {
        if (!TestUtils.isBleSupported(getContext())) {
            return;
        }

        // getServices() returns an empty list if service discovery has not yet been performed.
        List<BluetoothGattService> services = mBluetoothGatt.getServices();
        assertNotNull(services);
        assertTrue(services.isEmpty());
    }

    public void testConnect() throws Exception {
        if (!TestUtils.isBleSupported(getContext())) {
            return;
        }

        mBluetoothGatt.connect();
    }

    public void testSetPreferredPhy() throws Exception {
        if (!TestUtils.isBleSupported(getContext())) {
            return;
        }

        mBluetoothGatt.setPreferredPhy(BluetoothDevice.PHY_LE_1M, BluetoothDevice.PHY_LE_1M,
                BluetoothDevice.PHY_OPTION_NO_PREFERRED);
    }

    public void testGetConnectedDevices() {
        if (!TestUtils.isBleSupported(getContext())) {
            return;
        }

        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGatt.getConnectedDevices());
    }

    public void testGetConnectionState() {
        if (!TestUtils.isBleSupported(getContext())) {
            return;
        }

        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGatt.getConnectionState(null));
    }

    public void testGetDevicesMatchingConnectionStates() {
        if (!TestUtils.isBleSupported(getContext())) {
            return;
        }

        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGatt.getDevicesMatchingConnectionStates(null));
    }
}
