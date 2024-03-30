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

import android.app.PendingIntent;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.sysprop.BluetoothProperties;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothMapClientTest extends AndroidTestCase {
    private static final String TAG = BluetoothMapClientTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private static final String ACTION_MESSAGE_SENT_SUCCESSFULLY =
            "android.bluetooth.cts.BluetoothMapClientTest.MESSAGE_SENT_SUCCESSFULLY";
    private static final String ACTION_MESSAGE_DELIVERED_SUCCESSFULLY =
            "android.bluetooth.cts.BluetoothMapClientTest"
            + ".MESSAGE_DELIVERED_SUCCESSFULLY";

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    private BluetoothMapClient mBluetoothMapClient;
    private boolean mIsMapClientSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_BLUETOOTH);
            if (!mHasBluetooth) return;

            mIsMapClientSupported = BluetoothProperties.isProfileMapClientEnabled().orElse(false);
            if (!mIsMapClientSupported) return;

            mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
            mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

            BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
            mAdapter = manager.getAdapter();
            assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

            mProfileConnectionlock = new ReentrantLock();
            mConditionProfileConnection = mProfileConnectionlock.newCondition();
            mIsProfileReady = false;
            mBluetoothMapClient = null;

            mAdapter.getProfileProxy(getContext(), new BluetoothMapClientServiceListener(),
                    BluetoothProfile.MAP_CLIENT);
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (!(mHasBluetooth && mIsMapClientSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothMapClient != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.MAP_CLIENT,
                    mBluetoothMapClient);
            mBluetoothMapClient = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
    }

    public void test_closeProfileProxy() {
        if (!(mHasBluetooth && mIsMapClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.MAP_CLIENT, mBluetoothMapClient);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    public void test_getConnectedDevices() {
        if (!(mHasBluetooth && mIsMapClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothMapClient.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    public void test_getConnectionPolicy() {
        if (!(mHasBluetooth && mIsMapClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothMapClient.getConnectionPolicy(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothMapClient.getConnectionPolicy(testDevice));
    }

    public void test_getConnectionState() {
        if (!(mHasBluetooth && mIsMapClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothMapClient.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothMapClient.getConnectionState(testDevice));
    }

    public void test_getDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsMapClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothMapClient.getDevicesMatchingConnectionStates(new int[] {});
        assertTrue(connectedDevices.isEmpty());
    }

    public void test_sendMessage() {
        if (!(mHasBluetooth && mIsMapClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        Collection<Uri> contacts = new HashSet<Uri>();
        String message = "";
        PendingIntent sentIntent = PendingIntent.getBroadcast(getContext(), 0,
                new Intent(ACTION_MESSAGE_SENT_SUCCESSFULLY),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent deliveredIntent = PendingIntent.getBroadcast(getContext(), 0,
                new Intent(ACTION_MESSAGE_DELIVERED_SUCCESSFULLY),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // Verify returns false when invalid input device is given
        assertFalse(mBluetoothMapClient.sendMessage(
                /* BluetoothDevice */ null, contacts, message, sentIntent, deliveredIntent));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothMapClient.sendMessage(
                testDevice, contacts, message, sentIntent, deliveredIntent));
    }

    public void test_setConnectionPolicy() {
        if (!(mHasBluetooth && mIsMapClientSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothMapClient.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(mBluetoothMapClient.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothMapClient.setConnectionPolicy(
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

    private final class BluetoothMapClientServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothMapClient = (BluetoothMapClient) proxy;
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
