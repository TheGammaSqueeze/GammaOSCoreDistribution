/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.user;

import static android.car.test.mocks.AndroidMockitoHelper.mockAmStartUserInBackground;
import static android.car.test.mocks.AndroidMockitoHelper.mockAmSwitchUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockDpmLogoutUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockStopUserWithDelayedLocking;
import static android.car.test.mocks.AndroidMockitoHelper.mockStopUserWithDelayedLockingThrows;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmCreateGuest;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmCreateUser;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmGetUserSwitchability;
import static android.car.test.mocks.AndroidMockitoHelper.mockUmHasUserRestrictionForUser;
import static android.car.test.mocks.JavaMockitoHelper.getResult;

import static com.android.car.user.MockedUserHandleBuilder.expectEphemeralUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectGuestUserExists;
import static com.android.car.user.MockedUserHandleBuilder.expectRegularUserExists;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.expectThrows;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.car.CarVersion;
import android.car.ICarResultReceiver;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.settings.CarSettings;
import android.car.test.mocks.AbstractExtendedMockitoTestCase.ExpectWtf;
import android.car.test.mocks.BlockingAnswer;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserCreationResult;
import android.car.user.UserIdentificationAssociationResponse;
import android.car.user.UserLifecycleEventFilter;
import android.car.user.UserRemovalResult;
import android.car.user.UserStartResult;
import android.car.user.UserStopResult;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AndroidFuture;
import android.content.Context;
import android.hardware.automotive.vehicle.CreateUserRequest;
import android.hardware.automotive.vehicle.CreateUserStatus;
import android.hardware.automotive.vehicle.InitialUserInfoRequestType;
import android.hardware.automotive.vehicle.InitialUserInfoResponseAction;
import android.hardware.automotive.vehicle.SwitchUserResponse;
import android.hardware.automotive.vehicle.SwitchUserStatus;
import android.os.Binder;
import android.os.NewUserResponse;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import com.android.car.hal.HalCallback;
import com.android.car.internal.util.DebugUtils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for the {@link CarUserService}.
 */
public final class CarUserServiceTest extends BaseCarUserServiceTestCase {

    private static final String TAG = CarUserServiceTest.class.getSimpleName();

    private static final int PRE_CREATION_STAGE_BEFORE_SUSPEND = 1;
    private static final int PRE_CREATION_STAGE_ON_SYSTEM_START = 2;

    @Mock
    private Binder mMockBinder;
    @Mock
    private Binder mAnotherMockBinder;
    @Mock
    private ICarResultReceiver mLifecycleEventReceiver;
    @Mock
    private ICarResultReceiver mAnotherLifecycleEventReceiver;

    public CarUserServiceTest() {
        super(CarUserService.TAG);
    }

    @Before
    public void setUp() {
        when(mLifecycleEventReceiver.asBinder()).thenReturn(mMockBinder);
        when(mAnotherLifecycleEventReceiver.asBinder()).thenReturn(mAnotherMockBinder);
    }

    @Test
    public void testInitAndRelease() {
        // init()
        ICarUxRestrictionsChangeListener listener = initService();
        assertThat(listener).isNotNull();

        // release()
        mCarUserService.release();
        verify(mCarUxRestrictionService).unregisterUxRestrictionsChangeListener(listener);
    }

    @Test
    public void testSetInitialUser() throws Exception {
        UserHandle user = UserHandle.of(101);

        mCarUserService.setInitialUser(user);

        assertThat(mCarUserService.getInitialUser()).isEqualTo(user);
    }

    @Test
    @ExpectWtf
    public void testSetInitialUser_nullUser() throws Exception {
        mCarUserService.setInitialUser(null);

        mockInteractAcrossUsersPermission(true);
        assertThat(mCarUserService.getInitialUser()).isNull();
    }

    @Test
    public void testSendInitialUserToSystemServer() throws Exception {
        UserHandle user = UserHandle.of(101);
        mCarUserService.setCarServiceHelper(mICarServiceHelper);

        mCarUserService.setInitialUser(user);

        verify(mICarServiceHelper).sendInitialUser(user);
    }

    @Test
    public void testsetInitialUserFromSystemServer() throws Exception {
        UserHandle user = UserHandle.of(101);

        mCarUserService.setInitialUserFromSystemServer(user);

        assertThat(mCarUserService.getInitialUser()).isEqualTo(user);
    }

    @Test
    public void testsetInitialUserFromSystemServer_nullUser() throws Exception {
        mCarUserService.setInitialUserFromSystemServer(null);

        assertThat(mCarUserService.getInitialUser()).isNull();
    }

    @Test
    public void testSetICarServiceHelper_withUxRestrictions() throws Exception {
        mockGetUxRestrictions(/* restricted= */ true);
        ICarUxRestrictionsChangeListener listener = initService();

        mCarUserService.setCarServiceHelper(mICarServiceHelper);
        verify(mICarServiceHelper).setSafetyMode(false);

        updateUxRestrictions(listener, /* restricted= */ false);
        verify(mICarServiceHelper).setSafetyMode(true);
    }

    @Test
    public void testSetICarServiceHelper_withoutUxRestrictions() throws Exception {
        mockGetUxRestrictions(/* restricted= */ false);
        ICarUxRestrictionsChangeListener listener = initService();

        mCarUserService.setCarServiceHelper(mICarServiceHelper);
        verify(mICarServiceHelper).setSafetyMode(true);

        updateUxRestrictions(listener, /* restricted= */ true);
        verify(mICarServiceHelper).setSafetyMode(false);
    }

    @Test
    public void testAddUserLifecycleListener_checkNullParameter() {
        UserLifecycleEventFilter filter = new UserLifecycleEventFilter.Builder()
                .addEventType(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING).build();

        assertThrows(NullPointerException.class,
                () -> mCarUserService.addUserLifecycleListener(filter, /* listener= */null));
    }

    @Test
    public void testRemoveUser_binderMethod() {
        CarUserService spy = spy(mCarUserService);

        spy.removeUser(42, mUserRemovalFuture);

        verify(spy).removeUser(42, NO_CALLER_RESTRICTIONS, mUserRemovalFuture);
    }

    @Test
    public void testRemoveUserLifecycleListener_checkNullParameter() {
        assertThrows(NullPointerException.class,
                () -> mCarUserService.removeUserLifecycleListener(null));
    }

    @Test
    public void testRemoveUserLifecycleListener() throws Exception {
        // Arrange: add 2 listeners.
        UserLifecycleListener mockListener1 = mock(UserLifecycleListener.class);
        UserLifecycleListener mockListener2 = mock(UserLifecycleListener.class);
        mCarUserService.addUserLifecycleListener(/* filter= */null, mockListener1);
        mCarUserService.addUserLifecycleListener(/* filter= */null, mockListener2);

        // Act: remove the first listener, and an event occurs.
        mCarUserService.removeUserLifecycleListener(mockListener1);
        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);
        waitForHandlerThreadToFinish();

