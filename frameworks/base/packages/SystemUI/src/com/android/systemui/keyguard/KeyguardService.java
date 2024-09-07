/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.systemui.keyguard;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_KEYGUARD_GOING_AWAY;

import android.app.ActivityTaskManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.Process;
import android.os.Debug;

import android.os.Trace;
import android.util.Log;
import android.util.Slog;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.IRemoteAnimationRunner;
import android.view.RemoteAnimationAdapter;
import android.view.RemoteAnimationDefinition;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.IRemoteTransition;
import android.window.RemoteTransition;
import android.window.TransitionFilter;
import android.window.TransitionInfo;

import com.android.internal.policy.IKeyguardDismissCallback;
import com.android.internal.policy.IKeyguardExitCallback;
import com.android.internal.policy.IKeyguardService;
import com.android.internal.policy.IKeyguardStateCallback;
import com.android.internal.policy.IKeyguardDrawnCallback;
import com.android.keyguard.mediator.ScreenOnCoordinator;
import com.android.systemui.SystemUIApplication;
import com.android.systemui.settings.DisplayTracker;
import com.android.wm.shell.transition.ShellTransitions;
import com.android.wm.shell.transition.Transitions;

import java.util.ArrayList;

import javax.inject.Inject;

public class KeyguardService extends Service {
    static final String TAG = "KeyguardService";
    static final String PERMISSION = android.Manifest.permission.CONTROL_KEYGUARD;
    public static boolean sEnableRemoteKeyguardGoingAwayAnimation = false;
    private final KeyguardViewMediator mKeyguardViewMediator;
    private final KeyguardLifecyclesDispatcher mKeyguardLifecyclesDispatcher;
    private final ScreenOnCoordinator mScreenOnCoordinator;
    private final ShellTransitions mShellTransitions;
    private final DisplayTracker mDisplayTracker;

    @Inject
    public KeyguardService(KeyguardViewMediator keyguardViewMediator,
                           KeyguardLifecyclesDispatcher keyguardLifecyclesDispatcher,
                           ScreenOnCoordinator screenOnCoordinator,
                           ShellTransitions shellTransitions,
                           DisplayTracker displayTracker) {
        super();
        mKeyguardViewMediator = keyguardViewMediator;
        mKeyguardLifecyclesDispatcher = keyguardLifecyclesDispatcher;
        mScreenOnCoordinator = screenOnCoordinator;
        mShellTransitions = shellTransitions;
        mDisplayTracker = displayTracker;
    }

