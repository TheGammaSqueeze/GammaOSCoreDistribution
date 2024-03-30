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
import android.bluetooth.BluetoothStatusCodes;
import android.content.ContentResolver;
import android.os.Parcel;
import android.telecom.Log;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.os.SomeArgs;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;
import com.android.server.telecom.bluetooth.BluetoothDeviceManager;
import com.android.server.telecom.bluetooth.BluetoothRouteManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class BluetoothRouteManagerTest extends TelecomTestCase {
    private static final int TEST_TIMEOUT = 1000;
    static final BluetoothDevice DEVICE1 = makeBluetoothDevice("00:00:00:00:00:01");
    static final BluetoothDevice DEVICE2 = makeBluetoothDevice("00:00:00:00:00:02");
    static final BluetoothDevice DEVICE3 = makeBluetoothDevice("00:00:00:00:00:03");
    static final BluetoothDevice HEARING_AID_DEVICE = makeBluetoothDevice("00:00:00:00:00:04");

    @Mock private BluetoothAdapter mBluetoothAdapter;
    @Mock private BluetoothDeviceManager mDeviceManager;
    @Mock private BluetoothHeadset mBluetoothHeadset;
    @Mock private BluetoothHearingAid mBluetoothHearingAid;
    @Mock private BluetoothLeAudio mBluetoothLeAudio;
    @Mock private Timeouts.Adapter mTimeoutsAdapter;
    @Mock private BluetoothRouteManager.BluetoothStateListener mListener;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SmallTest
    @Test
    public void testConnectBtRetryWhileNotConnected() {
        BluetoothRouteManager sm = setupStateMachine(
                BluetoothRouteManager.AUDIO_OFF_STATE_NAME, null);
        setupConnectedDevices(new BluetoothDevice[]{DEVICE1}, null, null, null, null, null);
        when(mTimeoutsAdapter.getRetryBluetoothConnectAudioBackoffMillis(
                nullable(ContentResolver.class))).thenReturn(0L);
        when(mBluetoothHeadset.connectAudio()).thenReturn(BluetoothStatusCodes.ERROR_UNKNOWN);
        executeRoutingAction(sm, BluetoothRouteManager.CONNECT_BT, DEVICE1.getAddress());
        // Wait 3 times: for the first connection attempt, the retry attempt,
        // the second retry, and once more to make sure there are only three attempts.
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        verifyConnectionAttempt(DEVICE1, 3);
        assertEquals(BluetoothRouteManager.AUDIO_OFF_STATE_NAME, sm.getCurrentState().getName());
        sm.getHandler().removeMessages(BluetoothRouteManager.CONNECTION_TIMEOUT);
        sm.quitNow();
    }

    @SmallTest
    @Test
    public void testAmbiguousActiveDevice() {
        BluetoothRouteManager sm = setupStateMachine(
                BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX, DEVICE1);
        setupConnectedDevices(new BluetoothDevice[]{DEVICE1},
                new BluetoothDevice[]{HEARING_AID_DEVICE}, new BluetoothDevice[]{DEVICE2},
                DEVICE1, HEARING_AID_DEVICE, DEVICE2);
        sm.onActiveDeviceChanged(DEVICE1, BluetoothDeviceManager.DEVICE_TYPE_HEADSET);
        sm.onActiveDeviceChanged(DEVICE2, BluetoothDeviceManager.DEVICE_TYPE_LE_AUDIO);
        sm.onActiveDeviceChanged(HEARING_AID_DEVICE,
                BluetoothDeviceManager.DEVICE_TYPE_HEARING_AID);
        executeRoutingAction(sm, BluetoothRouteManager.BT_AUDIO_LOST, DEVICE1.getAddress());

        verifyConnectionAttempt(HEARING_AID_DEVICE, 0);
        verifyConnectionAttempt(DEVICE1, 0);
        verifyConnectionAttempt(DEVICE2, 0);
        assertEquals(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX
                        + ":" + DEVICE1.getAddress(),
                sm.getCurrentState().getName());
        sm.quitNow();
    }

    @SmallTest
    @Test
    public void testAudioOnDeviceWithScoOffActiveDevice() {
        BluetoothRouteManager sm = setupStateMachine(
                BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX, DEVICE1);
        setupConnectedDevices(new BluetoothDevice[]{DEVICE1}, null, null, DEVICE1, null, null);
        when(mBluetoothHeadset.getAudioState(DEVICE1))
                .thenReturn(BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
        executeRoutingAction(sm, BluetoothRouteManager.BT_AUDIO_LOST, DEVICE1.getAddress());

        verifyConnectionAttempt(DEVICE1, 0);
        assertEquals(BluetoothRouteManager.AUDIO_OFF_STATE_NAME,
                sm.getCurrentState().getName());
        sm.quitNow();
    }

    @SmallTest
    @Test
    public void testConnectBtRetryWhileConnectedToAnotherDevice() {
        BluetoothRouteManager sm = setupStateMachine(
                BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX, DEVICE1);
        setupConnectedDevices(new BluetoothDevice[]{DEVICE1, DEVICE2}, null, null, null, null,
                              null);
        when(mTimeoutsAdapter.getRetryBluetoothConnectAudioBackoffMillis(
                nullable(ContentResolver.class))).thenReturn(0L);
        when(mBluetoothHeadset.connectAudio()).thenReturn(BluetoothStatusCodes.ERROR_UNKNOWN);
        executeRoutingAction(sm, BluetoothRouteManager.CONNECT_BT, DEVICE2.getAddress());
        // Wait 3 times: the first connection attempt is accounted for in executeRoutingAction,
        // so wait twice for the retry attempt, again to make sure there are only three attempts,
        // and once more for good luck.
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        verifyConnectionAttempt(DEVICE2, 3);
        assertEquals(BluetoothRouteManager.AUDIO_CONNECTED_STATE_NAME_PREFIX
                        + ":" + DEVICE1.getAddress(),
                sm.getCurrentState().getName());
        sm.getHandler().removeMessages(BluetoothRouteManager.CONNECTION_TIMEOUT);
        sm.quitNow();
    }

    private BluetoothRouteManager setupStateMachine(String initialState,
            BluetoothDevice initialDevice) {
        resetMocks();
        BluetoothRouteManager sm = new BluetoothRouteManager(mContext,
                new TelecomSystem.SyncRoot() { }, mDeviceManager, mTimeoutsAdapter);
        sm.setListener(mListener);
        sm.setInitialStateForTesting(initialState, initialDevice);
        waitForHandlerAction(sm.getHandler(), TEST_TIMEOUT);
        resetMocks();
        return sm;
    }

    private void setupConnectedDevices(BluetoothDevice[] hfpDevices,
            BluetoothDevice[] hearingAidDevices, BluetoothDevice[] leAudioDevices,
            BluetoothDevice hfpActiveDevice, BluetoothDevice hearingAidActiveDevice,
            BluetoothDevice leAudioDevice) {
        if (hfpDevices == null) hfpDevices = new BluetoothDevice[]{};
        if (hearingAidDevices == null) hearingAidDevices = new BluetoothDevice[]{};
        if (leAudioDevice == null) leAudioDevices = new BluetoothDevice[]{};

        when(mDeviceManager.getNumConnectedDevices()).thenReturn(
                hfpDevices.length + hearingAidDevices.length + leAudioDevices.length);
        List<BluetoothDevice> allDevices = Stream.of(
                Arrays.stream(hfpDevices), Arrays.stream(hearingAidDevices),
                Arrays.stream(leAudioDevices)).flatMap(i -> i).collect(Collectors.toList());

        when(mDeviceManager.getConnectedDevices()).thenReturn(allDevices);
        when(mBluetoothHeadset.getConnectedDevices()).thenReturn(Arrays.asList(hfpDevices));
        when(mBluetoothAdapter.getActiveDevices(eq(BluetoothProfile.HEADSET)))
                .thenReturn(Arrays.asList(hfpActiveDevice));
        when(mBluetoothHeadset.getAudioState(hfpActiveDevice))
                .thenReturn(BluetoothHeadset.STATE_AUDIO_CONNECTED);

        when(mBluetoothHearingAid.getConnectedDevices())
                .thenReturn(Arrays.asList(hearingAidDevices));
        when(mBluetoothAdapter.getActiveDevices(eq(BluetoothProfile.HEARING_AID)))
                .thenReturn(Arrays.asList(hearingAidActiveDevice, null));
        when(mBluetoothAdapter.getActiveDevices(eq(BluetoothProfile.LE_AUDIO)))
                .thenReturn(Arrays.asList(leAudioDevice, null));
    }

    static void executeRoutingAction(BluetoothRouteManager brm, int message, String
            device) {
        SomeArgs args = SomeArgs.obtain();
        args.arg1 = Log.createSubsession();
        args.arg2 = device;
        brm.sendMessage(message, args);
        waitForHandlerAction(brm.getHandler(), TEST_TIMEOUT);
    }

    public static BluetoothDevice makeBluetoothDevice(String address) {
        Parcel p1 = Parcel.obtain();
        p1.writeString(address);
        p1.setDataPosition(0);
        BluetoothDevice device = BluetoothDevice.CREATOR.createFromParcel(p1);
        p1.recycle();
        return device;
    }

    private void resetMocks() {
        reset(mDeviceManager, mListener, mBluetoothHeadset, mTimeoutsAdapter);
        when(mDeviceManager.getBluetoothHeadset()).thenReturn(mBluetoothHeadset);
        when(mDeviceManager.getBluetoothHearingAid()).thenReturn(mBluetoothHearingAid);
        when(mDeviceManager.getBluetoothAdapter()).thenReturn(mBluetoothAdapter);
        when(mDeviceManager.getLeAudioService()).thenReturn(mBluetoothLeAudio);
        when(mBluetoothHeadset.connectAudio()).thenReturn(BluetoothStatusCodes.SUCCESS);
        when(mBluetoothAdapter.setActiveDevice(nullable(BluetoothDevice.class),
                eq(BluetoothAdapter.ACTIVE_DEVICE_ALL))).thenReturn(true);
        when(mTimeoutsAdapter.getRetryBluetoothConnectAudioBackoffMillis(
                nullable(ContentResolver.class))).thenReturn(100000L);
        when(mTimeoutsAdapter.getBluetoothPendingTimeoutMillis(
                nullable(ContentResolver.class))).thenReturn(100000L);
    }

    private void verifyConnectionAttempt(BluetoothDevice device, int numTimes) {
        verify(mDeviceManager, times(numTimes)).connectAudio(eq(device.getAddress()),
            anyBoolean());
    }
}
