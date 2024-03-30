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

package com.android.bedstead.harrier;

import static android.Manifest.permission.INTERACT_ACROSS_PROFILES;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.app.AppOpsManager.OPSTR_FINE_LOCATION;
import static android.app.AppOpsManager.OPSTR_START_FOREGROUND;
import static android.app.admin.DevicePolicyManager.DELEGATION_APP_RESTRICTIONS;
import static android.app.admin.DevicePolicyManager.DELEGATION_CERT_INSTALL;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.bedstead.harrier.OptionalBoolean.FALSE;
import static com.android.bedstead.harrier.OptionalBoolean.TRUE;
import static com.android.bedstead.harrier.UserType.ANY;
import static com.android.bedstead.harrier.UserType.PRIMARY_USER;
import static com.android.bedstead.harrier.UserType.SECONDARY_USER;
import static com.android.bedstead.harrier.annotations.RequireAospBuild.GMS_CORE_PACKAGE;
import static com.android.bedstead.harrier.annotations.RequireCnGmsBuild.CHINA_GOOGLE_SERVICES_FEATURE;
import static com.android.bedstead.harrier.annotations.enterprise.EnsureHasDelegate.AdminType.DEVICE_OWNER;
import static com.android.bedstead.harrier.annotations.enterprise.EnsureHasDelegate.AdminType.PRIMARY;
import static com.android.bedstead.nene.appops.AppOpsMode.ALLOWED;
import static com.android.bedstead.nene.permissions.CommonPermissions.READ_CONTACTS;
import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;
import static com.android.bedstead.nene.users.UserType.SECONDARY_USER_TYPE_NAME;
import static com.android.bedstead.nene.users.UserType.SYSTEM_USER_TYPE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.PermissionChecker;
import android.os.Build;
import android.os.Bundle;
import android.platform.test.annotations.AppModeFull;

