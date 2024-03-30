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

package com.android.bedstead.nene.users;

import static android.app.ActivityManager.STOP_USER_ON_SWITCH_DEFAULT;
import static android.app.ActivityManager.STOP_USER_ON_SWITCH_FALSE;
import static android.app.ActivityManager.STOP_USER_ON_SWITCH_TRUE;
import static android.os.Build.VERSION.SDK_INT;

import static com.android.bedstead.harrier.OptionalBoolean.TRUE;
import static com.android.bedstead.harrier.UserType.SYSTEM_USER;
import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;
import static com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME;
import static com.android.bedstead.nene.users.UserType.SYSTEM_USER_TYPE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.os.Build;
import android.os.UserHandle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasNoSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireRunNotOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.Poll;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class UsersTest {

    private static final int MAX_SYSTEM_USERS = UserType.UNLIMITED;
    private static final int MAX_SYSTEM_USERS_PER_PARENT = UserType.UNLIMITED;
    private static final String INVALID_TYPE_NAME = "invalidTypeName";
    private static final int MAX_MANAGED_PROFILES = UserType.UNLIMITED;
    private static final int MAX_MANAGED_PROFILES_PER_PARENT = 1;
    private static final int NON_EXISTING_USER_ID = 10000;
    private static final int USER_ID = NON_EXISTING_USER_ID;
    private static final String USER_NAME = "userName";

    private final UserType mSecondaryUserType =
            TestApis.users().supportedType(SECONDARY_USER_TYPE_NAME);
    private final UserType mManagedProfileType =
            TestApis.users().supportedType(MANAGED_PROFILE_TYPE_NAME);
    private final UserReference mInstrumentedUser = TestApis.users().instrumented();

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // We don't want to test the exact list of any specific device, so we check that it returns
    // some known types which will exist on the emulators (used for presubmit tests).

    @Test
    public void supportedTypes_containsManagedProfile() {
        UserType managedProfileUserType =
                TestApis.users().supportedTypes().stream().filter(
                        (ut) -> ut.name().equals(MANAGED_PROFILE_TYPE_NAME)).findFirst().get();

        assertThat(managedProfileUserType.baseType()).containsExactly(UserType.BaseType.PROFILE);
        assertThat(managedProfileUserType.enabled()).isTrue();
        assertThat(managedProfileUserType.maxAllowed()).isEqualTo(MAX_MANAGED_PROFILES);
        assertThat(managedProfileUserType.maxAllowedPerParent())
                .isEqualTo(MAX_MANAGED_PROFILES_PER_PARENT);
    }

    @Test
    public void supportedTypes_containsSystemUser() {
        UserType systemUserType =
                TestApis.users().supportedTypes().stream().filter(
                        (ut) -> ut.name().equals(SYSTEM_USER_TYPE_NAME)).findFirst().get();

        assertThat(systemUserType.baseType()).containsExactly(
                UserType.BaseType.SYSTEM, UserType.BaseType.FULL);
        assertThat(systemUserType.enabled()).isTrue();
        assertThat(systemUserType.maxAllowed()).isEqualTo(MAX_SYSTEM_USERS);
        assertThat(systemUserType.maxAllowedPerParent()).isEqualTo(MAX_SYSTEM_USERS_PER_PARENT);
    }

    @Test
    public void supportedType_validType_returnsType() {
        UserType managedProfileUserType =
                TestApis.users().supportedType(MANAGED_PROFILE_TYPE_NAME);

        assertThat(managedProfileUserType.baseType()).containsExactly(UserType.BaseType.PROFILE);
        assertThat(managedProfileUserType.enabled()).isTrue();
        assertThat(managedProfileUserType.maxAllowed()).isEqualTo(MAX_MANAGED_PROFILES);
        assertThat(managedProfileUserType.maxAllowedPerParent())
                .isEqualTo(MAX_MANAGED_PROFILES_PER_PARENT);
    }

    @Test
    public void supportedType_invalidType_returnsNull() {
        assertThat(TestApis.users().supportedType(INVALID_TYPE_NAME)).isNull();
    }

    @Test
    public void all_containsCreatedUser() {
        UserReference user = TestApis.users().createUser().create();

        try {
            assertThat(TestApis.users().all()).contains(user);
        } finally {
            user.remove();
        }
    }

    @Test
    public void all_userAddedSinceLastCallToUsers_containsNewUser() {
        UserReference user = TestApis.users().createUser().create();
        TestApis.users().all();
        UserReference user2 = TestApis.users().createUser().create();

        try {
            assertThat(TestApis.users().all()).contains(user2);
        } finally {
            user.remove();
            user2.remove();
        }
    }

    @Test
    public void all_userRemovedSinceLastCallToUsers_doesNotContainRemovedUser() {
        UserReference user = TestApis.users().createUser().create();
        TestApis.users().all();
        user.remove();

        assertThat(TestApis.users().all()).doesNotContain(user);
    }

    @Test
    public void find_userExists_returnsUserReference() {
        UserReference user = TestApis.users().createUser().create();
        try {
            assertThat(TestApis.users().find(user.id())).isEqualTo(user);
        } finally {
            user.remove();
        }
    }

    @Test
    public void find_userDoesNotExist_returnsUserReference() {
        assertThat(TestApis.users().find(NON_EXISTING_USER_ID)).isNotNull();
    }

    @Test
    public void find_fromUserHandle_referencesCorrectId() {
        assertThat(TestApis.users().find(UserHandle.of(USER_ID)).id()).isEqualTo(USER_ID);
    }

    @Test
    public void find_constructedReferenceReferencesCorrectId() {
        assertThat(TestApis.users().find(USER_ID).id()).isEqualTo(USER_ID);
    }

    @Test
    public void createUser_additionalSystemUser_throwsException()  {
        assertThrows(NeneException.class, () ->
                TestApis.users().createUser()
                        .type(TestApis.users().supportedType(SYSTEM_USER_TYPE_NAME))
                        .create());
    }

    @Test
    public void createUser_userIsCreated()  {
        UserReference user = TestApis.users().createUser().create();

        try {
            assertThat(TestApis.users().all()).contains(user);
        } finally {
            user.remove();
        }
    }

    @Test
    public void createUser_createdUserHasCorrectName() {
        UserReference userReference = TestApis.users().createUser()
                .name(USER_NAME)
                .create();

        try {
            assertThat(userReference.name()).isEqualTo(USER_NAME);
        } finally {
            userReference.remove();
        }
    }

    @Test
    public void createUser_createdUserHasCorrectTypeName() {
        UserReference userReference = TestApis.users().createUser()
                .type(mSecondaryUserType)
                .create();

        try {
            assertThat(userReference.type()).isEqualTo(mSecondaryUserType);
        } finally {
            userReference.remove();
        }
    }

    @Test
    public void createUser_specifiesNullUserType_throwsException() {
        UserBuilder userBuilder = TestApis.users().createUser();

        assertThrows(NullPointerException.class, () -> userBuilder.type(null));
    }

    @Test
    public void createUser_specifiesSystemUserType_throwsException() {
        UserType type = TestApis.users().supportedType(SYSTEM_USER_TYPE_NAME);
        UserBuilder userBuilder = TestApis.users().createUser()
                .type(type);

        assertThrows(NeneException.class, userBuilder::create);
    }

    @Test
    public void createUser_specifiesSecondaryUserType_createsUser() {
        UserReference user = TestApis.users().createUser().type(mSecondaryUserType).create();

        try {
            assertThat(user.exists()).isTrue();
        } finally {
            user.remove();
        }
    }

    @Test
    @EnsureHasNoDeviceOwner // Device Owners can disable managed profiles
    @EnsureHasNoWorkProfile(forUser = SYSTEM_USER)
    public void createUser_specifiesManagedProfileUserType_createsUser() {
        UserReference systemUser = TestApis.users().system();
        UserReference user = TestApis.users().createUser()
                .type(mManagedProfileType).parent(systemUser).create();

        try {
            assertThat(user.exists()).isTrue();
        } finally {
            user.remove();
        }
    }

    @Test
    @EnsureHasNoWorkProfile(forUser = SYSTEM_USER)
    public void createUser_createsProfile_parentIsSet() {
        UserReference systemUser = TestApis.users().system();
        UserReference user = TestApis.users().createUser()
                .type(mManagedProfileType).parent(systemUser).create();

        try {
            assertThat(user.parent()).isEqualTo(TestApis.users().system());
        } finally {
            user.remove();
        }
    }

    @Test
    public void createUser_specifiesParentOnNonProfileType_throwsException() {
        UserReference systemUser = TestApis.users().system();
        UserBuilder userBuilder = TestApis.users().createUser()
                .type(mSecondaryUserType).parent(systemUser);

        assertThrows(NeneException.class, userBuilder::create);
    }

    @Test
    public void createUser_specifiesProfileTypeWithoutParent_throwsException() {
        UserBuilder userBuilder = TestApis.users().createUser()
                .type(mManagedProfileType);

        assertThrows(NeneException.class, userBuilder::create);
    }

    @Test
    public void createUser_androidLessThanS_createsManagedProfileNotOnSystemUser_throwsException() {
        assumeTrue("After Android S, managed profiles may be a profile of a non-system user",
                SDK_INT < Build.VERSION_CODES.S);

        UserReference nonSystemUser = TestApis.users().createUser().create();

        try {
            UserBuilder userBuilder = TestApis.users().createUser()
                    .type(mManagedProfileType)
                    .parent(nonSystemUser);

            assertThrows(NeneException.class, userBuilder::create);
        } finally {
            nonSystemUser.remove();
        }
    }

    @Test
    public void createAndStart_isStarted() {
        UserReference user = null;

        try {
            user = TestApis.users().createUser().name(USER_NAME).createAndStart();
            assertThat(user.isUnlocked()).isTrue();
        } finally {
            if (user != null) {
                user.remove();
            }
        }
    }

    @Test
    public void system_hasId0() {
        assertThat(TestApis.users().system().id()).isEqualTo(0);
    }

    @Test
    public void instrumented_hasCurrentProccessId() {
        assertThat(TestApis.users().instrumented().id())
                .isEqualTo(android.os.Process.myUserHandle().getIdentifier());
    }

    @Test
    @EnsureHasNoSecondaryUser
    public void findUsersOfType_noMatching_returnsEmptySet() {
        assertThat(TestApis.users().findUsersOfType(mSecondaryUserType)).isEmpty();
    }

    @Test
    public void findUsersOfType_nullType_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.users().findUsersOfType(null));
    }

    @Test
    @EnsureHasSecondaryUser
    @Ignore("TODO: Re-enable when harrier .secondaryUser() only"
            + " returns the harrier-managed secondary user")
    public void findUsersOfType_returnsUsers() {
        try (UserReference additionalUser = TestApis.users().createUser().create()) {
            assertThat(TestApis.users().findUsersOfType(mSecondaryUserType))
                    .containsExactly(sDeviceState.secondaryUser(), additionalUser);
        }
    }

    @Test
    public void findUsersOfType_profileType_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.users().findUsersOfType(mManagedProfileType));
    }

    @Test
    @EnsureHasNoSecondaryUser
    public void findUserOfType_noMatching_returnsNull() {
        assertThat(TestApis.users().findUserOfType(mSecondaryUserType)).isNull();
    }

    @Test
    public void findUserOfType_nullType_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.users().findUserOfType(null));
    }

    @Test
    @EnsureHasSecondaryUser
    public void findUserOfType_multipleMatchingUsers_throwsException() {
        try (UserReference additionalUser = TestApis.users().createUser().create()) {
            assertThrows(NeneException.class,
                    () -> TestApis.users().findUserOfType(mSecondaryUserType));
        }
    }

    @Test
    @EnsureHasSecondaryUser // TODO(scottjonathan): This should have a way of specifying exactly 1
    public void findUserOfType_oneMatchingUser_returnsUser() {
        assertThat(TestApis.users().findUserOfType(mSecondaryUserType)).isNotNull();
    }

    @Test
    public void findUserOfType_profileType_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.users().findUserOfType(mManagedProfileType));
    }

    @Test
    @EnsureHasNoWorkProfile
    public void findProfilesOfType_noMatching_returnsEmptySet() {
        assertThat(TestApis.users().findProfilesOfType(mManagedProfileType, mInstrumentedUser))
                .isEmpty();
    }

    @Test
    public void findProfilesOfType_nullType_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.users().findProfilesOfType(
                        /* userType= */ null, mInstrumentedUser));
    }

    @Test
    public void findProfilesOfType_nullParent_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.users().findProfilesOfType(
                        mManagedProfileType, /* parent= */ null));
    }

    // TODO(scottjonathan): Once we have profiles which support more than one instance, test this

    @Test
    @EnsureHasNoWorkProfile
    public void findProfileOfType_noMatching_returnsNull() {
        assertThat(TestApis.users().findProfileOfType(mManagedProfileType, mInstrumentedUser))
                .isNull();
    }

    @Test
    public void findProfilesOfType_nonProfileType_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.users().findProfilesOfType(mSecondaryUserType, mInstrumentedUser));
    }

    @Test
    public void findProfileOfType_nullType_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.users().findProfileOfType(/* userType= */ null, mInstrumentedUser));
    }

    @Test
    public void findProfileOfType_nonProfileType_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.users().findProfileOfType(mSecondaryUserType, mInstrumentedUser));
    }

    @Test
    public void findProfileOfType_nullParent_throwsException() {
        assertThrows(NullPointerException.class,
                () -> TestApis.users().findProfileOfType(mManagedProfileType, /* parent= */ null));
    }

    @Test
    @EnsureHasWorkProfile // TODO(scottjonathan): This should have a way of specifying exactly 1
    public void findProfileOfType_oneMatchingUser_returnsUser() {
        assertThat(TestApis.users().findProfileOfType(mManagedProfileType, mInstrumentedUser))
                .isNotNull();
    }

    @Test
    public void nonExisting_userDoesNotExist() {
        UserReference userReference = TestApis.users().nonExisting();

        assertThat(userReference.exists()).isFalse();
    }

    @Test
    @EnsureHasSecondaryUser(switchedToUser = TRUE)
    public void currentUser_secondaryUser_returnsCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.secondaryUser());
    }

    @Test
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    public void currentUser_primaryUser_returnsCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.primaryUser());
    }

    @Test
    @RequireRunNotOnSecondaryUser
    @EnsureHasSecondaryUser
    @RequireHeadlessSystemUserMode
    public void switch_hasSetStopBgUsersOnSwitch_stopsUser() throws Exception {
        try {
            TestApis.users().setStopBgUsersOnSwitch(STOP_USER_ON_SWITCH_TRUE);
            TestApis.users().system().switchTo();

            Poll.forValue("Secondary user running",
                    () -> sDeviceState.secondaryUser().isRunning())
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();

            assertThat(sDeviceState.secondaryUser().isRunning()).isFalse();
        } finally {
            sDeviceState.secondaryUser().start();
            TestApis.users().setStopBgUsersOnSwitch(STOP_USER_ON_SWITCH_DEFAULT);
        }
    }

    @Test
    @RequireRunOnSecondaryUser
    public void switch_hasSetStopBgUsersOnSwitchFalse_doesNotStopUser() {
        try {
            TestApis.users().setStopBgUsersOnSwitch(STOP_USER_ON_SWITCH_FALSE);
            TestApis.users().system().switchTo();

            assertThat(sDeviceState.secondaryUser().isRunning()).isTrue();
        } finally {
            TestApis.users().setStopBgUsersOnSwitch(STOP_USER_ON_SWITCH_DEFAULT);
            sDeviceState.secondaryUser().start();
            sDeviceState.secondaryUser().switchTo();
        }
    }
}
