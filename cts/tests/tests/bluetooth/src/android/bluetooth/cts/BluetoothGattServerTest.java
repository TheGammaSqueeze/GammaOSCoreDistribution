/*
 * Copyright 2022 The Android Open Source Project
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

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static org.junit.Assert.assertThrows;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.UUID;

public class BluetoothGattServerTest extends AndroidTestCase {

    private final UUID TEST_UUID = UUID.fromString("0000110a-0000-1000-8000-00805f9b34fb");
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothManager mBluetoothManager;
    private UiAutomation mUIAutomation;
    private boolean mHasBluetooth;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
        mUIAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUIAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        if (!mHasBluetooth) return;
        mBluetoothAdapter = getContext().getSystemService(BluetoothManager.class).getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mBluetoothAdapter, mContext));
        mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
        mBluetoothGattServer = mBluetoothManager.openGattServer(mContext,
                new BluetoothGattServerCallback() {
                });
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth) {
            mUIAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
            if (mBluetoothAdapter != null && mBluetoothGattServer != null) {
                mBluetoothGattServer.close();
                mBluetoothGattServer = null;
            }
            mBluetoothAdapter = null;
            mUIAutomation.dropShellPermissionIdentity();
        }
    }

    public void testGetConnectedDevices() {
        if (!mHasBluetooth) return;
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGattServer.getConnectedDevices());
    }

    public void testGetConnectionState() {
        if (!mHasBluetooth) return;
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGattServer.getConnectionState(null));
    }

    public void testGetDevicesMatchingConnectionStates() {
        if (!mHasBluetooth) return;
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothGattServer.getDevicesMatchingConnectionStates(null));
    }

    public void testGetService() {
        if (!mHasBluetooth) return;
        assertNull(mBluetoothGattServer.getService(TEST_UUID));
    }

    public void testGetServices() {
        if (!mHasBluetooth) return;
        assertEquals(mBluetoothGattServer.getServices(), new ArrayList<BluetoothGattService>());
    }

    public void testReadPhy() {
        if (!mHasBluetooth) return;
        BluetoothDevice testDevice = mBluetoothAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mUIAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothGattServer.readPhy(testDevice));
    }

    public void testSetPreferredPhy() {
        if (!mHasBluetooth) return;
        BluetoothDevice testDevice = mBluetoothAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mUIAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothGattServer.setPreferredPhy(testDevice,
                BluetoothDevice.PHY_LE_1M_MASK, BluetoothDevice.PHY_LE_1M_MASK,
                BluetoothDevice.PHY_OPTION_NO_PREFERRED));
    }
}
