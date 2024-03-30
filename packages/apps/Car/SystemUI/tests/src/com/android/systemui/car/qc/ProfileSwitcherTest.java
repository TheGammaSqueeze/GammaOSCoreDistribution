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

package com.android.systemui.car.qc;

import static android.os.UserManager.SWITCHABILITY_STATUS_OK;
import static android.os.UserManager.SWITCHABILITY_STATUS_USER_SWITCH_DISALLOWED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.car.user.CarUserManager;
import android.car.user.UserCreationResult;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AsyncFuture;
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import androidx.test.filters.SmallTest;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.systemui.R;
import com.android.systemui.SysuiTestCase;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
public class ProfileSwitcherTest extends SysuiTestCase {

    private ProfileSwitcher mProfileSwitcher;
    private List<UserInfo> mAliveUsers = new ArrayList<>();

    @Mock
    private UserManager mUserManager;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private CarUserManager mCarUserManager;

    @Before
    public void setUp() throws ExecutionException, InterruptedException, TimeoutException {
        MockitoAnnotations.initMocks(this);

        when(mUserManager.getAliveUsers()).thenReturn(mAliveUsers);
        when(mUserManager.getUserSwitchability(any())).thenReturn(SWITCHABILITY_STATUS_OK);
        when(mDevicePolicyManager.isDeviceManaged()).thenReturn(false);
        when(mDevicePolicyManager.isOrganizationOwnedDeviceWithManagedProfile()).thenReturn(false);

        AsyncFuture<UserSwitchResult> switchResultFuture = mock(AsyncFuture.class);
        UserSwitchResult switchResult = mock(UserSwitchResult.class);
        when(switchResult.isSuccess()).thenReturn(true);
        when(switchResultFuture.get(anyLong(), any())).thenReturn(switchResult);
        when(mCarUserManager.switchUser(anyInt())).thenReturn(switchResultFuture);

        mProfileSwitcher = new ProfileSwitcher(mContext, mUserManager, mDevicePolicyManager,
                mCarUserManager);
    }

