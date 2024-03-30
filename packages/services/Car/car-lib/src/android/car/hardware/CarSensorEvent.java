/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.hardware;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.car.annotation.AddedInOrBefore;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

/**
 * A CarSensorEvent object corresponds to a single sensor event coming from the car. The sensor
 * data is stored in a sensor-type specific format in the object's float and byte arrays.
 *
 * To aid unmarshalling the object's data arrays, this class provides static nested classes and
 * conversion methods. The conversion methods each have an optional data parameter which,
 * if not null, will be used and returned. This parameter should be used to avoid unnecessary
 * object churn whenever possible. Additionally, calling a conversion method on a CarSensorEvent
 * object with an inappropriate type will result in an {@code UnsupportedOperationException}
 * being thrown.
 *
 * @deprecated consider using {@link CarPropertyValue} and
 * {@link android.car.hardware.property.CarPropertyManager} instead.
 */
@Deprecated
public class CarSensorEvent implements Parcelable {

    /**
     *  GEAR_* represents meaning of intValues[0] for {@link CarSensorManager#SENSOR_TYPE_GEAR}
     *  sensor type.
     *  GEAR_NEUTRAL means transmission gear is in neutral state, and the car may be moving.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_NEUTRAL    = 0x0001;
    /**
     * intValues[0] from 1 to 99 represents transmission gear number for moving forward.
     * GEAR_FIRST is for gear number 1.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_FIRST      = 0x0010;
    /** Gear number 2. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_SECOND     = 0x0020;
    /** Gear number 3. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_THIRD      = 0x0040;
    /** Gear number 4. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_FOURTH     = 0x0080;
    /** Gear number 5. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_FIFTH      = 0x0100;
    /** Gear number 6. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_SIXTH      = 0x0200;
    /** Gear number 7. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_SEVENTH    = 0x0400;
    /** Gear number 8. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_EIGHTH     = 0x0800;
    /** Gear number 9. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_NINTH      = 0x1000;
    /** Gear number 10. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_TENTH      = 0x2000;
    /**
     * This is for transmission without specific gear number for moving forward like CVT. It tells
     * that car is in a transmission state to move it forward.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_DRIVE      = 0x0008;
    /** Gear in parking state */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_PARK       = 0x0004;
    /** Gear in reverse */
    @AddedInOrBefore(majorVersion = 33)
    public static final int GEAR_REVERSE    = 0x0002;

    /**
     * Ignition state is unknown.
     *
     * The constants that starts with IGNITION_STATE_ represent values for
     * {@link CarSensorManager#SENSOR_TYPE_IGNITION_STATE} sensor.
     * */
    @AddedInOrBefore(majorVersion = 33)
    public static final int IGNITION_STATE_UNDEFINED = 0;
    /**
     * Steering wheel is locked.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int IGNITION_STATE_LOCK = 1;
    /** Typically engine is off, but steering wheel is unlocked. */
    @AddedInOrBefore(majorVersion = 33)
    public static final int IGNITION_STATE_OFF = 2;
    /** Accessory is turned off, but engine is not running yet (for EV car is not ready to move). */
    @AddedInOrBefore(majorVersion = 33)
    public static final int IGNITION_STATE_ACC = 3;
    /** In this state engine typically is running (for EV, car is ready to move). */
    @AddedInOrBefore(majorVersion = 33)
    public static final int IGNITION_STATE_ON = 4;
    /** In this state engine is typically starting (cranking). */
    @AddedInOrBefore(majorVersion = 33)
    public static final int IGNITION_STATE_START = 5;

    /**
     * Index for {@link CarSensorManager#SENSOR_TYPE_ENV_OUTSIDE_TEMPERATURE} in floatValues.
     * Temperature in Celsius degrees.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int INDEX_ENVIRONMENT_TEMPERATURE = 0;

    /**
     * Index for {@link CarSensorManager#SENSOR_TYPE_WHEEL_TICK_DISTANCE} in longValues. RESET_COUNT
     * is incremented whenever the HAL detects that a sensor reset has occurred.  It represents to
     * the upper layer that the WHEEL_DISTANCE values will not be contiguous with other values
     * reported with a different RESET_COUNT.
     */
    @AddedInOrBefore(majorVersion = 33)
    public static final int INDEX_WHEEL_DISTANCE_RESET_COUNT = 0;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INDEX_WHEEL_DISTANCE_FRONT_LEFT = 1;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INDEX_WHEEL_DISTANCE_FRONT_RIGHT = 2;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INDEX_WHEEL_DISTANCE_REAR_RIGHT = 3;
    @AddedInOrBefore(majorVersion = 33)
    public static final int INDEX_WHEEL_DISTANCE_REAR_LEFT = 4;

