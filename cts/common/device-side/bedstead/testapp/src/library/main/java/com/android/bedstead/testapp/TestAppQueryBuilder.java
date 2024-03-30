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

package com.android.bedstead.testapp;

import com.android.queryable.Queryable;
import com.android.queryable.info.ActivityInfo;
import com.android.queryable.info.ServiceInfo;
import com.android.queryable.queries.ActivityQuery;
import com.android.queryable.queries.BooleanQuery;
import com.android.queryable.queries.BooleanQueryHelper;
import com.android.queryable.queries.BundleQuery;
import com.android.queryable.queries.BundleQueryHelper;
import com.android.queryable.queries.IntegerQuery;
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.ServiceQuery;
import com.android.queryable.queries.SetQuery;
import com.android.queryable.queries.SetQueryHelper;
import com.android.queryable.queries.StringQuery;
import com.android.queryable.queries.StringQueryHelper;

/** Builder for progressively building {@link TestApp} queries. */
public final class TestAppQueryBuilder implements Queryable {
    private final TestAppProvider mProvider;

    StringQueryHelper<TestAppQueryBuilder> mPackageName = new StringQueryHelper<>(this);
    BundleQueryHelper<TestAppQueryBuilder> mMetadata = new BundleQueryHelper<>(this);
    IntegerQueryHelper<TestAppQueryBuilder> mMinSdkVersion = new IntegerQueryHelper<>(this);
    IntegerQueryHelper<TestAppQueryBuilder> mMaxSdkVersion = new IntegerQueryHelper<>(this);
    IntegerQueryHelper<TestAppQueryBuilder> mTargetSdkVersion = new IntegerQueryHelper<>(this);
    SetQueryHelper<TestAppQueryBuilder, String, StringQuery<?>> mPermissions =
            new SetQueryHelper<>(this);
    BooleanQueryHelper<TestAppQueryBuilder> mTestOnly = new BooleanQueryHelper<>(this);
    SetQueryHelper<TestAppQueryBuilder, ActivityInfo, ActivityQuery<?>> mActivities =
            new SetQueryHelper<>(this);
    SetQueryHelper<TestAppQueryBuilder, ServiceInfo, ServiceQuery<?>> mServices =
            new SetQueryHelper<>(this);
    BooleanQueryHelper<TestAppQueryBuilder> mIsDeviceAdmin = new BooleanQueryHelper<>(this);
    StringQueryHelper<TestAppQueryBuilder> mSharedUserId = new StringQueryHelper<>(this);

    TestAppQueryBuilder(TestAppProvider provider) {
        if (provider == null) {
            throw new NullPointerException();
        }
        mProvider = provider;
    }

    /**
     * Query for a {@link TestApp} with a given package name.
     *
     * <p>Only use this filter when you are relying specifically on the package name itself. If you
     * are relying on features you know the {@link TestApp} with that package name has, query for
     * those features directly.
     */
    public StringQuery<TestAppQueryBuilder> wherePackageName() {
        return mPackageName;
    }

    /**
     * Query for a {@link TestApp} by metadata.
     */
    public BundleQuery<TestAppQueryBuilder> whereMetadata() {
        return mMetadata;
    }

    /**
     * Query for a {@link TestApp} by minSdkVersion.
     */
    public IntegerQuery<TestAppQueryBuilder> whereMinSdkVersion() {
        return mMinSdkVersion;
    }

    /**
     * Query for a {@link TestApp} by maxSdkVersion.
     */
    public IntegerQuery<TestAppQueryBuilder> whereMaxSdkVersion() {
        return mMaxSdkVersion;
    }

    /**
     * Query for a {@link TestApp} by targetSdkVersion.
     */
    public IntegerQuery<TestAppQueryBuilder> whereTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    /**
     * Query for a {@link TestApp} by declared permissions.
     */
    public SetQuery<TestAppQueryBuilder, String, StringQuery<?>> wherePermissions() {
        return mPermissions;
    }

    /**
     * Query for a {@link TestApp} by the testOnly attribute.
     */
    public BooleanQuery<TestAppQueryBuilder> whereTestOnly() {
        return mTestOnly;
    }

