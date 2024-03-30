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

import static com.android.bedstead.nene.permissions.CommonPermissions.QUERY_ADMIN_POLICY;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.PermittedAccessibilityServices;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.accessibility.AccessibilityService;
import com.android.bedstead.nene.packages.Package;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public class AccessibilityServicesTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final DevicePolicyManager sDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    private static final Set<String> SYSTEM_ACCESSIBILITY_SERVICE_PACKAGES =
            TestApis.accessibility().installedAccessibilityServices().stream()
                    .map(AccessibilityService::pkg)
                    .filter(Package::hasSystemFlag)
                    .map(Package::packageName)
                    .collect(Collectors.toSet());

    private static final String ACCESSIBILITY_SERVICE_PACKAGE_NAME = "pkg";

    @Before
    public void setUp() {
        // We can only proceed with the test if no non-system services are enabled
        sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(), /* packageNames= */ null);
        assertThat(TestApis.accessibility().enabledAccessibilityServices().stream()
                .map(i -> i.pkg())
                .filter(p -> !p.hasSystemFlag())
                .collect(Collectors.toSet())).isEmpty();
    }

    @After
    public void resetPermittedAccessibilityServices() {
        sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(), /* packageNames= */ null);
    }

    @PolicyAppliesTest(policy = PermittedAccessibilityServices.class)
    @EnsureHasPermission(QUERY_ADMIN_POLICY)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_nullPackageName_allServicesArePermitted() {
        sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(), /* packageNames= */ null);

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedAccessibilityServices(sDeviceState.dpc().componentName()))
                .isNull();
        assertThat(sDevicePolicyManager.getPermittedAccessibilityServices(
                TestApis.users().instrumented().id())).isNull();
    }

    @PolicyAppliesTest(policy = PermittedAccessibilityServices.class)
    @EnsureHasPermission(value = QUERY_ADMIN_POLICY)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_emptyList_onlyPermitsSystemServices() {
        sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(), /* packageNames= */ ImmutableList.of());

        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getPermittedAccessibilityServices(sDeviceState.dpc().componentName()))
                .isEmpty();
        // Move it into a set to avoid duplicates
        Set<String> permittedServices = new HashSet<>(
                sDevicePolicyManager.getPermittedAccessibilityServices(
                        TestApis.users().instrumented().id()));
        assertThat(permittedServices)
                .containsExactlyElementsIn(SYSTEM_ACCESSIBILITY_SERVICE_PACKAGES);
    }

    @PolicyAppliesTest(policy = PermittedAccessibilityServices.class)
    @EnsureHasPermission(value = QUERY_ADMIN_POLICY)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_includeNonSystemApp_permitsNonSystemApp() {
        sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(),
                ImmutableList.of(ACCESSIBILITY_SERVICE_PACKAGE_NAME));

        assertThat(sDeviceState.dpc().devicePolicyManager().getPermittedAccessibilityServices(
                sDeviceState.dpc().componentName())).containsExactly(
                        ACCESSIBILITY_SERVICE_PACKAGE_NAME);
        assertThat(sDevicePolicyManager.getPermittedAccessibilityServices(
                TestApis.users().instrumented().id()))
                .contains(ACCESSIBILITY_SERVICE_PACKAGE_NAME);
    }

    @PolicyAppliesTest(policy = PermittedAccessibilityServices.class)
    @EnsureHasPermission(QUERY_ADMIN_POLICY)
    @Postsubmit(reason = "new test")
    public void setPermittedAccessibilityServices_includeNonSystemApp_stillPermitsSystemApps() {
        sDeviceState.dpc().devicePolicyManager().setPermittedAccessibilityServices(
                sDeviceState.dpc().componentName(),
                ImmutableList.of(ACCESSIBILITY_SERVICE_PACKAGE_NAME));

        assertThat(sDeviceState.dpc().devicePolicyManager().getPermittedAccessibilityServices(
                sDeviceState.dpc().componentName())).containsExactly(
                        ACCESSIBILITY_SERVICE_PACKAGE_NAME);
        assertThat(sDevicePolicyManager.getPermittedAccessibilityServices(
                TestApis.users().instrumented().id()))
                .containsAtLeastElementsIn(SYSTEM_ACCESSIBILITY_SERVICE_PACKAGES);
    }
}
