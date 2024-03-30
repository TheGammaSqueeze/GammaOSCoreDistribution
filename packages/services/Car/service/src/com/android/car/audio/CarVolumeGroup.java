/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.car.audio.hal.HalAudioGainCallback.reasonToString;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.builtin.util.Slogf;
import android.car.media.CarAudioManager;
import android.car.media.CarVolumeGroupInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioContext.AudioContext;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A class encapsulates a volume group in car.
 *
 * Volume in a car is controlled by group. A group holds one or more car audio contexts.
 * Call {@link CarAudioManager#getVolumeGroupCount()} to get the count of {@link CarVolumeGroup}
 * supported in a car.
 */
/* package */ final class CarVolumeGroup {

    public static final int UNINITIALIZED = -1;

    private final boolean mUseCarVolumeGroupMute;
    private final boolean mHasCriticalAudioContexts;
    private final CarAudioSettings mSettingsManager;
    private final int mDefaultGain;
    private final int mId;
    private final int mMaxGain;
    private final int mMinGain;
    private final int mStepSize;
    private final int mZoneId;
    private final SparseArray<String> mContextToAddress;
    private final ArrayMap<String, CarAudioDeviceInfo> mAddressToCarAudioDeviceInfo;

    private final Object mLock = new Object();
    private final CarAudioContext mCarAudioContext;

    @GuardedBy("mLock")
    private int mStoredGainIndex;

    @GuardedBy("mLock")
    private int mCurrentGainIndex = UNINITIALIZED;

    @GuardedBy("mLock")
    private boolean mIsMuted;
    @GuardedBy("mLock")
    private @UserIdInt int mUserId = UserHandle.CURRENT.getIdentifier();

    /**
     * Attenuated gain is set to {@see CarAudioDeviceInfo#UNINITIALIZED} till attenuation explicitly
     * reported by {@see HalAudioGainCallback#onAudioDeviceGainsChanged} for one or more {@see
     * android.hardware.automotive.audiocontrol#Reasons}. When the reason is cleared, it returns
     * back to {@see CarAudioDeviceInfo#UNINITIALIZED}.
     */
    @GuardedBy("mLock")
    private int mAttenuatedGainIndex = UNINITIALIZED;

    /**
     * Limitation gain is set to max gain value till limitation explicitly reported by {@see
     * HalAudioGainCallback#onAudioDeviceGainsChanged} for one or more {@see
     * android.hardware.automotive.audiocontrol#Reasons}. When the reason is cleared, it returns
     * back to max.
     */
    @GuardedBy("mLock")
    private int mLimitedGainIndex;

    /**
     * Blocked gain is set to {@see CarAudioDeviceInfo#UNINITIALIZED} till blocking case explicitly
     * reported by {@see HalAudioGainCallback#onAudioDeviceGainsChanged} for one or more {@see
     * android.hardware.automotive.audiocontrol#Reasons}. When the reason is cleared, it returns
     * back to {@see CarAudioDeviceInfo#UNINITIALIZED}.
     */
    @GuardedBy("mLock")
    private int mBlockedGainIndex = UNINITIALIZED;

    /**
     * Reasons list currently reported for this port by {@see
     * HalAudioGainCallback#onAudioDeviceGainsChanged}.
     */
    private List<Integer> mReasons = new ArrayList<>();

    private CarVolumeGroup(CarAudioContext carAudioContext, CarAudioSettings settingsManager,
            SparseArray<String> contextToAddress, ArrayMap<String,
            CarAudioDeviceInfo> addressToCarAudioDeviceInfo, int zoneId, int id, int stepSize,
            int defaultGain, int minGain, int maxGain, boolean useCarVolumeGroupMute) {

        mSettingsManager = settingsManager;
        mContextToAddress = contextToAddress;
        mAddressToCarAudioDeviceInfo = addressToCarAudioDeviceInfo;
        mCarAudioContext = carAudioContext;
        mZoneId = zoneId;
        mId = id;
        mStepSize = stepSize;
        mDefaultGain = defaultGain;
        mMinGain = minGain;
        mMaxGain = maxGain;
        mLimitedGainIndex = getIndexForGain(mMaxGain);
        mUseCarVolumeGroupMute = useCarVolumeGroupMute;
        List<AudioAttributes> volumeAttributes = new ArrayList<>();
        for (int index = 0; index <  contextToAddress.size(); index++) {
            int context = contextToAddress.keyAt(index);
            List<AudioAttributes> audioAttributes =
                    Arrays.asList(mCarAudioContext.getAudioAttributesForContext(context));
            volumeAttributes.addAll(audioAttributes);
        }

        mHasCriticalAudioContexts = containsCriticalAttributes(volumeAttributes);
    }

    void init() {
        synchronized (mLock) {
            mStoredGainIndex = mSettingsManager.getStoredVolumeGainIndexForUser(
                    mUserId, mZoneId, mId);
            updateCurrentGainIndexLocked();
        }
    }

    @GuardedBy("mLock")
    private void setBlockedLocked(int blockedIndex) {
        mBlockedGainIndex = blockedIndex;
    }

    @GuardedBy("mLock")
    private void resetBlockedLocked() {
        setBlockedLocked(UNINITIALIZED);
    }

    @GuardedBy("mLock")
    private boolean isBlockedLocked() {
        return mBlockedGainIndex != UNINITIALIZED;
    }

    @GuardedBy("mLock")
    private void setLimitLocked(int limitIndex) {
        mLimitedGainIndex = limitIndex;
    }

    @GuardedBy("mLock")
    private void resetLimitLocked() {
        setLimitLocked(getIndexForGain(mMaxGain));
    }

    @GuardedBy("mLock")
    private boolean isLimitedLocked() {
        return mLimitedGainIndex != getIndexForGain(mMaxGain);
    }

    @GuardedBy("mLock")
    private boolean isOverLimitLocked() {
        return isOverLimitLocked(mCurrentGainIndex);
    }

    @GuardedBy("mLock")
    private boolean isOverLimitLocked(int index) {
        return isLimitedLocked() && (index > mLimitedGainIndex);
    }

    @GuardedBy("mLock")
    private void setAttenuatedGainLocked(int attenuatedGainIndex) {
        mAttenuatedGainIndex = attenuatedGainIndex;
    }

    @GuardedBy("mLock")
    private void resetAttenuationLocked() {
        setAttenuatedGainLocked(UNINITIALIZED);
    }

    @GuardedBy("mLock")
    private boolean isAttenuatedLocked() {
        return mAttenuatedGainIndex != UNINITIALIZED;
    }

    void setBlocked(int blockedIndex) {
        synchronized (mLock) {
            setBlockedLocked(blockedIndex);
        }
    }

    void resetBlocked() {
        synchronized (mLock) {
            resetBlockedLocked();
        }
    }

    boolean isBlocked() {
        synchronized (mLock) {
            return isBlockedLocked();
        }
    }

    void setLimit(int limitIndex) {
        synchronized (mLock) {
            setLimitLocked(limitIndex);
        }
    }

    void resetLimit() {
        synchronized (mLock) {
            resetLimitLocked();
        }
    }

    boolean isLimited() {
        synchronized (mLock) {
            return isLimitedLocked();
        }
    }

    boolean isOverLimit() {
        synchronized (mLock) {
            return isOverLimitLocked();
        }
    }

    void setAttenuatedGain(int attenuatedGainIndex) {
        synchronized (mLock) {
            setAttenuatedGainLocked(attenuatedGainIndex);
        }
    }

    void resetAttenuation() {
        synchronized (mLock) {
            resetAttenuationLocked();
        }
    }

    boolean isAttenuated() {
        synchronized (mLock) {
            return isAttenuatedLocked();
        }
    }

    @Nullable
    CarAudioDeviceInfo getCarAudioDeviceInfoForAddress(String address) {
        return mAddressToCarAudioDeviceInfo.get(address);
    }

    @AudioContext
    int[] getContexts() {
        final int[] carAudioContexts = new int[mContextToAddress.size()];
        for (int i = 0; i < carAudioContexts.length; i++) {
            carAudioContexts[i] = mContextToAddress.keyAt(i);
        }
        return carAudioContexts;
    }

    /**
     * Returns the devices address for the given context
     * or {@code null} if the context does not exist in the volume group
     */
    @Nullable
    String getAddressForContext(int audioContext) {
        return mContextToAddress.get(audioContext);
    }

    /**
     * Returns the audio devices for the given context
     * or {@code null} if the context does not exist in the volume group
     */
    @Nullable
    AudioDeviceInfo getAudioDeviceForContext(int audioContext) {
        String address = getAddressForContext(audioContext);
        if (address == null) {
            return null;
        }

        CarAudioDeviceInfo info = mAddressToCarAudioDeviceInfo.get(address);
        if (info == null) {
            return null;
        }

        return info.getAudioDeviceInfo();
    }

    @AudioContext
    List<Integer> getContextsForAddress(@NonNull String address) {
        List<Integer> carAudioContexts = new ArrayList<>();
        for (int i = 0; i < mContextToAddress.size(); i++) {
            String value = mContextToAddress.valueAt(i);
            if (address.equals(value)) {
                carAudioContexts.add(mContextToAddress.keyAt(i));
            }
        }
        return carAudioContexts;
    }

    List<String> getAddresses() {
        return new ArrayList<>(mAddressToCarAudioDeviceInfo.keySet());
    }

    int getMaxGainIndex() {
        synchronized (mLock) {
            return getIndexForGain(mMaxGain);
        }
    }

    int getMinGainIndex() {
        synchronized (mLock) {
            return getIndexForGain(mMinGain);
        }
    }

    int getCurrentGainIndex() {
        synchronized (mLock) {
            if (mIsMuted) {
                return getIndexForGain(mMinGain);
            }
            if (isBlockedLocked()) {
                return mBlockedGainIndex;
            }
            if (isAttenuatedLocked()) {
                // Need to figure out if attenuation shall be hidden to end user
                // as while ducked from IAudioControl
                // Also, keep unchanged current index / use it as a cache of previous value
                //
                // TODO(b/) clarify in case of volume adjustment if the reference index is the
                // ducked index or the current index. Taking current may lead to gap of index > 1.
                return mAttenuatedGainIndex;
            }
            if (isOverLimitLocked()) {
                return mLimitedGainIndex;
            }
            return getCurrentGainIndexLocked();
        }
    }

    @GuardedBy("mLock")
    private int getCurrentGainIndexLocked() {
        return mCurrentGainIndex;
    }

    /**
     * Sets the gain on this group, gain will be set on all devices within volume group.
     */
    void setCurrentGainIndex(int gainIndex) {
        Preconditions.checkArgument(isValidGainIndex(gainIndex),
                "Gain out of range (%d:%d) index %d", mMinGain, mMaxGain, gainIndex);
        synchronized (mLock) {
            if (isBlockedLocked()) {
                // prevent any volume change while {@link IAudioGainCallback} reported block event.
                // TODO(b/) callback mecanism to inform HMI/User of failure and reason why if needed
                return;
            }
            if (isOverLimitLocked(gainIndex)) {
                // TODO(b/) callback to inform if over limit index and why if needed.
                gainIndex = mLimitedGainIndex;
            }
            if (isAttenuatedLocked()) {
                resetAttenuationLocked();
            }
            if (mIsMuted) {
                setMuteLocked(false);
            }
            // In case of attenuation/Limitation, requested index is now the new reference for
            // cached current index.
            mCurrentGainIndex = gainIndex;

            setCurrentGainIndexLocked(mCurrentGainIndex);
        }
    }

    @GuardedBy("mLock")
    private void setCurrentGainIndexLocked(int gainIndex) {
        int gainInMillibels = getGainForIndex(gainIndex);
        for (int index = 0; index < mAddressToCarAudioDeviceInfo.size(); index++) {
            CarAudioDeviceInfo info = mAddressToCarAudioDeviceInfo.valueAt(index);
            info.setCurrentGain(gainInMillibels);
        }
        storeGainIndexForUserLocked(gainIndex, mUserId);
    }

    boolean hasCriticalAudioContexts() {
        return mHasCriticalAudioContexts;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    public String toString() {
        synchronized (mLock) {
            return "CarVolumeGroup id: " + mId
                    + " currentGainIndex: " + mCurrentGainIndex
                    + " contexts: " + Arrays.toString(getContexts())
                    + " addresses: " + String.join(", ", getAddresses());
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.printf("CarVolumeGroup(%d)\n", mId);
            writer.increaseIndent();
            writer.printf("Zone Id(%b)\n", mZoneId);
            writer.printf("Is Muted(%b)\n", mIsMuted);
            writer.printf("UserId(%d)\n", mUserId);
            writer.printf("Persist Volume Group Mute(%b)\n",
                    mSettingsManager.isPersistVolumeGroupMuteEnabled(mUserId));
            writer.printf("Step size: %d\n", mStepSize);
            writer.printf("Gain values (min / max / default/ current): %d %d %d %d\n", mMinGain,
                    mMaxGain, mDefaultGain, getGainForIndex(mCurrentGainIndex));
            writer.printf("Gain indexes (min / max / default / current): %d %d %d %d\n",
                    getMinGainIndex(), getMaxGainIndex(), getDefaultGainIndex(), mCurrentGainIndex);
            for (int i = 0; i < mContextToAddress.size(); i++) {
                writer.printf("Context: %s -> Address: %s\n",
                        mCarAudioContext.toString(mContextToAddress.keyAt(i)),
                        mContextToAddress.valueAt(i));
            }
            mAddressToCarAudioDeviceInfo.keySet().stream()
                    .map(mAddressToCarAudioDeviceInfo::get)
                    .forEach((info -> info.dump(writer)));
            writer.printf("Reported reasons:\n");
            writer.increaseIndent();
            for (int index = 0; index < mReasons.size(); index++) {
                int reason = mReasons.get(index);
                writer.printf("%s\n", reasonToString(reason));
            }
            writer.decreaseIndent();
            writer.printf("Gain infos:\n");
            writer.increaseIndent();
            writer.printf(
                    "Blocked: %b%s\n",
                    isBlockedLocked(),
                    (isBlockedLocked() ? " (at: " + mBlockedGainIndex + ")" : ""));
            writer.printf(
                    "Limited: %b%s\n",
                    isLimitedLocked(),
                    (isLimitedLocked() ? " (at: " + mLimitedGainIndex + ")" : ""));
            writer.printf(
                    "Attenuated: %b%s\n",
                    isAttenuatedLocked(),
                    (isAttenuatedLocked() ? " (at: " + mAttenuatedGainIndex + ")" : ""));
            writer.decreaseIndent();
            // Empty line for comfortable reading
            writer.println();
            writer.decreaseIndent();
        }
    }

    void loadVolumesSettingsForUser(@UserIdInt int userId) {
        synchronized (mLock) {
            //Update the volume for the new user
            updateUserIdLocked(userId);
            //Update the current gain index
            updateCurrentGainIndexLocked();
            setCurrentGainIndexLocked(getCurrentGainIndexLocked());
            //Reset devices with current gain index
            updateGroupMuteLocked();
        }
    }

    void setMute(boolean mute) {
        synchronized (mLock) {
            setMuteLocked(mute);
        }
    }

    @GuardedBy("mLock")
    private void setMuteLocked(boolean mute) {
        mIsMuted = mute;
        if (mSettingsManager.isPersistVolumeGroupMuteEnabled(mUserId)) {
            mSettingsManager.storeVolumeGroupMuteForUser(mUserId, mZoneId, mId, mute);
        }
    }

    boolean isMuted() {
        synchronized (mLock) {
            return mIsMuted;
        }
    }

    private static boolean containsCriticalAttributes(List<AudioAttributes> volumeAttributes) {
        for (int index = 0; index < volumeAttributes.size(); index++) {
            if (CarAudioContext.isCriticalAudioAudioAttribute(volumeAttributes.get(index))) {
                return true;
            }
        }
        return false;
    }

    @GuardedBy("mLock")
    private void updateUserIdLocked(@UserIdInt int userId) {
        mUserId = userId;
        mStoredGainIndex = getCurrentGainIndexForUserLocked();
    }

    @GuardedBy("mLock")
    private int getCurrentGainIndexForUserLocked() {
        int gainIndexForUser = mSettingsManager.getStoredVolumeGainIndexForUser(mUserId, mZoneId,
                mId);
        Slogf.i(CarLog.TAG_AUDIO, "updateUserId userId " + mUserId
                + " gainIndexForUser " + gainIndexForUser);
        return gainIndexForUser;
    }

    /**
     * Update the current gain index based on the stored gain index
     */
    @GuardedBy("mLock")
    private void updateCurrentGainIndexLocked() {
        if (isValidGainIndex(mStoredGainIndex)) {
            mCurrentGainIndex = mStoredGainIndex;
        } else {
            mCurrentGainIndex = getIndexForGain(mDefaultGain);
        }
    }

    private boolean isValidGainIndex(int gainIndex) {
        return gainIndex >= getIndexForGain(mMinGain)
                && gainIndex <= getIndexForGain(mMaxGain);
    }

    private int getDefaultGainIndex() {
        synchronized (mLock) {
            return getIndexForGain(mDefaultGain);
        }
    }

    @GuardedBy("mLock")
    private void storeGainIndexForUserLocked(int gainIndex, @UserIdInt int userId) {
        mSettingsManager.storeVolumeGainIndexForUser(userId,
                mZoneId, mId, gainIndex);
    }

    private int getGainForIndex(int gainIndex) {
        return mMinGain + gainIndex * mStepSize;
    }

    private int getIndexForGain(int gainInMillibel) {
        return (gainInMillibel - mMinGain) / mStepSize;
    }

    @GuardedBy("mLock")
    private void updateGroupMuteLocked() {
        if (!mUseCarVolumeGroupMute) {
            return;
        }
        if (!mSettingsManager.isPersistVolumeGroupMuteEnabled(mUserId)) {
            mIsMuted = false;
            return;
        }
        mIsMuted = mSettingsManager.getVolumeGroupMuteForUser(mUserId, mZoneId, mId);
    }

    void onAudioGainChanged(List<Integer> halReasons, CarAudioGainConfigInfo gain) {
        if (getCarAudioDeviceInfoForAddress(gain.getDeviceAddress()) == null) {
            Slogf.e(CarLog.TAG_AUDIO,
                    "onAudioGainChanged no port found for address %s on group %d",
                    gain.getDeviceAddress(),
                    mId);
            return;
        }
        synchronized (mLock) {
            mReasons = new ArrayList<>(halReasons);
            int halIndex = gain.getVolumeIndex();
            if (CarAudioGainMonitor.shouldBlockVolumeRequest(halReasons)) {
                setBlockedLocked(halIndex);
            } else {
                resetBlockedLocked();
            }
            if (CarAudioGainMonitor.shouldLimitVolume(halReasons)) {
                setLimitLocked(halIndex);
            } else {
                resetLimitLocked();
            }
            if (CarAudioGainMonitor.shouldDuckGain(halReasons)) {
                setAttenuatedGainLocked(halIndex);
            } else {
                resetAttenuationLocked();
            }
            int indexToBroadCast = mCurrentGainIndex;
            if (isBlockedLocked()) {
                indexToBroadCast = mBlockedGainIndex;
            } else if (isAttenuatedLocked()) {
                indexToBroadCast = mAttenuatedGainIndex;
            } else if (isOverLimitLocked()) {
                // TODO(b/) callback to inform if over limit index and why if needed.
                indexToBroadCast = mLimitedGainIndex;
            }
            // Blocked/Attenuated index shall have been already apply by Audio HAL on HW.
            // However, keep in sync & broadcast to all ports this volume group deals with.
            //
            // Do not update current gain cache, keep it for restoring rather using reported index
            // when the event is cleared.
            setCurrentGainIndexLocked(indexToBroadCast);
        }
    }

    CarVolumeGroupInfo getCarVolumeGroupInfo() {
        int gainIndex;
        boolean isMuted;
        boolean isBlocked;
        boolean isAttenuated;
        synchronized (mLock) {
            gainIndex = mCurrentGainIndex;
            isMuted = mIsMuted;
            isBlocked = isBlockedLocked();
            isAttenuated = isAttenuatedLocked();
        }

        return new CarVolumeGroupInfo.Builder("group id " + mId, mZoneId, mId)
                .setVolumeGainIndex(gainIndex).setMaxVolumeGainIndex(getMaxGainIndex())
                .setMinVolumeGainIndex(getMinGainIndex()).setMuted(isMuted).setBlocked(isBlocked)
                .setAttenuated(isAttenuated).build();
    }

    List<AudioAttributes> getAudioAttributes() {
        List<AudioAttributes> audioAttributes = new ArrayList<>();
        for (int index = 0; index < mContextToAddress.size(); index++) {
            int context = mContextToAddress.keyAt(index);
            AudioAttributes[] contextAttributes =
                    mCarAudioContext.getAudioAttributesForContext(context);
            for (int attrIndex = 0; attrIndex < contextAttributes.length; attrIndex++) {
                audioAttributes.add(contextAttributes[attrIndex]);
            }
        }

        return audioAttributes;
    }

    static final class Builder {
        private static final int UNSET_STEP_SIZE = -1;

        private final int mId;
        private final int mZoneId;
        private final boolean mUseCarVolumeGroupMute;
        private final CarAudioSettings mCarAudioSettings;
        private final SparseArray<String> mContextToAddress = new SparseArray<>();
        private final CarAudioContext mCarAudioContext;
        private final ArrayMap<String, CarAudioDeviceInfo> mAddressToCarAudioDeviceInfo =
                new ArrayMap<>();

        @VisibleForTesting
        int mStepSize = UNSET_STEP_SIZE;
        @VisibleForTesting
        int mDefaultGain = Integer.MIN_VALUE;
        @VisibleForTesting
        int mMaxGain = Integer.MIN_VALUE;
        @VisibleForTesting
        int mMinGain = Integer.MAX_VALUE;

        Builder(CarAudioSettings carAudioSettings, CarAudioContext carAudioContext,
                int zoneId, int id, boolean useCarVolumeGroupMute) {
            mCarAudioSettings = Objects.requireNonNull(carAudioSettings,
                    "Car audio settings can not be null");
            mCarAudioContext = Objects.requireNonNull(carAudioContext,
                    "Car audio context can not be null");
            mZoneId = zoneId;
            mId = id;
            mUseCarVolumeGroupMute = useCarVolumeGroupMute;
        }

        Builder setDeviceInfoForContext(int carAudioContextId, CarAudioDeviceInfo info) {
            Preconditions.checkArgument(mContextToAddress.get(carAudioContextId) == null,
                    "Context %s has already been set to %s",
                    mCarAudioContext.toString(carAudioContextId),
                    mContextToAddress.get(carAudioContextId));

            if (mAddressToCarAudioDeviceInfo.isEmpty()) {
                mStepSize = info.getStepValue();
            } else {
                Preconditions.checkArgument(
                        info.getStepValue() == mStepSize,
                        "Gain controls within one group must have same step value");
            }

            mAddressToCarAudioDeviceInfo.put(info.getAddress(), info);
            mContextToAddress.put(carAudioContextId, info.getAddress());

            if (info.getDefaultGain() > mDefaultGain) {
                // We're arbitrarily selecting the highest
                // device default gain as the group's default.
                mDefaultGain = info.getDefaultGain();
            }
            if (info.getMaxGain() > mMaxGain) {
                mMaxGain = info.getMaxGain();
            }
            if (info.getMinGain() < mMinGain) {
                mMinGain = info.getMinGain();
            }

            return this;
        }

        CarVolumeGroup build() {
            Preconditions.checkArgument(mStepSize != UNSET_STEP_SIZE,
                    "setDeviceInfoForContext has to be called at least once before building");
            CarVolumeGroup group = new CarVolumeGroup(mCarAudioContext, mCarAudioSettings,
                    mContextToAddress, mAddressToCarAudioDeviceInfo, mZoneId, mId, mStepSize,
                    mDefaultGain, mMinGain, mMaxGain, mUseCarVolumeGroupMute);
            group.init();
            return group;
        }
    }
}
