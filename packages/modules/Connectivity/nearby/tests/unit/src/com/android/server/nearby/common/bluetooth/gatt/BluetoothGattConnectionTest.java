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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothStatusCodes;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

import androidx.test.filters.SdkSuppress;

import com.android.server.nearby.common.bluetooth.BluetoothConsts;
import com.android.server.nearby.common.bluetooth.BluetoothException;
import com.android.server.nearby.common.bluetooth.BluetoothGattException;
import com.android.server.nearby.common.bluetooth.ReservedUuids;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattConnection.ChangeObserver;
import com.android.server.nearby.common.bluetooth.gatt.BluetoothGattHelper.OperationType;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothDevice;
import com.android.server.nearby.common.bluetooth.testability.android.bluetooth.BluetoothGattWrapper;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.Operation;
import com.android.server.nearby.common.bluetooth.util.BluetoothOperationExecutor.SynchronousOperation;

import junit.framework.TestCase;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link BluetoothGattConnection}.
 */
public class BluetoothGattConnectionTest extends TestCase {

    private static final UUID SERVICE_UUID = UUID.randomUUID();
    private static final UUID CHARACTERISTIC_UUID = UUID.randomUUID();
    private static final UUID DESCRIPTOR_UUID = UUID.randomUUID();
    private static final byte[] DATA = "data".getBytes();
    private static final int RSSI = -63;
    private static final int CONNECTION_PRIORITY = 128;
    private static final int MTU_REQUEST = 512;
    private static final BluetoothGattHelper.ConnectionOptions CONNECTION_OPTIONS =
            BluetoothGattHelper.ConnectionOptions.builder().build();

    @Mock
    private BluetoothGattWrapper mMockBluetoothGattWrapper;
    @Mock
    private BluetoothDevice mMockBluetoothDevice;
    @Mock
    private BluetoothOperationExecutor mMockBluetoothOperationExecutor;
    @Mock
    private BluetoothGattService mMockBluetoothGattService;
    @Mock
    private BluetoothGattService mMockBluetoothGattService2;
    @Mock
    private BluetoothGattCharacteristic mMockBluetoothGattCharacteristic;
    @Mock
    private BluetoothGattCharacteristic mMockBluetoothGattCharacteristic2;
    @Mock
    private BluetoothGattDescriptor mMockBluetoothGattDescriptor;
    @Mock
    private BluetoothGattConnection.CharacteristicChangeListener mMockCharChangeListener;
    @Mock
    private BluetoothGattConnection.ChangeObserver mMockChangeObserver;
    @Mock
    private BluetoothGattConnection.ConnectionCloseListener mMockConnectionCloseListener;

    @Captor
    private ArgumentCaptor<Operation<?>> mOperationCaptor;
    @Captor
    private ArgumentCaptor<SynchronousOperation<?>> mSynchronousOperationCaptor;
    @Captor
    private ArgumentCaptor<BluetoothGattCharacteristic> mCharacteristicCaptor;
    @Captor
    private ArgumentCaptor<BluetoothGattDescriptor> mDescriptorCaptor;

    private BluetoothGattConnection mBluetoothGattConnection;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        initMocks(this);

        mBluetoothGattConnection = new BluetoothGattConnection(
                mMockBluetoothGattWrapper,
                mMockBluetoothOperationExecutor,
                CONNECTION_OPTIONS);
        mBluetoothGattConnection.onConnected();

