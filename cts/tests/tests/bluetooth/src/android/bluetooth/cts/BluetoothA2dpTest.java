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
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.bluetooth.BluetoothA2dp.DYNAMIC_BUFFER_SUPPORT_A2DP_SOFTWARE_ENCODING;
import static android.bluetooth.BluetoothA2dp.DYNAMIC_BUFFER_SUPPORT_NONE;

import android.app.UiAutomation;
import android.bluetooth.BluetoothA2dp;
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

public class BluetoothA2dpTest extends AndroidTestCase {
    private static final String TAG = BluetoothA2dpTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    private BluetoothA2dp mBluetoothA2dp;
    private boolean mIsA2dpSupported;
    private boolean mIsProfileReady;
    private ReentrantLock mProfileConnectionlock;
    private Condition mConditionProfileConnection;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) return;

        mIsA2dpSupported = TestUtils.isProfileEnabled(BluetoothProfile.A2DP);
        if (!mIsA2dpSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();

        mIsProfileReady = false;
        mBluetoothA2dp = null;

        mAdapter.getProfileProxy(getContext(), new BluetoothA2dpServiceListener(),
                BluetoothProfile.A2DP);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (!(mHasBluetooth && mIsA2dpSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothA2dp != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.A2DP, mBluetoothA2dp);
            mBluetoothA2dp = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
        mUiAutomation.dropShellPermissionIdentity();
    }

    public void test_closeProfileProxy() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.A2DP, mBluetoothA2dp);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    public void test_getConnectedDevices() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertEquals(mBluetoothA2dp.getConnectedDevices(),
                new ArrayList<BluetoothDevice>());
    }

    public void test_getDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertEquals(mBluetoothA2dp.getDevicesMatchingConnectionStates(
                new int[]{BluetoothProfile.STATE_CONNECTED}),
                new ArrayList<BluetoothDevice>());
    }

    public void test_getConnectionState() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(mBluetoothA2dp.getConnectionState(testDevice),
                BluetoothProfile.STATE_DISCONNECTED);
    }

    public void test_isA2dpPlaying() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothA2dp.isA2dpPlaying(testDevice));
    }

    public void test_getCodecStatus() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertNull(mBluetoothA2dp.getCodecStatus(testDevice));
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.getCodecStatus(null);
        });
    }

    public void test_setCodecConfigPreference() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.setCodecConfigPreference(null, null);
        });
    }

    public void test_setOptionalCodecsEnabled() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertThrows(IllegalArgumentException.class,
                () -> mBluetoothA2dp.setOptionalCodecsEnabled(null, 0));
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothA2dp
                .setOptionalCodecsEnabled(testDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN));
        assertThrows(SecurityException.class, () -> mBluetoothA2dp
                .setOptionalCodecsEnabled(testDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED));
        assertThrows(SecurityException.class, () -> mBluetoothA2dp
                .setOptionalCodecsEnabled(testDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED));
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    public void test_getConnectionPolicy() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothA2dp.getConnectionPolicy(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothA2dp.getConnectionPolicy(testDevice));
    }

    public void test_setConnectionPolicy() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothA2dp.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(mBluetoothA2dp.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothA2dp.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    public void test_getDynamicBufferSupport() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        int dynamicBufferSupport = mBluetoothA2dp.getDynamicBufferSupport();
        assertTrue(dynamicBufferSupport >= DYNAMIC_BUFFER_SUPPORT_NONE
                && dynamicBufferSupport <= DYNAMIC_BUFFER_SUPPORT_A2DP_SOFTWARE_ENCODING);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns DYNAMIC_BUFFER_SUPPORT_NONE if bluetooth is not enabled
        assertEquals(DYNAMIC_BUFFER_SUPPORT_NONE, mBluetoothA2dp.getDynamicBufferSupport());
    }

    public void test_getBufferConstraints() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertNotNull(mBluetoothA2dp.getBufferConstraints());
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        // Verify returns null if bluetooth is not enabled
        assertNull(mBluetoothA2dp.getBufferConstraints());
    }

    public void test_setBufferLengthMillis() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        int sourceCodecTypeAAC = 1;

        assertTrue(mBluetoothA2dp.setBufferLengthMillis(sourceCodecTypeAAC, 0));
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        // Verify returns null if bluetooth is not enabled
        assertFalse(mBluetoothA2dp.setBufferLengthMillis(sourceCodecTypeAAC, 0));
    }

    public void test_optionalCodecs() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(-1, mBluetoothA2dp.isOptionalCodecsEnabled(testDevice));
        assertEquals(-1, mBluetoothA2dp.isOptionalCodecsSupported(testDevice));

        mBluetoothA2dp.enableOptionalCodecs(testDevice);
        // Device is not in state machine so should not be enabled
        assertEquals(-1, mBluetoothA2dp.isOptionalCodecsEnabled(testDevice));

        mBluetoothA2dp.disableOptionalCodecs(testDevice);
        assertEquals(-1, mBluetoothA2dp.isOptionalCodecsEnabled(testDevice));

        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.isOptionalCodecsEnabled(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.isOptionalCodecsSupported(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.enableOptionalCodecs(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.disableOptionalCodecs(null);
        });
    }

    public void test_setAvrcpAbsoluteVolume() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        // Only check if no crash occurs
        try {
            mBluetoothA2dp.setAvrcpAbsoluteVolume(0);
        } catch (Exception e) {
            fail("setAvrcpAbsoluteVolume(0) should not fail. " + e.getMessage());
        }
    }

    private static <T extends Exception> void assertThrows(Class<T> clazz, Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            if (!clazz.isAssignableFrom(e.getClass())) {
                throw e;
            }
        }
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

    private final class BluetoothA2dpServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothA2dp = (BluetoothA2dp) proxy;
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
