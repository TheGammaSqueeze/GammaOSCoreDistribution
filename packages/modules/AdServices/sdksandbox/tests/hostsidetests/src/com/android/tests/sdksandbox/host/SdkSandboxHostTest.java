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

package com.android.tests.sdksandbox.host;

import static com.google.common.truth.Truth.assertThat;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class SdkSandboxHostTest extends BaseHostJUnit4Test {

    private boolean mMultiuserSupported;

    private int mInitialUserId;
    private HashSet<Integer> mOriginalUsers;

    @Before
    public void setUp() throws Exception {
        assertThat(getBuild()).isNotNull();  // ensure build has been set before test is run

        mMultiuserSupported = getDevice().isMultiUserSupported();
        mInitialUserId = getDevice().getCurrentUser();
        mOriginalUsers = new HashSet<>(getDevice().listUsers());
    }

    @After
    public void teardown() throws Exception {
        removeTestUsers();
    }

    @Test
    public void testSdkSandboxPackageInstallatedForAllUsers() throws Exception {
        // Test installation for new users
        if (mMultiuserSupported) {
            final int testUser = getDevice().createUser("Test User");
        }

        for (Integer userId : getDevice().listUsers()) {
            // TODO(b/204991850): add below code after package install is handled
            // assertTrue(getDevice().isPackageInstalled(SUPPLEMENTAL_PROCESS_PKG,
            //                                          Integer.toString(userId)));
        }
    }

    private void removeTestUsers() throws Exception {
        if (!mMultiuserSupported) {
            return;
        }

        getDevice().switchUser(mInitialUserId);
        for (Integer userId : getDevice().listUsers()) {
            if (!mOriginalUsers.contains(userId)) {
                getDevice().removeUser(userId);
            }
        }
    }
}
