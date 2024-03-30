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

package com.android.internal.car.updatable;

import static com.android.car.internal.common.CommonConstants.CAR_SERVICE_INTERFACE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import android.car.ICar;
import android.car.builtin.os.UserManagerHelper;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.internal.car.CarServiceHelperInterface;
import com.android.server.wm.CarLaunchParamsModifierInterface;

import java.util.function.BiConsumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * This class contains unit tests for the {@link CarServiceHelperServiceUpdatableImpl}.
 */
@RunWith(AndroidJUnit4.class)
public final class CarServiceHelperServiceUpdatableImplTest
        extends AbstractExtendedMockitoTestCase {

    @Mock
    private Context mMockContext;
    @Mock
    private CarServiceProxy mCarServiceProxy;
    @Mock
    private CarServiceHelperInterface mCarServiceHelperInterface;
    @Mock
    private CarLaunchParamsModifierInterface mCarLaunchParamsModifierInterface;
    @Mock
    private ICar mICarBinder;
    @Mock
    private IBinder mIBinder;

    private CarServiceHelperServiceUpdatableImpl mCarServiceHelperServiceUpdatableImpl;

    public CarServiceHelperServiceUpdatableImplTest() {
        super(CarServiceHelperServiceUpdatableImpl.TAG);
    }

    @Before
    public void setTestFixtures() {
        mCarServiceHelperServiceUpdatableImpl = new CarServiceHelperServiceUpdatableImpl(
                mMockContext,
                mCarServiceHelperInterface,
                mCarLaunchParamsModifierInterface,
                mCarServiceProxy);
    }

    @Test
    public void testCarServiceLaunched() throws Exception {
        mockSystemContext();
        mockBindService();

        mCarServiceHelperServiceUpdatableImpl.onStart();

        verifyBindService();
    }

    @Test
    public void testHandleCarServiceConnection() throws Exception {
        mockICarBinder();

        mCarServiceHelperServiceUpdatableImpl.handleCarServiceConnection(mIBinder);

        verify(mICarBinder).setSystemServerConnections(any(), any());
    }

    @Test
    public void testHandleCarServiceCrash() throws Exception {
        mockICarBinder();
        doThrow(new RemoteException()).when(mICarBinder).setSystemServerConnections(any(), any());

        mCarServiceHelperServiceUpdatableImpl.handleCarServiceConnection(mIBinder);

        verify(mCarServiceHelperInterface).dumpServiceStacks();
    }

    @Test
    public void testOnUserRemoved() throws Exception {
        UserHandle user = UserHandle.of(101);
        mCarServiceHelperServiceUpdatableImpl.onUserRemoved(user);

        verify(mCarServiceProxy).onUserRemoved(user);
    }

    @Test
    public void testOnFactoryReset() throws Exception {
        BiConsumer<Integer, Bundle> callback = (x, y) -> {};
        mCarServiceHelperServiceUpdatableImpl.onFactoryReset(callback);

        verify(mCarServiceProxy).onFactoryReset(any());
    }

    @Test
    public void testInitBootUser() throws Exception {
        mCarServiceHelperServiceUpdatableImpl.initBootUser();

        verify(mCarServiceProxy).initBootUser();
    }

    @Test
    public void testSendUserLifecycleEvent_nullFromUser() throws Exception {
        int eventType = 1;
        UserHandle userFrom = null;
        UserHandle userTo = UserHandle.SYSTEM;

        mCarServiceHelperServiceUpdatableImpl.sendUserLifecycleEvent(eventType, userFrom, userTo);

        verify(mCarServiceProxy).sendUserLifecycleEvent(eventType, UserManagerHelper.USER_NULL,
                userTo.getIdentifier());
    }

    @Test
    public void testSendUserLifecycleEvent() throws Exception {
        int eventType = 1;
        UserHandle userFrom = UserHandle.SYSTEM;
        int userId = 101;
        UserHandle userTo = UserHandle.of(userId);

        mCarServiceHelperServiceUpdatableImpl.sendUserLifecycleEvent(eventType, userFrom, userTo);

        verify(mCarServiceProxy).sendUserLifecycleEvent(eventType, userFrom.getIdentifier(),
                userTo.getIdentifier());
    }

    private void mockICarBinder() {
        when(ICar.Stub.asInterface(mIBinder)).thenReturn(mICarBinder);
    }

    private void mockSystemContext() {
        when(mMockContext.createContextAsUser(UserHandle.SYSTEM, /* flags= */ 0))
                .thenReturn(mMockContext);
    }

    private void mockBindService() {
        when(mMockContext.bindService(any(), eq(Context.BIND_AUTO_CREATE), any(), any()))
                .thenReturn(true);
    }

    private void verifyBindService() throws Exception {
        verify(mMockContext).bindService(
                argThat(intent -> intent.getAction().equals(CAR_SERVICE_INTERFACE)),
                eq(Context.BIND_AUTO_CREATE), any(), any());
    }
}
