/*
 * Copyright 2021 The Android Open Source Project
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
package com.android.bluetooth.a2dpsink;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioFormat;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class A2dpSinkStateMachineTest {
    private Context mTargetContext;

    private BluetoothAdapter mAdapter;
    private BluetoothDevice mDevice;
    private final String mDeviceAddress = "11:11:11:11:11:11";
    @Mock private A2dpSinkService mService;
    @Mock private A2dpSinkNativeInterface mNativeInterface;

    A2dpSinkStateMachine mStateMachine;
    private static final int TIMEOUT_MS = 1000;
    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int UNHANDLED_MESSAGE = 9999;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertThat(mAdapter).isNotNull();
        mDevice = mAdapter.getRemoteDevice(mDeviceAddress);

        doNothing().when(mService).removeStateMachine(any(A2dpSinkStateMachine.class));

        mStateMachine = new A2dpSinkStateMachine(mDevice, mService, mNativeInterface);
        mStateMachine.start();
        assertThat(mStateMachine.getDevice()).isEqualTo(mDevice);
        assertThat(mStateMachine.getAudioConfig()).isNull();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @After
    public void tearDown() throws Exception {
        mStateMachine = null;
        mDevice = null;
        mAdapter = null;
    }

    private void mockDeviceConnectionPolicy(BluetoothDevice device, int policy) {
        doReturn(policy).when(mService).getConnectionPolicy(device);
    }

    private void sendConnectionEvent(int state) {
        mStateMachine.sendMessage(A2dpSinkStateMachine.STACK_EVENT,
                StackEvent.connectionStateChanged(mDevice, state));
    }

    private void sendAudioConfigChangedEvent(int sampleRate, int channelCount) {
        mStateMachine.sendMessage(A2dpSinkStateMachine.STACK_EVENT,
                StackEvent.audioConfigChanged(mDevice, sampleRate, channelCount));
    }

    /**********************************************************************************************
     * DISCONNECTED STATE TESTS                                                                   *
     *********************************************************************************************/

    @Test
    public void testConnectInDisconnected() {
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        mStateMachine.connect();
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        verify(mNativeInterface, timeout(TIMEOUT_MS).times(1)).connectA2dpSink(mDevice);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    @Test
    public void testDisconnectInDisconnected() {
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        mStateMachine.disconnect();
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testAudioConfigChangedInDisconnected() {
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        sendAudioConfigChangedEvent(44, 1);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mStateMachine.getAudioConfig()).isNull();
    }

    @Test
    public void testIncomingConnectedInDisconnected() {
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTED);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void testAllowedIncomingConnectionInDisconnected() {
        mockDeviceConnectionPolicy(mDevice, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        verify(mNativeInterface, times(0)).connectA2dpSink(mDevice);
    }

    @Test
    public void testForbiddenIncomingConnectionInDisconnected() {
        mockDeviceConnectionPolicy(mDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        verify(mNativeInterface, times(1)).disconnectA2dpSink(mDevice);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testUnknownIncomingConnectionInDisconnected() {
        mockDeviceConnectionPolicy(mDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        verify(mNativeInterface, times(0)).connectA2dpSink(mDevice);
    }

    @Test
    public void testIncomingDisconnectInDisconnected() {
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTED);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        verify(mService, timeout(TIMEOUT_MS).times(1)).removeStateMachine(mStateMachine);
    }

    @Test
    public void testIncomingDisconnectingInDisconnected() {
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTING);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        verify(mService, times(0)).removeStateMachine(mStateMachine);
    }

    @Test
    public void testIncomingConnectingInDisconnected() {
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testUnhandledMessageInDisconnected() {
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        mStateMachine.sendMessage(UNHANDLED_MESSAGE);
        mStateMachine.sendMessage(UNHANDLED_MESSAGE, 0 /* arbitrary payload */);
    }

    /**********************************************************************************************
     * CONNECTING STATE TESTS                                                                     *
     *********************************************************************************************/

    @Test
    public void testConnectedInConnecting() {
        testConnectInDisconnected();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTED);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void testConnectingInConnecting() {
        testConnectInDisconnected();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    @Test
    public void testDisconnectingInConnecting() {
        testConnectInDisconnected();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTING);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    @Test
    public void testDisconnectedInConnecting() {
        testConnectInDisconnected();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTED);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        verify(mService, timeout(TIMEOUT_MS).times(1)).removeStateMachine(mStateMachine);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testConnectionTimeoutInConnecting() {
        testConnectInDisconnected();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        verify(mService, timeout(CONNECT_TIMEOUT_MS).times(1)).removeStateMachine(mStateMachine);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testAudioStateChangeInConnecting() {
        testConnectInDisconnected();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        sendAudioConfigChangedEvent(44, 1);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        assertThat(mStateMachine.getAudioConfig()).isNull();
    }

    @Test
    public void testConnectInConnecting() {
        testConnectInDisconnected();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        mStateMachine.connect();
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    @Test
    public void testDisconnectInConnecting() {
        testConnectInDisconnected();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
        mStateMachine.disconnect();
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    /**********************************************************************************************
     * CONNECTED STATE TESTS                                                                      *
     *********************************************************************************************/

    @Test
    public void testConnectInConnected() {
        testConnectedInConnecting();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
        mStateMachine.connect();
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void testDisconnectInConnected() {
        testConnectedInConnecting();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
        mStateMachine.disconnect();
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        verify(mNativeInterface, times(1)).disconnectA2dpSink(mDevice);
        verify(mService, timeout(TIMEOUT_MS).times(1)).removeStateMachine(mStateMachine);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testAudioStateChangeInConnected() {
        testConnectedInConnecting();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
        sendAudioConfigChangedEvent(44, 1);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
        BluetoothAudioConfig expected =
                new BluetoothAudioConfig(44, 1, AudioFormat.ENCODING_PCM_16BIT);
        BluetoothAudioConfig config = mStateMachine.getAudioConfig();
        assertThat(config).isEqualTo(expected);
    }

    @Test
    public void testConnectedInConnected() {
        testConnectedInConnecting();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTED);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void testConnectingInConnected() {
        testConnectedInConnecting();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_CONNECTING);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    @Test
    public void testDisconnectingInConnected() {
        testConnectedInConnecting();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTING);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        verify(mService, timeout(TIMEOUT_MS).times(1)).removeStateMachine(mStateMachine);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void testDisconnectedInConnected() {
        testConnectedInConnecting();
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_CONNECTED);
        sendConnectionEvent(BluetoothProfile.STATE_DISCONNECTED);
        TestUtils.waitForLooperToFinishScheduledTask(mStateMachine.getHandler().getLooper());
        verify(mService, timeout(TIMEOUT_MS).times(1)).removeStateMachine(mStateMachine);
        assertThat(mStateMachine.getState()).isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    /**********************************************************************************************
     * OTHER TESTS                                                                                *
     *********************************************************************************************/

    @Test
    public void testDump() {
        StringBuilder sb = new StringBuilder();
        mStateMachine.dump(sb);
        assertThat(sb.toString()).isNotNull();
    }
}