        // Verify: the second listener has been invoked but the removed listener has not.
        verify(mockListener1, never()).onEvent(any(UserLifecycleEvent.class));
        verify(mockListener2).onEvent(any(UserLifecycleEvent.class));
    }

    @Test
    public void testOnUserLifecycleEvent_legacyUserSwitch_halCalled() throws Exception {
        // Arrange
        mockExistingUsers(mExistingUsers);

        // Act
        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);

        // Verify
        verify(mUserHal).legacyUserSwitch(any());
    }

    @Test
    public void testOnUserLifecycleEvent_legacyUserSwitch_halnotSupported() throws Exception {
        // Arrange
        mockExistingUsers(mExistingUsers);
        mockUserHalSupported(false);

        // Act
        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);

        // Verify
        verify(mUserHal, never()).legacyUserSwitch(any());
    }

    @Test
    public void testOnUserLifecycleEvent_ensureAllListenersAreNotified() throws Exception {
        // Arrange: add two listeners, one to fail on onEvent
        // Adding the failure listener first.
        UserLifecycleListener failureListener = mock(UserLifecycleListener.class);
        doThrow(new RuntimeException("Failed onEvent invocation")).when(
                failureListener).onEvent(any(UserLifecycleEvent.class));
        mCarUserService.addUserLifecycleListener(/*filter= */null, failureListener);
        mockExistingUsers(mExistingUsers);

        // Adding the non-failure listener later.
        mCarUserService.addUserLifecycleListener(/* filter= */null, mUserLifecycleListener);

        // Act
        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);

        // Verify
        verifyListenerOnEventInvoked(mRegularUserId,
                CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING);
    }

    @Test
    public void testOnUserLifecycleEvent_notifyListener_nullFilter() throws Exception {
        // Arrange: add 2 listeners, both with null filter which will pass all events.
        UserLifecycleListener mockListener1 = mock(UserLifecycleListener.class);
        UserLifecycleListener mockListener2 = mock(UserLifecycleListener.class);
        mCarUserService.addUserLifecycleListener(/* filter= */null, mockListener1);
        mCarUserService.addUserLifecycleListener(/* filter= */null, mockListener2);

        // Act: an event occurs.
        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);
        waitForHandlerThreadToFinish();

        // Verify: both listeners have been invoked.
        verify(mockListener1).onEvent(any(UserLifecycleEvent.class));
        verify(mockListener2).onEvent(any(UserLifecycleEvent.class));
    }

    @Test
    public void testOnUserLifecycleEvent_notifyListener_nonNullFilter() throws Exception {
        // Arrange: add 3 listeners with different filters
        UserLifecycleListener mockListener1 = mock(UserLifecycleListener.class);
        mCarUserService.addUserLifecycleListener(
                new UserLifecycleEventFilter.Builder()
                        .addEventType(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING).build(),
                mockListener1);
        UserLifecycleListener mockListener2 = mock(UserLifecycleListener.class);
        mCarUserService.addUserLifecycleListener(
                new UserLifecycleEventFilter.Builder()
                        .addUser(UserHandle.of(mAdminUserId)).build(), mockListener2);
        UserLifecycleListener mockListener3 = mock(UserLifecycleListener.class);
        mCarUserService.addUserLifecycleListener(new UserLifecycleEventFilter.Builder()
                .addUser(UserHandle.of(mRegularUserId)).build(), mockListener3);

        // Act: 2 events occurs. User switching and then user unlocked.
        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);
        sendUserUnlockedEvent(mRegularUserId);
        waitForHandlerThreadToFinish();

        // Verify: listeners are called different number of times based on their filter conditions.
        verify(mockListener1, never()).onEvent(any(UserLifecycleEvent.class));
        verify(mockListener2, times(1)).onEvent(any(UserLifecycleEvent.class));
        verify(mockListener3, times(2)).onEvent(any(UserLifecycleEvent.class));
    }

    @Test
    public void testResetLifecycleListenerForApp() throws Exception {
        // Arrange: add 2 receivers.
        mCarUserService.setLifecycleListenerForApp("package1", /* filter= */null,
                mLifecycleEventReceiver);
        mCarUserService.setLifecycleListenerForApp("package2", /* filter= */null,
                mAnotherLifecycleEventReceiver);

        // Act: remove the first receiver, and an event occurs.
        mCarUserService.resetLifecycleListenerForApp(mLifecycleEventReceiver);
        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);
        waitForHandlerThreadToFinish();

        // Verify: the second receiver has been invoked but the removed receiver has not.
        verify(mLifecycleEventReceiver, never()).send(anyInt(), any());
        verify(mAnotherLifecycleEventReceiver).send(eq(mRegularUserId), any());
    }

    @Test
    public void testOnUserLifecycleEvent_notifyReceiver_nullFilter() throws Exception {
        // Arrange: add 2 receivers, both with null filter which will pass all events.
        mCarUserService.setLifecycleListenerForApp("package1", /* filter= */null,
                mLifecycleEventReceiver);
        mCarUserService.setLifecycleListenerForApp("package2", /* filter= */null,
                mAnotherLifecycleEventReceiver);

        // Act: an event occurs.
        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);
        waitForHandlerThreadToFinish();

        // Verify: both receivers invoked.
        verify(mLifecycleEventReceiver).send(eq(mRegularUserId), any());
        verify(mAnotherLifecycleEventReceiver).send(eq(mRegularUserId), any());
    }

    @Test
    public void testOnUserLifecycleEvent_notifyReceiver_nonNullFilter() throws Exception {
        // Arrange: add 2 receivers with different filters.
        mCarUserService.setLifecycleListenerForApp("package1",
                new UserLifecycleEventFilter.Builder()
                        .addEventType(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING).build(),
                mLifecycleEventReceiver);
        mCarUserService.setLifecycleListenerForApp("package2",
                new UserLifecycleEventFilter.Builder()
                        .addUser(UserHandle.of(mRegularUserId)).build(),
                mAnotherLifecycleEventReceiver);

        // Act: 2 events occurs. User switching and then user unlocked.
        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);
        sendUserUnlockedEvent(mRegularUserId);
        waitForHandlerThreadToFinish();

        // Verify: receivers are called or not depending on what their filter evaluates to.
        verify(mLifecycleEventReceiver, never()).send(anyInt(), any());
        verify(mAnotherLifecycleEventReceiver, times(2)).send(anyInt(), any());
    }

    @Test
    public void testOnUserLifecycleEvent_notifyReceiver_targetVersionCheck() throws Exception {
        // Arrange: add receivers.
        mCarUserService.setLifecycleListenerForApp("package1",
                new UserLifecycleEventFilter.Builder()
                        .addEventType(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED).build(),
                mLifecycleEventReceiver);
        mCarUserService.setLifecycleListenerForApp("package2",
                new UserLifecycleEventFilter.Builder()
                        .addEventType(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED).build(),
                mAnotherLifecycleEventReceiver);

        when(mCarPackageManagerService.getTargetCarVersion("package1"))
                .thenReturn(CarVersion.VERSION_CODES.TIRAMISU_0);
        when(mCarPackageManagerService.getTargetCarVersion("package2"))
                .thenReturn(CarVersion.VERSION_CODES.TIRAMISU_1);

        // Act: User created event occurs.
        sendUserLifecycleEvent(/* fromUser */ 0, mRegularUserId,
                CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED);
        waitForHandlerThreadToFinish();

        // Verify: receivers are called or not depending on whether the target version meets
        // requirement.
        verify(mLifecycleEventReceiver, never()).send(anyInt(), any());
        verify(mAnotherLifecycleEventReceiver).send(anyInt(), any());
    }

    @Test
    public void testOnUserLifecycleEvent_notifyReceiver_singleReceiverWithMultipleFilters()
            throws Exception {
        // Arrange: add one receiver with multiple filters.
        mCarUserService.setLifecycleListenerForApp("package1",
                new UserLifecycleEventFilter.Builder()
                        .addEventType(CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING).build(),
                mLifecycleEventReceiver);
        mCarUserService.setLifecycleListenerForApp("package1",
                new UserLifecycleEventFilter.Builder().addUser(UserHandle.of(mRegularUserId))
                        .build(),
                mLifecycleEventReceiver);

        // Act: user switching event occurs.
        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);
        waitForHandlerThreadToFinish();

        // Verify: receiver is called since one of the filters evaluates to true.
        verify(mLifecycleEventReceiver).send(eq(mRegularUserId), any());
    }

    @Test
    public void testOnUserLifecycleEvent_postUnlockedEvent_notifiesServiceListenersOnly()
            throws Exception {
        // Arrange: add listeners with null filters.
        UserLifecycleListener mockServiceListener = mock(UserLifecycleListener.class);
        mCarUserService.addUserLifecycleListener(/* filter= */ null, mockServiceListener);
        mCarUserService.setLifecycleListenerForApp("package1", /* filter= */ null,
                mLifecycleEventReceiver);

        // Act: user post-unlocked event occurs.
        sendUserLifecycleEvent(/* fromUser */ 0, mRegularUserId,
                CarUserManager.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED);
        waitForHandlerThreadToFinish();

        // Verify: service listener has been called but app listener has not.
        verify(mockServiceListener).onEvent(any(UserLifecycleEvent.class));
        verify(mLifecycleEventReceiver, never()).send(anyInt(), any());
    }

    /**
     * Test that the {@link CarUserService} disables the location service for headless user 0 upon
     * first run.
     */
    @Test
    public void testDisableLocationForHeadlessSystemUserOnFirstRun() {
        sendUserUnlockedEvent(UserHandle.USER_SYSTEM);
        verify(mLocationManager).setLocationEnabledForUser(
                /* enabled= */ false, UserHandle.of(UserHandle.USER_SYSTEM));
        verify(mLocationManager).setAdasGnssLocationEnabled(false);
    }

    /**
     * Test that the {@link CarUserService} updates last active user on user switch in non-headless
     * system user mode.
     */
    @Test
    public void testLastActiveUserUpdatedOnUserSwitch_nonHeadlessSystemUser() throws Exception {
        mockIsHeadlessSystemUser(mRegularUserId, false);
        mockExistingUsers(mExistingUsers);

        sendUserSwitchingEvent(mAdminUserId, mRegularUserId);

        verifyLastActiveUserSet(mRegularUser);
    }

    /**
     * Test that the {@link CarUserService} sets default guest restrictions on first boot.
     */
    @Test
    public void testInitializeGuestRestrictions_IfNotAlreadySet() {
        sendUserUnlockedEvent(UserHandle.USER_SYSTEM);
        assertThat(getSettingsInt(CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET)).isEqualTo(1);
    }

    @Test
    public void testRunOnUser0UnlockImmediate() {
        mUser0TaskExecuted = false;
        sendUserUnlockedEvent(UserHandle.USER_SYSTEM);
        mCarUserService.runOnUser0Unlock(() -> {
            mUser0TaskExecuted = true;
        });
        assertThat(mUser0TaskExecuted).isTrue();
    }

    @Test
    public void testRunOnUser0UnlockLater() {
        mUser0TaskExecuted = false;
        mCarUserService.runOnUser0Unlock(() -> {
            mUser0TaskExecuted = true;
        });
        assertThat(mUser0TaskExecuted).isFalse();
        sendUserUnlockedEvent(UserHandle.USER_SYSTEM);
        assertThat(mUser0TaskExecuted).isTrue();
    }

    /**
     * Test is lengthy as it is testing LRU logic.
     */
    @Test
    public void testBackgroundUserList() throws RemoteException {
        int user1 = 101;
        int user2 = 102;
        int user3 = 103;
        int user4Guest = 104;
        int user5 = 105;

        UserHandle user1Handle = expectRegularUserExists(mMockedUserHandleHelper, user1);
        UserHandle user2Handle = expectRegularUserExists(mMockedUserHandleHelper, user2);
        UserHandle user3Handle = expectRegularUserExists(mMockedUserHandleHelper, user3);
        UserHandle user4GuestHandle = expectGuestUserExists(mMockedUserHandleHelper, user4Guest,
                /* isEphemeral= */ true);
        UserHandle user5Handle = expectRegularUserExists(mMockedUserHandleHelper, user5);

        mockGetCurrentUser(user1);
        sendUserUnlockedEvent(UserHandle.USER_SYSTEM);
        // user 0 should never go to that list.
        assertThat(mCarUserService.getBackgroundUsersToRestart()).isEmpty();

        sendUserUnlockedEvent(user1);
        assertThat(mCarUserService.getBackgroundUsersToRestart()).containsExactly(user1);

        // user 2 background, ignore in restart list
        sendUserUnlockedEvent(user2);
        assertThat(mCarUserService.getBackgroundUsersToRestart()).containsExactly(user1);

        mockGetCurrentUser(user3);
        sendUserUnlockedEvent(user3);
        assertThat(mCarUserService.getBackgroundUsersToRestart()).containsExactly(user1, user3);

        mockGetCurrentUser(user4Guest);
        sendUserUnlockedEvent(user4Guest);
        assertThat(mCarUserService.getBackgroundUsersToRestart()).containsExactly(user1, user3);

        mockGetCurrentUser(user5);
        sendUserUnlockedEvent(user5);
        assertThat(mCarUserService.getBackgroundUsersToRestart()).containsExactly(user3, user5);
    }

    /**
     * Test is lengthy as it is testing LRU logic.
     */
    @Test
    public void testBackgroundUsersStartStopKeepBackgroundUserList() throws Exception {
        int user1 = 101;
        int user2 = 102;
        int user3 = 103;

        UserHandle user1Handle = UserHandle.of(user1);
        UserHandle user2Handle = UserHandle.of(user2);
        UserHandle user3Handle = UserHandle.of(user3);

        mockGetCurrentUser(user1);
        sendUserUnlockedEvent(UserHandle.USER_SYSTEM);
        sendUserUnlockedEvent(user1);
        mockGetCurrentUser(user2);
        sendUserUnlockedEvent(user2);
        sendUserUnlockedEvent(user1);
        mockGetCurrentUser(user3);
        sendUserUnlockedEvent(user3);
        mockStopUserWithDelayedLocking(user3, ActivityManager.USER_OP_IS_CURRENT);

        assertThat(mCarUserService.getBackgroundUsersToRestart()).containsExactly(user2, user3);

        doReturn(true).when(() -> ActivityManagerHelper.startUserInBackground(user2));
        doReturn(true).when(() -> ActivityManagerHelper.unlockUser(user2));
        assertThat(mCarUserService.startAllBackgroundUsersInGarageMode()).containsExactly(user2);
        sendUserUnlockedEvent(user2);
        assertThat(mCarUserService.getBackgroundUsersToRestart()).containsExactly(user2, user3);

        // should not stop the current fg user
        assertThat(mCarUserService.stopBackgroundUserInGagageMode(user3)).isFalse();
        assertThat(mCarUserService.stopBackgroundUserInGagageMode(user2)).isTrue();
        assertThat(mCarUserService.getBackgroundUsersToRestart()).containsExactly(user2, user3);
        assertThat(mCarUserService.getBackgroundUsersToRestart()).containsExactly(user2, user3);
    }

    @Test
    public void testStopUser_success() throws Exception {
        int userId = 101;
        mockStopUserWithDelayedLocking(userId, ActivityManager.USER_OP_SUCCESS);

        AndroidFuture<UserStopResult> userStopResult = new AndroidFuture<>();
        stopUser(userId, userStopResult);

        assertThat(getUserStopResult(userStopResult, userId).getStatus())
                .isEqualTo(UserStopResult.STATUS_SUCCESSFUL);
        assertThat(getUserStopResult(userStopResult, userId).isSuccess()).isTrue();
    }

    @Test
    public void testStopUser_permissionDenied() throws Exception {
        int userId = 101;
        mockManageUsersPermission(android.Manifest.permission.MANAGE_USERS, false);
        mockManageUsersPermission(android.Manifest.permission.CREATE_USERS, false);

        AndroidFuture<UserStopResult> userStopResult = new AndroidFuture<>();
        assertThrows(SecurityException.class, () -> stopUser(userId, userStopResult));
    }

    @Test
    public void testStopUser_fail() throws Exception {
        int userId = 101;
        AndroidFuture<UserStopResult> userStopResult = new AndroidFuture<>();
        CarUserService carUserServiceLocal = new CarUserService(
                mMockContext,
                mUserHal,
                mMockedUserManager,
                mMockedUserHandleHelper,
                mMockedDevicePolicyManager,
                mMockedActivityManager,
                /* maxRunningUsers= */ 3,
                mInitialUserSetter,
                mUserPreCreator,
                mCarUxRestrictionService,
                mMockedHandler,
                mCarPackageManagerService);
        mockStopUserWithDelayedLockingThrows(userId, new IllegalStateException());

        carUserServiceLocal.stopUser(userId, userStopResult);

        ArgumentCaptor<Runnable> runnableCaptor =
                ArgumentCaptor.forClass(Runnable.class);
        verify(mMockedHandler).post(runnableCaptor.capture());
        Runnable runnable = runnableCaptor.getValue();
        expectThrows(IllegalStateException.class, ()-> runnable.run());
    }

    @Test
    public void testStopUser_userDoesNotExist() throws Exception {
        int userId = 101;
        mockStopUserWithDelayedLocking(userId, ActivityManager.USER_OP_UNKNOWN_USER);

        AndroidFuture<UserStopResult> userStopResult = new AndroidFuture<>();
        stopUser(userId, userStopResult);

        assertThat(getUserStopResult(userStopResult, userId).getStatus())
                .isEqualTo(UserStopResult.STATUS_USER_DOES_NOT_EXIST);
        assertThat(getUserStopResult(userStopResult, userId).isSuccess()).isFalse();
    }

    @Test
    public void testStopUser_systemUser() throws Exception {
        int userId = UserHandle.USER_SYSTEM;
        mockStopUserWithDelayedLocking(userId, ActivityManager.USER_OP_ERROR_IS_SYSTEM);

        AndroidFuture<UserStopResult> userStopResult = new AndroidFuture<>();
        stopUser(userId, userStopResult);

        assertThat(getUserStopResult(userStopResult, userId).getStatus())
                .isEqualTo(UserStopResult.STATUS_FAILURE_SYSTEM_USER);
        assertThat(getUserStopResult(userStopResult, userId).isSuccess()).isFalse();
    }

    @Test
    public void testStopUser_currentUser() throws Exception {
        int userId = 101;
        mockStopUserWithDelayedLocking(userId, ActivityManager.USER_OP_IS_CURRENT);

        AndroidFuture<UserStopResult> userStopResult = new AndroidFuture<>();
        stopUser(userId, userStopResult);

        assertThat(getUserStopResult(userStopResult, userId).getStatus())
                .isEqualTo(UserStopResult.STATUS_FAILURE_CURRENT_USER);
        assertThat(getUserStopResult(userStopResult, userId).isSuccess()).isFalse();
    }

    @Test
    public void testStopBackgroundUserForSystemUser() throws Exception {
        mockStopUserWithDelayedLocking(
                UserHandle.USER_SYSTEM, ActivityManager.USER_OP_ERROR_IS_SYSTEM);

        assertThat(mCarUserService.stopBackgroundUserInGagageMode(UserHandle.USER_SYSTEM))
                .isFalse();
    }

    @Test
    public void testStopBackgroundUserForFgUser() throws Exception {
        int userId = 101;
        mockStopUserWithDelayedLocking(userId, ActivityManager.USER_OP_IS_CURRENT);

        assertThat(mCarUserService.stopBackgroundUserInGagageMode(userId)).isFalse();
    }

    @Test
    public void testRemoveUser_currentUser_successSetEphemeral() throws Exception {
        UserHandle currentUser = mRegularUser;
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        UserHandle removeUser = mRegularUser;
        int removedUserId = removeUser.getIdentifier();
        mockRemoveUserNoCallback(removeUser, UserManager.REMOVE_RESULT_DEFERRED);

        removeUser(removedUserId, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(removedUserId),
                UserRemovalResult.STATUS_SUCCESSFUL_SET_EPHEMERAL);
        assertNoHalUserRemoval();
    }

    @Test
    public void testRemoveUser_alreadyBeingRemoved_success() throws Exception {
        UserHandle currentUser = mRegularUser;
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        UserHandle removeUser = mRegularUser;
        int removedUserId = removeUser.getIdentifier();
        mockRemoveUser(removeUser, UserManager.REMOVE_RESULT_ALREADY_BEING_REMOVED);

        removeUser(removedUserId, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(removedUserId),
                UserRemovalResult.STATUS_SUCCESSFUL);
        assertHalRemove(currentUser, removeUser);
    }

    @Test
    public void testRemoveUser_currentLastAdmin_successSetEphemeral() throws Exception {
        UserHandle currentUser = mAdminUser;
        List<UserHandle> existingUsers = Arrays.asList(mAdminUser, mRegularUser);
        mockExistingUsersAndCurrentUser(existingUsers, currentUser);
        UserHandle removeUser = mAdminUser;
        int removedUserId = removeUser.getIdentifier();
        mockRemoveUserNoCallback(removeUser, UserManager.REMOVE_RESULT_DEFERRED);

        removeUser(mAdminUserId, NO_CALLER_RESTRICTIONS, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(removedUserId),
                UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL);
        assertNoHalUserRemoval();
    }

    @Test
    public void testRemoveUser_userNotExist() throws Exception {
        int removedUserId = 15;
        removeUser(removedUserId, NO_CALLER_RESTRICTIONS, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(removedUserId),
                UserRemovalResult.STATUS_USER_DOES_NOT_EXIST);
    }

    @Test
    public void testRemoveUser_lastAdminUser_success() throws Exception {
        UserHandle currentUser = mRegularUser;
        UserHandle removeUser = mAdminUser;
        List<UserHandle> existingUsers = Arrays.asList(mAdminUser, mRegularUser);

        mockExistingUsersAndCurrentUser(existingUsers, currentUser);
        mockRemoveUser(removeUser);

        removeUser(mAdminUserId, NO_CALLER_RESTRICTIONS, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(mAdminUserId),
                UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED);
        assertHalRemove(currentUser, removeUser);
    }

    @Test
    public void testRemoveUser_notLastAdminUser_success() throws Exception {
        UserHandle currentUser = mRegularUser;
        // Give admin rights to current user.
        // currentUser.flags = currentUser.flags | FLAG_ADMIN;
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        UserHandle removeUser = mAdminUser;
        int removedUserId = removeUser.getIdentifier();
        mockRemoveUser(removeUser);

        removeUser(removedUserId, NO_CALLER_RESTRICTIONS, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(removedUserId),
                UserRemovalResult.STATUS_SUCCESSFUL);
        assertHalRemove(currentUser, removeUser);
    }

    @Test
    public void testRemoveUser_success() throws Exception {
        UserHandle currentUser = mAdminUser;
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        UserHandle removeUser = mRegularUser;
        int removedUserId = removeUser.getIdentifier();
        mockRemoveUser(removeUser);

        removeUser(removedUserId, NO_CALLER_RESTRICTIONS, mUserRemovalFuture);
        UserRemovalResult result = getUserRemovalResult(removedUserId);

        assertUserRemovalResultStatus(result, UserRemovalResult.STATUS_SUCCESSFUL);
        assertHalRemove(currentUser, removeUser);
    }

    @Test
    public void testRemoveUser_halNotSupported() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        UserHandle removeUser = mRegularUser;
        int removedUserId = removeUser.getIdentifier();
        mockUserHalSupported(false);
        mockRemoveUser(removeUser);

        removeUser(removedUserId, NO_CALLER_RESTRICTIONS, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(removedUserId),
                UserRemovalResult.STATUS_SUCCESSFUL);
        assertNoHalUserRemoval();
    }

    @Test
    public void testRemoveUser_androidFailure() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int targetUserId = mRegularUserId;
        mockRemoveUser(mRegularUser, UserManager.REMOVE_RESULT_ERROR_UNKNOWN);

        removeUser(targetUserId, NO_CALLER_RESTRICTIONS, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(targetUserId),
                UserRemovalResult.STATUS_ANDROID_FAILURE);
    }

    @Test
    public void testRemoveUserWithRestriction_nonAdminRemovingAdmin() throws Exception {
        UserHandle currentUser = mRegularUser;
        UserHandle removeUser = mAdminUser;
        int removedUserId = removeUser.getIdentifier();
        mockGetCallingUserHandle(currentUser.getIdentifier());
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        mockRemoveUser(removeUser, /* overrideDevicePolicy= */ true);

        assertThrows(SecurityException.class,
                () -> removeUser(removedUserId, HAS_CALLER_RESTRICTIONS, mUserRemovalFuture));
    }

    @Test
    public void testRemoveUserWithRestriction_nonAdminRemovingNonAdmin() throws Exception {
        UserHandle currentUser = mRegularUser;
        UserHandle removeUser = mAnotherRegularUser;
        int removedUserId = removeUser.getIdentifier();
        mockGetCallingUserHandle(currentUser.getIdentifier());
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        mockRemoveUser(removeUser, /* overrideDevicePolicy= */ true);

        assertThrows(SecurityException.class,
                () -> removeUser(removedUserId, HAS_CALLER_RESTRICTIONS, mUserRemovalFuture));
    }

    @Test
    public void testRemoveUserWithRestriction_nonAdminRemovingItself() throws Exception {
        UserHandle currentUser = mRegularUser;
        UserHandle removeUser = mRegularUser;
        int removedUserId = removeUser.getIdentifier();
        mockGetCallingUserHandle(currentUser.getIdentifier());
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        mockRemoveUserNoCallback(removeUser, /* overrideDevicePolicy= */ true,
                UserManager.REMOVE_RESULT_DEFERRED);

        removeUser(removedUserId, HAS_CALLER_RESTRICTIONS, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(removedUserId),
                UserRemovalResult.STATUS_SUCCESSFUL_SET_EPHEMERAL);
        assertNoHalUserRemoval();
    }

    @Test
    public void testRemoveUserWithRestriction_adminRemovingAdmin() throws Exception {
        UserHandle currentUser = mAdminUser;
        UserHandle removeUser = mAnotherAdminUser;
        int removedUserId = removeUser.getIdentifier();
        mockGetCallingUserHandle(currentUser.getIdentifier());
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        mockRemoveUser(removeUser, /* overrideDevicePolicy= */ true);

        removeUser(removedUserId, HAS_CALLER_RESTRICTIONS, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(removedUserId),
                UserRemovalResult.STATUS_SUCCESSFUL);
        assertHalRemove(currentUser, removeUser, /* overrideDevicePolicy= */ true);
    }

    @Test
    public void testRemoveUserWithRestriction_adminRemovingNonAdmin() throws Exception {
        UserHandle currentUser = mAdminUser;
        UserHandle removeUser = mRegularUser;
        int removedUserId = removeUser.getIdentifier();
        mockGetCallingUserHandle(currentUser.getIdentifier());
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        mockRemoveUser(removeUser, /* overrideDevicePolicy= */ true);

        removeUser(removedUserId, HAS_CALLER_RESTRICTIONS, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(removedUserId),
                UserRemovalResult.STATUS_SUCCESSFUL);
        assertHalRemove(currentUser, removeUser, /* overrideDevicePolicy= */ true);
    }

    @Test
    public void testRemoveUserWithRestriction_adminRemovingItself() throws Exception {
        UserHandle currentUser = mAdminUser;
        UserHandle removeUser = mAdminUser;
        int removedUserId = removeUser.getIdentifier();
        mockGetCallingUserHandle(currentUser.getIdentifier());
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        mockRemoveUserNoCallback(removeUser, /* overrideDevicePolicy= */ true,
                UserManager.REMOVE_RESULT_DEFERRED);

        removeUser(removedUserId, HAS_CALLER_RESTRICTIONS, mUserRemovalFuture);

        assertUserRemovalResultStatus(getUserRemovalResult(removedUserId),
                UserRemovalResult.STATUS_SUCCESSFUL_SET_EPHEMERAL);
        assertNoHalUserRemoval();
    }

    @Test
    public void testSwitchUser_nullReceiver() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);

        assertThrows(NullPointerException.class,
                () -> switchUser(mAdminUserId, mAsyncCallTimeoutMs, null));
    }

    @Test
    public void testSwitchUser_nonExistingTarget() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> mCarUserService
                .switchUser(NON_EXISTING_USER, mAsyncCallTimeoutMs, mUserSwitchFuture));
    }

    @Test
    public void testSwitchUser_noUserSwitchability() throws Exception {
        UserHandle currentUser = mAdminUser;
        mockExistingUsersAndCurrentUser(mExistingUsers, currentUser);
        mockUmGetUserSwitchability(mMockedUserManager,
                UserManager.SWITCHABILITY_STATUS_SYSTEM_USER_LOCKED);

        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_NOT_SWITCHABLE);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_targetSameAsCurrentUser() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);

        switchUser(mAdminUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mAdminUserId).getStatus(),
                UserSwitchResult.STATUS_OK_USER_ALREADY_IN_FOREGROUND);
        verifyNoUserSwitch();
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_halNotSupported_success() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mockUserHalSupported(false);
        mockAmSwitchUser(mMockedActivityManager, mRegularUser, true);

        switchUser(mRegularUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mRegularUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);

        // update current user due to successful user switch
        mockCurrentUser(mRegularUser);
        sendUserUnlockedEvent(mRegularUserId);
        assertNoHalUserSwitch();
        assertNoPostSwitch();
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_halNotSupported_failure() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mockUserHalSupported(false);
        // Don't need to call mockAmSwitchUser() because it returns false by default

        switchUser(mRegularUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResultForAndroidFailure(getUserSwitchResult(mRegularUserId),
                UserManager.USER_OPERATION_ERROR_UNKNOWN);
        assertNoHalUserSwitch();
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_HalSuccessAndroidSuccess() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mGuestUser, true);

        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);

        // update current user due to successful user switch
        mockCurrentUser(mGuestUser);
        sendUserUnlockedEvent(mGuestUserId);
        assertPostSwitch(requestId, mGuestUserId, mGuestUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_HalSuccessAndroidFailure() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mGuestUser, false);

        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_ANDROID_FAILURE);
        assertPostSwitch(requestId, mAdminUserId, mGuestUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_HalFailure() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mSwitchUserResponse.status = SwitchUserStatus.FAILURE;
        mSwitchUserResponse.errorMessage = "Error Message";
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);

        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResultWithError(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_HAL_FAILURE, mSwitchUserResponse.errorMessage);
        verifyNoUserSwitch();
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_nullHalResponse() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mockHalSwitch(mAdminUserId, mGuestUser, /* response= */ null);

        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResultWithError(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE, /* expectedErrorMessage= */ null);
        verifyNoUserSwitch();
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_error_badCallbackStatus() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mockHalSwitch(mAdminUserId, HalCallback.STATUS_WRONG_HAL_RESPONSE,
                mSwitchUserResponse, mGuestUser);

        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE);
        verifyNoUserSwitch();
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_failUxRestrictedOnInit() throws Exception {
        mockGetUxRestrictions(/*restricted= */ true);
        mockExistingUsersAndCurrentUser(mAdminUser);

        initService();
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_UX_RESTRICTION_FAILURE);
        assertNoHalUserSwitch();
        verifyNoUserSwitch();
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_failUxRestrictionsChanged() throws Exception {
        mockGetUxRestrictions(/*restricted= */ false); // not restricted when CarService init()s
        mockExistingUsersAndCurrentUser(mAdminUser);
        mSwitchUserResponse.requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mGuestUser, true);

        // Should be ok first time...
        ICarUxRestrictionsChangeListener listener = initService();
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);
        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);

        // ...but then fail after the state changed
        mockCurrentUser(mGuestUser);
        updateUxRestrictions(listener, /* restricted= */ true); // changed state
        switchUser(mAdminUserId, mAsyncCallTimeoutMs, mUserSwitchFuture2);
        assertUserSwitchResult(getUserSwitchResult2(mAdminUserId),
                UserSwitchResult.STATUS_UX_RESTRICTION_FAILURE);

        // Verify only initial call succeeded (if second was also called the mocks, verify() would
        // fail because it was called more than once()
        assertHalSwitchAnyUser();
        verifyAnyUserSwitch();
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_multipleCallsDifferentUser_beforeFirstUserUnlocked()
            throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mGuestUser, true);
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        // calling another user switch before unlock
        int newRequestId = 43;
        SwitchUserResponse switchUserResponse = new SwitchUserResponse();
        switchUserResponse.status = SwitchUserStatus.SUCCESS;
        switchUserResponse.requestId = newRequestId;
        mockHalSwitch(mAdminUserId, mRegularUser, switchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mRegularUser, true);
        switchUser(mRegularUserId, mAsyncCallTimeoutMs, mUserSwitchFuture2);

        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        assertUserSwitchResult(getUserSwitchResult2(mRegularUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        assertNoPostSwitch();
        assertHalSwitch(mAdminUserId, mGuestUserId);
        assertHalSwitch(mAdminUserId, mRegularUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_multipleCallsDifferentUser_beforeFirstUserUnlock_abandonFirstCall()
            throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mGuestUser, true);
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        // calling another user switch before unlock
        int newRequestId = 43;
        SwitchUserResponse switchUserResponse = new SwitchUserResponse();
        switchUserResponse.status = SwitchUserStatus.SUCCESS;
        switchUserResponse.requestId = newRequestId;
        mockHalSwitch(mAdminUserId, mRegularUser, switchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mRegularUser, true);
        switchUser(mRegularUserId, mAsyncCallTimeoutMs, mUserSwitchFuture2);
        mockCurrentUser(mRegularUser);
        sendUserUnlockedEvent(mRegularUserId);

        if (false) { // TODO(b/214437189): add this assertion (right now it's returning SUCCESSFUL
            assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                    UserSwitchResult.STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST);
        }
        assertUserSwitchResult(getUserSwitchResult2(mRegularUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        assertPostSwitch(newRequestId, mRegularUserId, mRegularUserId);
        assertHalSwitch(mAdminUserId, mGuestUserId);
        assertHalSwitch(mAdminUserId, mRegularUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_multipleCallsDifferentUser_beforeFirstUserUnlock_legacySwitch()
            throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mGuestUser, true);

        // First switch, using CarUserManager
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        // update current user due to successful user switch
        mockCurrentUser(mGuestUser);

        assertHalSwitch(mAdminUserId, mGuestUserId);
        // Unlock event was not sent, so it should not receive postSwitch
        assertNoPostSwitch();

        // Second switch, using legacy APIs
        sendUserSwitchingEvent(mGuestUserId, mAdminUserId);

        verify(mUserHal).legacyUserSwitch(
                isSwitchUserRequest(/* requestId= */ 0, mGuestUserId, mAdminUserId));
    }

    @Test
    public void testSwitchUser_multipleCallsDifferentUser_beforeHALResponded()
            throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        // calling another user switch before unlock
        int newRequestId = 43;
        SwitchUserResponse switchUserResponse = new SwitchUserResponse();
        switchUserResponse.status = SwitchUserStatus.SUCCESS;
        switchUserResponse.requestId = newRequestId;
        mockHalSwitch(mAdminUserId, mRegularUser, switchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mRegularUser, true);
        switchUser(mRegularUserId, mAsyncCallTimeoutMs, mUserSwitchFuture2);

        if (false) { // TODO(b/214437189): add this assertion (right now it times out)
            assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                    UserSwitchResult.STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST);
        }
        assertUserSwitchResult(getUserSwitchResult2(mRegularUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        assertNoPostSwitch();
        assertHalSwitch(mAdminUserId, mGuestUserId);
        assertHalSwitch(mAdminUserId, mRegularUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_multipleCallsDifferentUser_beforeHALResponded_abandonFirstCall()
            throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        // calling another user switch before unlock
        int newRequestId = 43;
        SwitchUserResponse switchUserResponse = new SwitchUserResponse();
        switchUserResponse.status = SwitchUserStatus.SUCCESS;
        switchUserResponse.requestId = newRequestId;
        mockHalSwitch(mAdminUserId, mRegularUser, switchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mRegularUser, true);
        switchUser(mRegularUserId, mAsyncCallTimeoutMs, mUserSwitchFuture2);

        mockCurrentUser(mRegularUser);
        sendUserUnlockedEvent(mRegularUserId);

        if (false) { // TODO(b/214437189): add this assertion (right now it times out)
            assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                    UserSwitchResult.STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST);
        }
        assertUserSwitchResult(getUserSwitchResult2(mRegularUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        assertPostSwitch(newRequestId, mRegularUserId, mRegularUserId);
        assertHalSwitch(mAdminUserId, mGuestUserId);
        assertHalSwitch(mAdminUserId, mRegularUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_multipleCallsDifferentUser_HALRespondedLate_abandonFirstCall()
            throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        BlockingAnswer<Void> blockingAnswer = mockHalSwitchLateResponse(mAdminUserId,
                mGuestUser, mSwitchUserResponse);
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        // calling another user switch before unlock
        int newRequestId = 43;
        SwitchUserResponse switchUserResponse = new SwitchUserResponse();
        switchUserResponse.status = SwitchUserStatus.SUCCESS;
        switchUserResponse.requestId = newRequestId;
        mockHalSwitch(mAdminUserId, mRegularUser, switchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mRegularUser, true);
        switchUser(mRegularUserId, mAsyncCallTimeoutMs, mUserSwitchFuture2);

        mockCurrentUser(mRegularUser);
        sendUserUnlockedEvent(mRegularUserId);
        blockingAnswer.unblock();

        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST);
        assertUserSwitchResult(getUserSwitchResult2(mRegularUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        assertPostSwitch(newRequestId, mRegularUserId, mRegularUserId);
        assertHalSwitch(mAdminUserId, mGuestUserId);
        assertHalSwitch(mAdminUserId, mRegularUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_multipleCallsSameUser_beforeHALResponded() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;

        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        // calling another user switch before unlock
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture2);

        // TODO(b/214437189): should also assert first call
        assertUserSwitchResult(getUserSwitchResult2(mGuestUserId),
                UserSwitchResult.STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO);
        assertNoPostSwitch();
        assertHalSwitch(mAdminUserId, mGuestUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_multipleCallsSameUser_beforeFirstUserUnlocked() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mGuestUser, true);

        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);
        // calling another user switch before unlock
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture2);

        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        assertUserSwitchResult(getUserSwitchResult2(mGuestUserId),
                UserSwitchResult.STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO);
        assertNoPostSwitch();
        assertHalSwitch(mAdminUserId, mGuestUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_multipleCallsSameUser_beforeFirstUserUnlocked_noAffectOnFirstCall()
            throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mGuestUser, true);

        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);
        int newRequestId = 43;
        mSwitchUserResponse.requestId = newRequestId;

        // calling another user switch before unlock
        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture2);
        mockCurrentUser(mGuestUser);
        sendUserUnlockedEvent(mGuestUserId);

        assertUserSwitchResult(getUserSwitchResult(mGuestUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        assertUserSwitchResult(getUserSwitchResult2(mGuestUserId),
                UserSwitchResult.STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO);
        assertPostSwitch(requestId, mGuestUserId, mGuestUserId);
        assertHalSwitch(mAdminUserId, mGuestUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_InvalidPermission() throws Exception {
        mockManageUsersPermission(android.Manifest.permission.MANAGE_USERS, false);

        assertThrows(SecurityException.class, () -> mCarUserService
                .switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture));
    }

    @Test
    public void testLegacyUserSwitch_ok() throws Exception {
        mockExistingUsers(mExistingUsers);
        int targetUserId = mRegularUserId;
        int sourceUserId = mAdminUserId;

        mockCallerUid(Binder.getCallingUid(), true);
        mCarUserService.setUserSwitchUiCallback(mSwitchUserUiReceiver);

        sendUserSwitchingEvent(sourceUserId, targetUserId);

        verify(mUserHal).legacyUserSwitch(
                isSwitchUserRequest(/* requestId= */ 0, sourceUserId, targetUserId));
        verify(mSwitchUserUiReceiver).send(targetUserId, null);
        verifyNoLogoutUser();
    }

    @Test
    public void testLegacyUserSwitch_notCalledAfterNormalSwitch() throws Exception {
        // Arrange - emulate normal switch
        mockExistingUsersAndCurrentUser(mAdminUser);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mGuestUser, true);
        int targetUserId = mGuestUserId;
        mockCallerUid(Binder.getCallingUid(), true);
        mCarUserService.setUserSwitchUiCallback(mSwitchUserUiReceiver);
        switchUser(targetUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        // Act - trigger legacy switch
        sendUserSwitchingEvent(mAdminUserId, targetUserId);

        // Assert
        verify(mUserHal, never()).legacyUserSwitch(any());
        verify(mSwitchUserUiReceiver).send(targetUserId, null);
        verifyNoLogoutUser();
    }

    @Test
    public void testSetSwitchUserUI_receiverSetAndCalled() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int callerId = Binder.getCallingUid();
        mockCallerUid(callerId, true);
        int requestId = 42;
        mCarUserService.setUserSwitchUiCallback(mSwitchUserUiReceiver);

        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mAdminUserId, mGuestUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mGuestUser, true);

        switchUser(mGuestUserId, mAsyncCallTimeoutMs, mUserSwitchFuture);

        // update current user due to successful user switch
        verify(mSwitchUserUiReceiver).send(mGuestUserId, null);
    }

    @Test
    public void testSetSwitchUserUI_nonCarSysUiCaller() throws Exception {
        int callerId = Binder.getCallingUid();
        mockCallerUid(callerId, false);

        assertThrows(SecurityException.class,
                () -> mCarUserService.setUserSwitchUiCallback(mSwitchUserUiReceiver));
    }

    @Test
    public void testSwitchUser_OEMRequest_success() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mockAmSwitchUser(mMockedActivityManager, mRegularUser, true);
        int requestId = -1;

        mCarUserService.switchAndroidUserFromHal(requestId, mRegularUserId);
        mockCurrentUser(mRegularUser);
        sendUserUnlockedEvent(mRegularUserId);

        assertPostSwitch(requestId, mRegularUserId, mRegularUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testSwitchUser_OEMRequest_failure() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mockAmSwitchUser(mMockedActivityManager, mRegularUser, false);
        int requestId = -1;

        mCarUserService.switchAndroidUserFromHal(requestId, mRegularUserId);

        assertPostSwitch(requestId, mAdminUserId, mRegularUserId);
        verifyNoLogoutUser();
    }

    @Test
    public void testLogoutUser_currentUserNotSwitchedByDeviceAdmin() throws Exception {
        mockNoLogoutUserId();

        logoutUser(mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getResult(mUserSwitchFuture, "result of user not logged in"),
                UserSwitchResult.STATUS_NOT_LOGGED_IN);
        verifyNoLogoutUser();
        verifyNoUserSwitch();
    }

    @Test
    public void testLogoutUser_halNotSupported_noUserSwitchability() throws Exception {
        mockLogoutUser(mAdminUser);
        mockUserHalSupported(false);
        mockDpmLogoutUser(mMockedDevicePolicyManager, UserManager.USER_OPERATION_SUCCESS);
        mockUmGetUserSwitchability(mMockedUserManager,
                UserManager.SWITCHABILITY_STATUS_SYSTEM_USER_LOCKED);

        logoutUser(mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mAdminUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        verifyLogoutUser();
        verifyNoUserSwitch();
    }

    @Test
    public void testLogoutUser_halNotSupported_success() throws Exception {
        mockLogoutUser(mAdminUser);
        mockUserHalSupported(false);
        mockDpmLogoutUser(mMockedDevicePolicyManager, UserManager.USER_OPERATION_SUCCESS);

        logoutUser(mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mAdminUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);
        verifyLogoutUser();
        verifyNoUserSwitch();
    }

    @Test
    public void testLogoutUser_halNotSupported_failure() throws Exception {
        mockLogoutUser(mAdminUser);
        mockUserHalSupported(false);
        mockDpmLogoutUser(mMockedDevicePolicyManager, UserManager.USER_OPERATION_ERROR_MAX_USERS);

        logoutUser(mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResultForAndroidFailure(getUserSwitchResult(mAdminUserId),
                UserManager.USER_OPERATION_ERROR_MAX_USERS);
        verifyLogoutUser();
        verifyNoUserSwitch();
    }

    @Test
    public void testLogoutUser_halSuccessAndroidSuccess() throws Exception {
        mockExistingUsersAndCurrentUser(mGuestUser);
        mockLogoutUser(mAdminUser);
        mockUserHalSupported(true);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mGuestUserId, mAdminUser, mSwitchUserResponse);
        mockAmSwitchUser(mMockedActivityManager, mAdminUser, true);

        logoutUser(mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mAdminUserId),
                UserSwitchResult.STATUS_SUCCESSFUL);

        // update current user due to successful user switch
        mockCurrentUser(mAdminUser);
        sendUserUnlockedEvent(mAdminUserId);
        assertPostSwitch(requestId, mAdminUserId, mAdminUserId);
        verifyLogoutUser();
        verifyNoUserSwitch();
    }

    @Test
    public void testLogoutUser_halSuccessAndroidFailure() throws Exception {
        mockExistingUsersAndCurrentUser(mGuestUser);
        mockLogoutUser(mAdminUser);
        mockUserHalSupported(true);
        int requestId = 42;
        mSwitchUserResponse.status = SwitchUserStatus.SUCCESS;
        mSwitchUserResponse.requestId = requestId;
        mockHalSwitch(mGuestUserId, mAdminUser, mSwitchUserResponse);
        mockDpmLogoutUser(mMockedDevicePolicyManager, UserManager.USER_OPERATION_ERROR_MAX_USERS);

        logoutUser(mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResultForAndroidFailure(getUserSwitchResult(mAdminUserId),
                UserManager.USER_OPERATION_ERROR_MAX_USERS);
        verifyLogoutUser();
        verifyNoUserSwitch();
        assertPostSwitch(requestId, mGuestUserId, mAdminUserId);
    }

    @Test
    public void testLogoutUser_halFailure() throws Exception {
        mockExistingUsersAndCurrentUser(mGuestUser);
        mockLogoutUser(mAdminUser);
        mockUserHalSupported(true);
        mSwitchUserResponse.status = SwitchUserStatus.FAILURE;
        mSwitchUserResponse.errorMessage = "Error Message";
        mockHalSwitch(mGuestUserId, mAdminUser, mSwitchUserResponse);

        logoutUser(mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResultWithError(getUserSwitchResult(mAdminUserId),
                UserSwitchResult.STATUS_HAL_FAILURE, mSwitchUserResponse.errorMessage);

        verifyNoLogoutUser();
        verifyNoUserSwitch();
    }

    @Test
    public void testLogoutUser_failUxRestrictedOnInit() throws Exception {
        mockGetUxRestrictions(/*restricted= */ true);
        mockLogoutUser(mAdminUser);

        initService();
        logoutUser(mAsyncCallTimeoutMs, mUserSwitchFuture);

        assertUserSwitchResult(getUserSwitchResult(mAdminUserId),
                UserSwitchResult.STATUS_UX_RESTRICTION_FAILURE);
        verifyNoLogoutUser();
        assertNoHalUserSwitch();
        verifyNoUserSwitch();
    }

    @Test
    public void testLogoutUser_InvalidPermission() throws Exception {
        mockManageUsersPermission(android.Manifest.permission.MANAGE_USERS, false);

        assertThrows(SecurityException.class, () -> mCarUserService
                .logoutUser(mAsyncCallTimeoutMs, mUserSwitchFuture));
    }

    @Test
    public void testCreateUser_nullType() throws Exception {
        assertThrows(NullPointerException.class, () -> mCarUserService
                .createUser("dude", null, 108, mAsyncCallTimeoutMs, new AndroidFuture<>(),
                        NO_CALLER_RESTRICTIONS));
    }

    @Test
    public void testCreateUser_nullReceiver() throws Exception {
        assertThrows(NullPointerException.class, () -> mCarUserService
                .createUser("dude", "TypeONegative", 108, mAsyncCallTimeoutMs, null,
                        NO_CALLER_RESTRICTIONS));
    }

    @Test
    public void testCreateUser_umCreateReturnsNull() throws Exception {
        NewUserResponse response = new NewUserResponse(/* user= */ null,
                UserManager.USER_OPERATION_ERROR_MAX_USERS);
        mockUmCreateUser(mMockedUserManager, "dude", "TypeONegative", 108, response);

        createUser("dude", "TypeONegative", 108, mAsyncCallTimeoutMs, mUserCreationFuture,
                NO_CALLER_RESTRICTIONS);

        UserCreationResult result = getUserCreationResult();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_ANDROID_FAILURE);
        assertThat(result.getAndroidFailureStatus())
                .isEqualTo(UserManager.USER_OPERATION_ERROR_MAX_USERS);
        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isNull();
        assertNoHalUserCreation();
        verifyNoUserRemoved();
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_umCreateThrowsException() throws Exception {
        RuntimeException exception = new RuntimeException("D'OH!");
        mockUmCreateUser(mMockedUserManager, "dude", "TypeONegative", 108, exception);

        createUser("dude", "TypeONegative", 108, mAsyncCallTimeoutMs, mUserCreationFuture,
                NO_CALLER_RESTRICTIONS);

        UserCreationResult result = getUserCreationResult();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_ANDROID_FAILURE);
        assertThat(result.getAndroidFailureStatus())
                .isEqualTo(UserManager.USER_OPERATION_ERROR_UNKNOWN);
        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isEqualTo(exception.toString());
        assertNoHalUserCreation();
        verifyNoUserRemoved();
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_internalHalFailure() throws Exception {
        UserHandle newUser = UserHandle.of(42);
        mockUmCreateUser(mMockedUserManager, "dude", "TypeONegative", 108, newUser);
        mockHalCreateUser(HalCallback.STATUS_INVALID, /* not_used_status= */ -1);
        mockRemoveUser(newUser);

        createUser("dude", "TypeONegative", 108, mAsyncCallTimeoutMs, mUserCreationFuture,
                NO_CALLER_RESTRICTIONS);

        UserCreationResult result = getUserCreationResult();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.getAndroidFailureStatus()).isNull();
        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isNull();
        verifyUserRemoved(newUser.getIdentifier());
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_halFailure() throws Exception {
        UserHandle newUser = UserHandle.of(42);
        mockUmCreateUser(mMockedUserManager, "dude", "TypeONegative", 108, newUser);
        mockHalCreateUser(HalCallback.STATUS_OK, CreateUserStatus.FAILURE);
        mockRemoveUser(newUser);

        createUser("dude", "TypeONegative", 108, mAsyncCallTimeoutMs, mUserCreationFuture,
                NO_CALLER_RESTRICTIONS);

        UserCreationResult result = getUserCreationResult();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_HAL_FAILURE);
        assertThat(result.getAndroidFailureStatus()).isNull();
        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isNull();

        verifyUserRemoved(newUser.getIdentifier());
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_halTimeout() throws Exception {
        UserHandle newUser = UserHandle.of(42);
        mockUmCreateUser(mMockedUserManager, "dude", "TypeONegative", 108, newUser);
        mockHalCreateUser(HalCallback.STATUS_HAL_SET_TIMEOUT, /* response= */ null);
        mockRemoveUser(newUser);

        createUser("dude", "TypeONegative", 108, mAsyncCallTimeoutMs, mUserCreationFuture,
                NO_CALLER_RESTRICTIONS);

        UserCreationResult result = getUserCreationResult();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.getAndroidFailureStatus()).isNull();
        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isNull();

        verifyUserRemoved(newUser.getIdentifier());
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_halServiceThrowsRuntimeException() throws Exception {
        UserHandle newUser = UserHandle.of(42);
        mockUmCreateUser(mMockedUserManager, "dude", "TypeONegative", 108, newUser);
        Exception exception = new RuntimeException("D'OH!");
        mockHalCreateUserThrowsRuntimeException(exception);
        mockRemoveUser(newUser);

        createUser("dude", "TypeONegative", 108, mAsyncCallTimeoutMs, mUserCreationFuture,
                NO_CALLER_RESTRICTIONS);

        UserCreationResult result = getUserCreationResult();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_HAL_INTERNAL_FAILURE);
        assertThat(result.getAndroidFailureStatus()).isNull();
        assertThat(result.getUser()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isEqualTo(exception.toString());

        verifyUserRemoved(newUser.getIdentifier());
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_halNotSupported_success() throws Exception {
        mockUserHalSupported(false);
        mockExistingUsersAndCurrentUser(mAdminUser);
        int userId = mRegularUserId;
        mockUmCreateUser(mMockedUserManager, "dude", UserManager.USER_TYPE_FULL_SECONDARY,
                UserManagerHelper.FLAG_EPHEMERAL, mRegularUser);

        createUser("dude", UserManager.USER_TYPE_FULL_SECONDARY, UserManagerHelper.FLAG_EPHEMERAL,
                mAsyncCallTimeoutMs, mUserCreationFuture, NO_CALLER_RESTRICTIONS);

        UserCreationResult result = getUserCreationResult();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_SUCCESSFUL);
        assertThat(result.getAndroidFailureStatus()).isNull();
        assertThat(result.getUser()).isEqualTo(mRegularUser);
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isNull();

        assertNoHalUserCreation();
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_disallowAddUser() throws Exception {
        mockUmHasUserRestrictionForUser(mMockedUserManager, Process.myUserHandle(),
                UserManager.DISALLOW_ADD_USER, /* value= */ true);
        mockUmCreateUser(mMockedUserManager, "dude", UserManager.USER_TYPE_FULL_SECONDARY,
                /* flags= */ 0, UserHandle.of(42));

        createUser("dude", UserManager.USER_TYPE_FULL_SECONDARY, /* flags= */ 0,
                mAsyncCallTimeoutMs, mUserCreationFuture, NO_CALLER_RESTRICTIONS);

        assertUserCreationWithInternalErrorMessage(mUserCreationFuture,
                UserCreationResult.STATUS_ANDROID_FAILURE,
                CarUserService.ERROR_TEMPLATE_DISALLOW_ADD_USER, Process.myUserHandle(),
                UserManager.DISALLOW_ADD_USER);
        assertNoHalUserCreation();
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_success() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int userId = 300;
        expectEphemeralUserExists(mMockedUserHandleHelper, userId);

        mockUmCreateUser(mMockedUserManager, "dude", UserManager.USER_TYPE_FULL_SECONDARY,
                UserManagerHelper.FLAG_EPHEMERAL, UserHandle.of(userId));
        ArgumentCaptor<CreateUserRequest> requestCaptor =
                mockHalCreateUser(HalCallback.STATUS_OK, CreateUserStatus.SUCCESS);

        createUser("dude", UserManager.USER_TYPE_FULL_SECONDARY, UserManagerHelper.FLAG_EPHEMERAL,
                mAsyncCallTimeoutMs, mUserCreationFuture, NO_CALLER_RESTRICTIONS);

        // Assert request
        CreateUserRequest request = requestCaptor.getValue();
        Log.d(TAG, "createUser() request: " + request);
        assertThat(request.newUserName).isEqualTo("dude");
        assertThat(request.newUserInfo.userId).isEqualTo(userId);
        assertThat(request.newUserInfo.flags).isEqualTo(
                android.hardware.automotive.vehicle.UserInfo.USER_FLAG_EPHEMERAL);
        assertDefaultUsersInfo(request.usersInfo, mAdminUser);

        UserCreationResult result = getUserCreationResult();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_SUCCESSFUL);
        assertThat(result.getAndroidFailureStatus()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isNull();
        UserHandle newUser = result.getUser();
        assertThat(newUser).isNotNull();
        assertThat(newUser.getIdentifier()).isEqualTo(userId);

        verify(mMockedUserManager, never()).createGuest(any(Context.class));
        verifyNoUserRemoved();
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_guest_success() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        int userId = mGuestUserId;
        mockUmCreateGuest(mMockedUserManager, "guest", userId);
        ArgumentCaptor<CreateUserRequest> requestCaptor =
                mockHalCreateUser(HalCallback.STATUS_OK, CreateUserStatus.SUCCESS);

        createUser("guest", UserManager.USER_TYPE_FULL_GUEST, /* flags= */ 0,
                mAsyncCallTimeoutMs, mUserCreationFuture, NO_CALLER_RESTRICTIONS);

        // Assert request
        CreateUserRequest request = requestCaptor.getValue();
        Log.d(TAG, "createUser() request: " + request);
        assertThat(request.newUserName).isEqualTo("guest");
        assertThat(request.newUserInfo.userId).isEqualTo(userId);
        assertThat(request.newUserInfo.flags).isEqualTo(
                android.hardware.automotive.vehicle.UserInfo.USER_FLAG_GUEST);
        assertDefaultUsersInfo(request.usersInfo, mAdminUser);

        UserCreationResult result = getUserCreationResult();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_SUCCESSFUL);
        assertThat(result.getAndroidFailureStatus()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isNull();
        UserHandle newUser = result.getUser();
        assertThat(newUser).isNotNull();
        assertThat(newUser.getIdentifier()).isEqualTo(userId);

        verify(mMockedUserManager, never()).createUser(anyString(), anyString(), anyInt());
        verifyNoUserRemoved();
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_guest_failsWithNonZeroFlags() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);

        String name = "guest";
        int flags = UserManagerHelper.FLAG_EPHEMERAL;
        createUser(name, UserManager.USER_TYPE_FULL_GUEST, flags, mAsyncCallTimeoutMs,
                mUserCreationFuture, NO_CALLER_RESTRICTIONS);

        assertUserCreationInvalidArgumentsFailureWithInternalErrorMessage(mUserCreationFuture,
                CarUserService.ERROR_TEMPLATE_INVALID_FLAGS_FOR_GUEST_CREATION, flags, name);
    }

    @Test
    public void testCreateUser_success_nullName() throws Exception {
        String nullName = null;
        mockExistingUsersAndCurrentUser(mAdminUser);
        int userId = 300;
        UserHandle expectedeUser = expectEphemeralUserExists(mMockedUserHandleHelper, userId);

        mockUmCreateUser(mMockedUserManager, nullName, UserManager.USER_TYPE_FULL_SECONDARY,
                UserManagerHelper.FLAG_EPHEMERAL, expectedeUser);
        ArgumentCaptor<CreateUserRequest> requestCaptor =
                mockHalCreateUser(HalCallback.STATUS_OK, CreateUserStatus.SUCCESS);

        createUser(nullName, UserManager.USER_TYPE_FULL_SECONDARY, UserManagerHelper.FLAG_EPHEMERAL,
                mAsyncCallTimeoutMs, mUserCreationFuture, NO_CALLER_RESTRICTIONS);

        // Assert request
        CreateUserRequest request = requestCaptor.getValue();
        Log.d(TAG, "createUser() request: " + request);
        assertThat(request.newUserName).isEmpty();
        assertThat(request.newUserInfo.userId).isEqualTo(userId);
        assertThat(request.newUserInfo.flags).isEqualTo(
                android.hardware.automotive.vehicle.UserInfo.USER_FLAG_EPHEMERAL);
        assertDefaultUsersInfo(request.usersInfo, mAdminUser);

        UserCreationResult result = getUserCreationResult();
        assertThat(result.getStatus()).isEqualTo(UserCreationResult.STATUS_SUCCESSFUL);
        assertThat(result.getAndroidFailureStatus()).isNull();
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getInternalErrorMessage()).isNull();

        UserHandle newUser = result.getUser();
        assertThat(newUser).isNotNull();
        assertThat(newUser.getIdentifier()).isEqualTo(userId);

        verifyNoUserRemoved();
        assertNoHalUserRemoval();
    }

    @Test
    public void testCreateUser_binderMethod() {
        CarUserService spy = spy(mCarUserService);
        AndroidFuture<UserCreationResult> receiver = new AndroidFuture<>();
        int flags = 42;
        int timeoutMs = 108;

        spy.createUser("name", "type", flags, timeoutMs, receiver);

        verify(spy).createUser("name", "type", flags, timeoutMs, receiver,
                NO_CALLER_RESTRICTIONS);
    }

    @Test
    public void testCreateUserWithRestrictions_nonAdminCreatingAdmin() throws Exception {
        UserHandle currentUser = mRegularUser;
        mockExistingUsersAndCurrentUser(currentUser);
        mockGetCallingUserHandle(currentUser.getIdentifier());

        createUser("name", UserManager.USER_TYPE_FULL_SECONDARY, UserManagerHelper.FLAG_ADMIN,
                mAsyncCallTimeoutMs, mUserCreationFuture, HAS_CALLER_RESTRICTIONS);

        assertUserCreationInvalidArgumentsFailureWithInternalErrorMessage(mUserCreationFuture,
                CarUserService.ERROR_TEMPLATE_NON_ADMIN_CANNOT_CREATE_ADMIN_USERS,
                mRegularUser.getIdentifier());
    }

    @Test
    public void testCreateUserWithRestrictions_invalidTypes() throws Exception {
        createUserWithRestrictionsInvalidTypes(UserManager.USER_TYPE_FULL_DEMO);
        createUserWithRestrictionsInvalidTypes(UserManager.USER_TYPE_FULL_RESTRICTED);
        createUserWithRestrictionsInvalidTypes(UserManager.USER_TYPE_FULL_SYSTEM);
        createUserWithRestrictionsInvalidTypes(UserManager.USER_TYPE_PROFILE_MANAGED);
        createUserWithRestrictionsInvalidTypes(UserManager.USER_TYPE_SYSTEM_HEADLESS);
    }

    @Test
    public void testCreateUserWithRestrictions_invalidFlags() throws Exception {
        createUserWithRestrictionsInvalidTypes(UserManagerHelper.FLAG_DEMO);
        createUserWithRestrictionsInvalidTypes(UserManagerHelper.FLAG_DISABLED);
        createUserWithRestrictionsInvalidTypes(UserManagerHelper.FLAG_EPHEMERAL);
        createUserWithRestrictionsInvalidTypes(UserManagerHelper.FLAG_FULL);
        createUserWithRestrictionsInvalidTypes(UserManagerHelper.FLAG_INITIALIZED);
        createUserWithRestrictionsInvalidTypes(UserManagerHelper.FLAG_MANAGED_PROFILE);
        createUserWithRestrictionsInvalidTypes(UserManagerHelper.FLAG_PRIMARY);
        createUserWithRestrictionsInvalidTypes(UserManagerHelper.FLAG_QUIET_MODE);
        createUserWithRestrictionsInvalidTypes(UserManagerHelper.FLAG_RESTRICTED);
        createUserWithRestrictionsInvalidTypes(UserManagerHelper.FLAG_SYSTEM);
    }

    @Test
    @ExpectWtf
    public void testCreateUserEvenWhenDisallowed_noHelper() throws Exception {
        UserHandle userHandle = mCarUserService.createUserEvenWhenDisallowed("name",
                UserManager.USER_TYPE_FULL_SECONDARY, UserManagerHelper.FLAG_ADMIN);
        waitForHandlerThreadToFinish();

        assertThat(userHandle).isNull();
    }

    @Test
    public void testCreateUserEvenWhenDisallowed_remoteException() throws Exception {
        mCarUserService.setCarServiceHelper(mICarServiceHelper);
        when(mICarServiceHelper.createUserEvenWhenDisallowed(any(), any(), anyInt()))
                .thenThrow(new RemoteException("D'OH!"));

        UserHandle userHandle = mCarUserService.createUserEvenWhenDisallowed("name",
                UserManager.USER_TYPE_FULL_SECONDARY, UserManagerHelper.FLAG_ADMIN);
        waitForHandlerThreadToFinish();

        assertThat(userHandle).isNull();
    }

    @Test
    public void testCreateUserEvenWhenDisallowed_success() throws Exception {
        UserHandle user = UserHandle.of(100);
        mCarUserService.setCarServiceHelper(mICarServiceHelper);
        when(mICarServiceHelper.createUserEvenWhenDisallowed("name",
                UserManager.USER_TYPE_FULL_SECONDARY, UserManagerHelper.FLAG_ADMIN))
                        .thenReturn(user);

        UserHandle actualUser = mCarUserService.createUserEvenWhenDisallowed("name",
                UserManager.USER_TYPE_FULL_SECONDARY, UserManagerHelper.FLAG_ADMIN);
        waitForHandlerThreadToFinish();

        assertThat(actualUser).isNotNull();
        assertThat(actualUser.getIdentifier()).isEqualTo(100);
    }

    @Test
    public void testStartUserInBackground_success() throws Exception {
        int userId = 101;
        mockCurrentUser(mRegularUser);
        UserHandle newUser = expectRegularUserExists(mMockedUserHandleHelper, userId);
        mockAmStartUserInBackground(newUser.getIdentifier(), true);

        AndroidFuture<UserStartResult> userStartResult = new AndroidFuture<>();
        startUserInBackground(newUser.getIdentifier(), userStartResult);

        assertThat(getUserStartResult(userStartResult, userId).getStatus())
                .isEqualTo(UserStartResult.STATUS_SUCCESSFUL);
        assertThat(getUserStartResult(userStartResult, userId).isSuccess()).isTrue();
    }

    @Test
    public void testStartUserInBackground_permissionDenied() throws Exception {
        int userId = 101;
        mockManageUsersPermission(android.Manifest.permission.MANAGE_USERS, false);
        mockManageUsersPermission(android.Manifest.permission.CREATE_USERS, false);

        AndroidFuture<UserStartResult> userStartResult = new AndroidFuture<>();
        assertThrows(SecurityException.class,
                () -> startUserInBackground(userId, userStartResult));
    }

    @Test
    public void testStartUserInBackground_fail() throws Exception {
        int userId = 101;
        UserHandle user = expectRegularUserExists(mMockedUserHandleHelper, userId);
        mockCurrentUser(mRegularUser);
        mockAmStartUserInBackground(userId, false);

        AndroidFuture<UserStartResult> userStartResult = new AndroidFuture<>();
        startUserInBackground(userId, userStartResult);

        assertThat(getUserStartResult(userStartResult, userId).getStatus())
                .isEqualTo(UserStartResult.STATUS_ANDROID_FAILURE);
        assertThat(getUserStartResult(userStartResult, userId).isSuccess()).isFalse();
    }

    @Test
    public void testStartUserInBackground_currentUser() throws Exception {
        int userId = 101;
        UserHandle newUser = expectRegularUserExists(mMockedUserHandleHelper, userId);
        mockGetCurrentUser(newUser.getIdentifier());
        mockAmStartUserInBackground(newUser.getIdentifier(), true);

        AndroidFuture<UserStartResult> userStartResult = new AndroidFuture<>();
        startUserInBackground(newUser.getIdentifier(), userStartResult);

        assertThat(getUserStartResult(userStartResult, userId).getStatus())
                .isEqualTo(UserStartResult.STATUS_SUCCESSFUL_USER_IS_CURRENT_USER);
        assertThat(getUserStartResult(userStartResult, userId).isSuccess()).isTrue();
    }

    @Test
    public void testStartUserInBackground_userDoesNotExist() throws Exception {
        int userId = 101;
        mockCurrentUser(mRegularUser);
        mockAmStartUserInBackground(userId, true);

        AndroidFuture<UserStartResult> userStartResult = new AndroidFuture<>();
        startUserInBackground(userId, userStartResult);

        assertThat(getUserStartResult(userStartResult, userId).getStatus())
                .isEqualTo(UserStartResult.STATUS_USER_DOES_NOT_EXIST);
        assertThat(getUserStartResult(userStartResult, userId).isSuccess()).isFalse();
    }

    @Test
    public void testIsHalSupported() throws Exception {
        when(mUserHal.isSupported()).thenReturn(true);
        assertThat(mCarUserService.isUserHalSupported()).isTrue();
    }

    @Test
    public void testGetUserIdentificationAssociation_nullTypes() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mCarUserService.getUserIdentificationAssociation(null));
    }

    @Test
    public void testGetUserIdentificationAssociation_emptyTypes() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> mCarUserService.getUserIdentificationAssociation(new int[] {}));
    }

    @Test
    public void testGetUserIdentificationAssociation_noPermission() throws Exception {
        mockManageUsersPermission(android.Manifest.permission.MANAGE_USERS, false);
        assertThrows(SecurityException.class,
                () -> mCarUserService.getUserIdentificationAssociation(new int[] { 42 }));
    }

    @Test
    public void testGetUserIdentificationAssociation_noSuchUser() throws Exception {
        // Should fail because we're not mocking UserManager.getUserInfo() to set the flag
        assertThrows(IllegalArgumentException.class,
                () -> mCarUserService.getUserIdentificationAssociation(new int[] { 42 }));
    }

    @Test
    public void testGetUserIdentificationAssociation_service_returnNull() throws Exception {
        mockCurrentUserForBinderCalls();

        UserIdentificationAssociationResponse response = mCarUserService
                .getUserIdentificationAssociation(new int[] { 108 });

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getValues()).isNull();
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    public void testGetUserIdentificationAssociation_halNotSupported() throws Exception {
        mockUserHalUserAssociationSupported(false);

        UserIdentificationAssociationResponse response = mCarUserService
                .getUserIdentificationAssociation(new int[] { });

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getValues()).isNull();
        assertThat(response.getErrorMessage()).isEqualTo(CarUserService.VEHICLE_HAL_NOT_SUPPORTED);
        verify(mUserHal, never()).getUserAssociation(any());
    }

    @Test
    public void testGetUserIdentificationAssociation_ok() throws Exception {
        UserHandle currentUser = mockCurrentUserForBinderCalls();

        int[] types = new int[] { 1, 2, 3 };
        int[] values = new int[] { 10, 20, 30 };
        mockHalGetUserIdentificationAssociation(currentUser, types, values, "D'OH!");

        UserIdentificationAssociationResponse response = mCarUserService
                .getUserIdentificationAssociation(types);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getValues()).asList().containsAtLeast(10, 20, 30).inOrder();
        assertThat(response.getErrorMessage()).isEqualTo("D'OH!");
    }

    @Test
    public void testSetUserIdentificationAssociation_nullTypes() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> mCarUserService
                .setUserIdentificationAssociation(mAsyncCallTimeoutMs,
                        null, new int[] {42}, mUserAssociationRespFuture));
    }

    @Test
    public void testSetUserIdentificationAssociation_emptyTypes() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> mCarUserService
                .setUserIdentificationAssociation(mAsyncCallTimeoutMs,
                        new int[0], new int[] {42}, mUserAssociationRespFuture));
    }

    @Test
    public void testSetUserIdentificationAssociation_nullValues() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> mCarUserService
                .setUserIdentificationAssociation(mAsyncCallTimeoutMs,
                        new int[] {42}, null, mUserAssociationRespFuture));
    }
    @Test
    public void testSetUserIdentificationAssociation_sizeMismatch() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> mCarUserService
                .setUserIdentificationAssociation(mAsyncCallTimeoutMs,
                        new int[] {1}, new int[] {2, 2}, mUserAssociationRespFuture));
    }

    @Test
    public void testSetUserIdentificationAssociation_nullFuture() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> mCarUserService
                .setUserIdentificationAssociation(mAsyncCallTimeoutMs,
                        new int[] {42}, new int[] {42}, null));
    }

    @Test
    public void testSetUserIdentificationAssociation_noPermission() throws Exception {
        mockManageUsersPermission(android.Manifest.permission.MANAGE_USERS, false);
        assertThrows(SecurityException.class, () -> mCarUserService
                .setUserIdentificationAssociation(mAsyncCallTimeoutMs,
                        new int[] {42}, new int[] {42}, mUserAssociationRespFuture));
    }

    @Test
    public void testSetUserIdentificationAssociation_noCurrentUser() throws Exception {
        // Should fail because we're not mocking UserManager.getUserInfo() to set the flag
        assertThrows(IllegalArgumentException.class, () -> mCarUserService
                .setUserIdentificationAssociation(mAsyncCallTimeoutMs,
                        new int[] {42}, new int[] {42}, mUserAssociationRespFuture));
    }

    @Test
    public void testSetUserIdentificationAssociation_halNotSupported() throws Exception {
        int[] types = new int[] { 1, 2, 3 };
        int[] values = new int[] { 10, 20, 30 };
        mockUserHalUserAssociationSupported(false);

        mCarUserService.setUserIdentificationAssociation(mAsyncCallTimeoutMs, types, values,
                mUserAssociationRespFuture);
        UserIdentificationAssociationResponse response = getUserAssociationRespResult();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getValues()).isNull();
        assertThat(response.getErrorMessage()).isEqualTo(CarUserService.VEHICLE_HAL_NOT_SUPPORTED);
        verify(mUserHal, never()).setUserAssociation(anyInt(), any(), any());
    }

    @Test
    public void testSetUserIdentificationAssociation_halFailedWithErrorMessage() throws Exception {
        mockCurrentUserForBinderCalls();
        mockHalSetUserIdentificationAssociationFailure("D'OH!");
        int[] types = new int[] { 1, 2, 3 };
        int[] values = new int[] { 10, 20, 30 };
        mCarUserService.setUserIdentificationAssociation(mAsyncCallTimeoutMs, types, values,
                mUserAssociationRespFuture);

        UserIdentificationAssociationResponse response = getUserAssociationRespResult();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getValues()).isNull();
        assertThat(response.getErrorMessage()).isEqualTo("D'OH!");

    }

    @Test
    public void testSetUserIdentificationAssociation_halFailedWithoutErrorMessage()
            throws Exception {
        mockCurrentUserForBinderCalls();
        mockHalSetUserIdentificationAssociationFailure(/* errorMessage= */ null);
        int[] types = new int[] { 1, 2, 3 };
        int[] values = new int[] { 10, 20, 30 };
        mCarUserService.setUserIdentificationAssociation(mAsyncCallTimeoutMs, types, values,
                mUserAssociationRespFuture);

        UserIdentificationAssociationResponse response = getUserAssociationRespResult();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getValues()).isNull();
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    public void testSetUserIdentificationAssociation_ok() throws Exception {
        UserHandle currentUser = mockCurrentUserForBinderCalls();

        int[] types = new int[] { 1, 2, 3 };
        int[] values = new int[] { 10, 20, 30 };
        mockHalSetUserIdentificationAssociationSuccess(currentUser, types, values, "D'OH!");

        mCarUserService.setUserIdentificationAssociation(mAsyncCallTimeoutMs, types, values,
                mUserAssociationRespFuture);

        UserIdentificationAssociationResponse response = getUserAssociationRespResult();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getValues()).asList().containsAtLeast(10, 20, 30).inOrder();
        assertThat(response.getErrorMessage()).isEqualTo("D'OH!");
    }

    @Test
    public void testInitBootUser_halNotSupported() {
        mockUserHalSupported(false);

        mCarUserService.initBootUser();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter).set(argThat((info) -> {
            return info.type == InitialUserSetter.TYPE_DEFAULT_BEHAVIOR
                    && info.userLocales == null && info.supportsOverrideUserIdProperty;
        }));
    }

    @Test
    public void testInitBootUser_halNullResponse() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mockHalGetInitialInfo(mAdminUserId, null);

        mCarUserService.initBootUser();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter).set(argThat((info) -> {
            return info.type == InitialUserSetter.TYPE_DEFAULT_BEHAVIOR;
        }));
    }

    @Test
    public void testInitBootUser_halDefaultResponse() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mGetUserInfoResponse.action = InitialUserInfoResponseAction.DEFAULT;
        mGetUserInfoResponse.userLocales = "LOL";
        mockHalGetInitialInfo(mAdminUserId, mGetUserInfoResponse);

        mCarUserService.initBootUser();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter).set(argThat((info) -> {
            return info.type == InitialUserSetter.TYPE_DEFAULT_BEHAVIOR
                    && info.userLocales.equals("LOL");
        }));
    }

    @Test
    public void testInitBootUser_halSwitchResponse() throws Exception {
        int switchUserId = mGuestUserId;
        mockExistingUsersAndCurrentUser(mAdminUser);
        mGetUserInfoResponse.action = InitialUserInfoResponseAction.SWITCH;
        mGetUserInfoResponse.userToSwitchOrCreate.userId = switchUserId;
        mockHalGetInitialInfo(mAdminUserId, mGetUserInfoResponse);

        mCarUserService.initBootUser();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter).set(argThat((info) -> {
            return info.type == InitialUserSetter.TYPE_SWITCH
                    && info.switchUserId == switchUserId;
        }));
    }

    @Test
    public void testInitBootUser_halCreateResponse() throws Exception {
        int newUserFlags = 42;
        String newUserName = "TheDude";
        mockExistingUsersAndCurrentUser(mAdminUser);
        mGetUserInfoResponse.action = InitialUserInfoResponseAction.CREATE;
        mGetUserInfoResponse.userToSwitchOrCreate.flags = newUserFlags;
        mGetUserInfoResponse.userNameToCreate = newUserName;
        mockHalGetInitialInfo(mAdminUserId, mGetUserInfoResponse);

        mCarUserService.initBootUser();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter).set(argThat((info) -> {
            return info.type == InitialUserSetter.TYPE_CREATE
                    && info.newUserFlags == newUserFlags
                    && info.newUserName == newUserName;
        }));
    }

    @Test
    public void testInitBootUser_preCreateUser() throws Exception {
        mockUserPreCreationStage(PRE_CREATION_STAGE_ON_SYSTEM_START);

        CarUserService service = newCarUserService(/* switchGuestUserBeforeGoingSleep= */ false);

        service.initBootUser();
        waitForHandlerThreadToFinish();

        verify(mUserPreCreator).managePreCreatedUsers();
    }

    @Test
    public void testInitBootUser_noPreCreateUser() throws Exception {
        mCarUserService.initBootUser();
        waitForHandlerThreadToFinish();

        verify(mUserPreCreator, never()).managePreCreatedUsers();
    }

    @Test
    public void testUpdatePreCreatedUser_success() throws Exception {
        mCarUserService.updatePreCreatedUsers();
        waitForHandlerThreadToFinish();

        verify(mUserPreCreator).managePreCreatedUsers();
    }

    @Test
    public void testOnSuspend_replace() throws Exception {
        mockExistingUsersAndCurrentUser(mGuestUser);
        when(mInitialUserSetter.canReplaceGuestUser(any())).thenReturn(true);
        mockUserPreCreationStage(PRE_CREATION_STAGE_BEFORE_SUSPEND);

        CarUserService service = newCarUserService(/* switchGuestUserBeforeGoingSleep= */ true);
        service.onSuspend();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter).set(argThat((info) -> {
            return info.type == InitialUserSetter.TYPE_REPLACE_GUEST;
        }));
        verify(mUserPreCreator).managePreCreatedUsers();
    }

    @Test
    public void testOnSuspend_notReplace() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mockUserPreCreationStage(PRE_CREATION_STAGE_BEFORE_SUSPEND);

        CarUserService service = newCarUserService(/* switchGuestUserBeforeGoingSleep= */ true);
        service.onSuspend();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter, never()).set(any());
        verify(mUserPreCreator).managePreCreatedUsers();
    }

    @Test
    public void testOnSuspend_preCreateUser() throws Exception {
        mockUserPreCreationStage(PRE_CREATION_STAGE_BEFORE_SUSPEND);

        CarUserService service = newCarUserService(/* switchGuestUserBeforeGoingSleep= */ false);

        service.onSuspend();
        waitForHandlerThreadToFinish();

        verify(mUserPreCreator).managePreCreatedUsers();
    }

    @Test
    public void testOnSuspend_noPreCreateUser() throws Exception {
        mCarUserService.onSuspend();
        waitForHandlerThreadToFinish();

        verify(mUserPreCreator, never()).managePreCreatedUsers();
    }

    @Test
    public void testOnResume_halNullResponse_replaceTrue() throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mockHalGetInitialInfo(mAdminUserId, null);

        mCarUserService.onResume();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter).set(argThat((info) -> {
            return info.type == InitialUserSetter.TYPE_DEFAULT_BEHAVIOR
                    && info.replaceGuest;
        }));
    }

    @Test
    public void testOnResume_halDefaultResponse_replaceGuest()
            throws Exception {
        mockExistingUsersAndCurrentUser(mAdminUser);
        mGetUserInfoResponse.action = InitialUserInfoResponseAction.DEFAULT;
        mGetUserInfoResponse.userLocales = "LOL";
        mockHalGetInitialInfo(mAdminUserId, mGetUserInfoResponse);

        mCarUserService.onResume();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter).set(argThat((info) -> {
            return info.type == InitialUserSetter.TYPE_DEFAULT_BEHAVIOR && info.replaceGuest
                    && info.userLocales.equals("LOL");
        }));
    }

    @Test
    public void testOnResume_halSwitchResponse_replaceGuest()
            throws Exception {
        int switchUserId = mGuestUserId;
        mockExistingUsersAndCurrentUser(mAdminUser);
        mGetUserInfoResponse.action = InitialUserInfoResponseAction.SWITCH;
        mGetUserInfoResponse.userToSwitchOrCreate.userId = switchUserId;
        mockHalGetInitialInfo(mAdminUserId, mGetUserInfoResponse);

        mCarUserService.onResume();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter).set(argThat((info) -> {
            return info.type == InitialUserSetter.TYPE_SWITCH && info.replaceGuest
                    && info.switchUserId == switchUserId;
        }));
    }

    @Test
    public void testOnResume_halDisabled()
            throws Exception {
        mockUserHalSupported(false);

        mCarUserService.onResume();
        waitForHandlerThreadToFinish();

        verify(mInitialUserSetter).set(argThat((info) -> {
            return info.type == InitialUserSetter.TYPE_DEFAULT_BEHAVIOR && info.replaceGuest;
        }));
    }

    @Test
    public void testInitialUserInfoRequestType_FirstBoot() throws Exception {
        when(mInitialUserSetter.hasInitialUser()).thenReturn(false);
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.isDeviceUpgrading()).thenReturn(true);

        assertThat(mCarUserService.getInitialUserInfoRequestType())
                .isEqualTo(InitialUserInfoRequestType.FIRST_BOOT);
    }

    @Test
    public void testInitialUserInfoRequestType_FirstBootAfterOTA() throws Exception {
        when(mInitialUserSetter.hasInitialUser()).thenReturn(true);
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.isDeviceUpgrading()).thenReturn(true);

        assertThat(mCarUserService.getInitialUserInfoRequestType())
                .isEqualTo(InitialUserInfoRequestType.FIRST_BOOT_AFTER_OTA);
    }

    @Test
    public void testInitialUserInfoRequestType_ColdBoot() throws Exception {
        when(mInitialUserSetter.hasInitialUser()).thenReturn(true);
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.isDeviceUpgrading()).thenReturn(false);

        assertThat(mCarUserService.getInitialUserInfoRequestType())
                .isEqualTo(InitialUserInfoRequestType.COLD_BOOT);
    }

    @Test
    public void testUserOpFlags() {
        userOpFlagTest(CarUserService.USER_OP_SUCCESS, ActivityManager.USER_OP_SUCCESS);
        userOpFlagTest(CarUserService.USER_OP_UNKNOWN_USER, ActivityManager.USER_OP_UNKNOWN_USER);
        userOpFlagTest(CarUserService.USER_OP_IS_CURRENT, ActivityManager.USER_OP_IS_CURRENT);
        userOpFlagTest(CarUserService.USER_OP_ERROR_IS_SYSTEM,
                ActivityManager.USER_OP_ERROR_IS_SYSTEM);
        userOpFlagTest(CarUserService.USER_OP_ERROR_RELATED_USERS_CANNOT_STOP,
                ActivityManager.USER_OP_ERROR_RELATED_USERS_CANNOT_STOP);
    }

    private void mockUserPreCreationStage(int stage) {
        when(mMockedResources
                .getInteger(com.android.car.R.integer.config_userPreCreationStage))
                        .thenReturn(stage);
    }

    private void assertUserSwitchResult(UserSwitchResult result, int expectedStatus) {
        assertUserSwitchResult(result.getStatus(), expectedStatus);
        assertNoErrorMessage(result);
        assertWithMessage("android failure status on %s", result)
                .that(result.getAndroidFailureStatus()).isNull();
    }

    private void assertUserSwitchResultForAndroidFailure(UserSwitchResult result,
            int expectedAndroidFailure) {
        assertUserSwitchResult(result.getStatus(), UserSwitchResult.STATUS_ANDROID_FAILURE);
        assertNoErrorMessage(result);
        Integer actualAndroidFailure = result.getAndroidFailureStatus();
        assertWithMessage("android failure status on %s", result).that(actualAndroidFailure)
                .isNotNull();

        assertWithMessage("android failure status (where %s=%s and %s=%s) on %s",
                expectedAndroidFailure, userOperationErrorToString(expectedAndroidFailure),
                actualAndroidFailure, userOperationErrorToString(actualAndroidFailure),
                result).that(actualAndroidFailure).isEqualTo(expectedAndroidFailure);
    }

    private void assertUserSwitchResultWithError(UserSwitchResult result, int expectedStatus,
            @Nullable String expectedErrorMessage) {
        assertUserSwitchResult(result.getStatus(), expectedStatus);
        assertWithMessage("error message on %s", result).that(result.getErrorMessage())
                .isEqualTo(expectedErrorMessage);
        assertWithMessage("android failure status on %s", result)
                .that(result.getAndroidFailureStatus()).isNull();
    }

    private void assertUserSwitchResult(int actual, int expected) {
        assertWithMessage("user switch result (where %s=%s and %s=%s)",
                expected, userSwitchResultToString(expected),
                actual, userSwitchResultToString(actual))
                        .that(actual).isEqualTo(expected);
    }

    private void assertNoErrorMessage(UserSwitchResult result) {
        String errorMessage = result.getErrorMessage();
        if (errorMessage != null) {
            assertWithMessage("error message on %s", result).that(result.getErrorMessage())
                    .isEmpty();
        }
    }

    protected void userOpFlagTest(int carConstant, int amConstant) {
        assertWithMessage("Constant %s",
                DebugUtils.constantToString(CarUserService.class, "USER_OP_", carConstant))
                .that(carConstant).isEqualTo(amConstant);
    }

    private void logoutUser(int timeoutMs, AndroidFuture<UserSwitchResult> receiver) {
        mCarUserService.logoutUser(timeoutMs, receiver);
        waitForHandlerThreadToFinish();
    }

    private static String userSwitchResultToString(int result) {
        return DebugUtils.constantToString(UserSwitchResult.class, "STATUS_", result);
    }

    private static String userOperationErrorToString(int error) {
        return DebugUtils.constantToString(UserManager.class, "USER_OPERATION_", error);
    }

    private UserStartResult getUserStartResult(AndroidFuture<UserStartResult> future, int userId) {
        return getResult(future, "starting user %d", userId);
    }

    private UserStopResult getUserStopResult(AndroidFuture<UserStopResult> future, int userId) {
        return getResult(future, "stopping user %d", userId);
    }
}
