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
import static android.media.AudioDeviceInfo.TYPE_BUS;
import static android.media.AudioDeviceInfo.TYPE_FM_TUNER;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.expectThrows;

import android.media.AudioDeviceAttributes;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CarAudioZonesValidatorTest {
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void validate_thereIsAtLeastOneZone() {
        RuntimeException exception = expectThrows(RuntimeException.class,
                () -> CarAudioZonesValidator.validate(new SparseArray<CarAudioZone>()));

        assertThat(exception).hasMessageThat().contains("At least one zone should be defined");

    }

    @Test
    public void validate_failsOnEmptyInputDevices() {
        CarAudioZone zone = new MockBuilder().withInputDevices(new ArrayList<>()).build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(zone.getId(), zone);

        IllegalArgumentException exception = expectThrows(IllegalArgumentException.class,
                () -> CarAudioZonesValidator.validate(zones));

        assertThat(exception).hasMessageThat().contains("Primary Zone Input Devices");
    }

    @Test
    public void validate_failsOnNullInputDevices() {
        CarAudioZone zone = new MockBuilder().withInputDevices(null).build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(zone.getId(), zone);

        NullPointerException exception = expectThrows(NullPointerException.class,
                () -> CarAudioZonesValidator.validate(zones));

        assertThat(exception).hasMessageThat().contains("Primary Zone Input Devices");
    }

    @Test
    public void validate_failsOnMissingMicrophoneInputDevices() {
        CarAudioZone zone = new MockBuilder().withInputDevices(
                List.of(generateInputAudioDeviceAttributeInfo("tuner", TYPE_FM_TUNER)))
                .build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(zone.getId(), zone);

        RuntimeException exception = expectThrows(RuntimeException.class,
                () -> CarAudioZonesValidator.validate(zones));

        assertThat(exception).hasMessageThat().contains("Primary Zone must have");
    }

    @Test
    public void validate_volumeGroupsForEachZone() {
        SparseArray<CarAudioZone> zones = generateAudioZonesWithPrimary();
        CarAudioZone zoneOne = new MockBuilder()
                .withInvalidVolumeGroups()
                .withZoneId(1)
                .build();
        zones.put(zoneOne.getId(), zoneOne);

        RuntimeException exception = expectThrows(RuntimeException.class,
                () -> CarAudioZonesValidator.validate(zones));

        assertThat(exception).hasMessageThat()
                .contains("Invalid volume groups configuration for zone " + 1);
    }

    @Test
    public void validate_eachAddressAppearsInOnlyOneZone() {
        CarVolumeGroup mockVolumeGroup = generateVolumeGroup(List.of("one", "two", "three"));

        CarAudioZone primaryZone = new MockBuilder()
                .withVolumeGroups(new CarVolumeGroup[]{mockVolumeGroup})
                .build();

        CarVolumeGroup mockSecondaryVolumeGroup = generateVolumeGroup(
                List.of("three", "four", "five"));

        CarAudioZone secondaryZone = new MockBuilder()
                .withZoneId(1)
                .withVolumeGroups(new CarVolumeGroup[]{mockSecondaryVolumeGroup})
                .build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(primaryZone.getId(), primaryZone);
        zones.put(secondaryZone.getId(), secondaryZone);

        RuntimeException exception = expectThrows(RuntimeException.class,
                () -> CarAudioZonesValidator.validate(zones));

        assertThat(exception).hasMessageThat().contains(
                "Device with address three appears in multiple volume groups or audio zones");
    }

    @Test
    public void validate_passesWithoutExceptionForValidZoneConfiguration() {
        SparseArray<CarAudioZone> zones = generateAudioZonesWithPrimary();

        CarAudioZonesValidator.validate(zones);
    }

    private SparseArray<CarAudioZone> generateAudioZonesWithPrimary() {
        CarAudioZone zone = new MockBuilder().withInputDevices(getValidInputDevices()).build();
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        zones.put(zone.getId(), zone);
        return zones;
    }

    private CarVolumeGroup generateVolumeGroup(List<String> deviceAddresses) {
        CarVolumeGroup mockVolumeGroup = Mockito.mock(CarVolumeGroup.class);
        when(mockVolumeGroup.getAddresses()).thenReturn(deviceAddresses);
        return mockVolumeGroup;
    }

    private List<AudioDeviceAttributes> getValidInputDevices() {
        return List.of(generateInputAudioDeviceAttributeInfo("mic", TYPE_BUILTIN_MIC),
                generateInputAudioDeviceAttributeInfo("tuner", TYPE_FM_TUNER),
                generateInputAudioDeviceAttributeInfo("bus", TYPE_BUS));
    }
    private static class MockBuilder {
        private boolean mHasValidVolumeGroups = true;
        private int mZoneId = PRIMARY_AUDIO_ZONE;
        private CarVolumeGroup[] mVolumeGroups = new CarVolumeGroup[0];
        private List<AudioDeviceAttributes> mInputDevices = new ArrayList<>();

        CarAudioZone build() {
            CarAudioZone zoneMock = Mockito.mock(CarAudioZone.class);
            when(zoneMock.getId()).thenReturn(mZoneId);
            when(zoneMock.validateVolumeGroups()).thenReturn(mHasValidVolumeGroups);
            when(zoneMock.getVolumeGroups()).thenReturn(mVolumeGroups);
            when(zoneMock.getInputAudioDevices()).thenReturn(mInputDevices);
            return zoneMock;
        }

        MockBuilder withInvalidVolumeGroups() {
            mHasValidVolumeGroups = false;
            return this;
        }

        MockBuilder withZoneId(int zoneId) {
            mZoneId = zoneId;
            return this;
        }

        MockBuilder withVolumeGroups(CarVolumeGroup[] volumeGroups) {
            mVolumeGroups = volumeGroups;
            return this;
        }

        MockBuilder withInputDevices(List<AudioDeviceAttributes> inputDevices) {
            mInputDevices = inputDevices;
            return this;
        }
    }

    private AudioDeviceAttributes generateInputAudioDeviceAttributeInfo(String address, int type) {
        AudioDeviceAttributes inputMock = mock(AudioDeviceAttributes.class);
        when(inputMock.getAddress()).thenReturn(address);
        when(inputMock.getType()).thenReturn(type);
        return inputMock;
    }
}