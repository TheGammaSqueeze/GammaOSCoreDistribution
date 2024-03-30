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

package android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.android.internal.util.Preconditions.checkArgument;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Data class used by safety sources to propagate safety information such as their safety status and
 * safety issues.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetySourceData implements Parcelable {

    /**
     * Indicates that no opinion is currently associated with the information provided.
     *
     * <p>This severity level will be reflected in the UI of a {@link SafetySourceStatus} through a
     * grey icon.
     *
     * <p>For a {@link SafetySourceStatus}, this severity level indicates that the safety source
     * currently does not have sufficient information on the severity level of the {@link
     * SafetySourceStatus}.
     *
     * <p>This severity level cannot be used to indicate the severity level of a {@link
     * SafetySourceIssue}.
     */
    public static final int SEVERITY_LEVEL_UNSPECIFIED = 100;

    /**
     * Indicates the presence of an informational message or the absence of any safety issues.
     *
     * <p>This severity level will be reflected in the UI of either a {@link SafetySourceStatus} or
     * a {@link SafetySourceIssue} through a green icon.
     *
     * <p>For a {@link SafetySourceStatus}, this severity level indicates either the absence of any
     * {@link SafetySourceIssue}s or the presence of only {@link SafetySourceIssue}s with the same
     * severity level.
     *
     * <p>For a {@link SafetySourceIssue}, this severity level indicates that the {@link
     * SafetySourceIssue} represents an informational message relating to the safety source. {@link
     * SafetySourceIssue}s of this severity level will be dismissible by the user from the UI, and
     * will not trigger a confirmation dialog upon a user attempting to dismiss the warning.
     */
    public static final int SEVERITY_LEVEL_INFORMATION = 200;

    /**
     * Indicates the presence of a medium-severity safety issue which the user is encouraged to act
     * on.
     *
     * <p>This severity level will be reflected in the UI of either a {@link SafetySourceStatus} or
     * a {@link SafetySourceIssue} through a yellow icon.
     *
     * <p>For a {@link SafetySourceStatus}, this severity level indicates the presence of at least
     * one medium-severity {@link SafetySourceIssue} relating to the safety source which the user is
     * encouraged to act on, and no {@link SafetySourceIssue}s with higher severity level.
     *
     * <p>For a {@link SafetySourceIssue}, this severity level indicates that the {@link
     * SafetySourceIssue} represents a medium-severity safety issue relating to the safety source
     * which the user is encouraged to act on. {@link SafetySourceIssue}s of this severity level
     * will be dismissible by the user from the UI, and will trigger a confirmation dialog upon a
     * user attempting to dismiss the warning.
     */
    public static final int SEVERITY_LEVEL_RECOMMENDATION = 300;

    /**
     * Indicates the presence of a critical or urgent safety issue that should be addressed by the
     * user.
     *
     * <p>This severity level will be reflected in the UI of either a {@link SafetySourceStatus} or
     * a {@link SafetySourceIssue} through a red icon.
     *
     * <p>For a {@link SafetySourceStatus}, this severity level indicates the presence of at least
     * one critical or urgent {@link SafetySourceIssue} relating to the safety source that should be
     * addressed by the user.
     *
     * <p>For a {@link SafetySourceIssue}, this severity level indicates that the {@link
     * SafetySourceIssue} represents a critical or urgent safety issue relating to the safety source
     * that should be addressed by the user. {@link SafetySourceIssue}s of this severity level will
     * be dismissible by the user from the UI, and will trigger a confirmation dialog upon a user
     * attempting to dismiss the warning.
     */
    public static final int SEVERITY_LEVEL_CRITICAL_WARNING = 400;

    /**
     * All possible severity levels for a {@link SafetySourceStatus} or a {@link SafetySourceIssue}.
     *
     * <p>The numerical values of the levels are not used directly, rather they are used to build a
     * continuum of levels which support relative comparison. The higher the severity level the
     * higher the threat to the user.
     *
     * <p>For a {@link SafetySourceStatus}, the severity level is meant to convey the aggregated
     * severity of the safety source, and it contributes to the overall severity level in the Safety
     * Center. If the {@link SafetySourceData} contains {@link SafetySourceIssue}s, the severity
     * level of the s{@link SafetySourceStatus} must match the highest severity level among the
     * {@link SafetySourceIssue}s.
     *
     * <p>For a {@link SafetySourceIssue}, not all severity levels can be used. The severity level
     * also determines how a {@link SafetySourceIssue}s is "dismissible" by the user, i.e. how the
     * user can choose to ignore the issue and remove it from view in the Safety Center.
     *
     * @hide
     */
    @IntDef(
            prefix = {"SEVERITY_LEVEL_"},
            value = {
                SEVERITY_LEVEL_UNSPECIFIED,
                SEVERITY_LEVEL_INFORMATION,
                SEVERITY_LEVEL_RECOMMENDATION,
                SEVERITY_LEVEL_CRITICAL_WARNING
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SeverityLevel {}

    @NonNull
    public static final Creator<SafetySourceData> CREATOR =
            new Creator<SafetySourceData>() {
                @Override
                public SafetySourceData createFromParcel(Parcel in) {
                    SafetySourceStatus status = in.readTypedObject(SafetySourceStatus.CREATOR);
                    List<SafetySourceIssue> issues =
                            requireNonNull(in.createTypedArrayList(SafetySourceIssue.CREATOR));
                    Builder builder = new Builder().setStatus(status);
                    for (int i = 0; i < issues.size(); i++) {
                        builder.addIssue(issues.get(i));
                    }
                    return builder.build();
                }

                @Override
                public SafetySourceData[] newArray(int size) {
                    return new SafetySourceData[size];
                }
            };

    @Nullable private final SafetySourceStatus mStatus;
    @NonNull private final List<SafetySourceIssue> mIssues;

    private SafetySourceData(
            @Nullable SafetySourceStatus status, @NonNull List<SafetySourceIssue> issues) {
        this.mStatus = status;
        this.mIssues = issues;
    }

    /** Returns the data for the {@link SafetySourceStatus} to be shown in UI. */
    @Nullable
    public SafetySourceStatus getStatus() {
        return mStatus;
    }

    /** Returns the data for the list of {@link SafetySourceIssue}s to be shown in UI. */
    @NonNull
    public List<SafetySourceIssue> getIssues() {
        return mIssues;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeTypedObject(mStatus, flags);
        dest.writeTypedList(mIssues);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySourceData)) return false;
        SafetySourceData that = (SafetySourceData) o;
        return Objects.equals(mStatus, that.mStatus) && mIssues.equals(that.mIssues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mStatus, mIssues);
    }

    @Override
    public String toString() {
        return "SafetySourceData{" + ", mStatus=" + mStatus + ", mIssues=" + mIssues + '}';
    }

    /** Builder class for {@link SafetySourceData}. */
    public static final class Builder {

        @NonNull private final List<SafetySourceIssue> mIssues = new ArrayList<>();

        @Nullable private SafetySourceStatus mStatus;

        /** Sets data for the {@link SafetySourceStatus} to be shown in UI. */
        @NonNull
        public Builder setStatus(@Nullable SafetySourceStatus status) {
            mStatus = status;
            return this;
        }

        /** Adds data for a {@link SafetySourceIssue} to be shown in UI. */
        @NonNull
        public Builder addIssue(@NonNull SafetySourceIssue safetySourceIssue) {
            mIssues.add(requireNonNull(safetySourceIssue));
            return this;
        }

        /**
         * Clears data for all the {@link SafetySourceIssue}s that were added to this {@link
         * Builder}.
         */
        @NonNull
        public Builder clearIssues() {
            mIssues.clear();
            return this;
        }

        /** Creates the {@link SafetySourceData} defined by this {@link Builder}. */
        @NonNull
        public SafetySourceData build() {
            List<SafetySourceIssue> issues = unmodifiableList(new ArrayList<>(mIssues));
            if (mStatus != null) {
                int issuesMaxSeverityLevel = getIssuesMaxSeverityLevel(issues);
                if (issuesMaxSeverityLevel > SafetySourceData.SEVERITY_LEVEL_INFORMATION) {
                    checkArgument(
                            issuesMaxSeverityLevel <= mStatus.getSeverityLevel(),
                            "Safety source data must not contain any issue with a severity level "
                                    + "both greater than SEVERITY_LEVEL_INFORMATION and greater "
                                    + "than the status severity level");
                }
            }
            return new SafetySourceData(mStatus, issues);
        }

        private static int getIssuesMaxSeverityLevel(@NonNull List<SafetySourceIssue> issues) {
            int max = Integer.MIN_VALUE;
            for (int i = 0; i < issues.size(); i++) {
                max = Math.max(max, issues.get(i).getSeverityLevel());
            }
            return max;
        }
    }
}
