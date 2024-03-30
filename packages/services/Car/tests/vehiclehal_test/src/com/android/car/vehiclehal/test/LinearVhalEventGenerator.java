/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.vehiclehal.test;

import static org.junit.Assert.assertEquals;

import android.hardware.automotive.vehicle.V2_0.IVehicle;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;

class LinearVhalEventGenerator implements VhalEventGenerator {

    private final IVehicle mVehicle;

    private int mProp;
    private Duration mInterval;
    private float mInitialValue;
    private float mDispersion;
    private float mIncrement;

    LinearVhalEventGenerator(IVehicle vehicle) {
        mVehicle = vehicle;
        reset();
    }

    LinearVhalEventGenerator reset() {
        mProp = 0;
        mInterval = Duration.ofSeconds(1);
        mInitialValue = 1000;
        mDispersion = 0;
        mInitialValue = 0;
        return this;
    }

    LinearVhalEventGenerator setIntervalMs(long intervalMs) {
        mInterval = Duration.ofMillis(intervalMs);
        return this;
    }

    LinearVhalEventGenerator setInitialValue(float initialValue) {
        mInitialValue = initialValue;
        return this;
    }

    LinearVhalEventGenerator setDispersion(float dispersion) {
        mDispersion = dispersion;
        return this;
    }

    LinearVhalEventGenerator setIncrement(float increment) {
        mIncrement = increment;
        return this;
    }

    LinearVhalEventGenerator setProp(int prop) {
        mProp = prop;
        return this;
    }

    @Override
    public void start() throws Exception {
        ArrayList<String> options = new ArrayList<String>(Arrays.asList(
                "--debughal", "--genfakedata", "--startlinear", String.format("%d", mProp),
                String.format("%f", mInitialValue), String.format("%f", mInitialValue),
                String.format("%f", mDispersion), String.format("%f", mIncrement),
                String.format("%d", mInterval.toNanos())));

        NativePipeHelper pipe = new NativePipeHelper();
        pipe.create();
        mVehicle.debug(pipe.getNativeHandle(), options);
        assertEquals("", pipe.getOutput());
        pipe.close();
    }

    @Override
    public void stop() throws Exception {
        ArrayList<String> options = new ArrayList<String>(Arrays.asList(
                "--debughal", "--genfakedata", "--stoplinear",
                String.format("%d", mProp)));
        NativePipeHelper pipe = new NativePipeHelper();
        pipe.create();
        mVehicle.debug(pipe.getNativeHandle(), options);
        assertEquals("", pipe.getOutput());
        pipe.close();
    }
}
