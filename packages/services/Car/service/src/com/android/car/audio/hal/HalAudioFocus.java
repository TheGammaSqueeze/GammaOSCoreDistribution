/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.audio.hal;

import static android.car.builtin.media.AudioManagerHelper.usageToString;
import static android.media.AudioManager.AUDIOFOCUS_LOSS;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.car.media.CarAudioManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioContext;
import com.android.car.audio.CarAudioContext.AudioAttributesWrapper;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.annotation.AttributeUsage;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Manages focus requests from the HAL on a per-zone per-usage basis
 */
public final class HalAudioFocus implements HalFocusListener {
    private static final String TAG = CarLog.tagFor(HalAudioFocus.class);

    private final AudioManager mAudioManager;
    private final AudioControlWrapper mAudioControlWrapper;

    private final Object mLock = new Object();

    // Map of Maps. Top level keys are ZoneIds. Second level keys are audio attribute wrapper.
    // Values are HalAudioFocusRequests
    @GuardedBy("mLock")
    private final SparseArray<Map<AudioAttributesWrapper, HalAudioFocusRequest>>
            mHalFocusRequestsByZoneAndUsage;

    public HalAudioFocus(@NonNull AudioManager audioManager,
            @NonNull AudioControlWrapper audioControlWrapper,
            @NonNull int[] audioZoneIds) {
        mAudioManager = Objects.requireNonNull(audioManager);
        mAudioControlWrapper = Objects.requireNonNull(audioControlWrapper);
        Objects.requireNonNull(audioZoneIds);

        mHalFocusRequestsByZoneAndUsage = new SparseArray<>(audioZoneIds.length);
        for (int index = 0; index < audioZoneIds.length; index++) {
            mHalFocusRequestsByZoneAndUsage.put(audioZoneIds[index], new ArrayMap<>());
        }
    }

    /**
     * Registers {@code IFocusListener} on {@code AudioControlWrapper} to receive HAL audio focus
     * request and abandon calls.
     */
    public void registerFocusListener() {
        mAudioControlWrapper.registerFocusListener(this);
    }

    /**
     * Unregisters {@code IFocusListener} from {@code AudioControlWrapper}.
     */
    public void unregisterFocusListener() {
        mAudioControlWrapper.unregisterFocusListener();
    }

