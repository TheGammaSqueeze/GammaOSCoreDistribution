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
package com.android.car.audio;


import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC;

import android.media.AudioDeviceAttributes;
import android.util.SparseArray;

import com.android.internal.util.Preconditions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class CarAudioZonesValidator {
    private CarAudioZonesValidator() {
    }

    static void validate(SparseArray<CarAudioZone> carAudioZones) {
        validateAtLeastOneZoneDefined(carAudioZones);
        validateVolumeGroupsForEachZone(carAudioZones);
        validateEachAddressAppearsAtMostOnce(carAudioZones);
        validatePrimaryZoneHasInputDevice(carAudioZones);
    }

    private static void validatePrimaryZoneHasInputDevice(SparseArray<CarAudioZone> carAudioZones) {
        CarAudioZone primaryZone = carAudioZones.get(PRIMARY_AUDIO_ZONE);
        List<AudioDeviceAttributes> devices = primaryZone.getInputAudioDevices();
        Preconditions.checkCollectionNotEmpty(devices, "Primary Zone Input Devices");
        for (int index = 0; index < devices.size(); index++) {
            AudioDeviceAttributes device = devices.get(index);
            if (device.getType() == TYPE_BUILTIN_MIC) {
                return;
            }
        }
        throw new RuntimeException("Primary Zone must have at least one microphone input device");
    }

    private static void validateAtLeastOneZoneDefined(SparseArray<CarAudioZone> carAudioZones) {
        if (carAudioZones.size() == 0) {
            throw new RuntimeException("At least one zone should be defined");
        }
    }

    private static void validateVolumeGroupsForEachZone(SparseArray<CarAudioZone> carAudioZones) {
        for (int i = 0; i < carAudioZones.size(); i++) {
            CarAudioZone zone = carAudioZones.valueAt(i);
            if (!zone.validateVolumeGroups()) {
                throw new RuntimeException(
                        "Invalid volume groups configuration for zone " + zone.getId());
            }
        }
    }

    private static void validateEachAddressAppearsAtMostOnce(
            SparseArray<CarAudioZone> carAudioZones) {
        Set<String> addresses = new HashSet<>();
        for (int i = 0; i < carAudioZones.size(); i++) {
            CarAudioZone zone = carAudioZones.valueAt(i);
            for (CarVolumeGroup carVolumeGroup : zone.getVolumeGroups()) {
                for (String address : carVolumeGroup.getAddresses()) {
                    if (!addresses.add(address)) {
                        throw new RuntimeException("Device with address "
                                + address + " appears in multiple volume groups or audio zones");
                    }
                }
            }
        }
    }
}
