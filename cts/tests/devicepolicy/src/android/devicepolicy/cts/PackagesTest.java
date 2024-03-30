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

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.HideApplication;
import com.android.bedstead.harrier.policies.SuspendPackage;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class PackagesTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().any();
    private static final TestAppInstance sTestAppInstance = sTestApp.install();

    @AfterClass
    public static void teardownClass() {
        sTestAppInstance.uninstall();
    }

    @CanSetPolicyTest(policy = HideApplication.class)
    @Postsubmit(reason = "new test")
    public void isApplicationHidden_applicationIsHidden_returnsTrue() {
        try {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(), true);

            assertThat(sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                    sDeviceState.dpc().componentName(), sTestApp.packageName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(), false);
        }
    }

    @CanSetPolicyTest(policy = HideApplication.class)
    @Postsubmit(reason = "new test")
    public void isApplicationHidden_applicationIsNotHidden_returnsFalse() {
        sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                sDeviceState.dpc().componentName(), sTestApp.packageName(), false);

        assertThat(sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), sTestApp.packageName())).isFalse();
    }

    @CannotSetPolicyTest(policy = HideApplication.class)
    @Postsubmit(reason = "new test")
    public void isApplicationHidden_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager()
                        .isApplicationHidden(
                                sDeviceState.dpc().componentName(), sTestApp.packageName()));
    }

    @CannotSetPolicyTest(policy = HideApplication.class)
    @Postsubmit(reason = "new test")
    public void setApplicationHidden_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager()
                        .setApplicationHidden(
                                sDeviceState.dpc().componentName(), sTestApp.packageName(), true));
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    public void isPackageSuspended_packageIsSuspended_returnsTrue() throws Exception {
        try {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    sDeviceState.dpc().componentName(), new String[]{sTestApp.packageName()}, true);

            assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                    sDeviceState.dpc().componentName(), sTestApp.packageName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                    sDeviceState.dpc().componentName(), new String[]{sTestApp.packageName()},
                    false);
        }
    }

    @CanSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    public void isPackageSuspended_packageIsNotSuspended_returnFalse() throws Exception {
        sDeviceState.dpc().devicePolicyManager().setPackagesSuspended(
                sDeviceState.dpc().componentName(), new String[]{sTestApp.packageName()}, false);

        assertThat(sDeviceState.dpc().devicePolicyManager().isPackageSuspended(
                sDeviceState.dpc().componentName(), sTestApp.packageName())).isFalse();
    }

    @CannotSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    public void isPackageSuspended_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager()
                        .isPackageSuspended(
                                sDeviceState.dpc().componentName(), sTestApp.packageName()));
    }

    @CannotSetPolicyTest(policy = SuspendPackage.class)
    @Postsubmit(reason = "new test")
    public void setPackageSuspended_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager()
                        .setPackagesSuspended(
                                sDeviceState.dpc().componentName(),
                                new String[]{sTestApp.packageName()}, true));
    }
}