    @Override
    public void onCreate() {
        ((SystemUIApplication) getApplication()).startServicesIfNeeded();
        return;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    void checkPermission() {
        if (Binder.getCallingUid() == Process.SYSTEM_UID) return;

        if (getBaseContext().checkCallingOrSelfPermission(PERMISSION) != PERMISSION_GRANTED) {
            Log.w(TAG, "Caller needs permission '" + PERMISSION + "' to call " + Debug.getCaller());
            throw new SecurityException("Access denied to process: " + Binder.getCallingPid()
                    + ", must have permission " + PERMISSION);
        }
    }

    private final IKeyguardService.Stub mBinder = new IKeyguardService.Stub() {

        @Override
        public void addStateMonitorCallback(IKeyguardStateCallback callback) {
            checkPermission();
            // Do nothing
        }

        @Override
        public void verifyUnlock(IKeyguardExitCallback callback) {
            // Prevent unlock verification
        }

        @Override
        public void setOccluded(boolean isOccluded, boolean animate) {
            Log.d(TAG, "setOccluded(" + isOccluded + ")");
            // Force Keyguard to always be occluded, preventing it from showing
            isOccluded = true;
            Trace.beginSection("KeyguardService.mBinder#setOccluded");
            checkPermission();
            mKeyguardViewMediator.setOccluded(isOccluded, animate);
            Trace.endSection();
        }

        @Override
        public void dismiss(IKeyguardDismissCallback callback, CharSequence message) {
            // No-op to prevent Keyguard dismissal
        }

        @Override
        public void onDreamingStarted() {
            checkPermission();
            // Do nothing to prevent Keyguard showing during dreaming
        }

        @Override
        public void onDreamingStopped() {
            checkPermission();
            // Do nothing
        }

        @Override
        public void onStartedGoingToSleep(@PowerManager.GoToSleepReason int pmSleepReason) {
            checkPermission();
            // Prevent Keyguard from triggering on sleep
            mKeyguardLifecyclesDispatcher.dispatch(
                    KeyguardLifecyclesDispatcher.STARTED_GOING_TO_SLEEP, pmSleepReason);
        }

        @Override
        public void onFinishedGoingToSleep(
                @PowerManager.GoToSleepReason int pmSleepReason, boolean cameraGestureTriggered) {
            checkPermission();
            // Prevent Keyguard lock on sleep
            mKeyguardLifecyclesDispatcher.dispatch(
                    KeyguardLifecyclesDispatcher.FINISHED_GOING_TO_SLEEP);
        }

        @Override
        public void onStartedWakingUp(@PowerManager.WakeReason int pmWakeReason, boolean cameraGestureTriggered) {
            Trace.beginSection("KeyguardService.mBinder#onStartedWakingUp");
            checkPermission();
            // Prevent Keyguard from showing on wakeup
            mKeyguardLifecyclesDispatcher.dispatch(
                    KeyguardLifecyclesDispatcher.STARTED_WAKING_UP, pmWakeReason);
            Trace.endSection();
        }

        @Override
        public void onFinishedWakingUp() {
            Trace.beginSection("KeyguardService.mBinder#onFinishedWakingUp");
            checkPermission();
            mKeyguardLifecyclesDispatcher.dispatch(KeyguardLifecyclesDispatcher.FINISHED_WAKING_UP);
            Trace.endSection();
        }

        @Override
        public void onScreenTurningOn(IKeyguardDrawnCallback callback) {
            Trace.beginSection("KeyguardService.mBinder#onScreenTurningOn");
            checkPermission();
            mKeyguardLifecyclesDispatcher.dispatch(KeyguardLifecyclesDispatcher.SCREEN_TURNING_ON,
                    callback);

            // Immediately invoke callback to prevent any delay in screen turning on
            try {
                if (callback != null) {
                    callback.onDrawn();
                }
            } catch (RemoteException e) {
                Log.w(TAG, "Exception calling onDrawn():", e);
            }

            Trace.endSection();
        }

        @Override
        public void onScreenTurnedOn() {
            Trace.beginSection("KeyguardService.mBinder#onScreenTurnedOn");
            checkPermission();
            mKeyguardLifecyclesDispatcher.dispatch(KeyguardLifecyclesDispatcher.SCREEN_TURNED_ON);
            mScreenOnCoordinator.onScreenTurnedOn();
            Trace.endSection();
        }

        @Override
        public void onScreenTurningOff() {
            checkPermission();
            mKeyguardLifecyclesDispatcher.dispatch(KeyguardLifecyclesDispatcher.SCREEN_TURNING_OFF);
        }

        @Override
        public void onScreenTurnedOff() {
            checkPermission();
            mKeyguardViewMediator.onScreenTurnedOff();
            mKeyguardLifecyclesDispatcher.dispatch(KeyguardLifecyclesDispatcher.SCREEN_TURNED_OFF);
            mScreenOnCoordinator.onScreenTurnedOff();
        }

        @Override
        public void setKeyguardEnabled(boolean enabled) {
            // Prevent Keyguard from ever being enabled
            enabled = false;
            checkPermission();
            mKeyguardViewMediator.setKeyguardEnabled(enabled);
        }

        @Override
        public void onSystemReady() {
            Trace.beginSection("KeyguardService.mBinder#onSystemReady");
            checkPermission();
            // Do nothing, preventing Keyguard from being initialized
            Trace.endSection();
        }

        @Override
        public void doKeyguardTimeout(Bundle options) {
            checkPermission();
            // No-op, prevent Keyguard timeout behavior
        }

        @Override
        public void setSwitchingUser(boolean switching) {
            checkPermission();
            // Prevent any Keyguard behavior during user switching
            mKeyguardViewMediator.setSwitchingUser(switching);
        }

        @Override
        public void setCurrentUser(int userId) {
            checkPermission();
            mKeyguardViewMediator.setCurrentUser(userId);
        }

        @Override
        public void onBootCompleted() {
            checkPermission();
            mKeyguardViewMediator.onBootCompleted();
        }

        @Override
        public void startKeyguardExitAnimation(long startTime, long fadeoutDuration) {
            // No-op, prevent Keyguard exit animation
        }

        @Override
        public void onShortPowerPressedGoHome() {
            checkPermission();
            mKeyguardViewMediator.onShortPowerPressedGoHome();
        }

        @Override
        public void dismissKeyguardToLaunch(Intent intentToLaunch) {
            checkPermission();
            // Prevent launching an intent from dismissing Keyguard
        }

        @Override
        public void onSystemKeyPressed(int keycode) {
            checkPermission();
            mKeyguardViewMediator.onSystemKeyPressed(keycode);
        }
    };
}

