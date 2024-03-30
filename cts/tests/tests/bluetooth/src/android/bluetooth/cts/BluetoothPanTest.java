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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.TETHER_PRIVILEGED;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothPan;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.sysprop.BluetoothProperties;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothPanTest extends AndroidTestCase {
    private static final String TAG = BluetoothPanTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    private BluetoothPan mBluetoothPan;
    private boolean mIsPanSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;

        mIsPanSupported = BluetoothProperties.isProfilePanPanuEnabled().orElse(false)
                || BluetoothProperties.isProfilePanNapEnabled().orElse(false);
        if (!mIsPanSupported) return;

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothPan = null;

        mAdapter.getProfileProxy(getContext(), new BluetoothPanServiceListener(),
                BluetoothProfile.PAN);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (!(mHasBluetooth && mIsPanSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothPan != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.PAN, mBluetoothPan);
            mBluetoothPan = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
        TestUtils.dropPermissionAsShellUid();
    }

    public void test_closeProfileProxy() {
        if (!(mHasBluetooth && mIsPanSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothPan);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.PAN, mBluetoothPan);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    public void test_getConnectedDevices() {
        if (!(mHasBluetooth && mIsPanSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothPan);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothPan.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    public void test_getDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsPanSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothPan);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothPan.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    public void test_getConnectionState() {
        if (!(mHasBluetooth && mIsPanSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothPan);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothPan.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothPan.getConnectionState(testDevice));
    }

    public void test_setConnectionPolicy() {
        if (!(mHasBluetooth && mIsPanSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothPan);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothPan.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(mBluetoothPan.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothPan.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    public void test_setAndCheckBluetoothTethering() {
        if (!(mHasBluetooth && mIsPanSupported)) return;

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED,
                TETHER_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothPan);

        assertFalse(mBluetoothPan.isTetheringOn());
        mBluetoothPan.setBluetoothTethering(true);
        assertTrue(mBluetoothPan.isTetheringOn());
        mBluetoothPan.setBluetoothTethering(false);
        assertFalse(mBluetoothPan.isTetheringOn());
    }

    private boolean waitForProfileConnect() {
        mProfileConnectionlock.lock();
        try {
            // Wait for the Adapter to be disabled
            while (!mIsProfileReady) {
                if (!mConditionProfileConnection.await(
                        PROXY_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for Profile Connect");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileConnect: interrrupted");
        } finally {
            mProfileConnectionlock.unlock();
        }
        return mIsProfileReady;
    }

    private boolean waitForProfileDisconnect() {
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mProfileConnectionlock.lock();
        try {
            while (mIsProfileReady) {
                if (!mConditionProfileConnection.await(
                        PROXY_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for Profile Disconnect");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileDisconnect: interrrupted");
        } finally {
            mProfileConnectionlock.unlock();
        }
        return !mIsProfileReady;
    }

    private final class BluetoothPanServiceListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothPan = (BluetoothPan) proxy;
            mIsProfileReady = true;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            mProfileConnectionlock.lock();
            mIsProfileReady = false;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }
    }
}
