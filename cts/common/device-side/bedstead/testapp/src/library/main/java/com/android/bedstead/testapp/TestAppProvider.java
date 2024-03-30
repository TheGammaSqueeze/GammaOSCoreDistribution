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

import android.content.Context;
import android.content.IntentFilter;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.queryable.info.ActivityInfo;
import com.android.queryable.info.ServiceInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Entry point to Test App. Used for querying for {@link TestApp} instances. */
public final class TestAppProvider {

    private static final String TAG = TestAppProvider.class.getSimpleName();

    // Must be instrumentation context to access resources
    private static final Context sContext = TestApis.context().instrumentationContext();

    private boolean mTestAppsInitialised = false;
    private final Set<TestAppDetails> mTestApps = new HashSet<>();
    private Set<TestAppDetails> mTestAppsSnapshot = null;

    public TestAppProvider() {
        initTestApps();
    }

    /** Begin a query for a {@link TestApp}. */
    public TestAppQueryBuilder query() {
        return new TestAppQueryBuilder(this);
    }

    /** Get any {@link TestApp}. */
    public TestApp any() {
        TestApp testApp = query().get();
        Log.d(TAG, "any(): returning " + testApp);
        return testApp;
    }

    Set<TestAppDetails> testApps() {
        return mTestApps;
    }

    /** Save the state of the provider, to be reset by {@link #restore()}. */
    public void snapshot() {
        mTestAppsSnapshot = new HashSet<>(mTestApps);
        mTestAppsSnapshot = new HashSet<>(mTestApps);
    }

    /**
     * Restore the state of the provider to that recorded by {@link #snapshot()}.
     */
    public void restore() {
        if (mTestAppsSnapshot == null) {
            throw new IllegalStateException("You must call snapshot() before restore()");
        }
        mTestApps.clear();
        mTestApps.addAll(mTestAppsSnapshot);
    }

    private void initTestApps() {
        if (mTestAppsInitialised) {
            return;
        }
        mTestAppsInitialised = true;

        int indexId = sContext.getResources().getIdentifier(
                "raw/index", /* defType= */ null, sContext.getPackageName());

        try (InputStream inputStream = sContext.getResources().openRawResource(indexId)) {
            TestappProtos.TestAppIndex index = TestappProtos.TestAppIndex.parseFrom(inputStream);
            for (int i = 0; i < index.getAppsCount(); i++) {
                loadApk(index.getApps(i));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error loading testapp index", e);
        }
    }

    private void loadApk(TestappProtos.AndroidApp app) {
        TestAppDetails details = new TestAppDetails();
        details.mApp = app;
        details.mResourceIdentifier = sContext.getResources().getIdentifier(
                "raw/" + getApkNameWithoutSuffix(app.getApkName()),
                /* defType= */ null, sContext.getPackageName());

        for (int i = 0; i < app.getMetadataCount(); i++) {
            TestappProtos.Metadata metadataEntry = app.getMetadata(i);
            details.mMetadata.putString(metadataEntry.getName(), metadataEntry.getValue());
        }

        for (int i = 0; i < app.getPermissionsCount(); i++) {
            details.mPermissions.add(app.getPermissions(i).getName());
        }

        for (int i = 0; i < app.getActivitiesCount(); i++) {
            TestappProtos.Activity activityEntry = app.getActivities(i);
            details.mActivities.add(ActivityInfo.builder()
                    .activityClass(activityEntry.getName())
                    .exported(activityEntry.getExported())
                    .intentFilters(intentFilterSetFromProtoList(
                            activityEntry.getIntentFiltersList()))
                    .permission(activityEntry.getPermission().equals("") ? null
                            : activityEntry.getPermission())
                    .build());
        }

        for (int i = 0; i < app.getServicesCount(); i++) {
            TestappProtos.Service serviceEntry = app.getServices(i);
            details.mServices.add(ServiceInfo.builder()
                    .serviceClass(serviceEntry.getName())
                    .intentFilters(intentFilterSetFromProtoList(
                            serviceEntry.getIntentFiltersList()))
                    .build());
        }

        mTestApps.add(details);
    }

    private Set<IntentFilter> intentFilterSetFromProtoList(
            List<TestappProtos.IntentFilter> list) {
        Set<IntentFilter> filterInfoSet = new HashSet<>();

        for (TestappProtos.IntentFilter filter : list) {
            IntentFilter filterInfo = intentFilterFromProto(filter);
            filterInfoSet.add(filterInfo);
        }

        return filterInfoSet;
    }

    private IntentFilter intentFilterFromProto(TestappProtos.IntentFilter filterProto) {
        IntentFilter filter = new IntentFilter();

        for (String action : filterProto.getActionsList()) {
            filter.addAction(action);
        }
        for (String category : filterProto.getCategoriesList()) {
            filter.addCategory(category);
        }

        return filter;
    }

    private String getApkNameWithoutSuffix(String apkName) {
        return apkName.split("\\.", 2)[0];
    }

    void markTestAppUsed(TestAppDetails testApp) {
        mTestApps.remove(testApp);
    }
}
