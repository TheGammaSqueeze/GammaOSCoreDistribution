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

import static android.car.builtin.media.AudioManagerHelper.usageToXsdString;

import android.hardware.audio.common.PlaybackTrackMetadata;
import android.hardware.automotive.audiocontrol.DuckingInfo;
import android.media.AudioAttributes;
import android.media.audio.common.AudioChannelLayout;
import android.media.audio.common.AudioDevice;
import android.media.audio.common.AudioDeviceAddress;
import android.media.audio.common.AudioDeviceDescription;

import com.android.car.internal.annotation.AttributeUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Car HAL audio Utils */
public final class CarHalAudioUtils {
    private CarHalAudioUtils() {}

    /**
     * Creates {@link DuckingInfo} instance from contents of {@link CarDuckingInfo}.
     *
     * <p>Converts usages to XSD strings as part of this process.
     */
    public static DuckingInfo generateDuckingInfo(CarDuckingInfo carDuckingInfo) {
        Objects.requireNonNull(carDuckingInfo, "Car Ducking Info can not be null");
        DuckingInfo duckingInfo = new DuckingInfo();
        duckingInfo.zoneId = carDuckingInfo.getZoneId();
        duckingInfo.deviceAddressesToDuck =
                carDuckingInfo.getAddressesToDuck().toArray(new String[0]);
        duckingInfo.deviceAddressesToUnduck =
                carDuckingInfo.getAddressesToUnduck().toArray(new String[0]);
        List<PlaybackTrackMetadata> playbackTrackMetadataList =
                carDuckingInfo.getPlaybackMetaDataHoldingFocus();
        duckingInfo.playbackMetaDataHoldingFocus =
                playbackTrackMetadataList.toArray(PlaybackTrackMetadata[]::new);
        duckingInfo.usagesHoldingFocus = metadatasToUsageStrings(playbackTrackMetadataList);
        return duckingInfo;
    }

    /**
     * Converts the {@link AttributeUsage} into a metadata for a particular
     * audio zone.
     *
     */
    public static PlaybackTrackMetadata audioAttributeToMetadata(
            AudioAttributes audioAttributes, CarAudioZone zone) {
        Objects.requireNonNull(zone, "Car audio zone can not be null");
        int carAudioContextId = zone.getCarAudioContext()
                .getContextForAudioAttribute(audioAttributes);
        PlaybackTrackMetadata playbackTrackMetadata = new PlaybackTrackMetadata();
        playbackTrackMetadata.usage = audioAttributes.getSystemUsage();
        playbackTrackMetadata.tags = new String[0];
        playbackTrackMetadata.channelMask = AudioChannelLayout.none(0);
        AudioDeviceDescription audioDeviceDescription = new AudioDeviceDescription();
        audioDeviceDescription.connection = new String();
        AudioDevice audioDevice = new AudioDevice();
        audioDevice.type = audioDeviceDescription;
        audioDevice.address =
                AudioDeviceAddress.id(zone.getAddressForContext(carAudioContextId));
        playbackTrackMetadata.sourceDevice = audioDevice;
        return playbackTrackMetadata;
    }

    /**
     * Converts the list of {@link AttributeUsage} usages into
     * Playback metadata for a particular zone.
     *
     */
    public static List<PlaybackTrackMetadata> audioAttributesToMetadatas(
            List<AudioAttributes> audioAttributes, CarAudioZone zone) {
        Objects.requireNonNull(zone, "Car audio zone can not be null");
        List<PlaybackTrackMetadata> playbackTrackMetadataList =
                new ArrayList<>(audioAttributes.size());
        for (int index = 0; index < audioAttributes.size(); index++) {
            playbackTrackMetadataList.add(audioAttributeToMetadata(audioAttributes.get(index),
                    zone));
        }
        return playbackTrackMetadataList;
    }

    /**
     * Converts a playback track metadata into the corresponding audio attribute
     *
     */
    public static AudioAttributes metadataToAudioAttribute(
            PlaybackTrackMetadata playbackTrackMetadataList) {
        return CarAudioContext.getAudioAttributeFromUsage(playbackTrackMetadataList.usage);
    }

    /**
     * Converts a list playback track metadata into list of audio attributes
     *
     */
    public static List<AudioAttributes> metadataToAudioAttributes(
            List<PlaybackTrackMetadata> playbackTrackMetadataList) {
        List<AudioAttributes> audioAttributesForMetadata =
                new ArrayList<>(playbackTrackMetadataList.size());
        for (int index = 0; index < playbackTrackMetadataList.size(); index++) {
            audioAttributesForMetadata.add(
                    metadataToAudioAttribute(playbackTrackMetadataList.get(index)));
        }
        return audioAttributesForMetadata;
    }

    /**
     * Converts a list of playback track metadata into an array of
     * audio usages in string representation.
     */
    public static String[] metadatasToUsageStrings(
            List<PlaybackTrackMetadata> playbackTrackMetadataList) {
        String[] usageLiteralsForMetadata = new String[playbackTrackMetadataList.size()];
        for (int index = 0; index < playbackTrackMetadataList.size(); index++) {
            PlaybackTrackMetadata playbackTrackMetadata = playbackTrackMetadataList.get(index);
            usageLiteralsForMetadata[index] = usageToXsdString(playbackTrackMetadata.usage);
        }
        return usageLiteralsForMetadata;
    }
}