        when(mMockBluetoothGattWrapper.getDevice()).thenReturn(mMockBluetoothDevice);
        when(mMockBluetoothGattWrapper.discoverServices()).thenReturn(true);
        when(mMockBluetoothGattWrapper.refresh()).thenReturn(true);
        when(mMockBluetoothGattWrapper.readCharacteristic(mMockBluetoothGattCharacteristic))
                .thenReturn(true);
        when(mMockBluetoothGattWrapper
                .writeCharacteristic(ArgumentMatchers.<BluetoothGattCharacteristic>any(), any(),
                        anyInt()))
                .thenReturn(BluetoothStatusCodes.SUCCESS);
        when(mMockBluetoothGattWrapper.readDescriptor(mMockBluetoothGattDescriptor))
                .thenReturn(true);
        when(mMockBluetoothGattWrapper.writeDescriptor(
                ArgumentMatchers.<BluetoothGattDescriptor>any(), any()))
                .thenReturn(BluetoothStatusCodes.SUCCESS);
        when(mMockBluetoothGattWrapper.readRemoteRssi()).thenReturn(true);
        when(mMockBluetoothGattWrapper.requestConnectionPriority(CONNECTION_PRIORITY))
                .thenReturn(true);
        when(mMockBluetoothGattWrapper.requestMtu(MTU_REQUEST)).thenReturn(true);
        when(mMockBluetoothGattWrapper.getServices())
                .thenReturn(Arrays.asList(mMockBluetoothGattService));
        when(mMockBluetoothGattService.getUuid()).thenReturn(SERVICE_UUID);
        when(mMockBluetoothGattService.getCharacteristics())
                .thenReturn(Arrays.asList(mMockBluetoothGattCharacteristic));
        when(mMockBluetoothGattCharacteristic.getUuid()).thenReturn(CHARACTERISTIC_UUID);
        when(mMockBluetoothGattCharacteristic.getProperties())
                .thenReturn(
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY
                                | BluetoothGattCharacteristic.PROPERTY_WRITE);
        BluetoothGattDescriptor clientConfigDescriptor =
                new BluetoothGattDescriptor(
                        ReservedUuids.Descriptors.CLIENT_CHARACTERISTIC_CONFIGURATION,
                        BluetoothGattDescriptor.PERMISSION_WRITE);
        when(mMockBluetoothGattCharacteristic.getDescriptor(
                ReservedUuids.Descriptors.CLIENT_CHARACTERISTIC_CONFIGURATION))
                .thenReturn(clientConfigDescriptor);
        when(mMockBluetoothGattCharacteristic.getDescriptors())
                .thenReturn(Arrays.asList(mMockBluetoothGattDescriptor, clientConfigDescriptor));
        when(mMockBluetoothGattDescriptor.getUuid()).thenReturn(DESCRIPTOR_UUID);
        when(mMockBluetoothDevice.getBondState()).thenReturn(BluetoothDevice.BOND_BONDED);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getDevice() {
        BluetoothDevice result = mBluetoothGattConnection.getDevice();

        assertThat(result).isEqualTo(mMockBluetoothDevice);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getConnectionOptions() {
        BluetoothGattHelper.ConnectionOptions result = mBluetoothGattConnection
                .getConnectionOptions();

        assertThat(result).isSameInstanceAs(CONNECTION_OPTIONS);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_isConnected_false_beforeConnection() {
        mBluetoothGattConnection = new BluetoothGattConnection(
                mMockBluetoothGattWrapper,
                mMockBluetoothOperationExecutor,
                CONNECTION_OPTIONS);

        boolean result = mBluetoothGattConnection.isConnected();

        assertThat(result).isFalse();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_isConnected_true_afterConnection() {
        boolean result = mBluetoothGattConnection.isConnected();

        assertThat(result).isTrue();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_isConnected_false_afterDisconnection() {
        mBluetoothGattConnection.onClosed();

        boolean result = mBluetoothGattConnection.isConnected();

        assertThat(result).isFalse();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getService_notDiscovered() throws Exception {
        BluetoothGattService result = mBluetoothGattConnection.getService(SERVICE_UUID);
        verify(mMockBluetoothOperationExecutor).execute(mSynchronousOperationCaptor.capture());
        mSynchronousOperationCaptor.getValue().call();
        verify(mMockBluetoothOperationExecutor)
                .execute(
                        mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.SLOW_OPERATION_TIMEOUT_MILLIS));
        mOperationCaptor.getValue().run();

        assertThat(result).isEqualTo(mMockBluetoothGattService);
        verify(mMockBluetoothGattWrapper).discoverServices();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getService_alreadyDiscovered() throws Exception {
        mBluetoothGattConnection.getService(SERVICE_UUID);
        verify(mMockBluetoothOperationExecutor).execute(mSynchronousOperationCaptor.capture());
        mSynchronousOperationCaptor.getValue().call();
        reset(mMockBluetoothOperationExecutor);

        BluetoothGattService result = mBluetoothGattConnection.getService(SERVICE_UUID);

        assertThat(result).isEqualTo(mMockBluetoothGattService);
        // Verify that service discovery has been done only once
        verifyNoMoreInteractions(mMockBluetoothOperationExecutor);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getService_notFound() throws Exception {
        when(mMockBluetoothGattWrapper.getServices()).thenReturn(
                Arrays.<BluetoothGattService>asList());

        try {
            mBluetoothGattConnection.getService(SERVICE_UUID);
            fail("Expected BluetoothException");
        } catch (BluetoothException expected) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getService_moreThanOne() throws Exception {
        when(mMockBluetoothGattWrapper.getServices())
                .thenReturn(Arrays.asList(mMockBluetoothGattService, mMockBluetoothGattService));

        try {
            mBluetoothGattConnection.getService(SERVICE_UUID);
            fail("Expected BluetoothException");
        } catch (BluetoothException expected) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getCharacteristic() throws Exception {
        BluetoothGattCharacteristic result =
                mBluetoothGattConnection.getCharacteristic(SERVICE_UUID, CHARACTERISTIC_UUID);

        assertThat(result).isEqualTo(mMockBluetoothGattCharacteristic);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getCharacteristic_notFound() throws Exception {
        when(mMockBluetoothGattService.getCharacteristics())
                .thenReturn(Arrays.<BluetoothGattCharacteristic>asList());

        try {
            mBluetoothGattConnection.getCharacteristic(SERVICE_UUID, CHARACTERISTIC_UUID);
            fail("Expected BluetoothException");
        } catch (BluetoothException expected) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getCharacteristic_moreThanOne() throws Exception {
        when(mMockBluetoothGattService.getCharacteristics())
                .thenReturn(
                        Arrays.asList(mMockBluetoothGattCharacteristic,
                                mMockBluetoothGattCharacteristic));

        try {
            mBluetoothGattConnection.getCharacteristic(SERVICE_UUID, CHARACTERISTIC_UUID);
            fail("Expected BluetoothException");
        } catch (BluetoothException expected) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getCharacteristic_moreThanOneService() throws Exception {
        // Add a new service with the same service UUID as our existing one, but add a different
        // characteristic inside of it.
        when(mMockBluetoothGattWrapper.getServices())
                .thenReturn(Arrays.asList(mMockBluetoothGattService, mMockBluetoothGattService2));
        when(mMockBluetoothGattService2.getUuid()).thenReturn(SERVICE_UUID);
        when(mMockBluetoothGattService2.getCharacteristics())
                .thenReturn(Arrays.asList(mMockBluetoothGattCharacteristic2));
        when(mMockBluetoothGattCharacteristic2.getUuid())
                .thenReturn(
                        new UUID(
                                CHARACTERISTIC_UUID.getMostSignificantBits(),
                                CHARACTERISTIC_UUID.getLeastSignificantBits() + 1));
        when(mMockBluetoothGattCharacteristic2.getProperties())
                .thenReturn(
                        BluetoothGattCharacteristic.PROPERTY_NOTIFY
                                | BluetoothGattCharacteristic.PROPERTY_WRITE);

        mBluetoothGattConnection.getCharacteristic(SERVICE_UUID, CHARACTERISTIC_UUID);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getDescriptor() throws Exception {
        when(mMockBluetoothGattCharacteristic.getDescriptors())
                .thenReturn(Arrays.asList(mMockBluetoothGattDescriptor));

        BluetoothGattDescriptor result =
                mBluetoothGattConnection
                        .getDescriptor(SERVICE_UUID, CHARACTERISTIC_UUID, DESCRIPTOR_UUID);

        assertThat(result).isEqualTo(mMockBluetoothGattDescriptor);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getDescriptor_notFound() throws Exception {
        when(mMockBluetoothGattCharacteristic.getDescriptors())
                .thenReturn(Arrays.<BluetoothGattDescriptor>asList());

        try {
            mBluetoothGattConnection
                    .getDescriptor(SERVICE_UUID, CHARACTERISTIC_UUID, DESCRIPTOR_UUID);
            fail("Expected BluetoothException");
        } catch (BluetoothException expected) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getDescriptor_moreThanOne() throws Exception {
        when(mMockBluetoothGattCharacteristic.getDescriptors())
                .thenReturn(
                        Arrays.asList(mMockBluetoothGattDescriptor, mMockBluetoothGattDescriptor));

        try {
            mBluetoothGattConnection
                    .getDescriptor(SERVICE_UUID, CHARACTERISTIC_UUID, DESCRIPTOR_UUID);
            fail("Expected BluetoothException");
        } catch (BluetoothException expected) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_discoverServices() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new SynchronousOperation<>(
                        mMockBluetoothOperationExecutor, OperationType.NOTIFICATION_CHANGE)))
                .thenReturn(mMockChangeObserver);

        mBluetoothGattConnection.discoverServices();

        verify(mMockBluetoothOperationExecutor).execute(mSynchronousOperationCaptor.capture());
        mSynchronousOperationCaptor.getValue().call();
        verify(mMockBluetoothOperationExecutor)
                .execute(
                        mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.SLOW_OPERATION_TIMEOUT_MILLIS));
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).discoverServices();
        verify(mMockBluetoothGattWrapper, never()).refresh();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_discoverServices_serviceChange() throws Exception {
        when(mMockBluetoothGattWrapper.getService(ReservedUuids.Services.GENERIC_ATTRIBUTE))
                .thenReturn(mMockBluetoothGattService);
        when(mMockBluetoothGattService
                .getCharacteristic(ReservedUuids.Characteristics.SERVICE_CHANGE))
                .thenReturn(mMockBluetoothGattCharacteristic);

        mBluetoothGattConnection.discoverServices();

        verify(mMockBluetoothOperationExecutor).execute(mSynchronousOperationCaptor.capture());
        mSynchronousOperationCaptor.getValue().call();
        verify(mMockBluetoothOperationExecutor, times(2))
                .execute(
                        mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.SLOW_OPERATION_TIMEOUT_MILLIS));
        verify(mMockBluetoothGattWrapper).refresh();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_discoverServices_SelfDefinedServiceDynamic() throws Exception {
        when(mMockBluetoothGattWrapper.getService(BluetoothConsts.SERVICE_DYNAMIC_SERVICE))
                .thenReturn(mMockBluetoothGattService);
        when(mMockBluetoothGattService
                .getCharacteristic(BluetoothConsts.SERVICE_DYNAMIC_CHARACTERISTIC))
                .thenReturn(mMockBluetoothGattCharacteristic);

        mBluetoothGattConnection.discoverServices();

        verify(mMockBluetoothOperationExecutor).execute(mSynchronousOperationCaptor.capture());
        mSynchronousOperationCaptor.getValue().call();
        verify(mMockBluetoothOperationExecutor, times(2))
                .execute(
                        mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.SLOW_OPERATION_TIMEOUT_MILLIS));
        verify(mMockBluetoothGattWrapper).refresh();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_discoverServices_refreshWithGattErrorOnMncAbove() throws Exception {
        if (VERSION.SDK_INT <= VERSION_CODES.LOLLIPOP_MR1) {
            return;
        }
        mBluetoothGattConnection.discoverServices();
        verify(mMockBluetoothOperationExecutor).execute(mSynchronousOperationCaptor.capture());

        doThrow(new BluetoothGattException("fail", BluetoothGattConnection.GATT_ERROR))
                .doReturn(null)
                .when(mMockBluetoothOperationExecutor)
                .execute(isA(Operation.class),
                        eq(BluetoothGattConnection.SLOW_OPERATION_TIMEOUT_MILLIS));
        mSynchronousOperationCaptor.getValue().call();
        verify(mMockBluetoothOperationExecutor, times(2))
                .execute(
                        mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.SLOW_OPERATION_TIMEOUT_MILLIS));
        verify(mMockBluetoothGattWrapper).refresh();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_discoverServices_refreshWithGattInternalErrorOnMncAbove() throws Exception {
        if (VERSION.SDK_INT <= VERSION_CODES.LOLLIPOP_MR1) {
            return;
        }
        mBluetoothGattConnection.discoverServices();
        verify(mMockBluetoothOperationExecutor).execute(mSynchronousOperationCaptor.capture());

        doThrow(new BluetoothGattException("fail", BluetoothGattConnection.GATT_INTERNAL_ERROR))
                .doReturn(null)
                .when(mMockBluetoothOperationExecutor)
                .execute(isA(Operation.class),
                        eq(BluetoothGattConnection.SLOW_OPERATION_TIMEOUT_MILLIS));
        mSynchronousOperationCaptor.getValue().call();
        verify(mMockBluetoothOperationExecutor, times(2))
                .execute(
                        mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.SLOW_OPERATION_TIMEOUT_MILLIS));
        verify(mMockBluetoothGattWrapper).refresh();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_discoverServices_dynamicServices_notBonded() throws Exception {
        when(mMockBluetoothGattWrapper.getService(ReservedUuids.Services.GENERIC_ATTRIBUTE))
                .thenReturn(mMockBluetoothGattService);
        when(mMockBluetoothGattService
                .getCharacteristic(ReservedUuids.Characteristics.SERVICE_CHANGE))
                .thenReturn(mMockBluetoothGattCharacteristic);
        when(mMockBluetoothDevice.getBondState()).thenReturn(BluetoothDevice.BOND_NONE);

        mBluetoothGattConnection.discoverServices();

        verify(mMockBluetoothGattWrapper, never()).refresh();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_readCharacteristic() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new Operation<byte[]>(
                        OperationType.READ_CHARACTERISTIC,
                        mMockBluetoothGattWrapper,
                        mMockBluetoothGattCharacteristic),
                BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS))
                .thenReturn(DATA);

        byte[] result = mBluetoothGattConnection
                .readCharacteristic(mMockBluetoothGattCharacteristic);

        assertThat(result).isEqualTo(DATA);
        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mOperationCaptor.capture(), anyLong());
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).readCharacteristic(mMockBluetoothGattCharacteristic);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_readCharacteristic_by_uuid() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new Operation<byte[]>(
                        OperationType.READ_CHARACTERISTIC,
                        mMockBluetoothGattWrapper,
                        mMockBluetoothGattCharacteristic),
                BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS))
                .thenReturn(DATA);

        byte[] result = mBluetoothGattConnection
                .readCharacteristic(SERVICE_UUID, CHARACTERISTIC_UUID);

        assertThat(result).isEqualTo(DATA);
        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mOperationCaptor.capture(), anyLong());
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).readCharacteristic(mMockBluetoothGattCharacteristic);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_writeCharacteristic() throws Exception {
        BluetoothGattCharacteristic characteristic =
                new BluetoothGattCharacteristic(
                        CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE, 0);
        mBluetoothGattConnection.writeCharacteristic(characteristic, DATA);

        verify(mMockBluetoothOperationExecutor)
                .execute(mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS));
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).writeCharacteristic(mCharacteristicCaptor.capture(),
                eq(DATA), eq(characteristic.getWriteType()));
        BluetoothGattCharacteristic writtenCharacteristic = mCharacteristicCaptor.getValue();
        assertThat(writtenCharacteristic.getUuid()).isEqualTo(CHARACTERISTIC_UUID);
        assertThat(writtenCharacteristic).isEqualTo(characteristic);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_writeCharacteristic_by_uuid() throws Exception {
        mBluetoothGattConnection.writeCharacteristic(SERVICE_UUID, CHARACTERISTIC_UUID, DATA);

        verify(mMockBluetoothOperationExecutor)
                .execute(mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS));
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).writeCharacteristic(mCharacteristicCaptor.capture(),
                eq(DATA), anyInt());
        BluetoothGattCharacteristic writtenCharacteristic = mCharacteristicCaptor.getValue();
        assertThat(writtenCharacteristic.getUuid()).isEqualTo(CHARACTERISTIC_UUID);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_readDescriptor() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new Operation<byte[]>(
                        OperationType.READ_DESCRIPTOR, mMockBluetoothGattWrapper,
                        mMockBluetoothGattDescriptor),
                BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS))
                .thenReturn(DATA);

