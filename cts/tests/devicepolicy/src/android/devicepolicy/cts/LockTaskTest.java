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

package android.devicepolicy.cts;

import static android.app.ActivityManager.LOCK_TASK_MODE_LOCKED;
import static android.app.ActivityManager.LOCK_TASK_MODE_NONE;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NONE;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO;
import static android.content.Intent.ACTION_DIAL;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.content.pm.PackageManager.FEATURE_TELEPHONY;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.remotedpc.RemoteDpc.DPC_COMPONENT_NAME;
import static com.android.eventlib.truth.EventLogsSubject.assertThat;
import static com.android.queryable.queries.StringQuery.string;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.testng.Assert.assertThrows;

import android.app.ActivityOptions;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.stats.devicepolicy.EventId;
import android.telecom.TelecomManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.LockTask;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.activities.Activity;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivity;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public final class LockTaskTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final String PACKAGE_NAME = "com.android.package.test";
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    @IntTestParameter({
            LOCK_TASK_FEATURE_SYSTEM_INFO,
            LOCK_TASK_FEATURE_HOME,
            LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
            LOCK_TASK_FEATURE_KEYGUARD})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface IndividuallySettableFlagTestParameter {
    }

    @IntTestParameter({
            LOCK_TASK_FEATURE_SYSTEM_INFO,
            LOCK_TASK_FEATURE_OVERVIEW,
            LOCK_TASK_FEATURE_NOTIFICATIONS,
            LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
            LOCK_TASK_FEATURE_KEYGUARD})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface SettableWithHomeFlagTestParameter {
    }

    private static final TestApp sLockTaskTestApp = sDeviceState.testApps().query()
            .wherePackageName().isEqualTo("com.android.bedstead.testapp.LockTaskApp")
            .get(); // TODO(scottjonathan): filter by containing activity not by package name
    private static final TestApp sTestApp =
            sDeviceState.testApps().query().whereActivities().isNotEmpty().get();

    private static final TestApp sSecondTestApp =
            sDeviceState.testApps().query().whereActivities().isNotEmpty().get();

    private static final ComponentReference BLOCKED_ACTIVITY_COMPONENT =
            TestApis.packages().component(new ComponentName(
                    "android", "com.android.internal.app.BlockedAppActivity"));

    private static final String ACTION_EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL";

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setLockTaskPackages_lockTaskPackagesIsSet() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{PACKAGE_NAME});

        try {
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getLockTaskPackages(DPC_COMPONENT_NAME)).asList()
                    .containsExactly(PACKAGE_NAME);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startLockTask_recordsMetric() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create();
             TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{sTestApp.packageName()});
            Activity<TestAppActivity> activity = testApp.activities().any().start();

            try {
                activity.startLockTask();

                assertThat(metrics.query()
                        .whereType().isEqualTo(EventId.SET_LOCKTASK_MODE_ENABLED_VALUE)
                        .whereAdminPackageName().isEqualTo(DPC_COMPONENT_NAME.getPackageName())
                        .whereBoolean().isTrue()
                        .whereStrings().contains(
                                string().isEqualTo(sTestApp.packageName())
                        )).wasLogged();
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @CannotSetPolicyTest(policy = LockTask.class)
    public void getLockTaskPackages_policyIsNotAllowedToBeFetched_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME));
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setLockTaskPackages_empty_lockTaskPackagesIsSet() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{});

        try {
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getLockTaskPackages(DPC_COMPONENT_NAME)).asList()
                    .isEmpty();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setLockTaskPackages_includesPolicyExemptApp_lockTaskPackagesIsSet() {
        Set<String> policyExemptApps = TestApis.devicePolicy().getPolicyExemptApps();
        assumeFalse("OEM does not define any policy-exempt apps",
                policyExemptApps.isEmpty());
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        String policyExemptApp = policyExemptApps.iterator().next();

        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{policyExemptApp});

        try {
            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getLockTaskPackages(DPC_COMPONENT_NAME)).asList()
                    .containsExactly(policyExemptApp);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @CannotSetPolicyTest(policy = LockTask.class)
    public void setLockTaskPackages_policyIsNotAllowedToBeSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{}));
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void isLockTaskPermitted_lockTaskPackageIsSet_returnsTrue() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{PACKAGE_NAME});

        try {
            assertThat(sLocalDevicePolicyManager.isLockTaskPermitted(PACKAGE_NAME)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyDoesNotApplyTest(policy = LockTask.class)
    // TODO(scottjonathan): Confirm expected behaviour here
    public void isLockTaskPermitted_lockTaskPackageIsSet_policyDoesntApply_returnsFalse() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{PACKAGE_NAME});

        try {
            assertThat(sLocalDevicePolicyManager.isLockTaskPermitted(PACKAGE_NAME)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void isLockTaskPermitted_lockTaskPackageIsNotSet_returnsFalse() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{});

        try {
            assertThat(sLocalDevicePolicyManager.isLockTaskPermitted(PACKAGE_NAME)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void isLockTaskPermitted_includesPolicyExemptApps() {
        Set<String> policyExemptApps = TestApis.devicePolicy().getPolicyExemptApps();
        // TODO(b/188035301): Add a unit test which ensures this actually gets tested
        assumeFalse("OEM does not define any policy-exempt apps",
                policyExemptApps.isEmpty());
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{});

            for (String app : policyExemptApps) {
                assertWithMessage("isLockTaskPermitted(%s)", app)
                        .that(sLocalDevicePolicyManager.isLockTaskPermitted(app)).isTrue();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    // TODO(b/188893663): Support additional parameterization for cases like this
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setLockTaskFeatures_individuallySettableFlag_setsFeature(
            @IndividuallySettableFlagTestParameter int flag) {
        int originalLockTaskFeatures =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskFeatures(DPC_COMPONENT_NAME);

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(DPC_COMPONENT_NAME, flag);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getLockTaskFeatures(DPC_COMPONENT_NAME))
                    .isEqualTo(flag);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(DPC_COMPONENT_NAME, originalLockTaskFeatures);
        }
    }


    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setLockTaskFeatures_overviewFeature_throwsException() {
        // Overview can only be used in combination with home
        int originalLockTaskFeatures =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskFeatures(DPC_COMPONENT_NAME);

        try {
            assertThrows(IllegalArgumentException.class, () -> {
                sDeviceState.dpc().devicePolicyManager()
                        .setLockTaskFeatures(DPC_COMPONENT_NAME, LOCK_TASK_FEATURE_OVERVIEW);
            });
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(DPC_COMPONENT_NAME, originalLockTaskFeatures);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setLockTaskFeatures_notificationsFeature_throwsException() {
        // Notifications can only be used in combination with home
        int originalLockTaskFeatures =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskFeatures(DPC_COMPONENT_NAME);

        try {
            assertThrows(IllegalArgumentException.class, () -> {
                sDeviceState.dpc().devicePolicyManager()
                        .setLockTaskFeatures(DPC_COMPONENT_NAME, LOCK_TASK_FEATURE_NOTIFICATIONS);
            });
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(DPC_COMPONENT_NAME, originalLockTaskFeatures);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    // TODO(b/188893663): Support additional parameterization for cases like this
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setLockTaskFeatures_multipleFeatures_setsFeatures(
            @SettableWithHomeFlagTestParameter int flag) {
        int originalLockTaskFeatures =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskFeatures(DPC_COMPONENT_NAME);

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(DPC_COMPONENT_NAME, LOCK_TASK_FEATURE_HOME | flag);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getLockTaskFeatures(DPC_COMPONENT_NAME))
                    .isEqualTo(LOCK_TASK_FEATURE_HOME | flag);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(DPC_COMPONENT_NAME, originalLockTaskFeatures);
        }
    }

    @CannotSetPolicyTest(policy = LockTask.class)
    public void setLockTaskFeatures_policyIsNotAllowedToBeSet_throwsException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager()
                        .setLockTaskFeatures(DPC_COMPONENT_NAME, LOCK_TASK_FEATURE_NONE));
    }

    @CannotSetPolicyTest(policy = LockTask.class)
    public void getLockTaskFeatures_policyIsNotAllowedToBeFetched_throwsException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskFeatures(DPC_COMPONENT_NAME));
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startLockTask_includedInLockTaskPackages_taskIsLocked() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{sTestApp.packageName()});
        try (TestAppInstance testApp = sTestApp.install()) {
            Activity<TestAppActivity> activity = testApp.activities().any().start();

            activity.startLockTask();

            try {
                assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component());
                assertThat(TestApis.activities().getLockTaskModeState()).isEqualTo(
                        LOCK_TASK_MODE_LOCKED);
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startLockTask_notIncludedInLockTaskPackages_taskIsNotLocked() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{});
        try (TestAppInstance testApp = sTestApp.install()) {
            Activity<TestAppActivity> activity = testApp.activities().any().start();

            activity.activity().startLockTask();

            try {
                assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component());
                assertThat(TestApis.activities().getLockTaskModeState()).isNotEqualTo(
                        LOCK_TASK_MODE_LOCKED);
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyDoesNotApplyTest(policy = LockTask.class)
    public void startLockTask_includedInLockTaskPackages_policyShouldNotApply_taskIsNotLocked() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{sTestApp.packageName()});
        try (TestAppInstance testApp = sTestApp.install()) {
            Activity<TestAppActivity> activity = testApp.activities().any().start();

            activity.activity().startLockTask();

            try {
                assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component());
                assertThat(TestApis.activities().getLockTaskModeState()).isNotEqualTo(
                        LOCK_TASK_MODE_LOCKED);
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void finish_isLocked_doesNotFinish() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{sTestApp.packageName()});
        try (TestAppInstance testApp = sTestApp.install()) {
            Activity<TestAppActivity> activity = testApp.activities().any().start();
            activity.startLockTask();

            activity.activity().finish();

            try {
                // We don't actually watch for the Destroyed event because that'd be waiting for a
                // non occurrence of an event which is slow
                assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component());
                assertThat(TestApis.activities().getLockTaskModeState()).isEqualTo(
                        LOCK_TASK_MODE_LOCKED);
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void finish_hasStoppedLockTask_doesFinish() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{sTestApp.packageName()});
        try (TestAppInstance testApp = sTestApp.install()) {
            Activity<TestAppActivity> activity = testApp.activities().any().start();
            activity.startLockTask();
            activity.stopLockTask();

            activity.activity().finish();

            assertThat(activity.activity().events().activityDestroyed()).eventOccurred();
            assertThat(TestApis.activities().foregroundActivity()).isNotEqualTo(
                    activity.activity().component());
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setLockTaskPackages_removingCurrentlyLockedTask_taskFinishes() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{sTestApp.packageName()});
        try (TestAppInstance testApp = sTestApp.install()) {
            Activity<TestAppActivity> activity = testApp.activities().any().start();
            activity.startLockTask();

            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{});

            assertThat(activity.activity().events().activityDestroyed()).eventOccurred();
            assertThat(TestApis.activities().foregroundActivity()).isNotEqualTo(
                    activity.activity().component());
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE) // TODO(b/235576683): Re-enable
    public void setLockTaskPackages_removingCurrentlyLockedTask_otherLockedTasksRemainLocked() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        sDeviceState.dpc().devicePolicyManager()
                .setLockTaskPackages(DPC_COMPONENT_NAME,
                        new String[]{sTestApp.packageName(), sSecondTestApp.packageName()});
        try (TestAppInstance testApp = sTestApp.install();
             TestAppInstance testApp2 = sSecondTestApp.install()) {
            Activity<TestAppActivity> activity = testApp.activities().any().start();
            activity.startLockTask();
            Activity<TestAppActivity> activity2 = testApp2.activities().any().start();
            activity2.startLockTask();

            try {
                sDeviceState.dpc().devicePolicyManager()
                        .setLockTaskPackages(DPC_COMPONENT_NAME,
                                new String[]{sTestApp.packageName()});

                assertThat(activity2.activity().events().activityDestroyed()).eventOccurred();
                assertThat(TestApis.activities().getLockTaskModeState()).isEqualTo(
                        LOCK_TASK_MODE_LOCKED);
                assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component());
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startActivity_withinSameTask_startsActivity() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        try (TestAppInstance testApp = sTestApp.install();
             TestAppInstance testApp2 = sSecondTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, new String[]{sTestApp.packageName()});
            Activity<TestAppActivity> firstActivity = testApp.activities().any().start();
            TestAppActivityReference secondActivity = testApp2.activities().any();
            Intent secondActivityIntent = new Intent();
            // TODO(scottjonathan): Add filter to ensure no taskAffinity or launchMode which would
            //  stop launching in same task
            secondActivityIntent.setComponent(secondActivity.component().componentName());

            firstActivity.startActivity(secondActivityIntent);

            assertThat(secondActivity.events().activityStarted()).eventOccurred();
            assertThat(TestApis.activities().foregroundActivity())
                    .isEqualTo(secondActivity.component());
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startActivity_withinSameTask_blockStartInTask_doesNotStart() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        int originalLockTaskFeatures =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskFeatures(DPC_COMPONENT_NAME);
        try (TestAppInstance testApp = sTestApp.install();
             TestAppInstance testApp2 = sSecondTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager()
                        .setLockTaskPackages(DPC_COMPONENT_NAME,
                                new String[]{sTestApp.packageName()});
                sDeviceState.dpc().devicePolicyManager()
                        .setLockTaskFeatures(DPC_COMPONENT_NAME,
                                LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK);
                Activity<TestAppActivity> firstActivity = testApp.activities().any().start();
                firstActivity.startLockTask();
                TestAppActivityReference secondActivity = testApp2.activities().any();
                Intent secondActivityIntent = new Intent();
                secondActivityIntent.setComponent(secondActivity.component().componentName());

                firstActivity.activity().startActivity(secondActivityIntent);

                Poll.forValue("Foreground activity",
                        () -> TestApis.activities().foregroundActivity())
                        .toBeEqualTo(BLOCKED_ACTIVITY_COMPONENT)
                        .errorOnFail()
                        .await();
            } finally {
                sDeviceState.dpc().devicePolicyManager()
                        .setLockTaskPackages(DPC_COMPONENT_NAME, originalLockTaskPackages);
                sDeviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                        DPC_COMPONENT_NAME, originalLockTaskFeatures);
            }
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startActivity_inNewTask_blockStartInTask_doesNotStart() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskPackages(DPC_COMPONENT_NAME);
        int originalLockTaskFeatures =
                sDeviceState.dpc().devicePolicyManager()
                        .getLockTaskFeatures(DPC_COMPONENT_NAME);
        try (TestAppInstance testApp = sTestApp.install();
             TestAppInstance testApp2 = sSecondTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager()
                        .setLockTaskPackages(DPC_COMPONENT_NAME,
                                new String[]{sTestApp.packageName()});
                sDeviceState.dpc().devicePolicyManager()
                        .setLockTaskFeatures(
                                DPC_COMPONENT_NAME, LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK);
                Activity<TestAppActivity> firstActivity = testApp.activities().any().start();
                firstActivity.startLockTask();
                TestAppActivityReference secondActivity = testApp2.activities().any();
                Intent secondActivityIntent = new Intent();
                secondActivityIntent.setComponent(secondActivity.component().componentName());
                secondActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                firstActivity.activity().startActivity(secondActivityIntent);

                Poll.forValue("Foreground activity",
                        () -> TestApis.activities().foregroundActivity())
                        .toBeEqualTo(BLOCKED_ACTIVITY_COMPONENT)
                        .errorOnFail()
                        .await();
            } finally {
                sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                        originalLockTaskPackages);
                sDeviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                        DPC_COMPONENT_NAME, originalLockTaskFeatures);
            }
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startActivity_fromPermittedPackage_newTask_starts() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);

        try (TestAppInstance testApp = sTestApp.install();
             TestAppInstance testApp2 = sSecondTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                        new String[]{sTestApp.packageName(), sSecondTestApp.packageName()});
                Activity<TestAppActivity> firstActivity = testApp.activities().any().start();
                firstActivity.startLockTask();
                TestAppActivityReference secondActivity = testApp2.activities().any();
                Intent secondActivityIntent = new Intent();
                secondActivityIntent.setComponent(secondActivity.component().componentName());
                secondActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                firstActivity.startActivity(secondActivityIntent);

                assertThat(TestApis.activities().foregroundActivity())
                        .isEqualTo(secondActivity.component());
            } finally {
                sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                        originalLockTaskPackages);
            }
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startActivity_fromNonPermittedPackage_newTask_doesNotStart() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);

        try (TestAppInstance testApp = sTestApp.install();
             TestAppInstance testApp2 = sSecondTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                        new String[]{sTestApp.packageName()});
                Activity<TestAppActivity> firstActivity = testApp.activities().any().start();
                firstActivity.startLockTask();
                TestAppActivityReference secondActivity = testApp2.activities().any();
                Intent secondActivityIntent = new Intent();
                secondActivityIntent.setComponent(secondActivity.component().componentName());
                secondActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                firstActivity.activity().startActivity(secondActivityIntent);

                assertThat(TestApis.activities().foregroundActivity())
                        .isEqualTo(firstActivity.activity().component());
            } finally {
                sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                        originalLockTaskPackages);
            }
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startActivity_lockTaskEnabledOption_startsInLockTaskMode() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);

        try (TestAppInstance testApp = sTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                        new String[]{sTestApp.packageName()});
                Bundle options = ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle();
                Activity<TestAppActivity> activity = testApp.activities().any().start(options);

                try {
                    assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                            activity.activity().component());
                    assertThat(TestApis.activities().getLockTaskModeState()).isEqualTo(
                            LOCK_TASK_MODE_LOCKED);
                } finally {
                    activity.stopLockTask();
                }
            } finally {
                sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                        originalLockTaskPackages);
            }
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startActivity_lockTaskEnabledOption_notAllowedPackage_throwsException() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);

        try (TestAppInstance testApp = sTestApp.install()) {
            try {
                sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                        new String[]{});
                Bundle options = ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle();

                assertThrows(SecurityException.class, () -> {
                    testApp.activities().any().start(options);
                });
            } finally {
                sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                        originalLockTaskPackages);
            }
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startActivity_ifWhitelistedActivity_startsInLockTaskMode() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);

        try (TestAppInstance testApp = sLockTaskTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                    new String[]{sLockTaskTestApp.packageName()});
            Activity<TestAppActivity> activity = testApp.activities().query()
                    .whereActivity().activityClass().simpleName().isEqualTo("ifwhitelistedactivity")
                    // TODO(scottjonathan): filter for lock task mode - currently we can't check
                    //  this so we just get a fixed package which contains a fixed activity
                    .get().start();

            try {
                assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component());
                assertThat(TestApis.activities().getLockTaskModeState()).isEqualTo(
                        LOCK_TASK_MODE_LOCKED);
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                    originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void startActivity_ifWhitelistedActivity_notWhitelisted_startsNotInLockTaskMode() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);

        try (TestAppInstance testApp = sLockTaskTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                    new String[]{});
            Activity<TestAppActivity> activity = testApp.activities().query()
                    .whereActivity().activityClass().simpleName().isEqualTo("ifwhitelistedactivity")
                    // TODO(scottjonathan): filter for lock task mode - currently we can't check
                    //  this so we just get a fixed package which contains a fixed activity
                    .get().start();

            try {
                assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component());
                assertThat(TestApis.activities().getLockTaskModeState()).isEqualTo(
                        LOCK_TASK_MODE_NONE);
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                    originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void finish_ifWhitelistedActivity_doesNotFinish() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);

        try (TestAppInstance testApp = sLockTaskTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                    new String[]{sLockTaskTestApp.packageName()});
            Activity<TestAppActivity> activity = testApp.activities().query()
                    .whereActivity().activityClass().simpleName().isEqualTo("ifwhitelistedactivity")
                    // TODO(scottjonathan): filter for lock task mode - currently we can't check
                    //  this so we just get a fixed package which contains a fixed activity
                    .get().start();

            activity.activity().finish();

            try {
                // We don't actually watch for the Destroyed event because that'd be waiting for a
                // non occurrence of an event which is slow
                assertThat(TestApis.activities().foregroundActivity()).isEqualTo(
                        activity.activity().component());
                assertThat(TestApis.activities().getLockTaskModeState()).isEqualTo(
                        LOCK_TASK_MODE_LOCKED);
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                    originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setLockTaskPackages_removingExistingIfWhitelistedActivity_stopsTask() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);

        try (TestAppInstance testApp = sLockTaskTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                    new String[]{sLockTaskTestApp.packageName()});
            Activity<TestAppActivity> activity = testApp.activities().query()
                    .whereActivity().activityClass().simpleName().isEqualTo("ifwhitelistedactivity")
                    // TODO(scottjonathan): filter for lock task mode - currently we can't check
                    //  this so we just get a fixed package which contains a fixed activity
                    .get().start();

            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                    new String[]{});

            assertThat(activity.activity().events().activityDestroyed()).eventOccurred();
            assertThat(TestApis.activities().foregroundActivity()).isNotEqualTo(
                    activity.activity().component());
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @RequireFeature(FEATURE_TELEPHONY)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    // Tests that the default dialer doesn't crash or otherwise misbehave in lock task mode
    public void launchDefaultDialerInLockTaskMode_launches() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);
        TelecomManager telecomManager =
                TestApis.context().instrumentedContext().getSystemService(TelecomManager.class);
        String dialerPackage = telecomManager.getSystemDialerPackage();
        try {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                    new String[]{dialerPackage});

            Bundle options = ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle();
            Intent intent = new Intent(ACTION_DIAL);
            intent.setPackage(dialerPackage);
            intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);

            TestApis.context().instrumentedContext().startActivity(intent, options);

            Poll.forValue("Foreground package",
                    () -> TestApis.activities().foregroundActivity().pkg())
                    .toMeet(pkg -> pkg != null && pkg.packageName().equals(dialerPackage))
                    .errorOnFail()
                    .await();

            Poll.forValue("Lock task mode state",
                    () -> TestApis.activities().getLockTaskModeState())
                    .toBeEqualTo(LOCK_TASK_MODE_LOCKED)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    DPC_COMPONENT_NAME, originalLockTaskPackages);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @RequireFeature(FEATURE_TELEPHONY)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void launchEmergencyDialerInLockTaskMode_notWhitelisted_noKeyguardFeature_doesNotLaunch() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);
        int originalLockTaskFeatures =
                sDeviceState.dpc().devicePolicyManager().getLockTaskFeatures(DPC_COMPONENT_NAME);
        String emergencyDialerPackageName = getEmergencyDialerPackageName();
        assumeFalse(emergencyDialerPackageName == null);
        try (TestAppInstance testApp = sLockTaskTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(DPC_COMPONENT_NAME,
                    new String[]{sLockTaskTestApp.packageName()});
            sDeviceState.dpc().devicePolicyManager()
                    .setLockTaskFeatures(DPC_COMPONENT_NAME, 0);
            Activity<TestAppActivity> activity = testApp.activities().any().start();

            try {
                activity.startLockTask();
                Intent intent = new Intent(ACTION_EMERGENCY_DIAL);
                intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);

                activity.activity().startActivity(intent);

                if (TestApis.activities().foregroundActivity() != null) {
                    assertThat(TestApis.activities().foregroundActivity().pkg().packageName())
                            .isNotEqualTo(emergencyDialerPackageName);
                }
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    DPC_COMPONENT_NAME, originalLockTaskPackages);
            sDeviceState.dpc().devicePolicyManager().setLockTaskFeatures(DPC_COMPONENT_NAME,
                    originalLockTaskFeatures);
        }
    }

    @PolicyAppliesTest(policy = LockTask.class)
    @RequireFeature(FEATURE_TELEPHONY)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void launchEmergencyDialerInLockTaskMode_notWhitelisted_keyguardFeature_launches() {
        String[] originalLockTaskPackages =
                sDeviceState.dpc().devicePolicyManager().getLockTaskPackages(DPC_COMPONENT_NAME);
        int originalLockTaskFeatures =
                sDeviceState.dpc().devicePolicyManager().getLockTaskFeatures(DPC_COMPONENT_NAME);
        String emergencyDialerPackageName = getEmergencyDialerPackageName();
        assumeFalse(emergencyDialerPackageName == null);
        try (TestAppInstance testApp = sLockTaskTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    DPC_COMPONENT_NAME, new String[]{sLockTaskTestApp.packageName()});
            sDeviceState.dpc().devicePolicyManager().setLockTaskFeatures(DPC_COMPONENT_NAME,
                    LOCK_TASK_FEATURE_KEYGUARD);
            Activity<TestAppActivity> activity = testApp.activities().any().start();
            try {
                activity.startLockTask();
                Intent intent = new Intent(ACTION_EMERGENCY_DIAL);
                intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);

                activity.startActivity(intent);

                assertThat(TestApis.activities().foregroundActivity().pkg())
                        .isEqualTo(TestApis.packages().find(emergencyDialerPackageName));
                assertThat(TestApis.activities().getLockTaskModeState()).isEqualTo(
                        LOCK_TASK_MODE_LOCKED);
            } finally {
                activity.stopLockTask();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setLockTaskPackages(
                    DPC_COMPONENT_NAME, originalLockTaskPackages);
            sDeviceState.dpc().devicePolicyManager().setLockTaskFeatures(
                    DPC_COMPONENT_NAME, originalLockTaskFeatures);
        }
    }

    private String getEmergencyDialerPackageName() {
        PackageManager packageManager =
                TestApis.context().instrumentedContext().getPackageManager();
        Intent intent = new Intent(ACTION_EMERGENCY_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ResolveInfo dialerInfo =
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return (dialerInfo != null) ? dialerInfo.activityInfo.packageName : null;
    }
}
