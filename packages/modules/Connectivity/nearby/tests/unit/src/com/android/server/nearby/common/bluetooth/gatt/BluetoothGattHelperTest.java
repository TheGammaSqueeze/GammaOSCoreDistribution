/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.nearby.common.bluetooth.gatt;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.ParcelUuid;
import android.test.mock.MockContext;

import androidx.test.filters.SdkSuppress;

import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattHelper.ConnectionOptions;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattHelper.OperationType;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothAdapter;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothDevice;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothGattWrapper;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.le.BluetoothLeScanner;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.le.ScanCallback;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.le.ScanResult;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.BluetoothOperationTimeoutException;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.Operation;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.UUID;

/**
 * Unit tests for {@link BluetoothGattHelper}.
 */
public class BluetoothGattHelperTest extends TestCase {

    private static final UUID SERVICE_UUID = UUID.randomUUID();
    private static final int GATT_STATUS = 1234;
    private static final Operation<BluetoothDevice> SCANNING_OPERATION =
            new Operation<BluetoothDevice>(OperationType.SCAN);
    private static final byte[] CHARACTERISTIC_VALUE = "characteristic_value".getBytes();
    private static final byte[] DESCRIPTOR_VALUE = "descriptor_value".getBytes();
    private static final int RSSI = -63;
    private static final int MTU = 50;
    private static final long CONNECT_TIMEOUT_MILLIS = 5000;

    private Context mMockApplicationContext = new MockContext();
    @Mock
    private BluetoothAdapter mMockBluetoothAdapter;
    @Mock
    private BluetoothLeScanner mMockBluetoothLeScanner;
    @Mock
    private BluetoothOperationExecutor mMockBluetoothOperationExecutor;
    @Mock
    private BluetoothDevice mMockBluetoothDevice;
    @Mock
    private BluetoothGattConnection mMockBluetoothGattConnection;
    @Mock
    private BluetoothGattWrapper mMockBluetoothGattWrapper;
    @Mock
    private BluetoothGattCharacteristic mMockBluetoothGattCharacteristic;
    @Mock
    private BluetoothGattDescriptor mMockBluetoothGattDescriptor;
    @Mock
    private ScanResult mMockScanResult;

    @Captor
    private ArgumentCaptor<Operation<?>> mOperationCaptor;
    @Captor
    private ArgumentCaptor<ScanSettings> mScanSettingsCaptor;

    private BluetoothGattHelper mBluetoothGattHelper;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        initMocks(this);

