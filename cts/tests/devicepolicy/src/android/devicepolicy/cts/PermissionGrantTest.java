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

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.ACTIVITY_RECOGNITION;
import static android.Manifest.permission.BODY_SENSORS;
import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.READ_CALENDAR;
import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.READ_SMS;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.app.admin.DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY;
import static android.app.admin.DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT;
import static android.app.admin.DevicePolicyManager.PERMISSION_POLICY_PROMPT;

import static com.android.bedstead.nene.notifications.NotificationListenerQuerySubject.assertThat;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureUnlocked;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.NotificationsTest;
import com.android.bedstead.harrier.annotations.StringTestParameter;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.SetPermissionGrantState;
import com.android.bedstead.harrier.policies.SetSensorPermissionGranted;
import com.android.bedstead.harrier.policies.SetSmsPermissionGranted;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.notifications.NotificationListener;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivity;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@RunWith(BedsteadJUnit4.class)
public final class PermissionGrantTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // From PermissionController ADMIN_AUTO_GRANTED_PERMISSIONS_ALERTING_NOTIFICATION_CHANNEL_ID
    private static final String AUTO_GRANTED_PERMISSIONS_CHANNEL_ID =
            "alerting auto granted permissions";
    private static final String PERMISSION_CONTROLLER_PACKAGE_NAME =
            TestApis.context().instrumentedContext().getPackageManager()
                    .getPermissionControllerPackageName();

    private static final String GRANTABLE_PERMISSION = READ_CALENDAR;

    private static final String DEVELOPMENT_PERMISSION = INTERACT_ACROSS_USERS;

    @StringTestParameter({
            ACCESS_FINE_LOCATION,
            ACCESS_BACKGROUND_LOCATION,
            ACCESS_COARSE_LOCATION,
            CAMERA,
            ACTIVITY_RECOGNITION,
            BODY_SENSORS})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface SensorPermissionTestParameter {
    }

    @StringTestParameter({
            ACCESS_FINE_LOCATION,
            ACCESS_BACKGROUND_LOCATION,
            ACCESS_COARSE_LOCATION})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface LocationPermissionTestParameter {
    }

    @StringTestParameter({
            // Grantable permission
            READ_CALENDAR,
            READ_SMS, // All DPCs can deny sms permission
            // All DPCs can deny sensor permissions
            ACCESS_FINE_LOCATION,
            ACCESS_BACKGROUND_LOCATION,
            ACCESS_COARSE_LOCATION,
            CAMERA,
            ACTIVITY_RECOGNITION,
            BODY_SENSORS
    })
    @Retention(RetentionPolicy.RUNTIME)
    private @interface DeniablePermissionTestParameter {
    }

    private static final String NON_EXISTING_PACKAGE_NAME = "non.existing.package";
    private static final String NOT_DECLARED_PERMISSION = "not.declared.permission";

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .wherePermissions().contains(
                    READ_SMS,
                    CAMERA,
                    ACTIVITY_RECOGNITION,
                    BODY_SENSORS,
                    READ_CONTACTS,
                    ACCESS_FINE_LOCATION,
                    ACCESS_BACKGROUND_LOCATION,
                    ACCESS_COARSE_LOCATION
            ).wherePermissions().doesNotContain(
                    NOT_DECLARED_PERMISSION
            ).get();
    private static final TestApp sNotInstalledTestApp = sDeviceState.testApps().query()
            .wherePermissions().contains(GRANTABLE_PERMISSION)
            .whereActivities().isNotEmpty().get();
    private static TestAppInstance sTestAppInstance =
            sTestApp.install(TestApis.users().instrumented());

    @AfterClass
    public static void teardownClass() {
        sTestAppInstance.uninstall();
    }

    @PolicyDoesNotApplyTest(policy = SetSmsPermissionGranted.class)
    public void getPermissionGrantState_smsPermission_notAbleToSetState_alsoCantReadState() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), READ_SMS);
        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, PERMISSION_GRANT_STATE_GRANTED);

            sTestApp.pkg().grantPermission(TestApis.users().instrumented(), READ_SMS);
            // TODO(b/204041462): Replace granting the permission here with the user pressing the
            //  "deny" button on the permission

            assertWithMessage("Should not be able to read permission grant state but can")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            READ_SMS))
                    .isEqualTo(PERMISSION_GRANT_STATE_DEFAULT);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, existingGrantState);
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(), READ_SMS);
        }
    }

    @PolicyDoesNotApplyTest(policy = SetSensorPermissionGranted.class)
    public void getPermissionGrantState_sensorPermission_notAbleToSetState_alsoCantReadState(
            @SensorPermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_GRANTED);

            sTestApp.pkg().grantPermission(TestApis.users().instrumented(), permission);
            // TODO(b/204041462): Replace granting the permission here with the user pressing the
            //  "deny" button on the permission

            assertWithMessage("Should not be able to read permission grant state but can")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission))
                    .isEqualTo(PERMISSION_GRANT_STATE_DEFAULT);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(), permission);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void denyPermission_setsGrantState(@DeniablePermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);

        try {
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission, PERMISSION_GRANT_STATE_DENIED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to denied but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission))
                    .isEqualTo(PERMISSION_GRANT_STATE_DENIED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void grantPermission_setsGrantState() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);

        try {
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to granted but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION))
                    .isEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionGrantState.class)
    public void denyPermission_permissionIsDenied(
            @DeniablePermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            sTestApp.pkg().grantPermission(TestApis.users().instrumented(), permission);
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_DENIED);

            assertWithMessage("Permission should not be granted but was").that(
                    sTestApp.pkg().hasPermission(permission)).isFalse();

            // TODO(b/204041462): Test that the app cannot request the permission
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(), permission);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionGrantState.class)
    public void grantPermission_permissionIsGranted() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(),
                    GRANTABLE_PERMISSION);
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should be granted but was not").that(
                    sTestApp.pkg().hasPermission(GRANTABLE_PERMISSION)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(),
                    GRANTABLE_PERMISSION);
        }
    }

    @PolicyDoesNotApplyTest(policy = SetPermissionGrantState.class)
    public void denyPermission_doesNotApply_permissionIsNotDenied(
            @DeniablePermissionTestParameter String permission) {
        try {
            sTestApp.pkg().grantPermission(TestApis.users().instrumented(), permission);

            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_DENIED);

            assertWithMessage("Permission should not be denied but was").that(
                    sTestApp.pkg().hasPermission(permission)).isTrue();
        } finally {
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(), permission);
        }
    }

    @PolicyDoesNotApplyTest(policy = SetPermissionGrantState.class)
    public void grantPermission_doesNotApply_permissionIsNotGranted(
            @DeniablePermissionTestParameter String permission) {
        try {
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(), permission);

            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should not be granted but was").that(
                    sTestApp.pkg().hasPermission(permission)).isFalse();
        } finally {
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(), permission);
        }
    }

    @CannotSetPolicyTest(policy = SetPermissionGrantState.class)
    public void grantPermission_cannotBeSet_throwsException(
            @DeniablePermissionTestParameter String permission) {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setPermissionGrantState(sDeviceState.dpc().componentName(), sTestApp.packageName(),
                        permission, PERMISSION_GRANT_STATE_GRANTED));
    }

    // TODO(b/204041462): Add test that the user can manually grant sensor permissions

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void grantDevelopmentPermission_cannotGrant() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), DEVELOPMENT_PERMISSION);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            DEVELOPMENT_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return false")
                    .that(wasSet).isFalse();
            assertWithMessage("Permission grant state should not be set to granted but was")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            DEVELOPMENT_PERMISSION))
                    .isNotEqualTo(PERMISSION_GRANT_STATE_GRANTED);
            assertWithMessage("Permission should not be granted but was")
                    .that(sTestApp.pkg().hasPermission(
                    DEVELOPMENT_PERMISSION)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    DEVELOPMENT_PERMISSION, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void denyDevelopmentPermission_cannotDeny() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), DEVELOPMENT_PERMISSION);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            DEVELOPMENT_PERMISSION, PERMISSION_GRANT_STATE_DENIED);

            assertWithMessage("setPermissionGrantState did not return false")
                    .that(wasSet).isFalse();
            assertWithMessage("Permission grant state should not be set to granted but was")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            DEVELOPMENT_PERMISSION))
                    .isNotEqualTo(PERMISSION_GRANT_STATE_DENIED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    DEVELOPMENT_PERMISSION, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setDevelopmentPermissionToDefault_cannotSet() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), DEVELOPMENT_PERMISSION);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            DEVELOPMENT_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);

            assertWithMessage("setPermissionGrantState did not return false")
                    .that(wasSet).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    DEVELOPMENT_PERMISSION, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetSmsPermissionGranted.class)
    public void grantSmsPermission_setsGrantState() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), READ_SMS);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            READ_SMS, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to granted but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            READ_SMS))
                    .isEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetSensorPermissionGranted.class)
    @Ignore("TODO(198280344): Re-enable when we can set sensor permissions using device owner")
    public void grantSensorPermission_setsGrantState(
            @SensorPermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to granted but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission))
                    .isEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @PolicyAppliesTest(policy = SetSmsPermissionGranted.class)
    public void grantSmsPermission_permissionIsGranted() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), READ_SMS);
        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should be granted but was not").that(
                    sTestApp.pkg().hasPermission(READ_SMS)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, existingGrantState);
        }
    }

    @PolicyAppliesTest(policy = SetSensorPermissionGranted.class)
    @Ignore("TODO(198280344): Re-enable when we can set sensor permissions using device owner")
    public void grantSensorPermission_permissionIsGranted(
            @SensorPermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should be granted but was not").that(
                    sTestApp.pkg().hasPermission(permission)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @PolicyDoesNotApplyTest(policy = SetSmsPermissionGranted.class)
    public void grantSmsPermission_doesNotApplyToUser_permissionIsNotGranted() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), READ_SMS);
        sTestApp.pkg().denyPermission(READ_SMS);

        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should not be granted but was").that(
                    sTestApp.pkg().hasPermission(READ_SMS)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, existingGrantState);
        }
    }

    @PolicyDoesNotApplyTest(policy = SetSensorPermissionGranted.class)
    @Ignore("TODO(198280344): Re-enable when we can set sensor permissions using device owner")
    public void grantSensorPermission_doesNotApplyToUser_permissionIsNotGranted(
            @SensorPermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should not be granted but was").that(
                    sTestApp.pkg().hasPermission(permission)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @CannotSetPolicyTest(policy = SetSmsPermissionGranted.class, includeNonDeviceAdminStates = false)
    public void grantSmsPermission_cannotBeApplied_returnsTrueButDoesNotSetGrantState() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), READ_SMS);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            READ_SMS, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should not be set to granted but was")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            READ_SMS))
                    .isNotEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, existingGrantState);
        }
    }

    @CannotSetPolicyTest(policy = SetSmsPermissionGranted.class, includeDeviceAdminStates = false)
    public void grantSmsPermission_nonDeviceAdmin_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                        sDeviceState.dpc().componentName(), sTestApp.packageName(),
                        READ_SMS, PERMISSION_GRANT_STATE_GRANTED));
    }

    @CannotSetPolicyTest(policy = SetSensorPermissionGranted.class)
    public void grantSensorPermission_cannotBeApplied_returnsTrueButDoesNotSetGrantState(
            @SensorPermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should not be set to granted but was")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission))
                    .isNotEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @CannotSetPolicyTest(policy = SetPermissionGrantState.class)
    public void getPermissionGrantState_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION);
        });
    }

    @CannotSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setPermissionPolicy_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_GRANT);
        });
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setPermissionPolicy_setsPolicy(@IntTestParameter(
            {PERMISSION_POLICY_AUTO_GRANT, PERMISSION_POLICY_AUTO_DENY}) int policy) {
        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), policy);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPermissionPolicy(
                    sDeviceState.dpc().componentName())).isEqualTo(policy);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionGrantState.class)
    @EnsureScreenIsOn
    @EnsureUnlocked
    public void setPermissionPolicy_grant_automaticallyGrantsPermissions() {
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_GRANT);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(new String[]{GRANTABLE_PERMISSION}, /* requestCode= */ 0);

            Poll.forValue("Permission granted",
                    () -> sNotInstalledTestApp.pkg().hasPermission(GRANTABLE_PERMISSION))
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionGrantState.class)
    @EnsureScreenIsOn
    @EnsureUnlocked
    public void setPermissionPolicy_deny_automaticallyDeniesPermissions() {
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_DENY);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(new String[]{GRANTABLE_PERMISSION}, /* requestCode= */ 0);

            Poll.forValue("Permission granted",
                    () -> sNotInstalledTestApp.pkg().hasPermission(GRANTABLE_PERMISSION))
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }


    @PolicyAppliesTest(policy = SetSensorPermissionGranted.class)
    @NotificationsTest
    @Ignore("TODO(198280344): Re-enable when we can set sensor permissions using device owner")
    public void grantLocationPermission_userNotified(
            @LocationPermissionTestParameter String permission) throws Exception {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                sDeviceState.dpc().componentName(), sTestApp.packageName(),
                permission, PERMISSION_GRANT_STATE_DEFAULT);
        try (NotificationListener notifications = TestApis.notifications().createListener()) {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_GRANTED);

            assertThat(notifications.query()
                    .wherePackageName().isEqualTo(PERMISSION_CONTROLLER_PACKAGE_NAME)
                    .whereNotification().channelId().isEqualTo(
                            AUTO_GRANTED_PERMISSIONS_CHANNEL_ID)
            ).wasPosted();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setPermissionGrantState_permissionIsNotDeclared_doesNotSetGrantState() {
        boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                .setPermissionGrantState(sDeviceState.dpc().componentName(), sTestApp.packageName(),
                        NOT_DECLARED_PERMISSION, PERMISSION_GRANT_STATE_DENIED);

        assertWithMessage("setPermissionGrantState did not return false")
                .that(wasSet).isFalse();
        assertWithMessage("Permission grant state should not be changed but was")
                .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                        sDeviceState.dpc().componentName(), sTestApp.packageName(),
                        NOT_DECLARED_PERMISSION))
                .isEqualTo(PERMISSION_GRANT_STATE_DEFAULT);
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setPermissionGrantState_appIsNotInstalled_doesNotSetGrantState() {
        boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                .setPermissionGrantState(
                        sDeviceState.dpc().componentName(), NON_EXISTING_PACKAGE_NAME,
                        GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DENIED);

        assertWithMessage("setPermissionGrantState did not return false")
                .that(wasSet).isFalse();
        assertWithMessage("Permission grant state should not be changed but was")
                .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                        sDeviceState.dpc().componentName(), NON_EXISTING_PACKAGE_NAME,
                        NOT_DECLARED_PERMISSION))
                .isEqualTo(PERMISSION_GRANT_STATE_DEFAULT);
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setPermissionGrantStateDefault_wasPreviouslyGranted_permissionStaysGranted() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to default but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION))
                    .isEqualTo(PERMISSION_GRANT_STATE_DEFAULT);
            assertWithMessage("Permission should be granted but was not").that(
                    sTestApp.pkg().hasPermission(GRANTABLE_PERMISSION)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setPermissionGrantStateDefault_wasPreviouslyDenied_permissionStaysDenied() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DENIED);
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to default but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION))
                    .isEqualTo(PERMISSION_GRANT_STATE_DEFAULT);
            assertWithMessage("Permission should be denied but was not").that(
                    sTestApp.pkg().hasPermission(GRANTABLE_PERMISSION)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }
}
