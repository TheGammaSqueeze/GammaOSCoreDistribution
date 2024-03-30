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

import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.RequireSdkVersion;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class SecureSettingsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final String KEY = "key";
    private static final String INVALID_KEY = "noKey";
    private static final int INT_VALUE = 123;
    private static final String STRING_VALUE = "String";

    @After
    public void resetSecureSettings() {
        TestApis.settings().secure().reset();
    }

    @Test
    public void putInt_putsIntIntoSecureSettingsOnInstrumentedUser() throws Exception {
        TestApis.settings().secure().putInt(KEY, INT_VALUE);

        assertThat(android.provider.Settings.Secure.getInt(sContext.getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void putIntWithContentResolver_putsIntIntoSecureSettings() throws Exception {
        TestApis.settings().secure().putInt(sContext.getContentResolver(), KEY, INT_VALUE);

        assertThat(android.provider.Settings.Secure.getInt(sContext.getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void putIntWithContentResolver_preS_throwsException() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
                TestApis.settings().secure().putInt(
                        sContext.getContentResolver(), KEY, INT_VALUE));
    }

    @Test
    public void putIntWithUser_instrumentedUser_putsIntIntoSecureSettings() throws Exception {
        TestApis.settings().secure().putInt(TestApis.users().instrumented(), KEY, INT_VALUE);

        assertThat(android.provider.Settings.Secure.getInt(sContext.getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void putIntWithUser_differentUser_putsIntIntoSecureSettings() throws Exception {
        TestApis.settings().secure().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            assertThat(android.provider.Settings.Secure.getInt(
                    TestApis.context().androidContextAsUser(sDeviceState.secondaryUser())
                            .getContentResolver(), KEY)).isEqualTo(INT_VALUE);
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void putIntWithUser_differentUser_preS_throwsException() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
                TestApis.settings().secure().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE));
    }

    @Test
    public void getInt_getsIntFromSecureSettingsOnInstrumentedUser() {
        TestApis.settings().secure().putInt(KEY, INT_VALUE);

        assertThat(TestApis.settings().secure().getInt(KEY)).isEqualTo(INT_VALUE);
    }

    @Test
    public void getInt_invalidKey_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.settings().secure().getInt(INVALID_KEY));
    }

    @Test
    public void getInt_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().secure().getInt(INVALID_KEY, INT_VALUE)).isEqualTo(
                INT_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_getsIntFromSecureSettings() {
        TestApis.settings().secure().putInt(
                TestApis.context().instrumentedContext().getContentResolver(), KEY, INT_VALUE);

        assertThat(TestApis.settings().secure().getInt(
                TestApis.context().instrumentedContext().getContentResolver(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void getIntWithContentResolver_preS_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> TestApis.settings().secure().getInt(
                TestApis.context().instrumentedContext().getContentResolver(), KEY));
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_invalidKey_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.settings().secure().getInt(
                        TestApis.context().instrumentedContext().getContentResolver(),
                        INVALID_KEY));
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithContentResolver_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().secure().getInt(
                TestApis.context().instrumentedContext().getContentResolver(),
                INVALID_KEY, INT_VALUE)).isEqualTo(INT_VALUE);
    }

    @Test
    public void getIntWithUser_instrumentedUser_getsIntFromSecureSettings() {
        TestApis.settings().secure().putInt(KEY, INT_VALUE);

        assertThat(TestApis.settings().secure().getInt(TestApis.users().instrumented(), KEY))
                .isEqualTo(INT_VALUE);
    }

    @Test
    public void getIntWithUser_invalidKey_throwsException() {
        assertThrows(NeneException.class,
                () -> TestApis.settings().secure().getInt(
                        TestApis.users().instrumented(), INVALID_KEY));
    }

    @Test
    public void getIntWithUser_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().secure().getInt(
                TestApis.users().instrumented(), INVALID_KEY, INT_VALUE)).isEqualTo(INT_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getIntWithUser_differentUser_getsIntFromSecureSettings() {
        TestApis.settings().secure().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        assertThat(TestApis.settings().secure().getInt(
                sDeviceState.secondaryUser(), KEY)).isEqualTo(INT_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void getIntWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            TestApis.settings().secure().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        });
    }

    @Test
    public void putString_putsStringIntoSecureSettingsOnInstrumentedUser() throws Exception {
        TestApis.settings().secure().putString(KEY, STRING_VALUE);

        assertThat(android.provider.Settings.Secure.getString(sContext.getContentResolver(), KEY))
                .isEqualTo(STRING_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void putStringWithContentResolver_putsStringIntoSecureSettings() throws Exception {
        TestApis.settings().secure().putString(sContext.getContentResolver(), KEY, STRING_VALUE);

        assertThat(android.provider.Settings.Secure.getString(sContext.getContentResolver(), KEY))
                .isEqualTo(STRING_VALUE);
    }

    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void putStringWithContentResolver_preS_throwsException() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
                TestApis.settings().secure().putString(
                        sContext.getContentResolver(), KEY, STRING_VALUE));
    }

    @Test
    public void putStringWithUser_instrumentedUser_putsStringIntoSecureSettings() throws Exception {
        TestApis.settings().secure().putString(TestApis.users().instrumented(), KEY, STRING_VALUE);

        assertThat(android.provider.Settings.Secure.getString(sContext.getContentResolver(), KEY))
                .isEqualTo(STRING_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void putStringWithUser_differentUser_putsStringIntoSecureSettings() throws Exception {
        TestApis.settings().secure().putString(sDeviceState.secondaryUser(), KEY, STRING_VALUE);

        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            assertThat(android.provider.Settings.Secure.getString(
                    TestApis.context().androidContextAsUser(sDeviceState.secondaryUser())
                            .getContentResolver(), KEY)).isEqualTo(STRING_VALUE);
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void putStringWithUser_differentUser_preS_throwsException() throws Exception {
        assertThrows(UnsupportedOperationException.class, () ->
                TestApis.settings().secure()
                        .putString(sDeviceState.secondaryUser(), KEY, STRING_VALUE));
    }

    @Test
    public void getString_getsStringFromSecureSettingsOnInstrumentedUser() {
        TestApis.settings().secure().putString(KEY, STRING_VALUE);

        assertThat(TestApis.settings().secure().getString(KEY)).isEqualTo(STRING_VALUE);
    }

    @Test
    public void getString_invalidKey_returnsNull() {
        assertThat(TestApis.settings().secure().getString(INVALID_KEY)).isNull();
    }

    @Test
    public void getString_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().secure().getString(INVALID_KEY, STRING_VALUE)).isEqualTo(
                STRING_VALUE);
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getStringWithContentResolver_getsStringFromSecureSettings() {
        TestApis.settings().secure().putString(
                TestApis.context().instrumentedContext().getContentResolver(), KEY, STRING_VALUE);

        assertThat(TestApis.settings().secure().getString(
                TestApis.context().instrumentedContext().getContentResolver(), KEY))
                .isEqualTo(STRING_VALUE);
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void getStringWithContentResolver_preS_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> TestApis.settings().secure()
                .getString(
                        TestApis.context().instrumentedContext().getContentResolver(), KEY));
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getStringWithContentResolver_invalidKey_returnsNull() {
        assertThat(TestApis.settings().secure().getString(
                        TestApis.context().instrumentedContext().getContentResolver(),
                        INVALID_KEY)).isNull();
    }

    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getStringWithContentResolver_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().secure().getString(
                TestApis.context().instrumentedContext().getContentResolver(),
                INVALID_KEY, STRING_VALUE)).isEqualTo(STRING_VALUE);
    }

    @Test
    public void getStringWithUser_instrumentedUser_getsStringFromSecureSettings() {
        TestApis.settings().secure().putString(KEY, STRING_VALUE);

        assertThat(TestApis.settings().secure().getString(TestApis.users().instrumented(), KEY))
                .isEqualTo(STRING_VALUE);
    }

    @Test
    public void getStringWithUser_invalidKey_returnsNull() {
        assertThat(TestApis.settings().secure().getString(
                        TestApis.users().instrumented(), INVALID_KEY)).isNull();
    }

    @Test
    public void getStringWithUser_invalidKey_withDefault_returnsDefault() {
        assertThat(TestApis.settings().secure().getString(
                TestApis.users().instrumented(), INVALID_KEY, STRING_VALUE))
                .isEqualTo(STRING_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void getStringWithUser_differentUser_getsStringFromSecureSettings() {
        TestApis.settings().secure().putString(sDeviceState.secondaryUser(), KEY, STRING_VALUE);

        assertThat(TestApis.settings().secure().getString(
                sDeviceState.secondaryUser(), KEY)).isEqualTo(STRING_VALUE);
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void getStringWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            TestApis.settings().secure().putString(sDeviceState.secondaryUser(), KEY, STRING_VALUE);
        });
    }

    // TODO(b/201319369): this requires a system user but should not
    @RequireRunOnSystemUser
    @Test
    public void reset_resetsSecureSettings() {
        TestApis.settings().secure().putInt(KEY, INT_VALUE);

        TestApis.settings().secure().reset();

        assertThrows(NeneException.class, () -> TestApis.settings().secure().getInt(KEY));
    }

    // TODO(b/201319369): this requires a system user but should not
    @RequireRunOnSystemUser
    @Test
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    public void resetWithContentResolver_resetsSecureSettings() {
        ContentResolver contentResolver =
                TestApis.context().instrumentedContext().getContentResolver();
        TestApis.settings().secure().putInt(contentResolver, KEY, INT_VALUE);

        TestApis.settings().secure().reset(contentResolver);

        assertThrows(NeneException.class,
                () -> TestApis.settings().secure().getInt(
                        contentResolver,
                        KEY));
    }

    @Test
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void resetWithContentResolver_preS_throwsException() {
        ContentResolver contentResolver =
                TestApis.context().instrumentedContext().getContentResolver();

        assertThrows(UnsupportedOperationException.class,
                () -> TestApis.settings().secure().reset(contentResolver));
    }

    // TODO(b/201319369): this requires a system user but should not
    @RequireRunOnSystemUser
    @Test
    public void resetWithUser_instrumentedUser_resetsSecureSettings() {
        TestApis.settings().secure().putInt(TestApis.users().instrumented(), KEY, INT_VALUE);

        TestApis.settings().secure().reset(TestApis.users().instrumented());

        assertThrows(NeneException.class,
                () -> TestApis.settings().secure().getInt(
                        TestApis.users().instrumented(),
                        KEY));
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(min = Build.VERSION_CODES.S)
    @Ignore("b/194669450")
    public void resetWithUser_differentUser_resetsSecureSettings() {
        TestApis.settings().secure().putInt(sDeviceState.secondaryUser(), KEY, INT_VALUE);

        TestApis.settings().secure().reset(sDeviceState.secondaryUser());

        assertThrows(NeneException.class,
                () -> TestApis.settings().secure().getInt(
                        sDeviceState.secondaryUser(),
                        KEY));
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireSdkVersion(max = Build.VERSION_CODES.R)
    public void resetWithUser_differentUser_preS_throwsException() {
        assertThrows(UnsupportedOperationException.class,
                () -> TestApis.settings().secure().reset(sDeviceState.secondaryUser()));
    }
}
