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

package android.car.cts.builtin.os;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.car.builtin.os.UserManagerHelper;
import android.content.Context;
import android.os.Binder;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public final class UserManagerHelperTest {

    private static final String TAG = UserManagerHelperTest.class.getSimpleName();

    private static final int WAIT_TIME_FOR_OPERATION_MS = 60_000;
    private static final int WAIT_TIME_BEFORE_RETRY_MS = 1_000;
    private static final int WAIT_TIME_FOR_NEGATIVE_RESULT_MS = 30_000;

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    private Context mContext;
    private UserManager mUserManager;
    private UserHandle mUserToRemove;

    @Before
    public void setup() {
        mContext = mInstrumentation.getContext();
        mUserManager = mContext.getSystemService(UserManager.class);
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                android.Manifest.permission.CREATE_USERS);
        removeAnyPreCreatedUser();
    }

    private void removeAnyPreCreatedUser() {
        // Existing pre-created user can interfere with the test logic. Remove all existing
        // pre-created Users.
        List<UserHandle> allUsersHandles = UserManagerHelper.getUserHandles(mUserManager,
                /* excludePartial= */ true, /* excludeDying= */ true,
                /* excludePreCreated= */ false);
        for (UserHandle userHandle : allUsersHandles) {
            if (UserManagerHelper.isPreCreatedUser(mUserManager, userHandle)) {
                Log.v(TAG, "Removing pre-craeted user " + userHandle);
                boolean result = mUserManager.removeUser(userHandle);
                Log.v(TAG, "Pre-created user: " + userHandle + " Removed: " + result);
            }
        }
    }

    @After
    public void cleanUp() throws Exception {
        try {
            if (mUserToRemove != null) {
                Log.v(TAG, "Removing user created during test. User " + mUserToRemove);
                boolean result = mUserManager.removeUser(mUserToRemove);
                Log.v(TAG, "User: " + mUserToRemove + " Removed: " + result);
            }
        } catch (Exception e) {
            Log.v(TAG, "Cannot remove User:" + mUserToRemove + ". Exception: " + e);
        } finally {
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Test
    public void testMultiplePropertiesForCurrentUser() {
        // Current user should not be ephemeral because test runs as secondary user.
        UserHandle currentUser = UserHandle.of(ActivityManager.getCurrentUser());
        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, currentUser)).isFalse();

        // Current user should be enabled.
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, currentUser)).isTrue();

        // Current user should not be preCreated
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, currentUser)).isFalse();

        // Current should be initialized, otherwise test would be running
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, currentUser)).isTrue();

        // Current should be part of getUserHandles
        assertGetUserHandlesHasUser(currentUser);
    }


    @Test
    public void testMultiplePropertiesForGuestUser() throws Exception {
        UserHandle guestUser = createGuestUser();

        // Should be ephemeral
        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, guestUser)).isTrue();

        // User should be enabled.
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, guestUser)).isTrue();

        // User should not be preCreated
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, guestUser)).isFalse();

        // User should be initialized, but to confirm, we should wait for some time as
        // Initialization flag is set later on. Any better option?
        Thread.sleep(WAIT_TIME_FOR_NEGATIVE_RESULT_MS);
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, guestUser)).isFalse();

        // User should be part of getUserHandles
        assertGetUserHandlesHasUser(guestUser);
    }

    @Test
    public void testMultiplePropertiesForSecondaryFullUser() throws Exception {
        UserHandle fullUser = createSecondaryUser();

        // Should not be ephemeral
        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, fullUser)).isFalse();

        // User should be enabled.
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, fullUser)).isTrue();

        // User should not be preCreated
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, fullUser)).isFalse();

        // User should be initialized, but to confirm, we should wait for some time as
        // Initialization flag is set later on. Any better option?
        Thread.sleep(WAIT_TIME_FOR_NEGATIVE_RESULT_MS);
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, fullUser)).isFalse();

        // User should be part of getUserHandles
        assertGetUserHandlesHasUser(fullUser);
    }

    @Test
    public void testMultiplePropertiesForPreCreatedGuestUser() throws Exception {
        UserHandle preCreateUser = preCreateUserTest(UserManager.USER_TYPE_FULL_SECONDARY);

        // User should not be ephemeral
        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, preCreateUser)).isFalse();

        // User should be enabled.
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, preCreateUser)).isTrue();

        // User should be preCreated
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, preCreateUser)).isTrue();

        // User should be initialized, wait for it.
        waitForUserToInitialize(preCreateUser);
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, preCreateUser)).isTrue();

        // User should be part of getUserHandles
        assertGetUserHandlesHasUser(preCreateUser);
    }

    @Test
    public void testMultiplePropertiesForPreCreatedFullUser() throws Exception {
        UserHandle preCreateUser = preCreateUserTest(UserManager.USER_TYPE_FULL_GUEST);

        // Should not be ephemeral, will be ephemeral after promoted
        assertThat(UserManagerHelper.isEphemeralUser(mUserManager, preCreateUser)).isFalse();

        // User should be enabled.
        assertThat(UserManagerHelper.isEnabledUser(mUserManager, preCreateUser)).isTrue();

        // User should be preCreated
        assertThat(UserManagerHelper.isPreCreatedUser(mUserManager, preCreateUser)).isTrue();

        // User should be initialized, wait for it.
        waitForUserToInitialize(preCreateUser);
        assertThat(UserManagerHelper.isInitializedUser(mUserManager, preCreateUser)).isTrue();

        // User should be part of getUserHandles
        assertGetUserHandlesHasUser(preCreateUser);
    }

    @Test
    public void testGetDefaultUserTypeForUserInfoFlags() {
        // Simple example.
        assertThat(UserManagerHelper
                .getDefaultUserTypeForUserInfoFlags(UserManagerHelper.FLAG_MANAGED_PROFILE))
                        .isEqualTo(UserManager.USER_TYPE_PROFILE_MANAGED);

        // Type plus a non-type flag.
        assertThat(UserManagerHelper
                .getDefaultUserTypeForUserInfoFlags(
                        UserManagerHelper.FLAG_GUEST | UserManagerHelper.FLAG_EPHEMERAL))
                                .isEqualTo(UserManager.USER_TYPE_FULL_GUEST);

        // Two types, which is illegal.
        assertThrows(IllegalArgumentException.class, () -> UserManagerHelper
                .getDefaultUserTypeForUserInfoFlags(
                        UserManagerHelper.FLAG_MANAGED_PROFILE | UserManagerHelper.FLAG_GUEST));

        // No type, which defaults to {@link UserManager#USER_TYPE_FULL_SECONDARY}.
        assertThat(UserManagerHelper
                .getDefaultUserTypeForUserInfoFlags(UserManagerHelper.FLAG_EPHEMERAL))
                        .isEqualTo(UserManager.USER_TYPE_FULL_SECONDARY);
    }

    @Test
    public void testGetDefaultUserName() {
        assertThat(UserManagerHelper.getDefaultUserName(mContext)).isNotNull();
    }

    @Test
    public void testGetMaxRunningUsers() {
        assertThat(UserManagerHelper.getMaxRunningUsers(mContext)).isGreaterThan(0);
    }

    @Test
    public void testGetUserId() {
        assertThat(UserManagerHelper.getUserId(Binder.getCallingUid()))
                .isEqualTo(Binder.getCallingUserHandle().getIdentifier());
    }

    private void assertGetUserHandlesHasUser(UserHandle user) {
        List<UserHandle> allUsersHandles = UserManagerHelper.getUserHandles(mUserManager,
                /* excludePartial= */ false, /* excludeDying= */ false,
                /* excludePreCreated= */ false);
        assertThat(allUsersHandles).contains(user);
    }

    private void waitForUserToInitialize(UserHandle preCreateUser) throws Exception {
        long startTime = SystemClock.elapsedRealtime();
        long waitTime = SystemClock.elapsedRealtime() - startTime;
        while (!UserManagerHelper.isInitializedUser(mUserManager, preCreateUser)
                && waitTime < WAIT_TIME_FOR_OPERATION_MS) {
            waitTime = SystemClock.elapsedRealtime() - startTime;
            Log.v(TAG, "Waiting for user to initialize. Wait time in MS:" + waitTime);
            Thread.sleep(WAIT_TIME_BEFORE_RETRY_MS);
        }
    }

    private UserHandle createGuestUser() {
        NewUserRequest request = new NewUserRequest.Builder()
                .setUserType(UserManager.USER_TYPE_FULL_GUEST).setEphemeral().build();
        NewUserResponse response = mUserManager.createUser(request);
        if (response.isSuccessful()) {
            mUserToRemove = response.getUser();
            return mUserToRemove;
        }
        fail("Could not create guest User. Response: " + response);
        return null;
    }

    private UserHandle createSecondaryUser() {
        NewUserRequest request = new NewUserRequest.Builder()
                .setUserType(UserManager.USER_TYPE_FULL_SECONDARY).build();
        NewUserResponse response = mUserManager.createUser(request);
        if (response.isSuccessful()) {
            mUserToRemove = response.getUser();
            return mUserToRemove;
        }
        fail("Could not create secondary User. Response: " + response);
        return null;
    }

    private UserHandle createPreCreatedUser(String type) {
        mUserToRemove = UserManagerHelper.preCreateUser(mUserManager, type);
        if (mUserToRemove == null) {
            fail("Could not create precreated User of type:" + type);
        }
        return mUserToRemove;
    }

    private UserHandle preCreateUserTest(String type) {
        UserHandle user = createPreCreatedUser(type);
        assertPrecreatedUserExists(user, type);
        return user;
    }

    private void assertPrecreatedUserExists(UserHandle user, String type) {
        String allUsers = SystemUtil.runShellCommand("cmd user list --all -v");
        String[] result = allUsers.split("\n");
        for (int i = 0; i < result.length; i++) {
            if (result[i].contains("id=" + user.getIdentifier())) {
                assertThat(result[i]).contains("(pre-created)");
                if (type == UserManager.USER_TYPE_FULL_SECONDARY) {
                    assertThat(result[i]).contains("type=full.SECONDARY");
                }
                if (type == UserManager.USER_TYPE_FULL_GUEST) {
                    assertThat(result[i]).contains("type=full.GUEST");
                }
                return;
            }
        }
        fail("User not found. All users: " + allUsers + ". Expected user: " + user);
    }
}
