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

import static android.car.hardware.property.CarPropertyManager.SENSOR_RATE_ONCHANGE;

import static com.android.car.internal.property.CarPropertyHelper.SYNC_OP_LIMIT_TRY_AGAIN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Pair;
import android.util.SparseArray;

import com.android.car.hal.PropertyHalService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public final class CarPropertyServiceUnitTest {

    @Mock
    private Context mContext;
    @Mock
    private PropertyHalService mHalService;
    @Mock
    private ICarPropertyEventListener mICarPropertyEventListener;
    @Mock
    private IBinder mIBinder;

    private CarPropertyService mService;

    private static final int SPEED_ID = VehiclePropertyIds.PERF_VEHICLE_SPEED;
    private static final int HVAC_TEMP = VehiclePropertyIds.HVAC_TEMPERATURE_SET;
    private static final String GRANTED_PERMISSION = "GRANTED_PERMISSION";

    @Before
    public void setUp() {
        when(mICarPropertyEventListener.asBinder()).thenReturn(mIBinder);
        when(mContext.checkCallingOrSelfPermission(GRANTED_PERMISSION)).thenReturn(
                PackageManager.PERMISSION_GRANTED);

        SparseArray<CarPropertyConfig<?>> configs = new SparseArray<>();
        configs.put(SPEED_ID, CarPropertyConfig.newBuilder(
                Float.class, SPEED_ID,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL).build());
        when(mHalService.getReadPermission(SPEED_ID)).thenReturn(GRANTED_PERMISSION);
        // HVAC_TEMP is actually not a global property, but for simplicity, make it global here.
        configs.put(HVAC_TEMP, CarPropertyConfig.newBuilder(
                Float.class, HVAC_TEMP,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL).build());
        when(mHalService.getReadPermission(HVAC_TEMP)).thenReturn(GRANTED_PERMISSION);
        when(mHalService.getWritePermission(HVAC_TEMP)).thenReturn(GRANTED_PERMISSION);
        when(mHalService.getPropertyList()).thenReturn(configs);

        mService = new CarPropertyService(mContext, mHalService);
        mService.init();
    }

    @Test
    public void testRegisterListenerNull() {
        assertThrows(IllegalArgumentException.class,
                () -> mService.registerListener(SPEED_ID, /* rate=*/ 10, /* listener= */ null));
    }

    @Test
    public void testRegisterUnregisterForContinuousProperty() throws Exception {
        ICarPropertyEventListener mMockHandler1 = mock(ICarPropertyEventListener.class);
        ICarPropertyEventListener mMockHandler2 = mock(ICarPropertyEventListener.class);
        // Must use two different binders because listener is uniquely identified by binder.
        IBinder mBinder1 = mock(IBinder.class);
        IBinder mBinder2 = mock(IBinder.class);
        when(mMockHandler1.asBinder()).thenReturn(mBinder1);
        when(mMockHandler2.asBinder()).thenReturn(mBinder2);
        // Initially SPEED_ID is not subscribed, so should return -1.
        when(mHalService.getSampleRate(SPEED_ID)).thenReturn(-1f);
        CarPropertyValue mValue = mock(CarPropertyValue.class);
        when(mHalService.getPropertySafe(SPEED_ID, 0)).thenReturn(mValue);

        // Register the first listener.
        mService.registerListener(SPEED_ID, /* rate= */ 10, mMockHandler1);

        // Wait until we get the on property change event for the initial value.
        verify(mMockHandler1, timeout(5000)).onEvent(any());

        verify(mHalService).subscribeProperty(SPEED_ID, 10f);
        verify(mHalService).getPropertySafe(SPEED_ID, 0);

        // Clean up invocation state.
        clearInvocations(mHalService);
        when(mHalService.getSampleRate(SPEED_ID)).thenReturn(10f);

        // Register the second listener.
        mService.registerListener(SPEED_ID, /* rate= */ 20, mMockHandler2);

        // Wait until we get the on property change event for the initial value.
        verify(mMockHandler2, timeout(5000)).onEvent(any());

        verify(mHalService).subscribeProperty(SPEED_ID, 20f);
        verify(mHalService).getPropertySafe(SPEED_ID, 0);

        // Clean up invocation state.
        clearInvocations(mHalService);
        when(mHalService.getSampleRate(SPEED_ID)).thenReturn(20f);

        // Unregister the second listener, the first listener must still be registered.
        mService.unregisterListener(SPEED_ID, mMockHandler2);

        // The property must not be unsubscribed.
        verify(mHalService, never()).unsubscribeProperty(anyInt());
        // The subscription rate must be updated.
        verify(mHalService).subscribeProperty(SPEED_ID, 10f);
        when(mHalService.getSampleRate(SPEED_ID)).thenReturn(10f);

        // Unregister the first listener. We have no more listeners, must cause unsubscription.
        mService.unregisterListener(SPEED_ID, mMockHandler1);

        verify(mHalService).unsubscribeProperty(SPEED_ID);
    }

    @Test
    public void testRegisterUnregisterForOnChangeProperty() throws Exception {
        ICarPropertyEventListener mMockHandler1 = mock(ICarPropertyEventListener.class);
        ICarPropertyEventListener mMockHandler2 = mock(ICarPropertyEventListener.class);
        // Must use two different binders because listener is uniquely identified by binder.
        IBinder mBinder1 = mock(IBinder.class);
        IBinder mBinder2 = mock(IBinder.class);
        when(mMockHandler1.asBinder()).thenReturn(mBinder1);
        when(mMockHandler2.asBinder()).thenReturn(mBinder2);
        // Initially HVAC_TEMP is not subscribed, so should return -1.
        when(mHalService.getSampleRate(HVAC_TEMP)).thenReturn(-1f);
        CarPropertyValue mValue = mock(CarPropertyValue.class);
        when(mHalService.getPropertySafe(HVAC_TEMP, 0)).thenReturn(mValue);

        // Register the first listener.
        mService.registerListener(HVAC_TEMP, /* rate= */ SENSOR_RATE_ONCHANGE, mMockHandler1);

        // Wait until we get the on property change event for the initial value.
        verify(mMockHandler1, timeout(5000)).onEvent(any());

        verify(mHalService).subscribeProperty(HVAC_TEMP, 0f);
        verify(mHalService).getPropertySafe(HVAC_TEMP, 0);

        // Clean up invocation state.
        clearInvocations(mHalService);
        when(mHalService.getSampleRate(HVAC_TEMP)).thenReturn(0f);

        // Register the second listener.
        mService.registerListener(HVAC_TEMP, /* rate= */ SENSOR_RATE_ONCHANGE, mMockHandler2);

        // Wait until we get the on property change event for the initial value.
        verify(mMockHandler2, timeout(5000)).onEvent(any());

        // Must not subscribe again.
        verify(mHalService, never()).subscribeProperty(anyInt(), anyFloat());
        verify(mHalService).getPropertySafe(HVAC_TEMP, 0);

        // Clean up invocation state.
        clearInvocations(mHalService);

        // Unregister the second listener, the first listener must still be registered.
        mService.unregisterListener(HVAC_TEMP, mMockHandler2);

        // The property must not be unsubscribed.
        verify(mHalService, never()).unsubscribeProperty(anyInt());

        // Unregister the first listener. We have no more listeners, must cause unsubscription.
        mService.unregisterListener(HVAC_TEMP, mMockHandler1);

        verify(mHalService).unsubscribeProperty(HVAC_TEMP);
    }

    private static class EventListener extends ICarPropertyEventListener.Stub{
        private final CarPropertyService mService;
        private List<CarPropertyEvent> mEvents;
        private Exception mException;

        EventListener(CarPropertyService service) {
            mService = service;
        }

        @Override
        public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
            CountDownLatch latch = new CountDownLatch(1);
            new Thread(() -> {
                // Call a function in CarPropertyService to make sure there is no dead lock. This
                // call doesn't actually do anything. We must use a new thread here because
                // the same thread can obtain the same lock again even if there is a dead lock.
                mService.getReadPermission(0);
                latch.countDown();
            }, "onEventThread").start();
            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    mException = new Exception("timeout waiting for getReadPermission, dead lock?");
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                mException = new Exception("waiting for onEvent thread interrupted");
                return;
            }
            mEvents = events;
        }

        List<CarPropertyEvent> getEvents() throws Exception {
            if (mException != null) {
                throw mException;
            }
            return mEvents;
        }
    }

    @Test
    public void testOnEventCallback_MustNotHaveDeadLock() throws Exception {
        // This test checks that CarPropertyService must not hold any lock while calling
        // ICarPropertyListener's onEvent callback, otherwise it might cause dead lock if
        // the callback calls another function in CarPropertyService that requires the same lock.

        // We don't care about the result for getReadPermission so just return an empty map.
        when(mHalService.getPermissionsForAllProperties()).thenReturn(
                new SparseArray<Pair<String, String>>());
        mService.init();

        // Initially HVAC_TEMP is not subscribed, so should return -1.
        when(mHalService.getSampleRate(HVAC_TEMP)).thenReturn(-1f);
        CarPropertyValue<Float> value = new CarPropertyValue<Float>(HVAC_TEMP, 0, 1.0f);
        when(mHalService.getPropertySafe(HVAC_TEMP, 0)).thenReturn(value);
        EventListener listener = new EventListener(mService);

        mService.registerListener(HVAC_TEMP, /* rate= */ SENSOR_RATE_ONCHANGE, listener);
        List<CarPropertyEvent> events = List.of(new CarPropertyEvent(0, value));
        mService.onPropertyChange(events);

        List<CarPropertyEvent> actualEvents = listener.getEvents();

        assertThat(actualEvents.size()).isEqualTo(events.size());
        for (int i = 0; i < events.size(); i++) {
            CarPropertyEvent actualEvent = actualEvents.get(i);
            CarPropertyEvent expectedEvent = events.get(i);
            assertThat(actualEvent.getEventType()).isEqualTo(expectedEvent.getEventType());
            assertThat(actualEvent.getErrorCode()).isEqualTo(expectedEvent.getErrorCode());
            CarPropertyValue actualCarPropertyValue = actualEvent.getCarPropertyValue();
            CarPropertyValue expectedCarPropertyValue = expectedEvent.getCarPropertyValue();
            assertThat(actualCarPropertyValue.getPropertyId()).isEqualTo(
                    expectedCarPropertyValue.getPropertyId());
            assertThat(actualCarPropertyValue.getAreaId()).isEqualTo(
                    expectedCarPropertyValue.getAreaId());
            assertThat(actualCarPropertyValue.getStatus()).isEqualTo(
                    expectedCarPropertyValue.getStatus());
            assertThat(actualCarPropertyValue.getTimestamp()).isEqualTo(
                    expectedCarPropertyValue.getTimestamp());
            assertThat(actualCarPropertyValue.getValue()).isEqualTo(
                    expectedCarPropertyValue.getValue());
        }
    }

    // Test that limited number of sync operations are allowed at once.
    @Test
    public void testSyncOperationLimit() throws Exception {
        CountDownLatch startCd = new CountDownLatch(16);
        CountDownLatch finishCd = new CountDownLatch(16);
        CountDownLatch returnCd = new CountDownLatch(1);
        when(mHalService.getProperty(HVAC_TEMP, 0))
                .thenAnswer((invocation) -> {
                    return new CarPropertyValue(HVAC_TEMP, /* areaId= */ 0, Float.valueOf(11f));
                });
        doAnswer((invocation) -> {
            // Notify the operation has started.
            startCd.countDown();

            // Wait for the signal before finishing the operation.
            returnCd.await(10, TimeUnit.SECONDS);
            return null;
        }).when(mHalService).setProperty(any());

        Executor executor = Executors.newFixedThreadPool(16);
        for (int i = 0; i < 16; i++) {
            executor.execute(() -> {
                mService.setProperty(
                        new CarPropertyValue(HVAC_TEMP, /* areaId= */ 0, Float.valueOf(11f)),
                        mICarPropertyEventListener);
                finishCd.countDown();
            });
        }

        // Wait until 16 requests are ongoing.
        startCd.await(10, TimeUnit.SECONDS);

        // The 17th request must throw exception.
        Exception e = assertThrows(ServiceSpecificException.class, () -> mService.getProperty(
                HVAC_TEMP, /* areaId= */ 0));
        assertThat(((ServiceSpecificException) e).errorCode).isEqualTo(SYNC_OP_LIMIT_TRY_AGAIN);

        // Unblock the operations.
        returnCd.countDown();
        finishCd.await(10, TimeUnit.SECONDS);
    }
}
