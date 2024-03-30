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

import static android.Manifest.permission.READ_NEARBY_STREAMING_POLICY;

import static com.android.bedstead.harrier.OptionalBoolean.TRUE;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.NearbyAppStreamingPolicy;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class NearbyAppStreamingPolicyTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    @PolicyAppliesTest(policy = NearbyAppStreamingPolicy.class)
    public void getNearbyAppStreamingPolicy_defaultToSameManagedAccountOnly() {
        RemoteDevicePolicyManager dpm = sDeviceState.dpc().devicePolicyManager();

        assertThat(dpm.getNearbyAppStreamingPolicy())
                .isEqualTo(DevicePolicyManager.NEARBY_STREAMING_SAME_MANAGED_ACCOUNT_ONLY);
    }

    @PolicyAppliesTest(policy = NearbyAppStreamingPolicy.class)
    public void setNearbyAppStreamingPolicy_policyApplied_works() {
        RemoteDevicePolicyManager dpm = sDeviceState.dpc().devicePolicyManager();
        int originalPolicy = dpm.getNearbyAppStreamingPolicy();

        dpm.setNearbyAppStreamingPolicy(DevicePolicyManager.NEARBY_STREAMING_DISABLED);

        try {
            assertThat(dpm.getNearbyAppStreamingPolicy())
                    .isEqualTo(DevicePolicyManager.NEARBY_STREAMING_DISABLED);
        } finally {
            dpm.setNearbyAppStreamingPolicy(originalPolicy);
        }
    }

    @CannotSetPolicyTest(policy = NearbyAppStreamingPolicy.class)
    public void setNearbyAppStreamingPolicy_policyIsNotAllowedToBeSet_throwsException() {
        RemoteDevicePolicyManager dpm = sDeviceState.dpc().devicePolicyManager();

        assertThrows(SecurityException.class, () ->
                dpm.setNearbyAppStreamingPolicy(DevicePolicyManager.NEARBY_STREAMING_DISABLED));
    }

    @Postsubmit(reason = "new test")
    @PolicyDoesNotApplyTest(policy = NearbyAppStreamingPolicy.class)
    @EnsureHasPermission(READ_NEARBY_STREAMING_POLICY)
    public void setNearbyAppStreamingPolicy_setEnabled_doesNotApply() {
        RemoteDevicePolicyManager dpm = sDeviceState.dpc().devicePolicyManager();
        int originalPolicy = dpm.getNearbyAppStreamingPolicy();

        dpm.setNearbyAppStreamingPolicy(DevicePolicyManager.NEARBY_STREAMING_ENABLED);

        try {
            assertThat(sLocalDevicePolicyManager.getNearbyAppStreamingPolicy()).isNotEqualTo(
                    DevicePolicyManager.NEARBY_STREAMING_ENABLED);
        } finally {
            dpm.setNearbyAppStreamingPolicy(originalPolicy);
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser(installInstrumentedApp = TRUE)
    @EnsureHasPermission(READ_NEARBY_STREAMING_POLICY)
    @EnsureDoesNotHavePermission({INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL})
    public void getNearbyAppStreamingPolicy_calledAcrossUsers_throwsException() {
        DevicePolicyManager dpm;
        try (PermissionContext p = TestApis.permissions()
                .withPermission(INTERACT_ACROSS_USERS_FULL)) {
            dpm = TestApis.context()
                    .instrumentedContextAsUser(sDeviceState.secondaryUser())
                    .getSystemService(DevicePolicyManager.class);
        }

        assertThrows(SecurityException.class, () -> dpm.getNearbyAppStreamingPolicy());
    }
}
