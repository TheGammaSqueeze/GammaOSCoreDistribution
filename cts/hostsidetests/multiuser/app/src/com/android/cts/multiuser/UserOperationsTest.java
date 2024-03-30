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
package com.android.cts.multiuser;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

public final class UserOperationsTest {
    private static final String TAG = UserOperationsTest.class.getSimpleName();

    private Context mContext;
    private UserManager mUserManager;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mUserManager = mContext.getSystemService(UserManager.class);
    }

    @Test
    public void removeUserWhenPossibleDeviceSide() throws Exception {
        Log.i(TAG, "Running removeUserWhenPossibleDeviceSide");

        int userId = Integer.parseInt(InstrumentationRegistry.getArguments().getString("userId"));
        boolean overrideDevicePolicy = Boolean.parseBoolean(InstrumentationRegistry.getArguments()
                .getString("overrideDevicePolicy"));

        int expectedResult = Integer.parseInt(InstrumentationRegistry.getArguments()
                .getString("expectedResult"));

        // get create User permissions
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.CREATE_USERS);
        try {
            int result = mUserManager.removeUserWhenPossible(UserHandle.of(userId),
                    overrideDevicePolicy);

            assertWithMessage("removeUserWhenPossible response").that(result)
                    .isEqualTo(expectedResult);
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }
}
