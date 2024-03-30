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

package com.android.car.audio;

import static com.android.car.audio.CarAudioService.SystemClockWrapper;
import static com.android.car.audio.CarAudioUtils.hasExpired;

import android.annotation.NonNull;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class CarAudioPlaybackCallback extends AudioManager.AudioPlaybackCallback {
    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final ArrayMap<AudioAttributes, Long> mAudioAttributesStartTime = new ArrayMap<>();
    @GuardedBy("mLock")
    private final ArrayMap<String, AudioPlaybackConfiguration> mLastActiveConfigs =
            new ArrayMap<>();
    private final CarAudioZone mCarPrimaryAudioZone;
    private final SystemClockWrapper mClock;
    private final int mVolumeKeyEventTimeoutMs;

    CarAudioPlaybackCallback(@NonNull CarAudioZone carPrimaryAudioZone,
            @NonNull SystemClockWrapper clock,
            int volumeKeyEventTimeoutMs) {
        mCarPrimaryAudioZone = Objects.requireNonNull(carPrimaryAudioZone);
        mClock = Objects.requireNonNull(clock);
        mVolumeKeyEventTimeoutMs = Preconditions.checkArgumentNonnegative(volumeKeyEventTimeoutMs);
    }

    @Override
    public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configurations) {
        ArrayMap<String, AudioPlaybackConfiguration> newActiveConfigs =
                filterNewActiveConfiguration(configurations);

        synchronized (mLock) {
            List<AudioPlaybackConfiguration> newlyInactiveConfigurations =
                    getNewlyInactiveConfigurationsLocked(newActiveConfigs);

            mLastActiveConfigs.clear();
            mLastActiveConfigs.putAll(newActiveConfigs);

            startTimersForContextThatBecameInactiveLocked(newlyInactiveConfigurations);
        }
    }

    /**
     * Returns all active contexts for the primary zone
     * @return all active audio contexts, including those that recently became inactive but are
     * considered active due to the audio playback timeout.
     */
    public List<AudioAttributes> getAllActiveAudioAttributesForPrimaryZone() {
        synchronized (mLock) {
            List<AudioAttributes> activeContexts = getCurrentlyActiveAttributesLocked();
            activeContexts
                    .addAll(getStillActiveContextAndRemoveExpiredContextsLocked());
            return activeContexts;
        }
    }

    @GuardedBy("mLock")
    private void startTimersForContextThatBecameInactiveLocked(
            List<AudioPlaybackConfiguration> inactiveConfigs) {
        List<AudioAttributes> activeAttributes = mCarPrimaryAudioZone
                .findActiveAudioAttributesFromPlaybackConfigurations(inactiveConfigs);

        for (int index = 0; index < inactiveConfigs.size(); index++) {
            mAudioAttributesStartTime.put(activeAttributes.get(index), mClock.uptimeMillis());
        }
    }

    @GuardedBy("mLock")
    private List<AudioPlaybackConfiguration> getNewlyInactiveConfigurationsLocked(
            Map<String, AudioPlaybackConfiguration> newActiveConfigurations) {
        List<AudioPlaybackConfiguration> newlyInactiveConfigurations = new ArrayList<>();
        for (int index = 0; index < mLastActiveConfigs.size(); index++) {
            if (newActiveConfigurations
                    .containsKey(mLastActiveConfigs.keyAt(index))) {
                continue;
            }
            newlyInactiveConfigurations.add(mLastActiveConfigs.valueAt(index));
        }
        return newlyInactiveConfigurations;
    }

    private ArrayMap<String, AudioPlaybackConfiguration> filterNewActiveConfiguration(
            List<AudioPlaybackConfiguration> configurations) {
        ArrayMap<String, AudioPlaybackConfiguration> newActiveConfigs = new ArrayMap<>();
        for (int index = 0; index < configurations.size(); index++) {
            AudioPlaybackConfiguration configuration = configurations.get(index);
            if (!configuration.isActive()) {
                continue;
            }
            if (mCarPrimaryAudioZone
                    .isAudioDeviceInfoValidForZone(configuration.getAudioDeviceInfo())) {
                newActiveConfigs.put(
                        configuration.getAudioDeviceInfo().getAddress(), configuration);
            }
        }
        return newActiveConfigs;
    }

    @GuardedBy("mLock")
    private List<AudioAttributes> getCurrentlyActiveAttributesLocked() {
        return mCarPrimaryAudioZone.findActiveAudioAttributesFromPlaybackConfigurations(
                new ArrayList<>(mLastActiveConfigs.values()));
    }

    @GuardedBy("mLock")
    private List<AudioAttributes> getStillActiveContextAndRemoveExpiredContextsLocked() {
        List<AudioAttributes> attributesToRemove = new ArrayList<>();
        List<AudioAttributes> activeAttributes = new ArrayList<>();
        for (int index = 0; index < mAudioAttributesStartTime.size(); index++) {
            long startTime = mAudioAttributesStartTime.valueAt(index);
            if (hasExpired(startTime, mClock.uptimeMillis(), mVolumeKeyEventTimeoutMs)) {
                attributesToRemove.add(mAudioAttributesStartTime.keyAt(index));
                continue;
            }
            activeAttributes.add(mAudioAttributesStartTime.keyAt(index));
        }

        for (int indexToRemove = 0; indexToRemove < attributesToRemove.size(); indexToRemove++) {
            mAudioAttributesStartTime.remove(attributesToRemove.get(indexToRemove));
        }
        return activeAttributes;
    }

    void resetStillActiveContexts() {
        synchronized (mLock) {
            mAudioAttributesStartTime.clear();
        }
    }
}
