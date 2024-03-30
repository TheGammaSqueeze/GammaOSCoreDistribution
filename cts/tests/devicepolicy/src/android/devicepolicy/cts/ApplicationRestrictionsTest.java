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

import static android.content.Context.RECEIVER_EXPORTED;
import static android.content.Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.eventlib.truth.EventLogsSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.stats.devicepolicy.EventId;
import android.util.Log;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.ApplicationRestrictions;
import com.android.bedstead.harrier.policies.ApplicationRestrictionsManagingPackage;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(BedsteadJUnit4.class)
public final class ApplicationRestrictionsTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final String TAG = ApplicationRestrictionsTest.class.getSimpleName();
    private static final String[] TEST_STRINGS = new String[]{
            "<bad/>",
            ">worse!\"£$%^&*()'<",
            "<JSON>\"{ \\\"One\\\": { \\\"OneOne\\\": \\\"11\\\", \\\""
                    + "OneTwo\\\": \\\"12\\\" }, \\\"Two\\\": \\\"2\\\" } <JSON/>\""
    };

    private static final TestApp sTestApp = sDeviceState.testApps().any();
    private static final TestApp sDifferentTestApp = sDeviceState.testApps().any();

    // Should be consistent with assertEqualToBundle
    private static Bundle createBundle(String id) {
        Bundle result = new Bundle();
        // Tests for 6 allowed types: Integer, Boolean, String, String[], Bundle and Parcelable[]
        // Also test for string escaping handling
        result.putBoolean("boolean_0", false);
        result.putBoolean("boolean_1", true);
        result.putInt("integer", 0xfffff);
        // If a null is stored, "" will be read back
        result.putString("empty", "");
        result.putString("string", id);
        result.putStringArray("string[]", TEST_STRINGS);

        // Adding a bundle, which contain 2 nested restrictions - bundle_string and bundle_int
        Bundle bundle = new Bundle();
        bundle.putString("bundle_string", "bundle_string");
        bundle.putInt("bundle_int", 1);
        result.putBundle("bundle", bundle);

        // Adding an array of 2 bundles
        Bundle[] bundleArray = new Bundle[2];
        bundleArray[0] = new Bundle();
        bundleArray[0].putString("bundle_array_string", "bundle_array_string");
        // Put bundle inside bundle
        bundleArray[0].putBundle("bundle_array_bundle", bundle);
        bundleArray[1] = new Bundle();
        bundleArray[1].putString("bundle_array_string2", "bundle_array_string2");
        result.putParcelableArray("bundle_array", bundleArray);
        return result;
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_applicationRestrictionsAreSet() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = createBundle("setApplicationRestrictions_applicationRestrictionsAreSet");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertEqualToBundle("setApplicationRestrictions_applicationRestrictionsAreSet",
                    testApp.userManager().getApplicationRestrictions(sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_applicationRestrictionsAlreadySet_setsNewRestrictions() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = createBundle("setApplicationRestrictions_applicationRestrictionsAlreadySet_setsNewRestrictions");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            new Bundle());
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertEqualToBundle("setApplicationRestrictions_applicationRestrictionsAlreadySet_setsNewRestrictions",
                    testApp.userManager().getApplicationRestrictions(sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_applicationRestrictionsAreSet_returnsApplicationRestrictions() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = createBundle("getApplicationRestrictions_applicationRestrictionsAreSet_returnsApplicationRestrictions");

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertEqualToBundle("getApplicationRestrictions_applicationRestrictionsAreSet_returnsApplicationRestrictions",
                    sDeviceState.dpc().devicePolicyManager().getApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_differentPackage_throwsException() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = createBundle("getApplicationRestrictions_differentPackage_throwsException");

        try (TestAppInstance differentTestApp = sDifferentTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertThrows(SecurityException.class,
                    () -> differentTestApp.userManager().getApplicationRestrictions(
                            sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_setForOtherPackage_returnsNull() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = createBundle("getApplicationRestrictions_setForOtherPackage_returnsNull");

        try (TestAppInstance differentTestApp = sDifferentTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertNotEqualToBundle("getApplicationRestrictions_setForOtherPackage_returnsNull",
                    differentTestApp.userManager().getApplicationRestrictions(
                    sDifferentTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_policyDoesNotApply_applicationRestrictionsAreNotSet() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager().getApplicationRestrictions(
                        sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = createBundle("setApplicationRestrictions_policyDoesNotApply_applicationRestrictionsAreNotSet");

        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertNotEqualToBundle("setApplicationRestrictions_policyDoesNotApply_applicationRestrictionsAreNotSet",
                    testApp.userManager().getApplicationRestrictions(sTestApp.packageName()));
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_cannotSetPolicy_throwsException() {
        Bundle bundle = createBundle("setApplicationRestrictions_cannotSetPolicy_throwsException");
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);
        });
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(policy = ApplicationRestrictions.class)
    public void getApplicationRestrictions_cannotSetPolicy_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager()
                    .getApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName());
        });
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictions.class, singleTestOnly = true)
    public void setApplicationRestrictions_nullComponent_throwsException() {
        Bundle bundle = createBundle("setApplicationRestrictions_nullComponent_throwsException");
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(null,
                        sTestApp.packageName(), bundle));
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_restrictionsChangedBroadcastIsReceived() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = createBundle("setApplicationRestrictions_restrictionsChangedBroadcastIsReceived");

        try (TestAppInstance testApp = sTestApp.install()) {
            testApp.registerReceiver(new IntentFilter(ACTION_APPLICATION_RESTRICTIONS_CHANGED),
                    RECEIVER_EXPORTED);

            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertThat(testApp.events().broadcastReceived().whereIntent().action().isEqualTo(
                    ACTION_APPLICATION_RESTRICTIONS_CHANGED)).eventOccurred();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictionsManagingPackage.class)
    public void setApplicationRestrictionsManagingPackage_applicationRestrictionsManagingPackageIsSet()
            throws Exception {
        final String originalApplicationRestrictionsManagingPackage =
                sDeviceState.dpc().devicePolicyManager().getApplicationRestrictionsManagingPackage(
                        sDeviceState.dpc().componentName());
        try (TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictionsManagingPackage(
                    sDeviceState.dpc().componentName(), sTestApp.packageName());

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getApplicationRestrictionsManagingPackage(sDeviceState.dpc().componentName()))
                    .isEqualTo(sTestApp.packageName());
        } finally {
            try {
                sDeviceState.dpc().devicePolicyManager().setApplicationRestrictionsManagingPackage(
                        sDeviceState.dpc().componentName(),
                        originalApplicationRestrictionsManagingPackage);
            } catch (Throwable expected) {
                // If the original has been removed this can throw
            }
        }
    }

    @Postsubmit(reason = "New test")
    @CanSetPolicyTest(policy = ApplicationRestrictionsManagingPackage.class)
    public void setApplicationRestrictionsManagingPackage_appNotInstalled_throwsException() {
        sDifferentTestApp.uninstall();

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .setApplicationRestrictionsManagingPackage(
                                sDeviceState.dpc().componentName(),
                                sDifferentTestApp.packageName()));
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = ApplicationRestrictions.class)
    public void setApplicationRestrictions_logged() {
        Bundle originalApplicationRestrictions =
                sDeviceState.dpc().devicePolicyManager()
                        .getApplicationRestrictions(
                                sDeviceState.dpc().componentName(), sTestApp.packageName());
        Bundle bundle = createBundle("setApplicationRestrictions_logged");

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create();
             TestAppInstance testApp = sTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager()
                    .setApplicationRestrictions(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            bundle);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_APPLICATION_RESTRICTIONS_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().packageName())
                    .whereStrings().contains(sTestApp.packageName())
                    .whereStrings().size().isEqualTo(1))
                    .wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationRestrictions(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), originalApplicationRestrictions);
        }
    }

    // Should be consistent with createBundle
    private void assertEqualToBundle(String id, Bundle bundle) {
        assertWithMessage("bundle0 size")
                .that(bundle.size()).isEqualTo(8);
        assertBooleanKey(bundle, "boolean_0", false);
        assertBooleanKey(bundle, "boolean_1", true);
        assertIntKey(bundle, "integer", 0xfffff);
        assertStringKey(bundle, "empty", "");
        assertStringKey(bundle, "string", id);
        assertStringsKey(bundle, "string[]", TEST_STRINGS);

        Bundle childBundle = bundle.getBundle("bundle");
        assertStringKey(childBundle, "bundle_string", "bundle_string");
        assertIntKey(childBundle, "bundle_int", 1);

        Parcelable[] bundleArray = bundle.getParcelableArray("bundle_array");
        assertWithMessage("size of bundle_array").that(bundleArray).hasLength(2);

        // Verifying bundle_array[0]
        Bundle bundle1 = (Bundle) bundleArray[0];
        assertStringKey(bundle1, "bundle_array_string", "bundle_array_string");

        Bundle bundle1ChildBundle = getBundleKey(bundle1, "bundle_array_bundle");

        assertWithMessage("bundle_array_bundle")
                .that(bundle1ChildBundle).isNotNull();
        assertStringKey(bundle1ChildBundle, "bundle_string", "bundle_string");
        assertIntKey(bundle1ChildBundle, "bundle_int", 1);

        // Verifying bundle_array[1]
        Bundle bundle2 = (Bundle) bundleArray[1];
        assertStringKey(bundle2, "bundle_array_string2", "bundle_array_string2");
    }

    private void assertBooleanKey(Bundle bundle, String key, boolean expectedValue) {

        boolean value = bundle.getBoolean(key);
        Log.v(TAG, "assertBooleanKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key", key)
                .that(value).isEqualTo(expectedValue);
    }

    private void assertIntKey(Bundle bundle, String key, int expectedValue) {
        int value = bundle.getInt(key);
        Log.v(TAG, "assertIntKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key", key)
                .that(value).isEqualTo(expectedValue);
    }

    private void assertStringKey(Bundle bundle, String key, String expectedValue) {
        String value = bundle.getString(key);
        Log.v(TAG, "assertStringKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key", key)
                .that(value).isEqualTo(expectedValue);
    }

    private void assertStringsKey(Bundle bundle, String key, String[] expectedValue) {
        String[] value = bundle.getStringArray(key);
        Log.v(TAG, "assertStringsKey(): " + key + "="
                + (value == null ? "null" : Arrays.toString(value)));

        assertWithMessage("bundle's '%s' key", key).that(value).asList()
                .containsExactlyElementsIn(expectedValue).inOrder();
    }

    private Bundle getBundleKey(Bundle bundle, String key) {
        Bundle value = bundle.getBundle(key);
        Log.v(TAG, "getBundleKey(): " + key + "=" + value);
        assertWithMessage("bundle's '%s' key", key).that(value).isNotNull();
        return value;
    }

    private void assertNotEqualToBundle(String id, Bundle value) {
        // This uses an arbitrary value from the test bundle
        assertWithMessage("Bundle should not be equal to test bundle")
                .that(value.getString("string")).isNotEqualTo(id);
    }
}
