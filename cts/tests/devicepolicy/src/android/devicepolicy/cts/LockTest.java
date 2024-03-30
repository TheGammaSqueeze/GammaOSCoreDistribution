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

import static android.Manifest.permission.LOCK_DEVICE;
import static android.content.pm.PackageManager.FEATURE_AUTOMOTIVE;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;

import static com.android.bedstead.harrier.Defaults.DEFAULT_PASSWORD;
import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.remotedpc.RemoteDpc.DPC_COMPONENT_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet;
import com.android.bedstead.harrier.annotations.EnsurePasswordSet;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireTargetSdkVersion;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.DeprecatedResetPassword;
import com.android.bedstead.harrier.policies.LockNow;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
@RequireFeature("android.software.secure_lock_screen")
public class LockTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);
    private static final KeyguardManager sLocalKeyguardManager =
            TestApis.context().instrumentedContext().getSystemService(KeyguardManager.class);

    // TODO(191637162): When @PolicyAppliesTest supports permissions, remove
    @RequireFeature("android.software.secure_lock_screen")
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureHasPermission(LOCK_DEVICE)
    @EnsureScreenIsOn
    @EnsurePasswordNotSet
    @Postsubmit(reason = "New test")
    @Test
    public void lockNow_permission_noPasswordSet_turnsScreenOff() throws Exception {
        sLocalDevicePolicyManager.lockNow();

        Poll.forValue("isScreenOn", () -> TestApis.device().isScreenOn())
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    // TODO(191637162): When @PolicyAppliesTest supports permissions, remove
    @RequireFeature("android.software.secure_lock_screen")
    @RequireFeature(FEATURE_AUTOMOTIVE)
    @EnsureHasPermission(LOCK_DEVICE)
    @EnsureScreenIsOn
    @EnsurePasswordNotSet
    @Postsubmit(reason = "New test")
    @Test
    public void lockNow_permission_automotive_noPasswordSet_doesNotTurnScreenOff()
            throws Exception {
        sLocalDevicePolicyManager.lockNow();

        assertThat(TestApis.device().isScreenOn()).isTrue();
    }

    // TODO(191637162): When @PolicyAppliesTest supports permissions, remove
    @RequireFeature("android.software.secure_lock_screen")
    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureHasPermission(LOCK_DEVICE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @Postsubmit(reason = "New test")
    @Test
    public void lockNow_permission_passwordSet_locksDevice() throws Exception {
        sLocalDevicePolicyManager.lockNow();

        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @PolicyAppliesTest(policy = LockNow.class)
    @Postsubmit(reason = "New test")
    @EnsurePasswordNotSet
    public void lockNow_logsMetric() {
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().lockNow(/* flags= */ 0);

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.LOCK_NOW_VALUE)
                    .whereAdminPackageName().isEqualTo(DPC_COMPONENT_NAME.getPackageName())
                    .whereInteger().isEqualTo(0)
            ).wasLogged();
        }
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordNotSet
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = LockNow.class)
    public void lockNow_noPasswordSet_turnsScreenOff() throws Exception {
        sDeviceState.dpc().devicePolicyManager().lockNow();

        Poll.forValue("isScreenOn", () -> TestApis.device().isScreenOn())
                .toBeEqualTo(false)
                .errorOnFail()
                .await();
    }

    @RequireFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordNotSet
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = LockNow.class)
    public void lockNow_automotive_noPasswordSet_doesNotTurnScreenOff() throws Exception {
        sDeviceState.dpc().devicePolicyManager().lockNow();

        assertThat(TestApis.device().isScreenOn()).isTrue();
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @EnsureScreenIsOn
    @EnsurePasswordSet
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = LockNow.class)
    public void lockNow_passwordSet_locksDevice() throws Exception {
        sDeviceState.dpc().devicePolicyManager().lockNow();

        Poll.forValue("isDeviceLocked", sLocalKeyguardManager::isDeviceLocked)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireTargetSdkVersion(max = N)
    @PolicyAppliesTest(policy = DeprecatedResetPassword.class)
    public void resetPassword_targetBeforeN_returnsFalse() {
        assertThat(sDeviceState.dpc()
                .devicePolicyManager().resetPassword(DEFAULT_PASSWORD, /* flags= */ 0)).isFalse();
    }

    @RequireDoesNotHaveFeature(FEATURE_AUTOMOTIVE)
    @RequireTargetSdkVersion(min = O)
    @PolicyAppliesTest(policy = DeprecatedResetPassword.class)
    public void resetPassword_targetAfterO_throwsSecurityException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager()
                        .resetPassword(DEFAULT_PASSWORD, /* flags= */ 0));
    }
}
