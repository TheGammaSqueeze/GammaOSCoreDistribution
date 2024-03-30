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

package android.car.oem;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.annotation.ApiRequirements;
import android.car.media.CarVolumeGroupInfo;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class to encapsulate the audio focus evaluation to the OEM audio service
 *
 * @hide
 */
@SystemApi
@ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
        minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
public final class OemCarAudioFocusEvaluationRequest implements Parcelable {

    private @Nullable final AudioFocusEntry mAudioFocusRequest;
    private @NonNull final List<CarVolumeGroupInfo>  mMutedVolumeGroups;
    private @NonNull final List<AudioFocusEntry> mFocusHolders;
    private @NonNull final List<AudioFocusEntry> mFocusLosers;
    private final int mAudioZoneId;

    /**
     * @hide
     */
    @VisibleForTesting
    public OemCarAudioFocusEvaluationRequest(Parcel in) {
        byte flg = in.readByte();
        mAudioFocusRequest = (flg & Builder.FOCUS_REQUEST_FIELDS_SET) == 0
                ? null : AudioFocusEntry.CREATOR.createFromParcel(in);
        mMutedVolumeGroups = new ArrayList<>();
        in.readParcelableList(mMutedVolumeGroups, CarVolumeGroupInfo.class.getClassLoader());
        mFocusHolders = new ArrayList<>();
        in.readParcelableList(mFocusHolders, AudioFocusEntry.class.getClassLoader());
        mFocusLosers = new ArrayList<>();
        in.readParcelableList(mFocusLosers, AudioFocusEntry.class.getClassLoader());
        mAudioZoneId = in.readInt();
    }

    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @NonNull
    public static final Creator<OemCarAudioFocusEvaluationRequest> CREATOR =
            new Creator<>() {
                @Override
                public OemCarAudioFocusEvaluationRequest createFromParcel(Parcel in) {
                    return new OemCarAudioFocusEvaluationRequest(in);
                }

                @Override
                public OemCarAudioFocusEvaluationRequest[] newArray(int size) {
                    return new OemCarAudioFocusEvaluationRequest[size];
                }
            };


    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int describeContents() {
        return 0;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        byte flg = 0;
        if (mAudioFocusRequest != null) {
            flg = (byte) (flg | Builder.FOCUS_REQUEST_FIELDS_SET);
        }
        dest.writeByte(flg);
        if (mAudioFocusRequest != null) {
            mAudioFocusRequest.writeToParcel(dest, flags);
        }
        dest.writeParcelableList(mMutedVolumeGroups, flags);
        dest.writeParcelableList(mFocusHolders, flags);
        dest.writeParcelableList(mFocusLosers, flags);
        dest.writeInt(mAudioZoneId);
    }

