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

package com.android.bedstead.nene.settings;

import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.os.Build;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class SystemSettingsTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final String KEY = "screen_brightness"; // must be a public system setting
    private static final String INVALID_KEY = "noKey";
    private static final int INT_VALUE = 123;
    private static final String STRING_VALUE = "testValue";

    @Test
    public void putInt_putsIntIntoSystemSettingsOnInstrumentedUser() throws Exception {
        TestApis.settings().system().putInt(KEY, INT_VALUE);

        assertThat(android.provider.Settings.System.getInt(sContext.getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void putIntWithContentResolver_putsIntIntoSystemSettings() throws Exception {
        TestApis.settings().system().putInt(sContext.getContentResolver(), KEY, INT_VALUE);

        assertThat(android.provider.Settings.System.getInt(sContext.getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void putIntWithContentResolver_preS_throwsException() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
                TestApis.settings().system().putInt(
                        sContext.getContentResolver(), KEY, INT_VALUE));
    }

    @Test
    public void putIntWithUser_instrumentedUser_putsIntIntoSystemSettings() throws Exception {
        TestApis.settings().system().putInt(TestApis.users().instrumented(), KEY, INT_VALUE);

        assertThat(android.provider.Settings.System.getInt(sContext.getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void putIntWithUser_differentUser_putsIntIntoSystemSettings() throws Exception {
        TestApis.settings().system().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            assertThat(android.provider.Settings.System.getInt(
                    TestApis.context().androidContextAsUser(sDeviceState.secondaryUser())
                            .getContentResolver(), KEY)).isEqualTo(INT_VALUE);
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void putIntWithUser_differentUser_preS_throwsException() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
                TestApis.settings().system().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE));
    }

    @Test
    public void getInt_getsIntFromSystemSettingsOnInstrumentedUser() {
        TestApis.settings().system().putInt(KEY, INT_VALUE);

        assertThat(TestApis.settings().system().getInt(KEY)).isEqualTo(INT_VALUE);
    }

    @Test
    public void getInt_invalidKey_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.settings().system().getInt(INVALID_KEY));
    }

    @Test
    public void getInt_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().system().getInt(INVALID_KEY, INT_VALUE)).isEqualTo(
                INT_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_getsIntFromSystemSettings() {
        TestApis.settings().system().putInt(
                TestApis.context().instrumentedContext().getContentResolver(), KEY, INT_VALUE);

        assertThat(TestApis.settings().system().getInt(
                TestApis.context().instrumentedContext().getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_invalidKey_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.settings().system().getInt(
                        TestApis.context().instrumentedContext().getContentResolver(),
                        INVALID_KEY));
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().system().getInt(
                TestApis.context().instrumentedContext().getContentResolver(),
                INVALID_KEY, INT_VALUE)).isEqualTo(INT_VALUE);
    }

    @Test
    public void getIntWithUser_instrumentedUser_getsIntFromSystemSettings() {
        TestApis.settings().system().putInt(KEY, INT_VALUE);

        assertThat(TestApis.settings().system().getInt(TestApis.users().instrumented(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    public void getIntWithUser_invalidKey_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.settings().system().getInt(
                        TestApis.users().instrumented(), INVALID_KEY));
    }

    @Test
    public void getIntWithUser_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().system().getInt(
                TestApis.users().instrumented(), INVALID_KEY, INT_VALUE)).isEqualTo(INT_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithUser_differentUser_getsIntFromSystemSettings() {
        TestApis.settings().system().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        assertThat(TestApis.settings().system().getInt(
                sDeviceState.secondaryUser(), KEY)).isEqualTo(INT_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void getIntWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            TestApis.settings().system().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        });
    }

    @Test
    public void putString_putsStringIntoSystemSettingsOnInstrumentedUser() throws Exception {
        TestApis.settings().system().putString(KEY, STRING_VALUE);

        assertThat(android.provider.Settings.System.getString(sContext.getContentResolver(), KEY))
                .isEqualTo(STRING_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void putStringWithContentResolver_putsStringIntoSystemSettings() throws Exception {
        TestApis.settings().system().putString(sContext.getContentResolver(), KEY, STRING_VALUE);

        assertThat(android.provider.Settings.System.getString(sContext.getContentResolver(), KEY))
                .isEqualTo(STRING_VALUE);
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void putStringWithContentResolver_preS_throwsException() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
                TestApis.settings().system().putString(
                        sContext.getContentResolver(), KEY, STRING_VALUE));
    }

    @Test
    public void putStringWithUser_instrumentedUser_putsStringIntoSystemSettings() throws Exception {
        TestApis.settings().system().putString(
                TestApis.users().instrumented(), KEY, STRING_VALUE);

        assertThat(android.provider.Settings.System.getString(sContext.getContentResolver(), KEY))
                .isEqualTo(STRING_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void putStringWithUser_differentUser_putsStringIntoSystemSettings() throws Exception {
        TestApis.settings().system().putString(sDeviceState.secondaryUser(), KEY, STRING_VALUE);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            assertThat(android.provider.Settings.System.getString(
                    TestApis.context().androidContextAsUser(sDeviceState.secondaryUser())
                            .getContentResolver(), KEY)).isEqualTo(STRING_VALUE);
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void putStringWithUser_differentUser_preS_throwsException() throws Exception {
        assertThrows(UnsupportedOperationException.class,
                () -> TestApis.settings().system().putString(sDeviceState.secondaryUser(), KEY,
                        STRING_VALUE));
    }

    @Test
    public void getString_getsStringFromSystemSettingsOnInstrumentedUser() {
        TestApis.settings().system().putString(KEY, STRING_VALUE);

        assertThat(TestApis.settings().system().getString(KEY)).isEqualTo(STRING_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_getsStringFromSystemSettings() {
        TestApis.settings().system().putString(
                TestApis.context().instrumentedContext().getContentResolver(), KEY, STRING_VALUE);

        assertThat(TestApis.settings().system().getString(
                TestApis.context().instrumentedContext().getContentResolver(), KEY))
                .isEqualTo(STRING_VALUE);
    }

    @Test
    public void getStringWithUser_instrumentedUser_getsStringFromSystemSettings() {
        TestApis.settings().system().putString(KEY, STRING_VALUE);

        assertThat(TestApis.settings().system().getString(TestApis.users().instrumented(), KEY))
                .isEqualTo(STRING_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getStringWithUser_differentUser_getsStringFromSystemSettings() {
        TestApis.settings().system().putString(sDeviceState.secondaryUser(), KEY, STRING_VALUE);

        assertThat(TestApis.settings().system().getString(
                sDeviceState.secondaryUser(), KEY)).isEqualTo(STRING_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void getStringWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            TestApis.settings().system().putString(sDeviceState.secondaryUser(), KEY,
                    STRING_VALUE);
        });
    }
}
