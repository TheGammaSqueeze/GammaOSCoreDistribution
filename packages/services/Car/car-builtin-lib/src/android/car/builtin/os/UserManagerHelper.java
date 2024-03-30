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

package android.car.builtin.os;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.annotation.UserIdInt;
import android.car.builtin.annotation.AddedIn;
import android.car.builtin.annotation.PlatformVersion;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for User related operations.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class UserManagerHelper {
    private UserManagerHelper() {
        throw new UnsupportedOperationException();
    }

    /** user id for invalid user */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final @UserIdInt int USER_NULL = UserHandle.USER_NULL;

    /** A user id constant to indicate the "system" user of the device */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final @UserIdInt int USER_SYSTEM = UserHandle.USER_SYSTEM;

    // Flags copied from UserInfo.
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_PRIMARY = UserInfo.FLAG_PRIMARY;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_ADMIN = UserInfo.FLAG_ADMIN;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_GUEST = UserInfo.FLAG_GUEST;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_RESTRICTED = UserInfo.FLAG_RESTRICTED;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_INITIALIZED = UserInfo.FLAG_INITIALIZED;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_MANAGED_PROFILE = UserInfo.FLAG_MANAGED_PROFILE;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_DISABLED = UserInfo.FLAG_DISABLED;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_QUIET_MODE = UserInfo.FLAG_QUIET_MODE;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_EPHEMERAL = UserInfo.FLAG_EPHEMERAL;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_DEMO = UserInfo.FLAG_DEMO;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_FULL = UserInfo.FLAG_FULL;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_SYSTEM = UserInfo.FLAG_SYSTEM;
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static final int FLAG_PROFILE = UserInfo.FLAG_PROFILE;

    /**
     * Returns all users based on the boolean flags.
     */
    @NonNull
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static List<UserHandle> getUserHandles(@NonNull UserManager userManager,
            boolean excludePartial, boolean excludeDying, boolean excludePreCreated) {
        List<UserInfo> users = userManager.getUsers(excludePartial, excludeDying,
                excludePreCreated);

        List<UserHandle> result = new ArrayList<>(users.size());
        for (UserInfo user : users) {
            result.add(user.getUserHandle());
        }
        return result;
    }

    /**
     * Checks if a user is ephemeral.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static boolean isEphemeralUser(@NonNull UserManager userManager,
            @NonNull UserHandle user) {
        return userManager.isUserEphemeral(user.getIdentifier());
    }

    /**
     * Checks if a user is enabled.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static boolean isEnabledUser(@NonNull UserManager userManager,
            @NonNull UserHandle user) {
        return userManager.getUserInfo(user.getIdentifier()).isEnabled();
    }

    /**
     * Checks if a user is precreated.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static boolean isPreCreatedUser(@NonNull UserManager userManager,
            @NonNull UserHandle user) {
        return userManager.getUserInfo(user.getIdentifier()).preCreated;
    }

    /**
     * Checks if a user is initialized.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static boolean isInitializedUser(@NonNull UserManager userManager,
            @NonNull UserHandle user) {
        return userManager.getUserInfo(user.getIdentifier()).isInitialized();
    }

    /**
     * Gets DefaultUserType given userInfo flags.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static String getDefaultUserTypeForUserInfoFlags(int userInfoFlag) {
        return UserInfo.getDefaultUserType(userInfoFlag);
    }

    /**
     * Precreates user based on user type
     */
    @Nullable
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static UserHandle preCreateUser(@NonNull UserManager userManager, @NonNull String type) {
        UserInfo userInfo = userManager.preCreateUser(type);
        return userInfo == null ? null : userInfo.getUserHandle();
    }

    /**
     * Gets the default name for a user.
     */
    @NonNull
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static String getDefaultUserName(@NonNull Context context) {
        return context.getResources().getString(com.android.internal.R.string.owner_name);
    }

    /**
     * Gets the maximum number of users that can be running at any given time.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static int getMaxRunningUsers(@NonNull Context context) {
        return context.getResources()
                .getInteger(com.android.internal.R.integer.config_multiuserMaxRunningUsers);
    }

    /**
     * Marks guest for deletion
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static boolean markGuestForDeletion(@NonNull UserManager userManager,
            @NonNull UserHandle user) {
        return userManager.markGuestForDeletion(user.getIdentifier());
    }

    /**
     * Returns the user id for a given uid.
     */
    @AddedIn(PlatformVersion.TIRAMISU_0)
    public static @UserIdInt int getUserId(int uid) {
        return UserHandle.getUserId(uid);
    }
}
