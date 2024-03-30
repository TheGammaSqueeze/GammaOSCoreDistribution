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

package com.android.bluetooth.le_audio;

import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothLeAudioCodecStatus;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothLeAudioCallback;
import android.bluetooth.IBluetoothLeBroadcastCallback;
import android.content.AttributionSource;
import android.os.ParcelUuid;
import android.os.RemoteCallbackList;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

public class LeAudioBinderTest {
    @Mock
    private LeAudioService mMockService;
    @Mock
    private RemoteCallbackList<IBluetoothLeAudioCallback> mLeAudioCallbacks;
    @Mock
    private RemoteCallbackList<IBluetoothLeBroadcastCallback> mBroadcastCallbacks;

    private LeAudioService.BluetoothLeAudioBinder mBinder;
    private BluetoothAdapter mAdapter;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mBinder = new LeAudioService.BluetoothLeAudioBinder(mMockService);
        mMockService.mLeAudioCallbacks = mLeAudioCallbacks;
        mMockService.mBroadcastCallbacks = mBroadcastCallbacks;
    }

    @After
    public void cleanUp() {
        mBinder.cleanup();
    }

    @Test
    public void connect() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.connect(device, source, recv);
        verify(mMockService).connect(device);
    }

    @Test
    public void disconnect() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.disconnect(device, source, recv);
        verify(mMockService).disconnect(device);
    }

    @Test
    public void getConnectedDevices() {
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getConnectedDevices(source, recv);
        verify(mMockService).getConnectedDevices();
    }

    @Test
    public void getConnectedGroupLeadDevice() {
        int groupId = 1;
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getConnectedGroupLeadDevice(groupId, source, recv);
        verify(mMockService).getConnectedGroupLeadDevice(groupId);
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        int[] states = new int[] {BluetoothProfile.STATE_DISCONNECTED };
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getDevicesMatchingConnectionStates(states, source, recv);
        verify(mMockService).getDevicesMatchingConnectionStates(states);
    }

    @Test
    public void getConnectionState() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.getConnectionState(device, source, recv);
        verify(mMockService).getConnectionState(device);
    }

    @Test
    public void setActiveDevice() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.setActiveDevice(device, source, recv);
        verify(mMockService).setActiveDevice(device);
    }

    @Test
    public void getActiveDevices() {
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.getActiveDevices(source, recv);
        verify(mMockService).getActiveDevices();
    }

    @Test
    public void getAudioLocation() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.getAudioLocation(device, source, recv);
        verify(mMockService).getAudioLocation(device);
    }

    @Test
    public void setConnectionPolicy() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.setConnectionPolicy(device, connectionPolicy, source, recv);
        verify(mMockService).setConnectionPolicy(device, connectionPolicy);
    }

    @Test
    public void getConnectionPolicy() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.getConnectionPolicy(device, source, recv);
        verify(mMockService).getConnectionPolicy(device);
    }

    @Test
    public void setCcidInformation() {
        ParcelUuid uuid = new ParcelUuid(new UUID(0, 0));
        int ccid = 0;
        int contextType = BluetoothLeAudio.CONTEXT_TYPE_UNSPECIFIED;
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();

        mBinder.setCcidInformation(uuid, ccid, contextType, source, recv);
        verify(mMockService).setCcidInformation(uuid, ccid, contextType);
    }

    @Test
    public void getGroupId() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.getGroupId(device, source, recv);
        verify(mMockService).getGroupId(device);
    }

    @Test
    public void groupAddNode() {
        int groupId = 1;
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.groupAddNode(groupId, device, source, recv);
        verify(mMockService).groupAddNode(groupId, device);
    }

    @Test
    public void setInCall() {
        boolean inCall = true;
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();

        mBinder.setInCall(inCall, source, recv);
        verify(mMockService).setInCall(inCall);
    }

    @Test
    public void setInactiveForHfpHandover() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();

        mBinder.setInactiveForHfpHandover(device, source, recv);
        verify(mMockService).setInactiveForHfpHandover(device);
    }

    @Test
    public void groupRemoveNode() {
        int groupId = 1;
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.groupRemoveNode(groupId, device, source, recv);
        verify(mMockService).groupRemoveNode(groupId, device);
    }

    @Test
    public void setVolume() {
        int volume = 3;
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();

        mBinder.setVolume(volume, source, recv);
        verify(mMockService).setVolume(volume);
    }

    @Test
    public void registerCallback() {
        IBluetoothLeAudioCallback callback = Mockito.mock(IBluetoothLeAudioCallback.class);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();

        mBinder.registerCallback(callback, source, recv);
        verify(mMockService.mLeAudioCallbacks).register(callback);
    }

    @Test
    public void unregisterCallback() {
        IBluetoothLeAudioCallback callback = Mockito.mock(IBluetoothLeAudioCallback.class);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();

        mBinder.unregisterCallback(callback, source, recv);
        verify(mMockService.mLeAudioCallbacks).unregister(callback);
    }

    @Test
    public void registerLeBroadcastCallback() {
        IBluetoothLeBroadcastCallback callback = Mockito.mock(IBluetoothLeBroadcastCallback.class);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();

        mBinder.registerLeBroadcastCallback(callback, source, recv);
        verify(mMockService.mBroadcastCallbacks).register(callback);
    }

    @Test
    public void unregisterLeBroadcastCallback() {
        IBluetoothLeBroadcastCallback callback = Mockito.mock(IBluetoothLeBroadcastCallback.class);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();

        mBinder.unregisterLeBroadcastCallback(callback, source, recv);
        verify(mMockService.mBroadcastCallbacks).unregister(callback);
    }

    @Test
    public void startBroadcast() {
        BluetoothLeAudioContentMetadata metadata =
                new BluetoothLeAudioContentMetadata.Builder().build();
        byte[] code = new byte[] { 0x00 };
        AttributionSource source = new AttributionSource.Builder(0).build();

        mBinder.startBroadcast(metadata, code, source);
        verify(mMockService).createBroadcast(metadata, code);
    }

    @Test
    public void stopBroadcast() {
        int id = 1;
        AttributionSource source = new AttributionSource.Builder(0).build();

        mBinder.stopBroadcast(id, source);
        verify(mMockService).stopBroadcast(id);
    }

    @Test
    public void updateBroadcast() {
        int id = 1;
        BluetoothLeAudioContentMetadata metadata =
                new BluetoothLeAudioContentMetadata.Builder().build();
        AttributionSource source = new AttributionSource.Builder(0).build();

        mBinder.updateBroadcast(id, metadata, source);
        verify(mMockService).updateBroadcast(id, metadata);
    }

    @Test
    public void isPlaying() {
        int id = 1;
        BluetoothLeAudioContentMetadata metadata =
                new BluetoothLeAudioContentMetadata.Builder().build();
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.isPlaying(id, source, recv);
        verify(mMockService).isPlaying(id);
    }

    @Test
    public void getAllBroadcastMetadata() {
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<List<BluetoothLeBroadcastMetadata>> recv =
                SynchronousResultReceiver.get();

        mBinder.getAllBroadcastMetadata(source, recv);
        verify(mMockService).getAllBroadcastMetadata();
    }

    @Test
    public void getMaximumNumberOfBroadcasts() {
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.getMaximumNumberOfBroadcasts(source, recv);
        verify(mMockService).getMaximumNumberOfBroadcasts();
    }

    @Test
    public void getCodecStatus() {
        int groupId = 1;
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<BluetoothLeAudioCodecStatus> recv =
                SynchronousResultReceiver.get();

        mBinder.getCodecStatus(groupId, source, recv);
        verify(mMockService).getCodecStatus(groupId);
    }

    @Test
    public void setCodecConfigPreference() {
        int groupId = 1;
        BluetoothLeAudioCodecConfig inputConfig =
                new BluetoothLeAudioCodecConfig.Builder().build();
        BluetoothLeAudioCodecConfig outputConfig =
                new BluetoothLeAudioCodecConfig.Builder().build();
        AttributionSource source = new AttributionSource.Builder(0).build();

        mBinder.setCodecConfigPreference(groupId, inputConfig, outputConfig, source);
        verify(mMockService).setCodecConfigPreference(groupId, inputConfig, outputConfig);
    }
}
