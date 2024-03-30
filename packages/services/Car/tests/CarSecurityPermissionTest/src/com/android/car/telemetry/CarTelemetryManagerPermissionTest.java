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

package com.android.car.telemetry;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.expectThrows;

import android.car.Car;
import android.car.telemetry.CarTelemetryManager;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * This class contains security permission tests for {@link CarTelemetryManager}'s system APIs.
 */
@RunWith(AndroidJUnit4.class)
public class CarTelemetryManagerPermissionTest {
    private final byte[] mMetricsConfigBytes = "metricsConfig".getBytes();
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final String mMetricsConfigName = "name";
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private Car mCar;
    private CarTelemetryManager mCarTelemetryManager;

    @Before
    public void setUp() {
        mCar = Objects.requireNonNull(Car.createCar(mContext));
        mCarTelemetryManager = (CarTelemetryManager) mCar.getCarManager(Car.CAR_TELEMETRY_SERVICE);
    }

    @After
    public void tearDown() {
        mCar.disconnect();
    }

    @Test
    public void testAddMetricsConfig() {
        Exception e = expectThrows(SecurityException.class,
                () -> mCarTelemetryManager.addMetricsConfig("name", mMetricsConfigBytes, mExecutor,
                        (metricsConfigName, statusCode) -> { }));

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testRemoveMetricsConfig() {
        Exception e = expectThrows(SecurityException.class,
                () -> mCarTelemetryManager.removeMetricsConfig(mMetricsConfigName));

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testRemoveAllMetricsConfigs() {
        Exception e = expectThrows(SecurityException.class,
                () -> mCarTelemetryManager.removeAllMetricsConfigs());

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testGetFinishedReport() {
        Exception e = expectThrows(SecurityException.class,
                () -> mCarTelemetryManager.getFinishedReport(mMetricsConfigName, mExecutor,
                        (metricsConfigName, report, telemetryError, status) -> { }));

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testGetAllFinishedReports() {
        Exception e = expectThrows(SecurityException.class,
                () -> mCarTelemetryManager.getAllFinishedReports(mExecutor,
                        (metricsConfigName, report, telemetryError, status) -> { }));

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testSetReportReadyListener() {
        Exception e = expectThrows(SecurityException.class,
                () -> mCarTelemetryManager.setReportReadyListener(
                        mExecutor, (metricsConfigName) -> { }));

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }

    @Test
    public void testClearReportReadyListener() {
        Exception e = expectThrows(SecurityException.class,
                () -> mCarTelemetryManager.clearReportReadyListener());

        assertThat(e.getMessage()).contains(Car.PERMISSION_USE_CAR_TELEMETRY_SERVICE);
    }
}
