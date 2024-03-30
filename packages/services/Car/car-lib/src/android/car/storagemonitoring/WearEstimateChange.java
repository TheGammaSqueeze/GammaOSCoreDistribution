/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.car.storagemonitoring;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.car.annotation.AddedInOrBefore;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;

import java.time.Instant;
import java.util.Objects;

/**
 * Change in wear-out information.
 *
 * Contains information about the first cycle during which the system detected a change
 * in wear-out information of the flash storage.
 *
 * @hide
 */
@SystemApi
public final class WearEstimateChange implements Parcelable {
    @AddedInOrBefore(majorVersion = 33)
    public static final Parcelable.Creator<WearEstimateChange> CREATOR =
            new Parcelable.Creator<WearEstimateChange>() {
        public WearEstimateChange createFromParcel(Parcel in) {
            return new WearEstimateChange(in);
        }

        public WearEstimateChange[] newArray(int size) {
                return new WearEstimateChange[size];
            }
    };

    /**
     * The previous wear estimate.
     */
    @AddedInOrBefore(majorVersion = 33)
    public final @NonNull WearEstimate oldEstimate;

    /**
     * The new wear estimate.
     */
    @AddedInOrBefore(majorVersion = 33)
    public final @NonNull WearEstimate newEstimate;

    /**
     * Total CarService uptime when this change was detected.
     */
    @AddedInOrBefore(majorVersion = 33)
    public final long uptimeAtChange;

    /**
     * Wall-clock time when this change was detected.
     */
    @AddedInOrBefore(majorVersion = 33)
    public final @NonNull Instant dateAtChange;

    /**
     * Whether this change was within the vendor range for acceptable flash degradation.
     */
    @AddedInOrBefore(majorVersion = 33)
    public final boolean isAcceptableDegradation;

    public WearEstimateChange(WearEstimate oldEstimate,
                              WearEstimate newEstimate,
                              long uptimeAtChange,
                              Instant dateAtChange,
                              boolean isAcceptableDegradation) {
        if (uptimeAtChange < 0) {
            throw new IllegalArgumentException("uptimeAtChange must be >= 0");
        }
        this.oldEstimate = requireNonNull(oldEstimate);
        this.newEstimate = requireNonNull(newEstimate);
        this.uptimeAtChange = uptimeAtChange;
        this.dateAtChange = Instant.ofEpochSecond(requireNonNull(dateAtChange).getEpochSecond());
        this.isAcceptableDegradation = isAcceptableDegradation;
    }

    public WearEstimateChange(Parcel in) {
        oldEstimate = in.readParcelable(WearEstimate.class.getClassLoader());
        newEstimate = in.readParcelable(WearEstimate.class.getClassLoader());
        uptimeAtChange = in.readLong();
        long secs = in.readLong();
        int nanos = in.readInt();
        dateAtChange = Instant.ofEpochSecond(secs, nanos);
        isAcceptableDegradation = in.readInt() == 1;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE)
    @AddedInOrBefore(majorVersion = 33)
    public int describeContents() {
        return 0;
    }

    @Override
    @AddedInOrBefore(majorVersion = 33)
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(oldEstimate, flags);
        dest.writeParcelable(newEstimate, flags);
        dest.writeLong(uptimeAtChange);
        dest.writeLong(dateAtChange.getEpochSecond());
        dest.writeInt(dateAtChange.getNano());
        dest.writeInt(isAcceptableDegradation ? 1 : 0);
    }

    @Override
    @AddedInOrBefore(majorVersion = 33)
    public boolean equals(Object other) {
        if (other instanceof WearEstimateChange) {
            WearEstimateChange wo = (WearEstimateChange) other;
            return wo.isAcceptableDegradation == isAcceptableDegradation
                    && wo.uptimeAtChange == uptimeAtChange
                    && wo.dateAtChange.equals(dateAtChange)
                    && wo.oldEstimate.equals(oldEstimate)
                    && wo.newEstimate.equals(newEstimate);
        }
        return false;
    }

    @Override
    @AddedInOrBefore(majorVersion = 33)
    public int hashCode() {
        return Objects.hash(oldEstimate,
                            newEstimate,
                            uptimeAtChange,
                            dateAtChange,
                            isAcceptableDegradation);
    }

    @Override
    @AddedInOrBefore(majorVersion = 33)
    public String toString() {
        return String.format(
                "wear change{old level=%s, new level=%s, uptime=%d, date=%s, acceptable=%s}",
                oldEstimate,
                newEstimate,
                uptimeAtChange,
                dateAtChange,
                isAcceptableDegradation ? "yes" : "no");
    }

}
