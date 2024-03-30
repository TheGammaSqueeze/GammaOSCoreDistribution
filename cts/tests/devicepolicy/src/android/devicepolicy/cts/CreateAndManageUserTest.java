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

import static android.os.UserManager.USER_OPERATION_ERROR_LOW_STORAGE;
import static android.provider.Settings.Global.SYS_STORAGE_THRESHOLD_MAX_BYTES;
import static android.provider.Settings.Global.SYS_STORAGE_THRESHOLD_PERCENTAGE;

import static com.android.bedstead.remotedpc.RemoteDpc.DPC_COMPONENT_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.expectThrows;

import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.policies.CreateAndManageUser;
import com.android.bedstead.nene.TestApis;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class CreateAndManageUserTest {

    private static final String TAG = "CreateAndManageUserTest";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Before
    public void setUp() {
        sDeviceState.requireCanSupportAdditionalUser();
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = CreateAndManageUser.class)
    public void createAndManageUser_returnUserHandle() {
        UserHandle userHandle = null;
        try {
            userHandle = createAndManageUser();

            assertThat(userHandle).isNotNull();
        } finally {
            removeUser(userHandle);
        }
    }

    @Postsubmit(reason = "new test")
    @CanSetPolicyTest(policy = CreateAndManageUser.class)
    public void createAndManageUser_lowStorage_throwOperationException() {
        try {
            TestApis.settings().global().putInt(SYS_STORAGE_THRESHOLD_PERCENTAGE, 100);
            TestApis.settings().global().putString(SYS_STORAGE_THRESHOLD_MAX_BYTES,
                    String.valueOf(Long.MAX_VALUE));

            UserManager.UserOperationException e = expectThrows(
                    UserManager.UserOperationException.class, this::createAndManageUser);

            assertThat(e.getUserOperationResult()).isEqualTo(USER_OPERATION_ERROR_LOW_STORAGE);
        } finally {
            TestApis.settings().global().reset();
        }
    }

    private UserHandle createAndManageUser() {
        return createAndManageUser(/* adminExtras= */ null, /* flags= */ 0);
    }

    private UserHandle createAndManageUser(PersistableBundle adminExtras, int flags) {
        final String testUserName = "TestUser_" + System.currentTimeMillis();
        final UserHandle userHandle = sDeviceState.dpc().devicePolicyManager().createAndManageUser(
                DPC_COMPONENT_NAME, testUserName, DPC_COMPONENT_NAME, adminExtras, flags);
        return userHandle;
    }

    private void removeUser(UserHandle userHandle) {
        if (userHandle != null) {
            TestApis.users().find(userHandle).remove();
        }
    }
}
