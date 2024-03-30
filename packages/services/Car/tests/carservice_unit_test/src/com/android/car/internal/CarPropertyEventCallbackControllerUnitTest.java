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

package com.android.car.internal;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.CarPropertyManager;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;


public final class CarPropertyEventCallbackControllerUnitTest {
    private static final int PROPERTY_ID = 1234;
    private static final int AREA_ID_1 = 908;
    private static final int AREA_ID_2 = 304;
    private static final Float FIRST_UPDATE_RATE_HZ = 1F;
    private static final Float SECOND_BIGGER_UPDATE_RATE_HZ = 2F;
    private static final Float SECOND_SMALLER_UPDATE_RATE_HZ = 0.5F;
    private static final long TIMESTAMP_NANOS = Duration.ofSeconds(1).toNanos();
    private static final long FRESH_TIMESTAMP_NANOS = Duration.ofSeconds(2).toNanos();
    private static final long ALMOST_FRESH_TIMESTAMP_NANOS = Duration.ofMillis(1999).toNanos();
    private static final long STALE_TIMESTAMP_NANOS = Duration.ofMillis(500).toNanos();
    private static final Integer INTEGER_VALUE_1 = 8438;
    private static final Integer INTEGER_VALUE_2 = 4834;
    private static final CarPropertyValue<Integer> GOOD_CAR_PROPERTY_VALUE = new CarPropertyValue<>(
            PROPERTY_ID, AREA_ID_1, CarPropertyValue.STATUS_AVAILABLE, TIMESTAMP_NANOS,
            INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> FRESH_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(PROPERTY_ID, AREA_ID_1, CarPropertyValue.STATUS_AVAILABLE,
                    FRESH_TIMESTAMP_NANOS, INTEGER_VALUE_2);
    private static final CarPropertyValue<Integer> ALMOST_FRESH_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(PROPERTY_ID, AREA_ID_1, CarPropertyValue.STATUS_AVAILABLE,
                    ALMOST_FRESH_TIMESTAMP_NANOS, INTEGER_VALUE_2);
    private static final CarPropertyValue<Integer> STALE_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(PROPERTY_ID, AREA_ID_1, CarPropertyValue.STATUS_AVAILABLE,
                    STALE_TIMESTAMP_NANOS, INTEGER_VALUE_2);
    private static final CarPropertyValue<Integer> STALE_CAR_PROPERTY_VALUE_WITH_DIFFERENT_AREA_ID =
            new CarPropertyValue<>(PROPERTY_ID, AREA_ID_2, CarPropertyValue.STATUS_AVAILABLE,
                    STALE_TIMESTAMP_NANOS, INTEGER_VALUE_1);
    private static final CarPropertyValue<Integer> ERROR_CAR_PROPERTY_VALUE =
            new CarPropertyValue<>(PROPERTY_ID, AREA_ID_1, CarPropertyValue.STATUS_ERROR,
                    TIMESTAMP_NANOS, null);
    @Rule
    public final MockitoRule mockito = MockitoJUnit.rule();
    @Mock
    private CarPropertyEventCallbackController.RegistrationUpdateCallback
            mRegistrationUpdateCallback;
    @Captor
    private ArgumentCaptor<Integer> mPropertyIdCaptor;
    @Captor
    private ArgumentCaptor<Float> mUpdateRateHzCaptor;
    @Captor
    private ArgumentCaptor<CarPropertyValue> mCarPropertyValueCaptor;
    @Mock
    private CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback;
    @Mock
    private CarPropertyManager.CarPropertyEventCallback mCarPropertyEventCallback2;
    private CarPropertyEventCallbackController mCarPropertyEventCallbackController;

    @Before
    public void setUp() {
        when(mRegistrationUpdateCallback.register(anyInt(), anyFloat())).thenReturn(true);
        mCarPropertyEventCallbackController = new CarPropertyEventCallbackController(PROPERTY_ID,
                new Object(), mRegistrationUpdateCallback);
    }

    @Test
    public void add_registerCalledAfterFirstAdd() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();

        verify(mRegistrationUpdateCallback).register(PROPERTY_ID, FIRST_UPDATE_RATE_HZ);
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
    }