    private void setUpLogout() {
        UserInfo user1 = generateUser(1000, "User1", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        UserInfo user2 = generateUser(1001, "User2", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        when(mDevicePolicyManager.isDeviceManaged()).thenReturn(true);
        when(mDevicePolicyManager.isLogoutEnabled()).thenReturn(true);
        when(mDevicePolicyManager.getLogoutUser()).thenReturn(mock(UserHandle.class));
    }

    @Test
    public void logoutAllowed_managedDevice_switchAllowed() {
        setUpLogout();
        List<QCRow> rows = getProfileRows();
        assertThat(rows).hasSize(6);
        assertThat(rows.get(0).getSubtitle()).isEqualTo(
                mContext.getString(R.string.do_disclosure_generic));
        assertThat(rows.get(1).getTitle()).isEqualTo("User1");
        assertThat(rows.get(2).getTitle()).isEqualTo("User2");
        assertThat(rows.get(3).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(4).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
        assertThat(rows.get(5).getTitle()).isEqualTo(mContext.getString(R.string.end_session));
    }

    @Test
    public void logoutAllowed_managedDevice_switchDisallowed() {
        setUpLogout();
        when(mUserManager.getUserSwitchability(any()))
                .thenReturn(SWITCHABILITY_STATUS_USER_SWITCH_DISALLOWED);
        when(mUserManager.getUserInfo(ActivityManager.getCurrentUser()))
                .thenReturn(mAliveUsers.get(0));

        List<QCRow> rows = getProfileRows();
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getSubtitle()).isEqualTo(
                mContext.getString(R.string.do_disclosure_generic));
        assertThat(rows.get(1).getTitle()).isEqualTo(mAliveUsers.get(0).name);
        assertThat(rows.get(2).getTitle()).isEqualTo(mContext.getString(R.string.end_session));
    }

    @Test
    public void switchNotAllowed_returnsOnlyCurrentUser() {
        when(mUserManager.getUserSwitchability(any()))
                .thenReturn(SWITCHABILITY_STATUS_USER_SWITCH_DISALLOWED);
        UserInfo currentUser = generateUser(ActivityManager.getCurrentUser(),
                "Current User", /* supportsSwitch= */ true, /* isGuest= */ false);
        mAliveUsers.add(currentUser);
        when(mUserManager.getUserInfo(ActivityManager.getCurrentUser())).thenReturn(currentUser);
        UserInfo otherUser = generateUser(1001, "Other User", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        mAliveUsers.add(otherUser);
        List<QCRow> rows = getProfileRows();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTitle()).isEqualTo("Current User");
    }

    @Test
    public void switchAllowed_usersSwitchable_returnsAllRows() {
        UserInfo user1 = generateUser(1000, "User1", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        UserInfo user2 = generateUser(1001, "User2", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect four rows - one for each user, one for the guest user, and one for add user
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).getTitle()).isEqualTo("User1");
        assertThat(rows.get(1).getTitle()).isEqualTo("User2");
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(3).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void switchAllowed_orderUsersByCreationTime() {
        UserInfo user1 = generateUser(1001, "User2", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        UserInfo user2 = generateUser(1000, "User1", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect four rows - one for each user, one for the guest user, and one for add user
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).getTitle()).isEqualTo("User2");
        assertThat(rows.get(1).getTitle()).isEqualTo("User1");
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(3).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void switchAllowed_userNotSwitchable_returnsValidRows() {
        UserInfo user1 = generateUser(1000, "User1", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        UserInfo user2 = generateUser(1001, "User2", /* supportsSwitch= */ false,
                /* isGuest= */ false);
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect three rows - one for the valid user, one for the guest user, and one for add user
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getTitle()).isEqualTo("User1");
        assertThat(rows.get(1).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void switchAllowed_userGuest_returnsValidRows() {
        UserInfo user1 = generateUser(1000, "User1", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        UserInfo user2 = generateUser(1001, "User2", /* supportsSwitch= */ true,
                /* isGuest= */ true);
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect three rows - one for the valid user, one for the guest user, and one for add user
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getTitle()).isEqualTo("User1");
        assertThat(rows.get(1).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void switchAllowed_addUserDisallowed_returnsValidRows() {
        when(mUserManager.hasUserRestrictionForUser(eq(UserManager.DISALLOW_ADD_USER),
                any())).thenReturn(true);
        UserInfo user1 = generateUser(1000, "User1", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        UserInfo user2 = generateUser(1001, "User2", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect three rows - one for each user and one for the guest user
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).getTitle()).isEqualTo("User1");
        assertThat(rows.get(1).getTitle()).isEqualTo("User2");
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
    }

    @Test
    public void switchAllowed_deviceManaged_returnsValidRows() {
        when(mDevicePolicyManager.isDeviceManaged()).thenReturn(true);
        UserInfo user1 = generateUser(1000, "User1", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        mAliveUsers.add(user1);
        List<QCRow> rows = getProfileRows();
        // Expect four rows - one for the device owner message, one for the user,
        // one for the guest user, and one for add user
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).getSubtitle()).isEqualTo(
                mContext.getString(R.string.do_disclosure_generic));
        assertThat(rows.get(1).getTitle()).isEqualTo("User1");
        assertThat(rows.get(2).getTitle()).isEqualTo(
                mContext.getString(com.android.internal.R.string.guest_name));
        assertThat(rows.get(3).getTitle()).isEqualTo(
                mContext.getString(R.string.car_add_user));
    }

    @Test
    public void onUserPressed_triggersSwitch() {
        int currentUserId = 1000;
        int otherUserId = 1001;
        UserInfo user1 = generateUser(currentUserId, "User1", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        UserInfo user2 = generateUser(otherUserId, "User2", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        mAliveUsers.add(user1);
        mAliveUsers.add(user2);
        List<QCRow> rows = getProfileRows();
        // Expect four rows - one for each user, one for the guest user, and one for add user
        assertThat(rows).hasSize(4);
        QCRow otherUserRow = rows.get(1);
        otherUserRow.getActionHandler().onAction(otherUserRow, mContext, new Intent());
        verify(mCarUserManager).switchUser(otherUserId);
    }

    @Test
    public void onGuestPressed_createsAndSwitches()
            throws ExecutionException, InterruptedException, TimeoutException {
        int currentUserId = 1000;
        int guestUserId = 1001;
        AsyncFuture<UserCreationResult> createResultFuture = mock(AsyncFuture.class);
        when(createResultFuture.get(anyLong(), any())).thenReturn(null);
        when(mCarUserManager.createGuest(any())).thenReturn(createResultFuture);

        UserInfo guestUserInfo = mock(UserInfo.class);
        guestUserInfo.id = guestUserId;
        when(mUserManager.findCurrentGuestUser()).thenReturn(guestUserInfo);

        UserInfo user1 = generateUser(currentUserId, "User1", /* supportsSwitch= */ true,
                /* isGuest= */ false);
        mAliveUsers.add(user1);
        List<QCRow> rows = getProfileRows();
        // Expect 3 rows - one for the user, one for the guest user, and one for add user
        assertThat(rows).hasSize(3);
        QCRow guestRow = rows.get(1);
        guestRow.getActionHandler().onAction(guestRow, mContext, new Intent());
        verify(mCarUserManager).createGuest(any());
        verify(mCarUserManager).switchUser(guestUserId);
    }

    private List<QCRow> getProfileRows() {
        QCItem item = mProfileSwitcher.getQCItem();
        assertThat(item).isNotNull();
        assertThat(item instanceof QCList).isTrue();
        return ((QCList) item).getRows();
    }

    private UserInfo generateUser(int id, String name, boolean supportsSwitch, boolean isGuest) {
        UserInfo info = mock(UserInfo.class);
        info.id = id;
        info.name = name;
        info.creationTime = System.currentTimeMillis();
        when(info.supportsSwitchToByUser()).thenReturn(supportsSwitch);
        when(info.isGuest()).thenReturn(isGuest);
        return info;
    }
}
