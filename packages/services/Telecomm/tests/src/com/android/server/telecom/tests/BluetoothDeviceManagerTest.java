/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Parcel;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.bluetooth.BluetoothDeviceManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;
import com.android.server.telecom.bluetooth.BluetoothStateReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class BluetoothDeviceManagerTest extends TelecomTestCase {
    private static final String DEVICE_ADDRESS_1 = "00:00:00:00:00:01";

    @Mock BluetoothRouteManager mRouteManager;
    @Mock BluetoothHeadset mBluetoothHeadset;
    @Mock BluetoothAdapter mAdapter;
    @Mock BluetoothHearingAid mBluetoothHearingAid;
    @Mock BluetoothLeAudio mBluetoothLeAudio;
    @Mock AudioManager mockAudioManager;

    BluetoothDeviceManager mBluetoothDeviceManager;
    BluetoothProfile.ServiceListener serviceListenerUnderTest;
    BluetoothStateReceiver receiverUnderTest;
    ArgumentCaptor<BluetoothLeAudio.Callback> leAudioCallbacksTest;

    private BluetoothDevice device1;
    private BluetoothDevice device2;
    private BluetoothDevice device3;
    private BluetoothDevice device4;
    private BluetoothDevice device5;
    private BluetoothDevice device6;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        device1 = makeBluetoothDevice("00:00:00:00:00:01");
        // hearing aid
        device2 = makeBluetoothDevice("00:00:00:00:00:02");
        device3 = makeBluetoothDevice("00:00:00:00:00:03");
        // hearing aid
        device4 = makeBluetoothDevice("00:00:00:00:00:04");
        // le audio
        device5 = makeBluetoothDevice("00:00:00:00:00:05");
        device6 = makeBluetoothDevice("00:00:00:00:00:06");

        when(mBluetoothHearingAid.getHiSyncId(device2)).thenReturn(100L);
        when(mBluetoothHearingAid.getHiSyncId(device4)).thenReturn(100L);

        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mBluetoothDeviceManager = new BluetoothDeviceManager(mContext, mAdapter);
        mBluetoothDeviceManager.setBluetoothRouteManager(mRouteManager);

        mockAudioManager = mContext.getSystemService(AudioManager.class);

        ArgumentCaptor<BluetoothProfile.ServiceListener> serviceCaptor =
                ArgumentCaptor.forClass(BluetoothProfile.ServiceListener.class);
        verify(mAdapter).getProfileProxy(eq(mContext),
                serviceCaptor.capture(), eq(BluetoothProfile.HEADSET));
        serviceListenerUnderTest = serviceCaptor.getValue();

        receiverUnderTest = new BluetoothStateReceiver(mBluetoothDeviceManager, mRouteManager);

        mBluetoothDeviceManager.setHeadsetServiceForTesting(mBluetoothHeadset);
        mBluetoothDeviceManager.setHearingAidServiceForTesting(mBluetoothHearingAid);

        leAudioCallbacksTest =
                         ArgumentCaptor.forClass(BluetoothLeAudio.Callback.class);
        mBluetoothDeviceManager.setLeAudioServiceForTesting(mBluetoothLeAudio);
        verify(mBluetoothLeAudio).registerCallback(any(), leAudioCallbacksTest.capture());
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testSingleDeviceConnectAndDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        assertEquals(0, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testAddDeviceFailsWhenServicesAreNull() {
        mBluetoothDeviceManager.setHeadsetServiceForTesting(null);
        mBluetoothDeviceManager.setHearingAidServiceForTesting(null);

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));

        assertEquals(0, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testMultiDeviceConnectAndDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(1)).thenReturn(device5);

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device6, 2);
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(2)).thenReturn(device6);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        assertEquals(3, mBluetoothDeviceManager.getNumConnectedDevices());
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testLeAudioMissedGroupCallbackBeforeConnected() {
        /* This should be called on connection state changed */
        when(mBluetoothLeAudio.getGroupId(device5)).thenReturn(1);
        when(mBluetoothLeAudio.getGroupId(device6)).thenReturn(1);

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(1)).thenReturn(device5);
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(1, mBluetoothDeviceManager.getUniqueConnectedDevices().size());
    }

    @SmallTest
    @Test
    public void testLeAudioGroupAvailableBeforeConnect() {
        /* Device is known (e.g. from storage) */
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device6, 1);
        /* Make sure getGroupId is not called for known devices */
        verify(mBluetoothLeAudio, never()).getGroupId(device5);
        verify(mBluetoothLeAudio, never()).getGroupId(device6);

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(1)).thenReturn(device5);
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(1, mBluetoothDeviceManager.getUniqueConnectedDevices().size());
    }

    @SmallTest
    @Test
    public void testHearingAidDedup() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device4,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        assertEquals(3, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(2, mBluetoothDeviceManager.getUniqueConnectedDevices().size());
    }

    @SmallTest
    @Test
    public void testLeAudioDedup() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device6, 1);
        when(mBluetoothLeAudio.getConnectedGroupLeadDevice(1)).thenReturn(device5);
        assertEquals(2, mBluetoothDeviceManager.getNumConnectedDevices());
        assertEquals(2, mBluetoothDeviceManager.getUniqueConnectedDevices().size());
    }

    @SmallTest
    @Test
    public void testHeadsetServiceDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        serviceListenerUnderTest.onServiceDisconnected(BluetoothProfile.HEADSET);

        verify(mRouteManager).onActiveDeviceChanged(isNull(),
                eq(BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        verify(mRouteManager).onDeviceLost(device1.getAddress());
        verify(mRouteManager).onDeviceLost(device3.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device2.getAddress());
        assertNull(mBluetoothDeviceManager.getBluetoothHeadset());
        assertEquals(1, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testHearingAidServiceDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        serviceListenerUnderTest.onServiceDisconnected(BluetoothProfile.HEARING_AID);

        verify(mRouteManager).onActiveDeviceChanged(isNull(),
                eq(BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        verify(mRouteManager).onDeviceLost(device2.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device1.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device3.getAddress());
        assertNull(mBluetoothDeviceManager.getBluetoothHearingAid());
        assertEquals(2, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testLeAudioServiceDisconnect() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device3,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothLeAudio.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        serviceListenerUnderTest.onServiceDisconnected(BluetoothProfile.LE_AUDIO);

        verify(mRouteManager).onActiveDeviceChanged(isNull(),
                eq(BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        verify(mRouteManager).onDeviceLost(device5.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device1.getAddress());
        verify(mRouteManager, never()).onDeviceLost(device3.getAddress());
        assertNull(mBluetoothDeviceManager.getLeAudioService());
        assertEquals(2, mBluetoothDeviceManager.getNumConnectedDevices());
    }

    @SmallTest
    @Test
    public void testHearingAidChangesIgnoredWhenNotInCall() {
        receiverUnderTest.setIsInCall(false);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device2,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        Intent activeDeviceChangedIntent =
                new Intent(BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED);
        activeDeviceChangedIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, device2);
        receiverUnderTest.onReceive(mContext, activeDeviceChangedIntent);

        verify(mRouteManager).onActiveDeviceChanged(device2,
                BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID);
        verify(mRouteManager, never()).sendMessage(BluetoothRouteManager.BT_AUDIO_IS_ON);
    }

    @SmallTest
    @Test
    public void testLeAudioGroupChangesIgnoredWhenNotInCall() {
        receiverUnderTest.setIsInCall(false);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothLeAudio.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        Intent activeDeviceChangedIntent =
                        new Intent(BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED);
        activeDeviceChangedIntent.putExtra(BluetoothDevice.EXTRA_DEVICE, device5);
        receiverUnderTest.onReceive(mContext, activeDeviceChangedIntent);

        verify(mRouteManager).onActiveDeviceChanged(device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO);
        verify(mRouteManager, never()).sendMessage(BluetoothRouteManager.BT_AUDIO_IS_ON);
    }

    @SmallTest
    @Test
    public void testConnectDisconnectAudioHeadset() {
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device1,
                        BluetoothDeviceManager.DEVICE_TYPE_HEADSET));
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                    eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);
        mBluetoothDeviceManager.connectAudio(device1.getAddress(), false);
        verify(mAdapter).setActiveDevice(device1, BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL);
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL));
        mBluetoothDeviceManager.disconnectAudio();
        verify(mBluetoothHeadset).disconnectAudio();
    }

    @SmallTest
    @Test
    public void testConnectDisconnectAudioHearingAid() {
        receiverUnderTest.setIsInCall(true);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);

        AudioDeviceInfo mockAudioDeviceInfo = mock(AudioDeviceInfo.class);
        when(mockAudioDeviceInfo.getType()).thenReturn(AudioDeviceInfo.TYPE_HEARING_AID);
        List<AudioDeviceInfo> devices = new ArrayList<>();
        devices.add(mockAudioDeviceInfo);

        when(mockAudioManager.getAvailableCommunicationDevices())
                .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(eq(mockAudioDeviceInfo)))
                .thenReturn(true);

        mBluetoothDeviceManager.connectAudio(device5.getAddress(), false);
        verify(mAdapter).setActiveDevice(device5, BluetoothAdapter.ACTIVE_DEVICE_ALL);
        verify(mBluetoothHeadset, never()).connectAudio();
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));

        receiverUnderTest.onReceive(mContext, buildActiveDeviceChangeActionIntent(device5,
                BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID));

        when(mockAudioManager.getCommunicationDevice()).thenReturn(mockAudioDeviceInfo);
        mBluetoothDeviceManager.disconnectAudio();
        verify(mockAudioManager).clearCommunicationDevice();
    }

    @SmallTest
    @Test
    public void testConnectDisconnectAudioLeAudio() {
        receiverUnderTest.setIsInCall(true);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);

        AudioDeviceInfo mockAudioDeviceInfo = mock(AudioDeviceInfo.class);
        when(mockAudioDeviceInfo.getType()).thenReturn(AudioDeviceInfo.TYPE_BLE_HEADSET);
        List<AudioDeviceInfo> devices = new ArrayList<>();
        devices.add(mockAudioDeviceInfo);

        when(mockAudioManager.getAvailableCommunicationDevices())
                        .thenReturn(devices);
        when(mockAudioManager.setCommunicationDevice(mockAudioDeviceInfo))
                       .thenReturn(true);

        mBluetoothDeviceManager.connectAudio(device5.getAddress(), false);
        verify(mAdapter).setActiveDevice(device5, BluetoothAdapter.ACTIVE_DEVICE_ALL);
        verify(mBluetoothHeadset, never()).connectAudio();
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));

        receiverUnderTest.onReceive(mContext, buildActiveDeviceChangeActionIntent(device5,
                BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));

        mBluetoothDeviceManager.disconnectAudio();
        verify(mockAudioManager).clearCommunicationDevice();
    }

    @SmallTest
    @Test
    public void testConnectEarbudLeAudio() {
        receiverUnderTest.setIsInCall(true);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_CONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device5, 1);
        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothLeAudio.STATE_CONNECTED, device6,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));
        leAudioCallbacksTest.getValue().onGroupNodeAdded(device6, 1);
        when(mAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);
        mBluetoothDeviceManager.connectAudio(device5.getAddress(), false);
        verify(mAdapter).setActiveDevice(device5, BluetoothAdapter.ACTIVE_DEVICE_ALL);
        verify(mBluetoothHeadset, never()).connectAudio();
        verify(mAdapter, never()).setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_PHONE_CALL));

        when(mAdapter.getActiveDevices(eq(BluetoothProfile.LE_AUDIO)))
                .thenReturn(Arrays.asList(device5, device6));

        receiverUnderTest.onReceive(mContext,
                buildConnectionActionIntent(BluetoothHeadset.STATE_DISCONNECTED, device5,
                        BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO));

        mBluetoothDeviceManager.connectAudio(device6.getAddress(), false);
        verify(mAdapter).setActiveDevice(device6, BluetoothAdapter.ACTIVE_DEVICE_ALL);
    }

    @SmallTest
    @Test
    public void testClearHearingAidCommunicationDevice() {
        AudioDeviceInfo mockAudioDeviceInfo = mock(AudioDeviceInfo.class);
        when(mockAudioDeviceInfo.getAddress()).thenReturn(DEVICE_ADDRESS_1);
        when(mockAudioDeviceInfo.getType()).thenReturn(AudioDeviceInfo.TYPE_HEARING_AID);
        List<AudioDeviceInfo> devices = new ArrayList<>();
        devices.add(mockAudioDeviceInfo);

        when(mockAudioManager.getAvailableCommunicationDevices())
                .thenReturn(devices);

        mBluetoothDeviceManager.setHearingAidCommunicationDevice();
        when(mockAudioManager.getCommunicationDevice()).thenReturn(null);
        mBluetoothDeviceManager.clearHearingAidCommunicationDevice();
        verify(mRouteManager).onAudioLost(eq(DEVICE_ADDRESS_1));
        assertFalse(mBluetoothDeviceManager.isHearingAidSetAsCommunicationDevice());
    }

    private Intent buildConnectionActionIntent(int state, BluetoothDevice device, int deviceType) {
        String intentString;

        switch (deviceType) {
            case BluetoothDeviceManager.DEVICE_TYPE_HEADSET:
                intentString = BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED;
                break;
            case BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID:
                intentString = BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED;
                break;
            case BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO:
                intentString = BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED;
                break;
            default:
                return null;
        }

        Intent i = new Intent(intentString);
        i.putExtra(BluetoothHeadset.EXTRA_STATE, state);
        i.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        return i;
    }


    private Intent buildActiveDeviceChangeActionIntent(BluetoothDevice device, int deviceType) {
        String intentString;

        switch (deviceType) {
            case BluetoothDeviceManager.DEVICE_TYPE_HEADSET:
                intentString = BluetoothHeadset.ACTION_ACTIVE_DEVICE_CHANGED;
                break;
            case BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID:
                intentString = BluetoothHearingAid.ACTION_ACTIVE_DEVICE_CHANGED;
                break;
            case BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO:
                intentString = BluetoothLeAudio.ACTION_LE_AUDIO_ACTIVE_DEVICE_CHANGED;
                break;
            default:
                return null;
        }

        Intent i = new Intent(intentString);
        i.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        i.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                | Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
        return i;
    }

    private BluetoothDevice makeBluetoothDevice(String address) {
        Parcel p1 = Parcel.obtain();
        p1.writeString(address);
        p1.setDataPosition(0);
        BluetoothDevice device = BluetoothDevice.CREATOR.createFromParcel(p1);
        p1.recycle();
        return device;
    }
}
