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

import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(JUnit4.class)
public class TestAppProviderTest {

    // Expects that this package name matches an actual test app
    private static final String EXISTING_PACKAGENAME = "com.android.bedstead.testapp.EmptyTestApp";

    // Expects that this package name does not match an actual test app
    private static final String NOT_EXISTING_PACKAGENAME = "not.existing.test.app";

    // Expected to be a class name which is used in a test app
    private static final String KNOWN_EXISTING_TESTAPP_ACTIVITY_CLASSNAME =
            "android.testapp.activity";

    private static final String QUERY_ONLY_TEST_APP_PACKAGE_NAME = "com.android.RemoteDPC";

    private static final String PERMISSION_DECLARED_BY_TESTAPP = "android.permission.READ_CALENDAR";

    private static final String METADATA_KEY = "test-metadata-key";
    private static final String METADATA_VALUE = "test-metadata-value";

    private TestAppProvider mTestAppProvider;

    @Before
    public void setup() {
        mTestAppProvider = new TestAppProvider();
    }

    @Test
    public void get_queryMatches_returnsTestApp() {
        TestAppQueryBuilder query = mTestAppProvider.query()
                .wherePackageName().isEqualTo(EXISTING_PACKAGENAME);

        assertThat(query.get()).isNotNull();
    }

    @Test
    public void get_queryMatches_packageNameIsSet() {
        TestAppQueryBuilder query = mTestAppProvider.query()
                .wherePackageName().isEqualTo(EXISTING_PACKAGENAME);

        assertThat(query.get().packageName()).isEqualTo(EXISTING_PACKAGENAME);
    }

    @Test
    public void get_queryDoesNotMatch_throwsException() {
        TestAppQueryBuilder query = mTestAppProvider.query()
                .wherePackageName().isEqualTo(NOT_EXISTING_PACKAGENAME);

        assertThrows(NotFoundException.class, query::get);
    }

    @Test
    public void any_returnsTestApp() {
        assertThat(mTestAppProvider.any()).isNotNull();
    }

    @Test
    public void any_returnsDifferentTestApps() {
        assertThat(mTestAppProvider.any()).isNotEqualTo(mTestAppProvider.any());
    }

    @Test
    public void query_onlyReturnsTestAppOnce() {
        mTestAppProvider.query().wherePackageName().isEqualTo(EXISTING_PACKAGENAME).get();

        TestAppQueryBuilder query = mTestAppProvider.query().wherePackageName().isEqualTo(EXISTING_PACKAGENAME);

        assertThrows(NotFoundException.class, query::get);
    }

    @Test
    public void query_afterRestore_returnsTestAppAgain() {
        mTestAppProvider.snapshot();
        mTestAppProvider.query().wherePackageName().isEqualTo(EXISTING_PACKAGENAME).get();

        mTestAppProvider.restore();

        assertThat(mTestAppProvider.query().wherePackageName()
                .isEqualTo(EXISTING_PACKAGENAME).get()).isNotNull();
    }

    @Test
    public void query_afterRestoreWithAppAlreadyUsed_doesNotReturnTestAppAgain() {
        mTestAppProvider.query().wherePackageName().isEqualTo(EXISTING_PACKAGENAME).get();
        mTestAppProvider.snapshot();

        mTestAppProvider.restore();

        TestAppQueryBuilder query =
                mTestAppProvider.query().wherePackageName().isEqualTo(EXISTING_PACKAGENAME);
        assertThrows(NotFoundException.class, query::get);
    }

    @Test
    public void restore_noSnapshot_throwsException() {
        assertThrows(IllegalStateException.class, mTestAppProvider::restore);
    }

    @Test
    public void any_doesNotReturnPackageQueryOnlyTestApps() {
        Set<String> testAppPackageNames = new HashSet<>();

        while (true) {
            try {
                testAppPackageNames.add(mTestAppProvider.any().packageName());
            } catch (NotFoundException e) {
                // Expected when we run out of test apps
                break;
            }
        }

        assertThat(testAppPackageNames).doesNotContain(QUERY_ONLY_TEST_APP_PACKAGE_NAME);
    }

    @Test
    public void query_queryByPackageName_doesReturnPackageQueryOnlyTestApps() {
        assertThat(
                mTestAppProvider.query()
                        .wherePackageName().isEqualTo(QUERY_ONLY_TEST_APP_PACKAGE_NAME)
                        .get()).isNotNull();
    }

