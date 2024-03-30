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

package com.android.car.am;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.car.Car;
import android.car.app.CarActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.os.UserHandle;

import com.android.car.internal.ICarServiceHelper;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CarActivityServiceUnitTest {

    // Comes from android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
    private static final int FEATURE_DEFAULT_TASK_CONTAINER = 1;

    private CarActivityService mCarActivityService;

    private final ComponentName mTestActivity = new ComponentName("test.pkg", "test.activity");

    @Rule
    public TestName mTestName = new TestName();
    @Mock
    private Context mContext;
    @Mock
    private ICarServiceHelper mICarServiceHelper;

    @Before
    public void setUp() {
        mCarActivityService = spy(new CarActivityService(mContext));

        int nonCurrentUserId = 9999990;
        boolean isNonCurrentUserTest = mTestName.getMethodName().contains("NonCurrentUser");
        int callerId = isNonCurrentUserTest ? nonCurrentUserId : UserHandle.USER_SYSTEM;
        when(mCarActivityService.getCaller()).thenReturn(callerId);
    }

    @Test
    public void setPersistentActivityThrowsException_ifICarServiceHelperIsNotSet() {
        assertThrows(IllegalStateException.class,
                () -> mCarActivityService.setPersistentActivity(
                        mTestActivity, DEFAULT_DISPLAY, FEATURE_DEFAULT_TASK_CONTAINER));
    }

    @Test
    public void setPersistentActivityThrowsException_withoutPermission() {
        mCarActivityService.setICarServiceHelper(mICarServiceHelper);
        when(mContext.checkCallingOrSelfPermission(eq(Car.PERMISSION_CONTROL_CAR_APP_LAUNCH)))
                .thenReturn(PackageManager.PERMISSION_DENIED);

        assertThrows(SecurityException.class,
                () -> mCarActivityService.setPersistentActivity(
                        mTestActivity, DEFAULT_DISPLAY, FEATURE_DEFAULT_TASK_CONTAINER));
    }

    @Test
    public void setPersistentActivityInvokesICarServiceHelper() throws RemoteException {
        int displayId = 9;

        mCarActivityService.setICarServiceHelper(mICarServiceHelper);

        int ret = mCarActivityService.setPersistentActivity(
                mTestActivity, displayId, FEATURE_DEFAULT_TASK_CONTAINER);
        assertThat(ret).isEqualTo(CarActivityManager.RESULT_SUCCESS);

        ArgumentCaptor<ComponentName> activityCaptor = ArgumentCaptor.forClass(ComponentName.class);
        ArgumentCaptor<Integer> displayIdCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> featureIdCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mICarServiceHelper).setPersistentActivity(
                activityCaptor.capture(), displayIdCaptor.capture(), featureIdCaptor.capture());

        assertThat(activityCaptor.getValue()).isEqualTo(mTestActivity);
        assertThat(displayIdCaptor.getValue()).isEqualTo(displayId);
        assertThat(featureIdCaptor.getValue()).isEqualTo(FEATURE_DEFAULT_TASK_CONTAINER);
    }

    @Test
    public void setPersistentActivityReturnsErrorForNonCurrentUser() throws RemoteException {
        mCarActivityService.setICarServiceHelper(mICarServiceHelper);

        int ret = mCarActivityService.setPersistentActivity(
                mTestActivity, DEFAULT_DISPLAY, FEATURE_DEFAULT_TASK_CONTAINER);
        assertThat(ret).isEqualTo(CarActivityManager.RESULT_INVALID_USER);
    }
}
