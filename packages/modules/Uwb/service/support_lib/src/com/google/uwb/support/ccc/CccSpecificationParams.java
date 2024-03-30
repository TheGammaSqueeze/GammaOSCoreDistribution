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

package com.google.uwb.support.ccc;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.uwb.UwbManager;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.uwb.support.base.RequiredParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Defines parameters for CCC capability reports
 *
 * <p>This is returned as a bundle from the service API {@link UwbManager#getSpecificationInfo}.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CccSpecificationParams extends CccParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private final List<CccProtocolVersion> mProtocolVersions;
    @UwbConfig private final List<Integer> mUwbConfigs;
    private final List<CccPulseShapeCombo> mPulseShapeCombos;
    private final int mRanMultiplier;
    @ChapsPerSlot private final List<Integer> mChapsPerSlot;
    @SyncCodeIndex private final List<Integer> mSyncCodes;
    @Channel private final List<Integer> mChannels;
    @HoppingConfigMode private final List<Integer> mHoppingConfigModes;
    @HoppingSequence private final List<Integer> mHoppingSequences;

    private static final String KEY_PROTOCOL_VERSIONS = "protocol_versions";
    private static final String KEY_UWB_CONFIGS = "uwb_configs";
    private static final String KEY_PULSE_SHAPE_COMBOS = "pulse_shape_combos";
    private static final String KEY_RAN_MULTIPLIER = "ran_multiplier";
    private static final String KEY_CHAPS_PER_SLOTS = "chaps_per_slots";
    private static final String KEY_SYNC_CODES = "sync_codes";
    private static final String KEY_CHANNELS = "channels";
    private static final String KEY_HOPPING_CONFIGS = "hopping_config_modes";
    private static final String KEY_HOPPING_SEQUENCES = "hopping_sequences";

    private CccSpecificationParams(
            List<CccProtocolVersion> protocolVersions,
            @UwbConfig List<Integer> uwbConfigs,
            List<CccPulseShapeCombo> pulseShapeCombos,
            int ranMultiplier,
            @ChapsPerSlot List<Integer> chapsPerSlot,
            @SyncCodeIndex List<Integer> syncCodes,
            @Channel List<Integer> channels,
            @HoppingConfigMode List<Integer> hoppingConfigModes,
            @HoppingSequence List<Integer> hoppingSequences) {
        mProtocolVersions = protocolVersions;
        mUwbConfigs = uwbConfigs;
        mPulseShapeCombos = pulseShapeCombos;
        mRanMultiplier = ranMultiplier;
        mChapsPerSlot = chapsPerSlot;
        mSyncCodes = syncCodes;
        mChannels = channels;
        mHoppingConfigModes = hoppingConfigModes;
        mHoppingSequences = hoppingSequences;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        String[] protocols = new String[mProtocolVersions.size()];
        for (int i = 0; i < protocols.length; i++) {
            protocols[i] = mProtocolVersions.get(i).toString();
        }
        String[] pulseShapeCombos = new String[mPulseShapeCombos.size()];
        for (int i = 0; i < pulseShapeCombos.length; i++) {
            pulseShapeCombos[i] = mPulseShapeCombos.get(i).toString();
        }
        bundle.putStringArray(KEY_PROTOCOL_VERSIONS, protocols);
        bundle.putIntArray(KEY_UWB_CONFIGS, toIntArray(mUwbConfigs));
        bundle.putStringArray(KEY_PULSE_SHAPE_COMBOS, pulseShapeCombos);
        bundle.putInt(KEY_RAN_MULTIPLIER, mRanMultiplier);
        bundle.putIntArray(KEY_CHAPS_PER_SLOTS, toIntArray(mChapsPerSlot));
        bundle.putIntArray(KEY_SYNC_CODES, toIntArray(mSyncCodes));
        bundle.putIntArray(KEY_CHANNELS, toIntArray(mChannels));
        bundle.putIntArray(KEY_HOPPING_CONFIGS, toIntArray(mHoppingConfigModes));
        bundle.putIntArray(KEY_HOPPING_SEQUENCES, toIntArray(mHoppingSequences));
        return bundle;
    }

    public static CccSpecificationParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static CccSpecificationParams parseVersion1(PersistableBundle bundle) {
        CccSpecificationParams.Builder builder = new CccSpecificationParams.Builder();
        String[] protocolStrings = checkNotNull(bundle.getStringArray(KEY_PROTOCOL_VERSIONS));
        for (String protocol : protocolStrings) {
            builder.addProtocolVersion(CccProtocolVersion.fromString(protocol));
        }

        for (int config : checkNotNull(bundle.getIntArray(KEY_UWB_CONFIGS))) {
            builder.addUwbConfig(config);
        }

        String[] pulseShapeComboStrings =
                checkNotNull(bundle.getStringArray(KEY_PULSE_SHAPE_COMBOS));
        for (String pulseShapeCombo : pulseShapeComboStrings) {
            builder.addPulseShapeCombo(CccPulseShapeCombo.fromString(pulseShapeCombo));
        }

        builder.setRanMultiplier(bundle.getInt(KEY_RAN_MULTIPLIER));

        for (int chapsPerSlot : checkNotNull(bundle.getIntArray(KEY_CHAPS_PER_SLOTS))) {
            builder.addChapsPerSlot(chapsPerSlot);
        }

        for (int syncCode : checkNotNull(bundle.getIntArray(KEY_SYNC_CODES))) {
            builder.addSyncCode(syncCode);
        }

        for (int channel : checkNotNull(bundle.getIntArray(KEY_CHANNELS))) {
            builder.addChannel(channel);
        }

        for (int hoppingConfig : checkNotNull(bundle.getIntArray(KEY_HOPPING_CONFIGS))) {
            builder.addHoppingConfigMode(hoppingConfig);
        }

        for (int hoppingSequence : checkNotNull(bundle.getIntArray(KEY_HOPPING_SEQUENCES))) {
            builder.addHoppingSequence(hoppingSequence);
        }

        return builder.build();
    }

    private int[] toIntArray(List<Integer> data) {
        int[] res = new int[data.size()];
        for (int i = 0; i < data.size(); i++) {
            res[i] = data.get(i);
        }
        return res;
    }

    public List<CccProtocolVersion> getProtocolVersions() {
        return mProtocolVersions;
    }

    @UwbConfig
    public List<Integer> getUwbConfigs() {
        return mUwbConfigs;
    }

    public List<CccPulseShapeCombo> getPulseShapeCombos() {
        return mPulseShapeCombos;
    }

    @IntRange(from = 0, to = 255)
    public int getRanMultiplier() {
        return mRanMultiplier;
    }

    @ChapsPerSlot
    public List<Integer> getChapsPerSlot() {
        return mChapsPerSlot;
    }

    @SyncCodeIndex
    public List<Integer> getSyncCodes() {
        return mSyncCodes;
    }

    @Channel
    public List<Integer> getChannels() {
        return mChannels;
    }

    @HoppingSequence
    public List<Integer> getHoppingSequences() {
        return mHoppingSequences;
    }

    @HoppingConfigMode
    public List<Integer> getHoppingConfigModes() {
        return mHoppingConfigModes;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other instanceof CccSpecificationParams) {
            CccSpecificationParams otherSpecificationParams = (CccSpecificationParams) other;
            return otherSpecificationParams.mProtocolVersions.equals(mProtocolVersions)
                && otherSpecificationParams.mPulseShapeCombos.equals(mPulseShapeCombos)
                && otherSpecificationParams.mUwbConfigs.equals(mUwbConfigs)
                && otherSpecificationParams.mRanMultiplier == mRanMultiplier
                && otherSpecificationParams.mChapsPerSlot.equals(mChapsPerSlot)
                && otherSpecificationParams.mSyncCodes.equals(mSyncCodes)
                && otherSpecificationParams.mChannels.equals(mChannels)
                && otherSpecificationParams.mHoppingConfigModes.equals(mHoppingConfigModes)
                && otherSpecificationParams.mHoppingSequences.equals(mHoppingSequences);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(
            new int[] {
                mProtocolVersions.hashCode(),
                mPulseShapeCombos.hashCode(),
                mUwbConfigs.hashCode(),
                mRanMultiplier,
                mChapsPerSlot.hashCode(),
                mSyncCodes.hashCode(),
                mChannels.hashCode(),
                mHoppingConfigModes.hashCode(),
                mHoppingSequences.hashCode()
            });
    }

    /** Builder */
    public static class Builder {
        private List<CccProtocolVersion> mProtocolVersions = new ArrayList<>();
        @UwbConfig private List<Integer> mUwbConfigs = new ArrayList<>();
        private List<CccPulseShapeCombo> mPulseShapeCombos = new ArrayList<>();
        private RequiredParam<Integer> mRanMultiplier = new RequiredParam<>();
        @ChapsPerSlot private List<Integer> mChapsPerSlot = new ArrayList<>();
        @SyncCodeIndex private List<Integer> mSyncCodes = new ArrayList<>();
        @Channel private List<Integer> mChannels = new ArrayList<>();
        @HoppingSequence private List<Integer> mHoppingSequences = new ArrayList<>();
        @HoppingConfigMode private List<Integer> mHoppingConfigModes = new ArrayList<>();

        public Builder addProtocolVersion(@NonNull CccProtocolVersion version) {
            mProtocolVersions.add(version);
            return this;
        }

        public Builder addUwbConfig(@UwbConfig int uwbConfig) {
            mUwbConfigs.add(uwbConfig);
            return this;
        }

        public Builder addPulseShapeCombo(CccPulseShapeCombo pulseShapeCombo) {
            mPulseShapeCombos.add(pulseShapeCombo);
            return this;
        }

        public Builder setRanMultiplier(int ranMultiplier) {
            if (ranMultiplier < 0 || ranMultiplier > 255) {
                throw new IllegalArgumentException("Invalid RAN Multiplier");
            }
            mRanMultiplier.set(ranMultiplier);
            return this;
        }

        public Builder addChapsPerSlot(@ChapsPerSlot int chapsPerSlot) {
            mChapsPerSlot.add(chapsPerSlot);
            return this;
        }

        public Builder addSyncCode(@SyncCodeIndex int syncCode) {
            mSyncCodes.add(syncCode);
            return this;
        }

        public Builder addChannel(@Channel int channel) {
            mChannels.add(channel);
            return this;
        }

        public Builder addHoppingConfigMode(@HoppingConfigMode int hoppingConfigMode) {
            mHoppingConfigModes.add(hoppingConfigMode);
            return this;
        }

        public Builder addHoppingSequence(@HoppingSequence int hoppingSequence) {
            mHoppingSequences.add(hoppingSequence);
            return this;
        }

        public CccSpecificationParams build() {
            if (mProtocolVersions.size() == 0) {
                throw new IllegalStateException("No protocol versions set");
            }

            if (mUwbConfigs.size() == 0) {
                throw new IllegalStateException("No UWB Configs set");
            }

            if (mPulseShapeCombos.size() == 0) {
                throw new IllegalStateException("No Pulse Shape Combos set");
            }

            if (mChapsPerSlot.size() == 0) {
                throw new IllegalStateException("No Slot Durations set");
            }

            if (mSyncCodes.size() == 0) {
                throw new IllegalStateException("No Sync Codes set");
            }

            if (mChannels.size() == 0) {
                throw new IllegalStateException("No channels set");
            }

            if (mHoppingConfigModes.size() == 0) {
                throw new IllegalStateException("No hopping config modes set");
            }

            if (mHoppingSequences.size() == 0) {
                throw new IllegalStateException("No hopping sequences set");
            }

            return new CccSpecificationParams(
                    mProtocolVersions,
                    mUwbConfigs,
                    mPulseShapeCombos,
                    mRanMultiplier.get(),
                    mChapsPerSlot,
                    mSyncCodes,
                    mChannels,
                    mHoppingConfigModes,
                    mHoppingSequences);
        }
    }
}
