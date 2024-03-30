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

package com.android.permissioncontroller.safetycenter.ui;

/** An entry to be shown as a preference in Safety Center */
public class SafetyEntry {

    /** Minimum entry order. Smaller values will be set to DEFAULT_ENTRY_ORDER. */
    private static final int MIN_ENTRY_ORDER = 1;
    /** Default entry order. Unspecified order values will be set to this. */
    private static final int DEFAULT_ENTRY_ORDER = 1000;

    private String mTitle;
    private String mSummary;
    private SeverityLevel mSeverityLevel;
    private int mOrder;
    private String mSafetySourceId;

    private SafetyEntry(SafetyEntry.Builder builder) {
        mTitle = builder.mTitle;
        mSummary = builder.mSummary;
        mSeverityLevel = builder.mSeverityLevel;
        mOrder = builder.mOrder;
        mSafetySourceId = builder.mSafetySourceId;
    }

    /** Builds the SafetyEntry. */
    public static SafetyEntry.Builder builder() {
        return new SafetyEntry.Builder();
    }

    /** Returns the title. */
    public String getTitle() {
        return mTitle;
    }

    /** Returns the summary. */
    public String getSummary() {
        return mSummary;
    }

    /** Returns the severity level. */
    public SeverityLevel getSeverityLevel() {
        return mSeverityLevel;
    }

    /** Returns the order that this entry should have in the entry list. */
    public int getOrder() {
        return mOrder;
    }

    /** Returns the safety source id. */
    public String getSafetySourceId() {
        return mSafetySourceId;
    }

    /** Builder for the SafetyEntry class. */
    public static class Builder {
        private String mTitle;
        private String mSummary;
        private SeverityLevel mSeverityLevel;
        private int mOrder = DEFAULT_ENTRY_ORDER;
        private String mSafetySourceId;

        /** Sets the title. */
        public SafetyEntry.Builder setTitle(String title) {
            mTitle = title;
            return this;
        }

        /** Sets the summary. */
        public SafetyEntry.Builder setSummary(String summary) {
            mSummary = summary;
            return this;
        }

        /** Sets the severity level. */
        public SafetyEntry.Builder setSeverityLevel(SeverityLevel severityLevel) {
            mSeverityLevel = severityLevel;
            return this;
        }

        /**
         * Sets the order that this entry should appear in the list of entries.
         * If <= 0, the value will be ignored and the entry will use a default order.
         */
        public SafetyEntry.Builder setOrder(int order) {
            mOrder = order >= MIN_ENTRY_ORDER ? order : DEFAULT_ENTRY_ORDER;
            return this;
        }

        /** Sets the safety source id. */
        public SafetyEntry.Builder setSafetySourceId(String safetySourceId) {
            mSafetySourceId = safetySourceId;
            return this;
        }

        /** Builds the SafetyEntry. */
        public SafetyEntry build() {
            return new SafetyEntry(this);
        }
    }
}