    private static final long MILLI_IN_NANOS = 1000000L;

    /** Sensor type for this event like {@link CarSensorManager#SENSOR_TYPE_CAR_SPEED}. */
    @AddedInOrBefore(majorVersion = 33)
    public int sensorType;

    /**
     * When this data was received from car. It is elapsed real-time of data reception from car in
     * nanoseconds since system boot.
     */
    @AddedInOrBefore(majorVersion = 33)
    public long timestamp;
    /**
     * array holding float type of sensor data. If the sensor has single value, only floatValues[0]
     * should be used. */
    @AddedInOrBefore(majorVersion = 33)
    public final float[] floatValues;
    /** array holding int type of sensor data */
    @AddedInOrBefore(majorVersion = 33)
    public final int[] intValues;
    /** array holding long int type of sensor data */
    @AddedInOrBefore(majorVersion = 33)
    public final long[] longValues;

    /** @hide */
    public CarSensorEvent(Parcel in) {
        sensorType = in.readInt();
        timestamp = in.readLong();
        int len = in.readInt();
        floatValues = new float[len];
        in.readFloatArray(floatValues);
        len = in.readInt();
        intValues = new int[len];
        in.readIntArray(intValues);
        // version 1 up to here
        len = in.readInt();
        longValues = new long[len];
        in.readLongArray(longValues);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public int describeContents() {
        return 0;
    }

    @Override
    @AddedInOrBefore(majorVersion = 33)
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(sensorType);
        dest.writeLong(timestamp);
        dest.writeInt(floatValues.length);
        dest.writeFloatArray(floatValues);
        dest.writeInt(intValues.length);
        dest.writeIntArray(intValues);
        dest.writeInt(longValues.length);
        dest.writeLongArray(longValues);
    }

    @AddedInOrBefore(majorVersion = 33)
    public static final Parcelable.Creator<CarSensorEvent> CREATOR =
            new Parcelable.Creator<CarSensorEvent>() {
        public CarSensorEvent createFromParcel(Parcel in) {
            return new CarSensorEvent(in);
        }

        public CarSensorEvent[] newArray(int size) {
            return new CarSensorEvent[size];
        }
    };

    /** @hide */
    public CarSensorEvent(int sensorType, long timestamp, int floatValueSize, int intValueSize,
                          int longValueSize) {
        this.sensorType = sensorType;
        this.timestamp = timestamp;
        floatValues = new float[floatValueSize];
        intValues = new int[intValueSize];
        longValues = new long[longValueSize];
    }

    /** @hide */
    CarSensorEvent(int sensorType, long timestamp, float[] floatValues, int[] intValues,
                   long[] longValues) {
        this.sensorType = sensorType;
        this.timestamp = timestamp;
        this.floatValues = floatValues;
        this.intValues = intValues;
        this.longValues = longValues;
    }

    private void checkType(int type) {
        if (sensorType == type) {
            return;
        }
        throw new UnsupportedOperationException(String.format(
                "Invalid sensor type: expected %d, got %d", type, sensorType));
    }

    /**
     * Environment data with timestamp and temperature.
     */
    public static class EnvironmentData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        /** If unsupported by the car, this value is NaN. */
        @AddedInOrBefore(majorVersion = 33)
        public float temperature;

