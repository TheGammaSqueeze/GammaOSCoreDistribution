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
import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothA2dpSinkTest extends AndroidTestCase {
    private static final String TAG = BluetoothA2dpSinkTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;;

    private BluetoothA2dpSink mBluetoothA2dpSink;
    private boolean mIsA2dpSinkSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) return;

        mIsA2dpSinkSupported = TestUtils.isProfileEnabled(BluetoothProfile.A2DP_SINK);
        if (!mIsA2dpSinkSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothA2dpSink = null;

        mAdapter.getProfileProxy(getContext(), new BluetoothA2dpSinkServiceListener(),
                BluetoothProfile.A2DP_SINK);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (!(mHasBluetooth && mIsA2dpSinkSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothA2dpSink != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.A2DP_SINK, mBluetoothA2dpSink);
            mBluetoothA2dpSink = null;
            mIsProfileReady = false;
        }
        mUiAutomation.dropShellPermissionIdentity();
        mAdapter = null;
    }

    public void test_closeProfileProxy() {
        if (!(mHasBluetooth && mIsA2dpSinkSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.A2DP_SINK, mBluetoothA2dpSink);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    public void test_getConnectedDevices() {
        if (!(mHasBluetooth && mIsA2dpSinkSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        assertEquals(mBluetoothA2dpSink.getConnectedDevices(), new ArrayList<BluetoothDevice>());

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothA2dpSink.getConnectedDevices());
    }

    public void test_getDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsA2dpSinkSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        assertEquals(mBluetoothA2dpSink.getDevicesMatchingConnectionStates(
                new int[]{BluetoothProfile.STATE_CONNECTED}),
                new ArrayList<BluetoothDevice>());
    }

    public void test_getConnectionState() {
        if (!(mHasBluetooth && mIsA2dpSinkSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(mBluetoothA2dpSink.getConnectionState(testDevice),
                BluetoothProfile.STATE_DISCONNECTED);

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class,
                () -> mBluetoothA2dpSink.getConnectionState(testDevice));
    }

    public void test_getConnectionPolicy() {
        if (!(mHasBluetooth && mIsA2dpSinkSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothA2dpSink.getConnectionPolicy(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothA2dpSink.getConnectionPolicy(testDevice));
    }

    public void test_setConnectionPolicy() {
        if (!(mHasBluetooth && mIsA2dpSinkSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothA2dpSink.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(mBluetoothA2dpSink.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothA2dpSink.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    public void test_isAudioPlaying() {
        if (!(mHasBluetooth && mIsA2dpSinkSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dpSink);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothA2dpSink.isAudioPlaying(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothA2dpSink.isAudioPlaying(testDevice));
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

    private final class BluetoothA2dpSinkServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothA2dpSink = (BluetoothA2dpSink) proxy;
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