    @Test
    public void add_registerCalledIfSecondRateIsBiggerWithSameCallback() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                SECOND_BIGGER_UPDATE_RATE_HZ)).isTrue();

        verify(mRegistrationUpdateCallback, times(2)).register(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture());
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(PROPERTY_ID, PROPERTY_ID);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                SECOND_BIGGER_UPDATE_RATE_HZ);
    }

    @Test
    public void add_registerCalledIfSecondRateIsSmallerWithSameCallback() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                SECOND_SMALLER_UPDATE_RATE_HZ)).isTrue();

        verify(mRegistrationUpdateCallback, times(2)).register(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture());
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(PROPERTY_ID, PROPERTY_ID);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                SECOND_SMALLER_UPDATE_RATE_HZ);
    }

    @Test
    public void add_registerCalledIfSecondRateIsBiggerWithDifferentCallback() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback2,
                SECOND_BIGGER_UPDATE_RATE_HZ)).isTrue();

        verify(mRegistrationUpdateCallback, times(2)).register(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture());
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(PROPERTY_ID, PROPERTY_ID);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                SECOND_BIGGER_UPDATE_RATE_HZ);
    }

    @Test
    public void add_registerNotCalledIfSecondRateIsSmallerWithDifferentCallback() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback2,
                SECOND_SMALLER_UPDATE_RATE_HZ)).isTrue();

        verify(mRegistrationUpdateCallback).register(PROPERTY_ID, FIRST_UPDATE_RATE_HZ);
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
    }

    @Test
    public void add_returnsFalseIfRegistrationCallbackReturnsFalse() {
        when(mRegistrationUpdateCallback.register(PROPERTY_ID, FIRST_UPDATE_RATE_HZ)).thenReturn(
                false);
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isFalse();

        verify(mRegistrationUpdateCallback).register(PROPERTY_ID, FIRST_UPDATE_RATE_HZ);
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
    }

    @Test
    public void add_registersAgainIfTheFirstCallbackReturnsFalse() {
        when(mRegistrationUpdateCallback.register(PROPERTY_ID, FIRST_UPDATE_RATE_HZ)).thenReturn(
                false, true);
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isFalse();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();

        verify(mRegistrationUpdateCallback, times(2)).register(PROPERTY_ID, FIRST_UPDATE_RATE_HZ);
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
    }

    @Test
    public void add_restoresOriginalRateHzIfTheSecondCallbackReturnsFalse() {
        when(mRegistrationUpdateCallback.register(PROPERTY_ID, FIRST_UPDATE_RATE_HZ)).thenReturn(
                true);
        when(mRegistrationUpdateCallback.register(PROPERTY_ID,
                SECOND_BIGGER_UPDATE_RATE_HZ)).thenReturn(false);

        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        GOOD_CAR_PROPERTY_VALUE));
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                SECOND_BIGGER_UPDATE_RATE_HZ)).isFalse();
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        ALMOST_FRESH_CAR_PROPERTY_VALUE));

        verify(mRegistrationUpdateCallback).register(PROPERTY_ID, FIRST_UPDATE_RATE_HZ);
        verify(mRegistrationUpdateCallback).register(PROPERTY_ID, SECOND_BIGGER_UPDATE_RATE_HZ);
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
        verify(mCarPropertyEventCallback).onChangeEvent(mCarPropertyValueCaptor.capture());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
        assertThat(mCarPropertyValueCaptor.getAllValues()).containsExactly(GOOD_CAR_PROPERTY_VALUE);
    }

    @Test
    public void remove_noCallbackCalledIfNoCallbacksAdded() {
        assertThat(mCarPropertyEventCallbackController.remove(mCarPropertyEventCallback)).isTrue();

        verify(mRegistrationUpdateCallback, never()).register(anyInt(), anyFloat());
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
    }

    @Test
    public void remove_unregisterCalledIfRemovingSameCallbackAdded() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.remove(mCarPropertyEventCallback)).isTrue();

        verify(mRegistrationUpdateCallback).register(PROPERTY_ID, FIRST_UPDATE_RATE_HZ);
        verify(mRegistrationUpdateCallback).unregister(PROPERTY_ID);
    }

    @Test
    public void remove_unregisterCalledIfRemovingSameCallbackAddedTwice() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                SECOND_BIGGER_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.remove(mCarPropertyEventCallback)).isTrue();

        verify(mRegistrationUpdateCallback, times(2)).register(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture());
        verify(mRegistrationUpdateCallback).unregister(PROPERTY_ID);
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(PROPERTY_ID, PROPERTY_ID);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                SECOND_BIGGER_UPDATE_RATE_HZ);
    }

    @Test
    public void remove_registerCalledIfBiggerRateRemoved() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback2,
                SECOND_BIGGER_UPDATE_RATE_HZ)).isTrue();
        assertThat(
                mCarPropertyEventCallbackController.remove(mCarPropertyEventCallback2)).isFalse();

        verify(mRegistrationUpdateCallback, times(3)).register(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture());
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(PROPERTY_ID, PROPERTY_ID,
                PROPERTY_ID);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                SECOND_BIGGER_UPDATE_RATE_HZ, FIRST_UPDATE_RATE_HZ);
    }

    @Test
    public void remove_registerNotCalledIfSmallerRateRemoved() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback2,
                SECOND_BIGGER_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.remove(mCarPropertyEventCallback)).isFalse();

        verify(mRegistrationUpdateCallback, times(2)).register(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture());
        verify(mRegistrationUpdateCallback, never()).unregister(anyInt());
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(PROPERTY_ID, PROPERTY_ID);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                SECOND_BIGGER_UPDATE_RATE_HZ);
    }

    @Test
    public void remove_returnsTrueIfAllCallbacksRemoved() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback2,
                SECOND_BIGGER_UPDATE_RATE_HZ)).isTrue();
        assertThat(
                mCarPropertyEventCallbackController.remove(mCarPropertyEventCallback2)).isFalse();
        assertThat(mCarPropertyEventCallbackController.remove(mCarPropertyEventCallback)).isTrue();

        verify(mRegistrationUpdateCallback, times(3)).register(mPropertyIdCaptor.capture(),
                mUpdateRateHzCaptor.capture());
        verify(mRegistrationUpdateCallback).unregister(PROPERTY_ID);
        assertThat(mPropertyIdCaptor.getAllValues()).containsExactly(PROPERTY_ID, PROPERTY_ID,
                PROPERTY_ID);
        assertThat(mUpdateRateHzCaptor.getAllValues()).containsExactly(FIRST_UPDATE_RATE_HZ,
                SECOND_BIGGER_UPDATE_RATE_HZ, FIRST_UPDATE_RATE_HZ);
    }

    @Test
    public void forwardPropertyChanged_doesNothingIfNoCallbacksAdded() {
        CarPropertyValue<Integer> carPropertyValue = new CarPropertyValue<>(PROPERTY_ID, AREA_ID_1,
                567);
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        carPropertyValue));

        verify(mCarPropertyEventCallback, never()).onChangeEvent(any());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
        verify(mCarPropertyEventCallback2, never()).onChangeEvent(any());
        verify(mCarPropertyEventCallback2, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback2, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void forwardPropertyChanged_forwardsToCallback() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        GOOD_CAR_PROPERTY_VALUE));

        verify(mCarPropertyEventCallback).onChangeEvent(GOOD_CAR_PROPERTY_VALUE);
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void forwardPropertyChanged_forwardsToMultipleCallbacks() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback2,
                CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        GOOD_CAR_PROPERTY_VALUE));

        verify(mCarPropertyEventCallback).onChangeEvent(GOOD_CAR_PROPERTY_VALUE);
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
        verify(mCarPropertyEventCallback2).onChangeEvent(GOOD_CAR_PROPERTY_VALUE);
        verify(mCarPropertyEventCallback2, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback2, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void forwardPropertyChanged_skipsStaleCarPropertyValues() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        GOOD_CAR_PROPERTY_VALUE));
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        STALE_CAR_PROPERTY_VALUE));

        verify(mCarPropertyEventCallback).onChangeEvent(GOOD_CAR_PROPERTY_VALUE);
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void forwardPropertyChanged_skipsCarPropertyValuesWithNonZeroUpdateRate() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        GOOD_CAR_PROPERTY_VALUE));
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        ALMOST_FRESH_CAR_PROPERTY_VALUE));

        verify(mCarPropertyEventCallback).onChangeEvent(mCarPropertyValueCaptor.capture());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
        assertThat(mCarPropertyValueCaptor.getAllValues()).containsExactly(GOOD_CAR_PROPERTY_VALUE);
    }

    @Test
    public void forwardPropertyChanged_forwardsFreshCarPropertyValues() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        GOOD_CAR_PROPERTY_VALUE));
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        FRESH_CAR_PROPERTY_VALUE));

        verify(mCarPropertyEventCallback, times(2)).onChangeEvent(
                mCarPropertyValueCaptor.capture());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
        assertThat(mCarPropertyValueCaptor.getAllValues()).containsExactly(GOOD_CAR_PROPERTY_VALUE,
                FRESH_CAR_PROPERTY_VALUE);
    }

    @Test
    public void forwardPropertyChanged_forwardsFreshCarPropertyValuesWithNonZeroUpdateRate() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                FIRST_UPDATE_RATE_HZ)).isTrue();
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        GOOD_CAR_PROPERTY_VALUE));
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        FRESH_CAR_PROPERTY_VALUE));

        verify(mCarPropertyEventCallback, times(2)).onChangeEvent(
                mCarPropertyValueCaptor.capture());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
        assertThat(mCarPropertyValueCaptor.getAllValues()).containsExactly(GOOD_CAR_PROPERTY_VALUE,
                FRESH_CAR_PROPERTY_VALUE);
    }

    @Test
    public void forwardPropertyChanged_forwardsStaleCarPropertyValuesWithDifferentAreaId() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        GOOD_CAR_PROPERTY_VALUE));
        mCarPropertyEventCallbackController.forwardPropertyChanged(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        STALE_CAR_PROPERTY_VALUE_WITH_DIFFERENT_AREA_ID));

        verify(mCarPropertyEventCallback, times(2)).onChangeEvent(
                mCarPropertyValueCaptor.capture());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
        assertThat(mCarPropertyValueCaptor.getAllValues()).containsExactly(GOOD_CAR_PROPERTY_VALUE,
                STALE_CAR_PROPERTY_VALUE_WITH_DIFFERENT_AREA_ID);
    }

    @Test
    public void forwardErrorEvent_doesNothingIfNoCallbacksAdded() {
        CarPropertyValue<Integer> carPropertyValue = new CarPropertyValue<>(PROPERTY_ID, AREA_ID_1,
                567);
        mCarPropertyEventCallbackController.forwardErrorEvent(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_ERROR, carPropertyValue));

        verify(mCarPropertyEventCallback, never()).onChangeEvent(any());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
        verify(mCarPropertyEventCallback2, never()).onChangeEvent(any());
        verify(mCarPropertyEventCallback2, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback2, never()).onErrorEvent(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void forwardErrorEvent_forwardsToCallback() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        mCarPropertyEventCallbackController.forwardErrorEvent(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        ERROR_CAR_PROPERTY_VALUE,
                        CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG));

        verify(mCarPropertyEventCallback, never()).onChangeEvent(any());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback).onErrorEvent(PROPERTY_ID, AREA_ID_1,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG);
    }

    @Test
    public void forwardErrorEvent_forwardsToMultipleCallbacks() {
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback,
                CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        assertThat(mCarPropertyEventCallbackController.add(mCarPropertyEventCallback2,
                CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
        mCarPropertyEventCallbackController.forwardErrorEvent(
                new CarPropertyEvent(CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE,
                        ERROR_CAR_PROPERTY_VALUE,
                        CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG));

        verify(mCarPropertyEventCallback, never()).onChangeEvent(any());
        verify(mCarPropertyEventCallback, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback).onErrorEvent(PROPERTY_ID, AREA_ID_1,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG);
        verify(mCarPropertyEventCallback2, never()).onChangeEvent(any());
        verify(mCarPropertyEventCallback2, never()).onErrorEvent(anyInt(), anyInt());
        verify(mCarPropertyEventCallback2).onErrorEvent(PROPERTY_ID, AREA_ID_1,
                CarPropertyManager.CAR_SET_PROPERTY_ERROR_CODE_INVALID_ARG);
    }
}
