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

package com.android.car.os;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import android.car.os.CpuAvailabilityMonitoringConfig;
import android.car.os.ICpuAvailabilityChangeListener;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.os.IBinder;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * <p>This class contains unit tests for the {@link CarPerformanceService}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class CarPerformanceServiceUnitTest extends AbstractExtendedMockitoTestCase {

    @Mock private Context mMockContext;

    private CarPerformanceService mCarPerformanceService;

    public CarPerformanceServiceUnitTest() {
        super(CarPerformanceService.TAG);
    }

    @Before
    public void setUp() throws Exception {
        mCarPerformanceService = new CarPerformanceService(mMockContext);
        mCarPerformanceService.init();
    }

    @After
    public void tearDown() throws Exception {
        mCarPerformanceService.release();
    }

    @Test
    public void testAddRemoveCpuAvailabilityChangeListener() throws Exception {
        ICpuAvailabilityChangeListener mockListener = createMockCpuAvailabilityChangeListener();
        CpuAvailabilityMonitoringConfig config = new CpuAvailabilityMonitoringConfig.Builder(
                /* lowerBoundPercent= */ 10, /* upperBoundPercent= */ 90,
                /* timeoutInSeconds= */ 300).build();
        mCarPerformanceService.addCpuAvailabilityChangeListener(config, mockListener);

        IBinder mockBinder = mockListener.asBinder();
        verify(mockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        mCarPerformanceService.removeCpuAvailabilityChangeListener(mockListener);

        verify(mockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    @Test
    public void testDuplicateAddCpuAvailabilityChangeListener() throws Exception {
        ICpuAvailabilityChangeListener mockListener = createMockCpuAvailabilityChangeListener();
        CpuAvailabilityMonitoringConfig config = new CpuAvailabilityMonitoringConfig.Builder(
                /* lowerBoundPercent= */ 10, /* upperBoundPercent= */ 90,
                /* timeoutInSeconds= */ 300).build();
        mCarPerformanceService.addCpuAvailabilityChangeListener(config, mockListener);

        IBinder mockBinder = mockListener.asBinder();
        verify(mockBinder).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        mCarPerformanceService.addCpuAvailabilityChangeListener(config, mockListener);

        verify(mockBinder, times(2)).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
        verify(mockBinder).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        mCarPerformanceService.removeCpuAvailabilityChangeListener(mockListener);

        verify(mockBinder, times(2)).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());

        mCarPerformanceService.addCpuAvailabilityChangeListener(config, mockListener);

        verify(mockBinder, times(3)).linkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    @Test
    public void testAddCpuAvailabilityChangeListenerThrowsExceptions() throws Exception {
        ICpuAvailabilityChangeListener mockListener = createMockCpuAvailabilityChangeListener();
        CpuAvailabilityMonitoringConfig goodConfig =
                new CpuAvailabilityMonitoringConfig.Builder(/* lowerBoundPercent= */ 10,
                        /* upperBoundPercent= */ 90, /* timeoutInSeconds= */ 300).build();

        NullPointerException npeThrown = expectThrows(NullPointerException.class,
                () -> mCarPerformanceService.addCpuAvailabilityChangeListener(null, mockListener));
        assertWithMessage("NullPointerException thrown on null config")
                .that(npeThrown).hasMessageThat().contains("Configuration must be non-null");

        npeThrown = expectThrows(NullPointerException.class,
                () -> mCarPerformanceService.addCpuAvailabilityChangeListener(goodConfig, null));
        assertWithMessage("NullPointerException thrown on null listener")
                .that(npeThrown).hasMessageThat().contains("Listener must be non-null");

        CpuAvailabilityMonitoringConfig ignoreBoundsConfig =
                new CpuAvailabilityMonitoringConfig.Builder(
                        CpuAvailabilityMonitoringConfig.IGNORE_PERCENT_LOWER_BOUND,
                        CpuAvailabilityMonitoringConfig.IGNORE_PERCENT_UPPER_BOUND,
                        /* timeoutInSeconds= */ 300).build();
        IllegalArgumentException iaeThrown = expectThrows(IllegalArgumentException.class,
                () -> mCarPerformanceService.addCpuAvailabilityChangeListener(ignoreBoundsConfig,
                        mockListener));
        assertWithMessage("IllegalArgumentException thrown on ignore lower/uppwer bound percents")
                .that(iaeThrown).hasMessageThat().contains(
                        "lower bound percent(0) and upper bound percent(100)");

        CpuAvailabilityMonitoringConfig mismatchBoundsConfig =
                new CpuAvailabilityMonitoringConfig.Builder(/* lowerBoundPercent= */ 90,
                        /* upperBoundPercent= */ 10, /* timeoutInSeconds= */ 300).build();
        iaeThrown = expectThrows(IllegalArgumentException.class,
                () -> mCarPerformanceService.addCpuAvailabilityChangeListener(mismatchBoundsConfig,
                        mockListener));
        assertWithMessage("IllegalArgumentException thrown on invalid lower/upper bound percents")
                .that(iaeThrown).hasMessageThat().contains(
                        "lower bound percent(90) and upper bound percent(10)");
    }

    @Test
    public void testRemoveUnaddedCpuAvailabilityChangeListener() throws Exception {
        ICpuAvailabilityChangeListener mockListener = createMockCpuAvailabilityChangeListener();

        IBinder mockBinder = mockListener.asBinder();

        mCarPerformanceService.removeCpuAvailabilityChangeListener(mockListener);

        verify(mockBinder, never()).unlinkToDeath(any(IBinder.DeathRecipient.class), anyInt());
    }

    private static ICpuAvailabilityChangeListener createMockCpuAvailabilityChangeListener() {
        ICpuAvailabilityChangeListener listener = mock(ICpuAvailabilityChangeListener.Stub.class);
        when(listener.asBinder()).thenCallRealMethod();
        return listener;
    }
}