        byte[] result = mBluetoothGattConnection.readDescriptor(mMockBluetoothGattDescriptor);

        assertThat(result).isEqualTo(DATA);
        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mOperationCaptor.capture(), anyLong());
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).readDescriptor(mMockBluetoothGattDescriptor);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_readDescriptor_by_uuid() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new Operation<byte[]>(
                        OperationType.READ_DESCRIPTOR, mMockBluetoothGattWrapper,
                        mMockBluetoothGattDescriptor),
                BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS))
                .thenReturn(DATA);

        byte[] result =
                mBluetoothGattConnection
                        .readDescriptor(SERVICE_UUID, CHARACTERISTIC_UUID, DESCRIPTOR_UUID);

        assertThat(result).isEqualTo(DATA);
        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mOperationCaptor.capture(), anyLong());
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).readDescriptor(mMockBluetoothGattDescriptor);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_writeDescriptor() throws Exception {
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(DESCRIPTOR_UUID, 0);
        mBluetoothGattConnection.writeDescriptor(descriptor, DATA);

        verify(mMockBluetoothOperationExecutor)
                .execute(mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS));
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).writeDescriptor(mDescriptorCaptor.capture(), eq(DATA));
        BluetoothGattDescriptor writtenDescriptor = mDescriptorCaptor.getValue();
        assertThat(writtenDescriptor.getUuid()).isEqualTo(DESCRIPTOR_UUID);
        assertThat(writtenDescriptor).isEqualTo(descriptor);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_writeDescriptor_by_uuid() throws Exception {
        mBluetoothGattConnection.writeDescriptor(
                SERVICE_UUID, CHARACTERISTIC_UUID, DESCRIPTOR_UUID, DATA);

        verify(mMockBluetoothOperationExecutor)
                .execute(mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS));
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).writeDescriptor(mDescriptorCaptor.capture(), eq(DATA));
        BluetoothGattDescriptor writtenDescriptor = mDescriptorCaptor.getValue();
        assertThat(writtenDescriptor.getUuid()).isEqualTo(DESCRIPTOR_UUID);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_readRemoteRssi() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new Operation<Integer>(OperationType.READ_RSSI, mMockBluetoothGattWrapper),
                BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS))
                .thenReturn(RSSI);

        int result = mBluetoothGattConnection.readRemoteRssi();

        assertThat(result).isEqualTo(RSSI);
        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(
                        mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS));
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).readRemoteRssi();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getMaxDataPacketSize() throws Exception {
        int result = mBluetoothGattConnection.getMaxDataPacketSize();

        assertThat(result).isEqualTo(mBluetoothGattConnection.getMtu() - 3);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testSetNotificationEnabled_indication_enable() throws Exception {
        when(mMockBluetoothGattCharacteristic.getProperties())
                .thenReturn(BluetoothGattCharacteristic.PROPERTY_INDICATE);

        mBluetoothGattConnection.setNotificationEnabled(mMockBluetoothGattCharacteristic, true);

        verify(mMockBluetoothGattWrapper)
                .setCharacteristicNotification(mMockBluetoothGattCharacteristic, true);
        verify(mMockBluetoothOperationExecutor).execute(mOperationCaptor.capture(), anyLong());
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).writeDescriptor(mDescriptorCaptor.capture(),
                eq(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE));
        BluetoothGattDescriptor writtenDescriptor = mDescriptorCaptor.getValue();
        assertThat(writtenDescriptor.getUuid())
                .isEqualTo(ReservedUuids.Descriptors.CLIENT_CHARACTERISTIC_CONFIGURATION);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_getNotificationEnabled_notification_enable() throws Exception {
        mBluetoothGattConnection.setNotificationEnabled(mMockBluetoothGattCharacteristic, true);

        verify(mMockBluetoothGattWrapper)
                .setCharacteristicNotification(mMockBluetoothGattCharacteristic, true);
        verify(mMockBluetoothOperationExecutor).execute(mOperationCaptor.capture(), anyLong());
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).writeDescriptor(mDescriptorCaptor.capture(),
                eq(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE));
        BluetoothGattDescriptor writtenDescriptor = mDescriptorCaptor.getValue();
        assertThat(writtenDescriptor.getUuid())
                .isEqualTo(ReservedUuids.Descriptors.CLIENT_CHARACTERISTIC_CONFIGURATION);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_setNotificationEnabled_indication_disable() throws Exception {
        when(mMockBluetoothGattCharacteristic.getProperties())
                .thenReturn(BluetoothGattCharacteristic.PROPERTY_INDICATE);

        mBluetoothGattConnection.setNotificationEnabled(mMockBluetoothGattCharacteristic, false);

        verify(mMockBluetoothGattWrapper)
                .setCharacteristicNotification(mMockBluetoothGattCharacteristic, false);
        verify(mMockBluetoothOperationExecutor).execute(mOperationCaptor.capture(), anyLong());
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).writeDescriptor(mDescriptorCaptor.capture(),
                eq(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE));
        BluetoothGattDescriptor writtenDescriptor = mDescriptorCaptor.getValue();
        assertThat(writtenDescriptor.getUuid())
                .isEqualTo(ReservedUuids.Descriptors.CLIENT_CHARACTERISTIC_CONFIGURATION);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_setNotificationEnabled_notification_disable() throws Exception {
        mBluetoothGattConnection.setNotificationEnabled(mMockBluetoothGattCharacteristic, false);

        verify(mMockBluetoothGattWrapper)
                .setCharacteristicNotification(mMockBluetoothGattCharacteristic, false);
        verify(mMockBluetoothOperationExecutor).execute(mOperationCaptor.capture(), anyLong());
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).writeDescriptor(mDescriptorCaptor.capture(),
                eq(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE));
        BluetoothGattDescriptor writtenDescriptor = mDescriptorCaptor.getValue();
        assertThat(writtenDescriptor.getUuid())
                .isEqualTo(ReservedUuids.Descriptors.CLIENT_CHARACTERISTIC_CONFIGURATION);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_setNotificationEnabled_failure() throws Exception {
        when(mMockBluetoothGattCharacteristic.getProperties())
                .thenReturn(BluetoothGattCharacteristic.PROPERTY_READ);

        try {
            mBluetoothGattConnection.setNotificationEnabled(mMockBluetoothGattCharacteristic,
                    true);
            fail("BluetoothException was expected");
        } catch (BluetoothException expected) {
        }
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_enableNotification_Uuid() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new SynchronousOperation<>(
                        mMockBluetoothOperationExecutor,
                        OperationType.NOTIFICATION_CHANGE,
                        mMockBluetoothGattCharacteristic)))
                .thenReturn(mMockChangeObserver);
        mBluetoothGattConnection.enableNotification(SERVICE_UUID, CHARACTERISTIC_UUID);

        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mSynchronousOperationCaptor.capture());
        ((ChangeObserver) mSynchronousOperationCaptor.getValue().call())
                .setListener(mMockCharChangeListener);
        mBluetoothGattConnection.onCharacteristicChanged(mMockBluetoothGattCharacteristic, DATA);
        verify(mMockCharChangeListener).onValueChange(DATA);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_enableNotification() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new SynchronousOperation<>(
                        mMockBluetoothOperationExecutor,
                        OperationType.NOTIFICATION_CHANGE,
                        mMockBluetoothGattCharacteristic)))
                .thenReturn(mMockChangeObserver);
        mBluetoothGattConnection.enableNotification(mMockBluetoothGattCharacteristic);

        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mSynchronousOperationCaptor.capture());
        ((ChangeObserver) mSynchronousOperationCaptor.getValue().call())
                .setListener(mMockCharChangeListener);

        mBluetoothGattConnection.onCharacteristicChanged(mMockBluetoothGattCharacteristic, DATA);

        verify(mMockCharChangeListener).onValueChange(DATA);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_enableNotification_observe() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new SynchronousOperation<>(
                        mMockBluetoothOperationExecutor,
                        OperationType.NOTIFICATION_CHANGE,
                        mMockBluetoothGattCharacteristic)))
                .thenReturn(mMockChangeObserver);
        mBluetoothGattConnection.enableNotification(mMockBluetoothGattCharacteristic);

        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mSynchronousOperationCaptor.capture());
        ChangeObserver changeObserver = (ChangeObserver) mSynchronousOperationCaptor.getValue()
                .call();
        mBluetoothGattConnection.onCharacteristicChanged(mMockBluetoothGattCharacteristic, DATA);
        assertThat(changeObserver.waitForUpdate(TimeUnit.SECONDS.toMillis(1))).isEqualTo(DATA);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_disableNotification_Uuid() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new SynchronousOperation<>(
                        OperationType.NOTIFICATION_CHANGE, mMockBluetoothGattCharacteristic)))
                .thenReturn(mMockChangeObserver);
        mBluetoothGattConnection
                .enableNotification(SERVICE_UUID, CHARACTERISTIC_UUID)
                .setListener(mMockCharChangeListener);

        mBluetoothGattConnection.disableNotification(SERVICE_UUID, CHARACTERISTIC_UUID);

        mBluetoothGattConnection.onCharacteristicChanged(mMockBluetoothGattCharacteristic, DATA);
        verify(mMockCharChangeListener, never()).onValueChange(DATA);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_disableNotification() throws Exception {
        when(mMockBluetoothOperationExecutor.executeNonnull(
                new SynchronousOperation<ChangeObserver>(
                        OperationType.NOTIFICATION_CHANGE, mMockBluetoothGattCharacteristic)))
                .thenReturn(mMockChangeObserver);
        mBluetoothGattConnection
                .enableNotification(mMockBluetoothGattCharacteristic)
                .setListener(mMockCharChangeListener);
        verify(mMockBluetoothOperationExecutor)
                .executeNonnull(mSynchronousOperationCaptor.capture());
        mSynchronousOperationCaptor.getValue().call();

        mBluetoothGattConnection.disableNotification(mMockBluetoothGattCharacteristic);
        verify(mMockBluetoothOperationExecutor).execute(mSynchronousOperationCaptor.capture());
        mSynchronousOperationCaptor.getValue().call();

        mBluetoothGattConnection.onCharacteristicChanged(mMockBluetoothGattCharacteristic, DATA);
        verify(mMockCharChangeListener, never()).onValueChange(DATA);
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_addCloseListener() throws Exception {
        mBluetoothGattConnection.addCloseListener(mMockConnectionCloseListener);

        mBluetoothGattConnection.onClosed();
        verify(mMockConnectionCloseListener).onClose();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_removeCloseListener() throws Exception {
        mBluetoothGattConnection.addCloseListener(mMockConnectionCloseListener);

        mBluetoothGattConnection.removeCloseListener(mMockConnectionCloseListener);

        mBluetoothGattConnection.onClosed();
        verify(mMockConnectionCloseListener, never()).onClose();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_close() throws Exception {
        mBluetoothGattConnection.close();

        verify(mMockBluetoothOperationExecutor)
                .execute(mOperationCaptor.capture(),
                        eq(BluetoothGattConnection.OPERATION_TIMEOUT_MILLIS));
        mOperationCaptor.getValue().run();
        verify(mMockBluetoothGattWrapper).disconnect();
        verify(mMockBluetoothGattWrapper).close();
    }

    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void test_onClosed() throws Exception {
        mBluetoothGattConnection.onClosed();

        verify(mMockBluetoothOperationExecutor, never())
                .execute(mOperationCaptor.capture(), anyLong());
        verify(mMockBluetoothGattWrapper).close();
    }
}