        mBluetoothGattHelper = new BluetoothGattHelper(
                mMockApplicationContext,
                mMockBluetoothAdapter,
                mMockBluetoothOperationExecutor);

        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(mMockBluetoothLeScanner);
        when(mMockBluetoothOperationExecutor.executeNonnull(SCANNING_OPERATION,
                BluetoothGattHelper.LOW_LATENCY_SCAN_MILLIS)).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothOperationExecutor.executeNonnull(SCANNING_OPERATION)).thenReturn(
                mMockBluetoothDevice);
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new Operation<BluetoothGattConnection>(OperationType.CONNECT, mMockBluetoothDevice),
                CONNECT_TIMEOUT_MILLIS))
                .thenReturn(mMockBluetoothGattConnection);
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new Operation<BluetoothGattConnection>(OperationType.CONNECT,
                        mMockBluetoothDevice)))
                .thenReturn(mMockBluetoothGattConnection);
        when(mMockBluetoothGattCharacteristic.getValue()).thenReturn(CHARACTERISTIC_VALUE);
        when(mMockBluetoothGattDescriptor.getValue()).thenReturn(DESCRIPTOR_VALUE);
        when(mMockScanResult.getDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothGattWrapper.getDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothDevice.connectGatt(eq(mMockApplicationContext), anyBoolean(),
                eq(mBluetoothGattHelper.mBluetoothGattCallback))).thenReturn(
                mMockBluetoothGattWrapper);
        when(mMockBluetoothDevice.connectGatt(eq(mMockApplicationContext), anyBoolean(),
                eq(mBluetoothGattHelper.mBluetoothGattCallback), anyInt()))
                .thenReturn(mMockBluetoothGattWrapper);
        when(mMockBluetoothGattConnection.getConnectionOptions())
                .thenReturn(ConnectionOptions.builder().build());
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_autoConnect_uuid_success_lowLatency() throws Exception {
        BluetoothGattConnection result = mBluetoothGattHelper.autoConnect(SERVICE_UUID);

        assertThat(result).isEqualTo(mMockBluetoothGattConnection);
        verify(mMockBluetoothOperationExecutor, atLeastOnce())
                .executeNonnull(mOperationCaptor.capture(),
                        anyLong());
        for (Operation<?> operation : mOperationCaptor.getAllValues()) {
            operation.run();
        }
        verify(mMockBluetoothLeScanner).startScan(eq(Arrays.asList(
                new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build())),
                mScanSettingsCaptor.capture(), eq(mBluetoothGattHelper.mScanCallback));
        assertThat(mScanSettingsCaptor.getValue().getScanMode()).isEqualTo(
                ScanSettings.SCAN_MODE_LOW_LATENCY);
        verify(mMockBluetoothLeScanner).stopScan(mBluetoothGattHelper.mScanCallback);
        verifyNoMoreInteractions(mMockBluetoothLeScanner);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_autoConnect_uuid_success_lowPower() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(SCANNING_OPERATION,
                BluetoothGattHelper.LOW_LATENCY_SCAN_MILLIS)).thenThrow(
                new BluetoothOperationTimeoutException("Timeout"));

        BluetoothGattConnection result = mBluetoothGattHelper.autoConnect(SERVICE_UUID);

        assertThat(result).isEqualTo(mMockBluetoothGattConnection);
        verify(mMockBluetoothOperationExecutor).executeNonnull(mOperationCaptor.capture());
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothLeScanner).startScan(eq(Arrays.asList(
                new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVICE_UUID)).build())),
                mScanSettingsCaptor.capture(), eq(mBluetoothGattHelper.mScanCallback));
        assertThat(mScanSettingsCaptor.getValue().getScanMode()).isEqualTo(
                ScanSettings.SCAN_MODE_LOW_POWER);
        verify(mMockBluetoothLeScanner, times(2)).stopScan(mBluetoothGattHelper.mScanCallback);
        verifyNoMoreInteractions(mMockBluetoothLeScanner);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_autoConnect_uuid_success_afterRetry() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new Operation<BluetoothGattConnection>(OperationType.CONNECT, mMockBluetoothDevice),
                BluetoothGattHelper.LOW_LATENCY_SCAN_MILLIS))
                .thenThrow(new BluetoothException("first attempt fails!"))
                .thenReturn(mMockBluetoothGattConnection);

        BluetoothGattConnection result = mBluetoothGattHelper.autoConnect(SERVICE_UUID);

        assertThat(result).isEqualTo(mMockBluetoothGattConnection);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_autoConnect_uuid_failure_scanning() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(SCANNING_OPERATION,
                BluetoothGattHelper.LOW_LATENCY_SCAN_MILLIS)).thenThrow(
                new BluetoothException("Scanning failed"));

        try {
            mBluetoothGattHelper.autoConnect(SERVICE_UUID);
            fail("BluetoothException expected");
        } catch (BluetoothException e) {
            // expected
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_autoConnect_uuid_failure_connecting() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new Operation<BluetoothGattConnection>(OperationType.CONNECT, mMockBluetoothDevice),
                CONNECT_TIMEOUT_MILLIS))
                .thenThrow(new BluetoothException("Connect failed"));

        try {
            mBluetoothGattHelper.autoConnect(SERVICE_UUID);
            fail("BluetoothException expected");
        } catch (BluetoothException e) {
            // expected
        }
        verify(mMockBluetoothOperationExecutor, times(3))
                .executeNonnull(
                        new Operation<BluetoothGattConnection>(OperationType.CONNECT,
                                mMockBluetoothDevice),
                        CONNECT_TIMEOUT_MILLIS);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_autoConnect_uuid_failure_noBle() throws Exception {
        when(mMockBluetoothAdapter.getBluetoothLeScanner()).thenReturn(null);

        try {
            mBluetoothGattHelper.autoConnect(SERVICE_UUID);
            fail("BluetoothException expected");
        } catch (BluetoothException e) {
            // expected
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_connect() throws Exception {
        BluetoothGattConnection result = mBluetoothGattHelper.connect(mMockBluetoothDevice);

        assertThat(result).isEqualTo(mMockBluetoothGattConnection);
        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mOperationCaptor.capture(), eq(CONNECT_TIMEOUT_MILLIS));
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothDevice).connectGatt(mMockApplicationContext, false,
                mBluetoothGattHelper.mBluetoothGattCallback,
                android.bluetooth.BluetoothDevice.TRANSPORT_LE);
        assertThat(mBluetoothGattHelper.mConnections.get(mMockBluetoothGattWrapper).getDevice())
                .isEqualTo(mMockBluetoothDevice);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_connect_withOptionAutoConnect_success() throws Exception {
        BluetoothGattConnection result = mBluetoothGattHelper
                .connect(
                        mMockBluetoothDevice,
                        ConnectionOptions.builder()
                                .setAutoConnect(true)
                                .build());

        assertThat(result).isEqualTo(mMockBluetoothGattConnection);
        verify(mMockBluetoothOperationExecutor).executeNonnull(mOperationCaptor.capture());
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothDevice).connectGatt(mMockApplicationContext, true,
                mBluetoothGattHelper.mBluetoothGattCallback,
                android.bluetooth.BluetoothDevice.TRANSPORT_LE);
        assertThat(mBluetoothGattHelper.mConnections.get(mMockBluetoothGattWrapper)
                .getConnectionOptions())
                .isEqualTo(ConnectionOptions.builder()
                        .setAutoConnect(true)
                        .build());
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_connect_withOptionAutoConnect_failure_nullResult() throws Exception {
        when(mMockBluetoothDevice.connectGatt(eq(mMockApplicationContext), anyBoolean(),
                eq(mBluetoothGattHelper.mBluetoothGattCallback),
                eq(android.bluetooth.BluetoothDevice.TRANSPORT_LE))).thenReturn(null);

        try {
            mBluetoothGattHelper.connect(
                    mMockBluetoothDevice,
                    ConnectionOptions.builder()
                            .setAutoConnect(true)
                            .build());
            verify(mMockBluetoothOperationExecutor).executeNonnull(mOperationCaptor.capture());
            mOperationCaptor.getValue().run();
            fail("BluetoothException expected");
        } catch (BluetoothException e) {
            // expected
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_connect_withOptionRequestConnectionPriority_success() throws Exception {
        // Operation succeeds on the 3rd try.
        when(mMockBluetoothGattWrapper
                .requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH))
                .thenReturn(false)
                .thenReturn(false)
                .thenReturn(true);

        BluetoothGattConnection result = mBluetoothGattHelper
                .connect(
                        mMockBluetoothDevice,
                        ConnectionOptions.builder()
                                .setConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                                .build());

        assertThat(result).isEqualTo(mMockBluetoothGattConnection);
        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mOperationCaptor.capture(), eq(CONNECT_TIMEOUT_MILLIS));
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothDevice).connectGatt(mMockApplicationContext, false,
                mBluetoothGattHelper.mBluetoothGattCallback,
                android.bluetooth.BluetoothDevice.TRANSPORT_LE);
        assertThat(mBluetoothGattHelper.mConnections.get(mMockBluetoothGattWrapper)
                .getConnectionOptions())
                .isEqualTo(ConnectionOptions.builder()
                        .setConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        .build());
        verify(mMockBluetoothGattWrapper, times(3))
                .requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_connect_cancel() throws Exception {
        mBluetoothGattHelper.connect(mMockBluetoothDevice);

        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mOperationCaptor.capture(), eq(CONNECT_TIMEOUT_MILLIS));
        Operation<?> operation = mOperationCaptor.getValue();
        operation.run();
        operation.cancel();

        verify(mMockBluetoothGattWrapper).disconnect();
        verify(mMockBluetoothGattWrapper).close();
        assertThat(mBluetoothGattHelper.mConnections.get(mMockBluetoothGattWrapper)).isNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onConnectionStateChange_connected_success()
            throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);

        mBluetoothGattHelper.mBluetoothGattCallback.onConnectionStateChange(
                mMockBluetoothGattWrapper,
                BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(
                new Operation<>(OperationType.CONNECT, mMockBluetoothDevice),
                BluetoothGatt.GATT_SUCCESS,
                mMockBluetoothGattConnection);
        verify(mMockBluetoothGattConnection).onConnected();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onConnectionStateChange_connected_success_withMtuOption()
            throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);
        when(mMockBluetoothGattConnection.getConnectionOptions())
                .thenReturn(BluetoothGattHelper.ConnectionOptions.builder()
                        .setMtu(MTU)
                        .build());
        when(mMockBluetoothGattWrapper.requestMtu(MTU)).thenReturn(true);

        mBluetoothGattHelper.mBluetoothGattCallback.onConnectionStateChange(
                mMockBluetoothGattWrapper,
                BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED);

        verifyZeroInteractions(mMockBluetoothOperationExecutor);
        verify(mMockBluetoothGattConnection, never()).onConnected();
        verify(mMockBluetoothGattWrapper).requestMtu(MTU);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onConnectionStateChange_connected_success_failMtuOption()
            throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);
        when(mMockBluetoothGattConnection.getConnectionOptions())
                .thenReturn(BluetoothGattHelper.ConnectionOptions.builder()
                        .setMtu(MTU)
                        .build());
        when(mMockBluetoothGattWrapper.requestMtu(MTU)).thenReturn(false);

        mBluetoothGattHelper.mBluetoothGattCallback.onConnectionStateChange(
                mMockBluetoothGattWrapper,
                BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED);

        verify(mMockBluetoothOperationExecutor).notifyFailure(
                eq(new Operation<>(OperationType.CONNECT, mMockBluetoothDevice)),
                any(BluetoothException.class));
        verify(mMockBluetoothGattConnection, never()).onConnected();
        verify(mMockBluetoothGattWrapper).disconnect();
        verify(mMockBluetoothGattWrapper).close();
        assertThat(mBluetoothGattHelper.mConnections.get(mMockBluetoothGattWrapper)).isNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onConnectionStateChange_connected_unexpectedSuccess()
            throws Exception {
        mBluetoothGattHelper.mBluetoothGattCallback.onConnectionStateChange(
                mMockBluetoothGattWrapper,
                BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED);

        verifyZeroInteractions(mMockBluetoothOperationExecutor);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onConnectionStateChange_connected_failure()
            throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);

        mBluetoothGattHelper.mBluetoothGattCallback
                .onConnectionStateChange(
                        mMockBluetoothGattWrapper,
                        BluetoothGatt.GATT_FAILURE,
                        BluetoothGatt.STATE_CONNECTED);

        verify(mMockBluetoothOperationExecutor)
                .notifyCompletion(
                        new Operation<>(OperationType.CONNECT, mMockBluetoothDevice),
                        BluetoothGatt.GATT_FAILURE,
                        null);
        verify(mMockBluetoothGattWrapper).disconnect();
        verify(mMockBluetoothGattWrapper).close();
        assertThat(mBluetoothGattHelper.mConnections.get(mMockBluetoothGattWrapper)).isNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onConnectionStateChange_disconnected_unexpectedSuccess()
            throws Exception {
        mBluetoothGattHelper.mBluetoothGattCallback
                .onConnectionStateChange(
                        mMockBluetoothGattWrapper,
                        BluetoothGatt.GATT_SUCCESS,
                        BluetoothGatt.STATE_DISCONNECTED);

        verifyZeroInteractions(mMockBluetoothOperationExecutor);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onConnectionStateChange_disconnected_notConnected()
            throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);
        when(mMockBluetoothGattConnection.isConnected()).thenReturn(false);

        mBluetoothGattHelper.mBluetoothGattCallback
                .onConnectionStateChange(
                        mMockBluetoothGattWrapper,
                        GATT_STATUS,
                        BluetoothGatt.STATE_DISCONNECTED);

        verify(mMockBluetoothOperationExecutor)
                .notifyCompletion(
                        new Operation<>(OperationType.CONNECT, mMockBluetoothDevice),
                        GATT_STATUS,
                        null);
        verify(mMockBluetoothGattWrapper).disconnect();
        verify(mMockBluetoothGattWrapper).close();
        assertThat(mBluetoothGattHelper.mConnections.get(mMockBluetoothGattWrapper)).isNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onConnectionStateChange_disconnected_success()
            throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);
        when(mMockBluetoothGattConnection.isConnected()).thenReturn(true);

        mBluetoothGattHelper.mBluetoothGattCallback.onConnectionStateChange(
                mMockBluetoothGattWrapper,
                BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_DISCONNECTED);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(
                new Operation<>(OperationType.DISCONNECT, mMockBluetoothDevice),
                BluetoothGatt.GATT_SUCCESS);
        verify(mMockBluetoothGattConnection).onClosed();
        assertThat(mBluetoothGattHelper.mConnections.get(mMockBluetoothGattWrapper)).isNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onConnectionStateChange_disconnected_failure()
            throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);
        when(mMockBluetoothGattConnection.isConnected()).thenReturn(true);

        mBluetoothGattHelper.mBluetoothGattCallback.onConnectionStateChange(
                mMockBluetoothGattWrapper,
                BluetoothGatt.GATT_FAILURE, BluetoothGatt.STATE_DISCONNECTED);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(
                new Operation<>(OperationType.DISCONNECT, mMockBluetoothDevice),
                BluetoothGatt.GATT_FAILURE);
        verify(mMockBluetoothGattConnection).onClosed();
        assertThat(mBluetoothGattHelper.mConnections.get(mMockBluetoothGattWrapper)).isNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onServicesDiscovered() throws Exception {
        mBluetoothGattHelper.mBluetoothGattCallback.onServicesDiscovered(mMockBluetoothGattWrapper,
                GATT_STATUS);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(
                new Operation<Void>(OperationType.DISCOVER_SERVICES_INTERNAL,
                        mMockBluetoothGattWrapper),
                GATT_STATUS);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onCharacteristicRead() throws Exception {
        mBluetoothGattHelper.mBluetoothGattCallback.onCharacteristicRead(mMockBluetoothGattWrapper,
                mMockBluetoothGattCharacteristic, GATT_STATUS);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(new Operation<byte[]>(
                        OperationType.READ_CHARACTERISTIC, mMockBluetoothGattWrapper,
                        mMockBluetoothGattCharacteristic),
                GATT_STATUS, CHARACTERISTIC_VALUE);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onCharacteristicWrite() throws Exception {
        mBluetoothGattHelper.mBluetoothGattCallback.onCharacteristicWrite(mMockBluetoothGattWrapper,
                mMockBluetoothGattCharacteristic, GATT_STATUS);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(new Operation<Void>(
                        OperationType.WRITE_CHARACTERISTIC, mMockBluetoothGattWrapper,
                        mMockBluetoothGattCharacteristic),
                GATT_STATUS);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onDescriptorRead() throws Exception {
        mBluetoothGattHelper.mBluetoothGattCallback.onDescriptorRead(mMockBluetoothGattWrapper,
                mMockBluetoothGattDescriptor, GATT_STATUS);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(new Operation<byte[]>(
                        OperationType.READ_DESCRIPTOR, mMockBluetoothGattWrapper,
                        mMockBluetoothGattDescriptor),
                GATT_STATUS,
                DESCRIPTOR_VALUE);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onDescriptorWrite() throws Exception {
        mBluetoothGattHelper.mBluetoothGattCallback.onDescriptorWrite(mMockBluetoothGattWrapper,
                mMockBluetoothGattDescriptor, GATT_STATUS);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(new Operation<Void>(
                        OperationType.WRITE_DESCRIPTOR, mMockBluetoothGattWrapper,
                        mMockBluetoothGattDescriptor),
                GATT_STATUS);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onReadRemoteRssi() throws Exception {
        mBluetoothGattHelper.mBluetoothGattCallback.onReadRemoteRssi(mMockBluetoothGattWrapper,
                RSSI, GATT_STATUS);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(
                new Operation<Integer>(OperationType.READ_RSSI, mMockBluetoothGattWrapper),
                GATT_STATUS, RSSI);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onReliableWriteCompleted() throws Exception {
        mBluetoothGattHelper.mBluetoothGattCallback.onReliableWriteCompleted(
                mMockBluetoothGattWrapper,
                GATT_STATUS);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(
                new Operation<Void>(OperationType.WRITE_RELIABLE, mMockBluetoothGattWrapper),
                GATT_STATUS);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onMtuChanged() throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);
        when(mMockBluetoothGattConnection.isConnected()).thenReturn(true);

        mBluetoothGattHelper.mBluetoothGattCallback
                .onMtuChanged(mMockBluetoothGattWrapper, MTU, GATT_STATUS);

        verify(mMockBluetoothOperationExecutor).notifyCompletion(
                new Operation<>(OperationType.CHANGE_MTU, mMockBluetoothGattWrapper), GATT_STATUS,
                MTU);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothGattCallback_onMtuChangedDuringConnection_success() throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);
        when(mMockBluetoothGattConnection.isConnected()).thenReturn(false);

        mBluetoothGattHelper.mBluetoothGattCallback.onMtuChanged(
                mMockBluetoothGattWrapper, MTU, BluetoothGatt.GATT_SUCCESS);

        verify(mMockBluetoothGattConnection).onConnected();
        verify(mMockBluetoothOperationExecutor)
                .notifyCompletion(
                        new Operation<>(OperationType.CONNECT, mMockBluetoothDevice),
                        BluetoothGatt.GATT_SUCCESS,
                        mMockBluetoothGattConnection);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testBluetoothGattCallback_onMtuChangedDuringConnection_fail() throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);
        when(mMockBluetoothGattConnection.isConnected()).thenReturn(false);

        mBluetoothGattHelper.mBluetoothGattCallback
                .onMtuChanged(mMockBluetoothGattWrapper, MTU, GATT_STATUS);

        verify(mMockBluetoothGattConnection).onConnected();
        verify(mMockBluetoothOperationExecutor)
                .notifyCompletion(
                        new Operation<>(OperationType.CONNECT, mMockBluetoothDevice),
                        GATT_STATUS,
                        mMockBluetoothGattConnection);
        verify(mMockBluetoothGattWrapper).disconnect();
        verify(mMockBluetoothGattWrapper).close();
        assertThat(mBluetoothGattHelper.mConnections.get(mMockBluetoothGattWrapper)).isNull();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_BluetoothGattCallback_onCharacteristicChanged() throws Exception {
        mBluetoothGattHelper.mConnections.put(mMockBluetoothGattWrapper,
                mMockBluetoothGattConnection);

        mBluetoothGattHelper.mBluetoothGattCallback.onCharacteristicChanged(
                mMockBluetoothGattWrapper,
                mMockBluetoothGattCharacteristic);

        verify(mMockBluetoothGattConnection).onCharacteristicChanged(
                mMockBluetoothGattCharacteristic,
                CHARACTERISTIC_VALUE);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_ScanCallback_onScanFailed() throws Exception {
        mBluetoothGattHelper.mScanCallback.onScanFailed(ScanCallback.SCAN_FAILED_INTERNAL_ERROR);

        verify(mMockBluetoothOperationExecutor).notifyFailure(
                eq(new Operation<BluetoothDevice>(OperationType.SCAN)),
                isA(BluetoothException.class));
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_ScanCallback_onScanResult() throws Exception {
        mBluetoothGattHelper.mScanCallback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES,
                mMockScanResult);

        verify(mMockBluetoothOperationExecutor).notifySuccess(
                new Operation<BluetoothDevice>(OperationType.SCAN), mMockBluetoothDevice);
    }
}
