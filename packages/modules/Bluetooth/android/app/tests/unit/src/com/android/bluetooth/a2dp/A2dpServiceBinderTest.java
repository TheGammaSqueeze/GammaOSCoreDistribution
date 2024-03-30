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

package com.android.bluetooth.a2dp;

import static android.bluetooth.BluetoothCodecConfig.SOURCE_CODEC_TYPE_INVALID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doReturn;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BufferConstraints;
import android.content.AttributionSource;

import androidx.test.InstrumentationRegistry;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class A2dpServiceBinderTest {
    @Mock private A2dpService mService;

    private A2dpService.BluetoothA2dpBinder mBinder;
    private BluetoothAdapter mAdapter;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mBinder = new A2dpService.BluetoothA2dpBinder(mService);
        doReturn(InstrumentationRegistry.getTargetContext().getPackageManager())
                .when(mService).getPackageManager();
    }

    @After
    public void cleaUp() {
        mBinder.cleanup();
    }

    @Test
    public void connect() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.connect(device, recv);
        verify(mService).connect(device);
    }

    @Test
    public void connectWithAttribution() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.connectWithAttribution(device, source, recv);
        verify(mService).connect(device);
    }

    @Test
    public void disconnect() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.disconnect(device, recv);
        verify(mService).disconnect(device);
    }

    @Test
    public void disconnectWithAttribution() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.disconnectWithAttribution(device, source, recv);
        verify(mService).disconnect(device);
    }

    @Test
    public void getConnectedDevices() {
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getConnectedDevices(recv);
        verify(mService).getConnectedDevices();
    }

    @Test
    public void getConnectedDevicesWithAttribution() {
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getConnectedDevicesWithAttribution(source, recv);
        verify(mService).getConnectedDevices();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        int[] states = new int[] {BluetoothProfile.STATE_CONNECTED };
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getDevicesMatchingConnectionStates(states, recv);
        verify(mService).getDevicesMatchingConnectionStates(states);
    }

    @Test
    public void getDevicesMatchingConnectionStatesWithAttribution() {
        int[] states = new int[] {BluetoothProfile.STATE_CONNECTED };
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getDevicesMatchingConnectionStatesWithAttribution(states, source, recv);
        verify(mService).getDevicesMatchingConnectionStates(states);
    }

    @Test
    public void getConnectionState() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getConnectionState(device, recv);
        verify(mService).getConnectionState(device);
    }

    @Test
    public void getConnectionStateWithAttribution() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getConnectionStateWithAttribution(device, source, recv);
        verify(mService).getConnectionState(device);
    }

    @Test
    public void setActiveDevice() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.setActiveDevice(device, source, recv);
        verify(mService).setActiveDevice(device);
    }

    @Test
    public void getActiveDevice() {
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<BluetoothDevice> recv = SynchronousResultReceiver.get();

        mBinder.getActiveDevice(source, recv);
        verify(mService).getActiveDevice();
    }

    @Test
    public void setConnectionPolicy() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.setConnectionPolicy(device, connectionPolicy, source, recv);
        verify(mService).setConnectionPolicy(device, connectionPolicy);
    }

    @Test
    public void getConnectionPolicy() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.getConnectionPolicy(device, source, recv);
        verify(mService).getConnectionPolicy(device);
    }

    @Test
    public void setAvrcpAbsoluteVolume() {
        int volume = 3;
        AttributionSource source = new AttributionSource.Builder(0).build();

        mBinder.setAvrcpAbsoluteVolume(volume, source);
        verify(mService).setAvrcpAbsoluteVolume(volume);
    }

    @Test
    public void isA2dpPlaying() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.isA2dpPlaying(device, source, recv);
        verify(mService).isA2dpPlaying(device);
    }

    @Test
    public void getCodecStatus() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<BluetoothCodecStatus> recv =
                SynchronousResultReceiver.get();

        mBinder.getCodecStatus(device, source, recv);
        verify(mService).getCodecStatus(device);
    }

    @Test
    public void setCodecConfigPreference() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        BluetoothCodecConfig config = new BluetoothCodecConfig(SOURCE_CODEC_TYPE_INVALID);
        AttributionSource source = new AttributionSource.Builder(0).build();

        mBinder.setCodecConfigPreference(device, config, source);
        verify(mService).setCodecConfigPreference(device, config);
    }

    @Test
    public void enableOptionalCodecs() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();

        mBinder.enableOptionalCodecs(device, source);
        verify(mService).enableOptionalCodecs(device);
    }

    @Test
    public void disableOptionalCodecs() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();

        mBinder.disableOptionalCodecs(device, source);
        verify(mService).disableOptionalCodecs(device);
    }

    @Test
    public void isOptionalCodecsSupported() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.isOptionalCodecsSupported(device, source, recv);
        verify(mService).getSupportsOptionalCodecs(device);
    }

    @Test
    public void isOptionalCodecsEnabled() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.isOptionalCodecsEnabled(device, source, recv);
        verify(mService).getOptionalCodecsEnabled(device);
    }

    @Test
    public void setOptionalCodecsEnabled() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        int value = BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;
        AttributionSource source = new AttributionSource.Builder(0).build();

        mBinder.setOptionalCodecsEnabled(device, value, source);
        verify(mService).setOptionalCodecsEnabled(device, value);
    }

    @Test
    public void getDynamicBufferSupport() {
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.getDynamicBufferSupport(source, recv);
        verify(mService).getDynamicBufferSupport();
    }

    @Test
    public void getBufferConstraints() {
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<BufferConstraints> recv = SynchronousResultReceiver.get();

        mBinder.getBufferConstraints(source, recv);
        verify(mService).getBufferConstraints();
    }

    @Test
    public void setBufferLengthMillis() {
        int codec = 0;
        int value = BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN;
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.setBufferLengthMillis(codec, value, source, recv);
        verify(mService).setBufferLengthMillis(codec, value);
    }
}
