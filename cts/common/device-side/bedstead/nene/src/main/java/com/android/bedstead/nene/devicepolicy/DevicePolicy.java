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

package com.android.bedstead.nene.devicepolicy;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.os.Build.VERSION.SDK_INT;

import static com.android.bedstead.nene.permissions.CommonPermissions.FORCE_DEVICE_POLICY_MANAGER_LOGS;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_ADMINS;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_ROLE_HOLDERS;
import static com.android.bedstead.nene.utils.Versions.T;

import static org.junit.Assert.fail;

import android.annotation.TargetApi;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.AdbParseException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.Retry;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.Versions;
import com.android.compatibility.common.util.BlockingCallback;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Test APIs related to device policy.
 */
public final class DevicePolicy {

    public static final DevicePolicy sInstance = new DevicePolicy();

    private static final String LOG_TAG = "DevicePolicy";

    private final AdbDevicePolicyParser mParser;

    private DeviceOwner mCachedDeviceOwner;
    private Map<UserReference, ProfileOwner> mCachedProfileOwners;

    private DevicePolicy() {
        mParser = AdbDevicePolicyParser.get(SDK_INT);
    }

    /**
     * Set the profile owner for a given {@link UserReference}.
     */
    public ProfileOwner setProfileOwner(UserReference user, ComponentName profileOwnerComponent) {
        if (user == null || profileOwnerComponent == null) {
            throw new NullPointerException();
        }

        ShellCommand.Builder command =
                ShellCommand.builderForUser(user, "dpm set-profile-owner")
                .addOperand(profileOwnerComponent.flattenToShortString())
                .validate(ShellCommandUtils::startsWithSuccess);

        // TODO(b/187925230): If it fails, we check for terminal failure states - and if not
        //  we retry because if the profile owner was recently removed, it can take some time
        //  to be allowed to set it again
        try {
            Retry.logic(command::execute)
                    .terminalException((ex) -> {
                        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
                            return false; // Just retry on old versions as we don't have stderr
                        }
                        if (ex instanceof AdbException) {
                            String error = ((AdbException) ex).error();
                            if (error.contains("is being removed")) {
                                return false;
                            }
                        }

                        // Assume all other errors are terminal
                        return true;
                    })
                    .timeout(Duration.ofMinutes(5))
                    .run();
        } catch (Throwable e) {
            throw new NeneException("Could not set profile owner for user "
                    + user + " component " + profileOwnerComponent, e);
        }

        Poll.forValue("Profile Owner", () -> TestApis.devicePolicy().getProfileOwner(user))
                .toNotBeNull()
                .errorOnFail()
                .await();

