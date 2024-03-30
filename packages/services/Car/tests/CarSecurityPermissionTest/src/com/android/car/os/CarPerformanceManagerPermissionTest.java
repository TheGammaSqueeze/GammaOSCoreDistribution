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

import static android.car.Car.CAR_PERFORMANCE_SERVICE;
import static android.car.Car.PERMISSION_MANAGE_THREAD_PRIORITY;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.expectThrows;

import android.car.Car;
import android.car.os.CarPerformanceManager;
import android.car.os.ThreadPolicyWithPriority;
import android.content.Context;
import android.os.Handler;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

/**
 * This class contains security permission tests for the
 * {@link android.car.os#CarPerformanceManager}'s system APIs.
 */
@RunWith(AndroidJUnit4.class)
public final class CarPerformanceManagerPermissionTest {
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();

    private CarPerformanceManager mCarPerformanceManager;

    @Before
    public void setUp() {
        Car car = Objects.requireNonNull(Car.createCar(mContext, (Handler) null));
        mCarPerformanceManager = (CarPerformanceManager) car.getCarManager(CAR_PERFORMANCE_SERVICE);
    }

    @Test
    public void testSetThreadPriority() throws Exception {
        ThreadPolicyWithPriority p = new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO, /* priority= */ 1);
        Exception e = expectThrows(
                SecurityException.class, () -> mCarPerformanceManager.setThreadPriority(p));

        assertThat(e.getMessage()).contains(PERMISSION_MANAGE_THREAD_PRIORITY);
    }

    @Test
    public void testGetThreadPriority() throws Exception {
        Exception e = expectThrows(
                SecurityException.class, () -> mCarPerformanceManager.getThreadPriority());

        assertThat(e.getMessage()).contains(PERMISSION_MANAGE_THREAD_PRIORITY);
    }
}
