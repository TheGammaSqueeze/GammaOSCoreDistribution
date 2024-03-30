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
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.os.Build;
import android.sysprop.BluetoothProperties;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothHidHostTest extends AndroidTestCase {
    private static final String TAG = BluetoothHidHostTest.class.getSimpleName();
    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500; // ms timeout for Proxy Connect

    private boolean mHasBluetooth;
    private boolean mIsHidHostSupported;
    private BluetoothAdapter mAdapter;
    private BluetoothHidHost mHidHost;
    private UiAutomation mUiAutomation;

    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Override
    public void setUp() throws Exception {
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) return;

        mHasBluetooth =
                getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;

        mIsHidHostSupported = BluetoothProperties.isProfileHidHostEnabled().orElse(false);
        if (!mIsHidHostSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mHidHost = null;

        mAdapter.getProfileProxy(
                getContext(), new BluetoothHidHostListener(), BluetoothProfile.HID_HOST);
    }

    @Override
    public void tearDown() throws Exception {
        if (!(mHasBluetooth && mIsHidHostSupported)) {
            return;
        }
        if (mAdapter != null && mHidHost != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.HID_HOST, mHidHost);
            mHidHost = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
    }

    public void test_closeProfileProxy() {
        if (!(mHasBluetooth && mIsHidHostSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.HID_HOST, mHidHost);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    public void test_getConnectedDevices() {
        if (!(mHasBluetooth && mIsHidHostSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mHidHost.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    public void test_getDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsHidHostSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mHidHost.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    public void test_getConnectionState() {
        if (!(mHasBluetooth && mIsHidHostSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mHidHost.getConnectionState(null);
                });
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns STATE_DISCONNECTED if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED, mHidHost.getConnectionState(testDevice));
    }

    public void test_getConnectionPolicy() {
        if (!(mHasBluetooth && mIsHidHostSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mHidHost.getConnectionPolicy(null);
                });
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mHidHost.getConnectionPolicy(testDevice));
    }

    public void test_setConnectionPolicy() {
        if (!(mHasBluetooth && mIsHidHostSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(
                mHidHost.setConnectionPolicy(
                        testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mHidHost.setConnectionPolicy(null, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
                });

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(
                mHidHost.setConnectionPolicy(
                        testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
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
                } // else spurious wake ups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileConnect: interrupted");
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

    private final class BluetoothHidHostListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            try {
                mHidHost = (BluetoothHidHost) proxy;
                mIsProfileReady = true;
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