    @Test
    public void query_byFeature_returnsDifferentTestAppsForSameQuery() {
        TestApp firstResult = mTestAppProvider.query()
                .whereTestOnly().isFalse()
                .get();
        TestApp secondResult = mTestAppProvider.query()
                .whereTestOnly().isFalse()
                .get();

        assertThat(firstResult).isNotEqualTo(secondResult);
    }

    @Test
    public void query_testOnly_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereTestOnly().isTrue()
                .get();

        assertThat(testApp.testOnly()).isTrue();
    }

    @Test
    public void query_notTestOnly_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereTestOnly().isFalse()
                .get();

        assertThat(testApp.testOnly()).isFalse();
    }

    @Test
    public void query_minSdkVersion_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereMinSdkVersion().isGreaterThanOrEqualTo(28)
                .get();

        assertThat(testApp.minSdkVersion()).isAtLeast(28);
    }

    @Test
    public void query_targetSdkVersion_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereTargetSdkVersion().isGreaterThanOrEqualTo(28)
                .get();

        assertThat(testApp.targetSdkVersion()).isAtLeast(28);
    }

    @Test
    public void query_withPermission_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .wherePermissions().contains(PERMISSION_DECLARED_BY_TESTAPP)
                .get();

        assertThat(testApp.permissions()).contains(PERMISSION_DECLARED_BY_TESTAPP);
    }

    @Test
    public void query_withoutPermission_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .wherePermissions().doesNotContain(PERMISSION_DECLARED_BY_TESTAPP)
                .get();

        assertThat(testApp.permissions()).doesNotContain(PERMISSION_DECLARED_BY_TESTAPP);
    }

    @Test
    public void query_metadata_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereMetadata().key(METADATA_KEY).stringValue().isEqualTo(METADATA_VALUE)
                .get();

        assertThat(testApp.metadata().get(METADATA_KEY)).isEqualTo(METADATA_VALUE);
    }

    @Test
    public void query_withExistingActivity_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereActivities().contains(
                        activity().activityClass()
                            .className().isEqualTo(KNOWN_EXISTING_TESTAPP_ACTIVITY_CLASSNAME)
                )
                .get();

        Set<String> activityClassNames = testApp.activities().stream()
                .map(a -> a.className()).collect(Collectors.toSet());
        assertThat(activityClassNames).contains(KNOWN_EXISTING_TESTAPP_ACTIVITY_CLASSNAME);
    }

    @Test
    public void query_withAnyActivity_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereActivities().isNotEmpty()
                .get();

        assertThat(testApp.activities()).isNotEmpty();
    }

    @Test
    public void query_withNoActivity_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereActivities().isEmpty()
                .get();

        assertThat(testApp.activities()).isEmpty();
    }

    @Test
    public void query_isDeviceAdmin_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereIsDeviceAdmin().isTrue()
                .get();

        assertThat(testApp.packageName()).isEqualTo(
                "com.android.bedstead.testapp.DeviceAdminTestApp");
    }

    @Test
    public void query_isNotDeviceAdmin_returnsMatching() {
        TestApp testApp = mTestAppProvider.query()
                .whereIsDeviceAdmin().isFalse()
                .get();

        assertThat(testApp.packageName()).isNotEqualTo(
                "com.android.bedstead.testapp.DeviceAdminTestApp");
    }

    @Test
    public void query_doesNotSpecifySharedUserId_sharedUserIdIsNull() {
        TestApp testApp = mTestAppProvider.query()
                .get();

        assertThat(testApp.sharedUserId()).isNull();
    }

    @Test
    @Ignore("re-enable when we have a test app which has a shareuserid")
    public void query_doesSpecifySharedUserId_matches() {
        TestApp testApp = mTestAppProvider.query()
                .whereSharedUserId().isEqualTo("com.android.bedstead")
                .get();

        assertThat(testApp.sharedUserId()).isEqualTo("com.android.bedstead");
    }

    @Test
    public void query_specifiesNullSharedUserId_matches() {
        TestApp testApp = mTestAppProvider.query()
                .whereSharedUserId().isNull()
                .get();

        assertThat(testApp.sharedUserId()).isNull();
    }
}
