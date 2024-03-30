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
import static android.Manifest.permission.BLUETOOTH_SCAN;
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastAssistant;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastAssistantTest {
    private static final String TAG = BluetoothLeBroadcastAssistantTest.class.getSimpleName();

    private static final int START_SEARCH_TIMEOUT_MS = 100;
    private static final int ADD_SOURCE_TIMEOUT_MS = 100;
    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private static final String TEST_ADDRESS_1 = "EF:11:22:33:44:55";
    private static final String TEST_ADDRESS_2 = "EF:11:22:33:44:66";
    private static final int TEST_BROADCAST_ID = 42;
    private static final int TEST_ADVERTISER_SID = 1234;
    private static final int TEST_PA_SYNC_INTERVAL = 100;
    private static final int TEST_PRESENTATION_DELAY_MS = 345;

    private static final int TEST_CODEC_ID = 42;

    // For BluetoothLeAudioCodecConfigMetadata
    private static final long TEST_AUDIO_LOCATION_FRONT_LEFT = 0x01;

    // For BluetoothLeAudioContentMetadata
    private static final String TEST_PROGRAM_INFO = "Test";
    // German language code in ISO 639-3
    private static final String TEST_LANGUAGE = "deu";

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    Executor mExecutor;

    private BluetoothLeBroadcastAssistant mBluetoothLeBroadcastAssistant;
    private boolean mIsBroadcastAssistantSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Mock
    BluetoothLeBroadcastAssistant.Callback mCallbacks;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            return;
        }
        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) {
            return;
        }
        MockitoAnnotations.initMocks(this);
        mExecutor = mContext.getMainExecutor();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT,BLUETOOTH_PRIVILEGED,BLUETOOTH_SCAN);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothLeBroadcastAssistant = null;

        mIsBroadcastAssistantSupported =
                mAdapter.isLeAudioBroadcastAssistantSupported() == FEATURE_SUPPORTED;
        if (mIsBroadcastAssistantSupported) {
            boolean isBroadcastAssistantEnabledInConfig =
                    TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
            assertTrue("Config must be true when profile is supported",
                    isBroadcastAssistantEnabledInConfig);
        }

        mAdapter.getProfileProxy(mContext, new ServiceListener(),
                BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT);
    }

    @After
    public void tearDown() {
        if (mHasBluetooth) {
            if (mAdapter != null && mBluetoothLeBroadcastAssistant != null) {
                mAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT,
                        mBluetoothLeBroadcastAssistant);
                mBluetoothLeBroadcastAssistant = null;
                mIsProfileReady = false;
            }
            mAdapter = null;
            TestUtils.dropPermissionAsShellUid();
        }
    }

    @Test
    public void testCloseProfileProxy() {
        if (shouldSkipTest()) {
            return;
        }

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(
                BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT, mBluetoothLeBroadcastAssistant);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @Test
    public void testAddSource() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        BluetoothDevice testDevice = mAdapter.getRemoteLeDevice(TEST_ADDRESS_1,
                BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothDevice testSourceDevice = mAdapter.getRemoteLeDevice(TEST_ADDRESS_1,
                BluetoothDevice.ADDRESS_TYPE_RANDOM);

        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                .setEncrypted(false)
                .setSourceDevice(testSourceDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                .setBroadcastId(TEST_BROADCAST_ID)
                .setBroadcastCode(null)
                .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS);

        BluetoothLeBroadcastSubgroup[] subgroups = new BluetoothLeBroadcastSubgroup[] {
                createBroadcastSubgroup()
        };
        for (BluetoothLeBroadcastSubgroup subgroup : subgroups) {
            builder.addSubgroup(subgroup);
        }
        BluetoothLeBroadcastMetadata metadata = builder.build();

        // Verifies that it throws exception when no callback is registered
        assertThrows(IllegalStateException.class, () -> mBluetoothLeBroadcastAssistant
                .addSource(testDevice, metadata, true));

        mBluetoothLeBroadcastAssistant.registerCallback(mExecutor, mCallbacks);

        // Verify that exceptions is thrown when sink or source is null
        assertThrows(NullPointerException.class, () -> mBluetoothLeBroadcastAssistant
                .addSource(testDevice, null, true));
        assertThrows(NullPointerException.class, () -> mBluetoothLeBroadcastAssistant
                .addSource(null, metadata, true));
        assertThrows(NullPointerException.class, () -> mBluetoothLeBroadcastAssistant
                .addSource(null, null, true));

        // Verify that adding source to unknown test device will fail
        mBluetoothLeBroadcastAssistant.addSource(testDevice, metadata, true);
        verify(mCallbacks, timeout(ADD_SOURCE_TIMEOUT_MS)).onSourceAddFailed(testDevice, metadata,
                BluetoothStatusCodes.ERROR_REMOTE_LINK_ERROR);

        // Verify that removing null source device will throw exception
        assertThrows(NullPointerException.class,
                () -> mBluetoothLeBroadcastAssistant.removeSource(null, 0));

        // Verify that removing unknown device will fail
        mBluetoothLeBroadcastAssistant.removeSource(testDevice, 0);
        verify(mCallbacks, timeout(ADD_SOURCE_TIMEOUT_MS)).onSourceRemoveFailed(
                testDevice, 0, BluetoothStatusCodes.ERROR_REMOTE_LINK_ERROR);

        // Do not forget to unregister callbacks
        mBluetoothLeBroadcastAssistant.unregisterCallback(mCallbacks);
    }

    @Test
    public void testGetAllSources() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        BluetoothDevice testDevice = mAdapter.getRemoteLeDevice(TEST_ADDRESS_1,
                BluetoothDevice.ADDRESS_TYPE_RANDOM);

        // Verify implementation throws exception when input is null
        assertThrows(NullPointerException.class,
                () -> mBluetoothLeBroadcastAssistant.getAllSources(null));

        // Verify returns empty list if a device is not connected
        assertTrue(mBluetoothLeBroadcastAssistant.getAllSources(testDevice).isEmpty());

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        // Verify returns empty list if bluetooth is not enabled
        assertTrue(mBluetoothLeBroadcastAssistant.getAllSources(testDevice).isEmpty());
    }

    @Test
    public void testSetConnectionPolicy() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        BluetoothDevice testDevice = mAdapter.getRemoteLeDevice(TEST_ADDRESS_1,
                BluetoothDevice.ADDRESS_TYPE_RANDOM);

        // Verify that it returns unknown for an unknown test device
        assertEquals(BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                mBluetoothLeBroadcastAssistant.getConnectionPolicy(testDevice));

        // Verify that it returns true even for an unknown test device
        assertTrue(mBluetoothLeBroadcastAssistant.setConnectionPolicy(testDevice,
                    BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        // Verify that it returns the same value we set before
        assertEquals(BluetoothProfile.CONNECTION_POLICY_ALLOWED,
                mBluetoothLeBroadcastAssistant.getConnectionPolicy(testDevice));
    }

    @Test
    public void testGetMaximumSourceCapacity() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        BluetoothDevice testDevice = mAdapter.getRemoteLeDevice(TEST_ADDRESS_1,
                BluetoothDevice.ADDRESS_TYPE_RANDOM);

        // Verifies that it returns 0 for an unknown test device
        assertEquals(mBluetoothLeBroadcastAssistant.getMaximumSourceCapacity(testDevice), 0);

        // Verifies that it throws exception when input is null
        assertThrows(NullPointerException.class,
                () -> mBluetoothLeBroadcastAssistant.getMaximumSourceCapacity(null));
    }

    @Test
    public void testIsSearchInProgress() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // Verify that it returns false when search is not in progress
        assertFalse(mBluetoothLeBroadcastAssistant.isSearchInProgress());
    }

    @Test
    public void testModifySource() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        BluetoothDevice testDevice = mAdapter.getRemoteLeDevice(TEST_ADDRESS_1,
                BluetoothDevice.ADDRESS_TYPE_RANDOM);
        BluetoothDevice testSourceDevice = mAdapter.getRemoteLeDevice(TEST_ADDRESS_1,
                BluetoothDevice.ADDRESS_TYPE_RANDOM);

        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                .setEncrypted(false)
                .setSourceDevice(testSourceDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                .setBroadcastId(TEST_BROADCAST_ID)
                .setBroadcastCode(null)
                .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS);

        BluetoothLeBroadcastSubgroup[] subgroups = new BluetoothLeBroadcastSubgroup[] {
                createBroadcastSubgroup()
        };
        for (BluetoothLeBroadcastSubgroup subgroup : subgroups) {
            builder.addSubgroup(subgroup);
        }
        BluetoothLeBroadcastMetadata metadata = builder.build();

        // Verifies that it throws exception when callback is not registered
        assertThrows(IllegalStateException.class, () -> mBluetoothLeBroadcastAssistant
                .modifySource(testDevice, 0, metadata));

        mBluetoothLeBroadcastAssistant.registerCallback(mExecutor, mCallbacks);

        // Verifies that it throws exception when argument is null
        assertThrows(NullPointerException.class, () -> mBluetoothLeBroadcastAssistant
                .modifySource(null, 0, null));
        assertThrows(NullPointerException.class, () -> mBluetoothLeBroadcastAssistant
                .modifySource(testDevice, 0, null));
        assertThrows(NullPointerException.class, () -> mBluetoothLeBroadcastAssistant
                .modifySource(null, 0, metadata));

        // Verify failure callback when test device is not connected
        mBluetoothLeBroadcastAssistant.modifySource(testDevice, 0, metadata);
        verify(mCallbacks, timeout(ADD_SOURCE_TIMEOUT_MS)).onSourceModifyFailed(
                testDevice, 0, BluetoothStatusCodes.ERROR_REMOTE_LINK_ERROR);
    }

    @Test
    public void testRegisterCallback() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        Executor executor = mContext.getMainExecutor();
        BluetoothLeBroadcastAssistant.Callback callback =
                new BluetoothLeBroadcastAssistant.Callback() {
                    @Override
                    public void onSearchStarted(int reason) {}
                    @Override
                    public void onSearchStartFailed(int reason) {}
                    @Override
                    public void onSearchStopped(int reason) {}
                    @Override
                    public void onSearchStopFailed(int reason) {}
                    @Override
                    public void onSourceFound(BluetoothLeBroadcastMetadata source) {}
                    @Override
                    public void onSourceAdded(BluetoothDevice sink, int sourceId, int reason) {}
                    @Override
                    public void onSourceAddFailed(BluetoothDevice sink,
                            BluetoothLeBroadcastMetadata source, int reason) {}
                    @Override
                    public void onSourceModified(BluetoothDevice sink, int sourceId, int reason) {}
                    @Override
                    public void onSourceModifyFailed(
                            BluetoothDevice sink, int sourceId, int reason) {}
                    @Override
                    public void onSourceRemoved(BluetoothDevice sink, int sourceId, int reason) {}
                    @Override
                    public void onSourceRemoveFailed(
                            BluetoothDevice sink, int sourceId, int reason) {}
                    @Override
                    public void onReceiveStateChanged(BluetoothDevice sink, int sourceId,
                            BluetoothLeBroadcastReceiveState state) {}
                };
        // empty calls to callback override
        callback.onSearchStarted(0);
        callback.onSearchStartFailed(0);
        callback.onSearchStopped(0);
        callback.onSearchStopFailed(0);
        callback.onSourceFound(null);
        callback.onSourceAdded(null, 0, 0);
        callback.onSourceAddFailed(null, null, 0);
        callback.onSourceModified(null, 0, 0);
        callback.onSourceModifyFailed(null, 0, 0);
        callback.onSourceRemoved(null, 0, 0);
        callback.onSourceRemoveFailed(null, 0, 0);
        callback.onReceiveStateChanged(null, 0, null);

        // Verify parameter
        assertThrows(NullPointerException.class, () -> mBluetoothLeBroadcastAssistant
                .registerCallback(null, callback));
        assertThrows(NullPointerException.class, () -> mBluetoothLeBroadcastAssistant
                .registerCallback(executor, null));
        assertThrows(NullPointerException.class, () -> mBluetoothLeBroadcastAssistant
                .unregisterCallback(null));


        // Verify that register and unregister callback will not cause any crush issues
        mBluetoothLeBroadcastAssistant.registerCallback(executor, callback);
        mBluetoothLeBroadcastAssistant.unregisterCallback(callback);
    }

    @Test
    public void testStartSearchingForSources() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // Verifies that it throws exception when no callback is registered
        assertThrows(IllegalStateException.class, () -> mBluetoothLeBroadcastAssistant
                .startSearchingForSources(Collections.emptyList()));

        mBluetoothLeBroadcastAssistant.registerCallback(mExecutor, mCallbacks);

        // Verifies that it throws exception when filter is null
        assertThrows(NullPointerException.class, () -> mBluetoothLeBroadcastAssistant
                .startSearchingForSources(null));

        // Verify that starting search triggers callback with the right reason
        mBluetoothLeBroadcastAssistant.startSearchingForSources(Collections.emptyList());
        verify(mCallbacks, timeout(START_SEARCH_TIMEOUT_MS))
                .onSearchStarted(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);

        // Verify search state is right
        assertTrue(mBluetoothLeBroadcastAssistant.isSearchInProgress());

        // Verify that stopping search triggers the callback with the right reason
        mBluetoothLeBroadcastAssistant.stopSearchingForSources();
        verify(mCallbacks, timeout(START_SEARCH_TIMEOUT_MS))
                .onSearchStarted(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);

        // Verify search state is right
        assertFalse(mBluetoothLeBroadcastAssistant.isSearchInProgress());

        // Do not forget to unregister callbacks
        mBluetoothLeBroadcastAssistant.unregisterCallback(mCallbacks);
    }

    @Test
    public void testGetConnectedDevices() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // Verify returns empty list if no broadcast assistant device is connected
        List<BluetoothDevice> connectedDevices =
                mBluetoothLeBroadcastAssistant.getConnectedDevices();
        assertNotNull(connectedDevices);
        assertTrue(connectedDevices.isEmpty());

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        connectedDevices = mBluetoothLeBroadcastAssistant.getConnectedDevices();
        assertNotNull(connectedDevices);
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void testGetDevicesMatchingConnectionStates() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        // Verify returns empty list if no broadcast assistant device is connected
        int[] states = {BluetoothProfile.STATE_CONNECTED};
        List<BluetoothDevice> connectedDevices =
                mBluetoothLeBroadcastAssistant.getDevicesMatchingConnectionStates(states);
        assertNotNull(connectedDevices);
        assertTrue(connectedDevices.isEmpty());

        // Verify exception is thrown when null input is given
        assertThrows(NullPointerException.class,
                () -> mBluetoothLeBroadcastAssistant.getDevicesMatchingConnectionStates(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        connectedDevices =
                mBluetoothLeBroadcastAssistant.getDevicesMatchingConnectionStates(states);
        assertNotNull(connectedDevices);
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void testGetConnectionState() {
        if (shouldSkipTest()) {
            return;
        }

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcastAssistant);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify exception is thrown when null input is given
        assertThrows(NullPointerException.class, () ->
                mBluetoothLeBroadcastAssistant.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothLeBroadcastAssistant.getConnectionState(testDevice));
    }

    @Test
    public void testProfileSupportLogic() {
        if (!mHasBluetooth) {
            return;
        }
        if (mAdapter.isLeAudioBroadcastAssistantSupported()
                == BluetoothStatusCodes.FEATURE_NOT_SUPPORTED) {
            assertFalse(mIsBroadcastAssistantSupported);
            return;
        }
        assertTrue(mIsBroadcastAssistantSupported);
    }

    private boolean shouldSkipTest() {
        return !(mHasBluetooth && mIsBroadcastAssistantSupported);
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

    private final class ServiceListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothLeBroadcastAssistant = (BluetoothLeBroadcastAssistant) proxy;
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

    static BluetoothLeBroadcastSubgroup createBroadcastSubgroup() {
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_LEFT).build();
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).setLanguage(TEST_LANGUAGE).build();
        BluetoothLeBroadcastSubgroup.Builder builder = new BluetoothLeBroadcastSubgroup.Builder()
                .setCodecId(TEST_CODEC_ID)
                .setCodecSpecificConfig(codecMetadata)
                .setContentMetadata(contentMetadata);
        BluetoothLeAudioCodecConfigMetadata emptyMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder().build();
        BluetoothLeBroadcastChannel channel = new BluetoothLeBroadcastChannel.Builder()
                .setChannelIndex(42).setSelected(true).setCodecMetadata(emptyMetadata).build();
        builder.addChannel(channel);
        return builder.build();
    }
}
