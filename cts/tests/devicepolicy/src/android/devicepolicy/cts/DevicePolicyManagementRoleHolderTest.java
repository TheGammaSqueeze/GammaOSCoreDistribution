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

import static android.Manifest.permission.LAUNCH_DEVICE_MANAGER_SETUP;
import static android.app.admin.DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_FINALIZATION;
import static android.app.admin.DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE;
import static android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE;
import static android.content.Intent.ACTION_MANAGED_PROFILE_REMOVED;
import static android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_ROLE_HOLDERS;
import static com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;
import static com.android.queryable.queries.ServiceQuery.service;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.ProvisioningException;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;

import com.android.bedstead.deviceadminapp.DeviceAdminApp;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasNoSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireMultiUserSupport;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.CddTest;
import com.android.eventlib.truth.EventLogsSubject;
import com.android.queryable.queries.ActivityQuery;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO(b/228016400): replace usages of createAndProvisionManagedProfile with a nene API
@RunWith(BedsteadJUnit4.class)
public class DevicePolicyManagementRoleHolderTest { // TODO: This is crashing on non-headless - figure it out - on headless it d't run with btest so follow up....
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final ComponentName DEVICE_ADMIN_COMPONENT_NAME =
            DeviceAdminApp.deviceAdminComponentName(sContext);
    private static final String PROFILE_OWNER_NAME = "testDeviceAdmin";
    private static final ManagedProfileProvisioningParams MANAGED_PROFILE_PROVISIONING_PARAMS =
            createManagedProfileProvisioningParamsBuilder().build();
    private static final String EXISTING_ACCOUNT_TYPE =
            "com.android.bedstead.testapp.AccountManagementApp.account.type";
    private static final Account ACCOUNT_WITH_EXISTING_TYPE =
            new Account("user0", EXISTING_ACCOUNT_TYPE);
    private static final String TEST_PASSWORD = "password";
    private static final String MANAGED_USER_NAME = "managed user name";

    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static final ActivityQuery<?> sQueryForRoleHolderTrustedSourceAction =
            (ActivityQuery<?>)
            activity().intentFilters().contains(
                intentFilter().actions().contains(
                        ACTION_ROLE_HOLDER_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE))
                    .permission().isEqualTo(LAUNCH_DEVICE_MANAGER_SETUP);
    private static final ActivityQuery<?> sQueryForRoleHolderManagedProfileAction =
            (ActivityQuery<?>)
            activity().intentFilters().contains(
                intentFilter().actions().contains(
                        ACTION_ROLE_HOLDER_PROVISION_MANAGED_PROFILE))
                    .permission().isEqualTo(LAUNCH_DEVICE_MANAGER_SETUP);
    private static final ActivityQuery<?> sQueryForRoleHolderFinalizationAction =
            (ActivityQuery<?>)
            activity().intentFilters().contains(
                intentFilter().actions().contains(
                        ACTION_ROLE_HOLDER_PROVISION_FINALIZATION))
                    .permission().isEqualTo(LAUNCH_DEVICE_MANAGER_SETUP);
    private static final TestApp sRoleHolderApp = sDeviceState.testApps()
            .query()
            .whereActivities()
            .contains(
                    sQueryForRoleHolderTrustedSourceAction,
                    sQueryForRoleHolderManagedProfileAction,
                    sQueryForRoleHolderFinalizationAction)
            .get();
    private static final AccountManager sAccountManager =
            sContext.getSystemService(AccountManager.class);
    private static final TestApp sAccountManagementApp = sDeviceState.testApps()
            .query()
            // TODO(b/198417584): Support Querying XML resources in TestApp.
            // TODO(b/198590265) Filter for the correct account type.
            .whereServices().contains(
                    service().serviceClass().className()
                            .isEqualTo("com.android.bedstead.testapp.AccountManagementApp"
                                    + ".TestAppAccountAuthenticatorService"))
            .get();

