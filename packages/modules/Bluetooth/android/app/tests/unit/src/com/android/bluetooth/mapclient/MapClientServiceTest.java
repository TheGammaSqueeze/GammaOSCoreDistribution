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
package com.android.bluetooth.mapclient;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Intent;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class MapClientServiceTest {
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    @Rule public final ServiceTestRule mServiceRule = new ServiceTestRule();

    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;

    private MapClientService mService = null;
    private BluetoothAdapter mAdapter = null;
    private BluetoothDevice mRemoteDevice;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("Ignore test when MapClientService is not enabled",
                MapClientService.isEnabled());
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        doReturn(true, false).when(mAdapterService).isStartedProfile(anyString());
        TestUtils.startService(mServiceRule, MapClientService.class);
        mService = MapClientService.getMapClientService();
        assertThat(mService).isNotNull();
        // Try getting the Bluetooth adapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertThat(mAdapter).isNotNull();
        mRemoteDevice = mAdapter.getRemoteDevice(REMOTE_DEVICE_ADDRESS);
    }

    @After
    public void tearDown() throws Exception {
        if (!MapClientService.isEnabled()) {
            return;
        }
        TestUtils.stopService(mServiceRule, MapClientService.class);
        mService = MapClientService.getMapClientService();
        assertThat(mService).isNull();
        TestUtils.clearAdapterService(mAdapterService);
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void initialize() {
        assertThat(MapClientService.getMapClientService()).isNotNull();
    }

    @Test
    public void setMapClientService_withNull() {
        MapClientService.setMapClientService(null);

        assertThat(MapClientService.getMapClientService()).isNull();
    }

    @Test
    public void dump_callsStateMachineDump() {
        MceStateMachine sm = mock(MceStateMachine.class);
        mService.getInstanceMap().put(mRemoteDevice, sm);
        StringBuilder builder = new StringBuilder();

        mService.dump(builder);

        verify(sm).dump(builder);
    }

    @Test
    public void setConnectionPolicy() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
        when(mDatabaseManager.setProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.MAP_CLIENT, connectionPolicy)).thenReturn(true);

        assertThat(mService.setConnectionPolicy(mRemoteDevice, connectionPolicy)).isTrue();
    }

    @Test
    public void getConnectionPolicy() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        when(mDatabaseManager.getProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.MAP_CLIENT)).thenReturn(connectionPolicy);

        assertThat(mService.getConnectionPolicy(mRemoteDevice)).isEqualTo(connectionPolicy);
    }

    @Test
    public void connect_whenPolicyIsForbidden_returnsFalse() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
        when(mDatabaseManager.getProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.MAP_CLIENT)).thenReturn(connectionPolicy);

        assertThat(mService.connect(mRemoteDevice)).isFalse();
    }

    @Test
    public void connect_whenPolicyIsAllowed_returnsTrue() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        when(mDatabaseManager.getProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.MAP_CLIENT)).thenReturn(connectionPolicy);

        assertThat(mService.connect(mRemoteDevice)).isTrue();
    }

    @Test
    public void disconnect_whenNotConnected_returnsFalse() {
        assertThat(mService.disconnect(mRemoteDevice)).isFalse();
    }

    @Test
    public void disconnect_whenConnected_returnsTrue() {
        int connectionState = BluetoothProfile.STATE_CONNECTED;
        MceStateMachine sm = mock(MceStateMachine.class);
        when(sm.getState()).thenReturn(connectionState);
        mService.getInstanceMap().put(mRemoteDevice, sm);

        assertThat(mService.disconnect(mRemoteDevice)).isTrue();

        verify(sm).disconnect();
    }

    @Test
    public void getConnectionState_whenNotConnected() {
        assertThat(mService.getConnectionState(mRemoteDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void getConnectionState_whenConnected() {
        int connectionState = BluetoothProfile.STATE_CONNECTED;
        MceStateMachine sm = mock(MceStateMachine.class);
        when(sm.getState()).thenReturn(connectionState);
        mService.getInstanceMap().put(mRemoteDevice, sm);

        assertThat(mService.getConnectionState(mRemoteDevice)).isEqualTo(connectionState);
    }

    @Test
    public void getConnectedDevices() {
        int connectionState = BluetoothProfile.STATE_CONNECTED;
        MceStateMachine sm = mock(MceStateMachine.class);
        BluetoothDevice[] bondedDevices = new BluetoothDevice[] {mRemoteDevice};
        when(mAdapterService.getBondedDevices()).thenReturn(bondedDevices);
        mService.getInstanceMap().put(mRemoteDevice, sm);
        when(sm.getState()).thenReturn(connectionState);

        assertThat(mService.getConnectedDevices()).contains(mRemoteDevice);
    }

    @Test
    public void getMceStateMachineForDevice() {
        MceStateMachine sm = mock(MceStateMachine.class);
        mService.getInstanceMap().put(mRemoteDevice, sm);

        assertThat(mService.getMceStateMachineForDevice(mRemoteDevice)).isEqualTo(sm);
    }

    @Test
    public void getSupportedFeatures() {
        int supportedFeatures = 100;
        MceStateMachine sm = mock(MceStateMachine.class);
        mService.getInstanceMap().put(mRemoteDevice, sm);
        when(sm.getSupportedFeatures()).thenReturn(supportedFeatures);

        assertThat(mService.getSupportedFeatures(mRemoteDevice)).isEqualTo(supportedFeatures);
        verify(sm).getSupportedFeatures();
    }

    @Test
    public void setMessageStatus() {
        String handle = "FFAB";
        int status = 123;
        MceStateMachine sm = mock(MceStateMachine.class);
        mService.getInstanceMap().put(mRemoteDevice, sm);
        when(sm.setMessageStatus(handle, status)).thenReturn(true);

        assertThat(mService.setMessageStatus(mRemoteDevice, handle, status)).isTrue();
        verify(sm).setMessageStatus(handle, status);
    }

    @Test
    public void getUnreadMessages() {
        MceStateMachine sm = mock(MceStateMachine.class);
        mService.getInstanceMap().put(mRemoteDevice, sm);
        when(sm.getUnreadMessages()).thenReturn(true);

        assertThat(mService.getUnreadMessages(mRemoteDevice)).isTrue();
        verify(sm).getUnreadMessages();
    }

    @Test
    public void cleanUpDevice() {
        MceStateMachine sm = mock(MceStateMachine.class);
        mService.getInstanceMap().put(mRemoteDevice, sm);

        mService.cleanupDevice(mRemoteDevice);

        assertThat(mService.getInstanceMap()).doesNotContainKey(mRemoteDevice);
    }

    @Test
    public void broadcastReceiver_withRandomAction_doesNothing() {
        MceStateMachine sm = mock(MceStateMachine.class);
        mService.getInstanceMap().put(mRemoteDevice, sm);

        Intent intent = new Intent("Test_random_action");
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
        mService.mMapReceiver.onReceive(mService, intent);

        verify(sm, never()).disconnect();
    }

    @Test
    public void broadcastReceiver_withActionAclDisconnected_withoutDevice_doesNothing() {
        int connectionState = BluetoothProfile.STATE_CONNECTED;
        MceStateMachine sm = mock(MceStateMachine.class);
        mService.getInstanceMap().put(mRemoteDevice, sm);
        when(sm.getState()).thenReturn(connectionState);

        Intent intent = new Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        // Device is not included in this intent
        mService.mMapReceiver.onReceive(mService, intent);

        verify(sm, never()).disconnect();
    }

    @Test
    public void broadcastReceiver_withActionAclDisconnected_whenNotConnected_doesNothing() {
        // No state machine exists for this device
        Intent intent = new Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
        mService.mMapReceiver.onReceive(mService, intent);
    }

    @Test
    public void broadcastReceiver_withActionAclDisconnected_whenConnected_callsDisconnect() {
        int connectionState = BluetoothProfile.STATE_CONNECTED;
        MceStateMachine sm = mock(MceStateMachine.class);
        mService.getInstanceMap().put(mRemoteDevice, sm);
        when(sm.getState()).thenReturn(connectionState);

        Intent intent = new Intent(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
        mService.mMapReceiver.onReceive(mService, intent);

        verify(sm).disconnect();
    }

    @Test
    public void broadcastReceiver_withActionSdpRecord_withoutMasRecord_doesNothing() {
        MceStateMachine sm = mock(MceStateMachine.class);
        mService.getInstanceMap().put(mRemoteDevice, sm);

        Intent intent = new Intent(BluetoothDevice.ACTION_SDP_RECORD);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
        intent.putExtra(BluetoothDevice.EXTRA_UUID, BluetoothUuid.MAS);
        // No MasRecord / searchStatus is included in this intent
        mService.mMapReceiver.onReceive(mService, intent);
    }
}
