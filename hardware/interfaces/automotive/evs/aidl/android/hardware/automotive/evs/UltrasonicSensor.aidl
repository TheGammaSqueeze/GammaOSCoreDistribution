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

package android.hardware.automotive.evs;

import android.hardware.automotive.evs.SensorPose;

/**
 * Structure that contains all information of an ultrasonic sensor.
 */
@VintfStability
parcelable UltrasonicSensor {
    /**
     * Pose provides the orientation and location of the ultrasonic sensor within the car.
     * The +Y axis points along the center of the beam spread the X axis to the right and the Z
     * axis in the up direction.
     */
    SensorPose pose;
    /**
     * Maximum range of the sensor in milli-metres.
     */
    float maxRangeMm;
    /**
     * Half-angle of the angle of measurement of the sensor, relative to the
     * sensor’s x axis, in radians.
     */
    float angleOfMeasurement;
}
