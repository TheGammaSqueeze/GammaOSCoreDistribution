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

package android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

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
 * An issue in the Safety Center.
 *
 * <p>An issue represents an actionable matter on the device of elevated importance.
 *
 * <p>It contains localized messages to display to the user, explaining the underlying threat or
 * warning and suggested fixes, and contains actions that a user may take from the UI to resolve the
 * issue.
 *
 * <p>Issues are ephemeral and disappear when resolved by user action or dismissal.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetyCenterIssue implements Parcelable {

    /** Indicates that this is low-severity, and informational. */
    public static final int ISSUE_SEVERITY_LEVEL_OK = 2100;

    /** Indicates that this issue describes a safety recommendation. */
    public static final int ISSUE_SEVERITY_LEVEL_RECOMMENDATION = 2200;

    /** Indicates that this issue describes a critical safety warning. */
    public static final int ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING = 2300;

    /**
     * All possible severity levels for a {@link SafetyCenterIssue}.
     *
     * @hide
     * @see SafetyCenterIssue#getSeverityLevel()
     * @see Builder#setSeverityLevel(int)
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = "ISSUE_SEVERITY_LEVEL_",
            value = {
                ISSUE_SEVERITY_LEVEL_OK,
                ISSUE_SEVERITY_LEVEL_RECOMMENDATION,
                ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING,
            })
    public @interface IssueSeverityLevel {}

    @NonNull
    public static final Creator<SafetyCenterIssue> CREATOR =
            new Creator<SafetyCenterIssue>() {
                @Override
                public SafetyCenterIssue createFromParcel(Parcel in) {
                    String id = in.readString();
                    CharSequence title = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                    CharSequence subtitle = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                    CharSequence summary = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                    return new Builder(id, title, summary)
                            .setSubtitle(subtitle)
                            .setSeverityLevel(in.readInt())
                            .setDismissible(in.readBoolean())
                            .setShouldConfirmDismissal(in.readBoolean())
                            .setActions(in.createTypedArrayList(Action.CREATOR))
                            .build();
                }

                @Override
                public SafetyCenterIssue[] newArray(int size) {
                    return new SafetyCenterIssue[size];
                }
            };

    @NonNull private final String mId;
    @NonNull private final CharSequence mTitle;
    @Nullable private final CharSequence mSubtitle;
    @NonNull private final CharSequence mSummary;
    @IssueSeverityLevel private final int mSeverityLevel;
    private final boolean mDismissible;
    private final boolean mShouldConfirmDismissal;
    @NonNull private final List<Action> mActions;

    private SafetyCenterIssue(
            @NonNull String id,
            @NonNull CharSequence title,
            @Nullable CharSequence subtitle,
            @NonNull CharSequence summary,
            @IssueSeverityLevel int severityLevel,
            boolean isDismissible,
            boolean shouldConfirmDismissal,
            @NonNull List<Action> actions) {
        mId = id;
        mTitle = title;
        mSubtitle = subtitle;
        mSummary = summary;
        mSeverityLevel = severityLevel;
        mDismissible = isDismissible;
        mShouldConfirmDismissal = shouldConfirmDismissal;
        mActions = actions;
    }

    /**
     * Returns the encoded string ID which uniquely identifies this issue within the Safety Center
     * on the device for the current user across all profiles and accounts.
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /** Returns the title that describes this issue. */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /** Returns the subtitle of this issue, or {@code null} if it has none. */
    @Nullable
    public CharSequence getSubtitle() {
        return mSubtitle;
    }

    /** Returns the summary text that describes this issue. */
    @NonNull
    public CharSequence getSummary() {
        return mSummary;
    }

    /** Returns the {@link IssueSeverityLevel} of this issue. */
    @IssueSeverityLevel
    public int getSeverityLevel() {
        return mSeverityLevel;
    }

    /** Returns {@code true} if this issue can be dismissed. */
    public boolean isDismissible() {
        return mDismissible;
    }

    /** Returns {@code true} if this issue should have its dismissal confirmed. */
    public boolean shouldConfirmDismissal() {
        return mShouldConfirmDismissal;
    }

    /**
     * Returns the ordered list of {@link Action} objects that may be taken to resolve this issue.
     *
     * <p>An issue may have 0-2 actions. The first action will be considered the "Primary" action of
     * the issue.
     */
    @NonNull
    public List<Action> getActions() {
        return mActions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SafetyCenterIssue)) return false;
        SafetyCenterIssue that = (SafetyCenterIssue) o;
        return mSeverityLevel == that.mSeverityLevel
                && mDismissible == that.mDismissible
                && mShouldConfirmDismissal == that.mShouldConfirmDismissal
                && Objects.equals(mId, that.mId)
                && TextUtils.equals(mTitle, that.mTitle)
                && TextUtils.equals(mSubtitle, that.mSubtitle)
                && TextUtils.equals(mSummary, that.mSummary)
                && Objects.equals(mActions, that.mActions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mTitle,
                mSubtitle,
                mSummary,
                mSeverityLevel,
                mDismissible,
                mShouldConfirmDismissal,
                mActions);
    }

    @Override
    public String toString() {
        return "SafetyCenterIssue{"
                + "mId='"
                + mId
                + '\''
                + ", mTitle="
                + mTitle
                + ", mSubtitle="
                + mSubtitle
                + ", mSummary="
                + mSummary
                + ", mSeverityLevel="
                + mSeverityLevel
                + ", mDismissible="
                + mDismissible
                + ", mConfirmDismissal="
                + mShouldConfirmDismissal
                + ", mActions="
                + mActions
                + '}';
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
        dest.writeBoolean(mDismissible);
        dest.writeBoolean(mShouldConfirmDismissal);
        dest.writeTypedList(mActions);
    }

    /** Builder class for {@link SafetyCenterIssue}. */
    public static final class Builder {

        @NonNull private String mId;
        @NonNull private CharSequence mTitle;
        @NonNull private CharSequence mSummary;
        @Nullable private CharSequence mSubtitle;
        @IssueSeverityLevel private int mSeverityLevel = ISSUE_SEVERITY_LEVEL_OK;
        private boolean mDismissible = true;
        private boolean mShouldConfirmDismissal = true;
        private List<Action> mActions = new ArrayList<>();

        /**
         * Creates a {@link Builder} for a {@link SafetyCenterIssue}.
         *
         * @param id a unique encoded string ID, see {@link #getId()} for details
         * @param title a title that describes this issue
         * @param summary a summary of this issue
         */
        public Builder(
                @NonNull String id, @NonNull CharSequence title, @NonNull CharSequence summary) {
            mId = requireNonNull(id);
            mTitle = requireNonNull(title);
            mSummary = requireNonNull(summary);
        }

        /** Creates a {@link Builder} with the values from the given {@link SafetyCenterIssue}. */
        public Builder(@NonNull SafetyCenterIssue issue) {
            mId = issue.mId;
            mTitle = issue.mTitle;
            mSubtitle = issue.mSubtitle;
            mSummary = issue.mSummary;
            mSeverityLevel = issue.mSeverityLevel;
            mDismissible = issue.mDismissible;
            mShouldConfirmDismissal = issue.mShouldConfirmDismissal;
            mActions = new ArrayList<>(issue.mActions);
        }

        /** Sets the ID for this issue. */
        @NonNull
        public Builder setId(@NonNull String id) {
            mId = requireNonNull(id);
            return this;
        }

        /** Sets the title for this issue. */
        @NonNull
        public Builder setTitle(@NonNull CharSequence title) {
            mTitle = requireNonNull(title);
            return this;
        }

        /** Sets or clears the optional subtitle for this issue. */
        @NonNull
        public Builder setSubtitle(@Nullable CharSequence subtitle) {
            mSubtitle = subtitle;
            return this;
        }

        /** Sets the summary for this issue. */
        @NonNull
        public Builder setSummary(@NonNull CharSequence summary) {
            mSummary = requireNonNull(summary);
            return this;
        }

        /**
         * Sets {@link IssueSeverityLevel} for this issue. Defaults to {@link
         * #ISSUE_SEVERITY_LEVEL_OK}.
         */
        @NonNull
        public Builder setSeverityLevel(@IssueSeverityLevel int severityLevel) {
            mSeverityLevel = validateIssueSeverityLevel(severityLevel);
            return this;
        }

        /** Sets whether this issue can be dismissed. Defaults to {@code true}. */
        @NonNull
        public Builder setDismissible(boolean dismissible) {
            mDismissible = dismissible;
            return this;
        }

        /**
         * Sets whether this issue should have its dismissal confirmed. Defaults to {@code true}.
         */
        @NonNull
        public Builder setShouldConfirmDismissal(boolean confirmDismissal) {
            mShouldConfirmDismissal = confirmDismissal;
            return this;
        }

        /**
         * Sets the list of potential actions to be taken to resolve this issue. Defaults to an
         * empty list.
         */
        @NonNull
        public Builder setActions(@NonNull List<Action> actions) {
            mActions = requireNonNull(actions);
            return this;
        }

        /** Creates the {@link SafetyCenterIssue} defined by this {@link Builder}. */
        @NonNull
        public SafetyCenterIssue build() {
            return new SafetyCenterIssue(
                    mId,
                    mTitle,
                    mSubtitle,
                    mSummary,
                    mSeverityLevel,
                    mDismissible,
                    mShouldConfirmDismissal,
                    unmodifiableList(new ArrayList<>(mActions)));
        }
    }

    /**
     * An action that can be taken to resolve a given issue.
     *
     * <p>When a user initiates an {@link Action}, that action's associated {@link PendingIntent}
     * will be executed, and the {@code successMessage} will be displayed if present.
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
                                .setIsInFlight(in.readBoolean())
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
        private final boolean mInFlight;
        @Nullable private final CharSequence mSuccessMessage;

        private Action(
                @NonNull String id,
                @NonNull CharSequence label,
                @NonNull PendingIntent pendingIntent,
                boolean willResolve,
                boolean inFlight,
                @Nullable CharSequence successMessage) {
            mId = id;
            mLabel = label;
            mPendingIntent = pendingIntent;
            mWillResolve = willResolve;
            mInFlight = inFlight;
            mSuccessMessage = successMessage;
        }

        /** Returns the ID of this action. */
        @NonNull
        public String getId() {
            return mId;
        }

        /** Returns a label describing this {@link Action}. */
        @NonNull
        public CharSequence getLabel() {
            return mLabel;
        }

        /** Returns the {@link PendingIntent} to execute when this {@link Action} is taken. */
        @NonNull
        public PendingIntent getPendingIntent() {
            return mPendingIntent;
        }

        /**
         * Returns whether invoking this action will fix or address the issue sufficiently for it to
         * be considered resolved (i.e. the issue will no longer need to be conveyed to the user in
         * the UI).
         */
        public boolean willResolve() {
            return mWillResolve;
        }

        /**
         * Returns whether this action is currently being executed (i.e. the user clicked on a
         * button that triggered this action, and now the Safety Center is waiting for the action's
         * result).
         */
        public boolean isInFlight() {
            return mInFlight;
        }

        /**
         * Returns the success message to display after successfully completing this {@link Action}
         * or {@code null} if none should be displayed.
         */
        @Nullable
        public CharSequence getSuccessMessage() {
            return mSuccessMessage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Action)) return false;
            Action action = (Action) o;
            return Objects.equals(mId, action.mId)
                    && TextUtils.equals(mLabel, action.mLabel)
                    && Objects.equals(mPendingIntent, action.mPendingIntent)
                    && mWillResolve == action.mWillResolve
                    && mInFlight == action.mInFlight
                    && TextUtils.equals(mSuccessMessage, action.mSuccessMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    mId, mLabel, mSuccessMessage, mWillResolve, mInFlight, mPendingIntent);
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
                    + ", mInFlight="
                    + mInFlight
                    + ", mSuccessMessage="
                    + mSuccessMessage
                    + '}';
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
            dest.writeBoolean(mInFlight);
            TextUtils.writeToParcel(mSuccessMessage, dest, flags);
        }

        /** Builder class for {@link Action}. */
        public static final class Builder {

            @NonNull private String mId;
            @NonNull private CharSequence mLabel;
            @NonNull private PendingIntent mPendingIntent;
            private boolean mWillResolve;
            private boolean mInFlight;
            @Nullable private CharSequence mSuccessMessage;

            /**
             * Creates a new {@link Builder} for an {@link Action}.
             *
             * @param id a unique ID for this action
             * @param label a label describing this action
             * @param pendingIntent a {@link PendingIntent} to be sent when this action is taken
             */
            public Builder(
                    @NonNull String id,
                    @NonNull CharSequence label,
                    @NonNull PendingIntent pendingIntent) {
                mId = requireNonNull(id);
                mLabel = requireNonNull(label);
                mPendingIntent = requireNonNull(pendingIntent);
            }

            /** Sets the ID of this {@link Action} */
            @NonNull
            public Builder setId(@NonNull String id) {
                mId = requireNonNull(id);
                return this;
            }

            /** Sets the label of this {@link Action}. */
            @NonNull
            public Builder setLabel(@NonNull CharSequence label) {
                mLabel = requireNonNull(label);
                return this;
            }

            /** Sets the {@link PendingIntent} to be sent when this {@link Action} is taken. */
            @NonNull
            public Builder setPendingIntent(@NonNull PendingIntent pendingIntent) {
                mPendingIntent = requireNonNull(pendingIntent);
                return this;
            }

            /**
             * Sets whether this action will resolve the issue when executed. Defaults to {@code
             * false}.
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
             * Sets a boolean that indicates whether this action is currently being executed (i.e.
             * the user clicked on a button that triggered this action, and now the Safety Center is
             * waiting for the action's result). Defaults to {@code false}.
             *
             * @see #isInFlight()
             */
            @SuppressLint("MissingGetterMatchingBuilder")
            @NonNull
            public Builder setIsInFlight(boolean inFlight) {
                mInFlight = inFlight;
                return this;
            }

            /**
             * Sets or clears the optional success message to be displayed when this {@link Action}
             * completes.
             */
            @NonNull
            public Builder setSuccessMessage(@Nullable CharSequence successMessage) {
                mSuccessMessage = successMessage;
                return this;
            }

            /** Creates the {@link Action} defined by this {@link Builder}. */
            @NonNull
            public Action build() {
                return new Action(
                        mId, mLabel, mPendingIntent, mWillResolve, mInFlight, mSuccessMessage);
            }
        }
    }

    @IssueSeverityLevel
    private static int validateIssueSeverityLevel(int value) {
        switch (value) {
            case ISSUE_SEVERITY_LEVEL_OK:
            case ISSUE_SEVERITY_LEVEL_RECOMMENDATION:
            case ISSUE_SEVERITY_LEVEL_CRITICAL_WARNING:
                return value;
            default:
        }
        throw new IllegalArgumentException(
                String.format("Unexpected IssueSeverityLevel for SafetyCenterIssue: %s", value));
    }
}
