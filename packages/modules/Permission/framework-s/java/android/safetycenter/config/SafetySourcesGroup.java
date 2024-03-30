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

package android.safetycenter.config;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.StringRes;
import android.annotation.SystemApi;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Data class used to represent the initial configuration of a group of safety sources.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetySourcesGroup implements Parcelable {

    /**
     * Indicates that the safety sources group should be displayed as a collapsible group with an
     * icon (stateless or stateful) and an optional default summary.
     */
    public static final int SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE = 0;

    /**
     * Indicates that the safety sources group should be displayed as a rigid group with no icon and
     * no summary.
     */
    public static final int SAFETY_SOURCES_GROUP_TYPE_RIGID = 1;

    /** Indicates that the safety sources group should not be displayed. */
    public static final int SAFETY_SOURCES_GROUP_TYPE_HIDDEN = 2;

    /**
     * All possible types for a safety sources group.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = "SAFETY_SOURCES_GROUP_TYPE_",
            value = {
                SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE,
                SAFETY_SOURCES_GROUP_TYPE_RIGID,
                SAFETY_SOURCES_GROUP_TYPE_HIDDEN
            })
    public @interface SafetySourceGroupType {}

    /**
     * Indicates that the safety sources group will not be displayed with any special icon when all
     * the sources contained in it are stateless.
     */
    public static final int STATELESS_ICON_TYPE_NONE = 0;

    /**
     * Indicates that the safety sources group will be displayed with the privacy icon when all the
     * sources contained in it are stateless.
     */
    public static final int STATELESS_ICON_TYPE_PRIVACY = 1;

    /**
     * All possible stateless icon types for a safety sources group.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = "STATELESS_ICON_TYPE_",
            value = {STATELESS_ICON_TYPE_NONE, STATELESS_ICON_TYPE_PRIVACY})
    public @interface StatelessIconType {}

    @NonNull
    public static final Creator<SafetySourcesGroup> CREATOR =
            new Creator<SafetySourcesGroup>() {
                @Override
                public SafetySourcesGroup createFromParcel(Parcel in) {
                    Builder builder =
                            new Builder()
                                    .setId(in.readString())
                                    .setTitleResId(in.readInt())
                                    .setSummaryResId(in.readInt())
                                    .setStatelessIconType(in.readInt());
                    List<SafetySource> safetySources =
                            requireNonNull(in.createTypedArrayList(SafetySource.CREATOR));
                    for (int i = 0; i < safetySources.size(); i++) {
                        builder.addSafetySource(safetySources.get(i));
                    }
                    return builder.build();
                }

                @Override
                public SafetySourcesGroup[] newArray(int size) {
                    return new SafetySourcesGroup[size];
                }
            };

    @NonNull private final String mId;
    @StringRes private final int mTitleResId;
    @StringRes private final int mSummaryResId;
    @StatelessIconType private final int mStatelessIconType;
    @NonNull private final List<SafetySource> mSafetySources;

    private SafetySourcesGroup(
            @NonNull String id,
            @StringRes int titleResId,
            @StringRes int summaryResId,
            @StatelessIconType int statelessIconType,
            @NonNull List<SafetySource> safetySources) {
        mId = id;
        mTitleResId = titleResId;
        mSummaryResId = summaryResId;
        mStatelessIconType = statelessIconType;
        mSafetySources = safetySources;
    }

    /**
     * Returns the type of this safety sources group.
     *
     * <p>The type is inferred according to the state of certain fields. If no title is provided
     * when building the group, the group is of type hidden. If a title is provided but no summary
     * or stateless icon are provided when building the group, the group is of type rigid.
     * Otherwise, the group is of type collapsible.
     */
    @SafetySourceGroupType
    public int getType() {
        if (mTitleResId == Resources.ID_NULL) {
            return SAFETY_SOURCES_GROUP_TYPE_HIDDEN;
        }
        if (mSummaryResId != Resources.ID_NULL || mStatelessIconType != STATELESS_ICON_TYPE_NONE) {
            return SAFETY_SOURCES_GROUP_TYPE_COLLAPSIBLE;
        }
        return SAFETY_SOURCES_GROUP_TYPE_RIGID;
    }

    /**
     * Returns the id of this safety sources group.
     *
     * <p>The id is unique among safety sources groups in a Safety Center configuration.
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /**
     * Returns the resource id of the title of this safety sources group.
     *
     * <p>The id refers to a string resource that is either accessible from any resource context or
     * that is accessible from the same resource context that was used to load the Safety Center
     * configuration. The id is {@link Resources#ID_NULL} when a title is not provided.
     */
    @StringRes
    public int getTitleResId() {
        return mTitleResId;
    }

    /**
     * Returns the resource id of the summary of this safety sources group.
     *
     * <p>The id refers to a string resource that is either accessible from any resource context or
     * that is accessible from the same resource context that was used to load the Safety Center
     * configuration. The id is {@link Resources#ID_NULL} when a summary is not provided.
     */
    @StringRes
    public int getSummaryResId() {
        return mSummaryResId;
    }

    /**
     * Returns the stateless icon type of this safety sources group.
     *
     * <p>If set to a value other than {@link SafetySourcesGroup#STATELESS_ICON_TYPE_NONE}, the icon
     * specified will be displayed for collapsible groups when all the sources contained in the
     * group are stateless.
     */
    @StatelessIconType
    public int getStatelessIconType() {
        return mStatelessIconType;
    }

    /**
     * Returns the list of {@link SafetySource}s in this safety sources group.
     *
     * <p>A safety sources group contains at least one {@link SafetySource}.
     */
    @NonNull
    public List<SafetySource> getSafetySources() {
        return mSafetySources;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySourcesGroup)) return false;
        SafetySourcesGroup that = (SafetySourcesGroup) o;
        return Objects.equals(mId, that.mId)
                && mTitleResId == that.mTitleResId
                && mSummaryResId == that.mSummaryResId
                && mStatelessIconType == that.mStatelessIconType
                && Objects.equals(mSafetySources, that.mSafetySources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mTitleResId, mSummaryResId, mStatelessIconType, mSafetySources);
    }

    @Override
    public String toString() {
        return "SafetySourcesGroup{"
                + "mId='"
                + mId
                + '\''
                + ", mTitleResId="
                + mTitleResId
                + ", mSummaryResId="
                + mSummaryResId
                + ", mStatelessIconType="
                + mStatelessIconType
                + ", mSafetySources="
                + mSafetySources
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mId);
        dest.writeInt(mTitleResId);
        dest.writeInt(mSummaryResId);
        dest.writeInt(mStatelessIconType);
        dest.writeTypedList(mSafetySources);
    }

    /** Builder class for {@link SafetySourcesGroup}. */
    public static final class Builder {

        private final List<SafetySource> mSafetySources = new ArrayList<>();

        @Nullable private String mId;
        @Nullable @StringRes private Integer mTitleResId;
        @Nullable @StringRes private Integer mSummaryResId;
        @Nullable @StatelessIconType private Integer mStatelessIconType;

        /** Creates a {@link Builder} for a {@link SafetySourcesGroup}. */
        public Builder() {}

        /**
         * Sets the id of this safety sources group.
         *
         * <p>The id must be unique among safety sources groups in a Safety Center configuration.
         */
        @NonNull
        public Builder setId(@Nullable String id) {
            mId = id;
            return this;
        }

        /**
         * Sets the resource id of the title of this safety sources group.
         *
         * <p>The id must refer to a string resource that is either accessible from any resource
         * context or that is accessible from the same resource context that was used to load the
         * Safety Center configuration. The id defaults to {@link Resources#ID_NULL} when a title is
         * not provided. A title is required unless the group only contains safety sources of type
         * issue only.
         */
        @NonNull
        public Builder setTitleResId(@StringRes int titleResId) {
            mTitleResId = titleResId;
            return this;
        }

        /**
         * Sets the resource id of the summary of this safety sources group.
         *
         * <p>The id must refer to a string resource that is either accessible from any resource
         * context or that is accessible from the same resource context that was used to load the
         * Safety Center configuration. The id defaults to {@link Resources#ID_NULL} when a summary
         * is not provided.
         */
        @NonNull
        public Builder setSummaryResId(@StringRes int summaryResId) {
            mSummaryResId = summaryResId;
            return this;
        }

        /**
         * Sets the stateless icon type of this safety sources group.
         *
         * <p>If set to a value other than {@link SafetySourcesGroup#STATELESS_ICON_TYPE_NONE}, the
         * icon specified will be displayed for collapsible groups when all the sources contained in
         * the group are stateless.
         */
        @NonNull
        public Builder setStatelessIconType(@StatelessIconType int statelessIconType) {
            mStatelessIconType = statelessIconType;
            return this;
        }

        /**
         * Adds a {@link SafetySource} to this safety sources group.
         *
         * <p>A safety sources group must contain at least one {@link SafetySource}.
         */
        @NonNull
        public Builder addSafetySource(@NonNull SafetySource safetySource) {
            mSafetySources.add(requireNonNull(safetySource));
            return this;
        }

        /**
         * Creates the {@link SafetySourcesGroup} defined by this {@link Builder}.
         *
         * <p>Throws an {@link IllegalStateException} if any constraint on the safety sources group
         * is violated.
         */
        @NonNull
        public SafetySourcesGroup build() {
            BuilderUtils.validateAttribute(mId, "id", true, false);
            List<SafetySource> safetySources = unmodifiableList(new ArrayList<>(mSafetySources));
            if (safetySources.isEmpty()) {
                throw new IllegalStateException("Safety sources group empty");
            }
            boolean titleRequired = false;
            int safetySourcesSize = safetySources.size();
            for (int i = 0; i < safetySourcesSize; i++) {
                int type = safetySources.get(i).getType();
                if (type != SafetySource.SAFETY_SOURCE_TYPE_ISSUE_ONLY) {
                    titleRequired = true;
                    break;
                }
            }
            int titleResId = BuilderUtils.validateResId(mTitleResId, "title", titleRequired, false);
            int summaryResId = BuilderUtils.validateResId(mSummaryResId, "summary", false, false);
            int statelessIconType =
                    BuilderUtils.validateIntDef(
                            mStatelessIconType,
                            "statelessIconType",
                            false,
                            false,
                            STATELESS_ICON_TYPE_NONE,
                            STATELESS_ICON_TYPE_NONE,
                            STATELESS_ICON_TYPE_PRIVACY);
            return new SafetySourcesGroup(
                    mId, titleResId, summaryResId, statelessIconType, safetySources);
        }
    }
}
