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

import static com.android.bedstead.harrier.UserType.PRIMARY_USER;
import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_PROFILES;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import static com.google.common.truth.Truth.assertThat;

import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.CrossUserTest;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnumTestParameter;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.PermissionTest;
import com.android.bedstead.harrier.annotations.StringTestParameter;
import com.android.bedstead.harrier.annotations.UserPair;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnDeviceOwnerUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerPrimaryUser;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@RunWith(BedsteadJUnit4.class)
public class BedsteadJUnit4Test {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @StringTestParameter({"A", "B"})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface TwoValuesStringTestParameter {

    }

    private enum EnumWithThreeValues {
        ONE, TWO, THREE
    }

    private static int sSimpleParameterizedCalls = 0;
    private static int sMultipleSimpleParameterizedCalls = 0;
    private static int sBedsteadParameterizedCalls = 0;
    private static int sBedsteadPlusSimpleParameterizedCalls = 0;
    private static int sIndirectParameterizedCalls = 0;
    private static int sIntParameterizedCalls = 0;
    private static int sEnumParameterizedCalls = 0;

    @AfterClass
    public static void afterClass() {
        assertThat(sSimpleParameterizedCalls).isEqualTo(2);
        assertThat(sMultipleSimpleParameterizedCalls).isEqualTo(4);
        assertThat(sBedsteadParameterizedCalls).isEqualTo(2);
        assertThat(sBedsteadPlusSimpleParameterizedCalls).isEqualTo(4);
        assertThat(sIndirectParameterizedCalls).isEqualTo(2);
        assertThat(sIntParameterizedCalls).isEqualTo(2);
        assertThat(sEnumParameterizedCalls).isEqualTo(3);
    }

    @Test
    @IncludeRunOnDeviceOwnerUser
    @IncludeRunOnProfileOwnerPrimaryUser
    public void bedsteadParameterized() {
        sBedsteadParameterizedCalls += 1;
    }

    @Test
    @IncludeRunOnDeviceOwnerUser
    @IncludeRunOnProfileOwnerPrimaryUser
    public void bedsteadPlusSimpleParameterized(@StringTestParameter({"A", "B"}) String argument) {
        sBedsteadPlusSimpleParameterizedCalls += 1;
    }

    @Test
    public void simpleParameterized(@StringTestParameter({"A", "B"}) String argument) {
        sSimpleParameterizedCalls += 1;
    }

    @Test
    public void multipleSimpleParameterized(
            @StringTestParameter({"A", "B"}) String argument1,
            @StringTestParameter({"C", "D"}) String argument2) {
        sMultipleSimpleParameterizedCalls += 1;
    }

    @Test
    public void indirectParameterized(@TwoValuesStringTestParameter String argument) {
        sIndirectParameterizedCalls += 1;
    }

    @Test
    public void intParameterized(@IntTestParameter({1, 2}) int argument) {
        sIntParameterizedCalls += 1;
    }

    @Test
    public void enumParameterized(
            @EnumTestParameter(EnumWithThreeValues.class) EnumWithThreeValues argument) {
        sEnumParameterizedCalls += 1;
    }

    @PermissionTest({INTERACT_ACROSS_PROFILES, INTERACT_ACROSS_USERS})
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    public void permissionTestAnnotation_generatesRunsWithOnePermissionOrOther() {
        assertThat(TestApis.permissions().hasPermission(INTERACT_ACROSS_USERS_FULL)).isTrue();
        if (TestApis.permissions().hasPermission(INTERACT_ACROSS_PROFILES)) {
            assertThat(TestApis.permissions().hasPermission(INTERACT_ACROSS_USERS)).isFalse();
        } else {
            assertThat(TestApis.permissions().hasPermission(INTERACT_ACROSS_USERS)).isTrue();
        }
    }

    @UserTest({UserType.PRIMARY_USER, UserType.WORK_PROFILE})
    @Test
    public void userTestAnnotation_isRunningOnCorrectUsers() {
        if (!TestApis.users().instrumented().equals(sDeviceState.primaryUser())) {
            assertThat(TestApis.users().instrumented()).isEqualTo(sDeviceState.workProfile());
        }
    }

    @CrossUserTest({
            @UserPair(from = PRIMARY_USER, to = WORK_PROFILE),
            @UserPair(from = WORK_PROFILE, to = PRIMARY_USER),
    })
    @Test
    public void crossUserTestAnnotation_isRunningWithCorrectUserPairs() {
        if (TestApis.users().instrumented().equals(sDeviceState.primaryUser())) {
            assertThat(sDeviceState.otherUser()).isEqualTo(sDeviceState.workProfile());
        } else {
            assertThat(TestApis.users().instrumented()).isEqualTo(sDeviceState.workProfile());
            assertThat(sDeviceState.otherUser()).isEqualTo(sDeviceState.primaryUser());
        }
    }
}