    /**
     * Returns the audio zone id for the request
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int getAudioZoneId() {
        return mAudioZoneId;
    }

    /**
     * Returns the current audio focus info to evaluate,
     * in cases where the audio focus info is null
     * the request is to re-evaluate current focus holder and losers.
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public @Nullable AudioFocusEntry getAudioFocusRequest() {
        return mAudioFocusRequest;
    }

    /**
     * Returns the currently muted volume groups
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public @NonNull List<CarVolumeGroupInfo> getMutedVolumeGroups() {
        return mMutedVolumeGroups;
    }

    /**
     * Returns the current focus holder
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public @NonNull List<AudioFocusEntry> getFocusHolders() {
        return mFocusHolders;
    }

    /**
     * Returns the current focus losers (.i.e focus request that have transiently lost focus)
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public @NonNull List<AudioFocusEntry> getFocusLosers() {
        return mFocusLosers;
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof OemCarAudioFocusEvaluationRequest)) {
            return false;
        }

        OemCarAudioFocusEvaluationRequest that = (OemCarAudioFocusEvaluationRequest) o;

        return safeEquals(mAudioFocusRequest, that.mAudioFocusRequest)
                && mFocusHolders.equals(that.mFocusHolders)
                && mFocusLosers.equals(that.mFocusLosers)
                && mMutedVolumeGroups.equals(that.mMutedVolumeGroups)
                && mAudioZoneId == that.mAudioZoneId;
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int hashCode() {
        return Objects.hash(mAudioFocusRequest, mFocusHolders, mFocusLosers, mMutedVolumeGroups,
                mAudioZoneId);
    }

    /**
     * @hide
     */
    @VisibleForTesting
    public OemCarAudioFocusEvaluationRequest(
            @Nullable AudioFocusEntry audioFocusEntry,
            @NonNull List<CarVolumeGroupInfo> mutedVolumeGroups,
            @NonNull List<AudioFocusEntry> focusHolders,
            @NonNull List<AudioFocusEntry> focusLosers,
            int audioZoneId) {
        this.mAudioFocusRequest = audioFocusEntry;
        Preconditions.checkArgument(mutedVolumeGroups != null,
                "Muted volume groups can not be null");
        Preconditions.checkArgument(focusHolders != null,
                "Focus holders can not be null");
        Preconditions.checkArgument(focusLosers != null,
                "Focus losers can not be null");
        this.mMutedVolumeGroups = mutedVolumeGroups;
        this.mFocusHolders = focusHolders;
        this.mFocusLosers = focusLosers;
        this.mAudioZoneId = audioZoneId;
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public String toString() {
        return new StringBuilder().append("OemCarAudioFocusEvaluationRequest {audioZoneId = ")
                .append(mAudioZoneId).append(", audioFocusInfo = ").append(mAudioFocusRequest)
                .append(", mutedVolumeGroups = ").append(mMutedVolumeGroups)
                .append(", focusHolders = ").append(mFocusHolders)
                .append(", focusLosers = ").append(mFocusLosers)
                .append(" }").toString();
    }

    /**
     * A builder for {@link OemCarAudioFocusEvaluationRequest}
     */
    @SuppressWarnings("WeakerAccess")
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static final class Builder {

        private static final int FOCUS_REQUEST_FIELDS_SET = 0x1;
        private static final int MUTED_VOLUME_GROUPS_FIELDS_SET = 0x2;
        private static final int FOCUS_HOLDERS_FIELDS_SET = 0x4;
        private static final int FOCUS_LOSERS_FIELDS_SET = 0x8;
        private static final int ZONE_ID_FIELDS_SET = 0x10;
        private static final int BUILDER_USED_FIELDS_SET = 0x20;

        private int mAudioZoneId;
        private @Nullable AudioFocusEntry mAudioFocusRequest;
        private @NonNull List<CarVolumeGroupInfo> mMutedVolumeGroups;
        private @NonNull List<AudioFocusEntry> mFocusHolders;
        private @NonNull List<AudioFocusEntry> mFocusLosers;

        private long mBuilderFieldsSet = 0L;

        public Builder(
                @NonNull List<CarVolumeGroupInfo> mutedVolumeGroups,
                @NonNull List<AudioFocusEntry> focusHolders,
                @NonNull List<AudioFocusEntry> focusLosers,
                int audioZoneId) {
            Preconditions.checkArgument(mutedVolumeGroups != null,
                    "Muted volume groups can not be null");
            Preconditions.checkArgument(focusHolders != null,
                    " Focus holders can not be null");
            Preconditions.checkArgument(focusLosers != null,
                    "Focus losers can not be null");
            mMutedVolumeGroups = mutedVolumeGroups;
            mFocusHolders = focusHolders;
            mFocusLosers = focusLosers;
            mAudioZoneId = audioZoneId;
        }

        /**
         * set the audio zone id
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setAudioZoneId(int value) {
            checkNotUsed();
            mBuilderFieldsSet |= ZONE_ID_FIELDS_SET;
            mAudioZoneId = value;
            return this;
        }

        /**
         * Sets the current focus info to evaluate
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @NonNull
        public Builder setAudioFocusRequest(@NonNull AudioFocusEntry audioFocusRequest) {
            Preconditions.checkArgument(audioFocusRequest != null,
                    "Audio focus request can not be null");
            checkNotUsed();
            mBuilderFieldsSet |= FOCUS_REQUEST_FIELDS_SET;
            mAudioFocusRequest = audioFocusRequest;
            return this;
        }

        /**
         * Sets the currently muted group volumes
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @NonNull
        public Builder setMutedVolumeGroups(
                @NonNull List<CarVolumeGroupInfo> mutedVolumeGroups) {
            Preconditions.checkArgument(mutedVolumeGroups != null,
                    "Muted volume groups can not be null");
            checkNotUsed();
            mBuilderFieldsSet |= MUTED_VOLUME_GROUPS_FIELDS_SET;
            mMutedVolumeGroups = mutedVolumeGroups;
            return this;
        }

        /** @see #setMutedVolumeGroups */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder addMutedVolumeGroups(@NonNull CarVolumeGroupInfo mutedVolumeGroup) {
            Preconditions.checkArgument(mutedVolumeGroup != null,
                    "Muted volume group can not be null");
            if (mMutedVolumeGroups == null) setMutedVolumeGroups(new ArrayList<>());
            mMutedVolumeGroups.add(mutedVolumeGroup);
            return this;
        }

        /**
         * Sets the focus holders
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setFocusHolders(@NonNull List<AudioFocusEntry> focusHolders) {
            Preconditions.checkArgument(focusHolders != null,
                    "Focus holders can not be null");
            checkNotUsed();
            mBuilderFieldsSet |= FOCUS_HOLDERS_FIELDS_SET;
            mFocusHolders = focusHolders;
            return this;
        }

        /** @see #setFocusHolders */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder addFocusHolders(@NonNull AudioFocusEntry focusHolder) {
            Preconditions.checkArgument(focusHolder != null,
                    "Focus holder can not be null");
            if (mFocusHolders == null) setFocusHolders(new ArrayList<>());
            mFocusHolders.add(focusHolder);
            return this;
        }

        /**
         * Sets the focus losers
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setFocusLosers(@NonNull List<AudioFocusEntry> focusLosers) {
            Preconditions.checkArgument(focusLosers != null,
                    "Focus losers can not be null");
            checkNotUsed();
            mBuilderFieldsSet |= FOCUS_LOSERS_FIELDS_SET;
            mFocusLosers = focusLosers;
            return this;
        }

        /** @see #setFocusLosers */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder addFocusLosers(@NonNull AudioFocusEntry focusLoser) {
            Preconditions.checkArgument(focusLoser != null,
                    "Focus loser can not be null");
            if (mFocusLosers == null) setFocusLosers(new ArrayList<>());
            mFocusLosers.add(focusLoser);
            return this;
        }

        /** Builds the instance. This builder should not be touched after calling this! */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @NonNull
        public OemCarAudioFocusEvaluationRequest build() {
            checkNotUsed();
            mBuilderFieldsSet |= BUILDER_USED_FIELDS_SET; // Mark builder used

            OemCarAudioFocusEvaluationRequest o = new OemCarAudioFocusEvaluationRequest(
                    mAudioFocusRequest,
                    mMutedVolumeGroups,
                    mFocusHolders,
                    mFocusLosers,
                    mAudioZoneId);
            return o;
        }

        private void checkNotUsed() {
            if ((mBuilderFieldsSet & BUILDER_USED_FIELDS_SET) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }

    private static boolean safeEquals(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }
}
