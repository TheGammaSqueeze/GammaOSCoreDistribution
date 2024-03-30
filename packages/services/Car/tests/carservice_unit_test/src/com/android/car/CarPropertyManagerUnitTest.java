/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car;

import static android.car.VehiclePropertyIds.HVAC_TEMPERATURE_SET;
import static android.car.hardware.property.CarPropertyManager.SENSOR_RATE_ONCHANGE;

import static com.android.car.internal.property.CarPropertyHelper.SYNC_OP_LIMIT_TRY_AGAIN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarInternalErrorException;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.ICarProperty;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

/**
 * <p>This class contains unit tests for the {@link CarPropertyManager}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class CarPropertyManagerUnitTest {
    private static final int CONTINUOUS_PROPERTY = 111;
    private static final int ON_CHANGE_PROPERTY = 222;
    private static final int STATIC_PROPERTY = 333;
    private static final float MIN_UPDATE_RATE_HZ = 10;
    private static final float MAX_UPDATE_RATE_HZ = 100;
    private static final float FIRST_UPDATE_RATE_HZ = 50;
    private static final float LARGER_UPDATE_RATE_HZ = 50.1f;
    private static final float SMALLER_UPDATE_RATE_HZ = 49.9f;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Mock
    private Car mCar;
    @Mock
    private ApplicationInfo mApplicationInfo;
    @Mock
    private ICarProperty mICarProperty;
    @Mock
    private Context mContext;
    @Mock
    private CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback;
    @Mock
    private CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback2;
    @Mock
    private CarPropertyConfig mContinuousCarPropertyConfig;
    @Mock
    private CarPropertyConfig mOnChangeCarPropertyConfig;
    @Mock
    private CarPropertyConfig mStaticCarPropertyConfig;

    @Captor
    private ArgumentCaptor<Integer> mPropertyIdCaptor;
    @Captor
    private ArgumentCaptor<Float> mUpdateRateHzCaptor;

    private CarPropertyManager mCarPropertyManager;

    private static List<CarPropertyEvent> createErrorCarPropertyEventList() {
        CarPropertyValue<Integer> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0,
                CarPropertyValue.STATUS_ERROR, 0, -1);
        CarPropertyEvent carPropertyEvent = new CarPropertyEvent(
                CarPropertyEvent.PROPERTY_EVENT_ERROR, value,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
        return List.of(carPropertyEvent);
    }

    private static List<CarPropertyEvent> createCarPropertyEventList() {
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0, 17.0f);
        CarPropertyEvent carPropertyEvent = new CarPropertyEvent(
                CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE, value);
        return List.of(carPropertyEvent);
    }

    @Before
    public void setUp() throws RemoteException {
        when(mCar.getContext()).thenReturn(mContext);
        when(mCar.getEventHandler()).thenReturn(mMainHandler);
        when(mContext.getApplicationInfo()).thenReturn(mApplicationInfo);
        when(mContinuousCarPropertyConfig.getChangeMode()).thenReturn(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS);
        when(mContinuousCarPropertyConfig.getMinSampleRate()).thenReturn(MIN_UPDATE_RATE_HZ);
        when(mContinuousCarPropertyConfig.getMaxSampleRate()).thenReturn(MAX_UPDATE_RATE_HZ);
        when(mOnChangeCarPropertyConfig.getChangeMode()).thenReturn(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE);
        when(mStaticCarPropertyConfig.getChangeMode()).thenReturn(
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC);
        when(mICarProperty.getPropertyConfigList(new int[]{CONTINUOUS_PROPERTY})).thenReturn(
                ImmutableList.of(mContinuousCarPropertyConfig));
        when(mICarProperty.getPropertyConfigList(new int[]{ON_CHANGE_PROPERTY})).thenReturn(
                ImmutableList.of(mOnChangeCarPropertyConfig));
        when(mICarProperty.getPropertyConfigList(new int[]{STATIC_PROPERTY})).thenReturn(
                ImmutableList.of(mStaticCarPropertyConfig));
        mCarPropertyManager = new CarPropertyManager(mCar, mICarProperty);
    }

    private void setAppTargetSdk(int appTargetSdk) {
        mApplicationInfo.targetSdkVersion = appTargetSdk;
        mCarPropertyManager = new CarPropertyManager(mCar, mICarProperty);
    }

    @Test
    public void getProperty_returnsValue() throws RemoteException {
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenReturn(value);

        assertThat(mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0)).isEqualTo(value);
    }

    @Test
    public void setProperty_setsValue() throws RemoteException {
        mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f);

        ArgumentCaptor<CarPropertyValue> value = ArgumentCaptor.forClass(CarPropertyValue.class);

        verify(mICarProperty).setProperty(value.capture(), any());
        assertThat(value.getValue().getValue()).isEqualTo(17.0f);
    }

    @Test
    public void registerCallback_returnsFalseIfPropertyIdNotSupportedInVehicle()
            throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback,
                VehiclePropertyIds.INVALID, FIRST_UPDATE_RATE_HZ)).isFalse();
        verify(mICarProperty, never()).registerListener(anyInt(), anyFloat(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void registerCallback_registersWithServiceOnFirstCallback() throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty).registerListener(eq(CONTINUOUS_PROPERTY), eq(FIRST_UPDATE_RATE_HZ),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void registerCallback_registersWithMaxUpdateRateOnFirstCallback()
            throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        MAX_UPDATE_RATE_HZ + 1)).isTrue();
        verify(mICarProperty).registerListener(eq(CONTINUOUS_PROPERTY), eq(MAX_UPDATE_RATE_HZ),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void registerCallback_registersWithMinUpdateRateOnFirstCallback()
            throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        MIN_UPDATE_RATE_HZ - 1)).isTrue();
        verify(mICarProperty).registerListener(eq(CONTINUOUS_PROPERTY), eq(MIN_UPDATE_RATE_HZ),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void registerCallback_registersWithOnChangeRateForOnChangeProperty()
            throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, ON_CHANGE_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty).registerListener(eq(ON_CHANGE_PROPERTY),
                eq(CarPropertyManager.SENSOR_RATE_ONCHANGE), any(ICarPropertyEventListener.class));
    }

    @Test
    public void registerCallback_registersWithOnChangeRateForStaticProperty()
            throws RemoteException {
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback, STATIC_PROPERTY,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty).registerListener(eq(STATIC_PROPERTY),
                eq(CarPropertyManager.SENSOR_RATE_ONCHANGE), any(ICarPropertyEventListener.class));
    }

    @Test
    public void registerCallback_returnsFalseForRemoteException() throws RemoteException {
        RemoteException remoteException = new RemoteException();
        doThrow(remoteException).when(mICarProperty).registerListener(eq(ON_CHANGE_PROPERTY),
                eq(CarPropertyManager.SENSOR_RATE_ONCHANGE), any(ICarPropertyEventListener.class));
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, ON_CHANGE_PROPERTY,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE)).isFalse();
    }

    @Test
    public void registerCallback_recoversAfterFirstRemoteException() throws RemoteException {
        RemoteException remoteException = new RemoteException();
        doThrow(remoteException).doNothing().when(mICarProperty).registerListener(
                eq(ON_CHANGE_PROPERTY), eq(CarPropertyManager.SENSOR_RATE_ONCHANGE),
                any(ICarPropertyEventListener.class));
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, ON_CHANGE_PROPERTY,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE)).isFalse();
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, ON_CHANGE_PROPERTY,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        verify(mICarProperty, times(2)).registerListener(eq(ON_CHANGE_PROPERTY),
                eq(CarPropertyManager.SENSOR_RATE_ONCHANGE), any(ICarPropertyEventListener.class));
    }

    @Test
    public void registerCallback_registersTwiceWithHigherRateCallback() throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback2,
                CONTINUOUS_PROPERTY, LARGER_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty, times(2)).registerListener(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(CONTINUOUS_PROPERTY,
                CONTINUOUS_PROPERTY);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                LARGER_UPDATE_RATE_HZ);
    }

    @Test
    public void registerCallback_registersOnSecondLowerRateWithSameCallback()
            throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        SMALLER_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty, times(2)).registerListener(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(CONTINUOUS_PROPERTY,
                CONTINUOUS_PROPERTY);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                SMALLER_UPDATE_RATE_HZ);
    }

    @Test
    public void registerCallback_doesNotRegistersOnSecondLowerRateCallback()
            throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback2,
                CONTINUOUS_PROPERTY, SMALLER_UPDATE_RATE_HZ)).isTrue();
        verify(mICarProperty).registerListener(eq(CONTINUOUS_PROPERTY), eq(FIRST_UPDATE_RATE_HZ),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void registerCallback_registersTwiceForDifferentProperties() throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, ON_CHANGE_PROPERTY,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        verify(mICarProperty, times(2)).registerListener(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(CONTINUOUS_PROPERTY,
                ON_CHANGE_PROPERTY);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                CarPropertyManager.SENSOR_RATE_ONCHANGE);
    }

    @Test
    public void unregisterCallback_doesNothingIfNothingRegistered() throws RemoteException {
        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback, STATIC_PROPERTY);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void unregisterCallback_doesNothingIfPropertyIsNotRegisteredForCallback()
            throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback, STATIC_PROPERTY);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void unregisterCallback_doesNothingIfCallbackIsNotRegisteredForProperty()
            throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback2, CONTINUOUS_PROPERTY);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void unregisterCallback_unregistersCallbackForSingleProperty() throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY);
        verify(mICarProperty).unregisterListener(eq(CONTINUOUS_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void unregisterCallback_unregistersCallbackForSpecificProperty() throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, ON_CHANGE_PROPERTY,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback, ON_CHANGE_PROPERTY);
        verify(mICarProperty).unregisterListener(eq(ON_CHANGE_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void unregisterCallback_unregistersCallbackForBothProperties() throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, ON_CHANGE_PROPERTY,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);
        verify(mICarProperty, times(2)).unregisterListener(mPropertyIdCaptor.capture(),
                any(ICarPropertyEventListener.class));
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(CONTINUOUS_PROPERTY,
                ON_CHANGE_PROPERTY);
    }

    @Test
    public void unregisterCallback_unregistersAllCallbackForSingleProperty()
            throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback2, ON_CHANGE_PROPERTY,
                        CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);
        verify(mICarProperty).unregisterListener(eq(CONTINUOUS_PROPERTY),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void unregisterCallback_unregistersUpdatesRegisteredRateHz() throws RemoteException {
        assertThat(
                mCarPropertyManager.registerCallback(mCarPropertyEventCallback, CONTINUOUS_PROPERTY,
                        FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyManager.registerCallback(mCarPropertyEventCallback2,
                CONTINUOUS_PROPERTY, LARGER_UPDATE_RATE_HZ)).isTrue();

        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback2);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
        verify(mICarProperty, times(3)).registerListener(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture(), any(ICarPropertyEventListener.class));
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(CONTINUOUS_PROPERTY,
                CONTINUOUS_PROPERTY, CONTINUOUS_PROPERTY);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                LARGER_UPDATE_RATE_HZ, FIRST_UPDATE_RATE_HZ);
    }

    @Test
    public void unregisterCallback_doesNothingWithPropertyIdIfNothingRegistered()
            throws RemoteException {
        mCarPropertyManager.unregisterCallback(mCarPropertyEventCallback);
        verify(mICarProperty, never()).unregisterListener(anyInt(),
                any(ICarPropertyEventListener.class));
    }

    @Test
    public void onErrorEvent_callbackIsCalledWithErrorEvent() throws RemoteException {
        List<CarPropertyEvent> eventList = createErrorCarPropertyEventList();
        List<CarPropertyConfig> configs = List.of(
                CarPropertyConfig.newBuilder(Float.class, HVAC_TEMPERATURE_SET,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL).build());
        when(mICarProperty.getPropertyConfigList(new int[]{HVAC_TEMPERATURE_SET})).thenReturn(
                configs);
        ICarPropertyEventListener listener = getCarPropertyEventListener();

        listener.onEvent(eventList);

        // Wait until we get the on error event for the initial value.
        verify(mCarPropertyEventCallback, timeout(5000)).onErrorEvent(HVAC_TEMPERATURE_SET, 0,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_UNKNOWN);
    }

    @Test
    public void onChangeEvent_callbackIsCalledWithEvent() throws RemoteException {
        List<CarPropertyEvent> eventList = createCarPropertyEventList();
        List<CarPropertyConfig> configs = List.of(
                CarPropertyConfig.newBuilder(Float.class, HVAC_TEMPERATURE_SET,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL).build());
        when(mICarProperty.getPropertyConfigList(new int[]{HVAC_TEMPERATURE_SET})).thenReturn(
                configs);
        ICarPropertyEventListener listener = getCarPropertyEventListener();
        ArgumentCaptor<CarPropertyValue> value = ArgumentCaptor.forClass(CarPropertyValue.class);

        listener.onEvent(eventList);

        // Wait until we get the on property change event for the initial value.
        verify(mCarPropertyEventCallback, timeout(5000)).onChangeEvent(value.capture());
        assertThat(value.getValue().getPropertyId()).isEqualTo(HVAC_TEMPERATURE_SET);
        assertThat(value.getValue().getValue()).isEqualTo(17.0f);
    }

    private ICarPropertyEventListener getCarPropertyEventListener() throws RemoteException {
        ArgumentCaptor<ICarPropertyEventListener> carPropertyEventListenerArgumentCaptor =
                ArgumentCaptor.forClass(ICarPropertyEventListener.class);
        mCarPropertyManager.registerCallback(mCarPropertyEventCallback, HVAC_TEMPERATURE_SET,
                SENSOR_RATE_ONCHANGE);

        verify(mICarProperty).registerListener(eq(HVAC_TEMPERATURE_SET), eq(SENSOR_RATE_ONCHANGE),
                carPropertyEventListenerArgumentCaptor.capture());

        return carPropertyEventListenerArgumentCaptor.getValue();
    }

    @Test
    public void testGetProperty_syncOpTryAgain() throws RemoteException {
        CarPropertyValue<Float> value = new CarPropertyValue<>(HVAC_TEMPERATURE_SET, 0, 17.0f);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(SYNC_OP_LIMIT_TRY_AGAIN)).thenReturn(value);

        assertThat(mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0)).isEqualTo(value);
        verify(mICarProperty, times(2)).getProperty(HVAC_TEMPERATURE_SET, 0);
    }

    @Test
    public void testGetProperty_syncOpTryAgain_exceedRetryCountLimit() throws RemoteException {
        setAppTargetSdk(Build.VERSION_CODES.R);

        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(SYNC_OP_LIMIT_TRY_AGAIN));

        assertThrows(CarInternalErrorException.class, () ->
                mCarPropertyManager.getProperty(HVAC_TEMPERATURE_SET, 0));
        verify(mICarProperty, times(10)).getProperty(HVAC_TEMPERATURE_SET, 0);
    }

    @Test
    public void testSetProperty_syncOpTryAgain() throws RemoteException {
        doThrow(new ServiceSpecificException(SYNC_OP_LIMIT_TRY_AGAIN)).doNothing()
                .when(mICarProperty).setProperty(any(), any());

        mCarPropertyManager.setProperty(Float.class, HVAC_TEMPERATURE_SET, 0, 17.0f);

        verify(mICarProperty, times(2)).setProperty(any(), any());
    }

    @Test
    public void testIsPropertyAvailable_syncOpTryAgain() throws Exception {
        CarPropertyValue<Integer> expectedValue = new CarPropertyValue<>(HVAC_TEMPERATURE_SET,
                /* areaId= */ 0, CarPropertyValue.STATUS_AVAILABLE, /* timestamp= */ 0,
                /* value= */ 1);
        when(mICarProperty.getProperty(HVAC_TEMPERATURE_SET, 0)).thenThrow(
                new ServiceSpecificException(SYNC_OP_LIMIT_TRY_AGAIN)).thenReturn(expectedValue);

        assertThat(mCarPropertyManager.isPropertyAvailable(HVAC_TEMPERATURE_SET, /* areaId= */ 0))
                .isTrue();
        verify(mICarProperty, times(2)).getProperty(HVAC_TEMPERATURE_SET, 0);
    }

}
