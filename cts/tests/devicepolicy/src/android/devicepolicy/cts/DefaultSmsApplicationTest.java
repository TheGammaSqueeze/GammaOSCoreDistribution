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


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.admin.RemoteDevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Telephony;
import android.telephony.TelephonyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DefaultSmsApplication;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.remotedpc.RemotePolicyManager;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

// TODO(b/198442101): Add tests for the COPE case when we can sideload system apps
@RunWith(BedsteadJUnit4.class)
public final class DefaultSmsApplicationTest {
    @ClassRule
    @Rule
    public static DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final TestApp sSmsApp = sDeviceState.testApps()
            .query()
            .wherePackageName()
            // TODO(b/198420874): Query for the intent filters relevant to the SMS tests
            .isEqualTo("com.android.bedstead.testapp.SmsApp")
            .get();
    private static final String FAKE_SMS_APP_NAME = "FakeSmsAppName";

    private ComponentName mAdmin;
    private RemoteDevicePolicyManager mDpm;
    private TelephonyManager mTelephonyManager;
    private RoleManager mRoleManager;

    @Before
    public void setUp() {
        RemotePolicyManager dpc = sDeviceState.dpc();
        mAdmin = dpc.componentName();
        mDpm = dpc.devicePolicyManager();
        mTelephonyManager = sContext.getSystemService(TelephonyManager.class);
        mRoleManager = sContext.getSystemService(RoleManager.class);
    }

    // TODO(b/198588696): Add support is @RequireSmsCapable and @RequireNotSmsCapable
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = DefaultSmsApplication.class)
    public void setDefaultSmsApplication_works() {
        assumeTrue(mTelephonyManager.isSmsCapable()
                || (mRoleManager != null && mRoleManager.isRoleAvailable(RoleManager.ROLE_SMS)));
        String previousSmsAppName = getDefaultSmsPackage();
        try (TestAppInstance smsApp = sSmsApp.install()) {
            mDpm.setDefaultSmsApplication(mAdmin, smsApp.packageName());

            assertThat(getDefaultSmsPackage()).isEqualTo(smsApp.packageName());
        } finally {
            mDpm.setDefaultSmsApplication(mAdmin, previousSmsAppName);
        }
    }

    // TODO(b/198588696): Add support is @RequireSmsCapable and @RequireNotSmsCapable
    @Postsubmit(reason = "new test")
    @PolicyDoesNotApplyTest(policy = DefaultSmsApplication.class)
    public void setDefaultSmsApplication_unchanged() {
        assumeTrue(mTelephonyManager.isSmsCapable()
                || (mRoleManager != null && mRoleManager.isRoleAvailable(RoleManager.ROLE_SMS)));
        String previousSmsAppName = getDefaultSmsPackage();
        try (TestAppInstance smsApp = sSmsApp.install()) {
            mDpm.setDefaultSmsApplication(mAdmin, smsApp.packageName());

            assertThat(getDefaultSmsPackage()).isEqualTo(previousSmsAppName);
        } finally {
            mDpm.setDefaultSmsApplication(mAdmin, previousSmsAppName);
        }
    }

    // TODO(b/198588696): Add support is @RequireSmsCapable and @RequireNotSmsCapable
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DefaultSmsApplication.class)
    public void setDefaultSmsApplication_smsPackageDoesNotExist_unchanged() {
        assumeTrue(mTelephonyManager.isSmsCapable()
                || (mRoleManager != null && mRoleManager.isRoleAvailable(RoleManager.ROLE_SMS)));
        String previousSmsAppName = getDefaultSmsPackage();

        mDpm.setDefaultSmsApplication(mAdmin, FAKE_SMS_APP_NAME);

        try {
            assertThat(getDefaultSmsPackage()).isEqualTo(previousSmsAppName);
        } finally {
            mDpm.setDefaultSmsApplication(mAdmin, previousSmsAppName);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DefaultSmsApplication.class)
    public void setDefaultSmsApplication_nullAdmin_throwsException() {
        try (TestAppInstance smsApp = sSmsApp.install()) {

            assertThrows(NullPointerException.class, () ->
                    mDpm.setDefaultSmsApplication(
                            /* admin= */ null, smsApp.packageName()));
        }
    }

    // TODO(b/198588696): Add support is @RequireSmsCapable and @RequireNotSmsCapable
    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = DefaultSmsApplication.class)
    public void setDefaultSmsApplication_notSmsCapable_unchanged() {
        assumeTrue(!mTelephonyManager.isSmsCapable()
                && (mRoleManager == null || !mRoleManager.isRoleAvailable(RoleManager.ROLE_SMS)));
        String previousSmsAppName = getDefaultSmsPackage();
        try (TestAppInstance smsApp = sSmsApp.install()) {
            mDpm.setDefaultSmsApplication(mAdmin, smsApp.packageName());

            assertThat(getDefaultSmsPackage()).isEqualTo(previousSmsAppName);
        } finally {
            mDpm.setDefaultSmsApplication(mAdmin, previousSmsAppName);
        }
    }

    @Postsubmit(reason = "new test")
    // We don't include non device admin states as passing a null admin is a NullPointerException
    @CannotSetPolicyTest(policy = DefaultSmsApplication.class, includeNonDeviceAdminStates = false)
    public void setDefaultSmsApplication_invalidAdmin_throwsException() {
        try (TestAppInstance smsApp = sSmsApp.install()) {

            assertThrows(SecurityException.class, () ->
                    mDpm.setDefaultSmsApplication(mAdmin, smsApp.packageName()));
        }
    }

    private String getDefaultSmsPackage() {
        return Telephony.Sms.getDefaultSmsPackage(sContext);
    }
}
