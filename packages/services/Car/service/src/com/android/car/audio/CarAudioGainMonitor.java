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

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.hardware.automotive.audiocontrol.Reasons;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.audio.hal.HalAudioGainCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Provides audio gain callback registration helpers and implements AudioGain listener business
 * logic.
 */
/* package */ final class CarAudioGainMonitor {
    @NonNull private final AudioControlWrapper mAudioControlWrapper;
    @NonNull private final SparseArray<CarAudioZone> mCarAudioZones;

    CarAudioGainMonitor(
            AudioControlWrapper audioControlWrapper,
            SparseArray<CarAudioZone> carAudioZones) {
        mAudioControlWrapper =
                Objects.requireNonNull(
                        audioControlWrapper, "Audio Control Wrapper can not be null");
        mCarAudioZones = Objects.requireNonNull(carAudioZones, "Car Audio Zones can not be null");
    }

    public void reset() {
        // TODO (b/224885748): handle specific logic on IAudioControl service died event
    }

    /**
     * Registers {@code HalAudioGainCallback} on {@code AudioControlWrapper} to receive HAL audio
     * gain change notifications.
     */
    public void registerAudioGainListener(HalAudioGainCallback callback) {
        Objects.requireNonNull(callback, "Hal Audio Gain callback can not be null");
        mAudioControlWrapper.registerAudioGainCallback(callback);
    }

    /** Unregisters {@code HalAudioGainCallback} from {@code AudioControlWrapper}. */
    public void unregisterAudioGainListener() {
        mAudioControlWrapper.unregisterAudioGainCallback();
    }

    /**
     * Audio Gain event dispatcher. Implements the callback that triggered from {@link
     * IAudioGainCallback#onAudioDeviceGainsChanged} with the list of reasons and the list of {@link
     * CarAudioGainConfigInfo} involved. It is in charge of dispatching /delegating to the zone the
     * {@link CarAudioGainConfigInfo} belongs the processing of the callback.
     */
    void handleAudioDeviceGainsChanged(List<Integer> reasons, List<CarAudioGainConfigInfo> gains) {
        // Delegate to CarAudioZone / CarVolumeGroup
        // Group gains by Audio Zones first
        SparseArray<List<CarAudioGainConfigInfo>> gainsByZones = new SparseArray<>();
        for (int index = 0; index < gains.size(); index++) {
            CarAudioGainConfigInfo gain = gains.get(index);
            int zone = gain.getZoneId();
            if (!gainsByZones.contains(zone)) {
                gainsByZones.put(zone, new ArrayList<>(1));
            }
            gainsByZones.get(zone).add(gain);
        }
        for (int i = 0; i < gainsByZones.size(); i++) {
            int zoneId = gainsByZones.keyAt(i);
            if (!mCarAudioZones.contains(zoneId)) {
                Slogf.e(
                        CarLog.TAG_AUDIO,
                        "onAudioDeviceGainsChanged reported change on invalid "
                                + "zone: %d, reasons=%s, gains=%s",
                        zoneId,
                        reasons,
                        gains);
                continue;
            }
            CarAudioZone carAudioZone = mCarAudioZones.get(zoneId);
            carAudioZone.onAudioGainChanged(reasons, gainsByZones.valueAt(i));
        }
    }

    static boolean shouldBlockVolumeRequest(List<Integer> reasons) {
        return reasons.contains(Reasons.FORCED_MASTER_MUTE)
                || reasons.contains(Reasons.TCU_MUTE)
                || reasons.contains(Reasons.REMOTE_MUTE);
    }

    static boolean shouldLimitVolume(List<Integer> reasons) {
        return reasons.contains(Reasons.THERMAL_LIMITATION)
                || reasons.contains(Reasons.SUSPEND_EXIT_VOL_LIMITATION);
    }

    static boolean shouldDuckGain(List<Integer> reasons) {
        return reasons.contains(Reasons.ADAS_DUCKING) || reasons.contains(Reasons.NAV_DUCKING);
    }
}
