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

package android.car.media;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.annotation.ApiRequirements;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Class to encapsulate car volume group information.
 *
 * @hide
 */
@SystemApi
@ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
        minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
public final class CarVolumeGroupInfo implements Parcelable {

    private static final long IS_USED_FIELD_SET = 0x01;

    private final String mName;
    private final int mZoneId;
    private final int mId;
    private final int mVolumeGainIndex;
    private final int mMaxVolumeGainIndex;
    private final int mMinVolumeGainIndex;
    private final boolean mIsMuted;
    private final boolean mIsBlocked;
    private final boolean mIsAttenuated;

    private CarVolumeGroupInfo(
            String name,
            int zoneId,
            int id,
            int volumeGainIndex,
            int maxVolumeGainIndex,
            int minVolumeGainIndex,
            boolean isMuted,
            boolean isBlocked,
            boolean isAttenuated) {
        mName = Objects.requireNonNull(name, "Volume info name can not be null");
        mZoneId = zoneId;
        mId = id;
        mVolumeGainIndex = volumeGainIndex;
        mMaxVolumeGainIndex = maxVolumeGainIndex;
        mMinVolumeGainIndex = minVolumeGainIndex;
        mIsMuted = isMuted;
        mIsBlocked = isBlocked;
        mIsAttenuated = isAttenuated;
    }

    /**
     * Creates volume info from parcel
     *
     * @hide
     */
    @VisibleForTesting()
    public CarVolumeGroupInfo(Parcel in) {
        mZoneId = in.readInt();
        mId = in.readInt();
        mName = in.readString();
        mVolumeGainIndex = in.readInt();
        mMaxVolumeGainIndex = in.readInt();
        mMinVolumeGainIndex = in.readInt();
        mIsMuted = in.readBoolean();
        mIsBlocked = in.readBoolean();
        mIsAttenuated = in.readBoolean();

    }

    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    @NonNull
    public static final Creator<CarVolumeGroupInfo> CREATOR = new Creator<>() {
        @Override
        @NonNull
        public CarVolumeGroupInfo createFromParcel(@NonNull Parcel in) {
            return new CarVolumeGroupInfo(in);
        }

        @Override
        @NonNull
        public CarVolumeGroupInfo[] newArray(int size) {
            return new CarVolumeGroupInfo[size];
        }
    };

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int describeContents() {
        return 0;
    }