        /** @hide */
        private EnvironmentData() {};
    }

    /**
     * Convenience method for obtaining an {@link EnvironmentData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_ENV_OUTSIDE_TEMPERATURE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return an EnvironmentData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public EnvironmentData getEnvironmentData(EnvironmentData data) {
        checkType(CarSensorManager.SENSOR_TYPE_ENV_OUTSIDE_TEMPERATURE);
        if (data == null) {
            data = new EnvironmentData();
        }
        data.timestamp = timestamp;
        data.temperature = floatValues[INDEX_ENVIRONMENT_TEMPERATURE];
        return data;
    }

    /** @hide*/
    public static class IgnitionStateData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public int ignitionState;

        /** @hide */
        private IgnitionStateData() {};
    }

    /**
     * Convenience method for obtaining a {@link IgnitionStateData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_IGNITION_STATE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a IgnitionStateData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public IgnitionStateData getIgnitionStateData(IgnitionStateData data) {
        checkType(CarSensorManager.SENSOR_TYPE_IGNITION_STATE);
        if (data == null) {
            data = new IgnitionStateData();
        }
        data.timestamp = timestamp;
        data.ignitionState = intValues[0];
        return data;
    }

    /** @hide */
    public static class NightData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public boolean isNightMode;

        /** @hide */
        private NightData() {};
    }

    /**
     * Convenience method for obtaining a {@link NightData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_NIGHT}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a NightData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public NightData getNightData(NightData data) {
        checkType(CarSensorManager.SENSOR_TYPE_NIGHT);
        if (data == null) {
            data = new NightData();
        }
        data.timestamp = timestamp;
        data.isNightMode = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class GearData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public int gear;

        /** @hide */
        private GearData() {};
    }

    /**
     * Convenience method for obtaining a {@link GearData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_GEAR}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a GearData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public GearData getGearData(GearData data) {
        checkType(CarSensorManager.SENSOR_TYPE_GEAR);
        if (data == null) {
            data = new GearData();
        }
        data.timestamp = timestamp;
        data.gear = intValues[0];
        return data;
    }

    /** @hide */
    public static class ParkingBrakeData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public boolean isEngaged;

        /** @hide */
        private ParkingBrakeData() {}
    }

    /**
     * Convenience method for obtaining a {@link ParkingBrakeData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_PARKING_BRAKE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a ParkingBreakData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public ParkingBrakeData getParkingBrakeData(ParkingBrakeData data) {
        checkType(CarSensorManager.SENSOR_TYPE_PARKING_BRAKE);
        if (data == null) {
            data = new ParkingBrakeData();
        }
        data.timestamp = timestamp;
        data.isEngaged = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class FuelLevelData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        /** Fuel level in milliliters.  Negative values indicate this property is unsupported. */
        @AddedInOrBefore(majorVersion = 33)
        public float level;

        /** @hide */
        private FuelLevelData() {};
    }

    /**
     * Convenience method for obtaining a {@link FuelLevelData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_FUEL_LEVEL}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a FuelLevel object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public FuelLevelData getFuelLevelData(FuelLevelData data) {
        checkType(CarSensorManager.SENSOR_TYPE_FUEL_LEVEL);
        if (data == null) {
            data = new FuelLevelData();
        }
        data.timestamp = timestamp;
        if (floatValues == null) {
            data.level = -1.0f;
        } else {
            if (floatValues[0] < 0) {
                data.level = -1.0f;
            } else {
                data.level = floatValues[0];
            }
        }
        return data;
    }

    /** @hide */
    public static class OdometerData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public float kms;

        /** @hide */
        private OdometerData() {};
    }

    /**
     * Convenience method for obtaining an {@link OdometerData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_ODOMETER}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return an OdometerData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public OdometerData getOdometerData(OdometerData data) {
        checkType(CarSensorManager.SENSOR_TYPE_ODOMETER);
        if (data == null) {
            data = new OdometerData();
        }
        data.timestamp = timestamp;
        data.kms = floatValues[0];
        return data;
    }

    /** @hide */
    public static class RpmData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public float rpm;

        /** @hide */
        private RpmData() {};
    }

    /**
     * Convenience method for obtaining a {@link RpmData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_RPM}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a RpmData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public RpmData getRpmData(RpmData data) {
        checkType(CarSensorManager.SENSOR_TYPE_RPM);
        if (data == null) {
            data = new RpmData();
        }
        data.timestamp = timestamp;
        data.rpm = floatValues[0];
        return data;
    }

    /** @hide */
    public static class CarSpeedData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public float carSpeed;

        /** @hide */
        private CarSpeedData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarSpeedData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_CAR_SPEED}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarSpeedData object corresponding to the data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public CarSpeedData getCarSpeedData(CarSpeedData data) {
        checkType(CarSensorManager.SENSOR_TYPE_CAR_SPEED);
        if (data == null) {
            data = new CarSpeedData();
        }
        data.timestamp = timestamp;
        data.carSpeed = floatValues[0];
        return data;
    }

    /** @hide */
    public static class CarWheelTickDistanceData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public long sensorResetCount;
        @AddedInOrBefore(majorVersion = 33)
        public long frontLeftWheelDistanceMm;
        @AddedInOrBefore(majorVersion = 33)
        public long frontRightWheelDistanceMm;
        @AddedInOrBefore(majorVersion = 33)
        public long rearRightWheelDistanceMm;
        @AddedInOrBefore(majorVersion = 33)
        public long rearLeftWheelDistanceMm;

        /** @hide */
        private CarWheelTickDistanceData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarWheelTickDistanceData} object from a
     * CarSensorEvent object with type {@link CarSensorManager#SENSOR_TYPE_WHEEL_TICK_DISTANCE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return CarWheelTickDistanceData object corresponding to data contained in the CarSensorEvent
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public CarWheelTickDistanceData getCarWheelTickDistanceData(CarWheelTickDistanceData data) {
        checkType(CarSensorManager.SENSOR_TYPE_WHEEL_TICK_DISTANCE);
        if (data == null) {
            data = new CarWheelTickDistanceData();
        }
        data.timestamp = timestamp;
        data.sensorResetCount = longValues[INDEX_WHEEL_DISTANCE_RESET_COUNT];
        data.frontLeftWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_FRONT_LEFT];
        data.frontRightWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_FRONT_RIGHT];
        data.rearRightWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_REAR_RIGHT];
        data.rearLeftWheelDistanceMm = longValues[INDEX_WHEEL_DISTANCE_REAR_LEFT];
        return data;
    }

    /** @hide */
    public static class CarAbsActiveData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public boolean absIsActive;

        /** @hide */
        private CarAbsActiveData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarAbsActiveData} object from a CarSensorEvent
     * object with type {@link CarSensorManager#SENSOR_TYPE_ABS_ACTIVE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarAbsActiveData object corresponding to data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public CarAbsActiveData getCarAbsActiveData(CarAbsActiveData data) {
        checkType(CarSensorManager.SENSOR_TYPE_ABS_ACTIVE);
        if (data == null) {
            data = new CarAbsActiveData();
        }
        data.timestamp = timestamp;
        data.absIsActive = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class CarTractionControlActiveData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public boolean tractionControlIsActive;

        /** @hide */
        private CarTractionControlActiveData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarTractionControlActiveData} object from a
     * CarSensorEvent object with type {@link CarSensorManager#SENSOR_TYPE_TRACTION_CONTROL_ACTIVE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarTractionControlActiveData object corresponding to data contained in the
     *     CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public CarTractionControlActiveData getCarTractionControlActiveData(
            CarTractionControlActiveData data) {
        checkType(CarSensorManager.SENSOR_TYPE_TRACTION_CONTROL_ACTIVE);
        if (data == null) {
            data = new CarTractionControlActiveData();
        }
        data.timestamp = timestamp;
        data.tractionControlIsActive = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class CarFuelDoorOpenData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public boolean fuelDoorIsOpen;

        /** @hide */
        private CarFuelDoorOpenData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarFuelDoorOpenData} object from a
     * CarSensorEvent object with type {@link CarSensorManager#SENSOR_TYPE_FUEL_DOOR_OPEN}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarFuelDoorOpenData object corresponding to data contained in the
     *     CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public CarFuelDoorOpenData getCarFuelDoorOpenData(CarFuelDoorOpenData data) {
        checkType(CarSensorManager.SENSOR_TYPE_FUEL_DOOR_OPEN);
        if (data == null) {
            data = new CarFuelDoorOpenData();
        }
        data.timestamp = timestamp;
        data.fuelDoorIsOpen = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class CarEvBatteryLevelData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        /** Battery Level in Watt-hours */
        @AddedInOrBefore(majorVersion = 33)
        public float evBatteryLevel;

        /** @hide */
        private CarEvBatteryLevelData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarEvBatteryLevelData} object from a
     * CarSensorEvent object with type {@link CarSensorManager#SENSOR_TYPE_EV_BATTERY_LEVEL}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarEvBatteryLevelData object corresponding to data contained in the
     *     CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public CarEvBatteryLevelData getCarEvBatteryLevelData(CarEvBatteryLevelData data) {
        checkType(CarSensorManager.SENSOR_TYPE_EV_BATTERY_LEVEL);
        if (data == null) {
            data = new CarEvBatteryLevelData();
        }
        data.timestamp = timestamp;
        if (floatValues == null) {
            data.evBatteryLevel = -1.0f;
        } else {
            if (floatValues[0] < 0) {
                data.evBatteryLevel = -1.0f;
            } else {
                data.evBatteryLevel = floatValues[0];
            }
        }
        return data;
    }

    /** @hide */
    public static class CarEvChargePortOpenData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public boolean evChargePortIsOpen;

        /** @hide */
        private CarEvChargePortOpenData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarEvChargePortOpenData} object from a
     * CarSensorEvent object with type {@link CarSensorManager#SENSOR_TYPE_EV_CHARGE_PORT_OPEN}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarEvChargePortOpenData object corresponding to data contained in the
     *     CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public CarEvChargePortOpenData getCarEvChargePortOpenData(CarEvChargePortOpenData data) {
        checkType(CarSensorManager.SENSOR_TYPE_EV_CHARGE_PORT_OPEN);
        if (data == null) {
            data = new CarEvChargePortOpenData();
        }
        data.timestamp = timestamp;
        data.evChargePortIsOpen = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class CarEvChargePortConnectedData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public boolean evChargePortIsConnected;

        /** @hide */
        private CarEvChargePortConnectedData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarEvChargePortConnectedData} object from a
     * CarSensorEvent with type {@link CarSensorManager#SENSOR_TYPE_EV_CHARGE_PORT_CONNECTED}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarEvChargePortConnectedData object corresponding to data contained in the
     *     CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public CarEvChargePortConnectedData getCarEvChargePortConnectedData(
            CarEvChargePortConnectedData data) {
        checkType(CarSensorManager.SENSOR_TYPE_EV_CHARGE_PORT_CONNECTED);
        if (data == null) {
            data = new CarEvChargePortConnectedData();
        }
        data.timestamp = timestamp;
        data.evChargePortIsConnected = intValues[0] == 1;
        return data;
    }

    /** @hide */
    public static class CarEvBatteryChargeRateData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        /** EV battery charging rate in mW.
         * Positive values indicates battery being charged.  Negative values indicate discharge */
        @AddedInOrBefore(majorVersion = 33)
        public float evChargeRate;

        /** @hide */
        private CarEvBatteryChargeRateData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarEvBatteryChargeRateData} object from a
     * CarSensorEvent object with type {@link CarSensorManager#SENSOR_TYPE_EV_BATTERY_CHARGE_RATE}.
     *
     * @param data an optional output parameter which, if non-null, will be used by this method
     *     instead of a newly created object.
     * @return a CarEvBatteryChargeRateData object corresponding to data contained in the
     *     CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public CarEvBatteryChargeRateData getCarEvBatteryChargeRateData(
            CarEvBatteryChargeRateData data) {
        checkType(CarSensorManager.SENSOR_TYPE_EV_BATTERY_CHARGE_RATE);
        if (data == null) {
            data = new CarEvBatteryChargeRateData();
        }
        data.timestamp = timestamp;
        data.evChargeRate = floatValues[0];
        return data;
    }

    /** @hide */
    public static class CarEngineOilLevelData {
        @AddedInOrBefore(majorVersion = 33)
        public long timestamp;
        @AddedInOrBefore(majorVersion = 33)
        public int engineOilLevel;

        /** @hide */
        private CarEngineOilLevelData() {};
    }

    /**
     * Convenience method for obtaining a {@link CarEngineOilLevelData} object from a
     * CarSensorEvent object with type {@link CarSensorManager#SENSOR_TYPE_ENGINE_OIL_LEVEL}.
     *
     * @param data an optional output parameter, which, if non-null, will be used by this method
     *      instead of a newly created object.
     * @return a CarEngineOilLEvelData object corresponding to data contained in the CarSensorEvent.
     * @hide
     */
    @AddedInOrBefore(majorVersion = 33)
    public CarEngineOilLevelData getCarEngineOilLevelData(CarEngineOilLevelData data) {
        checkType(CarSensorManager.SENSOR_TYPE_ENGINE_OIL_LEVEL);
        if (data == null) {
            data = new CarEngineOilLevelData();
        }
        data.timestamp = timestamp;
        data.engineOilLevel = intValues[0];
        return data;
    }

    /** @hide */
    @Override
    @AddedInOrBefore(majorVersion = 33)
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName() + "[");
        sb.append("type:" + Integer.toHexString(sensorType));
        if (floatValues != null && floatValues.length > 0) {
            sb.append(" float values:");
            for (float v: floatValues) {
                sb.append(" " + v);
            }
        }
        if (intValues != null && intValues.length > 0) {
            sb.append(" int values:");
            for (int v: intValues) {
                sb.append(" " + v);
            }
        }
        if (longValues != null && longValues.length > 0) {
            sb.append(" long values:");
            for (long v: longValues) {
                sb.append(" " + v);
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
