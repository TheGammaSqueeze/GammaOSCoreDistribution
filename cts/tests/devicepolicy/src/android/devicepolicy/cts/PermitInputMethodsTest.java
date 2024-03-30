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

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.permissions.CommonPermissions.QUERY_ADMIN_POLICY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.SetPermittedInputMethods;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.inputmethods.InputMethod;
import com.android.bedstead.nene.packages.Package;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class PermitInputMethodsTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final DevicePolicyManager sLocalDevicePolicyManager = TestApis.context()
            .instrumentedContext().getSystemService(DevicePolicyManager.class);

    private static final Set<String> SYSTEM_INPUT_METHODS_PACKAGES =
            TestApis.inputMethods().installedInputMethods().stream()
                    .map(InputMethod::pkg)
                    .filter(Package::hasSystemFlag)
                    .map(Package::packageName)
                    .collect(Collectors.toSet());

    private static final List<String> NON_SYSTEM_INPUT_METHOD_PACKAGES =
            TestApis.inputMethods().installedInputMethods().stream()
                    .map(InputMethod::pkg)
                    .filter(p -> !p.hasSystemFlag())
                    .map(Package::packageName)
                    .collect(Collectors.toList());

    @After
    public void teardown() {
        sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                sDeviceState.dpc().componentName(), /* packageNames= */ null);
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = SetPermittedInputMethods.class)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_allPermitted() {
        assertThat(sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                sDeviceState.dpc().componentName(), /* packageNames= */ null)).isTrue();

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedInputMethods(sDeviceState.dpc().componentName())).isNull();
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethods()).isNull();
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).isNull();
    }

    @Postsubmit(reason = "New test")
    @CannotSetPolicyTest(
            policy = SetPermittedInputMethods.class, includeNonDeviceAdminStates = false)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_canNotSet_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                    sDeviceState.dpc().componentName(), /* packageNames= */ null);
        });
    }

    @Postsubmit(reason = "New test")
    @PolicyDoesNotApplyTest(policy = SetPermittedInputMethods.class)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_policyDoesNotApply_isNotSet() {
        assumeFalse("A system input method is required",
                SYSTEM_INPUT_METHODS_PACKAGES.isEmpty());

        List<String> enabledNonSystemImes = NON_SYSTEM_INPUT_METHOD_PACKAGES;

        assertThat(sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                sDeviceState.dpc().componentName(), /* packageNames= */ enabledNonSystemImes)
        ).isTrue();

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedInputMethods(sDeviceState.dpc().componentName()))
                .containsExactlyElementsIn(enabledNonSystemImes);
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethods()).isNull();
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethodsForCurrentUser()).isNull();
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = SetPermittedInputMethods.class)
    @EnsureHasPermission({INTERACT_ACROSS_USERS_FULL, QUERY_ADMIN_POLICY})
    public void setPermittedInputMethods_includesSetPlusSystem() {
        assumeFalse("A system input method is required",
                SYSTEM_INPUT_METHODS_PACKAGES.isEmpty());

        List<String> enabledNonSystemImes = NON_SYSTEM_INPUT_METHOD_PACKAGES;
        Set<String> permittedPlusSystem = new HashSet<>();
        permittedPlusSystem.addAll(SYSTEM_INPUT_METHODS_PACKAGES);
        permittedPlusSystem.addAll(enabledNonSystemImes);

        assertThat(sDeviceState.dpc().devicePolicyManager().setPermittedInputMethods(
                sDeviceState.dpc().componentName(), /* packageNames= */ enabledNonSystemImes)
        ).isTrue();

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedInputMethods(sDeviceState.dpc().componentName()))
                .containsExactlyElementsIn(enabledNonSystemImes);
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethods())
                .containsExactlyElementsIn(permittedPlusSystem);
        assertThat(sLocalDevicePolicyManager.getPermittedInputMethodsForCurrentUser())
                .containsExactlyElementsIn(permittedPlusSystem);
    }
}
