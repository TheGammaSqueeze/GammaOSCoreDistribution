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

import static android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS;

import static com.android.queryable.queries.IntentFilterQuery.intentFilter;
import static com.android.queryable.queries.ServiceQuery.service;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OperationCanceledException;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.AccountManagement;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.remotedpc.RemotePolicyManager;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class AccountManagementTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final TestApp sAccountManagementApp = sDeviceState.testApps()
            .query()
            // TODO(b/198590265) Filter for the correct account type.
            .whereServices().contains(
                    service().intentFilters().contains(
                            intentFilter().actions().contains(
                                    "android.accounts.AccountAuthenticator"))
                    )
            .get();
    private static final String EXISTING_ACCOUNT_TYPE =
            "com.android.bedstead.testapp.AccountManagementApp.account.type";
    private static final String FAKE_ACCOUNT_TYPE = "com.placeholder.account";
    private static final Account ACCOUNT_WITH_EXISTING_TYPE
            = new Account("user0", EXISTING_ACCOUNT_TYPE);

    private ComponentName mAdmin;
    private RemoteDevicePolicyManager mDpm;
    private AccountManager mAccountManager;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = sDeviceState.dpc();
        mAdmin = dpc.componentName();
        mDpm = dpc.devicePolicyManager();
        mAccountManager = sContext.getSystemService(AccountManager.class);
    }

    @PolicyAppliesTest(policy = AccountManagement.class)
    public void getAccountTypesWithManagementDisabled_emptyByDefault() {
        assertThat(mDpm.getAccountTypesWithManagementDisabled()).isEmpty();
    }

    @Postsubmit(reason = "new test")
    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = AccountManagement.class, includeNonDeviceAdminStates = false)
    public void setAccountTypesWithManagementDisabled_invalidAdmin_throwsException() {
        assertThrows(OperationCanceledException.class, () ->
                mDpm.setAccountManagementDisabled(
                        mAdmin, FAKE_ACCOUNT_TYPE, /* disabled= */ false));
    }

    @CanSetPolicyTest(policy = AccountManagement.class, singleTestOnly = true)
    public void setAccountTypesWithManagementDisabled_nullAdmin_throwsException() {
        assertThrows(NullPointerException.class, () ->
                mDpm.setAccountManagementDisabled(
                        /* admin= */ null, FAKE_ACCOUNT_TYPE, /* disabled= */ false));
    }

    @PolicyAppliesTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_disableAccountType_works() {
        try {
            mDpm.setAccountManagementDisabled(mAdmin, FAKE_ACCOUNT_TYPE, /* disabled= */ true);

            assertThat(mDpm.getAccountTypesWithManagementDisabled().length).isEqualTo(1);
            assertThat(mDpm.getAccountTypesWithManagementDisabled()[0])
                    .isEqualTo(FAKE_ACCOUNT_TYPE);
        } finally {
            mDpm.setAccountManagementDisabled(mAdmin, FAKE_ACCOUNT_TYPE, /* disabled= */ false);
        }
    }

    @PolicyAppliesTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_addSameAccountTypeTwice_presentOnlyOnce() {
        try {
            mDpm.setAccountManagementDisabled(mAdmin, FAKE_ACCOUNT_TYPE, /* disabled= */ true);
            mDpm.setAccountManagementDisabled(mAdmin, FAKE_ACCOUNT_TYPE, /* disabled= */ true);

            assertThat(mDpm.getAccountTypesWithManagementDisabled().length).isEqualTo(1);
            assertThat(mDpm.getAccountTypesWithManagementDisabled()[0])
                    .isEqualTo(FAKE_ACCOUNT_TYPE);
        } finally {
            mDpm.setAccountManagementDisabled(mAdmin, FAKE_ACCOUNT_TYPE, /* disabled= */ false);
        }
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void setAccountManagementDisabled_disableThenEnable_noDisabledAccountTypes() {
        mDpm.setAccountManagementDisabled(mAdmin, FAKE_ACCOUNT_TYPE, /* disabled= */ true);
        mDpm.setAccountManagementDisabled(mAdmin, FAKE_ACCOUNT_TYPE, /* disabled= */ false);

        assertThat(mDpm.getAccountTypesWithManagementDisabled()).isEmpty();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    public void addAccount_fromDpcWithAccountManagementDisabled_accountAdded()
            throws Exception {
        try (TestAppInstance accountAuthenticatorApp = sAccountManagementApp.install()) {
            mDpm.setAccountManagementDisabled(mAdmin, EXISTING_ACCOUNT_TYPE, /* disabled= */ true);

            // Management is disabled, but the DO/PO is still allowed to use the APIs
            Bundle result = addAccountWithType(sDeviceState.dpc(), EXISTING_ACCOUNT_TYPE);

            assertThat(result.getString(AccountManager.KEY_ACCOUNT_TYPE))
                    .isEqualTo(EXISTING_ACCOUNT_TYPE);
        } finally {
            mDpm.setAccountManagementDisabled(mAdmin, EXISTING_ACCOUNT_TYPE, /* disabled= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    public void addAccount_fromDpcWithDisallowModifyAccountsRestriction_accountAdded()
            throws Exception {
        try (TestAppInstance accountAuthenticatorApp = sAccountManagementApp.install()) {
            mDpm.addUserRestriction(mAdmin, DISALLOW_MODIFY_ACCOUNTS);

            // Management is disabled, but the DO/PO is still allowed to use the APIs
            Bundle result = addAccountWithType(sDeviceState.dpc(), EXISTING_ACCOUNT_TYPE);

            assertThat(result.getString(AccountManager.KEY_ACCOUNT_TYPE))
                    .isEqualTo(EXISTING_ACCOUNT_TYPE);
        } finally {
            mDpm.clearUserRestriction(mAdmin, DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    public void removeAccount_fromDpcWithDisallowModifyAccountsRestriction_accountRemoved()
            throws Exception {
        try (TestAppInstance accountAuthenticatorApp = sAccountManagementApp.install()) {
            mDpm.addUserRestriction(mAdmin, UserManager.DISALLOW_MODIFY_ACCOUNTS);

            // Management is disabled, but the DO/PO is still allowed to use the APIs
            addAccountWithType(sDeviceState.dpc(), EXISTING_ACCOUNT_TYPE);
            Bundle result = removeAccount(sDeviceState.dpc(), ACCOUNT_WITH_EXISTING_TYPE);

            assertThat(result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT)).isTrue();
        } finally {
            mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void addAccount_withDisallowModifyAccountsRestriction_throwsException() {
        try (TestAppInstance accountAuthenticatorApp = sAccountManagementApp.install()) {
            mDpm.addUserRestriction(mAdmin, UserManager.DISALLOW_MODIFY_ACCOUNTS);

            assertThrows(OperationCanceledException.class, () ->
                    addAccountWithTypeOnce(EXISTING_ACCOUNT_TYPE));
        } finally {
            mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    public void removeAccount_withDisallowModifyAccountsRestriction_throwsException()
            throws Exception {
        try (TestAppInstance accountAuthenticatorApp = sAccountManagementApp.install()) {
            addAccountFromInstrumentedAppWithType(EXISTING_ACCOUNT_TYPE);
            mDpm.addUserRestriction(mAdmin, UserManager.DISALLOW_MODIFY_ACCOUNTS);

            assertThrows(OperationCanceledException.class, () ->
                    removeAccountFromInstrumentedApp(ACCOUNT_WITH_EXISTING_TYPE));
        } finally {
            // Account is automatically removed when the test app is removed
            mDpm.clearUserRestriction(mAdmin, UserManager.DISALLOW_MODIFY_ACCOUNTS);
        }
    }

    @CanSetPolicyTest(policy = AccountManagement.class)
    public void addAccount_withAccountManagementDisabled_throwsException() {
        try (TestAppInstance accountAuthenticatorApp = sAccountManagementApp.install()) {
            mDpm.setAccountManagementDisabled(mAdmin, EXISTING_ACCOUNT_TYPE, /* disabled= */ true);

            assertThrows(OperationCanceledException.class, () ->
                    addAccountWithTypeOnce(EXISTING_ACCOUNT_TYPE));
        } finally {
            mDpm.setAccountManagementDisabled(mAdmin, EXISTING_ACCOUNT_TYPE, /* disabled= */ false);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = AccountManagement.class)
    public void removeAccount_withAccountManagementDisabled_throwsException()
            throws Exception {
        try (TestAppInstance accountAuthenticatorApp = sAccountManagementApp.install()) {
            addAccountFromInstrumentedAppWithType(EXISTING_ACCOUNT_TYPE);
            mDpm.setAccountManagementDisabled(mAdmin, EXISTING_ACCOUNT_TYPE, /* disabled= */ true);

            assertThrows(OperationCanceledException.class, () ->
                    removeAccountFromInstrumentedApp(ACCOUNT_WITH_EXISTING_TYPE));
        } finally {
            // Account is automatically removed when the test app is removed
            mDpm.setAccountManagementDisabled(mAdmin, EXISTING_ACCOUNT_TYPE, /* disabled= */ false);
        }
    }

    /**
     * Blocks until an account of {@code type} is added.
     */
    // TODO(b/199077745): Remove poll once AccountManager race condition is fixed
    private Bundle addAccountFromInstrumentedAppWithType(String type) {
        return Poll.forValue("created account bundle", () -> addAccountWithTypeOnce(type))
                .toNotBeNull()
                .errorOnFail()
                .await();
    }

    /**
     * Blocks until an account of {@code type} is added.
     */
    // TODO(b/199077745): Remove poll once AccountManager race condition is fixed
    private Bundle addAccountWithType(TestAppInstance testApp, String type) {
        return Poll.forValue("created account bundle", () -> addAccountWithTypeOnce(testApp, type))
                .toNotBeNull()
                .errorOnFail()
                .await();
    }

    private Bundle addAccountWithTypeOnce(String type) throws Exception {
        return mAccountManager.addAccount(
                type,
                /* authTokenType= */ null,
                /* requiredFeatures= */ null,
                /* addAccountOptions= */ null,
                /* activity= */ null,
                /* callback= */ null,
                /* handler= */ null).getResult();
    }

    private Bundle addAccountWithTypeOnce(TestAppInstance testApp, String type)
            throws Exception {
        return testApp.accountManager().addAccount(
                type,
                /* authTokenType= */ null,
                /* requiredFeatures= */ null,
                /* addAccountOptions= */ null,
                /* activity= */ null,
                /* callback= */ null,
                /* handler= */ null).getResult();
    }

    /**
     * Blocks until {@code account} is removed.
     */
    private Bundle removeAccountFromInstrumentedApp(Account account)
            throws Exception {
        return mAccountManager.removeAccount(
                account,
                /* activity= */ null,
                /* callback= */  null,
                /* handler= */ null)
                .getResult();
    }

    /**
     * Blocks until {@code account} is removed.
     */
    private Bundle removeAccount(TestAppInstance testApp, Account account)
            throws Exception {
        return testApp.accountManager().removeAccount(
                account,
                /* activity= */ null,
                /* callback= */  null,
                /* handler= */ null)
                .getResult();
    }
}
