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

package com.android.car.audio;

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_MEDIA;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;

import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;
import android.hardware.automotive.audiocontrol.IAudioControl;
import android.hardware.automotive.audiocontrol.Reasons;
import android.os.IBinder;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.audio.hal.AudioControlWrapperAidl;
import com.android.car.audio.hal.HalAudioGainCallback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class CarAudioGainMonitorTest extends AbstractExtendedMockitoTestCase {
    private static final int PRIMARY_ZONE_ID = 0;
    private static final int PASSENGER_ZONE_ID = 1;
    private static final int UNKNOWN_ZONE_ID = 50;
    private static final int REAR_ZONE_ID = 2;
    private static final String PRIMARY_MEDIA_ADDRESS = "primary_media";
    private static final String PRIMARY_NAVIGATION_ADDRESS = "primary_navigation_address";
    private static final String PRIMARY_CALL_ADDRESS = "primary_call_address";
    private static final String REAR_MEDIA_ADDRESS = "rear_media";

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo());

    private static final @CarAudioContext.AudioContext int TEST_MEDIA_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(
                    CarAudioContext.getAudioAttributeFromUsage(USAGE_MEDIA));
    private static final @CarAudioContext.AudioContext int TEST_NAVIGATION_AUDIO_CONTEXT =
            TEST_CAR_AUDIO_CONTEXT.getContextForAudioAttribute(CarAudioContext
                    .getAudioAttributeFromUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE));

    private final SparseArray<CarAudioZone> mCarAudioZones = generateZoneMocks();

    @Mock private IBinder mBinder;

    @Mock private IAudioControl mAudioControl;

    private AudioControlWrapperAidl mAudioControlWrapperAidl;

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(AudioControlWrapperAidl.class);
    }

    @Before
    public void setUp() {
        when(mBinder.queryLocalInterface(anyString())).thenReturn(mAudioControl);
        doReturn(mBinder).when(AudioControlWrapperAidl::getService);
        mAudioControlWrapperAidl = spy(new AudioControlWrapperAidl(mBinder));
    }

    @Test
    public void constructor_fails() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CarAudioGainMonitor(
                                /* AudioControlWrapper= */ null, /* SparseArray<CarAudioZone>= */
                                null));

        assertThrows(
                NullPointerException.class,
                () ->
                        new CarAudioGainMonitor(
                                mAudioControlWrapperAidl, /* SparseArray<CarAudioZone>= */ null));

        assertThrows(
                NullPointerException.class,
                () -> new CarAudioGainMonitor(/* AudioControlWrapper= */ null, mCarAudioZones));
    }

    @Test
    public void constructor_succeeds() {
        CarAudioGainMonitor carAudioGainMonitor =
                new CarAudioGainMonitor(mAudioControlWrapperAidl, mCarAudioZones);

        assertNotNull(carAudioGainMonitor);
    }

    @Test
    public void registercallback_succeeds() {
        CarAudioGainMonitor carAudioGainMonitor =
                new CarAudioGainMonitor(mAudioControlWrapperAidl, mCarAudioZones);

        HalAudioGainCallback callback = mock(HalAudioGainCallback.class);
        carAudioGainMonitor.registerAudioGainListener(callback);
        verify(mAudioControlWrapperAidl).registerAudioGainCallback(eq(callback));

        carAudioGainMonitor.unregisterAudioGainListener();
        verify(mAudioControlWrapperAidl).unregisterAudioGainCallback();
    }

    @Test
    public void registercallback_multipleTimes() {
        CarAudioGainMonitor carAudioGainMonitor =
                new CarAudioGainMonitor(mAudioControlWrapperAidl, mCarAudioZones);
        HalAudioGainCallback callback = mock(HalAudioGainCallback.class);
        carAudioGainMonitor.registerAudioGainListener(callback);
        verify(mAudioControlWrapperAidl).registerAudioGainCallback(eq(callback));

        carAudioGainMonitor.registerAudioGainListener(callback);
        verify(mAudioControlWrapperAidl, times(2)).registerAudioGainCallback(eq(callback));

        HalAudioGainCallback callback2 = mock(HalAudioGainCallback.class);
        carAudioGainMonitor.registerAudioGainListener(callback2);
        verify(mAudioControlWrapperAidl).registerAudioGainCallback(eq(callback2));
    }

    @Test
    public void handleAudioDeviceGainsChanged_validZones() {
        CarAudioGainMonitor carAudioGainMonitor =
                new CarAudioGainMonitor(mAudioControlWrapperAidl, mCarAudioZones);
        List<Integer> reasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);
        AudioGainConfigInfo primaryZoneGain = new AudioGainConfigInfo();
        primaryZoneGain.zoneId = PRIMARY_ZONE_ID;
        primaryZoneGain.devicePortAddress = PRIMARY_MEDIA_ADDRESS;
        CarAudioGainConfigInfo primaryZoneCarGain = new CarAudioGainConfigInfo(primaryZoneGain);
        AudioGainConfigInfo primaryZoneGain2 = new AudioGainConfigInfo();
        primaryZoneGain2.zoneId = PRIMARY_ZONE_ID;
        primaryZoneGain2.devicePortAddress = PRIMARY_NAVIGATION_ADDRESS;
        CarAudioGainConfigInfo primaryZoneCarGain2 = new CarAudioGainConfigInfo(primaryZoneGain2);
        AudioGainConfigInfo primaryZoneGain3 = new AudioGainConfigInfo();
        primaryZoneGain3.zoneId = PRIMARY_ZONE_ID;
        primaryZoneGain3.devicePortAddress = PRIMARY_CALL_ADDRESS;
        CarAudioGainConfigInfo primaryZoneCarGain3 = new CarAudioGainConfigInfo(primaryZoneGain3);
        AudioGainConfigInfo passengerZoneGain = new AudioGainConfigInfo();
        passengerZoneGain.zoneId = PASSENGER_ZONE_ID;
        CarAudioGainConfigInfo passengerZoneCarGain = new CarAudioGainConfigInfo(passengerZoneGain);
        AudioGainConfigInfo rearZoneGain = new AudioGainConfigInfo();
        rearZoneGain.zoneId = REAR_ZONE_ID;
        CarAudioGainConfigInfo rearZoneCarGain = new CarAudioGainConfigInfo(rearZoneGain);

        SparseArray<List<CarAudioGainConfigInfo>> gainsForZones = new SparseArray<>();
        gainsForZones.put(
                PRIMARY_ZONE_ID,
                Arrays.asList(primaryZoneCarGain, primaryZoneCarGain2, primaryZoneCarGain3));
        gainsForZones.put(PASSENGER_ZONE_ID, Arrays.asList(passengerZoneCarGain));
        gainsForZones.put(REAR_ZONE_ID, Arrays.asList(rearZoneCarGain));

        List<CarAudioGainConfigInfo> gains =
                List.of(
                        primaryZoneCarGain,
                        primaryZoneCarGain2,
                        primaryZoneCarGain3,
                        passengerZoneCarGain,
                        rearZoneCarGain);
        carAudioGainMonitor.handleAudioDeviceGainsChanged(reasons, gains);

        for (int index = 0; index < mCarAudioZones.size(); index++) {
            CarAudioZone carAudioZone = mCarAudioZones.valueAt(index);
            List<CarAudioGainConfigInfo> gainsForZone = gainsForZones.get(carAudioZone.getId());
            verify(carAudioZone).onAudioGainChanged(eq(reasons), eq(gainsForZone));
        }
    }

    @Test
    public void handleAudioDeviceGainsChanged_validAndUnknownZones() {
        CarAudioGainMonitor carAudioGainMonitor =
                new CarAudioGainMonitor(mAudioControlWrapperAidl, mCarAudioZones);
        List<Integer> reasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);
        AudioGainConfigInfo primaryZoneGain = new AudioGainConfigInfo();
        primaryZoneGain.zoneId = PRIMARY_ZONE_ID;
        CarAudioGainConfigInfo primaryZoneCarGain = new CarAudioGainConfigInfo(primaryZoneGain);
        AudioGainConfigInfo rearZoneGain = new AudioGainConfigInfo();
        rearZoneGain.zoneId = REAR_ZONE_ID;
        CarAudioGainConfigInfo rearZoneCarGain = new CarAudioGainConfigInfo(rearZoneGain);
        AudioGainConfigInfo unknownGain = new AudioGainConfigInfo();
        unknownGain.zoneId = UNKNOWN_ZONE_ID;
        CarAudioGainConfigInfo unknownCarGain = new CarAudioGainConfigInfo(unknownGain);

        SparseArray<List<CarAudioGainConfigInfo>> gainsForZones = new SparseArray<>();
        gainsForZones.put(PRIMARY_ZONE_ID, Arrays.asList(primaryZoneCarGain));
        gainsForZones.put(REAR_ZONE_ID, Arrays.asList(rearZoneCarGain));
        gainsForZones.put(UNKNOWN_ZONE_ID, Arrays.asList(unknownCarGain));

        List<CarAudioGainConfigInfo> gains =
                List.of(primaryZoneCarGain, unknownCarGain, rearZoneCarGain);
        carAudioGainMonitor.handleAudioDeviceGainsChanged(reasons, gains);

        for (int index = 0; index < mCarAudioZones.size(); index++) {
            CarAudioZone carAudioZone = mCarAudioZones.valueAt(index);
            Integer zoneId = carAudioZone.getId();
            if (gainsForZones.contains(zoneId)) {
                List<CarAudioGainConfigInfo> gainsForZone = gainsForZones.get(zoneId);
                verify(carAudioZone).onAudioGainChanged(eq(reasons), eq(gainsForZone));
                continue;
            }
            verify(carAudioZone, never()).onAudioGainChanged(any(), any());
        }
    }

    @Test
    public void handleAudioDeviceGainsChanged_unknownZones() {
        CarAudioGainMonitor carAudioGainMonitor =
                new CarAudioGainMonitor(mAudioControlWrapperAidl, mCarAudioZones);
        List<Integer> reasons = List.of(Reasons.REMOTE_MUTE, Reasons.NAV_DUCKING);

        AudioGainConfigInfo unknownGain = new AudioGainConfigInfo();
        unknownGain.zoneId = UNKNOWN_ZONE_ID;
        CarAudioGainConfigInfo unknownCarGain = new CarAudioGainConfigInfo(unknownGain);
        AudioGainConfigInfo unknownGain2 = new AudioGainConfigInfo();
        unknownGain2.zoneId = REAR_ZONE_ID + 1;
        CarAudioGainConfigInfo unknownCarGain2 = new CarAudioGainConfigInfo(unknownGain2);

        List<CarAudioGainConfigInfo> gains = List.of(unknownCarGain, unknownCarGain2);
        carAudioGainMonitor.handleAudioDeviceGainsChanged(reasons, gains);

        for (int index = 0; index < mCarAudioZones.size(); index++) {
            CarAudioZone carAudioZone = mCarAudioZones.valueAt(index);
            verify(carAudioZone, never()).onAudioGainChanged(any(), any());
        }
    }

    @Test
    public void shouldBlockVolumeRequest_returnsTrue() {
        List<Integer> blockingReasons =
                List.of(Reasons.FORCED_MASTER_MUTE, Reasons.TCU_MUTE, Reasons.REMOTE_MUTE);

        // One by one
        for (int index = 0; index < blockingReasons.size(); index++) {
            List<Integer> reasons = Arrays.asList(blockingReasons.get(index));
            assertWithMessage("Volume Requests Blocked")
                    .that(CarAudioGainMonitor.shouldBlockVolumeRequest(reasons))
                    .isTrue();
        }
        // All
        assertWithMessage("Volume Requests Blocked")
                .that(CarAudioGainMonitor.shouldBlockVolumeRequest(blockingReasons))
                .isTrue();

        List<Integer> mixedReasons =
                List.of(
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.NAV_DUCKING,
                        Reasons.THERMAL_LIMITATION);

        assertWithMessage("Volume Requests Blocked")
                .that(CarAudioGainMonitor.shouldBlockVolumeRequest(mixedReasons))
                .isTrue();
    }

    @Test
    public void shouldBlockVolumeRequest_returnsFalse() {
        List<Integer> nonBlockingReasons =
                List.of(
                        Reasons.NAV_DUCKING,
                        Reasons.ADAS_DUCKING,
                        Reasons.THERMAL_LIMITATION,
                        Reasons.SUSPEND_EXIT_VOL_LIMITATION);

        // One by one
        for (int index = 0; index < nonBlockingReasons.size(); index++) {
            List<Integer> reasons = Arrays.asList(nonBlockingReasons.get(index));
            assertWithMessage("Volume Requests Blocked")
                    .that(CarAudioGainMonitor.shouldBlockVolumeRequest(reasons))
                    .isFalse();
        }
        // All
        assertWithMessage("Volume Requests Blocked")
                .that(CarAudioGainMonitor.shouldBlockVolumeRequest(nonBlockingReasons))
                .isFalse();
    }

    @Test
    public void shouldLimitVolume_returnsTrue() {
        List<Integer> limitReasons =
                List.of(Reasons.THERMAL_LIMITATION, Reasons.SUSPEND_EXIT_VOL_LIMITATION);

        // One by one
        for (int index = 0; index < limitReasons.size(); index++) {
            List<Integer> reasons = Arrays.asList(limitReasons.get(index));
            assertWithMessage("Volume Limited")
                    .that(CarAudioGainMonitor.shouldLimitVolume(reasons))
                    .isTrue();
        }
        // All
        assertWithMessage("Volume Limited")
                .that(CarAudioGainMonitor.shouldLimitVolume(limitReasons))
                .isTrue();

        List<Integer> mixedReasons =
                List.of(
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.NAV_DUCKING,
                        Reasons.THERMAL_LIMITATION);

        assertWithMessage("Volume Limited")
                .that(CarAudioGainMonitor.shouldLimitVolume(mixedReasons))
                .isTrue();
    }

    @Test
    public void shouldLimitVolume_returnsFalse() {
        List<Integer> nonLimitReasons =
                List.of(
                        Reasons.NAV_DUCKING,
                        Reasons.ADAS_DUCKING,
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.TCU_MUTE,
                        Reasons.REMOTE_MUTE);

        // One by one
        for (int index = 0; index < nonLimitReasons.size(); index++) {
            List<Integer> reasons = Arrays.asList(nonLimitReasons.get(index));
            assertWithMessage("Volume Limited")
                    .that(CarAudioGainMonitor.shouldLimitVolume(reasons))
                    .isFalse();
        }
        // All
        assertWithMessage("Volume Limited")
                .that(CarAudioGainMonitor.shouldLimitVolume(nonLimitReasons))
                .isFalse();
    }

    @Test
    public void shouldDuckGain_returnsTrue() {
        List<Integer> limitReasons = List.of(Reasons.ADAS_DUCKING, Reasons.NAV_DUCKING);

        // One by one
        for (int index = 0; index < limitReasons.size(); index++) {
            List<Integer> reasons = Arrays.asList(limitReasons.get(index));
            assertWithMessage("Volume Requests Blocked")
                    .that(CarAudioGainMonitor.shouldDuckGain(reasons))
                    .isTrue();
        }
        // All
        assertWithMessage("Volume Attenuated")
                .that(CarAudioGainMonitor.shouldDuckGain(limitReasons))
                .isTrue();

        List<Integer> mixedReasons =
                List.of(
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.NAV_DUCKING,
                        Reasons.THERMAL_LIMITATION);

        assertWithMessage("Volume Attenuated")
                .that(CarAudioGainMonitor.shouldDuckGain(mixedReasons))
                .isTrue();
    }

    @Test
    public void shouldDuckGain_returnsFalse() {
        List<Integer> nonDuckingReasons =
                List.of(
                        Reasons.THERMAL_LIMITATION,
                        Reasons.SUSPEND_EXIT_VOL_LIMITATION,
                        Reasons.FORCED_MASTER_MUTE,
                        Reasons.TCU_MUTE,
                        Reasons.REMOTE_MUTE);

        // One by one
        for (int index = 0; index < nonDuckingReasons.size(); index++) {
            List<Integer> reasons = Arrays.asList(nonDuckingReasons.get(index));
            assertWithMessage("Volume Attenuated")
                    .that(CarAudioGainMonitor.shouldDuckGain(reasons))
                    .isFalse();
        }
        // All
        assertWithMessage("Volume Attenuated")
                .that(CarAudioGainMonitor.shouldDuckGain(nonDuckingReasons))
                .isFalse();
    }

    private static SparseArray<CarAudioZone> generateZoneMocks() {
        SparseArray<CarAudioZone> zones = new SparseArray<>();
        CarAudioZone primaryZone = mock(CarAudioZone.class, RETURNS_DEEP_STUBS);
        when(primaryZone.getId()).thenReturn(PRIMARY_ZONE_ID);
        when(primaryZone.getAddressForContext(TEST_MEDIA_AUDIO_CONTEXT))
                .thenReturn(PRIMARY_MEDIA_ADDRESS);
        when(primaryZone.getAddressForContext(TEST_NAVIGATION_AUDIO_CONTEXT))
                .thenReturn(PRIMARY_NAVIGATION_ADDRESS);
        zones.append(PRIMARY_ZONE_ID, primaryZone);

        CarAudioZone passengerZone = mock(CarAudioZone.class, RETURNS_DEEP_STUBS);
        when(passengerZone.getId()).thenReturn(PASSENGER_ZONE_ID);
        zones.append(PASSENGER_ZONE_ID, passengerZone);

        CarAudioZone rearZone = mock(CarAudioZone.class, RETURNS_DEEP_STUBS);
        when(rearZone.getId()).thenReturn(REAR_ZONE_ID);
        when(rearZone.getAddressForContext(TEST_MEDIA_AUDIO_CONTEXT))
                .thenReturn(REAR_MEDIA_ADDRESS);
        zones.append(REAR_ZONE_ID, rearZone);

        return zones;
    }
}
