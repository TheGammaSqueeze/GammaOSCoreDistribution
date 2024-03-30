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

import static com.google.common.truth.Truth.assertWithMessage;

import android.hardware.automotive.vehicle.VehicleApPowerStateReq;
import android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam;

import org.junit.Test;

public final class PowerHalServiceTest {

    @Test
    public void testCanPostponeShutdown() throws Exception {
        PowerHalService.PowerState powerState = createShutdownPrepare(
                VehicleApPowerStateShutdownParam.CAN_HIBERNATE);
        assertWithMessage("canPostponeShutdown with CAN_HIBERNATE flag")
                .that(powerState.canPostponeShutdown()).isTrue();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY);
        assertWithMessage("canPostponeShutdown with HIBERNATE_IMMEDIATELY flag")
                .that(powerState.canPostponeShutdown()).isFalse();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.CAN_SLEEP);
        assertWithMessage("canPostponeShutdown with CAN_SLEEP flag")
                .that(powerState.canPostponeShutdown()).isTrue();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY);
        assertWithMessage("canPostponeShutdown with SLEEP_IMMEDIATELY flag")
                .that(powerState.canPostponeShutdown()).isFalse();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
        assertWithMessage("canPostponeShutdown with SHUTDOWN_IMMEDIATELY flag")
                .that(powerState.canPostponeShutdown()).isFalse();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
        assertWithMessage("canPostponeShutdown with SHUTDOWN_ONLY flag")
                .that(powerState.canPostponeShutdown()).isTrue();
    }

    private PowerHalService.PowerState createShutdownPrepare(int flag) {
        return new PowerHalService.PowerState(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE, flag);
    }

    @Test
    public void testCanSuspend() throws Exception {
        PowerHalService.PowerState powerState = createShutdownPrepare(
                VehicleApPowerStateShutdownParam.CAN_HIBERNATE);
        assertWithMessage("canSuspend with CAN_HIBERNATE flag")
                .that(powerState.canSuspend()).isTrue();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.CAN_SLEEP);
        assertWithMessage("canSuspend with CAN_SLEEP flag")
                .that(powerState.canSuspend()).isTrue();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY);
        assertWithMessage("canSuspend with SLEEP_IMMEDIATELY flag")
                .that(powerState.canSuspend()).isTrue();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY);
        assertWithMessage("canSuspend with HIBERNATE_IMMEDIATELY flag")
                .that(powerState.canSuspend()).isTrue();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.CAN_HIBERNATE);
        assertWithMessage("canSuspend with CAN_HIBERNATE flag")
                .that(powerState.canSuspend()).isTrue();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
        assertWithMessage("canSuspend with SHUTDOWN_IMMEDIATELY flag")
                .that(powerState.canSuspend()).isFalse();

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
        assertWithMessage("canSuspend with SHUTDOWN_ONLY flag")
                .that(powerState.canSuspend()).isFalse();
    }

    @Test
    public void testGetSuspendType() throws Exception {
        PowerHalService.PowerState powerState = createShutdownPrepare(
                VehicleApPowerStateShutdownParam.CAN_HIBERNATE);
        assertWithMessage("getShutdownType with CAN_HIBERNATE flag")
                .that(powerState.getShutdownType())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_HIBERNATION);

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY);
        assertWithMessage("getShutdownType with HIBERNATE_IMMEDIATELY flag")
                .that(powerState.getShutdownType())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_HIBERNATION);

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.CAN_SLEEP);
        assertWithMessage("getShutdownType with CAN_SLEEP flag")
                .that(powerState.getShutdownType())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_DEEP_SLEEP);

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY);
        assertWithMessage("getShutdownType with SLEEP_IMMEDIATELY flag")
                .that(powerState.getShutdownType())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_DEEP_SLEEP);

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
        assertWithMessage("getShutdownType with SHUTDOWN_ONLY flag")
                .that(powerState.getShutdownType())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_POWER_OFF);

        powerState = createShutdownPrepare(VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
        assertWithMessage("getShutdownType with SHUTDOWN_IMMEDIATELY flag")
                .that(powerState.getShutdownType())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_POWER_OFF);

        powerState = createShutdownPrepare(0);
        assertWithMessage("getShutdownType with no flag")
                .that(powerState.getShutdownType())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_POWER_OFF);
    }
}
