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

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothVolumeControl;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothVolumeControlTest extends AndroidTestCase {
    private static final String TAG = BluetoothVolumeControlTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    private BluetoothVolumeControl mBluetoothVolumeControl;
    private boolean mIsVolumeControlSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;
    private boolean mVolumeOffsetChangedCallbackCalled;
    private TestCallback mTestCallback;
    private Executor mTestExecutor;
    private BluetoothDevice mTestDevice;
    private int mTestVolumeOffset;

    class TestCallback implements BluetoothVolumeControl.Callback {
        @Override
        public void onVolumeOffsetChanged(BluetoothDevice device, int volumeOffset) {
            mVolumeOffsetChangedCallbackCalled = true;
            assertTrue(device == mTestDevice);
            assertTrue(volumeOffset == mTestVolumeOffset);
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);

        if (!mHasBluetooth) return;

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothVolumeControl = null;

        boolean isLeAudioSupportedInConfig =
                TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO);
        boolean isVolumeControlEnabledInConfig =
                TestUtils.isProfileEnabled(BluetoothProfile.VOLUME_CONTROL);
        if (isLeAudioSupportedInConfig) {
            /* If Le Audio is supported then Volume Control shall be supported */
            assertTrue("Config must be true when profile is supported",
                    isVolumeControlEnabledInConfig);
        }

        if (isVolumeControlEnabledInConfig) {
            mIsVolumeControlSupported = mAdapter.getProfileProxy(getContext(),
                    new BluetoothVolumeControlServiceListener(),
                    BluetoothProfile.VOLUME_CONTROL);
            assertTrue("Service shall be supported ", mIsVolumeControlSupported);

            mTestCallback = new TestCallback();
            mTestExecutor = mContext.getMainExecutor();
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth) {
            if (mBluetoothVolumeControl != null) {
                mBluetoothVolumeControl.close();
                mBluetoothVolumeControl = null;
                mIsProfileReady = false;
                mTestDevice = null;
                mTestVolumeOffset = 0;
                mTestCallback = null;
                mTestExecutor = null;
            }
            mAdapter = null;
            TestUtils.dropPermissionAsShellUid();
        }
    }

    public void testCloseProfileProxy() {
        if (!(mHasBluetooth && mIsVolumeControlSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothVolumeControl);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.VOLUME_CONTROL, mBluetoothVolumeControl);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    public void testGetConnectedDevices() {
        if (!(mHasBluetooth && mIsVolumeControlSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothVolumeControl);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothVolumeControl.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    public void testGetDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsVolumeControlSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothVolumeControl);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothVolumeControl.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    public void testRegisterUnregisterCallback() {
        if (!(mHasBluetooth && mIsVolumeControlSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothVolumeControl);

        // Verify parameter
        assertThrows(NullPointerException.class, () ->
                mBluetoothVolumeControl.registerCallback(null, mTestCallback));
        assertThrows(NullPointerException.class, () ->
                mBluetoothVolumeControl.registerCallback(mTestExecutor, null));
        assertThrows(NullPointerException.class, () ->
                mBluetoothVolumeControl.unregisterCallback(null));

        // Test success register unregister
        try {
            mBluetoothVolumeControl.registerCallback(mTestExecutor, mTestCallback);
        } catch (Exception e) {
            fail("Exception caught from register(): " + e.toString());
        }

        try {
            mBluetoothVolumeControl.unregisterCallback(mTestCallback);
        } catch (Exception e) {
            fail("Exception caught from unregister(): " + e.toString());
        }

        TestUtils.dropPermissionAsShellUid();
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class,
                () -> mBluetoothVolumeControl.registerCallback(mTestExecutor, mTestCallback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    public void testSetVolumeOffset() {
        if (!(mHasBluetooth && mIsVolumeControlSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothVolumeControl);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        try {
            mBluetoothVolumeControl.setVolumeOffset(mTestDevice, 0);
        } catch (Exception e) {
            fail("Exception caught from connect(): " + e.toString());
        }
    }

    public void testIsVolumeOffsetAvailable() {
        if (!(mHasBluetooth && mIsVolumeControlSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothVolumeControl);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertTrue(!mBluetoothVolumeControl.isVolumeOffsetAvailable(mTestDevice));
    }

    public void testVolumeOffsetCallback() {
        if (!(mHasBluetooth && mIsVolumeControlSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothVolumeControl);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mVolumeOffsetChangedCallbackCalled = false;

        /* Note. This is just for api coverage until proper testing tools are set up */
        mTestVolumeOffset = 1;
        mTestCallback.onVolumeOffsetChanged(mTestDevice, mTestVolumeOffset);
        assertTrue(mVolumeOffsetChangedCallbackCalled);
    }

    public void testGetConnectionState() {
        if (!(mHasBluetooth && mIsVolumeControlSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothVolumeControl);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothVolumeControl.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothVolumeControl.getConnectionState(testDevice));
    }

    public void testGetConnectionPolicy() {
        if (!(mHasBluetooth && mIsVolumeControlSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothVolumeControl);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothVolumeControl.getConnectionPolicy(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothVolumeControl.getConnectionPolicy(testDevice));
    }

    public void testSetConnectionPolicy() {
        if (!(mHasBluetooth && mIsVolumeControlSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothVolumeControl);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothVolumeControl.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(mBluetoothVolumeControl.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothVolumeControl.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
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

    private final class BluetoothVolumeControlServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothVolumeControl = (BluetoothVolumeControl) proxy;
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