import com.android.bedstead.harrier.annotations.EnsureBluetoothDisabled;
import com.android.bedstead.harrier.annotations.EnsureBluetoothEnabled;
import com.android.bedstead.harrier.annotations.EnsureDemoMode;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveAppOp;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet;
import com.android.bedstead.harrier.annotations.EnsureHasAppOp;
import com.android.bedstead.harrier.annotations.EnsureHasNoSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasNoTvProfile;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasTvProfile;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureNotDemoMode;
import com.android.bedstead.harrier.annotations.EnsurePackageNotInstalled;
import com.android.bedstead.harrier.annotations.EnsurePasswordNotSet;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasAppOp;
import com.android.bedstead.harrier.annotations.EnsureTestAppHasPermission;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.OtherUser;
import com.android.bedstead.harrier.annotations.RequireAospBuild;
import com.android.bedstead.harrier.annotations.RequireCnGmsBuild;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireGmsBuild;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireLowRamDevice;
import com.android.bedstead.harrier.annotations.RequireNotCnGmsBuild;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireNotLowRamDevice;
import com.android.bedstead.harrier.annotations.RequirePackageInstalled;
import com.android.bedstead.harrier.annotations.RequirePackageNotInstalled;
import com.android.bedstead.harrier.annotations.RequireRunNotOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnTvProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.harrier.annotations.RequireUserSupported;
import com.android.bedstead.harrier.annotations.TestTag;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDelegate;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoProfileOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnBackgroundDeviceOwnerUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnDeviceOwnerUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfProfileOwnerUsingParentInstance;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerProfileWithNoDeviceOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnUnaffiliatedDeviceOwnerSecondaryUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Tags;
import com.android.bedstead.remotedpc.RemoteDelegate;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.NotFoundException;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class DeviceStateTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final String APP_OP = OPSTR_FINE_LOCATION;

    private static final String TV_PROFILE_TYPE_NAME = "com.android.tv.profile";

    private static final String TEST_PERMISSION_1 = INTERACT_ACROSS_PROFILES;
    private static final String TEST_PERMISSION_2 = INTERACT_ACROSS_USERS_FULL;

    private static final DevicePolicyManager sLocalDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);

    // Expects that this package name matches an actual test app
    private static final String TEST_APP_PACKAGE_NAME = "com.android.bedstead.testapp.LockTaskApp";
    private static final String TEST_APP_PACKAGE_NAME2 = "com.android.bedstead.testapp.SmsApp";
    private static final String TEST_APP_USED_IN_FIELD_NAME =
            "com.android.bedstead.testapp.NotEmptyTestApp";
    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get();

    private static final long TIMEOUT = 4000000;

    @Test
    @EnsureHasWorkProfile
    public void workProfile_workProfileProvided_returnsWorkProfile() {
        assertThat(sDeviceState.workProfile()).isNotNull();
    }

    @Test
    @EnsureHasWorkProfile
    public void profile_profileIsProvided_returnsProfile() {
        assertThat(sDeviceState.profile(MANAGED_PROFILE_TYPE_NAME)).isNotNull();
    }

    @Test
    @RequireRunOnWorkProfile
    public void workProfile_runningOnWorkProfile_returnsCurrentProfile() {
        assertThat(sDeviceState.workProfile()).isEqualTo(TestApis.users().instrumented());
    }

    @Test
    @EnsureHasNoWorkProfile
    public void workProfile_noWorkProfile_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::workProfile);
    }

    @Test
    @EnsureHasNoWorkProfile
    @EnsureHasNoDeviceOwner
    public void workProfile_createdWorkProfile_throwsException() {
        try (UserReference workProfile = TestApis.users().createUser()
                .parent(TestApis.users().instrumented())
                .type(TestApis.users().supportedType(MANAGED_PROFILE_TYPE_NAME))
                .create()) {
            assertThrows(IllegalStateException.class, sDeviceState::workProfile);
        }
    }

    @Test
    @EnsureHasWorkProfile
    public void ensureHasWorkProfileAnnotation_workProfileExists() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(MANAGED_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNotNull();
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): When supported, test the forUser argument

    @Test
    @EnsureHasNoWorkProfile
    public void ensureHasNoWorkProfileAnnotation_workProfileDoesNotExist() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(MANAGED_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNull();
    }

    @Test
    @EnsureHasTvProfile
    public void tvProfile_tvProfileProvided_returnsTvProfile() {
        assertThat(sDeviceState.tvProfile()).isNotNull();
    }

    @Test
    @RequireRunOnTvProfile
    public void tvProfile_runningOnTvProfile_returnsCurrentProfile() {
        assertThat(sDeviceState.tvProfile()).isEqualTo(TestApis.users().instrumented());
    }

    @Test
    @EnsureHasNoTvProfile
    public void tvProfile_noTvProfile_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::tvProfile);
    }

    @Test
    @RequireUserSupported(TV_PROFILE_TYPE_NAME)
    @EnsureHasNoTvProfile
    public void tvProfile_createdTvProfile_throwsException() {
        try (UserReference tvProfile = TestApis.users().createUser()
                .parent(TestApis.users().instrumented())
                .type(TestApis.users().supportedType(TV_PROFILE_TYPE_NAME))
                .create()) {
            assertThrows(IllegalStateException.class, sDeviceState::tvProfile);
        }
    }

    @Test
    @EnsureHasTvProfile
    public void ensureHasTvProfileAnnotation_tvProfileExists() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(TV_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNotNull();
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): When supported, test the forUser argument

    @Test
    @RequireUserSupported(TV_PROFILE_TYPE_NAME)
    @EnsureHasNoTvProfile
    public void ensureHasNoTvProfileAnnotation_tvProfileDoesNotExist() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(TV_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNull();
    }

    @Test
    @EnsureHasSecondaryUser
    public void secondaryUser_secondaryUserProvided_returnsSecondaryUser() {
        assertThat(sDeviceState.secondaryUser()).isNotNull();
    }

    @Test
    @EnsureHasSecondaryUser
    public void user_userProvided_returnUser() {
        assertThat(sDeviceState.user(SECONDARY_USER_TYPE_NAME)).isNotNull();
    }

    @Test
    @RequireRunOnSecondaryUser
    public void secondaryUser_runningOnSecondaryUser_returnsCurrentUser() {
        assertThat(sDeviceState.secondaryUser()).isEqualTo(TestApis.users().instrumented());
    }

    @Test
    @EnsureHasNoSecondaryUser
    public void secondaryUser_noSecondaryUser_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::secondaryUser);
    }

    @Test
    @EnsureHasNoSecondaryUser
    public void secondaryUser_createdSecondaryUser_throwsException() {
        try (UserReference secondaryUser = TestApis.users().createUser()
                .type(TestApis.users().supportedType(SECONDARY_USER_TYPE_NAME))
                .create()) {
            assertThrows(IllegalStateException.class, sDeviceState::secondaryUser);
        }
    }

    @Test
    @EnsureHasSecondaryUser
    public void ensureHasSecondaryUserAnnotation_secondaryUserExists() {
        assertThat(TestApis.users().findUserOfType(
                TestApis.users().supportedType(SECONDARY_USER_TYPE_NAME))
        ).isNotNull();
    }

    // TODO(scottjonathan): test the installTestApp argument
    // TODO(scottjonathan): Test the forUser argument

    @Test
    @EnsureHasNoSecondaryUser
    public void ensureHasNoSecondaryUserAnnotation_secondaryUserDoesNotExist() {
        assertThat(TestApis.users().findUserOfType(
                TestApis.users().supportedType(SECONDARY_USER_TYPE_NAME))
        ).isNull();
    }

    @Test
    @EnsureHasPermission(TEST_PERMISSION_1)
    @RequireSdkVersion(min = Build.VERSION_CODES.R,
            reason = "Used permissions not available prior to R")
    public void ensureHasPermission_permissionIsGranted() {
        assertThat(TestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_1)).isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    @EnsureHasPermission({TEST_PERMISSION_1, TEST_PERMISSION_2})
    public void ensureHasPermission_multiplePermissions_permissionsAreGranted() {
        assertThat(TestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_1)).isEqualTo(PERMISSION_GRANTED);
        assertThat(TestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_2)).isEqualTo(PERMISSION_GRANTED);
    }

    @Test
    @EnsureDoesNotHavePermission(TEST_PERMISSION_1)
    @AppModeFull // withoutPermission does not work on instant apps
    public void ensureDoesNotHavePermission_permissionIsDenied() {
        assertThat(TestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_1)).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    @EnsureDoesNotHavePermission({TEST_PERMISSION_1, TEST_PERMISSION_2})
    @AppModeFull // withoutPermission does not work on instant apps
    public void ensureDoesNotHavePermission_multiplePermissions_permissionsAreDenied() {
        assertThat(TestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_1)).isEqualTo(PERMISSION_DENIED);
        assertThat(TestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_2)).isEqualTo(PERMISSION_DENIED);
    }

    @Test
    @EnsureHasPermission(TEST_PERMISSION_1)
    @EnsureDoesNotHavePermission(TEST_PERMISSION_2)
    @RequireSdkVersion(min = Build.VERSION_CODES.R,
            reason = "Used permissions not available prior to R")
    @AppModeFull // withoutPermission does not work on instant apps
    public void ensureHasPermissionAndDoesNotHavePermission_permissionsAreCorrect() {
        assertThat(TestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_1)).isEqualTo(PERMISSION_GRANTED);
        assertThat(TestApis.context().instrumentedContext()
                .checkSelfPermission(TEST_PERMISSION_2)).isEqualTo(PERMISSION_DENIED);
    }

    @EnsureHasDeviceOwner
    public void ensureHasDeviceOwnerAnnotation_deviceOwnerIsSet() {
        assertThat(TestApis.devicePolicy().getDeviceOwner()).isNotNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    public void ensureHasNoDeviceOwnerAnnotation_deviceOwnerIsNotSet() {
        assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
    }

    @Test
    @EnsureHasDeviceOwner
    public void deviceOwner_deviceOwnerIsSet_returnsDeviceOwner() {
        assertThat(sDeviceState.deviceOwner()).isNotNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    public void deviceOwner_deviceOwnerIsNotSet_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::deviceOwner);
    }

    @Test
    @EnsureHasProfileOwner
    public void ensureHasProfileOwnerAnnotation_defaultUser_profileOwnerIsSet() {
        assertThat(TestApis.devicePolicy().getProfileOwner(TestApis.users().instrumented()))
                .isNotNull();
    }

    @Test
    @EnsureHasNoProfileOwner
    public void ensureHasNoProfileOwnerAnnotation_defaultUser_profileOwnerIsNotSet() {
        assertThat(TestApis.devicePolicy().getProfileOwner(TestApis.users().instrumented()))
                .isNull();
    }

    @EnsureHasSecondaryUser
    @EnsureHasProfileOwner(onUser = UserType.SECONDARY_USER)
    public void ensureHasProfileOwnerAnnotation_otherUser_setsProfileOwner() {
        assertThat(TestApis.devicePolicy().getProfileOwner(sDeviceState.secondaryUser()))
                .isNotNull();
    }

    @EnsureHasSecondaryUser
    @EnsureHasNoProfileOwner(onUser = UserType.SECONDARY_USER)
    public void ensureHasNoProfileOwnerAnnotation_otherUser_profileOwnerIsNotSet() {
        assertThat(TestApis.devicePolicy().getProfileOwner(sDeviceState.secondaryUser())).isNull();
    }

    @Test
    @EnsureHasNoProfileOwner
    public void profileOwner_defaultUser_profileOwnerIsNotSet_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::profileOwner);
    }

    @Test
    @EnsureHasProfileOwner
    public void profileOwner_defaultUser_profileOwnerIsSet_returnsProfileOwner() {
        assertThat(TestApis.devicePolicy().getProfileOwner(TestApis.users().instrumented()))
                .isNotNull();
    }

    @EnsureHasSecondaryUser
    @EnsureHasProfileOwner(onUser = UserType.SECONDARY_USER)
    public void profileOwner_otherUser_profileOwnerIsSet_returnsProfileOwner() {
        assertThat(sDeviceState.profileOwner(sDeviceState.secondaryUser())).isNotNull();
    }

    @Test
    @EnsureHasSecondaryUser
    @EnsureHasNoProfileOwner(onUser = UserType.SECONDARY_USER)
    public void profileOwner_otherUser_profileOwnerIsNotSet_throwsException() {
        assertThrows(IllegalStateException.class, sDeviceState::profileOwner);
    }

    @Test
    public void profileOwner_userType_onUserIsNull_throwsException() {
        assertThrows(NullPointerException.class,
                () -> sDeviceState.profileOwner((UserType) null));
    }

    @Test
    public void profileOwner_userReference_onUserIsNull_throwsException() {
        assertThrows(NullPointerException.class,
                () -> sDeviceState.profileOwner((UserReference) null));
    }

    @RequireRunOnWorkProfile
    public void requireRunOnWorkProfileAnnotation_isRunningOnWorkProfile() {
        assertThat(
                TestApis.users().instrumented().type().name()).isEqualTo(MANAGED_PROFILE_TYPE_NAME);
    }

    @Test
    @RequireRunOnWorkProfile
    public void requireRunOnWorkProfileAnnotation_workProfileHasProfileOwner() {
        assertThat(
                TestApis.devicePolicy().getProfileOwner(TestApis.users().instrumented())
        ).isNotNull();
    }

    @Test
    @RequireRunOnSecondaryUser
    public void requireRunOnSecondaryUserAnnotation_isRunningOnSecondaryUser() {
        assertThat(
                TestApis.users().instrumented().type().name()).isEqualTo(SECONDARY_USER_TYPE_NAME);
    }

    @Test
    @IncludeRunOnDeviceOwnerUser
    public void includeRunOnDeviceOwnerUserAnnotation_isRunningOnDeviceOwnerUser() {
        assertThat(TestApis.devicePolicy().getDeviceOwner().user())
                .isEqualTo(TestApis.users().instrumented());
    }

    @Test
    @IncludeRunOnUnaffiliatedDeviceOwnerSecondaryUser
    public void includeRunOnNonAffiliatedDeviceOwnerSecondaryUserAnnotation_isRunningOnNonAffiliatedDeviceOwnerSecondaryUser() {
        assertThat(TestApis.devicePolicy().getDeviceOwner().user())
                .isNotEqualTo(TestApis.users().instrumented());
        assertThat(TestApis.users().instrumented().type().name())
                .isEqualTo(SECONDARY_USER_TYPE_NAME);
    }

    @Test
    @IncludeRunOnProfileOwnerProfileWithNoDeviceOwner
    public void includeRunOnProfileOwnerAnnotation_hasProfileOwner() {
        assertThat(
                TestApis.devicePolicy().getProfileOwner(TestApis.users().instrumented())
        ).isNotNull();
    }

    @Test
    @IncludeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerProfile
    public void includeRunOnSecondaryUserInDifferentProfileGroupToProfileOwnerAnnotation_isRunningOnSecondaryUserInDifferentProfileGroupToProfileOwner() {
        assertThat(TestApis.users().instrumented().type().name())
                .isEqualTo(SECONDARY_USER_TYPE_NAME);
        assertThat(sDeviceState.workProfile(PRIMARY_USER))
                .isNotEqualTo(TestApis.users().instrumented());
        assertThat(TestApis.devicePolicy().getProfileOwner(sDeviceState.workProfile(PRIMARY_USER)))
                .isNotNull();

        // TODO(scottjonathan): Assert profile groups are different
    }

    @RequirePackageInstalled(value = GMS_CORE_PACKAGE, onUser = ANY)
    public void requirePackageInstalledAnnotation_anyUser_packageIsInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUsers()).isNotEmpty();
    }

    @Test
    @RequirePackageInstalled(GMS_CORE_PACKAGE)
    public void requirePackageInstalledAnnotation_currentUser_packageIsInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUser())
                .isTrue();
    }

    @Test
    @RequirePackageNotInstalled(value = GMS_CORE_PACKAGE, onUser = ANY)
    public void requirePackageNotInstalledAnnotation_anyUser_packageIsNotInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUsers()).isEmpty();
    }

    @Test
    @RequirePackageNotInstalled(GMS_CORE_PACKAGE)
    public void requirePackageNotInstalledAnnotation_currentUser_packageIsNotInstalled() {
        Package pkg = TestApis.packages().find(GMS_CORE_PACKAGE);

        assertThat(pkg.installedOnUser()).isFalse();
    }

    @Test
    @EnsurePackageNotInstalled(value = GMS_CORE_PACKAGE, onUser = ANY)
    @Ignore // TODO(scottjonathan): Restore this with a package which can be uninstalled
    public void ensurePackageNotInstalledAnnotation_anyUser_packageIsNotInstalled() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).installedOnUsers()).isEmpty();
    }

    @Test
    @EnsurePackageNotInstalled(GMS_CORE_PACKAGE)
    @Ignore // TODO(scottjonathan): Restore this with a package which can be uninstalled
    public void ensurePackageNotInstalledAnnotation_currentUser_packageIsNotInstalled() {
        Package pkg = TestApis.packages().find(GMS_CORE_PACKAGE);

        assertThat(pkg.installedOnUser()).isFalse();
    }

    @Test
    @RequireAospBuild
    public void requireAospBuildAnnotation_isRunningOnAospBuild() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).exists()).isFalse();
    }

    @Test
    @RequireGmsBuild
    public void requireGmsBuildAnnotation_isRunningOnGmsbuild() {
        assertThat(TestApis.packages().find(GMS_CORE_PACKAGE).exists()).isTrue();
    }

    @Test
    @RequireCnGmsBuild
    public void requireCnGmsBuildAnnotation_isRunningOnCnGmsBuild() {
        assertThat(TestApis.packages().features()).contains(CHINA_GOOGLE_SERVICES_FEATURE);
    }

    @Test
    @RequireNotCnGmsBuild
    public void requireNotCnGmsBuildAnnotation_isNotRunningOnCnGmsBuild() {
        assertThat(TestApis.packages().features()).doesNotContain(CHINA_GOOGLE_SERVICES_FEATURE);

    }

    @Test
    @RequireFeature(CHINA_GOOGLE_SERVICES_FEATURE)
    public void requireHasFeatureAnnotation_doesNotHaveFeature() {
        assertThat(TestApis.packages().features()).contains(CHINA_GOOGLE_SERVICES_FEATURE);
    }

    @Test
    @RequireDoesNotHaveFeature(CHINA_GOOGLE_SERVICES_FEATURE)
    public void requireDoesNotHaveFeatureAnnotation_doesNotHaveFeature() {
        assertThat(TestApis.packages().features()).doesNotContain(CHINA_GOOGLE_SERVICES_FEATURE);
    }

    @Test
    @RequireSdkVersion(min = 27)
    public void requireSdkVersionAnnotation_min_minIsMet() {
        assertThat(Build.VERSION.SDK_INT).isGreaterThan(26);
    }

    @Test
    @RequireSdkVersion(max = 30)
    public void requireSdkVersionAnnotation_max_maxIsMet() {
        assertThat(Build.VERSION.SDK_INT).isLessThan(31);
    }

    @Test
    @RequireSdkVersion(min = 27, max = 30)
    public void requireSdkVersionAnnotation_minAndMax_bothAreMet() {
        assertThat(Build.VERSION.SDK_INT).isGreaterThan(26);
        assertThat(Build.VERSION.SDK_INT).isLessThan(31);
    }

    @Test
    @RequireRunOnPrimaryUser
    public void requireRunOnPrimaryUserAnnotation_isRunningOnPrimaryUser() {
        assertThat(TestApis.users().instrumented().type().name())
                .isEqualTo(SYSTEM_USER_TYPE_NAME);
    }

    @Test
    @RequireRunOnTvProfile
    public void requireRunOnTvProfileAnnotation_isRunningOnTvProfile() {
        assertThat(TestApis.users().instrumented().type().name())
                .isEqualTo(TV_PROFILE_TYPE_NAME);
    }

    @Test
    @IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner
    public void includeRunOnParentOfProfileOwnerAnnotation_isRunningOnParentOfProfileOwner() {
        assertThat(sDeviceState.workProfile()).isNotNull();
    }

    @Test
    @RequireRunOnPrimaryUser
    public void requireRunOnUser_isCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.primaryUser());
    }

    @Test
    @RequireRunOnPrimaryUser(switchedToUser = FALSE)
    public void requireRunOnUser_specifyNotSwitchedToUser_isNotCurrentUser() {
        assertThat(TestApis.users().current()).isNotEqualTo(sDeviceState.primaryUser());
    }

    @Test
    @RequireRunNotOnSecondaryUser
    public void requireRunNotOnSecondaryUser_currentUserIsNotSecondary() {
        assertThat(TestApis.users().current().type().name()).isNotEqualTo(SECONDARY_USER_TYPE_NAME);
    }

    @Test
    @RequireRunNotOnSecondaryUser
    public void requireRunNotOnSecondaryUser_instrumentedUserIsNotSecondary() {
        assertThat(TestApis.users().instrumented().type().name())
                .isNotEqualTo(SECONDARY_USER_TYPE_NAME);
    }

    @Test
    @EnsureHasSecondaryUser(switchedToUser = FALSE) // We don't test the default as it's ANY
    public void ensureHasUser_specifyIsNotSwitchedToUser_isNotCurrentUser() {
        assertThat(TestApis.users().current()).isNotEqualTo(sDeviceState.secondaryUser());
    }

    @Test
    @EnsureHasSecondaryUser(switchedToUser = TRUE)
    public void ensureHasUser_specifySwitchedToUser_isCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.secondaryUser());
    }

    @Test
    @RequireRunOnWorkProfile
    public void requireRunOnProfile_parentIsCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.workProfile().parent());
    }

    @Test
    @RequireRunOnWorkProfile(switchedToParentUser = FALSE)
    public void requireRunOnProfile_specifyNotSwitchedToParentUser_parentIsNotCurrentUser() {
        assertThat(TestApis.users().current()).isNotEqualTo(sDeviceState.workProfile().parent());
    }

    @Test
    @EnsureHasWorkProfile(switchedToParentUser = FALSE) // We don't test the default as it's ANY
    public void ensureHasWorkProfile_specifyNotSwitchedToParentUser_parentIsNotCurrentUser() {
        assertThat(TestApis.users().current()).isNotEqualTo(sDeviceState.workProfile().parent());
    }

    @Test
    @EnsureHasWorkProfile(switchedToParentUser = TRUE)
    public void ensureHasWorkProfile_specifySwitchedToParentUser_parentIsCurrentUser() {
        assertThat(TestApis.users().current()).isEqualTo(sDeviceState.workProfile().parent());
    }

    @Test
    @IncludeRunOnBackgroundDeviceOwnerUser
    public void includeRunOnBackgroundDeviceOwnerUserAnnotation_isRunningOnDeviceOwnerUser() {
        assertThat(TestApis.users().instrumented())
                .isEqualTo(sDeviceState.dpc().user());
    }

    @Test
    @IncludeRunOnBackgroundDeviceOwnerUser
    public void includeRunOnBackgroundDeviceOwnerUserAnnotation_isNotCurrentUser() {
        assertThat(TestApis.users().current())
                .isNotEqualTo(TestApis.users().instrumented());
    }

    @Test
    @EnsureHasNoProfileOwner
    public void ensureHasNoProfileOwnerAnnotation_currentUserHasNoProfileOwner() {
        assertThat(TestApis.devicePolicy()
                .getProfileOwner(TestApis.users().instrumented()))
                .isNull();
    }

    @Test
    @EnsureHasNoDeviceOwner
    public void ensureHasNoDeviceOwnerAnnotation_noDeviceOwner() {
        assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
    }

    @Test
    @EnsureHasNoDpc
    public void ensureHasNoDpcAnnotation_currentUserHasNoProfileOwner() {
        assertThat(TestApis.devicePolicy()
                .getProfileOwner(TestApis.users().instrumented()))
                .isNull();
    }

    @Test
    @EnsureHasNoDpc
    public void ensureHasNoDpcAnnotation_noDeviceOwner() {
        assertThat(TestApis.devicePolicy().getDeviceOwner()).isNull();
    }

    @Test
    @EnsureHasNoDpc
    public void ensureHasNoDpcAnnotation_workProfileDoesNotExist() {
        assertThat(TestApis.users().findProfileOfType(
                TestApis.users().supportedType(MANAGED_PROFILE_TYPE_NAME),
                TestApis.users().instrumented())
        ).isNull();
    }

    @Test
    @RequireNotHeadlessSystemUserMode
    public void requireNotHeadlessSystemUserModeAnnotation_notHeadlessSystemUserMode() {
        assertThat(TestApis.users().isHeadlessSystemUserMode()).isFalse();
    }

    @Test
    @RequireHeadlessSystemUserMode
    public void requireHeadlessSystemUserModeAnnotation_isHeadlessSystemUserMode() {
        assertThat(TestApis.users().isHeadlessSystemUserMode()).isTrue();
    }

    @Test
    @RequireLowRamDevice(reason = "Test")
    public void requireLowRamDeviceAnnotation_isLowRamDevice() {
        assertThat(TestApis.context().instrumentedContext().getSystemService(ActivityManager.class)
                .isLowRamDevice()).isTrue();
    }

    @Test
    @RequireNotLowRamDevice(reason = "Test")
    public void requireNotLowRamDeviceAnnotation_isNotLowRamDevice() {
        assertThat(TestApis.context().instrumentedContext().getSystemService(ActivityManager.class)
                .isLowRamDevice()).isFalse();
    }

    @Test
    @TestTag("TestTag")
    public void testTagAnnoation_testTagIsSet() {
        assertThat(Tags.hasTag("TestTag")).isTrue();
    }

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasDelegate(admin = DEVICE_OWNER, scopes = DELEGATION_CERT_INSTALL, isPrimary = true)
    public void ensureHasPrimaryDelegateAnnotation_dpcReturnsDelegate() {
        assertThat(sDeviceState.dpc()).isInstanceOf(RemoteDelegate.class);
    }

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasDelegate(admin = DEVICE_OWNER, scopes = DELEGATION_CERT_INSTALL, isPrimary = false)
    public void ensureHasNonPrimaryDelegateAnnotation_dpcReturnsDpc() {
        assertThat(sDeviceState.dpc()).isInstanceOf(RemoteDpc.class);
    }

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasDelegate(admin = DEVICE_OWNER, scopes = DELEGATION_CERT_INSTALL, isPrimary = true)
    public void ensureHasDelegateAnnotation_dpcCanUseDelegatedFunctionality() {
        assertThat(sDeviceState.dpc().devicePolicyManager().getEnrollmentSpecificId()).isNotNull();
    }

    @Test
    @EnsureHasDeviceOwner
    @EnsureHasDelegate(admin = DEVICE_OWNER,
            scopes = {DELEGATION_CERT_INSTALL, DELEGATION_APP_RESTRICTIONS}, isPrimary = true)
    public void ensureHasDelegateAnnotation_multipleScopes_dpcCanUseAllDelegatedFunctionality() {
        assertThat(sDeviceState.dpc().devicePolicyManager().getEnrollmentSpecificId()).isNotNull();
        sDeviceState.dpc().devicePolicyManager()
                .setApplicationRestrictions(
                        sDeviceState.dpc().componentName(),
                        sDeviceState.dpc().packageName(), new Bundle());
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @EnsureHasDelegate(admin = PRIMARY, scopes = {})
    public void ensureHasDelegateAnnotation_primaryAdminWithoutReplace_dpcReturnsDpc() {
        assertThat(sDeviceState.dpc()).isInstanceOf(RemoteDpc.class);
    }

    @Test
    @EnsureHasDeviceOwner(isPrimary = true)
    @EnsureHasDelegate(admin = PRIMARY, scopes = {}, isPrimary = true)
    public void ensureHasDelegateAnnotation_primaryAdminAndReplace_dpcReturnsDelegate() {
        assertThat(sDeviceState.dpc()).isInstanceOf(RemoteDelegate.class);
    }

    @Test
    @EnsureScreenIsOn
    public void ensureScreenIsOnAnnotation_screenIsOn() {
        assertThat(TestApis.device().isScreenOn()).isTrue();
    }

    @Test
    @EnsurePasswordNotSet
    public void requirePasswordNotSetAnnotation_passwordNotSet() {
        assertThat(TestApis.users().instrumented().hasPassword()).isFalse();
    }

    @Test
    @IncludeRunOnParentOfProfileOwnerUsingParentInstance
    public void includeRunOnParentOfProfileOwnerUsingProfileInstanceAnnotation_runningOnParentOfProfile() {
        assertThat(sDeviceState.workProfile().parent()).isEqualTo(TestApis.users().instrumented());
    }

    @Test
    @IncludeRunOnParentOfProfileOwnerUsingParentInstance
    public void includeRunOnParentOfProfileOwnerUsingProfileInstanceAnnotation_dpcIsOnProfile() {
        assertThat(sDeviceState.dpc().user()).isEqualTo(sDeviceState.workProfile());
    }

    @Test
    @IncludeRunOnParentOfProfileOwnerUsingParentInstance
    public void includeRunOnParentOfProfileOwnerUsingProfileInstanceAnnotation_devicePolicyManagerAffectsParent() {
        long previousRequiredStrongAuthTimeout =
                sLocalDevicePolicyManager.getRequiredStrongAuthTimeout(/* admin= */ null);
        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setRequiredStrongAuthTimeout(sDeviceState.dpc().componentName(), TIMEOUT);

            assertThat(sLocalDevicePolicyManager
                    .getRequiredStrongAuthTimeout(/* admin= */ null)).isEqualTo(TIMEOUT);
        } finally {
            sDeviceState.dpc().devicePolicyManager()
                    .setRequiredStrongAuthTimeout(
                            sDeviceState.dpc().componentName(), previousRequiredStrongAuthTimeout);
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @OtherUser(SECONDARY_USER)
    public void otherUserAnnotation_otherUserReturnsCorrectType() {
        assertThat(sDeviceState.otherUser()).isEqualTo(sDeviceState.secondaryUser());
    }

    @Test
    public void otherUser_noOtherUserSpecified_throwsException() {
        assertThrows(IllegalStateException.class, () -> sDeviceState.otherUser());
    }

    @Test
    @EnsureBluetoothEnabled
    public void ensureBluetoothEnabledAnnotation_bluetoothIsEnabled() {
        assertThat(TestApis.bluetooth().isEnabled()).isTrue();
    }

    @Test
    @EnsureBluetoothDisabled
    public void ensureBluetoothDisabledAnnotation_bluetoothIsDisabled() {
        assertThat(TestApis.bluetooth().isEnabled()).isFalse();
    }

    @EnsureHasAppOp(APP_OP)
    public void ensureHasAppOpAnnotation_appOpIsAllowed() {
        assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isTrue();
    }

    @Test
    @EnsureDoesNotHaveAppOp(APP_OP)
    public void ensureDoesNotHaveAppOpAnnotation_appOpIsNotAllowed() {
        assertThat(TestApis.permissions().hasAppOpAllowed(APP_OP)).isFalse();
    }

    // We run this test twice to ensure that teardown doesn't change behaviour
    @Test
    public void testApps_testAppsAreAvailableToMultipleTests_1() {
        assertThat(sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_PACKAGE_NAME).get()).isNotNull();
    }

    @Test
    public void testApps_testAppsAreAvailableToMultipleTests_2() {
        assertThat(sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_PACKAGE_NAME).get()).isNotNull();
    }

    // We run this test twice to ensure that teardown doesn't change behaviour
    @Test
    public void testApps_staticTestAppsAreNotReleased_1() {
        assertThrows(NotFoundException.class, () -> sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get());
    }

    @Test
    public void testApps_staticTestAppsAreNotReleased_2() {
        assertThrows(NotFoundException.class, () -> sDeviceState.testApps().query()
                .wherePackageName().isEqualTo(TEST_APP_USED_IN_FIELD_NAME).get());
    }

    @EnsureTestAppInstalled(packageName = TEST_APP_PACKAGE_NAME)
    @Test
    public void ensureTestAppInstalledAnnotation_testAppIsInstalled() {
        assertThat(TestApis.packages().find(TEST_APP_PACKAGE_NAME).installedOnUser()).isTrue();
    }

    @EnsureHasSecondaryUser
    @EnsureTestAppInstalled(packageName = TEST_APP_PACKAGE_NAME, onUser = SECONDARY_USER)
    @Test
    public void ensureTestAppInstalledAnnotation_testAppIsInstalledOnCorrectUser() {
        assertThat(TestApis.packages().find(TEST_APP_PACKAGE_NAME)
                .installedOnUser(sDeviceState.secondaryUser())).isTrue();
    }

    @EnsureTestAppInstalled(packageName = TEST_APP_PACKAGE_NAME)
    @Test
    public void testApp_returnsTestApp() {
        assertThat(sDeviceState.testApp().packageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
    }

    @Test
    public void testApp_noHarrierManagedTestApp_throwsException() {
        try (TestAppInstance testApp = sDeviceState.testApps().any().install()) {
            assertThrows(NeneException.class, sDeviceState::testApp);
        }
    }

    @EnsureTestAppInstalled(key = "testApp1", packageName = TEST_APP_PACKAGE_NAME)
    @EnsureTestAppInstalled(key = "testApp2", packageName = TEST_APP_PACKAGE_NAME2)
    @Test
    public void testApp_withKey_returnsCorrectTestApp() {
        assertThat(sDeviceState.testApp("testApp1").packageName())
                .isEqualTo(TEST_APP_PACKAGE_NAME);
        assertThat(sDeviceState.testApp("testApp2").packageName())
                .isEqualTo(TEST_APP_PACKAGE_NAME2);
    }

    @EnsureTestAppInstalled(packageName = TEST_APP_PACKAGE_NAME, isPrimary = true)
    @Test
    public void dpc_primaryTestApp_returnsTestApp() {
        assertThat(sDeviceState.dpc().packageName()).isEqualTo(TEST_APP_PACKAGE_NAME);
    }

    @EnsureTestAppInstalled(packageName = TEST_APP_PACKAGE_NAME)
    @EnsureTestAppHasPermission(READ_CONTACTS)
    @Test
    public void ensureTestAppHasPermissionAnnotation_testAppHasPermission() {
        assertThat(sDeviceState.testApp().context().checkSelfPermission(
                READ_CONTACTS)).isEqualTo(PermissionChecker.PERMISSION_GRANTED);
    }

    @EnsureTestAppInstalled(packageName = TEST_APP_PACKAGE_NAME)
    @EnsureTestAppHasAppOp(OPSTR_START_FOREGROUND)
    @Test
    public void ensureTestAppHasAppOpAnnotation_testAppHasAppOp() {
        assertThat(sDeviceState.testApp()
                .testApp().pkg().appOps().get(OPSTR_START_FOREGROUND)).isEqualTo(ALLOWED);
    }

    @EnsureGlobalSettingSet(key = "testGlobalSetting", value = "testValue")
    @Test
    public void ensureGlobalSettingSetAnnotation_globalSettingIsSet() {
        assertThat(TestApis.settings().global().getString("testGlobalSetting"))
                .isEqualTo("testValue");
    }

    @EnsureDemoMode
    @Test
    public void ensureDemoModeAnnotation_deviceIsInDemoMode() {
        assertThat(TestApis.settings().global().getInt("device_demo_mode"))
                .isEqualTo(1);
    }

    @EnsureNotDemoMode
    @Test
    public void ensureNotDemoModeAnnotation_deviceIsNotInDemoMode() {
        assertThat(TestApis.settings().global().getInt("device_demo_mode"))
                .isEqualTo(0);
    }
}
