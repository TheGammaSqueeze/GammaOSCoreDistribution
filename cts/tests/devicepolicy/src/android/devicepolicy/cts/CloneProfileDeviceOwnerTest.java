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

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.UserManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.RequireMultiUserSupport;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class CloneProfileDeviceOwnerTest {
    @ClassRule
    @Rule
    public static DeviceState sDeviceState = new DeviceState();

    /**
     * Test creation of clone profile should not be allowed when device owner is set.
     */
    @Test
    @EnsureHasDeviceOwner
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @RequireMultiUserSupport
    public void createCloneProfile_hasDeviceOwner_fails() {
        assertThrows(NeneException.class,
                () -> TestApis.users().createUser()
                        .parent(TestApis.users().instrumented())
                        .type(TestApis.users().supportedType(UserManager.USER_TYPE_PROFILE_CLONE))
                        .create());
    }

    /**
     * Test creation of clone profile should be allowed when device owner is not set.
     */
    @Test
    @EnsureHasNoDeviceOwner
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @RequireMultiUserSupport
    public void createCloneProfile_noDeviceOwner_succeeds() {
        UserReference cloneUser = TestApis.users().createUser()
                .parent(TestApis.users().instrumented())
                .type(TestApis.users().supportedType(UserManager.USER_TYPE_PROFILE_CLONE))
                .create();

        try {
            assertThat(cloneUser.exists()).isTrue();
        } finally {
            cloneUser.remove();
        }
    }
}
