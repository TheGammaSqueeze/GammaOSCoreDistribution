/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.power;

import static android.net.ConnectivityManager.TETHERING_WIFI;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.car.Car;
import android.car.ICarResultReceiver;
import android.car.builtin.app.VoiceInteractionHelper;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPowerPolicyListener;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.hardware.power.PowerComponent;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification;
import android.frameworks.automotive.powerpolicy.internal.PolicyState;
import android.hardware.automotive.vehicle.VehicleApPowerStateReq;
import android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam;
import android.net.TetheringManager;
import android.net.wifi.WifiManager;
import android.os.UserManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.AtomicFile;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseBooleanArray;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.hal.MockedPowerHalService;
import com.android.car.hal.PowerHalService;
import com.android.car.hal.PowerHalService.PowerState;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.systeminterface.DisplayInterface;
import com.android.car.systeminterface.IOInterface;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.systeminterface.SystemStateInterface;
import com.android.car.systeminterface.WakeLockInterface;
import com.android.car.test.utils.TemporaryDirectory;
import com.android.car.test.utils.TemporaryFile;
import com.android.car.user.CarUserService;
import com.android.compatibility.common.util.PollingCheck;
import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SmallTest
public final class CarPowerManagementServiceUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CarPowerManagementServiceUnitTest.class.getSimpleName();
    private static final long WAIT_TIMEOUT_MS = 2000;
    private static final long WAIT_TIMEOUT_LONG_MS = 5000;
    private static final int WAKE_UP_DELAY = 100;
    private static final String NONSILENT_STRING = "0";
    private static final String NORMAL_BOOT = "reboot,shell";

    private static final int CURRENT_USER_ID = 42;
    private static final int CURRENT_GUEST_ID = 108; // must be different than CURRENT_USER_ID;
    private static final int NEW_GUEST_ID = 666;
    public static final String SYSTEM_POWER_POLICY_ALL_ON = "system_power_policy_all_on";
    public static final String SYSTEM_POWER_POLICY_NO_USER_INTERACTION =
            "system_power_policy_no_user_interaction";
    public static final String SYSTEM_POWER_POLICY_INITIAL_ON = "system_power_policy_initial_on";

    private final MockDisplayInterface mDisplayInterface = new MockDisplayInterface();
    private final MockSystemStateInterface mSystemStateInterface = new MockSystemStateInterface();
    private final MockWakeLockInterface mWakeLockInterface = new MockWakeLockInterface();
    private final MockIOInterface mIOInterface = new MockIOInterface();
    private final PowerSignalListener mPowerSignalListener = new PowerSignalListener();
    @Spy
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final TemporaryFile mComponentStateFile;

    private MockedPowerHalService mPowerHal;
    private SystemInterface mSystemInterface;
    private PowerComponentHandler mPowerComponentHandler;
    private CarPowerManagementService mService;
    private CompletableFuture<Void> mFuture;
    private TemporaryFile mFileHwStateMonitoring;
    private TemporaryFile mFileKernelSilentMode;
    private FakeCarPowerPolicyDaemon mPowerPolicyDaemon;
    private boolean mVoiceInteractionEnabled;

    @Mock
    private UserManager mUserManager;
    @Mock
    private Resources mResources;
    @Mock
    private CarUserService mUserService;
    @Mock
    private WifiManager mWifiManager;
    @Mock
    private TetheringManager mTetheringManager;

    public CarPowerManagementServiceUnitTest() throws Exception {
        super(CarPowerManagementService.TAG);

        mComponentStateFile = new TemporaryFile("COMPONENT_STATE_FILE");
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session
            .spyStatic(ActivityManager.class)
            .spyStatic(VoiceInteractionHelper.class);
    }

    @Before
    public void setUp() throws Exception {
        mPowerHal = new MockedPowerHalService(/*isPowerStateSupported=*/true,
                /*isDeepSleepAllowed=*/true,
                /*isHibernationAllowed=*/true,
                /*isTimedWakeupAllowed=*/true);
        mSystemInterface = SystemInterface.Builder.defaultSystemInterface(mContext)
            .withDisplayInterface(mDisplayInterface)
            .withSystemStateInterface(mSystemStateInterface)
            .withWakeLockInterface(mWakeLockInterface)
            .withIOInterface(mIOInterface).build();

        setCurrentUser(CURRENT_USER_ID, /* isGuest= */ false);
        setService();
    }

    @After
    public void tearDown() throws Exception {
        if (mService != null) {
            mService.release();
        }
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarServiceUtils.finishAllHandlerTasks();
        mIOInterface.tearDown();
    }

    /**
     * Helper method to create mService and initialize a test case
     */
    private void setService() throws Exception {
        doReturn(mResources).when(mContext).getResources();
        // During the test, changing Wifi state according to a power policy takes long time, leading
        // to timeout. Also, we don't want to actually change Wifi state.
        doReturn(mWifiManager).when(mContext).getSystemService(WifiManager.class);
        doReturn(mTetheringManager).when(mContext).getSystemService(TetheringManager.class);
        when(mResources.getInteger(R.integer.maxGarageModeRunningDurationInSecs))
                .thenReturn(900);
        when(mResources.getInteger(R.integer.config_maxSuspendWaitDuration))
                .thenReturn(WAKE_UP_DELAY);
        doReturn(true).when(() -> VoiceInteractionHelper.isAvailable());
        doAnswer(invocation -> {
            mVoiceInteractionEnabled = (boolean) invocation.getArguments()[0];
            return null;
        }).when(() -> VoiceInteractionHelper.setEnabled(anyBoolean()));

        Log.i(TAG, "setService(): overridden overlay properties: "
                + ", maxGarageModeRunningDurationInSecs="
                + mResources.getInteger(R.integer.maxGarageModeRunningDurationInSecs));
        mFileHwStateMonitoring = new TemporaryFile("HW_STATE_MONITORING");
        mFileKernelSilentMode = new TemporaryFile("KERNEL_SILENT_MODE");
        mFileHwStateMonitoring.write(NONSILENT_STRING);
        mPowerPolicyDaemon = new FakeCarPowerPolicyDaemon();
        mPowerComponentHandler = new PowerComponentHandler(mContext, mSystemInterface,
                new AtomicFile(mComponentStateFile.getFile()));
        mService = new CarPowerManagementService(mContext, mResources, mPowerHal, mSystemInterface,
                mUserManager, mUserService, mPowerPolicyDaemon, mPowerComponentHandler,
                mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), NORMAL_BOOT);
        CarLocalServices.addService(CarPowerManagementService.class, mService);
        mService.init();
        mService.setShutdownTimersForTest(0, 0);
        mPowerHal.setSignalListener(mPowerSignalListener);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);
        assertStateReceived(MockedPowerHalService.SET_WAIT_FOR_VHAL, 0);
    }

    @Test
    public void testShutdown() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        assertThat(mService.garageModeShouldExitImmediately()).isFalse();
        mDisplayInterface.waitForDisplayOff(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testCanHibernate() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_HIBERNATION_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_HIBERNATION_EXIT);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_HIBERNATE));
        assertThat(mService.garageModeShouldExitImmediately()).isFalse();

        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_HIBERNATION_ENTRY);
        mPowerSignalListener.waitFor(PowerHalService.SET_HIBERNATION_ENTRY, WAIT_TIMEOUT_MS);
        verify(mUserService).onSuspend();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_HIBERNATION_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_HIBERNATION_EXIT, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testHibernateImmediately() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_HIBERNATION_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_HIBERNATION_EXIT);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY));

        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_HIBERNATION_ENTRY, 0);
        assertThat(mService.garageModeShouldExitImmediately()).isTrue();
        mPowerSignalListener.waitFor(PowerHalService.SET_HIBERNATION_ENTRY, WAIT_TIMEOUT_MS);
        verify(mUserService).onSuspend();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_HIBERNATION_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_HIBERNATION_EXIT, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownImmediately() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY));
        // Since modules have to manually schedule next wakeup, we should not schedule next wakeup
        // To test module behavior, we need to actually implement mock listener module.
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START, 0);
        assertThat(mService.garageModeShouldExitImmediately()).isTrue();
        mDisplayInterface.waitForDisplayOff(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testSuspend() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        // Request suspend
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        // Verify suspend
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        assertThat(mService.garageModeShouldExitImmediately()).isFalse();
    }

    @Test
    public void testShutdownOnSuspend() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        // Tell it to shutdown
        mService.requestShutdownOnNextSuspend();
        // Request suspend
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        // Verify shutdown
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
        // Cancel the shutdown
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_CANCELLED);

        // Request suspend again
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        // Verify suspend
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
    }

    @Test
    public void testShutdownCancel() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        // Start shutting down
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START, 0);
        // Cancel the shutdown
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_CANCELLED);
        // Go to suspend
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
    }

    @Test
    public void testSleepImmediately() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
        assertThat(mService.garageModeShouldExitImmediately()).isTrue();
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);

        // Send the finished signal from HAL to CPMS
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownWithProcessing() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE, 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testSleepEntryAndWakeup() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);
        // Send the finished signal from HAL to CPMS
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownPostponeAfterSuspend() throws Exception {
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        mDisplayInterface.waitForDisplayOff(WAIT_TIMEOUT_MS);
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);

        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);
        // Second processing after wakeup
        assertThat(mDisplayInterface.isDisplayEnabled()).isTrue();
        assertThat(mService.getCurrentPowerPolicy().getPolicyId())
                .isEqualTo(SYSTEM_POWER_POLICY_INITIAL_ON);

        mService.setStateForWakeUp();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mDisplayInterface.waitForDisplayOn(WAIT_TIMEOUT_MS);
        // Should wait until Handler has finished ON processing
        CarServiceUtils.runOnLooperSync(mService.getHandlerThread().getLooper(), () -> { });

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));

        // Should suspend within timeout
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);
    }

    /**
     * This test case tests the same scenario as {@link #testUserSwitchingOnResume_differentUser()},
     * but indirectly triggering {@code switchUserOnResumeIfNecessary()} through HAL events.
     */
    @Test
    public void testSleepEntryAndWakeUpForProcessing() throws Exception {
        mService.handleOn();
        // Speed up the polling for power state transitions
        mService.setShutdownTimersForTest(10, 40);

        suspendAndResume();
    }

    @Test
    public void testRegisterListenerWithCompletion() throws Exception {
        grantAdjustShutdownProcessPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        SparseBooleanArray stateMapToCompletion = new SparseBooleanArray();
        ICarPowerStateListener listenerRegistered = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                stateMapToCompletion.put(state, true);
                if (CarPowerManagementService.isCompletionAllowed(state)) {
                    mService.finished(state, this);
                }
            }
        };
        mService.registerListenerWithCompletion(listenerRegistered);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);

        assertWithMessage("WAIT_FOR_VHAL notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_WAIT_FOR_VHAL)).isFalse();
        assertWithMessage("ON notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_ON)).isTrue();
        assertWithMessage("PRE_SHUTDOWN_PREPARE notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE)).isTrue();
        assertWithMessage("SHUTDOWN_PREPARE notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_SHUTDOWN_PREPARE)).isTrue();
        assertWithMessage("SHUTDOWN_ENTER notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_SHUTDOWN_ENTER)).isTrue();
        assertWithMessage("POST_SHUTDOWN_ENTER notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_POST_SHUTDOWN_ENTER)).isTrue();
    }

    @Test
    public void testUnregisterListenerWithCompletion() throws Exception {
        grantAdjustShutdownProcessPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        ICarPowerStateListener listenerUnregistered = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                fail("No notification should be sent to unregistered listener");
            }
        };
        mService.registerListenerWithCompletion(listenerUnregistered);
        mService.unregisterListener(listenerUnregistered);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownPrepareWithCompletion_timeout() throws Exception {
        grantAdjustShutdownProcessPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        // Shortens the timeout for listen completion
        when(mResources.getInteger(R.integer.config_preShutdownPrepareTimeout))
                .thenReturn(10);
        mService.setShutdownTimersForTest(1000, 1000);
        ICarPowerStateListener listener = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                // Does nothing to make timeout occur
            }
        };
        mService.registerListenerWithCompletion(listener);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        // Power state should reach SHUTDOWN_ENTER because waiting for listeners to complete is done
        // after timeout.
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_LONG_MS);
    }

    @Test
    public void testFactoryResetOnResume() throws Exception {
        ICarResultReceiver callback = mock(ICarResultReceiver.class);
        mService.setFactoryResetCallback(callback);

        // TODO: shouldn't need to expose handleOn() but rather emulate the steps as it's done on
        // suspendAndResume(), but that method is making too many expectations that won't happen
        // it's factory reset
        mService.handleOn();

        // Arguments don't matter
        verify(callback).send(anyInt(), any());
    }

    @Test
    public void testDefinePowerPolicyFromCommand() throws Exception {
        String policyId = "policy_id_valid";
        int status = mService.definePowerPolicy(policyId, new String[]{"AUDIO", "BLUETOOTH"},
                new String[]{"WIFI"});
        assertThat(status).isEqualTo(PolicyOperationStatus.OK);
        assertThat(mPowerPolicyDaemon.getLastDefinedPolicyId()).isEqualTo(policyId);

        status = mService.definePowerPolicy(policyId, new String[]{"AUDIO", "BLUTTOOTH"},
                new String[]{"WIFI", "NFC"});
        assertThat(status).isEqualTo(PolicyOperationStatus.ERROR_DEFINE_POWER_POLICY);

        status = mService.definePowerPolicy(policyId, new String[]{"AUDIO", "INVALID_COMPONENT"},
                new String[]{"WIFI"});
        assertThat(status).isEqualTo(PolicyOperationStatus.ERROR_DEFINE_POWER_POLICY);
    }

    @Test
    public void testApplyPowerPolicy() throws Exception {
        grantPowerPolicyPermission();

        String policyId = "policy_id_audio_off";
        mService.definePowerPolicy(policyId, new String[]{}, new String[]{"AUDIO"});
        MockedPowerPolicyListener listenerToWait = new MockedPowerPolicyListener();
        CarPowerPolicyFilter filterAudio = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();
        mService.addPowerPolicyListener(filterAudio, listenerToWait);

        mService.applyPowerPolicy(policyId);

        waitForPowerPolicy(policyId);
        assertThat(mPowerComponentHandler.getAccumulatedPolicy().getPolicyId()).isEqualTo(policyId);
        PollingCheck.check("Current power policy of listener is null", WAIT_TIMEOUT_LONG_MS,
                () -> listenerToWait.getCurrentPowerPolicy() != null);
        assertThat(mPowerPolicyDaemon.getLastNotifiedPolicyId()).isEqualTo(policyId);
    }

    @Test
    public void testApplyInvalidPowerPolicy() {
        grantPowerPolicyPermission();
        // Power policy which doesn't exist.
        String policyId = "policy_id_not_available";

        assertThrows(IllegalArgumentException.class, () -> mService.applyPowerPolicy(policyId));
    }

    @Test
    public void testApplySystemPowerPolicyFromApps() {
        grantPowerPolicyPermission();
        String policyId = "system_power_policy_no_user_interaction";

        assertThrows(IllegalArgumentException.class, () -> mService.applyPowerPolicy(policyId));
    }

    @Test
    public void testDefinePowerPolicyGroupFromCommand() throws Exception {
        String policyIdOne = "policy_id_valid1";
        String policyIdTwo = "policy_id_valid2";
        mService.definePowerPolicy(policyIdOne, new String[0], new String[0]);
        mService.definePowerPolicy(policyIdTwo, new String[0], new String[0]);
        String policyGroupId = "policy_group_id_valid";
        String[] args = new String[]{"define-power-policy-group", policyGroupId,
                "WaitForVHAL:policy_id_valid1", "On:policy_id_valid2"};
        StringWriter stringWriter = new StringWriter();

        try (IndentingPrintWriter writer = new IndentingPrintWriter(stringWriter, "  ")) {
            boolean isSuccess = mService.definePowerPolicyGroupFromCommand(args, writer);

            assertThat(isSuccess).isTrue();

            args = new String[]{"set-power-policy-group", policyGroupId};
            isSuccess = mService.setPowerPolicyGroupFromCommand(args, writer);

            assertThat(isSuccess).isTrue();
        }
    }

    @Test
    public void testDefinePowerPolicyGroupFromCommand_noPolicyDefined() throws Exception {
        String policyGroupId = "policy_group_id_invalid";
        String[] args = new String[]{"define-power-policy-group", policyGroupId,
                "On:policy_id_not_exist"};
        StringWriter stringWriter = new StringWriter();

        try (IndentingPrintWriter writer = new IndentingPrintWriter(stringWriter, "  ")) {
            boolean isSuccess = mService.definePowerPolicyGroupFromCommand(args, writer);

            assertThat(isSuccess).isFalse();
        }
    }

    @Test
    public void testDefinePowerPolicyGroupFromCommand_invalidStateName() throws Exception {
        String policyId = "policy_id_valid";
        mService.definePowerPolicy(policyId, new String[0], new String[0]);
        String policyGroupId = "policy_group_id_invalid";
        String[] args = new String[]{"define-power-policy-group", policyGroupId,
                "InvalidStateName:policy_id_valid"};
        StringWriter stringWriter = new StringWriter();
        IndentingPrintWriter writer = new IndentingPrintWriter(stringWriter, "  ");

        boolean isSuccess = mService.definePowerPolicyGroupFromCommand(args, writer);

        assertThat(isSuccess).isFalse();
        writer.close();
    }

    @Test
    public void testAddPowerPolicyListener() throws Exception {
        grantPowerPolicyPermission();

        String policyIdEnableAudioWifi = "policy_id_enable_audio_wifi";
        MockedPowerPolicyListener listenerAudio = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerWifi = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerLocation = new MockedPowerPolicyListener();

        CarPowerPolicyFilter filterAudio = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();
        CarPowerPolicyFilter filterWifi = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.WIFI).build();
        CarPowerPolicyFilter filterLocation = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.LOCATION).build();

        mService.addPowerPolicyListener(filterAudio, listenerAudio);
        mService.addPowerPolicyListener(filterWifi, listenerWifi);

        String policyIdAllOff = "all_off";
        mService.definePowerPolicy(policyIdAllOff, new String[]{},
                new String[]{"AUDIO", "WIFI", "DISPLAY"});
        mService.applyPowerPolicy(policyIdAllOff);
        waitForPowerPolicy(policyIdAllOff);

        assertThat(mService.getCurrentPowerPolicy().isComponentEnabled(
                PowerComponent.AUDIO)).isFalse();
        assertThat(
                mService.getCurrentPowerPolicy().isComponentEnabled(PowerComponent.WIFI)).isFalse();
        assertThat(mService.getCurrentPowerPolicy().isComponentEnabled(
                PowerComponent.DISPLAY)).isFalse();

        mService.definePowerPolicy(policyIdEnableAudioWifi, new String[]{"AUDIO", "WIFI"},
                new String[]{});
        mService.applyPowerPolicy(policyIdEnableAudioWifi);

        waitForPolicyId(listenerAudio, policyIdEnableAudioWifi,
                "Current power policy of listenerAudio is not " + policyIdEnableAudioWifi);
        assertThat(
                mService.getCurrentPowerPolicy().isComponentEnabled(PowerComponent.AUDIO)).isTrue();

        waitForPolicyId(listenerWifi, policyIdEnableAudioWifi,
                "Current power policy of listenerWifi is not " + policyIdEnableAudioWifi);
        assertThat(listenerLocation.getCurrentPowerPolicy()).isNull();
    }

    @Test
    public void testRemovePowerPolicyListener() throws Exception {
        grantPowerPolicyPermission();

        String policyId = "policy_id_disable_audio";
        mService.definePowerPolicy(policyId, new String[]{}, new String[]{"AUDIO", "WIFI"});
        MockedPowerPolicyListener listenerAudio = new MockedPowerPolicyListener();
        MockedPowerPolicyListener referenceListenerAudio = new MockedPowerPolicyListener();

        CarPowerPolicyFilter filterAudio = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();

        mService.addPowerPolicyListener(filterAudio, listenerAudio);
        mService.addPowerPolicyListener(filterAudio, referenceListenerAudio);
        mService.removePowerPolicyListener(listenerAudio);
        mService.applyPowerPolicy(policyId);

        waitForPolicyId(referenceListenerAudio, policyId,
                "Current power policy of referenceListenerAudio is not " + policyId);
        assertThat(listenerAudio.getCurrentPowerPolicy()).isNull();
    }

    /**
     * This test case increases the code coverage to cover methods
     * {@code describeContents()} and {@code newArray()}. They are public APIs
     * can not be marked out as BOILERPLATE_CODE.
     */
    @Test
    public void testParcelableCreation() throws Exception {
        grantPowerPolicyPermission();

        CarPowerPolicy policy = mService.getCurrentPowerPolicy();
        assertThat(policy.describeContents()).isEqualTo(0);

        CarPowerPolicy[] policies = CarPowerPolicy.CREATOR.newArray(1);
        assertThat(policies.length).isEqualTo(1);

        CarPowerPolicyFilter filterAudio = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();
        assertThat(filterAudio.describeContents()).isEqualTo(0);

        CarPowerPolicyFilter[] filters = CarPowerPolicyFilter.CREATOR.newArray(1);
        assertThat(filters.length).isEqualTo(1);
    }


    @Test
    public void testPowerPolicyAfterShutdownCancel() throws Exception {
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        assertThat(mService.getCurrentPowerPolicy().getPolicyId())
                .isEqualTo(SYSTEM_POWER_POLICY_INITIAL_ON);
        mPowerHal.setCurrentPowerState(
                new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_CANCELLED);

        waitForPowerPolicy(SYSTEM_POWER_POLICY_INITIAL_ON);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_ON);

        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        waitForPowerPolicy(SYSTEM_POWER_POLICY_ALL_ON);
    }

    @Test
    public void testSuspendFailure() throws Exception {
        suspendWithFailure(/* nextPowerState= */ null);
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testSuspendFailureWithForbiddenTransition() throws Exception {
        suspendWithFailure(/* nextPowerState= */ VehicleApPowerStateReq.ON);
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testSuspendFailureWithAllowedTransition() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_CANCELLED);
        suspendWithFailure(/* nextPowerState= */ VehicleApPowerStateReq.CANCEL_SHUTDOWN);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_CANCELLED, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testPowerPolicyOnSilentBoot() throws Exception {
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mService.setSilentMode(SilentModeHandler.SILENT_MODE_FORCED_SILENT);

        assertThat(mService.getCurrentPowerPolicy().getPolicyId())
                .isEqualTo(SYSTEM_POWER_POLICY_NO_USER_INTERACTION);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_ON);

        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        assertThat(mService.getCurrentPowerPolicy().getPolicyId())
                .isEqualTo(SYSTEM_POWER_POLICY_NO_USER_INTERACTION);

        mService.setSilentMode(SilentModeHandler.SILENT_MODE_FORCED_NON_SILENT);

        assertThat(mService.getCurrentPowerPolicy().getPolicyId())
                .isEqualTo(SYSTEM_POWER_POLICY_ALL_ON);
    }

    @Test
    public void testDisableWifiAndTethering() throws Exception {
        grantPowerPolicyPermission();
        when(mResources.getBoolean(R.bool.config_wifiAdjustmentForSuspend))
                .thenReturn(true);
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.isWifiApEnabled()).thenReturn(true);
        mService = new CarPowerManagementService(mContext, mResources, mPowerHal, mSystemInterface,
                mUserManager, mUserService, mPowerPolicyDaemon, mPowerComponentHandler,
                mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), NORMAL_BOOT);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mService);
        mService.init();
        mService.setShutdownTimersForTest(/* pollingIntervalMs= */ 0, /* shutdownTimeoutMs= */ 0);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);

        suspendDevice();

        verify(mWifiManager, atLeastOnce()).setWifiEnabled(false);
        verify(mTetheringManager).stopTethering(TETHERING_WIFI);
    }

    private void suspendDevice() throws Exception {
        mService.handleOn();
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        mDisplayInterface.waitForDisplayOff(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
    }

    private void suspendWithFailure(Integer nextPowerState) throws Exception {
        mSystemStateInterface.setSleepEntryResult(false);
        mSystemStateInterface.setSimulateSleep(false);
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);

        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
        assertThat(mService.garageModeShouldExitImmediately()).isTrue();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));

        mSystemStateInterface.waitForDeepSleepEntry(WAIT_TIMEOUT_MS);

        if (nextPowerState != null) {
            mPowerHal.setCurrentPowerState(new PowerState(nextPowerState, 0));
        }
    }

    private void suspendAndResume() throws Exception {
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        Log.d(TAG, "suspend()");
        mVoiceInteractionEnabled = true;
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        mDisplayInterface.waitForDisplayOff(WAIT_TIMEOUT_MS);
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        assertVoiceInteractionDisabled();
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);
        verify(mUserService).onSuspend();

        // Send the finished signal
        Log.d(TAG, "resume()");
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);

        // second processing after wakeup
        assertThat(mService.getCurrentPowerPolicy().getPolicyId()).isEqualTo(
                SYSTEM_POWER_POLICY_INITIAL_ON);
        assertThat(mDisplayInterface.isDisplayEnabled()).isTrue();

        mFileHwStateMonitoring.write(NONSILENT_STRING); // Wake non-silently
        mService.setStateForWakeUp();
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        assertVoiceInteractionEnabled();

        mDisplayInterface.waitForDisplayOn(WAIT_TIMEOUT_MS);
        // Should wait until Handler has finished ON processing.
        CarServiceUtils.runOnLooperSync(mService.getHandlerThread().getLooper(), () -> { });

        verify(mUserService).onResume();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);

        verify(mUserService, times(2)).onSuspend();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        // PM will shutdown system as it was not woken-up due timer and it is not power on.
        mSystemStateInterface.setWakeupCausedByTimer(false);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        // Since we just woke up from shutdown, wake up time will be 0
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        assertThat(mDisplayInterface.isDisplayEnabled()).isTrue();
    }

    private void assertStateReceived(int expectedState, int expectedParam) throws Exception {
        int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_MS);
        assertThat(state[0]).isEqualTo(expectedState);
        assertThat(state[1]).isEqualTo(expectedParam);
    }

    private void assertStateReceivedForShutdownOrSleepWithPostpone(int lastState,
            int expectedSecondParameter)
            throws Exception {
        while (true) {
            if (mFuture != null && !mFuture.isDone()) {
                mFuture.complete(null);
            }
            int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_LONG_MS);
            if (state[0] == PowerHalService.SET_SHUTDOWN_POSTPONE) {
                continue;
            }
            if (state[0] == lastState) {
                assertThat(state[1]).isEqualTo(expectedSecondParameter);
                return;
            }
        }
    }

    private void assertStateReceivedForShutdownOrSleepWithPostpone(int lastState) throws Exception {
        int expectedSecondParameter =
                (lastState == MockedPowerHalService.SET_DEEP_SLEEP_ENTRY
                        || lastState == MockedPowerHalService.SET_SHUTDOWN_START
                        || lastState == MockedPowerHalService.SET_HIBERNATION_ENTRY)
                        ? WAKE_UP_DELAY : 0;
        assertStateReceivedForShutdownOrSleepWithPostpone(lastState, expectedSecondParameter);
    }

    private void assertVoiceInteractionEnabled() throws Exception {
        PollingCheck.check("Voice interaction is not enabled", WAIT_TIMEOUT_LONG_MS,
                () -> {
                    return mVoiceInteractionEnabled;
                });
    }

    private void assertVoiceInteractionDisabled() throws Exception {
        PollingCheck.check("Voice interaction is not disabled", WAIT_TIMEOUT_LONG_MS,
                () -> {
                    return !mVoiceInteractionEnabled;
                });
    }

    private void waitForPowerPolicy(String policyId) throws Exception {
        PollingCheck.check("Policy id is not " + policyId, WAIT_TIMEOUT_LONG_MS,
                () -> {
                    CarPowerPolicy policy = mService.getCurrentPowerPolicy();
                    return policy != null && policyId.equals(policy.getPolicyId());
                });
    }

    private static void waitForPolicyId(MockedPowerPolicyListener listener, String policyId,
            String errorMsg) throws Exception {
        PollingCheck.check(errorMsg, WAIT_TIMEOUT_LONG_MS,
                () -> {
                    CarPowerPolicy policy = listener.getCurrentPowerPolicy();
                    return policy != null && policyId.equals(policy.getPolicyId());
                });
    }

    private static void waitForSemaphore(Semaphore semaphore, long timeoutMs)
            throws InterruptedException {
        if (!semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("timeout");
        }
    }

    private UserInfo setCurrentUser(int userId, boolean isGuest) {
        mockGetCurrentUser(userId);
        final UserInfo userInfo = new UserInfo();
        userInfo.id = userId;
        userInfo.userType = isGuest
                ? UserManager.USER_TYPE_FULL_GUEST
                : UserManager.USER_TYPE_FULL_SECONDARY;
        Log.v(TAG, "UM.getUserInfo("  + userId + ") will return " + userInfo.toFullString());
        when(mUserManager.getUserInfo(userId)).thenReturn(userInfo);
        return userInfo;
    }

    private void grantPowerPolicyPermission() {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY);
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_READ_CAR_POWER_POLICY);
    }

    private void grantAdjustShutdownProcessPermission() {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_SHUTDOWN_PROCESS);
    }

    private static final class MockDisplayInterface implements DisplayInterface {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private boolean mDisplayOn = true;
        private final Semaphore mDisplayStateWait = new Semaphore(0);

        @Override
        public void init(CarPowerManagementService carPowerManagementService,
                CarUserService carUserService) {}

        @Override
        public void setDisplayBrightness(int brightness) {}

        @Override
        public void setDisplayState(boolean on) {
            synchronized (mLock) {
                mDisplayOn = on;
            }
            mDisplayStateWait.release();
        }

        private void waitForDisplayOn(long timeoutMs) throws Exception {
            waitForDisplayState(true, timeoutMs);
        }

        private void waitForDisplayOff(long timeoutMs) throws Exception {
            waitForDisplayState(false, timeoutMs);
        }

        private void waitForDisplayState(boolean desiredState, long timeoutMs) throws Exception {
            int nTries = 0;
            while (true) {
                synchronized (mLock) {
                    if (mDisplayOn == desiredState) {
                        break;
                    }
                }
                if (nTries > 5) throw new IllegalStateException("timeout");
                waitForSemaphore(mDisplayStateWait, timeoutMs);
                nTries++;
            }
        }

        @Override
        public void startDisplayStateMonitoring() {}

        @Override
        public void stopDisplayStateMonitoring() {}

        @Override
        public void refreshDisplayBrightness() {}

        @Override
        public boolean isDisplayEnabled() {
            synchronized (mLock) {
                return mDisplayOn;
            }
        }
    }

    private static final class MockSystemStateInterface implements SystemStateInterface {
        private final Semaphore mShutdownWait = new Semaphore(0);
        private final Semaphore mSleepWait = new Semaphore(0);
        private final Semaphore mSleepExitWait = new Semaphore(0);

        private boolean mSleepEntryResult = true;
        private boolean mSimulateSleep = true;

        private boolean mWakeupCausedByTimer = false;

        @Override
        public void shutdown() {
            mShutdownWait.release();
        }

        public void waitForShutdown(long timeoutMs) throws Exception {
            waitForSemaphore(mShutdownWait, timeoutMs);
        }

        @Override
        public boolean enterDeepSleep() {
            if (mSimulateSleep) {
                return simulateSleep();
            }

            mSleepWait.release();
            return mSleepEntryResult;
        }

        @Override
        public boolean enterHibernation() {
            return simulateSleep();
        }

        private boolean simulateSleep() {
            mSleepWait.release();
            try {
                mSleepExitWait.tryAcquire(WAIT_TIMEOUT_MS , TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
            return mSleepEntryResult;
        }

        public void waitForSleepEntryAndWakeup(long timeoutMs) throws Exception {
            waitForSemaphore(mSleepWait, timeoutMs);
            mSleepExitWait.release();
        }

        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay) {}

        @Override
        public boolean isWakeupCausedByTimer() {
            Log.i(TAG, "isWakeupCausedByTimer:" + mWakeupCausedByTimer);
            return mWakeupCausedByTimer;
        }

        public synchronized void setWakeupCausedByTimer(boolean set) {
            mWakeupCausedByTimer = set;
        }

        @Override
        public boolean isSystemSupportingDeepSleep() {
            return true;
        }

        @Override
        public boolean isSystemSupportingHibernation() {
            return true;
        }

        public void setSleepEntryResult(boolean sleepEntryResult) {
            mSleepEntryResult = sleepEntryResult;
        }

        public void setSimulateSleep(boolean simulateSleep) {
            mSimulateSleep = simulateSleep;
        }

        public void waitForDeepSleepEntry(long waitTimeoutMs) throws InterruptedException {
            waitForSemaphore(mSleepWait, waitTimeoutMs);
        }
    }

    private static final class MockWakeLockInterface implements WakeLockInterface {

        @Override
        public void releaseAllWakeLocks() {}

        @Override
        public void switchToPartialWakeLock() {}

        @Override
        public void switchToFullWakeLock() {}
    }

    private static final class MockIOInterface implements IOInterface {
        private TemporaryDirectory mFilesDir;

        @Override
        public File getSystemCarDir() {
            if (mFilesDir == null) {
                try {
                    mFilesDir = new TemporaryDirectory(TAG);
                } catch (IOException e) {
                    Log.e(TAG, "failed to create temporary directory", e);
                    fail("failed to create temporary directory. exception was: " + e);
                }
            }
            return mFilesDir.getDirectory();
        }

        public void tearDown() {
            if (mFilesDir != null) {
                try {
                    mFilesDir.close();
                } catch (Exception e) {
                    Log.w(TAG, "could not remove temporary directory", e);
                }
            }
        }
    }

    private class PowerSignalListener implements MockedPowerHalService.SignalListener {
        private final SparseArray<Semaphore> mSemaphores;

        private PowerSignalListener() {
            mSemaphores = new SparseArray<>();
        }

        public void addEventListener(int eventId) {
            mSemaphores.put(eventId, new Semaphore(0));
        }

        public void waitFor(int signal, long timeoutMs) throws Exception {
            Semaphore semaphore = mSemaphores.get(signal);
            if (semaphore == null) {
                throw new IllegalArgumentException("no semaphore registered for event = " + signal);
            }
            waitForSemaphore(semaphore, timeoutMs);
        }

        @Override
        public void sendingSignal(int signal) {
            Semaphore semaphore = mSemaphores.get(signal);
            if (semaphore == null) {
                return;
            }
            semaphore.release();
        }
    }

    static final class FakeCarPowerPolicyDaemon extends ICarPowerPolicySystemNotification.Stub {
        private String mLastNotifiedPolicyId;
        private String mLastDefinedPolicyId;

        @Override
        public PolicyState notifyCarServiceReady() {
            // do nothing
            return null;
        }

        @Override
        public void notifyPowerPolicyChange(String policyId, boolean force) {
            mLastNotifiedPolicyId = policyId;
        }

        @Override
        public void notifyPowerPolicyDefinition(String policyId, String[] enabledComponents,
                String[] disabledComponents) {
            mLastDefinedPolicyId = policyId;
        }

        public String getLastNotifiedPolicyId() {
            return mLastNotifiedPolicyId;
        }

        public String getLastDefinedPolicyId() {
            return mLastDefinedPolicyId;
        }

        @Override
        public String getInterfaceHash() {
            return ICarPowerPolicySystemNotification.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return ICarPowerPolicySystemNotification.VERSION;
        }
    }

    private final class MockedPowerPolicyListener extends ICarPowerPolicyListener.Stub {
        private final Object mLock = new Object();
        private CarPowerPolicy mCurrentPowerPolicy;

        @Override
        public void onPolicyChanged(CarPowerPolicy appliedPolicy,
                CarPowerPolicy accumulatedPolicy) {
            synchronized (mLock) {
                mCurrentPowerPolicy = accumulatedPolicy;
            }
        }

        @Nullable
        public CarPowerPolicy getCurrentPowerPolicy() throws Exception {
            synchronized (mLock) {
                return mCurrentPowerPolicy;
            }
        }
    }
}