        return new ProfileOwner(user,
                TestApis.packages().find(
                        profileOwnerComponent.getPackageName()), profileOwnerComponent);
    }

    /**
     * Get the profile owner for the instrumented user.
     */
    public ProfileOwner getProfileOwner() {
        return getProfileOwner(TestApis.users().instrumented());
    }

    /**
     * Get the profile owner for a given {@link UserReference}.
     */
    public ProfileOwner getProfileOwner(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }
        fillCache();
        return mCachedProfileOwners.get(user);
    }

    /**
     * Set the device owner.
     */
    public DeviceOwner setDeviceOwner(ComponentName deviceOwnerComponent) {
        if (deviceOwnerComponent == null) {
            throw new NullPointerException();
        }

        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            return setDeviceOwnerPreS(deviceOwnerComponent);
        }

        DevicePolicyManager devicePolicyManager =
                TestApis.context().instrumentedContext()
                        .getSystemService(DevicePolicyManager.class);
        UserReference user = TestApis.users().system();

        boolean dpmUserSetupComplete = user.getSetupComplete();
        Boolean currentUserSetupComplete = null;

        try {
            user.setSetupComplete(false);

            try (PermissionContext p =
                         TestApis.permissions().withPermission(
                                 MANAGE_PROFILE_AND_DEVICE_OWNERS, MANAGE_DEVICE_ADMINS,
                                 INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS, CREATE_USERS)) {

                // TODO(b/187925230): If it fails, we check for terminal failure states - and if not
                //  we retry because if the DO/PO was recently removed, it can take some time
                //  to be allowed to set it again
                Retry.logic(() -> {
                            devicePolicyManager.setActiveAdmin(deviceOwnerComponent,
                                    /* refreshing= */ true, user.id());
                            setDeviceOwnerOnly(devicePolicyManager,
                                    deviceOwnerComponent, "Nene", user.id());
                }).terminalException((e) -> checkForTerminalDeviceOwnerFailures(
                        user, deviceOwnerComponent, /* allowAdditionalUsers= */ true))
                        .timeout(Duration.ofMinutes(5))
                        .run();
            } catch (Throwable e) {
                throw new NeneException("Error setting device owner", e);
            }
        } finally {
            user.setSetupComplete(dpmUserSetupComplete);
            if (currentUserSetupComplete != null) {
                TestApis.users().current().setSetupComplete(currentUserSetupComplete);
            }
        }

        Package deviceOwnerPackage = TestApis.packages().find(
                deviceOwnerComponent.getPackageName());

        Poll.forValue("Device Owner", () -> TestApis.devicePolicy().getDeviceOwner())
                .toNotBeNull()
                .errorOnFail()
                .await();

        return new DeviceOwner(user, deviceOwnerPackage, deviceOwnerComponent);
    }

    /**
     * Set Device Owner without changing any other device state.
     *
     * <p>This is used instead of {@link DevicePolicyManager#setDeviceOwner(ComponentName)} directly
     * because on S_V2 and above, that method can also set profile owners and install packages in
     * some circumstances.
     */
    private void setDeviceOwnerOnly(DevicePolicyManager devicePolicyManager,
            ComponentName component, String name, int deviceOwnerUserId) {
        if (Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S_V2)) {
            devicePolicyManager.setDeviceOwnerOnly(component, name, deviceOwnerUserId);
        } else {
            devicePolicyManager.setDeviceOwner(component, name, deviceOwnerUserId);
        }
    }

    /**
     * Resets organization ID via @TestApi.
     * @param user whose organization ID to clear
     */
    public void clearOrganizationId(UserReference user) {
        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            DevicePolicyManager devicePolicyManager =
                    TestApis.context().instrumentedContextAsUser(user)
                            .getSystemService(DevicePolicyManager.class);
            devicePolicyManager.clearOrganizationId();
        }
    }

    private DeviceOwner setDeviceOwnerPreS(ComponentName deviceOwnerComponent) {
        UserReference user = TestApis.users().system();

        ShellCommand.Builder command = ShellCommand.builderForUser(
                user, "dpm set-device-owner")
                .addOperand(deviceOwnerComponent.flattenToShortString())
                .validate(ShellCommandUtils::startsWithSuccess);
        // TODO(b/187925230): If it fails, we check for terminal failure states - and if not
        //  we retry because if the device owner was recently removed, it can take some time
        //  to be allowed to set it again

        try {
            Retry.logic(command::execute)
                .terminalException((e) -> checkForTerminalDeviceOwnerFailures(
                            user, deviceOwnerComponent, /* allowAdditionalUsers= */ false))
                    .timeout(Duration.ofMinutes(5))
                    .run();
        } catch (Throwable e) {
            throw new NeneException("Error setting device owner", e);
        }

        return new DeviceOwner(user,
                TestApis.packages().find(
                        deviceOwnerComponent.getPackageName()), deviceOwnerComponent);
    }

    private boolean checkForTerminalDeviceOwnerFailures(
            UserReference user, ComponentName deviceOwnerComponent, boolean allowAdditionalUsers) {
        DeviceOwner deviceOwner = getDeviceOwner();
        if (deviceOwner != null) {
            // TODO(scottjonathan): Should we actually fail here if the component name is the
            //  same?

            throw new NeneException(
                    "Could not set device owner for user " + user
                            + " as a device owner is already set: " + deviceOwner);
        }

        Package pkg = TestApis.packages().find(
                deviceOwnerComponent.getPackageName());
        if (!TestApis.packages().installedForUser(user).contains(pkg)) {
            throw new NeneException(
                    "Could not set device owner for user " + user
                            + " as the package " + pkg + " is not installed");
        }

        if (!componentCanBeSetAsDeviceAdmin(deviceOwnerComponent, user)) {
            throw new NeneException("Could not set device owner for user "
                    + user + " as component " + deviceOwnerComponent + " is not valid");
        }

        if (!allowAdditionalUsers) {
            Collection<UserReference> users = TestApis.users().all();

            if (users.size() > 1) {
                throw new NeneException("Could not set device owner for user "
                        + user + " as there are already additional users on the device: " + users);
            }

        }
        // TODO(scottjonathan): Check accounts

        return false;
    }

    private boolean componentCanBeSetAsDeviceAdmin(ComponentName component, UserReference user) {
        PackageManager packageManager =
                TestApis.context().instrumentedContext().getPackageManager();
        Intent intent = new Intent("android.app.action.DEVICE_ADMIN_ENABLED");
        intent.setComponent(component);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            List<ResolveInfo> r =
                    packageManager.queryBroadcastReceiversAsUser(
                            intent, /* flags= */ 0, user.userHandle());
            return (!r.isEmpty());
        }
    }

    /**
     * Get the device owner.
     */
    @Nullable
    public DeviceOwner getDeviceOwner() {
        fillCache();
        return mCachedDeviceOwner;
    }

    private void fillCache() {
        int retries = 5;
        while (true) {
            try {
                // TODO: Replace use of adb on supported versions of Android
                String devicePolicyDumpsysOutput =
                        ShellCommand.builder("dumpsys device_policy").execute();
                AdbDevicePolicyParser.ParseResult result = mParser.parse(devicePolicyDumpsysOutput);

                mCachedDeviceOwner = result.mDeviceOwner;
                mCachedProfileOwners = result.mProfileOwners;

                return;
            } catch (AdbParseException e) {
                if (e.adbOutput().contains("DUMP TIMEOUT") && retries-- > 0) {
                    // Sometimes this call times out - just retry
                    Log.e(LOG_TAG, "Dump timeout when filling cache, retrying", e);
                } else {
                    throw new NeneException("Error filling cache", e);
                }
            } catch (AdbException e) {
                throw new NeneException("Error filling cache", e);
            }
        }
    }

    /** See {@link android.app.admin.DevicePolicyManager#getPolicyExemptApps()}. */
    @Experimental
    public Set<String> getPolicyExemptApps() {
        try (PermissionContext p = TestApis.permissions().withPermission(MANAGE_DEVICE_ADMINS)) {
            return TestApis.context()
                    .instrumentedContext()
                    .getSystemService(DevicePolicyManager.class)
                    .getPolicyExemptApps();
        }
    }

    @Experimental
    public void forceNetworkLogs() {
        try (PermissionContext p = TestApis.permissions().withPermission(FORCE_DEVICE_POLICY_MANAGER_LOGS)) {
            long throttle = TestApis.context()
                    .instrumentedContext()
                    .getSystemService(DevicePolicyManager.class)
                    .forceNetworkLogs();

            if (throttle == -1) {
                throw new NeneException("Error forcing network logs: returned -1");
            }
            if (throttle == 0) {
                return;
            }
            try {
                Thread.sleep(throttle);
            } catch (InterruptedException e) {
                throw new NeneException("Error waiting for network log throttle", e);
            }

            forceNetworkLogs();
        }
    }

    /**
     * Sets the provided {@code packageName} as a device policy management role holder.
     */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    @Experimental
    public void setDevicePolicyManagementRoleHolder(String packageName)
            throws InterruptedException {
        if (!Versions.meetsMinimumSdkVersionRequirement(T)) {
            return;
        }
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_ROLE_HOLDERS)) {
            DefaultBlockingCallback blockingCallback = new DefaultBlockingCallback();
            RoleManager roleManager = TestApis.context().instrumentedContext()
                    .getSystemService(RoleManager.class);
            TestApis.roles().setBypassingRoleQualification(true);
            roleManager.addRoleHolderAsUser(
                    RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT,
                    packageName,
                    /* flags= */ 0,
                    TestApis.context().instrumentationContext().getUser(),
                    TestApis.context().instrumentedContext().getMainExecutor(),
                    blockingCallback::triggerCallback);

            boolean success = blockingCallback.await();
            if (!success) {
                fail("Could not set role holder of "
                        + RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT + ".");
            }
        }
    }

    /**
     * Unsets the provided {@code packageName} as a device policy management role holder.
     */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    @Experimental
    public void unsetDevicePolicyManagementRoleHolder(String packageName)
            throws InterruptedException {
        if (!Versions.meetsMinimumSdkVersionRequirement(T)) {
            return;
        }
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_ROLE_HOLDERS)) {
            DefaultBlockingCallback blockingCallback = new DefaultBlockingCallback();
            RoleManager roleManager = TestApis.context().instrumentedContext()
                    .getSystemService(RoleManager.class);
            roleManager.removeRoleHolderAsUser(
                    RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT,
                    packageName,
                    /* flags= */ 0,
                    TestApis.context().instrumentationContext().getUser(),
                    TestApis.context().instrumentedContext().getMainExecutor(),
                    blockingCallback::triggerCallback);
            TestApis.roles().setBypassingRoleQualification(false);

            boolean success = blockingCallback.await();
            if (!success) {
                fail("Failed to clear the role holder of "
                        + RoleManager.ROLE_DEVICE_POLICY_MANAGEMENT + ".");
            }
        }
    }

    private static class DefaultBlockingCallback extends BlockingCallback<Boolean> {
        public void triggerCallback(Boolean success) {
            callbackTriggered(success);
        }
    }
}
