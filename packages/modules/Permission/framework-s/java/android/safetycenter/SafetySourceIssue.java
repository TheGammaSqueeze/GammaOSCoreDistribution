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
import android.annotation.SuppressLint;
import android.annotation.SystemApi;
import android.app.PendingIntent;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Data for a safety source issue in the Safety Center page.
 *
 * <p>An issue represents an actionable matter relating to a particular safety source.
 *
 * <p>The safety issue will contain localized messages to be shown in UI explaining the potential
 * threat or warning and suggested fixes, as well as actions a user is allowed to take from the UI
 * to resolve the issue.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetySourceIssue implements Parcelable {

    /** Indicates that the risk associated with the issue is related to a user's device safety. */
    public static final int ISSUE_CATEGORY_DEVICE = 100;

    /** Indicates that the risk associated with the issue is related to a user's account safety. */
    public static final int ISSUE_CATEGORY_ACCOUNT = 200;

    /** Indicates that the risk associated with the issue is related to a user's general safety. */
    public static final int ISSUE_CATEGORY_GENERAL = 300;

    /**
     * All possible issue categories.
     *
     * <p>An issue's category represents a specific area of safety that the issue relates to.
     *
     * <p>An issue can only have one associated category. If the issue relates to multiple areas of
     * safety, then choose the closest area or default to {@link #ISSUE_CATEGORY_GENERAL}.
     *
     * @hide
     * @see Builder#setIssueCategory(int)
     */
    @IntDef(
            prefix = {"ISSUE_CATEGORY_"},
            value = {
                ISSUE_CATEGORY_DEVICE,
                ISSUE_CATEGORY_ACCOUNT,
                ISSUE_CATEGORY_GENERAL,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface IssueCategory {}

    @NonNull
    public static final Creator<SafetySourceIssue> CREATOR =
            new Creator<SafetySourceIssue>() {
                @Override
                public SafetySourceIssue createFromParcel(Parcel in) {
                    String id = in.readString();
                    CharSequence title = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                    CharSequence subtitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                    CharSequence summary = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                    int severityLevel = in.readInt();
                    int issueCategory = in.readInt();
                    List<Action> actions = requireNonNull(in.createTypedArrayList(Action.CREATOR));
                    PendingIntent onDismissPendingIntent =
                            in.readTypedObject(PendingIntent.CREATOR);
                    String issueTypeId = in.readString();
                    Builder builder =
                            new Builder(id, title, summary, severityLevel, issueTypeId)
                                    .setSubtitle(subtitle)
                                    .setIssueCategory(issueCategory)
                                    .setOnDismissPendingIntent(onDismissPendingIntent);
                    for (int i = 0; i < actions.size(); i++) {
                        builder.addAction(actions.get(i));
                    }
                    return builder.build();
                }

                @Override
                public SafetySourceIssue[] newArray(int size) {
                    return new SafetySourceIssue[size];
                }
            };

    @NonNull private final String mId;
    @NonNull private final CharSequence mTitle;
    @Nullable private final CharSequence mSubtitle;
    @NonNull private final CharSequence mSummary;
    @SafetySourceData.SeverityLevel private final int mSeverityLevel;
    private final List<Action> mActions;
    @Nullable private final PendingIntent mOnDismissPendingIntent;
    @IssueCategory private final int mIssueCategory;
    @NonNull private final String mIssueTypeId;

    private SafetySourceIssue(
            @NonNull String id,
            @NonNull CharSequence title,
            @Nullable CharSequence subtitle,
            @NonNull CharSequence summary,
            @SafetySourceData.SeverityLevel int severityLevel,
            @IssueCategory int issueCategory,
            @NonNull List<Action> actions,
            @Nullable PendingIntent onDismissPendingIntent,
            @NonNull String issueTypeId) {
        this.mId = id;
        this.mTitle = title;
        this.mSubtitle = subtitle;
        this.mSummary = summary;
        this.mSeverityLevel = severityLevel;
        this.mIssueCategory = issueCategory;
        this.mActions = actions;
        this.mOnDismissPendingIntent = onDismissPendingIntent;
        this.mIssueTypeId = issueTypeId;
    }

    /**
     * Returns the identifier for this issue.
     *
     * <p>This id should uniquely identify the safety risk represented by this issue. Safety issues
     * will be deduped by this id to be shown in the UI.
     *
     * <p>On multiple instances of providing the same issue to be represented in Safety Center,
     * provide the same id across all instances.
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /** Returns the localized title of the issue to be displayed in the UI. */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /** Returns the localized subtitle of the issue to be displayed in the UI. */
    @Nullable
    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    /** Returns the localized summary of the issue to be displayed in the UI. */
    @NonNull
    public CharSequence getSummary() {
        return mSummary;
    }

    /** Returns the {@link SafetySourceData.SeverityLevel} of the issue. */
    @SafetySourceData.SeverityLevel
    public int getSeverityLevel() {
        return mSeverityLevel;
    }

    /**
     * Returns the category of the risk associated with the issue.
     *
     * <p>The default category will be {@link #ISSUE_CATEGORY_GENERAL}.
     */
    @IssueCategory
    public int getIssueCategory() {
        return mIssueCategory;
    }

    /**
     * Returns a list of {@link Action}s representing actions supported in the UI for this issue.
     *
     * <p>Each issue must contain at least one action, in order to help the user resolve the issue.
     *
     * <p>In Android {@link android.os.Build.VERSION_CODES#TIRAMISU}, each issue can contain at most
     * two actions supported from the UI.
     */
    @NonNull
    public List<Action> getActions() {
        return mActions;
    }

    /**
     * Returns the optional {@link PendingIntent} that will be invoked when an issue is dismissed.
     *
     * <p>When a safety issue is dismissed in Safety Center page, the issue is removed from view in
     * Safety Center page. This method returns an additional optional action specified by the safety
     * source that should be invoked on issue dismissal. The action contained in the {@link
     * PendingIntent} cannot start an activity.
     */
    @Nullable
    public PendingIntent getOnDismissPendingIntent() {
        return mOnDismissPendingIntent;
    }

    /**
     * Returns the identifier for the type of this issue.
     *
     * <p>The issue type should indicate the underlying basis for the issue, for e.g. a pending
     * update or a disabled security feature.
     *
     * <p>The difference between this id and {@link #getId()} is that the issue type id is meant to
     * be used for logging and should therefore contain no personally identifiable information (PII)
     * (e.g. for account name).
     *
     * <p>On multiple instances of providing the same issue to be represented in Safety Center,
     * provide the same issue type id across all instances.
     */
    @NonNull
    public String getIssueTypeId() {
        return mIssueTypeId;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mId);
        TextUtils.writeToParcel(mTitle, dest, flags);
        TextUtils.writeToParcel(mSubtitle, dest, flags);
        TextUtils.writeToParcel(mSummary, dest, flags);
        dest.writeInt(mSeverityLevel);
        dest.writeInt(mIssueCategory);
        dest.writeTypedList(mActions);
        dest.writeTypedObject(mOnDismissPendingIntent, flags);
        dest.writeString(mIssueTypeId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetySourceIssue)) return false;
        SafetySourceIssue that = (SafetySourceIssue) o;
        return mSeverityLevel == that.mSeverityLevel
                && TextUtils.equals(mId, that.mId)
                && TextUtils.equals(mTitle, that.mTitle)
                && TextUtils.equals(mSubtitle, that.mSubtitle)
                && TextUtils.equals(mSummary, that.mSummary)
                && mIssueCategory == that.mIssueCategory
                && mActions.equals(that.mActions)
                && Objects.equals(mOnDismissPendingIntent, that.mOnDismissPendingIntent)
                && TextUtils.equals(mIssueTypeId, that.mIssueTypeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mTitle,
                mSubtitle,
                mSummary,
                mSeverityLevel,
                mIssueCategory,
                mActions,
                mOnDismissPendingIntent,
                mIssueTypeId);
    }

    @Override
    public String toString() {
        return "SafetySourceIssue{"
                + "mId="
                + mId
                + "mTitle="
                + mTitle
                + ", mSubtitle="
                + mSubtitle
                + ", mSummary="
                + mSummary
                + ", mSeverityLevel="
                + mSeverityLevel
                + ", mIssueCategory="
                + mIssueCategory
                + ", mActions="
                + mActions
                + ", mOnDismissPendingIntent="
                + mOnDismissPendingIntent
                + ", mIssueTypeId="
                + mIssueTypeId
                + '}';
    }

    /**
     * Data for an action supported from a safety issue {@link SafetySourceIssue} in the Safety
     * Center page.
     *
     * <p>The purpose of the action is to allow the user to address the safety issue, either by
     * performing a fix suggested in the issue, or by navigating the user to the source of the issue
     * where they can be exposed to detail about the issue and further suggestions to resolve it.
     *
     * <p>The user will be allowed to invoke the action from the UI by clicking on a UI element and
     * consequently resolve the issue.
     *
     * @hide
     */
    @SystemApi
    public static final class Action implements Parcelable {

        @NonNull
        public static final Creator<Action> CREATOR =
                new Creator<Action>() {
                    @Override
                    public Action createFromParcel(Parcel in) {
                        String id = in.readString();
                        CharSequence label = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                        PendingIntent pendingIntent = in.readTypedObject(PendingIntent.CREATOR);
                        return new Builder(id, label, pendingIntent)
                                .setWillResolve(in.readBoolean())
                                .setSuccessMessage(
                                        TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in))
                                .build();
                    }

                    @Override
                    public Action[] newArray(int size) {
                        return new Action[size];
                    }
                };

        @NonNull private final String mId;
        @NonNull private final CharSequence mLabel;
        @NonNull private final PendingIntent mPendingIntent;
        private final boolean mWillResolve;
        @Nullable private final CharSequence mSuccessMessage;

        private Action(
                @NonNull String id,
                @NonNull CharSequence label,
                @NonNull PendingIntent pendingIntent,
                boolean willResolve,
                @Nullable CharSequence successMessage) {
            mId = id;
            mLabel = label;
            mPendingIntent = pendingIntent;
            mWillResolve = willResolve;
            mSuccessMessage = successMessage;
        }

        /**
         * Returns the ID of the action, unique among actions in a given {@link SafetySourceIssue}.
         */
        @NonNull
        public String getId() {
            return mId;
        }

        /**
         * Returns the localized label of the action to be displayed in the UI.
         *
         * <p>The label should indicate what action will be performed if when invoked.
         */
        @NonNull
        public CharSequence getLabel() {
            return mLabel;
        }

        /**
         * Returns a {@link PendingIntent} to be fired when the action is clicked on.
         *
         * <p>The {@link PendingIntent} should perform the action referred to by {@link
         * #getLabel()}.
         */
        @NonNull
        public PendingIntent getPendingIntent() {
            return mPendingIntent;
        }

        /**
         * Returns whether invoking this action will fix or address the issue sufficiently for it to
         * be considered resolved i.e. the issue will no longer need to be conveyed to the user in
         * the UI.
         */
        public boolean willResolve() {
            return mWillResolve;
        }

        /**
         * Returns the optional localized message to be displayed in the UI when the action is
         * invoked and completes successfully.
         */
        @Nullable
        public CharSequence getSuccessMessage() {
            return mSuccessMessage;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(mId);
            TextUtils.writeToParcel(mLabel, dest, flags);
            dest.writeTypedObject(mPendingIntent, flags);
            dest.writeBoolean(mWillResolve);
            TextUtils.writeToParcel(mSuccessMessage, dest, flags);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Action)) return false;
            Action that = (Action) o;
            return mId.equals(that.mId)
                    && TextUtils.equals(mLabel, that.mLabel)
                    && mPendingIntent.equals(that.mPendingIntent)
                    && mWillResolve == that.mWillResolve
                    && TextUtils.equals(mSuccessMessage, that.mSuccessMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mId, mLabel, mPendingIntent, mWillResolve, mSuccessMessage);
        }

        @Override
        public String toString() {
            return "Action{"
                    + "mId="
                    + mId
                    + ", mLabel="
                    + mLabel
                    + ", mPendingIntent="
                    + mPendingIntent
                    + ", mWillResolve="
                    + mWillResolve
                    + ", mSuccessMessage="
                    + mSuccessMessage
                    + '}';
        }

        /** Builder class for {@link Action}. */
        public static final class Builder {

            @NonNull private final String mId;
            @NonNull private final CharSequence mLabel;
            @NonNull private final PendingIntent mPendingIntent;
            private boolean mWillResolve = false;
            @Nullable private CharSequence mSuccessMessage;

            /** Creates a {@link Builder} for an {@link Action}. */
            public Builder(
                    @NonNull String id,
                    @NonNull CharSequence label,
                    @NonNull PendingIntent pendingIntent) {
                mId = requireNonNull(id);
                mLabel = requireNonNull(label);
                mPendingIntent = requireNonNull(pendingIntent);
            }

            /**
             * Sets whether the action will resolve the safety issue. Defaults to {@code false}.
             *
             * @see #willResolve()
             */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setWillResolve(boolean willResolve) {
                mWillResolve = willResolve;
                return this;
            }

            /**
             * Sets the optional localized message to be displayed in the UI when the action is
             * invoked and completes successfully.
             */
            @NonNull
            public Builder setSuccessMessage(@Nullable CharSequence successMessage) {
                mSuccessMessage = successMessage;
                return this;
            }

            /** Creates the {@link Action} defined by this {@link Builder}. */
            @NonNull
            public Action build() {
                return new Action(mId, mLabel, mPendingIntent, mWillResolve, mSuccessMessage);
            }
        }
    }

    /** Builder class for {@link SafetySourceIssue}. */
    public static final class Builder {

        @NonNull private final String mId;
        @NonNull private final CharSequence mTitle;
        @NonNull private final CharSequence mSummary;
        @SafetySourceData.SeverityLevel private final int mSeverityLevel;
        @NonNull private final String mIssueTypeId;
        private final List<Action> mActions = new ArrayList<>();

        @Nullable private CharSequence mSubtitle;
        @IssueCategory private int mIssueCategory = ISSUE_CATEGORY_GENERAL;
        @Nullable private PendingIntent mOnDismissPendingIntent;

        /** Creates a {@link Builder} for a {@link SafetySourceIssue}. */
        public Builder(
                @NonNull String id,
                @NonNull CharSequence title,
                @NonNull CharSequence summary,
                @SafetySourceData.SeverityLevel int severityLevel,
                @NonNull String issueTypeId) {
            this.mId = requireNonNull(id);
            this.mTitle = requireNonNull(title);
            this.mSummary = requireNonNull(summary);
            this.mSeverityLevel = validateSeverityLevel(severityLevel);
            this.mIssueTypeId = requireNonNull(issueTypeId);
        }

        /** Sets the localized subtitle. */
        @NonNull
        public Builder setSubtitle(@Nullable CharSequence subtitle) {
            mSubtitle = subtitle;
            return this;
        }

        /**
         * Sets the category of the risk associated with the issue.
         *
         * <p>The default category will be {@link #ISSUE_CATEGORY_GENERAL}.
         */
        @NonNull
        public Builder setIssueCategory(@IssueCategory int issueCategory) {
            mIssueCategory = validateIssueCategory(issueCategory);
            return this;
        }

        /** Adds data for an {@link Action} to be shown in UI. */
        @NonNull
        public Builder addAction(@NonNull Action actionData) {
            mActions.add(requireNonNull(actionData));
            return this;
        }

        /** Clears data for all the {@link Action}s that were added to this {@link Builder}. */
        @NonNull
        public Builder clearActions() {
            mActions.clear();
            return this;
        }

        /**
         * Sets an optional {@link PendingIntent} to be invoked when an issue is dismissed from the
         * UI.
         *
         * <p>In particular, if the source would like to be notified of issue dismissals in Safety
         * Center in order to be able to dismiss or ignore issues at the source, then set this
         * field. The action contained in the {@link PendingIntent} must not start an activity.
         *
         * @see #getOnDismissPendingIntent()
         */
        @NonNull
        public Builder setOnDismissPendingIntent(@Nullable PendingIntent onDismissPendingIntent) {
            checkArgument(
                    onDismissPendingIntent == null || !onDismissPendingIntent.isActivity(),
                    "Safety source issue on dismiss pending intent must not start an activity");
            mOnDismissPendingIntent = onDismissPendingIntent;
            return this;
        }

        /** Creates the {@link SafetySourceIssue} defined by this {@link Builder}. */
        @NonNull
        public SafetySourceIssue build() {
            List<SafetySourceIssue.Action> actions = unmodifiableList(new ArrayList<>(mActions));
            checkArgument(!actions.isEmpty(), "Safety source issue must contain at least 1 action");
            checkArgument(
                    actions.size() <= 2,
                    "Safety source issue must not contain more than 2 actions");
            return new SafetySourceIssue(
                    mId,
                    mTitle,
                    mSubtitle,
                    mSummary,
                    mSeverityLevel,
                    mIssueCategory,
                    actions,
                    mOnDismissPendingIntent,
                    mIssueTypeId);
        }
    }

    @SafetySourceData.SeverityLevel
    private static int validateSeverityLevel(int value) {
        switch (value) {
            case SafetySourceData.SEVERITY_LEVEL_INFORMATION:
            case SafetySourceData.SEVERITY_LEVEL_RECOMMENDATION:
            case SafetySourceData.SEVERITY_LEVEL_CRITICAL_WARNING:
                return value;
            case SafetySourceData.SEVERITY_LEVEL_UNSPECIFIED:
                throw new IllegalArgumentException(
                        "SeverityLevel for SafetySourceIssue must not be "
                                + "SEVERITY_LEVEL_UNSPECIFIED");
            default:
        }
        throw new IllegalArgumentException(
                String.format("Unexpected SeverityLevel for SafetySourceIssue: %s", value));
    }

    @IssueCategory
    private static int validateIssueCategory(int value) {
        switch (value) {
            case ISSUE_CATEGORY_DEVICE:
            case ISSUE_CATEGORY_ACCOUNT:
            case ISSUE_CATEGORY_GENERAL:
                return value;
            default:
        }
        throw new IllegalArgumentException(
                String.format("Unexpected IssueCategory for SafetySourceIssue: %s", value));
    }
}
