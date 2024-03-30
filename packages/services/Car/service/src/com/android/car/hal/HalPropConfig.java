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

import android.car.VehicleAreaType;
import android.car.hardware.CarPropertyConfig;
import android.hardware.automotive.vehicle.VehicleArea;
import android.hardware.automotive.vehicle.VehiclePropertyType;

import java.util.ArrayList;

/**
 * HalPropConfig represents a vehicle property config.
 */
public abstract class HalPropConfig {

    private static final int[] DEFAULT_AREA_IDS = {VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL};

    /**
     * Get the property ID.
     */
    public abstract int getPropId();

    /**
     * Get the access mode.
     */
    public abstract int getAccess();

    /**
     * Get the change mode.
     */
    public abstract int getChangeMode();

    /**
     * Get the area configs.
     */
    public abstract HalAreaConfig[] getAreaConfigs();

    /**
     * Get the config array.
     */
    public abstract int[] getConfigArray();

    /**
     * Get the config string.
     */
    public abstract String getConfigString();

    /**
     * Get the min sample rate.
     */
    public abstract float getMinSampleRate();

    /**
     * Get the max sample rate.
     */
    public abstract float getMaxSampleRate();

    /**
     * Converts to AIDL or HIDL VehiclePropConfig.
     */
    public abstract Object toVehiclePropConfig();

    /**
     * Converts {@link HalPropConfig} to {@link CarPropertyConfig}.
     *
     * @param mgrPropertyId The Property ID used by Car Property Manager, different from the
     *      property ID used by VHAL.
     */
    public CarPropertyConfig<?> toCarPropertyConfig(int mgrPropertyId) {
        int propId = getPropId();
        int areaType = getVehicleAreaType(propId & VehicleArea.MASK);

        Class<?> clazz = CarPropertyUtils.getJavaClass(propId & VehiclePropertyType.MASK);
        float maxSampleRate = 0f;
        float minSampleRate = 0f;
        if (getChangeMode() != CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC) {
            maxSampleRate = getMaxSampleRate();
            minSampleRate = getMinSampleRate();
        }

        int[] configIntArray = getConfigArray();
        ArrayList<Integer> configArray = new ArrayList<>(configIntArray.length);
        for (int i = 0; i < configIntArray.length; i++) {
            configArray.add(configIntArray[i]);
        }
        HalAreaConfig[] areaConfigs = getAreaConfigs();
        if (areaConfigs.length == 0) {
            return CarPropertyConfig
                    .newBuilder(clazz, mgrPropertyId, areaType, /* capacity= */ 1)
                    .addAreas(DEFAULT_AREA_IDS)
                    .setAccess(getAccess())
                    .setChangeMode(getChangeMode())
                    .setConfigArray(configArray)
                    .setConfigString(getConfigString())
                    .setMaxSampleRate(maxSampleRate)
                    .setMinSampleRate(minSampleRate)
                    .build();
        } else {
            CarPropertyConfig.Builder builder = CarPropertyConfig
                    .newBuilder(clazz, mgrPropertyId, areaType, /* capacity= */ areaConfigs.length)
                    .setAccess(getAccess())
                    .setChangeMode(getChangeMode())
                    .setConfigArray(configArray)
                    .setConfigString(getConfigString())
                    .setMaxSampleRate(maxSampleRate)
                    .setMinSampleRate(minSampleRate);

            for (HalAreaConfig area : areaConfigs) {
                int areaId = area.getAreaId();
                if (classMatched(Integer.class, clazz)) {
                    builder.addAreaConfig(areaId, area.getMinInt32Value(), area.getMaxInt32Value());
                } else if (classMatched(Float.class, clazz)) {
                    builder.addAreaConfig(areaId, area.getMinFloatValue(), area.getMaxFloatValue());
                } else if (classMatched(Long.class, clazz)) {
                    builder.addAreaConfig(areaId, area.getMinInt64Value(), area.getMaxInt64Value());
                } else if (classMatched(Boolean.class, clazz)
                        || classMatched(Float[].class, clazz)
                        || classMatched(Integer[].class, clazz)
                        || classMatched(Long[].class, clazz)
                        || classMatched(String.class, clazz)
                        || classMatched(byte[].class, clazz)
                        || classMatched(Object[].class, clazz)) {
                    // These property types do not have min/max values
                    builder.addArea(areaId);
                } else {
                    throw new IllegalArgumentException("Unexpected type: " + clazz);
                }
            }
            return builder.build();
        }
    }

    private static @VehicleAreaType.VehicleAreaTypeValue int getVehicleAreaType(int halArea) {
        switch (halArea) {
            case VehicleArea.GLOBAL:
                return VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL;
            case VehicleArea.SEAT:
                return VehicleAreaType.VEHICLE_AREA_TYPE_SEAT;
            case VehicleArea.DOOR:
                return VehicleAreaType.VEHICLE_AREA_TYPE_DOOR;
            case VehicleArea.WINDOW:
                return VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW;
            case VehicleArea.MIRROR:
                return VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR;
            case VehicleArea.WHEEL:
                return VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL;
            default:
                throw new RuntimeException("Unsupported area type " + halArea);
        }
    }

    private static boolean classMatched(Class<?> class1, Class<?> class2) {
        return class1 == class2 || class1.getComponentType() == class2;
    }

}
