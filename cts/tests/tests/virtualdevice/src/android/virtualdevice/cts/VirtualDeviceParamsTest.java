/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.virtualdevice.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.VirtualDeviceParams;
import android.content.ComponentName;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.util.TestAppHelper;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualDeviceParamsTest {

    @Test
    public void setAllowedAndBlockedCrossTaskNavigations_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setAllowedCrossTaskNavigations(Set.of(
                    TestAppHelper.MAIN_ACTIVITY_COMPONENT));
            paramsBuilder.setBlockedCrossTaskNavigations(Set.of());
        });

    }

    @Test
    public void setBlockedAndAllowedCrossTaskNavigations_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setBlockedCrossTaskNavigations(Set.of(
                    TestAppHelper.MAIN_ACTIVITY_COMPONENT));
            paramsBuilder.setAllowedCrossTaskNavigations(Set.of());
        });

    }

    @Test
    public void getAllowedCrossTaskNavigations_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAllowedCrossTaskNavigations(
                        Set.of(
                                new ComponentName("test", "test.Activity1"),
                                new ComponentName("test", "test.Activity2")))
                .build();

        assertThat(params.getAllowedCrossTaskNavigations()).containsExactly(
                new ComponentName("test", "test.Activity1"),
                new ComponentName("test", "test.Activity2"));
        assertThat(params.getDefaultNavigationPolicy())
                .isEqualTo(VirtualDeviceParams.NAVIGATION_POLICY_DEFAULT_BLOCKED);
    }

    @Test
    public void getBlockedCrossTaskNavigations_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setBlockedCrossTaskNavigations(
                        Set.of(
                                new ComponentName("test", "test.Activity1"),
                                new ComponentName("test", "test.Activity2")))
                .build();

        assertThat(params.getBlockedCrossTaskNavigations()).containsExactly(
                new ComponentName("test", "test.Activity1"),
                new ComponentName("test", "test.Activity2"));
        assertThat(params.getDefaultNavigationPolicy())
                .isEqualTo(VirtualDeviceParams.NAVIGATION_POLICY_DEFAULT_ALLOWED);
    }

    @Test
    public void setAllowedAndBlockedActivities_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setAllowedActivities(Set.of(TestAppHelper.MAIN_ACTIVITY_COMPONENT));
            paramsBuilder.setBlockedActivities(Set.of());
        });
    }

    @Test
    public void setBlockedAndAllowedActivities_shouldThrowException() {
        VirtualDeviceParams.Builder paramsBuilder = new VirtualDeviceParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> {
            paramsBuilder.setBlockedActivities(Set.of(TestAppHelper.MAIN_ACTIVITY_COMPONENT));
            paramsBuilder.setAllowedActivities(Set.of());
        });
    }

    @Test
    public void getAllowedActivities_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setAllowedActivities(
                        Set.of(
                                new ComponentName("test", "test.Activity1"),
                                new ComponentName("test", "test.Activity2")))
                .build();

        assertThat(params.getAllowedActivities()).containsExactly(
                new ComponentName("test", "test.Activity1"),
                new ComponentName("test", "test.Activity2"));
        assertThat(params.getDefaultActivityPolicy())
                .isEqualTo(VirtualDeviceParams.ACTIVITY_POLICY_DEFAULT_BLOCKED);
    }

    @Test
    public void getBlockedActivities_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setBlockedActivities(
                        Set.of(
                                new ComponentName("test", "test.Activity1"),
                                new ComponentName("test", "test.Activity2")))
                .build();

        assertThat(params.getBlockedActivities()).containsExactly(
                new ComponentName("test", "test.Activity1"),
                new ComponentName("test", "test.Activity2"));
        assertThat(params.getDefaultActivityPolicy())
                .isEqualTo(VirtualDeviceParams.ACTIVITY_POLICY_DEFAULT_ALLOWED);
    }

    @Test
    public void getLockState_shouldReturnConfiguredValue() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                .build();

        assertThat(params.getLockState()).isEqualTo(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED);
    }

    @Test
    public void getUsersWithMatchingAccounts_shouldReturnConfiguredSet() {
        VirtualDeviceParams params = new VirtualDeviceParams.Builder()
                .setUsersWithMatchingAccounts(Set.of(UserHandle.SYSTEM))
                .build();

        assertThat(params.getUsersWithMatchingAccounts()).containsExactly(UserHandle.SYSTEM);
    }
}

