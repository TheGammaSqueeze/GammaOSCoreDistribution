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

package com.google.uwb.support.generic;

import android.annotation.NonNull;
import android.os.PersistableBundle;
import android.uwb.UwbManager;

import com.google.uwb.support.base.RequiredParam;
import com.google.uwb.support.ccc.CccParams;
import com.google.uwb.support.ccc.CccSpecificationParams;
import com.google.uwb.support.fira.FiraParams;
import com.google.uwb.support.fira.FiraSpecificationParams;

import java.util.Objects;

/**
 * Defines parameters for generic capability.
 *
 * <p>This is returned as a bundle from the service API {@link UwbManager#getSpecificationInfo}.
 */
public class GenericSpecificationParams extends GenericParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private final FiraSpecificationParams mFiraSpecificationParams;
    private final CccSpecificationParams mCccSpecificationParams;
    private final boolean mHasPowerStatsSupport;

    private static final String KEY_FIRA_SPECIFICATION_PARAMS = FiraParams.PROTOCOL_NAME;
    private static final String KEY_CCC_SPECIFICATION_PARAMS = CccParams.PROTOCOL_NAME;
    private static final String KEY_POWER_STATS_QUERY_SUPPORT = "power_stats_query";

    private GenericSpecificationParams(
            FiraSpecificationParams firaSpecificationParams,
            CccSpecificationParams cccSpecificationParams,
            boolean hasPowerStatsSupport) {
        mFiraSpecificationParams = firaSpecificationParams;
        mCccSpecificationParams = cccSpecificationParams;
        mHasPowerStatsSupport = hasPowerStatsSupport;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public FiraSpecificationParams getFiraSpecificationParams() {
        return mFiraSpecificationParams;
    }

    public CccSpecificationParams getCccSpecificationParams() {
        return mCccSpecificationParams;
    }

    /**
     * @return if the power stats is supported
     */
    public boolean hasPowerStatsSupport() {
        return mHasPowerStatsSupport;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putPersistableBundle(KEY_FIRA_SPECIFICATION_PARAMS,
                mFiraSpecificationParams.toBundle());
        bundle.putPersistableBundle(KEY_CCC_SPECIFICATION_PARAMS,
                mCccSpecificationParams.toBundle());
        bundle.putBoolean(KEY_POWER_STATS_QUERY_SUPPORT, mHasPowerStatsSupport);
        return bundle;
    }

    public static GenericSpecificationParams fromBundle(PersistableBundle bundle) {
        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static GenericSpecificationParams parseVersion1(PersistableBundle bundle) {
        GenericSpecificationParams.Builder builder = new GenericSpecificationParams.Builder();
        return builder.setFiraSpecificationParams(
                        FiraSpecificationParams.fromBundle(
                                bundle.getPersistableBundle(KEY_FIRA_SPECIFICATION_PARAMS)))
                .setCccSpecificationParams(
                        CccSpecificationParams.fromBundle(
                                bundle.getPersistableBundle(KEY_CCC_SPECIFICATION_PARAMS)))
                .hasPowerStatsSupport(bundle.getBoolean(KEY_FIRA_SPECIFICATION_PARAMS))
                .build();
    }

    /** Builder */
    public static class Builder {
        private RequiredParam<FiraSpecificationParams> mFiraSpecificationParams =
                new RequiredParam<>();
        private RequiredParam<CccSpecificationParams> mCccSpecificationParams =
                new RequiredParam<>();
        private boolean mHasPowerStatsSupport = false;

        /**
         * Set FIRA specification params
         */
        public Builder setFiraSpecificationParams(
                @NonNull FiraSpecificationParams firaSpecificationParams) {
            mFiraSpecificationParams.set(Objects.requireNonNull(firaSpecificationParams));
            return this;
        }

        /**
         * Set CCC specification params
         */
        public Builder setCccSpecificationParams(
                @NonNull CccSpecificationParams cccSpecificationParams) {
            mCccSpecificationParams.set(Objects.requireNonNull(cccSpecificationParams));
            return this;
        }

        /**
         * Sets if the power stats is supported
         */
        public Builder hasPowerStatsSupport(boolean value) {
            mHasPowerStatsSupport = value;
            return this;
        }

        /**
         * Build {@link GenericSpecificationParams}
         */
        public GenericSpecificationParams build() {
            return new GenericSpecificationParams(
                    mFiraSpecificationParams.get(),
                    mCccSpecificationParams.get(),
                    mHasPowerStatsSupport);
        }
    }
}
