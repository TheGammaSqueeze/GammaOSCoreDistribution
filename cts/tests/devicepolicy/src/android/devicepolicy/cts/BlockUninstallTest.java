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

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.BeforeClass;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.BlockUninstall;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class BlockUninstallTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().any();

    private static final String NOT_INSTALLED_PACKAGE_NAME = "not.installed.package";

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    @BeforeClass
    public static void setupClass() {
        sTestApp.install();
    }

    @AfterClass
    public static void teardownClass() {
        sTestApp.uninstallFromAllUsers();
    }

    @Postsubmit(reason = "new test")
    @CannotSetPolicyTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true);
        });
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_true_isUninstallBlockedIsTrue() {
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true
            );

            assertThat(sDeviceState.dpc().devicePolicyManager().isUninstallBlocked(
                    sDeviceState.dpc().componentName(), sTestApp.packageName()
            )).isTrue();
            assertThat(sLocalDevicePolicyManager.isUninstallBlocked(/* admin= */ null,
                    sTestApp.packageName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false
            );
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyDoesNotApplyTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_true_isUninstallBlockedIsFalse() {
        try {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true
            );

            assertThat(sDeviceState.dpc().devicePolicyManager().isUninstallBlocked(
                    sDeviceState.dpc().componentName(), sTestApp.packageName()
            )).isTrue();
            assertThat(sLocalDevicePolicyManager.isUninstallBlocked(/* admin= */ null,
                    sTestApp.packageName())).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false
            );
        }
    }

    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_false_isUninstallBlockedIsFalse() {
        sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                sDeviceState.dpc().componentName(),
                sTestApp.packageName(), /* uninstallBlocked= */ false
        );

        assertThat(sDeviceState.dpc().devicePolicyManager().isUninstallBlocked(
                sDeviceState.dpc().componentName(), sTestApp.packageName()
        )).isFalse();
        assertThat(sLocalDevicePolicyManager.isUninstallBlocked(/* admin= */ null,
                sTestApp.packageName())).isFalse();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_true_appIsNotInstalled_silentlyFails() {
        sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                sDeviceState.dpc().componentName(),
                NOT_INSTALLED_PACKAGE_NAME, /* uninstallBlocked= */ true
        );

        assertThat(sDeviceState.dpc().devicePolicyManager().isUninstallBlocked(
                sDeviceState.dpc().componentName(), NOT_INSTALLED_PACKAGE_NAME
        )).isFalse();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = BlockUninstall.class)
    public void setUninstallBlocked_logged() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ true
            );

            assertThat(metrics.query()
                            .whereType().isEqualTo(EventId.SET_UNINSTALL_BLOCKED_VALUE)
                            .whereAdminPackageName().isEqualTo(
                                    sDeviceState.dpc().packageName())
                            .whereStrings().contains(sTestApp.packageName())
                            .whereStrings().size().isEqualTo(1)
                            .whereBoolean().isEqualTo(sDeviceState.dpc().isDelegate())
                            ).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUninstallBlocked(
                    sDeviceState.dpc().componentName(),
                    sTestApp.packageName(), /* uninstallBlocked= */ false
            );
        }
    }
}
