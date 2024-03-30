/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.UiAutomation;
import android.car.Car;
import android.car.EvConnectorType;
import android.car.FuelType;
import android.car.PortLocationType;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaType;
import android.car.VehicleAreaWheel;
import android.car.VehicleGear;
import android.car.VehicleIgnitionState;
import android.car.VehiclePropertyIds;
import android.car.VehicleUnit;
import android.car.cts.utils.VehiclePropertyVerifier;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.car.hardware.property.VehicleElectronicTollCollectionCardStatus;
import android.car.hardware.property.VehicleElectronicTollCollectionCardType;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.support.v4.content.ContextCompat;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;
import android.util.SparseArray;

import androidx.annotation.GuardedBy;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SmallTest
@RequiresDevice
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot get car related permissions.")
public class CarPropertyManagerTest extends CarApiTestBase {

    private static final long WAIT_CALLBACK = 1500L;
    private static final int NO_EVENTS = 0;
    private static final int ONCHANGE_RATE_EVENT_COUNTER = 1;
    private static final int UI_RATE_EVENT_COUNTER = 5;
    private static final int FAST_OR_FASTEST_EVENT_COUNTER = 10;
    private static final ImmutableSet<Integer> PORT_LOCATION_TYPES =
            ImmutableSet.<Integer>builder().add(PortLocationType.UNKNOWN,
                    PortLocationType.FRONT_LEFT, PortLocationType.FRONT_RIGHT,
                    PortLocationType.REAR_RIGHT, PortLocationType.REAR_LEFT,
                    PortLocationType.FRONT, PortLocationType.REAR).build();
    private static final ImmutableSet<Integer> VEHICLE_GEARS =
            ImmutableSet.<Integer>builder().add(VehicleGear.GEAR_UNKNOWN,
                    VehicleGear.GEAR_NEUTRAL, VehicleGear.GEAR_REVERSE,
                    VehicleGear.GEAR_PARK, VehicleGear.GEAR_DRIVE,
                    VehicleGear.GEAR_FIRST, VehicleGear.GEAR_SECOND,
                    VehicleGear.GEAR_THIRD, VehicleGear.GEAR_FOURTH,
                    VehicleGear.GEAR_FIFTH, VehicleGear.GEAR_SIXTH,
                    VehicleGear.GEAR_SEVENTH, VehicleGear.GEAR_EIGHTH,
                    VehicleGear.GEAR_NINTH).build();
    private static final ImmutableSet<Integer> DISTANCE_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.MILLIMETER, VehicleUnit.METER,
                    VehicleUnit.KILOMETER, VehicleUnit.MILE).build();
    private static final ImmutableSet<Integer> VOLUME_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.MILLILITER, VehicleUnit.LITER,
                    VehicleUnit.US_GALLON, VehicleUnit.IMPERIAL_GALLON).build();
    private static final ImmutableSet<Integer> PRESSURE_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.KILOPASCAL, VehicleUnit.PSI,
                    VehicleUnit.BAR).build();
    private static final ImmutableSet<Integer> BATTERY_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.WATT_HOUR, VehicleUnit.AMPERE_HOURS,
                    VehicleUnit.KILOWATT_HOUR).build();
    private static final ImmutableSet<Integer> SPEED_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.METER_PER_SEC,
                    VehicleUnit.MILES_PER_HOUR, VehicleUnit.KILOMETERS_PER_HOUR).build();
    private static final ImmutableSet<Integer> TURN_SIGNAL_STATES =
            ImmutableSet.<Integer>builder().add(/*TurnSignalState.NONE=*/0,
                    /*TurnSignalState.RIGHT=*/1, /*TurnSignalState.LEFT=*/2).build();
    private static final ImmutableSet<Integer> VEHICLE_LIGHT_STATES =
            ImmutableSet.<Integer>builder().add(/*VehicleLightState.OFF=*/0,
                    /*VehicleLightState.ON=*/1, /*VehicleLightState.DAYTIME_RUNNING=*/2).build();
    private static final ImmutableSet<Integer> VEHICLE_LIGHT_SWITCHES =
            ImmutableSet.<Integer>builder().add(/*VehicleLightSwitch.OFF=*/0,
                    /*VehicleLightSwitch.ON=*/1, /*VehicleLightSwitch.DAYTIME_RUNNING=*/2,
                    /*VehicleLightSwitch.AUTOMATIC=*/256).build();
    private static final ImmutableSet<Integer> HVAC_TEMPERATURE_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.CELSIUS,
                    VehicleUnit.FAHRENHEIT).build();
    private static final ImmutableSet<Integer> SINGLE_HVAC_FAN_DIRECTIONS = ImmutableSet.of(
            /*VehicleHvacFanDirection.FACE=*/0x1, /*VehicleHvacFanDirection.FLOOR=*/0x2,
            /*VehicleHvacFanDirection.DEFROST=*/0x4);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_HVAC_FAN_DIRECTIONS =
            generateAllPossibleHvacFanDirections();
    private static final ImmutableSet<Integer> VEHICLE_SEAT_OCCUPANCY_STATES = ImmutableSet.of(
            /*VehicleSeatOccupancyState.UNKNOWN=*/0, /*VehicleSeatOccupancyState.VACANT=*/1,
            /*VehicleSeatOccupancyState.OCCUPIED=*/2);

    /** contains property Ids for the properties required by CDD */
    private final ArraySet<Integer> mPropertyIds = new ArraySet<>();
    private CarPropertyManager mCarPropertyManager;

    private static ImmutableSet<Integer> generateAllPossibleHvacFanDirections() {
        ImmutableSet.Builder<Integer> allPossibleFanDirectionsBuilder = ImmutableSet.builder();
        for (int i = 1; i <= SINGLE_HVAC_FAN_DIRECTIONS.size(); i++) {
            allPossibleFanDirectionsBuilder.addAll(Sets.combinations(SINGLE_HVAC_FAN_DIRECTIONS,
                    i).stream().map(hvacFanDirectionCombo -> {
                Integer possibleHvacFanDirection = 0;
                for (Integer hvacFanDirection : hvacFanDirectionCombo) {
                    possibleHvacFanDirection |= hvacFanDirection;
                }
                return possibleHvacFanDirection;
            }).collect(Collectors.toList()));
        }
        return allPossibleFanDirectionsBuilder.build();
    }

    private static void verifyWheelTickConfigArray(int supportedWheels, int wheelToVerify,
            int configArrayIndex, int wheelTicksToUm) {
        if ((supportedWheels & wheelToVerify) != 0) {
            assertWithMessage(
                    "WHEEL_TICK configArray[" + configArrayIndex
                            + "] must specify the ticks to micrometers for " + wheelToString(
                            wheelToVerify))
                    .that(wheelTicksToUm)
                    .isGreaterThan(0);
        } else {
            assertWithMessage(
                    "WHEEL_TICK configArray[" + configArrayIndex + "] should be zero since "
                            + wheelToString(wheelToVerify)
                            + "is not supported")
                    .that(wheelTicksToUm)
                    .isEqualTo(0);
        }
    }

    private static void verifyWheelTickValue(int supportedWheels, int wheelToVerify,
            int valueIndex, Long ticks) {
        if ((supportedWheels & wheelToVerify) == 0) {
            assertWithMessage(
                    "WHEEL_TICK value[" + valueIndex + "] should be zero since "
                            + wheelToString(wheelToVerify)
                            + "is not supported")
                    .that(ticks)
                    .isEqualTo(0);
        }
    }

    private static String wheelToString(int wheel) {
        switch (wheel) {
            case VehicleAreaWheel.WHEEL_LEFT_FRONT:
                return "WHEEL_LEFT_FRONT";
            case VehicleAreaWheel.WHEEL_RIGHT_FRONT:
                return "WHEEL_RIGHT_FRONT";
            case VehicleAreaWheel.WHEEL_RIGHT_REAR:
                return "WHEEL_RIGHT_REAR";
            case VehicleAreaWheel.WHEEL_LEFT_REAR:
                return "WHEEL_LEFT_REAR";
            default:
                return Integer.toString(wheel);
        }
    }

    private static void adoptSystemLevelPermission(String permission, Runnable verifierRunnable) {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.adoptShellPermissionIdentity(permission);
        try {
            assumeTrue("Unable to adopt Car Shell permission: " + permission,
                    ContextCompat.checkSelfPermission(
                            InstrumentationRegistry.getInstrumentation().getTargetContext(),
                            permission) == PackageManager.PERMISSION_GRANTED);
            verifierRunnable.run();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mCarPropertyManager = (CarPropertyManager) getCar().getCarManager(Car.PROPERTY_SERVICE);
        mPropertyIds.add(VehiclePropertyIds.PERF_VEHICLE_SPEED);
        mPropertyIds.add(VehiclePropertyIds.GEAR_SELECTION);
        mPropertyIds.add(VehiclePropertyIds.NIGHT_MODE);
        mPropertyIds.add(VehiclePropertyIds.PARKING_BRAKE_ON);
    }

    /**
     * Test for {@link CarPropertyManager#getPropertyList()}
     */
    @Test
    public void testGetPropertyList() {
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        assertThat(allConfigs).isNotNull();
    }

    /**
     * Test for {@link CarPropertyManager#getPropertyList(ArraySet)}
     */
    @Test
    public void testGetPropertyListWithArraySet() {
        List<CarPropertyConfig> requiredConfigs = mCarPropertyManager.getPropertyList(mPropertyIds);
        // Vehicles need to implement all of those properties
        assertThat(requiredConfigs.size()).isEqualTo(mPropertyIds.size());
    }

    /**
     * Test for {@link CarPropertyManager#getCarPropertyConfig(int)}
     */
    @Test
    public void testGetPropertyConfig() {
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : allConfigs) {
            assertThat(mCarPropertyManager.getCarPropertyConfig(cfg.getPropertyId())).isNotNull();
        }
    }

    /**
     * Test for {@link CarPropertyManager#getAreaId(int, int)}
     */
    @Test
    public void testGetAreaId() {
        // For global properties, getAreaId should always return 0.
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : allConfigs) {
            if (cfg.isGlobalProperty()) {
                assertThat(mCarPropertyManager.getAreaId(cfg.getPropertyId(),
                        VehicleAreaSeat.SEAT_ROW_1_LEFT))
                        .isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
            } else {
                int[] areaIds = cfg.getAreaIds();
                // Because areaId in propConfig must not be overlapped with each other.
                // The result should be itself.
                for (int areaIdInConfig : areaIds) {
                    int areaIdByCarPropertyManager =
                            mCarPropertyManager.getAreaId(cfg.getPropertyId(), areaIdInConfig);
                    assertThat(areaIdByCarPropertyManager).isEqualTo(areaIdInConfig);
                }
            }
        }
    }

    @Test
    public void testInvalidMustNotBeImplemented() {
        assertThat(mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.INVALID)).isNull();
    }

    @CddTest(requirement = "2.5.1")
    @Test
    public void testMustSupportGearSelection() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.GEAR_SELECTION,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Integer.class).requireProperty().setPossibleConfigArrayValues(
                VEHICLE_GEARS).requirePropertyValueTobeInConfigArray().build().verify(
                mCarPropertyManager);
    }

    @CddTest(requirement = "2.5.1")
    @Test
    public void testMustSupportNightMode() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.NIGHT_MODE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Boolean.class).requireProperty().build().verify(mCarPropertyManager);
    }

    @CddTest(requirement = "2.5.1")
    @Test
    public void testMustSupportPerfVehicleSpeed() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                Float.class).requireProperty().build().verify(mCarPropertyManager);
    }

    @Test
    public void testPerfVehicleSpeedDisplayIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                Float.class).build().verify(mCarPropertyManager);
    }

    @CddTest(requirement = "2.5.1")
    @Test
    public void testMustSupportParkingBrakeOn() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.PARKING_BRAKE_ON,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Boolean.class).requireProperty().build().verify(mCarPropertyManager);
    }

    @Test
    public void testWheelTickIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.WHEEL_TICK,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                Long[].class).setConfigArrayVerifier(
                configArray -> {
                    assertWithMessage("WHEEL_TICK config array must be size 5")
                            .that(configArray.size())
                            .isEqualTo(5);

                    int supportedWheels = configArray.get(0);
                    assertWithMessage(
                            "WHEEL_TICK config array first element specifies which wheels are"
                                    + " supported")
                            .that(supportedWheels).isGreaterThan(
                                    VehicleAreaWheel.WHEEL_UNKNOWN);
                    assertWithMessage(
                            "WHEEL_TICK config array first element specifies which wheels are"
                                    + " supported")
                            .that(supportedWheels)
                            .isAtMost(VehicleAreaWheel.WHEEL_LEFT_FRONT
                                    | VehicleAreaWheel.WHEEL_RIGHT_FRONT |
                                    VehicleAreaWheel.WHEEL_LEFT_REAR
                                    | VehicleAreaWheel.WHEEL_RIGHT_REAR);

                    verifyWheelTickConfigArray(supportedWheels,
                            VehicleAreaWheel.WHEEL_LEFT_FRONT, 1, configArray.get(1));
                    verifyWheelTickConfigArray(supportedWheels,
                            VehicleAreaWheel.WHEEL_RIGHT_FRONT, 2, configArray.get(2));
                    verifyWheelTickConfigArray(supportedWheels,
                            VehicleAreaWheel.WHEEL_RIGHT_REAR, 3, configArray.get(3));
                    verifyWheelTickConfigArray(supportedWheels,
                            VehicleAreaWheel.WHEEL_LEFT_REAR, 4, configArray.get(4));
                }).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    List<Integer> wheelTickConfigArray = carPropertyConfig.getConfigArray();
                    int supportedWheels = wheelTickConfigArray.get(0);

                    Long[] wheelTicks = (Long[]) carPropertyValue.getValue();
                    assertWithMessage("WHEEL_TICK Long[] value must be size 5").that(
                            wheelTicks.length).isEqualTo(5);

                    verifyWheelTickValue(supportedWheels, VehicleAreaWheel.WHEEL_LEFT_FRONT, 1,
                            wheelTicks[1]);
                    verifyWheelTickValue(supportedWheels, VehicleAreaWheel.WHEEL_RIGHT_FRONT, 2,
                            wheelTicks[2]);
                    verifyWheelTickValue(supportedWheels, VehicleAreaWheel.WHEEL_RIGHT_REAR, 3,
                            wheelTicks[3]);
                    verifyWheelTickValue(supportedWheels, VehicleAreaWheel.WHEEL_LEFT_REAR, 4,
                            wheelTicks[4]);
                }).build().verify(mCarPropertyManager);

    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testInfoVinIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_IDENTIFICATION, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_VIN,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                    String.class).setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> assertWithMessage(
                            "INFO_VIN must be 17 characters").that(
                                (String) carPropertyValue.getValue()).hasLength(17))
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testInfoMakeIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_MAKE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                String.class).build().verify(mCarPropertyManager);
    }

    @Test
    public void testInfoModelIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_MODEL,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                String.class).build().verify(mCarPropertyManager);
    }

    @Test
    public void testInfoModelYearIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_MODEL_YEAR,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                Integer.class).build().verify(mCarPropertyManager);
    }

    @Test
    public void testInfoFuelCapacityIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_FUEL_CAPACITY,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                Float.class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> assertWithMessage(
                        "INFO_FUEL_CAPACITY Float value must be greater than or equal 0").that(
                        (Float) carPropertyValue.getValue()).isAtLeast(0)).build().verify(
                mCarPropertyManager);
    }

    @Test
    public void testInfoFuelTypeIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_FUEL_TYPE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                Integer[].class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    Integer[] fuelTypes = (Integer[]) carPropertyValue.getValue();
                    assertWithMessage("INFO_FUEL_TYPE must specify at least 1 fuel type").that(
                            fuelTypes.length).isGreaterThan(0);
                    for (Integer fuelType : fuelTypes) {
                        assertWithMessage(
                                "INFO_FUEL_TYPE must be a defined fuel type: " + fuelType).that(
                                fuelType).isIn(
                                ImmutableSet.builder().add(FuelType.UNKNOWN, FuelType.UNLEADED,
                                        FuelType.LEADED, FuelType.DIESEL_1, FuelType.DIESEL_2,
                                        FuelType.BIODIESEL, FuelType.E85, FuelType.LPG,
                                        FuelType.CNG, FuelType.LNG, FuelType.ELECTRIC,
                                        FuelType.HYDROGEN, FuelType.OTHER).build());
                    }
                }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testInfoEvBatteryCapacityIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                Float.class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> assertWithMessage(
                        "INFO_EV_BATTERY_CAPACITY Float value must be greater than or equal to 0")
                        .that((Float) carPropertyValue.getValue()).isAtLeast(0)).build().verify(
                mCarPropertyManager);
    }

    @Test
    public void testInfoEvConnectorTypeIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                Integer[].class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    Integer[] evConnectorTypes = (Integer[]) carPropertyValue.getValue();
                    assertWithMessage(
                            "INFO_EV_CONNECTOR_TYPE must specify at least 1 connection type").that(
                            evConnectorTypes.length).isGreaterThan(0);
                    for (Integer evConnectorType : evConnectorTypes) {
                        assertWithMessage(
                                "INFO_EV_CONNECTOR_TYPE must be a defined connection type: "
                                        + evConnectorType).that(
                                evConnectorType).isIn(
                                ImmutableSet.builder().add(EvConnectorType.UNKNOWN,
                                        EvConnectorType.J1772, EvConnectorType.MENNEKES,
                                        EvConnectorType.CHADEMO, EvConnectorType.COMBO_1,
                                        EvConnectorType.COMBO_2, EvConnectorType.TESLA_ROADSTER,
                                        EvConnectorType.TESLA_HPWC,
                                        EvConnectorType.TESLA_SUPERCHARGER, EvConnectorType.GBT,
                                        EvConnectorType.GBT_DC, EvConnectorType.SCAME,
                                        EvConnectorType.OTHER).build());
                    }
                }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testInfoFuelDoorLocationIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                Integer.class).setPossibleCarPropertyValues(PORT_LOCATION_TYPES).build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoEvPortLocationIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_EV_PORT_LOCATION,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                Integer.class).setPossibleCarPropertyValues(PORT_LOCATION_TYPES).build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoMultiEvPortLocationsIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_MULTI_EV_PORT_LOCATIONS,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                Integer[].class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    Integer[] evPortLocations = (Integer[]) carPropertyValue.getValue();
                    assertWithMessage(
                            "INFO_MULTI_EV_PORT_LOCATIONS must specify at least 1 port location")
                            .that(evPortLocations.length).isGreaterThan(0);
                    for (Integer evPortLocation : evPortLocations) {
                        assertWithMessage(
                                "INFO_MULTI_EV_PORT_LOCATIONS must be a defined port location: "
                                        + evPortLocation).that(
                                evPortLocation).isIn(PORT_LOCATION_TYPES);
                    }
                }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testInfoDriverSeatIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_DRIVER_SEAT,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                Integer.class).setPossibleCarPropertyValues(ImmutableSet.of(
                        VehicleAreaSeat.SEAT_UNKNOWN,
                        VehicleAreaSeat.SEAT_ROW_1_LEFT,
                        VehicleAreaSeat.SEAT_ROW_1_CENTER,
                        VehicleAreaSeat.SEAT_ROW_1_RIGHT))
                .setAreaIdsVerifier(areaIds -> assertWithMessage(
                "Even though INFO_DRIVER_SEAT is VEHICLE_AREA_TYPE_SEAT, it is meant to be "
                        + "VEHICLE_AREA_TYPE_GLOBAL, so its AreaIds must contain a single 0").that(
                areaIds).isEqualTo(new int[]{VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL}))
                .build().verify(mCarPropertyManager);
    }

    @Test
    public void testInfoExteriorDimensionsIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                Integer[].class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    Integer[] exteriorDimensions = (Integer[]) carPropertyValue.getValue();
                    assertWithMessage(
                            "INFO_EXTERIOR_DIMENSIONS must specify all 8 dimension measurements")
                            .that(exteriorDimensions.length).isEqualTo(8);
                    for (Integer exteriorDimension : exteriorDimensions) {
                        assertWithMessage(
                                "INFO_EXTERIOR_DIMENSIONS measurement must be greater than 0").that(
                                exteriorDimension).isGreaterThan(0);
                    }
                }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testElectronicTollCollectionCardTypeIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_TYPE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Integer.class).setPossibleCarPropertyValues(ImmutableSet.of(
                    VehicleElectronicTollCollectionCardType.UNKNOWN,
                    VehicleElectronicTollCollectionCardType.JP_ELECTRONIC_TOLL_COLLECTION_CARD,
                    VehicleElectronicTollCollectionCardType.JP_ELECTRONIC_TOLL_COLLECTION_CARD_V2)
        ).build().verify(mCarPropertyManager);
    }

    @Test
    public void testElectronicTollCollectionCardStatusIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_STATUS,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Integer.class).setPossibleCarPropertyValues(ImmutableSet.of(
                        VehicleElectronicTollCollectionCardStatus.UNKNOWN,
                        VehicleElectronicTollCollectionCardStatus
                                .ELECTRONIC_TOLL_COLLECTION_CARD_VALID,
                        VehicleElectronicTollCollectionCardStatus
                                .ELECTRONIC_TOLL_COLLECTION_CARD_INVALID,
                        VehicleElectronicTollCollectionCardStatus
                                .ELECTRONIC_TOLL_COLLECTION_CARD_NOT_INSERTED)
        ).build().verify(mCarPropertyManager);
    }

    @Test
    public void testEnvOutsideTemperatureIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                Float.class).build().verify(mCarPropertyManager);
    }

    @Test
    public void testCurrentGearIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.CURRENT_GEAR,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Integer.class).setPossibleConfigArrayValues(
                VEHICLE_GEARS).requirePropertyValueTobeInConfigArray().build().verify(
                mCarPropertyManager);
    }

    @Test
    public void testParkingBrakeAutoApplyIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.PARKING_BRAKE_AUTO_APPLY,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Boolean.class).build().verify(mCarPropertyManager);
    }

    @Test
    public void testIgnitionStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.IGNITION_STATE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Integer.class).setPossibleCarPropertyValues(ImmutableSet.of(
                        VehicleIgnitionState.UNDEFINED, VehicleIgnitionState.LOCK,
                        VehicleIgnitionState.OFF, VehicleIgnitionState.ACC, VehicleIgnitionState.ON,
                VehicleIgnitionState.START)).build().verify(mCarPropertyManager);
    }

    @Test
    public void testAbsActiveIfSupported() {
        adoptSystemLevelPermission(/* Car.PERMISSION_CAR_DYNAMICS_STATE = */
                "android.car.permission.CAR_DYNAMICS_STATE", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.ABS_ACTIVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Boolean.class).build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testTractionControlActiveIfSupported() {
        adoptSystemLevelPermission(/* Car.PERMISSION_CAR_DYNAMICS_STATE = */
                "android.car.permission.CAR_DYNAMICS_STATE", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.TRACTION_CONTROL_ACTIVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Boolean.class).build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testDoorPosIfSupported() {
        adoptSystemLevelPermission(/* Car.PERMISSION_CONTROL_CAR_DOORS = */
                "android.car.permission.CONTROL_CAR_DOORS", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.DOOR_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().requireMinValuesToBeZero().build()
                            .verify(mCarPropertyManager);
                });
    }

    @Test
    public void testDoorMoveIfSupported() {
        adoptSystemLevelPermission(/* Car.PERMISSION_CONTROL_CAR_DOORS = */
                "android.car.permission.CONTROL_CAR_DOORS", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.DOOR_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                            .requireZeroToBeContainedInMinMaxRanges().build().verify(
                            mCarPropertyManager);
                });
    }

    @Test
    public void testDoorLockIfSupported() {
        adoptSystemLevelPermission(/* Car.PERMISSION_CONTROL_CAR_DOORS = */
                "android.car.permission.CONTROL_CAR_DOORS", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.DOOR_LOCK,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class).build().verify(mCarPropertyManager);
                });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testMirrorZPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.MIRROR_Z_POS,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testMirrorZMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.MIRROR_Z_MOVE,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testMirrorYPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.MIRROR_Y_POS,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testMirrorYMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.MIRROR_Y_MOVE,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testMirrorLockIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.MIRROR_LOCK,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE, Boolean.class)
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testMirrorFoldIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.MIRROR_FOLD,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE, Boolean.class)
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testWindowPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.WINDOW_POS,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testWindowMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.WINDOW_MOVE,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testWindowLockIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.WINDOW_LOCK,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testDistanceDisplayUnitsIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_VENDOR_EXTENSION=*/
                "android.car.permission.CAR_VENDOR_EXTENSION", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.DISTANCE_DISPLAY_UNITS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleConfigArrayValues(
                            DISTANCE_DISPLAY_UNITS).requirePropertyValueTobeInConfigArray()
                            .verifySetterWithConfigArrayValues().build().verify(
                            mCarPropertyManager);
            });
    }

    @Test
    public void testFuelVolumeDisplayUnitsIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_VENDOR_EXTENSION=*/
                "android.car.permission.CAR_VENDOR_EXTENSION", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.FUEL_VOLUME_DISPLAY_UNITS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleConfigArrayValues(
                            VOLUME_DISPLAY_UNITS).requirePropertyValueTobeInConfigArray()
                            .verifySetterWithConfigArrayValues().build().verify(
                            mCarPropertyManager);

                });
    }

    @Test
    public void testTirePressureIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_TIRES=*/
                "android.car.permission.CAR_TIRES", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.TIRE_PRESSURE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                            Float.class).requireMinMaxValues().setCarPropertyValueVerifier(
                                    (carPropertyConfig, carPropertyValue) -> assertWithMessage(
                                            "TIRE_PRESSURE Float value"
                                                    + " at Area ID equals to "
                                                    + carPropertyValue.getAreaId()
                                                    + " must be greater than or equal 0").that(
                                    (Float) carPropertyValue.getValue()).isAtLeast(
                                    0)).build().verify(
                            mCarPropertyManager);
                });
    }

    @Test
    public void testCriticallyLowTirePressureIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_TIRES=*/
                "android.car.permission.CAR_TIRES", () -> {
                    VehiclePropertyVerifier.newBuilder(
                            VehiclePropertyIds.CRITICALLY_LOW_TIRE_PRESSURE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                            Float.class).setCarPropertyValueVerifier(
                                    (carPropertyConfig, carPropertyValue) -> {
                                        int areaId = carPropertyValue.getAreaId();

                                        assertWithMessage(
                                                "CRITICALLY_LOW_TIRE_PRESSURE Float value"
                                                        + "at Area ID equals to" + areaId
                                                        + " must be greater than or equal 0")
                                            .that((Float) carPropertyValue.getValue()).isAtLeast(0);

                                        CarPropertyConfig<?> tirePressureConfig =
                                                mCarPropertyManager.getCarPropertyConfig(
                                                        VehiclePropertyIds.TIRE_PRESSURE);

                                        if (tirePressureConfig == null
                                                || tirePressureConfig.getMinValue(areaId) == null) {
                                            return;
                                        }

                                        assertWithMessage(
                                                "CRITICALLY_LOW_TIRE_PRESSURE Float value"
                                                        + "at Area ID equals to" + areaId
                                                        + " must not exceed"
                                                        + " minFloatValue in TIRE_PRESSURE")
                                                .that((Float) carPropertyValue.getValue()).isAtMost(
                                                        (Float) tirePressureConfig
                                                                .getMinValue(areaId));
                                    }).build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testTirePressureDisplayUnitsIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_VENDOR_EXTENSION=*/
                "android.car.permission.CAR_VENDOR_EXTENSION", () -> {
                    VehiclePropertyVerifier.newBuilder(
                            VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleConfigArrayValues(
                            PRESSURE_DISPLAY_UNITS).requirePropertyValueTobeInConfigArray()
                            .verifySetterWithConfigArrayValues().build().verify(
                            mCarPropertyManager);

                });
    }

    @Test
    public void testEvBatteryDisplayUnitsIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_VENDOR_EXTENSION=*/
                "android.car.permission.CAR_VENDOR_EXTENSION", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.EV_BATTERY_DISPLAY_UNITS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleConfigArrayValues(
                            BATTERY_DISPLAY_UNITS).requirePropertyValueTobeInConfigArray()
                            .verifySetterWithConfigArrayValues().build().verify(
                            mCarPropertyManager);

                });
    }

    @Test
    public void testVehicleSpeedDisplayUnitsIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_VENDOR_EXTENSION=*/
                "android.car.permission.CAR_VENDOR_EXTENSION", () -> {
                    VehiclePropertyVerifier.newBuilder(
                            VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleConfigArrayValues(
                            SPEED_DISPLAY_UNITS).requirePropertyValueTobeInConfigArray()
                            .verifySetterWithConfigArrayValues().build().verify(
                            mCarPropertyManager);
                });
    }

    @Test
    public void testFuelConsumptionUnitsDistanceOverTimeIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_VENDOR_EXTENSION, () -> {
            VehiclePropertyVerifier.newBuilder(
                    VehiclePropertyIds.FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testFuelLevelIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                VehiclePropertyIds.FUEL_LEVEL,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                Float.class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    assertWithMessage(
                            "FUEL_LEVEL Float value must be greater than or equal 0").that(
                            (Float) carPropertyValue.getValue()).isAtLeast(0);

                    if (mCarPropertyManager.getCarPropertyConfig(
                            VehiclePropertyIds.INFO_FUEL_CAPACITY) == null) {
                        return;
                    }

                    CarPropertyValue<?> infoFuelCapacityValue = mCarPropertyManager.getProperty(
                            VehiclePropertyIds.INFO_FUEL_CAPACITY,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);

                    assertWithMessage(
                            "FUEL_LEVEL Float value must not exceed INFO_FUEL_CAPACITY Float "
                                    + "value").that(
                            (Float) carPropertyValue.getValue()).isAtMost(
                            (Float) infoFuelCapacityValue.getValue());
                }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testEvBatteryLevelIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                VehiclePropertyIds.EV_BATTERY_LEVEL,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                Float.class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    assertWithMessage(
                            "EV_BATTERY_LEVEL Float value must be greater than or equal 0").that(
                            (Float) carPropertyValue.getValue()).isAtLeast(0);

                    if (mCarPropertyManager.getCarPropertyConfig(
                            VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY) == null) {
                        return;
                    }

                    CarPropertyValue<?> infoEvBatteryCapacityValue =
                            mCarPropertyManager.getProperty(
                                    VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);

                    assertWithMessage(
                            "EV_BATTERY_LEVEL Float value must not exceed "
                                    + "INFO_EV_BATTERY_CAPACITY Float "
                                    + "value").that(
                            (Float) carPropertyValue.getValue()).isAtMost(
                            (Float) infoEvBatteryCapacityValue.getValue());
                }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testEvBatteryInstantaneousChargeRateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                Float.class).build().verify(mCarPropertyManager);
    }

    @Test
    public void testRangeRemainingIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                VehiclePropertyIds.RANGE_REMAINING,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                Float.class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    assertWithMessage(
                            "RANGE_REMAINING Float value must be greater than or equal 0").that(
                            (Float) carPropertyValue.getValue()).isAtLeast(0);
                }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testFuelLevelLowIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                VehiclePropertyIds.FUEL_LEVEL_LOW,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Boolean.class).build().verify(mCarPropertyManager);
    }

    @Test
    public void testFuelDoorOpenIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_ENERGY_PORTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.FUEL_DOOR_OPEN,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testEvChargePortOpenIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_ENERGY_PORTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testEvChargePortConnectedIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Boolean.class).build().verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargeCurrentDrawLimitIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.EV_CHARGE_CURRENT_DRAW_LIMIT,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Float.class).setConfigArrayVerifier(configArray -> {
            assertWithMessage("EV_CHARGE_CURRENT_DRAW_LIMIT config array must be size 1").that(
                    configArray.size()).isEqualTo(1);

            int maxCurrentDrawThresholdAmps = configArray.get(0);
            assertWithMessage("EV_CHARGE_CURRENT_DRAW_LIMIT config array first element specifies "
                    + "max current draw allowed by vehicle in amperes.").that(
                    maxCurrentDrawThresholdAmps).isGreaterThan(0);
        }).setCarPropertyValueVerifier((carPropertyConfig, carPropertyValue) -> {
            List<Integer> evChargeCurrentDrawLimitConfigArray = carPropertyConfig.getConfigArray();
            int maxCurrentDrawThresholdAmps = evChargeCurrentDrawLimitConfigArray.get(0);

            Float evChargeCurrentDrawLimit = (Float) carPropertyValue.getValue();
            assertWithMessage("EV_CHARGE_CURRENT_DRAW_LIMIT value must be greater than 0").that(
                    evChargeCurrentDrawLimit).isGreaterThan(0);
            assertWithMessage("EV_CHARGE_CURRENT_DRAW_LIMIT value must be less than or equal to max"
                    + " current draw by the vehicle").that(evChargeCurrentDrawLimit).isAtMost(
                    maxCurrentDrawThresholdAmps);
        }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargePercentLimitIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Float.class).setConfigArrayVerifier(configArray -> {
            for (int i = 0; i < configArray.size(); i++) {
                assertWithMessage("EV_CHARGE_PERCENT_LIMIT configArray[" + i
                        + "] valid charge percent limit must be greater than 0").that(
                        configArray.get(i)).isGreaterThan(0);
                assertWithMessage("EV_CHARGE_PERCENT_LIMIT configArray[" + i
                        + "] valid charge percent limit must be at most 100").that(
                        configArray.get(i)).isAtMost(100);
            }
        }).setCarPropertyValueVerifier((carPropertyConfig, carPropertyValue) -> {
            List<Integer> evChargePercentLimitConfigArray = carPropertyConfig.getConfigArray();
            Float evChargePercentLimit = (Float) carPropertyValue.getValue();

            if (evChargePercentLimitConfigArray.isEmpty()) {
                assertWithMessage("EV_CHARGE_PERCENT_LIMIT value must be greater than 0").that(
                        evChargePercentLimit).isGreaterThan(0);
                assertWithMessage("EV_CHARGE_PERCENT_LIMIT value must be at most 100").that(
                        evChargePercentLimit).isAtMost(100);
            } else {
                assertWithMessage(
                        "EV_CHARGE_PERCENT_LIMIT value must be in the configArray valid charge "
                                + "percent limit list").that(evChargePercentLimit.intValue()).isIn(
                        evChargePercentLimitConfigArray);
            }
        }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargeStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.EV_CHARGE_STATE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Integer.class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    Integer evChargeState = (Integer) carPropertyValue.getValue();
                    assertWithMessage("EV_CHARGE_STATE must be a defined charge state: "
                            + evChargeState).that(evChargeState).isIn(
                            ImmutableSet.of(/*EvChargeState.UNKNOWN=*/0,
                                    /*EvChargeState.CHARGING=*/1, /*EvChargeState.FULLY_CHARGED=*/2,
                                    /*EvChargeState.NOT_CHARGING=*/3, /*EvChargeState.ERROR=*/4));
                }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargeSwitchIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_ENERGY, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.EV_CHARGE_SWITCH,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testEvChargeTimeRemainingIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.EV_CHARGE_TIME_REMAINING,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                Integer.class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    assertWithMessage(
                            "FUEL_LEVEL Integer value must be greater than or equal 0").that(
                            (Integer) carPropertyValue.getValue()).isAtLeast(0);

                }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testEvRegenerativeBrakingStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.EV_REGENERATIVE_BRAKING_STATE,
                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                Integer.class).setCarPropertyValueVerifier(
                (carPropertyConfig, carPropertyValue) -> {
                    Integer evRegenerativeBrakingState = (Integer) carPropertyValue.getValue();
                    assertWithMessage("EV_REGENERATIVE_BRAKING_STATE must be a defined state: "
                            + evRegenerativeBrakingState).that(evRegenerativeBrakingState).isIn(
                            ImmutableSet.of(/*EvRegenerativeBrakingState.UNKNOWN=*/0,
                                    /*EvRegenerativeBrakingState.DISABLED=*/1,
                                    /*EvRegenerativeBrakingState.PARTIALLY_ENABLED=*/2,
                                    /*EvRegenerativeBrakingState.FULLY_ENABLED=*/3));
                }).build().verify(mCarPropertyManager);
    }

    @Test
    public void testPerfSteeringAngleIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_READ_STEERING_STATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.PERF_STEERING_ANGLE,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                    Float.class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testPerfRearSteeringAngleIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_READ_STEERING_STATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.PERF_REAR_STEERING_ANGLE,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                    Float.class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testEngineCoolantTempIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_CAR_ENGINE_DETAILED=*/
                "android.car.permission.CAR_ENGINE_DETAILED", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.ENGINE_COOLANT_TEMP,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                            Float.class).build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testEngineOilLevelIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_CAR_ENGINE_DETAILED=*/
                "android.car.permission.CAR_ENGINE_DETAILED", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.ENGINE_OIL_LEVEL,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setCarPropertyValueVerifier(
                                    (carPropertyConfig, carPropertyValue) -> assertWithMessage(
                                    "ENGINE_OIL_LEVEL Integer value must be greater than or equal"
                                            + " 0").that(
                                    (Integer) carPropertyValue.getValue()).isAtLeast(
                                    0)).build().verify(
                            mCarPropertyManager);
                });
    }

    @Test
    public void testEngineOilTempIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_CAR_ENGINE_DETAILED=*/
                "android.car.permission.CAR_ENGINE_DETAILED", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.ENGINE_OIL_TEMP,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                            Float.class).build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testEngineRpmIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_CAR_ENGINE_DETAILED=*/
                "android.car.permission.CAR_ENGINE_DETAILED", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.ENGINE_RPM,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                            Float.class).setCarPropertyValueVerifier(
                                    (carPropertyConfig, carPropertyValue) -> assertWithMessage(
                                    "ENGINE_RPM Float value must be greater than or equal 0").that(
                                    (Float) carPropertyValue.getValue()).isAtLeast(
                                    0)).build().verify(
                            mCarPropertyManager);
                });
    }

    @Test
    public void testPerfOdometerIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_MILEAGE=*/"android.car.permission.CAR_MILEAGE",
                () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.PERF_ODOMETER,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                            Float.class).setCarPropertyValueVerifier(
                            (carPropertyConfig, carPropertyValue) -> assertWithMessage(
                                    "PERF_ODOMETER Float value must be greater than or equal 0")
                                    .that((Float) carPropertyValue.getValue()).isAtLeast(0))
                            .build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testTurnSignalStateIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_EXTERIOR_LIGHTS=*/
                "android.car.permission.CAR_EXTERIOR_LIGHTS", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.TURN_SIGNAL_STATE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(TURN_SIGNAL_STATES).build()
                            .verify(mCarPropertyManager);
                });
    }

    @Test
    public void testHeadlightsStateIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_EXTERIOR_LIGHTS=*/
                "android.car.permission.CAR_EXTERIOR_LIGHTS", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HEADLIGHTS_STATE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                            .build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testHighBeamLightsStateIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_EXTERIOR_LIGHTS=*/
                "android.car.permission.CAR_EXTERIOR_LIGHTS", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                            .build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testFogLightsStateIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_EXTERIOR_LIGHTS=*/
                "android.car.permission.CAR_EXTERIOR_LIGHTS", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.FOG_LIGHTS_STATE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                            .setCarPropertyValueVerifier(
                                (carPropertyConfig, carPropertyValue) -> {
                                    assertWithMessage(
                                            "FRONT_FOG_LIGHTS_STATE must not be implemented"
                                                    + "when FOG_LIGHTS_STATE is implemented")
                                            .that(mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.FRONT_FOG_LIGHTS_STATE))
                                            .isNull();

                                    assertWithMessage(
                                            "REAR_FOG_LIGHTS_STATE must not be implemented"
                                                    + "when FOG_LIGHTS_STATE is implemented")
                                            .that(mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.REAR_FOG_LIGHTS_STATE))
                                            .isNull();
                                }).build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testHazardLightsStateIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_EXTERIOR_LIGHTS=*/
                "android.car.permission.CAR_EXTERIOR_LIGHTS", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HAZARD_LIGHTS_STATE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                            .build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testFrontFogLightsStateIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_EXTERIOR_LIGHTS=*/
                "android.car.permission.CAR_EXTERIOR_LIGHTS", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.FRONT_FOG_LIGHTS_STATE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                            .setCarPropertyValueVerifier(
                                (carPropertyConfig, carPropertyValue) -> {
                                    assertWithMessage(
                                            "FOG_LIGHTS_STATE must not be implemented"
                                                    + "when FRONT_FOG_LIGHTS_STATE is implemented")
                                            .that(mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.FOG_LIGHTS_STATE))
                                            .isNull();
                                }).build().verify(mCarPropertyManager);
                });
    }

    @Test
    public void testRearFogLightsStateIfSupported() {
        adoptSystemLevelPermission(/*Car.PERMISSION_EXTERIOR_LIGHTS=*/
                "android.car.permission.CAR_EXTERIOR_LIGHTS", () -> {
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.REAR_FOG_LIGHTS_STATE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                            .setCarPropertyValueVerifier(
                                (carPropertyConfig, carPropertyValue) -> {
                                    assertWithMessage(
                                            "FOG_LIGHTS_STATE must not be implemented"
                                                    + "when REAR_FOG_LIGHTS_STATE is implemented")
                                            .that(mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.FOG_LIGHTS_STATE))
                                            .isNull();
                                }).build().verify(mCarPropertyManager);
                });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testCabinLightsStateIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.CABIN_LIGHTS_STATE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testReadingLightsStateIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.READING_LIGHTS_STATE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testVehicleCurbWeightIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_PRIVILEGED_CAR_INFO, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.VEHICLE_CURB_WEIGHT,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                    Integer.class).setConfigArrayVerifier(configArray -> {
                assertWithMessage(
                        "VEHICLE_CURB_WEIGHT configArray must contain the gross weight in "
                                + "kilograms").that(configArray).hasSize(1);
                assertWithMessage(
                        "VEHICLE_CURB_WEIGHT configArray[0] must contain the gross weight in "
                                + "kilograms and be greater than zero").that(
                        configArray.get(0)).isGreaterThan(0);
            }).setCarPropertyValueVerifier((carPropertyConfig, carPropertyValue) -> {
                Integer curbWeightKg = (Integer) carPropertyValue.getValue();
                Integer grossWeightKg = carPropertyConfig.getConfigArray().get(0);

                assertWithMessage("VEHICLE_CURB_WEIGHT must be greater than zero").that(
                        curbWeightKg).isGreaterThan(0);
                assertWithMessage("VEHICLE_CURB_WEIGHT must be less than the gross weight").that(
                        curbWeightKg).isLessThan(grossWeightKg);
            }).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testHeadlightsSwitchIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HEADLIGHTS_SWITCH,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testTrailerPresentIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_PRIVILEGED_CAR_INFO, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.TRAILER_PRESENT,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).setPossibleCarPropertyValues(
                    ImmutableSet.of(/*TrailerState.UNKNOWN=*/
                            0, /*TrailerState.NOT_PRESENT*/
                            1, /*TrailerState.PRESENT=*/2, /*TrailerState.ERROR=*/
                            3)).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testHighBeamLightsSwitchIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HIGH_BEAM_LIGHTS_SWITCH,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testFogLightsSwitchIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.FOG_LIGHTS_SWITCH,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                    .setCarPropertyValueVerifier((carPropertyConfig, carPropertyValue) -> {
                        assertWithMessage("FRONT_FOG_LIGHTS_SWITCH must not be implemented"
                                + "when FOG_LIGHTS_SWITCH is implemented")
                                .that(mCarPropertyManager.getCarPropertyConfig(
                                        VehiclePropertyIds.FRONT_FOG_LIGHTS_SWITCH)).isNull();

                        assertWithMessage("REAR_FOG_LIGHTS_SWITCH must not be implemented"
                                + "when FOG_LIGHTS_SWITCH is implemented")
                                .that(mCarPropertyManager.getCarPropertyConfig(
                                        VehiclePropertyIds.REAR_FOG_LIGHTS_SWITCH)).isNull();
                    }).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testHazardLightsSwitchIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HAZARD_LIGHTS_SWITCH,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testFrontFogLightsSwitchIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.FRONT_FOG_LIGHTS_SWITCH,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                    .setCarPropertyValueVerifier((carPropertyConfig, carPropertyValue) -> {
                        assertWithMessage("FOG_LIGHTS_SWITCH must not be implemented"
                                + "when FRONT_FOG_LIGHTS_SWITCH is implemented")
                                .that(mCarPropertyManager.getCarPropertyConfig(
                                        VehiclePropertyIds.FOG_LIGHTS_SWITCH)).isNull();
                    }).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testRearFogLightsSwitchIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.REAR_FOG_LIGHTS_SWITCH,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                    .setCarPropertyValueVerifier((carPropertyConfig, carPropertyValue) -> {
                        assertWithMessage("FOG_LIGHTS_SWITCH must not be implemented"
                                + "when REAR_FOG_LIGHTS_SWITCH is implemented")
                                .that(mCarPropertyManager.getCarPropertyConfig(
                                        VehiclePropertyIds.FOG_LIGHTS_SWITCH)).isNull();
                    }).build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testCabinLightsSwitchIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.CABIN_LIGHTS_SWITCH,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testReadingLightsSwitchIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.READING_LIGHTS_SWITCH,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig"})
    public void testSeatMemorySelectIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_MEMORY_SELECT,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().requireMinValuesToBeZero()
                    .setCarPropertyConfigVerifier(carPropertyConfig -> {
                        int[] areaIds = carPropertyConfig.getAreaIds();
                        CarPropertyConfig<?> seatMemorySetCarPropertyConfig =
                                mCarPropertyManager.getCarPropertyConfig(
                                        VehiclePropertyIds.SEAT_MEMORY_SET);

                        assertWithMessage("SEAT_MEMORY_SET must be implemented if "
                                + "SEAT_MEMORY_SELECT is implemented").that(
                                        seatMemorySetCarPropertyConfig).isNotNull();

                        assertWithMessage("SEAT_MEMORY_SELECT area IDs must match the area IDs of "
                                + "SEAT_MEMORY_SET").that(Arrays.stream(areaIds).boxed().collect(
                                        Collectors.toList()))
                                .containsExactlyElementsIn(Arrays.stream(
                                        seatMemorySetCarPropertyConfig.getAreaIds()).boxed()
                                        .collect(Collectors.toList()));

                        for (int areaId : areaIds) {
                            Integer seatMemorySetAreaIdMaxValue =
                                    (Integer) seatMemorySetCarPropertyConfig.getMaxValue(areaId);
                            assertWithMessage("SEAT_MEMORY_SET - area ID: " + areaId
                                    + " must have max value defined")
                                    .that(seatMemorySetAreaIdMaxValue).isNotNull();
                            assertWithMessage("SEAT_MEMORY_SELECT - area ID: " + areaId
                                    + "'s max value must be equal to SEAT_MEMORY_SET's max value"
                                    + " under the same area ID")
                                    .that(seatMemorySetAreaIdMaxValue)
                                    .isEqualTo(carPropertyConfig.getMaxValue(areaId));
                        }
                    }).build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig"})
    public void testSeatMemorySetIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_MEMORY_SET,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().requireMinValuesToBeZero()
                    .setCarPropertyConfigVerifier(carPropertyConfig -> {
                        int[] areaIds = carPropertyConfig.getAreaIds();
                        CarPropertyConfig<?> seatMemorySelectCarPropertyConfig =
                                mCarPropertyManager.getCarPropertyConfig(
                                        VehiclePropertyIds.SEAT_MEMORY_SELECT);

                        assertWithMessage("SEAT_MEMORY_SELECT must be implemented if "
                                + "SEAT_MEMORY_SET is implemented").that(
                                seatMemorySelectCarPropertyConfig).isNotNull();

                        assertWithMessage("SEAT_MEMORY_SET area IDs must match the area IDs of "
                                + "SEAT_MEMORY_SELECT").that(Arrays.stream(areaIds).boxed().collect(
                                        Collectors.toList()))
                                .containsExactlyElementsIn(Arrays.stream(
                                        seatMemorySelectCarPropertyConfig.getAreaIds()).boxed()
                                        .collect(Collectors.toList()));

                        for (int areaId : areaIds) {
                            Integer seatMemorySelectAreaIdMaxValue =
                                    (Integer) seatMemorySelectCarPropertyConfig.getMaxValue(areaId);
                            assertWithMessage("SEAT_MEMORY_SELECT - area ID: " + areaId
                                    + " must have max value defined")
                                    .that(seatMemorySelectAreaIdMaxValue).isNotNull();
                            assertWithMessage("SEAT_MEMORY_SET - area ID: " + areaId
                                    + "'s max value must be equal to SEAT_MEMORY_SELECT's max value"
                                    + " under the same area ID")
                                    .that(seatMemorySelectAreaIdMaxValue)
                                    .isEqualTo(carPropertyConfig.getMaxValue(areaId));
                        }
                    }).build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatBeltBuckledIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_BELT_BUCKLED,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testSeatBeltHeightPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_BELT_HEIGHT_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    public void testSeatBeltHeightMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_BELT_HEIGHT_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testSeatForeAftPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_FORE_AFT_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    public void testSeatForeAftMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_FORE_AFT_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testSeatBackrestAngle1PosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    public void testSeatBackrestAngle1MoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testSeatBackrestAngle2PosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    public void testSeatBackrestAngle2MoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatHeightPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_HEIGHT_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatHeightMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_HEIGHT_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatDepthPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_DEPTH_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatDepthMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_DEPTH_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatTiltPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_TILT_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatTiltMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_TILT_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatLumbarForeAftPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatLumbarForeAftMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatLumbarSideSupportPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatLumbarSideSupportMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatHeadrestHeightMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_HEADREST_HEIGHT_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatHeadrestAnglePosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_HEADREST_ANGLE_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatHeadrestAngleMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_HEADREST_ANGLE_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatHeadrestForeAftPosIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_POS,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatHeadrestForeAftMoveIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_MOVE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class).requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testSeatOccupancyIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_SEATS, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.SEAT_OCCUPANCY,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Integer.class)
                    .setPossibleCarPropertyValues(VEHICLE_SEAT_OCCUPANCY_STATES).build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacDefrosterIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_DEFROSTER,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testHvacElectricDefrosterOnIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testHvacSideMirrorHeatIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).requireMinMaxValues().requireMinValuesToBeZero().build().verify(
                    mCarPropertyManager);
        });
    }

    @Test
    public void testHvacSteeringWheelHeatIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).requireMinMaxValues().requireZeroToBeContainedInMinMaxRanges()
                    .build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testHvacTemperatureDisplayUnitsIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).setPossibleConfigArrayValues(
                    HVAC_TEMPERATURE_DISPLAY_UNITS).requirePropertyValueTobeInConfigArray()
                    .verifySetterWithConfigArrayValues().build().verify(mCarPropertyManager);
        });
    }

    @Test
    public void testHvacTemperatureValueSuggestionIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Float[].class).build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacPowerOnIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_POWER_ON,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).setConfigArrayVerifier(configArray -> {
                CarPropertyConfig<?> hvacPowerOnCarPropertyConfig =
                        mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.HVAC_POWER_ON);
                for (int powerDependentProperty : configArray) {
                    CarPropertyConfig<?> powerDependentCarPropertyConfig =
                            mCarPropertyManager.getCarPropertyConfig(powerDependentProperty);
                    if (powerDependentCarPropertyConfig == null) {
                        continue;
                    }
                    assertWithMessage(
                            "HVAC_POWER_ON configArray must only contain VehicleAreaSeat type "
                                    + "properties: " + VehiclePropertyIds.toString(
                                    powerDependentProperty)).that(
                            powerDependentCarPropertyConfig.getAreaType()).isEqualTo(
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT);
                    for (int powerDependentAreaId : powerDependentCarPropertyConfig.getAreaIds()) {
                        boolean powerDependentAreaIdIsContained = false;
                        for (int hvacPowerOnAreaId : hvacPowerOnCarPropertyConfig.getAreaIds()) {
                            if ((powerDependentAreaId & hvacPowerOnAreaId)
                                    == powerDependentAreaId) {
                                powerDependentAreaIdIsContained = true;
                                break;
                            }
                        }
                        assertWithMessage(
                                "HVAC_POWER_ON's area IDs must contain the area IDs"
                                        + " of power dependent property: "
                                        + VehiclePropertyIds.toString(
                                        powerDependentProperty)).that(
                                        powerDependentAreaIdIsContained).isTrue();
                    }
                }
            }).build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacFanSpeedIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_FAN_SPEED,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).requireMinMaxValues().setPossiblyDependentOnHvacPowerOn().build()
                    .verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacFanDirectionAvailableIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                    Integer[].class).setPossiblyDependentOnHvacPowerOn().setAreaIdsVerifier(
                    areaIds -> {
                        CarPropertyConfig<?> hvacFanDirectionCarPropertyConfig =
                                mCarPropertyManager.getCarPropertyConfig(
                                        VehiclePropertyIds.HVAC_FAN_DIRECTION);
                        assertWithMessage("HVAC_FAN_DIRECTION must be implemented if "
                                + "HVAC_FAN_DIRECTION_AVAILABLE is implemented").that(
                                hvacFanDirectionCarPropertyConfig).isNotNull();

                        assertWithMessage(
                                "HVAC_FAN_DIRECTION_AVAILABLE area IDs must match the area IDs of "
                                        + "HVAC_FAN_DIRECTION").that(Arrays.stream(
                                areaIds).boxed().collect(
                                Collectors.toList())).containsExactlyElementsIn(Arrays.stream(
                                hvacFanDirectionCarPropertyConfig.getAreaIds()).boxed().collect(
                                Collectors.toList()));

                    }).setCarPropertyValueVerifier((carPropertyConfig, carPropertyValue) -> {
                Integer[] fanDirectionValues = (Integer[]) carPropertyValue.getValue();
                assertWithMessage(
                        "HVAC_FAN_DIRECTION_AVAILABLE area ID: " + carPropertyValue.getAreaId()
                                + " must have at least 1 direction defined").that(
                        fanDirectionValues.length).isAtLeast(1);
                assertWithMessage(
                        "HVAC_FAN_DIRECTION_AVAILABLE area ID: " + carPropertyValue.getAreaId()
                                + " values all must all be unique: " + Arrays.toString(
                                fanDirectionValues)).that(fanDirectionValues.length).isEqualTo(
                        ImmutableSet.copyOf(fanDirectionValues).size());
                for (Integer fanDirection : fanDirectionValues) {
                    assertWithMessage("HVAC_FAN_DIRECTION_AVAILABLE's area ID: "
                            + carPropertyValue.getAreaId()
                            + " must be a valid combination of fan directions").that(
                            fanDirection).isIn(ALL_POSSIBLE_HVAC_FAN_DIRECTIONS);
                }
            }).build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacFanDirectionIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_FAN_DIRECTION,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).setPossiblyDependentOnHvacPowerOn().setAreaIdsVerifier(
                    areaIds -> {
                        CarPropertyConfig<?> hvacFanDirectionAvailableCarPropertyConfig =
                                mCarPropertyManager.getCarPropertyConfig(
                                        VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE);
                        assertWithMessage("HVAC_FAN_DIRECTION_AVAILABLE must be implemented if "
                                + "HVAC_FAN_DIRECTION is implemented").that(
                                hvacFanDirectionAvailableCarPropertyConfig).isNotNull();

                        assertWithMessage("HVAC_FAN_DIRECTION area IDs must match the area IDs of "
                                + "HVAC_FAN_DIRECTION_AVAILABLE").that(Arrays.stream(
                                areaIds).boxed().collect(
                                Collectors.toList())).containsExactlyElementsIn(Arrays.stream(
                                hvacFanDirectionAvailableCarPropertyConfig.getAreaIds()).boxed()
                                .collect(Collectors.toList()));

                    }).setCarPropertyValueVerifier((carPropertyConfig, carPropertyValue) -> {
                CarPropertyValue<Integer[]> hvacFanDirectionAvailableCarPropertyValue =
                        mCarPropertyManager.getProperty(
                                VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                                carPropertyValue.getAreaId());
                assertWithMessage("HVAC_FAN_DIRECTION_AVAILABLE value must be available").that(
                        hvacFanDirectionAvailableCarPropertyValue).isNotNull();

                assertWithMessage("HVAC_FAN_DIRECTION area ID " + carPropertyValue.getAreaId()
                        + " value must be in list for HVAC_FAN_DIRECTION_AVAILABLE").that(
                        carPropertyValue.getValue()).isIn(
                        Arrays.asList(hvacFanDirectionAvailableCarPropertyValue.getValue()));
            }).build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacTemperatureCurrentIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Float.class).setPossiblyDependentOnHvacPowerOn().build().verify(
                    mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacTemperatureSetIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.Builder<Float> hvacTempSetVerifierBuilder =
                    VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                            VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                            CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                            Float.class).setPossiblyDependentOnHvacPowerOn().setConfigArrayVerifier(
                            configArray -> {
                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET config array must be size 6").that(
                                        configArray.size()).isEqualTo(6);

                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET lower bound must be less than the "
                                                + "upper bound for "
                                                + "the supported temperatures in Celsius").that(
                                        configArray.get(0)).isLessThan(configArray.get(1));
                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET increment in Celsius must be "
                                                + "greater than 0").that(
                                        configArray.get(2)).isGreaterThan(0);
                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET increment in Celsius must be less "
                                                + "than the "
                                                + "difference between the upper and lower bound "
                                                + "supported "
                                                + "temperatures").that(
                                        configArray.get(2)).isLessThan(
                                        configArray.get(1) - configArray.get(0));
                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET increment in Celsius must evenly "
                                        + "space the gap "
                                                + "between upper and lower bound").that(
                                        (configArray.get(1) - configArray.get(0)) % configArray.get(
                                                2)).isEqualTo(0);

                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET lower bound must be less than the "
                                        + "upper bound for "
                                                + "the supported temperatures in Fahrenheit").that(
                                        configArray.get(3)).isLessThan(configArray.get(4));
                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET increment in Fahrenheit must be "
                                        + "greater than 0").that(
                                        configArray.get(5)).isGreaterThan(0);
                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET increment in Fahrenheit must be "
                                        + "less than the "
                                                + "difference between the upper and lower bound "
                                                + "supported "
                                                + "temperatures").that(
                                        configArray.get(5)).isLessThan(
                                        configArray.get(4) - configArray.get(3));
                                assertWithMessage(
                                        "HVAC_TEMPERATURE_SET increment in Fahrenheit must evenly"
                                                + " space the gap "
                                                + "between upper and lower bound").that(
                                        (configArray.get(4) - configArray.get(3)) % configArray.get(
                                                5)).isEqualTo(0);

                            });

            CarPropertyConfig<?> hvacTempSetConfig = mCarPropertyManager.getCarPropertyConfig(
                    VehiclePropertyIds.HVAC_TEMPERATURE_SET);
            if (hvacTempSetConfig != null) {
                ImmutableSet.Builder<Float> possibleHvacTempSetValuesBuilder =
                        ImmutableSet.builder();
                for (int possibleHvacTempSetValue = hvacTempSetConfig.getConfigArray().get(0);
                        possibleHvacTempSetValue <= hvacTempSetConfig.getConfigArray().get(1);
                        possibleHvacTempSetValue += hvacTempSetConfig.getConfigArray().get(2)) {
                    possibleHvacTempSetValuesBuilder.add((float) possibleHvacTempSetValue / 10.0f);
                }
                hvacTempSetVerifierBuilder.setPossibleCarPropertyValues(
                        possibleHvacTempSetValuesBuilder.build());
            }

            hvacTempSetVerifierBuilder.build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacAcOnIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_AC_ON,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).setPossiblyDependentOnHvacPowerOn().build().verify(
                    mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacMaxAcOnIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_MAX_AC_ON,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).setPossiblyDependentOnHvacPowerOn().build().verify(
                    mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacMaxDefrostOnIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_MAX_DEFROST_ON,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).setPossiblyDependentOnHvacPowerOn().build().verify(
                    mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacRecircOnIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_RECIRC_ON,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).setPossiblyDependentOnHvacPowerOn().build().verify(
                    mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacAutoOnIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_AUTO_ON,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).setPossiblyDependentOnHvacPowerOn().build().verify(
                    mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacSeatTemperatureIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_SEAT_TEMPERATURE,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).setPossiblyDependentOnHvacPowerOn().requireMinMaxValues()
                    .requireZeroToBeContainedInMinMaxRanges().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacActualFanSpeedRpmIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).setPossiblyDependentOnHvacPowerOn().build().verify(
                    mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacAutoRecircOnIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_AUTO_RECIRC_ON,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).setPossiblyDependentOnHvacPowerOn().build().verify(
                    mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacSeatVentilationIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_SEAT_VENTILATION,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Integer.class).setPossiblyDependentOnHvacPowerOn().requireMinMaxValues()
                    .requireMinValuesToBeZero().build().verify(mCarPropertyManager);
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
            "android.car.hardware.property.CarPropertyManager#getProperty",
            "android.car.hardware.property.CarPropertyManager#setProperty",
            "android.car.hardware.property.CarPropertyManager#registerCallback",
            "android.car.hardware.property.CarPropertyManager#unregisterCallback"})
    public void testHvacDualOnIfSupported() {
        adoptSystemLevelPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE, () -> {
            VehiclePropertyVerifier.newBuilder(VehiclePropertyIds.HVAC_DUAL_ON,
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                    VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                    CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                    Boolean.class).setPossiblyDependentOnHvacPowerOn().setAreaIdsVerifier(
                    areaIds -> {
                        CarPropertyConfig<?> hvacTempSetCarPropertyConfig =
                                mCarPropertyManager.getCarPropertyConfig(
                                        VehiclePropertyIds.HVAC_TEMPERATURE_SET);
                        if (hvacTempSetCarPropertyConfig == null) {
                            return;
                        }
                        ImmutableSet<Integer> hvacTempSetAreaIds = ImmutableSet.copyOf(
                                Arrays.stream(
                                        hvacTempSetCarPropertyConfig.getAreaIds()).boxed().collect(
                                        Collectors.toList()));
                        ImmutableSet.Builder<Integer> allPossibleHvacDualOnAreaIdsBuilder =
                                ImmutableSet.builder();
                        for (int i = 2; i <= hvacTempSetAreaIds.size(); i++) {
                            allPossibleHvacDualOnAreaIdsBuilder.addAll(Sets.combinations(
                                    hvacTempSetAreaIds, i).stream().map(areaIdCombo -> {
                                Integer possibleHvacDualOnAreaId = 0;
                                for (Integer areaId : areaIdCombo) {
                                    possibleHvacDualOnAreaId |= areaId;
                                }
                                return possibleHvacDualOnAreaId;
                            }).collect(Collectors.toList()));
                        }
                        ImmutableSet<Integer> allPossibleHvacDualOnAreaIds =
                                allPossibleHvacDualOnAreaIdsBuilder.build();
                        for (int areaId : areaIds) {
                            assertWithMessage("HVAC_DUAL_ON area ID: " + areaId
                                    + " must be a combination of HVAC_TEMPERATURE_SET area IDs: "
                                    + Arrays.toString(
                                    hvacTempSetCarPropertyConfig.getAreaIds())).that(areaId).isIn(
                                    allPossibleHvacDualOnAreaIds);

                        }
                    }).build().verify(mCarPropertyManager);
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetProperty() {
        List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList(mPropertyIds);
        for (CarPropertyConfig cfg : configs) {
            if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ) {
                int[] areaIds = getAreaIdsHelper(cfg);
                int propId = cfg.getPropertyId();
                // no guarantee if we can get values, just call and check if it throws exception.
                if (cfg.getPropertyType() == Boolean.class) {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getBooleanProperty(propId, areaId);
                    }
                } else if (cfg.getPropertyType() == Integer.class) {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getIntProperty(propId, areaId);
                    }
                } else if (cfg.getPropertyType() == Float.class) {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getFloatProperty(propId, areaId);
                    }
                } else if (cfg.getPropertyType() == Integer[].class) {
                    for (int areId : areaIds) {
                        mCarPropertyManager.getIntArrayProperty(propId, areId);
                    }
                } else {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getProperty(
                                cfg.getPropertyType(), propId, areaId);
                    }
                }
            }
        }
    }

    @Test
    public void testGetIntArrayProperty() {
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : allConfigs) {
            if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE
                    || cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE
                    || cfg.getPropertyType() != Integer[].class) {
                // skip the test if the property is not readable or not an int array type property.
                continue;
            }
            switch (cfg.getPropertyId()) {
                case VehiclePropertyIds.INFO_FUEL_TYPE:
                    int[] fuelTypes = mCarPropertyManager.getIntArrayProperty(cfg.getPropertyId(),
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
                    verifyEnumsRange(EXPECTED_FUEL_TYPES, fuelTypes);
                    break;
                case VehiclePropertyIds.INFO_MULTI_EV_PORT_LOCATIONS:
                    int[] evPortLocations = mCarPropertyManager.getIntArrayProperty(
                            cfg.getPropertyId(), VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
                    verifyEnumsRange(EXPECTED_PORT_LOCATIONS, evPortLocations);
                    break;
                default:
                    int[] areaIds = getAreaIdsHelper(cfg);
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getIntArrayProperty(cfg.getPropertyId(), areaId);
                    }
            }
        }
    }

    private void verifyEnumsRange(List<Integer> expectedResults, int[] results) {
        assertThat(results).isNotNull();
        // If the property is not implemented in cars, getIntArrayProperty returns an empty array.
        if (results.length == 0) {
            return;
        }
        for (int result : results) {
            assertThat(result).isIn(expectedResults);
        }
    }

    @Test
    public void testIsPropertyAvailable() {
        List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList(mPropertyIds);

        for (CarPropertyConfig cfg : configs) {
            int[] areaIds = getAreaIdsHelper(cfg);
            for (int areaId : areaIds) {
                assertThat(mCarPropertyManager.isPropertyAvailable(cfg.getPropertyId(), areaId))
                        .isTrue();
            }
        }
    }

    @Test
    public void testSetProperty() {
        List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : configs) {
            if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE
                    && cfg.getPropertyType() == Boolean.class) {
                // In R, there is no property which is writable for third-party apps.
                for (int areaId : getAreaIdsHelper(cfg)) {
                    assertThrows(SecurityException.class,
                            () -> mCarPropertyManager.setBooleanProperty(
                                    cfg.getPropertyId(), areaId, true));
                }
            }
        }
    }

    @Test
    public void testRegisterCallback() throws Exception {
        //Test on registering a invalid property
        int invalidPropertyId = -1;
        boolean isRegistered = mCarPropertyManager.registerCallback(
                new CarPropertyEventCounter(), invalidPropertyId, 0);
        assertThat(isRegistered).isFalse();

        // Test for continuous properties
        int vehicleSpeed = VehiclePropertyIds.PERF_VEHICLE_SPEED;
        CarPropertyConfig<?> carPropertyConfig = mCarPropertyManager.getCarPropertyConfig(
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        float secondsToMillis = 1_000;
        long bufferMillis = 1_000; // 1 second
        // timeoutMillis is set to the maximum expected time needed to receive the required
        // number of PERF_VEHICLE_SPEED events for test. If the test does not receive the
        // required number of events before the timeout expires, it fails.
        long timeoutMillis =
                ((long) ((1.0f / carPropertyConfig.getMinSampleRate()) * secondsToMillis
                        * UI_RATE_EVENT_COUNTER)) + bufferMillis;
        CarPropertyEventCounter speedListenerUI = new CarPropertyEventCounter(timeoutMillis);
        CarPropertyEventCounter speedListenerFast = new CarPropertyEventCounter();

        assertThat(speedListenerUI.receivedEvent(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerUI.receivedError(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerUI.receivedErrorWithErrorCode(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerFast.receivedEvent(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerFast.receivedError(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerFast.receivedErrorWithErrorCode(vehicleSpeed)).isEqualTo(NO_EVENTS);

        speedListenerUI.resetCountDownLatch(UI_RATE_EVENT_COUNTER);
        mCarPropertyManager.registerCallback(speedListenerUI, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_UI);
        mCarPropertyManager.registerCallback(speedListenerFast, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_FASTEST);
        speedListenerUI.assertOnChangeEventCalled();
        mCarPropertyManager.unregisterCallback(speedListenerUI);
        mCarPropertyManager.unregisterCallback(speedListenerFast);

        assertThat(speedListenerUI.receivedEvent(vehicleSpeed)).isGreaterThan(NO_EVENTS);
        assertThat(speedListenerFast.receivedEvent(vehicleSpeed)).isAtLeast(
                speedListenerUI.receivedEvent(vehicleSpeed));
        // The test did not change property values, it should not get error with error codes.
        assertThat(speedListenerUI.receivedErrorWithErrorCode(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerFast.receivedErrorWithErrorCode(vehicleSpeed)).isEqualTo(NO_EVENTS);

        // Test for on_change properties
        int nightMode = VehiclePropertyIds.NIGHT_MODE;
        CarPropertyEventCounter nightModeListener = new CarPropertyEventCounter();
        nightModeListener.resetCountDownLatch(ONCHANGE_RATE_EVENT_COUNTER);
        mCarPropertyManager.registerCallback(nightModeListener, nightMode, 0);
        nightModeListener.assertOnChangeEventCalled();
        assertThat(nightModeListener.receivedEvent(nightMode)).isEqualTo(1);
        mCarPropertyManager.unregisterCallback(nightModeListener);
    }

    @Test
    public void testUnregisterCallback() throws Exception {

        int vehicleSpeed = VehiclePropertyIds.PERF_VEHICLE_SPEED;
        CarPropertyEventCounter speedListenerNormal = new CarPropertyEventCounter();
        CarPropertyEventCounter speedListenerUI = new CarPropertyEventCounter();

        mCarPropertyManager.registerCallback(speedListenerNormal, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_NORMAL);

        // test on unregistering a callback that was never registered
        try {
            mCarPropertyManager.unregisterCallback(speedListenerUI);
        } catch (Exception e) {
            Assert.fail();
        }

        mCarPropertyManager.registerCallback(speedListenerUI, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_UI);
        speedListenerUI.resetCountDownLatch(UI_RATE_EVENT_COUNTER);
        speedListenerUI.assertOnChangeEventCalled();
        mCarPropertyManager.unregisterCallback(speedListenerNormal, vehicleSpeed);

        int currentEventNormal = speedListenerNormal.receivedEvent(vehicleSpeed);
        int currentEventUI = speedListenerUI.receivedEvent(vehicleSpeed);
        speedListenerNormal.assertOnChangeEventNotCalled();

        assertThat(speedListenerNormal.receivedEvent(vehicleSpeed)).isEqualTo(currentEventNormal);
        assertThat(speedListenerUI.receivedEvent(vehicleSpeed)).isNotEqualTo(currentEventUI);

        mCarPropertyManager.unregisterCallback(speedListenerUI);
        speedListenerUI.assertOnChangeEventNotCalled();

        currentEventUI = speedListenerUI.receivedEvent(vehicleSpeed);
        assertThat(speedListenerUI.receivedEvent(vehicleSpeed)).isEqualTo(currentEventUI);
    }

    @Test
    public void testUnregisterWithPropertyId() throws Exception {
        // Ignores the test if wheel_tick property does not exist in the car.
        assumeTrue("WheelTick is not available, skip unregisterCallback test",
                mCarPropertyManager.isPropertyAvailable(
                        VehiclePropertyIds.WHEEL_TICK, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        CarPropertyConfig wheelTickConfig = mCarPropertyManager.getCarPropertyConfig(
                VehiclePropertyIds.WHEEL_TICK);
        CarPropertyConfig speedConfig = mCarPropertyManager.getCarPropertyConfig(
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        float maxSampleRateHz =
                Math.max(wheelTickConfig.getMaxSampleRate(), speedConfig.getMaxSampleRate());
        int eventCounter = getCounterBySampleRate(maxSampleRateHz);

        // Ignores the test if sampleRates for properties are too low.
        assumeTrue("The SampleRates for properties are too low, "
                + "skip testUnregisterWithPropertyId test", eventCounter != 0);
        CarPropertyEventCounter speedAndWheelTicksListener = new CarPropertyEventCounter();

        // CarService will register them to the maxSampleRate in CarPropertyConfig
        mCarPropertyManager.registerCallback(speedAndWheelTicksListener,
                VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST);
        mCarPropertyManager.registerCallback(speedAndWheelTicksListener,
                VehiclePropertyIds.WHEEL_TICK, CarPropertyManager.SENSOR_RATE_FASTEST);
        speedAndWheelTicksListener.resetCountDownLatch(eventCounter);
        speedAndWheelTicksListener.assertOnChangeEventCalled();

        // Tests unregister the individual property
        mCarPropertyManager.unregisterCallback(speedAndWheelTicksListener,
                VehiclePropertyIds.PERF_VEHICLE_SPEED);

        // Updates counter after unregistering the PERF_VEHICLE_SPEED
        int wheelTickEventCounter = getCounterBySampleRate(wheelTickConfig.getMaxSampleRate());
        speedAndWheelTicksListener.resetCountDownLatch(wheelTickEventCounter);
        speedAndWheelTicksListener.assertOnChangeEventCalled();
        int speedEventCountAfterFirstCountDown = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        int wheelTickEventCountAfterFirstCountDown = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.WHEEL_TICK);

        speedAndWheelTicksListener.resetCountDownLatch(wheelTickEventCounter);
        speedAndWheelTicksListener.assertOnChangeEventCalled();
        int speedEventCountAfterSecondCountDown = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        int wheelTickEventCountAfterSecondCountDown = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.WHEEL_TICK);

        assertThat(speedEventCountAfterFirstCountDown).isEqualTo(
                speedEventCountAfterSecondCountDown);
        assertThat(wheelTickEventCountAfterSecondCountDown)
                .isGreaterThan(wheelTickEventCountAfterFirstCountDown);
    }

    private int getCounterBySampleRate(float maxSampleRateHz) {
        if (Float.compare(maxSampleRateHz, (float) FAST_OR_FASTEST_EVENT_COUNTER) > 0) {
            return FAST_OR_FASTEST_EVENT_COUNTER;
        } else if (Float.compare(maxSampleRateHz, (float) UI_RATE_EVENT_COUNTER) > 0) {
            return UI_RATE_EVENT_COUNTER;
        } else if (Float.compare(maxSampleRateHz, (float) ONCHANGE_RATE_EVENT_COUNTER) > 0) {
            return ONCHANGE_RATE_EVENT_COUNTER;
        } else {
            return 0;
        }
    }

    // Returns {0} if the property is global property, otherwise query areaId for CarPropertyConfig
    private int[] getAreaIdsHelper(CarPropertyConfig config) {
        if (config.isGlobalProperty()) {
            return new int[]{0};
        } else {
            return config.getAreaIds();
        }
    }

    private static class CarPropertyEventCounter implements CarPropertyEventCallback {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final SparseArray<Integer> mEventCounter = new SparseArray<>();
        @GuardedBy("mLock")
        private final SparseArray<Integer> mErrorCounter = new SparseArray<>();
        @GuardedBy("mLock")
        private final SparseArray<Integer> mErrorWithErrorCodeCounter = new SparseArray<>();
        private int mCounter = FAST_OR_FASTEST_EVENT_COUNTER;
        private CountDownLatch mCountDownLatch = new CountDownLatch(mCounter);
        private final long mTimeoutMillis;

        CarPropertyEventCounter(long timeoutMillis) {
            mTimeoutMillis = timeoutMillis;
        }

        CarPropertyEventCounter() {
            this(WAIT_CALLBACK);
        }

        public int receivedEvent(int propId) {
            int val;
            synchronized (mLock) {
                val = mEventCounter.get(propId, 0);
            }
            return val;
        }

        public int receivedError(int propId) {
            int val;
            synchronized (mLock) {
                val = mErrorCounter.get(propId, 0);
            }
            return val;
        }

        public int receivedErrorWithErrorCode(int propId) {
            int val;
            synchronized (mLock) {
                val = mErrorWithErrorCodeCounter.get(propId, 0);
            }
            return val;
        }

        @Override
        public void onChangeEvent(CarPropertyValue value) {
            synchronized (mLock) {
                int val = mEventCounter.get(value.getPropertyId(), 0) + 1;
                mEventCounter.put(value.getPropertyId(), val);
            }
            mCountDownLatch.countDown();
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
            synchronized (mLock) {
                int val = mErrorCounter.get(propId, 0) + 1;
                mErrorCounter.put(propId, val);
            }
        }

        @Override
        public void onErrorEvent(int propId, int areaId, int errorCode) {
            synchronized (mLock) {
                int val = mErrorWithErrorCodeCounter.get(propId, 0) + 1;
                mErrorWithErrorCodeCounter.put(propId, val);
            }
        }

        public void resetCountDownLatch(int counter) {
            mCountDownLatch = new CountDownLatch(counter);
            mCounter = counter;
        }

        public void assertOnChangeEventCalled() throws InterruptedException {
            if (!mCountDownLatch.await(mTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "Callback is not called " + mCounter + "times in " + mTimeoutMillis
                                + " ms. It was only called " + (mCounter
                                - mCountDownLatch.getCount()) + " times.");
            }
        }

        public void assertOnChangeEventNotCalled() throws InterruptedException {
            // Once get an event, fail the test.
            mCountDownLatch = new CountDownLatch(1);
            if (mCountDownLatch.await(mTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Callback is called in " + mTimeoutMillis + " ms.");
            }
        }

    }
}
