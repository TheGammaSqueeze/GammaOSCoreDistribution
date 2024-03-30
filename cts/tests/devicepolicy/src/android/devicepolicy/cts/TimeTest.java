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

import android.app.admin.DevicePolicyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.AutoTimeRequired;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class TimeTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    @PolicyAppliesTest(policy = AutoTimeRequired.class)
    public void setAutoTimeRequired_false_setsAutoTimeNotRequired() {
        boolean originalValue = sLocalDevicePolicyManager.getAutoTimeRequired();

        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), false);

            assertThat(sDeviceState.dpc().devicePolicyManager().getAutoTimeRequired()).isFalse();

        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }

    @PolicyAppliesTest(policy = AutoTimeRequired.class)
    public void setAutoTimeRequired_true_setsAutoTimeRequired() {
        boolean originalValue = sLocalDevicePolicyManager.getAutoTimeRequired();

        try {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sDeviceState.dpc().devicePolicyManager().getAutoTimeRequired()).isTrue();

        } finally {
            sDeviceState.dpc().devicePolicyManager().setAutoTimeRequired(
                    sDeviceState.dpc().componentName(), originalValue);
        }
    }
}
