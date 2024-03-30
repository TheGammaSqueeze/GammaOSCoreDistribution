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

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.queryable.queries.ServiceQuery.service;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(BedsteadJUnit4.class)
public final class DeviceOwnerTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final TestApp sAccountManagementApp = sDeviceState.testApps()
            .query()
            // TODO(b/198417584): Support Querying XML resources in TestApp.
            // TODO(b/198590265) Filter for the correct account type.
            .whereServices().contains(
                    service().serviceClass().className()
                            .isEqualTo("com.android.bedstead.testapp.AccountManagementApp"
                                    + ".TestAppAccountAuthenticatorService"))
            .get();
    private static final TestApp sDpcApp = sDeviceState.testApps()
            .query().whereIsDeviceAdmin().isTrue()
            .get();

    private static final String EXISTING_ACCOUNT_TYPE =
            "com.android.bedstead.testapp.AccountManagementApp.account.type";
    private static final String SET_DEVICE_OWNER_COMMAND = "dpm set-device-owner";
    private static final Account ACCOUNT_WITH_EXISTING_TYPE =
            new Account("user0", EXISTING_ACCOUNT_TYPE);
    private static final String TEST_PASSWORD = "password";

    private static final AccountManager sAccountManager =
            sContext.getSystemService(AccountManager.class);
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasDeviceOwner
    public void setDeviceOwner_setsDeviceOwner() {
        assertThat(sDevicePolicyManager.isAdminActive(sDeviceState.dpc().componentName()))
                .isTrue();
        assertThat(sDevicePolicyManager.isDeviceOwnerApp(sDeviceState.dpc().packageName()))
                .isTrue();
        assertThat(sDevicePolicyManager.getDeviceOwner())
                .isEqualTo(sDeviceState.dpc().packageName());
    }


    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasNoDpc
    public void setDeviceOwnerViaAdb_deviceHasAccount_fails()
            throws InterruptedException {
        try (TestAppInstance accountAuthenticatorApp =
                     sAccountManagementApp.install(TestApis.users().instrumented());
             TestAppInstance dpcApp = sDpcApp.install(TestApis.users().instrumented())) {
            addAccount();

            assertThrows(AdbException.class, () ->
                    ShellCommand
                            .builderForUser(
                                    TestApis.users().instrumented(), SET_DEVICE_OWNER_COMMAND)
                            .addOperand(RemoteDpc.DPC_COMPONENT_NAME.flattenToString())
                            .execute());
            assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
            DevicePolicyManager dpm = TestApis.context().instrumentedContext()
                    .getSystemService(DevicePolicyManager.class);
            // After attempting and failing to set the device owner, it will remain as an active
            // admin for a short while
            Poll.forValue("Active admins", dpm::getActiveAdmins)
                    .toMeet(i -> i == null || !i.contains(RemoteDpc.DPC_COMPONENT_NAME))
                    .errorOnFail("Expected active admins to not contain RemoteDPC")
                    .timeout(Duration.ofMinutes(5))
                    .await();
        }
    }

    @UserTest({UserType.PRIMARY_USER, UserType.SECONDARY_USER})
    @EnsureHasDeviceOwner
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Postsubmit(reason = "new test")
    public void getDeviceOwnerNameOnAnyUser_returnsDeviceOwnerName() {
        assertThat(sDevicePolicyManager.getDeviceOwnerNameOnAnyUser())
                .isEqualTo(sDeviceState.dpc().packageName());
    }

    @UserTest({UserType.PRIMARY_USER, UserType.SECONDARY_USER})
    @EnsureHasDeviceOwner
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Postsubmit(reason = "new test")
    public void getDeviceOwnerComponentOnAnyUser_returnsDeviceOwnerComponent() {
        assertThat(sDevicePolicyManager.getDeviceOwnerComponentOnAnyUser())
                .isEqualTo(sDeviceState.dpc().componentName());
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
}
