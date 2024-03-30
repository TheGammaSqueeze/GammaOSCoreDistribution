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

package com.android.car.garagemode;

import static com.android.car.garagemode.GarageMode.ACTION_GARAGE_MODE_OFF;
import static com.android.car.garagemode.GarageMode.ACTION_GARAGE_MODE_ON;
import static com.android.car.garagemode.GarageMode.JOB_SNAPSHOT_INITIAL_UPDATE_MS;
import static com.android.car.power.CarPowerManagementService.INVALID_TIMEOUT;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.Car;
import android.car.hardware.power.CarPowerManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.car.CarLocalServices;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.user.CarUserService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class ControllerTest {

    private static final String TAG = "ControllerTest";
    @Rule public final MockitoRule rule = MockitoJUnit.rule();

    @Mock private Context mContextMock;
    @Mock private Looper mLooperMock;
    @Mock private Handler mHandlerMock;
    @Mock private Car mCarMock;
    @Mock private CarUserService mCarUserServiceMock;
    @Mock private SystemInterface mSystemInterfaceMock;
    @Mock private CarPowerManagementService mCarPowerManagementServiceMock;
    private CarUserService mCarUserServiceOriginal;
    private SystemInterface mSystemInterfaceOriginal;
    private CarPowerManagementService mCarPowerManagementServiceOriginal;
    @Captor private ArgumentCaptor<Intent> mIntentCaptor;
    @Captor private ArgumentCaptor<Integer> mIntegerCaptor;

    private Controller mController;
    private File mTempTestDir;

    @Before
    public void setUp() throws IOException {
        mCarUserServiceOriginal = CarLocalServices.getService(CarUserService.class);
        mCarPowerManagementServiceOriginal = CarLocalServices.getService(
                CarPowerManagementService.class);
        CarLocalServices.removeServiceForTest(CarUserService.class);
        CarLocalServices.addService(CarUserService.class, mCarUserServiceMock);
        CarLocalServices.removeServiceForTest(SystemInterface.class);
        CarLocalServices.addService(SystemInterface.class, mSystemInterfaceMock);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class,
                mCarPowerManagementServiceMock);

        mTempTestDir = Files.createTempDirectory("garagemode_test").toFile();
        when(mSystemInterfaceMock.getSystemCarDir()).thenReturn(mTempTestDir);
        Log.v(TAG, "Using temp dir: %s " + mTempTestDir.getAbsolutePath());

        mController = new Controller(mContextMock, mLooperMock, mHandlerMock,
                /* garageMode= */ null);

        doReturn(new ArrayList<Integer>()).when(mCarUserServiceMock)
                .startAllBackgroundUsersInGarageMode();
        doNothing().when(mSystemInterfaceMock)
                .sendBroadcastAsUser(any(Intent.class), any(UserHandle.class));
        mController.init();
    }

    @After
    public void tearDown() {
        CarLocalServices.removeServiceForTest(CarUserService.class);
        CarLocalServices.addService(CarUserService.class, mCarUserServiceOriginal);
        CarLocalServices.removeServiceForTest(SystemInterface.class);
        CarLocalServices.addService(SystemInterface.class, mSystemInterfaceOriginal);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class,
                mCarPowerManagementServiceOriginal);
    }

    @Test
    public void testOnShutdownPrepare_shouldInitiateGarageMode() {
        startAndAssertGarageModeWithSignal(CarPowerManager.STATE_SHUTDOWN_PREPARE);
        verify(mSystemInterfaceMock)
                .sendBroadcastAsUser(mIntentCaptor.capture(), eq(UserHandle.ALL));
        verifyGarageModeBroadcast(mIntentCaptor.getAllValues(), 1, ACTION_GARAGE_MODE_ON);
    }

    @Test
    public void testOnShutdownCancelled_shouldCancelGarageMode() {
        startAndAssertGarageModeWithSignal(CarPowerManager.STATE_SHUTDOWN_PREPARE);

        // Sending shutdown cancelled signal to controller, GarageMode should wrap up and stop
        mController.onStateChanged(CarPowerManager.STATE_SHUTDOWN_CANCELLED, INVALID_TIMEOUT);

        // Verify that GarageMode is not active anymore
        assertThat(mController.isGarageModeActive()).isFalse();

        // Verify that monitoring thread has stopped
        verify(mHandlerMock, Mockito.atLeastOnce()).removeCallbacks(any(Runnable.class));

        // Verify that OFF signal broadcasted to JobScheduler
        verify(mSystemInterfaceMock, times(2))
                .sendBroadcastAsUser(mIntentCaptor.capture(), eq(UserHandle.ALL));
        verifyGarageModeBroadcast(mIntentCaptor.getAllValues(), 1, ACTION_GARAGE_MODE_ON);
        verifyGarageModeBroadcast(mIntentCaptor.getAllValues(), 2, ACTION_GARAGE_MODE_OFF);

        // Verify that listener is completed due to the cancellation.
        verify(mCarPowerManagementServiceMock).completeHandlingPowerStateChange(
                eq(CarPowerManager.STATE_SHUTDOWN_PREPARE), eq(mController));
    }

    @Test
    public void testInitAndRelease() {
        Executor mockExecutor = mock(Executor.class);
        when(mContextMock.getMainExecutor()).thenReturn(mockExecutor);
        GarageMode garageMode = mock(GarageMode.class);
        Controller controller = new Controller(mContextMock, mLooperMock, mHandlerMock, garageMode);

        controller.init();
        controller.release();

        verify(garageMode).init();
        verify(garageMode).release();
    }

    @Test
    public void testConstructor() {
        Resources resourcesMock = mock(Resources.class);
        when(mContextMock.getResources()).thenReturn(resourcesMock);

        Controller controller = new Controller(mContextMock, mLooperMock);

        assertThat(controller).isNotNull();
    }

    @Test
    public void testOnStateChanged() {
        GarageMode garageMode = mock(GarageMode.class);
        Controller controller = spy(new Controller(mContextMock, mLooperMock, mHandlerMock,
                garageMode));
        controller.init();

        controller.onStateChanged(CarPowerManager.STATE_SHUTDOWN_CANCELLED, INVALID_TIMEOUT);
        verify(controller).resetGarageMode();

        clearInvocations(controller);
        controller.onStateChanged(CarPowerManager.STATE_SHUTDOWN_ENTER, INVALID_TIMEOUT);
        verify(controller).resetGarageMode();

        clearInvocations(controller);
        controller.onStateChanged(CarPowerManager.STATE_SUSPEND_ENTER, INVALID_TIMEOUT);
        verify(controller).resetGarageMode();

        clearInvocations(controller);
        controller.onStateChanged(CarPowerManager.STATE_HIBERNATION_ENTER, INVALID_TIMEOUT);
        verify(controller).resetGarageMode();

        clearInvocations(controller);
        controller.onStateChanged(CarPowerManager.STATE_INVALID , INVALID_TIMEOUT);
        verify(controller, never()).resetGarageMode();
    }

    private void verifyGarageModeBroadcast(List<Intent> intents, int times, String action) {
        // Capture sent intent and verify that it is correct
        assertWithMessage("no of intents").that(intents.size()).isAtLeast(times);
        Intent i = intents.get(times - 1);
        assertWithMessage("intent action on %s", i).that(i.getAction())
                .isEqualTo(action);

        // Verify that additional critical flags are bundled as well
        int flags = Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_NO_ABORT;
        boolean areRequiredFlagsSet = ((flags & i.getFlags()) == flags);
        assertThat(areRequiredFlagsSet).isTrue();
    }

    private void startAndAssertGarageModeWithSignal(int signal) {
        // Sending notification that state has changed
        mController.onStateChanged(signal, INVALID_TIMEOUT);

        // Assert that GarageMode has been started
        assertThat(mController.isGarageModeActive()).isTrue();

        // Verify that worker that polls running jobs from JobScheduler is scheduled.
        verify(mHandlerMock).postDelayed(any(), eq(JOB_SNAPSHOT_INITIAL_UPDATE_MS));
    }
}
