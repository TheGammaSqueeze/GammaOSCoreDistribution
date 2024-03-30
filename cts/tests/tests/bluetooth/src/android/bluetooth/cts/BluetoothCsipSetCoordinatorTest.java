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
import android.bluetooth.BluetoothCsipSetCoordinator;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.test.AndroidTestCase;
import android.util.Log;

import com.android.compatibility.common.util.ApiLevelUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothCsipSetCoordinatorTest extends AndroidTestCase {
    private static final String TAG = BluetoothCsipSetCoordinatorTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    private BluetoothCsipSetCoordinator mBluetoothCsipSetCoordinator;
    private boolean mIsCsipSetCoordinatorSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;
    private boolean mGroupLockCallbackCalled;
    private TestCallback mTestCallback;
    private Executor mTestExecutor;
    private BluetoothDevice mTestDevice;
    private boolean mIsLocked;
    private int mTestOperationStatus;
    private int mTestGroupId;

    class TestCallback implements BluetoothCsipSetCoordinator.ClientLockCallback {
        @Override
        public void onGroupLockSet(int groupId, int opStatus, boolean isLocked) {
            mGroupLockCallbackCalled = true;
            assertTrue(groupId == mTestGroupId);
            assertTrue(opStatus == mTestOperationStatus);
            assertTrue(isLocked == mIsLocked);
        }
    };

    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
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
            mBluetoothCsipSetCoordinator = null;

            boolean isLeAudioSupportedInConfig =
                     TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO);
            boolean isCsipConfigEnabled =
                     TestUtils.isProfileEnabled(BluetoothProfile.CSIP_SET_COORDINATOR);
            if (isLeAudioSupportedInConfig) {
                /* If Le Audio is supported then CSIP shall be supported */
                assertTrue("Config must be true when profile is supported", isCsipConfigEnabled);
            }

            if (isCsipConfigEnabled) {
                mIsCsipSetCoordinatorSupported = mAdapter.getProfileProxy(getContext(),
                        new BluetoothCsipServiceListener(),
                        BluetoothProfile.CSIP_SET_COORDINATOR);
                assertTrue("Service shall be supported ", mIsCsipSetCoordinatorSupported);

                mTestCallback = new TestCallback();
                mTestExecutor = mContext.getMainExecutor();
            }
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth) {
            if (mBluetoothCsipSetCoordinator != null) {
                mBluetoothCsipSetCoordinator.close();
                mBluetoothCsipSetCoordinator = null;
                mIsProfileReady = false;
                mTestDevice = null;
                mIsLocked = false;
                mTestOperationStatus = 0;
                mTestCallback = null;
                mTestExecutor = null;
            }
            mAdapter = null;
            TestUtils.dropPermissionAsShellUid();
        }
    }

    public void testCloseProfileProxy() {
        if (!(mHasBluetooth && mIsCsipSetCoordinatorSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothCsipSetCoordinator);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(
                BluetoothProfile.CSIP_SET_COORDINATOR, mBluetoothCsipSetCoordinator);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    public void testGetConnectedDevices() {
        if (!(mHasBluetooth && mIsCsipSetCoordinatorSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothCsipSetCoordinator);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothCsipSetCoordinator.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    public void testGetDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsCsipSetCoordinatorSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothCsipSetCoordinator);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothCsipSetCoordinator.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    public void testGetGroupUuidMapByDevice() {
        if (!(mHasBluetooth && mIsCsipSetCoordinatorSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothCsipSetCoordinator);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        TestUtils.dropPermissionAsShellUid();
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class, () ->
                mBluetoothCsipSetCoordinator.getGroupUuidMapByDevice(mTestDevice));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        Map<Integer, ParcelUuid> result = mBluetoothCsipSetCoordinator
                .getGroupUuidMapByDevice(mTestDevice);
        assertTrue(result.isEmpty());
    }

    public void testLockUnlockGroup() {
        if (!(mHasBluetooth && mIsCsipSetCoordinatorSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothCsipSetCoordinator);

        mTestGroupId = 1;
         // Verify parameter
        assertThrows(NullPointerException.class, () ->
                mBluetoothCsipSetCoordinator.lockGroup(mTestGroupId, null, mTestCallback));
        assertThrows(NullPointerException.class, () ->
                mBluetoothCsipSetCoordinator.lockGroup(mTestGroupId, mTestExecutor, null));

        TestUtils.dropPermissionAsShellUid();
        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class,
                () -> mBluetoothCsipSetCoordinator.lockGroup(mTestGroupId, mTestExecutor,
                                                            mTestCallback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Lock group
        mIsLocked = false;
        mTestOperationStatus = BluetoothStatusCodes.ERROR_CSIP_INVALID_GROUP_ID;
        try {
            mBluetoothCsipSetCoordinator.lockGroup(mTestGroupId,
                    mTestExecutor, mTestCallback);
        } catch (Exception e) {
            fail("Exception caught from register(): " + e.toString());
        }

        long uuidLsb = 0x01;
        long uuidMsb = 0x01;
        UUID uuid = new UUID(uuidMsb, uuidLsb);
        try {
            mBluetoothCsipSetCoordinator.unlockGroup(uuid);
        } catch (Exception e) {
            fail("Exception caught from register(): " + e.toString());
        }
    }

    public void testTestLockCallback() {
        if (!(mHasBluetooth && mIsCsipSetCoordinatorSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothCsipSetCoordinator);

        /* Note. This is just for api coverage until proper testing tools are set up */
        mTestGroupId = 1;
        mTestOperationStatus = 1;
        mIsLocked = true;

        mTestCallback.onGroupLockSet(mTestGroupId, mTestOperationStatus, mIsLocked);
        assertTrue(mGroupLockCallbackCalled);
    }

    public void testGetAllGroupIds() {
        if (!(mHasBluetooth && mIsCsipSetCoordinatorSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothCsipSetCoordinator);

        TestUtils.dropPermissionAsShellUid();
        assertThrows(SecurityException.class, () ->
                mBluetoothCsipSetCoordinator.getAllGroupIds(BluetoothUuid.CAP));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        List<Integer> result = mBluetoothCsipSetCoordinator.getAllGroupIds(null);
        assertTrue(result.isEmpty());
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

    private final class BluetoothCsipServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothCsipSetCoordinator = (BluetoothCsipSetCoordinator) proxy;
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
