/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.vc;

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.BluetoothVolumeControl;
import android.bluetooth.IBluetoothVolumeControlCallback;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Looper;
import android.os.ParcelUuid;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.csip.CsipSetCoordinatorService;
import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class VolumeControlServiceTest {
    private BluetoothAdapter mAdapter;
    private AttributionSource mAttributionSource;
    private Context mTargetContext;
    private VolumeControlService mService;
    private VolumeControlService.BluetoothVolumeControlBinder mServiceBinder;
    private BluetoothDevice mDevice;
    private BluetoothDevice mDeviceTwo;
    private HashMap<BluetoothDevice, LinkedBlockingQueue<Intent>> mDeviceQueueMap;
    private static final int TIMEOUT_MS = 1000;
    private static final int BT_LE_AUDIO_MAX_VOL = 255;
    private static final int MEDIA_MIN_VOL = 0;
    private static final int MEDIA_MAX_VOL = 25;
    private static final int CALL_MIN_VOL = 1;
    private static final int CALL_MAX_VOL = 8;

    private BroadcastReceiver mVolumeControlIntentReceiver;

    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private VolumeControlNativeInterface mNativeInterface;
    @Mock private AudioManager mAudioManager;
    @Mock private ServiceFactory mServiceFactory;
    @Mock private CsipSetCoordinatorService mCsipService;

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        TestUtils.setAdapterService(mAdapterService);
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        doReturn(true, false).when(mAdapterService).isStartedProfile(anyString());

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mAttributionSource = mAdapter.getAttributionSource();

        doReturn(MEDIA_MIN_VOL).when(mAudioManager)
                .getStreamMinVolume(eq(AudioManager.STREAM_MUSIC));
        doReturn(MEDIA_MAX_VOL).when(mAudioManager)
                .getStreamMaxVolume(eq(AudioManager.STREAM_MUSIC));
        doReturn(CALL_MIN_VOL).when(mAudioManager)
                .getStreamMinVolume(eq(AudioManager.STREAM_VOICE_CALL));
        doReturn(CALL_MAX_VOL).when(mAudioManager)
                .getStreamMaxVolume(eq(AudioManager.STREAM_VOICE_CALL));

        startService();
        mService.mVolumeControlNativeInterface = mNativeInterface;
        mService.mAudioManager = mAudioManager;
        mService.mFactory = mServiceFactory;
        mServiceBinder = (VolumeControlService.BluetoothVolumeControlBinder) mService.initBinder();
        mServiceBinder.mIsTesting = true;

        doReturn(mCsipService).when(mServiceFactory).getCsipSetCoordinatorService();

        // Override the timeout value to speed up the test
        VolumeControlStateMachine.sConnectTimeoutMs = TIMEOUT_MS;    // 1s

        // Set up the Connection State Changed receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothVolumeControl.ACTION_CONNECTION_STATE_CHANGED);

        mVolumeControlIntentReceiver = new VolumeControlIntentReceiver();
        mTargetContext.registerReceiver(mVolumeControlIntentReceiver, filter);

        // Get a device for testing
        mDevice = TestUtils.getTestDevice(mAdapter, 0);
        mDeviceTwo = TestUtils.getTestDevice(mAdapter, 1);
        mDeviceQueueMap = new HashMap<>();
        mDeviceQueueMap.put(mDevice, new LinkedBlockingQueue<>());
        mDeviceQueueMap.put(mDeviceTwo, new LinkedBlockingQueue<>());
        doReturn(BluetoothDevice.BOND_BONDED).when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
        doReturn(new ParcelUuid[]{BluetoothUuid.VOLUME_CONTROL}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
    }

    @After
    public void tearDown() throws Exception {
        if (mService == null) {
            return;
        }

        stopService();
        mTargetContext.unregisterReceiver(mVolumeControlIntentReceiver);
        mDeviceQueueMap.clear();
        TestUtils.clearAdapterService(mAdapterService);
        reset(mAudioManager);
    }

    private void startService() throws TimeoutException {
        TestUtils.startService(mServiceRule, VolumeControlService.class);
        mService = VolumeControlService.getVolumeControlService();
        Assert.assertNotNull(mService);
    }

    private void stopService() throws TimeoutException {
        TestUtils.stopService(mServiceRule, VolumeControlService.class);
        mService = VolumeControlService.getVolumeControlService();
        Assert.assertNull(mService);
    }

    private class VolumeControlIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                BluetoothDevice device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE);
                Assert.assertNotNull(device);
                LinkedBlockingQueue<Intent> queue = mDeviceQueueMap.get(device);
                Assert.assertNotNull(queue);
                queue.put(intent);
            } catch (InterruptedException e) {
                Assert.fail("Cannot add Intent to the Connection State queue: "
                        + e.getMessage());
            }
        }
    }

    private void verifyConnectionStateIntent(int timeoutMs, BluetoothDevice device,
            int newState, int prevState) {
        Intent intent = TestUtils.waitForIntent(timeoutMs, mDeviceQueueMap.get(device));
        Assert.assertNotNull(intent);
        Assert.assertEquals(BluetoothVolumeControl.ACTION_CONNECTION_STATE_CHANGED,
                intent.getAction());
        Assert.assertEquals(device, intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
        Assert.assertEquals(newState, intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1));
        Assert.assertEquals(prevState, intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,
                -1));
    }

    private void verifyNoConnectionStateIntent(int timeoutMs, BluetoothDevice device) {
        Intent intent = TestUtils.waitForNoIntent(timeoutMs, mDeviceQueueMap.get(device));
        Assert.assertNull(intent);
    }

    /**
     * Test getting VolumeControl Service: getVolumeControlService()
     */
    @Test
    public void testGetVolumeControlService() {
        Assert.assertEquals(mService, VolumeControlService.getVolumeControlService());
    }

    /**
     * Test stop VolumeControl Service
     */
    @Test
    public void testStopVolumeControlService() throws Exception {
        // Prepare: connect
        connectDevice(mDevice);
        // VolumeControl Service is already running: test stop().
        // Note: must be done on the main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                Assert.assertTrue(mService.stop());
            }
        });
        // Try to restart the service. Note: must be done on the main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            public void run() {
                Assert.assertTrue(mService.start());
            }
        });
    }

    /**
     * Test get/set policy for BluetoothDevice
     */
    @Test
    public void testGetSetPolicy() {
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        Assert.assertEquals("Initial device policy",
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                mService.getConnectionPolicy(mDevice));

        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        Assert.assertEquals("Setting device policy to POLICY_FORBIDDEN",
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mService.getConnectionPolicy(mDevice));

        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        Assert.assertEquals("Setting device policy to POLICY_ALLOWED",
                BluetoothProfile.CONNECTION_POLICY_ALLOWED,
                mService.getConnectionPolicy(mDevice));
    }

    /**
     * Test if getProfileConnectionPolicy works after the service is stopped.
     */
    @Test
    public void testGetPolicyAfterStopped() throws Exception {
        mService.stop();
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        int defaultRecvValue = -1000;
        mServiceBinder.getConnectionPolicy(mDevice, mAttributionSource, recv);
        int policy = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(defaultRecvValue);
        Assert.assertEquals("Initial device policy",
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN, policy);
    }

    /**
     *  Test okToConnect method using various test cases
     */
    @Test
    public void testOkToConnect() {
        int badPolicyValue = 1024;
        int badBondState = 42;
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, badPolicyValue, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, badPolicyValue, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, true);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.CONNECTION_POLICY_ALLOWED, true);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, badPolicyValue, false);
        testOkToConnectCase(mDevice,
                badBondState, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToConnectCase(mDevice,
                badBondState, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mDevice,
                badBondState, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToConnectCase(mDevice,
                badBondState, badPolicyValue, false);
    }

    /**
     * Test that an outgoing connection to device that does not have Volume Control UUID is rejected
     */
    @Test
    public void testOutgoingConnectMissingVolumeControlUuid() {
        // Update the device policy so okToConnect() returns true
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectVolumeControl(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectVolumeControl(any(BluetoothDevice.class));

        // Return No UUID
        doReturn(new ParcelUuid[]{}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        // Send a connect request
        Assert.assertFalse("Connect expected to fail", mService.connect(mDevice));
    }

    /**
     * Test that an outgoing connection to device that have Volume Control UUID is successful
     */
    @Test
    public void testOutgoingConnectDisconnectExistingVolumeControlUuid() throws Exception {
        // Update the device policy so okToConnect() returns true
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectVolumeControl(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectVolumeControl(any(BluetoothDevice.class));

        // Return Volume Control UUID
        doReturn(new ParcelUuid[]{BluetoothUuid.VOLUME_CONTROL}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        // Send a connect request via binder
        SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();
        mServiceBinder.connect(mDevice, mAttributionSource, recv);
        Assert.assertTrue("Connect expected to succeed",
                recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS)).getValue(false));

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(TIMEOUT_MS, mDevice, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);

        // Send a disconnect request via binder
        recv = SynchronousResultReceiver.get();
        mServiceBinder.disconnect(mDevice, mAttributionSource, recv);
        Assert.assertTrue("Disconnect expected to succeed",
                recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS)).getValue(false));

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(TIMEOUT_MS, mDevice, BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);
    }

    /**
     * Test that an outgoing connection to device with POLICY_FORBIDDEN is rejected
     */
    @Test
    public void testOutgoingConnectPolicyForbidden() {
        doReturn(true).when(mNativeInterface).connectVolumeControl(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectVolumeControl(any(BluetoothDevice.class));

        // Set the device policy to POLICY_FORBIDDEN so connect() should fail
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        // Send a connect request
        Assert.assertFalse("Connect expected to fail", mService.connect(mDevice));
    }

    /**
     * Test that an outgoing connection times out
     */
    @Test
    public void testOutgoingConnectTimeout() throws Exception {
        // Update the device policy so okToConnect() returns true
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectVolumeControl(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectVolumeControl(any(BluetoothDevice.class));

        // Send a connect request
        Assert.assertTrue("Connect failed", mService.connect(mDevice));

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(TIMEOUT_MS, mDevice, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                mService.getConnectionState(mDevice));

        // Verify the connection state broadcast, and that we are in Disconnected state
        verifyConnectionStateIntent(VolumeControlStateMachine.sConnectTimeoutMs * 2,
                mDevice, BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);

        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        int defaultRecvValue = -1000;
        mServiceBinder.getConnectionState(mDevice, mAttributionSource, recv);
        int state = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(defaultRecvValue);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED, state);
    }

    /**
     * Test that only CONNECTION_STATE_CONNECTED or CONNECTION_STATE_CONNECTING Volume Control stack
     * events will create a state machine.
     */
    @Test
    public void testCreateStateMachineStackEvents() {
        // Update the device policy so okToConnect() returns true
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectVolumeControl(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectVolumeControl(any(BluetoothDevice.class));

        // stack event: CONNECTION_STATE_CONNECTING - state machine should be created
        generateConnectionMessageFromNative(mDevice, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                mService.getConnectionState(mDevice));
        Assert.assertTrue(mService.getDevices().contains(mDevice));

        // stack event: CONNECTION_STATE_DISCONNECTED - state machine should be removed
        generateConnectionMessageFromNative(mDevice, BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mService.getConnectionState(mDevice));
        Assert.assertTrue(mService.getDevices().contains(mDevice));
        mService.bondStateChanged(mDevice, BluetoothDevice.BOND_NONE);
        Assert.assertFalse(mService.getDevices().contains(mDevice));

        // stack event: CONNECTION_STATE_CONNECTED - state machine should be created
        generateConnectionMessageFromNative(mDevice, BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mService.getConnectionState(mDevice));
        Assert.assertTrue(mService.getDevices().contains(mDevice));

        // stack event: CONNECTION_STATE_DISCONNECTED - state machine should be removed
        generateConnectionMessageFromNative(mDevice, BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mService.getConnectionState(mDevice));
        Assert.assertTrue(mService.getDevices().contains(mDevice));
        mService.bondStateChanged(mDevice, BluetoothDevice.BOND_NONE);
        Assert.assertFalse(mService.getDevices().contains(mDevice));

        // stack event: CONNECTION_STATE_DISCONNECTING - state machine should not be created
        generateUnexpectedConnectionMessageFromNative(mDevice,
                BluetoothProfile.STATE_DISCONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mService.getConnectionState(mDevice));
        Assert.assertFalse(mService.getDevices().contains(mDevice));

        // stack event: CONNECTION_STATE_DISCONNECTED - state machine should not be created
        generateUnexpectedConnectionMessageFromNative(mDevice,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mService.getConnectionState(mDevice));
        Assert.assertFalse(mService.getDevices().contains(mDevice));
    }

    /**
     * Test that a CONNECTION_STATE_DISCONNECTED Volume Control stack event will remove the state
     * machine only if the device is unbond.
     */
    @Test
    public void testDeleteStateMachineDisconnectEvents() {
        // Update the device policy so okToConnect() returns true
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectVolumeControl(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectVolumeControl(any(BluetoothDevice.class));

        // stack event: CONNECTION_STATE_CONNECTING - state machine should be created
        generateConnectionMessageFromNative(mDevice, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                mService.getConnectionState(mDevice));
        Assert.assertTrue(mService.getDevices().contains(mDevice));

        // stack event: CONNECTION_STATE_DISCONNECTED - state machine is not removed
        generateConnectionMessageFromNative(mDevice, BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mService.getConnectionState(mDevice));
        Assert.assertTrue(mService.getDevices().contains(mDevice));

        // stack event: CONNECTION_STATE_CONNECTING - state machine remains
        generateConnectionMessageFromNative(mDevice, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                mService.getConnectionState(mDevice));
        Assert.assertTrue(mService.getDevices().contains(mDevice));

        // device bond state marked as unbond - state machine is not removed
        doReturn(BluetoothDevice.BOND_NONE).when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
        Assert.assertTrue(mService.getDevices().contains(mDevice));

        // stack event: CONNECTION_STATE_DISCONNECTED - state machine is removed
        generateConnectionMessageFromNative(mDevice, BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mService.getConnectionState(mDevice));
        Assert.assertFalse(mService.getDevices().contains(mDevice));
    }

    /**
     * Test that various Volume Control stack events will broadcast related states.
     */
    @Test
    public void testVolumeControlStackEvents() {
        int group_id = -1;
        int volume = 6;
        boolean mute = false;

        // Send a message to trigger volume state changed broadcast
        VolumeControlStackEvent stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_VOLUME_STATE_CHANGED);
        stackEvent.device = mDevice;
        stackEvent.valueInt1 = group_id;
        stackEvent.valueInt2 = volume;
        stackEvent.valueBool1 = mute;
        mService.messageFromNative(stackEvent);
    }

    int getLeAudioVolume(int index, int minIndex, int maxIndex, int streamType) {
        // Note: This has to be the same as mBtHelper.setLeAudioVolume()
        return (int) Math.round((double) index * BT_LE_AUDIO_MAX_VOL / maxIndex);
    }

    void testVolumeCalculations(int streamType, int minIdx, int maxIdx) {
        // Send a message to trigger volume state changed broadcast
        final VolumeControlStackEvent stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_VOLUME_STATE_CHANGED);
        stackEvent.device = null;
        stackEvent.valueInt1 = 1;       // groupId
        stackEvent.valueBool1 = false;  // isMuted
        stackEvent.valueBool2 = true;   // isAutonomous

        IntStream.range(minIdx, maxIdx).forEach(idx -> {
            // Given the reference volume index, set the LeAudio Volume
            stackEvent.valueInt2 = getLeAudioVolume(idx,
                            mAudioManager.getStreamMinVolume(streamType),
                            mAudioManager.getStreamMaxVolume(streamType), streamType);
            mService.messageFromNative(stackEvent);

            // Verify that setting LeAudio Volume, sets the original volume index to Audio FW
            verify(mAudioManager, times(1)).setStreamVolume(eq(streamType), eq(idx), anyInt());
        });
    }

    @Test
    public void testAutonomousVolumeStateChange() {
        doReturn(AudioManager.MODE_IN_CALL).when(mAudioManager).getMode();
        testVolumeCalculations(AudioManager.STREAM_VOICE_CALL, CALL_MIN_VOL, CALL_MAX_VOL);

        doReturn(AudioManager.MODE_NORMAL).when(mAudioManager).getMode();
        testVolumeCalculations(AudioManager.STREAM_MUSIC, MEDIA_MIN_VOL, MEDIA_MAX_VOL);
    }

    /**
     * Test Volume Control cache.
     */
    @Test
    public void testVolumeCache() throws Exception {
        int groupId = 1;
        int volume = 6;

        Assert.assertEquals(-1, mService.getGroupVolume(groupId));
        final SynchronousResultReceiver<Void> voidRecv = SynchronousResultReceiver.get();
        mServiceBinder.setGroupVolume(groupId, volume, mAttributionSource, voidRecv);
        voidRecv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS));

        final SynchronousResultReceiver<Integer> intRecv = SynchronousResultReceiver.get();
        int defaultRecvValue = -100;
        mServiceBinder.getGroupVolume(groupId, mAttributionSource, intRecv);
        int groupVolume = intRecv.awaitResultNoInterrupt(
                Duration.ofMillis(TIMEOUT_MS)).getValue(defaultRecvValue);
        Assert.assertEquals(volume, groupVolume);

        volume = 10;
        // Send autonomous volume change.
        VolumeControlStackEvent stackEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_VOLUME_STATE_CHANGED);
        stackEvent.device = null;
        stackEvent.valueInt1 = groupId;
        stackEvent.valueInt2 = volume;
        stackEvent.valueBool1 = false;
        stackEvent.valueBool2 = true; /* autonomous */
        mService.messageFromNative(stackEvent);

        Assert.assertEquals(volume, mService.getGroupVolume(groupId));
    }

    /**
     * Test setting volume for a group member who connects after the volume level
     * for a group was already changed and cached.
     */
    @Test
    public void testLateConnectingDevice() throws Exception {
        int groupId = 1;
        int groupVolume = 56;

        // Both devices are in the same group
        when(mCsipService.getGroupId(mDevice, BluetoothUuid.CAP)).thenReturn(groupId);
        when(mCsipService.getGroupId(mDeviceTwo, BluetoothUuid.CAP)).thenReturn(groupId);

        // Update the device policy so okToConnect() returns true
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(any(BluetoothDevice.class),
                        eq(BluetoothProfile.VOLUME_CONTROL)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectVolumeControl(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectVolumeControl(any(BluetoothDevice.class));

        generateConnectionMessageFromNative(mDevice, BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mService.getConnectionState(mDevice));
        Assert.assertTrue(mService.getDevices().contains(mDevice));

        mService.setGroupVolume(groupId, groupVolume);
        verify(mNativeInterface, times(1)).setGroupVolume(eq(groupId), eq(groupVolume));
        verify(mNativeInterface, times(0)).setVolume(eq(mDeviceTwo), eq(groupVolume));

        // Verify that second device gets the proper group volume level when connected
        generateConnectionMessageFromNative(mDeviceTwo, BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mService.getConnectionState(mDeviceTwo));
        Assert.assertTrue(mService.getDevices().contains(mDeviceTwo));
        verify(mNativeInterface, times(1)).setVolume(eq(mDeviceTwo), eq(groupVolume));
    }

    /**
     * Test setting volume for a new group member who is discovered after the volume level
     * for a group was already changed and cached.
     */
    @Test
    public void testLateDiscoveredGroupMember() throws Exception {
        int groupId = 1;
        int groupVolume = 56;

        // For now only one device is in the group
        when(mCsipService.getGroupId(mDevice, BluetoothUuid.CAP)).thenReturn(groupId);
        when(mCsipService.getGroupId(mDeviceTwo, BluetoothUuid.CAP)).thenReturn(-1);

        // Update the device policy so okToConnect() returns true
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager
                .getProfileConnectionPolicy(any(BluetoothDevice.class),
                        eq(BluetoothProfile.VOLUME_CONTROL)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectVolumeControl(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectVolumeControl(any(BluetoothDevice.class));

        generateConnectionMessageFromNative(mDevice, BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mService.getConnectionState(mDevice));
        Assert.assertTrue(mService.getDevices().contains(mDevice));

        // Set the group volume
        mService.setGroupVolume(groupId, groupVolume);

        // Verify that second device will not get the group volume level if it is not a group member
        generateConnectionMessageFromNative(mDeviceTwo, BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mService.getConnectionState(mDeviceTwo));
        Assert.assertTrue(mService.getDevices().contains(mDeviceTwo));
        verify(mNativeInterface, times(0)).setVolume(eq(mDeviceTwo), eq(groupVolume));

        // But gets the volume when it becomes the group member
        when(mCsipService.getGroupId(mDeviceTwo, BluetoothUuid.CAP)).thenReturn(groupId);
        mService.handleGroupNodeAdded(groupId, mDeviceTwo);
        verify(mNativeInterface, times(1)).setVolume(eq(mDeviceTwo), eq(groupVolume));
    }

    @Test
    public void testServiceBinderGetDevicesMatchingConnectionStates() throws Exception {
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();
        mServiceBinder.getDevicesMatchingConnectionStates(null, mAttributionSource, recv);
        List<BluetoothDevice> devices = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(null);
        Assert.assertEquals(0, devices.size());
    }

    @Test
    public void testServiceBinderSetConnectionPolicy() throws Exception {
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();
        boolean defaultRecvValue = false;
        mServiceBinder.setConnectionPolicy(
                mDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, mAttributionSource, recv);
        Assert.assertTrue(recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(defaultRecvValue));
        verify(mDatabaseManager).setProfileConnectionPolicy(
                mDevice, BluetoothProfile.VOLUME_CONTROL, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
    }

    @Test
    public void testServiceBinderVolumeOffsetMethods() throws Exception {
        // Send a message to trigger connection completed
        VolumeControlStackEvent event = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_DEVICE_AVAILABLE);
        event.device = mDevice;
        event.valueInt1 = 2; // number of external outputs
        mService.messageFromNative(event);

        final SynchronousResultReceiver<Boolean> boolRecv = SynchronousResultReceiver.get();
        boolean defaultRecvValue = false;
        mServiceBinder.isVolumeOffsetAvailable(mDevice, mAttributionSource, boolRecv);
        Assert.assertTrue(boolRecv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(defaultRecvValue));

        int volumeOffset = 100;
        final SynchronousResultReceiver<Void> voidRecv = SynchronousResultReceiver.get();
        mServiceBinder.setVolumeOffset(mDevice, volumeOffset, mAttributionSource, voidRecv);
        voidRecv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS));
        verify(mNativeInterface).setExtAudioOutVolumeOffset(mDevice, 1, volumeOffset);
    }

    @Test
    public void testServiceBinderRegisterUnregisterCallback() throws Exception {
        IBluetoothVolumeControlCallback callback =
                Mockito.mock(IBluetoothVolumeControlCallback.class);
        Binder binder = Mockito.mock(Binder.class);
        when(callback.asBinder()).thenReturn(binder);

        int size = mService.mCallbacks.getRegisteredCallbackCount();
        SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();
        mServiceBinder.registerCallback(callback, mAttributionSource, recv);
        recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS)).getValue(null);
        Assert.assertEquals(size + 1, mService.mCallbacks.getRegisteredCallbackCount());

        recv = SynchronousResultReceiver.get();
        mServiceBinder.unregisterCallback(callback, mAttributionSource, recv);
        recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS)).getValue(null);
        Assert.assertEquals(size, mService.mCallbacks.getRegisteredCallbackCount());
    }

    @Test
    public void testServiceBinderMuteMethods() throws Exception {
        SynchronousResultReceiver<Void> voidRecv = SynchronousResultReceiver.get();
        mServiceBinder.mute(mDevice, mAttributionSource, voidRecv);
        voidRecv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS));
        verify(mNativeInterface).mute(mDevice);

        voidRecv = SynchronousResultReceiver.get();
        mServiceBinder.unmute(mDevice, mAttributionSource, voidRecv);
        voidRecv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS));
        verify(mNativeInterface).unmute(mDevice);

        int groupId = 1;
        voidRecv = SynchronousResultReceiver.get();
        mServiceBinder.muteGroup(groupId, mAttributionSource, voidRecv);
        voidRecv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS));
        verify(mNativeInterface).muteGroup(groupId);

        voidRecv = SynchronousResultReceiver.get();
        mServiceBinder.unmuteGroup(groupId, mAttributionSource, voidRecv);
        voidRecv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS));
        verify(mNativeInterface).unmuteGroup(groupId);
    }

    @Test
    public void testVolumeControlOffsetDescriptor() {
        VolumeControlService.VolumeControlOffsetDescriptor descriptor =
                new VolumeControlService.VolumeControlOffsetDescriptor();
        int invalidId = -1;
        int validId = 10;
        int testValue = 100;
        String testDesc = "testDescription";
        int testLocation = 10000;

        Assert.assertEquals(0, descriptor.size());
        descriptor.add(validId);
        Assert.assertEquals(1, descriptor.size());

        Assert.assertFalse(descriptor.setValue(invalidId, testValue));
        Assert.assertTrue(descriptor.setValue(validId, testValue));
        Assert.assertEquals(0, descriptor.getValue(invalidId));
        Assert.assertEquals(testValue, descriptor.getValue(validId));

        Assert.assertFalse(descriptor.setDescription(invalidId, testDesc));
        Assert.assertTrue(descriptor.setDescription(validId, testDesc));
        Assert.assertEquals(null, descriptor.getDescription(invalidId));
        Assert.assertEquals(testDesc, descriptor.getDescription(validId));

        Assert.assertFalse(descriptor.setLocation(invalidId, testLocation));
        Assert.assertTrue(descriptor.setLocation(validId, testLocation));
        Assert.assertEquals(0, descriptor.getLocation(invalidId));
        Assert.assertEquals(testLocation, descriptor.getLocation(validId));

        StringBuilder sb = new StringBuilder();
        descriptor.dump(sb);
        Assert.assertTrue(sb.toString().contains(testDesc));

        descriptor.add(validId + 1);
        Assert.assertEquals(2, descriptor.size());
        descriptor.remove(validId);
        Assert.assertEquals(1, descriptor.size());
        descriptor.clear();
        Assert.assertEquals(0, descriptor.size());
    }

    @Test
    public void testDump_doesNotCrash() throws Exception {
        connectDevice(mDevice);

        StringBuilder sb = new StringBuilder();
        mService.dump(sb);
    }

    private void connectDevice(BluetoothDevice device) throws Exception {
        VolumeControlStackEvent connCompletedEvent;

        List<BluetoothDevice> prevConnectedDevices = mService.getConnectedDevices();

        // Update the device policy so okToConnect() returns true
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager.getProfileConnectionPolicy(device, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectVolumeControl(device);
        doReturn(true).when(mNativeInterface).disconnectVolumeControl(device);

        // Send a connect request
        Assert.assertTrue("Connect failed", mService.connect(device));

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(TIMEOUT_MS, device, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                mService.getConnectionState(device));

        // Send a message to trigger connection completed
        connCompletedEvent = new VolumeControlStackEvent(
                VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = device;
        connCompletedEvent.valueInt1 = VolumeControlStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Connected state
        verifyConnectionStateIntent(TIMEOUT_MS, device, BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_CONNECTING);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED,
                mService.getConnectionState(device));

        // Verify that the device is in the list of connected devices
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();
        mServiceBinder.getConnectedDevices(mAttributionSource, recv);
        List<BluetoothDevice> connectedDevices =
                recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS)).getValue(null);
        Assert.assertTrue(connectedDevices.contains(device));
        // Verify the list of previously connected devices
        for (BluetoothDevice prevDevice : prevConnectedDevices) {
            Assert.assertTrue(connectedDevices.contains(prevDevice));
        }
    }

    private void generateConnectionMessageFromNative(BluetoothDevice device, int newConnectionState,
            int oldConnectionState) {
        VolumeControlStackEvent stackEvent =
                new VolumeControlStackEvent(
                        VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = device;
        stackEvent.valueInt1 = newConnectionState;
        mService.messageFromNative(stackEvent);
        // Verify the connection state broadcast
        verifyConnectionStateIntent(TIMEOUT_MS, device, newConnectionState, oldConnectionState);
    }

    private void generateUnexpectedConnectionMessageFromNative(BluetoothDevice device,
            int newConnectionState, int oldConnectionState) {
        VolumeControlStackEvent stackEvent =
                new VolumeControlStackEvent(
                        VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = device;
        stackEvent.valueInt1 = newConnectionState;
        mService.messageFromNative(stackEvent);
        // Verify the connection state broadcast
        verifyNoConnectionStateIntent(TIMEOUT_MS, device);
    }

    /**
     *  Helper function to test okToConnect() method
     *
     *  @param device test device
     *  @param bondState bond state value, could be invalid
     *  @param policy value, could be invalid
     *  @param expected expected result from okToConnect()
     */
    private void testOkToConnectCase(BluetoothDevice device, int bondState, int policy,
            boolean expected) {
        doReturn(bondState).when(mAdapterService).getBondState(device);
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager.getProfileConnectionPolicy(device, BluetoothProfile.VOLUME_CONTROL))
                .thenReturn(policy);
        Assert.assertEquals(expected, mService.okToConnect(device));
    }
}
