/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.hal;

import static com.android.car.hal.HalPropValueMatcher.isProperty;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.hardware.automotive.vehicle.StatusCode;
import android.hardware.automotive.vehicle.SubscribeOptions;
import android.hardware.automotive.vehicle.VehiclePropError;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.test.TestLooper;

import com.android.car.VehicleStub;
import com.android.car.VehicleStub.SubscriptionClient;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;

public final class HalClientUnitTest extends AbstractExtendedMockitoTestCase {

    private static final int WAIT_CAP_FOR_RETRIABLE_RESULT_MS = 100;
    private static final int SLEEP_BETWEEN_RETRIABLE_INVOKES_MS = 50;

    private static final int PROP = 42;
    private static final int AREA_ID = 108;

    private final HalPropValueBuilder mPropValueBuilder = new HalPropValueBuilder(/*isAidl=*/true);
    private final HalPropValue mProp = mPropValueBuilder.build(PROP, AREA_ID);

    @Mock VehicleStub mIVehicle;
    @Mock HalClientCallback mHalClientCallback;
    @Mock SubscriptionClient mSubscriptionClient;

    private HalClient mClient;
    private TestLooper mLooper = new TestLooper();

    public HalClientUnitTest() {
        super(HalClient.TAG);
    }

    @Before
    public void setFixtures() {
        when(mIVehicle.newSubscriptionClient(any())).thenReturn(mSubscriptionClient);
        mClient = new HalClient(mIVehicle, mLooper.getLooper(), mHalClientCallback,
                WAIT_CAP_FOR_RETRIABLE_RESULT_MS, SLEEP_BETWEEN_RETRIABLE_INVOKES_MS);
    }

    @Test
    public void testSet_remoteExceptionThenFail() throws Exception {
        doThrow(new RemoteException("Never give up, never surrender!"))
            .doThrow(new RemoteException("D'OH!"))
            .when(mIVehicle).set(isProperty(PROP));

        Exception actualException = expectThrows(ServiceSpecificException.class,
                () -> mClient.setValue(mProp));

        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(PROP));
        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(AREA_ID));
    }

    @Test
    public void testSet_remoteExceptionThenOk() throws Exception {
        doThrow(new RemoteException("Never give up, never surrender!"))
            .doNothing()
            .when(mIVehicle).set(isProperty(PROP));


        mClient.setValue(mProp);
    }

    @Test
    public void testSet_invalidArgument() throws Exception {
        doThrow(new ServiceSpecificException(StatusCode.INVALID_ARG))
                .when(mIVehicle).set(isProperty(PROP));

        Exception actualException = expectThrows(IllegalArgumentException.class,
                () -> mClient.setValue(mProp));

        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(PROP));
        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(AREA_ID));
    }

    @Test
    public void testSet_otherError() throws Exception {
        doThrow(new ServiceSpecificException(StatusCode.INTERNAL_ERROR))
            .when(mIVehicle).set(isProperty(PROP));

        Exception actualException = expectThrows(ServiceSpecificException.class,
                () -> mClient.setValue(mProp));

        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(PROP));
        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(AREA_ID));
    }

    @Test
    public void testSet_ok() throws Exception {
        doNothing().when(mIVehicle).set(isProperty(PROP));

        mClient.setValue(mProp);
    }

    @Test
    public void testGet_remoteExceptionThenFail() throws Exception {
        when(mIVehicle.get(isProperty(PROP)))
            .thenThrow(new RemoteException("Never give up, never surrender!"))
            .thenThrow(new RemoteException("D'OH!"));

        Exception actualException = expectThrows(ServiceSpecificException.class,
                () -> mClient.getValue(mProp));

        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(PROP));
        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(AREA_ID));
    }

    @Test
    public void testGet_remoteExceptionThenOk() throws Exception {
        HalPropValue propValue = mock(HalPropValue.class);
        when(mIVehicle.get(isProperty(PROP)))
            .thenThrow(new RemoteException("Never give up, never surrender!"))
            .thenReturn(propValue);

        HalPropValue gotValue = mClient.getValue(mProp);

        assertThat(gotValue).isEqualTo(propValue);
    }

    @Test
    public void testGet_invalidArgument() throws Exception {
        when(mIVehicle.get(isProperty(PROP))).thenThrow(new ServiceSpecificException(
                StatusCode.INVALID_ARG));

        Exception actualException = expectThrows(IllegalArgumentException.class,
                () -> mClient.getValue(mProp));

        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(PROP));
        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(AREA_ID));
    }

    @Test
    public void testGet_otherError() throws Exception {
        when(mIVehicle.get(isProperty(PROP))).thenThrow(new ServiceSpecificException(
                StatusCode.INTERNAL_ERROR));

        Exception actualException = expectThrows(ServiceSpecificException.class,
                () -> mClient.getValue(mProp));

        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(PROP));
        assertThat(actualException).hasMessageThat().contains(Integer.toHexString(AREA_ID));
    }

    @Test
    public void testGet_returnNull() throws Exception {
        when(mIVehicle.get(isProperty(PROP))).thenReturn(null);

        ServiceSpecificException actualException = expectThrows(ServiceSpecificException.class,
                () -> mClient.getValue(mProp));

        assertThat(actualException.errorCode).isEqualTo(StatusCode.NOT_AVAILABLE);
    }

    @Test
    public void testGet_ok() throws Exception {
        HalPropValue propValue = mock(HalPropValue.class);
        when(mIVehicle.get(isProperty(PROP))).thenReturn(propValue);

        HalPropValue gotValue = mClient.getValue(mProp);

        assertThat(gotValue).isEqualTo(propValue);
    }

    @Test
    public void testGetAllPropConfigs() throws Exception {
        HalPropConfig config = mock(HalPropConfig.class);
        HalPropConfig[] configs = new HalPropConfig[]{config};
        when(mIVehicle.getAllPropConfigs()).thenReturn(configs);

        assertThat(mClient.getAllPropConfigs()).isEqualTo(configs);
    }

    @Test
    public void testSubscribe() throws Exception {
        SubscribeOptions option = mock(SubscribeOptions.class);

        mClient.subscribe(option);

        verify(mSubscriptionClient).subscribe(new SubscribeOptions[]{option});
    }

    @Test
    public void testUnsubscribe() throws Exception {
        SubscribeOptions option = mock(SubscribeOptions.class);

        mClient.unsubscribe(1);

        verify(mSubscriptionClient).unsubscribe(1);
    }

    @Test
    public void testInternalCallbackOnPropertyEvent() throws Exception {
        HalClientCallback callback = mClient.getInternalCallback();

        HalPropValue propValue1 = mock(HalPropValue.class);
        HalPropValue propValue2 = mock(HalPropValue.class);
        ArrayList<HalPropValue> values = new ArrayList<HalPropValue>(
                Arrays.asList(propValue1, propValue2));

        callback.onPropertyEvent(values);

        mLooper.dispatchAll();

        verify(mHalClientCallback).onPropertyEvent(values);
    }

    @Test
    public void testInternalCallbackOnPropertySetError() throws Exception {
        HalClientCallback callback = mClient.getInternalCallback();

        VehiclePropError error1 = mock(VehiclePropError.class);
        VehiclePropError error2 = mock(VehiclePropError.class);
        ArrayList<VehiclePropError> errors = new ArrayList<VehiclePropError>(
                Arrays.asList(error1, error2));

        callback.onPropertySetError(errors);

        mLooper.dispatchAll();

        verify(mHalClientCallback).onPropertySetError(errors);
    }
}
