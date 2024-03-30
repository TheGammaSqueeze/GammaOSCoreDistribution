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

import android.annotation.NonNull;
import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;
import android.uwb.UwbManager;

import androidx.annotation.IntRange;
import androidx.annotation.RequiresApi;

import com.google.uwb.support.base.RequiredParam;

/**
 * Defines parameters for CCC open operation
 *
 * <p>This is passed as a bundle to the service API {@link UwbManager#openRangingSession}.
 */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CccOpenRangingParams extends CccParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private static final String KEY_PROTOCOL_VERSION = "protocol_version";
    private static final String KEY_UWB_CONFIG = "uwb_config";
    private static final String KEY_PULSE_SHAPE_COMBO = "pulse_shape_combo";
    private static final String KEY_SESSION_ID = "session_id";
    private static final String KEY_RAN_MULTIPLIER = "ran_multiplier";
    private static final String KEY_CHANNEL = "channel";
    private static final String KEY_NUM_CHAPS_PER_SLOT = "num_chaps_per_slot";
    private static final String KEY_NUM_RESPONDER_NODES = "num_responder_nodes";
    private static final String KEY_NUM_SLOTS_PER_ROUND = "num_slots_per_round";
    private static final String KEY_SYNC_CODE_INDEX = "sync_code_index";
    private static final String KEY_HOPPING_CONFIG_MODE = "hopping_config_mode";
    private static final String KEY_HOPPING_SEQUENCE = "hopping_sequence";

    private final CccProtocolVersion mProtocolVersion;
    @UwbConfig private final int mUwbConfig;
    private final CccPulseShapeCombo mPulseShapeCombo;
    private final int mSessionId;
    private final int mRanMultiplier;
    @Channel private final int mChannel;
    private final int mNumChapsPerSlot;
    private final int mNumResponderNodes;
    private final int mNumSlotsPerRound;
    @SyncCodeIndex private final int mSyncCodeIndex;
    @HoppingConfigMode private final int mHoppingConfigMode;
    @HoppingSequence private final int mHoppingSequence;

    private CccOpenRangingParams(
            CccProtocolVersion protocolVersion,
            @UwbConfig int uwbConfig,
            CccPulseShapeCombo pulseShapeCombo,
            int sessionId,
            int ranMultiplier,
            @Channel int channel,
            int numChapsPerSlot,
            int numResponderNodes,
            int numSlotsPerRound,
            @SyncCodeIndex int syncCodeIndex,
            @HoppingConfigMode int hoppingConfigMode,
            @HoppingSequence int hoppingSequence) {
        mProtocolVersion = protocolVersion;
        mUwbConfig = uwbConfig;
        mPulseShapeCombo = pulseShapeCombo;
        mSessionId = sessionId;
        mRanMultiplier = ranMultiplier;
        mChannel = channel;
        mNumChapsPerSlot = numChapsPerSlot;
        mNumResponderNodes = numResponderNodes;
        mNumSlotsPerRound = numSlotsPerRound;
        mSyncCodeIndex = syncCodeIndex;
        mHoppingConfigMode = hoppingConfigMode;
        mHoppingSequence = hoppingSequence;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putString(KEY_PROTOCOL_VERSION, mProtocolVersion.toString());
        bundle.putInt(KEY_UWB_CONFIG, mUwbConfig);
        bundle.putString(KEY_PULSE_SHAPE_COMBO, mPulseShapeCombo.toString());
        bundle.putInt(KEY_SESSION_ID, mSessionId);
        bundle.putInt(KEY_RAN_MULTIPLIER, mRanMultiplier);
        bundle.putInt(KEY_CHANNEL, mChannel);
        bundle.putInt(KEY_NUM_CHAPS_PER_SLOT, mNumChapsPerSlot);
        bundle.putInt(KEY_NUM_RESPONDER_NODES, mNumResponderNodes);
        bundle.putInt(KEY_NUM_SLOTS_PER_ROUND, mNumSlotsPerRound);
        bundle.putInt(KEY_SYNC_CODE_INDEX, mSyncCodeIndex);
        bundle.putInt(KEY_HOPPING_CONFIG_MODE, mHoppingConfigMode);
        bundle.putInt(KEY_HOPPING_SEQUENCE, mHoppingSequence);
        return bundle;
    }

    public static CccOpenRangingParams fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseBundleVersion1(bundle);

            default:
                throw new IllegalArgumentException("unknown bundle version");
        }
    }

    private static CccOpenRangingParams parseBundleVersion1(PersistableBundle bundle) {
        return new Builder()
                .setProtocolVersion(
                        CccProtocolVersion.fromString(
                                checkNotNull(bundle.getString(KEY_PROTOCOL_VERSION))))
                .setUwbConfig(bundle.getInt(KEY_UWB_CONFIG))
                .setPulseShapeCombo(
                        CccPulseShapeCombo.fromString(
                                checkNotNull(bundle.getString(KEY_PULSE_SHAPE_COMBO))))
                .setSessionId(bundle.getInt(KEY_SESSION_ID))
                .setRanMultiplier(bundle.getInt(KEY_RAN_MULTIPLIER))
                .setChannel(bundle.getInt(KEY_CHANNEL))
                .setNumChapsPerSlot(bundle.getInt(KEY_NUM_CHAPS_PER_SLOT))
                .setNumResponderNodes(bundle.getInt(KEY_NUM_RESPONDER_NODES))
                .setNumSlotsPerRound(bundle.getInt(KEY_NUM_SLOTS_PER_ROUND))
                .setSyncCodeIndex(bundle.getInt(KEY_SYNC_CODE_INDEX))
                .setHoppingConfigMode(bundle.getInt(KEY_HOPPING_CONFIG_MODE))
                .setHoppingSequence(bundle.getInt(KEY_HOPPING_SEQUENCE))
                .build();
    }

    public CccProtocolVersion getProtocolVersion() {
        return mProtocolVersion;
    }

    @UwbConfig
    public int getUwbConfig() {
        return mUwbConfig;
    }

    public CccPulseShapeCombo getPulseShapeCombo() {
        return mPulseShapeCombo;
    }

    public int getSessionId() {
        return mSessionId;
    }

    @IntRange(from = 0, to = 255)
    public int getRanMultiplier() {
        return mRanMultiplier;
    }

    @Channel
    public int getChannel() {
        return mChannel;
    }

    public int getNumChapsPerSlot() {
        return mNumChapsPerSlot;
    }

    public int getNumResponderNodes() {
        return mNumResponderNodes;
    }

    public int getNumSlotsPerRound() {
        return mNumSlotsPerRound;
    }

    @SyncCodeIndex
    public int getSyncCodeIndex() {
        return mSyncCodeIndex;
    }

    @HoppingConfigMode
    public int getHoppingConfigMode() {
        return mHoppingConfigMode;
    }

    @HoppingSequence
    public int getHoppingSequence() {
        return mHoppingSequence;
    }

    /** Builder */
    public static final class Builder {
        private RequiredParam<CccProtocolVersion> mProtocolVersion = new RequiredParam<>();
        @UwbConfig private RequiredParam<Integer> mUwbConfig = new RequiredParam<>();
        private RequiredParam<CccPulseShapeCombo> mPulseShapeCombo = new RequiredParam<>();
        private RequiredParam<Integer> mSessionId = new RequiredParam<>();
        private RequiredParam<Integer> mRanMultiplier = new RequiredParam<>();
        @Channel private RequiredParam<Integer> mChannel = new RequiredParam<>();
        @ChapsPerSlot private RequiredParam<Integer> mNumChapsPerSlot = new RequiredParam<>();
        private RequiredParam<Integer> mNumResponderNodes = new RequiredParam<>();
        @SlotsPerRound private RequiredParam<Integer> mNumSlotsPerRound = new RequiredParam<>();
        @SyncCodeIndex private RequiredParam<Integer> mSyncCodeIndex = new RequiredParam<>();

        @HoppingConfigMode
        private RequiredParam<Integer> mHoppingConfigMode = new RequiredParam<>();

        @HoppingSequence private RequiredParam<Integer> mHoppingSequence = new RequiredParam<>();

        public Builder() {}

        public Builder(@NonNull Builder builder) {
            mProtocolVersion.set(builder.mProtocolVersion.get());
            mUwbConfig.set(builder.mUwbConfig.get());
            mPulseShapeCombo.set(builder.mPulseShapeCombo.get());
            mSessionId.set(builder.mSessionId.get());
            mRanMultiplier.set(builder.mRanMultiplier.get());
            mChannel.set(builder.mChannel.get());
            mNumChapsPerSlot.set(builder.mNumChapsPerSlot.get());
            mNumResponderNodes.set(builder.mNumResponderNodes.get());
            mNumSlotsPerRound.set(builder.mNumSlotsPerRound.get());
            mSyncCodeIndex.set(builder.mSyncCodeIndex.get());
            mHoppingConfigMode.set(builder.mHoppingConfigMode.get());
            mHoppingSequence.set(builder.mHoppingSequence.get());
        }

        public Builder setProtocolVersion(CccProtocolVersion version) {
            mProtocolVersion.set(version);
            return this;
        }

        public Builder setUwbConfig(@UwbConfig int uwbConfig) {
            mUwbConfig.set(uwbConfig);
            return this;
        }

        public Builder setPulseShapeCombo(CccPulseShapeCombo pulseShapeCombo) {
            mPulseShapeCombo.set(pulseShapeCombo);
            return this;
        }

        public Builder setSessionId(int sessionId) {
            mSessionId.set(sessionId);
            return this;
        }

        public Builder setRanMultiplier(int ranMultiplier) {
            mRanMultiplier.set(ranMultiplier);
            return this;
        }

        public Builder setChannel(@Channel int channel) {
            mChannel.set(channel);
            return this;
        }

        public Builder setNumChapsPerSlot(@ChapsPerSlot int numChapsPerSlot) {
            mNumChapsPerSlot.set(numChapsPerSlot);
            return this;
        }

        public Builder setNumResponderNodes(int numResponderNodes) {
            mNumResponderNodes.set(numResponderNodes);
            return this;
        }

        public Builder setNumSlotsPerRound(@SlotsPerRound int numSlotsPerRound) {
            mNumSlotsPerRound.set(numSlotsPerRound);
            return this;
        }

        public Builder setSyncCodeIndex(@SyncCodeIndex int syncCodeIndex) {
            mSyncCodeIndex.set(syncCodeIndex);
            return this;
        }

        public Builder setHoppingConfigMode(@HoppingConfigMode int hoppingConfigMode) {
            mHoppingConfigMode.set(hoppingConfigMode);
            return this;
        }

        public Builder setHoppingSequence(@HoppingSequence int hoppingSequence) {
            mHoppingSequence.set(hoppingSequence);
            return this;
        }

        public CccOpenRangingParams build() {
            return new CccOpenRangingParams(
                    mProtocolVersion.get(),
                    mUwbConfig.get(),
                    mPulseShapeCombo.get(),
                    mSessionId.get(),
                    mRanMultiplier.get(),
                    mChannel.get(),
                    mNumChapsPerSlot.get(),
                    mNumResponderNodes.get(),
                    mNumSlotsPerRound.get(),
                    mSyncCodeIndex.get(),
                    mHoppingConfigMode.get(),
                    mHoppingSequence.get());
        }
    }
}
