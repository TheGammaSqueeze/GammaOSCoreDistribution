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

package com.android.car.hal;

import android.hardware.automotive.vehicle.V2_0.VehicleAreaConfig;

/**
 * HidlHalAreaConfig is a HalAreaConfig with an AIDL backend.
 */
public final class HidlHalAreaConfig extends HalAreaConfig {
    private final VehicleAreaConfig mConfig;

    public HidlHalAreaConfig(VehicleAreaConfig config) {
        mConfig = config;
    }

    /**
     * Get the area ID.
     */
    @Override
    public int getAreaId() {
        return mConfig.areaId;
    }

    /**
     * Get the min int value.
     */
    @Override
    public int getMinInt32Value() {
        return mConfig.minInt32Value;
    }

    /**
     * Get the max int value.
     */
    @Override
    public int getMaxInt32Value() {
        return mConfig.maxInt32Value;
    }

    /**
     * Get the min long value.
     */
    @Override
    public long getMinInt64Value() {
        return mConfig.minInt64Value;
    }

    /**
     * Get the max long value.
     */
    @Override
    public long getMaxInt64Value() {
        return mConfig.maxInt64Value;
    }

    /**
     * Get the min float value.
     */
    @Override
    public float getMinFloatValue() {
        return mConfig.minFloatValue;
    }

    /**
     * Get the max float value.
     */
    @Override
    public float getMaxFloatValue() {
        return mConfig.maxFloatValue;
    }
}
