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
import static android.bluetooth.BluetoothStatusCodes.FEATURE_SUPPORTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcast;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothLeBroadcastTest {
    private static final String TAG = BluetoothLeBroadcastTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private static final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";
    private static final int TEST_BROADCAST_ID = 42;
    private static final int TEST_ADVERTISER_SID = 1234;
    private static final int TEST_PA_SYNC_INTERVAL = 100;
    private static final int TEST_PRESENTATION_DELAY_MS = 345;

    private static final int TEST_CODEC_ID = 42;
    private static final int TEST_CHANNEL_INDEX = 56;

    // For BluetoothLeAudioCodecConfigMetadata
    private static final long TEST_AUDIO_LOCATION_FRONT_LEFT = 0x01;
    private static final long TEST_AUDIO_LOCATION_FRONT_RIGHT = 0x02;

    // For BluetoothLeAudioContentMetadata
    private static final String TEST_PROGRAM_INFO = "Test";
    // German language code in ISO 639-3
    private static final String TEST_LANGUAGE = "deu";

    private static final int TEST_REASON = BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST;

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    private BluetoothLeBroadcast mBluetoothLeBroadcast;
    private boolean mIsLeBroadcastSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    private boolean mOnBroadcastStartedCalled = false;
    private boolean mOnBroadcastStartFailedCalled = false;
    private boolean mOnBroadcastStoppedCalled = false;
    private boolean mOnBroadcastStopFailedCalled = false;
    private boolean mOnPlaybackStartedCalled = false;
    private boolean mOnPlaybackStoppedCalled = false;
    private boolean mOnBroadcastUpdatedCalled = false;
    private boolean mOnBroadcastUpdateFailedCalled = false;
    private boolean mOnBroadcastMetadataChangedCalled = false;

    private BluetoothLeBroadcastMetadata mTestMetadata;
    private CountDownLatch mCallbackCountDownLatch;

    BluetoothLeBroadcastSubgroup createBroadcastSubgroup() {
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

        BluetoothLeAudioCodecConfigMetadata channelCodecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_RIGHT).build();

        // builder expect at least one channel
        BluetoothLeBroadcastChannel channel =
                new BluetoothLeBroadcastChannel.Builder()
                        .setSelected(true)
                        .setChannelIndex(TEST_CHANNEL_INDEX)
                        .setCodecMetadata(channelCodecMetadata)
                        .build();
        builder.addChannel(channel);
        return builder.build();
    }

    BluetoothLeBroadcastMetadata createBroadcastMetadata() {
        BluetoothDevice testDevice =
                mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);

        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                        .setEncrypted(false)
                        .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                        .setBroadcastId(TEST_BROADCAST_ID)
                        .setBroadcastCode(null)
                        .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                        .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS);
        // builder expect at least one subgroup
        builder.addSubgroup(createBroadcastSubgroup());
        return builder.build();
    }

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
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothLeBroadcast = null;

        mIsLeBroadcastSupported =
                mAdapter.isLeAudioBroadcastSourceSupported() == FEATURE_SUPPORTED;
        if (mIsLeBroadcastSupported) {
            boolean isBroadcastSourceEnabledInConfig =
                    TestUtils.isProfileEnabled(BluetoothProfile.LE_AUDIO_BROADCAST);
            assertTrue("Config must be true when profile is supported",
                    isBroadcastSourceEnabledInConfig);
        }
        if (!mIsLeBroadcastSupported) {
            return;
        }

        mIsLeBroadcastSupported = mAdapter.getProfileProxy(mContext, new ServiceListener(),
                BluetoothProfile.LE_AUDIO_BROADCAST);
        assertTrue("Profile proxy should be accessible when profile is supported",
                mIsLeBroadcastSupported);
    }

    @After
    public void tearDown() {
        if (mHasBluetooth) {
            if (mBluetoothLeBroadcast != null) {
                mBluetoothLeBroadcast.close();
                mBluetoothLeBroadcast = null;
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
        assertNotNull(mBluetoothLeBroadcast);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO_BROADCAST, mBluetoothLeBroadcast);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @Test
    public void testGetConnectedDevices() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify if asserts as Broadcaster is not connection-oriented profile
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothLeBroadcast.getConnectedDevices());
    }

    @Test
    public void testGetDevicesMatchingConnectionStates() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify if asserts as Broadcaster is not connection-oriented profile
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothLeBroadcast.getDevicesMatchingConnectionStates(null));
    }

    @Test
    public void testGetConnectionState() {
        if (shouldSkipTest()) {
            return;
        }

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        // Verify if asserts as Broadcaster is not connection-oriented profile
        assertThrows(UnsupportedOperationException.class,
                () -> mBluetoothLeBroadcast.getConnectionState(null));
    }

    @Test
    public void testProfileSupportLogic() {
        if (!mHasBluetooth) {
            return;
        }
        if (mAdapter.isLeAudioBroadcastSourceSupported()
                == BluetoothStatusCodes.FEATURE_NOT_SUPPORTED) {
            assertFalse(mIsLeBroadcastSupported);
            return;
        }
        assertTrue(mIsLeBroadcastSupported);
    }

    @Test
    public void testRegisterUnregisterCallback() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        Executor executor = mContext.getMainExecutor();
        BluetoothLeBroadcast.Callback callback =
                new BluetoothLeBroadcast.Callback() {
                    @Override
                    public void onBroadcastStarted(int reason, int broadcastId) {}
                    @Override
                    public void onBroadcastStartFailed(int reason) {}
                    @Override
                    public void onBroadcastStopped(int reason, int broadcastId) {}
                    @Override
                    public void onBroadcastStopFailed(int reason) {}
                    @Override
                    public void onPlaybackStarted(int reason, int broadcastId) {}
                    @Override
                    public void onPlaybackStopped(int reason, int broadcastId) {}
                    @Override
                    public void onBroadcastUpdated(int reason, int broadcastId) {}
                    @Override
                    public void onBroadcastUpdateFailed(int reason, int broadcastId) {}
                    @Override
                    public void onBroadcastMetadataChanged(int broadcastId,
                            BluetoothLeBroadcastMetadata metadata) {}
                };

        // Verify invalid parameters
        assertThrows(NullPointerException.class, () ->
                mBluetoothLeBroadcast.registerCallback(null, callback));
        assertThrows(NullPointerException.class, () ->
                mBluetoothLeBroadcast.registerCallback(executor, null));
        assertThrows(NullPointerException.class, () ->
                mBluetoothLeBroadcast.unregisterCallback(null));

        // Verify valid parameters
        mBluetoothLeBroadcast.registerCallback(executor, callback);
        mBluetoothLeBroadcast.unregisterCallback(callback);
    }

    @Test
    public void testRegisterCallbackNoPermission() {
        if (shouldSkipTest()) {
            return;
        }

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        Executor executor = mContext.getMainExecutor();
        BluetoothLeBroadcast.Callback callback =
                new BluetoothLeBroadcast.Callback() {
                    @Override
                    public void onBroadcastStarted(int reason, int broadcastId) {}
                    @Override
                    public void onBroadcastStartFailed(int reason) {}
                    @Override
                    public void onBroadcastStopped(int reason, int broadcastId) {}
                    @Override
                    public void onBroadcastStopFailed(int reason) {}
                    @Override
                    public void onPlaybackStarted(int reason, int broadcastId) {}
                    @Override
                    public void onPlaybackStopped(int reason, int broadcastId) {}
                    @Override
                    public void onBroadcastUpdated(int reason, int broadcastId) {}
                    @Override
                    public void onBroadcastUpdateFailed(int reason, int broadcastId) {}
                    @Override
                    public void onBroadcastMetadataChanged(int broadcastId,
                            BluetoothLeBroadcastMetadata metadata) {}
                };

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class,
                () -> mBluetoothLeBroadcast.registerCallback(executor, callback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @Test
    public void testCallbackCalls() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        BluetoothLeBroadcast.Callback callback =
                new BluetoothLeBroadcast.Callback() {
                    @Override
                    public void onBroadcastStarted(int reason, int broadcastId) {
                        mOnBroadcastStartedCalled = true;
                        assertEquals(TEST_BROADCAST_ID, broadcastId);
                        assertEquals(TEST_REASON, reason);
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onBroadcastStartFailed(int reason) {
                        mOnBroadcastStartFailedCalled = true;
                        assertEquals(TEST_REASON, reason);
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onBroadcastStopped(int reason, int broadcastId) {
                        mOnBroadcastStoppedCalled = true;
                        assertEquals(TEST_BROADCAST_ID, broadcastId);
                        assertEquals(TEST_REASON, reason);
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onBroadcastStopFailed(int reason) {
                        mOnBroadcastStopFailedCalled = true;
                        assertEquals(TEST_REASON, reason);
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onPlaybackStarted(int reason, int broadcastId) {
                        mOnPlaybackStartedCalled = true;
                        assertEquals(TEST_BROADCAST_ID, broadcastId);
                        assertEquals(TEST_REASON, reason);
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onPlaybackStopped(int reason, int broadcastId) {
                        mOnPlaybackStoppedCalled = true;
                        assertEquals(TEST_BROADCAST_ID, broadcastId);
                        assertEquals(TEST_REASON, reason);
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onBroadcastUpdated(int reason, int broadcastId) {
                        mOnBroadcastUpdatedCalled = true;
                        assertEquals(TEST_BROADCAST_ID, broadcastId);
                        assertEquals(TEST_REASON, reason);
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onBroadcastUpdateFailed(int reason, int broadcastId) {
                        mOnBroadcastUpdateFailedCalled = true;
                        assertEquals(TEST_BROADCAST_ID, broadcastId);
                        assertEquals(TEST_REASON, reason);
                        mCallbackCountDownLatch.countDown();
                    }

                    @Override
                    public void onBroadcastMetadataChanged(int broadcastId,
                            BluetoothLeBroadcastMetadata metadata) {
                        mOnBroadcastMetadataChangedCalled = true;
                        assertEquals(TEST_BROADCAST_ID, broadcastId);
                        assertEquals(mTestMetadata, metadata);
                        mCallbackCountDownLatch.countDown();
                    }
                };

        mCallbackCountDownLatch = new CountDownLatch(9);
        try {
            callback.onBroadcastStarted(TEST_REASON, TEST_BROADCAST_ID);
            callback.onBroadcastStartFailed(TEST_REASON);
            callback.onBroadcastStopped(TEST_REASON, TEST_BROADCAST_ID);
            callback.onBroadcastStopFailed(TEST_REASON);
            callback.onPlaybackStarted(TEST_REASON, TEST_BROADCAST_ID);
            callback.onPlaybackStopped(TEST_REASON, TEST_BROADCAST_ID);
            callback.onBroadcastUpdated(TEST_REASON, TEST_BROADCAST_ID);
            callback.onBroadcastUpdateFailed(TEST_REASON, TEST_BROADCAST_ID);
            mTestMetadata = createBroadcastMetadata();
            callback.onBroadcastMetadataChanged(TEST_BROADCAST_ID, mTestMetadata);

            // Wait for all the callback calls or 5 seconds to verify
            mCallbackCountDownLatch.await(5, TimeUnit.SECONDS);
            assertTrue(mOnBroadcastStartedCalled);
            assertTrue(mOnBroadcastStartFailedCalled);
            assertTrue(mOnBroadcastStoppedCalled);
            assertTrue(mOnBroadcastStopFailedCalled);
            assertTrue(mOnPlaybackStartedCalled);
            assertTrue(mOnPlaybackStoppedCalled);
            assertTrue(mOnBroadcastUpdatedCalled);
            assertTrue(mOnBroadcastUpdateFailedCalled);
            assertTrue(mOnBroadcastMetadataChangedCalled);
        } catch (InterruptedException e) {
            fail("Failed to register callback call: " + e.toString());
        }
    }

    @Test
    public void testStartBroadcast() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        BluetoothLeAudioContentMetadata.Builder contentMetadataBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        byte[] broadcastCode = {1, 2, 3, 4, 5, 6};
        mBluetoothLeBroadcast.startBroadcast(contentMetadataBuilder.build(), broadcastCode);
    }

    @Test
    public void testUpdateBroadcast() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        BluetoothLeAudioContentMetadata.Builder contentMetadataBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        mBluetoothLeBroadcast.updateBroadcast(1, contentMetadataBuilder.build());
    }

    @Test
    public void testStopBroadcast() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothLeBroadcast.stopBroadcast(1);
    }

    @Test
    public void testIsPlaying() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        assertFalse(mBluetoothLeBroadcast.isPlaying(1));
    }

    @Test
    public void testGetAllBroadcastMetadata() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        List<BluetoothLeBroadcastMetadata> metaList =
                mBluetoothLeBroadcast.getAllBroadcastMetadata();
        assertTrue(metaList.isEmpty());
    }

    @Test
    public void testGetMaximumNumberOfBroadcasts() {
        if (shouldSkipTest()) {
            return;
        }
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeBroadcast);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        assertEquals(1, mBluetoothLeBroadcast.getMaximumNumberOfBroadcasts());
    }

    private boolean shouldSkipTest() {
        return !(mHasBluetooth && mIsLeBroadcastSupported);
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

    private final class ServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothLeBroadcast = (BluetoothLeBroadcast) proxy;
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