    /**
     * See {@link HalFocusListener#requestAudioFocus(int, int, int)}
     */
    public void requestAudioFocus(@AttributeUsage int usage, int zoneId, int focusGain) {
        synchronized (mLock) {
            Preconditions.checkArgument(mHalFocusRequestsByZoneAndUsage.contains(zoneId),
                    "Invalid zoneId %d provided in requestAudioFocus", zoneId);
            if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "Requesting focus gain " + focusGain + " with usage "
                        + usageToString(usage) + " and zoneId " + zoneId);
            }
            AudioAttributesWrapper audioAttributesWrapper =
                    CarAudioContext.getAudioAttributeWrapperFromUsage(usage);
            HalAudioFocusRequest currentRequest =
                    mHalFocusRequestsByZoneAndUsage.get(zoneId).get(audioAttributesWrapper);
            if (currentRequest != null) {
                if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                    Slogf.d(TAG, "A request already exists for zoneId " + zoneId + " and usage "
                            + usage);
                }
                mAudioControlWrapper.onAudioFocusChange(usage, zoneId, currentRequest.mFocusStatus);
            } else {
                makeAudioFocusRequestLocked(audioAttributesWrapper, zoneId, focusGain);
            }
        }
    }

    /**
     * See {@link HalFocusListener#abandonAudioFocus(int, int)}
     */
    public void abandonAudioFocus(@AttributeUsage int usage, int zoneId) {
        synchronized (mLock) {
            Preconditions.checkArgument(mHalFocusRequestsByZoneAndUsage.contains(zoneId),
                    "Invalid zoneId %d provided in abandonAudioFocus", zoneId);
            if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "Abandoning focus with usage " + usageToString(usage)
                        + " for zoneId " + zoneId);
            }
            abandonAudioFocusLocked(CarAudioContext.getAudioAttributeWrapperFromUsage(usage),
                    zoneId);
        }
    }

    /**
     * Clear out all existing focus requests. Called when HAL dies.
     */
    public void reset() {
        Slogf.d(TAG, "Resetting HAL Audio Focus requests");
        synchronized (mLock) {
            for (int i = 0; i < mHalFocusRequestsByZoneAndUsage.size(); i++) {
                int zoneId = mHalFocusRequestsByZoneAndUsage.keyAt(i);
                Map<AudioAttributesWrapper, HalAudioFocusRequest>
                        requestsByAttributes = mHalFocusRequestsByZoneAndUsage.valueAt(i);
                Set<AudioAttributesWrapper> wrapperSet =
                        new ArraySet<>(requestsByAttributes.keySet());
                for (AudioAttributesWrapper wrapper : wrapperSet) {
                    abandonAudioFocusLocked(wrapper, zoneId);
                }
            }
        }
    }

    /**
     * Returns the currently active {@link AudioAttribute}'s for an audio zone
     */
    public List<AudioAttributes> getActiveAudioAttributesForZone(int audioZoneId) {
        synchronized (mLock) {
            Map<AudioAttributesWrapper, HalAudioFocusRequest> halFocusRequestsForZone =
                    mHalFocusRequestsByZoneAndUsage.get(audioZoneId);
            List<AudioAttributes> activeAudioAttributes =
                    new ArrayList<>(halFocusRequestsForZone.size());

            for (AudioAttributesWrapper wrapper : halFocusRequestsForZone.keySet()) {
                activeAudioAttributes.add(wrapper.getAudioAttributes());
            }

            return activeAudioAttributes;
        }
    }

    /**
     * dumps the current state of the HalAudioFocus
     *
     * @param writer stream to write current state
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*HalAudioFocus*");

        writer.increaseIndent();
        writer.println("Current focus requests:");
        writer.increaseIndent();
        synchronized (mLock) {
            for (int i = 0; i < mHalFocusRequestsByZoneAndUsage.size(); i++) {
                int zoneId = mHalFocusRequestsByZoneAndUsage.keyAt(i);
                writer.printf("Zone %s:\n", zoneId);
                writer.increaseIndent();

                Map<AudioAttributesWrapper, HalAudioFocusRequest> requestsByAttributes =
                        mHalFocusRequestsByZoneAndUsage.valueAt(i);
                for (HalAudioFocusRequest request : requestsByAttributes.values()) {
                    writer.printf("%s\n", request);
                }
                writer.decreaseIndent();
            }
        }
        writer.decreaseIndent();
        writer.decreaseIndent();
    }

    @GuardedBy("mLock")
    private void abandonAudioFocusLocked(AudioAttributesWrapper audioAttributesWrapper,
            int zoneId) {
        Map<AudioAttributesWrapper, HalAudioFocusRequest> halAudioFocusRequests =
                mHalFocusRequestsByZoneAndUsage.get(zoneId);
        HalAudioFocusRequest currentRequest = halAudioFocusRequests.get(audioAttributesWrapper);

        if (currentRequest == null) {
            if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "No focus to abandon for audio attributes " + audioAttributesWrapper
                        + " and zoneId " + zoneId);
            }
            return;
        } else {
            // remove it from map
            halAudioFocusRequests.remove(audioAttributesWrapper);
        }

        int result = mAudioManager.abandonAudioFocusRequest(currentRequest.mAudioFocusRequest);
        if (result == AUDIOFOCUS_REQUEST_GRANTED) {
            if (Slogf.isLoggable(TAG, Log.DEBUG)) {
                Slogf.d(TAG, "Abandoned focus for audio attributes " + audioAttributesWrapper
                        + "and zoneId " + zoneId);
            }
            mAudioControlWrapper.onAudioFocusChange(audioAttributesWrapper.getAudioAttributes()
                    .getSystemUsage(), zoneId, AUDIOFOCUS_LOSS);
        } else {
            Slogf.w(TAG, "Failed to abandon focus for audio attributes " + audioAttributesWrapper
                    + " and zoneId " + zoneId);
        }
    }

    private AudioAttributes generateAudioAttributes(
            AudioAttributesWrapper audioAttributesWrapper, int zoneId) {
        AudioAttributes.Builder builder =
                new AudioAttributes.Builder(audioAttributesWrapper.getAudioAttributes());
        Bundle bundle = new Bundle();
        bundle.putInt(CarAudioManager.AUDIOFOCUS_EXTRA_REQUEST_ZONE_ID, zoneId);
        builder.addBundle(bundle);

        return builder.build();
    }

    @GuardedBy("mLock")
    private AudioFocusRequest generateFocusRequestLocked(
            AudioAttributesWrapper audioAttributesWrapper,
            int zoneId, int focusGain) {
        AudioAttributes attributes = generateAudioAttributes(audioAttributesWrapper, zoneId);
        return new AudioFocusRequest.Builder(focusGain)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener((int focusChange) -> {
                    onAudioFocusChange(attributes.getSystemUsage(), zoneId, focusChange);
                })
                .build();
    }

    private void onAudioFocusChange(int usage, int zoneId, int focusChange) {
        AudioAttributesWrapper wrapper = CarAudioContext.getAudioAttributeWrapperFromUsage(usage);
        synchronized (mLock) {
            HalAudioFocusRequest currentRequest =
                    mHalFocusRequestsByZoneAndUsage.get(zoneId).get(wrapper);
            if (currentRequest != null) {
                if (focusChange == AUDIOFOCUS_LOSS) {
                    mHalFocusRequestsByZoneAndUsage.get(zoneId).remove(wrapper);
                } else {
                    currentRequest.mFocusStatus = focusChange;
                }
                mAudioControlWrapper.onAudioFocusChange(usage, zoneId, focusChange);
            }

        }
    }

    @GuardedBy("mLock")
    private void makeAudioFocusRequestLocked(
            AudioAttributesWrapper audioAttributesWrapper,
            int zoneId, int focusGain) {
        AudioFocusRequest audioFocusRequest =
                generateFocusRequestLocked(audioAttributesWrapper, zoneId, focusGain);

        int requestResult = mAudioManager.requestAudioFocus(audioFocusRequest);

        int resultingFocusGain = focusGain;

        if (requestResult == AUDIOFOCUS_REQUEST_GRANTED) {
            HalAudioFocusRequest halAudioFocusRequest =
                    new HalAudioFocusRequest(audioFocusRequest, focusGain);
            mHalFocusRequestsByZoneAndUsage.get(zoneId)
                    .put(audioAttributesWrapper, halAudioFocusRequest);
        } else if (requestResult == AUDIOFOCUS_REQUEST_FAILED) {
            resultingFocusGain = AUDIOFOCUS_LOSS;
        } else if (requestResult == AUDIOFOCUS_REQUEST_DELAYED) {
            Slogf.w(TAG, "Delayed result for request with audio attributes "
                    + audioAttributesWrapper + ", zoneId " + zoneId
                    + ", and focusGain " + focusGain);
            resultingFocusGain = AUDIOFOCUS_LOSS;
        }

        mAudioControlWrapper.onAudioFocusChange(
                audioAttributesWrapper.getAudioAttributes().getSystemUsage(),
                zoneId, resultingFocusGain);
    }

    private final class HalAudioFocusRequest {
        final AudioFocusRequest mAudioFocusRequest;

        int mFocusStatus;

        HalAudioFocusRequest(AudioFocusRequest audioFocusRequest, int focusStatus) {
            mAudioFocusRequest = audioFocusRequest;
            mFocusStatus = focusStatus;
        }

        @Override
        public String toString() {
            return new StringBuilder()
                    .append("Request: ")
                    .append("[Audio attributes: ")
                    .append(mAudioFocusRequest.getAudioAttributes())
                    .append(", Focus request: ")
                    .append(mAudioFocusRequest.getFocusGain())
                    .append("]")
                    .append(", Status: ")
                    .append(mFocusStatus)
                    .toString();
        }
    }
}
