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

package com.android.systemui.car.hvac;

/**
 *  Utility class for HVAC-related use cases.
 */
public final class HvacUtils {
    /**
     * Converts temperature in Celsius to temperature in Fahrenheit.
     *
     * @param tempC temeprature in Celsius
     * @return temeprature in Fahrenheit
     */
    public static float celsiusToFahrenheit(float tempC) {
        return (tempC * 9f / 5f) + 32;
    }

    /**
     * Converts temperature in Fahrenheit to temperature in Celsius.
     *
     * @param tempF temperature in Fahrenheit
     * @return temperature in Celsius
     */
    public static float fahrenheitToCelsius(float tempF) {
        return (tempF - 32) * 5f / 9f;
    }
}