    /**
     * Returns the volume group name
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public @NonNull String getName() {
        return mName;
    }

    /**
     * Returns the zone id where the volume group belongs
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int getZoneId() {
        return mZoneId;
    }

    /**
     * Returns the volume group id
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int getId() {
        return mId;
    }

    /**
     * Returns the volume group volume gain index
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int getVolumeGainIndex() {
        return mVolumeGainIndex;
    }

    /**
     * Returns the volume group max volume gain index
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int getMaxVolumeGainIndex() {
        return mMaxVolumeGainIndex;
    }

    /**
     * Returns the volume group min volume gain index
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int getMinVolumeGainIndex() {
        return mMinVolumeGainIndex;
    }

    /**
     * Returns the volume mute state, {@code true} for muted
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public boolean isMuted() {
        return mIsMuted;
    }

    /**
     * Returns the volume blocked state, {@code true} for blocked
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public boolean isBlocked() {
        return mIsBlocked;
    }

    /**
     * Returns the volume attenuated state, {@code true} for attenuated
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public boolean isAttenuated() {
        return mIsAttenuated;
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public String toString() {
        return new StringBuilder().append("CarVolumeGroupId { .name = ").append(mName)
                .append(", zone id = ").append(mZoneId).append(" id = ").append(mId)
                .append(", gain = ").append(mVolumeGainIndex)
                .append(", max gain = ").append(mMaxVolumeGainIndex)
                .append(", min gain = ").append(mMinVolumeGainIndex)
                .append(", muted = ").append(mIsMuted)
                .append(", blocked = ").append(mIsBlocked)
                .append(", attenuated = ").append(mIsAttenuated)
                .append(" }").toString();
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(mZoneId);
        dest.writeInt(mId);
        dest.writeString(mName);
        dest.writeInt(mVolumeGainIndex);
        dest.writeInt(mMaxVolumeGainIndex);
        dest.writeInt(mMinVolumeGainIndex);
        dest.writeBoolean(mIsMuted);
        dest.writeBoolean(mIsBlocked);
        dest.writeBoolean(mIsAttenuated);
    }

    /**
     * Determines if it is the same volume group, only comparing the group name, zone id, and
     * group id.
     *
     * @return {@code true} if the group info is the same, {@code false} otherwise
     */
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public boolean isSameVolumeGroup(@Nullable CarVolumeGroupInfo group) {
        return  group != null && mZoneId == group.mZoneId && mId == group.mId
                && mName.equals(group.mName);
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CarVolumeGroupInfo)) {
            return false;
        }

        CarVolumeGroupInfo that = (CarVolumeGroupInfo) o;

        return isSameVolumeGroup(that) && mVolumeGainIndex == that.mVolumeGainIndex
                && mMaxVolumeGainIndex == that.mMaxVolumeGainIndex
                && mMinVolumeGainIndex == that.mMinVolumeGainIndex
                && mIsMuted == that.mIsMuted && mIsBlocked == that.mIsBlocked
                && mIsAttenuated == that.mIsAttenuated;
    }

    @Override
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public int hashCode() {
        return Objects.hash(mName, mZoneId, mId, mVolumeGainIndex, mMaxVolumeGainIndex,
                mMinVolumeGainIndex, mIsMuted, mIsBlocked, mIsAttenuated);
    }

    /**
     * A builder for {@link CarVolumeGroupInfo}
     */
    @SuppressWarnings("WeakerAccess")
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public static final class Builder {

        private @NonNull String mName;
        private int mZoneId;
        private int mId;
        private int mVolumeGainIndex;
        private int mMinVolumeGainIndex;
        private int mMaxVolumeGainIndex;
        private boolean mIsMuted;
        private boolean mIsBlocked;
        private boolean mIsAttenuated;

        private long mBuilderFieldsSet = 0L;

        public Builder(@NonNull String name, int zoneId, int id) {
            mName = Objects.requireNonNull(name, "Volume info name can not be null");
            mZoneId = zoneId;
            mId = id;
        }

        public Builder(@NonNull CarVolumeGroupInfo info) {
            Objects.requireNonNull(info, "Volume info can not be null");
            mName = info.mName;
            mZoneId = info.mZoneId;
            mId = info.mId;
            mVolumeGainIndex = info.mVolumeGainIndex;
            mMaxVolumeGainIndex = info.mMaxVolumeGainIndex;
            mMinVolumeGainIndex = info.mMinVolumeGainIndex;
            mIsMuted = info.mIsMuted;
            mIsBlocked = info.mIsBlocked;
            mIsAttenuated = info.mIsAttenuated;
        }

        /**
         * Sets the volume group volume gain index
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setVolumeGainIndex(int gainIndex) {
            checkNotUsed();
            mVolumeGainIndex = gainIndex;
            return this;
        }

        /**
         * Sets the volume group max volume gain index
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setMaxVolumeGainIndex(int gainIndex) {
            checkNotUsed();
            mMaxVolumeGainIndex = gainIndex;
            return this;
        }

        /**
         * Sets the volume group min volume gain index
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setMinVolumeGainIndex(int gainIndex) {
            checkNotUsed();
            mMinVolumeGainIndex = gainIndex;
            return this;
        }

        /**
         * Sets the volume group muted state,  {@code true} for muted
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setMuted(boolean muted) {
            checkNotUsed();
            mIsMuted = muted;
            return this;
        }

        /**
         * Sets the volume group blocked state, {@code true} for blocked
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setBlocked(boolean blocked) {
            checkNotUsed();
            mIsBlocked = blocked;
            return this;
        }

        /**
         * Sets the volume group attenuated state, {@code true} for attenuated
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        public @NonNull Builder setAttenuated(boolean attenuated) {
            checkNotUsed();
            mIsAttenuated = attenuated;
            return this;
        }

        /**
         * Builds the instance.
         *
         * @throws IllegalArgumentException if min volume gain index is larger than max volume
         * gain index, or if the volume gain index is outside the range of max and min volume
         * gain index.
         *
         * @throws IllegalStateException if the constructor is re-used
         */
        @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_3,
                minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
        @NonNull
        public CarVolumeGroupInfo build() {
            checkNotUsed();
            validateGainIndexRange();

            mBuilderFieldsSet |= IS_USED_FIELD_SET; // Mark builder used


            return new CarVolumeGroupInfo(mName, mZoneId, mId, mVolumeGainIndex,
                    mMaxVolumeGainIndex, mMinVolumeGainIndex, mIsMuted, mIsBlocked, mIsAttenuated);
        }

        private void validateGainIndexRange() {
            Preconditions.checkArgument(mMinVolumeGainIndex < mMaxVolumeGainIndex,
                    "Min volume gain index %d must be smaller than max volume gain index %d",
                    mMinVolumeGainIndex, mMaxVolumeGainIndex);

            Preconditions.checkArgumentInRange(mVolumeGainIndex, mMinVolumeGainIndex,
                    mMaxVolumeGainIndex, "Volume gain index");
        }

        private void checkNotUsed() throws IllegalStateException {
            if ((mBuilderFieldsSet & IS_USED_FIELD_SET) != 0) {
                throw new IllegalStateException(
                        "This Builder should not be reused. Use a new Builder instance instead");
            }
        }
    }
}