    @Ignore("b/268616097 fix issue with pre-existing accounts on the device")
    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @EnsureHasNoSecondaryUser
    @Test
    @CddTest(requirements = {"3.9.4/C-3-1"})
    public void createAndProvisionManagedProfile_roleHolderIsInWorkProfile()
            throws ProvisioningException, InterruptedException {
        UserHandle profile = null;
        String roleHolderPackageName = null;
        try (TestAppInstance roleHolderApp = sRoleHolderApp.install()) {
            try {
                roleHolderPackageName = roleHolderApp.packageName();
                TestApis.devicePolicy().setDevicePolicyManagementRoleHolder(roleHolderPackageName);

                profile = sDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PROVISIONING_PARAMS);

                UserReference userReference = UserReference.of(profile);
                Poll.forValue(() -> TestApis.packages().installedForUser(userReference))
                        .toMeet(packages -> packages.contains(
                                Package.of(roleHolderApp.packageName())))
                        .errorOnFail("Role holder package not installed on the managed profile.")
                        .await();
            } finally {
                if (roleHolderPackageName != null) {
                    TestApis.devicePolicy()
                            .unsetDevicePolicyManagementRoleHolder(roleHolderPackageName);
                }
            }
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
        }
    }

    @Ignore("b/268616097 fix issue with pre-existing accounts on the device")
    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasDeviceOwner
    @RequireRunOnPrimaryUser
    @EnsureHasNoSecondaryUser
    @RequireMultiUserSupport
    @Test
    @CddTest(requirements = {"3.9.4/C-3-1"})
    public void createAndManageUser_roleHolderIsInManagedUser() throws InterruptedException {
        UserHandle managedUser = null;
        String roleHolderPackageName = null;
        try (TestAppInstance roleHolderApp = sRoleHolderApp.install()) {
            try {
                roleHolderPackageName = roleHolderApp.packageName();
                TestApis.devicePolicy().setDevicePolicyManagementRoleHolder(roleHolderPackageName);

                managedUser = sDeviceState.dpc().devicePolicyManager().createAndManageUser(
                        RemoteDpc.DPC_COMPONENT_NAME,
                        MANAGED_USER_NAME,
                        RemoteDpc.DPC_COMPONENT_NAME,
                        /* adminExtras= */ null,
                        /* flags= */ 0);

                UserReference userReference = UserReference.of(managedUser);
                Poll.forValue(() -> TestApis.packages().installedForUser(userReference))
                        .toMeet(packages -> packages.contains(
                                Package.of(roleHolderApp.packageName())))
                        .errorOnFail("Role holder package not installed on the managed user.")
                        .await();
            } finally {
                if (roleHolderPackageName != null) {
                    TestApis.devicePolicy()
                            .unsetDevicePolicyManagementRoleHolder(roleHolderPackageName);
                }
            }
        } finally {
            if (managedUser != null) {
                TestApis.users().find(managedUser).remove();
            }
        }
    }

    @Ignore("b/268616097 fix issue with pre-existing accounts on the device")
    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @EnsureHasNoSecondaryUser
    @Test
    public void profileRemoved_roleHolderReceivesBroadcast() throws Exception {
        String roleHolderPackageName = null;
        try (TestAppInstance roleHolderApp = sRoleHolderApp.install()) {
            try {
                roleHolderPackageName = roleHolderApp.packageName();
                TestApis.devicePolicy().setDevicePolicyManagementRoleHolder(roleHolderPackageName);
                UserHandle profile = sDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PROVISIONING_PARAMS);

                TestApis.users().find(profile).remove();

                EventLogsSubject.assertThat(roleHolderApp.events().broadcastReceived()
                                .whereIntent().action().isEqualTo(ACTION_MANAGED_PROFILE_REMOVED))
                        .eventOccurred();
            } finally {
                if (roleHolderPackageName != null) {
                    TestApis.devicePolicy().unsetDevicePolicyManagementRoleHolder(
                            roleHolderPackageName);
                }
            }
        }
    }

    @Ignore("b/268616097 fix issue with pre-existing accounts on the device")
    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @EnsureHasNoSecondaryUser
    @Test
    public void profilePaused_roleHolderReceivesBroadcast() throws Exception {
        String roleHolderPackageName = null;
        try (TestAppInstance roleHolderApp = sRoleHolderApp.install()) {
            try {
                roleHolderPackageName = roleHolderApp.packageName();
                TestApis.devicePolicy().setDevicePolicyManagementRoleHolder(roleHolderPackageName);
                UserHandle profile = sDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PROVISIONING_PARAMS);

                TestApis.users().find(profile).setQuietMode(true);

                EventLogsSubject.assertThat(roleHolderApp.events().broadcastReceived()
                                .whereIntent().action().isEqualTo(
                                        ACTION_MANAGED_PROFILE_UNAVAILABLE))
                        .eventOccurred();
            } finally {
                if (roleHolderPackageName != null) {
                    TestApis.devicePolicy().unsetDevicePolicyManagementRoleHolder(
                            roleHolderPackageName);
                }
            }
        }
    }

    @Ignore("b/268616097 fix issue with pre-existing accounts on the device")
    @Postsubmit(reason = "new test")
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @EnsureHasNoSecondaryUser
    @Test
    public void profileStarted_roleHolderReceivesBroadcast() throws Exception {
        String roleHolderPackageName = null;
        try (TestAppInstance roleHolderApp = sRoleHolderApp.install()) {
            try {
                roleHolderPackageName = roleHolderApp.packageName();
                TestApis.devicePolicy().setDevicePolicyManagementRoleHolder(roleHolderPackageName);
                UserHandle profile = sDevicePolicyManager.createAndProvisionManagedProfile(
                        MANAGED_PROFILE_PROVISIONING_PARAMS);
                TestApis.users().find(profile).setQuietMode(true);

                TestApis.users().find(profile).setQuietMode(false);

                EventLogsSubject.assertThat(roleHolderApp.events().broadcastReceived()
                                .whereIntent().action().isEqualTo(ACTION_MANAGED_PROFILE_AVAILABLE))
                        .eventOccurred();
            } finally {
                if (roleHolderPackageName != null) {
                    TestApis.devicePolicy().unsetDevicePolicyManagementRoleHolder(
                            roleHolderPackageName);
                }
            }
        }
    }

    @Ignore("b/268616097 fix issue with pre-existing accounts on the device")
    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoSecondaryUser
    @EnsureHasNoWorkProfile
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_noUsersAndAccounts_returnsTrue()
            throws Exception {
        // TODO(b/222669810): add ensureHasNoAccounts annotation
        waitForNoAccounts();

        assertThat(
                sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification())
                .isTrue();
    }

    // TODO(b/222669810): add ensureHasNoAccounts annotation
    @Ignore("b/268616097 fix issue with pre-existing accounts on the device")
    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoSecondaryUser
    @EnsureHasNoWorkProfile
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireMultiUserSupport
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withUsers_returnsFalse()
            throws Exception {
        resetInternalShouldAllowBypassingState();
        // TODO(b/230096658): resetInternalShouldAllowBypassingState requires no additional
        //  profiles/users on the device to be able to set a role holder, switch to using
        //  @EnsureHasSecondaryUser once we add a testAPI for resetInternalShouldAllowBypassingState.
        final UserType secondaryUserType =
                TestApis.users().supportedType(SECONDARY_USER_TYPE_NAME);
        TestApis.users().createUser().type(secondaryUserType).create();

        assertThat(
                sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification())
                .isFalse();
    }

    // TODO(b/222669810): add ensureHasNoAccounts annotation
    @Ignore("b/268616097 fix issue with pre-existing accounts on the device")
    @Postsubmit(reason = "New test")
    @Test
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoSecondaryUser
    @EnsureHasNoWorkProfile
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withProfile_returnsFalse()
            throws Exception {
        resetInternalShouldAllowBypassingState();
        // TODO(b/230096658): resetInternalShouldAllowBypassingState requires no additional
        //  profiles/users on the device to be able to set a role holder, switch to using
        //  @EnsureHasWorkProfile once we add a testAPI for resetInternalShouldAllowBypassingState.
        sDevicePolicyManager.createAndProvisionManagedProfile(
                createManagedProfileProvisioningParamsBuilder().build());

        assertThat(
                sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification())
                .isFalse();
    }

    @Ignore("b/268616097 fix issue with pre-existing accounts on the device")
    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_ROLE_HOLDERS)
    @EnsureHasNoSecondaryUser
    @EnsureHasNoWorkProfile
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withAccounts_returnsFalse()
            throws Exception {
        resetInternalShouldAllowBypassingState();
        try (TestAppInstance accountAuthenticatorApp =
                     sAccountManagementApp.install(TestApis.users().instrumented())) {
            addAccount();

            assertThat(
                    sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification())
                    .isFalse();
        }
    }

    @Ignore("b/268616097 fix issue with pre-existing accounts on the device")
    @Postsubmit(reason = "New test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_ROLE_HOLDERS)
    public void shouldAllowBypassingDevicePolicyManagementRoleQualification_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDevicePolicyManager.shouldAllowBypassingDevicePolicyManagementRoleQualification());
    }

    /**
     * Blocks until an account is added.
     */
    private void addAccount() {
        Poll.forValue("account created success", this::addAccountOnce)
                .toBeEqualTo(true)
                .errorOnFail()
                .await();
    }

    private boolean addAccountOnce() {
        return sAccountManager.addAccountExplicitly(
                ACCOUNT_WITH_EXISTING_TYPE,
                TEST_PASSWORD,
                /* userdata= */ null);
    }

    // TODO(b/230096658): move to nene and replace with testAPI
    private void resetInternalShouldAllowBypassingState() throws Exception {
        TestApis.devicePolicy().setDevicePolicyManagementRoleHolder("PACKAGE_1");
        try (TestAppInstance accountAuthenticatorApp =
                     sAccountManagementApp.install(TestApis.users().instrumented())) {
            addAccount();
            TestApis.devicePolicy().setDevicePolicyManagementRoleHolder("PACKAGE_2");
        }
        waitForNoAccounts();
    }

    private void waitForNoAccounts() {
        AccountManager am = AccountManager.get(sContext);
        Poll.forValue(
                "Number of accounts",
                ()-> am.getAccounts().length).toBeEqualTo(0).errorOnFail().await();
    }

    private static ManagedProfileProvisioningParams.Builder
            createManagedProfileProvisioningParamsBuilder() {
        return new ManagedProfileProvisioningParams.Builder(
                DEVICE_ADMIN_COMPONENT_NAME,
                PROFILE_OWNER_NAME);
    }
}
