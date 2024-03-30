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

package com.android.car.qc;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.os.Parcel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Quick Control Action that are includes as either start or end actions in {@link QCRow}
 */
public class QCActionItem extends QCItem {
    private final boolean mIsChecked;
    private final boolean mIsAvailable;
    private Icon mIcon;
    private PendingIntent mAction;
    private PendingIntent mDisabledClickAction;

    public QCActionItem(@NonNull @QCItemType String type, boolean isChecked, boolean isEnabled,
            boolean isAvailable, boolean isClickableWhileDisabled, @Nullable Icon icon,
            @Nullable PendingIntent action, @Nullable PendingIntent disabledClickAction) {
        super(type, isEnabled, isClickableWhileDisabled);
        mIsChecked = isChecked;
        mIsAvailable = isAvailable;
        mIcon = icon;
        mAction = action;
        mDisabledClickAction = disabledClickAction;
    }

    public QCActionItem(@NonNull Parcel in) {
        super(in);
        mIsChecked = in.readBoolean();
        mIsAvailable = in.readBoolean();
        boolean hasIcon = in.readBoolean();
        if (hasIcon) {
            mIcon = Icon.CREATOR.createFromParcel(in);
        }
        boolean hasAction = in.readBoolean();
        if (hasAction) {
            mAction = PendingIntent.CREATOR.createFromParcel(in);
        }
        boolean hasDisabledClickAction = in.readBoolean();
        if (hasDisabledClickAction) {
            mDisabledClickAction = PendingIntent.CREATOR.createFromParcel(in);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeBoolean(mIsChecked);
        dest.writeBoolean(mIsAvailable);
        boolean includeIcon = getType().equals(QC_TYPE_ACTION_TOGGLE) && mIcon != null;
        dest.writeBoolean(includeIcon);
        if (includeIcon) {
            mIcon.writeToParcel(dest, flags);
        }
        boolean hasAction = mAction != null;
        dest.writeBoolean(hasAction);
        if (hasAction) {
            mAction.writeToParcel(dest, flags);
        }
        boolean hasDisabledClickAction = mDisabledClickAction != null;
        dest.writeBoolean(hasDisabledClickAction);
        if (hasDisabledClickAction) {
            mDisabledClickAction.writeToParcel(dest, flags);
        }
    }

    @Override
    public PendingIntent getPrimaryAction() {
        return mAction;
    }

    @Override
    public PendingIntent getDisabledClickAction() {
        return mDisabledClickAction;
    }

    public boolean isChecked() {
        return mIsChecked;
    }

    public boolean isAvailable() {
        return mIsAvailable;
    }

    @Nullable
    public Icon getIcon() {
        return mIcon;
    }

    public static Creator<QCActionItem> CREATOR = new Creator<QCActionItem>() {
        @Override
        public QCActionItem createFromParcel(Parcel source) {
            return new QCActionItem(source);
        }

        @Override
        public QCActionItem[] newArray(int size) {
            return new QCActionItem[size];
        }
    };

    /**
     * Builder for {@link QCActionItem}.
     */
    public static class Builder {
        private final String mType;
        private boolean mIsChecked;
        private boolean mIsEnabled = true;
        private boolean mIsAvailable = true;
        private boolean mIsClickableWhileDisabled = false;
        private Icon mIcon;
        private PendingIntent mAction;
        private PendingIntent mDisabledClickAction;

        public Builder(@NonNull @QCItemType String type) {
            if (!isValidType(type)) {
                throw new IllegalArgumentException("Invalid QCActionItem type provided" + type);
            }
            mType = type;
        }

        /**
         * Sets whether or not the action item should be checked.
         */
        public Builder setChecked(boolean checked) {
            mIsChecked = checked;
            return this;
        }

        /**
         * Sets whether or not the action item should be enabled.
         */
        public Builder setEnabled(boolean enabled) {
            mIsEnabled = enabled;
            return this;
        }

        /**
         * Sets whether or not the action item is available.
         */
        public Builder setAvailable(boolean available) {
            mIsAvailable = available;
            return this;
        }

        /**
         * Sets whether or not an action item should be clickable while disabled.
         */
        public Builder setClickableWhileDisabled(boolean clickable) {
            mIsClickableWhileDisabled = clickable;
            return this;
        }

        /**
         * Sets the icon for {@link QC_TYPE_ACTION_TOGGLE} actions
         */
        public Builder setIcon(@Nullable Icon icon) {
            mIcon = icon;
            return this;
        }

        /**
         * Sets the PendingIntent to be sent when the action item is clicked.
         */
        public Builder setAction(@Nullable PendingIntent action) {
            mAction = action;
            return this;
        }

        /**
         * Sets the PendingIntent to be sent when the action item is clicked while disabled.
         */
        public Builder setDisabledClickAction(@Nullable PendingIntent action) {
            mDisabledClickAction = action;
            return this;
        }

        /**
         * Builds the final {@link QCActionItem}.
         */
        public QCActionItem build() {
            return new QCActionItem(mType, mIsChecked, mIsEnabled, mIsAvailable,
                    mIsClickableWhileDisabled, mIcon, mAction, mDisabledClickAction);
        }

        private boolean isValidType(String type) {
            return type.equals(QC_TYPE_ACTION_SWITCH) || type.equals(QC_TYPE_ACTION_TOGGLE);
        }
    }
}