    /**
     * Query for an app which is a device admin.
     */
    public BooleanQuery<TestAppQueryBuilder> whereIsDeviceAdmin() {
        return mIsDeviceAdmin;
    }

    /**
     * Query for a {@link TestApp} by its sharedUserId;
     */
    public StringQuery<TestAppQueryBuilder> whereSharedUserId() {
        return mSharedUserId;
    }

    /**
     * Query for a {@link TestApp} by its activities.
     */
    public SetQuery<TestAppQueryBuilder, ActivityInfo, ActivityQuery<?>> whereActivities() {
        return mActivities;
    }

    /**
     * Query for a {@link TestApp} by its services.
     */
    public SetQuery<TestAppQueryBuilder, ServiceInfo, ServiceQuery<?>> whereServices() {
        return mServices;
    }

    /**
     * Get the {@link TestApp} matching the query.
     *
     * @throws NotFoundException if there is no matching @{link TestApp}.
     */
    public TestApp get() {
        // TODO(scottjonathan): Provide instructions on adding the TestApp if the query fails
        return new TestApp(resolveQuery());
    }

    private TestAppDetails resolveQuery() {
        for (TestAppDetails details : mProvider.testApps()) {
            if (!matches(details)) {
                continue;
            }

            mProvider.markTestAppUsed(details);
            return details;
        }

        throw new NotFoundException(this);
    }

    private boolean matches(TestAppDetails details) {
        if (!StringQueryHelper.matches(mPackageName, details.mApp.getPackageName())) {
            return false;
        }

        if (!BundleQueryHelper.matches(mMetadata, details.mMetadata)) {
            return false;
        }

        if (!IntegerQueryHelper.matches(
                mMinSdkVersion, details.mApp.getUsesSdk().getMinSdkVersion())) {
            return false;
        }

        if (!IntegerQueryHelper.matches(
                mMaxSdkVersion, details.mApp.getUsesSdk().getMaxSdkVersion())) {
            return false;
        }

        if (!IntegerQueryHelper.matches(
                mTargetSdkVersion, details.mApp.getUsesSdk().getTargetSdkVersion())) {
            return false;
        }

        if (!SetQueryHelper.matches(mActivities, details.mActivities)) {
            return false;
        }

        if (!SetQueryHelper.matches(mServices, details.mServices)) {
            return false;
        }

        if (!SetQueryHelper.matches(mPermissions, details.mPermissions)) {
            return false;
        }

        if (!BooleanQueryHelper.matches(mTestOnly, details.mApp.getTestOnly())) {
            return false;
        }

        // TODO(b/198419895): Actually query for the correct receiver + metadata
        boolean isDeviceAdmin = details.mApp.getPackageName().equals(
                "com.android.bedstead.testapp.DeviceAdminTestApp");
        if (!BooleanQueryHelper.matches(mIsDeviceAdmin, isDeviceAdmin)) {
            return false;
        }

        if (mSharedUserId.isEmpty()) {
            if (details.sharedUserId() != null) {
                return false;
            }
        } else {
            if (!StringQueryHelper.matches(mSharedUserId, details.sharedUserId())) {
                return false;
            }
        }

        if (details.mMetadata.getString("testapp-package-query-only", "false")
                .equals("true")) {
            if (!mPackageName.isQueryingForExactMatch()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String describeQuery(String fieldName) {
        return "{" + Queryable.joinQueryStrings(
                mPackageName.describeQuery("packageName"),
                mMetadata.describeQuery("metadata"),
                mMinSdkVersion.describeQuery("minSdkVersion"),
                mMaxSdkVersion.describeQuery("maxSdkVersion"),
                mTargetSdkVersion.describeQuery("targetSdkVersion"),
                mActivities.describeQuery("activities"),
                mServices.describeQuery("services"),
                mPermissions.describeQuery("permissions"),
                mSharedUserId.describeQuery("sharedUserId"),
                mTestOnly.describeQuery("testOnly"),
                mIsDeviceAdmin.describeQuery("isDeviceAdmin")
        ) + "}";
    }
}
