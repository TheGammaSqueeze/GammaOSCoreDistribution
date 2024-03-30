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

package com.android.car.oem.ducking;

import static com.android.car.oem.utils.AudioUtils.getAudioAttributeFromUsage;

import android.car.oem.OemCarAudioVolumeRequest;
import android.media.AudioAttributes;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Slog;

import com.android.car.oem.utils.AudioAttributesWrapper;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class for evaluating the focus interactions
 */
public final class DuckingInteractions {

    private static final String TAG = DuckingInteractions.class.getSimpleName();

    /**
     * List of ducking priorities, with the highest priority at the beginning of the list.
     */
    public static final List<AudioAttributes> DUCKED_PRIORITIES = List.of(
            getAudioAttributeFromUsage(AudioAttributes.USAGE_EMERGENCY),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_SAFETY),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANT),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_CALL_ASSISTANT),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_VEHICLE_STATUS),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ANNOUNCEMENT),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_ALARM),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_UNKNOWN),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_GAME),
            getAudioAttributeFromUsage(AudioAttributes.USAGE_MEDIA));

    private final ArrayMap<AudioAttributesWrapper, Integer> mAudioAttributeToPriority;

    public DuckingInteractions(List<AudioAttributes> audioAttributes) {
        mAudioAttributeToPriority = new ArrayMap<>(audioAttributes.size());
        for (int index = 0; index < audioAttributes.size(); index++) {
            mAudioAttributeToPriority.append(new AudioAttributesWrapper(audioAttributes.get(index)),
                    audioAttributes.size() - index - 1);
        }
    }

    /**
     * Returns a list of audio attributes to duck from the active list
     *
     * @param duckingRequest ducking request to evaluate
     *
     * @return audio attributes to duck
     */
    public List<AudioAttributes> getDuckedAudioAttributes(
            OemCarAudioVolumeRequest duckingRequest) {
        List<AudioAttributes> activeAudioAttributes = duckingRequest.getActivePlaybackAttributes();
        // Nothing to duck if there is only one or fewer items
        if (activeAudioAttributes.size() < 2) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Not ducking any audio attributes");
            }
            return Collections.EMPTY_LIST;
        }
        int highestPriority = -1;
        AudioAttributesWrapper highestPriorityAttribute = null;
        List<AudioAttributesWrapper> activeAudioAttributesWrappers =
                new ArrayList<>(activeAudioAttributes.size());
        for (int index = 0; index < activeAudioAttributes.size(); index++) {
            AudioAttributesWrapper wrapper =
                    new AudioAttributesWrapper(activeAudioAttributes.get(index));
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Evaluating " + wrapper);
            }
            activeAudioAttributesWrappers.add(wrapper);
            int priority = mAudioAttributeToPriority.getOrDefault(wrapper, /* defaultValue= */ -1);
            if (priority > highestPriority) {
                highestPriority = priority;
                highestPriorityAttribute = wrapper;
            }
        }

        // Duck everything except higher priority
        List<AudioAttributes> duckedAudioAttributes =
                new ArrayList<>(activeAudioAttributes.size() - 1);

        for (int index = 0; index < activeAudioAttributesWrappers.size(); index++) {
            AudioAttributesWrapper wrapper = activeAudioAttributesWrappers.get(index);
            if (wrapper.equals(highestPriorityAttribute)) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Slog.d(TAG, "Not ducking audio attribute[" + index + "]:"
                            + highestPriorityAttribute);
                }
                continue;
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Slog.d(TAG, "Ducking audio attribute[" + index + "]:" + wrapper);
            }
            duckedAudioAttributes.add(wrapper.getAudioAttributes());
        }

        return duckedAudioAttributes;
    }

    public void dump(PrintWriter writer, String indent) {
        writer.printf("%sDucking priorities: \n", indent);
        for (int index = mAudioAttributeToPriority.size() - 1; index > 0; index--) {
            writer.printf("%s%sPriority[%d]: %s \n", indent, indent, index,
                    mAudioAttributeToPriority.keyAt(
                            mAudioAttributeToPriority.indexOfValue(index)));
        }
    }
}
