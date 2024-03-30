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

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.sysprop.BluetoothProperties;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothHeadsetClientTest extends AndroidTestCase {
    private static final String TAG = BluetoothHeadsetClientTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    private BluetoothHeadsetClient mBluetoothHeadsetClient;
    private boolean mIsHeadsetClientSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;

        mIsHeadsetClientSupported = BluetoothProperties.isProfileHfpHfEnabled().orElse(false);
        if (!mIsHeadsetClientSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothHeadsetClient = null;

        mAdapter.getProfileProxy(getContext(), new BluetoothHeadsetClientServiceListener(),
                BluetoothProfile.HEADSET_CLIENT);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (!(mHasBluetooth && mIsHeadsetClientSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothHeadsetClient != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.HEADSET_CLIENT,
                    mBluetoothHeadsetClient);
            mBluetoothHeadsetClient = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
    }

    public void test_closeProfileProxy() {
        if (!(mHasBluetooth && mIsHeadsetClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadsetClient);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.HEADSET_CLIENT, mBluetoothHeadsetClient);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    public void test_getConnectedDevices() {
        if (!(mHasBluetooth && mIsHeadsetClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadsetClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothHeadsetClient.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    public void test_getDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsHeadsetClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadsetClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothHeadsetClient.getDevicesMatchingConnectionStates(new int[]{});
        assertTrue(connectedDevices.isEmpty());
    }

    public void test_getConnectionState() {
        if (!(mHasBluetooth && mIsHeadsetClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadsetClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothHeadsetClient.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothHeadsetClient.getConnectionState(testDevice));
    }

    public void test_getConnectionPolicy() {
        if (!(mHasBluetooth && mIsHeadsetClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadsetClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothHeadsetClient.getConnectionPolicy(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothHeadsetClient.getConnectionPolicy(testDevice));
    }

    public void test_setConnectionPolicy() {
        if (!(mHasBluetooth && mIsHeadsetClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadsetClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothHeadsetClient.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(mBluetoothHeadsetClient.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothHeadsetClient.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    public void test_getNetworkServiceState() {
        if (!(mHasBluetooth && mIsHeadsetClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadsetClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertNull(mBluetoothHeadsetClient.getNetworkServiceState(testDevice));
        assertNull(mBluetoothHeadsetClient.getNetworkServiceState(null));
    }

    public void test_createNetworkServiceStateFromParcel() {
        if (!(mHasBluetooth && mIsHeadsetClientSupported)) return;

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        Parcel p = Parcel.obtain();
        p.writeParcelable(testDevice, 0); // Device
        p.writeInt(0); // Service Available
        p.writeString(""); // Operator Name
        p.writeInt(0); // General Signal Strength
        p.writeInt(0); // Roaming
        p.setDataPosition(0);

        BluetoothHeadsetClient.NetworkServiceState state =
                BluetoothHeadsetClient.NetworkServiceState.CREATOR.createFromParcel(p);

        assertEquals(testDevice, state.getDevice());
        assertEquals(false, state.isServiceAvailable());
        assertEquals("", state.getNetworkOperatorName());
        assertEquals(0, state.getSignalStrength());
        assertEquals(false, state.isRoaming());
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

    private final class BluetoothHeadsetClientServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothHeadsetClient = (BluetoothHeadsetClient) proxy;
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
