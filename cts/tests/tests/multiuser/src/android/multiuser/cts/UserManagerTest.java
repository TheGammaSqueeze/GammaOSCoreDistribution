/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package android.multiuser.cts;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.QUERY_USERS;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;
import static android.multiuser.cts.PermissionHelper.adoptShellPermissionIdentity;
import static android.multiuser.cts.TestingUtils.getBooleanProperty;
import static android.os.UserManager.USER_OPERATION_SUCCESS;
import static android.os.UserManager.USER_TYPE_FULL_SECONDARY;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.SystemUserOnly;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class UserManagerTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private UserManager mUserManager;

    private final String mAccountName = "test_account_name";
    private final String mAccountType = "test_account_type";

    @Before
    public void setUp() {
        mUserManager = sContext.getSystemService(UserManager.class);
        assertWithMessage("UserManager service").that(mUserManager).isNotNull();
    }

    private void removeUser(UserHandle userHandle) {
        if (userHandle == null) {
            return;
        }

        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            assertThat(mUserManager.removeUser(userHandle)).isTrue();
        }
    }

    /**
     * Verify that the isUserAGoat() method always returns false for API level 30. This is
     * because apps targeting R no longer have access to package queries by default.
     */
    @Test
    public void testUserGoat_api30() {
        assertWithMessage("isUserAGoat()").that(mUserManager.isUserAGoat()).isFalse();
    }

    @Test
    public void testIsRemoveResultSuccessful() {
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_REMOVED))
                .isTrue();
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_DEFERRED))
                .isTrue();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ALREADY_BEING_REMOVED))
                        .isTrue();
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_UNKNOWN))
                .isFalse();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_USER_RESTRICTION))
                        .isFalse();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_USER_NOT_FOUND))
                        .isFalse();
        assertThat(
                UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_SYSTEM_USER))
                        .isFalse();
    }

    @Test
    public void testIsHeadlessSystemUserMode() throws Exception {
        boolean expected = getBooleanProperty(mInstrumentation,
                "ro.fw.mu.headless_system_user");
        assertWithMessage("isHeadlessSystemUserMode()")
                .that(UserManager.isHeadlessSystemUserMode()).isEqualTo(expected);
    }

    @Test
    public void testIsUserForeground_currentUser() throws Exception {
        assertWithMessage("isUserForeground() for current user")
                .that(mUserManager.isUserForeground()).isTrue();
    }
    // TODO(b/173541467): add testIsUserForeground_backgroundUser()
    // TODO(b/179163496): add testIsUserForeground_ tests for profile users

    @Test
    public void testCloneProfile() throws Exception {
        UserHandle userHandle = null;

        // Need CREATE_USERS permission to create user in test
        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            try {
                userHandle = mUserManager.createProfile(
                    "Clone profile", UserManager.USER_TYPE_PROFILE_CLONE, new HashSet<>());
            } catch (UserManager.UserOperationException e) {
                // Not all devices and user types support these profiles; skip if this one doesn't.
                assumeNoException("Couldn't create clone profile", e);
                return;
            }
            assertThat(userHandle).isNotNull();

            final Context userContext = sContext.createPackageContextAsUser("system", 0,
                    userHandle);
            final UserManager cloneUserManager = userContext.getSystemService(UserManager.class);
            assertThat(cloneUserManager.isMediaSharedWithParent()).isTrue();
            assertThat(cloneUserManager.isCredentialSharableWithParent()).isTrue();
            assertThat(cloneUserManager.isCloneProfile()).isTrue();
            assertThat(cloneUserManager.isProfile()).isTrue();
            assertThat(cloneUserManager.isUserOfType(UserManager.USER_TYPE_PROFILE_CLONE)).isTrue();

            final List<UserInfo> list = mUserManager.getUsers(true, true, true);
            final UserHandle finalUserHandle = userHandle;
            final List<UserInfo> cloneUsers = list.stream().filter(
                    user -> (user.id == finalUserHandle.getIdentifier()
                            && user.isCloneProfile()))
                    .collect(Collectors.toList());
            assertThat(cloneUsers.size()).isEqualTo(1);
        } finally {
            removeUser(userHandle);
        }
    }

    @Test
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testManagedProfile() throws Exception {
        UserHandle userHandle = null;

        try {
            try {
                userHandle = mUserManager.createProfile(
                    "Managed profile", UserManager.USER_TYPE_PROFILE_MANAGED, new HashSet<>());
            } catch (UserManager.UserOperationException e) {
                // Not all devices and user types support these profiles; skip if this one doesn't.
                assumeNoException("Couldn't create managed profile", e);
                return;
            }
            assertThat(userHandle).isNotNull();

            final UserManager umOfProfile = sContext
                    .createPackageContextAsUser("android", 0, userHandle)
                    .getSystemService(UserManager.class);

            // TODO(b/222584163): Remove the if{} clause after v33 Sdk bump.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                assertThat(umOfProfile.isManagedProfile()).isTrue();
            }
            assertThat(umOfProfile.isManagedProfile(userHandle.getIdentifier())).isTrue();
            assertThat(umOfProfile.isProfile()).isTrue();
            assertThat(umOfProfile.isUserOfType(UserManager.USER_TYPE_PROFILE_MANAGED)).isTrue();
        } finally {
            removeUser(userHandle);
        }
    }

    @Test
    @EnsureHasPermission({QUERY_USERS})
    public void testSystemUser() throws Exception {
        final UserManager umOfSys = sContext
                .createPackageContextAsUser("android", 0, UserHandle.SYSTEM)
                .getSystemService(UserManager.class);

        // TODO(b/222584163): Remove the if{} clause after v33 Sdk bump.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            assertThat(umOfSys.isSystemUser()).isTrue();
        }

        // We cannot demand what type of user SYSTEM is, but we can say some things it isn't.
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_PROFILE_CLONE)).isFalse();
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_PROFILE_MANAGED)).isFalse();
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_FULL_GUEST)).isFalse();

        assertThat(umOfSys.isProfile()).isFalse();
        assertThat(umOfSys.isManagedProfile()).isFalse();
        assertThat(umOfSys.isManagedProfile(UserHandle.USER_SYSTEM)).isFalse();
        assertThat(umOfSys.isCloneProfile()).isFalse();
    }

    @Test
    @SystemUserOnly(reason = "Restricted users are only supported on system user.")
    public void testRestrictedUser() throws Exception {
        UserHandle user = null;
        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            // Check that the SYSTEM user is not restricted.
            assertThat(mUserManager.isRestrictedProfile()).isFalse();
            assertThat(mUserManager.isRestrictedProfile(UserHandle.SYSTEM)).isFalse();
            assertThat(mUserManager.getRestrictedProfileParent()).isNull();

            final UserInfo info = mUserManager.createRestrictedProfile("Restricted user");

            // If the device supports Restricted users, it must report it correctly.
            assumeTrue("Couldn't create a restricted profile", info != null);

            user = UserHandle.of(info.id);
            assertThat(mUserManager.isRestrictedProfile(user)).isTrue();

            final Context userContext = sContext.createPackageContextAsUser("system", 0, user);
            final UserManager userUm = userContext.getSystemService(UserManager.class);
            // TODO(b/222584163): Remove the if{} clause after v33 Sdk bump.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                assertThat(userUm.isRestrictedProfile()).isTrue();
            }
            assertThat(userUm.getRestrictedProfileParent().isSystem()).isTrue();
        } finally {
            removeUser(user);
        }
    }

    private NewUserRequest newUserRequest() {
        final PersistableBundle accountOptions = new PersistableBundle();
        accountOptions.putString("test_account_option_key", "test_account_option_value");

        return new NewUserRequest.Builder()
                .setName("test_user")
                .setUserType(USER_TYPE_FULL_SECONDARY)
                .setUserIcon(Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565))
                .setAccountName(mAccountName)
                .setAccountType(mAccountType)
                .setAccountOptions(accountOptions)
                .build();
    }

    @Test
    public void testSomeUserHasAccount() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user = null;

        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isFalse();
            user = mUserManager.createUser(newUserRequest()).getUser();
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isTrue();
        } finally {
            removeUser(user);
        }
    }

    @Test
    public void testSomeUserHasAccount_shouldIgnoreToBeRemovedUsers() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            final NewUserResponse response = mUserManager.createUser(newUserRequest());
            assertThat(response.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            mUserManager.removeUser(response.getUser());
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isFalse();
        }
    }

    @Test
    public void testCreateUser_withNewUserRequest_shouldCreateUserWithCorrectProperties()
            throws PackageManager.NameNotFoundException {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user = null;

        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            final NewUserRequest request = newUserRequest();
            final NewUserResponse response = mUserManager.createUser(request);
            user = response.getUser();

            assertThat(response.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            assertThat(response.isSuccessful()).isTrue();
            assertThat(user).isNotNull();

            UserManager userManagerOfNewUser = sContext
                    .createPackageContextAsUser("android", 0, user)
                    .getSystemService(UserManager.class);

            assertThat(userManagerOfNewUser.getUserName()).isEqualTo(request.getName());
            // TODO(b/222584163): Remove the if{} clause after v33 Sdk bump.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                assertThat(userManagerOfNewUser.isUserNameSet()).isTrue();
            }
            assertThat(userManagerOfNewUser.getUserType()).isEqualTo(request.getUserType());
            assertThat(userManagerOfNewUser.isUserOfType(request.getUserType())).isEqualTo(true);
            // We can not test userIcon and accountOptions,
            // because getters require MANAGE_USERS permission.
            // And we are already testing accountName and accountType
            // are set correctly in testSomeUserHasAccount method.
        } finally {
            removeUser(user);
        }
    }

    @Test
    public void testCreateUser_withNewUserRequest_shouldNotAllowDuplicateUserAccounts() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user1 = null;
        UserHandle user2 = null;

        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            final NewUserResponse response1 = mUserManager.createUser(newUserRequest());
            user1 = response1.getUser();

            assertThat(response1.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            assertThat(response1.isSuccessful()).isTrue();
            assertThat(user1).isNotNull();

            final NewUserResponse response2 = mUserManager.createUser(newUserRequest());
            user2 = response2.getUser();

            assertThat(response2.getOperationResult()).isEqualTo(
                    UserManager.USER_OPERATION_ERROR_USER_ACCOUNT_ALREADY_EXISTS);
            assertThat(response2.isSuccessful()).isFalse();
            assertThat(user2).isNull();
        } finally {
            removeUser(user1);
            removeUser(user2);
        }
    }

    @Test
    @AppModeFull
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void getProfileParent_withNewlyCreatedProfile() {
        final UserReference parent = TestApis.users().instrumented();
        try (UserReference profile = TestApis.users().createUser()
                .parent(parent)
                .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                .createAndStart()) {
            assertThat(mUserManager.getProfileParent(profile.userHandle()))
                    .isEqualTo(parent.userHandle());
        }
    }

    @Test
    @AppModeFull
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void getProfileParent_returnsNullForNonProfile() {
        assertThat(mUserManager.getProfileParent(TestApis.users().system().userHandle())).isNull();
    }

    @Test
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetRemainingCreatableUserCount() {
        final int maxAllowedIterations = 15;
        final String userType = USER_TYPE_FULL_SECONDARY;
        final NewUserRequest request = new NewUserRequest.Builder().build();
        final ArrayDeque<UserHandle> usersCreated = new ArrayDeque<>();

        try {
            final int initialRemainingCount = mUserManager.getRemainingCreatableUserCount(userType);
            assertThat(initialRemainingCount).isAtLeast(0);

            final int numUsersToAdd = Math.min(maxAllowedIterations, initialRemainingCount);

            for (int i = 0; i < numUsersToAdd; i++) {
                usersCreated.push(mUserManager.createUser(request).getUser());
                assertThat(mUserManager.getRemainingCreatableUserCount(userType))
                        .isEqualTo(initialRemainingCount - usersCreated.size());
            }
            for (int i = 0; i < numUsersToAdd; i++) {
                mUserManager.removeUser(usersCreated.pop());
                assertThat(mUserManager.getRemainingCreatableUserCount(userType))
                        .isEqualTo(initialRemainingCount - usersCreated.size());
            }
        } finally {
            usersCreated.forEach(this::removeUser);
        }
    }

    @Test
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetRemainingCreatableProfileCount() {
        final int maxAllowedIterations = 15;
        final String type = USER_TYPE_PROFILE_MANAGED;
        final ArrayDeque<UserHandle> profilesCreated = new ArrayDeque<>();
        final Set<String> disallowedPackages = new HashSet<>();
        try {
            final int initialRemainingCount =
                    mUserManager.getRemainingCreatableProfileCount(type);
            assertThat(initialRemainingCount).isAtLeast(0);

            final int numUsersToAdd = Math.min(maxAllowedIterations, initialRemainingCount);

            for (int i = 0; i < numUsersToAdd; i++) {
                profilesCreated.push(mUserManager.createProfile(null, type, disallowedPackages));
                assertThat(mUserManager.getRemainingCreatableProfileCount(type))
                        .isEqualTo(initialRemainingCount - profilesCreated.size());
            }
            for (int i = 0; i < numUsersToAdd; i++) {
                mUserManager.removeUser(profilesCreated.pop());
                assertThat(mUserManager.getRemainingCreatableProfileCount(type))
                        .isEqualTo(initialRemainingCount - profilesCreated.size());
            }
        } finally {
            profilesCreated.forEach(this::removeUser);
        }
    }
}
